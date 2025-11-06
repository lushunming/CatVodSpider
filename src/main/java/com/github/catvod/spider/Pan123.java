package com.github.catvod.spider;

import com.github.catvod.api.Pan123Api;
import com.github.catvod.bean.Result;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Util;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author lushunming
 */
public class Pan123 extends Spider {


    @Override
    public void init(String extend) throws Exception {

        if (StringUtils.isNoneBlank(extend)) {
            // Pan123Api.INSTANCE.setAuth(extend);
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {

        @NotNull Map<@NotNull String, @NotNull String> shareData = Pan123Api.INSTANCE.getShareData(ids.get(0));
        return Result.string(Pan123Api.INSTANCE.getVod(shareData.get("key"), shareData.get("sharePwd")));
    }


    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return Pan123Api.INSTANCE.playerContent(id, flag);

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
            return TextUtils.join("$$$",  Pan123Api.INSTANCE.getPlayFormatList());
        }*/

        for (int i = 1; i <= ids.size(); i++) {

            for (String s : Pan123Api.INSTANCE.getPlayFormatList()) {
                playFrom.add(String.format(Locale.getDefault(), "pan123" + s + "#%02d%02d", i, index));

            }
            //  playFrom.add("天意" + i + index);
        }
        return Util.stringJoin("$$$", playFrom);
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
            @NotNull Map<@NotNull String, @NotNull String> shareData = Pan123Api.INSTANCE.getShareData(id);
            try {
                playUrl.add(Pan123Api.INSTANCE.getVod(shareData.get("key"), shareData.get("sharePwd")).getVodPlayUrl());
            } catch (Exception e) {
                SpiderDebug.log("获取播放地址出错:" + e.getMessage());
            }
        }
        return Util.stringJoin("$$$", playUrl);
    }


}
