package com.imanieh.zaersara.util

fun normalizeDigits(input: String): String = buildString {
    input.forEach { ch ->
        append(
            when (ch) {
                in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
                in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
                else -> ch
            }
        )
    }
}

fun normalizeNumeric(input: String, maxLength: Int? = null): String {
    val v = normalizeDigits(input).filter { it.isDigit() }
    return maxLength?.let { v.take(it) } ?: v
}
