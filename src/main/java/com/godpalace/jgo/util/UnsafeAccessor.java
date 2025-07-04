package com.godpalace.jgo.util;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Slf4j
public final class UnsafeAccessor {
    @Getter
    private static final Unsafe unsafe;

    private static Method method;

    private UnsafeAccessor() {
    }

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new UnsafeException(e.getMessage());
        }
    }

    public static void ensureClassInitialized(Class<?> clazz) {
        try {
            if (method == null) {
                method = unsafe.getClass().getDeclaredMethod("ensureClassInitialized", Class.class);
                method.setAccessible(true);
            }

            method.invoke(unsafe, clazz);
        } catch (Exception e) {
            throw new UnsafeException(e.getMessage());
        }
    }
}
