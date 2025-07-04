package com.godpalace.jgo.goext.sys.dll;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.win32.StdCallLibrary;

public class DllInjector {
    private static final Kernel32 kernel32 = Kernel32.INSTANCE;

    private DllInjector() {
    }

    private interface InjectorDll extends StdCallLibrary {
        InjectorDll INSTANCE = Native.load("Kernel32.dll", InjectorDll.class);
        Pointer GetProcAddress(WinDef.HMODULE hModule, WTypes.LPSTR lpProcName);
    }

    public static void inject(int pid, String dllPath) {
        WinNT.HANDLE hProcess = kernel32.OpenProcess(WinNT.PROCESS_ALL_ACCESS, false, pid);
        if (hProcess == null) {
            throw new HandleException("Failed to open process");
        }

        WinDef.HMODULE hModule = kernel32.LoadLibraryEx("kernel32.dll", null, 0);
        if (hModule == null) {
            throw new HandleException("Failed to load library");
        }

        Pointer loadLibraryA = InjectorDll.INSTANCE.GetProcAddress(hModule, new WTypes.LPSTR("LoadLibraryA"));
        if (loadLibraryA == null) {
            throw new FuncNotFoundException("Failed to get LoadLibraryA address");
        }

        Pointer rMemory = kernel32.VirtualAllocEx(hProcess, null, new BaseTSD.SIZE_T(dllPath.length() + 1L), WinNT.MEM_COMMIT, WinNT.PAGE_READWRITE);
        if (rMemory == null) {
            throw new MemoryException("Failed to allocate memory in process");
        }

        Memory memory = new Memory(dllPath.length() + 1L);
        memory.setString(0, dllPath);
        boolean success = kernel32.WriteProcessMemory(hProcess, rMemory, memory, dllPath.length(), null);
        memory.close();
        if (!success) {
            throw new MemoryException("Failed to write memory in process");
        }

        WinNT.HANDLE hThread = kernel32.CreateRemoteThread(hProcess, null, 0, loadLibraryA, rMemory, 0, null);
        if (hThread == null) {
            throw new HandleException("Failed to create remote thread");
        }

        kernel32.WaitForSingleObject(hThread, WinBase.INFINITE);
        kernel32.VirtualFreeEx(hProcess, rMemory, new BaseTSD.SIZE_T(), WinNT.MEM_RELEASE);
        kernel32.CloseHandle(hThread);
        kernel32.CloseHandle(hProcess);
    }
}
