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

package com.android.sts.common;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeThat;

import static java.util.stream.Collectors.joining;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Setup and run a native PoC, asserting exit conditions */
@AutoValue
public abstract class NativePoc {
    static final long DEFAULT_POC_TIMEOUT_SECONDS = 60;
    static final String TMP_PATH = "/data/local/tmp/";
    static final String RESOURCE_ROOT = "/";
    static final int BUF_SIZE = 65536;

    abstract String pocName();
    abstract ImmutableList<String> args();
    abstract ImmutableMap<String, String> envVars();
    abstract boolean useDefaultLdLibraryPath();
    abstract long timeoutSeconds();
    abstract ImmutableList<String> resources();
    abstract String resourcePushLocation();
    abstract boolean only32();
    abstract boolean only64();
    abstract AfterFunction after();
    abstract NativePocAsserter asserter();
    abstract boolean assumePocExitSuccess();

    NativePoc() {}

    public static Builder builder() {
        return new AutoValue_NativePoc.Builder()
                .args(ImmutableList.of())
                .envVars(ImmutableMap.of())
                .useDefaultLdLibraryPath(false)
                .timeoutSeconds(DEFAULT_POC_TIMEOUT_SECONDS)
                .resources(ImmutableList.of())
                .resourcePushLocation(TMP_PATH)
                .after((res) -> {})
                .only32(false)
                .only64(false)
                .asserter(new NativePocAsserter() {})
                .assumePocExitSuccess(true);
    }

    public static enum Bitness {
        AUTO, // push 32 or 64 bit version of PoC depending on device arch
        ONLY32, // push only 32bit version of PoC
        ONLY64 // push only 64bit version of PoC; raises error when running on 32bit-only device
    }

    @AutoValue.Builder
    public abstract static class Builder {
        /** Name of executable to be uploaded and run. Do not include "_sts??" suffix. */
        public abstract Builder pocName(String value);

        abstract String pocName();

        /** List of arguments to be passed to the executable PoC */
        public abstract Builder args(List<String> value);
        /** List of arguments to be passed to the executable PoC */
        public abstract Builder args(String... value);

        /** Map of environment variables to be set before running the PoC */
        public abstract Builder envVars(Map<String, String> value);

        abstract ImmutableMap<String, String> envVars();

        /** Whether to include /system/lib64 and /system/lib in LD_LIBRARY_PATH */
        public abstract Builder useDefaultLdLibraryPath(boolean value);

        abstract boolean useDefaultLdLibraryPath();

        /**
         * How long to let the PoC run before terminating
         *
         * @param value how many seconds to let the native PoC run before it's terminated
         * @param reason explain why a different timeout amount is needed instead of the default
         *     {@link #DEFAULT_POC_TIMEOUT_SECONDS}. Generally used for PoCs that tries to exploit
         *     race conditions.
         * @return this Builder instance
         */
        public Builder timeoutSeconds(long value, String reason) {
            return timeoutSeconds(value);
        }

        abstract Builder timeoutSeconds(long value);

        /** List of java resources to extract and upload to the device */
        public abstract Builder resources(List<String> value);
        /** List of java resources to extract and upload to the device */
        public abstract Builder resources(String... value);

        /** Where to upload extracted Java resources to. Defaults to where the PoC is uploaded */
        public abstract Builder resourcePushLocation(String value);

        abstract String resourcePushLocation();

        /** Force using 32-bit version of the PoC executable */
        public Builder only32() {
            return only32(true);
        }

        abstract Builder only32(boolean value);

        /** Force using 64-bit version of the PoC executable */
        public Builder only64() {
            return only64(true);
        }

        abstract Builder only64(boolean value);

        /** Force using 32bit or 64bit version of the native poc */
        public Builder bitness(Bitness bitness) {
            if (bitness == Bitness.ONLY32) {
                return only32(true).only64(false);
            }
            if (bitness == Bitness.ONLY64) {
                return only32(false).only64(true);
            }
            return only32(false).only64(false);
        }

        /**
         * Function to run after the PoC finishes executing but before assertion or cleanups.
         *
         * <p>This is typically used to wait for side effects of the PoC that may happen after the
         * PoC process itself finished, e.g. waiting for a crashdump to be written to file or for a
         * service to crash.
         */
        public abstract Builder after(AfterFunction value);

        /** A {@link NativePocAsserter} to check PoC execution results or side-effect */
        public abstract Builder asserter(NativePocAsserter value);

        /** Whether to throw an assumption failure when PoC does not return 0. Defaults true */
        public abstract Builder assumePocExitSuccess(boolean value);

        abstract NativePoc autoBuild();

        /** Build an immutable NativePoc object */
        public NativePoc build() {
            if (useDefaultLdLibraryPath()) {
                updateLdLibraryPath();
            }
            if (!resourcePushLocation().endsWith("/")) {
                resourcePushLocation(resourcePushLocation() + "/");
            }
            NativePoc nativePoc = autoBuild();
            assertFalse("both only32 & only64 are set!", nativePoc.only32() && nativePoc.only64());
            assertNotNull("pocName not set!", nativePoc.pocName());
            return nativePoc;
        }

