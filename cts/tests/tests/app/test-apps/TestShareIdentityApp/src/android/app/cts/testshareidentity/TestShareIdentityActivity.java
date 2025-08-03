/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.app.cts.testshareidentity;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.BroadcastOptions;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * Activity running in a separate package to facilitate testing of {@link
 * android.app.ActivityOptions#setShareIdentityEnabled(boolean)}. {@link
 * android.app.cts.ShareIdentityTest} starts this activity with the test parameters as extras in
 * the delivered Intent, then this activity configures the appropriate environment for the test
 * and calls back into {@link android.app.cts.ShareIdentityTest.ShareIdentityTestActivity} to
 * ensure this package's identity is only shared when expected.
 */
public class TestShareIdentityActivity extends Activity {
    private static final String TAG = "TestShareIdentityActivity";
    /**
     * Activity within {@link android.app.cts.ShareIdentityTest} that will be launched by
     * this activity to verify this package's identity is only shared when expected.
     */
    private static final ComponentName SHARE_IDENTITY_ACTIVITY = new ComponentName(
            "android.app.cts", "android.app.cts.ShareIdentityTest$ShareIdentityTestActivity");

    /**
     * Activity within {@link android.app.cts.ShareIdentityTest} that will be launched by
     * this activity to verify this package's initial caller identity is only shared when expected.
     */
    private static final ComponentName ACTIVITY_INITIAL_CALLER_SHARE_IDENTITY_ACTIVITY =
            new ComponentName("android.app.cts",
            "android.app.cts.ShareIdentityTest$ActivityInitialCallerShareIdentityTestActivity");

    /**
     * Activity within {@link android.app.cts.ShareIdentityTest} that will be launched by
     * this activity to verify this package's new intent #getCurrentCaller identity is only shared
     * when expected.
     */
    static final ComponentName ACTIVITY_NEW_INTENT_GET_CALLER_SHARE_IDENTITY_ACTIVITY =
            new ComponentName("android.app.cts",
            "android.app.cts.ShareIdentityTest"
                    + "$ActivityNewIntentGetCurrentCallerShareIdentityTestActivity");

    /**
     * Activity within {@link android.app.cts.ShareIdentityTest} that will be launched by
     * this activity to verify this package's new intent overload caller identity is only shared
     * when expected.
     */
    static final ComponentName ACTIVITY_NEW_INTENT_OVERLOAD_CALLER_SHARE_IDENTITY_ACTIVITY =
            new ComponentName("android.app.cts",
                    "android.app.cts.ShareIdentityTest"
                            + "$ActivityNewIntentOverloadCallerShareIdentityTestActivity");

    /**
     * Receiver within {@link android.app.cts.ShareIdentityTest} that is registered in the
     * manifest to receive broadcasts from this activity to verify the package's identity
     * is only shared when expected.
     */
    private static final ComponentName SHARED_IDENTITY_RECEIVER = new ComponentName(
            "android.app.cts", "android.app.cts.ShareIdentityTest$ShareIdentityTestReceiver");

    /**
     * Key used to receive and send the unique ID of the current test.
     */
    static final String TEST_ID_KEY = "testId";

