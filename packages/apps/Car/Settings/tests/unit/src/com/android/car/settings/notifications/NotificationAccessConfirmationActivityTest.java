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

package com.android.car.settings.notifications;

import static com.android.internal.notification.NotificationAccessConfirmationActivityContract.EXTRA_COMPONENT_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.car.test.mocks.AndroidMockitoHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;

import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.settings.common.ConfirmationDialogFragment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Locale;

/**
 * Unit tests for {@link NotificationAccessConfirmationActivity}
 */
@RunWith(AndroidJUnit4.class)
public final class NotificationAccessConfirmationActivityTest {
    private static final ComponentName TEMP_COMPONENT_NAME = ComponentName.unflattenFromString(
            "com.temp/com.temp.k");

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private ActivityScenario<TestActivity> mActivityScenario;
    private TestActivity mActivity;
    private PackageManager mPackageManager;

    @Mock
    private NotificationManager mNotificationManager;

    private static ServiceInfo createServiceInfoForTempComponent(String permission) {
        ServiceInfo info = new ServiceInfo();
        info.name = TEMP_COMPONENT_NAME.getClassName();
        info.packageName = TEMP_COMPONENT_NAME.getPackageName();
        info.permission = permission;
        return info;
    }

    private static ApplicationInfo createApplicationInfo() {
        ApplicationInfo info = new ApplicationInfo();
        info.name = TEMP_COMPONENT_NAME.getClassName();
        info.packageName = TEMP_COMPONENT_NAME.getPackageName();
        return info;
    }

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void componentNameEmpty_finishes() {
        Intent intent = new Intent(mContext, TestActivity.class);
        mActivityScenario = ActivityScenario.launch(intent);
        assertThat(mActivityScenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
    }

    @Test
    public void componentNameNonEmpty_showsConfirmationDialog() {
        launchActivityWithValidIntent();
        assertThat(mActivityScenario.getState()).isAtLeast(Lifecycle.State.CREATED);
    }

    @Test
    public void onAllow_permissionMissing_finishes() throws Exception {
        launchActivityWithValidIntent();
        assertThat(mActivityScenario.getState()).isAtLeast(Lifecycle.State.CREATED);
        ServiceInfo info = createServiceInfoForTempComponent(/* permission = */ "");
        doReturn(info).when(mPackageManager).getServiceInfo(info.getComponentName(), 0);

        AndroidMockitoHelper.syncRunOnUiThread(mActivity, () -> {
            getConfirmationDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        // Not asserting DESTROYED state as mActivityScenario.getState() returns STARTED state for
        // some unknown reason.
        assertThat(mActivity.mFinishTriggered).isTrue();
    }

    @Test
    public void onAllow_permissionAvailable_callsNotificationManager() throws Exception {
        launchActivityWithValidIntent();
        assertThat(mActivityScenario.getState()).isAtLeast(Lifecycle.State.CREATED);
        ServiceInfo info = createServiceInfoForTempComponent(
                Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE);
        doReturn(info).when(mPackageManager).getServiceInfo(info.getComponentName(), 0);

        AndroidMockitoHelper.syncRunOnUiThread(mActivity, () -> {
            getConfirmationDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(mNotificationManager)
                .setNotificationListenerAccessGranted(eq(info.getComponentName()), eq(true));
    }

    @Test
    public void onDeny_finishes() throws Exception {
        launchActivityWithValidIntent();
        assertThat(mActivityScenario.getState()).isAtLeast(Lifecycle.State.CREATED);

        AndroidMockitoHelper.syncRunOnUiThread(mActivity, () -> {
            getConfirmationDialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        // Not asserting DESTROYED state as mActivityScenario.getState() returns STARTED state for
        // some unknown reason.
        assertThat(mActivity.mFinishTriggered).isTrue();
    }

    private AlertDialog getConfirmationDialog() {
        return (AlertDialog) ((ConfirmationDialogFragment) mActivity.getSupportFragmentManager()
                .findFragmentByTag(ConfirmationDialogFragment.TAG))
                .getDialog();
    }

    private void launchActivityWithValidIntent() {
        Intent intent = new Intent(mContext, TestActivity.class)
                .putExtra(EXTRA_COMPONENT_NAME, TEMP_COMPONENT_NAME);

        mActivityScenario = ActivityScenario.launch(intent);
        mActivityScenario.onActivity(
                activity -> {
                    mActivity = activity;
                    mPackageManager = mActivity.getPackageManager();
                    mActivity.setPackageManagerSpy(mPackageManager);
                    mActivity.setNotificationManagerSpy(mNotificationManager);
                });
    }

    public static final class TestActivity extends NotificationAccessConfirmationActivity {
        @Nullable private PackageManager mPackageManagerSpy;
        @Nullable private NotificationManager mNotificationManagerSpy;
        boolean mFinishTriggered;

        void setPackageManagerSpy(PackageManager packageManagerSpy) {
            mPackageManagerSpy = packageManagerSpy;
        }

        void setNotificationManagerSpy(NotificationManager notificationManagerSpy) {
            mNotificationManagerSpy = notificationManagerSpy;
        }

        @Override
        public PackageManager getPackageManager() {
            if (mPackageManagerSpy == null) {
                mPackageManagerSpy = spy(super.getPackageManager());
                try {
                    ApplicationInfo info = createApplicationInfo();
                    doReturn(info).when(mPackageManagerSpy).getApplicationInfo(any(), any());
                } catch (PackageManager.NameNotFoundException e) {
                    // do nothing... tests will fail when activity finishes during onCreate()
                }
            }
            return mPackageManagerSpy;
        }

        @Override
        public Object getSystemService(String name) {
            return mNotificationManagerSpy != null
                    && name.toLowerCase(Locale.ROOT).contains("notification")
                    ? mNotificationManagerSpy
                    : super.getSystemService(name);
        }

        @Override
        public void finish() {
            mFinishTriggered = true;
            super.finish();
        }
    }
}
