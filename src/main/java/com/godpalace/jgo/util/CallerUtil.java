package com.godpalace.jgo.util;

import java.lang.reflect.Method;
import java.util.Optional;

public final class CallerUtil {
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(
            StackWalker.Option.RETAIN_CLASS_REFERENCE
    );

    private CallerUtil() {
    }

    public static StackTraceElement getCallerElement() {
        Optional<StackTraceElement> caller = STACK_WALKER.walk(frames ->
                frames.skip(2) // 跳过当前方法帧和getCallerMethod帧
                        .findFirst()
                        .map(StackWalker.StackFrame::toStackTraceElement)
        );

        return caller.orElseThrow(() ->
                new IllegalStateException("无法获取调用者方法信息")
        );
    }

    public static Class<?> getCallerClass() {
        Optional<Class<?>> caller = STACK_WALKER.walk(frames ->
                frames.skip(2) // 跳过当前方法帧和getCallerMethod帧
                        .findFirst()
                        .map(StackWalker.StackFrame::getDeclaringClass)
        );

        return caller.orElseThrow(() ->
                new IllegalStateException("无法获取调用者方法信息")
        );
    }

    public static Method getCallerMethod() {
        Optional<Class<?>> callerClass = STACK_WALKER.walk(frames ->
                frames.skip(2) // 跳过当前方法帧和getCallerMethod帧
                        .findFirst()
                        .map(StackWalker.StackFrame::getDeclaringClass)
        );

        Optional<String> callerMethodName = STACK_WALKER.walk(frames ->
                frames.skip(2) // 跳过当前方法帧和getCallerMethod帧
                        .findFirst()
                        .map(StackWalker.StackFrame::getMethodName)
        );

        if (callerClass.isEmpty() || callerMethodName.isEmpty()) {
            throw new IllegalStateException("无法获取调用者方法信息");
        }

        Class<?> aClass = callerClass.get();

        for (Method method : aClass.getDeclaredMethods()) {
            if (method.getName().equals(callerMethodName.get())) {
                return method;
            }
        }

        throw new IllegalStateException("没有找到调用者方法信息");
    }
}