    /**
     * Key used to receive the current test case.
     */
    static final String TEST_CASE_KEY = "testCase";
    /**
     * Test case to verify the launching app's identity is not shared when the activity
     * is not launched with {@link android.app.ActivityOptions}.
     */
    private static final int DEFAULT_SHARING_TEST_CASE = 0;
    /**
     * Test case to verify the launching app's identity is shared when the activity is launched
     * with {@link android.app.ActivityOptions#setShareIdentityEnabled(boolean)} set to
     * {@code true}.
     */
    private static final int EXPLICIT_OPT_IN_TEST_CASE = 1;
    /**
     * Test case to verify the launching app's identity is not shared when the activity is launched
     * with {@link android.app.ActivityOptions#setShareIdentityEnabled(boolean)} set to
     * {@code false}.
     */
    private static final int EXPLICIT_OPT_OUT_TEST_CASE = 2;
    /**
     * Test case to verify the sharing of an app's identity is not impacted by launching an
     * activity with {@link Activity#startActivityForResult(Intent, int)} since this method does
     * expose the app's identity from {@link Activity#getCallingPackage()}.
     */
    private static final int START_ACTIVITY_FOR_RESULT_TEST_CASE = 3;
    /**
     * Test case to verify the broadcasting app's identity is shared with a runtime receiver when
     * the broadcast is sent with {@link
     * android.app.BroadcastOptions#setShareIdentityEnabled(boolean)} set to {@code true}.
     */
    private static final int SEND_BROADCAST_RUNTIME_RECEIVER_OPT_IN_TEST_CASE = 4;
    /**
     * Test case to verify the broadcasting app's identity is shared with a manifest receiver when
     * the broadcast is sent with {@link
     * android.app.BroadcastOptions#setShareIdentityEnabled(boolean)} set to {@code true}.
     */
    private static final int SEND_BROADCAST_MANIFEST_RECEIVER_OPT_IN_TEST_CASE = 5;
    /**
     * Test case to verify the broadcasting app's identity is not shared with a runtime receiver
     * when the broadcast is sent with {@link
     * android.app.BroadcastOptions#setShareIdentityEnabled(boolean)} set to {@code false}.
     */
    private static final int SEND_BROADCAST_RUNTIME_RECEIVER_OPT_OUT_TEST_CASE = 6;
    /**
     * Test case to verify the broadcasting app's identity is not shared with a manifest receiver
     * when the broadcast is sent with {@link
     * android.app.BroadcastOptions#setShareIdentityEnabled(boolean)} set to {@code false}.
     */
    private static final int SEND_BROADCAST_MANIFEST_RECEIVER_OPT_OUT_TEST_CASE = 7;
    /**
     * Test case to verify the broadcasting app's identity is not shared with a runtime receiver
     * when the broadcast is not sent with {@link android.app.BroadcastOptions}.
     */
    private static final int SEND_BROADCAST_RUNTIME_RECEIVER_DEFAULT_TEST_CASE = 8;
    /**
     * Test case to verify the broadcasting app's identity is not shared with a manifest receiver
     * when the broadcast is not sent with {@link android.app.BroadcastOptions}.
     */
    private static final int SEND_BROADCAST_MANIFEST_RECEIVER_DEFAULT_TEST_CASE = 9;
    /**
     * Test case to verify the broadcasting app's identity is shared with a runtime receiver when
     * the ordered broadcast is sent with {@link
     * android.app.BroadcastOptions#setShareIdentityEnabled(boolean)} set to {@code true}.
     */
    private static final int SEND_ORDERED_BROADCAST_RUNTIME_RECEIVER_OPT_IN_TEST_CASE = 10;
    /**
     * Test case to verify the broadcasting app's identity is shared with a manifest receiver when
     * the ordered broadcast is sent with {@link
     * android.app.BroadcastOptions#setShareIdentityEnabled(boolean)} set to {@code true}.
     */
    private static final int SEND_ORDERED_BROADCAST_MANIFEST_RECEIVER_OPT_IN_TEST_CASE = 11;
    /**
     * Test case to verify the broadcasting app's identity is not shared with a runtime receiver
     * when the ordered broadcast is sent with {@link
     * android.app.BroadcastOptions#setShareIdentityEnabled(boolean)} set to {@code false}.
     */
    private static final int SEND_ORDERED_BROADCAST_RUNTIME_RECEIVER_OPT_OUT_TEST_CASE = 12;
    /**
     * Test case to verify the broadcasting app's identity is not shared with a manifest receiver
     * when the ordered broadcast is sent with {@link
     * android.app.BroadcastOptions#setShareIdentityEnabled(boolean)} set to {@code false}.
     */
    private static final int SEND_ORDERED_BROADCAST_MANIFEST_RECEIVER_OPT_OUT_TEST_CASE = 13;
    /**
     * Test case to verify the launching app's initial caller identity is not shared when the
     * activity is not launched with {@link android.app.ActivityOptions}.
     */
    private static final int DEFAULT_SHARING_TEST_CASE_ACTIVITY_INITIAL_CALLER = 14;
    /**
     * Test case to verify the launching app's initial caller identity is shared when the activity
     * is launched with {@link android.app.ActivityOptions#setShareIdentityEnabled(boolean)} set to
     * {@code true}.
     */
    private static final int EXPLICIT_OPT_IN_TEST_CASE_ACTIVITY_INITIAL_CALLER = 15;
    /**
     * Test case to verify the launching app's initial caller identity is not shared when the
     * activity is launched with
     * {@link android.app.ActivityOptions#setShareIdentityEnabled(boolean)} set to {@code false}.
     */
    private static final int EXPLICIT_OPT_OUT_TEST_CASE_ACTIVITY_INITIAL_CALLER = 16;
    /**
     * Test case to verify the sharing of an app's initial caller identity is not impacted by
     * launching an activity with {@link Activity#startActivityForResult(Intent, int)} since this
     * method does expose the app's identity from {@link Activity#getCallingPackage()}.
     */
    private static final int START_ACTIVITY_FOR_RESULT_TEST_CASE_ACTIVITY_INITIAL_CALLER = 17;
    /**
     * Test case to verify the launching app's new intent #getCurrentCaller identity is not shared
     * when the activity is not launched with {@link android.app.ActivityOptions}.
     *
     * <p>To reach the new intent code path, the test launches the activity twice:
     * <ul>
     *     <li>The first time, the activity is launched with the caller identity shared via
     *     {@link android.app.ActivityOptions#setShareIdentityEnabled(boolean)} set to {@code true}.
     *     <li>The second time, the activity is launched without
     *     {@link android.app.ActivityOptions}.
     * </ul>
     *
     * This verifies #getCurrentCaller correctly retrieves the intended caller.
     */
    static final int DEFAULT_SHARING_TEST_CASE_ACTIVITY_NEW_INTENT_GET_CURRENT_CALLER = 18;
    /**
     * Test case to verify the launching app's new intent #getCurrentCaller identity is shared when
     * the activity is launched with
     * {@link android.app.ActivityOptions#setShareIdentityEnabled(boolean)} set to {@code true}.
     *
     * <p>To reach the new intent code path, the test launches the activity twice:
     * <ul>
     *     <li>The first time, the activity is launched with the caller identity not shared via
     *     {@link android.app.ActivityOptions#setShareIdentityEnabled(boolean)} set to
     *     {@code false}.
     *     <li>The second time, the activity is launched with the caller identity shared via
     *     {@link android.app.ActivityOptions#setShareIdentityEnabled(boolean)} set to {@code true}.
     * </ul>
     *
     * This verifies #getCurrentCaller correctly retrieves the intended caller.
     */
    static final int EXPLICIT_OPT_IN_TEST_CASE_ACTIVITY_NEW_INTENT_GET_CURRENT_CALLER = 19;
    /**
     * Test case to verify the launching app's new intent #getCurrentCaller identity is not shared
     * when the activity is launched with
     * {@link android.app.ActivityOptions#setShareIdentityEnabled(boolean)} set to {@code false}.
     *
     * <p>To reach the new intent code path, the test launches the activity twice:
     * <ul>
     *     <li>The first time, the activity is launched with the caller identity shared via
     *     {@link android.app.ActivityOptions#setShareIdentityEnabled(boolean)} set to {@code true}.
     *     <li>The second time, the activity is launched with the caller identity not shared via
     *     {@link android.app.ActivityOptions#setShareIdentityEnabled(boolean)} set to
     *     {@code false}.
     * </ul>
     *
     * This verifies #getCurrentCaller correctly retrieves the intended caller.
     */
    static final int EXPLICIT_OPT_OUT_TEST_CASE_ACTIVITY_NEW_INTENT_GET_CURRENT_CALLER = 20;
    /**
     * Test case to verify the launching app's new intent caller identity in
     * #onNewIntent(Intent, ComponentCaller) is not shared when the activity is not launched with
     * {@link android.app.ActivityOptions}.
     *
     * <p>To reach the new intent code path, the test launches the activity twice:
     * <ul>
     *     <li>The first time, the activity is launched with the caller identity shared via
     *     {@link android.app.ActivityOptions#setShareIdentityEnabled(boolean)} set to {@code true}.
     *     <li>The second time, the activity is launched without
     *     {@link android.app.ActivityOptions}.
     * </ul>
     *
     * This verifies #onNewIntent(Intent, ComponentCaller) correctly retrieves the intended caller.
     */
    static final int DEFAULT_SHARING_TEST_CASE_ACTIVITY_NEW_INTENT_OVERLOAD_CALLER = 21;
    /**
     * Test case to verify the launching app's new intent caller identity in
     * #onNewIntent(Intent, ComponentCaller) is shared when the activity is launched with
     * {@link android.app.ActivityOptions#setShareIdentityEnabled(boolean)} set to {@code true}.
     *
     * <p>To reach the new intent code path, the test launches the activity twice:
     * <ul>
     *     <li>The first time, the activity is launched with the caller identity not shared via
     *     {@link android.app.ActivityOptions#setShareIdentityEnabled(boolean)} set to
     *     {@code false}.
     *     <li>The second time, the activity is launched with the caller identity shared via
     *     {@link android.app.ActivityOptions#setShareIdentityEnabled(boolean)} set to {@code true}.
     * </ul>
     *
     * This verifies #onNewIntent(Intent, ComponentCaller) correctly retrieves the intended caller.
     */
    static final int EXPLICIT_OPT_IN_TEST_CASE_ACTIVITY_NEW_INTENT_OVERLOAD_CALLER = 22;
    /**
     * Test case to verify the launching app's new intent caller identity in
     * #onNewIntent(Intent, ComponentCaller) is not shared when the activity is launched with
     * {@link android.app.ActivityOptions#setShareIdentityEnabled(boolean)} set to {@code false}.
     *
     * <p>To reach the new intent code path, the test launches the activity twice:
     * <ul>
     *     <li>The first time, the activity is launched with the caller identity shared via
     *     {@link android.app.ActivityOptions#setShareIdentityEnabled(boolean)} set to {@code true}.
     *     <li>The second time, the activity is launched with the caller identity not shared via
     *     {@link android.app.ActivityOptions#setShareIdentityEnabled(boolean)} set to
     *     {@code false}.
     * </ul>
     *
     * This verifies #onNewIntent(Intent, ComponentCaller) correctly retrieves the intended caller.
     */
    static final int EXPLICIT_OPT_OUT_TEST_CASE_ACTIVITY_NEW_INTENT_OVERLOAD_CALLER = 23;
    /**
     * Test case to verify the launching app's result #getCurrentCaller identity is not shared when
     * the activity is not launched with {@link android.app.ActivityOptions}.
     */
    private static final int DEFAULT_SHARING_TEST_CASE_ACTIVITY_RESULT_GET_CURRENT_CALLER = 24;
    /**
     * Test case to verify the launching app's result caller identity in
     * #onActivityResult(int, int, Intent, ComponentCaller) is not shared when the activity is not
     * launched with {@link android.app.ActivityOptions}.
     */
    private static final int DEFAULT_SHARING_TEST_CASE_ACTIVITY_RESULT_OVERLOAD_CALLER = 26;
    /**
     * Action for which the runtime receiver registers in the app driving the test.
     */
    private static final String TEST_BROADCAST_RUNTIME_ACTION =
            "android.app.cts.SHARE_IDENTITY_TEST_RUNTIME_ACTION";
    /**
     * Action for which the manifest receiver registers in the app driving the test.
     */
    private static final String TEST_BROADCAST_MANIFEST_ACTION =
            "android.app.cts.SHARE_IDENTITY_TEST_MANIFEST_ACTION";

