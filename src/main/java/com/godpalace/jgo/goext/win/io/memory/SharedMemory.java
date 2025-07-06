package com.godpalace.jgo.goext.win.io.memory;

import com.godpalace.jgo.goext.exit.ExitInvoker;
import com.godpalace.jgo.goext.win.io.IOTimeoutException;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.win32.StdCallLibrary;
import lombok.Getter;

import java.nio.charset.StandardCharsets;

/*
0-1: 引用计数
2-2: 是否支持多读者
...: 共享内存数据
 */
public class SharedMemory {
    private static final Kernel32 kernel32 = Kernel32.INSTANCE;
    private static final int OFFSET = 3;

    private final WinNT.HANDLE hMemory;
    private final WinNT.HANDLE hReadMutex;
    private final WinNT.HANDLE hWriteMutex;
    private final Pointer ptr;

    private final WinDef.DWORD size;
    private final WinDef.DWORD realSize;

    @Getter
    private final boolean supportMultiReader;

    private boolean isReader = false;
    private boolean isWriter = false;

    @Getter
    private boolean isFreed = false;

    private interface Kernel32Ex extends StdCallLibrary {
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

        // 获取是否支持多读者
        boolean supportMultiReader = ptr.getPointer().getByte(2) != 0;

        return new SharedMemory(name, handle, ptr.getPointer(), realSize, supportMultiReader);
    }

    private static boolean isSharedMemoryExists(String name) {
        WinNT.HANDLE handle = kernel32.OpenFileMapping(WinBase.FILE_MAP_READ, false, name);
        if (handle != null) {
            kernel32.CloseHandle(handle);
            return true;
        }

        return false;
    }

    private SharedMemory(String name, WinNT.HANDLE hMemory, Pointer ptr, WinDef.DWORD realSize, boolean supportMultiReader) {
        this.hMemory = hMemory;
        this.ptr = ptr;
        this.realSize = realSize;
        this.size = new WinDef.DWORD(realSize.longValue() - OFFSET);
        this.supportMultiReader = supportMultiReader;

        hReadMutex = supportMultiReader? null : kernel32.OpenMutex(WinBase.MUTEX_ALL_ACCESS, false, name + "_read");
        hWriteMutex = kernel32.OpenMutex(WinBase.MUTEX_ALL_ACCESS, false, name + "_write");

        // 引用计数加1
        short count = ptr.getShort(0);
        ptr.setShort(0, (short) (count + 1));
    }

    public SharedMemory(String name, long size) {
        this(name, size, false);
    }

    public SharedMemory(String name, long size, boolean supportMultiReader) {
        if (isSharedMemoryExists(name)) {
            throw new IllegalArgumentException("Shared memory already exists");
        }

        this.size = new WinDef.DWORD(size);
        this.realSize = new WinDef.DWORD(OFFSET + size);
        this.supportMultiReader = supportMultiReader;

        // 创建共享内存
        hMemory = Kernel32Ex.INSTANCE.CreateFileMappingA(WinBase.INVALID_HANDLE_VALUE, null, new WinDef.DWORD(WinNT.PAGE_READWRITE), new WinDef.DWORD(), realSize, new WTypes.LPSTR(name));
        ptr = Kernel32Ex.INSTANCE.MapViewOfFile(hMemory, new WinDef.DWORD(WinBase.FILE_MAP_ALL_ACCESS), new WinDef.DWORD(), new WinDef.DWORD(), new WinDef.DWORD()).getPointer();

        // 写入多读者标志
        ptr.setByte(2, (byte) (supportMultiReader ? 1 : 0));

        // 创建读写锁
        hReadMutex = supportMultiReader? null : kernel32.CreateMutex(null, false, name + "_read");
        hWriteMutex = kernel32.CreateMutex(null, false, name + "_write");

        // 更新引用计数
        ptr.setShort(0, (short) 1);

        // 注册退出函数
        ExitInvoker.addExitFunction(this::free);
    }

    public void free() {
        if (isFreed) return;
        isFreed = true;

        if (isReader) stopRead();
        if (isWriter) stopWrite();

        kernel32.CloseHandle(hReadMutex);
        kernel32.CloseHandle(hWriteMutex);

        short count = ptr.getShort(0);
        if (count == 1) {
            kernel32.UnmapViewOfFile(ptr);
            kernel32.CloseHandle(hMemory);
            return;
        }

        // 引用计数减1
        kernel32.CloseHandle(hMemory);
        ptr.setShort(0, (short) (count - 1));
    }

    public long getSize() {
        return size.longValue();
    }

    public short getRefCount() {
        checkFreed();
        return ptr.getShort(0);
    }

    public void startRead() {
        startRead(WinBase.INFINITE);
    }

    public void startRead(int timeout) {
        checkFreed();

        if (isReader) {
            throw new IllegalStateException("You have already started reading the shared memory");
        }

        if (isWriter) {
            throw new IllegalStateException("You cannot read the shared memory while you are writing to it");
        }

        if (!supportMultiReader) {
            // 等待锁
            int r = kernel32.WaitForSingleObject(hReadMutex, timeout);
            if (r == WinError.WAIT_TIMEOUT) {
                throw new IOTimeoutException("Timeout while waiting for read lock");
            }
        }

        isReader = true;
    }

    public void startWrite() {
        startWrite(WinBase.INFINITE);
    }

