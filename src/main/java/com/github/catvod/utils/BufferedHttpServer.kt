package com.github.catvod.utils

import com.github.catvod.crawler.SpiderDebug
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.text.Charsets.UTF_8

class AdvancedHttpServer(
    private val port: Int, private val threadPoolSize: Int = Runtime.getRuntime().availableProcessors()
) {
    private val serverSocket: ServerSocket
    private val threadPool: ExecutorService
    private var isRunning = false
    private val routes = mutableMapOf<String, (Request, Response) -> Unit>()

    init {
        serverSocket = ServerSocket(port)
        threadPool = Executors.newFixedThreadPool(threadPoolSize)
    }

    fun addRoutes(path: String, handler: (Request, Response) -> Unit) {
        routes[path] = handler
    }


    fun start() {
        isRunning = true
        SpiderDebug.log("服务器已启动，监听端口: $port")

        while (isRunning) {
            try {
                val clientSocket = serverSocket.accept()
                threadPool.execute { handleRequest(clientSocket) }
            } catch (e: IOException) {

                if (isRunning) {
                    e.printStackTrace(); SpiderDebug.log("出错：" + e.message)
                }

            }
        }
    }

    private fun handleRequest(clientSocket: Socket) {
        clientSocket.use {
            val reader = BufferedReader(InputStreamReader(it.inputStream, UTF_8))
            val writer = BufferedOutputStream(it.outputStream)

            try {

                // 解析请求行
                val requestLine = reader.readLine() ?: ""
                SpiderDebug.log("requestLine: $requestLine")
                if (requestLine.isBlank()) {
                    return // 空请求直接返回
                }
                val (method, path, _) = parseRequestLine(requestLine)

                // 解析路径和查询参数
                val (basePath, queryParams) = parsePath(path)

                // 读取请求头
                val headers = mutableMapOf<String, String>()
                var line: String?
                while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                    val colonIndex = line!!.indexOf(':')
                    if (colonIndex > 0) {
                        headers[line!!.substring(0, colonIndex).trim()] = line!!.substring(colonIndex + 1).trim()
                    }
                }

                // 解析请求体参数
                val contentLength = headers["Content-Length"]?.toIntOrNull() ?: 0
                val requestBody = if (contentLength > 0) {
                    buildString {
                        repeat(contentLength) {
                            val char = reader.read().takeIf { it != -1 }?.toChar() ?: return@buildString
                            append(char)
                        }
                    }
                } else ""

                val contentType = headers["Content-Type"] ?: ""
                val bodyParams = parseRequestBody(contentType, requestBody)

                // 创建请求对象
                val request = Request(
                    method = method,
                    path = basePath,
                    queryParams = queryParams,
                    headers = headers,
                    body = requestBody,
                    bodyParams = bodyParams
                )

                // 创建响应处理器
                val response = Response(writer)

                // 路由处理
                routeRequest(request, response)

                response.flush()
            } catch (e: IOException) {
                e.printStackTrace()
                SpiderDebug.log("IO错误: " + e.message)
            } catch (e: Exception) {
                e.printStackTrace()
                SpiderDebug.log("处理请求时发生未知错误: " + e.message)
            }
        }
    }

    private fun routeRequest(request: Request, response: Response) {
        val route = routes[request.path]
        if (route == null) {
            handleNotFound(response)
        } else {
            route.invoke(request, response)
        }
    }


    private fun handleNotFound(response: Response) {
        response.setStatusCode(404)
        response.setContentType("text/html")
        response.start()
        response.write("<html><body><h1>404 Not Found</h1></body></html>")
    }

    private fun parseRequestLine(requestLine: String): Triple<String, String, String> {
        val parts = requestLine.split(" ", limit = 3)
        return when {
            parts.size >= 3 -> Triple(parts[0], parts[1], parts[2])
            parts.size == 2 -> Triple(parts[0], parts[1], "HTTP/1.1")
            else -> Triple("GET", "/", "HTTP/1.1")
        }
    }

    private fun parsePath(path: String): Pair<String, Map<String, String>> {
        val queryIndex = path.indexOf('?')
        return if (queryIndex >= 0) {
            Pair(path.substring(0, queryIndex), parseQueryString(path.substring(queryIndex + 1)))
        } else {
            Pair(path, emptyMap())
        }
    }

    private fun parseQueryString(queryString: String): Map<String, String> {
        return queryString.split("&").filter { it.isNotEmpty() }.map { param ->
            val equalsIndex = param.indexOf('=')
            if (equalsIndex >= 0) {
                val key = URLDecoder.decode(param.substring(0, equalsIndex), "UTF-8")
                val value = URLDecoder.decode(param.substring(equalsIndex + 1), "UTF-8")
                key to value
            } else {
                URLDecoder.decode(param, "UTF-8") to ""
            }
        }.toMap()
    }

    private fun parseRequestBody(contentType: String, body: String): Map<String, String> {
        return when {
            contentType.contains("application/x-www-form-urlencoded") -> parseQueryString(body)

            contentType.contains("multipart/form-data") -> parseMultipartFormData(contentType, body)

            else -> emptyMap()
        }
    }

    private fun parseMultipartFormData(contentType: String, body: String): Map<String, String> {
        // 简化的multipart/form-data解析，实际实现需要处理boundary等
        return emptyMap()
    }

    // 添加优雅关闭
    fun stop(graceful: Boolean = true) {
        isRunning = false
        if (graceful) {
            threadPool.shutdown()
            try {
                if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow()
                }
            } catch (e: InterruptedException) {
                threadPool.shutdownNow()
            }
        } else {
            threadPool.shutdownNow()
        }
        serverSocket.close()
    }

    data class Request(
        val method: String,
        val path: String,
        val queryParams: Map<String, String>,
        val headers: Map<String, String>,
        val body: String,
        val bodyParams: Map<String, String>
    )

    class Response(private val writer: BufferedOutputStream) {
        private val headers = mutableMapOf<String, String>()
        private var contentType = "text/plain"
        private var statusCode = 200
        private var started = AtomicBoolean(false)

        fun setContentType(contentType: String) {
            this.contentType = contentType
        }

        fun setHeader(name: String, value: String) {
            headers[name] = value
        }

        fun setStatusCode(statusCode: Int) {
            this.statusCode = statusCode
        }

        private val codeMap = mutableMapOf<Int, String>(
            206 to "Partial Content",
            200 to "OK",
            404 to "NOT FOUND",
            400 to "BAD REQUEST",
            401 to "UNAUTHORIZED",
            403 to "FORBIDDEN",
            405 to "METHOD NOT ALLOWED",
            408 to "REQUEST TIMEOUT",
            413 to "PAYLOAD TOO LARGE",
            414 to "URI TOO LONG",
            415 to "UNSUPPORTED MEDIA TYPE",
            429 to "TOO MANY REQUESTS",
            500 to "INTERNAL SERVER ERROR",
            501 to "NOT IMPLEMENTED",
            503 to "SERVICE UNAVAILABLE",
            504 to "GATEWAY TIMEOUT",
            505 to "HTTP VERSION NOT SUPPORTED",
            507 to "INSUFFICIENT STORAGE",
            511 to "NETWORK AUTHENTICATION REQUIRED"
        )

        fun start() {
            if (started.compareAndSet(false, true)) {
                writer.write("HTTP/1.1 $statusCode ${codeMap[statusCode]}\r\n".toByteArray(UTF_8))

                writer.write("Content-Type: $contentType; charset=utf-8\r\n".toByteArray(UTF_8))

                headers.forEach { (name, value) ->
                    writer.write("$name: $value\r\n".toByteArray(UTF_8))
                }

                writer.write("\r\n".toByteArray(UTF_8))
                writer.flush()
            }
        }

        fun write(content: ByteArray) {
            if (!started.get()) {
                start()
            }
            writer.write(content)
        }

        fun write(content: String) {
            if (!started.get()) {
                start()
            }
            writer.write(content.toByteArray(UTF_8))
        }

        fun flush() {
            writer.flush()
        }
    }
}

