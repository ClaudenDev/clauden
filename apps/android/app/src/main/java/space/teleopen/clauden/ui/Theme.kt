package space.teleopen.clauden.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle as TextFontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.R
import space.teleopen.clauden.data.ButtonStyle
import space.teleopen.clauden.data.CustomTheme
import space.teleopen.clauden.data.FontStyle
import space.teleopen.clauden.data.MotionStyle
import space.teleopen.clauden.data.ThemeStyle
import space.teleopen.clauden.data.ToggleStyle
import space.teleopen.clauden.data.UiDensity
import space.teleopen.clauden.data.UiStyle

data class ClaudenColors(
    val bg: Color,
    val bg300: Color,
    val field: Color,
    val control: Color,
    val bubble: Color,
    val card: Color,
    val cardPreview: Color,
    val drawerBg: Color,
    val drawerPill: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val clay: Color,

    val onClay: Color,
    val border: Color,
    val borderStrong: Color,
    val codeBg: Color,
    val danger: Color,
    val isDark: Boolean,
    val blue: Color,
    val blueText: Color,
    val blueLink: Color,
    val switchOff: Color,
    val fieldBorder: Color,
    val buttonDisabled: Color,
    val buttonDisabledText: Color,
    val dangerSoft: Color,
    val popupBg: Color,
    val badgeBg: Color,
    val scrim: Color,
)

val LightColors = ClaudenColors(
    bg = Color(0xFFF9F9F7),
    bg300 = Color(0xFFF0EFEC),
    field = Color(0xFFFFFFFF),
    control = Color(0xFFE9E8E3),
    bubble = Color(0xFFF0EFEC),
    card = Color(0xFFF0EFEC),
    cardPreview = Color(0xFFF7F6F2),
    drawerBg = Color(0xFFF4F3EF),
    drawerPill = Color(0xFFE4E3DE),
    textPrimary = Color(0xFF131313),
    textSecondary = Color(0xFF383835),
    textTertiary = Color(0xFF7B7974),
    clay = Color(0xFFD97757),
    onClay = Color.White,
    border = Color(0xFFE5E0D6),
    borderStrong = Color(0xFFC9C8C3),
    codeBg = Color(0xFFF0EFEC),
    danger = Color(0xFFC0392B),
    isDark = false,
    blue = Color(0xFF2A78D6),
    blueText = Color(0xFF1F6FCC),
    blueLink = Color(0xFF2A78D6),
    switchOff = Color(0xFFD5D4CF),
    fieldBorder = Color(0xFFDCD4C2),
    buttonDisabled = Color(0xFFC9C8C3),
    buttonDisabledText = Color(0xFF6E6D68),
    dangerSoft = Color(0xFFB3261E),
    popupBg = Color(0xFFFFFFFF),
    badgeBg = Color(0xFFDCE8F8),
    scrim = Color(0x99000000),
)

val DarkColors = ClaudenColors(

    bg = Color(0xFF191919),
    bg300 = Color(0xFF1F1F1F),
    field = Color(0xFF1F1F1F),
    control = Color(0xFF2F2F2F),
    bubble = Color(0xFF262626),
    card = Color(0xFF0B0B0B),
    cardPreview = Color(0xFF0B0B0B),
    drawerBg = Color(0xFF141414),
    drawerPill = Color(0xFF303030),
    textPrimary = Color(0xFFEDEDED),
    textSecondary = Color(0xFFB9B9B9),
    textTertiary = Color(0xFF9C9C9C),
    clay = Color(0xFFD97757),
    onClay = Color.White,
    border = Color(0xFF2B2B2B),
    borderStrong = Color(0xFF4A4A4A),
    codeBg = Color(0xFF121212),
    danger = Color(0xFFE57373),
    isDark = true,
    blue = Color(0xFF2A78D6),
    blueText = Color(0xFF6DA7EC),
    blueLink = Color(0xFF5598E7),
    switchOff = Color(0xFF373736),
    fieldBorder = Color(0xFF3D3C3A),
    buttonDisabled = Color(0xFF5E5D59),
    buttonDisabledText = Color(0xFFD3D2CF),
    dangerSoft = Color(0xFFF4ABAB),
    popupBg = Color(0xFF151515),
    badgeBg = Color(0xFF062B57),
    scrim = Color(0x9E000000),
)

