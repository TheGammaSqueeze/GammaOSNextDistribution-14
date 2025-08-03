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

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.TreeMap;

/**
 * Test that objects get finalized and their references cleared in the right order.
 *
 * We maintain a list of nominally MAX_LIVE_OBJS numbered finalizable objects.
 * We then alternately drop the last 50, and add 50 more. When we see an object finalized
 * or its reference cleared, we make sure that the preceding objects in its group of 50
 * have also had their references cleared. We also perform a number of other more
 * straightforward checks, such as ensuring that all references are eventually cleared,
 * and all objects are finalized.
 */
public class Main {
    // TODO(b/216481630) Enable CHECK_PHANTOM_REFS. This currently occasionally reports a few
    // PhantomReferences as not enqueued. If this report is correct, this needs to be tracked
    // down and fixed.
    static final boolean CHECK_PHANTOM_REFS = false;

    static final int MAX_LIVE_OBJS = 150;
    static final int DROP_OBJS = 50;  // Number of linked objects dropped in each batch.
    static final int MIN_LIVE_OBJS = MAX_LIVE_OBJS - DROP_OBJS;
    static final int TOTAL_OBJS = 200_000;  // Allocate this many finalizable objects in total.
    static final boolean REPORT_DROPS = false;
    static volatile boolean pleaseStop;

    AtomicInteger totalFinalized = new AtomicInteger(0);
    int maxDropped = 0;
    int liveObjects = 0;

    // Number of next finalizable object to be allocated.
    int nextAllocated = 0;

    // List of finalizable objects in descending order. We add to the front and drop
    // from the rear.
    FinalizableObject listHead;

    // A possibly incomplete list of FinalizableObject indices that were finalized, but
    // have yet to be checked for consistency with reference processing.
    ArrayBlockingQueue<Integer> finalized = new ArrayBlockingQueue<>(20_000);

    // Maps from object number to Reference; Cleared references are deleted when queues are
    // processed.
    TreeMap<Integer, MyWeakReference> weakRefs = new TreeMap<>();
    ConcurrentHashMap<Integer, MyPhantomReference> phantomRefs = new ConcurrentHashMap<>();

    class FinalizableObject {
        int n;
        FinalizableObject next;
        FinalizableObject(int num, FinalizableObject nextObj) {
            n = num;
            next = nextObj;
        }
        protected void finalize() {
            if (!inPhantomRefs(n)) {
                System.out.println("PhantomRef enqueued before finalizer ran");
            }
            totalFinalized.incrementAndGet();
            if (!finalized.offer(n) && REPORT_DROPS) {
                System.out.println("Dropped finalization of " + n);
            }
        }
    }
    ReferenceQueue<FinalizableObject> refQueue = new ReferenceQueue<>();
    class MyWeakReference extends WeakReference<FinalizableObject> {
        int n;
        MyWeakReference(FinalizableObject obj) {
            super(obj, refQueue);
            n = obj.n;
        }
    };
    class MyPhantomReference extends PhantomReference<FinalizableObject> {
        int n;
        MyPhantomReference(FinalizableObject obj) {
            super(obj, refQueue);
            n = obj.n;
        }
    }
    boolean inPhantomRefs(int n) {
        MyPhantomReference ref = phantomRefs.get(n);
        if (ref == null) {
            return false;
        }
        if (ref.n != n) {
            System.out.println("phantomRef retrieval failed");
        }
        return true;
    }

    void CheckOKToClearWeak(int num) {
        if (num > maxDropped) {
            System.out.println("WeakRef to live object " + num + " was cleared/enqueued.");
        }
        int batchEnd = (num / DROP_OBJS + 1) * DROP_OBJS;
        for (MyWeakReference wr : weakRefs.subMap(num + 1, batchEnd).values()) {
            if (wr.n <= num || wr.n / DROP_OBJS != num / DROP_OBJS) {
                throw new AssertionError("MyWeakReference logic error!");
            }
            // wr referent was dropped in same batch and precedes it in list.
            if (wr.get() != null) {
                // This violates the WeakReference spec, and can result in strong references
                // to objects that have been cleaned.
                System.out.println("WeakReference to " + wr.n
                    + " was erroneously cleared after " + num);
            }
        }
    }

    void CheckOKToClearPhantom(int num) {
        if (num > maxDropped) {
            System.out.println("PhantomRef to live object " + num + " was enqueued.");
        }
        MyWeakReference wr = weakRefs.get(num);
        if (wr != null && wr.get() != null) {
            System.out.println("PhantomRef cleared before WeakRef for " + num);
        }
    }

