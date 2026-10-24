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

data class Layer(val id: Int, val name: String, val visible: Boolean = true, val locked: Boolean = false)

fun defaultLayers() = listOf(
    Layer(1, "Dinding"), Layer(2, "Pintu & Jendela"), Layer(3, "Ruang"),
    Layer(4, "Furnitur"), Layer(5, "Anotasi")
)

fun rotv(o: Offset, a: Float) = Offset(o.x * cos(a) - o.y * sin(a), o.x * sin(a) + o.y * cos(a))

/** Koordinat dalam METER. rot (radian) dipakai untuk ROOM/FURN (diputar terhadap pusat). */
data class El(
    val id: Int, val kind: Kind, val a: Offset, val b: Offset,
    val label: String = "", val note: String = "", val thick: Float = 0.15f,
    val layer: Int = 1, val rot: Float = 0f
) {
    fun len() = (b - a).getDistance()
    fun w() = abs(b.x - a.x)
    fun h() = abs(b.y - a.y)
    fun area() = w() * h()
    fun center() = (a + b) / 2f
    fun isRect() = kind == Kind.ROOM || kind == Kind.FURN
    fun isLine() = kind == Kind.WALL || kind == Kind.DOOR || kind == Kind.WINDOW || kind == Kind.DIM
    fun corners(): List<Offset> {
        val c = center(); val hx = w() / 2f; val hy = h() / 2f
        return listOf(Offset(-hx, -hy), Offset(hx, -hy), Offset(hx, hy), Offset(-hx, hy)).map { c + rotv(it, rot) }
    }
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
    val layers = mutableStateListOf<Layer>().apply { addAll(defaultLayers()) }
    var active by mutableStateOf(1)
    var autoLayer by mutableStateOf(true)
    var tool by mutableStateOf(Tool.WALL)
    var sel by mutableStateOf<Int?>(null)
    var zoom by mutableStateOf(1f)
    var pan by mutableStateOf(Offset(60f, 60f))
    var rot by mutableStateOf(0f) // rotasi kamera (radian)
    var snap by mutableStateOf(true)
    var furnType by mutableStateOf(FURNITURE[0])
    var title by mutableStateOf("Rumah Tipe 36")
    var msg by mutableStateOf<String?>(null)
    // dinding bersambung
    var chain by mutableStateOf(false)
    var chainPt by mutableStateOf<Offset?>(null)
    var chainFirst: Offset? = null

    private val undoS = ArrayDeque<List<El>>()
    private val redoS = ArrayDeque<List<El>>()
    private var nid = 1

    // ---------- kamera ----------
    fun scale() = 60f * zoom
    fun toScreen(p: Offset) = rotv(p * scale(), rot) + pan
    fun toWorld(o: Offset) = rotv(o - pan, -rot) / scale()

    /** Zoom + putar + geser dengan titik c (layar) sebagai pusat. */
    fun transformAt(c: Offset, zf: Float, rd: Float, pd: Offset) {
        val w = toWorld(c)
        zoom = (zoom * zf).coerceIn(0.3f, 5f); rot += rd
        pan = c - rotv(w * scale(), rot) + pd
    }
    fun zoomAt(f: Float, c: Offset) = transformAt(c, f, 0f, Offset.Zero)

    // ---------- snap ----------
    fun snapPt(p: Offset, exclude: Int? = null): Offset {
        if (!snap) return p
        val near = els.filter { it.kind == Kind.WALL && it.id != exclude && editable(it) }
            .flatMap { listOf(it.a, it.b) }.minByOrNull { (it - p).getDistance() }
        if (near != null && (near - p).getDistance() < 12f / scale()) return near
        return Offset((p.x / 0.1f).roundToInt() * 0.1f, (p.y / 0.1f).roundToInt() * 0.1f)
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

    // ---------- layer ----------
    fun layerOf(id: Int) = layers.firstOrNull { it.id == id }
    fun shown(e: El) = layerOf(e.layer)?.visible ?: true
    fun editable(e: El) = layerOf(e.layer)?.let { it.visible && !it.locked } ?: true
    private fun defaultLayer(k: Kind) = when (k) {
        Kind.WALL -> 1; Kind.DOOR, Kind.WINDOW -> 2; Kind.ROOM -> 3; Kind.FURN -> 4; else -> 5
    }
    fun layerFor(k: Kind): Int { val d = defaultLayer(k); return if (autoLayer && layerOf(d) != null) d else active }
    private fun canUse(id: Int): Boolean {
        val l = layerOf(id)
        if (l != null && (!l.visible || l.locked)) { msg = "Layer \"${l.name}\" tersembunyi atau terkunci"; return false }
        return true
    }
    fun setLayer(l: Layer) {
        val i = layers.indexOfFirst { it.id == l.id }; if (i >= 0) layers[i] = l
        selEl()?.let { if (!shown(it)) sel = null }
    }
    fun addLayer(n: String) {
        if (n.isBlank()) return
        val id = (layers.maxOfOrNull { it.id } ?: 0) + 1
        layers.add(Layer(id, n.trim())); active = id; autoLayer = false
    }
    fun deleteLayer(id: Int) {
        if (layers.size <= 1) { msg = "Minimal harus ada satu layer"; return }
        push(); val target = layers.first { it.id != id }.id
        for (i in els.indices) if (els[i].layer == id) els[i] = els[i].copy(layer = target)
        layers.removeAll { it.id == id }; if (active == id) active = target
    }

    // ---------- undo ----------
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

    // ---------- elemen ----------
    fun selEl(): El? = sel?.let { id -> els.firstOrNull { it.id == id } }
    fun update(id: Int, f: (El) -> El) {
        val i = els.indexOfFirst { it.id == id }
        if (i >= 0) els[i] = f(els[i])
    }

    fun commit(e: El): Boolean {
        val ok = if (e.isRect()) e.area() > 0.04f else e.len() > 0.05f
        if (!ok) return false
        val ly = layerFor(e.kind)
        if (!canUse(ly)) return false
        push()
        var lbl = e.label
        if (e.kind == Kind.DOOR) lbl = "P" + (els.count { it.kind == Kind.DOOR } + 1)
        if (e.kind == Kind.WINDOW) lbl = "J" + (els.count { it.kind == Kind.WINDOW } + 1)
        val id = nid++
        els.add(e.copy(id = id, label = lbl, layer = ly)); sel = if (e.kind == Kind.ROOM) id else null
        return true
    }

    fun addText(p: Offset, t: String) {
        if (t.isBlank()) return
        val ly = layerFor(Kind.TEXT); if (!canUse(ly)) return
        push(); val id = nid++
        els.add(El(id, Kind.TEXT, p, p, t, layer = ly)); sel = id
    }

    fun delete(id: Int) { push(); els.removeAll { it.id == id }; sel = null }
    fun duplicate(id: Int) {
        val e = els.firstOrNull { it.id == id } ?: return
        push(); val n = nid++; val d = Offset(0.5f, 0.5f)
        els.add(e.copy(id = n, a = e.a + d, b = e.b + d)); sel = n
    }

    /** Putar elemen terpilih: persegi diputar terhadap pusat, garis terhadap titik tengah. */
    fun rotateSel(id: Int, deg: Float) {
        val e = els.firstOrNull { it.id == id } ?: return
        push(); val d = Math.toRadians(deg.toDouble()).toFloat()
        if (e.isRect()) update(id) { it.copy(rot = it.rot + d) }
        else if (e.isLine()) { val m = e.center(); update(id) { it.copy(a = m + rotv(it.a - m, d), b = m + rotv(it.b - m, d)) } }
    }

    /** Geser satu ujung garis; ujung dinding lain yang menyambung ikut berpindah. */
    fun moveEnd(id: Int, isA: Boolean, np: Offset) {
        val e = els.firstOrNull { it.id == id } ?: return
        val old = if (isA) e.a else e.b
        update(id) { if (isA) it.copy(a = np) else it.copy(b = np) }
        if (e.kind != Kind.WALL) return
        for (i in els.indices) {
            val o = els[i]
            if (o.id == id || o.kind != Kind.WALL || !editable(o)) continue
            var n = o
            if ((o.a - old).getDistance() < 0.01f) n = n.copy(a = np)
            if ((o.b - old).getDistance() < 0.01f) n = n.copy(b = np)
            if (n != o) els[i] = n
        }
    }

    // ---------- dinding bersambung ----------
    fun endChain() { chainPt = null; chainFirst = null }
    fun closeChain() {
        val c = chainPt; val f = chainFirst
        if (c != null && f != null && (c - f).getDistance() > 0.05f) commit(El(-1, Kind.WALL, c, f))
        endChain()
    }

    fun hit(p: Offset, tol: Float): Int? {
        fun test(e: El): Boolean = when {
            e.isRect() -> { val l = rotv(p - e.center(), -e.rot); abs(l.x) <= e.w() / 2f + tol && abs(l.y) <= e.h() / 2f + tol }
            e.kind == Kind.TEXT -> (p - e.a).getDistance() < tol * 3
            else -> distSeg(p, e.a, e.b) < tol
        }
        val c = els.filter { editable(it) }
        return (c.lastOrNull { it.kind != Kind.ROOM && test(it) } ?: c.lastOrNull { it.kind == Kind.ROOM && test(it) })?.id
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
            els.filter { it.kind == k }.forEach { appendLine("- ${it.label}: lebar %.2f m  ${it.note}".format(it.len())) }
        }
        appendLine(); appendLine("== FURNITUR ==")
        els.filter { it.kind == Kind.FURN }.groupBy { it.label }.forEach { (n, l) -> appendLine("- $n x${l.size}") }
        val tx = els.filter { it.kind == Kind.TEXT }
        if (tx.isNotEmpty()) { appendLine(); appendLine("== CATATAN =="); tx.forEach { appendLine("- ${it.label}") } }
        appendLine(); appendLine("== LAYER ==")
        layers.forEach { l -> appendLine("- ${l.name}: ${els.count { it.layer == l.id }} objek" + (if (l.visible) "" else " (disembunyikan)") + (if (l.locked) " (terkunci)" else "")) }
    }

    fun toJson(): String {
        val arr = JSONArray()
        els.forEach {
            arr.put(JSONObject().put("id", it.id).put("k", it.kind.name)
                .put("ax", it.a.x.toDouble()).put("ay", it.a.y.toDouble())
                .put("bx", it.b.x.toDouble()).put("by", it.b.y.toDouble())
                .put("l", it.label).put("n", it.note).put("t", it.thick.toDouble())
                .put("ly", it.layer).put("r", it.rot.toDouble()))
        }
        val la = JSONArray()
        layers.forEach { la.put(JSONObject().put("id", it.id).put("n", it.name).put("v", it.visible).put("lk", it.locked)) }
        return JSONObject().put("title", title).put("layers", la).put("active", active)
            .put("auto", autoLayer).put("els", arr).toString()
    }

    fun fromJson(s: String) {
        val o = JSONObject(s); title = o.optString("title", title)
        layers.clear()
        val la = o.optJSONArray("layers")
        if (la != null && la.length() > 0) {
            for (i in 0 until la.length()) {
                val j = la.getJSONObject(i)
                layers.add(Layer(j.getInt("id"), j.getString("n"), j.optBoolean("v", true), j.optBoolean("lk", false)))
            }
        } else layers.addAll(defaultLayers())
        active = o.optInt("active", layers.first().id); autoLayer = o.optBoolean("auto", true)
        val arr = o.getJSONArray("els"); els.clear(); undoS.clear(); redoS.clear(); sel = null; endChain()
        for (i in 0 until arr.length()) {
            val j = arr.getJSONObject(i)
            els.add(El(j.getInt("id"), Kind.valueOf(j.getString("k")),
                Offset(j.getDouble("ax").toFloat(), j.getDouble("ay").toFloat()),
                Offset(j.getDouble("bx").toFloat(), j.getDouble("by").toFloat()),
                j.optString("l"), j.optString("n"), j.optDouble("t", 0.15).toFloat(),
                j.optInt("ly", 1), j.optDouble("r", 0.0).toFloat()))
        }
        nid = (els.maxOfOrNull { it.id } ?: 0) + 1
    }
}
