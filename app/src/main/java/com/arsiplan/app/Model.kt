package com.arsiplan.app

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

enum class Tool(val label: String) {
    PAN("Kamera"), SELECT("Pilih"), WALL("Dinding"), DOOR("Pintu"), WINDOW("Jendela"),
    ROOM("Ruang"), FURN("Furnitur"), DIM("Ukur"), TEXT("Teks")
}

enum class Kind(val label: String) {
    WALL("Dinding"), DOOR("Pintu"), WINDOW("Jendela"), ROOM("Ruang"),
    FURN("Furnitur"), DIM("Dimensi"), TEXT("Teks")
}

/** Semua koordinat dalam METER (world space). */
data class El(
    val id: Int, val kind: Kind, val a: Offset, val b: Offset,
    val label: String = "", val note: String = "", val thick: Float = 0.15f
) {
    fun len() = (b - a).getDistance()
    fun w() = abs(b.x - a.x)
    fun h() = abs(b.y - a.y)
    fun area() = w() * h()
}

fun distSeg(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val l2 = ab.x * ab.x + ab.y * ab.y
    if (l2 == 0f) return (p - a).getDistance()
    val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / l2).coerceIn(0f, 1f)
    return (p - (a + ab * t)).getDistance()
}

val FURNITURE = listOf(
    "Tempat Tidur", "Sofa", "Meja Makan", "Meja Kerja", "Lemari", "Kloset",
    "Wastafel", "Bak Mandi", "Kompor", "Kulkas", "Tangga", "Mobil"
)

class PlanState {
    val els = mutableStateListOf<El>()
    var tool by mutableStateOf(Tool.WALL)
    var sel by mutableStateOf<Int?>(null)
    var zoom by mutableStateOf(1f)
    var pan by mutableStateOf(Offset(60f, 60f))
    var snap by mutableStateOf(true)
    var furnType by mutableStateOf(FURNITURE[0])
    var title by mutableStateOf("Rumah Tipe 36")

    private val undoS = ArrayDeque<List<El>>()
    private val redoS = ArrayDeque<List<El>>()
    private var nid = 1

    fun scale() = 60f * zoom
    fun toScreen(p: Offset) = p * scale() + pan
    fun toWorld(o: Offset) = (o - pan) / scale()

    fun snapPt(p: Offset): Offset {
        if (!snap) return p
        val near = els.filter { it.kind == Kind.WALL }.flatMap { listOf(it.a, it.b) }
            .minByOrNull { (it - p).getDistance() }
        if (near != null && (near - p).getDistance() < 12f / scale()) return near
        return Offset((p.x / 0.1f).roundToInt() * 0.1f, (p.y / 0.1f).roundToInt() * 0.1f)
    }

    fun zoomAt(f: Float, c: Offset) {
        val ns = (zoom * f).coerceIn(0.3f, 5f); val k = ns / zoom
        pan = c - (c - pan) * k; zoom = ns
    }

    fun constrain(s: Offset, p: Offset, k: Kind): Offset {
        if (!snap || k == Kind.ROOM || k == Kind.FURN) return p
        val dx = abs(p.x - s.x); val dy = abs(p.y - s.y)
        return when {
            dy < dx * 0.12f -> Offset(p.x, s.y)
            dx < dy * 0.12f -> Offset(s.x, p.y)
            else -> p
        }
    }

    fun push() { undoS.addLast(els.toList()); if (undoS.size > 60) undoS.removeFirst(); redoS.clear() }
    fun undo() {
        if (undoS.isEmpty()) return
        redoS.addLast(els.toList()); val s = undoS.removeLast()
        els.clear(); els.addAll(s); sel = null
    }
    fun redo() {
        if (redoS.isEmpty()) return
        undoS.addLast(els.toList()); val s = redoS.removeLast()
        els.clear(); els.addAll(s); sel = null
    }

    fun update(id: Int, f: (El) -> El) {
        val i = els.indexOfFirst { it.id == id }
        if (i >= 0) els[i] = f(els[i])
    }

    fun commit(e: El) {
        val ok = if (e.kind == Kind.ROOM || e.kind == Kind.FURN) e.area() > 0.04f else e.len() > 0.05f
        if (!ok) return
        push()
        var lbl = e.label
        if (e.kind == Kind.DOOR) lbl = "P" + (els.count { it.kind == Kind.DOOR } + 1)
        if (e.kind == Kind.WINDOW) lbl = "J" + (els.count { it.kind == Kind.WINDOW } + 1)
        val id = nid++
        els.add(e.copy(id = id, label = lbl)); sel = if (e.kind == Kind.ROOM) id else null
    }

