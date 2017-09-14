package com.vertispan.aptexample.app;

import com.vertispan.aptexample.annotation.Sample;

import java.util.List;

@Sample               
public interface App {
    public static void main(String[] args) {
        System.out.println(new App_Impl().Thing());
    }

    List<String> Thing();

    List<String> Thing2();
}