val GlassDarkColors = ClaudenColors(
    bg = Color(0xFF0A0A0B),
    bg300 = Color(0x26FFFFFF),
    field = Color(0x14FFFFFF),
    control = Color(0x26FFFFFF),
    bubble = Color(0x1FFFFFFF),
    card = Color(0x14FFFFFF),
    cardPreview = Color(0x10FFFFFF),
    drawerBg = Color(0xE60E0E10),
    drawerPill = Color(0x2EFFFFFF),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFFB8B8C0),
    textTertiary = Color(0xFF7C7C86),
    clay = Color(0xFFE8E8ED),
    onClay = Color(0xFF111113),
    border = Color(0x38FFFFFF),
    borderStrong = Color(0x66FFFFFF),
    codeBg = Color(0xCC070708),
    danger = Color(0xFFFF6B81),
    isDark = true,
    blue = Color(0xFF64B5FF),
    blueText = Color(0xFF64B5FF),
    blueLink = Color(0xFF64B5FF),
    switchOff = Color(0x3AFFFFFF),
    fieldBorder = Color(0x38FFFFFF),
    buttonDisabled = Color(0x1FFFFFFF),
    buttonDisabledText = Color(0xFF7C7C86),
    dangerSoft = Color(0xFFFFB3BC),
    popupBg = Color(0xF2141416),
    badgeBg = Color(0x26FFFFFF),
    scrim = Color(0x8C000000),
)

val GlassLightColors = ClaudenColors(
    bg = Color(0xFFECECF0),
    bg300 = Color(0xB8FFFFFF),
    field = Color(0xC7FFFFFF),
    control = Color(0xE6FFFFFF),
    bubble = Color(0xCCFFFFFF),
    card = Color(0xB3FFFFFF),
    cardPreview = Color(0xC2FFFFFF),
    drawerBg = Color(0xE6FFFFFF),
    drawerPill = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF101013),
    textSecondary = Color(0xFF3D3D46),
    textTertiary = Color(0xFF6D6D78),
    clay = Color(0xFF1C1C1E),
    onClay = Color.White,
    border = Color(0x24101013),
    borderStrong = Color(0x3D101013),
    codeBg = Color(0xB8FFFFFF),
    danger = Color(0xFFD92D45),
    isDark = false,
    blue = Color(0xFF0A72D6),
    blueText = Color(0xFF0A72D6),
    blueLink = Color(0xFF0A72D6),
    switchOff = Color(0xFFD7D7DE),
    fieldBorder = Color(0x33101013),
    buttonDisabled = Color(0xFFC7C7CF),
    buttonDisabledText = Color(0xFF6D6D78),
    dangerSoft = Color(0xFFB3261E),
    popupBg = Color(0xF2FFFFFF),
    badgeBg = Color(0xE6FFFFFF),
    scrim = Color(0x52101013),
)

val NoirDarkColors = ClaudenColors(
    bg = Color(0xFF050506),
    bg300 = Color(0xFF0F0F12),
    field = Color(0xFF16161A),
    control = Color(0xFF202027),
    bubble = Color(0xFF16161B),
    card = Color(0xFF101013),
    cardPreview = Color(0xFF0B0B0D),
    drawerBg = Color(0xFF09090B),
    drawerPill = Color(0xFF1C1C22),
    textPrimary = Color(0xFFF6F6F8),
    textSecondary = Color(0xFFA9A9B3),
    textTertiary = Color(0xFF71717C),
    clay = Color(0xFFD97757),
    onClay = Color.White,
    border = Color(0x14FFFFFF),
    borderStrong = Color(0x2EFFFFFF),
    codeBg = Color(0xFF08080A),
    danger = Color(0xFFFF6B81),
    isDark = true,
    blue = Color(0xFF64B5FF),
    blueText = Color(0xFF64B5FF),
    blueLink = Color(0xFF64B5FF),
    switchOff = Color(0xFF2A2A31),
    fieldBorder = Color(0xFF2A2A31),
    buttonDisabled = Color(0xFF2A2A31),
    buttonDisabledText = Color(0xFF71717C),
    dangerSoft = Color(0xFFFFB3BC),
    popupBg = Color(0xFF101013),
    badgeBg = Color(0xFF1C1C22),
    scrim = Color(0xB3000000),
)

