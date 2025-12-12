package com.github.catvod.spider

import com.github.catvod.api.BaiduDrive
import com.github.catvod.api.BaiduDrive.getVod
import com.github.catvod.api.BaiduDrive.playerContent
import com.github.catvod.api.BaiduDrive.setCookie
import com.github.catvod.bean.Result
import com.github.catvod.bean.Vod
import com.github.catvod.crawler.Spider
import com.github.catvod.crawler.SpiderDebug
import com.github.catvod.utils.Json
import com.github.catvod.utils.Util
import kotlinx.coroutines.runBlocking

/**
 * @author ColaMint & Adam & FongMi
 */
class BaiDuPan : Spider() {
    @Throws(Exception::class)
    override fun init(extend: String) {
        setCookie(extend)
    }

    /**
     * ids  為 share_link
     * @param ids
     * @return
     * @throws Exception
     */
    @Throws(Exception::class)
    override fun detailContent(ids: MutableList<String>): String? {
        var vod: Vod? = null;
        runBlocking {
            vod = getVod(ids[0])
        }
        return Result.string(vod);

    }


    @Throws(Exception::class)
    override fun playerContent(flag: String, id: String, vipFlags: MutableList<String?>?): String {
        var content = ""
        runBlocking {
            content = playerContent(Json.safeObject(com.github.catvod.utils.Util.base64Decode(id)), flag)
        }
        return content
    }

    /**
     * 獲取詳情內容視頻播放來源（多 shared_link）
     *
     * @param ids share_link 集合
     * @param i
     * @return 詳情內容視頻播放來源
     */
    fun detailContentVodPlayFrom(ids: MutableList<String?>, index: Int): String? {
        val playFrom: MutableList<String?> = ArrayList<String?>()

        /* if (ids.size() < 2){
            return TextUtils.join("$$$",  BaiduDrive.get().getPlayFormatList());
        }*/
        for (i in 1..ids.size) {
            playFrom.add("BD原画" + i + index)/* for (s in getPlayFormatList()) {
                 playFrom.add(String.format(Locale.getDefault(), "BD" + s + "#%02d%02d", i, index))
             }*/
        }
        return Util.stringJoin("$$$", playFrom)
    }

    /**
     * 獲取詳情內容視頻播放地址（多 share_link）
     *
     * @param ids share_link 集合
     * @return 詳情內容視頻播放地址
     */
   
    fun detailContentVodPlayUrl(ids: List<String>): String? {
        val playUrl: MutableList<String?> = ArrayList<String?>()
        for (id in ids) {
            try {
                playUrl.add(getVod(id).getVodPlayUrl())
            } catch (e: Exception) {
                SpiderDebug.log("获取播放地址出错:" + e.message)
                playUrl.add("")
            }
        }
        return Util.stringJoin("$$$", playUrl)
    }

    companion object {
        @JvmField
        var URL_START = "https://pan.baidu.com"

        @Throws(Exception::class)
        fun proxy(params: MutableMap<String, String>): Array<Any> {
            val type = params["type"]
            if ("video" == type) return BaiduDrive.proxyVideo(params)
            //if ("sub".equals(type)) return AliYun.get().proxySub(params);
            return arrayOf<Any>()
        }
    }
}
