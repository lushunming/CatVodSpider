package com.github.catvod.utils

import com.github.catvod.crawler.SpiderDebug
import com.github.catvod.net.OkHttp

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * 视频代理服务器主类
 */
class VideoProxyServer(port: Int) : NanoHTTPD(port) {

    private val client = OkHttpClient.Builder().build()
    private val urlMap = mutableMapOf<String, String>();
    private val headerMap = mutableMapOf<String, Map<String, String>>();

    override fun serve(session: IHTTPSession): Response {
        session.uri

        val key = session.parameters["key"]?.firstOrNull() ?: return newFixedLengthResponse(
            Response.Status.BAD_REQUEST, "text/plain", "Missing 'key' parameter"
        )
        // 1. 获取真实视频地址和header
        val targetUrl = urlMap[key] ?: return newFixedLengthResponse(
            Response.Status.BAD_REQUEST, "text/plain", "Missing 'url' parameter"
        )
        val targetHeader = headerMap[key] ?: return newFixedLengthResponse(
            Response.Status.BAD_REQUEST, "text/plain", "Missing 'header' parameter"
        )

        // 2. 获取视频总大小
        val totalSize = getContentLength(targetUrl, targetHeader)
        SpiderDebug.log("获取视频总大小: $totalSize")
        if (totalSize <= 0L) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Failed to get video size")
        }

        // 3. 解析播放器的 Range 请求（用于支持拖动进度条）
        var startPosition = 0L
        val rangeHeader = session.headers["range"]
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            startPosition = rangeHeader.substringAfter("bytes=").substringBefore("-").toLongOrNull() ?: 0L
        }

        // 4. 创建 16 线程滑动窗口并发流
        val inputStream = SlidingWindowInputStream(
            url = targetUrl,
            headers = targetHeader,
            totalSize = totalSize,
            startPosition = startPosition,
            client = client
        )

        // 5. 构造并返回给播放器响应流
        val status = if (startPosition > 0) Response.Status.PARTIAL_CONTENT else Response.Status.OK
        val responseLength = totalSize - startPosition

        return newFixedLengthResponse(status, "video/mp4", inputStream, responseLength).apply {
            addHeader("Accept-Ranges", "bytes")
            addHeader("Content-Range", "bytes $startPosition-${totalSize - 1}/$totalSize")
        }
    }

    /**
     * 同步获取视频文件总大小
     */
    private fun getContentLength(url: String, headers: Map<String, String>): Long {

        return try {
            val newHeaders: MutableMap<String, String> = java.util.HashMap(headers)
            newHeaders["Range"] = "bytes=0-" + (1024 * 1024 - 1)
            newHeaders["range"] = "bytes=0-" + (1024 * 1024 - 1)
            val res = OkHttp.newCall(url, headers)

            res.use { response ->
                response.header("Content-Length")?.toLongOrNull() ?: 0L
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }

    fun buildProxyUrl(port: Int, url: String, headers: Map<String, String>): String {

        urlMap.clear()
        headerMap.clear()
        val key = Util.MD5(url)
        urlMap[key] = url
        headerMap[key] = headers

        return "http://127.0.0.1:${port}?key=$key"
    }
}

/**
 * 核心：16 线程内存滑动窗口并发流
 */
class SlidingWindowInputStream(
    private val url: String,
    private val headers: Map<String, String>,
    private val totalSize: Long,
    startPosition: Long,
    private val client: OkHttpClient
) : InputStream() {

    private val chunkSize = 1024 * 1024L // 每块 1MB
    private val maxConcurrency = 16      // 维持 16 个后台并发下载

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val chunkMap = ConcurrentHashMap<Int, Deferred<ByteArray>>()

    private var currentPosition = startPosition
    private var currentChunkIndex = -1
    private var currentChunkData: ByteArray? = null
    private var currentChunkOffset = 0

    init {
        // 初始化时预加载
        val startIndex = (startPosition / chunkSize).toInt()
        slideWindow(startIndex)
    }

    private fun slideWindow(startIndex: Int) {
        val maxChunkIndex = (totalSize / chunkSize).toInt()
        for (i in startIndex until startIndex + maxConcurrency) {
            if (i <= maxChunkIndex && !chunkMap.containsKey(i)) {
                chunkMap[i] = scope.async { downloadChunk(i) }
            }
        }
    }

    private fun downloadChunk(index: Int): ByteArray {
        val start = index * chunkSize
        val end = minOf(start + chunkSize - 1, totalSize - 1)
        val header = headers.toMutableMap()
        // 实现分段下载逻辑
        SpiderDebug.log("downloadChunk: $start-$end; ")
        header["Range"] = "bytes=$start-$end"



        return try {
            val res = OkHttp.newCall(url, header)
            res.use { response ->
                response.body.bytes()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ByteArray(0) // 真实环境中应加入重试逻辑
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (currentPosition >= totalSize) return -1

        val chunkIndex = (currentPosition / chunkSize).toInt()

        if (chunkIndex != currentChunkIndex) {
            // 释放上一个块的内存
            if (currentChunkIndex != -1) {
                chunkMap.remove(currentChunkIndex)
            }

            // 阻塞等待当前块下载完成
            currentChunkData = runBlocking {
                chunkMap[chunkIndex]?.await() ?: ByteArray(0)
            }

            currentChunkIndex = chunkIndex
            currentChunkOffset = (currentPosition % chunkSize).toInt()

            // 触发后续块的预加载
            slideWindow(chunkIndex)
        }

        val data = currentChunkData ?: return -1
        val remainingInChunk = data.size - currentChunkOffset
        if (remainingInChunk <= 0) return -1

        val bytesToRead = minOf(len, remainingInChunk)
        System.arraycopy(data, currentChunkOffset, b, off, bytesToRead)

        currentPosition += bytesToRead
        currentChunkOffset += bytesToRead

        return bytesToRead
    }

    override fun read(): Int {
        val b = ByteArray(1)
        return if (read(b, 0, 1) == -1) -1 else b[0].toInt() and 0xFF
    }

    override fun close() {
        super.close()
        // 播放器断开连接时，立即停止所有协程释放内存
        SpiderDebug.log("播放器断开连接时，立即停止所有协程释放内存")
        scope.cancel()
        chunkMap.clear()
        currentChunkData = null
    }
}