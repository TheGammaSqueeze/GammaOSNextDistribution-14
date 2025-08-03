/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.security.cts;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.PropertyUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class EncryptionTest {
    static {
        System.loadLibrary("ctssecurity_jni");
    }

    private static final String TAG = "EncryptionTest";

    private static native boolean aesIsFast();

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        // This feature name check only applies to devices that first shipped with
        // SC or later.
        final int firstApiLevel =
                Math.min(PropertyUtil.getFirstApiLevel(), PropertyUtil.getVendorApiLevel());
        if (firstApiLevel >= Build.VERSION_CODES.S) {
            // Assumes every test in this file asserts a requirement of CDD section 9.
            assumeTrue("Skipping test: FEATURE_SECURITY_MODEL_COMPATIBLE missing.",
                    !context.getPackageManager()
                    .hasSystemFeature(PackageManager.FEATURE_SECURITY_MODEL_COMPATIBLE));
        }
    }

    private void handleUnencryptedDevice() {
        // Prior to Android M, encryption wasn't required at all.
        if (PropertyUtil.getFirstApiLevel() < Build.VERSION_CODES.M) {
            Log.d(TAG, "Exempt from encryption due to an old starting API level.");
            return;
        }
        // Prior to Android Q, encryption wasn't required if AES performance is
        // too low or if the device is "typically shared (e.g. Television)".
        if (PropertyUtil.getFirstApiLevel() < Build.VERSION_CODES.Q) {
            if (FeatureUtil.isTV()) {
                Log.d(TAG, "Exempt from encryption because because device is TV.");
                return;
            }
            // Note: aesIsFast() takes ~2 second to run, so it's worth rearranging
            //     test logic to delay calling this.
            if (!aesIsFast()) {
                Log.d(TAG, "Exempt from encryption because AES performance is too low.");
                return;
            }
        }
        fail("Device encryption is required");
    }

    private void handleEncryptedDevice() {
        final String cryptoType = PropertyUtil.getProperty("ro.crypto.type");
        if ("file".equals(cryptoType)) {
            Log.d(TAG, "Device is encrypted with file-based encryption.");
            // Note: this test doesn't check whether the requirements for
            // encryption algorithms are met, since apps don't have a way to
            // query this information.  Instead, it's tested in
            // CtsNativeEncryptionTestCases.
            return;
        }
        if ("managed".equals(cryptoType)) {
            // Android is running in a virtualized environment and the file
            // system is encrypted by the host system.
            Log.d(TAG, "Device encryption is managed by the host system.");
            // Note: All encryption-related CDD requirements still must be met,
            // but they can't be tested directly in this case.
            return;
        }
        // Prior to Android Q, file-based encryption wasn't required
        // (full-disk encryption was also allowed).
        if (PropertyUtil.getFirstApiLevel() < Build.VERSION_CODES.Q) {
            Log.d(TAG, "Device is encrypted.");
            return;
        }
        fail("File-based encryption is required");
    }

    // "getprop", used by PropertyUtil.getProperty(), is not executable
    // to instant apps
    @AppModeFull
    @CddTest(requirement="9.9.2/C-0-1,C-0-2,C-0-3")
    @Test
    public void testEncryption() throws Exception {
        if ("encrypted".equals(PropertyUtil.getProperty("ro.crypto.state"))) {
            handleEncryptedDevice();
        } else {
            handleUnencryptedDevice();
        }
    }
}
