package com.github.catvod.spider

import cn.hutool.core.util.URLUtil
import com.github.catvod.bean.Class
import com.github.catvod.bean.Filter
import com.github.catvod.bean.Result
import com.github.catvod.bean.Vod
import com.github.catvod.net.OkHttp
import com.github.catvod.utils.Util
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * @author zhixc
 */
class SeedHub : Cloud() {
    private val siteUrl = "https://www.seedhub.cc"
    private val regexCategory: Pattern = Pattern.compile("/vodtype/(\\w+).html")
    private val regexPageTotal: Pattern = Pattern.compile("\\$\\(\"\\.mac_total\"\\)\\.text\\('(\\d+)'\\);")

    private var extend: JsonObject? = null


    private val header: MutableMap<String?, String?>
        get() {
            val header: MutableMap<String?, String?> = HashMap<String?, String?>()
            header.put("User-Agent", Util.MOBILE)
            return header
        }

    @Throws(Exception::class)
    override fun init(extend: String) {
        this.extend = JsonParser.parseString(extend).getAsJsonObject()
        super.init("")
    }

    override fun homeContent(filter: Boolean): String? {
        val classes: MutableList<Class?> = ArrayList<Class?>()
        val filters = LinkedHashMap<String?, MutableList<Filter?>?>()


        val html = OkHttp.string(siteUrl, this.header)
        val doc = Jsoup.parse(html)

        parseClassFromDoc(doc, classes)
        if (filter) {
            parseFilterFromDoc(doc, filters)
        }

        return Result.string(classes, parseVodListFromDoc(doc), filters)
    }

    private fun parseFilterFromDoc(
        doc: Document, filters: LinkedHashMap<String?, MutableList<Filter?>?>
    ) {

        val groups = doc.select("div.sidebar-group")
        for (group in groups) {
            val clazz = group.select("p.sidebar-heading a")
            if (clazz.attr("href").startsWith("/")) {
                val name = clazz.text()
                val url = clazz.attr("href")
                val filter = group.select("ul.sidebar-sub-headers>li.sidebar-sub-header>a")
                val filterList: MutableList<Filter?> = ArrayList<Filter?>()
                val values: MutableList<Filter.Value> = ArrayList<Filter.Value>()
                for (f in filter) {
                    val filterName = f.text()
                    val filterUrl = f.attr("href")
                    values.add(Filter.Value(filterName, filterUrl))
                }
                filterList.add(Filter("0", "分类", values))
                filters[url] = filterList
            }

        }
    }

    /**
     *获取分类
     */
    private fun parseClassFromDoc(
        doc: Document, classes: MutableList<Class?>
    ) {
        val navs = doc.select("div.nav-item")
        for (nav in navs) {
            val link = nav.select("a")
            if (link.attr("href").startsWith("/")) {
                val name = nav.select("a").text()
                val url = nav.select("a").attr("href")
                classes.add(Class(name, url))
            }
        }
    }

    override fun categoryContent(
        tid: String?, pg: String, filter: Boolean, extend: HashMap<String, String?>?
    ): String? {
        var urlParams = tid
        if (extend != null && extend.size > 0) {
            extend.keys.forEach {
                urlParams = extend[it]
            }
        }
        val doc = Jsoup.parse(
            OkHttp.string(
                String.format("%s%s?page=%s", siteUrl, urlParams, pg), this.header
            )
        )
        val page = pg.toInt()
        val limit = 20
        val total = Int.MAX_VALUE


        return Result.get().vod(parseVodListFromDoc(doc)).page(page, 0, limit, total).string()
    }

    private fun parseVodListFromDoc(doc: Document): MutableList<Vod?> {
        val list: MutableList<Vod?> = ArrayList<Vod?>()
        val elements = doc.select("div.cover")
        for (e in elements) {
            val vodId = e.selectFirst(" a")!!.attr("href")
            var vodPic = e.selectFirst("img")!!.attr("src")
            if (!vodPic.startsWith("http")) {
                vodPic = URLUtil.completeUrl(siteUrl, vodPic)
            }
            val vodName = e.selectFirst("h2")!!.text()
            val vodRemarks = e.select("ul >li")!!.text()
            list.add(Vod(vodId, vodName, vodPic, vodRemarks))
        }
        return list
    }

    @Throws(Exception::class)
    override fun detailContent(ids: MutableList<String?>): String? {
        val vodId = ids.get(0)
        val doc = Jsoup.parse(
            OkHttp.string(
                siteUrl + vodId, this.header
            )
        )

        val infos = doc.select("div.cover-container >ul > li").text().replace(" ", "")


        val item = Vod()
        item.setVodId(vodId)
        item.setVodName(doc.selectFirst("h1")!!.text())
        item.setVodPic(doc.selectFirst("div.cover-container img")!!.attr("src"))
        item.setVodArea(Util.getStrByRegex(Pattern.compile("制片国家/地区:(.*?)语言:"), infos))
        item.setTypeName(Util.getStrByRegex(Pattern.compile("类型:(.*?)制片"), infos))
        item.setVodDirector(Util.getStrByRegex(Pattern.compile("导演:(.*?)制片"), infos))
        item.setVodActor(Util.getStrByRegex(Pattern.compile("主演:(.*?)类型"), infos))
        item.setVodYear(Util.getStrByRegex(Pattern.compile("首播:(.*?)集数"), infos))
        item.setVodRemarks(Util.getStrByRegex(Pattern.compile("豆瓣评分:(.*?)常用标签"), infos))
        item.vodContent = doc.select("div.content > p").text()

        val shareLinks: MutableList<String?> = java.util.ArrayList<String?>()

        doc.select("ul.pan-links > li > a").forEach { element ->
            run {
                var link = siteUrl + element.attr("href")
                link = URLUtil.normalize(link, true)
                val string = OkHttp.string(link, header)
                val docEle = Jsoup.parse(string)
                docEle.select("a.direct-pan").attr("href").let {
                    if (it.isNotEmpty()) {
                        shareLinks.add(it)
                    }
                }

            }
        }
        item.vodPlayUrl = super.detailContentVodPlayUrl(shareLinks)
        item.setVodPlayFrom(super.detailContentVodPlayFrom(shareLinks))

        return Result.string(item)
    }

    @Throws(Exception::class)
    override fun searchContent(key: String?, quick: Boolean): String? {
        return searchContent(key, "1")
    }

    @Throws(Exception::class)
    override fun searchContent(key: String?, quick: Boolean, pg: String?): String? {
        return searchContent(key, pg)
    }

    private fun searchContent(key: String?, pg: String?): String? {
        val searchURL =
            siteUrl + String.format("/s/%s/?page=%s", URLEncoder.encode(key), pg)
        val html = OkHttp.string(searchURL, this.header)
        val doc = Jsoup.parse(html)

        return Result.string(parseVodListFromDoc(doc))
    }
}