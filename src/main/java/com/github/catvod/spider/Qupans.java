package com.github.catvod.spider;

import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.net.OkHttp;
import org.apache.http.util.TextUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Qupans extends Cloud {

    private static final String BASE_URL = "https://www.qupanshe.com";
    private static final String DEFAULT_COVER_URL = "https://fs-im-kefu.7moor-fs1.com/ly/4d2c3f00-7d4c-11e5-af15-41bf63ae4ea0/1743950734122/baidu.jpg";

    private String get(String url) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.put("Referer", BASE_URL);
        return OkHttp.string(url, headers);
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            JSONObject result = new JSONObject();
            JSONArray classes = new JSONArray();
            String[][] types = {{"电影","3"}, {"电视剧","2"}, {"综艺","4"}, {"动漫","5"}, {"纪录片","6"}};
            for (String[] type : types) {
                JSONObject cls = new JSONObject();
                cls.put("type_id", type[1]);
                cls.put("type_name", type[0]);
                classes.put(cls);
            }
            result.put("class", classes);
            return result.toString();
        } catch (Exception e) {
            return "{\"class\":[]}";
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            String html = get(BASE_URL + "/forum.php?mod=forumdisplay&fid=" + tid + "&page=" + pg);
            Document doc = Jsoup.parse(html);
            JSONArray list = new JSONArray();

            for (Element item : doc.select("div.tit_box > a.s")) {
                String title = item.text();
                if (title.contains("公告") || title.contains("求")) continue;
                JSONObject vod = new JSONObject();
                vod.put("vod_id", item.attr("href"));
                vod.put("vod_name", title);
                vod.put("vod_pic", DEFAULT_COVER_URL);
                vod.put("vod_remarks", "");
                list.put(vod);
            }

            return new JSONObject()
                    .put("list", list)
                    .put("page", pg)
                    .put("pagecount", "0")
                    .put("total", "0")
                    .toString();
        } catch (Exception e) {
            return "{\"list\":[]}";
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            String vodId = ids.get(0);
            String html = get(vodId.startsWith("http") ? vodId : BASE_URL + "/" + vodId);
            Document doc = Jsoup.parse(html);

            Vod vod = new Vod();
            vod.setVodId(vodId);
            vod.setVodName(doc.select("h1").first().text());
            vod.setVodPic(DEFAULT_COVER_URL);

            List<String> links = getLinks(doc);
            if (!links.isEmpty()) {
                String pwd = getPwd(doc);
                for (int i = 0; i < links.size(); i++) {
                    String link = links.get(i);
                    if (!link.contains("pwd=") && !TextUtils.isEmpty(pwd)) {
                        links.set(i, link + "?pwd=" + pwd);
                    }
                }
                vod.setVodPlayFrom(super.detailContentVodPlayFrom(links));
                vod.setVodPlayUrl(super.detailContentVodPlayUrl(links));
            }

            return Result.string(vod);
        } catch (Exception e) {
            Vod vod = new Vod();
            vod.setVodId(ids.get(0));
            vod.setVodName("加载失败");
            vod.setVodPic(DEFAULT_COVER_URL);
            return Result.string(vod);
        }
    }

    private List<String> getLinks(Document doc) {
        List<String> links = new ArrayList<>();
        // 从a标签中提取链接
        Elements linksElements = doc.select("a");
        for (Element linkElement : linksElements) {
            String href = linkElement.attr("href");
            if (href.contains(".baidu")) {
                links.add(href);
                break; // 只提取第一个百度网盘链接
            }
        }
        return links;
    }

    private String getPwd(Document doc) {
        try {
            // 使用正则表达式模式提取密码
            String patternStr = "提取码:\\s*([A-Za-z0-9]{4})";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(patternStr);
            Element contentElement = doc.select("td.t_f").first();
            if (contentElement != null) {
                String text = contentElement.text();
                java.util.regex.Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (Exception e) {
            // 忽略异常
        }
        return "";
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("searchsubmit", "yes");
            params.put("srchtxt", key);
            String[] fids = {"2", "3", "4", "5", "6"};
            for (int i = 0; i < fids.length; i++) params.put("srchfid[" + i + "]", fids[i]);

            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.put("content-type", "application/x-www-form-urlencoded");

            String html = OkHttp.post(BASE_URL + "/search.php?mod=forum", params, headers).getBody();
            Document doc = Jsoup.parse(html);

            JSONArray list = new JSONArray();
            for (Element item : doc.select("#threadlist ul li h3 > a")) {
                JSONObject vod = new JSONObject();
                vod.put("vod_id", item.attr("href"));
                vod.put("vod_name", item.text());
                vod.put("vod_pic", DEFAULT_COVER_URL);
                vod.put("vod_remarks", "");
                list.put(vod);
            }

            return new JSONObject()
                    .put("list", list)
                    .put("page", pg)
                    .put("pagecount", "1")
                    .put("total", list.length())
                    .toString();
        } catch (Exception e) {
            return "{\"list\":[]}";
        }
    }
}