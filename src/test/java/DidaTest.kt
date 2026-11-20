import com.github.catvod.spider.Ddrk
import com.github.catvod.spider.Dida
import common.AssertUtil
import common.TestInterface
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class DidaTest:TestInterface<Dida> {
    override var t: Dida = Dida().apply { init(" {\"site\":\"https://www.didahd.xyz/\"}") }

    @Test
    override fun homeTest() {
        val homeContent = t.homeContent(false)
        println(homeContent)
        AssertUtil.assertResult(homeContent)
    }

    @Test
    override fun cateTest() {
        val categoryContent = t.categoryContent("/type/1.html", "1", false, hashMapOf())
        println(categoryContent)
        AssertUtil.assertResult(categoryContent)
    }

    @Test
    override fun detailTest() {
        val detailContent = t.detailContent(listOf("/detail/3853.html"))
        println(detailContent)
        AssertUtil.assertResult(detailContent)
    }

    @Test
    override fun playTest() {
        val playerContent = t.playerContent(
            "",
            "/play/3853-6-4.html",
            mutableListOf()
        )
        println(playerContent)
        AssertUtil.assertResult(playerContent)
    }

    @Test
    override fun searchTest() {
        val searchContent = t.searchContent("只是", false)
        println(searchContent)
        AssertUtil.assertResult(searchContent)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun initI(): Unit {

        }
    }
}
