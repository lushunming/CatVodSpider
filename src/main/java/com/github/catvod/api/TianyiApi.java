package com.github.catvod.api;


import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.bean.tianyi.Item;
import com.github.catvod.bean.tianyi.ShareData;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.OkResult;
import com.github.catvod.spider.Init;
import com.github.catvod.utils.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TianyiApi {
    private String apiUrl = "https://cloud.189.cn/api/open/share/";
    public static final String URL_CONTAIN = "cloud.189.cn/";


    private Map<String, JsonObject> shareTokenCache = new HashMap<>();


    private ScheduledExecutorService service;
    private String sessionKey = "";
    private TianYiHandler tianYiHandler;
    private SimpleCookieJar cookieJar;


    public String[] getPlayFormatList() {
        return new String[]{"天意"};
    }

    private static class Loader {
        static volatile TianyiApi INSTANCE = new TianyiApi();
    }

    public static TianyiApi get() {
        return Loader.INSTANCE;
    }

    public void setCookie(String token) throws Exception {
        if (StringUtils.isNoneBlank(token)) {
            JsonObject obj = Json.safeObject(token);
            //初始化CookieJar
            if (Objects.nonNull(obj)) {
                tianYiHandler.setCookie(obj);
                tianYiHandler.loginWithPassword(obj.get("username").getAsString(), obj.get("password").getAsString());
            }
        }
        if (!isCookieValid()) {
            SpiderDebug.log("CookieJar不合法，请重新登录");
           // tianYiHandler.startScan();
        }
        getUserSizeInfo();
        this.sessionKey = getUserBriefInfo();
    }

    /**
     * 判断cookie是否为空，或者SSon为空，那就需要重新登陆
     *
     * @return
     */
    private boolean isCookieValid() {
        if (cookieJar.getCookieStore().size() == 0) {
            SpiderDebug.log("CookieJar为空");
            return false;
        } else {
            for (String key : cookieJar.getCookieStore().keySet()) {
                Map<String, String> cookieMap = cookieJar.getCookieStore().get(key);
                for (String k : cookieMap.keySet()) {
                    String cookieobj = cookieMap.get(k);
                    if (k.equals("SSON") && StringUtils.isNoneBlank(cookieobj)) {
                        SpiderDebug.log("SSON 不为空");
                        return true;
                    }

                }

            }
        }
        SpiderDebug.log("CookieJar 不合法，重新登录");
        return false;
    }

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) quark-cloud-drive/2.5.20 Chrome/100.0.4896.160 Electron/18.3.5.4-b478491100 Safari/537.36 Channel/pckk_other_ch");

        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("accept", "application/json;charset=UTF-8");
        headers.put("cookie", cookieJar.loadForRequest("https://cloud.189.cn/api/portal/getNewVlcVideoPlayUrl.action"));

        if (StringUtils.isNotBlank(sessionKey)) {
            headers.put("sessionKey", sessionKey);
        }

        return headers;
    }


    private TianyiApi() {


        tianYiHandler =  TianYiHandler.get();
        tianYiHandler.init();
        cookieJar = tianYiHandler.getCookieJar();
    }

    public File getCache() {
        return Path.tv("tianyi");
    }

    public Vod getVod(ShareData shareData) throws Exception {
        getShareToken(shareData);
        List<Item> files = new ArrayList<>();
        List<Item> subs = new ArrayList<>();
        try {
            JsonArray listData = listFile(1, shareData, files, subs, shareData.getShareId(), shareData.getFolderId(), 1);

        } catch (Exception e) {
            SpiderDebug.log("资源已取消:" + e.getMessage());
            Notify.show("资源已取消");
            throw new RuntimeException(e);
        }


        List<String> playFromtmp = new ArrayList<>();
        playFromtmp.add("天意");


        List<String> playUrl = new ArrayList<>();

        if (files.isEmpty()) {
            return null;
        }

        for (int index = 0; index < playFromtmp.size(); index++) {
            List<String> vodItems = new ArrayList<>();
            for (Item video_item : files) {
                vodItems.add(video_item.getEpisodeUrl("电影"));// + findSubs(video_item.getName(), subs));
            }
            playUrl.add(Util.stringJoin("#", vodItems));
        }


        Vod vod = new Vod();
        vod.setVodId("");
        vod.setVodContent("");
        vod.setVodPic("");
        vod.setVodName("");
        vod.setVodPlayUrl(Util.stringJoin("$$$", playUrl));
        vod.setVodPlayFrom(Util.stringJoin("$$$", playFromtmp));
        vod.setTypeName("天意云盘");
        return vod;
    }

    public String playerContent(String[] split, String flag) throws Exception {

        String fileId = split[0], shareId = split[1];
        String playUrl = "";

        playUrl = this.getDownload(shareId, fileId);
        Map<String, String> header = getHeaders();
        header.remove("Host");
        header.remove("Content-Type");


        header.put("Cookie", cookieJar.loadForRequest("https://cloud.189.cn/api/portal/getNewVlcVideoPlayUrl.action"));
        return Result.get().url(ProxyServer.INSTANCE.buildProxyUrl(playUrl, header)).octet().header(header).string();
    }


    /**
     * @param url
     * @param params get 参数
     * @param data   post json
     * @param retry
     * @param method
     * @return
     * @throws Exception
     */
    private String api(String url, Map<String, String> params, Map<String, Object> data, Integer retry, String method) throws Exception {


        int leftRetry = retry != null ? retry : 3;

        OkResult okResult;
        if ("GET".equals(method)) {
            okResult = OkHttp.get(this.apiUrl + url, params, getHeaders());
        } else {
            okResult = OkHttp.post(this.apiUrl + url, Json.toJson(data), getHeaders());
        }
       /* if (okResult.getResp().get("Set-Cookie") != null) {
            Matcher matcher = Pattern.compile("__puus=([^;]+)").matcher(StringUtils.join(okResult.getResp().get("Set-Cookie"), ";;;"));
            if (matcher.find()) {
                Matcher cookieMatcher = Pattern.compile("__puus=([^;]+)").matcher(this.cookie);
                if (cookieMatcher.find() && !cookieMatcher.group(1).equals(matcher.group(1))) {
                    this.cookie = this.cookie.replaceAll("__puus=[^;]+", "__puus=" + matcher.group(1));
                } else {
                    this.cookie = this.cookie + ";__puus=" + matcher.group(1);
                }
            }
        }
*/
        if (okResult.getCode() != 200 && leftRetry > 0) {
            SpiderDebug.log("api error code:" + okResult.getCode());
            Thread.sleep(1000);
            return api(url, params, data, leftRetry - 1, method);
        }
        return okResult.getBody();
    }


    public ShareData getShareData(String url, String accessCode) {
        // 从整个URL中直接提取访问码，无论格式如何
        Matcher accessMatcher = Pattern.compile("访问码[：:]([a-zA-Z0-9]+)").matcher(url);
        if (accessMatcher.find()) {
            accessCode = accessMatcher.group(1);
        } else if (accessCode == null || accessCode.isEmpty()) {
            accessCode = "";
        }

        String shareCode = null;
        // 第一种匹配规则：兼容http和https
        Matcher matcher = Pattern.compile("https?:\\/\\/cloud\\.189\\.cn\\/web\\/share\\?code=([^&\\s]+)").matcher(url);
        if (matcher.find() && matcher.group(1) != null) {
            shareCode = matcher.group(1);
        } else {
            // 第二种匹配规则：直接匹配 cloud.189.cn/t/ 格式，兼容http和https，匹配到空格前
            Matcher fallbackMatcher = Pattern.compile("https?:\\/\\/cloud\\.189\\.cn\\/t/([^\\s]+)").matcher(url);
            if (fallbackMatcher.find()) {
                shareCode = fallbackMatcher.group(1);
            }
        }

        ShareData shareData = new ShareData(shareCode, "0");
        shareData.setSharePwd(accessCode);
        return shareData;
    }


    private String getUserBriefInfo() throws Exception {
        OkResult result = OkHttp.get("https://cloud.189.cn/api/portal/v2/getUserBriefInfo.action", new HashMap<>(), getHeaders());
        JsonObject obj = Json.safeObject(result.getBody());
        return obj.get("sessionKey") == null ? "" : obj.get("sessionKey").getAsString();
    }

    private String getUserSizeInfo() throws Exception {
        OkResult result = OkHttp.get("https://cloud.189.cn/api/portal/getUserSizeInfo.action", new HashMap<>(), getHeaders());
        JsonObject res = Json.safeObject(result.getBody());
        if (StringUtils.isAllBlank(result.getBody()) || (Objects.nonNull(res.get("errorCode")) && res.get("errorCode").getAsString().equals("InvalidSessionKey"))) {
            // tianYiHandler.startScan();
            tianYiHandler.refreshCookie();
            //tianYiHandler.startScan();
        }
        return "";

    }


    private void getShareToken(ShareData shareData) throws Exception {
        if (!this.shareTokenCache.containsKey(shareData.getShareId())) {
            this.shareTokenCache.remove(shareData.getShareId());
            JsonObject shareToken = Json.safeObject(api("getShareInfoByCodeV2.action?noCache=0.8886566349412803&shareCode=" + shareData.getShareId(), new HashMap<>(), new HashMap<>(), 0, "GET"));
            /**
             * {
             *   "res_code" : 0.0,
             *   "res_message" : "成功",
             *   "accessCode" : "",
             *   "creator" : {
             *     "iconURL" : "",
             *     "oper" : false,
             *     "ownerAccount" : "185****1601@189.cn",
             *     "superVip" : 33.0,
             *     "vip" : 0.0
             *   },
             *   "expireTime" : 6.0,
             *   "expireType" : 1.0,
             *   "fileCreateDate" : "2025-03-20 13:49:18",
             *   "fileId" : "12350115314094",
             *   "fileLastOpTime" : "2025-03-20 13:49:19",
             *   "fileName" : "05_如何制作动感影集.mp4等",
             *   "fileSize" : 0.0,
             *   "fileType" : "batchShare",
             *   "isFolder" : true,
             *   "needAccessCode" : 1.0,
             *   "reviewStatus" : 1.0,
             *   "shareDate" : 1.742449758E12,
             *   "shareMode" : 1.0,
             *   "shareType" : 1.0
             * }
             */
            if (Objects.nonNull(shareToken.get("res_code")) && shareToken.get("res_code").getAsInt() == 0) {
                shareData.setShareId(shareToken.get("shareId").getAsString());
                shareData.setShareMode(shareToken.get("shareMode").getAsInt());
                shareData.setFolder(shareToken.get("isFolder").getAsBoolean());
                shareData.setFileId(shareToken.get("fileId").getAsString());
                shareData.setFolderId(shareToken.get("fileId").getAsString());

                this.shareTokenCache.put(shareData.getShareId(), shareToken);
            }
        }
    }

    private JsonArray listFile(int shareIndex, ShareData shareData, List<Item> videos, List<Item> subtitles, String shareId, String folderId, Integer page) throws Exception {
        int prePage = 200;
        page = page != null ? page : 1;
        String url = "listShareDir.action?" + "pageNum=" + page + "&pageSize=" + prePage + "&fileId=" + folderId + "&shareDirFileId=" + folderId + "&isFolder=" + shareData.getFolder() + "&shareId=" + shareId + "&shareMode=" + shareData.getShareMode() + "&iconOption=5" + "&orderBy=filename" + "&descending=false" + "&accessCode=" + shareData.getSharePwd();

        JsonObject listData = Json.safeObject(api(url, Collections.emptyMap(), Collections.emptyMap(), 0, "GET"));
        if (listData.get("res_code").getAsInt() != 0) return new JsonArray();
        if (listData.get("fileListAO").getAsJsonObject().get("count").getAsInt() == 0 && listData.get("fileListAO").getAsJsonObject().get("fileListSize").getAsInt() == 0)
            return new JsonArray();

        JsonArray items = listData.get("fileListAO").getAsJsonObject().get("fileList").getAsJsonArray();
        JsonArray subDir = listData.get("fileListAO").getAsJsonObject().get("folderList").getAsJsonArray();

        for (JsonElement item : items) {
            if (item.getAsJsonObject().get("mediaType").getAsInt() == 3) {
                if (item.getAsJsonObject().get("size").getAsLong() < 1024 * 1024 * 5) continue;

                videos.add(Item.objectFrom(item.getAsJsonObject(), shareData.getShareId(), shareIndex));
            } /*else if ("file".equals(item.get("type")) && this.subtitleExts.contains("." + Util.getExt((String) item.get("file_name")))) {
                subtitles.add(Item.objectFrom(item, shareData.getShareId(), shareIndex));
            }*/
        }
        if (listData.get("fileListAO").getAsJsonObject().get("count").getAsInt() > (items.size() + subDir.size())) {
            JsonArray nextItems = listFile(shareIndex, shareData, videos, subtitles, shareId, folderId, page + 1);
            items.addAll(nextItems);
        }
        for (JsonElement dir : subDir) {
            String subfolderId = dir.getAsJsonObject().get("id").getAsString();
            JsonArray subItems = listFile(shareIndex, shareData, videos, subtitles, shareId, subfolderId, null);
            items.addAll(subItems);
        }

        return items;
    }

    private Map<String, Object> findBestLCS(Item mainItem, List<Item> targetItems) {
        List<Map<String, Object>> results = new ArrayList<>();
        int bestMatchIndex = 0;
        for (int i = 0; i < targetItems.size(); i++) {
            Util.LCSResult currentLCS = Util.lcs(mainItem.getName(), targetItems.get(i).getName());
            Map<String, Object> result = new HashMap<>();
            result.put("target", targetItems.get(i));
            result.put("lcs", currentLCS);
            results.add(result);
            if (currentLCS.length > results.get(bestMatchIndex).get("lcs").toString().length()) {
                bestMatchIndex = i;
            }
        }
        Map<String, Object> bestMatch = results.get(bestMatchIndex);
        Map<String, Object> finalResult = new HashMap<>();
        finalResult.put("allLCS", results);
        finalResult.put("bestMatch", bestMatch);
        finalResult.put("bestMatchIndex", bestMatchIndex);
        return finalResult;
    }

    public void getFilesByShareUrl(int shareIndex, String shareInfo, List<Item> videos, List<Item> subtitles) throws Exception {
        ShareData shareData = getShareData((String) shareInfo, "");
        if (shareData == null) return;
        getShareToken(shareData);
        if (!this.shareTokenCache.containsKey(shareData.getShareId())) return;
        listFile(shareIndex, shareData, videos, subtitles, shareData.getShareId(), shareData.getFolderId(), 1);
        if (!subtitles.isEmpty()) {
            for (Item video : videos) {
                Map<String, Object> matchSubtitle = findBestLCS(video, subtitles);
                if (matchSubtitle.get("bestMatch") != null) {
                    video.setSubtitle((String) ((Map<String, Object>) matchSubtitle.get("bestMatch")).get("target"));
                }
            }
        }
    }


    private String getDownload(String shareId, String fileId) throws Exception {
        Map<String, String> headers = getHeaders();
        //headers.remove("sessionKey");
        OkResult result = OkHttp.get("https://cloud.189.cn/api/portal/getNewVlcVideoPlayUrl.action?shareId=" + shareId + "&dt=1&fileId=" + fileId + "&type=4&key=noCache", new HashMap<>(), headers);
        JsonObject res = Json.safeObject(result.getBody());
        if (Objects.nonNull(res.get("res_code")) && res.get("res_code").getAsInt() == 0) {

            if (res.get("normal") != null) {
                String normal = res.get("normal").getAsJsonObject().get("url").getAsString();
                //String downloadUrl = OkHttp.getLocation(normal, headers);
                SpiderDebug.log("获取天翼下载地址成功:" + normal);
                return normal;
            }
        } else if (res.get("errorCode") != null && res.get("errorCode").getAsString().equals("InvalidSessionKey")) {
            //刷新cookie
            SpiderDebug.log("天意cookie 过期，刷新cookie。。。。");
            tianYiHandler.refreshCookie();
            //重试下载
            SpiderDebug.log("重试下载。。。。");
            getDownload(shareId, fileId);
        }
        return "";
    }


}

