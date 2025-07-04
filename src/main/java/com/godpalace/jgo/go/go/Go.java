package com.godpalace.jgo.go.go;

import com.godpalace.jgo.JGoFunc;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class Go {
    private static ThreadPoolExecutor executor = null;

    private Go() {
    }

    public static void go(JGoFunc func) {
        if (func == null) {
            throw new IllegalArgumentException("func cannot be null");
        }

        if (executor == null) {
            executor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Integer.MAX_VALUE, 10L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        }

        executor.execute(() -> {
            try {
                func.go();
            } catch (Throwable e) {
                log.error("Error in go function", e);
            }
        });
    }

    public static void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
