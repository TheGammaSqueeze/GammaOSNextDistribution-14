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
 * Basic test of WeakReferences with large amounts of memory that's only reachable through
 * finalizers. Also makes sure that finalizer-reachable data is not collected.
 * Can easily be modified to time Reference.get() blocking.
 */
public class Main {
    static final boolean PRINT_TIMES = false;  // true will cause benchmark failure.
    // Data structures repeatedly allocated in background to trigger GC.
    // Size of finalizer reachable trees.
    static final int TREE_HEIGHT = 15;  // Trees contain 2^TREE_HEIGHT -1 allocated objects.
    // Number of finalizable tree-owning objects that exist at one point.
    static final int N_RESURRECTING_OBJECTS = 10;
    // Number of short-lived, not finalizer-reachable, objects allocated between trees.
    static final int N_PLAIN_OBJECTS = 20_000;
    // Number of SoftReferences to CBTs we allocate.
    static final int N_SOFTREFS = 10;

    static final boolean BACKGROUND_GC_THREAD = true;
    static final int NBATCHES = 10;
    static final int NREFS = PRINT_TIMES ? 1_000_000 : 300_000;  // Multiple of NBATCHES.
    static final int REFS_PER_BATCH = NREFS / NBATCHES;

    static volatile boolean pleaseStop = false;

    // Large array of WeakReferences filled and accessed by tests below.
    ArrayList<WeakReference<Integer>> weakRefs = new ArrayList<>(NREFS);

    /**
     * Complete binary tree data structure. make(n) takes O(2^n) space.
     */
    static class CBT {
        CBT left;
        CBT right;
        CBT(CBT l, CBT r) {
            left = l;
            right = r;
        }
        static CBT make(int n) {
            if (n == 0) {
                return null;
            }
            return new CBT(make(n - 1), make(n - 1));
        }
        /**
         * Check that path described by bit-vector path has the correct length.
         */
        void check(int n, int path) {
            CBT current = this;
            for (int i = 0; i < n; i++, path = path >>> 1) {
                // Unexpectedly short paths result in NPE.
                if ((path & 1) == 0) {
                    current = current.left;
                } else {
                    current = current.right;
                }
            }
            if (current != null) {
                System.out.println("Complete binary tree path too long");
            }
        }
    }


    /**
     * A finalizable object that refers to O(2^TREE_HEIGHT) otherwise unreachable memory.
     * When finalized, it creates a new identical object, making sure that one always stays
     * around.
     */
    static class ResurrectingObject {
        CBT stuff;
        ResurrectingObject() {
            stuff = CBT.make(TREE_HEIGHT);
        }
        static ResurrectingObject a[] = new ResurrectingObject[2];
        static int i = 0;
        static synchronized void allocOne() {
            a[(++i) % 2] = new ResurrectingObject();
            // Check the previous one to make it hard to optimize anything out.
            if (i > 1) {
                a[(i + 1) % 2].stuff.check(TREE_HEIGHT, i /* weirdly interpreted as path */);
            }
        }
        protected void finalize() {
            stuff.check(TREE_HEIGHT, 42 /* Some path descriptor */);
            // Allocate a new one to replace this one.
            allocOne();
        }
    }

    void fillWeakRefs() {
        for (int i = 0; i < NREFS; ++i) {
             weakRefs.add(null);
        }
    }

    /*
     * Return maximum observed time in nanos to dereference a WeakReference to an unreachable
     * object. weakRefs is presumed to be pre-filled to have the correct size.
     */
    long timeUnreachableInner() {
        long maxNanos = 0;
        // Fill weakRefs with WeakReferences to unreachable integers, a batch at a time.
        // Then time and test .get() calls on carefully sampled array entries, some of which
        // will have been cleared.
        for (int i = 0; i < NBATCHES; ++i) {
            for (int j = 0; j < REFS_PER_BATCH; ++j) {
                weakRefs.set(i * REFS_PER_BATCH + j,
                        new WeakReference(new Integer(i * REFS_PER_BATCH + j)));
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                System.out.println("Unexpected exception");
            }
            // Iterate over the filled-in section of weakRefs, but look only at a subset of the
            // elements, making sure the subsets for different top-level iterations are disjoint.
            // Otherwise the get() calls here will extend the lifetimes of the referents, and we
            // may never see any cleared WeakReferences.
            for (int j = (i + 1) * REFS_PER_BATCH - i - 1; j >= 0; j -= NBATCHES) {
                WeakReference<Integer> wr = weakRefs.get(j);
                if (wr != null) {
                    long startNanos = System.nanoTime();
                    Integer referent = wr.get();
                    long totalNanos = System.nanoTime() - startNanos;
                    if (referent == null) {
                        // Optimization to reduce max space use and scanning time.
                        weakRefs.set(j, null);
                    }
                    maxNanos = Math.max(maxNanos, totalNanos);
                    if (referent != null && referent.intValue() != j) {
                        System.out.println("Unexpected referent; expected " + j + " got "
                                + referent.intValue());
                    }
                }
            }
        }
        return maxNanos;
    }

