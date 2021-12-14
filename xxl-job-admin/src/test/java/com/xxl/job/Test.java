package com.xxl.job;


public class Test {
    public void ss() {
        String format = "http://10.233.96.168:9090/,http://10.233.96.183:9090/,http://10.233.96.215:9090/,http://10.233.96.22:9090/,http://10.233.96.24:9090/";
        System.out.println(format.split(",")[0]);
    }

    public void f() {
        for (int i = 1; i <= 500; i++) {
            System.out.println(i % 60);
        }


    }
}
