package com.github.catvod.spider;

import com.github.catvod.api.*;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.utils.Notify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Introduce extends Cloud {


    @Override
    public void init(String extend) throws Exception {

        super.init("");
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "UC"));
        classes.add(new Class("2", "quark"));
        classes.add(new Class("3", "天翼"));
        classes.add(new Class("4", "移动"));
        classes.add(new Class("5", "百度"));
        classes.add(new Class("6", "pan123"));
        List<Vod> list = new ArrayList<>();
        String pic = "";
        String name = "UCToken";
        list.add(new Vod("UCToken", name, pic));
        list.add(new Vod("https://pan.quark.cn/s/cb0e3473c3cb", "测试", pic));

        return Result.string(classes, list);
    }


    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        List<Vod> vodList = new ArrayList<>();
        //UC
        if (tid.equals("1")) {
            String pic1 = "https://androidcatvodspider.netlify.app/wechat.png";
            String name1 = "点击设置cookie";
            vodList.add(new Vod("UCCookie", name1, pic1));

            String pic = "https://androidcatvodspider.netlify.app/wechat.png";
            String name = "点击设置Token";
            vodList.add(new Vod("UCToken", name, pic));
            String pic3 = "https://androidcatvodspider.netlify.app/wechat.png";
            String name3 = "点击删除";
            vodList.add(new Vod("UCClean", name3, pic3));

        }
        if (tid.equals("2")) {
            String pic = "https://androidcatvodspider.netlify.app/wechat.png";
            String name = "点击设置Cookie";
            vodList.add(new Vod("QuarkCookie", name, pic));
            String pic3 = "https://androidcatvodspider.netlify.app/wechat.png";
            String name3 = "点击删除";
            vodList.add(new Vod("QuarkClean", name3, pic3));
        }
        if (tid.equals("3")) {
            String pic = "https://androidcatvodspider.netlify.app/wechat.png";
            String name = "点击设置账号";
            vodList.add(new Vod("TianYi", name, pic));
            String pic3 = "https://androidcatvodspider.netlify.app/wechat.png";
            String name3 = "点击删除";
            vodList.add(new Vod("TianYiClean", name3, pic3));
        }
        if (tid.equals("4")) {
            String pic = "https://androidcatvodspider.netlify.app/wechat.png";
            String name = "点击设置Cookie";
            vodList.add(new Vod("YiDongCookie", name, pic));
            String pic3 = "https://androidcatvodspider.netlify.app/wechat.png";
            String name3 = "点击删除";
            vodList.add(new Vod("YiDongClean", name3, pic3));
        }
        if (tid.equals("5")) {
            String pic = "https://androidcatvodspider.netlify.app/wechat.png";
            String name = "点击设置百度";
            vodList.add(new Vod("BDCookie", name, pic));
            String pic3 = "https://androidcatvodspider.netlify.app/wechat.png";
            String name3 = "点击删除";
            vodList.add(new Vod("BDClean", name3, pic3));
        }
        if (tid.equals("6")) {
            String pic = "https://androidcatvodspider.netlify.app/wechat.png";
            String name = "点击设置pan123";
            vodList.add(new Vod("Pan123Cookie", name, pic));
            String pic3 = "https://androidcatvodspider.netlify.app/wechat.png";
            String name3 = "点击删除";
            vodList.add(new Vod("Pan123Clean", name3, pic3));
        }
        return Result.get().vod(vodList).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        Vod item = new Vod();
        //UC Token 扫码
        if (vodId.equals("UCToken")) {
            UCTokenHandler qrCodeHandler = new UCTokenHandler();
            qrCodeHandler.startUC_TOKENScan();
            return Result.string(item);
        } else if (vodId.equals("UCCookie")) {
            UCApi.get().startFlow();
            return Result.string(item);
        } else if (vodId.equals("UCClean")) {
            UCApi.get().getCache().deleteOnExit();
            new UCTokenHandler().getCache().deleteOnExit();
            Notify.show("删除成功");
            return Result.string(item);
        } else if (vodId.equals("QuarkCookie")) {
            QuarkApi.get().initUserInfo();
            return Result.string(item);
        } else if (vodId.equals("QuarkClean")) {
            QuarkApi.get().getCache().deleteOnExit();
            Notify.show("删除成功");
            return Result.string(item);
        } else if (vodId.equals("TianYi")) {
            TianYiHandler tianYiHandler = TianYiHandler.get();
            tianYiHandler.startFlow();
            return Result.string(item);
        } else if (vodId.equals("TianYiClean")) {
            TianYiHandler tianYiHandler = TianYiHandler.get();
            tianYiHandler.getCache().deleteOnExit();
            Notify.show("删除成功");
            return Result.string(item);
        } else if (vodId.equals("YiDongCookie")) {
           /* YunTokenHandler yunTokenHandler=YunTokenHandler.get();
            yunTokenHandler.startFlow();*/
            return Result.string(item);
        } else if (vodId.equals("YiDongClean")) {
            YunTokenHandler yunTokenHandler = YunTokenHandler.get();
            yunTokenHandler.getCache().deleteOnExit();
            Notify.show("删除成功");
            return Result.string(item);
        } else if (vodId.equals("BDCookie")) {
            BaiDuYunHandler baiDuYunHandler = BaiDuYunHandler.get();
            baiDuYunHandler.startScan();
            return Result.string(item);
        } else if (vodId.equals("BDClean")) {
            BaiDuYunHandler baiDuYunHandler = BaiDuYunHandler.get();
            baiDuYunHandler.getCache().deleteOnExit();
            Notify.show("删除成功");
            return Result.string(item);
        } else if (vodId.equals("Pan123Cookie")) {
            Pan123Handler.INSTANCE.startFlow();
            return Result.string(item);
        } else if (vodId.equals("Pan123Clean")) {
            Pan123Handler.INSTANCE.getCache().deleteOnExit();
            Notify.show("删除成功");
           
            return Result.string(item);
        } else {
            item.setVodId(vodId);
            item.setVodName("测试");
            item.setVodPlayUrl(super.detailContentVodPlayUrl(List.of(vodId)));
            item.setVodPlayFrom(super.detailContentVodPlayFrom(List.of(vodId)));
            return Result.string(item);
        }


    }

}