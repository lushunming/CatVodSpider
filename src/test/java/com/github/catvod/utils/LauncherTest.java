package com.github.catvod.utils;

import org.junit.jupiter.api.Test;

import java.util.Map;

class LauncherTest {


    @Test
    public void homeTest() throws Exception {
      //  Launcher.startServer();

     //   boolean running = Launcher.isProcessRunning("server-windows-amd64.exe");
      //  System.out.println(running);
        Launcher.buildProxyUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ", Map.of("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"), 1);
    }


}