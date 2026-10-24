package com.arsiplan.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { App() } }
    }
}

private val Accent = Color(0xFFE65100)
private val AccentBg = Color(0xFFFFE0B2)
private val BlueprintBg = Color(0xFF174A8C)

private fun kindOf(t: Tool): Kind? = when (t) {
    Tool.WALL -> Kind.WALL; Tool.DOOR -> Kind.DOOR; Tool.WINDOW -> Kind.WINDOW
    Tool.ROOM -> Kind.ROOM; Tool.FURN -> Kind.FURN; Tool.DIM -> Kind.DIM; else -> null
}

@Composable
fun IconBtn(icon: ImageVector, desc: String, tint: Color = Color(0xFF37474F), onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(40.dp)) { Icon(icon, desc, Modifier.size(22.dp), tint = tint) }
}

private fun hint(st: PlanState): String {
    val base = when (st.tool) {
        Tool.PAN -> "Kamera: seret untuk geser"
        Tool.SELECT -> "Ketuk pilih, seret pindah, tarik ujung garis untuk menyambung"
        Tool.TEXT -> "Ketuk kanvas untuk menambah teks"
        Tool.WALL -> if (st.chain) "Dinding sambung: seret atau ketuk titik berikutnya" else "Dinding: seret untuk menggambar"
        else -> st.tool.label + ": seret untuk menggambar"
    }
    val ly = if (st.autoLayer) "Layer: otomatis" else "Layer aktif: " + (st.layerOf(st.active)?.name ?: "-")
    return base + "\nDua jari: geser, zoom, putar (semua mode)\n" + ly
}

