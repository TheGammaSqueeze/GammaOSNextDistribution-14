package com.bazel.example;

import com.bazel.example_lib.HelloLib;

public class HelloWorld {
  public static void main(String[] args) {
    System.out.println("Hello world!");
    System.out.println("Library says: " + HelloLib.libValue());
  }
}
