package com.github.catvod.api;


import cn.hutool.core.codec.Base64Decoder;
import com.github.catvod.bean.tianyi.Cache;
import com.github.catvod.bean.tianyi.User;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.OkResult;
import com.github.catvod.spider.Init;
import com.github.catvod.utils.*;
import com.google.gson.JsonObject;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Cipher;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TianYiHandler {

    public static final String API_URL = "https://open.e.189.cn";
    private ScheduledExecutorService service;
    private JDialog dialog;
    private Cache cache = null;

    public File getCache() {
        return Path.tv("tianyi");
    }

    public File geteCache() {
        return Path.tv("tianyie");
    }

    private String indexUrl = "";

    private String reqId;
    private String lt;

    private SimpleCookieJar cookieJar;

    public SimpleCookieJar getCookieJar() {
        return cookieJar;
    }

    private static class Loader {
        static volatile TianYiHandler INSTANCE = new TianYiHandler();
    }

    public static TianYiHandler get() {
        return Loader.INSTANCE;
    }

    private TianYiHandler() {

        cookieJar = new SimpleCookieJar();
        cache = Cache.objectFrom(Path.read(getCache()));
    }

    /**
     * 初始化
     */
    public void init() {
        String user = cache.getUser().getCookie();
        if (StringUtils.isNoneBlank(user)) {
            JsonObject jsonObject = Json.safeObject(user);
            String username = jsonObject.get("username").getAsString();
            String password = jsonObject.get("password").getAsString();
            if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
                this.startFlow();
                return;
            }
            this.loginWithPassword(username, password);
        } else {
            this.startFlow();
        }
    }

    public void cleanCookie() {

        cache.setTianyiUser(new User(""));
    }

    private Map<String, String> getHeader(String url) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        headers.put("Cookie", cookieJar.loadForRequest(url));
        return headers;
    }

    public void refreshCookie() throws IOException {

        String url = "https://cloud.189.cn/api/portal/loginUrl.action?redirectURL=https%3A%2F%2Fcloud.189.cn%2Fweb%2Fredirect.html&defaultSaveName=3&defaultSaveNameCheck=uncheck&browserId=16322f24d9405fb83331c3f6ce971b53";
        String index = OkHttp.getLocation(url, getHeader(url));
        SpiderDebug.log("unifyAccountLogin：" + index);

        Map<String, List<String>> resHeaderMap = OkHttp.getLocationHeader(index, getHeader(index));
        saveCookie(resHeaderMap.get("Set-Cookie"), index);
        indexUrl = resHeaderMap.get("Location").get(0);
        SpiderDebug.log("callbackUnify: " + indexUrl);

        Map<String, List<String>> callbackUnify = OkHttp.getLocationHeader(indexUrl, getHeader(indexUrl));
        saveCookie(callbackUnify.get("Set-Cookie"), indexUrl);
        SpiderDebug.log("refreshCookie header：" + Json.toJson(callbackUnify));

    }

    /*
     * 保存cookie
     *
     * @param cookie
     * @param url
     */
    private void saveCookie(List<String> cookie, String url) {
        if (cookie != null && cookie.size() > 0) {
            cookieJar.saveFromResponse(url, cookie);
        }
    }

    public byte[] startScan() throws Exception {


        SpiderDebug.log("index ori: " + "https://cloud.189.cn/api/portal/loginUrl.action?redirectURL=https%3A%2F%2Fcloud.189.cn%2Fweb%2Fredirect.html&defaultSaveName=3&defaultSaveNameCheck=uncheck&browserId=dff95dced0b03d9d972d920f03ddd05e");
        String index = OkHttp.getLocation("https://cloud.189.cn/api/portal/loginUrl.action?redirectURL=https://cloud.189.cn/web/redirect.html&defaultSaveName=3&defaultSaveNameCheck=uncheck&browserId=8d38da4344fba4699d13d6e6854319d7", Map.of("Cookie", ""));
        SpiderDebug.log("index red: " + index);
        Map<String, List<String>> resHeaderMap = OkHttp.getLocationHeader(index, getHeader(index));

        saveCookie(resHeaderMap.get("Set-Cookie"), index);

        indexUrl = resHeaderMap.get("Location").get(0);
        SpiderDebug.log("indexUrl red: " + indexUrl);

        HttpUrl httpParams = HttpUrl.parse(indexUrl);
        reqId = httpParams.queryParameter("reqId");
        lt = httpParams.queryParameter("lt");

        Result result = appConf();

        // Step 1: Get UUID
        JsonObject uuidInfo = getUUID();
        String uuid = uuidInfo.get("uuid").getAsString();
        String encryuuid = uuidInfo.get("encryuuid").getAsString();
        String encodeuuid = uuidInfo.get("encodeuuid").getAsString();

        // Step 2: Get QR Code
        byte[] byteStr = downloadQRCode(encodeuuid, reqId);

       // Init.run(() -> showQRCode(byteStr));
        // Step 3: Check login status
        // return
        Init.execute(() -> startService(uuid, encryuuid, reqId, lt, result.paramId, result.returnUrl));
        /*Map<String, Object> result = new HashMap<>();
        result.put("qrcode", "data:image/png;base64," + qrCode);
        result.put("status", "NEW");*/
        return byteStr;

    }

    public void loginWithPassword(String uname, String passwd) {
        try {
            // Step 1: 获取加密配置
            JsonObject encryptConf = getEncryptConf();
            String pubKey = encryptConf.getAsJsonObject("data").get("pubKey").getAsString();

            // Step 2: 获取登录参数
            PasswordLoginParams params = getLoginParams();

            // Step 3: 准备请求头
            Map<String, String> headers = buildLoginHeaders(params.lt, params.reqId);

            // Step 4: 获取应用配置
            AppConfig config = getAppConfig(headers);

            // Step 5: 加密凭证
            EncryptedCredentials credentials = encryptCredentials(uname, passwd, pubKey);

            // Step 6: 提交登录
            LoginResult loginResult = submitLogin(headers, config, credentials);

            // Step 7: 处理登录结果
            processLoginResult(loginResult);

            //保存的账号密码
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("username", uname);
            jsonObject.addProperty("password", passwd);
            cache.setTianyiUser(new User(Json.toJson(jsonObject)));

        } catch (Exception e) {
            SpiderDebug.log("登录失败: " + e.getMessage());
            Notify.show("天翼登录失败: " + e.getMessage());
        }
    }

    // 辅助方法实现
    private JsonObject getEncryptConf() throws Exception {
        String url = API_URL + "/api/logbox/config/encryptConf.do?appId=cloud";
        OkResult result = OkHttp.post(url, new HashMap<>(), getHeader(url));
        return Json.safeObject(result.getBody());
    }

    private PasswordLoginParams getLoginParams() throws Exception {
        String url = "https://cloud.189.cn/api/portal/loginUrl.action?redirectURL=https://cloud.189.cn/web/redirect.html?returnURL=/main.action";
        Map<String, List<String>> resHeaderMap = OkHttp.getLocationHeader(url, getHeader(url));


        String redUrl = resHeaderMap.get("Location").get(0);
        resHeaderMap = OkHttp.getLocationHeader(redUrl, getHeader(redUrl));
        HttpUrl httpUrl = HttpUrl.parse(resHeaderMap.get("Location").get(0));
        return new PasswordLoginParams(httpUrl.queryParameter("reqId"), httpUrl.queryParameter("lt"));
    }

    private Map<String, String> buildLoginHeaders(String lt, String reqId) {
        Map<String, String> headers = new HashMap<>(getHeader(API_URL));
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:74.0) Gecko/20100101 Firefox/76.0");
        headers.put("Referer", "https://open.e.189.cn/");
        headers.put("Lt", lt);
        headers.put("Reqid", reqId);
        return headers;
    }

    private AppConfig getAppConfig(Map<String, String> headers) throws Exception {
        Map<String, String> data = new HashMap<>();
        data.put("version", "2.0");
        data.put("appKey", "cloud");

        OkResult result = OkHttp.post(API_URL + "/api/logbox/oauth2/appConf.do", data, headers);
        JsonObject dataObj = Json.safeObject(result.getBody()).getAsJsonObject("data");
        return new AppConfig(dataObj.get("returnUrl").getAsString(), dataObj.get("paramId").getAsString());
    }

    private EncryptedCredentials encryptCredentials(String uname, String passwd, String pubKey) throws Exception {
        SpiderDebug.log("pubKey: " + pubKey);
        PublicKey publicKey = parsePublicKey(pubKey);
        return new EncryptedCredentials(encryptRSA(uname, publicKey), encryptRSA(passwd, publicKey));
    }

    private LoginResult submitLogin(Map<String, String> headers, AppConfig config, EncryptedCredentials credentials) throws Exception {
        Map<String, String> data = new HashMap<>();
        data.put("appKey", "cloud");
        data.put("version", "2.0");
        data.put("accountType", "02");
        //data.put("mailSuffix", "@189.cn");
        data.put("validateCode", "");
        data.put("returnUrl", config.returnUrl);
        data.put("paramId", config.paramId);
        data.put("captchaToken", "");
        data.put("dynamicCheck", "FALSE");
        data.put("clientType", "1");
        data.put("cb_SaveName", "3");
        data.put("isOauth2", "false");
        data.put("userName", "{NRP}" + credentials.encryptedUname);
        data.put("password", "{NRP}" + credentials.encryptedPasswd);

        OkResult result = OkHttp.post(API_URL + "/api/logbox/oauth2/loginSubmit.do", data, headers);
        return new LoginResult(Json.safeObject(result.getBody()).get("toUrl").getAsString(), result.getResp().get("Set-Cookie"));
    }

    private void processLoginResult(LoginResult result) throws Exception {
        saveCookie(result.cookies, API_URL + "/api/logbox/oauth2/loginSubmit.do");

        // 处理重定向
        Map<String, List<String>> okResult = OkHttp.getLocationHeader(result.toUrl, getHeader(result.toUrl));
        saveCookie(okResult.get("Set-Cookie"), result.toUrl);
    }

    // 辅助类
    private static class PasswordLoginParams {
        final String reqId;
        final String lt;

        PasswordLoginParams(String reqId, String lt) {
            this.reqId = reqId;
            this.lt = lt;
        }
    }

    private static class AppConfig {
        final String returnUrl;
        final String paramId;

        AppConfig(String returnUrl, String paramId) {
            this.returnUrl = returnUrl;
            this.paramId = paramId;
        }
    }

    private static class EncryptedCredentials {
        final String encryptedUname;
        final String encryptedPasswd;

        EncryptedCredentials(String encryptedUname, String encryptedPasswd) {
            this.encryptedUname = encryptedUname;
            this.encryptedPasswd = encryptedPasswd;
        }
    }

    private static class LoginResult {
        final String toUrl;
        final List<String> cookies;

        LoginResult(String toUrl, List<String> cookies) {
            this.toUrl = toUrl;
            this.cookies = cookies;
        }
    }

    private PublicKey parsePublicKey(String pubKey) throws Exception {

        byte[] decoded = Base64Decoder.decode(pubKey);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePublic(spec);

    }

    private String encryptRSA(String data, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encrypted = cipher.doFinal(data.getBytes(Charset.defaultCharset()));
        return bytesToHex(encrypted);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString().toUpperCase();
    }

    private String api(String url, Map<String, String> params, Map<String, String> headers, Integer retry, String method) throws InterruptedException {


        int leftRetry = retry != null ? retry : 3;

        OkResult okResult;
        if ("GET".equals(method)) {
            okResult = OkHttp.get(this.API_URL + url, params, headers);
        } else {
            okResult = OkHttp.post(this.API_URL + url, params, headers);
        }
        if (okResult.getResp().get("Set-Cookie") != null) {
            saveCookie(okResult.getResp().get("Set-Cookie"), this.API_URL);
        }

        if (okResult.getCode() != 200 && leftRetry > 0) {
            SpiderDebug.log("请求" + url + " failed;");
            Thread.sleep(1000);
            return api(url, params, headers, leftRetry - 1, method);
        }
        SpiderDebug.log("请求" + url + " 成功;" + "返回结果:" + okResult.getBody());
        return okResult.getBody();
    }

    /**
     * 获取appConf
     *
     * @param
     * @return
     */

    private @NotNull Result appConf() throws Exception {
        Map<String, String> tHeaders = getHeader(API_URL);
        tHeaders.put("Content-Type", "application/x-www-form-urlencoded");
        tHeaders.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:74.0) Gecko/20100101 Firefox/76.0");
        tHeaders.put("Referer", indexUrl);
        tHeaders.put("origin", API_URL);
        tHeaders.put("Lt", lt);
        tHeaders.put("Reqid", reqId);

        Map<String, String> param = new HashMap<>();

        param.put("version", "2.0");
        param.put("appKey", "cloud");
        String paramId;
        String returnUrl;
        String body = api("/api/logbox/oauth2/appConf.do", param, tHeaders, 3, "POST");

        paramId = Json.safeObject(body).get("data").getAsJsonObject().get("paramId").getAsString();
        returnUrl = Json.safeObject(body).get("data").getAsJsonObject().get("returnUrl").getAsString();

        SpiderDebug.log("paramId: " + paramId);
        SpiderDebug.log("returnUrl: " + returnUrl);
        return new Result(paramId, returnUrl);
    }

    public void setCookie(JsonObject obj) {
        cookieJar.setGlobalCookie(obj);
    }

    private static class Result {
        public final String paramId;
        public final String returnUrl;

        public Result(String paramId, String returnUrl) {
            this.paramId = paramId;
            this.returnUrl = returnUrl;
        }
    }


    public JsonObject getUUID() throws InterruptedException {
        Map<String, String> params = new HashMap<>();
        params.put("appId", "cloud");
        Map<String, String> headers = new HashMap<>();
        headers.put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/99.0.4844.51 Safari/537.36");
        headers.put("lt", lt);
        headers.put("reqId", reqId);
        headers.put("referer", indexUrl);


        String body = api("/api/logbox/oauth2/getUUID.do", params, headers, 3, "POST");
        return Json.safeObject(body);

    }

    public byte[] downloadQRCode(String uuid, String reqId) throws IOException {


        Map<String, String> headers = new HashMap<>();
        headers.put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/99.0.4844.51 Safari/537.36");

        headers.put("referer", indexUrl);

        //  OkResult okResult = OkHttp.get("https://open.e.189.cn/api/logbox/oauth2/image.do", params, headers);
//.addQueryParameter("uuid", uuid).addQueryParameter("REQID", reqId)
        HttpUrl url = HttpUrl.parse(API_URL + "/api/logbox/oauth2/image.do?uuid=" + uuid + "&REQID=" + reqId).newBuilder().build();

        Request request = new Request.Builder().url(url).headers(Headers.of(headers)).build();
        Response response = OkHttp.newCall(request);
        if (response.code() == 200) {
            return response.body().bytes();
        }
        return null;
    }


    private Map<String, Object> checkLoginStatus(String uuid, String encryuuid, String reqId, String lt, String paramId, String returnUrl) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("appId", "cloud");
        params.put("encryuuid", encryuuid);
        params.put("uuid", uuid);
        params.put("date", DateFormatUtils.format(new Date(), "yyyy-MM-ddHH:mm:ss") + new Random().nextInt(24));
        params.put("returnUrl", URLEncoder.encode(returnUrl, "UTF-8"));
        params.put("clientType", "1");
        params.put("timeStamp", (System.currentTimeMillis() / 1000 + 1) + "000");
        params.put("cb_SaveName", "0");
        params.put("isOauth2", "false");
        params.put("state", "");
        params.put("paramId", paramId);
        Map<String, String> headers = new HashMap<>();
        headers.put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/99.0.4844.51 Safari/537.36");
        headers.put("referer", indexUrl);
        headers.put("Reqid", reqId);

        String body = api("/api/logbox/oauth2/qrcodeLoginState.do", params, headers, 3, "POST");
        //  OkResult okResult = OkHttp.post(API_URL + "/api/logbox/oauth2/qrcodeLoginState.do", params, headers);
        SpiderDebug.log("qrcodeLoginState result------" + body);

        JsonObject obj = Json.safeObject(body).getAsJsonObject();
        if (Objects.nonNull(obj.get("status")) && obj.get("status").getAsInt() == 0) {

            SpiderDebug.log("扫码成功------" + obj.get("redirectUrl").getAsString());
            String redirectUrl = obj.get("redirectUrl").getAsString();


            fetchUserInfo(redirectUrl);


        } else {
            SpiderDebug.log("扫码失败------" + body);
        }


        return null;
    }

    private void fetchUserInfo(String redirectUrl) throws IOException {


        Map<String, List<String>> okResult = OkHttp.getLocationHeader(redirectUrl, getHeader(redirectUrl));
        saveCookie(okResult.get("Set-Cookie"), redirectUrl);
        SpiderDebug.log("扫码返回数据：" + Json.toJson(okResult));
        if (okResult.containsKey("Set-Cookie")) {

            //停止检验线程，关闭弹窗
            stopService();
        }


       /* if (okResult.getCode() == 200) {
            okResult.getBody();
        }*/
        return;

    }


