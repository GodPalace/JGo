package com.godpalace.jgo.util;

public final class CheckUtil {
    private CheckUtil() {
    }

    public static boolean isImage(String path) {
        String suffix = path.substring(path.lastIndexOf(".") + 1).toLowerCase();
        return "jpg".equals(suffix) || "jpeg".equals(suffix) || "png".equals(suffix) || "gif".equals(suffix) || "bmp".equals(suffix) || "ico".equals(suffix) || "wbmp".equals(suffix) || "webp".equals(suffix) || "cur".equals(suffix) || "ani".equals(suffix) || "tif".equals(suffix) || "tiff".equals(suffix) || "jfif".equals(suffix) || "jpe".equals(suffix) || "jif".equals(suffix) || "jfi".equals(suffix);
    }
}