    @Override
    public void onStart() {
        super.onStart();
        int testId = getIntent().getIntExtra(TEST_ID_KEY, -1);
        int testCase = getIntent().getIntExtra(TEST_CASE_KEY, -1);
        Intent intent = new Intent();
        intent.setComponent(getShareIdentityActivity(testCase));
        intent.putExtra(TEST_ID_KEY, testId);
        intent.putExtra(TEST_CASE_KEY, testCase);
        Bundle bundle;
        switch (testCase) {
            case DEFAULT_SHARING_TEST_CASE, DEFAULT_SHARING_TEST_CASE_ACTIVITY_INITIAL_CALLER:
                startActivity(intent);
                break;
            case EXPLICIT_OPT_IN_TEST_CASE, EXPLICIT_OPT_IN_TEST_CASE_ACTIVITY_INITIAL_CALLER,
                    // To catch the correct behavior for the new intent caller, the tests do the
                    // opposite sharing for the initial caller. For example, in this case the new
                    // intent caller's identity isn't meant to be shared, so here the initial caller
                    // does the opposite and shares their identity.
                    DEFAULT_SHARING_TEST_CASE_ACTIVITY_NEW_INTENT_GET_CURRENT_CALLER,
                    DEFAULT_SHARING_TEST_CASE_ACTIVITY_NEW_INTENT_OVERLOAD_CALLER,
                    EXPLICIT_OPT_OUT_TEST_CASE_ACTIVITY_NEW_INTENT_GET_CURRENT_CALLER,
                    EXPLICIT_OPT_OUT_TEST_CASE_ACTIVITY_NEW_INTENT_OVERLOAD_CALLER:
                bundle = ActivityOptions.makeBasic().setShareIdentityEnabled(true).toBundle();
                startActivity(intent, bundle);
                break;
            case EXPLICIT_OPT_OUT_TEST_CASE, EXPLICIT_OPT_OUT_TEST_CASE_ACTIVITY_INITIAL_CALLER,
                    // To catch the correct behavior for the new intent caller, the tests do the
                    // opposite sharing for the initial caller. For example, in this case the new
                    // intent caller's identity is meant to be shared, so here the initial caller
                    // does the opposite and doesn't share their identity.
                    EXPLICIT_OPT_IN_TEST_CASE_ACTIVITY_NEW_INTENT_GET_CURRENT_CALLER,
                    EXPLICIT_OPT_IN_TEST_CASE_ACTIVITY_NEW_INTENT_OVERLOAD_CALLER:
                bundle = ActivityOptions.makeBasic().setShareIdentityEnabled(false).toBundle();
                startActivity(intent, bundle);
                break;
            case START_ACTIVITY_FOR_RESULT_TEST_CASE,
                    START_ACTIVITY_FOR_RESULT_TEST_CASE_ACTIVITY_INITIAL_CALLER:
                startActivityForResult(intent, 0);
                break;
            case DEFAULT_SHARING_TEST_CASE_ACTIVITY_RESULT_GET_CURRENT_CALLER,
                    DEFAULT_SHARING_TEST_CASE_ACTIVITY_RESULT_OVERLOAD_CALLER:
                intent.setComponent(null);
                setResult(RESULT_OK, intent);
                break;
            case SEND_BROADCAST_RUNTIME_RECEIVER_OPT_IN_TEST_CASE:
                testSendBroadcast(testId, false, false, true, true);
                break;
            case SEND_BROADCAST_MANIFEST_RECEIVER_OPT_IN_TEST_CASE:
                testSendBroadcast(testId, false, true, true, true);
                break;
            case SEND_BROADCAST_RUNTIME_RECEIVER_OPT_OUT_TEST_CASE:
                testSendBroadcast(testId, false, false, false, true);
                break;
            case SEND_BROADCAST_MANIFEST_RECEIVER_OPT_OUT_TEST_CASE:
                testSendBroadcast(testId, false, true, false, true);
                break;
            case SEND_BROADCAST_RUNTIME_RECEIVER_DEFAULT_TEST_CASE:
                testSendBroadcast(testId, false, true, false, false);
                break;
            case SEND_BROADCAST_MANIFEST_RECEIVER_DEFAULT_TEST_CASE:
                testSendBroadcast(testId, false, false, false, false);
                break;
            case SEND_ORDERED_BROADCAST_RUNTIME_RECEIVER_OPT_IN_TEST_CASE:
                testSendBroadcast(testId, true, false, true, true);
                break;
            case SEND_ORDERED_BROADCAST_MANIFEST_RECEIVER_OPT_IN_TEST_CASE:
                testSendBroadcast(testId, true, true, true, true);
                break;
            case SEND_ORDERED_BROADCAST_RUNTIME_RECEIVER_OPT_OUT_TEST_CASE:
                testSendBroadcast(testId, true, false, false, true);
                break;
            case SEND_ORDERED_BROADCAST_MANIFEST_RECEIVER_OPT_OUT_TEST_CASE:
                testSendBroadcast(testId, true, true, false, true);
                break;
            default:
                Log.e(TAG, "Unexpected test case received: " + testCase);
        }
        finish();
    }

