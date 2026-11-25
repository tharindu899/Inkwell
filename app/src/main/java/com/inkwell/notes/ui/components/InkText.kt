package com.inkwell.notes.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.inkwell.notes.ui.theme.Ink

/**
 * Plain text with explicit styling (no Material defaults), so every screen
 * matches the CSS values exactly.
 */
@Composable
fun InkText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Ink.colors.text1,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Normal,
    family: FontFamily = Ink.sans,
    align: TextAlign = TextAlign.Start,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = weight,
            fontFamily = family,
            textAlign = align,
            letterSpacing = letterSpacing,
            lineHeight = lineHeight,
        ),
    )
}

@Composable
fun InkText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Ink.colors.text1,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Normal,
    family: FontFamily = Ink.sans,
    align: TextAlign = TextAlign.Start,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = weight,
            fontFamily = family,
            textAlign = align,
            lineHeight = lineHeight,
        ),
    )
}
