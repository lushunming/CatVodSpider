package com.github.catvod.spider

import com.github.catvod.bean.Result
import com.github.catvod.bean.Vod
import com.github.catvod.net.OkHttp
import com.github.catvod.utils.Json
import com.github.catvod.utils.Util
import java.net.URLEncoder
import java.nio.charset.Charset

/**
 * @author zhixc
 */
class Tg123Search : Cloud() {
    private val URL = "https://tgsou.252035.xyz/"

    private val header: Map<String, String>
        get() {
            val header: MutableMap<String, String> = HashMap()
            header["User-Agent"] = Util.CHROME
            return header
        }


    @Throws(Exception::class)
    override fun searchContent(key: String, quick: Boolean): String {
        val url =
            URL + "?channelUsername=wp123zy,xx123pan,yp123pan,zyfb123&pic=true&keyword=" + URLEncoder.encode(
                key, Charset.defaultCharset().name()
            )
        val list: MutableList<Vod> = ArrayList()
        val html = OkHttp.string(url, header)
        val json = Json.safeObject(html);
        json["results"].asJsonArray.forEach { element ->
            val array = element.asString.split("$$$")
            if (array.size >= 2 && array[1].isNotEmpty()) {
                array[1].split("##").forEach {


                    val id = it.split("@")[0]
                    val pic = it.split("@")[1].split("$$")[0]
                    val name = it.split("@")[1].split("$$")[1]

                    list.add(Vod(id, name, pic, ""))
                }


            }


        }


        return Result.string(list)
    }
}
