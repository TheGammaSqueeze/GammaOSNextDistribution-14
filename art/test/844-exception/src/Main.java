/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class Main {
  static Main empty;

  static class MyThread extends Thread {
    public void run() {
      // This will throw at `callMethodThatThrows` and trigger deoptimization checks which we used
      // to crash on.
      new Inner();
    }
  }

  public static class Inner {
    // Have a <clinit> method invoke another <clinit> method to ensure we execute in the
    // interpreter.
    static {
      new Inner2();
    }
  }

  public static class Inner2 {
    static {
      Main.callMethodThatThrows();
    }
  }

  public static void main(String[] args) throws Exception {
    System.loadLibrary(args[0]);
    // Disables use of nterp.
    Main.setAsyncExceptionsThrown();

    // Execute the test in a different thread, to ensure we still
    // return a 0 exit status.
    Thread t = new MyThread();
    t.setUncaughtExceptionHandler((th, e) -> {
      System.out.println("Caught exception");
    });
    t.start();
    t.join();
  }

  public static void callMethodThatThrows() {
    // Ensures we get deoptimization requests.
    Main.forceInterpreterOnThread();
    throw new Error("");
  }

  public static native void forceInterpreterOnThread();
  public static native void setAsyncExceptionsThrown();

}
