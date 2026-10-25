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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

private val UiDark = Color(0xFF1F2328)
private val UiRibbon = Color(0xFF2E3238)
private val UiLine = Color(0xFF454B52)
private val UiText = Color(0xFFE6E6E6)
private val UiDim = Color(0xFF9AA3AD)
private val Accent = Color(0xFF3D9BFF)
private val Dash = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(color = UiDark, contentColor = UiText) { App() }
            }
        }
    }
}

@Composable
fun IconBtn(icon: ImageVector, desc: String, tint: Color = UiText, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(40.dp)) { Icon(icon, desc, Modifier.size(22.dp), tint = tint) }
}

@Composable
fun RibbonBtn(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(58.dp).padding(2.dp).clip(RoundedCornerShape(6.dp))
            .background(if (on) Accent.copy(alpha = 0.35f) else Color.Transparent)
            .clickable { onClick() }.padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, Modifier.size(24.dp), tint = if (on) Color.White else UiText)
        Text(label, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (on) Color.White else UiDim)
    }
}

@Composable
fun RibbonGroup(name: String, content: @Composable RowScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(content = content)
        Text(name, fontSize = 9.sp, color = UiDim)
    }
    Box(Modifier.width(1.dp).height(58.dp).background(UiLine))
}

@Composable
fun StatusToggle(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        Modifier.padding(horizontal = 2.dp).clip(RoundedCornerShape(4.dp))
            .background(if (on) Accent.copy(alpha = 0.45f) else Color.Transparent)
            .clickable { onClick() }.padding(horizontal = 8.dp, vertical = 5.dp),
        fontSize = 11.sp, color = if (on) Color.White else UiDim, fontWeight = FontWeight.Bold
    )
}