/*    *//**
     * 显示qrcode
     *
     * @param
     *//*
   public void showQRCode(byte[] bytes) {
        try {
            int size = ResUtil.dp2px(240);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
            ImageView image = new ImageView(Init.context());
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImageBitmap(QRCode.Bytes2Bimap(bytes));
            FrameLayout frame = new FrameLayout(Init.context());
            params.gravity = Gravity.CENTER;
            frame.addView(image, params);
            dialog = new AlertDialog.Builder(Init.getActivity()).setView(frame).setOnCancelListener(this::dismiss).setOnDismissListener(this::dismiss).show();
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            Notify.show("请使用天翼网盘App扫描二维码");
        } catch (Exception ignored) {
        }
    }*/
    public void startFlow() {
        Init.run(this::showInput);
    }




    private void showInput() {
        try {
            JPanel jPanel = new JPanel();
            jPanel.setSize(Swings.dp2px(200), Swings.dp2px(80));

            JTextField nameField = new JTextField();
            nameField.setName("请输入天翼用户名");
            nameField.setColumns(Swings.dp2px(38));
            JTextField passwordField = new JTextField();
            passwordField.setName("请输入天翼密码");
            passwordField.setColumns(Swings.dp2px(38));
            JButton button = new JButton("Ok");
            jPanel.add(nameField);
            jPanel.add(passwordField);
            jPanel.add(button);


            JDialog jDialog = Util.showDialog(jPanel, "请输入天意用户名和密码");
            button.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    onPositive(nameField.getText().toString(), passwordField.getText().toString());
                    jDialog.dispose();
                }
            });
        } catch (Exception ignored) {
        }
    }


    private void onPositive(String username, String password) {
        dismiss();
        Init.execute(() -> {
            loginWithPassword(username, password);


        });
    }

    private void dismiss() {
        try {
            if (dialog != null) {
                dialog.setVisible(false);
                dialog.dispose();
            }
        } catch (Exception e) {
        }
    }

    private void stopService() {
        if (service != null) service.shutdownNow();
        Init.run(this::dismiss);
    }

    public void startService(String uuid, String encryuuid, String reqId, String lt, String paramId, String returnUrl) {
        SpiderDebug.log("----start  checkLoginStatus  service");

        service = Executors.newScheduledThreadPool(1);

        service.scheduleWithFixedDelay(() -> {
            SpiderDebug.log("----checkLoginStatus ing....");
            try {
                checkLoginStatus(uuid, encryuuid, reqId, lt, paramId, returnUrl);
            } catch (Exception e) {
                SpiderDebug.log("----checkLoginStatus error" + e.getMessage());
                throw new RuntimeException(e);
            }

        }, 1, 3, TimeUnit.SECONDS);
    }

}