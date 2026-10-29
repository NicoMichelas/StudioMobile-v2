package org.studiomobile.v2

import org.json.JSONArray
import org.json.JSONObject

data class ProjectItem(
    val id: String,
    val mediaPath: String,
    val displayName: String,
    val mimeType: String,
    val kind: String,
    var x: Float = 0.5f,
    var y: Float = 0.5f,
    var width: Float = 0.24f,
    var height: Float = 0.24f,
    val sha256: String
)

data class ProjectDocument(
    var name: String = "Sans titre",
    var canvasWidth: Int = 1920,
    var canvasHeight: Int = 1080,
    val items: MutableList<ProjectItem> = mutableListOf(),
    val schemaVersion: Int = 1
) {
    fun toJson(): JSONObject {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id); put("mediaPath", item.mediaPath)
                put("displayName", item.displayName); put("mimeType", item.mimeType)
                put("kind", item.kind); put("x", item.x); put("y", item.y)
                put("width", item.width); put("height", item.height)
                put("sha256", item.sha256)
            })
        }
        return JSONObject().apply {
            put("format", "studiomobile-project")
            put("schemaVersion", schemaVersion)
            put("name", name)
            put("canvas", JSONObject().put("width", canvasWidth).put("height", canvasHeight))
            put("items", arr)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ProjectDocument {
            require(json.optString("format") == "studiomobile-project") { "Format de projet non reconnu." }
            val version = json.optInt("schemaVersion", -1)
            require(version == 1) { "Version de projet non prise en charge : $version" }
            val canvas = json.getJSONObject("canvas")
            val doc = ProjectDocument(
                name = json.optString("name", "Sans titre"),
                canvasWidth = canvas.getInt("width"),
                canvasHeight = canvas.getInt("height")
            )
            val arr = json.getJSONArray("items")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                doc.items.add(ProjectItem(
                    id = o.getString("id"), mediaPath = o.getString("mediaPath"),
                    displayName = o.getString("displayName"), mimeType = o.getString("mimeType"),
                    kind = o.getString("kind"), x = o.getDouble("x").toFloat(),
                    y = o.getDouble("y").toFloat(), width = o.getDouble("width").toFloat(),
                    height = o.getDouble("height").toFloat(), sha256 = o.getString("sha256")
                ))
            }
            return doc
        }
    }
}
