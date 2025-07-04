package com.godpalace.jgo.goext.sys.io.memory;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.win32.StdCallLibrary;

/*
0-1: 引用计数
.....: 共享内存数据
 */
public class SharedMemory {
    private static final Kernel32 kernel32 = Kernel32.INSTANCE;

    private final WinNT.HANDLE hMemory;
    private final WinNT.HANDLE hReadMutex;
    private final WinNT.HANDLE hWriteMutex;
    private final Pointer ptr;

    private final WinDef.DWORD size;
    private final WinDef.DWORD realSize;

    private boolean isReader = false;
    private boolean isWriter = false;

    public interface Kernel32Ex extends StdCallLibrary {
        Kernel32Ex INSTANCE = Native.load("kernel32", Kernel32Ex.class);

        WinNT.HANDLE CreateFileMappingA(WinNT.HANDLE hFile, Pointer lpAttributes, WinDef.DWORD flProtect, WinDef.DWORD dwMaximumSizeHigh, WinDef.DWORD dwMaximumSizeLow, WTypes.LPSTR lpName);
        WinNT.HANDLE OpenFileMappingA(WinDef.DWORD dwDesiredAccess, WinDef.BOOL bInheritHandle, WTypes.LPSTR lpName);
        WinDef.LPVOID MapViewOfFile(WinNT.HANDLE hFileMappingObject, WinDef.DWORD dwDesiredAccess, WinDef.DWORD dwFileOffsetHigh, WinDef.DWORD dwFileOffsetLow, WinDef.DWORD dwNumberOfBytesToMap);
        WinDef.DWORD GetFileSize(WinNT.HANDLE hFile, BaseTSD.DWORD_PTR lpFileSizeHigh);
    }

    public static SharedMemory openSharedMemory(String name) {
        WinNT.HANDLE handle = Kernel32Ex.INSTANCE.OpenFileMappingA(new WinDef.DWORD(WinBase.FILE_MAP_ALL_ACCESS), new WinDef.BOOL(false), new WTypes.LPSTR(name));
        if (handle == null) {
            throw new IllegalArgumentException("Shared memory not exists");
        }

        WinDef.LPVOID ptr = Kernel32Ex.INSTANCE.MapViewOfFile(handle, new WinDef.DWORD(WinBase.FILE_MAP_ALL_ACCESS), new WinDef.DWORD(), new WinDef.DWORD(), new WinDef.DWORD());
        if (ptr == null) {
            throw new IllegalArgumentException("Failed to map view of file");
        }

        // 获取共享内存大小
        WinDef.DWORD realSize = Kernel32Ex.INSTANCE.GetFileSize(handle, null);
        if (realSize.longValue() == WinBase.INVALID_FILE_SIZE) {
            throw new IllegalArgumentException("Failed to get shared memory size");
        }

        return new SharedMemory(name, handle, ptr.getPointer(), realSize);
    }

    private static boolean isSharedMemoryExists(String name) {
        WinNT.HANDLE handle = kernel32.OpenFileMapping(WinBase.FILE_MAP_READ, false, name);
        if (handle != null) {
            kernel32.CloseHandle(handle);
            return true;
        }

        return false;
    }

    private SharedMemory(String name, WinNT.HANDLE hMemory, Pointer ptr, WinDef.DWORD realSize) {
        this.hMemory = hMemory;
        this.ptr = ptr;
        this.realSize = realSize;
        this.size = new WinDef.DWORD(realSize.longValue() - 2);

        hReadMutex = kernel32.OpenMutex(WinBase.MUTEX_ALL_ACCESS, false, name + "_read");
        hWriteMutex = kernel32.OpenMutex(WinBase.MUTEX_ALL_ACCESS, false, name + "_write");

        // 引用计数加1
        short count = ptr.getShort(0);
        ptr.setShort(0, (short) (count + 1));
    }

