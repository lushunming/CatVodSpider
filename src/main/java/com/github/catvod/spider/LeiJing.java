package com.github.catvod.spider;


import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LeiJing extends Cloud {
    private static final String siteUrl = "https://leijing1.com/";

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
    public void init( String extend) throws Exception {

        super.init( extend);
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        Document doc = Jsoup.parse(OkHttp.string(siteUrl, getHeader()));
        Elements elements = doc.select(" #tabNavigation > a.tab");
        for (Element e : elements) {
            String url = e.attr("href");
            String name = e.text();
            if (StringUtils.isNoneBlank(url)) {
                classes.add(new Class(url, name));
            }

        }

        return Result.string(classes, parseVodListFromDoc(doc));
    }

    private List<Vod> parseVodListFromDoc(Document doc) {
        List<Vod> list = new ArrayList<>();

        Elements topicItems = doc.select(".topicItem");

        for (Element each : topicItems) {
            // 检查是否有锁定标记
            if (each.select(".cms-lock-solid").size() > 0) {
                continue;
            }

            // 提取href
            String href = each.select("h2 a").attr("href");
            // 提取标题并处理空格
            String title = each.select("h2 a").text().trim().replaceAll("\\s+", " ");
            // 提取摘要
            String r = each.select(".summary").text();
            // 提取标签
            String tag = each.select(".tag").text();

            // 过滤条件
            if (r.contains("content") && !r.contains("cloud")) {
                continue;
            }
            if (tag.contains("软件") || tag.contains("游戏") || tag.contains("书籍") || tag.contains("图片") || tag.contains("公告") || tag.contains("音乐") || tag.contains("课程")) {
                continue;
            }

            // 创建Video对象
            Vod video = new Vod(href, title, "", "");
            list.add(video);
        }
        return list;

    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {

        Document doc = Jsoup.parse(OkHttp.string(String.format("%s%s&page=%s", siteUrl, tid, pg), getHeader()));
        List<Vod> list = parseVodListFromDoc(doc);
        int total = (Integer.parseInt(pg) + 1) * 30;
        return Result.get().vod(list).page(Integer.parseInt(pg), Integer.parseInt(pg) + 1, 30, total).string();
    }


    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        Document doc = Jsoup.parse(OkHttp.string(siteUrl + vodId, getHeader()));


        Vod item = new Vod();
        item.setVodId(vodId);
        item.setVodName(doc.selectFirst("div.title").text());

        Elements elements = doc.select("a");
        List<String> shareLinks = new ArrayList<>();

        for (Element element : elements) {
            if (element.attr("href").contains("https://cloud.189.cn")) {
                shareLinks.add(element.attr("href"));
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
        String searchURL = siteUrl + String.format("search?keyword=%s", URLEncoder.encode(key));
        String html = OkHttp.string(searchURL, getHeaderWithCookie());
        Document doc = Jsoup.parse(html);

        return Result.string(parseVodListFromDoc(doc));
    }
}
