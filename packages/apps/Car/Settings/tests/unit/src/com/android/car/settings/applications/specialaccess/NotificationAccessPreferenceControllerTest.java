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

package com.android.car.settings.applications.specialaccess;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.NotificationManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.UserHandle;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.TwoStatePreference;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.PollingCheck;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class NotificationAccessPreferenceControllerTest {

    private static final String PACKAGE_NAME = "com.android.test.package";
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private PreferenceGroup mPreferenceGroup;
    private NotificationAccessPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private ServiceInfo mListenerServiceInfo;
    private ComponentName mListenerComponent;
    private ApplicationInfo mApplicationInfo;
    private NotificationManager mNotificationManager;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private PackageManager mMockPackageManager;
    @Mock
    private Resources mMockResources;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreferenceGroup);
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mNotificationManager = spy(mContext.getSystemService(NotificationManager.class));
        mPreferenceController = new NotificationAccessPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions, mNotificationManager);

        PreferenceControllerTestUtil
                .assignPreference(mPreferenceController, mPreferenceGroup);

        mListenerServiceInfo = new ServiceInfo();
        mListenerServiceInfo.permission = Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE;
        mListenerServiceInfo.packageName = PACKAGE_NAME;
        mListenerServiceInfo.name = "SomeListenerService";
        mListenerServiceInfo.nonLocalizedLabel = "label";
        mApplicationInfo = new ApplicationInfo();
        mApplicationInfo.uid = 123;
        mApplicationInfo.name = "SomeApplicationInfo";
        mListenerServiceInfo.applicationInfo = mApplicationInfo;

        mListenerComponent = new ComponentName(mListenerServiceInfo.packageName,
                mListenerServiceInfo.name);
    }

    @Test
    public void onStart_loadsListenerServices_preferenceEnabled()
            throws PackageManager.NameNotFoundException {
        setupPreferenceController(/* permissionGranted= */true, mListenerServiceInfo);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        TwoStatePreference preference = (TwoStatePreference) mPreferenceGroup.getPreference(0);
        assertThat(preference.isEnabled()).isTrue();
    }

    @Test
    @UiThreadTest
    public void onStart_loadsFixedPackage_preferenceDisabled()
            throws PackageManager.NameNotFoundException {
        String[] fixedPackages = new String[] {PACKAGE_NAME};
        when(mContext.getResources()).thenReturn(mMockResources);
        when(mMockResources.getStringArray(anyInt()))
                .thenReturn(fixedPackages);
        mPreferenceController = new NotificationAccessPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions, mNotificationManager);

        PreferenceControllerTestUtil
                .assignPreference(mPreferenceController, mPreferenceGroup);

        setupPreferenceController(/* permissionGranted= */ true, mListenerServiceInfo);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        TwoStatePreference preference = (TwoStatePreference) mPreferenceGroup.getPreference(0);
        assertThat(preference.isEnabled()).isFalse();
    }

    @Test
    public void onStart_serviceAccessGranted_setsPreferenceChecked()
            throws PackageManager.NameNotFoundException {
        setupPreferenceController(/* permissionGranted= */true, mListenerServiceInfo);

        TwoStatePreference preference = (TwoStatePreference) mPreferenceGroup.getPreference(0);
        assertThat(preference.isChecked()).isTrue();
    }

    @Test
    public void onStart_serviceAccessNotGranted_setsPreferenceUnchecked()
            throws PackageManager.NameNotFoundException {
        setupPreferenceController(/* permissionGranted= */false, mListenerServiceInfo);

        TwoStatePreference preference = (TwoStatePreference) mPreferenceGroup.getPreference(0);
        assertThat(preference.isChecked()).isFalse();
    }

    @Test
    public void preferenceClicked_serviceAccessGranted_showsRevokeConfirmDialog()
            throws PackageManager.NameNotFoundException {
        setupPreferenceController(/* permissionGranted= */true, mListenerServiceInfo);
        TwoStatePreference preference = (TwoStatePreference) mPreferenceGroup.getPreference(0);

        preference.performClick();

        verify(mMockFragmentController).showDialog(any(
                ConfirmationDialogFragment.class),
                eq(NotificationAccessPreferenceController.REVOKE_CONFIRM_DIALOG_TAG));
    }

    @Test
    public void preferenceClicked_serviceAccessNotGranted_showsGrantConfirmDialog()
            throws PackageManager.NameNotFoundException {
        setupPreferenceController(/* permissionGranted= */false, mListenerServiceInfo);
        TwoStatePreference preference = (TwoStatePreference) mPreferenceGroup.getPreference(0);

        preference.performClick();

        verify(mMockFragmentController).showDialog(any(
                ConfirmationDialogFragment.class),
                eq(NotificationAccessPreferenceController.GRANT_CONFIRM_DIALOG_TAG));
    }

    @Test
    public void revokeConfirmed_revokesNotificationAccess()
            throws PackageManager.NameNotFoundException {
        setupPreferenceController(/* permissionGranted= */true, mListenerServiceInfo);
        TwoStatePreference preference = (TwoStatePreference) mPreferenceGroup.getPreference(0);
        preference.performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(dialogCaptor.capture(),
                eq(NotificationAccessPreferenceController.REVOKE_CONFIRM_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        dialogFragment.onClick(/* dialog= */ null, DialogInterface.BUTTON_POSITIVE);

        assertThat(mNotificationManager.isNotificationListenerAccessGranted(mListenerComponent))
                .isFalse();
    }

    @Test
    public void revokeConfirmed_notificationPolicyAccessNotGranted_removesAutomaticZenRules()
            throws PackageManager.NameNotFoundException {
        setupPreferenceController(/* permissionGranted= */true, mListenerServiceInfo);

        TwoStatePreference preference = (TwoStatePreference) mPreferenceGroup.getPreference(0);
        preference.performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(dialogCaptor.capture(),
                eq(NotificationAccessPreferenceController.REVOKE_CONFIRM_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        mNotificationManager.setNotificationPolicyAccessGranted(
                mListenerServiceInfo.packageName, /* granted= */ false);
        dialogFragment.onClick(/* dialog= */ null, DialogInterface.BUTTON_POSITIVE);

        PollingCheck.waitFor(
                () -> mPreferenceController.mAsyncTask.getStatus() == AsyncTask.Status.FINISHED);

        verify(mNotificationManager).removeAutomaticZenRules(any());
    }

    @Test
    public void grantConfirmed_grantsNotificationAccess()
            throws PackageManager.NameNotFoundException {
        setupPreferenceController(/* permissionGranted= */false, mListenerServiceInfo);
        TwoStatePreference preference = (TwoStatePreference) mPreferenceGroup.getPreference(0);
        preference.performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(dialogCaptor.capture(),
                eq(NotificationAccessPreferenceController.GRANT_CONFIRM_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        dialogFragment.onClick(/* dialog= */ null, DialogInterface.BUTTON_POSITIVE);

        assertThat(mNotificationManager.isNotificationListenerAccessGranted(mListenerComponent))
                .isTrue();
    }

    private void setupPreferenceController(boolean permissionGranted, ServiceInfo serviceInfo)
            throws PackageManager.NameNotFoundException {
        when(mContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager.getApplicationInfoAsUser(mListenerServiceInfo.packageName,
                /* flags= */ 0, UserHandle.myUserId())).thenReturn(mApplicationInfo);

        mNotificationManager.setNotificationListenerAccessGranted(mListenerComponent,
                /* granted= */ permissionGranted);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.onServicesReloaded(Collections.singletonList(serviceInfo));
    }
}
