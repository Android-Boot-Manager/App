package org.andbootmgr.app.util

import android.util.Log
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.andbootmgr.app.R
import java.io.File
import java.io.FileOutputStream

private class BudgetCallbackList(private val log: FileOutputStream?) : MutableList<String> {
	val internalList = ArrayList<String>()
	var cb: ((String) -> Unit)? = null
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
		return internalList.set(index, element)
	}

	override fun subList(fromIndex: Int, toIndex: Int): MutableList<String> {
		return internalList.subList(fromIndex, toIndex)
	}

	fun onAdd(element: String) {
		log?.write((element + "\n").encodeToByteArray())
		cb?.invoke(element)
	}
}

/* Monospace auto-scrolling text view, fed using MutableList<String>, catching exceptions and running logic on a different thread */
@Composable
fun Terminal(logFile: String? = null, action: (suspend (MutableList<String>) -> Unit)?) {
	val scrollH = rememberScrollState()
	val scrollV = rememberScrollState()
	val scope = rememberCoroutineScope()
	val text = remember { mutableStateOf("") }
	val ctx = LocalContext.current.applicationContext
	LaunchedEffect(Unit) {
		if (action == null && logFile != null) {
			throw IllegalArgumentException("logFile must be null if action is null")
		}
		StayAliveConnection(ctx) { service ->
			if (action != null) {
				val log = logFile?.let { FileOutputStream(File(ctx.externalCacheDir, it)) }
				val s = BudgetCallbackList(log)
				s.cb = { element ->
					scope.launch {
						text.value += element + "\n"
						delay(200) // Give it time to re-measure
						scrollV.animateScrollTo(scrollV.maxValue)
						scrollH.animateScrollTo(0)
					}
				}
				service.startWork({
					withContext(Dispatchers.Default) {
						try {
							action(s)
						} catch (e: Throwable) {
							s.add(ctx.getString(R.string.term_failure))
							s.add(ctx.getString(R.string.dev_details))
							s.add(Log.getStackTraceString(e))
						}
						withContext(Dispatchers.IO) {
							log?.close()
						}
					}
				}, s)
			} else {
				val s = service.workExtra as BudgetCallbackList
				text.value = s.joinToString("\n")
				s.cb = { element ->
					scope.launch {
						text.value += element + "\n"
						delay(200) // Give it time to re-measure
						scrollV.animateScrollTo(scrollV.maxValue)
						scrollH.animateScrollTo(0)
					}
				}
			}
		}
	}
	Text(text.value, modifier = Modifier
		.fillMaxSize()
		.horizontalScroll(scrollH)
		.verticalScroll(scrollV)
		.padding(10.dp), fontFamily = FontFamily.Monospace)
}