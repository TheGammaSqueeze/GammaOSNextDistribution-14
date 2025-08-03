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

interface Itf {
  public default void m() throws Exception {
    throw new Exception("Don't inline me");
  }
  public default void mConflict() throws Exception {
    throw new Exception("Don't inline me");
  }
}

// This is redefined in src2 with a mConflict method.
interface Itf2 {
}

public class Main implements Itf, Itf2 {

  public static void main(String[] args) {
    System.loadLibrary(args[0]);

    try {
      itf.mConflict();
      throw new Error("Expected IncompatibleClassChangeError");
    } catch (Exception e) {
      throw new Error("Unexpected exception");
    } catch (IncompatibleClassChangeError e) {
      // Expected.
    }
  }

  static Itf itf = new Main();
}
