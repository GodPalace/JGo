package com.godpalace.jgo;

import com.godpalace.jgo.go.JGo;
import com.godpalace.jgo.go.embed.EmbedFile;
import com.godpalace.jgo.go.embed.Embed;
import com.godpalace.jgo.goext.exit.ExitInvoker;
import com.godpalace.jgo.goext.exit.ExitFunc;
import com.godpalace.jgo.util.CallerUtil;
import com.godpalace.jgo.util.CheckUtil;
import com.godpalace.jgo.util.PackageUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

@Slf4j
public class JGoBoot {
    @Getter
    private static Class<?> caller;

    private JGoBoot() {
    }

    public static void setup() {
        setup(CallerUtil.getCallerClass());
    }

    public static void setup(Class<?> caller) {
        JGoBoot.caller = caller;
        log.info("JGoBoot setup for {}", caller.getName());

        Package callerPackage = caller.getPackage();
        List<String> classes = PackageUtil.getClassName(callerPackage.getName());

        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        for (String className : classes) {
            if (className.contains("test-classes")) {
                int index = className.lastIndexOf("test-classes");
                className = className.substring(index + 13);
            }

            try {
                Class<?> aClass = loader.loadClass(className);
                if (!aClass.isAnnotationPresent(JGo.class)) {
                    continue;
                }

                processClass(aClass);
                processFields(aClass);
                processMethods(aClass);

                log.debug("JGoBoot process {} done", className);
            } catch (ClassNotFoundException e) {
                log.error("Unable to load class {}", className, e);
            }
        }
    }

    private static void processClass(Class<?> aClass) {
        // TODO: process class annotation
    }

    private static void processFields(Class<?> aClass) {
        Field[] fields = aClass.getDeclaredFields();

        for (Field field : fields) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);

            try {
                if (field.isAnnotationPresent(Embed.class)) {
                    String value = field.getAnnotation(Embed.class).value();

                    if (field.getType() == String.class) {
                        field.set(null, value);
                        continue;
                    }

                    if (field.getType() == EmbedFile.class) {
                        EmbedFile embedFile = new EmbedFile(value, aClass);
                        field.set(null, embedFile);
                        continue;
                    }

                    if (field.getType() == byte[].class) {
                        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                        EmbedFile embedFile = new EmbedFile(value, aClass);
                        embedFile.releaseToStream(byteOut);
                        field.set(null, byteOut.toByteArray());
                        continue;
                    }

                    if (field.getType() == Image.class) {
                        if (!CheckUtil.isImage(value)) {
                            log.error("Unsupported image format for {}", value);
                            continue;
                        }

                        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                        EmbedFile embedFile = new EmbedFile(value, aClass);
                        embedFile.releaseToStream(byteOut);

                        byte[] bytes = byteOut.toByteArray();
                        Image image = Toolkit.getDefaultToolkit().createImage(bytes);
                        field.set(null, image);
                        continue;
                    }

                    log.error("Unsupported field type {}", field.getType());
                }
            } catch (IllegalAccessException e) {
                log.error("Unable to access field {}", field.getName(), e);
            }
        }
    }

    private static void processMethods(Class<?> aClass) {
        Method[] methods = aClass.getDeclaredMethods();

        for (Method method : methods) {
            String methodName = method.getName();

            try {
                if (!Modifier.isStatic(method.getModifiers())) continue;

                if (method.isAnnotationPresent(ExitFunc.class)) {
                    ExitInvoker.addExitFunction(() -> method.invoke(null));
                }
            } catch (Exception e) {
                log.error("Unable to process method {}", methodName, e);
            }
        }
    }
}
