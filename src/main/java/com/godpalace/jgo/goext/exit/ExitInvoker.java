package com.godpalace.jgo.goext.exit;

import com.godpalace.jgo.JGoFunc;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;

@Slf4j
public class ExitInvoker {
    private static final ArrayList<JGoFunc> exitFunctions = new ArrayList<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (JGoFunc func : exitFunctions) {
                try {
                    func.go();
                } catch (Throwable e) {
                    log.error("Error while executing exit function", e);
                }
            }
        }));
    }

    private ExitInvoker() {
    }

    public static void addExitFunction(JGoFunc func) {
        exitFunctions.add(func);
    }

    public static void removeExitFunction(JGoFunc func) {
        exitFunctions.remove(func);
    }
}