@Composable
fun App() {
    val st = remember { PlanState() }
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var showReport by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var cmd by remember { mutableStateOf("") }
    var textVal by remember { mutableStateOf("") }

    LaunchedEffect(st.msg) {
        val m = st.msg
        if (m != null) { Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show(); st.msg = null }
    }

    fun pick(t: Tool) {
        if (st.tool == t && t != Tool.SELECT && t != Tool.PAN) st.startTool(Tool.PAN) else st.startTool(t)
    }

    Column(Modifier.fillMaxSize().background(UiDark)) {
        // Bar judul
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFD32F2F)), contentAlignment = Alignment.Center) {
                Text("A", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
            }
            BasicTextField(
                st.title, { st.title = it }, singleLine = true,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = UiText),
                cursorBrush = SolidColor(Color.White)
            )
            IconBtn(Ic.Undo, "Undo") { st.undo() }
            IconBtn(Ic.Redo, "Redo") { st.redo() }
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
        // Tab ribbon
        Row(Modifier.fillMaxWidth().background(UiDark)) {
            listOf("Home", "Anotasi", "Tampilan").forEachIndexed { i, name ->
                Text(
                    name,
                    Modifier.clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (tab == i) UiRibbon else Color.Transparent)
                        .clickable { tab = i }.padding(horizontal = 16.dp, vertical = 7.dp),
                    fontSize = 12.sp, color = if (tab == i) Color.White else UiDim
                )
            }
        }
        // Ribbon
        Row(Modifier.fillMaxWidth().background(UiRibbon).horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
            if (tab == 0) {
                RibbonGroup("Seleksi") {
                    RibbonBtn(Tool.SELECT.icon(), "Pilih", st.tool == Tool.SELECT) { pick(Tool.SELECT) }
                    RibbonBtn(Tool.PAN.icon(), "Kamera", st.tool == Tool.PAN) { pick(Tool.PAN) }
                }
                RibbonGroup("Gambar") {
                    RibbonBtn(Tool.LINE.icon(), "Garis", st.tool == Tool.LINE) { pick(Tool.LINE) }
                    RibbonBtn(Tool.WALL.icon(), "Dinding", st.tool == Tool.WALL) { pick(Tool.WALL) }
                    RibbonBtn(Tool.RECT.icon(), "Persegi", st.tool == Tool.RECT) { pick(Tool.RECT) }
                    RibbonBtn(Tool.CIRCLE.icon(), "Lingkaran", st.tool == Tool.CIRCLE) { pick(Tool.CIRCLE) }
                }
                RibbonGroup("Arsitektur") {
                    RibbonBtn(Tool.DOOR.icon(), "Pintu", st.tool == Tool.DOOR) { pick(Tool.DOOR) }
                    RibbonBtn(Tool.WINDOW.icon(), "Jendela", st.tool == Tool.WINDOW) { pick(Tool.WINDOW) }
                    RibbonBtn(Tool.ROOM.icon(), "Ruang", st.tool == Tool.ROOM) { pick(Tool.ROOM) }
                    RibbonBtn(Tool.FURN.icon(), "Furnitur", st.tool == Tool.FURN) { pick(Tool.FURN) }
                }
                RibbonGroup("Ubah") {
                    RibbonBtn(Tool.MOVE.icon(), "Pindah", st.tool == Tool.MOVE) { pick(Tool.MOVE) }
                    RibbonBtn(Tool.COPY.icon(), "Salin", st.tool == Tool.COPY) { pick(Tool.COPY) }
                    RibbonBtn(Tool.ROTATE.icon(), "Putar", st.tool == Tool.ROTATE) { pick(Tool.ROTATE) }
                    RibbonBtn(Tool.OFFSET.icon(), "Offset", st.tool == Tool.OFFSET) { pick(Tool.OFFSET) }
                    RibbonBtn(Tool.ERASE.icon(), "Hapus", st.tool == Tool.ERASE) { pick(Tool.ERASE) }
                }
                RibbonGroup("Layer") {
                    RibbonBtn(Ic.Layers, if (st.autoLayer) "Auto" else (st.layerOf(st.active)?.name ?: "-"), false) { showLayers = true }
                }
            } else if (tab == 1) {
                RibbonGroup("Anotasi") {
                    RibbonBtn(Tool.DIM.icon(), "Dimensi", st.tool == Tool.DIM) { pick(Tool.DIM) }
                    RibbonBtn(Tool.TEXT.icon(), "Teks", st.tool == Tool.TEXT) { pick(Tool.TEXT) }
                }
                RibbonGroup("Layer") {
                    RibbonBtn(Ic.Layers, "Layer", false) { showLayers = true }
                }
            } else {
                RibbonGroup("Navigasi") {
                    RibbonBtn(Ic.ZoomIn, "Perbesar", false) { st.zoomAt(1.25f, Offset(st.viewW / 2f, st.viewH / 2f)) }
                    RibbonBtn(Ic.ZoomOut, "Perkecil", false) { st.zoomAt(0.8f, Offset(st.viewW / 2f, st.viewH / 2f)) }
                    RibbonBtn(Ic.Home, "Extents", false) { st.zoomExtents() }
                }
                RibbonGroup("Tampilan") {
                    RibbonBtn(Ic.Grid, "Grid", st.showGrid) { st.showGrid = !st.showGrid }
                    RibbonBtn(Ic.Window, if (st.blueprint) "Cetak biru" else "Hitam", false) { st.blueprint = !st.blueprint }
                }
            }
        }
        if (st.tool == Tool.FURN) {
            Row(Modifier.fillMaxWidth().background(UiRibbon).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp)) {
                FURNITURE.forEach { f ->
                    FilterChip(st.furnType == f, { st.furnType = f }, label = { Text(f, fontSize = 12.sp) }, modifier = Modifier.padding(horizontal = 2.dp))
                }
            }
        }
        // Area gambar
        Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { st.viewW = it.width.toFloat(); st.viewH = it.height.toFloat() }) {
            PlanCanvas(st, Modifier.fillMaxSize())
            Text("[-][Atas][Kerangka 2D]", Modifier.align(Alignment.TopStart).padding(8.dp), fontSize = 11.sp, color = Color(0xCCFFFFFF))
            Surface(Modifier.align(Alignment.CenterEnd).padding(6.dp), shape = RoundedCornerShape(8.dp), color = Color(0xAA1F2328)) {
                Column {
                    IconBtn(Ic.ZoomIn, "Perbesar") { st.zoomAt(1.25f, Offset(st.viewW / 2f, st.viewH / 2f)) }
                    IconBtn(Ic.ZoomOut, "Perkecil") { st.zoomAt(0.8f, Offset(st.viewW / 2f, st.viewH / 2f)) }
                    IconBtn(Ic.Home, "Zoom extents") { st.zoomExtents() }
                }
            }
        }
        if (st.selected.isNotEmpty()) Panel(st)
        // Command line
        Column(Modifier.fillMaxWidth().background(Color(0xFF262A2F)).padding(horizontal = 8.dp, vertical = 4.dp)) {
            st.history.takeLast(2).forEach { Text(it, fontSize = 11.sp, color = UiDim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(st.promptText(), Modifier.weight(1f), fontSize = 12.sp, color = UiText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                BasicTextField(
                    cmd, { cmd = it }, singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = Color.White),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { st.submit(cmd); cmd = "" }),
                    modifier = Modifier.width(110.dp).background(Color(0xFF14171A)).padding(8.dp)
                )
                TextButton({ st.submit(cmd); cmd = "" }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Enter", fontSize = 12.sp) }
                TextButton({ st.esc(); cmd = "" }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Esc", fontSize = 12.sp) }
            }
        }
        // Status bar
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF1B1E22)).horizontalScroll(rememberScrollState()).padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("MODEL", Modifier.padding(horizontal = 8.dp), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
            StatusToggle("ORTHO", st.ortho) { st.ortho = !st.ortho }
            StatusToggle("SNAP", st.gridSnap) { st.gridSnap = !st.gridSnap }
            StatusToggle("GRID", st.showGrid) { st.showGrid = !st.showGrid }
            StatusToggle("OSNAP", st.osnap) { st.osnap = !st.osnap }
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
    val tr = st.textReq
    if (tr != null) {
        AlertDialog(onDismissRequest = { st.textReq = null }, title = { Text("Teks / catatan") },
            text = { OutlinedTextField(textVal, { textVal = it }) },
            confirmButton = { TextButton({ st.addText(tr, textVal); textVal = ""; st.textReq = null }) { Text("Tambah") } },
            dismissButton = { TextButton({ st.textReq = null }) { Text("Batal") } })
    }
}

