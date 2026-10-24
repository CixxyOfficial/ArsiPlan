package com.arsiplan.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { App() } }
    }
}

private fun kindOf(t: Tool): Kind? = when (t) {
    Tool.WALL -> Kind.WALL; Tool.DOOR -> Kind.DOOR; Tool.WINDOW -> Kind.WINDOW
    Tool.ROOM -> Kind.ROOM; Tool.FURN -> Kind.FURN; Tool.DIM -> Kind.DIM; else -> null
}

@Composable
fun App() {
    val st = remember { PlanState() }
    val ctx = LocalContext.current
    var showReport by remember { mutableStateOf(false) }
    var textAt by remember { mutableStateOf<Offset?>(null) }
    var textVal by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(st.title, { st.title = it }, singleLine = true, modifier = Modifier.width(170.dp), label = { Text("Judul proyek") })
            TextButton({ st.undo() }) { Text("↶ Undo") }
            TextButton({ st.redo() }) { Text("↷ Redo") }
            FilterChip(st.snap, { st.snap = !st.snap }, label = { Text("Snap 10cm") })
            TextButton({ showReport = true }) { Text("📋 Laporan") }
            TextButton({
                ctx.openFileOutput("denah.json", 0).use { it.write(st.toJson().toByteArray()) }
                Toast.makeText(ctx, "Tersimpan", Toast.LENGTH_SHORT).show()
            }) { Text("💾 Simpan") }
            TextButton({
                try { st.fromJson(ctx.openFileInput("denah.json").bufferedReader().readText()) }
                catch (e: Exception) { Toast.makeText(ctx, "Belum ada file", Toast.LENGTH_SHORT).show() }
            }) { Text("📂 Buka") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
            Tool.values().forEach { t ->
                FilterChip(st.tool == t, { st.tool = t }, label = { Text(t.label) }, modifier = Modifier.padding(horizontal = 2.dp))
            }
        }
        if (st.tool == Tool.FURN) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
                FURNITURE.forEach { f ->
                    AssistChip({ st.furnType = f }, label = { Text(f) }, modifier = Modifier.padding(horizontal = 2.dp),
                        colors = AssistChipDefaults.assistChipColors(containerColor = if (st.furnType == f) Color(0xFFFFCC80) else Color.Transparent))
                }
            }
        }
        PlanCanvas(st, { textAt = it; textVal = "" }, Modifier.weight(1f).fillMaxWidth())
        st.sel?.let { id -> st.els.firstOrNull { it.id == id }?.let { Panel(st, it) } }
    }

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
fun Panel(st: PlanState, e: El) {
    Card(Modifier.fillMaxWidth().padding(6.dp)) {
        Column(Modifier.padding(8.dp)) {
            val info = when (e.kind) {
                Kind.ROOM -> " • %.2f × %.2f m = %.2f m²".format(e.w(), e.h(), e.area())
                Kind.FURN -> " • %.2f × %.2f m".format(e.w(), e.h())
                Kind.TEXT -> ""
                else -> " • %.2f m".format(e.len())
            }
            Text(e.kind.label + info, fontWeight = FontWeight.Bold)
            OutlinedTextField(e.label, { v -> st.update(e.id) { it.copy(label = v) } },
                label = { Text("Nama / label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (e.kind != Kind.TEXT && e.kind != Kind.DIM)
                OutlinedTextField(e.note, { v -> st.update(e.id) { it.copy(note = v) } },
                    label = { Text("Keterangan (bahan, finishing, spesifikasi)") }, modifier = Modifier.fillMaxWidth())
            if (e.kind == Kind.WALL) {
                Text("Tebal dinding: ${(e.thick * 100).roundToInt()} cm")
                Slider(e.thick, { v -> st.update(e.id) { it.copy(thick = v) } }, valueRange = 0.08f..0.4f)
            }
            Row {
                TextButton({ st.duplicate(e.id) }) { Text("Duplikat") }
                TextButton({ st.delete(e.id) }) { Text("Hapus", color = Color.Red) }
                TextButton({ st.sel = null }) { Text("Tutup") }
            }
        }
    }
}

@Composable
fun PlanCanvas(st: PlanState, onText: (Offset) -> Unit, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    var draft by remember { mutableStateOf<El?>(null) }
    var start by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier.background(Color.White)
            .pointerInput(st.tool) {
                detectTapGestures { o ->
                    when (st.tool) {
                        Tool.SELECT -> st.sel = st.hit(st.toWorld(o), 14f / st.scale())
                        Tool.TEXT -> onText(st.snapPt(st.toWorld(o)))
                        else -> {}
                    }
                }
            }
            .pointerInput(st.tool) {
                if (st.tool == Tool.PAN) {
                    detectTransformGestures { c, pn, z, _ ->
                        val ns = (st.zoom * z).coerceIn(0.3f, 5f); val k = ns / st.zoom
                        st.pan = c - (c - st.pan) * k + pn; st.zoom = ns
                    }
                } else detectDragGestures(
                    onDragStart = { o ->
                        start = st.snapPt(st.toWorld(o))
                        val k = kindOf(st.tool)
                        if (st.tool == Tool.SELECT) {
                            st.sel = st.hit(st.toWorld(o), 14f / st.scale()); if (st.sel != null) st.push()
                        } else if (k != null) {
                            draft = El(-1, k, start, start, if (k == Kind.ROOM) "Ruang" else if (k == Kind.FURN) st.furnType else "")
                        }
                    },
                    onDrag = { ch, d ->
                        ch.consume()
                        if (st.tool == Tool.SELECT) {
                            st.sel?.let { id -> val dw = d / st.scale(); st.update(id) { it.copy(a = it.a + dw, b = it.b + dw) } }
                        } else draft?.let { dr ->
                            draft = dr.copy(b = st.constrain(start, st.snapPt(st.toWorld(ch.position)), dr.kind))
                        }
                    },
                    onDragEnd = { draft?.let { st.commit(it) }; draft = null },
                    onDragCancel = { draft = null }
                )
            }
    ) {
        val s = st.scale()
        val step = if (s >= 40f) 0.5f else 1f
        var x = floor((-st.pan.x / s) / step) * step
        while (x * s + st.pan.x < size.width) {
            val major = abs(x - x.roundToInt()) < 0.001f
            val px = x * s + st.pan.x
            drawLine(if (major) Color(0xFFCFD8DC) else Color(0xFFECEFF1), Offset(px, 0f), Offset(px, size.height), 1f)
            x += step
        }
        var y = floor((-st.pan.y / s) / step) * step
        while (y * s + st.pan.y < size.height) {
            val major = abs(y - y.roundToInt()) < 0.001f
            val py = y * s + st.pan.y
            drawLine(if (major) Color(0xFFCFD8DC) else Color(0xFFECEFF1), Offset(0f, py), Offset(size.width, py), 1f)
            y += step
        }
        // Urutan gambar: ruang -> furnitur -> dinding -> pintu/jendela -> dimensi/teks
        val order = listOf(Kind.ROOM, Kind.FURN, Kind.WALL, Kind.WINDOW, Kind.DOOR, Kind.DIM, Kind.TEXT)
        order.forEach { k -> st.els.filter { it.kind == k }.forEach { drawEl(it, it.id == st.sel, st, tm) } }
        draft?.let { drawEl(it, false, st, tm) }
        // skala 1 m
        drawLine(Color.Black, Offset(20f, size.height - 20f), Offset(20f + s, size.height - 20f), 3f)
        txt(tm, "1 m", Offset(20f + s / 2, size.height - 34f), Color.Black, 11f)
    }
}

private fun DrawScope.txt(tm: TextMeasurer, t: String, c: Offset, col: Color = Color.Black, sp: Float = 12f) {
    val r = tm.measure(t, TextStyle(fontSize = sp.sp, color = col, textAlign = TextAlign.Center))
    drawText(r, topLeft = c - Offset(r.size.width / 2f, r.size.height / 2f))
}

private fun DrawScope.drawEl(e: El, sel: Boolean, st: PlanState, tm: TextMeasurer) {
    val s = st.scale(); val a = st.toScreen(e.a); val b = st.toScreen(e.b)
    val hi = Color(0xFFFF6D00)
    val len = (b - a).getDistance()
    val ang = atan2(b.y - a.y, b.x - a.x)
    val nrm = if (len > 0f) Offset(-(b.y - a.y), b.x - a.x) / len else Offset.Zero
    when (e.kind) {
        Kind.ROOM, Kind.FURN -> {
            val tl = Offset(min(a.x, b.x), min(a.y, b.y)); val sz = Size(abs(b.x - a.x), abs(b.y - a.y))
            val room = e.kind == Kind.ROOM
            drawRect(if (room) Color(0x332196F3) else Color(0xFFFFE0B2), tl, sz)
            drawRect(if (sel) hi else if (room) Color(0xFF1976D2) else Color(0xFF8D6E63), tl, sz, style = Stroke(if (sel) 4f else 2f))
            val c = tl + Offset(sz.width / 2, sz.height / 2)
            txt(tm, if (room) e.label + "\n%.1f m²".format(e.area()) else e.label, c, Color(0xFF263238), if (room) 13f else 10f)
        }
        Kind.WALL -> {
            drawLine(if (sel) hi else Color(0xFF37474F), a, b, max(e.thick * s, 4f), StrokeCap.Square)
            txt(tm, "%.2f".format(e.len()), (a + b) / 2f + nrm * 14f, Color(0xFF78909C), 9f)
        }
        Kind.DOOR -> {
            val col = if (sel) hi else Color(0xFF2E7D32)
            drawLine(Color.White, a, b, max(0.18f * s, 6f))
            val leaf = Offset(a.x + len * cos(ang - (PI / 2).toFloat()), a.y + len * sin(ang - (PI / 2).toFloat()))
            drawLine(col, a, leaf, 3f)
            drawArc(col, Math.toDegrees(ang.toDouble()).toFloat() - 90f, 90f, false,
                Offset(a.x - len, a.y - len), Size(2 * len, 2 * len), style = Stroke(1.5f))
            txt(tm, e.label, (a + b) / 2f - nrm * 14f, col, 10f)
        }
        Kind.WINDOW -> {
            val col = if (sel) hi else Color(0xFF0277BD)
            drawLine(Color.White, a, b, max(0.18f * s, 6f))
            drawLine(col, a + nrm * 4f, b + nrm * 4f, 1.5f); drawLine(col, a, b, 2f); drawLine(col, a - nrm * 4f, b - nrm * 4f, 1.5f)
            txt(tm, e.label, (a + b) / 2f - nrm * 14f, col, 10f)
        }
        Kind.DIM -> {
            val col = if (sel) hi else Color(0xFFC2185B)
            drawLine(col, a, b, 1.5f); drawLine(col, a - nrm * 7f, a + nrm * 7f, 1.5f); drawLine(col, b - nrm * 7f, b + nrm * 7f, 1.5f)
            txt(tm, "%.2f m".format(e.len()), (a + b) / 2f + nrm * 12f, col, 11f)
        }
        Kind.TEXT -> txt(tm, e.label, a, if (sel) hi else Color(0xFF263238), 13f)
    }
    if (sel && e.kind != Kind.ROOM && e.kind != Kind.FURN && e.kind != Kind.TEXT) {
        drawCircle(hi, 7f, a); drawCircle(hi, 7f, b)
    }
}