    /*
     * Wrapper for the above that also checks that references were reclaimed.
     * We do this separately to make sure any stack references from the core of the
     * test are gone. Empirically, we otherwise sometimes see the zeroth WeakReference
     * not reclaimed.
     */
    long timeUnreachable() {
        long maxNanos = timeUnreachableInner();
        Runtime.getRuntime().gc();
        System.runFinalization();  // Presumed to wait for reference clearing.
        for (int i = 0; i < NREFS; ++i) {
            if (weakRefs.get(i) != null && weakRefs.get(i).get() != null) {
                System.out.println("WeakReference to " + i + " wasn't cleared");
            }
        }
        return maxNanos;
    }

    /**
     * Return maximum observed time in nanos to dereference a WeakReference to a reachable
     * object. Overwrites weakRefs, which is presumed to have NREFS entries already.
    */
    long timeReachable() {
        long maxNanos = 0;
        // Similar to the above, but we use WeakReferences to otherwise reachable objects,
        // which should thus not get cleared.
        Integer[] strongRefs = new Integer[NREFS];
        for (int i = 0; i < NBATCHES; ++i) {
            for (int j = i * REFS_PER_BATCH; j < (i + 1) * REFS_PER_BATCH; ++j) {
                Integer newObj = new Integer(j);
                strongRefs[j] = newObj;
                weakRefs.set(j, new WeakReference(newObj));
            }
            for (int j = (i + 1) * REFS_PER_BATCH - 1; j >= 0; --j) {
                WeakReference<Integer> wr = weakRefs.get(j);
                long startNanos = System.nanoTime();
                Integer referent = wr.get();
                long totalNanos = System.nanoTime() - startNanos;
                maxNanos = Math.max(maxNanos, totalNanos);
                if (referent == null) {
                    System.out.println("Unexpectedly cleared referent at " + j);
                } else if (referent.intValue() != j) {
                    System.out.println("Unexpected reachable referent; expected " + j + " got "
                            + referent.intValue());
                }
            }
        }
        Reference.reachabilityFence(strongRefs);
        return maxNanos;
    }

    void runTest() {
        System.out.println("Starting");
        fillWeakRefs();
        long unreachableNanos = timeUnreachable();
        if (PRINT_TIMES) {
            System.out.println("Finished timeUnrechable()");
        }
        long reachableNanos = timeReachable();
        String unreachableMillis =
                String. format("%,.3f", ((double) unreachableNanos) / 1_000_000);
        String reachableMillis =
                String. format("%,.3f", ((double) reachableNanos) / 1_000_000);
        if (PRINT_TIMES) {
            System.out.println(
                    "Max time for WeakReference.get (unreachable): " + unreachableMillis);
            System.out.println(
                    "Max time for WeakReference.get (reachable): " + reachableMillis);
        }
        // Only report extremely egregious pauses to avoid spurious failures.
        if (unreachableNanos > 10_000_000_000L) {
            System.out.println("WeakReference.get (unreachable) time unreasonably long");
        }
        if (reachableNanos > 10_000_000_000L) {
            System.out.println("WeakReference.get (reachable) time unreasonably long");
        }
    }

    /**
     * Allocate and GC a lot, while keeping significant amounts of finalizer and
     * SoftReference-reachable memory around.
     */
    static Runnable allocFinalizable = new Runnable() {
        public void run() {
            // Allocate and drop some finalizable objects that take a long time
            // to mark. Designed to be hard to optimize away. Each of these objects will
            // build a new one in its finalizer before really going away.
            ArrayList<SoftReference<CBT>> softRefs = new ArrayList<>(N_SOFTREFS);
            for (int i = 0; i < N_SOFTREFS; ++i) {
                // These should not normally get reclaimed, since we shouldn't run out of
                // memory. They do increase tracing time.
                softRefs.add(new SoftReference(CBT.make(TREE_HEIGHT)));
            }
            for (int i = 0; i < N_RESURRECTING_OBJECTS; ++i) {
                ResurrectingObject.allocOne();
            }
            BigInteger counter = BigInteger.ZERO;
            for (int i = 1; !pleaseStop; ++i) {
                // Allocate a lot of short-lived objects, using BigIntegers to minimize the chance
                // of the allocation getting optimized out. This makes things slightly more
                // realistic, since not all objects will be finalizer reachable.
                for (int j = 0; j < N_PLAIN_OBJECTS / 2; ++j) {
                    counter = counter.add(BigInteger.TEN);
                }
                // Look at counter to reduce chance of optimizing out the allocation.
                if (counter.longValue() % 10 != 0) {
                    System.out.println("Bad allocFinalizable counter value: " + counter);
                }
                // Explicitly collect here, mostly to prevent heap growth. Otherwise we get
                // ahead of the GC and eventually block on it.
                Runtime.getRuntime().gc();
                if (PRINT_TIMES && i % 100 == 0) {
                    System.out.println("Collected " + i + " times");
                }
            }
            // To be safe, access softRefs.
            final CBT sample = softRefs.get(N_SOFTREFS / 2).get();
            if (sample != null) {
              sample.check(TREE_HEIGHT, 47 /* some path descriptor */);
            }
        }
    };

    public static void main(String[] args) throws Exception {
        Main theTest = new Main();
        Thread allocThread = null;
        if (BACKGROUND_GC_THREAD) {
            allocThread = new Thread(allocFinalizable);
            allocThread.setDaemon(true);  // Terminate if main thread dies.
            allocThread.start();
        }
        theTest.runTest();
        if (BACKGROUND_GC_THREAD) {
            pleaseStop = true;
            allocThread.join();
        }
        System.out.println("Finished");
    }
}
