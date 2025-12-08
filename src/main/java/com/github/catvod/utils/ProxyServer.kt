package com.github.catvod.utils

import com.github.catvod.crawler.SpiderDebug
import com.github.catvod.net.OkHttp
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.ByteArrayInputStream


object ProxyServer {
    private val THREAD_NUM = Runtime.getRuntime().availableProcessors()
    private val partSize = 1024 * 1024
    private var port = 12345
    private var httpServer: AdvancedHttpServer? = null
    private val infos = mutableMapOf<String, Map<String, List<String>>>();
    private val urlMap = mutableMapOf<String, String>();
    private val headerMap = mutableMapOf<String, Map<String, String>>();


    fun stop() {
        httpServer?.stop()
    }

    fun start() {

        do {
            try {
                httpServer = AdvancedHttpServer(port)
                httpServer?.addRoutes("/") { _, response ->
                    run {
                        response.setContentType("text/html")
                        response.start()
                        response.write("Hello, world!")
                    }
                };
                httpServer?.addRoutes("/proxy") { req, response ->
                    run {
                        val key = req.queryParams["key"];
                        val url = urlMap[key]
                        val header = headerMap[key]

                        if (url != null && header != null) {
                            proxyAsync(url, header, req, response)
                        }
                    }
                }
                httpServer?.start()

            } catch (e: Exception) {
                e.printStackTrace()
                SpiderDebug.log("启动服务出错:" + e.message)

                port++
                httpServer?.stop()
            }
        } while (port < 20000)
        SpiderDebug.log("启动服务 on $port")

    }

    private fun proxyAsync(
        url: String,
        headers: Map<String, String>,
        request: AdvancedHttpServer.Request,
        response: AdvancedHttpServer.Response
    ) {
        runBlocking {
            val channels = List(THREAD_NUM) { Channel<ByteArray>() }
            SpiderDebug.log("--proxyAsync url:  $url")
            SpiderDebug.log("--proxyAsync headers:  ${Json.toJson(headers)}")

            try {
                SpiderDebug.log("--proxyMultiThread: THREAD_NUM: $THREAD_NUM")


                var rangeHeader = request.headers["Range"]
                //没有range头
                if (rangeHeader.isNullOrEmpty()) {
                    // 处理初始请求
                    rangeHeader = "bytes=0-"
                }
                headers.toMutableMap().apply {
                    put("Range", rangeHeader)
                }

                // 解析范围请求
                val (startPoint, endPoint) = parseRangePoint(
                    rangeHeader
                )

                //缓存response header
                var info = infos[url]
                if (info == null) {
                    info = getInfo(url, headers)
                    infos[url] = info
                }

                SpiderDebug.log("startPoint: $startPoint; endPoint: $endPoint")
                val contentLength = getContentLength(info)
                SpiderDebug.log("contentLength: $contentLength")
                val finalEndPoint = if (endPoint == -1L) contentLength - 1 else endPoint
                response.setContentType("text/html")



                response.setHeader("Connection", "keep-alive")
                response.setHeader("Access-Control-Allow-Origin", "*")
                response.setHeader(
                    "Content-Length", (finalEndPoint - startPoint + 1).toString()
                )
                response.setHeader(
                    "Content-Range", "bytes $startPoint-$finalEndPoint/$contentLength"
                )
                info["Content-Type"]?.get(0)?.let { response.setHeader("Content-Type", it) }

                response.setStatusCode(206)
                response.start()
                // 使用流式响应

                var currentStart = startPoint


                // 启动生产者协程下载数据

                val producerJob = mutableListOf<Job>()

                while (currentStart <= finalEndPoint) {
                    producerJob.clear()
                    // 创建通道用于接收数据块

                    for (i in 0 until THREAD_NUM) {

                        if (currentStart > finalEndPoint) break
                        val chunkStart = currentStart
                        val chunkEnd = minOf(currentStart + partSize - 1, finalEndPoint)
                        producerJob += CoroutineScope(Dispatchers.IO).launch {
                            // 异步下载数据块
                            val data = getVideoStream(chunkStart, chunkEnd, url, headers)
                            //如果是0开始，且检测到恶意头，那么就把数据截断
                            if (chunkStart == 0L) {
                                val offset = detectMaliciousPrefix(data)
                                channels[i].send(data.copyOfRange(offset, data.size))
                            } else {
                                channels[i].send(data)
                            }
                        }
                        currentStart = chunkEnd + 1
                    }
                    for ((index, _) in producerJob.withIndex()) {

                        val data = channels[index].receive()
                        SpiderDebug.log("Received chunk: ${data.size} bytes")
                        response.write(data)
                    }

                }
                channels.forEach { it.close() }
            } catch (e: Exception) {
                SpiderDebug.log("proxyAsync error: ${e.message}")
                e.printStackTrace()
                response.write("proxyAsync error: ${e.message}")

            } finally {
                // channels.forEach { it.close() }

            }
        }
    }


