package org.studiomobile.v2

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var canvasView: ProjectCanvasView
    private val doc = ProjectDocument()
    private val mediaFiles = mutableMapOf<String, File>()
    private var selectedId: String? = null

    private val importPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        progress.visibility = View.VISIBLE; progress.isIndeterminate = true
        status.text = "Importation de ${uris.size} fichier(s)…"
        thread {
            var done = 0
            val errors = mutableListOf<String>()
            uris.forEach { uri ->
                try {
                    val imported = ProjectArchive.importMedia(this, uri, contentResolver) { bytes ->
                        runOnUiThread { status.text = "Import : ${importedSafeName(uri)} — ${bytes / 1024} Ko" }
                    }
                    val duplicate = doc.items.firstOrNull { it.sha256 == imported.item.sha256 }
                    if (duplicate == null) {
                        val item = imported.item
                        item.x = (0.18f + (doc.items.size % 4) * 0.2f).coerceIn(0.1f, 0.9f)
                        item.y = (0.22f + (doc.items.size % 3) * 0.25f).coerceIn(0.1f, 0.9f)
                        doc.items.add(item); mediaFiles[item.id] = imported.cachedFile
                    } else imported.cachedFile.delete()
                } catch (e: Exception) { errors.add("${uri.lastPathSegment}: ${e.message}") }
                done++
                runOnUiThread { status.text = "Importation : $done/${uris.size}" }
            }
            runOnUiThread {
                progress.visibility = View.GONE
                status.text = if (errors.isEmpty()) "Import terminé : ${doc.items.size} élément(s). Doublons ignorés." else "Import terminé avec ${errors.size} erreur(s) : ${errors.first()}"
                canvasView.invalidate()
            }
        }
    }

    private val openProject = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) openProjectUri(uri)
    }
    private val saveProject = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) saveProjectUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 12, 16, 8) }
        val title = TextView(this).apply { text = "Studio Mobile v2"; textSize = 22f; setTypeface(null, Typeface.BOLD) }
        root.addView(title)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(makeButton("Importer", 0) { importPicker.launch(arrayOf("image/*", "video/*")) })
        buttons.addView(makeButton("Ouvrir .studio", 1) { openProject.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) })
        buttons.addView(makeButton("Enregistrer", 2) { saveProject.launch("${safeDocName(doc.name)}.studio") })
        root.addView(buttons)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; visibility = View.GONE }
        root.addView(progress, LinearLayout.LayoutParams(-1, 12))
        status = TextView(this).apply { text = "Prêt. Importe des images ou vidéos pour commencer."; textSize = 12f; setPadding(0, 6, 0, 6) }
        root.addView(status)
        canvasView = ProjectCanvasView()
        root.addView(canvasView, LinearLayout.LayoutParams(-1, 0, 1f))
        val hint = TextView(this).apply { text = "Touchez un élément pour le sélectionner, puis faites-le glisser. Le projet est autonome : les médias sont intégrés au fichier .studio."; textSize = 12f }
        root.addView(hint)
        setContentView(root)
    }

    private fun makeButton(label: String, id: Int, action: () -> Unit): Button = Button(this).apply {
        text = label; textSize = 11f; isAllCaps = false; setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = 4 }
    }

    private fun saveProjectUri(uri: Uri) {
        progress.visibility = View.VISIBLE; progress.isIndeterminate = true
        status.text = "Préparation de l'archive…"
        thread {
            try {
                contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    ProjectArchive.save(this, doc, mediaFiles, out) { p ->
                        runOnUiThread { progress.isIndeterminate = false; progress.progress = p; status.text = "Enregistrement : $p%" }
                    }
                } ?: throw Exception("Impossible d'écrire le fichier choisi.")
                runOnUiThread { progress.visibility = View.GONE; status.text = "Projet enregistré : ${doc.items.size} élément(s)." }
            } catch (e: Exception) {
                runOnUiThread { progress.visibility = View.GONE; status.text = "Échec de sauvegarde : ${e.message}" }
            }
        }
    }

    private fun openProjectUri(uri: Uri) {
        progress.visibility = View.VISIBLE; progress.isIndeterminate = true
        status.text = "Ouverture et vérification du projet…"
        thread {
            try {
                val (loaded, loadedFiles) = contentResolver.openInputStream(uri)?.use { input ->
                    ProjectArchive.open(this, input) { p -> runOnUiThread { status.text = "Lecture de l'archive…"; progress.progress = p } }
                } ?: throw Exception("Impossible de lire ce fichier.")
                runOnUiThread {
                    doc.name = loaded.name; doc.canvasWidth = loaded.canvasWidth; doc.canvasHeight = loaded.canvasHeight
                    doc.items.clear(); doc.items.addAll(loaded.items)
                    mediaFiles.clear(); mediaFiles.putAll(loadedFiles)
                    progress.visibility = View.GONE
                    status.text = "Projet ouvert et vérifié : ${doc.items.size} élément(s)."
                    canvasView.invalidate()
                }
            } catch (e: Exception) {
                runOnUiThread { progress.visibility = View.GONE; status.text = "Ouverture impossible : ${e.message}" }
            }
        }
    }

    private fun importedSafeName(uri: Uri) = uri.lastPathSegment?.takeLast(28) ?: "média"
    private fun safeDocName(s: String) = s.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "MonProjet" }

    private inner class ProjectCanvasView : View(this@MainActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var active: ProjectItem? = null
        private var lastX = 0f; private var lastY = 0f
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.rgb(245, 246, 248))
            paint.color = Color.WHITE; canvas.drawRoundRect(12f, 12f, width-12f, height-12f, 12f, 12f, paint)
            val sx = width.toFloat() / doc.canvasWidth
            val sy = height.toFloat() / doc.canvasHeight
            doc.items.forEach { item ->
                val cx = item.x * width; val cy = item.y * height
                val w = item.width * width; val h = item.height * height
                val rect = RectF(cx-w/2, cy-h/2, cx+w/2, cy+h/2)
                paint.color = if (item.id == selectedId) Color.rgb(198, 226, 255) else Color.rgb(230, 234, 239)
                canvas.drawRoundRect(rect, 8f, 8f, paint)
                val file = mediaFiles[item.id]
                if (file != null && (item.kind == "image" || item.kind == "video")) {
                    // onDraw never decodes a file itself anymore: it only ever reads
                    // ThumbnailCache, which holds a small, capped-size bitmap generated
                    // once in the background. This is what makes dragging smooth again
                    // and what finally makes huge photos (and videos) safe to preview.
                    val thumb = ThumbnailCache.get(item.id)
                    if (thumb != null) {
                        val src = Rect(0, 0, thumb.width, thumb.height)
                        paint.color = Color.WHITE
                        canvas.drawBitmap(thumb, src, rect, paint)
                    } else {
                        drawLabel(canvas, item, rect)
                        if (!ThumbnailCache.isPending(item.id)) requestThumbnail(item.id, file, item.kind)
                    }
                } else drawLabel(canvas, item, rect)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = if (item.id == selectedId) 4f else 1.5f
                paint.color = if (item.id == selectedId) Color.BLUE else Color.DKGRAY
                canvas.drawRoundRect(rect, 8f, 8f, paint); paint.style = Paint.Style.FILL
            }
            if (doc.items.isEmpty()) {
                paint.color = Color.DKGRAY; paint.textSize = 18f
                canvas.drawText("Espace de composition", 32f, height/2f, paint)
                paint.textSize = 13f; canvas.drawText("Utilise « Importer » pour ajouter des médias", 32f, height/2f+26f, paint)
            }
        }
        private fun requestThumbnail(itemId: String, file: File, kind: String) {
            thread {
                ThumbnailCache.generate(itemId, file, kind)
                post { invalidate() } // back to the UI thread; invalidate() triggers one more onDraw that now finds the thumbnail ready
            }
        }
        private fun drawLabel(canvas: Canvas, item: ProjectItem, rect: RectF) {
            paint.color = Color.DKGRAY; paint.textSize = 15f
            val label = (if (item.kind == "video") "▶ " else if (item.kind == "image") "▧ " else "▤ ") + item.displayName
            val clipped = if (label.length > 22) label.take(19) + "…" else label
            canvas.drawText(clipped, rect.left+8f, rect.centerY(), paint)
        }
        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    active = doc.items.asReversed().firstOrNull { item ->
                        val cx=item.x*width; val cy=item.y*height
                        event.x in (cx-item.width*width/2)..(cx+item.width*width/2) &&
                        event.y in (cy-item.height*height/2)..(cy+item.height*height/2)
                    }
                    selectedId = active?.id; lastX=event.x; lastY=event.y; invalidate(); return true
                }
                MotionEvent.ACTION_MOVE -> {
                    active?.let {
                        it.x = (it.x + (event.x-lastX)/width).coerceIn(0.02f,0.98f)
                        it.y = (it.y + (event.y-lastY)/height).coerceIn(0.02f,0.98f)
                        lastX=event.x; lastY=event.y; invalidate()
                    }; return true
                }
                MotionEvent.ACTION_UP -> { active=null; return true }
            }
            return true
        }
    }
}
