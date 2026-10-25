package com.arsiplan.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Ikon berbasis path SVG (viewBox 24x24, stroke 2). */
private fun svg(name: String, vararg d: String): ImageVector {
    val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    d.forEach {
        b.addPath(addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }
    return b.build()
}

object Ic {
    val Pan = svg("pan", "M12 2v20M2 12h20M12 2l-3 3M12 2l3 3M12 22l-3-3M12 22l3-3M2 12l3-3M2 12l3 3M22 12l-3-3M22 12l-3 3")
    val Select = svg("select", "M5 3l14 7-6 2-2 6z")
    val Wall = svg("wall", "M3 5h18v14H3zM3 12h18M9 5v7M15 12v7")
    val Door = svg("door", "M6 21V3h12v18M3 21h18M14 12h0.01")
    val Window = svg("window", "M4 4h16v16H4zM12 4v16M4 12h16")
    val Room = svg("room", "M3 3h18v18H3zM3 12h9v9")
    val Furn = svg("furn", "M3 10h18v8H3zM6 10V6h12v4M3 18v2M21 18v2")
    val Dim = svg("dim", "M3 12h18M3 8v8M21 8v8M7 9l-4 3 4 3M17 9l4 3-4 3")
    val Text = svg("text", "M5 5h14M12 5v14M9 19h6")
    val Undo = svg("undo", "M9 14L4 9l5-5M4 9h10a6 6 0 0 1 0 12h-3")
    val Redo = svg("redo", "M15 14l5-5-5-5M20 9H10a6 6 0 0 0 0 12h3")
    val Grid = svg("grid", "M3 3h18v18H3zM3 9h18M3 15h18M9 3v18M15 3v18")
    val Menu = svg("menu", "M4 6h16M4 12h16M4 18h16")
    val Report = svg("report", "M6 3h9l4 4v14H6zM14 3v5h5M9 13h7M9 17h7")
    val Save = svg("save", "M5 3h12l4 4v14H3V3zM7 3v6h8V3M7 21v-7h10v7")
    val Open = svg("open", "M3 5h7l2 3h9v11H3z")
    val Copy = svg("copy", "M8 8h12v12H8zM4 16V4h12")
    val Trash = svg("trash", "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3")
    val Close = svg("close", "M6 6l12 12M18 6L6 18")
    val ZoomIn = svg("zin", "M4 4h12v12H4zM16 16l5 5M10 7v6M7 10h6")
    val ZoomOut = svg("zout", "M4 4h12v12H4zM16 16l5 5M7 10h6")
    val Home = svg("home", "M3 12l9-8 9 8M5 10v10h14V10")
    val Layers = svg("layers", "M12 3l9 5-9 5-9-5zM3 12l9 5 9-5M3 16l9 5 9-5")
    val Eye = svg("eye", "M2 12l4-5h12l4 5-4 5H6zM10 10h4v4h-4z")
    val EyeOff = svg("eyeoff", "M2 12l4-5h12l4 5-4 5H6zM3 3l18 18")
    val Lock = svg("lock", "M5 11h14v10H5zM8 11V7h8v4")
    val Unlock = svg("unlock", "M5 11h14v10H5zM8 11V7h8M16 7v1")
    val Plus = svg("plus", "M12 5v14M5 12h14")
    val RotL = svg("rotl", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8M3 3v5h5")
    val RotR = svg("rotr", "M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8M21 3v5h-5")
    val LineT = svg("linet", "M4 20L20 4")
    val RectT = svg("rectt", "M4 6h16v12H4z")
    val CircleT = svg("circlet", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0")
    val MoveT = svg("movet", "M13 3h8v8M21 3l-9 9M11 5H4v15h15v-7")
    val EraseT = svg("erase", "M3 14l9-9 7 7-9 9H6zM9 20h11")
    val OffsetT = svg("offset", "M3 6h18M3 11h18M12 15v6M9 18l3 3 3-3")
    val Link = svg("link", "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71")
}

fun Tool.icon(): ImageVector = when (this) {
    Tool.SELECT -> Ic.Select; Tool.PAN -> Ic.Pan; Tool.LINE -> Ic.LineT; Tool.WALL -> Ic.Wall
    Tool.RECT -> Ic.RectT; Tool.CIRCLE -> Ic.CircleT; Tool.DOOR -> Ic.Door; Tool.WINDOW -> Ic.Window
    Tool.ROOM -> Ic.Room; Tool.FURN -> Ic.Furn; Tool.DIM -> Ic.Dim; Tool.TEXT -> Ic.Text
    Tool.MOVE -> Ic.MoveT; Tool.COPY -> Ic.Copy; Tool.ROTATE -> Ic.RotR; Tool.OFFSET -> Ic.OffsetT
    Tool.ERASE -> Ic.EraseT
}
