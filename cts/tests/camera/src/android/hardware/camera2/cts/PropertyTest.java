/*
 * Copyright 2023 The Android Open Source Project
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

package android.hardware.camera2.cts;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.os.SystemProperties;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.TestApp;
import com.android.cts.install.lib.Uninstall;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test AndroidManifest properties.
 */
@RunWith(AndroidJUnit4.class)
public class PropertyTest {
    private static PackageManager sPackageManager;
    private static final String TAG = "PropertyTest";
    private static final String PROPERTY_APP1_PACKAGE_NAME =
            "com.android.camera2.cts.propertytestapp1";
    private static final String PROPERTY_APP2_PACKAGE_NAME =
            "com.android.camera2.cts.propertytestapp2";
    private static final TestApp PROPERTY_APP1 =
            new TestApp("CameraCtsPropertyTestApp1", PROPERTY_APP1_PACKAGE_NAME, 30,
                    false, "CameraCtsPropertyTestApp1.apk");
    private static final TestApp PROPERTY_APP2 =
            new TestApp("CameraCtsPropertyTestApp2", PROPERTY_APP2_PACKAGE_NAME, 30,
                    false, "CameraCtsPropertyTestApp2.apk");

    private CameraManager mCameraManager;
    private Instrumentation mInstrumentation;
    private Context mContext;
    private PackageManager mPackageManager;

    private static void adoptShellPermissions() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.INSTALL_PACKAGES, Manifest.permission.DELETE_PACKAGES);
    }

    private static void dropShellPermissions() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @BeforeClass
    public static void setupClass() throws Exception {
        sPackageManager = InstrumentationRegistry
                .getInstrumentation()
                .getContext()
                .getPackageManager();
        adoptShellPermissions();
        Uninstall.packages(PROPERTY_APP1_PACKAGE_NAME);
        Uninstall.packages(PROPERTY_APP2_PACKAGE_NAME);
        Install.single(PROPERTY_APP1).commit();
        Install.single(PROPERTY_APP2).commit();
        dropShellPermissions();
    }

    @AfterClass
    public static void teardownClass() throws Exception {
        adoptShellPermissions();
        Uninstall.packages(PROPERTY_APP1_PACKAGE_NAME);
        Uninstall.packages(PROPERTY_APP2_PACKAGE_NAME);
        dropShellPermissions();
    }

    @Before
    public void setup() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getContext();
        mPackageManager = mContext.getPackageManager();
    }

    @Test
    @AppModeFull
    public void testLandscapeToPortraitEnabled() throws Exception {
        if (SystemProperties.getBoolean(CameraManager.LANDSCAPE_TO_PORTRAIT_PROP, false)) {
            Log.i(TAG, "System property enabled, testing shouldOverrideToPortrait");
            assertTrue("shouldOverrideToPortrait should return true",
                    CameraManager.shouldOverrideToPortrait(mPackageManager,
                            PROPERTY_APP1_PACKAGE_NAME));
        }
    }

    @Test
    @AppModeFull
    public void testLandscapeToPortraitDisabled() throws Exception {
        if (SystemProperties.getBoolean(CameraManager.LANDSCAPE_TO_PORTRAIT_PROP, false)) {
            Log.i(TAG, "System property enabled, testing shouldOverrideToPortrait");
            assertFalse("shouldOverrideToPortrait should return false",
                    CameraManager.shouldOverrideToPortrait(mPackageManager,
                            PROPERTY_APP2_PACKAGE_NAME));
        }
    }
}
