package com.example.coffee.userapp

/** Card UID in the firmware's format (CardUid::toHex): upper-case hex, no separators. */
object CardUid {
    fun toHex(id: ByteArray): String = id.joinToString("") { "%02X".format(it) }
}
