package com.godpalace.jgo.goext.win.dll;

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

    private final ConcurrentHashMap<String, Function> funcs = new ConcurrentHashMap<>();
    private final WinDef.HMODULE hModule;

    private boolean isFreed = false;

    private interface Kernel32Ex extends StdCallLibrary {
        Kernel32Ex INSTANCE = Native.load("kernel32", Kernel32Ex.class);
        Pointer GetProcAddress(WinDef.HMODULE hModule, WTypes.LPSTR lpProcName);
    }

    public DllInvoker(String dllPath) {
        hModule = kernel32.LoadLibraryEx(dllPath, null, 0);
        ExitInvoker.addExitFunction(this::freeDll);
    }

    public Object call(String funcName, Class<?> returnType, Object... args) {
        Function function;
        if (funcs.containsKey(funcName)) {
            function = funcs.get(funcName);
        } else {
            Pointer namePtr = Kernel32Ex.INSTANCE.GetProcAddress(hModule, new WTypes.LPSTR(funcName));
            function = Function.getFunction(namePtr);
            funcs.put(funcName, function);
        }

        return function.invoke(returnType, args);
    }

    public void callVoid(String funcName, Object... args) {
        call(funcName, Void.class, args);
    }

    public void freeDll() {
        if (isFreed) {
            return;
        }

        kernel32.FreeLibrary(hModule);
        isFreed = true;
    }
}
