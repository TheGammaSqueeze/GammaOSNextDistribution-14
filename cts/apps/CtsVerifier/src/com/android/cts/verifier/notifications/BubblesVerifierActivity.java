/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.app.NotificationManager.BUBBLE_PREFERENCE_ALL;
import static android.app.NotificationManager.BUBBLE_PREFERENCE_NONE;
import static android.app.NotificationManager.BUBBLE_PREFERENCE_SELECTED;
import static android.app.NotificationManager.IMPORTANCE_DEFAULT;
import static android.content.Intent.ACTION_VIEW;
import static android.content.pm.PackageManager.FEATURE_INPUT_METHODS;
import static android.content.pm.PackageManager.FEATURE_SCREEN_LANDSCAPE;
import static android.content.pm.PackageManager.FEATURE_SCREEN_PORTRAIT;
import static android.content.pm.PackageManager.FEATURE_SENSOR_ACCELEROMETER;
import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import static com.android.cts.verifier.notifications.BubbleActivity.EXTRA_INSETS;
import static com.android.cts.verifier.notifications.BubbleActivity.EXTRA_TEST_NAME;
import static com.android.cts.verifier.notifications.BubbleActivity.TEST_MAX_HEIGHT;
import static com.android.cts.verifier.notifications.BubbleActivity.TEST_MIN_HEIGHT;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.ArraySet;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.Button;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.Set;

/**
 * Bubble notification tests: This test checks the behaviour of notifications that have a bubble.
 */
public class BubblesVerifierActivity extends PassFailButtons.Activity {

    private static final String CHANNEL_ID = "BubblesVerifierChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int NUM_SHORTCUTS = 3;
    private static final String SHORTCUT_CATEGORY =
            "com.android.cts.verifier.notifications.SHORTCUT_CATEGORY";

    private NotificationManager mNotificationManager;
    private ArrayList<ShortcutInfo> mShortcuts = new ArrayList<>();
    private boolean mIsLargeScreen;

    private TextView mTestTitle;
    private TextView mTestDescription;
    private Button mTestAction;
    private ViewGroup mTestStepButtonLayout;
    private TextView mTestState;
    private Button mTestStepPassed;
    private Button mTestStepFailed;

    private ArrayList<BubblesTestStep> mTests = new ArrayList<>();
    private int mCurrentTestIndex = -1; // gets incremented first time
    private int mStepFailureCount = 0;
    private boolean mShowingSummary = false;
    private boolean mSupportsBubble = false;

    private Handler mHandler = new Handler();
    private Runnable mRunnable;

    private abstract class BubblesTestStep {

        /** The title of the test step. */
        public abstract int getTestTitle();

        /** What the tester should do & look for to verify this step was successful. */
        public abstract int getTestDescription();

        /**
         * Text of the button that performs the action for this test (e.g. send bubble). If no
         * button is necessary this will return 0.
         */
        public int getButtonText() {
            return 0;
        }

        /** Code to run when the button is activated; only used if {@link #getButtonText()} != 0 */
        public void performTestAction() {
            // optional
        }

        /** Checks device state to see if the test passed.  */
        public boolean verify() {
            return true;
        }

        /** Return true if the test has stuff to check in {@link #verify()}. **/
        public boolean waitForVerify() {
            return false;
        }
    }

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        setContentView(R.layout.bubbles_main);

        mTestTitle = findViewById(R.id.bubble_test_title);
        mTestDescription = findViewById(R.id.bubble_test_description);
        mTestAction = findViewById(R.id.bubble_test_button);
        mTestStepButtonLayout = findViewById(R.id.button_layout);
        mTestState = findViewById(R.id.bubble_test_state);
        mTestStepPassed = findViewById(R.id.test_step_passed);
        mTestStepFailed = findViewById(R.id.test_step_failed);