    public SharedMemory(String name, long size) {
        if (isSharedMemoryExists(name)) {
            throw new IllegalArgumentException("Shared memory already exists");
        }

        this.size = new WinDef.DWORD(size);
        this.realSize = new WinDef.DWORD(2 + size);

        // 创建共享内存
        hMemory = Kernel32Ex.INSTANCE.CreateFileMappingA(WinBase.INVALID_HANDLE_VALUE, null, new WinDef.DWORD(WinNT.PAGE_READWRITE), new WinDef.DWORD(), realSize, new WTypes.LPSTR(name));
        ptr = Kernel32Ex.INSTANCE.MapViewOfFile(hMemory, new WinDef.DWORD(WinBase.FILE_MAP_ALL_ACCESS), new WinDef.DWORD(), new WinDef.DWORD(), new WinDef.DWORD()).getPointer();

        // 创建读写锁
        hReadMutex = kernel32.CreateMutex(null, false, name + "_read");
        hWriteMutex = kernel32.CreateMutex(null, false, name + "_write");

        // 更新引用计数
        ptr.setShort(0, (short) 1);
    }

    public void free() {
        if (isReader) startRead();
        if (isWriter) startWrite();

        kernel32.CloseHandle(hReadMutex);
        kernel32.CloseHandle(hWriteMutex);

        // 引用计数减1
        short count = ptr.getShort(0);
        if (count == 1) {
            kernel32.UnmapViewOfFile(ptr);
            kernel32.CloseHandle(hMemory);
            return;
        }

        kernel32.CloseHandle(hMemory);
        ptr.setShort(0, (short) (count - 1));
    }

    public long getSize() {
        return size.longValue();
    }

    public void startRead() {
        startRead(WinBase.INFINITE);
    }

    public void startRead(int timeout) {
        if (isReader) {
            throw new IllegalStateException("You have already started reading the shared memory");
        }

        if (isWriter) {
            throw new IllegalStateException("You cannot read the shared memory while you are writing to it");
        }

        // 等待锁
        int r = kernel32.WaitForSingleObject(hReadMutex, timeout);
        if (r == WinError.WAIT_TIMEOUT) {
            throw new IllegalStateException("Timeout while waiting for read lock");
        }

        isReader = true;
    }

    public void startWrite() {
        startWrite(WinBase.INFINITE);
    }

    public void startWrite(int timeout) {
        if (isWriter) {
            throw new IllegalStateException("You have already started writing to the shared memory");
        }

        if (isReader) {
            throw new IllegalStateException("You cannot write to the shared memory while you are reading it");
        }

        // 等待锁
        int r = kernel32.WaitForSingleObject(hWriteMutex, timeout);
        if (r == WinError.WAIT_TIMEOUT) {
            throw new IllegalStateException("Timeout while waiting for write lock");
        }

        isWriter = true;
    }

    public void stopRead() {
        if (!isReader) {
            return;
        }

        isReader = false;

        // 释放锁
        kernel32.ReleaseMutex(hReadMutex);
    }

    public void stopWrite() {
        if (!isWriter) {
            return;
        }

        isWriter = false;

        // 释放锁
        kernel32.ReleaseMutex(hWriteMutex);
    }

    public byte[] getBytes(long offset, int length) {
        checkRead();

        if (offset < 0 || offset >= size.longValue()) {
            throw new IllegalArgumentException("Offset out of range");
        }

        if (length < 0 || offset + length > size.longValue()) {
            throw new IllegalArgumentException("Length out of range");
        }

        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = ptr.getByte(2 + offset + i);
        }

