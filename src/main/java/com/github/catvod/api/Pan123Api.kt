package com.github.catvod.api

import cn.hutool.core.collection.CollectionUtil
import com.github.catvod.bean.Result
import com.github.catvod.bean.Vod
import com.github.catvod.bean.Vod.VodPlayBuilder
import com.github.catvod.bean.pan123.ShareInfo
import com.github.catvod.crawler.SpiderDebug
import com.github.catvod.net.OkHttp
import com.github.catvod.utils.Json
import com.github.catvod.utils.Launcher
import com.github.catvod.utils.Notify
import com.github.catvod.utils.ProxyServer
import com.github.catvod.utils.ProxyServerIns
import com.github.catvod.utils.Util
import com.google.gson.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.regex.Pattern

/**
 * 123网盘API操作类
 * 提供123网盘的文件分享、下载、播放等功能
 */
object Pan123Api {
    public const val regex =
        "https://(123592\\.com|123912\\.com|123865\\.com|123684\\.com|www\\.123684\\.com|www\\.123865\\.com|www\\.123912\\.com|www\\.123pan\\.com|www\\.123pan\\.cn|www\\.123592\\.com)/s/([^/]+)"

    private const val api = "https://www.123684.com/b/api/share/"
    private const val loginUrl = "https://login.123pan.com/api/user/sign_in"

    private var authToken = ""


    /**
     * 初始化方法，检查登录状态
     */
    fun init() {

    }


    /**
     * 获取认证token
     */
    private fun getAuth(): String {
        if (authToken.isNotBlank()) {
            return authToken
        }
        return Pan123Handler.getAuth()


    }


    /**
     * 登录方法
     */
    fun login(passport: String, password: String): JsonObject? {

        val data = mapOf(
            "passport" to passport, "password" to password, "remember" to true
        )

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
            "Content-Type" to "application/json",
            "App-Version" to "43",
            "Referer" to "https://login.123pan.com/centerlogin?redirect_url=https%3A%2F%2Fwww.123684.com&source_page=website",
            "Accept" to "application/json, text/plain, */*",
            "Origin" to "https://login.123pan.com",
            "Connection" to "keep-alive",
            "Accept-Language" to "zh-CN,zh;q=0.9"
        )

