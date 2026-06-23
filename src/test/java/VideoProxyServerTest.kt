import com.github.catvod.utils.ProxyServer
import com.github.catvod.utils.ProxyServerIns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Test

class VideoProxyServerTest {


    @Test
    fun proxyTest() {
       // GlobalScope.launch(Dispatchers.IO) {
            ProxyServer.stop()
            ProxyServer.start()
      //  }

        //val url = Launcher.buildProxyUrl("http://172.16.1.217:18089/ng-grid/video.mp4", mapOf())
        val url = Launcher.buildProxyUrl("https://storage.googleapis.com/exoplayer-test-media-1/mkv/android-screens-lavf-56.36.100-aac-avc-main-1280x720.mkv", mapOf())
        System.out.println(url)
        while (true) {

        }
    }
}