@Composable
fun App() {
    val st = remember { PlanState() }
    val ctx = LocalContext.current
    var showReport by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var textAt by remember { mutableStateOf<Offset?>(null) }
    var textVal by remember { mutableStateOf("") }
    var center by remember { mutableStateOf(Offset(300f, 300f)) }

    LaunchedEffect(st.msg) { st.msg?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); st.msg = null } }

    Column(Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(st.title, { st.title = it }, singleLine = true, modifier = Modifier.weight(1f),
                textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF263238)))
            IconBtn(Ic.Undo, "Undo") { st.undo() }
            IconBtn(Ic.Redo, "Redo") { st.redo() }
            IconBtn(Ic.Grid, "Snap", if (st.snap) Accent else Color(0xFF90A4AE)) { st.snap = !st.snap }
            IconBtn(Ic.Layers, "Layer") { showLayers = true }
            Box {
                IconBtn(Ic.Menu, "Menu") { menu = true }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Laporan & keterangan") }, { menu = false; showReport = true },
                        leadingIcon = { Icon(Ic.Report, null, Modifier.size(20.dp)) })
                    DropdownMenuItem({ Text("Simpan proyek") }, {
                        menu = false
                        ctx.openFileOutput("denah.json", 0).use { it.write(st.toJson().toByteArray()) }
                        Toast.makeText(ctx, "Tersimpan", Toast.LENGTH_SHORT).show()
                    }, leadingIcon = { Icon(Ic.Save, null, Modifier.size(20.dp)) })
                    DropdownMenuItem({ Text("Buka proyek") }, {
                        menu = false
                        try { st.fromJson(ctx.openFileInput("denah.json").bufferedReader().readText()) }
                        catch (e: Exception) { Toast.makeText(ctx, "Belum ada file", Toast.LENGTH_SHORT).show() }
                    }, leadingIcon = { Icon(Ic.Open, null, Modifier.size(20.dp)) })
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { center = Offset(it.width / 2f, it.height / 2f) }) {
            PlanCanvas(st, { textAt = it; textVal = "" }, Modifier.fillMaxSize())
            Surface(Modifier.align(Alignment.TopStart).padding(8.dp), shape = RoundedCornerShape(8.dp),
                color = Color(0xE6FFFFFF), shadowElevation = 2.dp) {
                Text(hint(st), Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 11.sp, color = Color(0xFF455A64))
            }
            Surface(Modifier.align(Alignment.BottomEnd).padding(8.dp), shape = RoundedCornerShape(12.dp),
                color = Color(0xE6FFFFFF), shadowElevation = 2.dp) {
                Column {
                    IconBtn(Ic.ZoomIn, "Perbesar") { st.zoomAt(1.25f, center) }
                    IconBtn(Ic.ZoomOut, "Perkecil") { st.zoomAt(0.8f, center) }
                    IconBtn(Ic.Home, "Reset tampilan") { st.zoom = 1f; st.pan = Offset(60f, 60f); st.rot = 0f }
                }
            }
        }
        if (st.tool == Tool.WALL) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                FilterChip(st.chain, { st.chain = !st.chain; st.endChain() }, label = { Text("Sambung", fontSize = 12.sp) },
                    leadingIcon = { Icon(Ic.Link, null, Modifier.size(16.dp)) }, modifier = Modifier.padding(horizontal = 2.dp))
                if (st.chain) {
                    AssistChip({ st.endChain() }, label = { Text("Selesai", fontSize = 12.sp) }, modifier = Modifier.padding(horizontal = 2.dp))
                    AssistChip({ st.closeChain() }, label = { Text("Tutup bentuk", fontSize = 12.sp) }, modifier = Modifier.padding(horizontal = 2.dp))
                }
            }
        }
        if (st.tool == Tool.FURN) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp)) {
                FURNITURE.forEach { f ->
                    FilterChip(st.furnType == f, { st.furnType = f }, label = { Text(f, fontSize = 12.sp) },
                        modifier = Modifier.padding(horizontal = 2.dp))
                }
            }
        }
        st.sel?.let { id -> st.els.firstOrNull { it.id == id }?.let { Panel(st, it) } }
        Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp)) {
                Tool.values().forEach { t ->
                    val on = st.tool == t
                    Column(
                        Modifier.weight(1f).padding(horizontal = 1.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (on) AccentBg else Color.Transparent)
                            .clickable { st.tool = if (on && t != Tool.PAN) Tool.PAN else t; st.sel = null; st.endChain() }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(t.icon(), t.label, Modifier.size(22.dp), tint = if (on) Accent else Color(0xFF607D8B))
                        Text(t.label, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Clip,
                            color = if (on) Accent else Color(0xFF607D8B), fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }

    if (showLayers) LayerDialog(st) { showLayers = false }
    if (showReport) {
        val rep = st.report()
        AlertDialog(onDismissRequest = { showReport = false },
            title = { Text("Laporan & Keterangan") },
            text = { Text(rep, Modifier.verticalScroll(rememberScrollState()), fontSize = 13.sp) },
            confirmButton = {
                TextButton({
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, rep)
                    ctx.startActivity(Intent.createChooser(i, "Bagikan"))
                }) { Text("Bagikan") }
            },
            dismissButton = { TextButton({ showReport = false }) { Text("Tutup") } })
    }
    textAt?.let { p ->
        AlertDialog(onDismissRequest = { textAt = null }, title = { Text("Teks / catatan") },
            text = { OutlinedTextField(textVal, { textVal = it }) },
            confirmButton = { TextButton({ st.addText(p, textVal); textAt = null }) { Text("Tambah") } },
            dismissButton = { TextButton({ textAt = null }) { Text("Batal") } })
    }
}

@Composable
fun LayerDialog(st: PlanState, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text("Layer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Otomatis per jenis objek", Modifier.weight(1f), fontSize = 13.sp)
                    Switch(st.autoLayer, { st.autoLayer = it })
                }
                Text("Ketuk layer untuk menjadikannya aktif (dipakai saat otomatis mati).", fontSize = 11.sp, color = Color(0xFF78909C))
                st.layers.forEach { l ->
                    val on = st.active == l.id
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (on && !st.autoLayer) AccentBg else Color.Transparent)
                        .clickable { st.active = l.id; st.autoLayer = false }.padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(l.name + " (" + st.els.count { it.layer == l.id } + ")", Modifier.weight(1f), fontSize = 14.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                        IconBtn(if (l.visible) Ic.Eye else Ic.EyeOff, "Tampil") { st.setLayer(l.copy(visible = !l.visible)) }
                        IconBtn(if (l.locked) Ic.Lock else Ic.Unlock, "Kunci") { st.setLayer(l.copy(locked = !l.locked)) }
                        IconBtn(Ic.Trash, "Hapus", Color(0xFFD32F2F)) { st.deleteLayer(l.id) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Layer baru") }, modifier = Modifier.weight(1f))
                    IconBtn(Ic.Plus, "Tambah") { st.addLayer(name); name = "" }
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Selesai") } })
}

@Composable
fun Panel(st: PlanState, e: El) {
    Card(Modifier.fillMaxWidth().heightIn(max = 250.dp).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 10.dp, end = 4.dp, bottom = 6.dp)) {
            val info = if (e.kind == Kind.DIM) " | %.2f m".format(e.len()) else ""
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.kind.label + info, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                IconBtn(Ic.Copy, "Duplikat") { st.duplicate(e.id) }
                IconBtn(Ic.Trash, "Hapus", Color(0xFFD32F2F)) { st.delete(e.id) }
                IconBtn(Ic.Close, "Tutup") { st.sel = null }
            }
            OutlinedTextField(e.label, { v -> st.update(e.id) { it.copy(label = v) } },
                label = { Text("Nama / label") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(end = 6.dp))
            if (e.kind != Kind.TEXT && e.kind != Kind.DIM)
                OutlinedTextField(e.note, { v -> st.update(e.id) { it.copy(note = v) } }, maxLines = 2,
                    label = { Text("Keterangan (bahan, finishing, spesifikasi)") },
                    modifier = Modifier.fillMaxWidth().padding(end = 6.dp, top = 4.dp))
            if (e.kind == Kind.WALL) {
                Text("Tebal dinding: ${(e.thick * 100).roundToInt()} cm", fontSize = 12.sp)
                Slider(e.thick, { v -> st.update(e.id) { it.copy(thick = v) } }, Modifier.padding(end = 6.dp), valueRange = 0.08f..0.4f)
            }
            if (e.kind != Kind.TEXT) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Putar", Modifier.padding(end = 2.dp), fontSize = 12.sp)
                    TextButton({ st.rotateSel(e.id, -90f) }) { Text("-90") }
                    IconBtn(Ic.RotL, "Putar kiri 15") { st.rotateSel(e.id, -15f) }
                    IconBtn(Ic.RotR, "Putar kanan 15") { st.rotateSel(e.id, 15f) }
                    TextButton({ st.rotateSel(e.id, 90f) }) { Text("+90") }
                    if (e.isRect()) Text("${Math.toDegrees(e.rot.toDouble()).roundToInt() % 360} derajat", fontSize = 11.sp)
                }
            }
            Text("Layer", fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                st.layers.forEach { l ->
                    FilterChip(e.layer == l.id, { st.update(e.id) { it.copy(layer = l.id) } },
                        label = { Text(l.name, fontSize = 11.sp) }, modifier = Modifier.padding(end = 4.dp))
                }
            }
        }
    }
}

