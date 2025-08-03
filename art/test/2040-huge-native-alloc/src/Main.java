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

import dalvik.system.VMRuntime;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

public class Main {

  static final int HOW_MANY_HUGE = 120;  // > 1GB to trigger blocking in default config.
  int allocated = 0;
  int deallocated = 0;
  static Object lock = new Object();
  final static int MAX_TRIES = 10;
  WeakReference<BufferHolder>[] references = new WeakReference[HOW_MANY_HUGE];

  class BufferHolder {
    private ByteBuffer buffer;
    BufferHolder() {
      ++allocated;
      buffer = getHugeNativeBuffer();
    }
    protected void finalize() {
      synchronized(lock) {
        ++deallocated;
      }
      deleteHugeNativeBuffer(buffer);
      buffer = null;
    }
  }

  // Repeatedly inform the GC of native allocations. Return the time (in nsecs) this takes.
  private static long timeNotifications() {
    final VMRuntime vmr = VMRuntime.getRuntime();
    final long startNanos = System.nanoTime();
    // Iteration count must be >= Heap::kNotifyNativeInterval.
    for (int i = 0; i < 400; ++i) {
      vmr.notifyNativeAllocation();
    }
    return System.nanoTime() - startNanos;
  }

  public static void main(String[] args) {
    System.loadLibrary(args[0]);
    System.out.println("Main Started");
    for (int i = 1; i <= MAX_TRIES; ++i) {
      Runtime.getRuntime().gc();
      if (new Main().tryToRun(i == MAX_TRIES)) {
        break;
      }
      if (i == MAX_TRIES / 2) {
        // Maybe some transient CPU load is causing issues here?
        try {
          Thread.sleep(3000);
        } catch (InterruptedException ignored) {
          System.out.println("Unexpected interrupt");
        }
      }
      // Clean up and try again.
      Runtime.getRuntime().gc();
      System.runFinalization();
    }
    System.out.println("Main Finished");
  }

  // Returns false on a failure that should be retried.
  boolean tryToRun(boolean lastChance) {
    final int startingGcNum = getGcNum();
    timeNotifications();  // warm up.
    final long referenceTime1 = timeNotifications();
    final long referenceTime2 = timeNotifications();
    final long referenceTime3 = timeNotifications();
    final long referenceTime = Math.min(referenceTime1, Math.min(referenceTime2, referenceTime3));

    // Allocate a GB+ of native memory without informing the GC.
    for (int i = 0; i < HOW_MANY_HUGE; ++i) {
      new BufferHolder();
    }

    if (startingGcNum != getGcNum()) {
      // Happens rarely, fail and retry.
      if (!lastChance) {
        return false;
      }
      System.out.println("Triggered early GC");
    }
    // One of the notifications should block for GC to catch up.
    long actualTime = timeNotifications();
    final long minBlockingTime = 2 * referenceTime + 2_000_000;

    if (startingGcNum == getGcNum()) {
      System.out.println("No gc completed");
    }
    if (actualTime > 500_000_000) {
      System.out.println("Notifications ran too slowly; excessive blocking? msec = "
          + (actualTime / 1_000_000));
    } else if (actualTime < minBlockingTime) {
      if (!lastChance) {
        // We sometimes see this, maybe because a GC is triggered by other means?
        // Try again before reporting.
        return false;
      }
      System.out.println("Notifications ran too quickly; no blocking GC? msec = "
          + (actualTime / 1_000_000) + " reference(msec) = " + (referenceTime / 1_000_000));
    }

    // Let finalizers run.
    try {
      Thread.sleep(3000);
    } catch (InterruptedException e) {
      System.out.println("Unexpected interrupt");
    }

    if (deallocated > allocated || deallocated < allocated - 5 /* slop for register references */) {
      System.out.println("Unexpected number of deallocated objects:");
      System.out.println("Allocated = " + allocated + " deallocated = " + deallocated);
    }
    System.out.println("Succeeded");
    return true;
  }

  private static native ByteBuffer getHugeNativeBuffer();
  private static native void deleteHugeNativeBuffer(ByteBuffer buf);
  private static native int getGcNum();
}
