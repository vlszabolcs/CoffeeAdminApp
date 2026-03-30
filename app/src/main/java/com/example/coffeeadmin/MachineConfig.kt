package com.example.coffeeadmin

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class MachineConfig(
    val free: Boolean = false,
    val price: Long? = 0L,
    val coffeeThreshold: Long? = 0L
) {
    // No-argument constructor for Firebase
    constructor() : this(false, 0L, 0L)

    @get:Exclude
    val priceInt: Int
        get() = price?.toInt() ?: 0

    @get:Exclude
    val priceLong: Long
        get() = price ?: 0L
}
