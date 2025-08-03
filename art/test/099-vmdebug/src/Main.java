/*
 * Copyright (C) 2014 The Android Open Source Project
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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;

public class Main {
    private static final String TEMP_FILE_NAME_PREFIX = "test";
    private static final String TEMP_FILE_NAME_SUFFIX = ".trace";

    public static void main(String[] args) throws Exception {
        String name = System.getProperty("java.vm.name");
        if (!"Dalvik".equals(name)) {
            System.out.println("This test is not supported on " + name);
            return;
        }
        testMethodTracing();
        testCountInstances();
        testRuntimeStat();
        testRuntimeStats();
        testGetAllocCount();
        testGetVmFeatureList();
        testDebuggerDetails();
    }

    private static File createTempFile() throws Exception {
        try {
            return  File.createTempFile(TEMP_FILE_NAME_PREFIX, TEMP_FILE_NAME_SUFFIX);
        } catch (IOException e) {
            System.setProperty("java.io.tmpdir", "/data/local/tmp");
            try {
                return File.createTempFile(TEMP_FILE_NAME_PREFIX, TEMP_FILE_NAME_SUFFIX);
            } catch (IOException e2) {
                System.setProperty("java.io.tmpdir", "/sdcard");
                return File.createTempFile(TEMP_FILE_NAME_PREFIX, TEMP_FILE_NAME_SUFFIX);
            }
        }
    }

    private static void testMethodTracing() throws Exception {
        File tempFile = null;
        try {
            tempFile = createTempFile();
            testMethodTracingToFile(tempFile);
        } finally {
            if (tempFile != null) {
                tempFile.delete();
            }
        }
    }

    private static void testMethodTracingToFile(File tempFile) throws Exception {
        String tempFileName = tempFile.getPath();

        if (VMDebug.getMethodTracingMode() != 0) {
            VMDebug.stopMethodTracing();
        }

        System.out.println("Confirm enable/disable");
        System.out.println("status=" + VMDebug.getMethodTracingMode());
        VMDebug.startMethodTracing(tempFileName, 0, 0, false, 0);
        System.out.println("status=" + VMDebug.getMethodTracingMode());
        VMDebug.stopMethodTracing();
        System.out.println("status=" + VMDebug.getMethodTracingMode());
        if (tempFile.length() == 0) {
            System.out.println("ERROR: tracing output file is empty");
        }

        System.out.println("Confirm sampling");
        VMDebug.startMethodTracing(tempFileName, 0, 0, true, 1000);
        System.out.println("status=" + VMDebug.getMethodTracingMode());
        VMDebug.stopMethodTracing();
        System.out.println("status=" + VMDebug.getMethodTracingMode());
        if (tempFile.length() == 0) {
            System.out.println("ERROR: sample tracing output file is empty");
        }

        System.out.println("Test starting when already started");
        VMDebug.startMethodTracing(tempFileName, 0, 0, false, 0);
        System.out.println("status=" + VMDebug.getMethodTracingMode());
        VMDebug.startMethodTracing(tempFileName, 0, 0, false, 0);
        System.out.println("status=" + VMDebug.getMethodTracingMode());

        System.out.println("Test stopping when already stopped");
        VMDebug.stopMethodTracing();
        System.out.println("status=" + VMDebug.getMethodTracingMode());
        VMDebug.stopMethodTracing();
        System.out.println("status=" + VMDebug.getMethodTracingMode());

        System.out.println("Test tracing with empty filename");
        try {
            VMDebug.startMethodTracing("", 0, 0, false, 0);
            System.out.println("Should have thrown an exception");
        } catch (Exception e) {
            System.out.println("Got expected exception");
        }

        System.out.println("Test tracing with bogus (< 1024 && != 0) filesize");
        try {
            VMDebug.startMethodTracing(tempFileName, 1000, 0, false, 0);
            System.out.println("Should have thrown an exception");
        } catch (Exception e) {
            System.out.println("Got expected exception");
        }

        try {
            VMDebug.startMethodTracingDdms(1000, 0, false, 0);
            System.out.println("Should have thrown an exception");
        } catch (Exception e) {
            System.out.println("Got expected exception");
        }

        System.out.println("Test sampling with bogus (<= 0) interval");
        try {
            VMDebug.startMethodTracing(tempFileName, 0, 0, true, 0);
            System.out.println("Should have thrown an exception");
        } catch (Exception e) {
            System.out.println("Got expected exception");
        }
        try {
            VMDebug.startMethodTracingDdms(0, 0, true, 0);
            System.out.println("Should have thrown an exception");
        } catch (Exception e) {
            System.out.println("Got expected exception");
        }

        tempFile.delete();
    }

    private static void checkNumber(String s) throws Exception {
        if (s == null) {
            System.out.println("Got null string");
            return;
        }
        long n = Long.parseLong(s);
        if (n < 0) {
            System.out.println("Got negative number " + n);
        }
    }

    private static void checkBiggerThanZero(int i) throws Exception {
        if (i <= 0) {
            System.out.println("Got zero or smaller  " + i);
        }
    }

    private static void checkZero(int i) throws Exception {
        if (i != 0) {
            System.out.println("Got non-zero result after reset " + i);
        }
    }

    private static void checkHistogram(String s) throws Exception {
        if (s == null || s.length() == 0) {
            System.out.println("Got null or empty string");
            return;
        }
        String[] buckets = s.split(",");
        long last_key = 0;
        for (int i = 0; i < buckets.length; ++i) {
            String bucket = buckets[i];
            if (bucket.length() == 0) {
                System.out.println("Got empty bucket");
                continue;
            }
            String[] kv = bucket.split(":");
            if (kv.length != 2 || kv[0].length() == 0 || kv[1].length() == 0) {
                System.out.println("Got bad bucket " + bucket);
                continue;
            }
            long key = Long.parseLong(kv[0]);
            long value = Long.parseLong(kv[1]);
            if (key < 0 || value < 0) {
                System.out.println("Got negative key or value " + bucket);
                continue;
            }
            if (key < last_key) {
                System.out.println("Got decreasing key " + bucket);
                continue;
            }
            last_key = key;
        }
    }

    private static void testRuntimeStat() throws Exception {
        // Invoke at least one GC and wait for 20 seconds or so so we get at
        // least one bucket in the histograms.
        for (int i = 0; i < 20; ++i) {
          Runtime.getRuntime().gc();
          Thread.sleep(1000L);
        }
        String gc_count = VMDebug.getRuntimeStat("art.gc.gc-count");
        String gc_time = VMDebug.getRuntimeStat("art.gc.gc-time");
        String bytes_allocated = VMDebug.getRuntimeStat("art.gc.bytes-allocated");
        String bytes_freed = VMDebug.getRuntimeStat("art.gc.bytes-freed");
        String blocking_gc_count = VMDebug.getRuntimeStat("art.gc.blocking-gc-count");
        String blocking_gc_time = VMDebug.getRuntimeStat("art.gc.blocking-gc-time");
        String gc_count_rate_histogram = VMDebug.getRuntimeStat("art.gc.gc-count-rate-histogram");
        String blocking_gc_count_rate_histogram =
            VMDebug.getRuntimeStat("art.gc.blocking-gc-count-rate-histogram");
        checkNumber(gc_count);
        checkNumber(gc_time);
        checkNumber(bytes_allocated);
        checkNumber(bytes_freed);
        checkNumber(blocking_gc_count);
        checkNumber(blocking_gc_time);
        checkHistogram(gc_count_rate_histogram);
        checkHistogram(blocking_gc_count_rate_histogram);
    }

    private static void testRuntimeStats() throws Exception {
        // Invoke at least one GC and wait for 20 seconds or so so we get at
        // least one bucket in the histograms.
        for (int i = 0; i < 20; ++i) {
          Runtime.getRuntime().gc();
          Thread.sleep(1000L);
        }
        Map<String, String> map = VMDebug.getRuntimeStats();
        String gc_count = map.get("art.gc.gc-count");
        String gc_time = map.get("art.gc.gc-time");
        String bytes_allocated = map.get("art.gc.bytes-allocated");
        String bytes_freed = map.get("art.gc.bytes-freed");
        String blocking_gc_count = map.get("art.gc.blocking-gc-count");
        String blocking_gc_time = map.get("art.gc.blocking-gc-time");
        String gc_count_rate_histogram = map.get("art.gc.gc-count-rate-histogram");
        String blocking_gc_count_rate_histogram =
            map.get("art.gc.blocking-gc-count-rate-histogram");
        checkNumber(gc_count);
        checkNumber(gc_time);
        checkNumber(bytes_allocated);
        checkNumber(bytes_freed);
        checkNumber(blocking_gc_count);
        checkNumber(blocking_gc_time);
        checkHistogram(gc_count_rate_histogram);
        checkHistogram(blocking_gc_count_rate_histogram);
    }

    /* constants for getAllocCount */
    private static final int KIND_ALLOCATED_OBJECTS     = 1<<0;
    private static final int KIND_ALLOCATED_BYTES       = 1<<1;
    private static final int KIND_FREED_OBJECTS         = 1<<2;
    private static final int KIND_FREED_BYTES           = 1<<3;
    private static final int RESET_ALL       = 0xffffffff;

    private static void testGetAllocCount() throws Exception {
        VMDebug.startAllocCounting();

        ClassA a1 = new ClassA();
        Object obj1 = new Object();
        Runtime.getRuntime().gc();

        int alloc_objects = VMDebug.getAllocCount(KIND_ALLOCATED_OBJECTS);
        int alloc_bytes = VMDebug.getAllocCount(KIND_ALLOCATED_BYTES);
        int freed_objects = VMDebug.getAllocCount(KIND_FREED_OBJECTS);
        int freed_bytes = VMDebug.getAllocCount(KIND_FREED_BYTES);
        checkBiggerThanZero(alloc_objects);
        checkBiggerThanZero(alloc_bytes);
        checkBiggerThanZero(freed_objects);
        checkBiggerThanZero(freed_bytes);

        VMDebug.stopAllocCounting();
        VMDebug.resetAllocCount(RESET_ALL);
        checkZero(VMDebug.getAllocCount(KIND_ALLOCATED_OBJECTS));
        checkZero(VMDebug.getAllocCount(KIND_ALLOCATED_BYTES));
        checkZero(VMDebug.getAllocCount(KIND_FREED_OBJECTS));
        checkZero(VMDebug.getAllocCount(KIND_FREED_BYTES));

        // Even if we create new classes the count should remain 0.
        ClassA a2 = new ClassA();
        Object obj2 = new Object();

        checkZero(VMDebug.getAllocCount(KIND_ALLOCATED_OBJECTS));
    }

    private static void testGetVmFeatureList() throws Exception {
        String[] feature_list = VMDebug.getVmFeatureList();
        if (feature_list.length == 0) {
            System.out.println("Got empty feature list");
        }
    }

    private static void testDebuggerDetails() throws Exception {
        boolean debugger_connected = VMDebug.isDebuggerConnected();
        boolean debugging_enabled = VMDebug.isDebuggingEnabled();
        long last_activity = VMDebug.lastDebuggerActivity();
        if (debugger_connected && last_activity < 0) {
            System.out.println("Last debugging activity expected but not found");
        }
        if (!debugger_connected && last_activity != -1) {
            System.out.println("Found unexpected last activity");
        }
        if (VMDebug.threadCpuTimeNanos() <= 0) {
            System.out.println("Could not get CPU thread time");
        }
        VMDebug.dumpHprofDataDdms();
        VMDebug.dumpReferenceTables();
    }

    static class ClassA { }
    static class ClassB { }
    static class ClassC extends ClassA { }

    private static void testCountInstances() throws Exception {
        ArrayList<Object> l = new ArrayList<Object>();
        l.add(new ClassA());
        l.add(new ClassB());
        l.add(new ClassA());
        l.add(new ClassC());
        Runtime.getRuntime().gc();
        System.out.println("Instances of ClassA " +
                VMDebug.countInstancesofClass(ClassA.class, false));
        System.out.println("Instances of ClassB " +
                VMDebug.countInstancesofClass(ClassB.class, false));
        System.out.println("Instances of null " + VMDebug.countInstancesofClass(null, false));
        System.out.println("Instances of ClassA assignable " +
                VMDebug.countInstancesofClass(ClassA.class, true));
        Class<?>[] classes = new Class<?>[] {ClassA.class, ClassB.class, null};
        long[] counts = VMDebug.countInstancesofClasses(classes, false);
        System.out.println("Array counts " + Arrays.toString(counts));
        counts = VMDebug.countInstancesofClasses(classes, true);
        System.out.println("Array counts assignable " + Arrays.toString(counts));
        int class_count = VMDebug.getLoadedClassCount();
        checkBiggerThanZero(class_count);
    }

    static class ClassD {
        public int mask;

        public ClassD(int mask) {
            this.mask = mask;
        }
    }

    static class ClassE extends ClassD {
        public ClassE(int mask) {
            super(mask);
        }
    }

    private static class VMDebug {
        private static final Method startMethodTracingMethod;
        private static final Method startMethodTracingDdmsMethod;
        private static final Method stopMethodTracingMethod;
        private static final Method getMethodTracingModeMethod;
        private static final Method getRuntimeStatMethod;
        private static final Method getRuntimeStatsMethod;
        private static final Method countInstancesOfClassMethod;
        private static final Method countInstancesOfClassesMethod;
        private static final Method getAllocCountMethod;
        private static final Method startAllocCountingMethod;
        private static final Method stopAllocCountingMethod;
        private static final Method setAllocTrackerStackDepthMethod;
        private static final Method resetAllocCountMethod;
        private static final Method getLoadedClassCountMethod;
        private static final Method getVmFeatureListMethod;
        private static final Method isDebuggerConnectedMethod;
        private static final Method isDebuggingEnabledMethod;
        private static final Method lastDebuggerActivityMethod;
        private static final Method threadCpuTimeNanosMethod;
        private static final Method dumpHprofDataDdmsMethod;
        private static final Method dumpReferenceTablesMethod;
        static {
            try {
                Class<?> c = Class.forName("dalvik.system.VMDebug");
                startMethodTracingMethod = c.getDeclaredMethod("startMethodTracing", String.class,
                        Integer.TYPE, Integer.TYPE, Boolean.TYPE, Integer.TYPE);
                startMethodTracingDdmsMethod = c.getDeclaredMethod("startMethodTracingDdms",
                        Integer.TYPE, Integer.TYPE, Boolean.TYPE, Integer.TYPE);
                stopMethodTracingMethod = c.getDeclaredMethod("stopMethodTracing");
                getMethodTracingModeMethod = c.getDeclaredMethod("getMethodTracingMode");
                getRuntimeStatMethod = c.getDeclaredMethod("getRuntimeStat", String.class);
                getRuntimeStatsMethod = c.getDeclaredMethod("getRuntimeStats");
                countInstancesOfClassMethod = c.getDeclaredMethod("countInstancesOfClass",
                        Class.class, Boolean.TYPE);
                countInstancesOfClassesMethod = c.getDeclaredMethod("countInstancesOfClasses",
                        Class[].class, Boolean.TYPE);
                getAllocCountMethod = c.getDeclaredMethod("getAllocCount",
                        Integer.TYPE);
                startAllocCountingMethod = c.getDeclaredMethod("startAllocCounting");
                stopAllocCountingMethod = c.getDeclaredMethod("stopAllocCounting");
                setAllocTrackerStackDepthMethod = c.getDeclaredMethod("setAllocTrackerStackDepth",
                        Integer.TYPE);
                resetAllocCountMethod = c.getDeclaredMethod("resetAllocCount",
                        Integer.TYPE);
                getLoadedClassCountMethod = c.getDeclaredMethod("getLoadedClassCount");
                getVmFeatureListMethod = c.getDeclaredMethod("getVmFeatureList");
                isDebuggerConnectedMethod = c.getDeclaredMethod("isDebuggerConnected");
                isDebuggingEnabledMethod = c.getDeclaredMethod("isDebuggingEnabled");
                lastDebuggerActivityMethod = c.getDeclaredMethod("lastDebuggerActivity");
                threadCpuTimeNanosMethod = c.getDeclaredMethod("threadCpuTimeNanos");
                dumpHprofDataDdmsMethod = c.getDeclaredMethod("dumpHprofDataDdms");
                dumpReferenceTablesMethod = c.getDeclaredMethod("dumpReferenceTables");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public static void startMethodTracing(String filename, int bufferSize, int flags,
                boolean samplingEnabled, int intervalUs) throws Exception {
            startMethodTracingMethod.invoke(null, filename, bufferSize, flags, samplingEnabled,
                    intervalUs);
        }
        public static void startMethodTracingDdms(int bufferSize, int flags,
                boolean samplingEnabled, int intervalUs) throws Exception {
            startMethodTracingDdmsMethod.invoke(null, bufferSize, flags, samplingEnabled,
                    intervalUs);
        }
        public static void stopMethodTracing() throws Exception {
            stopMethodTracingMethod.invoke(null);
        }
        public static int getMethodTracingMode() throws Exception {
            return (int) getMethodTracingModeMethod.invoke(null);
        }
        public static String getRuntimeStat(String statName) throws Exception {
            return (String) getRuntimeStatMethod.invoke(null, statName);
        }
        public static Map<String, String> getRuntimeStats() throws Exception {
            return (Map<String, String>) getRuntimeStatsMethod.invoke(null);
        }
        public static long countInstancesofClass(Class<?> c, boolean assignable) throws Exception {
            return (long) countInstancesOfClassMethod.invoke(null, new Object[]{c, assignable});
        }
        public static long[] countInstancesofClasses(Class<?>[] classes, boolean assignable)
                throws Exception {
            return (long[]) countInstancesOfClassesMethod.invoke(
                    null, new Object[]{classes, assignable});
        }
        public static int getAllocCount(Integer kind) throws Exception {
            return (int) getAllocCountMethod.invoke(null, kind);
        }
        public static void startAllocCounting() throws Exception {
            startAllocCountingMethod.invoke(null);
        }
        public static void stopAllocCounting() throws Exception {
            stopAllocCountingMethod.invoke(null);
        }
        public static void setAllocTrackerStackDepth(Integer stackDepth) throws Exception {
            setAllocTrackerStackDepthMethod.invoke(null, stackDepth);
        }
        public static void resetAllocCount(Integer kind) throws Exception {
            resetAllocCountMethod.invoke(null, kind);
        }
        public static int getLoadedClassCount() throws Exception {
            return (int) getLoadedClassCountMethod.invoke(null);
        }
        public static String[] getVmFeatureList() throws Exception {
            return (String[]) getVmFeatureListMethod.invoke(null);
        }
        public static boolean isDebuggerConnected() throws Exception {
            return (boolean) isDebuggerConnectedMethod.invoke(null);
        }
        public static boolean isDebuggingEnabled() throws Exception {
            return (boolean) isDebuggingEnabledMethod.invoke(null);
        }
        public static long lastDebuggerActivity() throws Exception {
            return (long) lastDebuggerActivityMethod.invoke(null);
        }
        public static long threadCpuTimeNanos() throws Exception {
            return (long) threadCpuTimeNanosMethod.invoke(null);
        }
        public static void dumpHprofDataDdms() throws Exception {
            dumpHprofDataDdmsMethod.invoke(null);
        }
        public static void dumpReferenceTables() throws Exception {
            dumpReferenceTablesMethod.invoke(null);
        }
    }
}
