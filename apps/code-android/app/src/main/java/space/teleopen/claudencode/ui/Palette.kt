package space.teleopen.claudencode.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import space.teleopen.claudencode.R

object Pal {
    val Bg = Color(0xFF070606)
    val BgDeep = Color(0xFF050505)
    val Panel = Color(0xFF0D0C0C)
    val PanelHi = Color(0xFF151413)
    val PanelSoft = Color(0xFF10100F)
    val Border = Color(0xFF262423)
    val BorderSoft = Color(0xFF1B1A19)
    val UserBlock = Color(0xFF151413)

    val Accent = Color(0xFFD8663F)
    val AccentBright = Color(0xFFFF6B3D)
    val AccentDim = Color(0xFF8C3C22)
    val AccentDeep = Color(0xFF2A120A)

    val Green = Color(0xFF6FC37A)
    val GreenDeep = Color(0xFF1E4D2D)
    val Blue = Color(0xFF4B7AD7)
    val Red = Color(0xFFE05A52)
    val Amber = Color(0xFFD9A55B)

    val White = Color(0xFFEDECEA)
    val Gray = Color(0xFF9A9793)
    val Dim = Color(0xFF6F6C69)
    val Faint = Color(0xFF3A3836)

    val Peri = Color(0xFFE3B98C)
    val PeriSoft = Color(0xFFBBB5AD)
    val Cyan = Color(0xFF418FAF)
    val CyanText = Color(0xFF0B141C)

    val CodeBg = Color(0xFF0B0A0A)
    val CodeBorder = Color(0xFF242221)
    val OrangeWash = Color(0xFF1A0D08)
}

private val monoFamily = FontFamily(
    Font(R.font.geist_mono, FontWeight.Normal),
    Font(R.font.geist_mono, FontWeight.Medium),
    Font(R.font.geist_mono, FontWeight.SemiBold),
    Font(R.font.geist_mono_bold, FontWeight.Bold),
    Font(R.font.geist_mono_bold, FontWeight.ExtraBold),
)

fun mono(size: TextUnit, weight: FontWeight? = null): TextStyle = TextStyle(
    fontFamily = monoFamily,
    fontSize = size * 0.9f,
    fontWeight = weight ?: FontWeight.Normal,
    lineHeight = size * 1.35f,
)

fun type(
    size: TextUnit,
    weight: FontWeight? = null,
    lineHeight: Float = 1.5f,
): TextStyle = TextStyle(
    fontFamily = monoFamily,
    fontSize = size,
    fontWeight = weight ?: FontWeight.Normal,
    lineHeight = size * lineHeight,
    letterSpacing = 0.1.sp,
)

val Spinner = charArrayOf('·', '✢', '✳', '∗', '✻', '✽', '✢', '✳', '∗', '✻', '✽')
