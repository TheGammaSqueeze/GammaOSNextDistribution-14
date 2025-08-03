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

package com.android.cts.verifier.notifications;


import static android.app.Notification.VISIBILITY_PRIVATE;
import static android.app.Notification.VISIBILITY_PUBLIC;
import static android.app.Notification.VISIBILITY_SECRET;
import static android.app.NotificationManager.IMPORTANCE_DEFAULT;
import static android.app.NotificationManager.VISIBILITY_NO_OVERRIDE;
import static android.provider.Settings.EXTRA_APP_PACKAGE;
import static android.provider.Settings.EXTRA_CHANNEL_ID;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.StringRes;

import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A verifier test which validates dismissal behaviour of notifications under various settings
 */
public class NotificationDismissVerifierActivity extends InteractiveVerifierActivity
        implements Runnable {
    static final String TAG = "NotifDismissVerifier";
    private static final String NOTIFICATION_CHANNEL_ID = TAG;

    @Override
    protected int getTitleResource() {
        return R.string.nd_test;
    }

    @Override
    protected int getInstructionsResource() {
        return R.string.nd_test_info;
    }

    private int getChannelVisibility() {
        NotificationChannel channel = mNm.getNotificationChannel(NOTIFICATION_CHANNEL_ID);
        int visibility = channel.getLockscreenVisibility();
        if (visibility == VISIBILITY_NO_OVERRIDE) {
            visibility = getGlobalVisibility();
        }
        if (visibility != VISIBILITY_SECRET
                && visibility != VISIBILITY_PRIVATE
                && visibility != VISIBILITY_PUBLIC) {
            throw new RuntimeException("Unexpected visibility: " + visibility);
        }
        return visibility;
    }

    private int getGlobalVisibility() {
        if (!getLockscreenNotificationsEnabled()) {
            return VISIBILITY_SECRET;
        } else if (!getLockscreenAllowPrivateNotifications()) {
            return VISIBILITY_PRIVATE;
        }
        return VISIBILITY_PUBLIC;
    }

    private boolean getLockscreenNotificationsEnabled() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS, 0) != 0;
    }

    private boolean getLockscreenAllowPrivateNotifications() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS, 0) != 0;
    }

    @Override
    protected List<InteractiveTestCase> createTestItems() {
        boolean isAutomotive = getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE);
        List<InteractiveTestCase> tests = new ArrayList<>();
        if (!isAutomotive) {

            // FIRST: set redaction settings
            tests.add(new SetScreenLockEnabledStep());
            tests.add(new SetGlobalVisibilityPublicStep());
            tests.add(new SetChannelLockscreenVisibilityPrivateStep());

            // NOW TESTING: Ongoing Notification CAN be dismissed when unlocked
            tests.add(new CanDismissOngoingNotificationTest());

            // NOW TESTING: Ongoing Notification can NOT be dismissed on lockscreen
            tests.add(new CannotDismissOngoingNotificationTest());

            // NOW TESTING: Notification redacted by channel CAN be dismissed on the lockscreen
            tests.add(new CanDismissRegularNotificationTest());

            tests.add(new SetChannelLockscreenVisibilityPublicStep());
            // NOW TESTING: Notifications not redacted at all CAN be dismissed on the lockscreen
            tests.add(new CanDismissRegularNotificationTest());

            tests.add(new SetGlobalVisibilityPrivateStep());
            // NOW TESTING: Notification redacted globally can NOT be dismissed on the lockscreen
            tests.add(new CannotDismissRegularNotificationTest());

            // FINALLY: restore device state
            tests.add(new SetScreenLockDisabledStep());
        }
        return tests;
    }

    private void createChannels() {
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_ID, IMPORTANCE_DEFAULT);
        mNm.createNotificationChannel(channel);
    }

    private void deleteChannels() {
        mNm.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID);
    }

    private void sendOngoingNotification() {
        sendNotification(true /* ongoing */);
    }

    private void sendRegularNotification() {
        sendNotification(false /* ongoing */);
    }

    private void sendNotification(boolean ongoing) {
        String tag = UUID.randomUUID().toString();
        long when = System.currentTimeMillis();
        Log.d(TAG, "Sending: tag=" + tag + " when=" + when + " ongoing=" + ongoing);

        Notification publicVersion = new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.nd_notif_title))
                .setSmallIcon(R.drawable.ic_stat_alice)
                .setWhen(when)
                .build();
        Notification privateVersion = new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.nd_notif_title))
                .setSmallIcon(R.drawable.ic_stat_alice)
                .setWhen(when)
                .setPublicVersion(publicVersion)
                .setOngoing(ongoing)
                .build();

        mNm.notify(tag, NOTIFICATION_ID, privateVersion);
    }

    private abstract class NotificationDismissBaseTest extends InteractiveTestCase {
        private View mView;
        @StringRes
        private final int mInstructionRes;

        NotificationDismissBaseTest(@StringRes int instructionRes) {
            mInstructionRes = instructionRes;
        }

        @Override
        protected void setUp() {
            createChannels();
            sendNotification();
            setButtonsEnabled(mView, true);
            status = READY;
            next();
        }

        @Override
        protected void tearDown() {
            mNm.cancelAll();
            deleteChannels();
            delay();
        }

        @Override
        protected View inflate(ViewGroup parent) {
            mView = createPassFailItem(parent, mInstructionRes);
            setButtonsEnabled(mView, false);
            return mView;
        }

        @Override
        boolean autoStart() {
            return true;
        }

        @Override
        protected void test() {
            status = WAIT_FOR_USER;
            next();
        }

        protected abstract void sendNotification();
    }

    private class CanDismissOngoingNotificationTest extends NotificationDismissBaseTest {

        CanDismissOngoingNotificationTest() {
            super(R.string.dismiss_notif_from_the_shade);
        }

        @Override
        protected void sendNotification() {
            sendOngoingNotification();
        }
    }

    private class CannotDismissOngoingNotificationTest extends NotificationDismissBaseTest {

        CannotDismissOngoingNotificationTest() {
            super(R.string.non_dismissable_notif_from_the_ls);
        }

        @Override
        protected void sendNotification() {
            sendOngoingNotification();
        }
    }

    private class CanDismissRegularNotificationTest extends NotificationDismissBaseTest {

        CanDismissRegularNotificationTest() {
            super(R.string.dismiss_notif_from_the_ls);
        }

        @Override
        protected void sendNotification() {
            sendRegularNotification();
        }
    }

    private class CannotDismissRegularNotificationTest extends NotificationDismissBaseTest {
        CannotDismissRegularNotificationTest() {
            super(R.string.non_dismissable_notif_from_the_ls);
        }

        @Override
        protected void sendNotification() {
            sendRegularNotification();
        }
    }

    /**
     * Asks the user to set the lockscreen visibility of the channel to the given value
     */
    private abstract class SetChannelLockscreenVisibilityBaseStep extends InteractiveTestCase {
        @StringRes
        private final int mInstructionRes;
        private final int mExpectVisibility;
        private View mView;

        SetChannelLockscreenVisibilityBaseStep(@StringRes int instructionRes,
                int expectVisibility) {
            mInstructionRes = instructionRes;
            mExpectVisibility = expectVisibility;
        }

        @Override
        protected View inflate(ViewGroup parent) {
            mView = createUserItem(parent, R.string.np_start_channel_settings, mInstructionRes);
            setButtonsEnabled(mView, false);
            return mView;
        }

        @Override
        protected void setUp() {
            createChannels();
            status = READY;
            setButtonsEnabled(mView, true);
            next();
        }

        @Override
        boolean autoStart() {
            return true;
        }

        @Override
        protected void test() {
            if (getChannelVisibility() == mExpectVisibility) {
                status = PASS;
            } else {
                // user hasn't jumped to settings yet
                status = WAIT_FOR_USER;
            }

            next();
        }

        protected void tearDown() {
            deleteChannels();
            next();
        }

        @Override
        protected Intent getIntent() {
            return new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(EXTRA_APP_PACKAGE, mContext.getPackageName())
                    .putExtra(EXTRA_CHANNEL_ID, NOTIFICATION_CHANNEL_ID);
        }
    }

    private class SetChannelLockscreenVisibilityPrivateStep extends
            SetChannelLockscreenVisibilityBaseStep {
        SetChannelLockscreenVisibilityPrivateStep() {
            super(R.string.nls_visibility, VISIBILITY_PRIVATE);
        }
    }

    private class SetChannelLockscreenVisibilityPublicStep extends
            SetChannelLockscreenVisibilityBaseStep {
        SetChannelLockscreenVisibilityPublicStep() {
            super(R.string.nls_restore_visibility, VISIBILITY_PUBLIC);
        }
    }

    private abstract class SetScreenLockBaseStep extends InteractiveTestCase {
        @StringRes
        private final int mInstructionRes;
        private final boolean mExpectSecure;
        private View mView;

        private SetScreenLockBaseStep(int instructionRes, boolean expectSecure) {
            mInstructionRes = instructionRes;
            mExpectSecure = expectSecure;
        }

        @Override
        protected View inflate(ViewGroup parent) {
            mView = createUserItem(parent, R.string.np_start_security_settings, mInstructionRes);
            setButtonsEnabled(mView, false);
            return mView;
        }

        @Override
        protected void setUp() {
            status = READY;
            setButtonsEnabled(mView, true);
            next();
        }

        @Override
        boolean autoStart() {
            return true;
        }

        @Override
        protected void test() {
            KeyguardManager km = getSystemService(KeyguardManager.class);
            if (km.isDeviceSecure() == mExpectSecure) {
                status = PASS;
            } else {
                status = WAIT_FOR_USER;
            }

            next();
        }

        @Override
        protected Intent getIntent() {
            return new Intent(Settings.ACTION_SECURITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }
    }

    private class SetScreenLockEnabledStep extends SetScreenLockBaseStep {
        private SetScreenLockEnabledStep() {
            super(R.string.add_screen_lock, true /* secure */);
        }
    }

    private class SetScreenLockDisabledStep extends SetScreenLockBaseStep {
        private SetScreenLockDisabledStep() {
            super(R.string.remove_screen_lock, false /* secure */);
        }
    }

    private abstract class SetGlobalVisibilityBaseStep extends InteractiveTestCase {
        @StringRes
        private final int mInstructionRes;
        private final int mExpectVisibility;
        private View mView;

        private SetGlobalVisibilityBaseStep(int instructionRes, int expectVisibility) {
            mInstructionRes = instructionRes;
            mExpectVisibility = expectVisibility;
        }

        @Override
        protected View inflate(ViewGroup parent) {
            mView = createUserItem(parent, R.string.np_start_notif_settings, mInstructionRes);
            setButtonsEnabled(mView, false);
            return mView;
        }

        @Override
        protected void setUp() {
            status = READY;
            setButtonsEnabled(mView, true);
            next();
        }

        @Override
        boolean autoStart() {
            return true;
        }

        @Override
        protected void test() {
            KeyguardManager km = getSystemService(KeyguardManager.class);
            if (!km.isDeviceSecure()) {
                // if lockscreen itself not set, this setting won't be available.
                status = FAIL;
            } else if (getGlobalVisibility() == mExpectVisibility) {
                status = PASS;
            } else {
                status = WAIT_FOR_USER;
            }

            next();
        }

        @Override
        protected Intent getIntent() {
            return new Intent(Settings.ACTION_NOTIFICATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }
    }

    private class SetGlobalVisibilityPublicStep extends SetGlobalVisibilityBaseStep {
        private SetGlobalVisibilityPublicStep() {
            super(R.string.set_global_visibility_public, VISIBILITY_PUBLIC);
        }
    }

    private class SetGlobalVisibilityPrivateStep extends SetGlobalVisibilityBaseStep {
        private SetGlobalVisibilityPrivateStep() {
            super(R.string.set_global_visibility_private, VISIBILITY_PRIVATE);
        }
    }
}
