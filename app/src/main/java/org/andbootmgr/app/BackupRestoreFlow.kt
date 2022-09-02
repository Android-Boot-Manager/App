package org.andbootmgr.app

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.andbootmgr.app.ui.theme.AbmTheme

class BackupRestoreWizardPageFactory(private val vm: WizardActivityState) {
    fun get(): List<IWizardPage> {
        return listOf(WizardPage("start",
            NavButton("Cancel") { it.startActivity(Intent(it, MainActivity::class.java)); it.finish() },
            NavButton("") {})
        {
            ChooseAction(vm)
        }, WizardPage("select",
            NavButton("Prev") { it.navigate("start") },
            NavButton("") {}
        ) {
            Select(vm)
        }, WizardPage("Complete_action",
            NavButton("") {},
            NavButton("") {}
        ) {
            Flash(vm)
        })
    }
}

@Composable
private fun ChooseAction(vm: WizardActivityState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Text("Backup & Restore")
        Text(stringResource(id = R.string.backup_msg))
        Button(onClick = { vm.navController.navigate() }) {
            Text("Backup")
        }
        Button(onClick = { /*TODO*/ }) {
            Text("Restore")
        }
        Button(onClick = { /*TODO*/ }) {
            Text("Flash sparse image")
        }
    }
}

@Composable
private fun Select(vm: WizardActivityState) {

}

@Composable
private fun Flash(vm: WizardActivityState) {

}