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

import android.Manifest;
import android.annotation.Nullable;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.Logger;
import com.android.internal.app.AlertActivity;
import com.android.settingslib.core.lifecycle.HideNonSystemOverlayMixin;

/**
 * This activity is a copy of
 * {@link com.android.settings.notification.NotificationAccessConfirmationActivity}.
 */
public class NotificationAccessConfirmationActivity extends FragmentActivity {
    private static final Logger LOG = new Logger(NotificationAccessConfirmationActivity.class);
    private static final String REQUIRED_PERMISSION =
            Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE;

    private ComponentName mComponentName;

    private final ConfirmationDialogFragment.ConfirmListener mConfirmListener =
            arguments -> onAllow();

    private final ConfirmationDialogFragment.RejectListener mRejectListener =
            arguments -> cancel();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLifecycle().addObserver(new HideNonSystemOverlayMixin(this));
        ConfirmationDialogFragment.resetListeners(findConfirmationDialogFragment(),
                mConfirmListener,
                mRejectListener,
                /* neutralListener= */ null);

        mComponentName = getIntent().getParcelableExtra(EXTRA_COMPONENT_NAME);
        CharSequence mAppLabel;

        if (mComponentName == null || mComponentName.getPackageName() == null) {
            finish();
            return;
        }

        try {
            ApplicationInfo applicationInfo = getPackageManager().getApplicationInfo(
                    mComponentName.getPackageName(), 0);
            mAppLabel = applicationInfo.loadSafeLabel(getPackageManager(),
                    PackageItemInfo.DEFAULT_MAX_LABEL_SIZE_PX,
                    PackageItemInfo.SAFE_LABEL_FLAG_TRIM
                            | PackageItemInfo.SAFE_LABEL_FLAG_FIRST_LINE);
        } catch (PackageManager.NameNotFoundException e) {
            LOG.e("Couldn't find app with package name for " + mComponentName, e);
            finish();
            return;
        }

        if (TextUtils.isEmpty(mAppLabel)) {
            finish();
            return;
        }

        ConfirmationDialogFragment confirmationDialogFragment =
                new ConfirmationDialogFragment.Builder(this)
                        .setTitle(getString(R.string.notification_listener_security_warning_title,
                                mAppLabel))
                        .setMessage(
                                getString(R.string.notification_listener_security_warning_summary,
                                        mAppLabel))
                        .setPositiveButton(R.string.allow, mConfirmListener)
                        .setNegativeButton(R.string.deny, mRejectListener)
                        .build();

        // Consistent with the permission dialog
        confirmationDialogFragment.setCancelable(false);
        confirmationDialogFragment
                .show(getSupportFragmentManager(), ConfirmationDialogFragment.TAG);
    }

    @Nullable
    private ConfirmationDialogFragment findConfirmationDialogFragment() {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(
                ConfirmationDialogFragment.TAG);
        if (fragment != null) {
            return (ConfirmationDialogFragment) fragment;
        }
        return null;
    }

    private void onAllow() {
        try {
            ServiceInfo serviceInfo = getPackageManager().getServiceInfo(mComponentName, 0);
            if (!REQUIRED_PERMISSION.equals(serviceInfo.permission)) {
                LOG.e("Service " + mComponentName + " lacks permission " + REQUIRED_PERMISSION);
                finish();
                return;
            }
        } catch (PackageManager.NameNotFoundException e) {
            LOG.e("Failed to get service info for " + mComponentName, e);
            finish();
            return;
        }

        getSystemService(NotificationManager.class)
                .setNotificationListenerAccessGranted(mComponentName, true);
        finish();
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        return AlertActivity.dispatchPopulateAccessibilityEvent(this, event);
    }

    @Override
    public void onBackPressed() {
        // Suppress finishing the activity on back button press,
        // consistently with the permission dialog behavior
    }

    private void cancel() {
        finish();
    }
}