        mTestStepPassed.setOnClickListener((v) -> runNextTestOrShowSummary());
        mTestStepFailed.setOnClickListener((v) -> {
            mStepFailureCount++;
            runNextTestOrShowSummary();
        });

        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        try {
            mSupportsBubble = getResources().getBoolean(getResources().getIdentifier(
                    "config_supportsBubble", "bool", "android"));
        } catch (Resources.NotFoundException e) {
            // Assume device does not support bubble, no need to do anything.
        }

        if (am.isLowRamDevice()) {
            // Bubbles don't occur on low ram, instead they just show as notifs so test that
            mTests.add(new LowRamBubbleTest());
        } else if (!mSupportsBubble) {
            // Bubbles don't occur on bubble disabled devices, only test notifications.
            mTests.add(new BubbleDisabledTest());
        } else {
            //
            // Behavior around settings at the device level and on the app settings page.
            //
            mTests.add(new CheckDefaultsTest());
            mTests.add(new BubbleButtonTest());
            mTests.add(new BubblePreferenceSelected());
            mTests.add(new DisableFeature());
            mTests.add(new EnableBubblesTest());
            //
            // Behavior around visibility of the bubble, the notification, and the dot
            // on the bubble for 'new messages'.
            //
            mTests.add(new SendBubbleTest());
            mTests.add(new SuppressNotifTest());
            mTests.add(new AddNotifTest());
            mTests.add(new RemoveMetadataTest());
            mTests.add(new AddMetadataTest());
            mTests.add(new ExpandBubbleTest());
            mTests.add(new DismissBubbleTest());
            mTests.add(new DismissNotificationTest());
            mTests.add(new AutoExpandBubbleTest());
            //
            // Behavior around the bubble overflow
            //
            mTests.add(new CheckOverflowExists());
            mTests.add(new DismissBubbleShowsInOverflow());
            mTests.add(new PromoteBubbleFromOverflow());
            mTests.add(new CancelRemovesBubblesInOverflow());
            mTests.add(new TapNotifWithOverflowBubble());
            //
            // Expanded view appearance
            //
            // Check if devices do not support rotation
            if (getPackageManager().hasSystemFeature(FEATURE_SCREEN_LANDSCAPE) &&
                getPackageManager().hasSystemFeature(FEATURE_SCREEN_PORTRAIT) &&
                getPackageManager().hasSystemFeature(FEATURE_SENSOR_ACCELEROMETER))
            {
                mTests.add(new PortraitAndLandscape());
            }
            mTests.add(new ScrimBehindExpandedView());
            if (getPackageManager().hasSystemFeature(FEATURE_INPUT_METHODS)) {
                mTests.add(new ImeInsetsExpandedView());
            }
            mTests.add(new MinHeightExpandedView());
            mTests.add(new MaxHeightExpandedView());
        }

        setPassFailButtonClickListeners();
        // Pass is are enabled when all the steps are done & succeeded
        getPassButton().setEnabled(false);
        // Sets the text in the dialog
        setInfoResources(R.string.bubbles_test_title, R.string.bubbles_test_info, -1);

        mIsLargeScreen = getResources().getConfiguration().smallestScreenWidthDp >= 600;
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.createNotificationChannel(
                new NotificationChannel(CHANNEL_ID, CHANNEL_ID, IMPORTANCE_DEFAULT));
        createShortcuts();

        runNextTestOrShowSummary();

