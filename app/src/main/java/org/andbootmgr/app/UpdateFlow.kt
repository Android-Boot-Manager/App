package org.andbootmgr.app

import android.content.Intent
import android.util.Log
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.topjohnwu.superuser.io.SuFile
import org.andbootmgr.app.util.ConfigFile
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.net.URL

class UpdateFlowWizardPageFactory(private val vm: WizardActivityState) {
    fun get(): List<IWizardPage> {
        val c = UpdateFlowDataHolder(vm)
        return listOf(WizardPage("start",
            NavButton("Cancel") {
                it.startActivity(Intent(it, MainActivity::class.java))
                it.finish()
            },
            NavButton("") {}
        ) {
            Start(c)
        }, WizardPage("flash",
            NavButton("") {},
            NavButton("") {}
        ) {
            Flash(c)
        })
    }
}

private class UpdateFlowDataHolder(val vm: WizardActivityState) {
    var e: ConfigFile? = null
    var ef: File? = null
    var json: JSONObject? = null
}

@Composable
private fun Start(u: UpdateFlowDataHolder) {
    LaunchedEffect(Unit) {
        val entries = mutableMapOf<ConfigFile, File>()
        val list = SuFile.open(u.vm.logic.abmEntries.absolutePath).listFiles()
        for (i in list!!) {
            try {
                entries[ConfigFile.importFromFile(i)] = i
            } catch (e: ActionAbortedCleanlyError) {
                Log.e("ABM", Log.getStackTraceString(e))
            }
        }
        val toFind = u.vm.activity.intent.getStringExtra("entryFilename") ?: "null"
        u.e = entries.entries.find { it.value.absolutePath == toFind }!!.also { u.ef = it.value }.key

        Thread {
            try {
                val json_text =
                    URL(u.e!!["xupdate"] ?: "https://example.com/update.json").readText()
                u.json = JSONTokener(json_text).nextValue() as JSONObject
            } catch (e: Exception) {
                Log.e("ABM", Log.getStackTraceString(e))
            }
            if (u.json != null) {
                //check for updates here
                u.vm.btnsOverride = true
                u.vm.nextText.value = "Next"
                u.vm.onNext.value = { u.vm.navigate("flash") }
            }
        }.start()
    }
    if (u.json == null) {
        Text("Failed to check for updates! Please try again later.")
    } else {
        //gui when check ok here
    }
}

@Composable
private fun Flash(u: UpdateFlowDataHolder) {
    Terminal(u.vm) { terminal ->
        u.vm.btnsOverride = true
        u.vm.nextText.value = "Finish"
        u.vm.onNext.value = {
            it.startActivity(Intent(it, MainActivity::class.java))
            it.finish()
        }
    }
}