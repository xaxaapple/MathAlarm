package ru.mathalarm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Палитра необрутализма: плоские яркие цвета + чёрный контур
val Ink = Color(0xFF111111)
val Paper = Color(0xFFFFF4D6)
val Yellow = Color(0xFFFFD93D)
val Pink = Color(0xFFFF6B9E)
val Blue = Color(0xFF6BB5FF)
val Green = Color(0xFF5CF08F)
val Orange = Color(0xFFFF9F45)
val Lilac = Color(0xFFC8A2FF)
val Red = Color(0xFFFF4D4D)
val White = Color(0xFFFFFFFF)

/** Плоский блок с толстой рамкой и жёсткой смещённой тенью. */
fun Modifier.brutal(bg: Color, shadow: Dp = 6.dp, border: Dp = 3.dp): Modifier = this
    .drawBehind {
        val s = shadow.toPx()
        drawRect(Ink, topLeft = Offset(s, s), size = size)
    }
    .background(bg)
    .border(border, Ink)

@Composable
fun BText(
    text: String,
    size: TextUnit = 18.sp,
    modifier: Modifier = Modifier,
    color: Color = Ink,
    align: TextAlign = TextAlign.Start,
    mono: Boolean = false,
    weight: FontWeight = FontWeight.Black
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = weight,
            textAlign = align,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
        )
    )
}

@Composable
fun BrutalCard(
    modifier: Modifier = Modifier,
    bg: Color = White,
    shadow: Dp = 6.dp,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.brutal(bg, shadow)) { content() }
}

/** Кнопка: при нажатии «вдавливается» в тень. */
@Composable
fun BrutalButton(
    text: String,
    modifier: Modifier = Modifier,
    bg: Color = Yellow,
    shadow: Dp = 6.dp,
    textSize: TextUnit = 20.sp,
    vertical: Dp = 14.dp,
    horizontal: Dp = 16.dp,
    mono: Boolean = false,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val push = if (pressed) shadow else 0.dp
    Box(
        modifier = modifier
            .offset(push, push)
            .brutal(bg, if (pressed) 0.dp else shadow)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = horizontal, vertical = vertical),
        contentAlignment = Alignment.Center
    ) {
        BText(text, size = textSize, align = TextAlign.Center, mono = mono)
    }
}
