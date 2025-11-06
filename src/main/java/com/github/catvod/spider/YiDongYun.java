package com.github.catvod.spider;


import com.github.catvod.api.YunDrive;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.Util;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author ColaMint & Adam & FongMi
 */
public class YiDongYun extends Spider {


    public static final CharSequence URL_START = "yun.139";

    @Override
    public void init(String extend) throws Exception {


    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        Vod vod = getVod(ids);

        return Result.string(vod);
    }

    private static @NotNull Vod getVod(List<String> ids) throws Exception {
        Vod.VodPlayBuilder builder = new Vod.VodPlayBuilder();
        String vodName = "";
        Map<String, List<Map<String, String>>> result = YunDrive.get().processShareData(ids.get(0));
        List<Vod.VodPlayBuilder.PlayUrl> list = new ArrayList<>();
        for (String s : result.keySet()) {
            vodName = s;
            for (Map<String, String> stringStringMap : result.get(s)) {
                Vod.VodPlayBuilder.PlayUrl playUrl = new Vod.VodPlayBuilder.PlayUrl();
                playUrl.url = stringStringMap.get("contentId") + "++" + stringStringMap.get("linkID");
                playUrl.name = stringStringMap.get("name");
                list.add(playUrl);
            }

        }
        builder.append("移动(极速)", list);
        List<Vod.VodPlayBuilder.PlayUrl> list2 = new ArrayList<>();

        for (String s : result.keySet()) {
            vodName = s;
            for (Map<String, String> stringStringMap : result.get(s)) {
                Vod.VodPlayBuilder.PlayUrl playUrl = new Vod.VodPlayBuilder.PlayUrl();
                playUrl.url = stringStringMap.get("path") + "++" + stringStringMap.get("linkID");
                playUrl.name = stringStringMap.get("name");
                list2.add(playUrl);
            }

        }
        builder.append("移动(原画)", list2);
        Vod.VodPlayBuilder.BuildResult buildResult = builder.build();
        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodPic("");
        vod.setVodYear("");
        vod.setVodName(vodName);
        vod.setVodContent("");
        vod.setVodPlayFrom(buildResult.vodPlayFrom);
        vod.setVodPlayUrl(buildResult.vodPlayUrl);
        return vod;
    }


    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {

        return YunDrive.get().playerContent(id.split("\\+\\+"), flag);
    }

    /**
     * 獲取詳情內容視頻播放來源（多 shared_link）
     *
     * @param ids share_link 集合
     * @param i
     * @return 詳情內容視頻播放來源
     */
    public String detailContentVodPlayFrom(List<String> ids, int index) {
        List<String> playFrom = new ArrayList<>();
        int i = 0;
        for (String id : ids) {
            i++;
            playFrom.add("移动(极速)" + i);
            playFrom.add("移动(原画)" + i);

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
            playUrl.add(getVod(List.of(id)).getVodPlayUrl());
        }
        return Util.stringJoin("$$$", playUrl);
    }


}
