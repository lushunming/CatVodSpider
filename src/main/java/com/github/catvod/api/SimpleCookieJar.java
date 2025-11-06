package com.github.catvod.api;


import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.HttpUrl;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleCookieJar {
    private Map<String, Map<String, String>> cookieStore = new HashMap<>();


    public Map<String, Map<String, String>> getCookieStore() {
        return cookieStore;
    }

    public void setCookieStore(Map<String, Map<String, String>> cookieStore) {
        this.cookieStore = cookieStore;
    }

    public SimpleCookieJar() {

    }


    public void saveFromResponse(String url, List<String> cookies) {
        HttpUrl httpUrl = HttpUrl.parse(url);
        SpiderDebug.log(" saveFromResponse url: " + url);
        SpiderDebug.log(" saveFromResponse cookie : " + Json.toJson(cookies));
        // 创建可修改的 Cookie 列表副本
        Map<String, String> oldCookies = cookieStore.get(httpUrl.host()) != null ? cookieStore.get(httpUrl.host()) : new HashMap<>();

        // 更新 Cookie
        for (String newCookie : cookies) {
            String[] split = newCookie.split(";");
            String cookieItem = split[0].trim();
            int equalsIndex = cookieItem.indexOf('=');
            if (equalsIndex > 0) {
                String key = cookieItem.substring(0, equalsIndex);
                String value = equalsIndex < cookieItem.length() - 1 ? cookieItem.substring(equalsIndex + 1) : "";
                if (value.equals("SSON") && StringUtils.isAllBlank(value)) {

                } else {
                    oldCookies.put(key, value);
                }

            }
        }


        cookieStore.put(httpUrl.host(), oldCookies);
        SpiderDebug.log(" cookieStore now: " + Json.toJson(cookieStore));

    }


    public void setGlobalCookie(JsonObject jsonObject) {

        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            String key = entry.getKey();
            JsonObject value = entry.getValue().getAsJsonObject();
            Map<String, String> cookiesForHost = new HashMap<>();
            for (String k : value.keySet()) {
                String cookieobj = value.get(k).getAsString();
                cookiesForHost.put(k, cookieobj);
            }
            cookieStore.put(key, cookiesForHost);
        }


    }

    /**
     * 根据请求URl获取cookie
     *
     * @param url
     * @return
     */
    public String loadForRequest(String url) {
        HttpUrl httpUrl = HttpUrl.parse(url);
        Map<String, String> cookieMap = cookieStore.get(httpUrl.host());
        List<String> cookieList = new ArrayList<>();
        if (cookieMap != null && cookieMap.size() > 0) {
            for (String s : cookieMap.keySet()) {
                cookieList.add(s + "=" + cookieMap.get(s));
            }
        }
        String cookie = StringUtils.join(cookieList, ";");
        SpiderDebug.log(" loadForRequest url:" + url);
        SpiderDebug.log(" loadForRequest cookie:" + cookie);
        return cookie;
    }
}