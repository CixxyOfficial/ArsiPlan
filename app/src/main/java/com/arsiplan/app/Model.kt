package com.arsiplan.app

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

enum class Tool(val label: String) {
    SELECT("Pilih"), PAN("Kamera"),
    LINE("Garis"), WALL("Dinding"), RECT("Persegi"), CIRCLE("Lingkaran"),
    DOOR("Pintu"), WINDOW("Jendela"), ROOM("Ruang"), FURN("Furnitur"),
    DIM("Dimensi"), TEXT("Teks"),
    MOVE("Pindah"), COPY("Salin"), ROTATE("Putar"), OFFSET("Offset"), ERASE("Hapus")
}

enum class Kind(val label: String) {
    LINE("Garis"), WALL("Dinding"), DOOR("Pintu"), WINDOW("Jendela"), RECT("Persegi"),
    ROOM("Ruang"), FURN("Furnitur"), CIRCLE("Lingkaran"), DIM("Dimensi"), TEXT("Teks")
}

fun Tool.kind(): Kind? = when (this) {
    Tool.LINE -> Kind.LINE; Tool.WALL -> Kind.WALL; Tool.RECT -> Kind.RECT; Tool.CIRCLE -> Kind.CIRCLE
    Tool.DOOR -> Kind.DOOR; Tool.WINDOW -> Kind.WINDOW; Tool.ROOM -> Kind.ROOM; Tool.FURN -> Kind.FURN
    Tool.DIM -> Kind.DIM; Tool.TEXT -> Kind.TEXT; else -> null
}

val ALIASES: Map<String, Tool> = mapOf(
    "L" to Tool.LINE, "LINE" to Tool.LINE, "W" to Tool.WALL, "WALL" to Tool.WALL,
    "REC" to Tool.RECT, "RECT" to Tool.RECT, "RECTANG" to Tool.RECT, "C" to Tool.CIRCLE, "CIRCLE" to Tool.CIRCLE,
    "DOOR" to Tool.DOOR, "DR" to Tool.DOOR, "WINDOW" to Tool.WINDOW, "WIN" to Tool.WINDOW,
    "ROOM" to Tool.ROOM, "RM" to Tool.ROOM, "FURN" to Tool.FURN, "F" to Tool.FURN,
    "DIM" to Tool.DIM, "DLI" to Tool.DIM, "T" to Tool.TEXT, "TEXT" to Tool.TEXT,
    "M" to Tool.MOVE, "MOVE" to Tool.MOVE, "CO" to Tool.COPY, "CP" to Tool.COPY, "COPY" to Tool.COPY,
    "RO" to Tool.ROTATE, "ROTATE" to Tool.ROTATE, "O" to Tool.OFFSET, "OFFSET" to Tool.OFFSET,
    "E" to Tool.ERASE, "ERASE" to Tool.ERASE, "SEL" to Tool.SELECT, "P" to Tool.PAN, "PAN" to Tool.PAN
)

// Warna indeks AutoCAD (ACI 1..7)
val ACI = listOf(0xFFFF4D4DL, 0xFFFFFF00L, 0xFF00E676L, 0xFF00E5FFL, 0xFF5C9DFFL, 0xFFFF4DFFL, 0xFFFFFFFFL)
fun aci(i: Int): Color = Color(ACI[((i - 1) % 7 + 7) % 7])

data class Layer(val id: Int, val name: String, val visible: Boolean = true, val locked: Boolean = false, val color: Int = 7)

fun defaultLayers() = listOf(
    Layer(1, "Dinding", color = 7), Layer(2, "Pintu & Jendela", color = 4), Layer(3, "Ruang", color = 3),
    Layer(4, "Furnitur", color = 2), Layer(5, "Anotasi", color = 6), Layer(6, "Garis", color = 5)
)

private fun legacyColor(id: Int) = when (id) { 1 -> 7; 2 -> 4; 3 -> 3; 4 -> 2; 5 -> 6; else -> 5 }

fun rotv(o: Offset, a: Float) = Offset(o.x * cos(a) - o.y * sin(a), o.x * sin(a) + o.y * cos(a))

