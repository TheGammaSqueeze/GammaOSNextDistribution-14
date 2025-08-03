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

import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_DISABLE_OUTGOING_CALLS;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_PACKAGE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.os.Bundle;
import android.os.UserManager;

import com.android.devicelockcontroller.storage.SetupParametersClient;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

@RunWith(RobolectricTestRunner.class)
public final class UserRestrictionsPolicyHandlerTest {
    private static final String TEST_PACKAGE = "test.package1";
    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Mock
    private DevicePolicyManager mMockDpm;
    @Mock
    private UserManager mMockUserManager;
    @Captor
    private ArgumentCaptor<String> mSetUserRestrictionCaptor;
    @Captor
    private ArgumentCaptor<String> mClearUserRestrictionCaptor;

    @Test
    public void onProvisionInProgressDebug_withoutKioskPackageName_shouldThrowException() {
        Bundle userRestrictions = new Bundle();
        when(mMockUserManager.getUserRestrictions()).thenReturn(userRestrictions);

        UserRestrictionsPolicyHandler handler = new UserRestrictionsPolicyHandler(mMockDpm,
                mMockUserManager,
                /* isDebug =*/ true,
                Executors.newSingleThreadExecutor());

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> handler.onProvisionInProgress().get());

        assertThat(thrown).hasCauseThat().isInstanceOf(IllegalStateException.class);
        assertThat(thrown).hasMessageThat().contains("Setup parameters does not exist!");
    }

    @Test
    public void onProvisionInProgress_withoutKioskPackageName_shouldThrowException() {
        Bundle userRestrictions = new Bundle();
        when(mMockUserManager.getUserRestrictions()).thenReturn(userRestrictions);
        UserRestrictionsPolicyHandler handler = new UserRestrictionsPolicyHandler(mMockDpm,
                mMockUserManager,
                /* isDebug =*/ false,
                Executors.newSingleThreadExecutor());

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> handler.onProvisionInProgress().get());

        assertThat(thrown).hasCauseThat().isInstanceOf(IllegalStateException.class);
        assertThat(thrown).hasMessageThat().contains("Setup parameters does not exist!");
    }

    @Test
    public void onProvisionInProgressDebug_withSetExpectedUserRestrictions()
            throws ExecutionException, InterruptedException {
        Bundle preferences = new Bundle();
        preferences.putString(EXTRA_KIOSK_PACKAGE, TEST_PACKAGE);
        SetupParametersClient.getInstance().createPrefs(preferences).get();

        Bundle userRestrictions = new Bundle();
        when(mMockUserManager.getUserRestrictions()).thenReturn(userRestrictions);
        UserRestrictionsPolicyHandler handler = new UserRestrictionsPolicyHandler(mMockDpm,
                mMockUserManager,
                /* isDebug =*/ true,
                Executors.newSingleThreadExecutor());

        handler.onProvisionInProgress().get();

        verify(mMockDpm).addUserRestriction(eq(null), mSetUserRestrictionCaptor.capture());
        List<String> allUserRestrictions = mSetUserRestrictionCaptor.getAllValues();
        assertThat(allUserRestrictions).containsExactlyElementsIn(
                new String[]{UserManager.DISALLOW_SAFE_BOOT});
        verify(mMockDpm, never()).clearUserRestriction(eq(null),
                mClearUserRestrictionCaptor.capture());
    }

    @Test
    public void onProvisionInProgress_withSetExpectedUserRestrictions()
            throws ExecutionException, InterruptedException {
        Bundle preferences = new Bundle();
        preferences.putString(EXTRA_KIOSK_PACKAGE, TEST_PACKAGE);
        SetupParametersClient.getInstance().createPrefs(preferences).get();

        Bundle userRestrictions = new Bundle();
        when(mMockUserManager.getUserRestrictions()).thenReturn(userRestrictions);
        UserRestrictionsPolicyHandler handler = new UserRestrictionsPolicyHandler(mMockDpm,
                mMockUserManager,
                /* isDebug =*/ false,
                Executors.newSingleThreadExecutor());

        handler.onProvisionInProgress().get();

        verify(mMockDpm, times(2)).addUserRestriction(eq(null),
                mSetUserRestrictionCaptor.capture());
        List<String> allUserRestrictions = mSetUserRestrictionCaptor.getAllValues();
        assertThat(allUserRestrictions).containsExactlyElementsIn(
                new String[]{UserManager.DISALLOW_SAFE_BOOT,
                        UserManager.DISALLOW_DEBUGGING_FEATURES});
    }

    @Test
    public void onProvisionInProgress_withDisableOutgoingCallShouldSetExpectedUserRestrictions()
            throws ExecutionException, InterruptedException {
        Bundle preferences = new Bundle();
        preferences.putString(EXTRA_KIOSK_PACKAGE, TEST_PACKAGE);
        preferences.putBoolean(EXTRA_KIOSK_DISABLE_OUTGOING_CALLS, true);
        SetupParametersClient.getInstance().createPrefs(preferences).get();

        Bundle userRestrictions = new Bundle();
        when(mMockUserManager.getUserRestrictions()).thenReturn(userRestrictions);
        UserRestrictionsPolicyHandler handler = new UserRestrictionsPolicyHandler(mMockDpm,
                mMockUserManager,
                /* isDebug =*/ false,
                Executors.newSingleThreadExecutor());

        handler.onProvisionInProgress().get();

        verify(mMockDpm, times(2)).addUserRestriction(eq(null),
                mSetUserRestrictionCaptor.capture());
        List<String> allUserRestrictions = mSetUserRestrictionCaptor.getAllValues();
        assertThat(allUserRestrictions).containsExactlyElementsIn(
                new String[]{UserManager.DISALLOW_SAFE_BOOT,
                        UserManager.DISALLOW_DEBUGGING_FEATURES});
    }

    @Test
    public void onProvisionInProgress_withDisallowUnknownSourcesShouldSetExpectedUserRestrictions()
            throws ExecutionException, InterruptedException {
        Bundle preferences = new Bundle();
        preferences.putString(EXTRA_KIOSK_PACKAGE, TEST_PACKAGE);
        preferences.putBoolean(EXTRA_DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES, true);
        SetupParametersClient.getInstance().createPrefs(preferences).get();

        Bundle userRestrictions = new Bundle();
        when(mMockUserManager.getUserRestrictions()).thenReturn(userRestrictions);
        UserRestrictionsPolicyHandler handler = new UserRestrictionsPolicyHandler(mMockDpm,
                mMockUserManager,
                /* isDebug =*/ false,
                Executors.newSingleThreadExecutor());

        handler.onProvisionInProgress().get();

        verify(mMockDpm, times(3)).addUserRestriction(eq(null),
                mSetUserRestrictionCaptor.capture());
        List<String> allUserRestrictions = mSetUserRestrictionCaptor.getAllValues();
        assertThat(allUserRestrictions).containsExactlyElementsIn(
                new String[]{UserManager.DISALLOW_SAFE_BOOT,
                        UserManager.DISALLOW_DEBUGGING_FEATURES,
                        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES});
    }

    @Test
    public void onLockedDebug_withDisableOutgoingCallShouldSetExpectedUserRestrictions()
            throws ExecutionException, InterruptedException {
        Bundle preferences = new Bundle();
        preferences.putString(EXTRA_KIOSK_PACKAGE, TEST_PACKAGE);
        preferences.putBoolean(EXTRA_KIOSK_DISABLE_OUTGOING_CALLS, true);
        SetupParametersClient.getInstance().createPrefs(preferences).get();

        Bundle userRestrictions = new Bundle();
        when(mMockUserManager.getUserRestrictions()).thenReturn(userRestrictions);
        UserRestrictionsPolicyHandler handler = new UserRestrictionsPolicyHandler(mMockDpm,
                mMockUserManager,
                /* isDebug =*/ true,
                Executors.newSingleThreadExecutor());

        handler.onLocked().get();

        verify(mMockDpm, times(2)).addUserRestriction(eq(null),
                mSetUserRestrictionCaptor.capture());
        List<String> allUserRestrictions = mSetUserRestrictionCaptor.getAllValues();
        assertThat(allUserRestrictions).containsExactlyElementsIn(
                new String[]{UserManager.DISALLOW_SAFE_BOOT,
                        UserManager.DISALLOW_OUTGOING_CALLS});
        verify(mMockDpm, never()).clearUserRestriction(eq(null),
                mClearUserRestrictionCaptor.capture());
    }

    @Test
    public void onLocked_withDisableOutgoingCallShouldSetExpectedUserRestrictions()
            throws ExecutionException, InterruptedException {
        Bundle preferences = new Bundle();
        preferences.putString(EXTRA_KIOSK_PACKAGE, TEST_PACKAGE);
        preferences.putBoolean(EXTRA_KIOSK_DISABLE_OUTGOING_CALLS, true);
        SetupParametersClient.getInstance().createPrefs(preferences).get();

        Bundle userRestrictions = new Bundle();
        when(mMockUserManager.getUserRestrictions()).thenReturn(userRestrictions);
        UserRestrictionsPolicyHandler handler = new UserRestrictionsPolicyHandler(mMockDpm,
                mMockUserManager,
                /* isDebug =*/ false,
                Executors.newSingleThreadExecutor());

        handler.onLocked().get();

        verify(mMockDpm, times(3)).addUserRestriction(eq(null),
                mSetUserRestrictionCaptor.capture());
        List<String> allUserRestrictions = mSetUserRestrictionCaptor.getAllValues();
        assertThat(allUserRestrictions).containsExactlyElementsIn(
                new String[]{UserManager.DISALLOW_SAFE_BOOT,
                        UserManager.DISALLOW_DEBUGGING_FEATURES,
                        UserManager.DISALLOW_OUTGOING_CALLS});
    }

    @Test
    public void onLocked_withDisableOutgoingCallAndUnknownSourcesShouldSetExpectedUserRestrictions()
            throws ExecutionException, InterruptedException {
        setupSetupParameters();

        Bundle userRestrictions = new Bundle();
        when(mMockUserManager.getUserRestrictions()).thenReturn(userRestrictions);
        UserRestrictionsPolicyHandler handler = new UserRestrictionsPolicyHandler(mMockDpm,
                mMockUserManager,
                /* isDebug =*/ false,
                Executors.newSingleThreadExecutor());

        handler.onLocked().get();

        verify(mMockDpm, times(4)).addUserRestriction(eq(null),
                mSetUserRestrictionCaptor.capture());
        List<String> allUserRestrictions = mSetUserRestrictionCaptor.getAllValues();
        assertThat(allUserRestrictions).containsExactlyElementsIn(
                new String[]{UserManager.DISALLOW_SAFE_BOOT,
                        UserManager.DISALLOW_DEBUGGING_FEATURES,
                        UserManager.DISALLOW_OUTGOING_CALLS,
                        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES});
    }

    @Test
    public void onCleared_shouldClearExpectedUserRestrictions()
            throws ExecutionException, InterruptedException {
        setupSetupParameters();

        Bundle userRestrictions = new Bundle();
        when(mMockUserManager.getUserRestrictions()).thenReturn(userRestrictions);
        UserRestrictionsPolicyHandler handler = new UserRestrictionsPolicyHandler(mMockDpm,
                mMockUserManager,
                /* isDebug =*/ false,
                Executors.newSingleThreadExecutor());

        handler.onLocked().get();

        verify(mMockDpm, times(4)).addUserRestriction(eq(null),
                mSetUserRestrictionCaptor.capture());
        List<String> allUserRestrictions = mSetUserRestrictionCaptor.getAllValues();
        assertThat(allUserRestrictions).containsExactlyElementsIn(
                new String[]{UserManager.DISALLOW_SAFE_BOOT,
                        UserManager.DISALLOW_DEBUGGING_FEATURES,
                        UserManager.DISALLOW_OUTGOING_CALLS,
                        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES});

        userRestrictions.putBoolean(UserManager.DISALLOW_SAFE_BOOT, true);
        userRestrictions.putBoolean(UserManager.DISALLOW_DEBUGGING_FEATURES, true);
        userRestrictions.putBoolean(UserManager.DISALLOW_OUTGOING_CALLS, true);
        userRestrictions.putBoolean(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, true);
        reset(mMockDpm);

        handler.onCleared().get();

        verify(mMockDpm, times(4)).clearUserRestriction(eq(null),
                mClearUserRestrictionCaptor.capture());
        List<String> allClearRestrictions = mClearUserRestrictionCaptor.getAllValues();
        assertThat(allClearRestrictions).containsExactlyElementsIn(
                new String[]{UserManager.DISALLOW_SAFE_BOOT,
                        UserManager.DISALLOW_DEBUGGING_FEATURES,
                        UserManager.DISALLOW_OUTGOING_CALLS,
                        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES});
    }

    @Test
    public void onClearedDebug_shouldClearExpectedUserRestrictions()
            throws ExecutionException, InterruptedException {
        setupSetupParameters();

        Bundle userRestrictions = new Bundle();
        when(mMockUserManager.getUserRestrictions()).thenReturn(userRestrictions);
        UserRestrictionsPolicyHandler handler = new UserRestrictionsPolicyHandler(mMockDpm,
                mMockUserManager,
                /* isDebug =*/ true,
                Executors.newSingleThreadExecutor());

        handler.onLocked().get();
        verify(mMockDpm, times(3)).addUserRestriction(eq(null),
                mSetUserRestrictionCaptor.capture());
        List<String> allUserRestrictions = mSetUserRestrictionCaptor.getAllValues();
        assertThat(allUserRestrictions).containsExactlyElementsIn(
                new String[]{UserManager.DISALLOW_SAFE_BOOT,
                        UserManager.DISALLOW_OUTGOING_CALLS,
                        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES});
        verify(mMockDpm, never()).clearUserRestriction(eq(null),
                mClearUserRestrictionCaptor.capture());

        userRestrictions.putBoolean(UserManager.DISALLOW_SAFE_BOOT, true);
        userRestrictions.putBoolean(UserManager.DISALLOW_OUTGOING_CALLS, true);
        userRestrictions.putBoolean(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, true);
        reset(mMockDpm);

        handler.onCleared().get();

        verify(mMockDpm, times(3)).clearUserRestriction(eq(null),
                mClearUserRestrictionCaptor.capture());
        List<String> allClearRestrictions = mClearUserRestrictionCaptor.getAllValues();
        assertThat(allClearRestrictions).containsExactlyElementsIn(
                new String[]{UserManager.DISALLOW_SAFE_BOOT,
                        UserManager.DISALLOW_OUTGOING_CALLS,
                        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES});
    }

    @Test
    public void onUnlockedDebug_withDisableOutgoingCallShouldSetExpectedUserRestrictions()
            throws ExecutionException, InterruptedException {
        Bundle preferences = new Bundle();
        preferences.putString(EXTRA_KIOSK_PACKAGE, TEST_PACKAGE);
        preferences.putBoolean(EXTRA_KIOSK_DISABLE_OUTGOING_CALLS, true);
        SetupParametersClient.getInstance().createPrefs(preferences).get();

        Bundle userRestrictions = new Bundle();
        when(mMockUserManager.getUserRestrictions()).thenReturn(userRestrictions);
        UserRestrictionsPolicyHandler handler = new UserRestrictionsPolicyHandler(mMockDpm,
                mMockUserManager,
                /* isDebug =*/ true,
                Executors.newSingleThreadExecutor());

        handler.onUnlocked().get();

        verify(mMockDpm, times(1)).addUserRestriction(eq(null),
                mSetUserRestrictionCaptor.capture());
        List<String> allUserRestrictions = mSetUserRestrictionCaptor.getAllValues();
        assertThat(allUserRestrictions).containsExactlyElementsIn(
                new String[]{UserManager.DISALLOW_SAFE_BOOT});
        verify(mMockDpm, never()).clearUserRestriction(eq(null),
                mClearUserRestrictionCaptor.capture());
    }

    @Test
    public void onUnlocked_withDisableOutgoingCallShouldSetExpectedUserRestrictions()
            throws ExecutionException, InterruptedException {
        Bundle preferences = new Bundle();
        preferences.putString(EXTRA_KIOSK_PACKAGE, TEST_PACKAGE);
        preferences.putBoolean(EXTRA_KIOSK_DISABLE_OUTGOING_CALLS, true);
        SetupParametersClient.getInstance().createPrefs(preferences).get();

        Bundle userRestrictions = new Bundle();
        when(mMockUserManager.getUserRestrictions()).thenReturn(userRestrictions);
        UserRestrictionsPolicyHandler handler = new UserRestrictionsPolicyHandler(mMockDpm,
                mMockUserManager,
                /* isDebug =*/ false,
                Executors.newSingleThreadExecutor());

        handler.onUnlocked().get();

        verify(mMockDpm, times(2)).addUserRestriction(eq(null),
                mSetUserRestrictionCaptor.capture());
        List<String> allUserRestrictions = mSetUserRestrictionCaptor.getAllValues();
        assertThat(allUserRestrictions).containsExactlyElementsIn(
                new String[]{UserManager.DISALLOW_SAFE_BOOT,
                        UserManager.DISALLOW_DEBUGGING_FEATURES});
    }

    @Test
    public void onUnlocked_withDisableOutgoingCallAndUnknownSourcesShouldSetExpectedRestrictions()
            throws ExecutionException, InterruptedException {
        setupSetupParameters();

        Bundle userRestrictions = new Bundle();
        when(mMockUserManager.getUserRestrictions()).thenReturn(userRestrictions);
        UserRestrictionsPolicyHandler handler = new UserRestrictionsPolicyHandler(mMockDpm,
                mMockUserManager,
                /* isDebug =*/ false,
                Executors.newSingleThreadExecutor());

        handler.onUnlocked().get();

        verify(mMockDpm, times(3)).addUserRestriction(eq(null),
                mSetUserRestrictionCaptor.capture());
        List<String> allUserRestrictions = mSetUserRestrictionCaptor.getAllValues();
        assertThat(allUserRestrictions).containsExactlyElementsIn(
                new String[]{UserManager.DISALLOW_SAFE_BOOT,
                        UserManager.DISALLOW_DEBUGGING_FEATURES,
                        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES});
    }

    @Test
    public void onProvisionPaused_shouldDoNothing()
            throws ExecutionException, InterruptedException {
        UserRestrictionsPolicyHandler handler = new UserRestrictionsPolicyHandler(mMockDpm,
                mMockUserManager,
                /* isDebug =*/ false,
                Executors.newSingleThreadExecutor());

        handler.onProvisionPaused().get();

        verifyNoInteractions(mMockDpm);
        verifyNoInteractions(mMockUserManager);
    }

    @Test
    public void onProvisionFailed_shouldDoNothing()
            throws ExecutionException, InterruptedException {
        UserRestrictionsPolicyHandler handler = new UserRestrictionsPolicyHandler(mMockDpm,
                mMockUserManager,
                /* isDebug =*/ false,
                Executors.newSingleThreadExecutor());

        handler.onProvisionFailed().get();

        verifyNoInteractions(mMockDpm);
        verifyNoInteractions(mMockUserManager);
    }

    private static void setupSetupParameters() throws InterruptedException, ExecutionException {
        Bundle preferences = new Bundle();
        preferences.putString(EXTRA_KIOSK_PACKAGE, TEST_PACKAGE);
        preferences.putBoolean(EXTRA_KIOSK_DISABLE_OUTGOING_CALLS, true);
        preferences.putBoolean(EXTRA_DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES, true);
        SetupParametersClient.getInstance().createPrefs(preferences).get();
    }
}
