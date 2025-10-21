package com.bogdan3000.dintegrate;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.nio.file.*;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class ConfigWatcher implements Runnable {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Path configFile;
    private final Path configDir;
    private volatile boolean running = true;

    // антидребезг (мс) — чтобы не дергать перезагрузку несколько раз подряд
    private static final long DEBOUNCE_MS = 500;

    public ConfigWatcher(Path configFile) {
        this.configFile = configFile;
        this.configDir = configFile.getParent();
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        if (configDir == null) {
            LOGGER.warn("[DIntegrate] ConfigWatcher: у файла нет родительской директории");
            return;
        }

        AtomicLong lastTriggered = new AtomicLong(0);

        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            configDir.register(watcher, ENTRY_MODIFY);
            LOGGER.info("[DIntegrate] ConfigWatcher: started for {}", configFile);

            while (running) {
                WatchKey key = watcher.take(); // блокируемся до события
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() != ENTRY_MODIFY) continue;

                    Path changed = (Path) event.context();
                    if (changed == null) continue;

                    // сравниваем только имя файла в папке config
                    if (changed.getFileName().equals(configFile.getFileName())) {
                        long now = Instant.now().toEpochMilli();
                        long last = lastTriggered.get();

                        if (now - last >= DEBOUNCE_MS) {
                            lastTriggered.set(now);
                            LOGGER.info("[DIntegrate] Config file changed — reloading and restarting connection...");
                            // Вызываем «горячую» перезагрузку и рестарт сокета
                            DonateIntegrate.reloadAndRestartFromWatcher();
                        } else {
                            LOGGER.debug("[DIntegrate] Config change debounced");
                        }
                    }
                }
                if (!key.reset()) break;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            LOGGER.info("[DIntegrate] ConfigWatcher: interrupted, stopping");
        } catch (Exception e) {
            LOGGER.error("[DIntegrate] ConfigWatcher error", e);
        }

        LOGGER.info("[DIntegrate] ConfigWatcher: stopped");
    }
}