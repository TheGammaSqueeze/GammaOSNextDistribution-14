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

package com.android.tv.frameworkpackagestubs;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup;

import java.util.List;

/**
 * Contains different stub classes.
 * <p>
 * These are broken out so that the intent filters are easier to track and so that
 * individual ones can create more specific messages if desired.
 */
public final class Stubs {

    /**
     * Base class for stubs.
     * <p>
     * Shows a toast and finishes.
     * <p>
     * Subclasses can override {@link #getMessage()} to customize the message.
     */
    public static class BaseActivity extends Activity {

        private Toast mToast;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            showToastAndFinish();
        }

        protected CharSequence getMessage() {
            return getResources().getString(R.string.message_not_supported);
        }

        protected void showToast() {
            cancelToast();
            mToast = Toast.makeText(this, getMessage(), Toast.LENGTH_LONG);
            mToast.show();
        }

        private void cancelToast() {
            if (mToast != null) {
                mToast.cancel();
            }
        }

        protected void showToastAndFinish() {
            showToast();
            finish();
        }
    }

    /**
     * Stub activity for browser events.
     */
    public static class BrowserStub extends BaseActivity {}

    /**
     * Stub activity for calendar events.
     */
    public static class CalendarStub extends BaseActivity {}

    /**
     * Stub activity for camera events.
     */
    public static class CameraStub extends BaseActivity {}

    /**
     * Stub activity for contacts events.
     */
    public static class ContactsStub extends BaseActivity {}

    /**
     * Stub activity for email events.
     */
    public static class EmailStub extends BaseActivity {}

    /**
     * Stub activity for music events.
     */
    public static class MusicStub extends BaseActivity {}

    /**
     * Stub activity for documents events.
     */
    public static class DocumentsStub extends BaseActivity {}

    /**
     * Stub activity for media events.
     */
    public static class MediaStub extends BaseActivity {}

    /**
     * Stub activity for settings events.
     */
    public static class SettingsStub extends BaseActivity {}

    /**
     * Stub activity for settings privacy events.
     */
    public static class SettingsPrivacyStub extends BaseActivity {
        private static final String WORK_POLICY_INFO_TEXT = "Your work policy info";

        @Override
        protected void showToastAndFinish() {
            showToast();

            if (hasWorkPolicyInfo()) {
                TextView tv = new TextView(this);
                tv.setText(WORK_POLICY_INFO_TEXT);
                addContentView(tv, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }

        private boolean hasWorkPolicyInfo() {
            final DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(
                Context.DEVICE_POLICY_SERVICE);
            final String deviceOwnerPackage = dpm.getDeviceOwner();
            if (deviceOwnerPackage != null) {
                Intent intent = new Intent(Settings.ACTION_SHOW_WORK_POLICY_INFO)
                        .setPackage(deviceOwnerPackage);
                List<ResolveInfo> resolveInfoList = getPackageManager().queryIntentActivities(intent, 0);
                return resolveInfoList.size() > 0;
            }
            return false;
        }
    }

    /**
     * Stub activity for clock events.
     */
    public static class ClockStub extends BaseActivity {}
}
