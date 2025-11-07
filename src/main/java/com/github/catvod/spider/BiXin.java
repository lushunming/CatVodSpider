package com.github.catvod.spider;


import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BiXin extends Cloud {
    private static final String siteUrl = "https://www.bixbiy.com/";

    private final String hostUrl = siteUrl;


    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        return header;
    }

    private Map<String, String> getHeaderWithCookie() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("cookie", "esc_search_captcha=1; result=43");
        return header;
    }

    @Override
    public void init(String extend) throws Exception {

        super.init(extend);
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        String temp = siteUrl + "api/discussions?include=user%2ClastPostedUser%2Ctags%2Ctags.parent%2CfirstPost&sort&page%5Boffset%5D=0";

        String resultStr = OkHttp.string(temp, getHeader());

        classes.add(new Class("mv", "影视"));

        return Result.string(classes, parseVodListFromDoc(resultStr));
    }

    private List<Vod> parseVodListFromDoc(String resultStr) {
        JsonObject json = Json.safeObject(resultStr);
        JsonArray arrays = json.get("data").getAsJsonArray();

        List<Vod> list = new ArrayList<>();
        for (JsonElement array : arrays) {
            JsonObject data = array.getAsJsonObject();
            String vodId = data.get("id").getAsString();
            String vodPic = "";
            JsonObject attributes = data.get("attributes").getAsJsonObject();
            String title = attributes.get("title").getAsString();
            String vodRemarks = "";
            String vodName = "";
            if (title.contains("（")) {
                vodName = title.split("（")[0];
                vodRemarks = title.split("（")[1];
            } else {
                vodName = title;
                vodRemarks = title;
            }

            list.add(new Vod(vodId, vodName, vodPic, vodRemarks));
        }
        return list;
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        int pageSize = 20;
        String temp = siteUrl +//+ "api/discussions?include=user%2ClastPostedUser%2Ctags%2Ctags.parent%2CfirstPost&filter%5Btag%5D=" + tid + "&sort&page%5Boffset%5D=" + (Integer.parseInt(pg) - 1) * pageSize;
                "api/discussions?include=user%2ClastPostedUser%2Ctags%2Ctags.parent%2CfirstPost%2Cuser.userBadges%2Cuser.userBadges.badge&filter%5Btag%5D=" + tid + "&sort&page%5Boffset%5D=" + (Integer.parseInt(pg) - 1) * pageSize;
        String resultStr = OkHttp.string(temp, getHeader());
        List<Vod> list = parseVodListFromDoc(resultStr);
        int total = (Integer.parseInt(pg) + 1) * 20;
        return Result.get().vod(list).page(Integer.parseInt(pg), Integer.parseInt(pg) + 1, pageSize, total).string();
    }


    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        Document doc = Jsoup.parse(OkHttp.string(siteUrl + "d/" + vodId, getHeader()));

        Vod item = new Vod();
        item.setVodId(vodId);
        item.setVodName(doc.selectFirst("  div.container  > h1").text());
        // item.setVodPic(doc.selectFirst("  div.Post-body > p > img").attr("src"));


        List<String> shareLinks = new ArrayList<>();
        for (Element element : doc.select("div.Post-body > p > a")) {
            if (element.attr("href").contains(YiDongYun.URL_START)) {
                shareLinks.add(element.attr("href").trim());
            }
        }


        item.setVodPlayUrl(super.detailContentVodPlayUrl(shareLinks));
        item.setVodPlayFrom(super.detailContentVodPlayFrom(shareLinks));

        return Result.string(item);
    }

    private String getStrByRegex(Pattern pattern, String str) {
        Matcher matcher = pattern.matcher(str);
        if (matcher.find()) return matcher.group(1).trim();
        return "";
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return searchContent(key, pg);
    }

    private String searchContent(String key, String pg) {
        int pageSize = 20;
        String temp = siteUrl + "api/discussions?include=user%2ClastPostedUser%2CmostRelevantPost%2CmostRelevantPost.user%2Ctags%2Ctags.parent%2CfirstPost&filter%5Bq%5D=" + URLEncoder.encode(key) + "&sort&page%5Boffset%5D=" + (Integer.parseInt(pg) - 1) * pageSize;

        String resultStr = OkHttp.string(temp, getHeader());


        return Result.string(parseVodListFromDoc(resultStr));
    }
}
