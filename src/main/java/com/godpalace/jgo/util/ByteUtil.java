package com.godpalace.jgo.util;

import com.godpalace.jgo.goext.sys.dll.MemoryException;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class ByteUtil {
    private static final Unsafe unsafe = UnsafeAccessor.getUnsafe();

    private ByteUtil() {
    }

    public static byte[] toBytes(Object obj) {
        // 获取对象大小
        int size = sizeOf(obj);
        byte[] bytes = new byte[size];

        // 获取对象地址
        long address = getObjectAddress(obj);

        // 将内存复制到字节数组
        for (int i = 0; i < size; i++) {
            bytes[i] = unsafe.getByte(address + i);
        }

        return bytes;
    }

    public static <T> T toObject(byte[] bytes, Class<T> clazz) {
        try {
            // 获取对象大小
            int size = bytes.length;

            // 创建对象
            Object obj = unsafe.allocateInstance(clazz);

            // 获取对象地址
            long address = getObjectAddress(obj);

            // 将字节数组复制到内存
            for (int i = 0; i < size; i++) {
                unsafe.putByte(address + i, bytes[i]);
            }

            return (T) obj;
        } catch (Exception e) {
            throw new MemoryException(e.getMessage());
        }
    }

    public static long getObjectAddress(Object obj) {
        Object[] array = new Object[]{obj};
        long baseOffset = unsafe.arrayBaseOffset(Object[].class);
        return unsafe.getLong(array, baseOffset);
    }

    public static int sizeOf(Object obj) {
        // 获取对象的类
        Class<?> clazz = obj.getClass();

        // 最大字段偏移量
        long maxOffset = 0;

        // 遍历所有字段
        while (clazz != null) {
            for (Field f : clazz.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    long offset = unsafe.objectFieldOffset(f);
                    if (offset > maxOffset) maxOffset = offset;
                }
            }
            clazz = clazz.getSuperclass();
        }

        // 计算大小（最大偏移 + 字段大小 + 对齐填充）
        return ((int) (maxOffset/8 + 1)) * 8;
    }
}
