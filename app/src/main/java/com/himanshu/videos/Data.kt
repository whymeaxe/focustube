package com.himanshu.videos

import android.content.Context
import android.text.Html
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.util.Locale

data class Creator(val id: String, val name: String, val avatar: String?)

data class Video(
    val id: String,
    val title: String,
    val channelId: String,
    val channelName: String,
    val published: Long,
    val thumbnail: String,
    val views: Long,
    val isShort: Boolean,
    val description: String,
)

/** Everything is fetched from YouTube's public pages + RSS feeds. No API key, no login. */
object YouTube {
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    private val UC = Regex("UC[\\w-]{22}")

    fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        c.setRequestProperty("Cookie", "CONSENT=YES+cb; SOCS=CAI")
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.instanceFollowRedirects = true
        try {
            val code = c.responseCode
            if (code == 404) throw IOException("Channel not found")
            if (code !in 200..299) throw IOException("YouTube returned $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun unescape(s: String) = Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString()

    /** Accepts @handle, channel URL, /c/ or /user/ URL, raw UC id, a video/shorts link, or a plain name. */
    fun resolve(rawInput: String): Creator {
        val input = Regex("https?://\\S+").find(rawInput)?.value ?: rawInput.trim()
        if (input.isBlank()) throw IOException("Paste a channel link or @handle")
        val url = when {
            UC.matches(input) -> "https://www.youtube.com/channel/$input"
            input.startsWith("@") -> "https://www.youtube.com/${input.substringBefore(' ')}"
            input.startsWith("http") -> input
            input.contains("youtube.com") || input.contains("youtu.be") -> "https://$input"
            else -> "https://www.youtube.com/@${input.replace(" ", "")}"
        }
        var html = get(url)
        val id = Regex("<meta itemprop=\"identifier\" content=\"(UC[\\w-]{22})\"").find(html)?.groupValues?.get(1)
            ?: Regex("\"externalId\":\"(UC[\\w-]{22})\"").find(html)?.groupValues?.get(1)
            ?: Regex("\"channelId\":\"(UC[\\w-]{22})\"").find(html)?.groupValues?.get(1)
            ?: Regex("youtube\\.com/channel/(UC[\\w-]{22})").find(html)?.groupValues?.get(1)
            ?: throw IOException("Couldn't find that creator. Try their channel link.")

        // A video link lands on a watch page — load the real channel page for name + avatar.
        if (!html.contains("\"externalId\":\"$id\"")) {
            html = runCatching { get("https://www.youtube.com/channel/$id") }.getOrDefault("")
        }
        var name = Regex("<meta property=\"og:title\" content=\"([^\"]+)\"").find(html)?.groupValues?.get(1)?.let(::unescape)
        val avatar = Regex("<meta property=\"og:image\" content=\"([^\"]+)\"").find(html)?.groupValues?.get(1)?.let(::unescape)
        if (name.isNullOrBlank()) name = runCatching { feed(id).first }.getOrNull()
        return Creator(id, name ?: "Creator", avatar)
    }

    /** Returns (channel title, latest ~15 uploads) from the public RSS feed. */
    fun feed(channelId: String): Pair<String, List<Video>> {
        val xml = get("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
        val p = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
        p.setInput(StringReader(xml))
        var feedTitle = ""
        var inEntry = false
        var tag = ""
        var cur = HashMap<String, String>()
        val out = ArrayList<Video>()
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    tag = p.name
                    when (p.name) {
                        "entry" -> { inEntry = true; cur = HashMap() }
                        "link" -> if (inEntry) cur["link"] = p.getAttributeValue(null, "href") ?: ""
                        "media:thumbnail" -> if (inEntry) cur["thumb"] = p.getAttributeValue(null, "url") ?: ""
                        "media:statistics" -> if (inEntry) cur["views"] = p.getAttributeValue(null, "views") ?: "0"
                    }
                }
                XmlPullParser.TEXT -> {
                    val t = p.text ?: ""
                    if (!inEntry) {
                        if (tag == "title") feedTitle += t
                    } else {
                        val key = when (tag) {
                            "yt:videoId" -> "id"
                            "title" -> "title"
                            "published" -> "published"
                            "name" -> "author"
                            "media:description" -> "desc"
                            else -> null
                        }
                        if (key != null) cur[key] = (cur[key] ?: "") + t
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (p.name == "entry") {
                        inEntry = false
                        val vid = cur["id"]?.trim().orEmpty()
                        if (vid.isNotEmpty()) {
                            out += Video(
                                id = vid,
                                title = cur["title"]?.trim().orEmpty(),
                                channelId = channelId,
                                channelName = cur["author"]?.trim().orEmpty().ifEmpty { feedTitle.trim() },
                                published = runCatching {
                                    OffsetDateTime.parse(cur["published"]!!.trim()).toInstant().toEpochMilli()
                                }.getOrDefault(0L),
                                thumbnail = cur["thumb"]?.takeIf { it.isNotBlank() }
                                    ?: "https://i.ytimg.com/vi/$vid/hqdefault.jpg",
                                views = cur["views"]?.trim()?.toLongOrNull() ?: 0L,
                                isShort = cur["link"]?.contains("/shorts/") == true,
                                description = cur["desc"]?.trim().orEmpty(),
                            )
                        }
                    }
                    tag = ""
                }
            }
            ev = p.next()
        }
        return feedTitle.trim() to out
    }
}

class Store(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("hv", Context.MODE_PRIVATE)

    fun creators(): List<Creator> = runCatching {
        val a = JSONArray(p.getString("creators", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Creator(o.getString("id"), o.getString("name"), o.optString("avatar").ifBlank { null })
        }
    }.getOrDefault(emptyList())

    fun saveCreators(list: List<Creator>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("avatar", it.avatar ?: "")) }
        p.edit().putString("creators", a.toString()).commit()
    }