val NoirLightColors = ClaudenColors(
    bg = Color(0xFFF6F6F8),
    bg300 = Color(0xFFEDEDF1),
    field = Color(0xFFFFFFFF),
    control = Color(0xFFE8E8ED),
    bubble = Color(0xFFEFEFF3),
    card = Color(0xFFFFFFFF),
    cardPreview = Color(0xFFF7F7FA),
    drawerBg = Color(0xFFEDEDF1),
    drawerPill = Color(0xFFE0E0E7),
    textPrimary = Color(0xFF0C0C10),
    textSecondary = Color(0xFF42424C),
    textTertiary = Color(0xFF787881),
    clay = Color(0xFFC85B3C),
    onClay = Color.White,
    border = Color(0xFFE3E3E9),
    borderStrong = Color(0xFFC7C7D1),
    codeBg = Color(0xFFF3F3F6),
    danger = Color(0xFFC0392B),
    isDark = false,
    blue = Color(0xFF2467C9),
    blueText = Color(0xFF2467C9),
    blueLink = Color(0xFF2467C9),
    switchOff = Color(0xFFD5D5DD),
    fieldBorder = Color(0xFFDCDCE4),
    buttonDisabled = Color(0xFFC7C7D1),
    buttonDisabledText = Color(0xFF6E6E78),
    dangerSoft = Color(0xFFB3261E),
    popupBg = Color(0xFFFFFFFF),
    badgeBg = Color(0xFFE7E7EC),
    scrim = Color(0x6614141A),
)

val Material3LightColors = LightColors
val Material3DarkColors = DarkColors

val LocalClaudenColors = staticCompositionLocalOf { LightColors }
val LocalThemeStyle = staticCompositionLocalOf { ThemeStyle.Classic }
val LocalButtonStyle = staticCompositionLocalOf { ButtonStyle.Soft }
val LocalToggleStyle = staticCompositionLocalOf { ToggleStyle.Classic }
val LocalUiDensity = staticCompositionLocalOf { UiDensity.Comfortable }
val LocalMotionStyle = staticCompositionLocalOf { MotionStyle.Full }
val LocalGlassLevel = staticCompositionLocalOf { 65 }

data class ClaudenShapes(
    val card: Shape,
    val dialog: Shape,
    val field: Shape,
    val bubble: Shape,
    val chip: Shape,
    val sheet: Shape,
    val button: Shape,
    val drawer: Shape,
    val hero: Shape,
)

val ClaudeShapes = ClaudenShapes(
    card = RoundedCornerShape(16.dp),
    dialog = RoundedCornerShape(20.dp),
    field = RoundedCornerShape(12.dp),
    bubble = RoundedCornerShape(12.dp),
    chip = RoundedCornerShape(8.dp),
    sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    button = CircleShape,
    drawer = RectangleShape,
    hero = RoundedCornerShape(24.dp),
)

val Material3Shapes = ClaudenShapes(

    card = RoundedCornerShape(12.dp),
    dialog = RoundedCornerShape(28.dp),
    field = RoundedCornerShape(4.dp),
    bubble = RoundedCornerShape(16.dp),
    chip = RoundedCornerShape(8.dp),
    sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    button = CircleShape,
    drawer = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
    hero = RoundedCornerShape(28.dp),
)

val LocalClaudenShapes = staticCompositionLocalOf { ClaudeShapes }
val LocalUiStyle = staticCompositionLocalOf { UiStyle.Claude }

object ClaudenFonts {
    val sans = FontFamily(
        Font(R.font.anthropic_sans, FontWeight.Normal, TextFontStyle.Normal),
        Font(R.font.anthropic_sans_italic, FontWeight.Normal, TextFontStyle.Italic),
    )
    val serif = FontFamily(
        Font(R.font.anthropic_serif, FontWeight.Normal, TextFontStyle.Normal),
        Font(R.font.anthropic_serif_italic, FontWeight.Normal, TextFontStyle.Italic),
    )
    val mono = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))
}

