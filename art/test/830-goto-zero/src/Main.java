/*
 * Copyright (C) 2021 The Android Open Source Project
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

import java.lang.reflect.Method;

public class Main {

    public static void main(String args[]) throws Exception {
      b2302318Test();
    }

    static void b2302318Test() {
      SpinThread st = new SpinThread();
      st.setDaemon(true);
      st.start();
      Thread.yield();
      Runtime.getRuntime().gc();
    }

}
class SpinThread extends Thread {
    public void run() {
      try {
        Class<?> cls = Class.forName("SmaliClass");
        cls.getDeclaredMethod("gotoZero").invoke(null);
      } catch (Exception e) {
        throw new Error(e);
      }
    }
}