    fun detectMaliciousPrefix(data: ByteArray): Int {


        val buffer = ByteArray(64) // 读取前64字节足够
        ByteArrayInputStream(data).use { fis ->
            fis.read(buffer)
        }

        // 检查是否以合法魔数开头
        if (isValidVideoHeader(buffer)) {
            return 0 // 正常，无恶意前缀
        }

        // 在后续位置查找合法魔数（比如最多跳过前256字节）
        val searchLimit = minOf(256, data.size)
        val searchBuffer = ByteArray(searchLimit)
        ByteArrayInputStream(data).use { fis ->
            fis.read(searchBuffer)
        }

        // 尝试从偏移1开始查找合法视频头
        for (offset in 1 until searchLimit - 16) {
            if (isValidVideoHeader(searchBuffer, offset)) {
                SpiderDebug.log("发现合法视频头位于偏移 $offset，疑似被插入恶意前缀！")
                return offset
            }
        }

        return 0 // 未找到合法头，可能是损坏或非视频文件
    }

    // 判断从指定偏移开始是否是合法视频头
    fun isValidVideoHeader(data: ByteArray, offset: Int = 0): Boolean {
        if (data.size - offset < 8) return false

        // MP4 / MOV: ... ftyp
        if (offset + 8 <= data.size && data[offset + 4].toInt() == 0x66 && // 'f'
            data[offset + 5].toInt() == 0x74 && // 't'
            data[offset + 6].toInt() == 0x79 && // 'y'
            data[offset + 7].toInt() == 0x70     // 'p'
        ) {
            // 还可进一步校验前4字节是否为合法 size（>=8 且合理）
            val size =
                (data[offset].toLong() and 0xFF shl 24) or (data[offset + 1].toLong() and 0xFF shl 16) or (data[offset + 2].toLong() and 0xFF shl 8) or (data[offset + 3].toLong() and 0xFF)
            if (size >= 8 && size <= 0x100000) return true
        }

        // AVI: RIFF
        if (offset + 4 <= data.size && data[offset] == 0x52.toByte() && data[offset + 1] == 0x49.toByte() && data[offset + 2] == 0x46.toByte() && data[offset + 3] == 0x46.toByte()) return true

        // MKV
        if (offset + 4 <= data.size && data[offset] == 0x1A.toByte() && data[offset + 1] == 0x45.toByte() && data[offset + 2] == 0xDF.toByte() && data[offset + 3] == 0xA3.toByte()) return true

        // FLV
        if (offset + 4 <= data.size && data[offset] == 0x46.toByte() && data[offset + 1] == 0x4C.toByte() && data[offset + 2] == 0x56.toByte() && data[offset + 3] == 0x01.toByte()) return true

        return false
    }


    private fun queryToMap(query: String?): Map<String, String>? {
        if (query == null) {
            return null
        }
        val result: MutableMap<String, String> = HashMap()
        for (param in query.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            val entry = param.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (entry.size > 1) {
                result[entry[0]] = entry[1]
            } else {
                result[entry[0]] = ""
            }
        }
        return result
    }