data class ClaudenFontSet(
    val sans: FontFamily,
    val serif: FontFamily,
    val mono: FontFamily,
)

val LocalClaudenFonts = staticCompositionLocalOf {
    ClaudenFontSet(ClaudenFonts.sans, ClaudenFonts.serif, ClaudenFonts.mono)
}

object ClaudenType {
    val serifTitle = TextStyle(
        fontFamily = ClaudenFonts.serif,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp,
    )
    val serifBody = TextStyle(
        fontFamily = ClaudenFonts.serif,
        fontSize = 17.sp,
        lineHeight = 28.sp,
    )
    val serifSmall = TextStyle(
        fontFamily = ClaudenFonts.serif,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )
    val mono = TextStyle(
        fontFamily = ClaudenFonts.mono,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    )
}

private fun claudenTypography(fonts: ClaudenFontSet): Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = fonts.serif,
        fontSize = 30.sp,
        lineHeight = 36.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = fonts.serif,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = fonts.sans,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontFamily = fonts.sans,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = fonts.sans,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = fonts.sans,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = fonts.sans,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontFamily = fonts.sans,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontFamily = fonts.sans,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
)

private fun material3ColorScheme(colors: ClaudenColors, dark: Boolean): ColorScheme {
    val base = if (dark) {
        darkColorScheme(
            primary = colors.clay,
            onPrimary = colors.onClay,
            primaryContainer = colors.bubble,
            onPrimaryContainer = colors.textPrimary,
            secondary = colors.control,
            onSecondary = colors.textPrimary,
            secondaryContainer = colors.control,
            onSecondaryContainer = colors.textPrimary,
            background = colors.bg,
            onBackground = colors.textPrimary,
            surface = colors.bg,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.bg300,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainerLowest = Color(0xFF0F0D13),
            surfaceContainerLow = colors.card,
            surfaceContainer = colors.bg300,
            surfaceContainerHigh = colors.popupBg,
            surfaceContainerHighest = colors.codeBg,
            outline = colors.borderStrong,
            outlineVariant = colors.border,
            error = colors.danger,
            onError = Color(0xFF601410),
            scrim = Color.Black,
            inverseSurface = colors.textPrimary,
            inverseOnSurface = colors.bg,
            surfaceTint = colors.clay,
        )
    } else {
        lightColorScheme(
            primary = colors.clay,
            onPrimary = colors.onClay,
            primaryContainer = colors.bubble,
            onPrimaryContainer = colors.textPrimary,
            secondary = colors.control,
            onSecondary = colors.textPrimary,
            secondaryContainer = colors.control,
            onSecondaryContainer = colors.textPrimary,
            background = colors.bg,
            onBackground = colors.textPrimary,
            surface = colors.bg,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.bg300,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = colors.card,
            surfaceContainer = colors.bg300,
            surfaceContainerHigh = colors.popupBg,
            surfaceContainerHighest = colors.codeBg,
            outline = colors.borderStrong,
            outlineVariant = colors.border,
            error = colors.danger,
            onError = Color.White,
            scrim = Color.Black,
            inverseSurface = colors.textPrimary,
            inverseOnSurface = colors.bg,
            surfaceTint = colors.clay,
        )
    }
    return base
}

private fun material3Typography(): Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
    ),
)

