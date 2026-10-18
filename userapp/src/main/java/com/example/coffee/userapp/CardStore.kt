package com.example.coffee.userapp

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The one card this phone shows. */
data class SavedCard(val uid: String, val name: String?)

/**
 * Keeps at most one card. Saving a new card replaces the old one; nothing else about the old
 * card is stored on the phone (the credit and the log are always read live from the database).
 */
class CardStore(context: Context) {
    private val prefs = context.getSharedPreferences("card", Context.MODE_PRIVATE)
    private val _card = MutableStateFlow(read())
    val card: StateFlow<SavedCard?> = _card.asStateFlow()

    fun save(card: SavedCard) {
        prefs.edit().clear().putString(KEY_UID, card.uid).putString(KEY_NAME, card.name).apply()
        _card.value = card
    }

    fun clear() {
        prefs.edit().clear().apply()
        _card.value = null
    }

    private fun read(): SavedCard? =
        prefs.getString(KEY_UID, null)?.let { SavedCard(it, prefs.getString(KEY_NAME, null)) }

    private companion object {
        const val KEY_UID = "cardUid"
        const val KEY_NAME = "name"
    }
}
