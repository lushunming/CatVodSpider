import com.github.catvod.utils.ProxyServerIns
import org.junit.jupiter.api.Test

class VideoProxyServerTest {


    @Test
    fun proxyTest() {
        ProxyServerIns.stop()
        ProxyServerIns.start()
        val url = ProxyServerIns.buildProxyUrl("http://172.16.1.217:18089/ng-grid/video.mp4", mapOf())
        System.out.println(url)
        while (true) {

        }
    }
}