    void emptyAndCheckQueues() {
        // Check recently finalized objects for consistency with cleared references.
        while (true) {
            Integer num = finalized.poll();
            if (num == null) {
                break;
            }
            MyWeakReference wr = weakRefs.get(num);
            if (wr != null) {
                if (wr.n != num) {
                    System.out.println("Finalization logic error!");
                }
                if (wr.get() != null) {
                    System.out.println("Finalizing object with uncleared reference");
                }
            }
            CheckOKToClearWeak(num);
        }
        // Check recently enqueued references for consistency.
        while (true) {
            Reference<FinalizableObject> ref = (Reference<FinalizableObject>) refQueue.poll();
            if (ref == null) {
                break;
            }
            if (ref instanceof MyWeakReference) {
                MyWeakReference wr = (MyWeakReference) ref;
                if (wr.get() != null) {
                    System.out.println("WeakRef " + wr.n + " enqueued but not cleared");
                }
                CheckOKToClearWeak(wr.n);
                if (weakRefs.remove(Integer.valueOf(wr.n)) != ref) {
                    System.out.println("Missing WeakReference: " + wr.n);
                }
            } else if (ref instanceof MyPhantomReference) {
                MyPhantomReference pr = (MyPhantomReference) ref;
                CheckOKToClearPhantom(pr.n);
                if (phantomRefs.remove(Integer.valueOf(pr.n)) != ref) {
                    System.out.println("Missing PhantomReference: " + pr.n);
                }
            } else {
                System.out.println("Found unrecognized reference in queue");
            }
        }
    }


    /**
     * Add n objects to the head of the list. These will be assigned the next n consecutive
     * numbers after the current head of the list.
     */
    void addObjects(int n) {
        for (int i = 0; i < n; ++i) {
            int me = nextAllocated++;
            listHead = new FinalizableObject(me, listHead);
            weakRefs.put(me, new MyWeakReference(listHead));
            phantomRefs.put(me, new MyPhantomReference(listHead));
        }
        liveObjects += n;
    }

    /**
     * Drop n finalizable objects from the tail of the list. These are the lowest-numbered objects
     * in the list.
     */
    void dropObjects(int n) {
        FinalizableObject list = listHead;
        FinalizableObject last = null;
        if (n > liveObjects) {
            System.out.println("Removing too many elements");
        }
        if (liveObjects == n) {
            maxDropped = list.n;
            listHead = null;
        } else {
            final int skip = liveObjects - n;
            for (int i = 0; i < skip; ++i) {
                last = list;
                list = list.next;
            }
            int expected = nextAllocated - skip - 1;
            if (list.n != expected) {
                System.out.println("dropObjects found " + list.n + " but expected " + expected);
            }
            maxDropped = expected;
            last.next = null;
        }
        liveObjects -= n;
    }

    void testLoop() {
        System.out.println("Starting");
        addObjects(MIN_LIVE_OBJS);
        final int ITERS = (TOTAL_OBJS - MIN_LIVE_OBJS) / DROP_OBJS;
        for (int i = 0; i < ITERS; ++i) {
            addObjects(DROP_OBJS);
            if (liveObjects != MAX_LIVE_OBJS) {
                System.out.println("Unexpected live object count");
            }
            dropObjects(DROP_OBJS);
            if (i % 100 == 0) {
              // Make sure we don't fall too far behind, otherwise we may run out of memory.
              System.runFinalization();
            }
            emptyAndCheckQueues();
        }
        dropObjects(MIN_LIVE_OBJS);
        if (liveObjects != 0 || listHead != null) {
            System.out.println("Unexpected live objecs at end");
        }
        if (maxDropped != TOTAL_OBJS - 1) {
            System.out.println("Unexpected dropped object count: " + maxDropped);
        }
        for (int i = 0; i < 2; ++i) {
            Runtime.getRuntime().gc();
            System.runFinalization();
            emptyAndCheckQueues();
        }
        if (!weakRefs.isEmpty()) {
            System.out.println("Weak Reference map nonempty size = " + weakRefs.size());
        }
        if (CHECK_PHANTOM_REFS && !phantomRefs.isEmpty()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                System.out.println("Unexpected interrupt");
            }
            if (!phantomRefs.isEmpty()) {
                System.out.println("Phantom Reference map nonempty size = " + phantomRefs.size());
                System.out.print("First elements:");
                int i = 0;
                for (MyPhantomReference pr : phantomRefs.values()) {
                    System.out.print(" " + pr.n);
                    if (++i > 10) {
                        break;
                    }
                }
                System.out.println("");
            }
        }
        if (totalFinalized.get() != TOTAL_OBJS) {
            System.out.println("Finalized only " + totalFinalized + " objects");
        }
    }

    static Runnable causeGCs = new Runnable() {
        public void run() {
            // Allocate a lot.
            BigInteger counter = BigInteger.ZERO;
            while (!pleaseStop) {
                counter = counter.add(BigInteger.TEN);
            }
            // Look at counter to reduce chance of optimizing out the allocation.
            if (counter.longValue() % 10 != 0) {
                 System.out.println("Bad causeGCs counter value: " + counter);
            }
        }
    };

    public static void main(String[] args) throws Exception {
        Main theTest = new Main();
        Thread gcThread = new Thread(causeGCs);
        gcThread.setDaemon(true);  // Terminate if main thread dies.
        gcThread.start();
        theTest.testLoop();
        pleaseStop = true;
        gcThread.join();
        System.out.println("Finished");
    }
}