    public void startWrite(int timeout) {
        checkFreed();

        if (isWriter) {
            throw new IllegalStateException("You have already started writing to the shared memory");
        }

        if (isReader) {
            throw new IllegalStateException("You cannot write to the shared memory while you are reading it");
        }

        // 等待锁
        int r = kernel32.WaitForSingleObject(hWriteMutex, timeout);
        if (r == WinError.WAIT_TIMEOUT) {
            throw new IOTimeoutException("Timeout while waiting for write lock");
        }

        isWriter = true;
    }

    public void stopRead() {
        checkFreed();

        if (!isReader) {
            return;
        }

        isReader = false;

        if (!supportMultiReader) {
            // 释放锁
            kernel32.ReleaseMutex(hReadMutex);
        }
    }

    public void stopWrite() {
        checkFreed();

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
            bytes[i] = ptr.getByte(OFFSET + offset + i);
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
            ptr.setByte(OFFSET + offset + i, bytes[i]);
        }
    }

    public boolean getBoolean(long offset) {
        checkRead();

        if (offset < 0 || offset >= size.longValue()) {
            throw new IllegalArgumentException("Offset out of range");
        }

        return ptr.getByte(OFFSET + offset) != 0;
    }

    public void setBoolean(long offset, boolean value) {
        checkWrite();

        if (offset < 0 || offset >= size.longValue()) {
            throw new IllegalArgumentException("Offset out of range");
        }

        ptr.setByte(OFFSET + offset, (byte) (value ? 1 : 0));
    }

    public byte getByte(long offset) {
        checkRead();

        if (offset < 0 || offset >= size.longValue()) {
            throw new IllegalArgumentException("Offset out of range");
        }

        return ptr.getByte(OFFSET + offset);
    }

    public void setByte(long offset, byte value) {
        checkWrite();

        if (offset < 0 || offset >= size.longValue()) {
            throw new IllegalArgumentException("Offset out of range");
        }

        ptr.setByte(OFFSET + offset, value);
    }

    public char getChar(long offset) {
        checkRead();

        if (offset < 0 || offset >= size.longValue() - 1) {
            throw new IllegalArgumentException("Offset out of range");
        }

        return ptr.getChar(OFFSET + offset);
    }

    public void setChar(long offset, char value) {
        checkWrite();

        if (offset < 0 || offset >= size.longValue() - 1) {
            throw new IllegalArgumentException("Offset out of range");
        }

        ptr.setChar(OFFSET + offset, value);
    }

    public String getString(long offset) {
        checkRead();

        if (offset < 0 || offset >= size.longValue()) {
            throw new IllegalArgumentException("Offset out of range");
        }

        int length = 0;
        byte b = ptr.getByte(OFFSET + offset);
        while (b != (byte) 0) {
            if (offset + 1 >= size.longValue()) throw new IllegalArgumentException("Length out of range");
            length++;
            b = ptr.getByte(OFFSET + offset + length);
        }

        byte[] bytes = getBytes(offset, length);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public void setString(long offset, String value) {
        checkWrite();

        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Value is null or empty");
        }

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        byte[] data = new byte[bytes.length + 1];
        System.arraycopy(bytes, 0, data, 0, bytes.length);
        data[bytes.length] = 0;

        setBytes(offset, data);
    }

    public short getShort(long offset) {
        checkRead();

        if (offset < 0 || offset >= size.longValue() - 1) {
            throw new IllegalArgumentException("Offset out of range");
        }

        return ptr.getShort(OFFSET + offset);
    }

    public void setShort(long offset, short value) {
        checkWrite();

        if (offset < 0 || offset >= size.longValue() - 1) {
            throw new IllegalArgumentException("Offset out of range");
        }

        ptr.setShort(OFFSET + offset, value);
    }

    public int getInt(long offset) {
        checkRead();

        if (offset < 0 || offset >= size.longValue() - 3) {
            throw new IllegalArgumentException("Offset out of range");
        }

        return ptr.getInt(OFFSET + offset);
    }

    public void setInt(long offset, int value) {
        checkWrite();

        if (offset < 0 || offset >= size.longValue() - 3) {
            throw new IllegalArgumentException("Offset out of range");
        }

        ptr.setInt(OFFSET + offset, value);
    }

    public long getLong(long offset) {
        checkRead();

        if (offset < 0 || offset >= size.longValue() - 7) {
            throw new IllegalArgumentException("Offset out of range");
        }

        return ptr.getLong(OFFSET + offset);
    }

    public void setLong(long offset, long value) {
        checkWrite();

        if (offset < 0 || offset >= size.longValue() - 7) {
            throw new IllegalArgumentException("Offset out of range");
        }

        ptr.setLong(OFFSET + offset, value);
    }

    public float getFloat(long offset) {
        checkRead();

        if (offset < 0 || offset >= size.longValue() - 3) {
            throw new IllegalArgumentException("Offset out of range");
        }

        return ptr.getFloat(OFFSET + offset);
    }

    public void setFloat(long offset, float value) {
        checkWrite();

        if (offset < 0 || offset >= size.longValue() - 3) {
            throw new IllegalArgumentException("Offset out of range");
        }

        ptr.setFloat(OFFSET + offset, value);
    }

    public double getDouble(long offset) {
        checkRead();

        if (offset < 0 || offset >= size.longValue() - 7) {
            throw new IllegalArgumentException("Offset out of range");
        }

        return ptr.getDouble(OFFSET + offset);
    }

    public void setDouble(long offset, double value) {
        checkWrite();

        if (offset < 0 || offset >= size.longValue() - 7) {
            throw new IllegalArgumentException("Offset out of range");
        }

        ptr.setDouble(OFFSET + offset, value);
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

    private void checkFreed() {
        if (isFreed) {
            throw new IllegalStateException("Shared memory has been freed");
        }
    }
}
