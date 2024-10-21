import com.github.catvod.spider.Jable;
import com.github.catvod.spider.Miss;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import common.AssertUtil;
import org.junit.jupiter.api.Test;

/**
 * @author heatdesert
 * @date 2024-01-27 12:51
 * @description
 */
public class MissTest {
    private static Miss douban = new Miss();

    static {
        try {
            douban.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void homeTest() throws Exception {
        String s = douban.homeContent(false);
        System.out.println(s);
        AssertUtil.INSTANCE.assertResult(s);
    }

    @Test
    public void cateTest() throws Exception {

        //String palyDoc= OkHttp.string("http://www.lzizy9.com/index.php/vod/play/id/79816/sid/1/nid/1.html");

        String s = douban.categoryContent("https://missav.com/dm513/cn/new", "2", false, Maps.newHashMap());
        System.out.println(s);
        AssertUtil.INSTANCE.assertResult(s);

    }

    @Test
    public void detailTest() throws Exception {

        String detail = douban.detailContent(Lists.newArrayList("https://missav.com/cn/smdy-107"));
        System.out.println(detail);

    }

    @Test
    public void playerTest() throws Exception {

        String palyer = douban.playerContent(null, "https://missav.com/cn/smdy-107", null);
        System.out.println(palyer);

    }

    @Test
    public void searchTest() throws Exception {

        String detail = douban.searchContent("fc", false);
        System.out.println(detail);

    }

}
