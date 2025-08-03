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

package android.provider.cts.settings;

import static android.provider.DeviceConfig.SYNC_DISABLED_MODE_NONE;
import static android.provider.Settings.RESET_MODE_PACKAGE_DEFAULTS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;
import com.android.internal.annotations.GuardedBy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class Settings_ConfigTest {

    private static final String NAMESPACE1 = "namespace1";
    private static final String NAMESPACE2 = "namespace2";
    private static final String EMPTY_NAMESPACE = "empty_namespace";
    private static final String KEY1 = "key1";
    private static final String KEY2 = "key2";
    private static final String VALUE1 = "value1";
    private static final String VALUE2 = "value2";
    private static final String VALUE3 = "value3";
    private static final String VALUE4 = "value4";
    private static final String DEFAULT_VALUE = "default_value";


    private static final String TAG = "ContentResolverTest";

    private static final Uri TABLE1_URI = Uri.parse("content://"
                                            + Settings.AUTHORITY + "/config");

    private static final String TEST_PACKAGE_NAME = "android.content.cts";

    private static final long OPERATION_TIMEOUT_MS = 10000;

    private static final long WAIT_FOR_PROPERTY_CHANGE_TIMEOUT_MILLIS = 2000; // 2 sec
    private final Object mLock = new Object();


    private static final String WRITE_DEVICE_CONFIG_PERMISSION =
            "android.permission.WRITE_DEVICE_CONFIG";

    private static final String READ_DEVICE_CONFIG_PERMISSION =
            "android.permission.READ_DEVICE_CONFIG";

    private static final String MONITOR_DEVICE_CONFIG_ACCESS =
            "android.permission.MONITOR_DEVICE_CONFIG_ACCESS";

    private static ContentResolver sContentResolver;
    private static Context sContext;

    private int mInitialSyncDisabledMode;

    /**
     * Get necessary permissions to access Setting.Config API and set up context and sync mode.
     */
    @Before
    public void setUpContext() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                WRITE_DEVICE_CONFIG_PERMISSION, READ_DEVICE_CONFIG_PERMISSION,
                MONITOR_DEVICE_CONFIG_ACCESS);
        sContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        sContentResolver = sContext.getContentResolver();
        mInitialSyncDisabledMode = Settings.Config.getSyncDisabledMode();
        Settings.Config.setSyncDisabledMode(SYNC_DISABLED_MODE_NONE);
    }

    /**
     * Clean up the namespaces, sync mode and permissions.
     */
    @After
    public void cleanUp() {
        deleteProperties(NAMESPACE1, Arrays.asList(KEY1, KEY2));
        deleteProperties(NAMESPACE2, Arrays.asList(KEY1, KEY2));
        Settings.Config.setSyncDisabledMode(mInitialSyncDisabledMode);
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

     /**
     * Checks that getting string which does not exist returns null.
     */
    @Test
    public void testGetString_empty() {
        String result = Settings.Config.getString(KEY1);
        assertNull("Request for non existent flag name in Settings.Config API should return null "
                + "while " + result + " was returned", result);
    }

    /**
     * Checks that getting strings which does not exist returns empty map.
     */
    @Test
    public void testGetStrings_empty() {
        Map<String, String> result = Settings.Config
                .getStrings(EMPTY_NAMESPACE, Arrays.asList(KEY1));
        assertTrue("Request for non existent flag name in Settings.Config API should return "
                + "empty map while " + result.toString() + " was returned", result.isEmpty());
    }

    /**
     * Checks that setting and getting string from the same namespace return correct value.
     */
    @Test
    public void testSetAndGetString_sameNamespace() {
        Settings.Config.putString(NAMESPACE1, KEY1, VALUE1, /*makeDefault=*/false);
        String result = Settings.Config.getStrings(NAMESPACE1, Arrays.asList(KEY1)).get(KEY1);
        assertEquals("Value read from Settings.Config API does not match written value.", VALUE1,
                result);
    }

    /**
     * Checks that setting a string in one namespace does not set the same string in a different
     * namespace.
     */
    @Test
    public void testSetAndGetString_differentNamespace() {
        Settings.Config.putString(NAMESPACE1, KEY1, VALUE1, /*makeDefault=*/false);
        String result = Settings.Config.getStrings(NAMESPACE2, Arrays.asList(KEY1)).get(KEY1);
        assertNull("Value for same keys written to different namespaces must not clash", result);
    }

    /**
     * Checks that different namespaces can keep different values for the same key.
     */
    @Test
    public void testSetAndGetString_multipleNamespaces() {
        Settings.Config.putString(NAMESPACE1, KEY1, VALUE1, /*makeDefault=*/false);
        Settings.Config.putString(NAMESPACE2, KEY1, VALUE2, /*makeDefault=*/false);
        String result = Settings.Config.getStrings(NAMESPACE1, Arrays.asList(KEY1)).get(KEY1);
        assertEquals("Value read from Settings.Config  API does not match written value.", VALUE1,
                result);
        result = Settings.Config.getStrings(NAMESPACE2, Arrays.asList(KEY1)).get(KEY1);
        assertEquals("Value read from Settings.Config API does not match written value.", VALUE2,
                result);
    }

    /**
     * Checks that saving value twice keeps the last value.
     */
    @Test
    public void testSetAndGetString_overrideValue() {
        Settings.Config.putString(NAMESPACE1, KEY1, VALUE1, /*makeDefault=*/false);
        Settings.Config.putString(NAMESPACE1, KEY1, VALUE2, /*makeDefault=*/false);
        String result = Settings.Config.getStrings(NAMESPACE1, Arrays.asList(KEY1)).get(KEY1);
        assertEquals("New value written to the same namespace/key did not override previous"
                + " value.", VALUE2, result);
    }

    /**
     * Checks that putString() fails with NullPointerException when called with null namespace.
     */
    @Test
    public void testPutString_nullNamespace() {
        try {
            Settings.Config.putString(null, KEY1, DEFAULT_VALUE, /*makeDefault=*/false);
            fail("Settings.Config.putString() with null namespace must result in "
                    + "NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }
    }

    /**
     * Checks that putString() fails with NullPointerException when called with null name.
     */
    @Test
    public void testPutString_nullName() {
        try {
            Settings.Config.putString(NAMESPACE1, null, DEFAULT_VALUE, /*makeDefault=*/false);
            fail("Settings.Config.putString() with null name must result in NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }
    }

    /**
     * Checks that setting and getting strings from the same namespace return correct values.
     */
    @Test
    public void testSetAndGetStrings_sameNamespace() throws Exception {
        assertNull(Settings.Config.getStrings(NAMESPACE1, Arrays.asList(KEY1)).get(KEY1));
        assertNull(Settings.Config.getStrings(NAMESPACE1, Arrays.asList(KEY1)).get(KEY2));
        Settings.Config.setStrings(NAMESPACE1, new HashMap<String, String>() {{
                put(KEY1, VALUE1);
                put(KEY2, VALUE2);
            }});

        assertEquals(VALUE1, Settings.Config.getStrings(NAMESPACE1, Arrays.asList(KEY1)).get(KEY1));
        assertEquals(VALUE2, Settings.Config.getStrings(NAMESPACE1, Arrays.asList(KEY2)).get(KEY2));
    }

    /**
     * Checks that setting strings in one namespace does not set the same strings in a
     * different namespace.
     */
    @Test
    public void testSetAndGetStrings_differentNamespace() throws Exception {
        Settings.Config.setStrings(NAMESPACE1, new HashMap<String, String>() {{
                put(KEY1, VALUE1);
                put(KEY2, VALUE2);
            }});

        assertNull(Settings.Config.getStrings(NAMESPACE2, Arrays.asList(KEY1)).get(KEY1));
        assertNull(Settings.Config.getStrings(NAMESPACE2, Arrays.asList(KEY2)).get(KEY2));
    }

    /**
     * Checks that different namespaces can keep different values for the same keys.
     */
    @Test
    public void testSetAndGetStrings_multipleNamespaces() throws Exception {
        Settings.Config.setStrings(NAMESPACE1, new HashMap<String, String>() {{
                put(KEY1, VALUE1);
                put(KEY2, VALUE2);
            }});
        Settings.Config.setStrings(NAMESPACE2, new HashMap<String, String>() {{
                put(KEY1, VALUE3);
                put(KEY2, VALUE4);
            }});

        Map<String, String> namespace1Values = Settings.Config
                .getStrings(NAMESPACE1, Arrays.asList(KEY1, KEY2));
        Map<String, String> namespace2Values = Settings.Config
                .getStrings(NAMESPACE2, Arrays.asList(KEY1, KEY2));

        assertEquals(namespace1Values.toString(), VALUE1, namespace1Values.get(KEY1));
        assertEquals(namespace1Values.toString(), VALUE2, namespace1Values.get(KEY2));
        assertEquals(namespace2Values.toString(), VALUE3, namespace2Values.get(KEY1));
        assertEquals(namespace2Values.toString(), VALUE4, namespace2Values.get(KEY2));
    }


    /**
     * Checks that saving values twice keeps the last values.
     */
    @Test
    public void testSetAndGetStrings_overrideValue() throws Exception {
        Settings.Config.setStrings(NAMESPACE1, new HashMap<String, String>() {{
                put(KEY1, VALUE1);
                put(KEY2, VALUE2);
            }});

        Settings.Config.setStrings(NAMESPACE1, new HashMap<String, String>() {{
                put(KEY1, VALUE3);
                put(KEY2, VALUE4);
            }});

        assertEquals(VALUE3, Settings.Config.getStrings(NAMESPACE1, Arrays.asList(KEY1)).get(KEY1));
        assertEquals(VALUE4, Settings.Config.getStrings(NAMESPACE1, Arrays.asList(KEY2)).get(KEY2));
    }


    /**
     * Checks that deleteString() fails with NullPointerException when called with null namespace.
     */
    @Test
    public void testDeleteString_nullKey() {
        try {
            Settings.Config.deleteString(null, KEY1);
            fail("Settings.Config.deleteString() with null namespace must result in "
                    + "NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }
    }

    /**
     * Checks that deleteString() fails with NullPointerException when called with null key.
     */
    @Test
    public void testDeleteString_nullNamespace() {
        try {
            Settings.Config.deleteString(NAMESPACE1, null);
            fail("Settings.Config.deleteString() with null key must result in "
                    + "NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }
    }

    /**
     * Checks delete string.
     */
    @Test
    public void testDeleteString() {
        Settings.Config.putString(NAMESPACE1, KEY1, VALUE1, /*makeDefault=*/false);
        assertEquals(VALUE1, Settings.Config.getStrings(NAMESPACE1, Arrays.asList(KEY1)).get(KEY1));

        Settings.Config.deleteString(NAMESPACE1, KEY1);
        assertNull(Settings.Config.getStrings(NAMESPACE1, Arrays.asList(KEY1)).get(KEY1));
    }


    /**
     * Test that reset to package default successfully resets values.
     */
    @Test
    public void testResetToPackageDefaults() {
        Settings.Config.putString(NAMESPACE1, KEY1, VALUE1, /*makeDefault=*/true);
        Settings.Config.putString(NAMESPACE1, KEY1, VALUE2, /*makeDefault=*/false);

        assertEquals(VALUE2, Settings.Config.getStrings(NAMESPACE1, Arrays.asList(KEY1)).get(KEY1));

        Settings.Config.resetToDefaults(RESET_MODE_PACKAGE_DEFAULTS, NAMESPACE1);

        assertEquals(VALUE1, Settings.Config.getStrings(NAMESPACE1, Arrays.asList(KEY1)).get(KEY1));
    }

    /**
     * Test updating syncDisabledMode.
     */
    @Test
    public void testSetSyncDisabledMode() {
        Settings.Config.setSyncDisabledMode(SYNC_DISABLED_MODE_NONE);
        assertEquals(SYNC_DISABLED_MODE_NONE, Settings.Config.getSyncDisabledMode());
        Settings.Config.setSyncDisabledMode(RESET_MODE_PACKAGE_DEFAULTS);
        assertEquals(RESET_MODE_PACKAGE_DEFAULTS, Settings.Config.getSyncDisabledMode());
    }

    /**
     * Test register content observer.
     */
    @Test
    public void testRegisterContentObserver() {
        final MockContentObserver mco = new MockContentObserver();

        Settings.Config.registerContentObserver(NAMESPACE1, true, mco);
        assertFalse(mco.hadOnChanged());

        Settings.Config.putString(NAMESPACE1, KEY1, VALUE2, /*makeDefault=*/false);
        new PollingCheck() {
            @Override
            protected boolean check() {
                return mco.hadOnChanged();
            }
        }.run();

        mco.reset();
        Settings.Config.unregisterContentObserver(mco);
        assertFalse(mco.hadOnChanged());
        Settings.Config.putString(NAMESPACE1, KEY1, VALUE1, /*makeDefault=*/false);

        assertFalse(mco.hadOnChanged());

        try {
            Settings.Config.registerContentObserver(null, false, mco);
            fail("did not throw Exceptionwhen uri is null.");
        } catch (NullPointerException e) {
            //expected.
        } catch (IllegalArgumentException e) {
            // also expected
        }

        try {
            Settings.Config.registerContentObserver(NAMESPACE1, false, null);
            fail("did not throw Exception when register null content observer.");
        } catch (NullPointerException e) {
            //expected.
        }

        try {
            sContentResolver.unregisterContentObserver(null);
            fail("did not throw NullPointerException when unregister null content observer.");
        } catch (NullPointerException e) {
            //expected.
        }
    }

    /**
     * Test set monitor callback.
     */
    @Test
    public void testSetMonitorCallback() {
        final CountDownLatch latch = new CountDownLatch(2);
        final TestMonitorCallback callback = new TestMonitorCallback(latch);

        Settings.Config.setMonitorCallback(sContentResolver,
                Executors.newSingleThreadExecutor(), callback);
        try {
            Settings.Config.setStrings(NAMESPACE1, new HashMap<String, String>() {{
                    put(KEY1, VALUE1);
                    put(KEY2, VALUE2);
                }});
        } catch (DeviceConfig.BadConfigException e) {
            fail("Callback set strings" + e.toString());
        }
        // Reading properties triggers the monitor callback function.
        Settings.Config.getStrings(NAMESPACE1, Arrays.asList(KEY1));

        try {
            if (!latch.await(OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                fail("Callback function was not called");
            }
        } catch (InterruptedException e) {
            // this part is executed when an exception (in this example InterruptedException) occurs
            fail("Callback function was not called due to interruption" + e.toString());
        }
        assertEquals(callback.onNamespaceUpdateCalls, 1);
        assertEquals(callback.onDeviceConfigAccessCalls, 1);
    }

    /**
     * Test clear monitor callback.
     */
    @Test
    public void testClearMonitorCallback() {
        final CountDownLatch latch = new CountDownLatch(2);
        final TestMonitorCallback callback = new TestMonitorCallback(latch);

        Settings.Config.setMonitorCallback(sContentResolver,
                Executors.newSingleThreadExecutor(), callback);
        Settings.Config.clearMonitorCallback(sContentResolver);
        // Reading properties triggers the monitor callback function.
        Settings.Config.getStrings(NAMESPACE1, Arrays.asList(KEY1));
        try {
            Settings.Config.setStrings(NAMESPACE1, new HashMap<String, String>() {{
                    put(KEY1, VALUE1);
                    put(KEY2, VALUE2);
                }});
        } catch (DeviceConfig.BadConfigException e) {
            fail("Callback set strings" + e.toString());
        }

        try {
            if (latch.await(OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                fail("Callback function was called while it has been cleared");
            }
        } catch (InterruptedException e) {
            // this part is executed when an exception (in this example InterruptedException) occurs
            fail("un expected interruption occur" + e.toString());
        }
        assertEquals(callback.onNamespaceUpdateCalls, 0);
        assertEquals(callback.onDeviceConfigAccessCalls, 0);
    }

    private class TestMonitorCallback implements DeviceConfig.MonitorCallback {
        public int onNamespaceUpdateCalls = 0;
        public int onDeviceConfigAccessCalls = 0;
        public CountDownLatch latch;

        TestMonitorCallback(CountDownLatch latch) {
            this.latch = latch;
        }

        public void onNamespaceUpdate(@NonNull String updatedNamespace) {
            onNamespaceUpdateCalls++;
            latch.countDown();
        }

        public void onDeviceConfigAccess(@NonNull String callingPackage,
                @NonNull String namespace) {
            onDeviceConfigAccessCalls++;
            latch.countDown();
        }
    }

    private static void deleteProperty(String namespace, String key) {
        Settings.Config.deleteString(namespace, key);
    }

    private static void deleteProperties(String namespace, List<String> keys) {
        HashMap<String, String> deletedKeys = new HashMap<String, String>();
        for (String key : keys) {
            deletedKeys.put(key, null);
        }

        try {
            Settings.Config.setStrings(namespace, deletedKeys);
        } catch (DeviceConfig.BadConfigException e) {
            fail("Failed to delete the properties " + e.toString());
        }
    }

    private static class MockContentObserver extends ContentObserver {
        private boolean mHadOnChanged = false;
        private List<Change> mChanges = new ArrayList<>();

        MockContentObserver() {
            super(null);
        }

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public synchronized void onChange(boolean selfChange, Collection<Uri> uris, int flags) {
            doOnChangeLocked(selfChange, uris, flags, /*userId=*/ -1);
        }

        @Override
        public synchronized void onChange(boolean selfChange, @NonNull Collection<Uri> uris,
                @ContentResolver.NotifyFlags int flags, UserHandle user) {
            doOnChangeLocked(selfChange, uris, flags, user.getIdentifier());
        }

        public synchronized boolean hadOnChanged() {
            return mHadOnChanged;
        }

        public synchronized void reset() {
            mHadOnChanged = false;
        }

        public synchronized boolean hadChanges(Collection<Change> changes) {
            return mChanges.containsAll(changes);
        }

        @GuardedBy("this")
        private void doOnChangeLocked(boolean selfChange, @NonNull Collection<Uri> uris,
                @ContentResolver.NotifyFlags int flags, @UserIdInt int userId) {
            final Change change = new Change(selfChange, uris, flags, userId);
            Log.v(TAG, change.toString());

            mHadOnChanged = true;
            mChanges.add(change);
        }
    }

    public static class Change {
        public final boolean selfChange;
        public final Iterable<Uri> uris;
        public final int flags;
        @UserIdInt
        public final int userId;

        public Change(boolean selfChange, Iterable<Uri> uris, int flags) {
            this.selfChange = selfChange;
            this.uris = uris;
            this.flags = flags;
            this.userId = -1;
        }

        public Change(boolean selfChange, Iterable<Uri> uris, int flags, @UserIdInt int userId) {
            this.selfChange = selfChange;
            this.uris = uris;
            this.flags = flags;
            this.userId = userId;
        }

        @Override
        public String toString() {
            return String.format("onChange(%b, %s, %d, %d)",
                    selfChange, asSet(uris).toString(), flags, userId);
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof Change) {
                final Change change = (Change) other;
                return change.selfChange == selfChange
                        && Objects.equals(asSet(change.uris), asSet(uris))
                        && change.flags == flags
                        && change.userId == userId;
            } else {
                return false;
            }
        }

        private static Set<Uri> asSet(Iterable<Uri> uris) {
            final Set<Uri> asSet = new HashSet<>();
            uris.forEach(asSet::add);
            return asSet;
        }
    }
}
