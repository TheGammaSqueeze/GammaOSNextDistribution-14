/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.os.cts;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Process;
import android.platform.test.annotations.IgnoreUnderRavenwood;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.flag.junit.RavenwoodFlagsValueProvider;
import android.platform.test.ravenwood.RavenwoodRule;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.sdksandbox.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * CTS for {@link Process}.
 *
 * We have more test in cts/tests/process/ too.
 */
@RunWith(AndroidJUnit4.class)
public class ProcessTest {
    @Rule public RavenwoodRule mRavenwood = new RavenwoodRule();

    // Required for RequiresFlagsEnabled and RequiresFlagsDisabled annotations to take effect.
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = RavenwoodRule.isOnRavenwood()
            ? RavenwoodFlagsValueProvider.createAllOnCheckFlagsRule()
            : DeviceFlagsValueProvider.createCheckFlagsRule();

    public static final int THREAD_PRIORITY_HIGHEST = -20;
    private static final String NONE_EXISITENT_NAME = "abcdefcg";
    private static final String WRONG_CACHE_NAME = "cache_abcdefg";
    private static final String PROCESS_SHELL= "shell";
    private static final String PROCESS_CACHE= "cache";
    private static final String REMOTE_SERVICE = "android.app.REMOTESERVICE";
    private static final int APP_UID = 10001;
    private static final int FIRST_SDK_SANDBOX_UID = 20000;
    private static final int LAST_SDK_SANDBOX_UID = 29999;
    private static final int SANDBOX_SDK_UID = 20001;
    private static final int ISOLATED_PROCESS_UID = 99037;
    private static final int APP_ZYGOTE_ISOLATED_UID = 90123;
    private static final String TAG = "ProcessTest";
    private ISecondary mSecondaryService = null;
    private Intent mIntent;
    private Object mSync;
    private boolean mHasConnected;
    private boolean mHasDisconnected;
    private ServiceConnection mSecondaryConnection;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        if (mRavenwood.isUnderRavenwood()) return;
        mContext = InstrumentationRegistry.getContext();
        mSync = new Object();
        mSecondaryConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className,
                    IBinder service) {
                // Connecting to a secondary interface is the same as any
                // other interface.
                android.util.Log.d(TAG, "connected");
                mSecondaryService = ISecondary.Stub.asInterface(service);
                synchronized (mSync) {
                    mHasConnected = true;
                    mSync.notify();
                }
            }
            public void onServiceDisconnected(ComponentName className) {
                Log.d(TAG, "disconnected");
                mSecondaryService = null;
                synchronized (mSync) {
                    mHasDisconnected = true;
                    mSync.notify();
                }
            }
        };
        mIntent = new Intent(REMOTE_SERVICE);
        mIntent.setPackage(mContext.getPackageName());
        mContext.startService(mIntent);

        Intent secondaryIntent = new Intent(ISecondary.class.getName());
        secondaryIntent.setPackage(mContext.getPackageName());
        mContext.bindService(secondaryIntent, mSecondaryConnection,
                Context.BIND_AUTO_CREATE);
        synchronized (mSync) {
            if (!mHasConnected) {
                try {
                    mSync.wait();
                } catch (InterruptedException e) {
                }
            }
        }
    }

    @After
    public void tearDown() throws Exception {
        if (mRavenwood.isUnderRavenwood()) return;
        if (mIntent != null) {
            mContext.stopService(mIntent);
        }
        if (mSecondaryConnection != null) {
            mContext.unbindService(mSecondaryConnection);
        }
    }

    @Test
    @IgnoreUnderRavenwood(reason = "Requires kernel support")
    public void testMiscMethods() {
        /*
         * Test setThreadPriority(int) and setThreadPriority(int, int)
         * 1.Set the priority of the calling thread, based on Linux priorities level,
         * from -20 for highest scheduling priority to 19 for lowest scheduling priority.
         * 2.Throws IllegalArgumentException if tid does not exist.
         */
        int myTid = Process.myTid();

        int priority = Process.getThreadPriority(myTid);
        assertTrue(priority >= THREAD_PRIORITY_HIGHEST
                && priority <= Process.THREAD_PRIORITY_LOWEST);

        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        assertEquals(Process.THREAD_PRIORITY_AUDIO, Process.getThreadPriority(myTid));

        Process.setThreadPriority(myTid, Process.THREAD_PRIORITY_LOWEST);
        assertEquals(Process.THREAD_PRIORITY_LOWEST, Process.getThreadPriority(myTid));

        Process.setThreadPriority(myTid, THREAD_PRIORITY_HIGHEST);
        assertEquals(THREAD_PRIORITY_HIGHEST, Process.getThreadPriority(myTid));

        int invalidPriority = THREAD_PRIORITY_HIGHEST - 1;
        Process.setThreadPriority(myTid, invalidPriority);
        assertEquals(THREAD_PRIORITY_HIGHEST, Process.getThreadPriority(myTid));

        try {
            Process.setThreadPriority(-1, Process.THREAD_PRIORITY_DEFAULT);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expect
        } // Hard to address logic of throws SecurityException

        /*
         * Returns the UID assigned to a particular user name, or -1 if there is
         * none.  If the given string consists of only numbers, it is converted
         * directly to a uid.
         */
        assertTrue(Process.getUidForName(PROCESS_SHELL) > 0);
        assertEquals(-1, Process.getUidForName(NONE_EXISITENT_NAME));
        assertEquals(0, Process.getUidForName("0"));

        /*
         * Returns the GID assigned to a particular user name, or -1 if there is
         * none.  If the given string consists of only numbers, it is converted
         * directly to a gid.
         */
        assertTrue(Process.getGidForName(PROCESS_CACHE) > 0);
        assertEquals(-1, Process.getGidForName(WRONG_CACHE_NAME));
        assertEquals(0, Process.getGidForName("0"));

        assertTrue(Process.myUid() >= 0);

        assertNotEquals(null, Process.getExclusiveCores());
    }

    /**
     * Test point of killProcess(int)
     * Only the process running the caller's packages/application
     * and any additional processes created by that app be able to kill each other's processes.
     */
    @Test
    @IgnoreUnderRavenwood(reason = "Requires kernel support")
    public void testKillProcess() throws Exception {
        long time = 0;
        int servicePid = 0;
        try {
            servicePid = mSecondaryService.getPid();
            time = mSecondaryService.getElapsedCpuTime();
        } finally {
            mContext.stopService(mIntent);
            mIntent = null;
        }

        assertTrue(time > 0);
        assertTrue(servicePid != Process.myPid());

        Process.killProcess(servicePid);
        synchronized (mSync) {
            if (!mHasDisconnected) {
                try {
                    mSync.wait();
                } catch (InterruptedException e) {
                }
            }
        }
        assertTrue(mHasDisconnected);
    }

    /**
     * Test myPid() point.
     * Returns the identifier of this process, which can be used with
     * {@link #killProcess} and {@link #sendSignal}.
     * Test sendSignal(int) point.
     * Send a signal to the given process.
     */
    @Test
    @IgnoreUnderRavenwood(reason = "Requires kernel support")
    public void testSendSignal() throws Exception {
        int servicePid = 0;
        try {
            servicePid = mSecondaryService.getPid();
        } finally {
            mContext.stopService(mIntent);
            mIntent = null;
        }
        assertTrue(servicePid != 0);
        assertTrue(Process.myPid() != servicePid);
        Process.sendSignal(servicePid, Process.SIGNAL_KILL);
        synchronized (mSync) {
            if (!mHasDisconnected) {
                try {
                    mSync.wait();
                } catch (InterruptedException e) {
                }
            }
        }
        assertTrue(mHasDisconnected);
    }

    /**
     * Tests {@link Process#isSdkSandbox() (boolean)} API.
     */
    @Test
    public void testIsSdkSandbox() {
        assertFalse(Process.isSdkSandbox());
    }

    /**
     * Tests for {@link Process#isSdkSandboxUid() (boolean)} API.
     */
    @Test
    public void testIsSdkSandboxUid_UidNotSandboxUid() {
        assertFalse(Process.isSdkSandboxUid(APP_UID));
    }

    /**
     * Tests for the following APIs
     * {@link Process#isSdkSandboxUid() (boolean)}
     */
    @Test
    public void testSdkSandboxUids() {
        for (int i = FIRST_SDK_SANDBOX_UID; i <= LAST_SDK_SANDBOX_UID; i++) {
            assertTrue(Process.isSdkSandboxUid(i));
        }
    }

    /**
     * Tests for {@link Process#getAppUidForSdkSandboxUid(int) (int)} API.
     */
    @Test
    public void testGetAppUidForSdkSandboxUid() {
        assertEquals(APP_UID, Process.getAppUidForSdkSandboxUid(SANDBOX_SDK_UID));
    }

    @Test
    public void testGetAppUidForSdkSandboxUid_invalidInput() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Process.getAppUidForSdkSandboxUid(-1));
        assertEquals(exception.getMessage(), "Input UID is not an SDK sandbox UID");
    }

    /**
     * Tests for {@link Process#getSdkSandboxUidForAppUid(int) (int)} API
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SDK_SANDBOX_UID_TO_APP_UID_API)
    public void testGetSdkSandboxUidForAppUid() {
        assertEquals(SANDBOX_SDK_UID, Process.getSdkSandboxUidForAppUid(APP_UID));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SDK_SANDBOX_UID_TO_APP_UID_API)
    public void testGetSdkSandboxUidForAppUid_invalidInput() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Process.getSdkSandboxUidForAppUid(-1));
        assertEquals(exception.getMessage(), "Input UID is not an app UID");
    }


    /**
     * Tests that the reserved UID is not taken by an actual package.
     */
    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public void testReservedVirtualUid() {
        PackageManager pm = mContext.getPackageManager();
        final String name = pm.getNameForUid(Process.SDK_SANDBOX_VIRTUAL_UID);
        assertNull(name);

        // PackageManager#getPackagesForUid requires android.permission.INTERACT_ACROSS_USERS for
        // cross-user calls.
        runWithShellPermissionIdentity(() -> {
            final String[] packages = pm.getPackagesForUid(Process.SDK_SANDBOX_VIRTUAL_UID);
            assertNull(packages);
        });
    }

    /**
     * Tests for {@link Process#isIsolatedUid(int)} (int)} API.
     */
    @Test
    public void testIsolatedProccesUids() {
        assertTrue(Process.isIsolatedUid(ISOLATED_PROCESS_UID));
        assertTrue(Process.isIsolatedUid(APP_ZYGOTE_ISOLATED_UID));
        // A random UID before the  FIRST_APPLICATION_UID is not an isolated process uid.
        assertFalse(Process.isIsolatedUid(57));
        // Sdk Sandbox UID is not an isolated process uid
        assertFalse(Process.isIsolatedUid(SANDBOX_SDK_UID));
        // App uid is not an isolated process uid
        assertFalse(Process.isIsolatedUid(APP_UID));
    }

    @Test
    public void testApplicationUids() {
        assertTrue(Process.isApplicationUid(Process.FIRST_APPLICATION_UID));
        assertTrue(Process.isApplicationUid(Process.LAST_APPLICATION_UID));
        assertFalse(Process.isApplicationUid(Process.ROOT_UID));
        assertFalse(Process.isApplicationUid(Process.PHONE_UID));
        assertFalse(Process.isApplicationUid(Process.INVALID_UID));
    }

    @Test
    public void testIs64Bit() {
        // We're not concerned with the answer, just that it works
        Process.is64Bit();
    }
}
