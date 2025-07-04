package com.godpalace.jgo.go.c;

import com.godpalace.jgo.JGoBoot;
import com.godpalace.jgo.go.embed.EmbedFile;
import com.godpalace.jgo.goext.exit.ExitInvoker;
import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WTypes;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.StdCallLibrary;
import io.github.rctcwyvrn.blake3.Blake3;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class C {
    private static final Kernel32 kernel32 = Kernel32.INSTANCE;
    private static final ConcurrentHashMap<String, Function> funcs = new ConcurrentHashMap<>();

    private static C instance = null;
    private static String compilerPath;
    private static WinDef.HMODULE hModule;

    private interface JGoKernel32 extends StdCallLibrary {
        JGoKernel32 INSTANCE = Native.load("kernel32", JGoKernel32.class);
        Pointer GetProcAddress(WinDef.HMODULE hModule, WTypes.LPSTR lpProcName);
    }

    private C() {
        File cFile = new File(System.getenv("TEMP"), "jgo" + System.currentTimeMillis() + ".c");
        File cDllFile = new File(System.getenv("TEMP"), "jgo.dll");
        File oldHashFile = new File(System.getenv("TEMP"), "jgo.hash");

        try {
            EmbedFile codeFile = new EmbedFile("jgo.c", JGoBoot.getCaller());

            // 计算哈希
            Blake3 blake3 = Blake3.newInstance();
            blake3.update(codeFile.readBytes());
            byte[] hashed = blake3.digest();

            // 读取旧的哈希
            byte[] oldHashed = null;
            if (oldHashFile.exists()) {
                oldHashed = Files.readAllBytes(oldHashFile.toPath());
            }

            if (oldHashed == null || !Arrays.equals(hashed, oldHashed)) {
                // 释放C文件
                codeFile.releaseToFile(cFile);

                // 编译成dll
                String[] command = {compilerPath, "-shared", "-o", cDllFile.getAbsolutePath(), cFile.getAbsolutePath()};
                Process process = Runtime.getRuntime().exec(command);
                process.waitFor();

                // 检查compiler是否成功
                if (process.exitValue() != 0) {
                    System.err.print("ERROR: ");
                    InputStream stream = process.getErrorStream();
                    stream.transferTo(System.err);
                    stream.close();

                    throw new CompileException("Compile failed");
                }
            }

            // 保存哈希
            Files.write(oldHashFile.toPath(), hashed, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

            // 加载dll
            if (hModule != null) {
                throw new IllegalStateException("C already initialized");
            }

            hModule = kernel32.LoadLibraryEx(cDllFile.getAbsolutePath(), null, 0);
            ExitInvoker.addExitFunction(() -> kernel32.FreeLibrary(hModule));
        } catch (InterruptedException e) {
            log.error("Failed to launch compiler, cause: ", new CompileException(e.getMessage()));
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            throw new CompileException("Compile failed, cause: " + e.getMessage());
        } finally {
            try {
                Files.delete(cFile.toPath());
            } catch (Exception ignored) {
                // 不处理
            }
        }
    }

    public static void init() {
        init("gcc");
    }

    /**
     * @param compilerPath 应指向gcc文件
     */
    public static void init(String compilerPath) {
        if (instance != null) {
            throw new IllegalStateException("C already initialized");
        }

        if (compilerPath == null) {
            throw new IllegalArgumentException("compilerPath cannot be null");
        }

        if (!compilerPath.equals("gcc") && !new File(compilerPath).exists()) {
            throw new IllegalArgumentException("compilerPath not found");
        }

        C.compilerPath = compilerPath;
        instance = new C();
    }

    public static Object call(String funcName, Class<?> returnType, Object... args) {
        if (instance == null) {
            init();
        }

        Function function;
        if (funcs.containsKey(funcName)) {
            function = funcs.get(funcName);
        } else {
            Pointer pointer = JGoKernel32.INSTANCE.GetProcAddress(hModule, new WTypes.LPSTR(funcName));
            function = Function.getFunction(pointer);
            funcs.put(funcName, function);
        }

        return function.invoke(returnType, args);
    }

    public static void callVoid(String funcName, Object... args) {
        if (instance == null) {
            init();
        }

        Function function;
        if (funcs.containsKey(funcName)) {
            function = funcs.get(funcName);
        } else {
            Pointer pointer = JGoKernel32.INSTANCE.GetProcAddress(hModule, new WTypes.LPSTR(funcName));
            function = Function.getFunction(pointer);
            funcs.put(funcName, function);
        }

        function.invokeVoid(args);
    }
}
