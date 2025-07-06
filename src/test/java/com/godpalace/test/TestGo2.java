package com.godpalace.test;

import com.godpalace.jgo.JGoBoot;
import com.godpalace.jgo.go.JGo;
import com.godpalace.jgo.goext.win.io.notice.Notice;

@JGo
public class TestGo2 {
    public static void main(String[] args) {
        JGoBoot.setup();

        Notice notice = new Notice("Test");
        notice.postNotice();
    }
}