    static ComponentName getShareIdentityActivity(int testCase) {
        return switch (testCase) {
            // Initial caller activity
            case DEFAULT_SHARING_TEST_CASE_ACTIVITY_INITIAL_CALLER,
                    EXPLICIT_OPT_IN_TEST_CASE_ACTIVITY_INITIAL_CALLER,
                    EXPLICIT_OPT_OUT_TEST_CASE_ACTIVITY_INITIAL_CALLER,
                    START_ACTIVITY_FOR_RESULT_TEST_CASE_ACTIVITY_INITIAL_CALLER ->
                    ACTIVITY_INITIAL_CALLER_SHARE_IDENTITY_ACTIVITY;

            // New intent #getCurrentCaller activity
            case DEFAULT_SHARING_TEST_CASE_ACTIVITY_NEW_INTENT_GET_CURRENT_CALLER,
                    EXPLICIT_OPT_IN_TEST_CASE_ACTIVITY_NEW_INTENT_GET_CURRENT_CALLER,
                    EXPLICIT_OPT_OUT_TEST_CASE_ACTIVITY_NEW_INTENT_GET_CURRENT_CALLER ->
                    ACTIVITY_NEW_INTENT_GET_CALLER_SHARE_IDENTITY_ACTIVITY;

            // New intent activity with an overload
            case DEFAULT_SHARING_TEST_CASE_ACTIVITY_NEW_INTENT_OVERLOAD_CALLER,
                    EXPLICIT_OPT_IN_TEST_CASE_ACTIVITY_NEW_INTENT_OVERLOAD_CALLER,
                    EXPLICIT_OPT_OUT_TEST_CASE_ACTIVITY_NEW_INTENT_OVERLOAD_CALLER ->
                    ACTIVITY_NEW_INTENT_OVERLOAD_CALLER_SHARE_IDENTITY_ACTIVITY;

            default -> SHARE_IDENTITY_ACTIVITY;
        };
    }

