package com.github.catvod.utils;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import okhttp3.Response;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

public class Launcher {

    private static int port = -1;

    /**
     * 通过进程名判断进程是否存在
     */
    public static boolean isProcessRunning(String processName) {
        return ProcessHandle.allProcesses().anyMatch(ph -> ph.info().command().map(cmd -> cmd.contains(processName)).orElse(false));
    }

    private static String getServerName() {
        String osKey = detectOs();
        switch (osKey) {
            case "mac":
                return "lu-proxy-server-darwin-amd64";
            case "linux":
                return "lu-proxy-server-linux-amd64";
            default:
                return "lu-proxy-server-windows-amd64.exe";
        }
    }

    private static String getServerPath() {
        return Path.tv() + File.separator + getServerName();
    }


    public static Process launch(String... args) throws Exception {
        String osKey = detectOs();
        String binaryPath = getServerPath();

        if (!osKey.contains("win")) {
            java.nio.file.Path binary = Paths.get(getServerPath());

            // 检查是否有执行权限
            if (!Files.isExecutable(binary)) {
                System.out.println("正在添加执行权限...");
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-xr-x");
                Files.setPosixFilePermissions(binary, perms);
                System.out.println("权限设置完成");
            }
        }

        // 构建命令列表
        List<String> command = new ArrayList<>();
        command.add(binaryPath);
        Collections.addAll(command, args);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    public static void startServer() {
        //1.检测本地文件是否存在，没有就下载文件
        loadServerFiles();

        //2.检测服务是否启动,服务没有启动就启动服务
        if (!isProcessRunning(getServerName())) {
            SpiderDebug.log("服务未启动,正在启动代理服务...");
            try {
                launch();
                // 关键修正：给底层服务 500ms 的启动初始化时间，避免立即扫描端口导致失败
                Thread.sleep(500);
            } catch (Exception e) {
                SpiderDebug.log("启动代理服务失败");
            }

        }
        SpiderDebug.log("服务已启动");
        //3.检测服务端口
        adjustPort();

    }

    private static void loadServerFiles() {
        //1.检测本地文件是否存在，没有就下载文件
        String os = detectOs();
        String binaryPath = getServerPath();
        if (!new File(binaryPath).exists()) {
            try {
                Response result = null;
                if (os.contains("win")) {
                    result = OkHttp.newCall("https://pc.lushunming.qzz.io/json/server-windows-amd64.exe");


                } else if (os.contains("mac")) {
                    result = OkHttp.newCall("https://pc.lushunming.qzz.io/json/server-darwin-amd64", new HashMap<>());

                } else if (os.contains("linux")) {
                    result = OkHttp.newCall("https://pc.lushunming.qzz.io/json/server-linux-amd64", new HashMap<>());
                }

                Files.write(new File(binaryPath).toPath(), result.body().bytes());

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
                if (resp.equals("ser200")) {
                    SpiderDebug.log("Found local server port " + pt);
                    port = pt;
                    break;
                }
                pt++;
            } catch (Exception e) {
                SpiderDebug.log("请求端口 异常：" + e.getMessage());
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

    /**
     * 构建代理链接
     *
     */
    public static String buildProxyUrl(String url, Map<String, String> headers, int threads) {
        String key = Util.MD5(url);
        Map<String, Object> params = new HashMap<>();
        params.put("url", url);
        params.put("headers", headers);
        params.put("key", key);

        OkHttp.post( getHostPort()+ "/buildUrl", Json.toJson(params), new HashMap<>());


        return getProxyUrl() + "?key=" + key + "&threads=" + threads;
    }

    public static String buildProxyUrl(String url, Map<String, String> headers) {


        return buildProxyUrl(url, headers, Runtime.getRuntime().availableProcessors() * 2);
    }

    private static String detectOs() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "mac";
        if (os.contains("nux") || os.contains("nix")) return "linux";
        return "unknown";
    }
}