package com.godpalace.jgo.goext.sys.dll;

import com.godpalace.jgo.goext.exit.ExitInvoker;
import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WTypes;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.StdCallLibrary;

import java.util.concurrent.ConcurrentHashMap;

public class DllInvoker {
    private static final Kernel32 kernel32 = Kernel32.INSTANCE;

    private final ConcurrentHashMap<String, WTypes.LPSTR> funcs = new ConcurrentHashMap<>();
    private final WinDef.HMODULE hModule;

    private boolean isFreed = false;

    private interface JGoKernel32 extends StdCallLibrary {
        JGoKernel32 INSTANCE = Native.load("kernel32", JGoKernel32.class);
        Pointer GetProcAddress(WinDef.HMODULE hModule, WTypes.LPSTR lpProcName);
    }

    public DllInvoker(String dllPath) {
        hModule = kernel32.LoadLibraryEx(dllPath, null, 0);
        ExitInvoker.addExitFunction(this::freeDll);
    }

    public Object call(String funcName, Class<?> returnType, Object... args) {
        WTypes.LPSTR namePtr;
        if (funcs.containsKey(funcName)) {
            namePtr = funcs.get(funcName);
        } else {
            namePtr = new WTypes.LPSTR(funcName);
            funcs.put(funcName, namePtr);
        }

        Pointer pointer = JGoKernel32.INSTANCE.GetProcAddress(hModule, namePtr);
        if (pointer == null) {
            throw new FuncNotFoundException("Function " + funcName + " not found");
        }

        Function function = Function.getFunction(pointer);
        return function.invoke(returnType, args);
    }

    public void callVoid(String funcName, Object... args) {
        WTypes.LPSTR namePtr;
        if (funcs.containsKey(funcName)) {
            namePtr = funcs.get(funcName);
        } else {
            namePtr = new WTypes.LPSTR(funcName);
            funcs.put(funcName, namePtr);
        }

        Pointer pointer = JGoKernel32.INSTANCE.GetProcAddress(hModule, namePtr);
        if (pointer == null) {
            throw new FuncNotFoundException("Function " + funcName + " not found");
        }

        Function function = Function.getFunction(pointer);
        function.invokeVoid(args);
    }

    public void freeDll() {
        if (isFreed) {
            return;
        }

        kernel32.FreeLibrary(hModule);
        isFreed = true;
    }
}
