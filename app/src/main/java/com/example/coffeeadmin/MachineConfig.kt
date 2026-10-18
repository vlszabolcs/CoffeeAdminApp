package com.example.coffeeadmin

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class CoffeeBeans(
    val inStock: Double = 0.0,
    val price: Double = 0.0,
    val threshold: Double = 0.0
)

@IgnoreExtraProperties
data class MachineConfig(
    val free: Boolean = false,
    val price: Long? = 0L,
    val coffeeThreshold: Long? = 0L,
    val coffeeBeans: CoffeeBeans = CoffeeBeans()
) {
    // No-argument constructor for Firebase
    constructor() : this(false, 0L, 0L, CoffeeBeans())

    @get:Exclude
    val priceInt: Int
        get() = price?.toInt() ?: 0

    @get:Exclude
    val priceLong: Long
        get() = price ?: 0L
}