@Composable
fun PlanCanvas(st: PlanState, onText: (Offset) -> Unit, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    var draft by remember { mutableStateOf<El?>(null) }
    var start by remember { mutableStateOf(Offset.Zero) }
    var mode by remember { mutableStateOf(0) } // 1 pindah, 2 ujung A, 3 ujung B, 4 kamera

    fun begin(o: Offset) {
        val wp = st.toWorld(o)
        mode = 0
        when (st.tool) {
            Tool.PAN -> mode = 4
            Tool.SELECT -> {
                val tol = 18f / st.scale()
                val s = st.selEl()
                if (s != null && s.isLine() && st.editable(s)) {
                    if ((s.a - wp).getDistance() < tol) mode = 2 else if ((s.b - wp).getDistance() < tol) mode = 3
                    if (mode != 0) { st.push(); return }
                }
                st.sel = st.hit(wp, 14f / st.scale())
                if (st.sel != null) { st.push(); mode = 1 }
            }
            Tool.TEXT -> {}
            else -> {
                val k = kindOf(st.tool) ?: return
                start = if (k == Kind.WALL && st.chain && st.chainPt != null) st.chainPt!! else st.snapPt(wp)
                draft = El(-1, k, start, start, if (k == Kind.ROOM) "Ruang" else if (k == Kind.FURN) st.furnType else "")
            }
        }
    }

    fun drag(pos: Offset, d: Offset) {
        when (mode) {
            4 -> st.pan = st.pan + d
            1 -> st.sel?.let { id ->
                val dw = rotv(d, -st.rot) / st.scale()
                st.update(id) { it.copy(a = it.a + dw, b = it.b + dw) }
            }
            2, 3 -> st.sel?.let { id -> st.moveEnd(id, mode == 2, st.snapPt(st.toWorld(pos), id)) }
            else -> draft?.let { dr -> draft = dr.copy(b = st.constrain(start, st.snapPt(st.toWorld(pos)), dr.kind)) }
        }
    }

    fun finish() {
        val d = draft
        if (d != null) {
            if (d.kind == Kind.WALL && st.chain) {
                val first = st.chainPt == null
                val ok = st.commit(d)
                if (ok) {
                    if (first) st.chainFirst = d.a
                    st.chainPt = d.b
                }
            } else {
                st.commit(d)
            }
        }
        draft = null; mode = 0
    }

    fun tap(o: Offset) {
        val wp = st.toWorld(o)
        when (st.tool) {
            Tool.SELECT -> st.sel = st.hit(wp, 14f / st.scale())
            Tool.TEXT -> onText(st.snapPt(wp))
            Tool.WALL -> if (st.chain) {
                val p = st.snapPt(wp); val cp = st.chainPt
                if (cp == null) { st.chainPt = p; st.chainFirst = p }
                else {
                    val e = st.constrain(cp, p, Kind.WALL)
                    if (st.commit(El(-1, Kind.WALL, cp, e))) st.chainPt = e
                }
            }
            else -> {}
        }
    }

    Canvas(
        modifier.background(BlueprintBg).pointerInput(Unit) {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var multi = false
                var dragging = false
                while (true) {
                    val ev = awaitPointerEvent()
                    val pressed = ev.changes.count { it.pressed }
                    if (pressed >= 2) {
                        // dua jari: geser + zoom + putar kamera, aktif di SEMUA mode
                        if (!multi) { multi = true; if (dragging) { draft = null; mode = 0; dragging = false } }
                        val z = ev.calculateZoom(); val r = ev.calculateRotation(); val pn = ev.calculatePan()
                        if (z != 1f || r != 0f || pn != Offset.Zero)
                            st.transformAt(ev.calculateCentroid(useCurrent = false), z, Math.toRadians(r.toDouble()).toFloat(), pn)
                        ev.changes.forEach { if (it.positionChanged()) it.consume() }
                    } else if (!multi) {
                        val ch = ev.changes.first()
                        if (!ch.pressed) { if (dragging) finish() else tap(down.position); break }
                        if (!dragging && (ch.position - down.position).getDistance() > slop) { dragging = true; begin(down.position) }
                        if (dragging) { drag(ch.position, ch.position - ch.previousPosition); ch.consume() }
                    }
                    if (pressed == 0) break
                }
            }
        }
    ) {
        val s = st.scale()
        val cs = listOf(Offset(0f, 0f), Offset(size.width, 0f), Offset(size.width, size.height), Offset(0f, size.height)).map { st.toWorld(it) }
        val minX = cs.minOf { it.x }; val maxX = cs.maxOf { it.x }; val minY = cs.minOf { it.y }; val maxY = cs.maxOf { it.y }
        val step = if (s >= 40f) 0.5f else 1f
        var x = floor(minX / step) * step
        while (x <= maxX) {
            val major = abs(x - x.roundToInt()) < 0.001f
            drawLine(if (major) Color(0x55FFFFFF) else Color(0x1FFFFFFF), st.toScreen(Offset(x, minY)), st.toScreen(Offset(x, maxY)), 1f)
            x += step
        }
        var y = floor(minY / step) * step
        while (y <= maxY) {
            val major = abs(y - y.roundToInt()) < 0.001f
            drawLine(if (major) Color(0x55FFFFFF) else Color(0x1FFFFFFF), st.toScreen(Offset(minX, y)), st.toScreen(Offset(maxX, y)), 1f)
            y += step
        }
        val order = listOf(Kind.ROOM, Kind.FURN, Kind.WALL, Kind.WINDOW, Kind.DOOR, Kind.DIM, Kind.TEXT)
        st.els.filter { st.shown(it) }
            .sortedWith(compareBy({ e -> st.layers.indexOfFirst { it.id == e.layer } }, { e -> order.indexOf(e.kind) }))
            .forEach { drawEl(it, it.id == st.sel, st, tm) }
        draft?.let { drawEl(it, false, st, tm) }
        st.chainPt?.let { val p = st.toScreen(it); drawCircle(Accent, 10f, p); drawCircle(Color.White, 4f, p) }
        // kompas (arah utara denah)
        val cc = Offset(size.width - 34f, 34f); val dir = rotv(Offset(0f, -1f), st.rot)
        drawCircle(Color(0x33FFFFFF), 20f, cc)
        drawLine(Color(0xFFFF8A80), cc, cc + dir * 15f, 3f, StrokeCap.Round)
        drawLine(Color(0xCCFFFFFF), cc, cc - dir * 10f, 3f, StrokeCap.Round)
        txt(tm, "U", cc + dir * 30f, Color(0xFFFF8A80), 10f)
    }
}

