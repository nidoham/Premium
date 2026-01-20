package com.nidoham.premium.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Material Design 3 typography scale for the application.
 *
 * Defines a consistent type system following Material Design 3 guidelines,
 * providing appropriate styles for various UI elements including headings,
 * body text, and labels with optimized readability and hierarchy.
 */
val Typography = Typography(
    /**
     * Large title style for prominent headings and primary application branding.
     * Recommended usage: Top app bar titles, screen headers.
     */
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),

    /**
     * Medium title style for secondary headings and emphasized list items.
     * Recommended usage: Contact names, section headers, dialog titles.
     */
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),

    /**
     * Small title style for tertiary headings and navigation labels.
     * Recommended usage: Tab headers, chip labels, card titles.
     */
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    /**
     * Large body text style for primary content.
     * Recommended usage: Main message content, article text, primary descriptions.
     */
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),

    /**
     * Medium body text style for secondary content.
     * Recommended usage: Message previews, supporting text, list item subtitles.
     */
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),

    /**
     * Small body text style for tertiary content and system messages.
     * Recommended usage: Captions, helper text, footnotes, system notifications.
     */
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),

    /**
     * Large label style for prominent UI elements and call-to-action text.
     * Recommended usage: Button text, emphasized badges, notification counts.
     */
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),

    /**
     * Medium label style for standard UI element labels.
     * Recommended usage: Timestamps, metadata labels, secondary badges.
     */
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),

    /**
     * Small label style for minimal UI annotations.
     * Recommended usage: Inline timestamps, status indicators, micro-copy.
     */
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)