@Composable
fun LayerDialog(st: PlanState, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text("Properti Layer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Layer otomatis per jenis objek", Modifier.weight(1f), fontSize = 13.sp)
                    Switch(st.autoLayer, { st.autoLayer = it })
                }
                Text("Ketuk nama layer untuk menjadikannya layer aktif. Ketuk kotak warna untuk ganti warna.", fontSize = 11.sp, color = UiDim)
                st.layers.forEach { l ->
                    val on = st.active == l.id && !st.autoLayer
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (on) Accent.copy(alpha = 0.3f) else Color.Transparent).padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)).background(aci(l.color))
                            .clickable { st.setLayer(l.copy(color = l.color % 7 + 1)) })
                        Text(
                            l.name + " (" + st.els.count { it.layer == l.id } + ")",
                            Modifier.weight(1f).clickable { st.active = l.id; st.autoLayer = false }.padding(horizontal = 10.dp, vertical = 12.dp),
                            fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
                        )
                        IconBtn(if (l.visible) Ic.Eye else Ic.EyeOff, "Tampil") { st.setLayer(l.copy(visible = !l.visible)) }
                        IconBtn(if (l.locked) Ic.Lock else Ic.Unlock, "Kunci") { st.setLayer(l.copy(locked = !l.locked)) }
                        IconBtn(Ic.Trash, "Hapus", Color(0xFFFF6B6B)) { st.deleteLayer(l.id) }
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
fun Panel(st: PlanState) {
    val e = st.selEl()
    Card(Modifier.fillMaxWidth().heightIn(max = 230.dp).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 10.dp, end = 4.dp, bottom = 6.dp)) {
            if (e == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Properti: ${st.selected.size} objek dipilih", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    IconBtn(Ic.Trash, "Hapus", Color(0xFFFF6B6B)) { st.eraseSel() }
                    IconBtn(Ic.Close, "Tutup") { st.selected.clear() }
                }
                Text("Pindah ke layer", fontSize = 12.sp, color = UiDim)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    st.layers.forEach { l ->
                        FilterChip(false, { st.push(); st.transformSel { it.copy(layer = l.id) } },
                            label = { Text(l.name, fontSize = 11.sp) }, modifier = Modifier.padding(end = 4.dp))
                    }
                }
            } else {
                val info = if (e.kind == Kind.DIM) " | %.2f m".format(e.len()) else ""
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Properti: " + e.kind.label + info, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    IconBtn(Ic.Copy, "Duplikat") { st.duplicate(e.id) }
                    IconBtn(Ic.Trash, "Hapus", Color(0xFFFF6B6B)) { st.delete(e.id) }
                    IconBtn(Ic.Close, "Tutup") { st.selected.clear() }
                }
                if (e.kind != Kind.LINE && e.kind != Kind.RECT && e.kind != Kind.CIRCLE) {
                    OutlinedTextField(e.label, { v -> st.update(e.id) { it.copy(label = v) } },
                        label = { Text("Nama / label") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(end = 6.dp))
                }
                if (e.kind == Kind.ROOM || e.kind == Kind.FURN || e.kind == Kind.WALL || e.kind == Kind.DOOR || e.kind == Kind.WINDOW) {
                    OutlinedTextField(e.note, { v -> st.update(e.id) { it.copy(note = v) } }, maxLines = 2,
                        label = { Text("Keterangan (bahan, finishing, spesifikasi)") },
                        modifier = Modifier.fillMaxWidth().padding(end = 6.dp, top = 4.dp))
                }
                if (e.kind == Kind.WALL) {
                    Text("Tebal dinding: ${(e.thick * 100).roundToInt()} cm", fontSize = 12.sp)
                    Slider(e.thick, { v -> st.update(e.id) { it.copy(thick = v) } }, Modifier.padding(end = 6.dp), valueRange = 0.08f..0.4f)
                }
                if (e.kind != Kind.TEXT && e.kind != Kind.CIRCLE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Putar", Modifier.padding(end = 2.dp), fontSize = 12.sp)
                        TextButton({ st.rotateSel(e.id, -90f) }) { Text("-90") }
                        IconBtn(Ic.RotL, "Putar kiri 15") { st.rotateSel(e.id, -15f) }
                        IconBtn(Ic.RotR, "Putar kanan 15") { st.rotateSel(e.id, 15f) }
                        TextButton({ st.rotateSel(e.id, 90f) }) { Text("+90") }
                    }
                }
                Text("Layer", fontSize = 12.sp, color = UiDim)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    st.layers.forEach { l ->
                        FilterChip(e.layer == l.id, { st.update(e.id) { it.copy(layer = l.id) } },
                            label = { Text(l.name, fontSize = 11.sp) }, modifier = Modifier.padding(end = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PlanCanvas(st: PlanState, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    var mode by remember { mutableStateOf(0) } // 1 pindah, 2 grip A, 3 grip B, 4 kamera, 5 jendela seleksi, 6 pilih titik
    var gripId by remember { mutableStateOf(-1) }
    var boxA by remember { mutableStateOf<Offset?>(null) }
    var boxB by remember { mutableStateOf<Offset?>(null) }

    fun begin(o: Offset) {
        mode = 0
        val wp = st.toWorld(o)
        if (st.tool == Tool.PAN) { mode = 4; return }
        if (st.needsPoint()) { mode = 6; st.hover(o); return }
        val tol = 18f / st.scale()
        for (id in st.selected.toList()) {
            val e = st.els.firstOrNull { it.id == id } ?: continue
            if (!st.editable(e)) continue
            if (e.isLine() && (e.a - wp).getDistance() < tol) { mode = 2; gripId = id; st.push(); return }
            if ((e.isLine() || e.kind == Kind.CIRCLE) && (e.b - wp).getDistance() < tol) { mode = 3; gripId = id; st.push(); return }
        }
        val h = st.hit(wp, 14f / st.scale())
        if (h != null) {
            if (st.tool == Tool.SELECT) {
                if (!st.selected.contains(h)) st.selectOnly(h)
                st.push(); mode = 1
            }
            return
        }
        if (st.tool == Tool.SELECT || (st.selecting() && st.tool != Tool.OFFSET)) { mode = 5; boxA = o; boxB = o }
    }

    fun drag(pos: Offset, d: Offset) {
        when (mode) {
            4 -> st.pan = st.pan + d
            6 -> st.hover(pos)
            1 -> { val dw = rotv(d, -st.rot) / st.scale(); st.transformSel { it.moved(dw) } }
            2, 3 -> st.moveEnd(gripId, mode == 2, st.resolve(st.toWorld(pos), gripId))
            5 -> boxB = pos
            else -> {}
        }
    }

    fun cancelGesture() { boxA = null; boxB = null; mode = 0 }

    fun endGesture(pos: Offset) {
        if (mode == 6) st.pickAt(pos)
        if (mode == 5) {
            val a = boxA; val b = boxB
            if (a != null && b != null) st.windowSelect(a, b)
        }
        cancelGesture()
    }

    fun tap(o: Offset) {
        if (st.tool == Tool.PAN) return
        if (st.needsPoint()) st.pickAt(o)
        else { st.cursor = st.toWorld(o); st.tapSelect(st.toWorld(o), 14f / st.scale()) }
    }

    Canvas(
        modifier.background(st.bg()).pointerInput(Unit) {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var multi = false
                var dragging = false
                while (true) {
                    val ev = awaitPointerEvent()
                    val pressed = ev.changes.count { it.pressed }
                    if (pressed >= 2) {
                        // dua jari: geser + zoom + putar kamera, aktif di semua perintah
                        if (!multi) { multi = true; if (dragging) { cancelGesture(); dragging = false } }
                        val z = ev.calculateZoom(); val r = ev.calculateRotation(); val pn = ev.calculatePan()
                        if (z != 1f || r != 0f || pn != Offset.Zero)
                            st.transformAt(ev.calculateCentroid(useCurrent = false), z, Math.toRadians(r.toDouble()).toFloat(), pn)
                        ev.changes.forEach { if (it.positionChanged()) it.consume() }
                    } else if (!multi) {
                        val ch = ev.changes.first()
                        if (!ch.pressed) { if (dragging) endGesture(ch.position) else tap(down.position); break }
                        if (!dragging && (ch.position - down.position).getDistance() > slop) { dragging = true; begin(down.position) }
                        if (dragging) { drag(ch.position, ch.position - ch.previousPosition); ch.consume() }
                    }
                    if (pressed == 0) break
                }
            }
        }
    ) {
        val s = st.scale()
        if (st.showGrid) {
            val cs = listOf(Offset(0f, 0f), Offset(size.width, 0f), Offset(size.width, size.height), Offset(0f, size.height)).map { st.toWorld(it) }
            val minX = cs.minOf { it.x }; val maxX = cs.maxOf { it.x }; val minY = cs.minOf { it.y }; val maxY = cs.maxOf { it.y }
            val step = if (s >= 40f) 0.5f else 1f
            var x = floor(minX / step) * step
            while (x <= maxX) {
                val major = abs(x - x.roundToInt()) < 0.001f
                drawLine(if (major) st.gridMajor() else st.gridMinor(), st.toScreen(Offset(x, minY)), st.toScreen(Offset(x, maxY)), 1f)
                x += step
            }
            var y = floor(minY / step) * step
            while (y <= maxY) {
                val major = abs(y - y.roundToInt()) < 0.001f
                drawLine(if (major) st.gridMajor() else st.gridMinor(), st.toScreen(Offset(minX, y)), st.toScreen(Offset(maxX, y)), 1f)
                y += step
            }
        }
        val order = listOf(Kind.ROOM, Kind.FURN, Kind.RECT, Kind.CIRCLE, Kind.WALL, Kind.LINE, Kind.WINDOW, Kind.DOOR, Kind.DIM, Kind.TEXT)
        st.els.filter { st.shown(it) }
            .sortedWith(compareBy({ e -> st.layers.indexOfFirst { it.id == e.layer } }, { e -> order.indexOf(e.kind) }))
            .forEach { drawEl(it, st.selected.contains(it.id), st, tm, 1f) }
        st.preview().forEach { drawEl(it, false, st, tm, 0.65f) }

        // grip objek terpilih
        for (e in st.selectedEls()) {
            val gp: List<Offset> = when {
                e.isLine() -> listOf(e.a, e.b, e.center())
                e.kind == Kind.CIRCLE -> listOf(e.a, e.b)
                e.isRect() -> e.corners() + e.center()
                else -> listOf(e.a)
            }
            for (g in gp) {
                val sp = st.toScreen(g)
                drawRect(Color(0xFF2979FF), sp - Offset(6f, 6f), Size(12f, 12f))
                drawRect(Color.White, sp - Offset(6f, 6f), Size(12f, 12f), style = Stroke(1f))
            }
        }
        // titik dasar perintah ubah / titik awal rantai
        for (p in st.pts) drawCircle(Color(0xFFFFEB3B), 4f, st.toScreen(p))

        // marker OSNAP
        val si = st.snapInfo
        if (si != null) {
            val p = st.toScreen(si.p); val yc = Color(0xFFFFEB3B)
            if (si.type == 0) drawRect(yc, p - Offset(9f, 9f), Size(18f, 18f), style = Stroke(2.5f))
            else if (si.type == 1) {
                val tri = Path()
                tri.moveTo(p.x, p.y - 10f); tri.lineTo(p.x - 10f, p.y + 8f); tri.lineTo(p.x + 10f, p.y + 8f); tri.close()
                drawPath(tri, yc, style = Stroke(2.5f))
            } else drawCircle(yc, 10f, p, style = Stroke(2.5f))
        }
        // crosshair
        val cur = st.cursor
        if (cur != null && st.tool != Tool.PAN) {
            val c = st.toScreen(cur); val cc = Color(0xAAFFFFFF)
            drawLine(cc, Offset(0f, c.y), Offset(size.width, c.y), 1f)
            drawLine(cc, Offset(c.x, 0f), Offset(c.x, size.height), 1f)
            drawRect(cc, c - Offset(5f, 5f), Size(10f, 10f), style = Stroke(1f))
        }
        // kotak seleksi jendela / persilangan
        val ba = boxA; val bb = boxB
        if (ba != null && bb != null) {
            val crossing = bb.x < ba.x
            val tl = Offset(min(ba.x, bb.x), min(ba.y, bb.y)); val sz = Size(abs(bb.x - ba.x), abs(bb.y - ba.y))
            val col = if (crossing) Color(0xFF00E676) else Color(0xFF3D9BFF)
            drawRect(col.copy(alpha = 0.18f), tl, sz)
            drawRect(col, tl, sz, style = Stroke(1.5f, pathEffect = if (crossing) Dash else null))
        }
        // ikon UCS
        val o = Offset(46f, size.height - 46f)
        val xd = rotv(Offset(1f, 0f), st.rot) * 36f; val yd = rotv(Offset(0f, -1f), st.rot) * 36f
        drawLine(Color(0xFFFF5252), o, o + xd, 3f, StrokeCap.Round)
        drawLine(Color(0xFF00E676), o, o + yd, 3f, StrokeCap.Round)
        drawRect(Color.White, o - Offset(4f, 4f), Size(8f, 8f), style = Stroke(1.5f))
        txt(tm, "X", o + xd * 1.3f, Color(0xFFFF5252), 11f)
        txt(tm, "Y", o + yd * 1.3f, Color(0xFF00E676), 11f)
        // kompas
        val kc = Offset(size.width - 34f, 34f); val dir = rotv(Offset(0f, -1f), st.rot)
        drawCircle(Color(0x33FFFFFF), 20f, kc)
        drawLine(Color(0xFFFF8A80), kc, kc + dir * 15f, 3f, StrokeCap.Round)
        drawLine(Color(0xCCFFFFFF), kc, kc - dir * 10f, 3f, StrokeCap.Round)
        txt(tm, "U", kc + dir * 30f, Color(0xFFFF8A80), 10f)
    }
}

private fun DrawScope.txt(tm: TextMeasurer, t: String, c: Offset, col: Color = Color.Black, sp: Float = 12f) {
    val r = tm.measure(t, TextStyle(fontSize = sp.sp, color = col, textAlign = TextAlign.Center))
    drawText(r, topLeft = c - Offset(r.size.width / 2f, r.size.height / 2f))
}

private fun DrawScope.drawEl(e: El, sel: Boolean, st: PlanState, tm: TextMeasurer, alpha: Float) {
    val s = st.scale(); val a = st.toScreen(e.a); val b = st.toScreen(e.b)
    val base = st.colorOf(e)
    val col = (if (sel) lerp(base, Color.White, 0.45f) else base).copy(alpha = alpha)
    val pe: PathEffect? = if (sel) Dash else null
    val len = (b - a).getDistance()
    val ang = atan2(b.y - a.y, b.x - a.x)
    val nrm = if (len > 0f) Offset(-(b.y - a.y), b.x - a.x) / len else Offset.Zero
    when (e.kind) {
        Kind.LINE -> drawLine(col, a, b, 1.8f, pathEffect = pe)
        Kind.WALL -> drawLine(col, a, b, max(e.thick * s, 4f), StrokeCap.Square)
        Kind.CIRCLE -> drawCircle(col, e.len() * s, a, style = Stroke(1.8f, pathEffect = pe))
        Kind.RECT, Kind.ROOM, Kind.FURN -> {
            val pts = e.corners().map { st.toScreen(it) }
            val path = Path()
            path.moveTo(pts[0].x, pts[0].y)
            for (i in 1..3) path.lineTo(pts[i].x, pts[i].y)
            path.close()
            if (e.kind == Kind.ROOM) drawPath(path, col.copy(alpha = 0.12f * alpha))
            if (e.kind == Kind.FURN) drawPath(path, col.copy(alpha = 0.08f * alpha))
            drawPath(path, col, style = Stroke(if (e.kind == Kind.FURN) 1.5f else 2f, pathEffect = pe))
            if (e.kind != Kind.RECT) txt(tm, e.label, st.toScreen(e.center()), col, if (e.kind == Kind.ROOM) 13f else 10f)
        }
        Kind.DOOR -> {
            drawLine(st.bg(), a, b, max(0.18f * s, 6f))
            val leaf = Offset(a.x + len * cos(ang - (PI / 2).toFloat()), a.y + len * sin(ang - (PI / 2).toFloat()))
            drawLine(col, a, leaf, 3f)
            drawArc(col, Math.toDegrees(ang.toDouble()).toFloat() - 90f, 90f, false,
                Offset(a.x - len, a.y - len), Size(2 * len, 2 * len), style = Stroke(1.5f))
            txt(tm, e.label, (a + b) / 2f - nrm * 14f, col, 10f)
        }
        Kind.WINDOW -> {
            drawLine(st.bg(), a, b, max(0.18f * s, 6f))
            drawLine(col, a + nrm * 4f, b + nrm * 4f, 1.5f); drawLine(col, a, b, 2f); drawLine(col, a - nrm * 4f, b - nrm * 4f, 1.5f)
            txt(tm, e.label, (a + b) / 2f - nrm * 14f, col, 10f)
        }
        Kind.DIM -> { // satu-satunya ukuran yang tampil: hasil pengukuran manual
            drawLine(col, a, b, 1.5f); drawLine(col, a - nrm * 7f, a + nrm * 7f, 1.5f); drawLine(col, b - nrm * 7f, b + nrm * 7f, 1.5f)
            txt(tm, "%.2f m".format(e.len()), (a + b) / 2f + nrm * 12f, col, 11f)
        }
        Kind.TEXT -> txt(tm, e.label, a, col, 13f)
    }
}
