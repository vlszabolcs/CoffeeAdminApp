package com.example.coffeeadmin

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class MachineConfig(
    val free: Boolean = false,
    val price: Int = 0
) {
    // No-argument constructor for Firebase
    constructor() : this(false, 0)
}
