package com.github.catvod.spider;

import cn.hutool.core.net.URLEncodeUtil;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Utils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FourKYingShi extends Spider {

    private static String siteUrl = "https://www.4kvm.tv";


    @Override
    public void init(String extend) throws Exception {
        try {
            super.init(extend);
            SpiderDebug.log("4k影视ext：" + extend);

            JsonObject extendJson = Json.safeObject(extend);

            String html = OkHttp.string(extendJson.get("site").getAsString(), getHeaders());
            Document doc = Jsoup.parse(html);
            Element element = doc.selectFirst("li > a");
            siteUrl = StringUtils.isAllBlank(element.attr("href")) ? siteUrl : element.attr("href");
            SpiderDebug.log("4k影视siteUrl：" + siteUrl);
        } catch (Exception e) {
            SpiderDebug.log(e.getMessage());
            throw new RuntimeException(e);
        }

    }

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Utils.CHROME);
        return headers;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Vod> list = new ArrayList<>();
        List<Class> classes = new ArrayList<>();


        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeaders()));
        Elements elements = doc.select("#header #main_header li > a");
        for (Element element : elements) {
            classes.add(new Class(element.attr("href"), element.text()));
        }
        classes.remove(elements.size() - 1);
        classes.remove(elements.size() - 2);


        getVodList(doc, list);
        return Result.string(classes, list);
    }

    private static void getVodList(Document doc, List<Vod> list) {
        for (Element element : doc.select(" article.item")) {
            try {
                if (element.select("h3").isEmpty()) {
                    continue;
                }
                String name = element.select("h3").text();
                String pic = element.select("img").attr("src");
                String url = element.select("a").attr("href");

                list.add(new Vod(url, name, pic));
            } catch (Exception ignored) {
            }
        }
    }


    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        //https://www.4kvm.tv/movies/page/2

        tid = tid + "/page/" + pg;
        String target = siteUrl + tid;

        List<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(OkHttp.string(target, getHeaders()));
        getVodList(doc, list);

        int total = (Integer.parseInt(pg) + 1) * 38;
        return Result.get().vod(list).page(Integer.parseInt(pg), Integer.parseInt(pg) + 1, 38, total).string();
    }

    private String getStrByRegex(Pattern pattern, String str) {
        Matcher matcher = pattern.matcher(str);
        if (matcher.find()) return matcher.group(1).trim();
        return "";
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        Document doc = Jsoup.parse(OkHttp.string(ids.get(0), getHeaders()));
        String name = doc.select("#single > div.content.right > div.sheader > div.data > h1").text();
        String pic = doc.selectFirst("#single > div.content.right > div.sheader > div.poster > img").attr("src");
        String playListUrl = doc.selectFirst("#seasons > div > div > a").attr("href");
        Elements dooplayCounter = doc.select("#dooplay-ajax-counter");
        if (dooplayCounter.size() > 0) {
            String postId = dooplayCounter.get(0).attr("data-postid");
            Elements dooplay = doc.select(" ul.ajax_mode > li.dooplay_player_option");

            String type = dooplay.attr("data-type");
            String dataName = dooplay.attr("data-nume");
            String dtAjax = Utils.getVar(doc.html(), "dtAjax");
            JsonObject dtAjaxJson = Json.safeObject(dtAjax);
            String urlApi = dtAjaxJson.getAsJsonPrimitive("url_api").getAsString();
            String url = dtAjaxJson.getAsJsonPrimitive("url").getAsString();
            String playMethod = dtAjaxJson.getAsJsonPrimitive("play_method").getAsString();

            if ("admin_ajax".equals(playMethod)) {
/*
 type: "POST",
                url: dtAjax.url,
                data: {
                    action: "doo_player_ajax",
                    post: e,
                    nume: nume,
                    type: type
                },
 */
            } else if ("wp_json".equals(playMethod)) {

                String embedReq = urlApi + postId + "?type=" + type + "&source=" + dataName;
            }


        }


        String html = OkHttp.string(playListUrl, getHeaders());
        String ifsrc = getStrByRegex(Pattern.compile("ifsrc:'(.*?)',"), html);
        String videourls = getStrByRegex(Pattern.compile("videourls:(.*?),\n"), html);

        Vod.VodPlayBuilder builder = new Vod.VodPlayBuilder();
        JsonElement element = Json.parse(videourls);

        for (int i = 0; i < element.getAsJsonArray().size(); i++) {
            List<Vod.VodPlayBuilder.PlayUrl> list = new ArrayList<>();

            for (int j = 0; j < element.getAsJsonArray().get(i).getAsJsonArray().size(); j++) {

                Vod.VodPlayBuilder.PlayUrl playUrl = new Vod.VodPlayBuilder.PlayUrl();
                playUrl.name = element.getAsJsonArray().get(i).getAsJsonArray().get(j).getAsJsonObject().get("name").getAsString();
                playUrl.url = ifsrc + "&source=" + i + "&ep=" + j;
                list.add(playUrl);
            }
            builder.append(i + "", list);
        }


        Vod.VodPlayBuilder.BuildResult result = builder.build();
        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodPic(siteUrl + pic);
        vod.setVodName(name);
        vod.setVodPlayFrom(result.vodPlayFrom);
        vod.setVodPlayUrl(result.vodPlayUrl);
        return Result.string(vod);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        List<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(OkHttp.string(siteUrl.concat(URLEncodeUtil.encode(key, StandardCharsets.UTF_8)), getHeaders()));
        for (Element element : doc.select("div.searchlist_img")) {
            try {
                String pic = element.select("a").attr("data-original");
                String url = element.select("a").attr("href");
                String name = element.select("a").attr("title");
                if (!pic.startsWith("http")) {
                    pic = siteUrl + pic;
                }
                String id = url.replace("/video/", "").replace(".html", "-1-1.html");
                list.add(new Vod(id, name, pic));
            } catch (Exception e) {
            }
        }
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String target = id;
        Document doc = Jsoup.parse(OkHttp.string(target));
        String regex = "url:'(.*?)',";

        String url = getStrByRegex(Pattern.compile(regex), doc.html());
        String localtion = OkHttp.getLocation(url, getHeaders());


        return Result.get().url(localtion).header(getHeaders()).string();
    }
}