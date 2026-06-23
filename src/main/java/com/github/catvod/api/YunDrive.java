package com.github.catvod.api;


import com.github.catvod.bean.Result;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.OkResult;
import com.github.catvod.utils.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YunDrive {
    private final Pattern regex = Pattern.compile("https://yun\\.139\\.com/shareweb/#/w/i/([^&]+)");
    private final SecretKeySpec secretKey;
    private final String baseUrl = "https://share-kd-njs.yun.139.com/yun-share/richlifeApp/devapp/IOutLink/";
    private final Map<String, String> baseHeaders = new HashMap<>();

    private final Map<String, JsonObject> cache = new HashMap<>();

    private static class Loader {
        static volatile YunDrive INSTANCE = new YunDrive();
    }

    public static YunDrive get() {
        return Loader.INSTANCE;
    }

    public YunDrive() {

        this.secretKey = new SecretKeySpec("PVGDwmcvfs1uV3d1".getBytes(Charset.defaultCharset()), "AES");
        baseHeaders.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
        baseHeaders.put("Content-Type", "application/json");
        baseHeaders.put("hcy-cool-flag", "1");
        baseHeaders.put("x-deviceinfo", "||3|12.27.0|chrome|131.0.0.0|5c7c68368f048245e1ce47f1c0f8f2d0||windows 10|1536X695|zh-CN|||");

    }

    private String encrypt(String data) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        byte[] ivBytes = new byte[16];
        new SecureRandom().nextBytes(ivBytes);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(ivBytes));
        byte[] encrypted = cipher.doFinal(data.getBytes(Charset.defaultCharset()));
        byte[] combined = new byte[ivBytes.length + encrypted.length];
        System.arraycopy(ivBytes, 0, combined, 0, ivBytes.length);
        System.arraycopy(encrypted, 0, combined, ivBytes.length, encrypted.length);
        return cn.hutool.core.codec.Base64.encode(combined);

    }

    private String decrypt(String data) throws GeneralSecurityException {
        byte[] combined = cn.hutool.core.codec.Base64.decode(data);
        byte[] ivBytes = Arrays.copyOfRange(combined, 0, 16);
        byte[] encrypted = Arrays.copyOfRange(combined, 16, combined.length);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(ivBytes));
        return new String(cipher.doFinal(encrypted), Charset.defaultCharset());
    }

    public String extractLinkID(String url) {
        String linkID = "";
        Matcher matcher = regex.matcher(url);
        boolean finded = matcher.find();
        if (!finded) {
            matcher = Pattern.compile("https://caiyun\\.139\\.com/m/i\\?([^&]+)").matcher(url);
            finded = matcher.find();
        }
        if (!finded) {
            matcher = Pattern.compile("https://yun.139.com/shareweb/#/w/i/([\\w-]+)").matcher(url);
            finded = matcher.find();
        }
        if (!finded) {
            matcher = Pattern.compile("https://caiyun.139.com/w/i/([\\w-]+)").matcher(url);
            finded = matcher.find();
        }

        if (finded) linkID = matcher.group(1);
        return linkID;
    }

    public JsonObject fetchShareInfo(String pCaID, String linkID) throws GeneralSecurityException {
        if (linkID.isEmpty()) throw new IllegalStateException("linkID not initialized");

        String cacheKey = linkID + "-" + pCaID;
        if (cache.containsKey(cacheKey)) return cache.get(cacheKey);

        Map<String, Object> requestBody = Map.of("getOutLinkInfoReq", Map.of("account", "", "linkID", linkID, "passwd", "", "caSrt", 1, "coSrt", 1, "srtDr", 0, "bNum", 1, "pCaID", pCaID, "eNum", 200), "commonAccountInfo", Map.of("account", "", "accountType", 1));


        OkResult okResult = OkHttp.post(baseUrl + "getOutLinkInfoV6", encrypt(Json.toJson(requestBody)), baseHeaders);
        if(Json.safeObject(decrypt(okResult.getBody())).getAsJsonObject().get("data").isJsonNull()){
            SpiderDebug.log("该分享已被取消，无法访问");
            Notify.show("该分享已被取消，无法访问");
            throw new RuntimeException("该分享已被取消，无法访问");
        }
        JsonObject result = Json.safeObject(decrypt(okResult.getBody())).getAsJsonObject("data");
        cache.put(cacheKey, result);
        return result;
    }

    public Map<String, List<Map<String, String>>> processShareData(String url) throws Exception {
        if (url == null || url.isEmpty()) return Collections.emptyMap();

        boolean isUrl = url.startsWith("http");
        String pCaID = isUrl ? "root" : url;
        String linkID = "";
        if (isUrl) linkID = extractLinkID(url);

        List<Map<String, String>> fileList = fetchFileList(pCaID, linkID);
        Map<String, List<Map<String, String>>> result = new LinkedHashMap<>();

        for (Map<String, String> item : fileList) {
            String name = item.get("name");
            List<Map<String, String>> subItems = fetchUrlList(item.get("path"), linkID);
            if (!subItems.isEmpty()) {

                List<Map<String, String>> list = result.get(name);
                if (list == null) {
                    list = new ArrayList<>();
                    result.put(name, list);
                }
                list.addAll(subItems);

            }
        }

        if (result.isEmpty()) {
            List<Map<String, String>> rootItems = fetchFileList(url, linkID);
            List<Map<String, String>> filteredList = new ArrayList<>();
            for (Map<String, String> m : rootItems) {
                if (!m.isEmpty()) {
                    filteredList.add(m);
                }
            }
            result.put("root", filteredList);
        }
        return result;
    }

    private List<Map<String, String>> fetchFileList(String pCaID, String linkID) throws Exception {
        if (pCaID == null) return Collections.emptyList();

        String actualID = pCaID.startsWith("http") ? "root" : pCaID;
        JsonObject response = fetchShareInfo(actualID, linkID);
        if (!response.has("caLst")) return Collections.emptyList();

        List<Map<String, String>> items = new ArrayList<>();
        Pattern filter = Pattern.compile("App|活动中心|免费|1T空间|免流");
        JsonElement array = response.get("caLst");
        if (!array.isJsonNull()) {
            for (JsonElement element : array.getAsJsonArray()) {
                JsonObject entry = element.getAsJsonObject();
                String name = entry.get("caName").getAsString();
                String path = entry.get("path").getAsString();

                if (!filter.matcher(name).find()) {
                    items.add(Map.of("name", name, "path", path));
                    items.addAll(fetchFileList(path, linkID));
                }
            }
        }

        return items;
    }

    private List<Map<String, String>> fetchUrlList(String pCaID, String linkID) throws Exception {
        JsonObject response = fetchShareInfo(pCaID, linkID);
        List<Map<String, String>> items = new ArrayList<>();

        if (response.has("coLst")) {
            for (JsonElement element : response.getAsJsonArray("coLst")) {
                JsonObject entry = element.getAsJsonObject();
                if (entry.get("coType").getAsInt() == 3) {
                    items.add(Map.of("name", entry.get("coName").getAsString(), "contentId", entry.get("coID").getAsString(), "linkID", linkID, "path", entry.get("path").getAsString()));
                }
            }
        } else if (response.has("caLst")) {
            for (JsonElement element : response.getAsJsonArray("caLst")) {
                items.addAll(fetchUrlList(element.getAsJsonObject().get("path").getAsString(), linkID));
            }
        }
        return items;
    }

    public String fetchPlayUrl(String contentId, String linkID) throws Exception {
        Map<String, Object> requestBody = Map.of("getContentInfoFromOutLinkReq", Map.of("contentId", contentId, "linkID", linkID, "account", ""), "commonAccountInfo", Map.of("account", "", "accountType", 1));


        OkResult okResult = OkHttp.post(baseUrl + "getContentInfoFromOutLink", Json.toJson(requestBody), Map.of("Accept-Encoding", "gzip, deflate, br, zstd", "User-Agent", baseHeaders.get("User-Agent")));
        String m3u8 = Json.safeObject(okResult.getBody()).getAsJsonObject("data").getAsJsonObject("contentInfo").get("presentURL").getAsString();

        String m3u8Str = OkHttp.string(m3u8);
        String resultUrl = m3u8;
        for (String s : m3u8Str.split("\n")) {
            if (s.contains("index.m3u8")) {
                resultUrl = s;
                break;
            }
        }
        return m3u8.split("playlist.m3u8")[0] + resultUrl;
    }

    public String get4kVideoInfo(String fid, String linkID) throws Exception {
        String auth = getAuth();
        String phone = getPhone();

        // 构建 JSON 请求体
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> dlFromOutLinkReqV3 = new HashMap<>();
        Map<String, Object> commonAccountInfo = new HashMap<>();

        dlFromOutLinkReqV3.put("linkID", linkID);
        dlFromOutLinkReqV3.put("account", phone);

        Map<String, Object> coIDLst = new HashMap<>();
        coIDLst.put("item", Collections.singletonList(fid));
        dlFromOutLinkReqV3.put("coIDLst", coIDLst);

        commonAccountInfo.put("account", phone);
        commonAccountInfo.put("accountType", 1);

        requestBody.put("dlFromOutLinkReqV3", dlFromOutLinkReqV3);
        requestBody.put("commonAccountInfo", commonAccountInfo);

       /* {
            "dlFromOutLinkReqV3" : {
            "linkID" : "105CpbaJQFYc6",
                    "account" : "18896781601",
                    "coIDLst" : {
                "item" : [ "DFTOdJkuAEwA1011ZpAcj1pl039202404112124392cb/Fkco6TgMKlnJwKbVul0ZKeYT5p2hIioVy" ]
            }
        },
            "commonAccountInfo" : {
            "account" : "18896781601",
                    "accountType" : 1
        }
        }*/


        // 构建请求
        Map<String, String> header = getHeader();


        OkResult okResult = OkHttp.post(baseUrl + "dlFromOutLinkV3", encrypt(Json.toJson(requestBody)), header);
        JsonObject resultJson = Json.safeObject(decrypt(okResult.getBody()));
        if (resultJson.get("resultCode").getAsInt() == 0) {
            return resultJson.getAsJsonObject("data").get("redrUrl").getAsString();

        }

        // 解析 JSON 响应
        return null;
    }

    private static String getPhone() {
        String phone = StringUtils.split(Util.base64Decode(getAuth()), ":")[1];
        SpiderDebug.log("phone:" + phone);
        return phone;
    }

    private static String getAuth() {
        String auth = YunTokenHandler.get().getToken();
        SpiderDebug.log("auth:" + auth);
        return auth;
    }


    private static Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();

        header.put("X-Deviceinfo", "||3|12.27.0|safari|13.1.2|1||macos 10.15.6|1324X381|zh-cn|||");
        header.put("hcy-cool-flag", "1");
        header.put("Authorization", "Basic " + getAuth());
        header.put("Content-Type", "application/json");
        return header;
    }


    public String playerContent(String[] split, String flag) throws Exception {
        String playUrl = "";
        if (flag.contains("原画")) {
            String contentId = split[0];
            String linkID = split[1];
            playUrl = YunDrive.get().get4kVideoInfo(contentId, linkID);
            playUrl = Launcher.buildProxyUrl(playUrl, new HashMap<>());

        } else {
            String contentId = split[0];
            String linkID = split[1];
            playUrl = YunDrive.get().fetchPlayUrl(contentId, linkID);
        }
        return Result.get().url(playUrl).octet().header(getHeader()).string();
    }

}
