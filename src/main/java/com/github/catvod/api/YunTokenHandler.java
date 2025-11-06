package com.github.catvod.api;


import com.github.catvod.bean.yun.Cache;
import com.github.catvod.bean.yun.User;
import com.github.catvod.utils.Path;

import java.io.File;

public class YunTokenHandler {

    private final Cache cache;

    public File getCache() {
        return Path.tv("yun139");
    }

    private YunTokenHandler() {
        cache = Cache.objectFrom(Path.read(getCache()));
    }

    private static class Loader {
        static volatile YunTokenHandler INSTANCE = new YunTokenHandler();
    }

    public static YunTokenHandler get() {
        return Loader.INSTANCE;
    }


    public String getToken() {
        User user = cache.getUser();
         return user.getCookie();
        //return "cGM6MTg4OTY3ODE2MDE6eTM1Tjd1dG58MXxSQ1N8MTc1NDQ2OTgwNzEyOXxzMlN0T1VEV3lOVmF5V3pNbGFfM2tJbVp1ZmlqSHBqaEhTSzVyNHZqVXNRLmlhV3loSUxHNDFkMUI5N1BqXzhWN0dtVWtKLnBTclhpNGpZU1EuTGZWMTV3MVFoZmNpcEVoZkxUV2tvYjB0bkFTYV9RTUhhaHhveWx6YkdmcEhQdjNCS1lrbnp1LkxaWDdKOE40YkNNRjkzT3piNmx2Y0d3TWdVUkl5b18ubVUt";
    }
}