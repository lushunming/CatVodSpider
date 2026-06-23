package com.github.catvod.utils;

import org.junit.jupiter.api.Test;

class LauncherTest {


    @Test
    public void homeTest() throws Exception {
        Launcher.startServer();

        boolean running = Launcher.isProcessRunning("server-windows-amd64.exe");
        System.out.println(running);
    }


}