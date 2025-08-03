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
package android.app.cts.getbindinguidimportance;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE;
import static android.app.stubs.shared.Shared_getBindingUidImportance.ACTION_TEST_PROVIDER;
import static android.app.stubs.shared.Shared_getBindingUidImportance.ACTION_TEST_SERVICE_BINDING;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.ActivityManager;
import android.app.Flags;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.ShellUtils;
import com.android.compatibility.common.util.TestUtils;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class ActivityManager_getBindingUidImportanceTest {
    private static final String TAG = "ActivityManager_getBindingUidImportanceTest";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String HELPER_PACKAGE = "android.app.stubs";
    private static final ComponentName HELPER_RECEIVER = new ComponentName(
            HELPER_PACKAGE, "android.app.stubs.Receiver_getBindingUidImportance");

    private static final int RECEIVER_TIMEOUT_SEC = 60 * 3;

    private static final int MY_UID = Process.myUid();
    private static final int NONEXISTENT_UID = -1;
    private static int sHelperUid;

    private static Context sContext;

    private static int sLastUidImportance;

    private static void resetLastImportance() {
        sLastUidImportance = -1;
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        sContext = InstrumentationRegistry.getInstrumentation().getContext();

        sHelperUid = sContext.getPackageManager().getPackageUid(HELPER_PACKAGE, 0);

        assertNotEquals("Helper UID must be different from mine", sHelperUid, MY_UID);
        Log.d(TAG, "Helper UID: " + sHelperUid);
    }

    @Before
    public void setUp() {
        // Force-stop the helper app to make sure there's no leftover binding, etc.
        ShellUtils.runShellCommand("am force-stop %s", HELPER_PACKAGE);

        resetLastImportance();
    }

    /**
     * Test: get the importance of an UID that's binding to this process.
     *
     * The API should return the actual importance.
     */
    // Add the flag annotation
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_GET_BINDING_UID_IMPORTANCE)
    public void testWithServiceBinding() throws Exception {
        sendBroadcastAndWait(ACTION_TEST_SERVICE_BINDING);

        // Because we call getBindingUidImportance() when the UID in running a receiver,
        // the importance shouldn't be cached.
        assertImportanceNotCached(sLastUidImportance);

        // Now the binding is disconnected, it should return GONE.
        resetLastImportance();
        assertImportanceGone(getBindingUidImportance(sContext, sHelperUid));
    }

    /**
     * Test: get the importance of an UID that's accessing a provider in this process.
     *
     * The API should return the actual importance.
     */
    // Add the flag annotation
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_GET_BINDING_UID_IMPORTANCE)
    public void testWithProviderAccess() throws Exception {
        sendBroadcastAndWait(ACTION_TEST_PROVIDER);

        // Because we call getBindingUidImportance() when the UID in running a receiver,
        // the importance shouldn't be cached.
        assertImportanceNotCached(sLastUidImportance);

        // Now the provider client is closed, it should return GONE.
        // But the provider connection isn't disconnected right away; there's some timeout,
        // so we account for that with waitUntil.
        resetLastImportance();
        TestUtils.waitUntil("UID importance should become GONE", 60, () -> {
            return getBindingUidImportance(sContext, sHelperUid) == IMPORTANCE_GONE;
        });
    }

    /**
     * Test: try to get the importance of an UID that's not binding and not accessing the provider.
     * It should return GONE.
     */
    // Add the flag annotation
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_GET_BINDING_UID_IMPORTANCE)
    public void testNoBindingNoProviderAccess() {
        assertImportanceGone(getBindingUidImportance(sContext, sHelperUid));
    }

    /**
     * Test: try to get the importance of an UID that doesn't exist.
     * It should return GONE.
     */
    // Add the flag annotation
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_GET_BINDING_UID_IMPORTANCE)
    public void testNonExistingUid() {
        assertImportanceGone(getBindingUidImportance(sContext, NONEXISTENT_UID));
    }

    /**
     * Getting the importance of own UID is always allowed.
     */
    // Add the flag annotation
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_GET_BINDING_UID_IMPORTANCE)
    public void testSelfImportance() {
        assertImportanceNotCached(getBindingUidImportance(sContext, MY_UID));
    }

    private static void assertImportanceNotCached(int importance) {
        if (importance < 0) {
            fail("importance is negative. getBindingUidImportance() wasn't called?");
        }
        if (importance > IMPORTANCE_SERVICE) {
            fail("Importance should be non cached, but was " + importance);
        }
    }

    public static void assertImportanceGone(int importance) {
        assertEquals("Importance should seem to be GONE", importance, IMPORTANCE_GONE);
    }

    /**
     * Call ActivityManager.getBindingUidImportance() with the required permission, and
     * return the result. It also updates {@link #sLastUidImportance}.
     * @return the importance
     */
    private static int getBindingUidImportance(Context context, int uid) {
        sLastUidImportance = ShellIdentityUtils.invokeMethodWithShellPermissions(
                context.getSystemService(ActivityManager.class),
                (am) -> am.getBindingUidImportance(uid),
                android.Manifest.permission.GET_BINDING_UID_IMPORTANCE);
        Log.d(TAG, "Importance of UID " + uid + " is " + sLastUidImportance);
        return sLastUidImportance;
    }

    private void sendBroadcastAndWait(String action) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        final BroadcastReceiver resultReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Result received");
                latch.countDown();
            }
        };

        sContext.sendOrderedBroadcast(
                new Intent(action).setComponent(HELPER_RECEIVER),
                null, // receiverPermission
                resultReceiver,
                new Handler(Looper.getMainLooper()), // scheduler
                0, // initialCode
                null, // initialData
                null); // initialExtras

        assertTrue("Receiver didn't finish in time",
                latch.await(RECEIVER_TIMEOUT_SEC, TimeUnit.SECONDS));
    }

    public static class MyService extends Service {
        @Override
        public IBinder onBind(Intent intent) {
            Log.d(TAG, "MyService.onBind");

            // Update sLastUidImportance.
            getBindingUidImportance(sContext, sHelperUid);
            return new Binder();
        }
    }

    public static class MyProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                String sortOrder) {
            Log.d(TAG, "MyProvider.query");

            // Update sLastUidImportance.
            getBindingUidImportance(sContext, sHelperUid);

            // Return a non-null cursor.
            return new MatrixCursor(new String[]{"xxx"}, 0);
        }

        @Override
        public String getType(Uri uri) {
            return null;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            return null;
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            return 0;
        }
    }
}
