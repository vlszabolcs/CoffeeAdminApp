package com.example.coffee.userapp

import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.coffee.userapp.ui.CoffeeAppTheme
import com.example.coffee.userapp.ui.UserApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: UserAppViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // reader mode only while the app waits for a card; it is switched off in onPause
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.scanning.collect { scanning ->
                    if (scanning) enableReader() else nfcAdapter?.disableReaderMode(this@MainActivity)
                }
            }
        }

        setContent {
            CoffeeAppTheme {
                UserApp(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfcAdapter
        viewModel.nfcStatus.value = when {
            adapter == null -> NfcStatus.Missing
            !adapter.isEnabled -> NfcStatus.Disabled
            else -> NfcStatus.Enabled
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    private fun enableReader() {
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) return
        adapter.enableReaderMode(
            this,
            { tag -> viewModel.onCardRead(CardUid.toHex(tag.id)) },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
    }
}
