package org.andbootmgr.app

import android.net.Uri
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.core.net.toFile
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.topjohnwu.superuser.io.SuFileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.andbootmgr.app.util.AbmOkHttp
import org.andbootmgr.app.util.SOUtils
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest

abstract class WizardFlow {
	abstract fun get(vm: WizardActivityState): List<IWizardPage>
}

@Composable
fun WizardCompat(mvm: MainActivityState, flow: WizardFlow) {
	DisposableEffect(Unit) {
		mvm.activity!!.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		onDispose { mvm.activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
	}
	val vm = remember { WizardActivityState(mvm) }
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

private class ExpectedDigestInputStream(stream: InputStream?,
                                        digest: MessageDigest?,
                                        private val expectedDigest: String
) : DigestInputStream(stream, digest) {
	@OptIn(ExperimentalStdlibApi::class)
	fun doAssert() {
		val hash = digest.digest().toHexString()
		if (hash != expectedDigest)
			throw HashMismatchException("digest $hash does not match expected hash $expectedDigest")
	}
}
class HashMismatchException(message: String) : Exception(message)

class WizardActivityState(val mvm: MainActivityState) {
	val codename = mvm.deviceInfo!!.codename
	val activity = mvm.activity!!
	lateinit var navController: NavHostController
	val logic = mvm.logic!!
	val deviceInfo = mvm.deviceInfo!!
	var prevText by mutableStateOf<String?>(null)
	var nextText by mutableStateOf<String?>(null)
	var onPrev by mutableStateOf<((WizardActivityState) -> Unit)?>(null)
	var onNext by mutableStateOf<((WizardActivityState) -> Unit)?>(null)

	// TODO remove flashes
	var flashes: HashMap<String, Pair<Uri, String?>> = HashMap()
	var texts by mutableStateOf("")
	val inetAvailable = HashMap<String/*id*/, Downloadable>()
	val idNeeded = mutableStateListOf<String>()
	val chosen = mutableStateMapOf<String, DledFile>()
	class Downloadable(val url: String, val hash: String?, val desc: String)

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
		if (inputStream is ExpectedDigestInputStream)
			inputStream.doAssert()
		return nread
	}

	fun flashStream(flashType: String): InputStream {
		return flashes[flashType]?.let {
			val i = when (it.first.scheme) {
				"content" ->
					activity.contentResolver.openInputStream(it.first)
						?: throw IOException("in == null")
				"file" ->
					FileInputStream(it.first.toFile())
				"http", "https" ->
					URL(it.first.toString()).openStream()
				else -> null
			}
			if (it.second != null)
				ExpectedDigestInputStream(i, MessageDigest.getInstance("SHA-256"), it.second!!)
			else i
		} ?: throw IllegalArgumentException()
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

class DledFile(val safFile: Uri?, val netFile: File?) {
	fun delete() {
		netFile?.delete()
	}

	fun openInputStream(vm: WizardActivityState): InputStream {
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

	fun toFile(vm: WizardActivityState): File {
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


class NavButton(val text: String, val onClick: (WizardActivityState) -> Unit)
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
fun WizardDownloader(vm: WizardActivityState) {
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
		var cancelDownload by remember { mutableStateOf<(() -> Unit)?>(null) }
		var progressText by remember { mutableStateOf(vm.activity.getString(R.string.connecting_text)) }
		if (cancelDownload != null) {
			AlertDialog(
				onDismissRequest = {},
				confirmButton = {
					Button(onClick = { cancelDownload!!() }) {
						Text(stringResource(id = R.string.cancel))
					}
				},
				title = { Text(stringResource(R.string.downloading)) },
				text = {
					LoadingCircle(progressText, paddingBetween = 10.dp)
				})
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
						if (vm.inetAvailable.containsKey(i)) {
							Button(onClick = {
								CoroutineScope(Dispatchers.Main).launch {
									val url = vm.inetAvailable[i]!!.url
									val downloadedFile = File(vm.logic.cacheDir, i)
									val h = vm.inetAvailable[i]!!.hash
									val client = AbmOkHttp(url, downloadedFile, h) { bytesRead, contentLength, _ ->
										progressText = vm.activity.getString(R.string.download_progress,
											SOUtils.humanReadableByteCountBin(bytesRead), SOUtils.humanReadableByteCountBin(contentLength))
									}
									try {
										progressText = vm.activity.getString(R.string.connecting_text)
										cancelDownload = {
											client.cancel()
											cancelDownload = null
										}
										if (client.run()) {
											vm.chosen[i] = DledFile(null, downloadedFile)
										}
									} catch (e: Exception) {
										Log.e("ABM", Log.getStackTraceString(e))
										withContext(Dispatchers.Main) {
											Toast.makeText(
												vm.activity,
												vm.activity.getString(R.string.dl_error),
												Toast.LENGTH_LONG
											).show()
										}
									}
									cancelDownload = null
								}
							}) {
								Text(stringResource(R.string.download))
							}
						}
						Button(onClick = {
							vm.activity.chooseFile("*/*") {
								vm.chosen[i] = DledFile(it, null)
							}
						}) {
							Text(stringResource(R.string.choose))
						}
					}
				}
			}
		}
		val isOk = vm.idNeeded.find { !vm.chosen.containsKey(it) } == null
		LaunchedEffect(isOk) {
			if (isOk) {
				vm.onNext = { it.navigate("flash") }
				vm.nextText = vm.activity.getString(R.string.install)
			} else {
				vm.onNext = {}
				vm.nextText = ""
			}
		}
	}
}