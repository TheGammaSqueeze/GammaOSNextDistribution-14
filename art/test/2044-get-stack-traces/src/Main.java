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

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.ref.SoftReference;
import java.math.BigInteger;
import java.util.ArrayList;

/**
 * We construct a main thread and worker threads, each retrieving stack traces
 * from the other. Since there are multiple workers, we may get a large number
 * of simultaneous stack trace attempts.
 */
public class Main {
    static final int NUM_THREADS = 5;
    static Thread mainThread;
    static volatile boolean pleaseStop = false;

    private static void getTrace(Thread t) {
      StackTraceElement trace[] = t.getStackTrace();
      if (!pleaseStop && (trace.length < 1 || trace.length > 20)) {
        // If called from traceGetter, we were started by the main thread, and it was still
        // running after the trace, so the main thread should have at least one frame on
        // the stack. If called by main(), we waited for all the traceGetters to start,
        // and didn't yet allow them to stop, so the same should be true.
        System.out.println("Stack trace for " + t.getName() + " has size " + trace.length);
        for (StackTraceElement e : trace) {
          System.out.println(e.toString());
        }
      }
    }

    /**
     * Repeatedly get and minimally check stack trace of main thread.
     */
    static Runnable traceGetter = new Runnable() {
        public void run() {
          System.out.println("Starting helper");
          while (!pleaseStop) {
            getTrace(mainThread);
          }
        }
    };

    public static void main(String[] args) throws Exception {
        System.out.println("Starting");
        Thread[] t = new Thread[NUM_THREADS];
        mainThread = Thread.currentThread();
        for (int i = 0; i < NUM_THREADS; ++i) {
          t[i] = new Thread(traceGetter);
          t[i].start();
        }
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
            System.out.println("Unexpectedly interrupted");
        }
        for (int i = 0; i < NUM_THREADS; ++i) {
          getTrace(t[i]);
        }
        System.out.println("Finished worker stack traces");
        long now = System.currentTimeMillis();
        while (System.currentTimeMillis() - now < 2000) {
          try {
            Thread.sleep(1);
          } catch (InterruptedException e) {
            System.out.println("Unexpectedly interrupted");
          }
        }
        pleaseStop = true;
        System.out.println("Finished");
    }
}
