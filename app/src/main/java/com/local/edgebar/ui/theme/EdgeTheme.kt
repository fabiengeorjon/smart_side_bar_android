package com.local.edgebar.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.local.edgebar.R

object EdgeColors {
    val Black = Color(0xFF000000)
    val Border = Color(0xFF262626)
    val Surface = Color(0xFF1A1A1A)
    val White = Color(0xFFFFFFFF)
    val Secondary = Color(0x99FFFFFF)
    /** Used only for critical states (service off, failures). */
    val Accent = Color(0xFFD71921)
}

/** Doto (SIL OFL 1.1), bundled. Used only for short labels. */
val Doto = FontFamily(Font(R.font.doto))

val DotoLabel = TextStyle(fontFamily = Doto, fontSize = 12.sp, letterSpacing = 2.sp, color = EdgeColors.White)

private val EdgeColorScheme = darkColorScheme(
    primary = EdgeColors.White,
    onPrimary = EdgeColors.Black,
    secondary = EdgeColors.White,
    onSecondary = EdgeColors.Black,
    background = EdgeColors.Black,
    onBackground = EdgeColors.White,
    surface = EdgeColors.Black,
    onSurface = EdgeColors.White,
    surfaceVariant = EdgeColors.Surface,
    onSurfaceVariant = EdgeColors.Secondary,
    surfaceContainer = EdgeColors.Surface,
    surfaceContainerHigh = EdgeColors.Surface,
    surfaceContainerHighest = EdgeColors.Surface,
    outline = EdgeColors.Border,
    outlineVariant = EdgeColors.Border,
    error = EdgeColors.Accent,
    onError = EdgeColors.White,
)

@Composable
fun EdgeBarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EdgeColorScheme, typography = Typography(), content = content)
}

/** 7x7 dot-matrix glyphs, drawn as dots for a restrained dot-matrix look. */
object Glyphs {
    val CALCULATOR = listOf(
        ".#####.",
        ".#...#.",
        ".#####.",
        ".......",
        ".#.#.#.",
        ".......",
        ".#.#.#.",
    )
    val TORCH = listOf(
        "#######",
        "#.....#",
        ".#...#.",
        "..###..",
        "..#.#..",
        "..###..",
        "..###..",
    )
    val SCREENSHOT = listOf(
        "##...##",
        "#.....#",
        ".......",
        "...#...",
        ".......",
        "#.....#",
        "##...##",
    )
    val LOCK = listOf(
        "..###..",
        ".#...#.",
        ".#...#.",
        "#######",
        "#.....#",
        "#..#..#",
        "#######",
    )
    val MENU = listOf(
        ".......",
        ".......",
        ".......",
        "#..#..#",
        ".......",
        ".......",
        ".......",
    )
    val APP = listOf(
        ".......",
        ".#####.",
        ".#...#.",
        ".#...#.",
        ".#...#.",
        ".#####.",
        ".......",
    )
}

@Composable
fun DotGlyph(rows: List<String>, size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val n = rows.size
        val cell = this.size.minDimension / n
        rows.forEachIndexed { r, line ->
            line.forEachIndexed { c, ch ->
                if (ch == '#') {
                    drawCircle(color, radius = cell * 0.38f, center = Offset(c * cell + cell / 2f, r * cell + cell / 2f))
                }
            }
        }
    }
}