/** Koordinat dalam METER. CIRCLE: a = pusat, b = titik di keliling. rot (radian) untuk RECT/ROOM/FURN. */
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
    fun isRect() = kind == Kind.RECT || kind == Kind.ROOM || kind == Kind.FURN
    fun isLine() = kind == Kind.LINE || kind == Kind.WALL || kind == Kind.DOOR || kind == Kind.WINDOW || kind == Kind.DIM
    fun corners(): List<Offset> {
        val c = center(); val hx = w() / 2f; val hy = h() / 2f
        return listOf(Offset(-hx, -hy), Offset(hx, -hy), Offset(hx, hy), Offset(-hx, hy)).map { c + rotv(it, rot) }
    }
}

fun El.moved(d: Offset): El = copy(a = a + d, b = b + d)

fun El.rotatedAbout(base: Offset, ang: Float): El {
    if (isRect()) {
        val c = center(); val nc = base + rotv(c - base, ang); val d = nc - c
        return copy(a = a + d, b = b + d, rot = rot + ang)
    }
    return copy(a = base + rotv(a - base, ang), b = base + rotv(b - base, ang))
}

fun offsetEl(e: El, side: Offset, d: Float): El? {
    if (e.isLine()) {
        val len = e.len(); if (len < 0.001f) return null
        val u = (e.b - e.a) / len; val n = Offset(-u.y, u.x)
        val dot = (side.x - e.a.x) * n.x + (side.y - e.a.y) * n.y
        val s = if (dot >= 0f) 1f else -1f
        val dv = n * (s * d)
        return e.copy(a = e.a + dv, b = e.b + dv)
    }
    if (e.kind == Kind.CIRCLE) {
        val r = e.len(); if (r < 0.001f) return null
        val outside = (side - e.a).getDistance() > r
        val nr = if (outside) r + d else r - d
        if (nr < 0.01f) return null
        val dir = (e.b - e.a) / r
        return e.copy(b = e.a + dir * nr)
    }
    if (e.isRect()) {
        val l = rotv(side - e.center(), -e.rot)
        val hx = e.w() / 2f; val hy = e.h() / 2f
        val inside = abs(l.x) < hx && abs(l.y) < hy
        val nx = if (inside) hx - d else hx + d
        val ny = if (inside) hy - d else hy + d
        if (nx < 0.01f || ny < 0.01f) return null
        val c = e.center()
        return e.copy(a = c - Offset(nx, ny), b = c + Offset(nx, ny))
    }
    return null
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

/** type: 0 ujung (endpoint), 1 titik tengah (midpoint), 2 pusat (center) */
class SnapInfo(val p: Offset, val type: Int)

class PlanState {
    val els = mutableStateListOf<El>()
    val layers = mutableStateListOf<Layer>().apply { addAll(defaultLayers()) }
    val selected = mutableStateListOf<Int>()
    val pts = mutableStateListOf<Offset>()
    val history = mutableStateListOf<String>()
    var active by mutableStateOf(1)
    var autoLayer by mutableStateOf(true)
    var tool by mutableStateOf(Tool.SELECT)
    var stage by mutableStateOf(0)
    var zoom by mutableStateOf(1f)
    var pan by mutableStateOf(Offset(60f, 60f))
    var rot by mutableStateOf(0f)
    var ortho by mutableStateOf(true)
    var gridSnap by mutableStateOf(true)
    var osnap by mutableStateOf(true)
    var showGrid by mutableStateOf(true)
    var blueprint by mutableStateOf(true)
    var furnType by mutableStateOf(FURNITURE[0])
    var title by mutableStateOf("Rumah Tipe 36")
    var msg by mutableStateOf<String?>(null)
    var cursor by mutableStateOf<Offset?>(null)
    var snapInfo by mutableStateOf<SnapInfo?>(null)
    var textReq by mutableStateOf<Offset?>(null)
    var offsetDist by mutableStateOf(0.15f)
    var offsetTarget: Int? = null
    var chainFirst: Offset? = null
    var lastTool: Tool? = null
    var viewW = 800f
    var viewH = 800f

    val sel: Int? get() = if (selected.size == 1) selected[0] else null

    private val undoS = ArrayDeque<List<El>>()
    private val redoS = ArrayDeque<List<El>>()
    private var nid = 1

    // ---------- tema ----------
    fun bg(): Color = if (blueprint) Color(0xFF174A8C) else Color(0xFF1E2329)
    fun gridMinor(): Color = if (blueprint) Color(0x1FFFFFFF) else Color(0x14FFFFFF)
    fun gridMajor(): Color = if (blueprint) Color(0x55FFFFFF) else Color(0x33FFFFFF)
    fun colorOf(e: El): Color = aci(layerOf(e.layer)?.color ?: 7)

    // ---------- kamera ----------
    fun scale() = 60f * zoom
    fun toScreen(p: Offset) = rotv(p * scale(), rot) + pan
    fun toWorld(o: Offset) = rotv(o - pan, -rot) / scale()

    fun transformAt(c: Offset, zf: Float, rd: Float, pd: Offset) {
        val w = toWorld(c)
        zoom = (zoom * zf).coerceIn(0.3f, 5f); rot += rd
        pan = c - rotv(w * scale(), rot) + pd
    }
    fun zoomAt(f: Float, c: Offset) = transformAt(c, f, 0f, Offset.Zero)

    fun zoomExtents() {
        if (els.isEmpty()) { zoom = 1f; rot = 0f; pan = Offset(60f, 60f); return }
        val ps: List<Offset> = els.flatMap { e ->
            if (e.isRect()) e.corners()
            else if (e.kind == Kind.CIRCLE) listOf(e.a - Offset(e.len(), e.len()), e.a + Offset(e.len(), e.len()))
            else listOf(e.a, e.b)
        }
        val minX = ps.minOf { it.x }; val maxX = ps.maxOf { it.x }
        val minY = ps.minOf { it.y }; val maxY = ps.maxOf { it.y }
        val bw = max(maxX - minX, 1f); val bh = max(maxY - minY, 1f)
        zoom = (min(viewW / (bw * 60f), viewH / (bh * 60f)) * 0.85f).coerceIn(0.3f, 5f); rot = 0f
        val c = Offset((minX + maxX) / 2f, (minY + maxY) / 2f)
        pan = Offset(viewW / 2f, viewH / 2f) - c * scale()
    }

    // ---------- layer ----------
    fun layerOf(id: Int) = layers.firstOrNull { it.id == id }
    fun shown(e: El) = layerOf(e.layer)?.visible ?: true
    fun editable(e: El) = layerOf(e.layer)?.let { it.visible && !it.locked } ?: true
    private fun defaultLayer(k: Kind) = when (k) {
        Kind.WALL -> 1; Kind.DOOR, Kind.WINDOW -> 2; Kind.ROOM -> 3; Kind.FURN -> 4
        Kind.DIM, Kind.TEXT -> 5; else -> 6
    }
    fun layerFor(k: Kind): Int { val d = defaultLayer(k); return if (autoLayer && layerOf(d) != null) d else active }
    private fun canUse(id: Int): Boolean {
        val l = layerOf(id)
        if (l != null && (!l.visible || l.locked)) { msg = "Layer \"${l.name}\" tersembunyi atau terkunci"; return false }
        return true
    }
    fun setLayer(l: Layer) {
        val i = layers.indexOfFirst { it.id == l.id }; if (i >= 0) layers[i] = l
        selected.removeAll { id -> els.firstOrNull { it.id == id }?.let { e -> !shown(e) } == true }
    }
    fun addLayer(n: String) {
        if (n.isBlank()) return
        val id = (layers.maxOfOrNull { it.id } ?: 0) + 1
        layers.add(Layer(id, n.trim(), color = (id % 7) + 1)); active = id; autoLayer = false
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
        els.clear(); els.addAll(s); selected.clear()
    }
    fun redo() {
        if (redoS.isEmpty()) return
        undoS.addLast(els.toList()); val s = redoS.removeLast()
        els.clear(); els.addAll(s); selected.clear()
    }

    fun log(s: String) { history.add(s); while (history.size > 40) history.removeAt(0) }

    // ---------- seleksi ----------
    fun selEl(): El? = sel?.let { id -> els.firstOrNull { it.id == id } }
    fun selectedEls(): List<El> = selected.mapNotNull { id -> els.firstOrNull { it.id == id } }
    fun selectOnly(id: Int) { selected.clear(); selected.add(id) }
    fun toggleSelect(id: Int) { if (!selected.remove(id)) selected.add(id) }

    fun update(id: Int, f: (El) -> El) {
        val i = els.indexOfFirst { it.id == id }
        if (i >= 0) els[i] = f(els[i])
    }
    fun transformSel(f: (El) -> El) {
        for (id in selected.toList()) {
            val i = els.indexOfFirst { it.id == id }
            if (i >= 0 && editable(els[i])) els[i] = f(els[i])
        }
    }

    fun hit(p: Offset, tol: Float): Int? {
        fun test(e: El): Boolean = when {
            e.kind == Kind.RECT -> {
                val l = rotv(p - e.center(), -e.rot); val hx = e.w() / 2f; val hy = e.h() / 2f
                abs(l.x) <= hx + tol && abs(l.y) <= hy + tol && (abs(l.x) >= hx - tol || abs(l.y) >= hy - tol)
            }
            e.isRect() -> { val l = rotv(p - e.center(), -e.rot); abs(l.x) <= e.w() / 2f + tol && abs(l.y) <= e.h() / 2f + tol }
            e.kind == Kind.CIRCLE -> abs((p - e.a).getDistance() - e.len()) < tol
            e.kind == Kind.TEXT -> (p - e.a).getDistance() < tol * 3
            else -> distSeg(p, e.a, e.b) < tol
        }
        val c = els.filter { editable(it) }
        return (c.lastOrNull { it.kind != Kind.ROOM && test(it) } ?: c.lastOrNull { it.kind == Kind.ROOM && test(it) })?.id
    }

    fun screenBox(e: El): Rect {
        val ps: List<Offset> = when {
            e.isRect() -> e.corners().map { toScreen(it) }
            e.kind == Kind.CIRCLE -> { val c = toScreen(e.a); val r = e.len() * scale(); listOf(c - Offset(r, r), c + Offset(r, r)) }
            else -> listOf(toScreen(e.a), toScreen(e.b))
        }
        return Rect(ps.minOf { it.x }, ps.minOf { it.y }, ps.maxOf { it.x }, ps.maxOf { it.y })
    }

    /** Seleksi jendela (kiri ke kanan) atau persilangan (kanan ke kiri), koordinat layar. */
    fun windowSelect(a: Offset, b: Offset) {
        val r = Rect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
        if (r.width < 6f && r.height < 6f) return
        val crossing = b.x < a.x
        val ids = els.filter { editable(it) }.filter { e ->
            val bx = screenBox(e)
            if (crossing) r.overlaps(bx) else (bx.left >= r.left && bx.right <= r.right && bx.top >= r.top && bx.bottom <= r.bottom)
        }.map { it.id }
        for (id in ids) if (!selected.contains(id)) selected.add(id)
    }

    fun tapSelect(wp: Offset, tol: Float) {
        val h = hit(wp, tol)
        if (tool == Tool.OFFSET && stage == 1) {
            if (h != null) { offsetTarget = h; selected.clear(); selected.add(h); stage = 2 }
            return
        }
        if (h == null) { selected.clear(); return }
        toggleSelect(h)
    }

    // ---------- snap / titik ----------
    private fun snapCands(e: El): List<SnapInfo> = when {
        e.isLine() -> listOf(SnapInfo(e.a, 0), SnapInfo(e.b, 0), SnapInfo(e.center(), 1))
        e.kind == Kind.CIRCLE -> listOf(SnapInfo(e.a, 2))
        e.isRect() -> e.corners().map { SnapInfo(it, 0) } + SnapInfo(e.center(), 2)
        else -> emptyList()
    }

    /** OSNAP -> ORTHO -> GRID SNAP */
    fun resolve(raw: Offset, exclude: Int? = null): Offset {
        snapInfo = null
        if (osnap) {
            var best: SnapInfo? = null
            var bd = 16f / scale()
            for (e in els) {
                if (!shown(e) || e.id == exclude) continue
                for (c in snapCands(e)) {
                    val d = (c.p - raw).getDistance()
                    if (d < bd) { bd = d; best = c }
                }
            }
            val b = best
            if (b != null) { snapInfo = b; return b.p }
        }
        var p = raw
        val last = pts.lastOrNull()
        if (ortho && last != null) {
            p = if (abs(p.x - last.x) >= abs(p.y - last.y)) Offset(p.x, last.y) else Offset(last.x, p.y)
        }
        if (gridSnap) p = Offset((p.x / 0.1f).roundToInt() * 0.1f, (p.y / 0.1f).roundToInt() * 0.1f)
        return p
    }

    fun hover(o: Offset) { cursor = resolve(toWorld(o)) }
    fun pickAt(o: Offset) { val p = resolve(toWorld(o)); cursor = p; onPick(p) }

    // ---------- perintah ----------
    fun needsPoint(): Boolean = when (tool) {
        Tool.SELECT, Tool.PAN, Tool.ERASE -> false
        Tool.MOVE, Tool.COPY, Tool.ROTATE -> stage >= 1
        Tool.OFFSET -> stage == 2
        else -> true
    }
    fun selecting(): Boolean = when (tool) {
        Tool.MOVE, Tool.COPY, Tool.ROTATE, Tool.ERASE -> stage == 0
        Tool.OFFSET -> stage == 1
        else -> false
    }

    fun startTool(t: Tool) {
        tool = t; pts.clear(); stage = 0; offsetTarget = null; chainFirst = null; snapInfo = null
        if (t != Tool.SELECT && t != Tool.PAN) { lastTool = t; log("Perintah: " + t.label.uppercase()) }
        when (t) {
            Tool.MOVE, Tool.COPY, Tool.ROTATE -> if (selected.isNotEmpty()) stage = 1
            Tool.ERASE -> if (selected.isNotEmpty()) { eraseSel(); tool = Tool.SELECT }
            Tool.OFFSET -> { selected.clear(); stage = 1 }
            else -> {}
        }
    }

    fun esc() {
        pts.clear(); stage = 0; offsetTarget = null; chainFirst = null; snapInfo = null
        selected.clear(); tool = Tool.SELECT; log("*Batal*")
    }

    fun enter() {
        when (tool) {
            Tool.LINE, Tool.WALL -> { pts.clear(); chainFirst = null }
            Tool.MOVE, Tool.COPY, Tool.ROTATE -> {
                if (stage == 0) { if (selected.isNotEmpty()) stage = 1 else log("Belum ada objek dipilih") }
                else if (tool == Tool.COPY && stage == 2) endModify()
            }
            Tool.ERASE -> { if (selected.isNotEmpty()) { eraseSel(); tool = Tool.SELECT } else log("Belum ada objek dipilih") }
            Tool.OFFSET -> endModify()
            Tool.SELECT -> { val lt = lastTool; if (lt != null) startTool(lt) }
            else -> pts.clear()
        }
    }

    fun submit(raw: String) {
        val t = raw.trim()
        if (t.isEmpty()) { enter(); return }
        log("> $t")
        val u = t.uppercase()
        if (u == "C" && (tool == Tool.LINE || tool == Tool.WALL) && pts.size >= 2) { closeChain(); return }
        if (u == "U" || u == "UNDO") { undo(); return }
        if (u == "REDO") { redo(); return }
        if (u == "ESC") { esc(); return }
        if (u == "Z" || u == "ZE") { zoomExtents(); return }
        if (typed(t)) return
        val tl = ALIASES[u]
        if (tl != null) { startTool(tl); return }
        log("Perintah tidak dikenal: $t")
    }

    private fun typed(t: String): Boolean {
        val num = t.toFloatOrNull() ?: t.replace(',', '.').toFloatOrNull()
        if (tool == Tool.OFFSET && num != null && num > 0f) { offsetDist = num; log("Jarak offset = $num"); return true }
        if (tool == Tool.ROTATE && stage == 2 && num != null && pts.isNotEmpty()) {
            applyRotate(pts[0], Math.toRadians(-num.toDouble()).toFloat()); return true
        }
        if (tool == Tool.CIRCLE && pts.size == 1 && num != null && num > 0f) {
            commit(El(-1, Kind.CIRCLE, pts[0], pts[0] + Offset(num, 0f))); pts.clear(); return true
        }
        if (needsPoint()) {
            val p = parsePoint(t)
            if (p != null) { cursor = p; onPick(p); return true }
        }
        return false
    }

    /** x,y | @x,y | jarak<sudut | @jarak<sudut | angka (jarak searah kursor). Sumbu Y ke atas, sudut berlawanan jarum jam. */
    private fun parsePoint(t: String): Offset? {
        val s = t.replace(" ", "")
        val rel = s.startsWith("@")
        val body = if (rel) s.substring(1) else s
        val last = pts.lastOrNull()
        if (body.contains('<')) {
            val parts = body.split('<')
            if (parts.size != 2) return null
            val d = parts[0].toFloatOrNull(); val a = parts[1].toFloatOrNull()
            if (d == null || a == null) return null
            val r = Math.toRadians(a.toDouble()).toFloat()
            return (last ?: Offset.Zero) + Offset(d * cos(r), -d * sin(r))
        }
        if (body.contains(',')) {
            val parts = body.split(',')
            if (parts.size != 2) return null
            val x = parts[0].toFloatOrNull(); val y = parts[1].toFloatOrNull()
            if (x == null || y == null) return null
            val base = if (rel) (last ?: Offset.Zero) else Offset.Zero
            return base + Offset(x, -y)
        }
        val d = body.toFloatOrNull()
        val c = cursor
        if (d == null || last == null || c == null) return null
        val v = c - last; val l = v.getDistance()
        if (l < 0.0001f) return null
        return last + v / l * d
    }

    fun promptText(): String {
        val n = tool.label.uppercase()
        return when (tool) {
            Tool.SELECT -> "Perintah: ketuk/seret untuk memilih, atau ketik perintah"
            Tool.PAN -> "KAMERA: geser satu jari, dua jari untuk zoom dan putar"
            Tool.LINE, Tool.WALL ->
                if (pts.isEmpty()) "$n Titik pertama:" else "$n Titik berikutnya [C=tutup, Enter=selesai]:"
            Tool.RECT, Tool.ROOM, Tool.FURN -> if (pts.isEmpty()) "$n Sudut pertama:" else "$n Sudut berlawanan:"
            Tool.CIRCLE -> if (pts.isEmpty()) "$n Titik pusat:" else "$n Titik radius atau ketik radius:"
            Tool.DOOR, Tool.WINDOW -> if (pts.isEmpty()) "$n Titik awal:" else "$n Titik akhir:"
            Tool.DIM -> if (pts.isEmpty()) "$n Titik ukur pertama:" else "$n Titik ukur kedua:"
            Tool.TEXT -> "$n Ketuk lokasi teks:"
            Tool.MOVE, Tool.COPY, Tool.ROTATE -> when (stage) {
                0 -> "$n Pilih objek lalu Enter:"
                1 -> "$n Titik dasar:"
                else -> if (tool == Tool.MOVE) "$n Titik tujuan:" else if (tool == Tool.COPY) "$n Titik tujuan [Enter=selesai]:" else "$n Titik sudut atau ketik derajat:"
            }
            Tool.OFFSET -> if (stage == 1) "$n Jarak <%.2f> (ketik angka), pilih objek:".format(offsetDist) else "$n Ketuk sisi offset:"
            Tool.ERASE -> "$n Pilih objek lalu Enter:"
        }
    }

    fun onPick(p: Offset) {
        when (tool) {
            Tool.LINE, Tool.WALL -> {
                val k = tool.kind() ?: return
                if (pts.isEmpty()) { pts.add(p); chainFirst = p }
                else if (commit(El(-1, k, pts.last(), p))) pts.add(p)
            }
            Tool.DIM, Tool.DOOR, Tool.WINDOW -> {
                val k = tool.kind() ?: return
                if (pts.isEmpty()) pts.add(p) else { commit(El(-1, k, pts[0], p)); pts.clear() }
            }
            Tool.RECT, Tool.ROOM, Tool.FURN -> {
                val k = tool.kind() ?: return
                if (pts.isEmpty()) pts.add(p)
                else {
                    val lbl = if (k == Kind.ROOM) "Ruang" else if (k == Kind.FURN) furnType else ""
                    commit(El(-1, k, pts[0], p, lbl)); pts.clear()
                }
            }
            Tool.CIRCLE -> if (pts.isEmpty()) pts.add(p) else { commit(El(-1, Kind.CIRCLE, pts[0], p)); pts.clear() }
            Tool.TEXT -> textReq = p
            Tool.MOVE, Tool.COPY, Tool.ROTATE -> modifyPick(p)
            Tool.OFFSET -> offsetPick(p)
            else -> {}
        }
    }

    private fun modifyPick(p: Offset) {
        if (stage == 1) { pts.clear(); pts.add(p); stage = 2; return }
        if (stage != 2 || pts.isEmpty()) return
        val base = pts[0]
        when (tool) {
            Tool.MOVE -> { push(); val d = p - base; transformSel { it.moved(d) }; endModify() }
            Tool.COPY -> {
                val d = p - base; push()
                val news = selectedEls().map { it.moved(d).copy(id = nid++) }
                els.addAll(news)
            }
            Tool.ROTATE -> applyRotate(base, atan2(p.y - base.y, p.x - base.x))
            else -> {}
        }
    }

    private fun applyRotate(base: Offset, ang: Float) { push(); transformSel { it.rotatedAbout(base, ang) }; endModify() }

    private fun offsetPick(p: Offset) {
        val t = els.firstOrNull { it.id == offsetTarget }
        if (t != null) {
            val n = offsetEl(t, p, offsetDist)
            if (n != null) { push(); els.add(n.copy(id = nid++)) } else log("Offset tidak dapat dibuat")
        }
        offsetTarget = null; selected.clear(); stage = 1
    }

    private fun endModify() { stage = 0; pts.clear(); tool = Tool.SELECT }

    fun eraseSel() {
        if (selected.isEmpty()) return
        push(); val ids = selected.toList()
        els.removeAll { ids.contains(it.id) && editable(it) }
        selected.clear(); log("${ids.size} objek dihapus")
    }

    fun closeChain() {
        val c = pts.lastOrNull(); val f = chainFirst
        if (c != null && f != null && (c - f).getDistance() > 0.05f) { val k = tool.kind(); if (k != null) commit(El(-1, k, c, f)) }
        pts.clear(); chainFirst = null
    }

    /** Objek bayangan saat memilih titik (rubber band). */
    fun preview(): List<El> {
        val c = cursor ?: return emptyList()
        val k = tool.kind()
        if (k != null && pts.isNotEmpty()) {
            val ly = layerFor(k)
            return when (tool) {
                Tool.LINE, Tool.WALL, Tool.DIM, Tool.DOOR, Tool.WINDOW -> listOf(El(-1, k, pts.last(), c, layer = ly))
                Tool.RECT, Tool.ROOM, Tool.FURN, Tool.CIRCLE -> listOf(El(-1, k, pts[0], c, layer = ly))
                else -> emptyList()
            }
        }
        if (stage == 2 && pts.isNotEmpty()) {
            val base = pts[0]
            if (tool == Tool.MOVE || tool == Tool.COPY) return selectedEls().map { it.moved(c - base) }
            if (tool == Tool.ROTATE) return selectedEls().map { it.rotatedAbout(base, atan2(c.y - base.y, c.x - base.x)) }
            if (tool == Tool.OFFSET) {
                val t = els.firstOrNull { it.id == offsetTarget }
                val n = if (t != null) offsetEl(t, c, offsetDist) else null
                return if (n != null) listOf(n) else emptyList()
            }
        }
        return emptyList()
    }

    // ---------- elemen ----------
    fun commit(e: El): Boolean {
        val ok = if (e.isRect()) e.area() > 0.04f else e.len() > 0.05f
        if (!ok) return false
        val ly = layerFor(e.kind)
        if (!canUse(ly)) return false
        push()
        var lbl = e.label
        if (e.kind == Kind.DOOR) lbl = "P" + (els.count { it.kind == Kind.DOOR } + 1)
        if (e.kind == Kind.WINDOW) lbl = "J" + (els.count { it.kind == Kind.WINDOW } + 1)
        els.add(e.copy(id = nid++, label = lbl, layer = ly))
        return true
    }

    fun addText(p: Offset, t: String) {
        if (t.isBlank()) return
        val ly = layerFor(Kind.TEXT); if (!canUse(ly)) return
        push(); els.add(El(nid++, Kind.TEXT, p, p, t, layer = ly))
    }

    fun delete(id: Int) { push(); els.removeAll { it.id == id }; selected.remove(id) }
    fun duplicate(id: Int) {
        val e = els.firstOrNull { it.id == id } ?: return
        push(); val n = nid++; val d = Offset(0.5f, 0.5f)
        els.add(e.copy(id = n, a = e.a + d, b = e.b + d)); selectOnly(n)
    }

    fun rotateSel(id: Int, deg: Float) {
        val e = els.firstOrNull { it.id == id } ?: return
        push(); val d = Math.toRadians(deg.toDouble()).toFloat()
        if (e.isRect()) update(id) { it.copy(rot = it.rot + d) }
        else if (e.isLine()) { val m = e.center(); update(id) { it.copy(a = m + rotv(it.a - m, d), b = m + rotv(it.b - m, d)) } }
    }

    /** Geser satu ujung; ujung garis/dinding sejenis yang menyambung ikut berpindah. */
    fun moveEnd(id: Int, isA: Boolean, np: Offset) {
        val e = els.firstOrNull { it.id == id } ?: return
        val old = if (isA) e.a else e.b
        update(id) { if (isA) it.copy(a = np) else it.copy(b = np) }
        if (e.kind != Kind.WALL && e.kind != Kind.LINE) return
        for (i in els.indices) {
            val o = els[i]
            if (o.id == id || o.kind != e.kind || !editable(o)) continue
            var n = o
            if ((o.a - old).getDistance() < 0.01f) n = n.copy(a = np)
            if ((o.b - old).getDistance() < 0.01f) n = n.copy(b = np)
            if (n != o) els[i] = n
        }
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
        layers.forEach { la.put(JSONObject().put("id", it.id).put("n", it.name).put("v", it.visible).put("lk", it.locked).put("c", it.color)) }
        return JSONObject().put("title", title).put("layers", la).put("active", active)
            .put("auto", autoLayer).put("els", arr).toString()
    }

    fun fromJson(s: String) {
        val o = JSONObject(s); title = o.optString("title", title)
        layers.clear()
        val la = o.optJSONArray("layers")
        if (la != null && la.length() > 0) {
            for (i in 0 until la.length()) {
                val j = la.getJSONObject(i); val id = j.getInt("id")
                layers.add(Layer(id, j.getString("n"), j.optBoolean("v", true), j.optBoolean("lk", false), j.optInt("c", legacyColor(id))))
            }
        } else layers.addAll(defaultLayers())
        active = o.optInt("active", layers.first().id); autoLayer = o.optBoolean("auto", true)
        val arr = o.getJSONArray("els"); els.clear(); undoS.clear(); redoS.clear()
        selected.clear(); pts.clear(); stage = 0; tool = Tool.SELECT
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
