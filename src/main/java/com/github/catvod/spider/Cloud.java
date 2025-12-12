package com.github.catvod.spider;


import com.github.catvod.api.Pan123Api;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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


    @Override
    public void init(String extend) throws Exception {
        JsonObject ext = StringUtils.isAllBlank(extend) ? new JsonObject() : Json.safeObject(extend);
        quark = new Quark();
        /* ali = new Ali();*/
        uc = new UC();
        tianYi = new TianYi();
        yiDongYun = new YiDongYun();
        baiDuPan = new BaiDuPan();
        pan123 = new Pan123();
        uc.init(ext.has("uccookie") ? ext.get("uccookie").getAsString() : "");
        quark.init(ext.has("cookie") ? ext.get("cookie").getAsString() : "");
        // ali.init(ext.has("token") ? ext.get("token").getAsString() : "");
        yiDongYun.init("");
        baiDuPan.init("");
        pan123.init("");
        tianYi.init(ext.has("tianyicookie") ? ext.get("tianyicookie").getAsString() : "");
    }

    @Override
    public String detailContent(List<String> shareUrl) throws Exception {
       /* if (shareUrl.get(0).matches(Ali.pattern.pattern())) {
            return ali.detailContent(shareUrl);
        } else*/
        if (shareUrl.get(0).matches(patternQuark)) {
            return quark.detailContent(shareUrl);
        } else if (shareUrl.get(0).matches(patternUC)) {
            return uc.detailContent(shareUrl);
        } else if (shareUrl.get(0).contains(URL_CONTAIN)) {
            return tianYi.detailContent(shareUrl);
        } else if (shareUrl.get(0).contains(YiDongYun.URL_START)) {
            return yiDongYun.detailContent(shareUrl);
        } else if (shareUrl.get(0).contains(BaiDuPan.URL_START)) {
            return baiDuPan.detailContent(shareUrl);
        } else if (shareUrl.get(0).matches(Pan123Api.regex)) {
            SpiderDebug.log("Pan123Api shareUrl：" + Json.toJson(shareUrl));
            return pan123.detailContent(shareUrl);
        }
        return null;
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (flag.contains("quark")) {
            return quark.playerContent(flag, id, vipFlags);
        } else if (flag.contains("uc")) {
            return uc.playerContent(flag, id, vipFlags);
        } else if (flag.contains("天意")) {
            return tianYi.playerContent(flag, id, vipFlags);
        } else if (flag.contains("移动")) {
            return yiDongYun.playerContent(flag, id, vipFlags);
        }/* else {
            return ali.playerContent(flag, id, vipFlags);
        }*/ else if (flag.contains("BD")) {
            return baiDuPan.playerContent(flag, id, vipFlags);
        } else if (flag.contains("pan123")) {
            return pan123.playerContent(flag, id, vipFlags);
        }/*else {
            return ali.playerContent(flag, id, vipFlags);
        }*/
        return null;
    }

    protected String detailContentVodPlayFrom(List<String> shareLinks) {
        List<String> from = new ArrayList<>();
        int i = 0;
        for (String shareLink : shareLinks) {
            i++;
            try {
                if (shareLink.matches(patternUC) && uc != null) {
                    from.add(uc.detailContentVodPlayFrom(ImmutableList.of(shareLink), i));
                } else if (shareLink.matches(patternQuark) && quark != null) {
                    from.add(quark.detailContentVodPlayFrom(ImmutableList.of(shareLink), i));
                } /*else if (shareLink.matches(Ali.pattern.pattern()) && ali != null) {
                    from.add(ali.detailContentVodPlayFrom(ImmutableList.of(shareLink)));
                }  */ else if (shareLink.contains(URL_CONTAIN)) {
                    from.add(tianYi.detailContentVodPlayFrom(List.of(shareLink), i));
                } else if (shareLink.contains(YiDongYun.URL_START)) {
                    from.add(yiDongYun.detailContentVodPlayFrom(List.of(shareLink), i));
                } else if (shareLink.contains(BaiDuPan.URL_START)) {
                    from.add(baiDuPan.detailContentVodPlayFrom(List.of(shareLink), i));
                } else if (shareLink.matches(Pan123Api.regex)) {
                    from.add(pan123.detailContentVodPlayFrom(List.of(shareLink), i));
                }
            } catch (Exception e) {
                from.add("解析失败");
            }
        }
        return StringUtils.join(from, "$$$");
    }

    protected String detailContentVodPlayUrl(List<String> shareLinks) throws Exception {
        Collections.sort(shareLinks, Collections.reverseOrder());
        List<String> urls = new CopyOnWriteArrayList<>();
        ExecutorService service = Executors.newFixedThreadPool(4);
        List<CompletableFuture> futures = new ArrayList<CompletableFuture>();
        for (String shareLink : shareLinks) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    if (shareLink.matches(Util.patternUC)) {
                        urls.add(uc.detailContentVodPlayUrl(List.of(shareLink)));
                    } else if (shareLink.matches(Util.patternQuark)) {
                        urls.add(quark.detailContentVodPlayUrl(List.of(shareLink)));
                    }/* else if (shareLink.matches(Util.patternAli)) {
                urls.add(ali.detailContentVodPlayUrl(List.of(shareLink)));
            } */ else if (shareLink.contains(URL_CONTAIN)) {
                        urls.add(tianYi.detailContentVodPlayUrl(List.of(shareLink)));
                    } else if (shareLink.contains(YiDongYun.URL_START)) {
                        urls.add(yiDongYun.detailContentVodPlayUrl(List.of(shareLink)));
                    } else if (shareLink.contains(BaiDuPan.URL_START)) {
                        urls.add(baiDuPan.detailContentVodPlayUrl(List.of(shareLink)));
                    } else if (shareLink.matches(Pan123Api.regex)) {
                        urls.add(pan123.detailContentVodPlayUrl(List.of(shareLink)));
                    }
                } catch (Exception e) {
                    SpiderDebug.log(e);
                }

            }, service));

        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return StringUtils.join(urls, "$$$");
    }
}
