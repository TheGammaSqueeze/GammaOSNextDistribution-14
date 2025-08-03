/*
 * Copyright (C) 2023 The Android Open Source Project
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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import sun.misc.Unsafe;

class Main {
    public static void main(String args[]) throws Exception {
        // Stress-test read-modify-write operations in adjacent memory locations.
        // This is intended to uncover bugs triggered by spurious CAS failures on
        // architectures where such spurious failures can happen. Bug: 218453177
        $noinline$testVarHandleBytes();
        $noinline$testVarHandleInts();
        $noinline$testVarHandleLongs();
        $noinline$testVarHandleReferences();
        $noinline$testUnsafeInts();
        $noinline$testUnsafeLongs();
        $noinline$testUnsafeReferences();

        // Stress-test read-modify-write operations on the same memory locations.
        // This is intended to uncover bugs with false-positive comparison in CAS.
        $noinline$testAtomicReference();
    }

    public static void $noinline$testVarHandleBytes() throws Exception {
        // Prepare `VarHandle` objects.
        VarHandle[] vhs = new VarHandle[] {
                MethodHandles.lookup().findVarHandle(FourBytes.class, "b1", byte.class),
                MethodHandles.lookup().findVarHandle(FourBytes.class, "b2", byte.class),
                MethodHandles.lookup().findVarHandle(FourBytes.class, "b3", byte.class),
                MethodHandles.lookup().findVarHandle(FourBytes.class, "b4", byte.class)
        };
        // Prepare threads.
        final FourBytes fourBytes = new FourBytes();
        final StopFlag stopFlag = new StopFlag();
        Thread[] threads = new Thread[4];
        for (int i = 0; i != 4; ++i) {
            final VarHandle vh = vhs[i];
            threads[i] = new Thread() {
                public void run() {
                    byte value = 0;
                    while (!stopFlag.stop) {
                        byte nextValue = (byte) (value + 1);
                        boolean success = vh.compareAndSet(fourBytes, value, nextValue);
                        assertTrue(success);
                        value = nextValue;
                    }
                }
            };
        }
        // Start threads.
        for (int i = 0; i != 4; ++i) {
            threads[i].start();
        }
        // Let the threads run for 5s.
        Thread.sleep(5000);
        // Stop threads.
        stopFlag.stop = true;
        for (int i = 0; i != 4; ++i) {
            threads[i].join();
        }
    }

    public static void $noinline$testVarHandleInts() throws Exception {
        // Prepare `VarHandle` objects.
        VarHandle[] vhs = new VarHandle[] {
                MethodHandles.lookup().findVarHandle(FourInts.class, "i1", int.class),
                MethodHandles.lookup().findVarHandle(FourInts.class, "i2", int.class),
                MethodHandles.lookup().findVarHandle(FourInts.class, "i3", int.class),
                MethodHandles.lookup().findVarHandle(FourInts.class, "i4", int.class)
        };
        // Prepare threads.
        final FourInts fourInts = new FourInts();
        final StopFlag stopFlag = new StopFlag();
        Thread[] threads = new Thread[4];
        for (int i = 0; i != 4; ++i) {
            final VarHandle vh = vhs[i];
            threads[i] = new Thread() {
                public void run() {
                    int value = 0;
                    while (!stopFlag.stop) {
                        int nextValue = value + 1;
                        boolean success = vh.compareAndSet(fourInts, value, nextValue);
                        assertTrue(success);
                        value = nextValue;
                    }
                }
            };
        }
        // Start threads.
        for (int i = 0; i != 4; ++i) {
            threads[i].start();
        }
        // Let the threads run for 5s.
        Thread.sleep(5000);
        // Stop threads.
        stopFlag.stop = true;
        for (int i = 0; i != 4; ++i) {
            threads[i].join();
        }
    }

    public static void $noinline$testVarHandleLongs() throws Exception {
        // Prepare `VarHandle` objects.
        VarHandle[] vhs = new VarHandle[] {
                MethodHandles.lookup().findVarHandle(FourLongs.class, "l1", long.class),
                MethodHandles.lookup().findVarHandle(FourLongs.class, "l2", long.class),
                MethodHandles.lookup().findVarHandle(FourLongs.class, "l3", long.class),
                MethodHandles.lookup().findVarHandle(FourLongs.class, "l4", long.class)
        };
        // Prepare threads.
        final FourLongs fourLongs = new FourLongs();
        final StopFlag stopFlag = new StopFlag();
        Thread[] threads = new Thread[4];
        for (int i = 0; i != 4; ++i) {
            final VarHandle vh = vhs[i];
            threads[i] = new Thread() {
                public void run() {
                    long value = 0;
                    while (!stopFlag.stop) {
                        long nextValue = value + 1L;
                        boolean success = vh.compareAndSet(fourLongs, value, nextValue);
                        assertTrue(success);
                        value = nextValue;
                    }
                }
            };
        }
        // Start threads.
        for (int i = 0; i != 4; ++i) {
            threads[i].start();
        }
        // Let the threads run for 5s.
        Thread.sleep(5000);
        // Stop threads.
        stopFlag.stop = true;
        for (int i = 0; i != 4; ++i) {
            threads[i].join();
        }
    }

    public static void $noinline$testVarHandleReferences() throws Exception {
        // Prepare `VarHandle` objects.
        VarHandle[] vhs = new VarHandle[] {
                MethodHandles.lookup().findVarHandle(FourReferences.class, "r1", Object.class),
                MethodHandles.lookup().findVarHandle(FourReferences.class, "r2", Object.class),
                MethodHandles.lookup().findVarHandle(FourReferences.class, "r3", Object.class),
                MethodHandles.lookup().findVarHandle(FourReferences.class, "r4", Object.class)
        };
        // Prepare threads.
        final FourReferences fourReferences = new FourReferences();
        Object[] values = new Object[] {
                null,
                new Object(),
                new Object(),
                new Object()
        };
        final StopFlag stopFlag = new StopFlag();
        Thread[] threads = new Thread[4];
        for (int i = 0; i != 4; ++i) {
            final VarHandle vh = vhs[i];
            threads[i] = new Thread() {
                public void run() {
                    int index = 0;
                    while (!stopFlag.stop) {
                        Object value = values[index];
                        index = (index + 1) & 3;
                        Object nextValue = values[index];
                        boolean success = vh.compareAndSet(fourReferences, value, nextValue);
                        assertTrue(success);
                    }
                }
            };
        }
        // Start threads.
        for (int i = 0; i != 4; ++i) {
            threads[i].start();
        }
        // Allocate memory to trigger some GCs
        for (int i = 0; i != 640 * 1024; ++i) {
            $noinline$allocateAtLeast1KiB();
        }
        // Stop threads.
        stopFlag.stop = true;
        for (int i = 0; i != 4; ++i) {
            threads[i].join();
        }
    }

    public static void $noinline$testUnsafeInts() throws Exception {
        // Prepare Unsafe offsets.
        final Unsafe unsafe = getUnsafe();
        long[] offsets = new long[] {
                unsafe.objectFieldOffset(FourInts.class.getField("i1")),
                unsafe.objectFieldOffset(FourInts.class.getField("i2")),
                unsafe.objectFieldOffset(FourInts.class.getField("i3")),
                unsafe.objectFieldOffset(FourInts.class.getField("i4"))
        };
        // Prepare threads.
        final FourInts fourInts = new FourInts();
        final StopFlag stopFlag = new StopFlag();
        Thread[] threads = new Thread[4];
        for (int i = 0; i != 4; ++i) {
            final long offset = offsets[i];
            threads[i] = new Thread() {
                public void run() {
                    int value = 0;
                    while (!stopFlag.stop) {
                        int nextValue = value + 1;
                        boolean success = unsafe.compareAndSwapInt(
                                fourInts, offset, value, nextValue);
                        assertTrue(success);
                        value = nextValue;
                    }
                }
            };
        }
        // Start threads.
        for (int i = 0; i != 4; ++i) {
            threads[i].start();
        }
        // Let the threads run for 5s.
        Thread.sleep(5000);
        // Stop threads.
        stopFlag.stop = true;
        for (int i = 0; i != 4; ++i) {
            threads[i].join();
        }
    }

    public static void $noinline$testUnsafeLongs() throws Exception {
        // Prepare Unsafe offsets.
        final Unsafe unsafe = getUnsafe();
        long[] offsets = new long[] {
                unsafe.objectFieldOffset(FourLongs.class.getField("l1")),
                unsafe.objectFieldOffset(FourLongs.class.getField("l2")),
                unsafe.objectFieldOffset(FourLongs.class.getField("l3")),
                unsafe.objectFieldOffset(FourLongs.class.getField("l4"))
        };
        // Prepare threads.
        final FourLongs fourLongs = new FourLongs();
        final StopFlag stopFlag = new StopFlag();
        Thread[] threads = new Thread[4];
        for (int i = 0; i != 4; ++i) {
            final long offset = offsets[i];
            threads[i] = new Thread() {
                public void run() {
                    long value = 0;
                    while (!stopFlag.stop) {
                        long nextValue = value + 1L;
                        boolean success = unsafe.compareAndSwapLong(
                                fourLongs, offset, value, nextValue);
                        assertTrue(success);
                        value = nextValue;
                    }
                }
            };
        }
        // Start threads.
        for (int i = 0; i != 4; ++i) {
            threads[i].start();
        }
        // Let the threads run for 5s.
        Thread.sleep(5000);
        // Stop threads.
        stopFlag.stop = true;
        for (int i = 0; i != 4; ++i) {
            threads[i].join();
        }
    }

    public static void $noinline$testUnsafeReferences() throws Exception {
        // Prepare Unsafe offsets.
        // D8 rewrites the bytecode with a workaround for CAS bug. To test the raw
        // `Unsafe.compareAndSwapObject()` call, we implement the call in smali
        // and wrap it in an indirect call.
        final UnsafeDispatch unsafeDispatch =
                (UnsafeDispatch) Class.forName("UnsafeWrapper").newInstance();
        final Unsafe unsafe = getUnsafe();
        long[] offsets = new long[] {
                unsafe.objectFieldOffset(FourReferences.class.getField("r1")),
                unsafe.objectFieldOffset(FourReferences.class.getField("r2")),
                unsafe.objectFieldOffset(FourReferences.class.getField("r3")),
                unsafe.objectFieldOffset(FourReferences.class.getField("r4"))
        };
        // Prepare threads.
        final FourReferences fourReferences = new FourReferences();
        Object[] values = new Object[] {
                null,
                new Object(),
                new Object(),
                new Object()
        };
        final StopFlag stopFlag = new StopFlag();
        Thread[] threads = new Thread[4];
        for (int i = 0; i != 4; ++i) {
            final long offset = offsets[i];
            threads[i] = new Thread() {
                public void run() {
                    int index = 0;
                    while (!stopFlag.stop) {
                        Object value = values[index];
                        index = (index + 1) & 3;
                        Object nextValue = values[index];
                        boolean success = unsafeDispatch.compareAndSwapObject(
                                unsafe, fourReferences, offset, value, nextValue);
                        assertTrue(success);
                    }
                }
            };
        }
        // Start threads.
        for (int i = 0; i != 4; ++i) {
            threads[i].start();
        }
        // Allocate memory to trigger some GCs
        for (int i = 0; i != 640 * 1024; ++i) {
            $noinline$allocateAtLeast1KiB();
        }
        // Stop threads.
        stopFlag.stop = true;
        for (int i = 0; i != 4; ++i) {
            threads[i].join();
        }
    }

    // Instead of using a `VarHandle` directly, this test uses `AtomicReference` which is
    // implemented using a `VarHandle`. This is because the normal `VarHandle` checks are
    // done without read barrier which makes them likely to fail and take the slow-path to
    // the runtime while the GC is marking (which is the case we're most interested in).
    // The `AtomicReference` uses a boot-image `VarHandle` which is optimized to avoid
    // those checks, making it more likely to hit bugs in the raw RMW operation.
    public static void $noinline$testAtomicReference() throws Exception {
        // Prepare `AtomicReference` object.
        // D8 rewrites the bytecode with a workaround for CAS bug. To test the raw
        // `AtomicReference.compareAndSet()` call, we implement the call in smali
        // and wrap it in an indirect call.
        final AtomicReferenceDispatch atomicReferenceDispatch =
                (AtomicReferenceDispatch) Class.forName("AtomicReferenceWrapper").newInstance();
        final AtomicReference aref = new AtomicReference(null);
        // Prepare threads.
        final Object[] objects = new Object[] {
                null,
                new Object(),
                new Object(),
                new Object()
        };
        final StopFlag stopFlag = new StopFlag();
        Thread[] threads = new Thread[4];
        for (int i = 0; i != 4; ++i) {
            if (i == 0) {
                threads[i] = new Thread() {
                    public void run() {
                        int index = 0;
                        Object value = objects[index];
                        while (!stopFlag.stop) {
                            index = (index + 1) & 3;
                            Object nextValue = objects[index];
                            boolean success = atomicReferenceDispatch.compareAndSet(
                                    aref, value, nextValue);
                            assertTrue(success);
                            value = nextValue;
                        }
                    }
                };
            } else {
                final Object value = objects[i];
                assertTrue(value != null);
                threads[i] = new Thread() {
                    public void run() {
                        // This thread is trying to overwrite a value with the same value.
                        // For a false-positive in CAS compare, it would actually change
                        // the value and cause the thread `threads[0]` to fail.
                        assertTrue(value != null);
                        while (!stopFlag.stop) {
                            // Do not check the return value.
                            atomicReferenceDispatch.compareAndSet(aref, value, value);
                        }
                    }
                };
            }
        };
        // Start threads.
        for (int i = 0; i != 4; ++i) {
            threads[i].start();
        }
        // Allocate memory to trigger some GCs
        for (int i = 0; i != 640 * 1024; ++i) {
            $noinline$allocateAtLeast1KiB();
        }
        // Stop threads.
        stopFlag.stop = true;
        for (int i = 0; i != 4; ++i) {
            threads[i].join();
        }
    }

    public static void assertTrue(boolean value) {
        if (!value) {
            throw new Error("Assertion failed!");
        }
    }

    public static Unsafe getUnsafe() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field f = unsafeClass.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (Unsafe) f.get(null);
    }

    public static void $noinline$allocateAtLeast1KiB() {
        // Give GC more work by allocating Object arrays.
        memory[allocationIndex] = new Object[1024 / 4];
        ++allocationIndex;
        if (allocationIndex == memory.length) {
            allocationIndex = 0;
        }
    }

    // We shall retain some allocated memory and release old allocations
    // so that the GC has something to do.
    public static Object[] memory = new Object[1024];
    public static int allocationIndex = 0;
}

class StopFlag {
    public volatile boolean stop = false;
}

class FourBytes {
    public byte b1 = (byte) 0;
    public byte b2 = (byte) 0;
    public byte b3 = (byte) 0;
    public byte b4 = (byte) 0;
}

class FourInts {
    public int i1 = 0;
    public int i2 = 0;
    public int i3 = 0;
    public int i4 = 0;
}

class FourLongs {
    public long l1 = 0L;
    public long l2 = 0L;
    public long l3 = 0L;
    public long l4 = 0L;
}

class FourReferences {
    public Object r1 = null;
    public Object r2 = null;
    public Object r3 = null;
    public Object r4 = null;
}

abstract class UnsafeDispatch {
    public abstract boolean compareAndSwapObject(
            Unsafe unsafe, Object obj, long offset, Object expected, Object new_value);
}

abstract class AtomicReferenceDispatch {
    public abstract boolean compareAndSet(AtomicReference aref, Object expected, Object new_value);
}
