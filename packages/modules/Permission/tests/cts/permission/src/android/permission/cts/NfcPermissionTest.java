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

package android.permission.cts;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.ControllerAlwaysOnListener;
import android.platform.test.annotations.AppModeFull;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.Executor;

@RunWith(JUnit4.class)
public final class NfcPermissionTest {

    private NfcAdapter mNfcAdapter;
    private static final String PKG_NAME = "com.android.packagename";

    private boolean supportsHardware() {
        final PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_NFC);
    }

    @Before
    public void setUp() {
        assumeTrue(supportsHardware());
        mNfcAdapter = NfcAdapter.getDefaultAdapter(InstrumentationRegistry.getTargetContext());
    }

    /**
     * Verifies that isControllerAlwaysOnSupported() requires Permission.
     * <p>
     * Requires Permission: {@link android.Manifest.permission#NFC_SET_CONTROLLER_ALWAYS_ON}.
     */
    @Test
    @AppModeFull
    public void testIsControllerAlwaysOnSupported() {
        try {
            mNfcAdapter.isControllerAlwaysOnSupported();
            fail("mNfcAdapter.isControllerAlwaysOnSupported() did not throw SecurityException"
                    + " as expected");
        } catch (SecurityException se) {
            // Expected Exception
        }
    }

    /**
     * Verifies that isControllerAlwaysOn() requires Permission.
     * <p>
     * Requires Permission: {@link android.Manifest.permission#NFC_SET_CONTROLLER_ALWAYS_ON}.
     */
    @Test
    @AppModeFull
    public void testIsControllerAlwaysOn() {
        try {
            mNfcAdapter.isControllerAlwaysOn();
            fail("mNfcAdapter.isControllerAlwaysOn() did not throw SecurityException"
                    + " as expected");
        } catch (SecurityException se) {
            // Expected Exception
        }
    }

    /**
     * Verifies that setControllerAlwaysOn(true) requires Permission.
     * <p>
     * Requires Permission: {@link android.Manifest.permission#NFC_SET_CONTROLLER_ALWAYS_ON}.
     */
    @Test
    @AppModeFull
    public void testSetControllerAlwaysOnTrue() {
        try {
            mNfcAdapter.setControllerAlwaysOn(true);
            fail("mNfcAdapter.setControllerAlwaysOn(true) did not throw SecurityException"
                    + " as expected");
        } catch (SecurityException se) {
            // Expected Exception
        }
    }

    /**
     * Verifies that setControllerAlwaysOn(false) requires Permission.
     * <p>
     * Requires Permission: {@link android.Manifest.permission#NFC_SET_CONTROLLER_ALWAYS_ON}.
     */
    @Test
    @AppModeFull
    public void testSetControllerAlwaysOnFalse() {
        try {
            mNfcAdapter.setControllerAlwaysOn(false);
            fail("mNfcAdapter.setControllerAlwaysOn(true) did not throw SecurityException"
                    + " as expected");
        } catch (SecurityException se) {
            // Expected Exception
        }
    }

    /**
     * Verifies that registerControllerAlwaysOnListener() requires Permission.
     * <p>
     * Requires Permission: {@link android.Manifest.permission#NFC_SET_CONTROLLER_ALWAYS_ON}.
     */
    @Test
    @AppModeFull
    public void testRegisterControllerAlwaysOnListener() {
        try {
            mNfcAdapter.registerControllerAlwaysOnListener(
                    new SynchronousExecutor(), new AlwaysOnStateListener());
            fail("mNfcAdapter.registerControllerAlwaysOnListener did not throw"
                    + "SecurityException as expected");
        } catch (SecurityException se) {
            // Expected Exception
        }
    }

    /**
     * Verifies that unregisterControllerAlwaysOnListener() requires Permission.
     * <p>
     * Requires Permission: {@link android.Manifest.permission#NFC_SET_CONTROLLER_ALWAYS_ON}.
     */
    @Test
    @AppModeFull
    public void testUnregisterControllerAlwaysOnListener() {
        try {
            mNfcAdapter.unregisterControllerAlwaysOnListener(new AlwaysOnStateListener());
            fail("mNfcAdapter.unregisterControllerAlwaysOnListener did not throw"
                    + "SecurityException as expected");
        } catch (SecurityException se) {
            // Expected Exception
        }
    }

    /**
     * Verifies that isTagIntentAppPreferenceSupported() requires Permission.
     * <p>
     * Requires Permission: {@link android.Manifest.permission.WRITE_SECURE_SETTINGS}.
     */
    @Test
    @AppModeFull
    public void testIsTagIntentAppPreferenceSupported() {
        try {
            mNfcAdapter.isTagIntentAppPreferenceSupported();
            fail("mNfcAdapter.isTagIntentAppPreferenceSupported() did not throw SecurityException"
                    + " as expected");
        } catch (SecurityException se) {
            // Expected Exception
        }
    }

    /**
     * Verifies that getTagIntentAppPreferenceForUser() requires Permission.
     * <p>
     * Requires Permission: {@link android.Manifest.permission.WRITE_SECURE_SETTINGS}.
     */
    @Test
    @AppModeFull
    public void testGetTagIntentAppPreferenceForUser() {
        try {
            mNfcAdapter.getTagIntentAppPreferenceForUser(ActivityManager.getCurrentUser());
            fail("mNfcAdapter.getTagIntentAppPreferenceForUser() did not throw SecurityException"
                    + " as expected");
        } catch (SecurityException se) {
            // Expected Exception
        }
    }

    /**
     * Verifies that setTagIntentAppPreferenceForUser() requires Permission.
     * <p>
     * Requires Permission: {@link android.Manifest.permission.WRITE_SECURE_SETTINGS}.
     */
    @Test
    @AppModeFull
    public void testSetTagIntentAppPreferenceForUser() {
        try {
            mNfcAdapter.setTagIntentAppPreferenceForUser(ActivityManager.getCurrentUser(),
                    PKG_NAME, true);
            fail("mNfcAdapter.setTagIntentAppPreferenceForUser() did not throw SecurityException"
                    + " as expected");
        } catch (SecurityException se) {
            // Expected Exception
        }
    }

    private class SynchronousExecutor implements Executor {
        public void execute(Runnable r) {
            r.run();
        }
    }

    private class AlwaysOnStateListener implements ControllerAlwaysOnListener {
        @Override
        public void onControllerAlwaysOnChanged(boolean isEnabled) {
            // Do nothing
        }
    }
}
