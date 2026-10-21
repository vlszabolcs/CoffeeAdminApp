package com.example.coffee.userapp

/** Pairing code made by the admin app: pair/<code> = {card, exp}; the database rules check the same format. */
object PairCode {
    const val PATH = "pair"
    const val LENGTH = 8

    fun valid(code: String): Boolean = code.length == LENGTH && code.all { it in '0'..'9' }
}
