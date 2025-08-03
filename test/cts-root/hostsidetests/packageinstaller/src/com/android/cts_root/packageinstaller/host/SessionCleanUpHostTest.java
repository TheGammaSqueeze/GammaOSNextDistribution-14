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

package com.android.cts_root.packageinstaller.host;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.LargeTest;

import com.android.ddmlib.Log;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Tests sessions are cleaned up (session id and staging files) when installation fails.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class SessionCleanUpHostTest extends BaseHostJUnit4Test {
    private static final String TAG = "SessionCleanUpHostTest";
    // Expiry time for staged sessions that have not changed state in this time
    private static final long MAX_TIME_SINCE_UPDATE_MILLIS = TimeUnit.DAYS.toMillis(21);

    /**
     * Checks staging directories are deleted when installation fails.
     */
    @Rule
    public TestRule mStagingDirectoryRule = (base, description) -> new Statement() {
        @Override
        public void evaluate() throws Throwable {
            List<String> stagedBefore = getStagingDirectoriesForStagedSessions();
            List<String> nonStagedBefore = getStagingDirectoriesForNonStagedSessions();
            Log.d(TAG, "stagedBefore=" + stagedBefore);
            Log.d(TAG, "nonStagedBefore=" + nonStagedBefore);

            base.evaluate();

            List<String> stagedAfter = getStagingDirectoriesForStagedSessions();
            List<String> nonStagedAfter = getStagingDirectoriesForNonStagedSessions();
            Log.d(TAG, "stagedAfter=" + stagedAfter);
            Log.d(TAG, "nonStagedAfter=" + nonStagedAfter);

            // stagedAfter will be a subset of stagedBefore if all staging directories created
            // during tests are correctly deleted when installation fails
            assertThat(stagedBefore).containsAtLeastElementsIn(stagedAfter);
            assertThat(nonStagedBefore).containsAtLeastElementsIn(nonStagedAfter);
        }
    };

    private void run(String method) throws Exception {
        assertThat(runDeviceTests("com.android.cts_root.packageinstaller",
                "com.android.cts_root.packageinstaller.SessionCleanUpTest",
                method)).isTrue();
    }

    @Before
    @After
    public void cleanUp() throws Exception {
        getDevice().uninstallPackage("com.android.cts.install.lib.testapp.A");
        getDevice().uninstallPackage("com.android.cts.install.lib.testapp.B");
        getDevice().uninstallPackage("com.android.cts.install.lib.testapp.C");
    }

    /**
     * Tests a successful single-package session is cleaned up.
     */
    @Test
    public void testSessionCleanUp_Single_Success() throws Exception {
        run("testSessionCleanUp_Single_Success");
    }

    /**
     * Tests a successful multi-package session is cleaned up.
     */
    @Test
    public void testSessionCleanUp_Multi_Success() throws Exception {
        run("testSessionCleanUp_Multi_Success");
    }

    /**
     * Tests a single-package session is cleaned up when verification failed.
     */
    @Test
    public void testSessionCleanUp_Single_VerificationFailed() throws Exception {
        run("testSessionCleanUp_Single_VerificationFailed");
    }

    /**
     * Tests a multi-package session is cleaned up when verification failed.
     */
    @Test
    public void testSessionCleanUp_Multi_VerificationFailed() throws Exception {
        run("testSessionCleanUp_Multi_VerificationFailed");
    }

    /**
     * Tests a single-package session is cleanup up when validation failed.
     */
    @Test
    public void testSessionCleanUp_Single_ValidationFailed() throws Exception {
        run("testSessionCleanUp_Single_ValidationFailed");
    }

    /**
     * Tests a multi-package session is cleaned up when validation failed.
     */
    @Test
    public void testSessionCleanUp_Multi_ValidationFailed() throws Exception {
        run("testSessionCleanUp_Multi_ValidationFailed");
    }

    /**
     * Tests a single-package session is cleaned up when user rejected the permission.
     */
    @Test
    public void testSessionCleanUp_Single_NoPermission() throws Exception {
        run("testSessionCleanUp_Single_NoPermission");
    }

    /**
     * Tests a multi-package session is cleaned up when user rejected the permission.
     */
    @Test
    public void testSessionCleanUp_Multi_NoPermission() throws Exception {
        run("testSessionCleanUp_Multi_NoPermission");
    }

    /**
     * Tests a single-package session is cleaned up when it expired.
     */
    @LargeTest
    @Ignore("b/217132609")
    @Test
    public void testSessionCleanUp_Single_Expire() throws Exception {
        run("testSessionCleanUp_Single_Expire_Install");
        getDevice().reboot();
        run("testSessionCleanUp_Single_Expire_VerifyInstall");
        expireSessions();
        run("testSessionCleanUp_Single_Expire_CleanUp");
    }

    /**
     * Tests a multi-package session is cleaned up when it expired.
     */
    @Ignore("b/217132609")
    @Test
    public void testSessionCleanUp_Multi_Expire() throws Exception {
        run("testSessionCleanUp_Multi_Expire_Install");
        getDevice().reboot();
        run("testSessionCleanUp_Multi_Expire_VerifyInstall");
        expireSessions();
        run("testSessionCleanUp_Multi_Expire_CleanUp");
    }

    /**
     * Tests sessions are cleaned up on low storage.
     */
    @Test
    public void testSessionCleanUp_LowStorage() throws Exception {
        Instant t1 = Instant.ofEpochMilli(getDevice().getDeviceDate());
        Instant t2 = t1.plusMillis(TimeUnit.DAYS.toMillis(1));
        try {
            run("testSessionCleanUp_LowStorage_Install");
            // Advance system clock to have old sessions
            getDevice().setDate(Date.from(t2));
            getDevice().executeShellCommand("am broadcast -a android.intent.action.TIME_SET");
            // Run PackageManager#freeStorage to abandon old sessions
            run("testSessionCleanUp_LowStorage_CleanUp");
        } finally {
            // Restore system clock
            getDevice().setDate(Date.from(t1));
            getDevice().executeShellCommand("am broadcast -a android.intent.action.TIME_SET");
        }
    }

    private List<String> getStagingDirectoriesForNonStagedSessions() throws Exception {
        return getStagingDirectories("/data/app", "vmdl\\d+.tmp");
    }

    private List<String> getStagingDirectoriesForStagedSessions() throws Exception {
        return getStagingDirectories("/data/app-staging", "session_\\d+");
    }

    private List<String> getStagingDirectories(String baseDir, String pattern) throws Exception {
        return getDevice().getFileEntry(baseDir).getChildren(false)
                .stream().filter(entry -> entry.getName().matches(pattern))
                .map(entry -> entry.getName())
                .collect(Collectors.toList());
    }

    private void expireSessions() throws Exception {
        Instant t1 = Instant.ofEpochMilli(getDevice().getDeviceDate());
        Instant t2 = t1.plusMillis(MAX_TIME_SINCE_UPDATE_MILLIS);
        try {
            // Advance system clock by MAX_TIME_SINCE_UPDATE_MILLIS to expire the staged session
            getDevice().setDate(Date.from(t2));
            getDevice().executeShellCommand("am broadcast -a android.intent.action.TIME_SET");
            // Restart system server to run expiration
            getDevice().executeShellCommand("stop");
            getDevice().executeShellCommand("start");
            getDevice().waitForDeviceAvailable();
        } finally {
            // Restore system clock
            getDevice().setDate(Date.from(t1));
            getDevice().executeShellCommand("am broadcast -a android.intent.action.TIME_SET");
        }
    }
}
