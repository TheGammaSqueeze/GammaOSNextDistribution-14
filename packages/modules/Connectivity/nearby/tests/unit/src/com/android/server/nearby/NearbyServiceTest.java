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

package com.android.server.nearby;

import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.Manifest.permission.READ_DEVICE_CONFIG;
import static android.Manifest.permission.WRITE_DEVICE_CONFIG;

import static com.android.server.nearby.NearbyConfiguration.NEARBY_SUPPORT_TEST_APP;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.app.AppOpsManager;
import android.app.UiAutomation;
import android.content.Context;
import android.nearby.IScanListener;
import android.nearby.ScanRequest;
import android.os.IBinder;
import android.provider.DeviceConfig;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.nearby.injector.Injector;
import com.android.server.nearby.util.permissions.DiscoveryPermissions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class NearbyServiceTest {

    private static final String NAMESPACE = NearbyConfiguration.getNamespace();
    private static final String PACKAGE_NAME = "android.nearby.test";
    private Context mContext;
    private NearbyService mService;
    private ScanRequest mScanRequest;
    private UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    @Mock
    private IScanListener mScanListener;
    @Mock
    private AppOpsManager mMockAppOpsManager;
    @Mock
    private IBinder mIBinder;

    @Before
    public void setUp()  {
        initMocks(this);
        when(mScanListener.asBinder()).thenReturn(mIBinder);

        mUiAutomation.adoptShellPermissionIdentity(
                READ_DEVICE_CONFIG, WRITE_DEVICE_CONFIG, BLUETOOTH_PRIVILEGED);
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mService = new NearbyService(mContext);
        mScanRequest = createScanRequest();
    }

    @After
    public void tearDown() {
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void test_register() {
        setMockInjector(/* isMockOpsAllowed= */ true);
        mService.registerScanListener(mScanRequest, mScanListener, PACKAGE_NAME,
                /* attributionTag= */ null);
    }

    @Test
    public void test_register_noPrivilegedPermission_throwsException() {
        DeviceConfig.setProperty(NAMESPACE, NEARBY_SUPPORT_TEST_APP,
                "false", false);
        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(java.lang.SecurityException.class,
                () -> mService.registerScanListener(mScanRequest, mScanListener, PACKAGE_NAME,
                        /* attributionTag= */ null));
    }

    @Test
    public void test_unregister_noPrivilegedPermission_throwsException() {
        DeviceConfig.setProperty(NAMESPACE, NEARBY_SUPPORT_TEST_APP,
                "false", false);
        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(java.lang.SecurityException.class,
                () -> mService.unregisterScanListener(mScanListener, PACKAGE_NAME,
                        /* attributionTag= */ null));
    }

    @Test
    public void test_unregister() {
        setMockInjector(/* isMockOpsAllowed= */ true);
        mService.registerScanListener(mScanRequest, mScanListener, PACKAGE_NAME,
                /* attributionTag= */ null);
        mService.unregisterScanListener(mScanListener,  PACKAGE_NAME, /* attributionTag= */ null);
    }

    private ScanRequest createScanRequest() {
        return new ScanRequest.Builder()
                .setScanType(ScanRequest.SCAN_TYPE_FAST_PAIR)
                .setBleEnabled(true)
                .build();
    }

    private void setMockInjector(boolean isMockOpsAllowed) {
        Injector injector = mock(Injector.class);
        when(injector.getAppOpsManager()).thenReturn(mMockAppOpsManager);
        when(mMockAppOpsManager.noteOp(eq(DiscoveryPermissions.OPSTR_BLUETOOTH_SCAN),
                anyInt(), eq(PACKAGE_NAME), nullable(String.class), nullable(String.class)))
                .thenReturn(isMockOpsAllowed
                        ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_ERRORED);
        mService.setInjector(injector);
    }
}
