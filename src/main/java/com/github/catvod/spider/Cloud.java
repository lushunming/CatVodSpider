package com.github.catvod.spider;


import com.github.catvod.api.Pan123Api;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.github.catvod.api.TianyiApi.URL_CONTAIN;
import static com.github.catvod.spider.Quark.patternQuark;
import static com.github.catvod.spider.UC.patternUC;

/**
 * @author ColaMint & Adam & FongMi
 */
public class Cloud extends Spider {
    private Quark quark = null;
    private Ali ali = null;
    private UC uc = null;
    private TianYi tianYi = null;
    private YiDongYun yiDongYun = null;
    private BaiDuPan baiDuPan = null;
    private Pan123 pan123 = null;
    private static final Map<String, ImmutablePair<List<String>, List<String>>> resultMap = new HashMap<>();

    private boolean isQuarkInit = false;
    private boolean isAliInit = false;
    private boolean isUCInit = false;
    private boolean isUCTokenInit = false;
    private boolean isTianYiInit = false;
    private boolean isYiDongYunInit = false;
    private boolean isBaiDuPanInit = false;
    private boolean isPan123Init = false;

    //获取配置文件状态
    private void getConfigFileState() {


        File[] files = Path.tv().listFiles(file -> file.getName().startsWith("."));
        if (files != null) {
            for (File file : files) {
                if (file.getName().equals(".quark")) {
                    isQuarkInit = true;
                }
                if (file.getName().equals(".ali")) {
                    isAliInit = true;
                }
                if (file.getName().equals(".uc")) {
                    isUCInit = true;
                }
                if (file.getName().equals(".uctoken")) {
                    isUCTokenInit = true;
                }
                if (file.getName().equals(".tianyi")) {
                    isTianYiInit = true;
                }
                if (file.getName().equals(".yun139")) {
                    isYiDongYunInit = true;
                }
                if (file.getName().equals(".bd")) {
                    isBaiDuPanInit = true;
                }
                if (file.getName().equals(".pan123")) {
                    isPan123Init = true;
                }
            }
        }
    }

    @Override
    public void init(String extend) throws Exception {
        getConfigFileState();
        JsonObject ext = StringUtils.isAllBlank(extend) ? new JsonObject() : Json.safeObject(extend);
        if (isQuarkInit) {
            quark = new Quark();
            quark.init(ext.has("cookie") ? ext.get("cookie").getAsString() : "");
        }
        /* if (isAliInit) {
            ali = new Ali();
            ali.init(ext.has("token") ? ext.get("token").getAsString() : "");
        }*/
        if (isUCInit&&isUCTokenInit) {
            uc = new UC();
            uc.init(ext.has("uccookie") ? ext.get("uccookie").getAsString() : "");
        }
        if (isTianYiInit) {
            tianYi = new TianYi();
            tianYi.init(ext.has("tianyicookie") ? ext.get("tianyicookie").getAsString() : "");
        }
        if (isYiDongYunInit) {
            yiDongYun = new YiDongYun();
            yiDongYun.init("");
        }
        if (isBaiDuPanInit) {
            baiDuPan = new BaiDuPan();
            baiDuPan.init("");
        }
        if (isPan123Init) {
            pan123 = new Pan123();
            pan123.init("");
        }




    }

    @Override
    public String detailContent(List<String> shareUrl) throws Exception {
       /* if (shareUrl.get(0).matches(Ali.pattern.pattern())) {
            return ali.detailContent(shareUrl);
        } else*/
        if (shareUrl.get(0).matches(patternQuark) && quark != null) {
            return quark.detailContent(shareUrl);
        } else if (shareUrl.get(0).matches(patternUC) && uc != null) {
            return uc.detailContent(shareUrl);
        } else if (shareUrl.get(0).contains(URL_CONTAIN) && tianYi != null) {
            return tianYi.detailContent(shareUrl);
        } else if (shareUrl.get(0).contains(YiDongYun.URL_START) && yiDongYun != null) {
            return yiDongYun.detailContent(shareUrl);
        } else if (shareUrl.get(0).contains(BaiDuPan.URL_START) && baiDuPan != null) {
            return baiDuPan.detailContent(shareUrl);
        } else if (shareUrl.get(0).matches(Pan123Api.regex) && pan123 != null) {
            SpiderDebug.log("Pan123Api shareUrl：" + Json.toJson(shareUrl));
            return pan123.detailContent(shareUrl);
        }
        return null;
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (flag.contains("quark") && quark != null) {
            return quark.playerContent(flag, id, vipFlags);
        } else if (flag.contains("uc") && uc != null) {
            return uc.playerContent(flag, id, vipFlags);
        } else if (flag.contains("天意") && tianYi != null) {
            return tianYi.playerContent(flag, id, vipFlags);
        } else if (flag.contains("移动") && yiDongYun != null) {
            return yiDongYun.playerContent(flag, id, vipFlags);
        }/* else {
            return ali.playerContent(flag, id, vipFlags);
        }*/ else if (flag.contains("BD") && baiDuPan != null) {
            return baiDuPan.playerContent(flag, id, vipFlags);
        } else if (flag.contains("pan123") && pan123 != null) {
            return pan123.playerContent(flag, id, vipFlags);
        }/*else {
            return ali.playerContent(flag, id, vipFlags);
        }*/
        return null;
    }

