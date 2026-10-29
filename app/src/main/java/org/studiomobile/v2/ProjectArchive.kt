package org.studiomobile.v2

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ProjectArchive {
    data class ImportedMedia(val item: ProjectItem, val cachedFile: File)

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun importMedia(context: Context, uri: Uri, resolver: ContentResolver, progress: (Long) -> Unit): ImportedMedia {
        val name = queryName(context, uri) ?: "media_${System.currentTimeMillis()}"
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val kind = if (mime.startsWith("video/")) "video" else if (mime.startsWith("image/")) "image" else "file"
        val target = File(context.cacheDir, "imports/${UUID.randomUUID()}_${safeName(name)}")
        target.parentFile?.mkdirs()
        var total = 0L
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).buffered().use { output ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n); total += n; progress(total)
                }
            }
        } ?: throw IOException("Impossible de lire le fichier sélectionné.")
        val hash = sha256(target)
        val id = UUID.randomUUID().toString()
        val path = "media/${id}_${safeName(name)}"
        return ImportedMedia(ProjectItem(id, path, name, mime, kind, sha256 = hash), target)
    }

    fun save(context: Context, doc: ProjectDocument, mediaFiles: Map<String, File>, output: OutputStream, progress: (Int) -> Unit) {
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            val manifest = doc.toJson().toString(2).toByteArray(Charsets.UTF_8)
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest); zip.closeEntry()
            val total = doc.items.size.coerceAtLeast(1)
            doc.items.forEachIndexed { index, item ->
                val file = mediaFiles[item.id] ?: throw IOException("Média absent du stockage : ${item.displayName}")
                zip.putNextEntry(ZipEntry(item.mediaPath))
                file.inputStream().buffered().use { it.copyTo(zip, 64 * 1024) }
                zip.closeEntry()
                progress(((index + 1) * 100) / total)
            }
        }
    }

    fun open(context: Context, input: InputStream, progress: (Int) -> Unit): Pair<ProjectDocument, Map<String, File>> {
        val cacheRoot = File(context.cacheDir, "opened/${UUID.randomUUID()}")
        cacheRoot.mkdirs()
        var manifestText: String? = null
        val extracted = mutableMapOf<String, File>()
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry: ZipEntry?
            var count = 0
            while (zip.nextEntry.also { entry = it } != null) {
                val e = entry!!
                val normalized = e.name.replace('\\', '/')
                require(!normalized.startsWith("/") && normalized.split("/").none { it == ".." }) {
                    "Archive refusée : chemin non sûr."
                }
                if (!e.isDirectory) {
                    if (normalized == "manifest.json") {
                        val out = ByteArrayOutputStream()
                        zip.copyTo(out)
                        require(out.size() <= 5 * 1024 * 1024) { "Manifeste trop volumineux." }
                        manifestText = out.toString("UTF-8")
                    } else if (normalized.startsWith("media/")) {
                        val dest = File(cacheRoot, normalized)
                        dest.parentFile?.mkdirs()
                        FileOutputStream(dest).buffered().use { zip.copyTo(it, 64 * 1024) }
                        extracted[normalized] = dest
                    }
                }
                count++
                progress((count % 100) + 1)
                zip.closeEntry()
            }
        }
        val manifest = manifestText ?: throw IOException("Le fichier ne contient pas manifest.json.")
        val doc = ProjectDocument.fromJson(JSONObject(manifest))
        doc.items.forEach { item ->
            val file = extracted[item.mediaPath] ?: throw IOException("Média manquant : ${item.displayName}")
            if (sha256(file) != item.sha256) throw IOException("Vérification SHA-256 échouée : ${item.displayName}")
        }
        return doc to doc.items.associate { it.id to extracted.getValue(it.mediaPath) }
    }

    private fun queryName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (_: Exception) { null }
    }
    private fun safeName(value: String) = value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100).ifBlank { "media.bin" }
}