    fun addText(p: Offset, t: String) {
        if (t.isBlank()) return
        push(); val id = nid++
        els.add(El(id, Kind.TEXT, p, p, t)); sel = id
    }

    fun delete(id: Int) { push(); els.removeAll { it.id == id }; sel = null }
    fun duplicate(id: Int) {
        val e = els.firstOrNull { it.id == id } ?: return
        push(); val n = nid++; val d = Offset(0.5f, 0.5f)
        els.add(e.copy(id = n, a = e.a + d, b = e.b + d)); sel = n
    }

    fun hit(p: Offset, tol: Float): Int? {
        fun test(e: El) = when (e.kind) {
            Kind.ROOM, Kind.FURN ->
                p.x in (min(e.a.x, e.b.x) - tol)..(max(e.a.x, e.b.x) + tol) &&
                p.y in (min(e.a.y, e.b.y) - tol)..(max(e.a.y, e.b.y) + tol)
            Kind.TEXT -> (p - e.a).getDistance() < tol * 3
            else -> distSeg(p, e.a, e.b) < tol
        }
        return (els.lastOrNull { it.kind != Kind.ROOM && test(it) } ?: els.lastOrNull { it.kind == Kind.ROOM && test(it) })?.id
    }

    fun report(): String = buildString {
        appendLine("DENAH: $title"); appendLine()
        val rooms = els.filter { it.kind == Kind.ROOM }
        appendLine("== DAFTAR RUANG ==")
        rooms.forEach {
            appendLine("- ${it.label}: %.2f x %.2f m = %.2f m²".format(it.w(), it.h(), it.area()))
            if (it.note.isNotBlank()) appendLine("    Ket: ${it.note}")
        }
        appendLine("Total luas ruang: %.2f m²".format(rooms.sumOf { it.area().toDouble() }))
        val walls = els.filter { it.kind == Kind.WALL }
        appendLine(); appendLine("== DINDING ==")
        appendLine("Jumlah: ${walls.size}, total panjang: %.2f m".format(walls.sumOf { it.len().toDouble() }))
        walls.filter { it.note.isNotBlank() }.forEach { appendLine("- %.2f m (t=%d cm): ${it.note}".format(it.len(), (it.thick * 100).roundToInt())) }
        for ((k, t) in listOf(Kind.DOOR to "PINTU", Kind.WINDOW to "JENDELA")) {
            appendLine(); appendLine("== $t ==")
            els.filter { it.kind == k }.forEach {
                appendLine("- ${it.label}: lebar %.2f m  ${it.note}".format(it.len()))
            }
        }
        appendLine(); appendLine("== FURNITUR ==")
        els.filter { it.kind == Kind.FURN }.groupBy { it.label }.forEach { (n, l) -> appendLine("- $n x${l.size}") }
        val tx = els.filter { it.kind == Kind.TEXT }
        if (tx.isNotEmpty()) { appendLine(); appendLine("== CATATAN =="); tx.forEach { appendLine("- ${it.label}") } }
    }

    fun toJson(): String {
        val arr = JSONArray()
        els.forEach {
            arr.put(JSONObject().put("id", it.id).put("k", it.kind.name)
                .put("ax", it.a.x.toDouble()).put("ay", it.a.y.toDouble())
                .put("bx", it.b.x.toDouble()).put("by", it.b.y.toDouble())
                .put("l", it.label).put("n", it.note).put("t", it.thick.toDouble()))
        }
        return JSONObject().put("title", title).put("els", arr).toString()
    }

    fun fromJson(s: String) {
        val o = JSONObject(s); title = o.optString("title", title)
        val arr = o.getJSONArray("els"); els.clear(); undoS.clear(); redoS.clear(); sel = null
        for (i in 0 until arr.length()) {
            val j = arr.getJSONObject(i)
            els.add(El(j.getInt("id"), Kind.valueOf(j.getString("k")),
                Offset(j.getDouble("ax").toFloat(), j.getDouble("ay").toFloat()),
                Offset(j.getDouble("bx").toFloat(), j.getDouble("by").toFloat()),
                j.optString("l"), j.optString("n"), j.optDouble("t", 0.15).toFloat()))
        }
        nid = (els.maxOfOrNull { it.id } ?: 0) + 1
    }
}
