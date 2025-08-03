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

package com.android.devicelockcontroller.policy;

import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_PACKAGE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.storage.SetupParametersClient;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

@RunWith(RobolectricTestRunner.class)
public final class PackagePolicyHandlerTest {
    private static final String TEST_KIOSK_PACKAGE = "test.package1";
    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Mock
    private DevicePolicyManager mMockDpm;
    @Captor
    private ArgumentCaptor<String> mKioskPackageNameCaptor;
    @Captor
    private ArgumentCaptor<Boolean> mUninstallBlockedCaptor;
    @Captor
    private ArgumentCaptor<List<String>> mUserControlDisabledPackages;

    private Context mContext;

    private PackagePolicyHandler mHandler;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mHandler = new PackagePolicyHandler(mContext, mMockDpm,
                Executors.newSingleThreadExecutor());
    }

    @Test
    public void onProvisioned_withKioskPackageSet_shouldHaveExpectedMethodCalls()
            throws ExecutionException, InterruptedException {
        setupSetupParameters();

        assertThat(mHandler.onProvisioned().get()).isTrue();

        verify(mMockDpm).setUninstallBlocked(eq(null), mKioskPackageNameCaptor.capture(),
                mUninstallBlockedCaptor.capture());
        assertThat(mKioskPackageNameCaptor.getValue()).isEqualTo(TEST_KIOSK_PACKAGE);
        assertThat(mUninstallBlockedCaptor.getValue()).isTrue();

        verify(mMockDpm).setUserControlDisabledPackages(eq(null),
                mUserControlDisabledPackages.capture());
        List<String> userControlDisabledPackages = mUserControlDisabledPackages.getValue();
        assertThat(userControlDisabledPackages).containsAnyIn(
                new String[]{mContext.getPackageName(), TEST_KIOSK_PACKAGE});
    }

    @Test
    public void onProvisioned_withoutKioskPackageSet_shouldHaveExpectedMethodCalls()
            throws ExecutionException, InterruptedException {
        assertThat(mHandler.onProvisioned().get()).isTrue();

        verify(mMockDpm, never()).setUninstallBlocked(eq(null), mKioskPackageNameCaptor.capture(),
                mUninstallBlockedCaptor.capture());

        verify(mMockDpm).setUserControlDisabledPackages(eq(null),
                mUserControlDisabledPackages.capture());
        List<String> userControlDisabledPackages = mUserControlDisabledPackages.getValue();
        assertThat(userControlDisabledPackages).containsAnyIn(
                new String[]{mContext.getPackageName()});
    }

    @Test
    public void onProvisioned_withSecurityException_shouldHaveExpectedMethodCalls()
            throws ExecutionException, InterruptedException {
        doAnswer((Answer<Object>) invocation -> {
            throw new SecurityException();
        }).when(mMockDpm).setUserControlDisabledPackages(any(), any());

        assertThat(mHandler.onProvisioned().get()).isFalse();

        verify(mMockDpm, never()).setUninstallBlocked(eq(null), mKioskPackageNameCaptor.capture(),
                mUninstallBlockedCaptor.capture());
    }

    @Test
    public void onCleared_withKioskPackageSet_shouldHaveExpectedMethodCalls()
            throws ExecutionException, InterruptedException {
        setupSetupParameters();

        assertThat(mHandler.onCleared().get()).isTrue();

        verify(mMockDpm).setUninstallBlocked(eq(null), mKioskPackageNameCaptor.capture(),
                mUninstallBlockedCaptor.capture());
        assertThat(mKioskPackageNameCaptor.getValue()).isEqualTo(TEST_KIOSK_PACKAGE);
        assertThat(mUninstallBlockedCaptor.getValue()).isFalse();

        verify(mMockDpm).setUserControlDisabledPackages(eq(null),
                mUserControlDisabledPackages.capture());
        List<String> userControlDisabledPackages = mUserControlDisabledPackages.getValue();
        assertThat(userControlDisabledPackages).containsAnyIn(
                new String[]{mContext.getPackageName()});
    }

    @Test
    public void onProvisionInProgress_shouldDoNothing()
            throws ExecutionException, InterruptedException {
        assertThat(mHandler.onProvisionInProgress().get()).isTrue();
        verifyNoInteractions(mMockDpm);
    }

    @Test
    public void onProvisionPaused_shouldDoNothing()
            throws ExecutionException, InterruptedException {
        assertThat(mHandler.onProvisionPaused().get()).isTrue();
        verifyNoInteractions(mMockDpm);
    }

    @Test
    public void onProvisionFailed_shouldDoNothing()
            throws ExecutionException, InterruptedException {
        assertThat(mHandler.onProvisionFailed().get()).isTrue();
        verifyNoInteractions(mMockDpm);
    }

    @Test
    public void onLocked_shouldDoNothing() throws ExecutionException, InterruptedException {
        assertThat(mHandler.onLocked().get()).isTrue();
        verifyNoInteractions(mMockDpm);
    }

    @Test
    public void onUnlocked_shouldDoNothing() throws ExecutionException, InterruptedException {
        assertThat(mHandler.onUnlocked().get()).isTrue();
        verifyNoInteractions(mMockDpm);
    }

    private static void setupSetupParameters() throws InterruptedException, ExecutionException {
        Bundle preferences = new Bundle();
        preferences.putString(EXTRA_KIOSK_PACKAGE, TEST_KIOSK_PACKAGE);
        SetupParametersClient.getInstance().createPrefs(preferences).get();
    }
}
