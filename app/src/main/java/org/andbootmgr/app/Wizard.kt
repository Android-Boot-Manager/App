package org.andbootmgr.app

import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.topjohnwu.superuser.io.SuFileOutputStream
import org.andbootmgr.app.util.AbmOkHttp
import org.andbootmgr.app.util.TerminalCancelException
import org.andbootmgr.app.util.TerminalList
import org.andbootmgr.app.util.TerminalWork
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

abstract class WizardFlow {
	abstract fun get(vm: WizardState): List<IWizardPage>
}

@Composable
fun WizardCompat(mvm: MainActivityState, flow: WizardFlow) {
	DisposableEffect(Unit) {
		mvm.activity!!.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		onDispose { mvm.activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
	}
	val vm = remember { WizardState(mvm) }
	vm.navController = rememberNavController()
	val wizardPages = remember(flow) { flow.get(vm) }
		NavHost(
			navController = vm.navController,
			startDestination = "start",
			modifier = Modifier
				.fillMaxWidth()
		) {
			for (i in wizardPages) {
				composable(i.name) {
					Column(modifier = Modifier.fillMaxSize()) {
						BackHandler {
							(vm.onPrev ?: i.prev.onClick)(vm)
						}
						Box(Modifier.fillMaxWidth().weight(1f)) {
							i.run()
						}
						Box(Modifier.fillMaxWidth()) {
							BasicButtonRow(vm.prevText ?: i.prev.text,
								{ (vm.onPrev ?: i.prev.onClick)(vm) },
								vm.nextText ?: i.next.text,
								{ (vm.onNext ?: i.next.onClick)(vm) })
						}
					}
				}
			}
		}
}

class HashMismatchException(message: String) : Exception(message)

class WizardState(val mvm: MainActivityState) {
	val codename = mvm.deviceInfo!!.codename
	val activity = mvm.activity!!
	lateinit var navController: NavHostController
	val logic = mvm.logic!!
	val deviceInfo = mvm.deviceInfo!!
	var prevText by mutableStateOf<String?>(null)
	var nextText by mutableStateOf<String?>(null)
	var onPrev by mutableStateOf<((WizardState) -> Unit)?>(null)
	var onNext by mutableStateOf<((WizardState) -> Unit)?>(null)

	val inetAvailable = mutableStateMapOf<String, Downloadable>()
	val idNeeded = mutableStateListOf<String>()
	val chosen = mutableStateMapOf<String, DownloadedFile>()
	class Downloadable(val url: String, val hash: String?, val desc: String)
	class DownloadedFile(private val safFile: Uri?, private val netFile: File?) {
		fun delete() {
			netFile?.delete()
		}

		fun openInputStream(vm: WizardState): InputStream {
			netFile?.let {
				return FileInputStream(it)
			}
			safFile?.let {
				val istr = vm.activity.contentResolver.openInputStream(it)
				if (istr != null) {
					return istr
				}
			}
			throw IllegalStateException("invalid DledFile OR failure")
		}

		fun toFile(vm: WizardState): File {
			netFile?.let { return it }
			safFile?.let {
				val istr = vm.activity.contentResolver.openInputStream(it)
				if (istr != null) {
					val f = File(vm.logic.cacheDir, System.currentTimeMillis().toString())
					vm.copyUnpriv(istr, f)
					istr.close()
					return f
				}
			}
			throw IllegalStateException("invalid DledFile OR safFile failure")
		}
	}
	suspend fun downloadRemainingFiles(terminal: TerminalList) {
		terminal.isCancelled = false
		for (id in idNeeded.filter { !chosen.containsKey(it) }) {
			if (!inetAvailable.containsKey(id))
				throw IllegalStateException("$id not chosen and not available from inet")
			terminal.add(activity.getString(R.string.downloading_s, id))
			val inet = inetAvailable[id]!!
			val f = File(logic.cacheDir, System.currentTimeMillis().toString())
			terminal.add(activity.getString(R.string.connecting_text))
			val client = AbmOkHttp(inet.url, f, inet.hash) { readBytes, total, done ->
				terminal[terminal.size - 1] = if (done) activity.getString(R.string.done) else
					activity.getString(
						R.string.download_progress,
						"${readBytes / (1024 * 1024)} MiB", "${total / (1024 * 1024)} MiB"
					)
			}
			terminal.cancel = { terminal.isCancelled = true; client.cancel() }
			try {
				client.run()
			} catch (e: IOException) {
				if (terminal.isCancelled == true) {
					throw TerminalCancelException()
				}
				throw e
			}
			if (terminal.isCancelled == true) {
				throw TerminalCancelException()
			}
			chosen[id] = DownloadedFile(null, f)
		}
		if (terminal.isCancelled == true) {
			throw TerminalCancelException()
		} else {
			terminal.isCancelled = null
		}
	}

	fun navigate(next: String) {
		prevText = null
		nextText = null
		onPrev = null
		onNext = null
		navController.navigate(next) {
			launchSingleTop = true
		}
	}
	fun finish() {
		mvm.init()
		mvm.currentWizardFlow = null
	}

	fun copy(inputStream: InputStream, outputStream: OutputStream): Long {
		var nread = 0L
		val buf = ByteArray(8192)
		var n: Int
		while (inputStream.read(buf).also { n = it } > 0) {
			outputStream.write(buf, 0, n)
			nread += n.toLong()
		}
		inputStream.close()
		outputStream.flush()
		outputStream.close()
		return nread
	}

	fun copyUnpriv(inputStream: InputStream, output: File) {
		Files.copy(inputStream, output.toPath(), StandardCopyOption.REPLACE_EXISTING)
		inputStream.close()
	}

	fun copyPriv(inputStream: InputStream, output: File) {
		val outStream = SuFileOutputStream.open(output)
		copy(inputStream, outStream)
	}
}


class NavButton(val text: String, val onClick: (WizardState) -> Unit)
class WizardPage(override val name: String, override val prev: NavButton,
                       override val next: NavButton, override val run: @Composable () -> Unit
) : IWizardPage

interface IWizardPage {
	val name: String
	val prev: NavButton
	val next: NavButton
	val run: @Composable () -> Unit
}

@Composable
fun BasicButtonRow(prev: String, onPrev: () -> Unit,
                   next: String, onNext: () -> Unit) {
	Row {
		TextButton(onClick = {
			onPrev()
		}, modifier = Modifier.weight(1f, true)) {
			Text(prev)
		}
		TextButton(onClick = {
			onNext()
		}, modifier = Modifier.weight(1f, true)) {
			Text(next)
		}
	}
}

@Composable
fun WizardDownloader(vm: WizardState, next: String) {
	Column(Modifier.fillMaxSize()) {
		Card {
			Row(
				Modifier
					.fillMaxWidth()
					.padding(20.dp)
			) {
				Icon(painterResource(id = R.drawable.ic_about), stringResource(id = R.string.icon_content_desc))
				Text(stringResource(id = R.string.provide_images))
			}
		}
		for (i in vm.idNeeded) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
				modifier = Modifier.fillMaxWidth()
			) {
				Column {
					Text(i)
					Text(
						vm.inetAvailable[i]?.desc ?: stringResource(R.string.user_selected),
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				Column {
					if (vm.chosen.containsKey(i)) {
						Button(onClick = {
							vm.chosen[i]!!.delete()
							vm.chosen.remove(i)
						}) {
							Text(stringResource(R.string.undo))
						}
					} else {
						Button(onClick = {
							vm.activity.chooseFile("*/*") {
								vm.chosen[i] = WizardState.DownloadedFile(it, null)
							}
						}) {
							Text(stringResource(R.string.choose))
						}
					}
				}
			}
		}
		val isOk = vm.idNeeded.find { !vm.chosen.containsKey(it) &&
				!vm.inetAvailable.containsKey(it) } == null
		LaunchedEffect(isOk) {
			if (isOk) {
				vm.onNext = { it.navigate(next) }
				vm.nextText = vm.activity.getString(R.string.install)
			} else {
				vm.onNext = {}
				vm.nextText = ""
			}
		}
	}
}

@Composable
fun WizardTerminalWork(vm: WizardState, logFile: String? = null,
                       action: suspend (TerminalList) -> Unit) {
	TerminalWork(logFile) {
		vm.mvm.currentWizardFlow = null
		action(it)
	}
}