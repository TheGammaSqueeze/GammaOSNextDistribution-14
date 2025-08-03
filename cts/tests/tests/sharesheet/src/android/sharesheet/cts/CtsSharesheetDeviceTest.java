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
package android.sharesheet.cts;

import static android.Manifest.permission.START_ACTIVITIES_FROM_BACKGROUND;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LabeledIntent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Point;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.chooser.ChooserAction;
import android.service.chooser.ChooserTarget;
import android.service.chooser.Flags;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * CtsSharesheetDeviceTest performs automated testing of the sharesheet/chooser API correctness.
 *
 * Some functionality cannot be tested in a fully automated way and is validated through CTS
 * Verifier tests in cts/apps/CtsVerifier/src/com/android/cts/verifier/sharesheet.
 */
@RunWith(AndroidJUnit4.class)
public class CtsSharesheetDeviceTest {

    @Rule(order = 0)
    public CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule(order = 1)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            START_ACTIVITIES_FROM_BACKGROUND);

    public static final String TAG = CtsSharesheetDeviceTest.class.getSimpleName();

    private static final int WAIT_AND_ASSERT_FOUND_TIMEOUT_MS = 5000;
    private static final int WAIT_AND_ASSERT_NOT_FOUND_TIMEOUT_MS = 2500;
    private static final int WAIT_FOR_IDLE_TIMEOUT_MS = 5000;

    private static final int MAX_EXTRA_INITIAL_INTENTS_SHOWN = 2;
    private static final int MAX_EXTRA_CHOOSER_TARGETS_SHOWN = 2;

    private static final String ACTION_INTENT_SENDER_FIRED_ON_CLICK =
            "android.sharesheet.cts.ACTION_INTENT_SENDER_FIRED_ON_CLICK";
    private static final String CHOOSER_CUSTOM_ACTION_BROADCAST_ACTION =
            "android.sharesheet.cts.CHOOSER_CUSTOM_ACTION_BROADCAST_ACTION";
    private static final String CHOOSER_REFINEMENT_BROADCAST_ACTION =
            "android.sharesheet.cts.CHOOSER_REFINEMENT_BROADCAST_ACTION";
    private static final String CTS_DATA_TYPE = "test/cts"; // Special CTS mime type
    private static final String CTS_ALTERNATE_DATA_TYPE = "test/cts_alternate";
    private static final String CATEGORY_CTS_TEST = "CATEGORY_CTS_TEST";

    private Context mContext;
    private Instrumentation mInstrumentation;
    public UiDevice mDevice;
    private UiObject2 mSharesheet;

    private String mPkg, mActivityLabelTesterPkg, mIntentFilterLabelTesterPkg;
    private String mSharesheetPkg;

    private ActivityManager mActivityManager;
    private ShortcutManager mShortcutManager;
    private PackageManager mPackageManager;

    private String mAppLabel,
            mActivityTesterAppLabel, mActivityTesterActivityLabel,
            mIntentFilterTesterAppLabel,
            mIntentFilterTesterIntentFilterLabel,
            mBlacklistLabel,
            mChooserTargetServiceLabel, mSharingShortcutLabel, mExtraChooserTargetsLabelBase,
            mExtraInitialIntentsLabelBase, mPreviewTitle, mPreviewText;
    private Set<ComponentName> mTargetsToExclude;

    /**
     * To validate Sharesheet API and API behavior works as intended, UI tests are required. It is
     * impossible to know how the Sharesheet UI will be modified by end partners, so these tests
     * attempt to assume use the minimum needed assumptions to make the tests work.
     *
     * We cannot assume a scrolling direction or starting point because of potential UI variations.
     * Because of limits of the UiAutomator pipeline only content visible on screen can be tested.
     * These two constraints mean that all automated Sharesheet tests must be for content we
     * reasonably expect to be visible after the sheet is opened without any direct interaction.
     *
     * Extra care is taken to ensure tested content is reasonably visible by:
     * - Splitting tests across multiple Sharesheet calls
     * - Excluding all packages not relevant to the test
     * - Assuming a max of three targets per row of apps
     */

    @Before
    public void init() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mDevice = UiDevice.getInstance(mInstrumentation);
        mContext = mInstrumentation.getTargetContext();

        assumeTrue(
                "Skip test: Device doesn't meet minimum resolution",
                meetsResolutionRequirements(mDevice));
        assumeFalse("Skip test: does not apply to automotive",
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));

        mPkg = mContext.getPackageName();
        mActivityLabelTesterPkg = mPkg + ".packages.activitylabeltester";
        mIntentFilterLabelTesterPkg = mPkg + ".packages.intentfilterlabeltester";
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mShortcutManager = mContext.getSystemService(ShortcutManager.class);
        mPackageManager = mContext.getPackageManager();

        // Load in string to match against
        mBlacklistLabel = mContext.getString(R.string.test_blacklist_label);
        mAppLabel = mContext.getString(R.string.test_app_label);
        mActivityTesterAppLabel = mContext.getString(R.string.test_activity_label_app);
        mActivityTesterActivityLabel = mContext.getString(R.string.test_activity_label_activity);
        mIntentFilterTesterAppLabel = mContext.getString(R.string.test_intent_filter_label_app);
        mIntentFilterTesterIntentFilterLabel =
                mContext.getString(R.string.test_intent_filter_label_intentfilter);
        mChooserTargetServiceLabel = mContext.getString(R.string.test_chooser_target_service_label);
        mSharingShortcutLabel = mContext.getString(R.string.test_sharing_shortcut_label);
        mExtraChooserTargetsLabelBase = mContext.getString(
                R.string.test_extra_chooser_targets_label);
        mExtraInitialIntentsLabelBase = mContext.getString(
                R.string.test_extra_initial_intents_label);
        mPreviewTitle = mContext.getString(R.string.test_preview_title);
        mPreviewText = mContext.getString(R.string.test_preview_text);
        // We want to only show targets in the sheet put forth by the CTS test. In order to do that
        // a special type is used but this doesn't prevent apps registered against */* from showing.
        // To hide */* targets, search for all matching targets and exclude them. Requires
        // permission android.permission.QUERY_ALL_PACKAGES.
        List<ResolveInfo> matchingTargets = mContext.getPackageManager().queryIntentActivities(
                createMatchingIntent(),
                PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_META_DATA
        );

        mTargetsToExclude = matchingTargets.stream()
                .map(ri -> new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name))
                .filter(cn -> {
                    // Exclude our own test targets
                    String pkg = cn.getPackageName();
                    boolean isInternalPkg = pkg.equals(mPkg)
                            || pkg.equals(mActivityLabelTesterPkg)
                            || pkg.equals(mIntentFilterLabelTesterPkg);

                    return !isInternalPkg;
                })
                .collect(Collectors.toSet());

        // We need to know the package used by the system Sharesheet so we can properly
        // wait for the UI to load. Do this by resolving which activity consumes the share intent.
        // There must be a system Sharesheet or fail, otherwise fetch its the package.
        Intent shareIntent = createShareIntent(false, 0, 0);
        ResolveInfo shareRi =
                mPackageManager.resolveActivity(shareIntent, PackageManager.MATCH_DEFAULT_ONLY);

        assertThat(shareRi).isNotNull();
        assertThat(shareRi.activityInfo).isNotNull();

        mSharesheetPkg = shareRi.activityInfo.packageName;
        assertThat(mSharesheetPkg).isNotNull();

        // Finally ensure the device is awake
        mDevice.wakeUp();
    }

    /**
     * To test all features the Sharesheet will need to be opened and closed a few times. To keep
     * total run time low, jam as many tests are possible into each visible test portion.
     */
    @Test
    public void bulkTest1() {
        final CountDownLatch appStarted = new CountDownLatch(1);
        final AtomicReference<Intent> targetLaunchIntent = new AtomicReference<>();

        CtsSharesheetDeviceActivity.setOnIntentReceivedConsumer(intent -> {
            targetLaunchIntent.set(intent);
            appStarted.countDown();
        });

        runAndExecuteCleanupBeforeAnyThrow(() -> {
            launchSharesheet(createShareIntent(false /* do not test preview */,
                    0 /* do not test EIIs */,
                    0 /* do not test ECTs */));
            doesExcludeComponents();
            showsApplicationLabel();
            showsAppAndActivityLabel();
            showsAppAndIntentFilterLabel();
            isChooserTargetServiceDirectShareDisabled();

            // Must be run last, partial completion closes the Sharesheet
            firesIntentSenderWithExtraChosenComponent();

            appStarted.await(1000, TimeUnit.MILLISECONDS);
            assertThat(targetLaunchIntent.get().getType()).isEqualTo(CTS_DATA_TYPE);
            assertThat(targetLaunchIntent.get().getAction()).isEqualTo(Intent.ACTION_SEND);
        }, () -> {
            // The Sharesheet may or may not be open depending on test success, close it if it is.
            closeSharesheetIfNeeded();
        });
    }

    @Test
    public void bulkTest2() {
        runAndExecuteCleanupBeforeAnyThrow(() -> {
            addShortcuts(1);
            launchSharesheet(createShareIntent(false /* do not test preview */,
                    MAX_EXTRA_INITIAL_INTENTS_SHOWN + 1 /* test EIIs at 1 above cap */,
                    MAX_EXTRA_CHOOSER_TARGETS_SHOWN + 1 /* test ECTs at 1 above cap */));
            // Note: EII and ECT cap is not tested here

            showsExtraInitialIntents();
            if (!mActivityManager.isLowRamDevice()) {
                showsExtraChooserTargets();
            }
            isSharingShortcutDirectShareEnabled();

        }, () -> {
            closeSharesheet();
            clearShortcuts();
        });
    }

    /**
     * Testing content preview must be isolated into its own test because in AOSP on small devices
     * the preview can push app and direct share content offscreen because of its height.
     */
    @Test
    public void contentPreviewTest() {
        runAndExecuteCleanupBeforeAnyThrow(() -> {
            launchSharesheet(createShareIntent(true /* test content preview */,
                    0 /* do not test EIIs */,
                    0 /* do not test ECTs */));
            showsContentPreviewTitle();
            showsContentPreviewText();
        }, () -> closeSharesheet());
    }

    // Launch the chooser with an EXTRA_INTENT of type "test/cts" and EXTRA_ALTERNATE_INTENTS with
    // one of "test/cts_alternate". Both of these will match CtsSharesheetDeviceActivity. Choose
    // that target, then in the refinement process, select the "test/cts_alternate" option and
    // then verify that the alternate type is seen by the activity in the end.
    @Test
    public void testRefinementIntentSender() {
        final CountDownLatch broadcastInvoked = new CountDownLatch(1);
        final CountDownLatch chooserCallbackInvoked = new CountDownLatch(1);
        final CountDownLatch appStarted = new CountDownLatch(1);

        final AtomicLong chooserCallbackCountdownAtRefinementStart = new AtomicLong();
        final AtomicReference<Intent> refinementRequest = new AtomicReference<>();

        BroadcastReceiver refinementReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                chooserCallbackCountdownAtRefinementStart.set(chooserCallbackInvoked.getCount());
                refinementRequest.set(intent);
                // Call back the sharesheet to complete the share.
                ResultReceiver resultReceiver = intent.getParcelableExtra(
                        Intent.EXTRA_RESULT_RECEIVER, ResultReceiver.class);

                Intent[] alternates = intent.getParcelableArrayExtra(
                        Intent.EXTRA_ALTERNATE_INTENTS, Intent.class);

                Bundle bundle = new Bundle();
                bundle.putParcelable(Intent.EXTRA_INTENT, alternates[0]);

                resultReceiver.send(Activity.RESULT_OK, bundle);
                broadcastInvoked.countDown();
            }
        };

        BroadcastReceiver chooserCallbackReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                chooserCallbackInvoked.countDown();
            }
        };

        final AtomicReference<String> dataTypeTargetLaunchedWith = new AtomicReference<>();

        CtsSharesheetDeviceActivity.setOnIntentReceivedConsumer(intent -> {
            // Ensure that the app was started with the alternate type chosen by refinement.
            dataTypeTargetLaunchedWith.set(intent.getType());
            appStarted.countDown();
        });

        mContext.registerReceiver(chooserCallbackReceiver,
                new IntentFilter(ACTION_INTENT_SENDER_FIRED_ON_CLICK),
                Context.RECEIVER_EXPORTED);
        mContext.registerReceiver(
                refinementReceiver,
                new IntentFilter(CHOOSER_REFINEMENT_BROADCAST_ACTION),
                Context.RECEIVER_EXPORTED);

        PendingIntent refinement = PendingIntent.getBroadcast(
                mContext,
                1,
                new Intent(CHOOSER_REFINEMENT_BROADCAST_ACTION)
                        .setPackage(mContext.getPackageName()),
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);

        runAndExecuteCleanupBeforeAnyThrow(() -> {
            Intent shareIntent = createShareIntent(false, 0, 0);
            shareIntent.putExtra(Intent.EXTRA_CHOOSER_REFINEMENT_INTENT_SENDER,
                    refinement.getIntentSender());
            Intent alternateIntent = new Intent(Intent.ACTION_SEND);
            alternateIntent.setType(CTS_ALTERNATE_DATA_TYPE);
            shareIntent.putExtra(Intent.EXTRA_ALTERNATE_INTENTS, new Intent[]{alternateIntent});
            launchSharesheet(shareIntent);
            findTextContains(mAppLabel).click();
            assertWithMessage("Refinement broadcast not received").that(
                    broadcastInvoked.await(1000, TimeUnit.MILLISECONDS)).isTrue();
            assertWithMessage("Chosen target callback not received").that(
                    chooserCallbackInvoked.await(1000, TimeUnit.MILLISECONDS)).isTrue();
            assertWithMessage("Share target didn't start").that(
                    appStarted.await(1000, TimeUnit.MILLISECONDS)).isTrue();

            assertThat(chooserCallbackCountdownAtRefinementStart.get()).isEqualTo(1);
            Intent mainIntentForRefinement =
                    refinementRequest.get().getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            assertThat(mainIntentForRefinement.getType()).isEqualTo(CTS_DATA_TYPE);
            Intent[] alternatesForRefinement =
                    refinementRequest.get().getParcelableArrayExtra(
                            Intent.EXTRA_ALTERNATE_INTENTS, Intent.class);
            assertThat(alternatesForRefinement).hasLength(1);
            assertThat(alternatesForRefinement[0].getType()).isEqualTo(CTS_ALTERNATE_DATA_TYPE);
            assertThat(dataTypeTargetLaunchedWith.get()).isEqualTo(CTS_ALTERNATE_DATA_TYPE);
        }, () -> {
            mContext.unregisterReceiver(refinementReceiver);
            mContext.unregisterReceiver(chooserCallbackReceiver);
            closeSharesheet();
        });
    }

    @Test
    public void testRefinementCanOverwritePayloadFields() {
        final CountDownLatch broadcastInvoked = new CountDownLatch(1);
        final CountDownLatch appStarted = new CountDownLatch(1);
        final AtomicReference<Intent> refinementRequest = new AtomicReference<>();
        final AtomicReference<Intent> targetLaunchIntent = new AtomicReference<>();

        final CharSequence originalTextPayload = "original text";
        final CharSequence refinedTextPayload = "refined text";
        final Uri originalMediaPayload = Uri.parse(
                "android.resource://android.sharesheet.cts/" + R.drawable.pre_refinement_payload);
        final Uri refinedMediaPayload = Uri.parse(
                "android.resource://android.sharesheet.cts/" + R.drawable.post_refinement_payload);

        BroadcastReceiver refinementReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refinementRequest.set(intent);
                Intent originalTargetIntent = intent.getParcelableExtra(
                        Intent.EXTRA_INTENT, Intent.class);

                // Replace the payload fields on the initial target.
                Intent refinedTargetIntent = new Intent(originalTargetIntent);
                refinedTargetIntent.putExtra(Intent.EXTRA_TEXT, refinedTextPayload);
                refinedTargetIntent.putExtra(Intent.EXTRA_STREAM, refinedMediaPayload);
                ClipData refinedClipData = new ClipData(
                        null,
                        new String[]{originalTargetIntent.getType()},
                        new ClipData.Item(refinedTextPayload, null, null, refinedMediaPayload));
                refinedTargetIntent.setClipData(refinedClipData);

                // Call back the sharesheet to complete the share.
                ResultReceiver resultReceiver = intent.getParcelableExtra(
                        Intent.EXTRA_RESULT_RECEIVER, ResultReceiver.class);

                Bundle bundle = new Bundle();
                bundle.putParcelable(Intent.EXTRA_INTENT, refinedTargetIntent);

                resultReceiver.send(Activity.RESULT_OK, bundle);
                broadcastInvoked.countDown();
            }
        };

        CtsSharesheetDeviceActivity.setOnIntentReceivedConsumer(intent -> {
            targetLaunchIntent.set(intent);
            appStarted.countDown();
        });

        mContext.registerReceiver(
                refinementReceiver,
                new IntentFilter(CHOOSER_REFINEMENT_BROADCAST_ACTION),
                Context.RECEIVER_EXPORTED);

        PendingIntent refinement = PendingIntent.getBroadcast(
                mContext,
                1,
                new Intent(CHOOSER_REFINEMENT_BROADCAST_ACTION)
                        .setPackage(mContext.getPackageName()),
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);

        runAndExecuteCleanupBeforeAnyThrow(() -> {
            Intent sendIntent = createMatchingIntent();
            sendIntent.putExtra(Intent.EXTRA_TEXT, originalTextPayload);
            sendIntent.putExtra(Intent.EXTRA_STREAM, originalMediaPayload);

            Intent wrappedShareIntent = Intent.createChooser(sendIntent, null, null);
            wrappedShareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            wrappedShareIntent.putExtra(Intent.EXTRA_CHOOSER_REFINEMENT_INTENT_SENDER,
                    refinement.getIntentSender());
            launchSharesheet(wrappedShareIntent);

            findTextContains(mAppLabel).click();
            assertThat(broadcastInvoked.await(1000, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(appStarted.await(1000, TimeUnit.MILLISECONDS)).isTrue();

            Intent originalTargetForRefinement =
                    refinementRequest.get().getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            assertThat(
                    originalTargetForRefinement.getCharSequenceExtra(Intent.EXTRA_TEXT)).isEqualTo(
                    originalTextPayload);
            assertThat(originalTargetForRefinement.getParcelableExtra(Intent.EXTRA_STREAM,
                    Uri.class)).isEqualTo(originalMediaPayload);
            ClipData.Item originalClipItem = originalTargetForRefinement.getClipData().getItemAt(0);
            assertThat(originalClipItem.getText()).isEqualTo(originalTextPayload);
            assertThat(originalClipItem.getUri()).isEqualTo(originalMediaPayload);

            assertThat(
                    targetLaunchIntent.get().getCharSequenceExtra(Intent.EXTRA_TEXT)).isEqualTo(
                    refinedTextPayload);
            assertThat(
                    targetLaunchIntent.get().getParcelableExtra(Intent.EXTRA_STREAM,
                            Uri.class)).isEqualTo(refinedMediaPayload);
            assertThat(targetLaunchIntent.get().getClipData().getItemCount()).isEqualTo(1);
            ClipData.Item refinedClipItem = targetLaunchIntent.get().getClipData().getItemAt(0);
            assertThat(refinedClipItem.getText()).isEqualTo(refinedTextPayload);
            assertThat(refinedClipItem.getUri()).isEqualTo(refinedMediaPayload);
        }, () -> {
            mContext.unregisterReceiver(refinementReceiver);
            closeSharesheet();
        });
    }

    @Test
    public void testShortcutSelection() {
        assumeFalse(
                "Direct share not required on low RAM devices", mActivityManager.isLowRamDevice());

        final String testShortcutId = "TEST_SHORTCUT";
        addShortcuts(createShortcut(testShortcutId));

        final CountDownLatch appStarted = new CountDownLatch(1);
        final AtomicReference<String> shortcutIdTargetLaunchedWith = new AtomicReference<>();

        CtsSharesheetDeviceActivity.setOnIntentReceivedConsumer(intent -> {
            shortcutIdTargetLaunchedWith.set(intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID));
            appStarted.countDown();
        });

        runAndExecuteCleanupBeforeAnyThrow(() -> {
            Intent shareIntent = createShareIntent(false, 0, 0);
            launchSharesheet(shareIntent);
            findTextContains(mSharingShortcutLabel).click();
            assertWithMessage("Shortcut app didn't start").that(
                    appStarted.await(1000, TimeUnit.MILLISECONDS)).isTrue();
            // The intent carries the shortcut ID that was registered with ShortcutManager.
            assertThat(shortcutIdTargetLaunchedWith.get()).isEqualTo(testShortcutId);
        }, () -> closeSharesheet());
    }

    @Test
    public void testShortcutSelectionRefinedToAlternate() {
        assumeFalse(
                "Direct share not required on low RAM devices", mActivityManager.isLowRamDevice());

        final String testShortcutId = "TEST_SHORTCUT";
        addShortcuts(createShortcut(testShortcutId));

        final CountDownLatch broadcastInvoked = new CountDownLatch(1);
        final CountDownLatch chooserCallbackInvoked = new CountDownLatch(1);
        final CountDownLatch appStarted = new CountDownLatch(1);

        final AtomicLong chooserCallbackCountdownAtRefinementStart = new AtomicLong();
        final AtomicReference<Intent> refinementRequest = new AtomicReference<>();

        BroadcastReceiver refinementReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                chooserCallbackCountdownAtRefinementStart.set(chooserCallbackInvoked.getCount());
                refinementRequest.set(intent);
                // Call back the sharesheet to complete the share.
                ResultReceiver resultReceiver = intent.getParcelableExtra(
                        Intent.EXTRA_RESULT_RECEIVER, ResultReceiver.class);

                Intent[] alternates = intent.getParcelableArrayExtra(
                        Intent.EXTRA_ALTERNATE_INTENTS, Intent.class);

                Bundle bundle = new Bundle();
                bundle.putParcelable(Intent.EXTRA_INTENT, alternates[0]);

                resultReceiver.send(Activity.RESULT_OK, bundle);
                broadcastInvoked.countDown();
            }
        };

        BroadcastReceiver chooserCallbackReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                chooserCallbackInvoked.countDown();
            }
        };

        final AtomicReference<String> shortcutIdTargetLaunchedWith = new AtomicReference<>();
        final AtomicReference<String> dataTypeTargetLaunchedWith = new AtomicReference<>();

        CtsSharesheetDeviceActivity.setOnIntentReceivedConsumer(intent -> {
            shortcutIdTargetLaunchedWith.set(intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID));
            dataTypeTargetLaunchedWith.set(intent.getType());
            appStarted.countDown();
        });

        mContext.registerReceiver(chooserCallbackReceiver,
                new IntentFilter(ACTION_INTENT_SENDER_FIRED_ON_CLICK),
                Context.RECEIVER_EXPORTED);

        mContext.registerReceiver(
                refinementReceiver,
                new IntentFilter(CHOOSER_REFINEMENT_BROADCAST_ACTION),
                Context.RECEIVER_EXPORTED);

        PendingIntent refinement = PendingIntent.getBroadcast(
                mContext,
                1,
                new Intent(CHOOSER_REFINEMENT_BROADCAST_ACTION)
                        .setPackage(mContext.getPackageName()),
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);

        runAndExecuteCleanupBeforeAnyThrow(() -> {
            Intent shareIntent = createShareIntent(false, 0, 0);
            shareIntent.putExtra(Intent.EXTRA_CHOOSER_REFINEMENT_INTENT_SENDER,
                    refinement.getIntentSender());
            Intent alternateIntent = new Intent(Intent.ACTION_SEND);
            alternateIntent.setType(CTS_ALTERNATE_DATA_TYPE);
            shareIntent.putExtra(Intent.EXTRA_ALTERNATE_INTENTS, new Intent[]{alternateIntent});
            launchSharesheet(shareIntent);
            findTextContains(mSharingShortcutLabel).click();
            assertThat(broadcastInvoked.await(1000, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(chooserCallbackInvoked.await(1000, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(appStarted.await(1000, TimeUnit.MILLISECONDS)).isTrue();

            Intent mainIntentForRefinement =
                    refinementRequest.get().getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            assertThat(mainIntentForRefinement.getType()).isEqualTo(CTS_DATA_TYPE);
            Intent[] alternatesForRefinement =
                    refinementRequest.get().getParcelableArrayExtra(
                            Intent.EXTRA_ALTERNATE_INTENTS, Intent.class);
            assertThat(alternatesForRefinement).hasLength(1);
            assertThat(alternatesForRefinement[0].getType()).isEqualTo(CTS_ALTERNATE_DATA_TYPE);
            // The intent carries the shortcut ID that was registered with ShortcutManager.
            assertThat(shortcutIdTargetLaunchedWith.get()).isEqualTo(testShortcutId);
            // Ensure that the app was started with the alternate type chosen by refinement.
            assertThat(dataTypeTargetLaunchedWith.get()).isEqualTo(CTS_ALTERNATE_DATA_TYPE);
        }, () -> {
            mContext.unregisterReceiver(refinementReceiver);
            mContext.unregisterReceiver(chooserCallbackReceiver);
            closeSharesheet();
        });
    }

    // Launch the chooser with an EXTRA_INTENT of type "test/cts" and EXTRA_ALTERNATE_INTENTS with
    // one of "test/cts_alternate". Ensure that the "alternate type" app, which only accepts
    // "test/cts_alternate" shows up and can be chosen.
    @Test
    public void testAlternateTargetsShown() {
        final CountDownLatch chooserCallbackInvoked = new CountDownLatch(1);
        final AtomicReference<ComponentName> chosenComponent = new AtomicReference<>();

        BroadcastReceiver chooserCallbackReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                chosenComponent.set(intent.getParcelableExtra(
                        Intent.EXTRA_CHOSEN_COMPONENT, ComponentName.class));
                chooserCallbackInvoked.countDown();
            }
        };

        mContext.registerReceiver(chooserCallbackReceiver,
                new IntentFilter(ACTION_INTENT_SENDER_FIRED_ON_CLICK),
                Context.RECEIVER_EXPORTED);


        runAndExecuteCleanupBeforeAnyThrow(() -> {
            Intent shareIntent = createShareIntent(false, 0, 0);
            Intent alternateIntent = new Intent(Intent.ACTION_SEND);
            alternateIntent.setType(CTS_ALTERNATE_DATA_TYPE);
            shareIntent.putExtra(Intent.EXTRA_ALTERNATE_INTENTS, new Intent[]{alternateIntent});
            launchSharesheet(shareIntent);
            findTextContains(mContext.getString(R.string.test_alternate_app_label)).click();
            assertWithMessage("Chosen component callback not received").that(
                    chooserCallbackInvoked.await(1000, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(chosenComponent.get().getPackageName()).isEqualTo(
                    "android.sharesheet.cts.packages.alternatetype");
            assertThat(chosenComponent.get().getClassName()).isEqualTo(
                    "android.sharesheet.cts.packages.LabelTestActivity");
        }, () -> {
            mContext.unregisterReceiver(chooserCallbackReceiver);
            closeSharesheet();
        });
    }

    @Test
    @ApiTest(apis = "android.content.Intent#EXTRA_CHOOSER_TARGETS")
    public void testChooserTargets() throws Throwable {
        assumeFalse(
                "EXTRA_CHOOSER_TARGETS not required on low RAM devices",
                mActivityManager.isLowRamDevice());

        final CountDownLatch appStarted = new CountDownLatch(1);
        final AtomicReference<Intent> targetLaunchIntent = new AtomicReference<>();

        CtsSharesheetDeviceActivity.setOnIntentReceivedConsumer(intent -> {
            targetLaunchIntent.set(intent);
            appStarted.countDown();
        });

        Bundle targetIntentExtras = new Bundle();
        targetIntentExtras.putBoolean("FROM_CHOOSER_TARGET", true);

        ChooserTarget chooserTarget = new ChooserTarget(
                "ChooserTarget",
                Icon.createWithResource(mContext, R.drawable.black_64x64),
                1f,
                new ComponentName(mPkg, mPkg + ".CtsSharesheetDeviceActivity"),
                targetIntentExtras);

        Intent shareIntent = createShareIntent(false, 0, 0);
        shareIntent.putExtra(Intent.EXTRA_CHOOSER_TARGETS, new ChooserTarget[]{chooserTarget});

        runAndExecuteCleanupBeforeAnyThrow(() -> {
            launchSharesheet(shareIntent);

            UiObject2 chooserTargetButton = mSharesheet.wait(
                    Until.findObject(By.textContains("ChooserTarget")),
                    WAIT_AND_ASSERT_FOUND_TIMEOUT_MS);
            assertWithMessage("Couldn't find app-provided ChooserTarget").that(
                    chooserTargetButton).isNotNull();
            Log.d(TAG, "clicking on the chooser target");
            chooserTargetButton.click();

            assertWithMessage("ChooserTarget app didn't start").that(
                    appStarted.await(1000, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(targetLaunchIntent.get().getBooleanExtra("FROM_CHOOSER_TARGET",
                    false)).isTrue();
        }, this::closeSharesheet);
    }

    @Test
    @ApiTest(apis = "android.content.Intent#EXTRA_CHOOSER_TARGETS")
    public void testChooserTargetsRefinement() {
        assumeFalse(
                "EXTRA_CHOOSER_TARGETS not required on low RAM devices",
                mActivityManager.isLowRamDevice());

        final CountDownLatch broadcastInvoked = new CountDownLatch(1);
        final CountDownLatch appStarted = new CountDownLatch(1);
        final AtomicReference<Intent> targetLaunchIntent = new AtomicReference<>();
        final AtomicReference<Intent> refinementInput = new AtomicReference<>();

        CtsSharesheetDeviceActivity.setOnIntentReceivedConsumer(intent -> {
            targetLaunchIntent.set(intent);
            appStarted.countDown();
        });

        BroadcastReceiver refinementReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Call back the sharesheet to complete the share.
                ResultReceiver resultReceiver = intent.getParcelableExtra(
                        Intent.EXTRA_RESULT_RECEIVER, ResultReceiver.class);

                Intent mainIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
                refinementInput.set(mainIntent);
                Intent refinedIntent = new Intent(mainIntent);
                refinedIntent.putExtra("REFINED", true);

                Bundle bundle = new Bundle();
                bundle.putParcelable(Intent.EXTRA_INTENT, refinedIntent);

                resultReceiver.send(Activity.RESULT_OK, bundle);
                broadcastInvoked.countDown();
            }
        };

        mContext.registerReceiver(
                refinementReceiver,
                new IntentFilter(CHOOSER_REFINEMENT_BROADCAST_ACTION),
                Context.RECEIVER_EXPORTED);

        PendingIntent refinement = PendingIntent.getBroadcast(
                mContext,
                1,
                new Intent(CHOOSER_REFINEMENT_BROADCAST_ACTION)
                        .setPackage(mContext.getPackageName()),
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);

        Bundle targetIntentExtras = new Bundle();
        targetIntentExtras.putBoolean("FROM_CHOOSER_TARGET", true);

        ChooserTarget chooserTarget = new ChooserTarget(
                "ChooserTarget",
                Icon.createWithResource(mContext, R.drawable.black_64x64),
                1f,
                new ComponentName(mPkg, mPkg + ".CtsSharesheetDeviceActivity"),
                targetIntentExtras);

        Intent shareIntent = createShareIntent(false, 0, 0);
        shareIntent.putExtra(Intent.EXTRA_CHOOSER_TARGETS, new ChooserTarget[]{chooserTarget});
        shareIntent.putExtra(Intent.EXTRA_CHOOSER_REFINEMENT_INTENT_SENDER,
                refinement.getIntentSender());

        runAndExecuteCleanupBeforeAnyThrow(() -> {
            launchSharesheet(shareIntent);

            UiObject2 chooserTargetButton = mSharesheet.wait(
                    Until.findObject(By.textContains("ChooserTarget")),
                    WAIT_AND_ASSERT_FOUND_TIMEOUT_MS);
            assertThat(chooserTargetButton).isNotNull();
            Log.d(TAG, "clicking on the chooser target");
            chooserTargetButton.click();

            assertWithMessage("Didn't receive refinement callback").that(
                    broadcastInvoked.await(1000, TimeUnit.MILLISECONDS)).isTrue();
            assertWithMessage("App didn't start after refinement").that(
                    appStarted.await(1000, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(targetLaunchIntent.get().getBooleanExtra("FROM_CHOOSER_TARGET",
                    false)).isTrue();
            assertThat(targetLaunchIntent.get().getBooleanExtra("REFINED", false)).isTrue();
            assertThat(
                    refinementInput.get().getBooleanExtra("FROM_CHOOSER_TARGET", false)).isTrue();
        }, () -> {
            mContext.unregisterReceiver(refinementReceiver);
            closeSharesheet();
        });
    }

    @Test
    @ApiTest(apis = "android.content.Intent#EXTRA_CHOOSER_CUSTOM_ACTIONS")
    public void testCustomAction() {
        final CountDownLatch broadcastInvoked = new CountDownLatch(1);
        BroadcastReceiver customActionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                broadcastInvoked.countDown();
                Log.d(TAG, "custom action invoked");
            }
        };

        mContext.registerReceiver(
                customActionReceiver,
                new IntentFilter(CHOOSER_CUSTOM_ACTION_BROADCAST_ACTION),
                Context.RECEIVER_EXPORTED);

        PendingIntent customAction = PendingIntent.getBroadcast(
                mContext,
                1,
                new Intent(CHOOSER_CUSTOM_ACTION_BROADCAST_ACTION),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);
        PendingIntent fakeCustomAction = PendingIntent.getBroadcast(
                mContext,
                1,
                new Intent("some_action"),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);
        Intent shareIntent = createShareIntent(true /* test content preview */,
                0 /* do not test EIIs */,
                0 /* do not test ECTs */);
        ChooserAction[] actions = new ChooserAction[]{
                createChooserAction("act1", fakeCustomAction),
                createChooserAction("act2", fakeCustomAction),
                createChooserAction("act3", customAction),
                createChooserAction("act4", fakeCustomAction),
                createChooserAction("act5", fakeCustomAction),
        };
        shareIntent.putExtra(Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS, actions);
        runAndExecuteCleanupBeforeAnyThrow(() -> {
            launchSharesheet(shareIntent);
            // 5 actions should be provided to the user, but it's possible that some require
            // scrolling which we currently can't do in this test, so just verify the existence
            // of the first two and click the third, which we expect to be at least partially
            // visible.
            waitAndAssertTextContains(actions[0].getLabel().toString());
            waitAndAssertTextContains(actions[1].getLabel().toString());
            clickText(actions[2].getLabel().toString());

            assertWithMessage("Custom action not invoked").that(
                    broadcastInvoked.await(1000, TimeUnit.MILLISECONDS)).isTrue();
        }, () -> {
            mContext.unregisterReceiver(customActionReceiver);
            closeSharesheet();
        });
    }

    @Test
    @ApiTest(apis = "android.content.Intent#EXTRA_CHOOSER_MODIFY_SHARE_ACTION")
    public void testModifyShare() {
        final CountDownLatch broadcastInvoked = new CountDownLatch(1);
        BroadcastReceiver modifyShareActionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                broadcastInvoked.countDown();
            }
        };
        PendingIntent modifyShare = PendingIntent.getBroadcast(
                mContext,
                1,
                new Intent(CHOOSER_CUSTOM_ACTION_BROADCAST_ACTION),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);

        Intent shareIntent = createShareIntent(true /* test content preview */,
                0 /* do not test EIIs */,
                0 /* do not test ECTs */);
        String modifyShareLabel = "Modify Share";
        ChooserAction modifyShareAction = new ChooserAction.Builder(
                Icon.createWithResource(
                        mInstrumentation.getContext().getPackageName(),
                        R.drawable.black_64x64),
                modifyShareLabel,
                modifyShare
        ).build();
        shareIntent.putExtra(Intent.EXTRA_CHOOSER_MODIFY_SHARE_ACTION, modifyShareAction);

        runAndExecuteCleanupBeforeAnyThrow(
                () -> {
                    mContext.registerReceiver(
                            modifyShareActionReceiver,
                            new IntentFilter(CHOOSER_CUSTOM_ACTION_BROADCAST_ACTION),
                            Context.RECEIVER_EXPORTED);
                    launchSharesheet(shareIntent);
                    clickText(modifyShareLabel);
                    assertWithMessage("Modify share action not invoked").that(
                            broadcastInvoked.await(1000, TimeUnit.MILLISECONDS)).isTrue();
                },
                () -> {
                    mContext.unregisterReceiver(modifyShareActionReceiver);
                    closeSharesheet();
                }
        );
    }

    @Test
    @ApiTest(apis = "android.content.Intent#EXTRA_METADATA_TEXT")
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SHARESHEET_METADATA_EXTRA)
    public void textSharesheetMetadata() {
        CharSequence testMetadataText = "Metadata test text";
        Intent shareIntent = createShareIntent(
                true, 0, 0
        );
        shareIntent.putExtra(Intent.EXTRA_METADATA_TEXT, testMetadataText);
        runAndExecuteCleanupBeforeAnyThrow(
                () -> {
                    launchSharesheet(shareIntent);
                    waitAndAssertTextContains(
                            testMetadataText.toString());
                },
                this::closeSharesheetIfNeeded
        );
    }

    /*
     * Test methods
     */

    /**
     * Tests API compliance for Intent.EXTRA_EXCLUDE_COMPONENTS. This test is necessary for other
     * tests to run as expected.
     *
     * Requires content loaded with permission: android.permission.QUERY_ALL_PACKAGES
     */
    private void doesExcludeComponents() {
        // The excluded component should not be found on screen
        waitAndAssertNoTextContains(mBlacklistLabel);
    }

    /**
     * Tests API behavior compliance for security to always show application label
     */
    private void showsApplicationLabel() {
        // For each app target the providing app's application manifest label should be shown
        waitAndAssertTextContains(mAppLabel);
    }

    /**
     * Tests API behavior compliance to show application and activity label when available
     */
    private void showsAppAndActivityLabel() {
        waitAndAssertTextContains(mActivityTesterAppLabel);
        waitAndAssertTextContains(mActivityTesterActivityLabel);
    }

    /**
     * Tests API behavior compliance to show application and intent filter label when available
     */
    private void showsAppAndIntentFilterLabel() {
        // NOTE: it is not necessary to show any set Activity label if an IntentFilter label is set
        waitAndAssertTextContains(mIntentFilterTesterAppLabel);
        waitAndAssertTextContains(mIntentFilterTesterIntentFilterLabel);
    }

    /**
     * Tests API compliance for Intent.EXTRA_INITIAL_INTENTS
     */
    private void showsExtraInitialIntents() {
        // Should show extra initial intents but must limit them, can't test limit here
        waitAndAssertTextContains(mExtraInitialIntentsLabelBase);
    }

    /**
     * Tests API compliance for Intent.EXTRA_CHOOSER_TARGETS
     */
    private void showsExtraChooserTargets() {
        // Should show chooser targets but must limit them, can't test limit here
        if (mActivityManager.isLowRamDevice()) {
            // The direct share row and EXTRA_CHOOSER_TARGETS should be hidden on low-ram devices
            waitAndAssertNoTextContains(mExtraChooserTargetsLabelBase);
        } else {
            waitAndAssertTextContains(mExtraChooserTargetsLabelBase);
        }
    }

    /**
     * Tests API behavior compliance for Intent.EXTRA_TITLE
     */
    private void showsContentPreviewTitle() {
        waitAndAssertTextContains(mPreviewTitle);
    }

    /**
     * Tests API behavior compliance for Intent.EXTRA_TEXT
     */
    private void showsContentPreviewText() {
        waitAndAssertTextContains(mPreviewText);
    }

    /**
     * Tests API compliance for Intent.EXTRA_CHOSEN_COMPONENT_INTENT_SENDER and related APIs
     * UI assumption: target labels are clickable, clicking opens target
     */
    private void firesIntentSenderWithExtraChosenComponent() {
        // To receive the extra chosen component a target must be clicked. Clicking the target
        // will close the Sharesheet. Run this last in any sequence of tests.

        // First find the target to click. This will fail if the showsApplicationLabel() test fails.
        UiObject2 shareTarget = findTextContains(mAppLabel);
        assertThat(shareTarget).isNotNull();

        ComponentName clickedComponent = new ComponentName(mContext,
                CtsSharesheetDeviceActivity.class);

        final CountDownLatch latch = new CountDownLatch(1);
        final Intent[] response = {null}; // Must be final so use an array

        // Listen for the PendingIntent broadcast on click
        BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                response[0] = intent;
                latch.countDown();
            }
        };
        mContext.registerReceiver(br, new IntentFilter(ACTION_INTENT_SENDER_FIRED_ON_CLICK),
                Context.RECEIVER_EXPORTED_UNAUDITED);

        // Start the event sequence and wait for results
        shareTarget.click();

        runAndExecuteCleanupBeforeAnyThrow(
                () -> latch.await(1000, TimeUnit.MILLISECONDS),
                () -> mContext.unregisterReceiver(br));

        // Finally validate the received Intent
        validateChosenComponentIntent(response[0], clickedComponent);
    }

    private void clickText(String label) {
        clickText(label, false);
    }

    private void clickText(String label, boolean caseSensitive) {
        UiObject2 customAction = mSharesheet.wait(
                Until.findObject(By.text(textContainsPattern(label, caseSensitive))),
                WAIT_AND_ASSERT_FOUND_TIMEOUT_MS);
        assertWithMessage("Couldn't find text '" + label + "'").that(customAction).isNotNull();
        Log.d(TAG, "clicking on the custom action");
        customAction.click();
    }

    private void validateChosenComponentIntent(Intent intent, ComponentName matchingComponent) {
        assertThat(intent).isNotNull();

        assertWithMessage("EXTRA_CHOSEN_COMPONENT not found in callback").that(
                intent.hasExtra(Intent.EXTRA_CHOSEN_COMPONENT)).isTrue();
        Object extra = intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT);
        assertThat(extra).isNotNull();

        assertThat(extra instanceof ComponentName).isTrue();
        ComponentName component = (ComponentName) extra;

        assertThat(component).isEqualTo(matchingComponent);
    }

    /**
     * Tests API behavior compliance for ChooserTargetService
     */
    public void isChooserTargetServiceDirectShareDisabled() {
        // ChooserTargets can take time to load. To account for this:
        // * All non-test ChooserTargetServices shouldn't be loaded because of denylist
        // * waitAndAssert operations have lengthy timeout periods
        // * Last time to run in suite so prior operations reduce wait time


        // ChooserTargetService was deprecated as of API level 30, results should not
        // appear in the list of results.
        waitAndAssertNoTextContains(mChooserTargetServiceLabel);
    }

    /**
     * Tests API behavior compliance for Sharing Shortcuts
     */
    public void isSharingShortcutDirectShareEnabled() {
        if (mActivityManager.isLowRamDevice()) {
            // Ensure direct share is disabled on low ram devices
            waitAndAssertNoTextContains(mSharingShortcutLabel);
        } else {
            // Ensure direct share is enabled
            waitAndAssertTextContains(mSharingShortcutLabel);
        }
    }

    /*
    Setup methods
     */

    /**
     * Included CTS tests can fail for resolutions that are too small. This is because
     * the tests check for visibility of UI elements that are hidden below certain resolutions.
     * Ensure that the device under test has the min necessary screen height in dp. Tests do not
     * fail at any width at or above the CDD minimum of 320dp.
     *
     * Tests have different failure heights:
     * bulkTest1, bulkTest2 fail below ~680 dp in height
     * contentPreviewTest fails below ~640dp in height
     *
     * For safety, check against screen height some buffer on the worst case: 700dp. Most new
     * consumer devices have a height above this.
     *
     * @return if min resolution requirements are met
     */
    private static boolean meetsResolutionRequirements(UiDevice device) {
        final Point displaySizeDp = device.getDisplaySizeDp();
        return displaySizeDp.y >= 700; // dp
    }

    public void addShortcuts(int size) {
        mShortcutManager.addDynamicShortcuts(createShortcuts(size));
    }

    public void addShortcuts(ShortcutInfo... shortcuts) {
        mShortcutManager.addDynamicShortcuts(Arrays.asList(shortcuts));
    }

    public void clearShortcuts() {
        mShortcutManager.removeAllDynamicShortcuts();
    }

    private List<ShortcutInfo> createShortcuts(int size) {
        List<ShortcutInfo> ret = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ret.add(createShortcut("" + i));
        }
        return ret;
    }

    private ShortcutInfo createShortcut(String id) {
        HashSet<String> categories = new HashSet<>();
        categories.add(CATEGORY_CTS_TEST);

        return new ShortcutInfo.Builder(mContext, id)
                .setShortLabel(mSharingShortcutLabel)
                .setIcon(Icon.createWithResource(mContext, R.drawable.black_64x64))
                .setCategories(categories)
                .setIntent(new Intent(Intent.ACTION_DEFAULT)) // must include an Intent w/ action
                .build();
    }

    private void launchSharesheet(Intent shareIntent) {
        mContext.startActivity(shareIntent);
        waitAndAssertPkgVisible(mSharesheetPkg, "Failed to find sharesheet on screen");
        mSharesheet = mDevice.findObject(By.pkg(mSharesheetPkg).depth(0));
        waitForIdle();
    }

    private void closeSharesheetIfNeeded() {
        if (isSharesheetVisible()) closeSharesheet();
    }

    private void closeSharesheet() {
        mDevice.pressBack();
        waitAndAssertPkgNotVisible(mSharesheetPkg);
        waitForIdle();
    }

    private boolean isSharesheetVisible() {
        // This method intentionally does not wait, looks to see if visible on method call
        try {
            return mDevice.findObject(By.pkg(mSharesheetPkg).depth(0)) != null;
        } catch (StaleObjectException e) {
            // If we get a StaleObjectException, it means that the underlying View has
            // already been destroyed, meaning the sharesheet is no longer visible.
            return false;
        }
    }

    private Intent createMatchingIntent() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(CTS_DATA_TYPE);
        return intent;
    }

    private Intent createShareIntent(
            boolean contentPreview,
            int numExtraInitialIntents,
            int numExtraChooserTargets) {
        Intent intent = createMatchingIntent();

        if (contentPreview) {
            intent.putExtra(Intent.EXTRA_TITLE, mPreviewTitle);
            intent.putExtra(Intent.EXTRA_TEXT, mPreviewText);
        }

        PendingIntent pi = PendingIntent.getBroadcast(
                mContext,
                9384 /* number not relevant */,
                new Intent(ACTION_INTENT_SENDER_FIRED_ON_CLICK)
                        .setPackage(mContext.getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        Intent shareIntent = Intent.createChooser(intent, null, pi.getIntentSender());

        // Intent.EXTRA_EXCLUDE_COMPONENTS is used to ensure only test targets appear
        List<ComponentName> list = new ArrayList<>(mTargetsToExclude);
        list.add(new ComponentName(mPkg, mPkg + ".BlacklistTestActivity"));
        shareIntent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS,
                list.toArray(new ComponentName[0]));

        if (numExtraInitialIntents > 0) {
            Intent[] eiis = new Intent[numExtraInitialIntents];
            for (int i = 0; i < eiis.length; i++) {
                Intent eii = new Intent();
                eii.setComponent(new ComponentName(mPkg,
                        mPkg + ".ExtraInitialIntentTestActivity"));

                LabeledIntent labeledEii = new LabeledIntent(eii, mPkg,
                        getExtraInitialIntentsLabel(i),
                        0 /* provide no icon */);

                eiis[i] = labeledEii;
            }

            shareIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, eiis);
        }

        if (numExtraChooserTargets > 0) {
            ChooserTarget[] ects = new ChooserTarget[numExtraChooserTargets];
            for (int i = 0; i < ects.length; i++) {
                ects[i] = new ChooserTarget(
                        getExtraChooserTargetLabel(i),
                        Icon.createWithResource(mContext, R.drawable.black_64x64),
                        1f,
                        new ComponentName(mPkg, mPkg + ".CtsSharesheetDeviceActivity"),
                        new Bundle());
            }

            shareIntent.putExtra(Intent.EXTRA_CHOOSER_TARGETS, ects);
        }

        // Ensure the sheet will launch directly from the test
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        return shareIntent;
    }

    private ChooserAction createChooserAction(String label, PendingIntent pendingIntent) {
        return new ChooserAction.Builder(
                Icon.createWithResource(
                        mInstrumentation.getContext().getPackageName(),
                        R.drawable.black_64x64),
                label,
                pendingIntent
        ).build();
    }

    private String getExtraChooserTargetLabel(int position) {
        return mExtraChooserTargetsLabelBase + " " + position;
    }

    private String getExtraInitialIntentsLabel(int position) {
        return mExtraInitialIntentsLabelBase + " " + position;
    }

    /*
     * UI testing methods
     */

    private void waitForIdle() {
        mDevice.waitForIdle(WAIT_FOR_IDLE_TIMEOUT_MS);
    }

    private void waitAndAssertPkgVisible(String pkg, String failureMessage) {
        waitAndAssertFoundOnDevice(By.pkg(pkg).depth(0), failureMessage);
    }

    private void waitAndAssertPkgNotVisible(String pkg) {
        waitAndAssertNotFoundOnDevice(By.pkg(pkg));
    }

    private void waitAndAssertTextContains(String containsText) {
        BySelector selector = By.text(textContainsPattern(containsText, false));
        String failureMessage = "Failed to find " + containsText + " on screen";
        assertWithMessage(failureMessage).that(
                        mSharesheet.wait(Until.findObject(selector),
                                WAIT_AND_ASSERT_FOUND_TIMEOUT_MS))
                .isNotNull();
    }

    private static Pattern textContainsPattern(String text, boolean caseSensitive) {
        int flags = Pattern.DOTALL;
        if (!caseSensitive) {
            flags |= Pattern.CASE_INSENSITIVE;
        }
        return Pattern.compile(String.format("^.*%s.*$", Pattern.quote(text)), flags);
    }

    /**
     * waitAndAssertNoTextContains waits for UI containing the given text within sharesheet to be
     * hidden, validates that it's indeed gone without waiting more and returns. This means if the
     * UI wasn't visible to start with the method will return without no timeout. Take care to call
     * this method only once there's reason to think the UI is in the right state for testing.
     */
    private void waitAndAssertNoTextContains(String containsText) {
        BySelector selector = By.textContains(containsText);
        String failureMessage = "Found text '" + containsText + "' but did not expect to";
        mSharesheet.wait(Until.gone(selector), WAIT_AND_ASSERT_NOT_FOUND_TIMEOUT_MS);
        assertWithMessage(failureMessage).that(mSharesheet.findObject(selector)).isNull();
    }

    /**
     * Same as waitAndAssertFound but searching the entire device UI.
     */
    private void waitAndAssertFoundOnDevice(BySelector selector, String failureMessage) {
        assertWithMessage(failureMessage).that(mDevice.wait(Until.findObject(selector),
                WAIT_AND_ASSERT_FOUND_TIMEOUT_MS)).isNotNull();
    }

    /**
     * Same as waitAndAssertNotFound() but searching the entire device UI.
     */
    private void waitAndAssertNotFoundOnDevice(BySelector selector) {
        mDevice.wait(Until.gone(selector), WAIT_AND_ASSERT_NOT_FOUND_TIMEOUT_MS);
        assertThat(mDevice.findObject(selector)).isNull();
    }

    /**
     * findTextContains uses logic similar to waitAndAssertFound to locate UI objects that contain
     * the provided String.
     *
     * @param containsText the String to search for, note this is not an exact match only contains
     * @return UiObject2 that can be used, for example, to execute a click
     */
    private UiObject2 findTextContains(String containsText) {
        return mSharesheet.wait(Until.findObject(By.textContains(containsText)),
                WAIT_AND_ASSERT_FOUND_TIMEOUT_MS);
    }

    /**
     * A {@link Runnable}-like interface that's declared to throw checked exceptions. This is
     * provided for convenience in writing inline ("lambda") blocks, so that test code doesn't need
     * extra boilerplate to handle every possible site of a checked exception (since we're going to
     * end up propagating these exceptions as test failures anyways).
     */
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    /**
     * Perform the requested {@code execution} (which may throw), but then perform the requested
     * {@code cleanup} (whether or not the main execution succeeded) before potentially throwing any
     * exception from the main execution. This is similar to the normal `try/finally` construct,
     * except that the `finally` (or `cleanup`) step is executed <em>before</em> any stack-unwinding
     * to try to catch the exception. Note that any re-thrown exception is wrapped as a
     * {@link RuntimeException} so that clients can skip the checked-exception boilerplate.
     * TODO: it may be possible to move all our cleanup steps to an `@After` method and avoid this
     * unusual construct, but we'd have to refactor to unify the cleanup logic across all tests.
     */
    private static void runAndExecuteCleanupBeforeAnyThrow(
            ThrowingRunnable execution, Runnable cleanup) {
        Throwable exceptionToRethrow = null;
        try {
            execution.run();
        } catch (Throwable mainExecutionException) {
            exceptionToRethrow = mainExecutionException;
        } finally {
            try {
                cleanup.run();
            } catch (Throwable cleanupException) {
                if (exceptionToRethrow == null) {
                    exceptionToRethrow = cleanupException;
                } else {
                    exceptionToRethrow.addSuppressed(cleanupException);
                }
            }

            if (exceptionToRethrow != null) {
                throw new RuntimeException(exceptionToRethrow);
            }
        }
    }
}
