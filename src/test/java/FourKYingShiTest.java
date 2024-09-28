import cn.hutool.json.JSONUtil;
import com.github.catvod.spider.DaGongRen;
import com.github.catvod.spider.FourKYingShi;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import common.AssertUtil;
import org.junit.jupiter.api.Test;

/**
 * @author heatdesert
 * @date 2024-01-27 12:51
 * @description
 */
public class FourKYingShiTest {
    private static FourKYingShi douban = new FourKYingShi();

    static {
        try {
            douban.init("{\"site\":\"https://4kvm.site/\"}");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void homeTest() throws Exception {
        String s = douban.homeContent(false);
        System.out.println(JSONUtil.toJsonPrettyStr(JSONUtil.parseObj(s)));
        AssertUtil.INSTANCE.assertResult(s);
    }

    @Test
    public void cateTest() throws Exception {

        //String palyDoc= OkHttp.string("http://www.lzizy9.com/index.php/vod/play/id/79816/sid/1/nid/1.html");

        String s = douban.categoryContent("/classify/hanju", "2", false, Maps.newHashMap());
        System.out.print(JSONUtil.toJsonPrettyStr(JSONUtil.parseObj(s)));
        AssertUtil.INSTANCE.assertResult(s);

    }

    @Test
    public void detailTest() throws Exception {

        String detail = douban.detailContent(Lists.newArrayList("https://www.4kvm.tv/tvshows/jsbfylqc"));
        System.out.print(JSONUtil.toJsonPrettyStr(JSONUtil.parseObj(detail)));

    }

    @Test
    public void playerTest() throws Exception {

        String palyer = douban.playerContent("0", "https://www.4kvm.tv/artplayer?id=147323&source=0&ep=0", null);
        System.out.print(JSONUtil.toJsonPrettyStr(JSONUtil.parseObj(palyer)));

    }

    @Test
    public void searchTest() throws Exception {

        String detail = douban.searchContent("红海", false);
        System.out.print(JSONUtil.toJsonPrettyStr(JSONUtil.parseObj(detail)));

    }

}
