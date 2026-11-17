package com.lifetrenz.qrreader.hid

/** One key press on a US keyboard: modifier bits plus a USB HID usage code. */
data class Key(val modifier: Int, val usage: Int) {
    val isLetter: Boolean get() = usage in 0x04..0x1D
}

/** Maps printable ASCII to US-layout key presses. Indian laptops use the same key positions for these characters. */
object KeyMap {
    const val SHIFT = 0x02
    const val ENTER = 0x28
    const val TAB = 0x2B

    private val symbols: Map<Char, Key> = buildMap {
        "!@#\$%^&*()".forEachIndexed { i, c ->
            put(c, Key(SHIFT, if (i == 9) 0x27 else 0x1E + i))
        }
        // unshifted, shifted, usage
        listOf(
            Triple('-', '_', 0x2D),
            Triple('=', '+', 0x2E),
            Triple('[', '{', 0x2F),
            Triple(']', '}', 0x30),
            Triple('\\', '|', 0x31),
            Triple(';', ':', 0x33),
            Triple('\'', '"', 0x34),
            Triple('`', '~', 0x35),
            Triple(',', '<', 0x36),
            Triple('.', '>', 0x37),
            Triple('/', '?', 0x38),
        ).forEach { (plain, shifted, usage) ->
            put(plain, Key(0, usage))
            put(shifted, Key(SHIFT, usage))
        }
        put(' ', Key(0, 0x2C))
    }

    fun forChar(c: Char): Key? = when (c) {
        in 'a'..'z' -> Key(0, 0x04 + (c - 'a'))
        in 'A'..'Z' -> Key(SHIFT, 0x04 + (c - 'A'))
        in '1'..'9' -> Key(0, 0x1E + (c - '1'))
        '0' -> Key(0, 0x27)
        else -> symbols[c]
    }
}
