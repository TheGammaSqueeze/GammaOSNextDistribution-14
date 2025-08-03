/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.car.toast;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ITransientNotificationCallback;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.Display;
import android.view.Gravity;
import android.view.View;

import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.toast.SystemUIToast;
import com.android.systemui.toast.ToastFactory;
import com.android.systemui.toast.ToastLogger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class CarToastUITest extends SysuiTestCase {
    private static final int UID = 0;
    private static final int DURATION = 1000;
    private static final String PACKAGE_NAME = "PACKAGE_NAME";
    private static final CharSequence TEXT = "TEXT";

    private CarToastUI mCarToastUI;

    @Mock
    private UserTracker mUserTracker;
    @Mock
    private CommandQueue mCommandQueue;
    @Mock
    private ToastFactory mToastFactory;
    @Mock
    private ToastLogger mToastLogger;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private IBinder mIBinder;
    @Mock
    private ITransientNotificationCallback mITransientNotificationCallback;
    @Mock
    private SystemUIToast mSystemUIToast;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mCarToastUI = new CarToastUI(mContext,
                mContext.getOrCreateTestableResources().getResources(), mCommandQueue,
                mToastFactory, mToastLogger, mUserTracker, mPackageManager);
        View view = new View(mContext);
        when(mSystemUIToast.hasCustomAnimation()).thenReturn(false);
        when(mSystemUIToast.getView()).thenReturn(view);
        when(mSystemUIToast.getGravity()).thenReturn(Gravity.BOTTOM);
        when(mSystemUIToast.getXOffset()).thenReturn(0);
        when(mSystemUIToast.getYOffset()).thenReturn(0);
        when(mSystemUIToast.getHorizontalMargin()).thenReturn(0);
        when(mSystemUIToast.getVerticalMargin()).thenReturn(0);
        when(mToastFactory.createToast(any(), eq(TEXT), eq(PACKAGE_NAME), anyInt(), anyInt()))
                .thenReturn(mSystemUIToast);
    }

    @After
    public void tearDown() {
        mContext.getOrCreateTestableResources().removeOverride(
                R.array.config_restrictedToastsPackageNameAllowList);
    }


    @Test
    public void showToast_notSystemNotPrivilegedNotPlatformKey_createToastNotCalled()
            throws PackageManager.NameNotFoundException {
        setupPackageInfo(/* isSystem= */ false, /* isPrivileged= */
                false, /* isSignedWithPlatformKey= */ false);

        mCarToastUI.showToast(UID, PACKAGE_NAME, mIBinder, TEXT, mIBinder, DURATION,
                mITransientNotificationCallback, Display.DEFAULT_DISPLAY);

        verify(mToastFactory, never()).createToast(any(), eq(TEXT), eq(PACKAGE_NAME), anyInt(),
                anyInt());
    }

    @Test
    public void showToast_notSystemNotPrivilegedIsPlatformKey_createToastCalled()
            throws PackageManager.NameNotFoundException {
        setupPackageInfo(/* isSystem= */ false, /* isPrivileged= */
                false, /* isSignedWithPlatformKey= */ true);

        mCarToastUI.showToast(UID, PACKAGE_NAME, mIBinder, TEXT, mIBinder, DURATION,
                mITransientNotificationCallback, Display.DEFAULT_DISPLAY);

        verify(mToastFactory).createToast(any(), eq(TEXT), eq(PACKAGE_NAME), anyInt(), anyInt());
    }

    @Test
    public void showToast_notSystemIsPrivilegedNotPlatformKey_createToastNotCalled()
            throws PackageManager.NameNotFoundException {
        setupPackageInfo(/* isSystem= */ false, /* isPrivileged= */
                true, /* isSignedWithPlatformKey= */ false);

        mCarToastUI.showToast(UID, PACKAGE_NAME, mIBinder, TEXT, mIBinder, DURATION,
                mITransientNotificationCallback, Display.DEFAULT_DISPLAY);

        verify(mToastFactory, never()).createToast(any(), eq(TEXT), eq(PACKAGE_NAME), anyInt(),
                anyInt());
    }

    @Test
    public void showToast_notSystemIsPrivilegedIsPlatformKey_createToastCalled()
            throws PackageManager.NameNotFoundException {
        setupPackageInfo(/* isSystem= */ false, /* isPrivileged= */
                true, /* isSignedWithPlatformKey= */ true);

        mCarToastUI.showToast(UID, PACKAGE_NAME, mIBinder, TEXT, mIBinder, DURATION,
                mITransientNotificationCallback, Display.DEFAULT_DISPLAY);

        verify(mToastFactory).createToast(any(), eq(TEXT), eq(PACKAGE_NAME), anyInt(), anyInt());
    }

    @Test
    public void showToast_isSystemNotPrivilegedNotPlatformKey_createToastNotCalled()
            throws PackageManager.NameNotFoundException {
        setupPackageInfo(/* isSystem= */ true, /* isPrivileged= */
                false, /* isSignedWithPlatformKey= */ false);

        mCarToastUI.showToast(UID, PACKAGE_NAME, mIBinder, TEXT, mIBinder, DURATION,
                mITransientNotificationCallback, Display.DEFAULT_DISPLAY);

        verify(mToastFactory, never()).createToast(any(), eq(TEXT), eq(PACKAGE_NAME), anyInt(),
                anyInt());
    }

    @Test
    public void showToast_isSystemNotPrivilegedNotPlatformKeyAllowListed_createToastCalled()
            throws PackageManager.NameNotFoundException {
        mContext.getOrCreateTestableResources().addOverride(
                R.array.config_restrictedToastsPackageNameAllowList,
                new String[]{PACKAGE_NAME});
        CarToastUI carToastUI = new CarToastUI(mContext,
                mContext.getOrCreateTestableResources().getResources(), mCommandQueue,
                mToastFactory, mToastLogger, mUserTracker, mPackageManager);
        setupPackageInfo(/* isSystem= */ true, /* isPrivileged= */
                false, /* isSignedWithPlatformKey= */ false);

        carToastUI.showToast(UID, PACKAGE_NAME, mIBinder, TEXT, mIBinder, DURATION,
                mITransientNotificationCallback, Display.DEFAULT_DISPLAY);

        verify(mToastFactory).createToast(any(), eq(TEXT), eq(PACKAGE_NAME), anyInt(), anyInt());
    }

    @Test
    public void showToast_isSystemNotPrivilegedIsPlatformKey_createToastCalled()
            throws PackageManager.NameNotFoundException {
        setupPackageInfo(/* isSystem= */ true, /* isPrivileged= */
                false, /* isSignedWithPlatformKey= */ true);

        mCarToastUI.showToast(UID, PACKAGE_NAME, mIBinder, TEXT, mIBinder, DURATION,
                mITransientNotificationCallback, Display.DEFAULT_DISPLAY);

        verify(mToastFactory).createToast(any(), eq(TEXT), eq(PACKAGE_NAME), anyInt(), anyInt());
    }

    @Test
    public void showToast_isSystemIsPrivilegedNotPlatformKey_createToastCalled()
            throws PackageManager.NameNotFoundException {
        setupPackageInfo(/* isSystem= */ true, /* isPrivileged= */
                true, /* isSignedWithPlatformKey= */ false);

        mCarToastUI.showToast(UID, PACKAGE_NAME, mIBinder, TEXT, mIBinder, DURATION,
                mITransientNotificationCallback, Display.DEFAULT_DISPLAY);

        verify(mToastFactory).createToast(any(), eq(TEXT), eq(PACKAGE_NAME), anyInt(), anyInt());
    }

    @Test
    public void showToast_isSystemIsPrivilegedIsPlatformKey_createToastCalled()
            throws PackageManager.NameNotFoundException {
        setupPackageInfo(/* isSystem= */ true, /* isPrivileged= */
                true, /* isSignedWithPlatformKey= */ true);

        mCarToastUI.showToast(UID, PACKAGE_NAME, mIBinder, TEXT, mIBinder, DURATION,
                mITransientNotificationCallback, Display.DEFAULT_DISPLAY);

        verify(mToastFactory).createToast(any(), eq(TEXT), eq(PACKAGE_NAME), anyInt(), anyInt());
    }

    private void setupPackageInfo(boolean isSystem, boolean isPrivileged,
            boolean isSignedWithPlatformKey)
            throws PackageManager.NameNotFoundException {
        ApplicationInfo applicationInfo = mock(ApplicationInfo.class);
        when(applicationInfo.isPrivilegedApp()).thenReturn(isPrivileged);
        when(applicationInfo.isSystemApp()).thenReturn(isSystem);
        when(applicationInfo.isSignedWithPlatformKey()).thenReturn(isSignedWithPlatformKey);
        when(mPackageManager.getApplicationInfoAsUser(eq(PACKAGE_NAME), anyInt(), anyInt()))
                .thenReturn(applicationInfo);
    }
}
