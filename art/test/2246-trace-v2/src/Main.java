/*
 * Copyright (C) 2024 The Android Open Source Project
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
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;

public class Main {
    private static final String TEMP_FILE_NAME_PREFIX = "test";
    private static final String TEMP_FILE_NAME_SUFFIX = ".trace";
    private static final int WALL_CLOCK_FLAG = 0x010;
    private static final int TRACE_OUTPUT_V2_FLAG = 0b010;
    private static final int STREAMING_DUAL_CLOCK_VERSION = 1;
    private static final int STREAMING_WALL_CLOCK_VERSION = 1;
    private static File file;

    public static void main(String[] args) throws Exception {
        System.loadLibrary(args[0]);
        String name = System.getProperty("java.vm.name");
        if (!"Dalvik".equals(name)) {
            System.out.println("This test is not supported on " + name);
            return;
        }

        ensureJitCompiled(Main.class, "$noinline$doSomeWorkJIT");

        System.out.println("***** streaming test - dual clock *******");
        testTracing(
                /* streaming=*/true, /* flags= */ 0, STREAMING_DUAL_CLOCK_VERSION);

        System.out.println("***** streaming test - wall clock *******");
        testTracing(
                /* streaming=*/true, /* flags= */ WALL_CLOCK_FLAG, STREAMING_WALL_CLOCK_VERSION);
    }

    public static void testTracing(boolean streaming, int flags, int expected_version)
            throws Exception {
        Main m = new Main();
        Thread t = new Thread(() -> {
            try {
                file = createTempFile();
                FileOutputStream out_file = new FileOutputStream(file);
                VMDebug.startMethodTracingV2(
                        file.getPath(), out_file.getFD(), 0, flags, false, 0, streaming);
                Main m1 = new Main();
                m1.$noinline$doSomeWork();
                // Call JITed code multiple times to flush out any issues with timestamps.
                for (int i = 0; i < 20; i++) {
                    m.$noinline$doSomeWorkJIT();
                }
                VMDebug.$noinline$stopMethodTracing();
                out_file.close();
                dumpTrace(file.getAbsolutePath(), "TestThread2246");
                file.delete();
            } catch (Exception e) {
                System.out.println("Exception in thread " + e);
                e.printStackTrace();
            } finally {
                file.delete();
            }
        }, "TestThread2246");
        try {
            if (VMDebug.getMethodTracingMode() != 0) {
                VMDebug.$noinline$stopMethodTracing();
            }

            t.start();
            t.join();

            file = createTempFile();
            FileOutputStream main_out_file = new FileOutputStream(file);
            VMDebug.startMethodTracingV2(
                    file.getPath(), main_out_file.getFD(), 0, flags, false, 0, streaming);
            m.$noinline$doSomeWork();
            // Call JITed code multiple times to flush out any issues with timestamps.
            for (int i = 0; i < 20; i++) {
                m.$noinline$doSomeWorkJIT();
            }
            m.doSomeWorkThrow();
            VMDebug.$noinline$stopMethodTracing();
            main_out_file.close();
            dumpTrace(file.getAbsolutePath(), "main");
            file.delete();
        } finally {
            file.delete();
        }
    }

    private static File createTempFile() throws Exception {
        try {
            return File.createTempFile(TEMP_FILE_NAME_PREFIX, TEMP_FILE_NAME_SUFFIX);
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

    public void callOuterFunction() {
        callLeafFunction();
    }

    public void callLeafFunction() {}

    public void $noinline$doSomeWork() {
        callOuterFunction();
        callLeafFunction();
    }

    public void $noinline$doSomeWorkJIT() {
        callOuterFunction();
        callLeafFunction();
    }

    public void callThrowFunction() throws Exception {
        throw new Exception("test");
    }

    public void doSomeWorkThrow() {
        try {
            callThrowFunction();
        } catch (Exception e) {
        }
    }

    private static class VMDebug {
        private static final Method startMethodTracingMethod;
        private static final Method stopMethodTracingMethod;
        private static final Method getMethodTracingModeMethod;
        static {
            try {
                Class<?> c = Class.forName("dalvik.system.VMDebug");
                startMethodTracingMethod = c.getDeclaredMethod("startMethodTracing", String.class,
                        FileDescriptor.class, Integer.TYPE, Integer.TYPE, Boolean.TYPE,
                        Integer.TYPE, Boolean.TYPE);
                stopMethodTracingMethod = c.getDeclaredMethod("stopMethodTracing");
                getMethodTracingModeMethod = c.getDeclaredMethod("getMethodTracingMode");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public static void startMethodTracingV2(String filename, FileDescriptor fd, int bufferSize,
                int flags, boolean samplingEnabled, int intervalUs, boolean streaming)
                throws Exception {
            startMethodTracingMethod.invoke(null, filename, fd, bufferSize,
                    flags | TRACE_OUTPUT_V2_FLAG, samplingEnabled, intervalUs, streaming);
        }
        public static void $noinline$stopMethodTracing() throws Exception {
            stopMethodTracingMethod.invoke(null);
        }
        public static int getMethodTracingMode() throws Exception {
            return (int) getMethodTracingModeMethod.invoke(null);
        }
    }

    private static native void ensureJitCompiled(Class<?> cls, String methodName);
    private static native void dumpTrace(String fileName, String threadName);
}
