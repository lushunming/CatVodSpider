package com.github.catvod.api

import com.github.catvod.bean.BD.Cache
import com.github.catvod.bean.yun.User
import com.github.catvod.crawler.SpiderDebug
import com.github.catvod.net.OkHttp
import com.github.catvod.spider.Init
import com.github.catvod.utils.*
import okhttp3.Headers.Companion.toHeaders
import okhttp3.Request
import org.apache.commons.lang3.StringEscapeUtils
import org.apache.commons.lang3.StringUtils
import java.io.File
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.swing.*
import kotlin.concurrent.Volatile


class BaiDuYunHandler private constructor() {
    private val cache: Cache
    private var service: ScheduledExecutorService? = null
    private var dialog: JDialog? = null
    private var cookies = ""
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 12; SM-X800) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/101.0.4951.40 Safari/537.36",
        "Accept" to "application/json, text/plain, */*",
        "Content-Type" to "application/x-www-form-urlencoded",
        "Origin" to "https://pan.baidu.com",
        "Referer" to "https://pan.baidu.com/"
    )

    fun getCache(): File {
        return Path.tv("bd")
    }

    init {
        cache = Cache.objectFrom(Path.read(getCache()))
    }

    private object Loader {
        @Volatile
        var INSTANCE: BaiDuYunHandler = BaiDuYunHandler()
    }

    val token: String
        get() {
            val user: User = cache.getUser()
            return user.getCookie()
            //return "cGM6MTg4OTY3ODE2MDE6eTM1Tjd1dG58MXxSQ1N8MTc1NDQ2OTgwNzEyOXxzMlN0T1VEV3lOVmF5V3pNbGFfM2tJbVp1ZmlqSHBqaEhTSzVyNHZqVXNRLmlhV3loSUxHNDFkMUI5N1BqXzhWN0dtVWtKLnBTclhpNGpZU1EuTGZWMTV3MVFoZmNpcEVoZkxUV2tvYjB0bkFTYV9RTUhhaHhveWx6YkdmcEhQdjNCS1lrbnp1LkxaWDdKOE40YkNNRjkzT3piNmx2Y0d3TWdVUkl5b18ubVUt";
        }


    companion object {
        @JvmStatic
        fun get(): BaiDuYunHandler {
            return Loader.INSTANCE
        }
    }

    @Throws(java.lang.Exception::class)
    fun startScan(): ByteArray {
        val result = loginByQRCode()
        // Step 2: Get QR Code
        val byteStr: ByteArray = downloadQRCode(result["qrCodeImageUrl"]!!);

        Init.run(Runnable { showQRCode(byteStr) })
        // Step 3: Check login status

        Init.execute(Runnable {
            startService(
                result["sign"]!!
            )
        })
        return byteStr
    }

    @Throws(IOException::class)
    fun downloadQRCode(url: String): ByteArray {
        val headers: MutableMap<String, String> = HashMap<String, String>()
        headers.put(
            "user-agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/99.0.4844.51 Safari/537.36"
        )


        val request = Request.Builder().url(url).headers(headers.toHeaders()).build()
        val response = OkHttp.newCall(request)
        if (response.code == 200) {
            return response.body!!.bytes()
        }
        return "".toByteArray()
    }

    fun loginByQRCode(): Map<String, String> {
        return try {
            // 获取登录二维码
            val timestamp = System.currentTimeMillis()
            val qrCodeUrl = "https://passport.baidu.com/v2/api/getqrcode?lp=pc&_=$timestamp"

            val response = OkHttp.string(qrCodeUrl, emptyMap(), headers)
            val json = Json.safeObject(response)

            if (json["errno"].asInt != 0) {
                return mapOf("error" to "获取登录二维码错误, code: ${json["errno"].asInt}")
            }

            val sign = json["sign"].asString
            val imgurl = json["imgurl"].asString
            val qrCodeImageUrl = "https://$imgurl"

            val qrLoginUrl =
                "https://wappass.baidu.com/wp/?qrlogin&t=$timestamp" + "&error=0&sign=$sign&cmd=login&lp=pc&tpl=netdisk&uaonly=" + "&client_id=&adapter=3&client=&qrloginfrom=pc&wechat=0&traceid="

            // 返回二维码信息供前端显示
            val result = mapOf(
                "qrCodeImageUrl" to qrCodeImageUrl, "qrLoginUrl" to qrLoginUrl, "sign" to sign
            )

            result
        } catch (e: Exception) {
            mapOf("error" to "获取二维码失败: ${e.message}")
        }
    }

    fun checkQRLoginStatus(sign: String): Map<String, Any> {
        return try {
            val timestamp = System.currentTimeMillis()
            val checkUrl =
                "https://passport.baidu.com/channel/unicast?channel_id=$sign" + "&tpl=netdisk&callback=&apiver=v3&tt=$timestamp&_=$timestamp"

            val response = OkHttp.string(checkUrl, emptyMap(), headers)
            val cleanResponse = response.trim('(', ' ', '\n', ')')
            val json = Json.safeObject(cleanResponse)

            if (json["errno"].asInt == 0) {
                SpiderDebug.log("百度扫码成功")
                val channelV = json["channel_v"].asString
                val channelJson = Json.safeObject(channelV)

                if (channelJson["status"].asInt == 0) {
                    val bduss = channelJson["v"].asString

                    // 执行登录
                    val loginTimestamp = System.currentTimeMillis()
                    val loginUrl =
                        "https://passport.baidu.com/v3/login/main/qrbdusslogin?" + "v=$loginTimestamp&bduss=$bduss&u=&loginVersion=v4&qrcode=1&tpl=netdisk&apiver=v3" + "&tt=$loginTimestamp&traceid=&callback=bd__cbs__cupstt"

                    val loginResponse = OkHttp.get(loginUrl, emptyMap(), headers)
                    val cleanLoginResponse = loginResponse.body.substringAfter("(").substringBeforeLast(")")
                    val loginJson = Json.safeObject(StringEscapeUtils.unescapeHtml4(cleanLoginResponse))

                    if (loginJson.has("errInfo") && loginJson["errInfo"].asJsonObject["no"].asString == "0") {
                        // 登录成功，设置cookie
                        SpiderDebug.log("百度登录成功，设置cookie：${bduss}")
                        cookies = "BDUSS=$bduss"
                        cookies = generateCooike(loginResponse.resp["set-cookie"])

                        if (cookies.isNotEmpty()) {
                            cache.setUser(User.objectFrom(this.cookies))
                            //停止检验线程，关闭弹窗
                            stopService()
                            Notify.show("百度登录成功")

                        }


                        mapOf("success" to true, "bduss" to bduss)
                    } else {
                        mapOf("error" to "登录失败: $cleanLoginResponse")
                    }
                } else {
                    mapOf("status" to channelJson["status"].asInt)
                }
            } else {
                mapOf("errno" to json["errno"].asInt)
            }
        } catch (e: Exception) {
            mapOf("error" to "检查登录状态失败: ${e.message}")
        }
    }

    fun generateCooike(cookies: List<String>?): String {
        if (cookies == null || cookies.isEmpty()) {
            return ""
        }

        val cookieList: MutableList<String?> = ArrayList<String?>()
        for (cookie in cookies) {
            cookieList.add(cookie.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0])
        }
        return StringUtils.join(cookieList, ";")
    }

    /**
     * 显示qrcode
     *
     * @param base64Str
     */


    private fun showQRCode(bytes: ByteArray) {
        try {
            val size = 300
            SwingUtilities.invokeLater(Runnable {
                val image = QRCode.Bytes2Bimap(bytes)
                val jPanel = JPanel()
                jPanel.setSize(Swings.dp2px(size), Swings.dp2px(size))
                jPanel.add(JLabel(ImageIcon(image)))
                dialog = Util.showDialog(jPanel, "请使用百度网盘App扫描二维码")
            })
            Util.notify("请使用百度网盘App扫描二维码")
        } catch (ignored: java.lang.Exception) {
        }
    }


    private fun dismiss() {
        try {
            if (dialog != null) {
                dialog!!.setVisible(false)
                dialog!!.dispose()
            }
        } catch (ignored: java.lang.Exception) {
        }
    }

    private fun stopService() {
        service?.shutdownNow()
        Init.run(Runnable { this.dismiss() })
    }

    fun startService(sign: String) {
        SpiderDebug.log("----start 百度 token  service")

        service = Executors.newScheduledThreadPool(1)

        service?.scheduleWithFixedDelay(Runnable {
            try {
                SpiderDebug.log("----check百度tatus中")

                checkQRLoginStatus(sign)
            } catch (e: UnsupportedEncodingException) {
                throw RuntimeException(e)
            }
        }, 3, 3, TimeUnit.SECONDS)
    }
}