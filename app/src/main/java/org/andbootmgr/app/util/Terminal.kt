package org.andbootmgr.app.util

import android.util.Log
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.topjohnwu.superuser.io.SuFileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.andbootmgr.app.R
import java.io.File
import java.io.OutputStream

private class BudgetCallbackList(private val scope: CoroutineScope,
                                 private val log: OutputStream?)
	: MutableList<String>, TerminalList {
	override var isCancelled by mutableStateOf<Boolean?>(null)
	override var cancel: (() -> Unit)? = null
	private val internalList = ArrayList<String>()
	private var textMinusLastLine = ""
	override var text by mutableStateOf("")
		private set
	override val size: Int
		get() = internalList.size

	override fun contains(element: String): Boolean {
		return internalList.contains(element)
	}

	override fun containsAll(elements: Collection<String>): Boolean {
		return internalList.containsAll(elements)
	}

	override fun get(index: Int): String {
		return internalList[index]
	}

	override fun indexOf(element: String): Int {
		return internalList.indexOf(element)
	}

	override fun isEmpty(): Boolean {
		return internalList.isEmpty()
	}

	override fun iterator(): MutableIterator<String> {
		return internalList.iterator()
	}

	override fun lastIndexOf(element: String): Int {
		return internalList.lastIndexOf(element)
	}

	override fun add(element: String): Boolean {
		onAdd(element)
		return internalList.add(element)
	}

	override fun add(index: Int, element: String) {
		onAdd(element)
		return internalList.add(index, element)
	}

	override fun addAll(index: Int, elements: Collection<String>): Boolean {
		for (i in elements) {
			onAdd(i)
		}
		return internalList.addAll(index, elements)
	}

	override fun addAll(elements: Collection<String>): Boolean {
		for (i in elements) {
			onAdd(i)
		}
		return internalList.addAll(elements)
	}

	override fun clear() {
		return internalList.clear()
	}

	override fun listIterator(): MutableListIterator<String> {
		return internalList.listIterator()
	}

	override fun listIterator(index: Int): MutableListIterator<String> {
		return internalList.listIterator(index)
	}

	override fun remove(element: String): Boolean {
		return internalList.remove(element)
	}

	override fun removeAll(elements: Collection<String>): Boolean {
		return internalList.removeAll(elements.toSet())
	}

	override fun removeAt(index: Int): String {
		return internalList.removeAt(index)
	}

	override fun retainAll(elements: Collection<String>): Boolean {
		return internalList.retainAll(elements.toSet())
	}

	override fun set(index: Int, element: String): String {
		return internalList.set(index, element).also {
			text = textMinusLastLine + element + "\n"
		}
	}

	override fun subList(fromIndex: Int, toIndex: Int): MutableList<String> {
		return internalList.subList(fromIndex, toIndex)
	}

	fun onAdd(element: String) {
		textMinusLastLine = text
		text += element + "\n"
		scope.launch {
			log?.write((element + "\n").encodeToByteArray())
		}
	}
}

interface TerminalList : MutableList<String> {
	val text: String
	var isCancelled: Boolean?
	var cancel: (() -> Unit)?
}
class TerminalCancelException : RuntimeException()

@Composable
fun Terminal(list: TerminalList) {
	val scrollH = rememberScrollState()
	val scrollV = rememberScrollState()
	LaunchedEffect(list.text) {
		delay(200) // Give it time to re-measure
		scrollV.animateScrollTo(scrollV.maxValue)
		scrollH.animateScrollTo(0)
	}
	Column(modifier = Modifier.fillMaxSize()) {
		Text(list.text, modifier = Modifier
				.fillMaxSize()
				.weight(1f)
				.horizontalScroll(scrollH)
				.verticalScroll(scrollV)
				.padding(10.dp), fontFamily = FontFamily.Monospace
		)
	}
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun TerminalWork(logFile: String? = null, action: suspend (TerminalList) -> Unit) {
	val ctx = LocalContext.current.applicationContext
	LaunchedEffect(Unit) {
		val logDispatcher = Dispatchers.IO.limitedParallelism(1)
		val log = logFile?.let { SuFileOutputStream.open(File(ctx.externalCacheDir, it)) }
		val s = BudgetCallbackList(CoroutineScope(logDispatcher), log)
		StayAliveConnection(ctx, {
			withContext(Dispatchers.Default) {
				try {
					action(s)
				} catch (_: TerminalCancelException) {
					s.add(ctx.getString(R.string.install_canceled))
				} catch (e: Throwable) {
					s.add(ctx.getString(R.string.term_failure))
					s.add(ctx.getString(R.string.dev_details))
					s.add(Log.getStackTraceString(e))
				}
				withContext(logDispatcher) {
					log?.close()
				}
			}
		}, s)
	}
}