    protected String detailContentVodPlayFrom(List<String> shareLinks) {
        ImmutablePair<List<String>, List<String>> pairs = resultMap.get(Util.MD5(Json.toJson(shareLinks)));
        if (pairs != null && pairs.left != null && !pairs.left.isEmpty()) {
            return StringUtils.join(pairs.right, "$$$");
        }

        getPlayFromAndUrl(shareLinks);
        pairs = resultMap.get(Util.MD5(Json.toJson(shareLinks)));
        if (pairs != null && pairs.left != null && !pairs.left.isEmpty()) {
            return StringUtils.join(pairs.right, "$$$");
        }
        return "";
    }

    protected String detailContentVodPlayUrl(List<String> shareLinks) throws ExecutionException, InterruptedException {

        ImmutablePair<List<String>, List<String>> pairs = resultMap.get(Util.MD5(Json.toJson(shareLinks)));
        if (pairs != null && pairs.left != null && !pairs.left.isEmpty()) {
            return StringUtils.join(pairs.left, "$$$");
        }

        getPlayFromAndUrl(shareLinks);
        pairs = resultMap.get(Util.MD5(Json.toJson(shareLinks)));
        if (pairs != null && pairs.left != null && !pairs.left.isEmpty()) {
            return StringUtils.join(pairs.left, "$$$");
        }
        return "";
    }


    //同時获取from 和url ，放入缓存，只要一个函数执行就行，避免重复执行
    private void getPlayFromAndUrl(List<String> shareLinks) {
        ExecutorService service = Executors.newFixedThreadPool(4);
        try {  //首先清空缓存，避免太多缓存
            resultMap.clear();
            List<String> urls = new ArrayList<>();
            List<String> froms = new ArrayList<>();
            Map<String, String> map = new ConcurrentHashMap<>(shareLinks.size());


            CountDownLatch latch = new CountDownLatch(shareLinks.size());
            int i = 0;
            for (String shareLink : shareLinks) {

                int finalI = ++i;
                service.submit(() -> {

                    String url = "";
                    String from = "";
                    if (shareLink.matches(Util.patternUC) && uc != null) {
                        url = uc.detailContentVodPlayUrl(List.of(shareLink));
                        from = uc.detailContentVodPlayFrom(List.of(shareLink), finalI);
                    } else if (shareLink.matches(Util.patternQuark) && quark != null) {
                        url = quark.detailContentVodPlayUrl(List.of(shareLink));
                        from = quark.detailContentVodPlayFrom(List.of(shareLink), finalI);
                    }/* else if (shareLink.matches(Util.patternAli)) {
                urls.add(ali.detailContentVodPlayUrl(List.of(shareLink)));
            } */ else if (shareLink.contains(URL_CONTAIN) && tianYi != null) {
                        url = tianYi.detailContentVodPlayUrl(List.of(shareLink));
                        from = tianYi.detailContentVodPlayFrom(List.of(shareLink), finalI);
                    } else if (shareLink.contains(YiDongYun.URL_START) && yiDongYun != null) {
                        url = yiDongYun.detailContentVodPlayUrl(List.of(shareLink));
                        from = yiDongYun.detailContentVodPlayFrom(List.of(shareLink), finalI);
                    } else if (shareLink.contains(BaiDuPan.URL_START) && baiDuPan != null) {
                        url = baiDuPan.detailContentVodPlayUrl(List.of(shareLink));
                        from = baiDuPan.detailContentVodPlayFrom(List.of(shareLink), finalI);
                    } else if (shareLink.matches(Pan123Api.regex) && pan123 != null) {
                        url = pan123.detailContentVodPlayUrl(List.of(shareLink));
                        from = pan123.detailContentVodPlayFrom(List.of(shareLink), finalI);
                    }
                    //只有连接不为空才放入进去
                    if (StringUtils.isNoneBlank(url)) {

                        map.put(url, from);
                    }
                    latch.countDown();

                });


            }

            latch.await();
            map.forEach((k, v) -> {
                urls.add(k);
                froms.add(v);
            });

            resultMap.put(Util.MD5(Json.toJson(shareLinks)), new ImmutablePair<>(urls, froms));

            SpiderDebug.log("---urls：" + Json.toJson(urls));
            SpiderDebug.log("---froms：" + Json.toJson(froms));
        } catch (Exception e) {
            SpiderDebug.log("获取异步结果出错：" + e);
        } finally {
            service.shutdown();
        }


    }
}