        restoreStateFromBundle(savedState);
    }

    @Override
    protected void onSaveInstanceState(Bundle savedState) {
        super.onSaveInstanceState(savedState);
        savedState.putInt("mCurrentTestIndex", mCurrentTestIndex);
        savedState.putInt("mStepFailureCount", mStepFailureCount);
        savedState.putBoolean("mShowingSummary", mShowingSummary);
        savedState.putBoolean("mTestStepPassed", mTestStepPassed.isEnabled());
    }

    private void restoreStateFromBundle(Bundle savedState) {
        if (savedState != null) {
            mCurrentTestIndex = savedState.getInt("mCurrentTestIndex", mCurrentTestIndex);
            mStepFailureCount = savedState.getInt("mStepFailureCount", mStepFailureCount);
            mShowingSummary = savedState.getBoolean("mShowingSummary", mShowingSummary);
            if (mShowingSummary) {
                updateViewForCompletionSummary();
            } else {
                BubblesTestStep currentTest = mTests.get(mCurrentTestIndex);
                updateViewForTest(currentTest);
                mTestStepPassed.setEnabled(savedState.getBoolean("mTestStepPassed", false));
            }
        }
    }

    private void runNextTestOrShowSummary() {
        if (mCurrentTestIndex + 1 >= mTests.size()) {
            updateViewForCompletionSummary();
        } else {
            mCurrentTestIndex++;
            BubblesTestStep currentTest = mTests.get(mCurrentTestIndex);
            updateViewForTest(currentTest);
        }
    }

    /** Populates the UI based on the provided test step */
    private void updateViewForTest(BubblesTestStep test) {
        mTestStepButtonLayout.setVisibility(VISIBLE);
        mTestTitle.setText(test.getTestTitle());
        mTestDescription.setText(test.getTestDescription());

        if (mRunnable != null) {
            mHandler.removeCallbacks(mRunnable);
        }
        if (test.waitForVerify()) {
            mTestState.setVisibility(VISIBLE);
            if (test.verify()) {
                mTestStepPassed.setEnabled(true);
                mTestState.setText(R.string.bubble_test_state_correct);
            } else {
                mTestState.setText(R.string.bubble_test_state_waiting);
                mRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (test.verify()) {
                            mTestStepPassed.setEnabled(true);
                            mTestState.setText(R.string.bubble_test_state_correct);
                        } else {
                            mHandler.postDelayed(this, 3000);
                        }
                    }
                };
                mHandler.postDelayed(mRunnable, 1000);
            }
        } else {
            mTestState.setVisibility(GONE);
        }
        if (test.getButtonText() != 0) {
            // Can't pass until test action has run
            mTestStepPassed.setEnabled(false);

            // Set up test action
            mTestAction.setOnClickListener((v) -> {
                test.performTestAction();
                mTestStepPassed.setEnabled(true);
            });
            mTestAction.setText(test.getButtonText());
            mTestAction.setVisibility(VISIBLE);
        } else {
            // No test action required
            mTestAction.setOnClickListener(null);
            mTestAction.setVisibility(INVISIBLE);
            mTestStepPassed.setEnabled(true);
        }
    }

    /** Populates the UI indicating results of test & updates test buttons as needed */
    private void updateViewForCompletionSummary() {
        mShowingSummary = true;
        // No longer need any of these buttons
        mTestStepButtonLayout.setVisibility(INVISIBLE);
        mTestAction.setVisibility(INVISIBLE);

        boolean didEverythingSucceed = mStepFailureCount == 0;
        int totalTests = mTests.size();
        int totalPasses = totalTests - mStepFailureCount;

        mTestTitle.setText(R.string.bubbles_test_summary_title);
        mTestDescription.setText(getString(R.string.bubbles_test_summary, totalPasses, totalTests));

        if (didEverythingSucceed) {
            getPassButton().setEnabled(true);
        }

        // Clean up
        ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
        shortcutManager.removeAllDynamicShortcuts();
        mNotificationManager.cancelAll();
    }

    private class CheckDefaultsTest extends BubblesTestStep {

        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_check_defaults_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_check_defaults_verify;
        }

        @Override
        public boolean waitForVerify() {
            return true;
        }

        @Override
        public boolean verify() {
            return mNotificationManager.getBubblePreference() == BUBBLE_PREFERENCE_NONE
                    && mNotificationManager.areBubblesEnabled();
        }
    }

    private class BubbleButtonTest extends BubblesTestStep {

        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_bubble_button_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_bubble_button_verify;
        }

        @Override
        public int getButtonText() {
            return R.string.bubbles_test_bubble_button_button;
        }

        @Override
        public void performTestAction() {
            Notification.Builder builder = getConversationNotif(getTestTitle());
            builder.setBubbleMetadata(getBubbleBuilder().build());
            mNotificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private class BubblePreferenceSelected extends BubblesTestStep {

        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_app_selected_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_app_selected_verify;
        }

        @Override
        public boolean waitForVerify() {
            return true;
        }

        @Override
        public boolean verify() {
            return mNotificationManager.getBubblePreference()
                    == BUBBLE_PREFERENCE_SELECTED;
        }
    }

    private class DisableFeature extends BubblesTestStep {

        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_disable_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_disable_verify;
        }

        @Override
        public int getButtonText() {
            return R.string.bubbles_test_disable_button;
        }

        @Override
        public void performTestAction() {
            final Intent intent = new Intent(Settings.ACTION_NOTIFICATION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }

        @Override
        public boolean waitForVerify() {
            return true;
        }

        @Override
        public boolean verify() {
            return !mNotificationManager.areBubblesEnabled();
        }
    }

    private class EnableBubblesTest extends BubblesTestStep {
        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_enable_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_enable_verify;
        }

        @Override
        public int getButtonText() {
            return R.string.bubbles_test_enable_button;
        }

        @Override
        public void performTestAction() {
            final String packageName = getApplicationContext().getPackageName();
            final int appUid = getApplicationInfo().uid;
            final Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName);
            intent.putExtra(Settings.EXTRA_APP_UID, appUid);
            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }

        @Override
        public boolean waitForVerify() {
            return true;
        }

        @Override
        public boolean verify() {
            return mNotificationManager.getBubblePreference() == BUBBLE_PREFERENCE_ALL
                    && mNotificationManager.areBubblesEnabled();
        }
    }

    private class SendBubbleTest extends BubblesTestStep {

        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_send_bubble_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_send_bubble_verify;
        }

        @Override
        public int getButtonText() {
            return R.string.bubbles_test_send_bubble_button;
        }

        @Override
        public void performTestAction() {
            Notification.Builder builder = getConversationNotif(getTestTitle());
            builder.setBubbleMetadata(getBubbleBuilder().build());

            mNotificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private class SuppressNotifTest extends BubblesTestStep {

        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_suppress_notif_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_suppress_notif_verify;
        }

        @Override
        public int getButtonText() {
            return R.string.bubbles_test_suppress_notif_button;
        }

        @Override
        public void performTestAction() {
            Notification.Builder builder = getConversationNotif(getTestTitle());
            Notification.BubbleMetadata metadata = getBubbleBuilder()
                    .setSuppressNotification(true)
                    .build();
            builder.setBubbleMetadata(metadata);

            mNotificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private class AddNotifTest extends BubblesTestStep {

        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_add_notif_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_add_notif_verify;
        }

        @Override
        public int getButtonText() {
            return R.string.bubbles_test_add_notif_button;
        }

        @Override
        public void performTestAction() {
            Notification.Builder builder = getConversationNotif(getTestTitle());
            Notification.BubbleMetadata metadata = getBubbleBuilder()
                    .setSuppressNotification(false)
                    .build();
            builder.setBubbleMetadata(metadata);

            mNotificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private class RemoveMetadataTest extends BubblesTestStep {

        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_remove_metadata_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_remove_metadata_verify;
        }

        @Override
        public int getButtonText() {
            return R.string.bubbles_test_remove_metadata_button;
        }

        @Override
        public void performTestAction() {
            Notification.Builder builder = getConversationNotif(getTestTitle());
            mNotificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private class AddMetadataTest extends BubblesTestStep {

        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_add_metadata_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_add_metadata_verify;
        }

        @Override
        public int getButtonText() {
            return R.string.bubbles_test_add_metadata_button;
        }

        @Override
        public void performTestAction() {
            Notification.Builder builder = getConversationNotif(getTestTitle());
            Notification.BubbleMetadata metadata = getBubbleBuilder().build();
            builder.setBubbleMetadata(metadata);

            mNotificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private class ExpandBubbleTest extends BubblesTestStep {
        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_expand_bubble_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_expand_bubble_verify;
        }
    }

    private class DismissBubbleTest extends BubblesTestStep {

        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_dismiss_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_dismiss_verify;
        }

        @Override
        public int getButtonText() {
            return R.string.bubbles_test_dismiss_button;
        }

        @Override
        public void performTestAction() {
            Notification.Builder builder = getConversationNotif(getTestTitle());
            Notification.BubbleMetadata metadata = getBubbleBuilder().build();
            builder.setBubbleMetadata(metadata);

            mNotificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private class DismissNotificationTest extends BubblesTestStep {

        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_dismiss_notif_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_dismiss_notif_verify;
        }

        @Override
        public int getButtonText() {
            return R.string.bubbles_test_dismiss_notif_button;
        }

        @Override
        public void performTestAction() {
            Notification.Builder builder = getConversationNotif(getTestTitle());
            Notification.BubbleMetadata metadata = getBubbleBuilder().build();
            builder.setBubbleMetadata(metadata);

            mNotificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private class AutoExpandBubbleTest extends BubblesTestStep {

        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_autoexpand_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_autoexpand_verify;
        }

        @Override
        public int getButtonText() {
            return R.string.bubbles_test_autoexpand_button;
        }

        @Override
        public void performTestAction() {
            Notification.Builder builder = getConversationNotif(getTestTitle());
            Notification.BubbleMetadata metadata =
                    getBubbleBuilder(mShortcuts.get(0)).setAutoExpandBubble(true).build();
            builder.setBubbleMetadata(metadata);

            mNotificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private class CheckOverflowExists extends BubblesTestStep {

        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_overflow_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_overflow_verify;
        }
    }

    private class DismissBubbleShowsInOverflow extends BubblesTestStep {

        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_bubbles_in_overflow_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_bubbles_in_overflow_verify;
        }

        @Override
        public int getButtonText() {
            return R.string.bubbles_test_bubbles_in_overflow_button;
        }

        @Override
        public void performTestAction() {
            Notification.Builder builder =
                    getConversationNotif(getString(getTestTitle()), mShortcuts.get(0));
            builder.setBubbleMetadata(getBubbleBuilder(mShortcuts.get(0)).build());
            mNotificationManager.notify(NOTIFICATION_ID, builder.build());

            builder = getConversationNotif(getString(getTestTitle()), mShortcuts.get(1));
            builder.setBubbleMetadata(getBubbleBuilder(mShortcuts.get(1)).build());
            mNotificationManager.notify(NOTIFICATION_ID + 1, builder.build());

            builder = getConversationNotif(getString(getTestTitle()), mShortcuts.get(2));
            builder.setBubbleMetadata(getBubbleBuilder(mShortcuts.get(2)).build());
            mNotificationManager.notify(NOTIFICATION_ID + 2, builder.build());
        }
    }

    private class PromoteBubbleFromOverflow extends BubblesTestStep {

        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_add_from_overflow_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_add_from_overflow_verify;
        }
    }

    private class CancelRemovesBubblesInOverflow extends BubblesTestStep {

        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_cancel_overflow_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_cancel_overflow_verify;
        }

        @Override
        public int getButtonText() {
            return R.string.bubbles_test_cancel_overflow_button;
        }

        @Override
        public void performTestAction() {
            // Make sure one is a bubble
            Notification.Builder builder = getConversationNotif(getTestTitle());
            Notification.BubbleMetadata metadata = getBubbleBuilder().build();
            builder.setBubbleMetadata(metadata);
            mNotificationManager.notify(NOTIFICATION_ID, builder.build());

            // Cancel the others
            mNotificationManager.cancel(NOTIFICATION_ID + 1);
            mNotificationManager.cancel(NOTIFICATION_ID + 2);
        }
    }

    private class TapNotifWithOverflowBubble extends BubblesTestStep {

        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_tap_notif_with_overflow_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_tap_notif_with_overflow_verify;
        }

        @Override
        public int getButtonText() {
            return R.string.bubbles_test_tap_notif_with_overflow_button;
        }

        @Override
        public void performTestAction() {
            Notification.Builder builder = getConversationNotif(getTestTitle());
            Notification.BubbleMetadata metadata = getBubbleBuilder().build();
            builder.setBubbleMetadata(metadata);
            mNotificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private class PortraitAndLandscape extends BubblesTestStep {

        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_expanded_position_title;
        }

        @Override
        public int getTestDescription() {
            return mIsLargeScreen
                    ? R.string.bubbles_test_expanded_position_largescreen_verify
                    : R.string.bubbles_test_expanded_position_phone_verify;
        }
    }

    private class ScrimBehindExpandedView extends BubblesTestStep {

        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_expanded_scrim_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_expanded_scrim_verify;
        }
    }

    private class ImeInsetsExpandedView extends BubblesTestStep {

        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_ime_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_ime_verify;
        }
    }

    private class MinHeightExpandedView extends BubblesTestStep {

        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_min_height_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_min_height_verify;
        }

        @Override
        public int getButtonText() {
            return R.string.bubbles_test_min_height_button;
        }

        @Override
        public void performTestAction() {
            mNotificationManager.cancelAll();
            Context context = getApplicationContext();
            Intent intent = new Intent(context, BubbleActivity.class);
            intent.putExtra(EXTRA_TEST_NAME, TEST_MIN_HEIGHT);
            final PendingIntent pendingIntent = PendingIntent.getActivity(context, 1, intent,
                    PendingIntent.FLAG_MUTABLE);
            Notification.BubbleMetadata.Builder b = new Notification.BubbleMetadata.Builder(
                    pendingIntent,
                    Icon.createWithResource(getApplicationContext(), R.drawable.ic_android));
            b.setDesiredHeight(0);

            Notification.Builder builder = getConversationNotif(getTestTitle());
            builder.setBubbleMetadata(b.build());
            mNotificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private class MaxHeightExpandedView extends BubblesTestStep {

        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_max_height_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_max_height_verify;
        }

        @Override
        public int getButtonText() {
            return R.string.bubbles_test_max_height_button;
        }

        @Override
        public void performTestAction() {
            WindowMetrics windowMetrics =
                    getSystemService(WindowManager.class).getCurrentWindowMetrics();
            WindowInsets metricInsets = windowMetrics.getWindowInsets();
            Insets insets = metricInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.navigationBars()
                            | WindowInsets.Type.statusBars()
                            | WindowInsets.Type.displayCutout());

            mNotificationManager.cancelAll();
            Context context = getApplicationContext();
            Intent intent = new Intent(context, BubbleActivity.class);
            Bundle extras = new Bundle();
            extras.putString(EXTRA_TEST_NAME, TEST_MAX_HEIGHT);
            // Pass the insets because the bubble'd activity window won't have them.
            extras.putParcelable(EXTRA_INSETS, insets);
            intent.putExtras(extras);
            final PendingIntent pendingIntent = PendingIntent.getActivity(context, 2, intent,
                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.BubbleMetadata.Builder b = new Notification.BubbleMetadata.Builder(
                    pendingIntent,
                    Icon.createWithResource(getApplicationContext(), R.drawable.ic_android));
            b.setDesiredHeight(Short.MAX_VALUE);

            Notification.Builder builder = getConversationNotif(getTestTitle());
            builder.setBubbleMetadata(b.build());
            mNotificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private class LowRamBubbleTest extends BubblesTestStep {
        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_lowram_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_lowram_verify;
        }

        @Override
        public int getButtonText() {
            return R.string.bubbles_test_lowram_button;
        }

        @Override
        public void performTestAction() {
            Notification.Builder builder = getConversationNotif(getTestTitle());
            builder.setBubbleMetadata(getBubbleBuilder().build());

            mNotificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private class BubbleDisabledTest extends BubblesTestStep {
        @Override
        public int getTestTitle() {
            return R.string.bubbles_test_disable_config_title;
        }

        @Override
        public int getTestDescription() {
            return R.string.bubbles_test_disable_config_verify;
        }

        @Override
        public int getButtonText() {
            return R.string.bubbles_test_disable_config_button;
        }

        @Override
        public void performTestAction() {
            Notification.Builder builder = getConversationNotif(getTestTitle());
            builder.setBubbleMetadata(getBubbleBuilder().build());

            mNotificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    /** Creates a shortcut to use for the notifications to be considered conversations */
    private void createShortcuts() {
        mShortcuts.clear();
        for (int i = 0; i < NUM_SHORTCUTS; i++) {
            Context context = getApplicationContext();
            Intent intent = new Intent(context, BubbleActivity.class);
            intent.setAction(ACTION_VIEW);
            intent.putExtra("CONVERSATION", i);
            String name = "BubbleBot" + i;
            Person person = new Person.Builder()
                    .setBot(false)
                    .setIcon(Icon.createWithResource(context, R.drawable.ic_android))
                    .setName(name)
                    .setImportant(true)
                    .build();
            Set<String> categorySet = new ArraySet<>();
            categorySet.add(SHORTCUT_CATEGORY);
            ShortcutInfo shortcut = new ShortcutInfo.Builder(context, name)
                    .setShortLabel(name)
                    .setLongLived(true)
                    .setPerson(person)
                    .setCategories(categorySet)
                    .setIcon(Icon.createWithResource(context, R.drawable.ic_android))
                    .setIntent(intent)
                    .build();
            mShortcuts.add(shortcut);
        }
        ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
        shortcutManager.addDynamicShortcuts(mShortcuts);
    }

    /** Creates a minimally filled out {@link android.app.Notification.BubbleMetadata.Builder} */
    private Notification.BubbleMetadata.Builder getBubbleBuilder() {
        return getBubbleBuilder(mShortcuts.get(0));
    }

    /** Creates a minimally filled out {@link android.app.Notification.BubbleMetadata.Builder} */
    private Notification.BubbleMetadata.Builder getBubbleBuilder(@Nullable ShortcutInfo info) {
        Notification.BubbleMetadata.Builder b;
        if (info == null) {
            Context context = getApplicationContext();
            Intent intent = new Intent(context, BubbleActivity.class);
            final PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                    PendingIntent.FLAG_MUTABLE);

            b = new Notification.BubbleMetadata.Builder(pendingIntent,
                    Icon.createWithResource(getApplicationContext(),
                            R.drawable.ic_android));
        } else {
            b = new Notification.BubbleMetadata.Builder(info.getId());
        }
        b.setDesiredHeight(Short.MAX_VALUE);
        return b;
    }

    /** Creates a {@link Notification.Builder} that is a conversation. */
    private Notification.Builder getConversationNotif(int contentResId) {
        return getConversationNotif(getString(contentResId), mShortcuts.get(0));
    }

    /** Creates a {@link Notification.Builder} that is a conversation. */
    private Notification.Builder getConversationNotif(@NonNull CharSequence content,
            @NonNull ShortcutInfo shortcutInfo) {
        Context context = getApplicationContext();
        Intent intent = new Intent(context, BubbleActivity.class);
        final PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_MUTABLE);

        Person person = new Person.Builder()
                .setName(shortcutInfo.getShortLabel())
                .build();
        RemoteInput remoteInput = new RemoteInput.Builder("reply_key").setLabel("reply").build();
        PendingIntent inputIntent = PendingIntent.getActivity(getApplicationContext(), 0,
                new Intent().setPackage(getApplicationContext().getPackageName()),
                PendingIntent.FLAG_MUTABLE);
        Icon icon = Icon.createWithResource(getApplicationContext(), R.drawable.ic_android);
        Notification.Action replyAction = new Notification.Action.Builder(icon, "Reply",
                inputIntent).addRemoteInput(remoteInput)
                .build();

        return new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_android)
                .setContentTitle("Bubble Notification")
                .setContentText(content)
                .setColor(Color.GREEN)
                .setShortcutId(shortcutInfo.getId())
                .setContentIntent(pendingIntent)
                .setActions(replyAction)
                .setStyle(new Notification.MessagingStyle(person)
                        .setConversationTitle("Bubble Chat")
                        .addMessage(content,
                                SystemClock.currentThreadTimeMillis(), person)
                );
    }
}