    fun seen(): Set<String> = HashSet(p.getStringSet("seen", emptySet()) ?: emptySet())
    fun saveSeen(s: Set<String>) {
        val trimmed = if (s.size > 6000) s.toList().takeLast(3000).toSet() else s
        p.edit().putStringSet("seen", HashSet(trimmed)).commit()
    }

    fun known(): Set<String> = HashSet(p.getStringSet("known", emptySet()) ?: emptySet())
    fun saveKnown(s: Set<String>) { p.edit().putStringSet("known", HashSet(s)).commit() }

    fun cachedFeed(): List<Video> = runCatching {
        val a = JSONArray(p.getString("feed", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Video(
                o.getString("id"), o.getString("title"), o.getString("cid"), o.getString("cn"),
                o.getLong("pub"), o.getString("th"), o.optLong("v"), o.optBoolean("s"), o.optString("d"),
            )
        }
    }.getOrDefault(emptyList())

    fun saveFeed(list: List<Video>) {
        val a = JSONArray()
        list.take(600).forEach {
            a.put(
                JSONObject().put("id", it.id).put("title", it.title).put("cid", it.channelId)
                    .put("cn", it.channelName).put("pub", it.published).put("th", it.thumbnail)
                    .put("v", it.views).put("s", it.isShort).put("d", it.description)
            )
        }
        p.edit().putString("feed", a.toString()).commit()
    }
}

fun timeAgo(ms: Long): String {
    if (ms <= 0) return ""
    val d = (System.currentTimeMillis() - ms) / 1000
    return when {
        d < 60 -> "just now"
        d < 3600 -> "${d / 60}m ago"
        d < 86400 -> "${d / 3600}h ago"
        d < 604800 -> "${d / 86400}d ago"
        d < 2592000 -> "${d / 604800}w ago"
        d < 31536000 -> "${d / 2592000}mo ago"
        else -> "${d / 31536000}y ago"
    }
}

fun viewsText(n: Long): String {
    fun f(x: Double, s: String) = String.format(Locale.US, "%.1f", x).removeSuffix(".0") + s
    return when {
        n <= 0 -> ""
        n >= 1_000_000_000 -> f(n / 1e9, "B views")
        n >= 1_000_000 -> f(n / 1e6, "M views")
        n >= 1_000 -> f(n / 1e3, "K views")
        else -> "$n views"
    }
}
