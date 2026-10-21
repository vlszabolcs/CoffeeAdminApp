package com.example.coffee.userview.update

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.coffee.userview.R

/**
 * Tells the user once per app start that a newer version is on GitHub. "Download" opens the APK in the
 * browser; installing it is still done by hand.
 */
@Composable
fun UpdateNotice(tagPrefix: String) {
    val context = LocalContext.current
    val checker = remember(tagPrefix) { AppUpdateChecker(context, tagPrefix) }
    var release by remember { mutableStateOf<AppRelease?>(null) }
    var dismissed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(checker) { release = checker.check() }

    val newer = release ?: return
    if (dismissed) return
    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text(stringResource(R.string.uv_update_title)) },
        text = { Text(stringResource(R.string.uv_update_text, newer.version, checker.installedVersion)) },
        confirmButton = {
            TextButton(onClick = {
                dismissed = true
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(newer.url))) }
            }) { Text(stringResource(R.string.uv_update_download)) }
        },
        dismissButton = {
            TextButton(onClick = { dismissed = true }) { Text(stringResource(R.string.uv_update_later)) }
        }
    )
}
