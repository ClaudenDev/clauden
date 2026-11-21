package space.teleopen.claudencode.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import space.teleopen.claudencode.R

@Composable
fun ClaudeMark(
    size: Dp = 32.dp,
    modifier: Modifier = Modifier,
    color: Color = Pal.Accent,
    hole: Color = Pal.Bg,
) {
    Image(
        painter = painterResource(R.drawable.clauden_mascot),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

@Composable
fun PixelMascot(size: Dp = 112.dp, modifier: Modifier = Modifier) {
    ClaudeMark(size = size, modifier = modifier)
}
