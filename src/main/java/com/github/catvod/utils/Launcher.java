package com.github.catvod.utils;


import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.spider.Init;
import com.github.catvod.spider.LuProxyNative;
import okhttp3.Response;

import java.io.*;
import java.nio.channels.FileLock;
import java.util.HashMap;
import java.util.Map;


public class Launcher {

    public static final String Server_URL = "http://pc.lushunming.qzz.io/json";
    private static int port = -1;
    private static boolean sLibLoaded = false;
    private static LuProxyNative sServer;
    private static final String LOCK_FILE = "server.lock";

    private static String getServerName() {
        //判断系统
        String os = System.getProperty("os.name");
        if (os.toLowerCase().contains("windows")) {
            return "libluserver.dll";
        }
        return "libluserver.so";
    }

    private static String getServerPath() {
        // 使用 Android 的 App 私有内部存储路径 (/data/user/0/包名/files/)
        return Path.tv() + File.separator + getServerName();
    }

    public static void deleteServerFiles() {
        File soFile = new File(getServerPath());
        if (soFile.exists()) {
            soFile.delete();
        }
    }


    public static void launch() {
        try {
            File soFile = new File(getServerPath());
            if (!sLibLoaded) {
                System.load(soFile.getAbsolutePath()); // 全路径加载
                sLibLoaded = true;
            }
            if (sServer == null) {
                sServer = new LuProxyNative();
                sServer.StartServer();
            }
        } catch (Exception e) {
            SpiderDebug.log("启动代理服务失败: " + e.getMessage());
            throw new RuntimeException(e);
        }


    }

    /**
     * 启动服务（注意：Android 端必须在子线程/异步任务中调用此方法！）
     */
    public static void startServer() {
        RandomAccessFile raf = null;
        FileLock lock = null;
        try {
            // ---- 关键修正 1：先检查本地服务是否已经在运行 ----
            adjustPort();
            if (port > 0) {
                SpiderDebug.log("监测到本地代理服务已在后台运行中 (Port: " + port + ")，跳过启动流程。");
                Notify.show("代理服务已在运行");
                return;
            }

            File lockFile = new File(Path.tv(), LOCK_FILE);
            raf = new RandomAccessFile(lockFile, "rw");
            lock = raf.getChannel().tryLock();

            if (lock == null) {
                SpiderDebug.log("服务已在其他ClassLoader或线程中启动，跳过");
                return;
            }

            // 1. 检测本地文件是否存在，没有就下载文件
            loadServerFiles();

            // 2. 检测服务是否启动，没有启动就启动服务
            SpiderDebug.log("服务未启动,正在启动代理服务...");
            try {
                launch();
                // 给底层服务 500ms 的启动初始化时间
                Thread.sleep(500);
            } catch (UnsatisfiedLinkError e) {
                // ---- 关键修正 2：捕获因 ClassLoader 冲突导致的链接错误 ----
                SpiderDebug.log("SO库已被其他ClassLoader加载，尝试直接检测端口... " + e.getMessage());
            } catch (Exception e) {
                SpiderDebug.log("启动代理服务失败: " + e.getMessage());
            }

            SpiderDebug.log("服务启动命令已发送，正在验证端口...");
            // 3. 检测服务端口
            adjustPort();
            if (port > 0) {
                SpiderDebug.log("服务已成功启动");
                Notify.show("启动代理服务成功");
            } else {
                SpiderDebug.log("服务启动失败，未能探测到有效端口");
            }
        } catch (Exception e) {
            SpiderDebug.log("启动失败: " + e);
        } finally {
            // 记得释放文件锁资源
            try {
                if (lock != null) lock.release();
                if (raf != null) raf.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void loadServerFiles() {
        String binaryPath = getServerPath();
        File file = new File(binaryPath);
        if (!file.exists()) {
            try {
                SpiderDebug.log("正在下载 Android 代理二进制文件...");
                String downloadUrl = Server_URL + "/"+getServerName();
                SpiderDebug.log("下载地址：" + downloadUrl);

                Response result = OkHttp.newCall(downloadUrl, new HashMap<>());
                if (result != null && result.body() != null) {


                    // 兼容老版本 Android
                    try (InputStream is = result.body().byteStream(); OutputStream os = new FileOutputStream(file)) {
                        byte[] buffer = new byte[4096];
                        int length;
                        while ((length = is.read(buffer)) > 0) {
                            os.write(buffer, 0, length);
                        }
                    }

                    SpiderDebug.log("下载server完成");
                }
            } catch (IOException e) {
                SpiderDebug.log("下载代理服务失败");
                throw new RuntimeException(e);
            }
        }
    }

    static void adjustPort() {
        if (port > 0) return;
        int pt = 12345;
        while (pt < 12360) {
            try {
                String resp = OkHttp.string("http://127.0.0.1:" + pt, null);
                if (resp != null && resp.equals("ser200")) {
                    SpiderDebug.log("Found local server port " + pt);
                    Notify.show("发现服务端口：" + pt);
                    port = pt;
                    break;
                }
                pt++;
            } catch (Exception e) {
                SpiderDebug.log("请求端口 异常，正在重试下一个... " + e.getMessage());
                // 每次请求失败稍微等待，防止 CPU 轮询空转
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                }
                pt++;
            }
        }
    }

    public static String getHostPort() {
        adjustPort();
        return "http://127.0.0.1:" + port;
    }

    public static String getProxyUrl() {
        return getHostPort() + "/proxy";
    }

    public static String buildProxyUrl(String url, Map<String, String> headers, int threads) {
        String key = Util.MD5(url);
        Map<String, Object> params = new HashMap<>();
        params.put("url", url);
        params.put("headers", headers);
        params.put("key", key);

        OkHttp.post(getHostPort() + "/buildUrl", Json.toJson(params), new HashMap<>());
        return getProxyUrl() + "?key=" + key + "&threads=" + threads;
    }

    public static String buildProxyUrl(String url, Map<String, String> headers) {
        return buildProxyUrl(url, headers, Runtime.getRuntime().availableProcessors() * 2);
    }
}