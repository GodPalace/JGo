package com.godpalace.test;

import com.godpalace.jgo.go.JGo;
import com.godpalace.jgo.util.ByteUtil;
import org.junit.Test;

import java.util.Arrays;

@JGo
public class TestUtil {
    @Test
    public void objectUtilTest() {
        class A {
            private final int a;

            public A(int a) {
                this.a = a;
            }

            public void hello() {
                System.out.println("hello " + a);
            }
        }

        byte[] bytes = ByteUtil.toBytes(new A(10));
        System.out.println(Arrays.toString(bytes));

        A a = ByteUtil.toObject(bytes, A.class);
        a.hello();
    }
}
