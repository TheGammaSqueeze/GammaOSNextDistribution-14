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
    // TODO: Reduce it once the userfaultfd GC is tested long enough.
    static final long DURATION_IN_MILLIS = 10_000;

    static public Object obj = null;
    static public Object[] array = new Object[4096];

    public static void main(String args[]) {
      final long start_time = System.currentTimeMillis();
      long end_time = start_time;
      int idx = 0;
      while (end_time - start_time < DURATION_IN_MILLIS) {
        try {
          // Trigger a null-pointer exception
          System.out.println(obj.toString());
        } catch (NullPointerException npe) {
          // Small enough to be not allocated in large-object space and hence keep the compaction
          // phase longer, while keeping marking phase shorter (as there aren't any references to
          // chase).
          array[idx++] = new byte[3000];
          idx %= array.length;
        }
        end_time = System.currentTimeMillis();
      }
      System.out.println("Done");
    }
}