    /**
     * Test method that sends a broadcast to the app driving the test with the appropriate
     * configuration for the test.
     *
     * @param testId             the ID of the current test
     * @param orderedBroadcast   whether the broadcast should be sent as ordered
     * @param toManifestReceiver whether the broadcast should be sent to a manifest receiver
     * @param shareIdentity      whether this app's identity should be shared with the receiver
     * @param useOptions         whether {@link android.app.BundleOptions} should be used when
     *                           sending
     *                           the broadcast
     */
    private void testSendBroadcast(int testId, boolean orderedBroadcast, boolean toManifestReceiver,
            boolean shareIdentity, boolean useOptions) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.putExtra(TEST_ID_KEY, testId);
        if (toManifestReceiver) {
            broadcastIntent.setAction(TEST_BROADCAST_MANIFEST_ACTION);
            broadcastIntent.setComponent(SHARED_IDENTITY_RECEIVER);
        } else {
            broadcastIntent.setAction(TEST_BROADCAST_RUNTIME_ACTION);
        }
        BroadcastOptions broadcastOptions = BroadcastOptions.makeBasic();
        broadcastOptions.setShareIdentityEnabled(shareIdentity);
        if (useOptions) {
            if (orderedBroadcast) {
                sendOrderedBroadcast(broadcastIntent, null, broadcastOptions.toBundle());
            } else {
                sendBroadcast(broadcastIntent, null, broadcastOptions.toBundle());
            }
        } else {
            if (orderedBroadcast) {
                sendOrderedBroadcast(broadcastIntent, null);
            } else {
                sendBroadcast(broadcastIntent, null);
            }
        }
    }
}
