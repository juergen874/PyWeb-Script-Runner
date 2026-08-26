package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

object AnsiFormatter {

    private val ANSI_COLOR_MAP = mapOf(
        30 to Color(0xFF1E293B), // Black
        31 to Color(0xFFEF4444), // Red
        32 to Color(0xFF10B981), // Green
        33 to Color(0xFFF59E0B), // Yellow
        34 to Color(0xFF3B82F6), // Blue
        35 to Color(0xFFD946EF), // Magenta
        36 to Color(0xFF06B6D4), // Cyan
        37 to Color(0xFFF8FAFC), // White
        90 to Color(0xFF64748B), // Bright Black / Gray
        91 to Color(0xFFF87171), // Bright Red
        92 to Color(0xFF34D399), // Bright Green
        93 to Color(0xFFFBBF24), // Bright Yellow
        94 to Color(0xFF60A5FA), // Bright Blue
        95 to Color(0xFFE879F9), // Bright Magenta
        96 to Color(0xFF22D3EE), // Bright Cyan
        97 to Color(0xFFFFFFFF)  // Bright White
    )

    // Regex matching standard ANSI escapes (with \u001b or \033) as well as stripped bracket codes like [1m, [34m, [0m
    private val ANSI_REGEX = Regex("(?:\u001B\\[|\\[0?)([0-9;]+)m")

    fun formatAnsi(input: String, defaultColor: Color): AnnotatedString {
        if (!input.contains("[") && !input.contains("\u001B")) {
            return AnnotatedString(input)
        }

        return buildAnnotatedString {
            var currentIndex = 0
            var currentColor = defaultColor
            var isBold = false

            val matches = ANSI_REGEX.findAll(input).toList()

            if (matches.isEmpty()) {
                append(input)
                return@buildAnnotatedString
            }

            for (match in matches) {
                // Append text before this escape code
                if (match.range.first > currentIndex) {
                    val textBefore = input.substring(currentIndex, match.range.first)
                    withStyle(
                        SpanStyle(
                            color = currentColor,
                            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                        )
                    ) {
                        append(textBefore)
                    }
                }

                // Process ANSI codes
                val codeString = match.groupValues[1]
                val codes = codeString.split(";").mapNotNull { it.toIntOrNull() }

                for (code in codes) {
                    when (code) {
                        0 -> {
                            currentColor = defaultColor
                            isBold = false
                        }
                        1 -> isBold = true
                        21, 22 -> isBold = false
                        39 -> currentColor = defaultColor
                        in 30..37, in 90..97 -> {
                            currentColor = ANSI_COLOR_MAP[code] ?: defaultColor
                        }
                    }
                }

                currentIndex = match.range.last + 1
            }

            // Append remaining text after last escape code
            if (currentIndex < input.length) {
                val remainingText = input.substring(currentIndex)
                withStyle(
                    SpanStyle(
                        color = currentColor,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                    )
                ) {
                    append(remainingText)
                }
            }
        }
    }
}