private val claudenMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ClaudenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    uiStyle: UiStyle = UiStyle.Claude,
    fontStyle: FontStyle = FontStyle.System,
    customTheme: CustomTheme = CustomTheme(),
    themeStyle: ThemeStyle = ThemeStyle.Classic,
    accentOverride: Int = 0,
    cornerRadius: Int = 12,
    glassLevel: Int = 65,
    buttonStyle: ButtonStyle = ButtonStyle.Soft,
    toggleStyle: ToggleStyle = ToggleStyle.Classic,
    textScale: Int = 100,
    uiDensity: UiDensity = UiDensity.Comfortable,
    motionStyle: MotionStyle = MotionStyle.Full,
    content: @Composable () -> Unit,
) {
    val material3 = themeStyle == ThemeStyle.Classic && uiStyle == UiStyle.Material3
    val glass = themeStyle == ThemeStyle.Glass
    val baseColors = when {
        glass && darkTheme -> GlassDarkColors
        glass -> GlassLightColors
        themeStyle == ThemeStyle.Noir && darkTheme -> NoirDarkColors
        themeStyle == ThemeStyle.Noir -> NoirLightColors
        material3 && darkTheme -> Material3DarkColors
        material3 -> Material3LightColors
        darkTheme -> DarkColors
        else -> LightColors
    }

    val themed = baseColors
    val accent = when {
        customTheme.enabled && customTheme.useAccent -> Color(customTheme.accent)
        accentOverride != 0 -> Color(accentOverride)
        else -> themed.clay
    }
    val colors = if (accent != themed.clay) {
        themed.copy(
            clay = accent,
            onClay = contrastOn(accent),
            blue = accent,
            blueText = accent,
            blueLink = accent,
        )
    } else {
        themed
    }
    val fonts = when {

        material3 -> ClaudenFontSet(FontFamily.SansSerif, FontFamily.SansSerif, FontFamily.Monospace)
        glass -> ClaudenFontSet(FontFamily.SansSerif, FontFamily.SansSerif, FontFamily.Monospace)
        fontStyle == FontStyle.System ->
            ClaudenFontSet(FontFamily.SansSerif, ClaudenFonts.serif, FontFamily.Monospace)
        else -> ClaudenFontSet(ClaudenFonts.sans, ClaudenFonts.serif, ClaudenFonts.mono)
    }
    val scheme = if (material3) {
        material3ColorScheme(colors, darkTheme)
    } else if (darkTheme) {
        darkColorScheme(
            primary = colors.clay,
            onPrimary = colors.onClay,
            background = colors.bg,
            onBackground = colors.textPrimary,
            surface = colors.bg,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.bg300,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.border,
        )
    } else {
        lightColorScheme(
            primary = colors.clay,
            onPrimary = colors.onClay,
            background = colors.bg,
            onBackground = colors.textPrimary,
            surface = colors.bg,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.bg300,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.border,
        )
    }
    val typography = if (material3) material3Typography() else claudenTypography(fonts)

    val radius = cornerRadius.coerceIn(6, 28)
    val radiusShape = RoundedCornerShape(radius.dp)
    val customButtonShape: Shape = when (buttonStyle) {
        ButtonStyle.Pill, ButtonStyle.Glass -> CircleShape
        ButtonStyle.Square -> RoundedCornerShape(6.dp)
        ButtonStyle.Soft -> RoundedCornerShape((radius * 0.85f).dp)
    }
    val computedShapes = ClaudenShapes(
        card = radiusShape,
        dialog = RoundedCornerShape((radius + 8).dp),
        field = RoundedCornerShape((radius * 0.8f).dp),
        bubble = RoundedCornerShape((radius + 4).dp),
        chip = RoundedCornerShape((radius * 0.6f).dp),
        sheet = RoundedCornerShape(topStart = (radius + 16).dp, topEnd = (radius + 16).dp),
        button = customButtonShape,
        drawer = if (glass || themeStyle == ThemeStyle.Noir) {
            RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
        } else {
            RectangleShape
        },
        hero = RoundedCornerShape((radius + 12).dp),
    )
    val shapes = if (material3) Material3Shapes else computedShapes

    val baseDensity = LocalDensity.current
    val scaledDensity = Density(
        density = baseDensity.density,
        fontScale = baseDensity.fontScale * (textScale.coerceIn(85, 125) / 100f),
    )

    CompositionLocalProvider(
        LocalClaudenColors provides colors,
        LocalClaudenFonts provides fonts,
        LocalClaudenShapes provides shapes,
        LocalUiStyle provides uiStyle,
        LocalCustomTheme provides customTheme,
        LocalThemeStyle provides themeStyle,
        LocalButtonStyle provides buttonStyle,
        LocalToggleStyle provides toggleStyle,
        LocalUiDensity provides uiDensity,
        LocalMotionStyle provides motionStyle,
        LocalGlassLevel provides glassLevel,
        LocalDensity provides scaledDensity,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = claudenMaterialShapes,
            content = content,
        )
    }
}