private fun DrawScope.txt(tm: TextMeasurer, t: String, c: Offset, col: Color = Color.Black, sp: Float = 12f) {
    val r = tm.measure(t, TextStyle(fontSize = sp.sp, color = col, textAlign = TextAlign.Center))
    drawText(r, topLeft = c - Offset(r.size.width / 2f, r.size.height / 2f))
}

private fun DrawScope.drawEl(e: El, sel: Boolean, st: PlanState, tm: TextMeasurer) {
    val s = st.scale(); val a = st.toScreen(e.a); val b = st.toScreen(e.b)
    val hi = Color(0xFFFFC107)
    val ink = Color.White; val soft = Color(0xFFB3E5FC); val cyan = Color(0xFF4DD0E1)
    val len = (b - a).getDistance()
    val ang = atan2(b.y - a.y, b.x - a.x)
    val nrm = if (len > 0f) Offset(-(b.y - a.y), b.x - a.x) / len else Offset.Zero
    when (e.kind) {
        Kind.ROOM, Kind.FURN -> {
            val pts = e.corners().map { st.toScreen(it) }
            val path = Path().apply { moveTo(pts[0].x, pts[0].y); for (i in 1..3) lineTo(pts[i].x, pts[i].y); close() }
            val room = e.kind == Kind.ROOM
            drawPath(path, if (room) Color(0x22FFFFFF) else Color(0x14FFFFFF))
            drawPath(path, if (sel) hi else soft, style = Stroke(if (sel) 4f else if (room) 2f else 1.5f))
            txt(tm, e.label, st.toScreen(e.center()), if (sel) hi else ink, if (room) 13f else 10f)
        }
        Kind.WALL -> drawLine(if (sel) hi else ink, a, b, max(e.thick * s, 4f), StrokeCap.Square)
        Kind.DOOR -> {
            val col = if (sel) hi else cyan
            drawLine(BlueprintBg, a, b, max(0.18f * s, 6f))
            val leaf = Offset(a.x + len * cos(ang - (PI / 2).toFloat()), a.y + len * sin(ang - (PI / 2).toFloat()))
            drawLine(col, a, leaf, 3f)
            drawArc(col, Math.toDegrees(ang.toDouble()).toFloat() - 90f, 90f, false,
                Offset(a.x - len, a.y - len), Size(2 * len, 2 * len), style = Stroke(1.5f))
            txt(tm, e.label, (a + b) / 2f - nrm * 14f, col, 10f)
        }
        Kind.WINDOW -> {
            val col = if (sel) hi else cyan
            drawLine(BlueprintBg, a, b, max(0.18f * s, 6f))
            drawLine(col, a + nrm * 4f, b + nrm * 4f, 1.5f); drawLine(col, a, b, 2f); drawLine(col, a - nrm * 4f, b - nrm * 4f, 1.5f)
            txt(tm, e.label, (a + b) / 2f - nrm * 14f, col, 10f)
        }
        Kind.DIM -> { // satu-satunya ukuran yang tampil: hasil pengukuran manual
            val col = if (sel) hi else Color(0xFFFFEE58)
            drawLine(col, a, b, 1.5f); drawLine(col, a - nrm * 7f, a + nrm * 7f, 1.5f); drawLine(col, b - nrm * 7f, b + nrm * 7f, 1.5f)
            txt(tm, "%.2f m".format(e.len()), (a + b) / 2f + nrm * 12f, col, 11f)
        }
        Kind.TEXT -> txt(tm, e.label, a, if (sel) hi else ink, 13f)
    }
    if (sel && e.isLine()) { drawCircle(hi, 8f, a); drawCircle(hi, 8f, b) }
}