        try {
            val response = OkHttp.post(loginUrl, Json.toJson(data), headers)
            SpiderDebug.log("登录请求信息：")
            SpiderDebug.log("URL: $loginUrl")
            SpiderDebug.log("账号: ${passport}")
            SpiderDebug.log("响应内容: ${response.body}")

            if (response.code == 200) {
                val authData = Json.safeObject(response.body)
                SpiderDebug.log("解析后的响应数据: $authData")
                if (authData.has("data") && authData.getAsJsonObject("data").has("token")) {
                    val token = authData.getAsJsonObject("data").get("token").asString
                    // setAuth(token)
                    SpiderDebug.log("登录成功")
                    authToken = token

                    return authData.get("data").asJsonObject
                }
            }

            throw Exception("登录失败: HTTP状态码=${response.code}, 响应=${response.body}")
        } catch (e: Exception) {
            SpiderDebug.log("登录过程中发生错误: ${e.message}")
            throw e
        }
    }

    /**
     * 解析分享链接，提取分享密钥和提取码
     */
    fun getShareData(url: String): Map<String, String> {
        SpiderDebug.log("123链接：$url")
        var sharePwd = ""
        var lurl = java.net.URLDecoder.decode(url, "UTF-8")

        // 处理提取码格式
        if ("提取码" in lurl && "?" !in lurl) {
            lurl = lurl.replace(Regex("提取码[:：]"), "?")
        }
        if ("提取码" in lurl && "?" in lurl) {
            lurl = lurl.replace(Regex("提取码[:：]?"), "")
        }
        if ("：" in lurl) {
            lurl = lurl.replace("：", "")
        }

        val matcher = Pattern.compile(regex).matcher(lurl)

        // 提取分享密码
        if ("?" in lurl) {
            val queryPart = lurl.split("?")[1]
            val pwdMatcher = Regex("[A-Za-z0-9]+").find(queryPart)
            if (pwdMatcher != null) {
                sharePwd = queryPart.split("=")[1]
            }
        }

        if (matcher.find()) {
            val match = matcher.group(2) ?: ""
            val key = when {
                "?" in match -> match.split("?")[0]
                ".html" in match -> match.replace(".html", "")
                "www" in match -> matcher.group(1) ?: match
                else -> match
            }
            return mapOf("key" to key, "sharePwd" to sharePwd)
        }

        return emptyMap()
    }

    /**
     * 根据分享链接获取文件列表
     */
    fun getFilesByShareUrl(shareKey: String, sharePwd: String): List<ShareInfo> {
        // 获取分享信息
        val cate = getShareInfo(shareKey, sharePwd, 0, 0)
        if (CollectionUtil.isEmpty(cate)) {
            SpiderDebug.log("该分享已被取消，无法访问")
            Notify.show("该分享已被取消，无法访问")
            throw RuntimeException("该分享已被取消，无法访问")
        }

        return cate
    }


    /**
     * 获取分享信息
     */
    private fun getShareInfo(
        shareKey: String, sharePwd: String, next: Int, parentFileId: Long
    ): List<ShareInfo> {


        //文件夹
        val folders = mutableListOf<ShareInfo>()
        //视频
        val videos = mutableListOf<ShareInfo>()


        val url =
            "${api}get?limit=100&next=$next&orderBy=file_name&orderDirection=asc&shareKey=${shareKey.trim()}&SharePwd=${sharePwd.ifEmpty { "" }}&ParentFileId=$parentFileId&Page=1"

        try {
            val response = OkHttp.string(
                url, mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
                )
            )

            val data = Json.safeObject(response)
            if (data.has("code") && data.get("code").asInt == 5103) {
                SpiderDebug.log("123获取文件列表出错：" + data.get("message").asString)
                return emptyList()
            }

            val info = data.getAsJsonObject("data")
            //其下没有文件
            if (info.get("Len").asLong <= 0) {

                return emptyList()
            }
            val nextValue = info.get("Next").asInt
            val infoList = info.getAsJsonArray("InfoList")

            // 处理文件夹
            for (item in infoList) {
                val itemObj = item.asJsonObject
                //文件夹
                if (itemObj.get("Category").asInt == 0) {
                    folders.add(
                        ShareInfo(
                            itemObj.get("FileName").asString,
                            shareKey,
                            sharePwd,
                            nextValue,
                            itemObj.get("FileId").asLong,
                            itemObj["S3KeyFlag"].asString,
                            itemObj["Size"].asLong,
                            itemObj["Etag"].asString,

                            )
                    )
                } else if (itemObj.get("Category").asInt == 2) {
                    videos.add(
                        ShareInfo(
                            itemObj.get("FileName").asString,
                            shareKey,
                            sharePwd,
                            nextValue,
                            itemObj.get("FileId").asLong,
                            itemObj["S3KeyFlag"].asString,
                            itemObj["Size"].asLong,
                            itemObj["Etag"].asString,
                        )
                    )
                }
            }


            // 递归获取子文件夹信息

            for (item in folders) {
                val result = getShareInfo(
                    item.shareKey, item.sharePwd, item.next, item.fileId
                )
                videos.addAll(result)
            }

            return videos
        } catch (e: Exception) {
            SpiderDebug.log("获取分享信息时发生错误: ${e.message}")
            return emptyList()
        }
    }


    /**
     * 获取文件下载链接
     */
    fun getDownload(
        shareKey: String, fileId: Long, s3KeyFlag: String, size: Long, etag: String
    ): String {


        SpiderDebug.log("获取下载链接参数：")
        SpiderDebug.log("ShareKey: $shareKey")
        SpiderDebug.log("FileID: $fileId")
        SpiderDebug.log("S3KeyFlag: $s3KeyFlag")
        SpiderDebug.log("Size: $size")
        SpiderDebug.log("Etag: $etag")
        SpiderDebug.log("Auth Token: ${getAuth().take(30)}...")

        val data = mapOf(
            "ShareKey" to shareKey, "FileID" to fileId, "S3KeyFlag" to s3KeyFlag, "Size" to size, "Etag" to etag
        )

        val url = "${api}download/info"
        SpiderDebug.log("请求URL: $url")
        SpiderDebug.log("请求数据: ${Json.toJson(data)}")

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
            "Content-Type" to "application/json",
            "Authorization" to "Bearer ${getAuth()}",
            "platform" to "android"

        )

        try {
            val response = OkHttp.post(url, Json.toJson(data), headers)
            SpiderDebug.log("响应状态码: ${response.code}")
            SpiderDebug.log("响应内容: ${response.body}")

            if (response.code == 200) {
                val responseData = Json.safeObject(response.body)
                if (responseData.has("data") && responseData.getAsJsonObject("data").has("DownloadURL")) {
                    val encodeUrl = responseData.getAsJsonObject("data").get("DownloadURL").asString
                    return Util.base64Decode(encodeUrl.toHttpUrlOrNull()?.queryParameter("params"))
                }
            }

            throw Exception("获取下载链接失败: HTTP状态码=${response.code}, 响应=${response.body}")
        } catch (e: Exception) {
            SpiderDebug.log("获取下载链接时发生错误: ${e.message}")
            throw e
        }
    }

    /**
     * 获取视频在线播放链接
     */
    fun getLiveTranscoding(
        shareKey: String, fileId: Long, s3KeyFlag: String, size: Long, etag: String
    ): List<Map<String, String>> {


        val url = "https://www.123684.com/b/api/video/play/info?" + "etag=$etag&size=$size&from=1&shareKey=$shareKey"

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
            "Authorization" to "Bearer ${getAuth()}",
            "Content-Type" to "application/json;charset=UTF-8",
            "platform" to "android"
        )

        try {
            val response = OkHttp.string(url, headers)

            val downData = Json.safeObject(response)
            if (downData.has("data") && downData.getAsJsonObject("data").has("video_play_info")) {
                val videoInfo = mutableListOf<Map<String, String>>()
                val videoPlayInfo = downData.getAsJsonObject("data").getAsJsonArray("video_play_info")

                for (item in videoPlayInfo) {
                    val itemObj = item.asJsonObject
                    if (itemObj.has("url") && !itemObj.get("url").isJsonNull) {
                        val resolution = if (itemObj.has("resolution")) itemObj.get("resolution").asString else ""
                        val urlValue = itemObj.get("url").asString

                        videoInfo.add(
                            mapOf(
                                "name" to resolution, "url" to urlValue
                            )
                        )
                    }
                }

                return videoInfo
            }
        } catch (e: Exception) {
            SpiderDebug.log("获取视频播放链接时发生错误: ${e.message}")
        }

        return emptyList()
    }

    fun getPlayFormatList(): Array<String> {
        return arrayOf("原画")
    }

    fun getVod(key: String, sharePwd: String): Vod {
        val list = getFilesByShareUrl(key, sharePwd)

        val builder = VodPlayBuilder()
        for (i in getPlayFormatList().indices) {
            val playUrls = mutableListOf<VodPlayBuilder.PlayUrl>();
            for (item in list) {

                val play = VodPlayBuilder.PlayUrl()
                play.name = "[${Util.getSize(item.Size.toDouble())}]${item.filename}"
                play.url =
                    listOf(item.shareKey, item.fileId, item.S3KeyFlag, item.Size, item.Etag).joinToString("\\+\\+")

                playUrls.add(play)

            }
            builder.append("pan123" + getPlayFormatList()[i], playUrls)
        }
        val buildResult = builder.build();

        val vod = Vod()
        vod.setVodId(key + "++" + sharePwd)
        vod.setVodPic("")
        vod.setVodYear("")
        vod.setVodName("")
        vod.setVodContent("")
        vod.setVodPlayFrom(buildResult.vodPlayFrom)
        vod.setVodPlayUrl(buildResult.vodPlayUrl)
        return vod


    }

    fun playerContent(id: String, flag: String): String {
        val itemJson = id.split("\\+\\+")

        SpiderDebug.log("播放参数：$itemJson")
        val url = getDownload(
            itemJson[0], itemJson[1].toLong(), itemJson[2], itemJson[3].toLong(), itemJson[4]
        )
        val header = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
        )

        return Result.get().url(Launcher.buildProxyUrl(url, header)).octet().header(header).string();


    }


}