package com.godpalace.jgo.goext.win.io.file;

import com.godpalace.jgo.goext.win.io.LockException;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.StdCallLibrary;

import java.io.File;

public class FileLock {
    private static final Kernel32 kernel32 = Kernel32.INSTANCE;

    private interface Kernel32Ex extends StdCallLibrary {
        Kernel32Ex INSTANCE = Native.load("kernel32", Kernel32Ex.class);
        WinDef.BOOL LockFile(WinNT.HANDLE hFile, long dwFileOffsetLow, long dwFileOffsetHigh, long nNumberOfBytesToLockLow, long nNumberOfBytesToLockHigh);
        WinDef.BOOL UnlockFile(WinNT.HANDLE hFile, long dwFileOffsetLow, long dwFileOffsetHigh, long nNumberOfBytesToLockLow, long nNumberOfBytesToLockHigh);
    }

    private final WinNT.HANDLE handle;

    public static FileLock lock(String path) {
        return lock(new File(path));
    }

    public static FileLock lock(File file) {
        WinNT.HANDLE hFile = kernel32.CreateFile(file.getAbsolutePath(), WinNT.GENERIC_READ | WinNT.GENERIC_WRITE, 0, null, WinNT.OPEN_EXISTING, WinNT.FILE_ATTRIBUTE_NORMAL, null);
        if (hFile == null) {
            throw new LockException("Failed to open file: " + file.getAbsolutePath());
        }

        WinDef.BOOL result = Kernel32Ex.INSTANCE.LockFile(hFile, 0, 0, 0, 0);
        if (!result.booleanValue()) {
            throw new LockException("Failed to lock file: " + file.getAbsolutePath());
        }

        return new FileLock(hFile);
    }

    private FileLock(WinNT.HANDLE handle) {
        this.handle = handle;
    }

    public void unlock() {
        WinDef.BOOL result = Kernel32Ex.INSTANCE.UnlockFile(handle, 0, 0, 0, 0);
        if (!result.booleanValue()) {
            throw new LockException("Failed to unlock file");
        }

        kernel32.CloseHandle(handle);
    }
}