    // 辅助函数（需要实现）
    private fun parseRangePoint(rangeHeader: String): Pair<Long, Long> {
        // 实现范围解析逻辑
        val regex = """bytes=(\d+)-(\d*)""".toRegex()
        val match = regex.find(rangeHeader) ?: return 0L to -1L
        val start = match.groupValues[1].toLong()
        val end = match.groupValues[2].takeIf { it.isNotEmpty() }?.toLong() ?: -1L
        return start to end
    }

    private fun getInfo(
        url: String?, headers: Map<String, String>
    ): Map<String, List<String>> {
        val newHeaders: MutableMap<String, String> = java.util.HashMap(headers)
        newHeaders["Range"] = "bytes=0-" + (1024 * 1024 - 1)
        newHeaders["range"] = "bytes=0-" + (1024 * 1024 - 1)
        val res = OkHttp.newCall(url, headers)
        res.body?.close()
        return res.headers.toMultimap()
    }

    private fun getContentLength(info: Map<String, List<String>>): Long {
        // 实现获取内容长度逻辑
        return info["Content-Length"]?.get(0)?.toLong() ?: 0L
    }

    private fun getVideoStream(
        start: Long, end: Long, url: String, headers: Map<String, String>
    ): ByteArray {
        val header = headers.toMutableMap()
        // 实现分段下载逻辑
        SpiderDebug.log("getVideoStream: $start-$end; ")
        header["Range"] = "bytes=$start-$end"
        val res = OkHttp.newCall(url, header)
        val body = res.body
        return body.bytes()
    }

    fun buildProxyUrl(url: String, headers: Map<String, String>): String {
        urlMap.clear()
        headerMap.clear()
        val key = Util.MD5(url)
        urlMap[key] = url
        headerMap[key] = headers

        return "http://127.0.0.1:$port/proxy?key=$key"
    }


}


/**
package com.github.catvod.utils

import com.github.catvod.crawler.SpiderDebug
import com.github.catvod.net.OkHttp
import com.google.gson.Gson
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import okhttp3.Response
import java.nio.ByteBuffer
import java.nio.charset.Charset

object KtorServer {

private val THREAD_NUM = Runtime.getRuntime().availableProcessors() * 2
private val infos = mutableMapOf<String, Array<Any>>()
var ser: io.ktor.server.engine.ApplicationEngine? = null
var port = 10010

//每个片1MB
private val partSize = 1024 * 1024 * 1
fun init() {

do {
try {
ser = embeddedServer(Netty, port) {
install(CallLogging)


routing {
get("/") {
call.respondText("ktor running on $port", ContentType.Text.Plain)
}
get("/proxy") {
SpiderDebug.log("代理中: ${call.parameters["url"]}")


val url = Util.base64Decode(call.parameters["url"])
val header: Map<String, String> = Gson().fromJson<Map<String, String>>(
Util.base64Decode(call.parameters["headers"]),
MutableMap::class.java
)
proxyAsync(
url, header, call
)
}
}
}.start(wait = true)

} catch (e: Exception) {
SpiderDebug.log("start server e:" + e.message)
++port
ser?.stop()
}
} while (port < 13000)
SpiderDebug.log("ktorServer start on $port")
}

 */
/** 启动服务器 *//*

    fun start() {

        CoroutineScope(Dispatchers.IO).launch { init() }
    }


    */
/** 停止服务器 *//*

    fun stop() {
        ser?.stop(1_000, 2_000)
    }



    */
