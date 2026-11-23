package com.example.spotifyclone.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotifyclone.theme.CrankrBodyFont
import com.example.spotifyclone.theme.TextWhite

/**
 * High-fidelity Marquee text component that automatically scrolls long titles
 * horizontally with smooth initial delay and looping, eliminating awkward truncation.
 */
@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextWhite,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    fontFamily: FontFamily = CrankrBodyFont,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    active: Boolean = true,
    velocity: Dp = 32.dp,
    initialDelayMillis: Int = 1800,
    delayMillis: Int = 1200
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textAlign = textAlign,
        maxLines = 1,
        overflow = if (active) TextOverflow.Clip else TextOverflow.Ellipsis,
        modifier = modifier.then(
            if (active) {
                Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = initialDelayMillis,
                    repeatDelayMillis = delayMillis,
                    velocity = velocity
                )
            } else {
                Modifier
            }
        )
    )
}
