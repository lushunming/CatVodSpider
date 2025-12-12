package com.github.catvod.spider;

import com.github.catvod.api.TianyiApi;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.tianyi.ShareData;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author lushunming
 */
public class TianYi extends Spider {


    @Override
    public void init(String extend) throws Exception {

        TianyiApi.get().setCookie(extend);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {

        ShareData shareData = TianyiApi.get().getShareData(ids.get(0), "");
        return Result.string(TianyiApi.get().getVod(shareData));
    }


    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return TianyiApi.get().playerContent(id.split("\\+\\+"), flag);

    }

    /**
     * 獲取詳情內容視頻播放來源（多 shared_link）
     *
     * @param ids share_link 集合
     * @param
     * @return 詳情內容視頻播放來源
     */
    public String detailContentVodPlayFrom(List<String> ids, int index) {
        List<String> playFrom = new ArrayList<>();
       /* if (ids.size() < 2){
            return TextUtils.join("$$$",  TianyiApi.get().getPlayFormatList());
        }*/

        for (int i = 1; i <= ids.size(); i++) {

            for (String s : TianyiApi.get().getPlayFormatList()) {
                playFrom.add(String.format(Locale.getDefault(), "天意" + s + "#%02d_%02d", i, index));

            }
            //  playFrom.add("天意" + i + index);
        }
        return StringUtils.join(playFrom, "$$$");
    }

    /**
     * 獲取詳情內容視頻播放地址（多 share_link）
     *
     * @param ids share_link 集合
     * @return 詳情內容視頻播放地址
     */
    public String detailContentVodPlayUrl(List<String> ids) throws Exception {
        List<String> playUrl = new ArrayList<>();
        for (String id : ids) {
            ShareData shareData = TianyiApi.get().getShareData(id, "");
            try {
                playUrl.add(TianyiApi.get().getVod(shareData).getVodPlayUrl());
            } catch (Exception e) {
                SpiderDebug.log("获取播放地址出错:" + e.getMessage());
            }
        }
        return StringUtils.join(playUrl, "$$$");
    }


}