/**
 * 获取是否分片信息，顺带请求一个1MB块
 *//*

    @Throws(java.lang.Exception::class)
    fun getInfo(url: String?, headers: Map<String, String>): Array<Any> {
        val newHeaders: MutableMap<String, String> = java.util.HashMap(headers)
        newHeaders["Range"] = "bytes=0-" + (1024 * 1024 - 1)
        newHeaders["range"] = "bytes=0-" + (1024 * 1024 - 1)
        val info = ProxyVideo.proxy(url, newHeaders)
        return info
    }

    private suspend fun proxyAsync(
        url: String, headers: Map<String, String>, call: ApplicationCall
    ) {
        val channels = List(THREAD_NUM) { Channel<ByteArray>() }
        try {
            SpiderDebug.log("--proxyMultiThread: THREAD_NUM: $THREAD_NUM")


            var rangeHeader = call.request.headers[HttpHeaders.Range]
            //没有range头
            if (rangeHeader.isNullOrEmpty()) {
                // 处理初始请求
                rangeHeader = "bytes=0-"
            }
            headers.toMutableMap().apply {
                put(HttpHeaders.Range, rangeHeader)
            }

            // 解析范围请求
            val (startPoint, endPoint) = parseRangePoint(
                rangeHeader
            )
            SpiderDebug.log("startPoint: $startPoint; endPoint: $endPoint")
            val contentLength = getContentLength(url, headers)
            SpiderDebug.log("contentLength: $contentLength")
            val finalEndPoint = if (endPoint == -1L) contentLength - 1 else endPoint

            call.response.headers.apply {
                append(HttpHeaders.Connection, "keep-alive")
                append(HttpHeaders.ContentLength, (finalEndPoint - startPoint + 1).toString())
                append(HttpHeaders.ContentRange, "bytes $startPoint-$finalEndPoint/$contentLength")
            }
            call.response.status(HttpStatusCode.PartialContent)

            // 使用流式响应
            call.respondBytesWriter() {
                var currentStart = startPoint


                // 启动生产者协程下载数据

                val producerJob = mutableListOf<Job>()

                while (currentStart <= finalEndPoint) {
                    producerJob.clear()
                    // 创建通道用于接收数据块

                    for (i in 0 until THREAD_NUM) {

                        if (currentStart > finalEndPoint) break
                        val chunkStart = currentStart
                        val chunkEnd = minOf(currentStart + partSize - 1, finalEndPoint)
                        producerJob += CoroutineScope(Dispatchers.IO).launch {
                            // 异步下载数据块
                            val data = getVideoStream(chunkStart, chunkEnd, url, headers)
                            channels[i].send(data)

                        }
                        currentStart = chunkEnd + 1
                    }
                    for ((index, job) in producerJob.withIndex()) {

                        val data = channels[index].receive()
                        SpiderDebug.log("Received chunk: ${data.size} bytes")
                        writeFully(ByteBuffer.wrap(data))
                    }
                }


            }
        } catch (e: Exception) {
            SpiderDebug.log("error: ${e.message}")
            call.respondText("error: ${e.message}", ContentType.Text.Plain)
        } finally {
            channels.forEach { it.close() }
        }
    }


    // 辅助函数（需要实现）
    private fun parseRangePoint(rangeHeader: String): Pair<Long, Long> {
        // 实现范围解析逻辑
        val regex = """bytes=(\d+)-(\d*)""".toRegex()
        val match = regex.find(rangeHeader) ?: return 0L to -1L
        val start = match.groupValues[1].toLong()
        val end = match.groupValues[2].takeIf { it.isNotEmpty() }?.toLong() ?: -1L
        return start to end
    }

    private fun getContentLength(url: String, headers: Map<String, String>): Long {
        // 实现获取内容长度逻辑
        val res = OkHttp.newCall(url, headers)
        res.body()?.close()
        return res.headers(HttpHeaders.ContentLength)[0]?.toLong() ?: 0L
    }

    private suspend fun getVideoStream(
        start: Long, end: Long, url: String, headers: Map<String, String>
    ): ByteArray {
        val header = headers.toMutableMap()
        // 实现分段下载逻辑
        SpiderDebug.log("getVideoStream: $start-$end; ")
        header[HttpHeaders.Range] = "bytes=$start-$end"
        val res = OkHttp.newCall(url, header)
        val body = res.body()
        return body?.bytes() ?: ByteArray(0)
    }


    private fun downloadRange(
        url: String, headerNew: Map<String, String>
    ): Response? = OkHttp.newCall(url, headerNew)
}
*/
