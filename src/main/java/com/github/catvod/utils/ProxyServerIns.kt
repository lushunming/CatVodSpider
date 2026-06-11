package com.github.catvod.utils

import com.github.catvod.crawler.SpiderDebug




// import ... 其他包保留你的原有逻辑

object ProxyServerIns {

    private var port = 12345
    private var httpServer: VideoProxyServer? = null
    private var isRunning = false // 新增：运行状态标识

    fun stop() {
        httpServer?.stop()
        isRunning = false
    }

    fun start() {
        // 防止多次调用 start() 导致重复创建
        if (isRunning) {
            SpiderDebug.log("服务已在运行中，端口: $port")
            return
        }

        do {
            try {
                httpServer = VideoProxyServer(port)
                httpServer?.start()

                // --- 关键修改开始 ---
                isRunning = true
                SpiderDebug.log("启动服务成功 on $port")
                break // 启动成功后，必须跳出循环！
                // --- 关键修改结束 ---

            } catch (e: Exception) {
                // 这里可以不用 printStackTrace，否则日志太脏
                SpiderDebug.log("端口 $port 启动失败(${e.message})，尝试下一个端口...")
                httpServer?.stop()
                port++
            }
        } while (port < 20000)

        // 如果循环结束还没跑起来，说明所有端口都试过了
        if (!isRunning) {
            SpiderDebug.log("严重错误：无法启动服务，端口 8082-20000 均不可用")
        }
    }

    fun buildProxyUrl(url: String, headers: Map<String, String>): String? {
        return httpServer?.buildProxyUrl(port, url, headers)
    }
}