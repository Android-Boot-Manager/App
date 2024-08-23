package org.andbootmgr.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.HashingSink
import okio.Source
import okio.buffer
import okio.sink
import org.andbootmgr.app.HashMismatchException
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class AbmOkHttp(private val url: String, private val f: File, private val hash: String?,
                pl: ((bytesRead: Long, contentLength: Long, done: Boolean) -> Unit)?) {
	private val client by lazy { OkHttpClient().newBuilder().readTimeout(1L, TimeUnit.HOURS)
		.let { c ->
			if (pl != null)
				c.addNetworkInterceptor {
					val originalResponse: Response = it.proceed(it.request())
					return@addNetworkInterceptor originalResponse.newBuilder()
						.body(ProgressResponseBody(originalResponse.body!!, pl))
						.build()
				}
			else c
		}.build() }
	private var call: Call? = null

	suspend fun run(): Boolean {
		return withContext(Dispatchers.IO) {
			val request =
				Request.Builder().url(url).build()
			val call = client.newCall(request)
			this@AbmOkHttp.call = call
			val response = call.execute()
			val rawSink = f.sink()
			val sink = if (hash != null) HashingSink.sha256(rawSink) else rawSink
			val buffer = sink.buffer()
			buffer.writeAll(response.body!!.source())
			buffer.close()
			val realHash = if (hash != null) (sink as HashingSink).hash.hex() else null
			if (!call.isCanceled()) {
				if (hash != null && realHash != hash) {
					try {
						f.delete()
					} catch (_: Exception) {}
					throw HashMismatchException("hash $realHash does not match expected hash $hash")
				}
				return@withContext true
			}
			return@withContext false
		}
	}

	fun cancel() {
		call?.cancel()
		f.delete()
	}

	private class ProgressResponseBody(
		private val responseBody: ResponseBody,
		private val progressListener: (bytesRead: Long, contentLength: Long, done: Boolean) -> Unit
	) :
		ResponseBody() {
		private var bufferedSource: BufferedSource? = null
		override fun contentType(): MediaType? {
			return responseBody.contentType()
		}

		override fun contentLength(): Long {
			return responseBody.contentLength()
		}

		override fun source(): BufferedSource {
			if (bufferedSource == null) {
				bufferedSource = source(responseBody.source()).buffer()
			}
			return bufferedSource!!
		}

		private fun source(source: Source): Source {
			return object : ForwardingSource(source) {
				var totalBytesRead = 0L

				@Throws(IOException::class)
				override fun read(sink: Buffer, byteCount: Long): Long {
					val bytesRead = super.read(sink, byteCount)
					// read() returns the number of bytes read, or -1 if this source is exhausted.
					totalBytesRead += if (bytesRead != -1L) bytesRead else 0
					progressListener(
						totalBytesRead,
						responseBody.contentLength(),
						bytesRead == -1L
					)
					return bytesRead
				}
			}
		}
	}
}