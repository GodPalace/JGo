package com.godpalace.jgo.goext.win.io.notice;

import com.godpalace.jgo.goext.exit.ExitInvoker;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;

public class Notice {
    private static final Kernel32 kernel32 = Kernel32.INSTANCE;

    private final WinNT.HANDLE hEvent;

    public Notice(String title) {
        hEvent = kernel32.CreateEvent(null, false, false, title);
        if (hEvent == null) {
            throw new EventException("Failed to create event");
        }

        ExitInvoker.addExitFunction(() -> kernel32.CloseHandle(hEvent));
    }

    public void waitNotice() {
        waitNotice(WinBase.INFINITE);
    }

    public void waitNotice(int timeout) {
        kernel32.WaitForSingleObject(hEvent, timeout);
    }

    public void postNotice() {
        kernel32.SetEvent(hEvent);
    }
}
