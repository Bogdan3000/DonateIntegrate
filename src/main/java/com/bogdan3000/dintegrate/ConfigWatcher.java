package com.bogdan3000.dintegrate;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class ConfigWatcher implements Runnable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private volatile boolean running = true;

    public ConfigWatcher(java.nio.file.Path configPath) {}

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        LOGGER.info("[DIntegrate] ConfigWatcher disabled — watching turned off (use /dpi reload instead)");
        while (running) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}