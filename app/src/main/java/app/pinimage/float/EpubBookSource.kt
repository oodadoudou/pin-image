package app.pinimage.float

import android.content.Context
import android.net.Uri
import org.w3c.dom.Element
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

data class EpubBook(
    val title: String,
    val author: String?,
    val coverFile: File?,
    val readingFile: File,
)

/** Offline EPUB 2/3 preparation for the floating reflow reader. */
object EpubBookSource {
    private const val MAX_ENTRY_COUNT = 20_000
    private const val MAX_EXTRACTED_BYTES = 512L * 1024L * 1024L

    fun prepare(context: Context, rawUri: String): EpubBook? = runCatching {
        val source = File(Uri.parse(rawUri).path ?: return@runCatching null)
        if (!source.isFile) return@runCatching null
        val id = sha256(source.absolutePath + ":" + source.length() + ":" + source.lastModified())
        val root = File(context.filesDir, "epub_books/$id")
        val marker = File(root, ".ready")
        if (!marker.exists()) {
            root.deleteRecursively()
            root.mkdirs()
            extractSafely(source, root)
        }
        val packagePath = parseXml(File(root, "META-INF/container.xml"))
            .getElementsByTagNameNS("*", "rootfile")
            .item(0)?.let { it as? Element }
            ?.getAttribute("full-path")
            ?.takeIf { it.isNotBlank() }
            ?: error("EPUB package document missing")
        val packageFile = safeResolve(root, packagePath)
        val packageDoc = parseXml(packageFile)
        val title = packageDoc.getElementsByTagNameNS("*", "title").item(0)?.textContent?.trim()
            ?.takeIf { it.isNotBlank() } ?: context.getString(app.pinimage.R.string.untitled_file)
        val author = packageDoc.getElementsByTagNameNS("*", "creator").item(0)?.textContent?.trim()
            ?.takeIf { it.isNotBlank() }

        data class ManifestItem(val id: String, val href: String, val type: String, val properties: String)
        val manifest = buildMap {
            val nodes = packageDoc.getElementsByTagNameNS("*", "item")
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                val item = ManifestItem(
                    element.getAttribute("id"),
                    element.getAttribute("href"),
                    element.getAttribute("media-type"),
                    element.getAttribute("properties"),
                )
                if (item.id.isNotBlank() && item.href.isNotBlank()) put(item.id, item)
            }
        }
        val packageDir = packageFile.parentFile ?: root
        val coverId = packageDoc.getElementsByTagNameNS("*", "meta").let { nodes ->
            (0 until nodes.length).asSequence().mapNotNull { nodes.item(it) as? Element }
                .firstOrNull { it.getAttribute("name").equals("cover", true) }
                ?.getAttribute("content")
        }
        val coverItem = manifest[coverId]
            ?: manifest.values.firstOrNull { "cover-image" in it.properties.split(Regex("\\s+")) }
            ?: manifest.values.firstOrNull { it.type.startsWith("image/") }
        val coverFile = coverItem?.let { runCatching { safeResolve(packageDir, it.href.substringBefore('#')) }.getOrNull() }
            ?.takeIf { it.isFile }

        val spineNodes = packageDoc.getElementsByTagNameNS("*", "itemref")
        val chapters = (0 until spineNodes.length).mapNotNull { index ->
            val id = (spineNodes.item(index) as? Element)?.getAttribute("idref") ?: return@mapNotNull null
            manifest[id]?.takeIf { it.type.contains("html") || it.type.contains("xhtml") }
                ?.let { safeResolve(packageDir, it.href.substringBefore('#')) }
                ?.takeIf { it.isFile }
        }
        if (chapters.isEmpty()) error("EPUB spine has no readable chapters")
        val readingFile = File(root, "pinimage-reading.html")
        readingFile.writeText(buildReadingHtml(title, chapters))
        marker.writeText("1")
        EpubBook(title, author, coverFile, readingFile)
    }.getOrNull()

    private fun extractSafely(source: File, root: File) {
        ZipFile(source).use { zip ->
            var count = 0
            var total = 0L
            val entries = zip.entries()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (++count > MAX_ENTRY_COUNT) error("EPUB contains too many files")
                val target = safeResolve(root, entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use { output ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > MAX_EXTRACTED_BYTES) error("EPUB is too large")
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun parseXml(file: File) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isXIncludeAware = false
        setExpandEntityReferences(false)
        runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    }.newDocumentBuilder().parse(file)

    private fun buildReadingHtml(title: String, chapters: List<File>): String {
        val sections = chapters.joinToString("\n") { chapter ->
            val raw = chapter.readText()
                .replace(Regex("(?is)<script\\b[^>]*>.*?</script>"), "")
                .replace(Regex("(?is)<(?:iframe|object|embed)\\b[^>]*>.*?</(?:iframe|object|embed)>"), "")
            val head = Regex("(?is)<head[^>]*>(.*?)</head>").find(raw)?.groupValues?.get(1).orEmpty()
            val body = Regex("(?is)<body[^>]*>(.*?)</body>").find(raw)?.groupValues?.get(1) ?: raw
            val base = chapter.parentFile.toURI()
            val safeHead = rewriteResources(head, base).replace(Regex("(?is)<title[^>]*>.*?</title>"), "")
            val safeBody = rewriteResources(body, base)
            "$safeHead<section class=\"pinimage-chapter\">$safeBody</section>"
        }
        return """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>${escape(title)}</title><style>
            :root{color-scheme:light}html,body{margin:0;padding:0;background:#fff;color:#1c1c1e}
            body{padding:22px 20px 56px;font-family:-apple-system,BlinkMacSystemFont,"Noto Sans",sans-serif;line-height:1.65;overflow-wrap:anywhere}
            img,svg,video{max-width:100%!important;height:auto!important}table{max-width:100%;border-collapse:collapse}pre{white-space:pre-wrap}
            .pinimage-chapter{max-width:46em;margin:0 auto;padding:0 0 32px}.pinimage-chapter+.pinimage-chapter{border-top:1px solid #d1d1d6;padding-top:28px}
            a{color:#007aff}body{-webkit-text-size-adjust:100%}</style></head><body>$sections</body></html>"""
    }

    private fun rewriteResources(html: String, base: URI): String =
        Regex("(?i)(src|href|poster)\\s*=\\s*([\"'])(.*?)\\2").replace(html) { match ->
            val value = match.groupValues[3]
            val rewritten = when {
                value.isBlank() || value.startsWith("#") || value.startsWith("data:") -> value
                value.startsWith("http:") || value.startsWith("https:") || value.startsWith("javascript:") -> "#"
                else -> runCatching { base.resolve(value).toString() }.getOrDefault("#")
            }
            "${match.groupValues[1]}=${match.groupValues[2]}$rewritten${match.groupValues[2]}"
        }

    private fun safeResolve(root: File, relative: String): File {
        val target = File(root, relative).canonicalFile
        val canonicalRoot = root.canonicalFile
        if (target != canonicalRoot && !target.path.startsWith(canonicalRoot.path + File.separator)) error("Unsafe EPUB path")
        return target
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
