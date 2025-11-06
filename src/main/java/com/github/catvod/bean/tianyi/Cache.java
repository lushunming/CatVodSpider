package com.github.catvod.bean.tianyi;

import com.github.catvod.api.TianYiHandler;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.spider.Init;
import com.github.catvod.utils.Path;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class Cache {

    @SerializedName("user")
    private User user;


    public static Cache objectFrom(String str) {
        SpiderDebug.log("Cache.objectFrom: " + str);
        Cache item = new Gson().fromJson(str, Cache.class);
        return item == null ? new Cache() : item;
    }

    public User getUser() {
        return user == null ? new User("") : user;
    }


    public void setTianyiUser(User user) {
        this.user = user;
        this.saveTianyiUser();
    }

    public void saveTianyiUser() {
        Init.execute(() -> Path.write( TianYiHandler.get().getCache(), toString()));
    }

    public void saveTianyieUser() {
        Init.execute(() -> Path.write( TianYiHandler.get().geteCache(), toString()));
    }


    @Override
    public String toString() {
        return new Gson().toJson(this);
    }


    public void setTianyieUser(User user) {
        this.user = user;
        this.saveTianyieUser();
    }
}