        private void updateLdLibraryPath() {
            String key = "LD_LIBRARY_PATH";
            String newVal;
            if (envVars().containsKey(key)) {
                newVal = envVars().get(key) + ":/system/lib64:/system/lib";
            } else {
                newVal = "/system/lib64:/system/lib";
            }
            Map<String, String> newMap =
                    new HashMap<>() {
                        {
                            putAll(envVars());
                            put(key, newVal);
                        }
                    };
            envVars(ImmutableMap.copyOf(newMap));
        }
    }

    /**
     * Execute the PoC with the given parameters and assertions.
     *
     * @param test the instance of BaseHostJUnit4Test this is running in. Usually called with "this"
     *     if called from an STS test.
     */
    public void run(final BaseHostJUnit4Test test) throws Exception {
        CLog.d("Trying to start NativePoc: %s", this.toString());
        CommandResult res = runPocAndAssert(test);
        assumeThat(
                "PoC timed out. You may want to make it faster or specify timeout amount",
                res.getStatus(),
                not(CommandStatus.TIMED_OUT));
        if (assumePocExitSuccess()) {
            assumeThat(
                    "PoC did not exit with success. stderr: " + res.getStderr(),
                    res.getStatus(),
                    is(CommandStatus.SUCCESS));
        }
    }

    private CommandResult runPocAndAssert(final BaseHostJUnit4Test test) throws Exception {
        ITestDevice device = test.getDevice();

        try (AutoCloseable aPoc = withPoc(test, device);
                AutoCloseable aRes = withResourcesUpload(device);
                AutoCloseable aAssert = asserter().withAutoCloseable(this, device)) {
            // Setup environment variable shell command prefix
            String envStr =
                    envVars().keySet().stream()
                            .map(k -> String.format("%s='%s'", k, escapeQuote(envVars().get(k))))
                            .collect(joining(" "));

            // Setup command arguments string for shell
            String argStr = args().stream().map(s -> escapeQuote(s)).collect(joining(" "));

            // Run the command
            CommandResult res =
                    device.executeShellV2Command(
                            String.format("cd %s; %s ./%s %s", TMP_PATH, envStr, pocName(), argStr),
                            timeoutSeconds(),
                            TimeUnit.SECONDS,
                            0 /* retryAttempts */);
            CLog.d(
                    "PoC exit code: %d\nPoC stdout:\n%s\nPoC stderr:\n%s\n",
                    res.getExitCode(), res.getStdout(), res.getStderr());

            after().run(res);
            asserter().checkCmdResult(res);
            return res;
        }
    }

    private static String escapeQuote(String s) {
        return s.replace("'", "'\"'\"'");
    }

    private AutoCloseable withPoc(final BaseHostJUnit4Test test, final ITestDevice device)
            throws DeviceNotAvailableException, FileNotFoundException {
        PocPusher pocPusher =
                new PocPusher().setDevice(device).setBuild(test.getBuild()).setAbi(test.getAbi());
        if (only32()) {
            pocPusher.only32();
        }
        if (only64()) {
            pocPusher.only64();
        }
        final String remoteFile = TMP_PATH + pocName();
        pocPusher.pushFile(pocName() + "_sts", remoteFile);
        device.executeShellV2Command(String.format("chmod 777 '%s'", remoteFile));
        CommandUtil.runAndCheck(device, String.format("test -r '%s'", remoteFile));
        CommandUtil.runAndCheck(device, String.format("test -w '%s'", remoteFile));
        CommandUtil.runAndCheck(device, String.format("test -x '%s'", remoteFile));

        return new AutoCloseable() {
            @Override
            public void close() throws DeviceNotAvailableException {
                device.deleteFile(remoteFile);
            }
        };
    }

    private AutoCloseable withResourcesUpload(final ITestDevice device)
            throws DeviceNotAvailableException, IOException {
        for (String resource : resources()) {
            File resTmpFile = File.createTempFile("STSNativePoc", "");
            try {
                try (InputStream in =
                                NativePoc.class.getResourceAsStream(RESOURCE_ROOT + resource);
                        OutputStream out =
                                new BufferedOutputStream(new FileOutputStream(resTmpFile))) {
                    byte[] buf = new byte[BUF_SIZE];
                    int chunkSize;
                    while ((chunkSize = in.read(buf)) != -1) {
                        out.write(buf, 0, chunkSize);
                    }
                }

                device.pushFile(resTmpFile, resourcePushLocation() + resource);
            } finally {
                resTmpFile.delete();
            }
        }

        return new AutoCloseable() {
            @Override
            public void close() throws DeviceNotAvailableException {
                tryRemoveResources(device);
            }
        };
    }

    private void tryRemoveResources(ITestDevice device) throws DeviceNotAvailableException {
        for (String resource : resources()) {
            device.deleteFile(resourcePushLocation() + resource);
        }
    }

    /** Lambda construct to run after PoC finished executing but before assertion and cleanup. */
    public static interface AfterFunction {
        void run(CommandResult res) throws Exception;
    }
}
