package com.github.catvod.bean.pan123;

import com.github.catvod.api.Pan123Handler;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.spider.Init;
import com.github.catvod.utils.Path;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class Cache {

    @SerializedName("user")
    private User user;


    public static Cache objectFrom(String str) {
        if(str.isEmpty()) return new Cache();
        SpiderDebug.log("Cache.objectFrom: " + str);
        Cache item = new Gson().fromJson(str, Cache.class);
        return item == null ? new Cache() : item;
    }

    public User getUser() {
        return user == null ? new User() : user;
    }


    public void setUserInfo(User user) {
        this.user = user;
        this.saveUser();
    }

    public void saveUser() {
        SpiderDebug.log("Cache.saveUser: " + toString());
        SpiderDebug.log("Cache.path: " + Pan123Handler.INSTANCE.getCache().getAbsolutePath());
        Init.execute(() -> Path.write(Pan123Handler.INSTANCE.getCache(), toString()));
    }


    @Override
    public String toString() {
        return new Gson().toJson(this);
    }


}