        return bytes;
    }

    public void setBytes(long offset, byte[] bytes) {
        checkWrite();

        if (offset < 0 || offset >= size.longValue()) {
            throw new IllegalArgumentException("Offset out of range");
        }

        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Bytes is null or empty");
        }

        if (offset + bytes.length > size.longValue()) {
            throw new IllegalArgumentException("Length out of range");
        }

        for (int i = 0; i < bytes.length; i++) {
            ptr.setByte(2 + offset + i, bytes[i]);
        }
    }

    public boolean getBoolean(long offset) {
        checkRead();

        if (offset < 0 || offset >= size.longValue()) {
            throw new IllegalArgumentException("Offset out of range");
        }

        return ptr.getByte(2 + offset) != 0;
    }

    public void setBoolean(long offset, boolean value) {
        checkWrite();

        if (offset < 0 || offset >= size.longValue()) {
            throw new IllegalArgumentException("Offset out of range");
        }

        ptr.setByte(2 + offset, (byte) (value ? 1 : 0));
    }

    public byte getByte(long offset) {
        checkRead();

        if (offset < 0 || offset >= size.longValue()) {
            throw new IllegalArgumentException("Offset out of range");
        }

        return ptr.getByte(2 + offset);
    }

    public void setByte(long offset, byte value) {
        checkWrite();

        if (offset < 0 || offset >= size.longValue()) {
            throw new IllegalArgumentException("Offset out of range");
        }

        ptr.setByte(2 + offset, value);
    }

    public char getChar(long offset) {
        checkRead();

        if (offset < 0 || offset >= size.longValue() - 1) {
            throw new IllegalArgumentException("Offset out of range");
        }

        return ptr.getChar(2 + offset);
    }

    public void setChar(long offset, char value) {
        checkWrite();

        if (offset < 0 || offset >= size.longValue() - 1) {
            throw new IllegalArgumentException("Offset out of range");
        }

        ptr.setChar(2 + offset, value);
    }

    public String getString(long offset) {
        checkRead();

        StringBuilder sb = new StringBuilder();

        char c = getChar(offset);
        while (c != '\u0000') {
            sb.append(c);
            offset += 2;
            c = getChar(offset);
        }

        return sb.toString();
    }

    public void setString(long offset, String value) {
        checkWrite();

        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Value is null or empty");
        }

        char[] chars = value.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            setChar(offset + i * 2L, chars[i]);
        }

        if (!value.endsWith("\u0000")) {
            setChar(offset + chars.length * 2L, '\u0000');
        }
    }

    public short getShort(long offset) {
        checkRead();

        if (offset < 0 || offset >= size.longValue() - 1) {
            throw new IllegalArgumentException("Offset out of range");
        }

        return ptr.getShort(2 + offset);
    }

    public void setShort(long offset, short value) {
        checkWrite();

        if (offset < 0 || offset >= size.longValue() - 1) {
            throw new IllegalArgumentException("Offset out of range");
        }

        ptr.setShort(2 + offset, value);
    }

    public int getInt(long offset) {
        checkRead();

        if (offset < 0 || offset >= size.longValue() - 3) {
            throw new IllegalArgumentException("Offset out of range");
        }

        return ptr.getInt(2 + offset);
    }

    public void setInt(long offset, int value) {
        checkWrite();

        if (offset < 0 || offset >= size.longValue() - 3) {
            throw new IllegalArgumentException("Offset out of range");
        }

        ptr.setInt(2 + offset, value);
    }

    public long getLong(long offset) {
        checkRead();

        if (offset < 0 || offset >= size.longValue() - 7) {
            throw new IllegalArgumentException("Offset out of range");
        }

        return ptr.getLong(2 + offset);
    }

    public void setLong(long offset, long value) {
        checkWrite();

        if (offset < 0 || offset >= size.longValue() - 7) {
            throw new IllegalArgumentException("Offset out of range");
        }

        ptr.setLong(2 + offset, value);
    }

    public float getFloat(long offset) {
        checkRead();

        if (offset < 0 || offset >= size.longValue() - 3) {
            throw new IllegalArgumentException("Offset out of range");
        }

        return ptr.getFloat(2 + offset);
    }

    public void setFloat(long offset, float value) {
        checkWrite();

        if (offset < 0 || offset >= size.longValue() - 3) {
            throw new IllegalArgumentException("Offset out of range");
        }

        ptr.setFloat(2 + offset, value);
    }

    public double getDouble(long offset) {
        checkRead();

        if (offset < 0 || offset >= size.longValue() - 7) {
            throw new IllegalArgumentException("Offset out of range");
        }

        return ptr.getDouble(2 + offset);
    }

    public void setDouble(long offset, double value) {
        checkWrite();

        if (offset < 0 || offset >= size.longValue() - 7) {
            throw new IllegalArgumentException("Offset out of range");
        }

        ptr.setDouble(2 + offset, value);
    }

    private void checkRead() {
        if (!isReader) {
            throw new IllegalStateException("You must start reading the shared memory before reading");
        }
    }

    private void checkWrite() {
        if (!isWriter) {
            throw new IllegalStateException("You must start writing to the shared memory before writing");
        }
    }
}
