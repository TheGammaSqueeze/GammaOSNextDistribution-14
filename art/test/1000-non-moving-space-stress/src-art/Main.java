/*
 * Copyright (C) 2018 The Android Open Source Project
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

import dalvik.system.VMRuntime;
import java.lang.ref.Reference;  // For reachabilityFence.
import java.util.ArrayList;

public class Main {
  private static final boolean SHOULD_PRINT = false;  // True causes failure.

  public static void main(String[] args) throws Exception {
    VMRuntime runtime = VMRuntime.getRuntime();

    try {
      int N = 1024 * 1024;
      int S = 512;
      for (int n = 0; n < N; ++n) {
        // Allocate unreachable objects.
        $noinline$Alloc(runtime);
        // Allocate an object with a substantial size to increase memory
        // pressure and eventually trigger non-explicit garbage collection
        // (explicit garbage collections triggered by java.lang.Runtime.gc()
        // are always full GCs). Upon garbage collection, the objects
        // allocated in $noinline$Alloc used to trigger a crash.
        Object[] moving_array = new Object[S];
      }
    } catch (OutOfMemoryError e) {
      System.out.println("Unexpected OOME");
    }
    Runtime.getRuntime().gc();
    int numAllocs = 0;
    ArrayList<Object> chunks = new ArrayList<>();
    try {
      final int MAX_PLAUSIBLE_ALLOCS = 1024 * 1024;
      for (numAllocs = 0; numAllocs < MAX_PLAUSIBLE_ALLOCS; ++numAllocs) {
        chunks.add(runtime.newNonMovableArray(Object.class, 252));  // About 1KB
      }
      // If we get here, we've allocated about 1GB of nonmovable memory, which
      // should be impossible.
    } catch (OutOfMemoryError e) {
      chunks.remove(0);  // Give us a little space back.
      if (((Object[]) (chunks.get(42)))[17] != null) {
        System.out.println("Bad entry in chunks array");
      } else {
        chunks.clear();  // Recover remaining space.
        if (SHOULD_PRINT) {
          System.out.println("Successfully allocated " + numAllocs + " non-movable KBs");
        }
        System.out.println("passed");
      }
      Reference.reachabilityFence(chunks);
      return;
    }
    Reference.reachabilityFence(chunks);
    System.out.println("Failed to exhaust non-movable space");
  }

  // When using the Concurrent Copying (CC) collector (default collector),
  // this method allocates an object in the non-moving space and an object
  // in the region space, make the former reference the later, and returns
  // nothing (so that none of these objects are reachable upon return).
  static void $noinline$Alloc(VMRuntime runtime) {
    Object[] non_moving_array = (Object[]) runtime.newNonMovableArray(Object.class, 1);
    // Small object, unlikely to trigger garbage collection.
    non_moving_array[0] = new Object();
  }

}
