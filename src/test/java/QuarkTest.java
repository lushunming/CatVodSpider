

import com.github.catvod.spider.Init;
import com.github.catvod.spider.Quark;
import com.github.catvod.utils.Json;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;


import java.util.ArrayList;
import java.util.Arrays;

public class QuarkTest {


    private static Quark spider;


    @BeforeAll
    public static void init() throws Exception {
        spider = new Quark();

        spider.init("_UP_28A_52_=386;_UP_BT_=html5;_UP_F7E_8D_=0z44HdIBxZZTFH3p1NV%2FwWJIkAWBTYaH20RoPCksvMmyhI6XxrMIHoi8gAqVoKf%2Bfw0hw4mmmcFLHpvA%2Fhicy1HUTu2LBlCP6GF%2FnM%2Bm0IJoj1BQdak3tm1o3OeN1OV9dQAEQ0UDfWTXDik4ZZxmO5Iwvj6IsFkb5GPrrCl5M87ivs0EP%2FjAQTQimMgEdat62Byd22%2BZGM703ymU3s8N9B3XRdiyy8E7vOTidzNw8s%2FWtKAIxWbnCzZn4%2FJMBUubLuroBmVIB9UVOMEdD6uzZJXMxBnUatpyAHLu79tlMNqP8TGNMQXXgvSqK5ufzR58ZeivnehV0qE%2FWt1yDEDt%2BfWrmT4mVs6zZWXvqpzmoV3MeygIUCEakh2GAn6rsLT1b2ZsrSkQkrM6F8u7yQFbh%2F0Q7RCSfK2U6tAXQttwc%2FtDK7HYGyvolg%3D%3D;_UP_6D1_64_=069;_UP_A4A_11_=wb9cc1693e1d486b8b2ac58e4839d64e;_UP_D_=mobilectoken=4IUaeDKAfn3pV-MKaWfg_GHG;__pus=76f683009a07bdd1c1a7c04f05838d4bAASVSWCD2jiL0GI43jmIC5x50sk6Tgbh4UtXFf/vqOUyaX8Aory/bsGsGCTl68Lo7sJPpZpBJdif81oItfZizhzH;__kp=d8da7a20-7522-11f0-8e98-7daef001221f;__kps=AATcZArVgS76EPn0FMaV4HEj;__ktd=sii/iz4ePzEaoVirXul7QQ==;__uid=AATcZArVgS76EPn0FMaV4HEj;__puus=61c63bfbc263adfbe5a859188d35a7efAATp/q8/QupT7IiBR1GWqZhxByonb4UDC4WnFYgIb13B2iYFHdgxSnxdN+95kH1fArNY/hthubIIPd5HiG4ryv0sQlCVH0Hwn5MEsE97xEB0KxcwyHF1K46gv2msmmdgfiWIiWHFOf6fS39+9s90OyrqT0b9KL+2zHp78ncAoEi5hEwyZQ2p5eoq/HSOiPrOepTZx5ZIKgrSu+/aLMT2HVkC");
        //Assert.assertFalse(map.getAsJsonArray("list").isEmpty());
    }

    @org.junit.jupiter.api.Test
    public void detailContent() throws Exception {

        String content = spider.detailContent(Arrays.asList("https://pan.quark.cn/s/469c2acf8640#/list/share"));
        System.out.println("detailContent--" + content);
        JsonObject map = Json.safeObject(content);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        System.out.println("detailContent--" + gson.toJson(map));
    }

    @org.junit.jupiter.api.Test
    public void playerContent() throws Exception {

        String content = spider.playerContent("quark原画11", "d413ebef0b254bcfa3633afcbb620dea++08d1df867eea009ef1fb1b535998138b++51096b3df096++rtIpvMXQI+/OIoISV6uyU1I2oVW6ZtxfCbH8FZwW1t0=", new ArrayList<>());
        System.out.println("playerContent--" + content);
        JsonObject map = Json.safeObject(content);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        System.out.println("playerContent--" + gson.toJson(map));
    }
}