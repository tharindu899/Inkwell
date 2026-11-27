package com.inkwell.notes.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * One place to change the app font.
 * To use Space Grotesk: put the .ttf files in app/src/main/res/font/ and
 * set  InkFont = FontFamily(Font(R.font.space_grotesk_medium, FontWeight.Medium), Font(R.font.space_grotesk_bold, FontWeight.Bold))
 */
val InkFont: FontFamily = FontFamily.SansSerif

object InkType {
    val Display = TextStyle(
        fontFamily = InkFont, fontWeight = FontWeight.Bold,
        fontSize = 140.sp, lineHeight = 124.sp, letterSpacing = (-7).sp,
    )
    val Headline = TextStyle(
        fontFamily = InkFont, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 33.sp, letterSpacing = (-0.5).sp,
    )
    val Title = TextStyle(
        fontFamily = InkFont, fontWeight = FontWeight.Bold,
        fontSize = 18.sp, lineHeight = 22.sp,
    )
    val Body = TextStyle(
        fontFamily = InkFont, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 22.sp,
    )
    val Label = TextStyle(
        fontFamily = InkFont, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp,
    )
    val Button = TextStyle(
        fontFamily = InkFont, fontWeight = FontWeight.Bold,
        fontSize = 17.sp, lineHeight = 22.sp,
    )
}
