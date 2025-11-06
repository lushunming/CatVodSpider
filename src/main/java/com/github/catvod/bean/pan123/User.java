package com.github.catvod.bean.pan123;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class User {

    @SerializedName("expire")
    private long expire;
    @SerializedName("cookie")
    private String cookie;

    @SerializedName("userName")
    private String userName;
    @SerializedName("password")
    private String password;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCookie() {
        return cookie;
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    public long getExpire() {
        return expire;
    }

    public void setExpire(long expire) {
        this.expire = expire;
    }

    public static User objectFrom(String str) {
        User item = new Gson().fromJson(str, User.class);
        return item == null ? new User() : item;
    }


    public void clean() {
        this.cookie = "";
        this.userName = "";
        this.password = "";
        this.expire = 0L;

    }
}
