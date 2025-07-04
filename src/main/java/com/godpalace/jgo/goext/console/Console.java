package com.godpalace.jgo.goext.console;

import lombok.extern.slf4j.Slf4j;

import java.awt.*;

@Slf4j
public class Console {
    public static final String RESET = "\u001B[0m";

    // 字体样式
    public static final String SY_BOLD = "1";
    public static final String SY_FAINT = "2";
    public static final String SY_ITALIC = "3";
    public static final String SY_UNDERLINE = "4";
    public static final String SY_BLINK = "5";
    public static final String SY_F_BLINK = "6";
    public static final String SY_REVERSE = "7";
    public static final String SY_HIDDEN = "8";
    public static final String SY_STRIKETHROUGH = "9";

    private Console() {
    }

    private static boolean isTried = false;
    public static void enableWindowsANSISupport() {
        if (isTried) return;
        isTried = true;

        if (System.getProperty("os.name").startsWith("Windows")) {
            try {
                new ProcessBuilder("cmd", "/c", "chcp 65001").redirectErrorStream(false).start().waitFor();
            } catch (Exception e) {
                log.error("Failed to enable Windows ANSI support", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void color(String str, Color color, String... style) {
        String rgbText = "\u001B[38;2;" + color.getRed() + ";" + color.getGreen() + ";" + color.getBlue() + "m";
        StringBuilder sb = new StringBuilder();
        sb.append(rgbText);

        sb.append("\u001B[");
        for (String s : style) {
            sb.append(s).append(";");
        }
        sb.deleteCharAt(sb.length() - 1).append("m");

        sb.append(str).append(RESET);
        System.out.print(sb);
    }

    public static void color(String str, Color color, Color bgColor, String... style) {
        String rgbText = "\u001B[38;2;" + color.getRed() + ";" + color.getGreen() + ";" + color.getBlue() + "m";
        String bgRgbText = "\u001B[48;2;" + bgColor.getRed() + ";" + bgColor.getGreen() + ";" + bgColor.getBlue() + "m";
        StringBuilder sb = new StringBuilder();
        sb.append(rgbText).append(bgRgbText);

        sb.append("\u001B[");
        for (String s : style) {
            sb.append(s).append(";");
        }
        sb.deleteCharAt(sb.length() - 1).append("m");

        sb.append(str).append(RESET);
        System.out.print(sb);
    }

    public static void colorln(String str, Color color, String... style) {
        String rgbText = "\u001B[38;2;" + color.getRed() + ";" + color.getGreen() + ";" + color.getBlue() + "m";
        StringBuilder sb = new StringBuilder();
        sb.append(rgbText);

        sb.append("\u001B[");
        for (String s : style) {
            sb.append(s).append(";");
        }
        sb.deleteCharAt(sb.length() - 1).append("m");

        sb.append(str).append(RESET);
        System.out.println(sb);
    }

    public static void colorln(String str, Color color, Color bgColor, String... style) {
        String rgbText = "\u001B[38;2;" + color.getRed() + ";" + color.getGreen() + ";" + color.getBlue() + "m";
        String bgRgbText = "\u001B[48;2;" + bgColor.getRed() + ";" + bgColor.getGreen() + ";" + bgColor.getBlue() + "m";
        StringBuilder sb = new StringBuilder();
        sb.append(rgbText).append(bgRgbText);

        sb.append("\u001B[");
        for (String s : style) {
            sb.append(s).append(";");
        }
        sb.deleteCharAt(sb.length() - 1).append("m");

        sb.append(str).append(RESET);
        System.out.println(sb);
    }

    public static void clearConsole() {
        if (System.getProperty("os.name").contains("win")) {
            System.out.print("\033[H\033[2J");
            System.out.flush();
        } else {
            System.out.print("\033[H\033[J");
            System.out.flush();
        }
    }
}
