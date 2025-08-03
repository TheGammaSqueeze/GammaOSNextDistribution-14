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
import static android.app.NotificationManager.IMPORTANCE_HIGH;
import static android.app.NotificationManager.VISIBILITY_NO_OVERRIDE;
import static android.provider.Settings.EXTRA_APP_PACKAGE;
import static android.provider.Settings.EXTRA_CHANNEL_ID;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.hardware.display.AmbientDisplayConfiguration;
import android.net.Uri;
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
 * A verifier test which validates behaviour of notifications with Full Screen Intent
 * under various settings
 */
public class NotificationFullScreenIntentVerifierActivity extends InteractiveVerifierActivity
        implements Runnable {
    static final String TAG = "NotifFsiVerifier";
    private static final String NOTIFICATION_CHANNEL_ID = TAG;

    private AmbientDisplayConfiguration mAmbientDisplayConfiguration = null;

    @Override
    protected int getTitleResource() {
        return R.string.fsi_test;
    }

    @Override
    protected int getInstructionsResource() {
        return R.string.fsi_test_info;
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

    private boolean isAlwaysOnAvailable() {
        if (mAmbientDisplayConfiguration == null) {
            mAmbientDisplayConfiguration = new AmbientDisplayConfiguration(mContext);
        }
        return mAmbientDisplayConfiguration.alwaysOnAvailable();
    }

    @Override
    protected List<InteractiveTestCase> createTestItems() {
        boolean isAutomotive = getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE);
        List<InteractiveTestCase> tests = new ArrayList<>();
        if (isAutomotive) {
            return tests;
        }
        // FIRST: set redaction settings
        tests.add(new SetScreenLockEnabledStep());
        tests.add(new SetGlobalVisibilityPublicStep());
        tests.add(new SetChannelLockscreenVisibilityPublicStep());

        // Grant permission for Full Screen Intent
        tests.add(new GrantFsiPermissionStep());

        // NOW TESTING: Screen unlocked FSI HUN with permission, should show sticky HUN for
        // at least 60s
        tests.add(new ScreenUnlockedFsiHunWithPermissionTest());

        // NOW TESTING: lockscreen FSI HUN with FSI permission, should launch FSI
        tests.add(new LockScreenFsiWithPermissionTestStep());

        // NOW TESTING: FSI HUN with FSI permission with screen is off, should launch FSI
        if (isAlwaysOnAvailable()) {
            tests.add(new DisableAodStep());
        }
        tests.add(new ScreenOffFsiWithPermissionTestStep());

        // NOW TESTING: FSI HUN with FSI permission on AOD, should launch FSI
        if (isAlwaysOnAvailable()) {
            tests.add(new EnableAodStep());
            tests.add(new AodFsiWithPermissionTestStep());
        }


        // Deny permission for Full Screen Intent
        tests.add(new DenyFsiPermissionStep());

        // NOW TESTING: Screen unlocked FSI without permission, should show sticky HUN for 60s
        tests.add(new ScreenUnlockedFsiHunWithoutPermissionTest());

        // NOW TESTING: lockscreen FSI HUN without FSI permission,
        // HUN shows up first in list, expanded with pill buttons
        tests.add(new LockScreenFsiWithoutPermissionTestStep());

        // NOW TESTING: FSI HUN without FSI permission on AOD
        // HUN pulses with pill buttons
        if (isAlwaysOnAvailable()) {
            tests.add(new EnableAodStep());
            tests.add(new AodFsiWithoutPermissionTestStep());
        }

        // Now testing: FSI HUN without FSI permission when screen is off,
        // HUN pulses with pill buttons
        if (isAlwaysOnAvailable()) {
            tests.add(new DisableAodStep());
        }
        tests.add(new ScreenOffFsiWithoutPermissionTestStep());

        return tests;
    }

    private void createChannels() {
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_ID, IMPORTANCE_HIGH);
        mNm.createNotificationChannel(channel);
    }

    private void deleteChannels() {
        mNm.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID);
    }


    /**
     * Post a heads up notification with full screen intent, and reply action
     */
    private void sendFullScreenIntentHeadsUpNotification() {
        String tag = UUID.randomUUID().toString();
        long when = System.currentTimeMillis();
        Log.d(TAG, "Sending: tag=" + tag + " when=" + when + " fsi=true");

        Icon icon = Icon.createWithResource(getApplicationContext(), R.drawable.ic_android);

        // Build the reply action
        PendingIntent inputIntent = PendingIntent.getActivity(getApplicationContext(), 0,
                new Intent().setPackage(getApplicationContext().getPackageName()),
                PendingIntent.FLAG_MUTABLE);
        RemoteInput remoteInput = new RemoteInput.Builder("reply_key").setLabel("reply").build();
        Notification.Action replyAction = new Notification.Action.Builder(icon, "Reply",
                inputIntent).addRemoteInput(remoteInput)
                .build();

        // Build the notification
        Notification fsiNotif = new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.fsi_notif_title))
                .setContentText(getString(R.string.fsi_notif_content))
                .setSmallIcon(R.drawable.ic_stat_alice)
                .setWhen(when)
                .setFullScreenIntent(
                        PendingIntent.getActivity(
                                mContext,
                                /* requestCode = */0,
                                /* intent = */ ShowWhenLockedActivity.makeActivityIntent(
                                        getApplicationContext(),
                                        /* description = */ getString(R.string.fsi_mock_call_desc),
                                        /* clearActivity = */ false),
                                PendingIntent.FLAG_MUTABLE),
                        /* highPriority = */ true)
                .setActions(replyAction)
                .build();
        mNm.notify(tag, NOTIFICATION_ID, fsiNotif);
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

    private abstract class SetAodBaseStep extends InteractiveTestCase {
        @StringRes
        private final int mInstructionRes;
        private final boolean mExpectAodStatus;

        private final AmbientDisplayConfiguration mAmbientDisplayConfiguration;

        private View mView;

        private SetAodBaseStep(int instructionRes, boolean expectAodStatus) {
            mInstructionRes = instructionRes;
            mExpectAodStatus = expectAodStatus;
            mAmbientDisplayConfiguration = new AmbientDisplayConfiguration(mContext);
        }

        @Override
        protected View inflate(ViewGroup parent) {
            mView = createUserItem(parent, R.string.np_start_lockscreen_settings, mInstructionRes);
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
            if (mAmbientDisplayConfiguration.alwaysOnEnabled(getUserId()) == mExpectAodStatus) {
                status = PASS;
            } else {
                status = WAIT_FOR_USER;
            }
            next();
        }

        @Override
        protected Intent getIntent() {
            return new Intent(Settings.ACTION_LOCKSCREEN_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }
    }

    private class EnableAodStep extends SetAodBaseStep {
        private EnableAodStep() {
            super(R.string.fsi_turn_on_aod, true /* secure */);
        }
    }

    private class DisableAodStep extends SetAodBaseStep {
        private DisableAodStep() {
            super(R.string.fsi_turn_off_aod, false /* secure */);
        }
    }

    private abstract class SetFsiPermissionBaseStep extends InteractiveTestCase {
        @StringRes
        private final int mInstructionRes;
        private final boolean mExpectPermission;
        private View mView;

        private SetFsiPermissionBaseStep(int instructionRes, boolean expectPermission) {
            mInstructionRes = instructionRes;
            mExpectPermission = expectPermission;
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
            if (mNm.canUseFullScreenIntent() == mExpectPermission) {
                status = PASS;
            } else {
                status = WAIT_FOR_USER;
            }
            next();
        }

        @Override
        protected Intent getIntent() {
            return new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                    .setData(Uri.parse("package:" + mContext.getPackageName()));
        }
    }

    private class GrantFsiPermissionStep extends SetFsiPermissionBaseStep {
        private GrantFsiPermissionStep() {
            super(R.string.fsi_grant_permission, true);
        }
    }

    private class DenyFsiPermissionStep extends SetFsiPermissionBaseStep {
        private DenyFsiPermissionStep() {
            super(R.string.fsi_deny_permission, false);
        }
    }

    private abstract class FullScreenIntentNotificationBaseTest extends InteractiveTestCase {
        private View mView;
        @StringRes
        private final int mInstructionRes;

        FullScreenIntentNotificationBaseTest(@StringRes int instructionRes) {
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

    private class ScreenUnlockedFsiHunWithPermissionTest
            extends FullScreenIntentNotificationBaseTest {

        ScreenUnlockedFsiHunWithPermissionTest() {
            super(R.string.fsi_sticky_hun);
        }

        @Override
        protected void sendNotification() {
            sendFullScreenIntentHeadsUpNotification();
        }
    }

    private class ScreenUnlockedFsiHunWithoutPermissionTest
            extends FullScreenIntentNotificationBaseTest {

        ScreenUnlockedFsiHunWithoutPermissionTest() {
            super(R.string.fsi_sticky_hun_60s);
        }

        @Override
        protected void sendNotification() {
            sendFullScreenIntentHeadsUpNotification();
        }
    }

    private abstract class LockscreenFsiTestBaseStep extends InteractiveTestCase {
        @StringRes
        private final int mInstructionRes;
        private View mView;

        private LockscreenFsiTestBaseStep(int instructionRes) {
            mInstructionRes = instructionRes;
        }

        @Override
        protected View inflate(ViewGroup parent) {
            mView = createUserAndPassFailItem(
                    /* parent = */ parent,
                    /* actionId = */ R.string.attention_ready,
                    /* messageFormatArgs = */ mInstructionRes);
            setButtonsEnabled(mView, false);
            return mView;
        }

        @Override
        protected void setUp() {
            createChannels();
            setButtonsEnabled(mView, true);
            status = WAIT_FOR_USER;
            next();
        }

        @Override
        protected void tearDown() {
            mNm.cancelAll();
            deleteChannels();
            delay();
        }

        @Override
        boolean autoStart() {
            return true;
        }

        @Override
        int autoStartStatus() {
            return WAIT_FOR_USER;
        }

        @Override
        protected void test() {
            if (status == READY) {
                mView.postDelayed(
                        NotificationFullScreenIntentVerifierActivity
                                .this::sendFullScreenIntentHeadsUpNotification,
                        3000);
                status = WAIT_FOR_USER;
            }
            next();
        }

    }

    private class LockScreenFsiWithoutPermissionTestStep extends LockscreenFsiTestBaseStep {
        private LockScreenFsiWithoutPermissionTestStep() {
            super(R.string.fsi_lockscreen_without_permission_instruction);
        }
    }

    private class LockScreenFsiWithPermissionTestStep extends LockscreenFsiTestBaseStep {
        private LockScreenFsiWithPermissionTestStep() {
            super(R.string.fsi_lockscreen_with_permission_instruction);
        }
    }

    private class ScreenOffFsiWithPermissionTestStep extends LockscreenFsiTestBaseStep {
        private ScreenOffFsiWithPermissionTestStep() {
            super(R.string.fsi_screen_off_with_permission_instruction);
        }
    }

    private class ScreenOffFsiWithoutPermissionTestStep extends LockscreenFsiTestBaseStep {
        private ScreenOffFsiWithoutPermissionTestStep() {
            super(R.string.fsi_screen_off_without_permission_instruction);
        }
    }

    private class AodFsiWithPermissionTestStep extends LockscreenFsiTestBaseStep {
        private AodFsiWithPermissionTestStep() {
            super(R.string.fsi_aod_with_permission_instruction);
        }
    }

    private class AodFsiWithoutPermissionTestStep extends LockscreenFsiTestBaseStep {
        private AodFsiWithoutPermissionTestStep() {
            super(R.string.fsi_aod_without_permission_instruction);
        }
    }

}
