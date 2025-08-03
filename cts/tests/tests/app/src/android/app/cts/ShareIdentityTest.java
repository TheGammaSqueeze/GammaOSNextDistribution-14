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
package android.app.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.ComponentCaller;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.UiDeviceUtils;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import libcore.util.HexEncoding;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests to verify activities and receivers only have access to the uid and package name of the
 * launching / sending app when that app has opted-in to sharing its identify via either
 * {@link android.app.ActivityOptions#setShareIdentityEnabled(boolean)} or {@link
 * android.app.BroadcastOptions#setShareIdentityEnabled(boolean)}, respectively.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ShareIdentityTest {
    private static final String TAG = "ShareIdentityTest";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    /**
     * The test package running in its own uid to verify its identity is only shared with {@link
     * ShareIdentityTestActivity} when expected.
     */
    private static final String TEST_PACKAGE = "android.app.cts.testshareidentity";
    /**
     * The test component in a separate package that can be configured to invoke {@link
     * ShareIdentityTestActivity} with and without identity sharing enabled to verify the
     * identity of the launching app is only available when that app has opted-in to
     * sharing its identity.
     */
    private static final ComponentName TEST_COMPONENT = new ComponentName(
            TEST_PACKAGE, TEST_PACKAGE + ".TestShareIdentityActivity");
    /**
     * The SHA-256 digest of the DER encoding of the rotated signing certificate for the test
     * package.
     */
    private static final String TEST_PACKAGE_ROTATED_SIGNER_DIGEST =
            "d78405f761ff6236cc9b570347a570aba0c62a129a3ac30c831c64d09ad95469";
    /**
     * The SHA-256 digest of the DER encoding of the original signing certificate for the test
     * package.
     */
    private static final String TEST_PACKAGE_ORIGINAL_SIGNER_DIGEST =
            "6a8b96e278e58f62cfe3584022cec1d0527fcb85a9e5d2e1694eb0405be5b599";

    /**
     * Key used to pass the int test case as an extra in the {@code Intent} to the {@link
     * #TEST_COMPONENT}.
     */
    private static final String TEST_CASE_KEY = "testCase";
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
    private static final int DEFAULT_SHARING_TEST_CASE_ACTIVITY_NEW_INTENT_GET_CURRENT_CALLER = 18;
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
    private static final int EXPLICIT_OPT_IN_TEST_CASE_ACTIVITY_NEW_INTENT_GET_CURRENT_CALLER = 19;
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
    private static final int EXPLICIT_OPT_OUT_TEST_CASE_ACTIVITY_NEW_INTENT_GET_CURRENT_CALLER = 20;
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
    private static final int DEFAULT_SHARING_TEST_CASE_ACTIVITY_NEW_INTENT_OVERLOAD_CALLER = 21;
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
    private static final int EXPLICIT_OPT_IN_TEST_CASE_ACTIVITY_NEW_INTENT_OVERLOAD_CALLER = 22;
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
    private static final int EXPLICIT_OPT_OUT_TEST_CASE_ACTIVITY_NEW_INTENT_OVERLOAD_CALLER = 23;
    /**
     * Test case to verify the launching app's result #getCurrentCaller identity is not shared when
     * the activity is not launched with {@link android.app.ActivityOptions}.
     */
    private static final int DEFAULT_SHARING_TEST_CASE_ACTIVITY_RESULT_GET_CURRENT_CALLER = 24;
    /**
     * Test case to verify the launching app's result #getCurrentCaller identity is shared when
     * the activity belongs to the same uid.
     */
    private static final int DEFAULT_SHARING_TEST_CASE_SAME_UID_ACTIVITY_RESULT_GET_CURRENT_CALLER =
            25;
    /**
     * Test case to verify the launching app's result caller identity in
     * #onActivityResult(int, int, Intent, ComponentCaller) is not shared when the activity is not
     * launched with {@link android.app.ActivityOptions}.
     */
    private static final int DEFAULT_SHARING_TEST_CASE_ACTIVITY_RESULT_OVERLOAD_CALLER = 26;
    /**
     * Test case to verify the launching app's result caller identity in
     * #onActivityResult(int, int, Intent, ComponentCaller) is shared when the activity belongs to
     * the same uid.
     */
    private static final int DEFAULT_SHARING_TEST_CASE_SAME_UID_ACTIVITY_RESULT_OVERLOAD_CALLER =
            27;
    /**
     * Action for which the runtime receiver registers to receive broadcasts from the test app.
     */
    private static final String TEST_BROADCAST_RUNTIME_ACTION =
            "android.app.cts.SHARE_IDENTITY_TEST_RUNTIME_ACTION";
    /**
     * The test receiver in a separate package that the new intent activities
     * {@link ActivityNewIntentOverloadCallerShareIdentityTestActivity} and
     * {@link ActivityNewIntentGetCurrentCallerShareIdentityTestActivity} send broadcast indicating
     * that the first launch was accomplished and the receiver has to start them again to go into
     * #onNewIntent.
     */
    private static final ComponentName TEST_SHARED_IDENTITY_RECEIVER = new ComponentName(
            TEST_PACKAGE, TEST_PACKAGE + ".TestShareIdentityReceiver");
    /**
     * The test receiver action for {@link #TEST_SHARED_IDENTITY_RECEIVER}
     */
    private static final String TEST_SHARED_IDENTITY_RECEIVER_ACTION =
            "android.app.cts.testshareidentity.RECEIVER_ACTION";
    /**
     * Activity that sends a result back to either
     * {@link ActivityResultGetCurrentCallerShareIdentityTestActivity} or
     * {@link ActivityResultOverloadCallerShareIdentityTestActivity} for the sharing test cases with
     * the same uid.
     */
    private static final ComponentName SET_RESULT_SAME_UID_ACTIVITY = new ComponentName(
            "android.app.cts",
            "android.app.cts.ShareIdentityTest$ActivitySetResultSameUidShareIdentityTestActivity");
    /**
     * Key used to pass the int test ID as an extra in the {@code Intent} to the {@link
     * #TEST_COMPONENT}. This ID is then passed back to {@link ShareIdentityTestActivity} to
     * obtain the current test's {@link TestData}.
     */
    private static final String TEST_ID_KEY = "testId";
    /**
     * Used to obtain a unique test ID for the current test case.
     */
    private static final AtomicInteger TEST_ID = new AtomicInteger();
    /**
     * Contains the mapping from test ID to {@link TestData} to allow the results of the test
     * to be obtained by the test method after execution returns to {@link
     * ShareIdentityTestActivity}.
     */
    private static final ConcurrentHashMap<Integer, TestData> sTestIdToData =
            new ConcurrentHashMap<>();

    private Context mContext = ApplicationProvider.getApplicationContext();

    private ShareIdentityTestReceiver mReceiver = new ShareIdentityTestReceiver();

    @Before
    public void setUp() {
        mContext.registerReceiver(mReceiver, new IntentFilter(TEST_BROADCAST_RUNTIME_ACTION),
                Context.RECEIVER_EXPORTED);
    }

    @After
    public void tearDown() {
        mContext.unregisterReceiver(mReceiver);
    }

    @Test
    @CddTest(requirement = "4/C-0-2")
    public void testShareIdentity_explicitIdentityShared_identityAvailableToActivity()
            throws Exception {
        // The APIs to share a launching app's identity were introduced to allow a launched
        // app to perform authorization checks; these typically involve an allow-list of known
        // packages and their expected signing identities. When an app opts-in to sharing its
        // identity with the launched activity, that should implicitly grant visibility to the
        // launching app, including its signing identity. This test verifies the launching app's
        // details are available via PackageManager queries when the app has opted-in to sharing
        // its identity and that the expected signing identity of the app can be verified.
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId, EXPLICIT_OPT_IN_TEST_CASE);

        mContext.startActivity(testIntent);

        assertTrue("Activity was not invoked by the timeout",
                testData.countDownLatch.await(10, TimeUnit.SECONDS));
        assertPackageVisibility(testData);
    }

    @Test
    @CddTest(requirement = "4/C-0-2")
    public void testShareIdentity_defaultIdentityNotShared_identityNotAvailableToActivity()
            throws Exception {
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId, DEFAULT_SHARING_TEST_CASE);

        mContext.startActivity(testIntent);

        assertTrue("Activity was not invoked by the timeout",
                testData.countDownLatch.await(10, TimeUnit.SECONDS));
        assertEquals(
                Process.INVALID_UID
                        + " launchedFromUid expected for app not opting-in to sharing identity",
                Process.INVALID_UID, testData.fromUid);
        assertNull("null launchedFromPackage expected for app not opting-in to sharing identity",
                testData.fromPackage);
    }

    @Test
    @CddTest(requirement = "4/C-0-2")
    public void testShareIdentity_explicitIdentityNotShared_identityNotAvailableToActivity()
            throws Exception {
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId, EXPLICIT_OPT_OUT_TEST_CASE);

        mContext.startActivity(testIntent);

        assertTrue("Activity was not invoked by the timeout",
                testData.countDownLatch.await(10, TimeUnit.SECONDS));
        assertEquals(
                Process.INVALID_UID
                        + " launchedFromUid expected for app not opting-in to sharing identity",
                Process.INVALID_UID, testData.fromUid);
        assertNull("null launchedFromPackage expected for app not opting-in to sharing identity",
                testData.fromPackage);
    }

    @Test
    @CddTest(requirement = "4/C-0-2")
    public void testShareIdentity_identityNotSharedSameUid_identityAvailableToActivity()
            throws Exception {
        // While an external app must opt-in to sharing its identity with a launched activity,
        // when the launching app belongs to the same uid as the launched activity, the platform
        // will grant the activity access to its own uid and package name.
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent intent = new Intent(mContext, ShareIdentityTestActivity.class);
        intent.putExtra(TEST_ID_KEY, testId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        mContext.startActivity(intent);

        assertTrue("Activity was not invoked by the timeout",
                testData.countDownLatch.await(10, TimeUnit.SECONDS));
        assertEquals(
                "Expected launchedFromUid not obtained after launching activity from same uid",
                Process.myUid(), testData.fromUid);
        assertEquals(
                "Expected launchedFromPackage not obtained after launching activity from same uid",
                mContext.getPackageName(), testData.fromPackage);
    }

    @Test
    @CddTest(requirement = "4/C-0-2")
    public void testShareIdentity_startActivityForResult_identityNotAvailableToActivity()
            throws Exception {
        // When an app launches an activity with startActivityForResult, the launching app's
        // package name is available to the activity with Activity#getCallingPackage to allow
        // any authorization checks to be performed. Since startActivityForResult requests can
        // be chained, it's possible that the launching app is not the original app that
        // requested the result. To avoid leaking any information about potential intermediate
        // apps forwarding a request, startActivityForResult will not implicitly share an app's
        // identity via getLaunchedFrom.
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId, START_ACTIVITY_FOR_RESULT_TEST_CASE);

        mContext.startActivity(testIntent);

        assertTrue("Activity was not invoked by the timeout",
                testData.countDownLatch.await(10, TimeUnit.SECONDS));
        assertEquals(
                Process.INVALID_UID
                        + " launchedFromUid expected for app invoking startActivityForResult and "
                        + "not opting-in to sharing identity",
                Process.INVALID_UID, testData.fromUid);
        assertNull(
                "null launchedFromPackage expected for app invoking startActivityForResult and "
                        + "not opting-in to sharing identity",
                testData.fromPackage);
    }

    @Test
    @CddTest(requirement = "4/C-0-2")
    public void testShareIdentity_sendBroadcastToRuntimeReceiverIdentityShared_identityAvailable()
            throws Exception {
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId, SEND_BROADCAST_RUNTIME_RECEIVER_OPT_IN_TEST_CASE);

        mContext.startActivity(testIntent);

        assertTrue("Broadcast was not received by the timeout",
                testData.countDownLatch.await(10, TimeUnit.SECONDS));
        assertPackageVisibility(testData);
    }

    @Test
    @CddTest(requirement = "4/C-0-2")
    public void testShareIdentity_sendBroadcastToManifestReceiverIdentityShared_identityAvailable()
            throws Exception {
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId,
                SEND_BROADCAST_MANIFEST_RECEIVER_OPT_IN_TEST_CASE);

        mContext.startActivity(testIntent);

        assertTrue("Broadcast was not received by the timeout",
                testData.countDownLatch.await(10, TimeUnit.SECONDS));
        assertPackageVisibility(testData);
    }

    @Test
    @CddTest(requirement = "4/C-0-2")
    public void
    testShareIdentity_sendBroadcastToRuntimeReceiverIdentityNotShared_identityNotAvailable()
            throws Exception {
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId,
                SEND_BROADCAST_RUNTIME_RECEIVER_OPT_OUT_TEST_CASE);

        mContext.startActivity(testIntent);

        assertTrue("Broadcast was not received by the timeout",
                testData.countDownLatch.await(10, TimeUnit.SECONDS));
        assertEquals(
                Process.INVALID_UID
                        + " sentFromUid expected for app not opting-in to sharing identity",
                Process.INVALID_UID, testData.fromUid);
        assertNull("null sentFromPackage expected for app not opting-in to sharing identity",
                testData.fromPackage);
    }

    @Test
    @CddTest(requirement = "4/C-0-2")
    public void
    testShareIdentity_sendBroadcastToManifestReceiverIdentityNotShared_identityNotAvailable()
            throws Exception {
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId,
                SEND_BROADCAST_MANIFEST_RECEIVER_OPT_OUT_TEST_CASE);

        mContext.startActivity(testIntent);

        assertTrue("Broadcast was not received by the timeout",
                testData.countDownLatch.await(10, TimeUnit.SECONDS));
        assertEquals(
                Process.INVALID_UID
                        + " sentFromUid expected for app not opting-in to sharing identity",
                Process.INVALID_UID, testData.fromUid);
        assertNull("null sentFromPackage expected for app not opting-in to sharing identity",
                testData.fromPackage);
    }

    @Test
    @CddTest(requirement = "4/C-0-2")
    public void testShareIdentity_sendBroadcastToRuntimeReceiverDefault_identityNotAvailable()
            throws Exception {
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId,
                SEND_BROADCAST_RUNTIME_RECEIVER_DEFAULT_TEST_CASE);

        mContext.startActivity(testIntent);

        assertTrue("Broadcast was not received by the timeout",
                testData.countDownLatch.await(10, TimeUnit.SECONDS));
        assertEquals(
                Process.INVALID_UID
                        + " sentFromUid expected for app not opting-in to sharing identity",
                Process.INVALID_UID, testData.fromUid);
        assertNull("null sentFromPackage expected for app not opting-in to sharing identity",
                testData.fromPackage);
    }

    @Test
    @CddTest(requirement = "4/C-0-2")
    public void testShareIdentity_sendBroadcastToManifestReceiverDefault_identityNotAvailable()
            throws Exception {
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId,
                SEND_BROADCAST_MANIFEST_RECEIVER_DEFAULT_TEST_CASE);

        mContext.startActivity(testIntent);

        assertTrue("Broadcast was not received by the timeout",
                testData.countDownLatch.await(10, TimeUnit.SECONDS));
        assertEquals(
                Process.INVALID_UID
                        + " sentFromUid expected for app not opting-in to sharing identity",
                Process.INVALID_UID, testData.fromUid);
        assertNull("null sentFromPackage expected for app not opting-in to sharing identity",
                testData.fromPackage);
    }

    @Test
    @CddTest(requirement = "4/C-0-2")
    public void
    testShareIdentity_sendOrderedBroadcastToRuntimeReceiverIdentityShared_identityAvailable()
            throws Exception {
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId,
                SEND_ORDERED_BROADCAST_RUNTIME_RECEIVER_OPT_IN_TEST_CASE);

        mContext.startActivity(testIntent);

        assertTrue("Broadcast was not received by the timeout",
                testData.countDownLatch.await(10, TimeUnit.SECONDS));
        assertPackageVisibility(testData);
    }

    @Test
    @CddTest(requirement = "4/C-0-2")
    public void
    testShareIdentity_sendOrderedBroadcastToManifestReceiverIdentityShared_identityAvailable()
            throws Exception {
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId,
                SEND_ORDERED_BROADCAST_MANIFEST_RECEIVER_OPT_IN_TEST_CASE);

        mContext.startActivity(testIntent);

        assertTrue("Broadcast was not received by the timeout",
                testData.countDownLatch.await(10, TimeUnit.SECONDS));
        assertPackageVisibility(testData);
    }


    @Test
    @CddTest(requirement = "4/C-0-2")
    public void
    testShareIdentity_sendOrderedBroadcastToRuntimeReceiverIdentityNotShared_identityNotAvailable()
            throws Exception {
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId,
                SEND_ORDERED_BROADCAST_RUNTIME_RECEIVER_OPT_OUT_TEST_CASE);

        mContext.startActivity(testIntent);

        assertTrue("Broadcast was not received by the timeout",
                testData.countDownLatch.await(10, TimeUnit.SECONDS));
        assertEquals(
                Process.INVALID_UID
                        + " sentFromUid expected for app not opting-in to sharing identity",
                Process.INVALID_UID, testData.fromUid);
        assertNull("null sentFromPackage expected for app not opting-in to sharing identity",
                testData.fromPackage);
    }

    @Test
    @CddTest(requirement = "4/C-0-2")
    public void
    testShareIdentity_sendOrderedBroadcastToManifestReceiverIdentityNotShared_identityNotAvailable()
            throws Exception {
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId,
                SEND_ORDERED_BROADCAST_MANIFEST_RECEIVER_OPT_OUT_TEST_CASE);

        mContext.startActivity(testIntent);

        assertTrue("Broadcast was not received by the timeout",
                testData.countDownLatch.await(10, TimeUnit.SECONDS));
        assertEquals(
                Process.INVALID_UID
                        + " sentFromUid expected for app not opting-in to sharing identity",
                Process.INVALID_UID, testData.fromUid);
        assertNull("null sentFromPackage expected for app not opting-in to sharing identity",
                testData.fromPackage);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller", "android.app.ComponentCaller#getUid",
            "android.app.ComponentCaller#getPackage"})
    @CddTest(requirement = "4/C-0-2")
    @RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    public void
    testActivityInitialCallerShareIdentity_explicitIdentityShared_identityAvailableToActivity()
            throws Exception {
        // The APIs to share a launching app's identity were introduced to allow a launched
        // app to perform authorization checks; these typically involve an allow-list of known
        // packages and their expected signing identities. When an app opts-in to sharing its
        // identity with the launched activity, that should implicitly grant visibility to the
        // launching app, including its signing identity. This test verifies the launching app's
        // details are available via PackageManager queries when the app has opted-in to sharing
        // its identity and that the expected signing identity of the app can be verified.
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId,
                EXPLICIT_OPT_IN_TEST_CASE_ACTIVITY_INITIAL_CALLER);

        mContext.startActivity(testIntent);

        assertTrue("Activity was not invoked by the timeout",
                testData.countDownLatch.await(10, TimeUnit.SECONDS));
        assertPackageVisibility(testData);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller", "android.app.ComponentCaller#getUid",
            "android.app.ComponentCaller#getPackage"})
    @CddTest(requirement = "4/C-0-2")
    @RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    public void
    testActivityInitialCallerShareIdentity_defaultIdentityNotShared_identityNotAvailableToActivity()
            throws Exception {
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId,
                DEFAULT_SHARING_TEST_CASE_ACTIVITY_INITIAL_CALLER);

        mContext.startActivity(testIntent);

        assertTrue("Activity was not invoked by the timeout",
                testData.countDownLatch.await(10, TimeUnit.SECONDS));
        assertEquals(
                Process.INVALID_UID
                        + " Activity initial ComponentCaller#getUid expected for app not opting-in"
                        + " to sharing identity",
                Process.INVALID_UID, testData.fromUid);
        assertNull("null Activity initial ComponentCaller#getPackage expected for app not opting-in"
                        + " to sharing identity",
                testData.fromPackage);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller", "android.app.ComponentCaller#getUid",
            "android.app.ComponentCaller#getPackage"})
    @CddTest(requirement = "4/C-0-2")
    @RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    public void
    testActivityInitialCallerShareIdentity_explicitIdentityNotShared_identityNotAvailableToActivity()
            throws Exception {
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId,
                EXPLICIT_OPT_OUT_TEST_CASE_ACTIVITY_INITIAL_CALLER);

        mContext.startActivity(testIntent);

        assertTrue("Activity was not invoked by the timeout",
                testData.countDownLatch.await(10, TimeUnit.SECONDS));
        assertEquals(
                Process.INVALID_UID
                        + " Activity initial ComponentCaller#getUid expected for app not opting-in"
                        + " to sharing identity",
                Process.INVALID_UID, testData.fromUid);
        assertNull("null Activity initial ComponentCaller#getPackage expected for app not opting-in"
                        + " to sharing identity",
                testData.fromPackage);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller", "android.app.ComponentCaller#getUid",
            "android.app.ComponentCaller#getPackage"})
    @CddTest(requirement = "4/C-0-2")
    @RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    public void
    testActivityInitialCallerShareIdentity_identityNotSharedSameUid_identityAvailableToActivity()
            throws Exception {
        // While an external app must opt-in to sharing its identity with a launched activity,
        // when the launching app belongs to the same uid as the launched activity, the platform
        // will grant the activity access to its own uid and package name.
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent intent = new Intent(mContext, ActivityInitialCallerShareIdentityTestActivity.class);
        intent.putExtra(TEST_ID_KEY, testId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        mContext.startActivity(intent);

        assertTrue("Activity was not invoked by the timeout",
                testData.countDownLatch.await(10, TimeUnit.SECONDS));
        assertEquals(
                "Expected Activity initial ComponentCaller#getUid not obtained after launching"
                        + " activity from same uid",
                Process.myUid(), testData.fromUid);
        assertEquals(
                "Expected Activity initial ComponentCaller#getPackage not obtained after launching"
                        + " activity from same uid",
                mContext.getPackageName(), testData.fromPackage);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller", "android.app.ComponentCaller#getUid",
            "android.app.ComponentCaller#getPackage"})
    @CddTest(requirement = "4/C-0-2")
    @RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    public void
    testActivityInitialCallerShareIdentity_startActivityForResult_identityNotAvailableToActivity()
            throws Exception {
        // When an app launches an activity with startActivityForResult, the launching app's
        // package name is available to the activity with Activity#getCallingPackage to allow
        // any authorization checks to be performed. Since startActivityForResult requests can
        // be chained, it's possible that the launching app is not the original app that
        // requested the result. To avoid leaking any information about potential intermediate
        // apps forwarding a request, startActivityForResult will not implicitly share an app's
        // identity via getLaunchedFrom.
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId,
                START_ACTIVITY_FOR_RESULT_TEST_CASE_ACTIVITY_INITIAL_CALLER);

        mContext.startActivity(testIntent);

        assertTrue("Activity was not invoked by the timeout",
                testData.countDownLatch.await(10, TimeUnit.SECONDS));
        assertEquals(
                Process.INVALID_UID
                        + " Activity initial ComponentCaller#getUid expected for app invoking"
                        + " startActivityForResult and not opting-in to sharing identity",
                Process.INVALID_UID, testData.fromUid);
        assertNull(
                "null Activity initial ComponentCaller#getPackage expected for app invoking"
                        + " startActivityForResult and not opting-in to sharing identity",
                testData.fromPackage);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent)", "android.app.ComponentCaller#getUid",
            "android.app.ComponentCaller#getPackage"})
    @CddTest(requirement = "4/C-0-2")
    @RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    public void
    testActivityNewIntentGetCurrentCallerShareIdentity_explicitIdentityShared_identityAvailableToActivity()
            throws Exception {
        // The APIs to share a launching app's identity were introduced to allow a launched
        // app to perform authorization checks; these typically involve an allow-list of known
        // packages and their expected signing identities. When an app opts-in to sharing its
        // identity with the launched activity, that should implicitly grant visibility to the
        // launching app, including its signing identity. This test verifies the launching app's
        // details are available via PackageManager queries when the app has opted-in to sharing
        // its identity and that the expected signing identity of the app can be verified.
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId,
                EXPLICIT_OPT_IN_TEST_CASE_ACTIVITY_NEW_INTENT_GET_CURRENT_CALLER);

        mContext.startActivity(testIntent);

        assertTrue("Activity was not invoked by the timeout",
                testData.countDownLatch.await(20, TimeUnit.SECONDS));
        assertPackageVisibility(testData);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent)", "android.app.ComponentCaller#getUid",
            "android.app.ComponentCaller#getPackage"})
    @CddTest(requirement = "4/C-0-2")
    @RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    public void
    testActivityNewIntentGetCurrentCallerShareIdentity_defaultIdentityNotShared_identityNotAvailableToActivity()
            throws Exception {
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId,
                DEFAULT_SHARING_TEST_CASE_ACTIVITY_NEW_INTENT_GET_CURRENT_CALLER);

        mContext.startActivity(testIntent);

        assertTrue("Activity was not invoked by the timeout",
                testData.countDownLatch.await(20, TimeUnit.SECONDS));
        assertEquals(
                Process.INVALID_UID
                        + " Activity new Intent #getCurrentCaller#getUid expected for app not"
                        + " opting-in to sharing identity",
                Process.INVALID_UID, testData.fromUid);
        assertNull("null Activity new Intent #getCurrentCaller#getPackage expected for app not"
                        + " opting-in to sharing identity",
                testData.fromPackage);
    }
    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent)", "android.app.ComponentCaller#getUid",
            "android.app.ComponentCaller#getPackage"})
    @CddTest(requirement = "4/C-0-2")
    @RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    public void
    testActivityNewIntentGetCurrentCallerShareIdentity_explicitIdentityNotShared_identityNotAvailableToActivity()
            throws Exception {
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId,
                EXPLICIT_OPT_OUT_TEST_CASE_ACTIVITY_NEW_INTENT_GET_CURRENT_CALLER);

        mContext.startActivity(testIntent);

        assertTrue("Activity was not invoked by the timeout",
                testData.countDownLatch.await(20, TimeUnit.SECONDS));
        assertEquals(
                Process.INVALID_UID
                        + " Activity new Intent #getCurrentCaller#getUid expected for app not"
                        + " opting-in to sharing identity",
                Process.INVALID_UID, testData.fromUid);
        assertNull("null Activity new Intent #getCurrentCaller#getPackage expected for app not"
                        + " opting-in to sharing identity",
                testData.fromPackage);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent)", "android.app.ComponentCaller#getUid",
            "android.app.ComponentCaller#getPackage"})
    @CddTest(requirement = "4/C-0-2")
    @RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    public void
    testActivityNewIntentGetCurrentCallerShareIdentity_identityNotSharedSameUid_identityAvailableToActivity()
            throws Exception {
        // While an external app must opt-in to sharing its identity with a launched activity,
        // when the launching app belongs to the same uid as the launched activity, the platform
        // will grant the activity access to its own uid and package name.
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent intent = new Intent(mContext,
                ActivityNewIntentGetCurrentCallerShareIdentityTestActivity.class);
        intent.putExtra(TEST_ID_KEY, testId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        mContext.startActivity(intent);
        mContext.startActivity(intent);

        assertTrue("Activity was not invoked by the timeout",
                testData.countDownLatch.await(20, TimeUnit.SECONDS));
        assertEquals(
                "Expected Activity new Intent #getCurrentCaller#getUid not obtained after launching"
                        + " activity from same uid",
                Process.myUid(), testData.fromUid);
        assertEquals(
                "Expected Activity new Intent #getCurrentCaller#getPackage not obtained after"
                        + " launching activity from same uid",
                mContext.getPackageName(), testData.fromPackage);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#getUid", "android.app.ComponentCaller#getPackage"})
    @CddTest(requirement = "4/C-0-2")
    @RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    public void
    testActivityNewIntentOverloadCallerShareIdentity_explicitIdentityShared_identityAvailableToActivity()
            throws Exception {
        // The APIs to share a launching app's identity were introduced to allow a launched
        // app to perform authorization checks; these typically involve an allow-list of known
        // packages and their expected signing identities. When an app opts-in to sharing its
        // identity with the launched activity, that should implicitly grant visibility to the
        // launching app, including its signing identity. This test verifies the launching app's
        // details are available via PackageManager queries when the app has opted-in to sharing
        // its identity and that the expected signing identity of the app can be verified.
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId,
                EXPLICIT_OPT_IN_TEST_CASE_ACTIVITY_NEW_INTENT_OVERLOAD_CALLER);

        mContext.startActivity(testIntent);

        assertTrue("Activity was not invoked by the timeout",
                testData.countDownLatch.await(20, TimeUnit.SECONDS));
        assertPackageVisibility(testData);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#getUid", "android.app.ComponentCaller#getPackage"})
    @CddTest(requirement = "4/C-0-2")
    @RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    public void
    testActivityNewIntentOverloadCallerShareIdentity_defaultIdentityNotShared_identityNotAvailableToActivity()
            throws Exception {
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId,
                DEFAULT_SHARING_TEST_CASE_ACTIVITY_NEW_INTENT_OVERLOAD_CALLER);

        mContext.startActivity(testIntent);

        assertTrue("Activity was not invoked by the timeout",
                testData.countDownLatch.await(20, TimeUnit.SECONDS));
        assertEquals(
                Process.INVALID_UID
                        + " Activity new Intent overloaded caller#getUid expected for app not"
                        + " opting-in to sharing identity",
                Process.INVALID_UID, testData.fromUid);
        assertNull("null Activity new Intent overloaded caller#getPackage expected for app not"
                        + " opting-in to sharing identity",
                testData.fromPackage);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#getUid", "android.app.ComponentCaller#getPackage"})
    @CddTest(requirement = "4/C-0-2")
    @RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    public void
    testActivityNewIntentOverloadCallerShareIdentity_explicitIdentityNotShared_identityNotAvailableToActivity()
            throws Exception {
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent testIntent = getTestIntent(testId,
                EXPLICIT_OPT_OUT_TEST_CASE_ACTIVITY_NEW_INTENT_OVERLOAD_CALLER);

        mContext.startActivity(testIntent);

        assertTrue("Activity was not invoked by the timeout",
                testData.countDownLatch.await(20, TimeUnit.SECONDS));
        assertEquals(
                Process.INVALID_UID
                        + " Activity new Intent overloaded caller#getUid expected for app not"
                        + " opting-in to sharing identity",
                Process.INVALID_UID, testData.fromUid);
        assertNull("null Activity new Intent overloaded caller#getPackage expected for app not"
                        + " opting-in to sharing identity",
                testData.fromPackage);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#getUid", "android.app.ComponentCaller#getPackage"})
    @CddTest(requirement = "4/C-0-2")
    @RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    public void
    testActivityNewIntentOverloadCallerShareIdentity_identityNotSharedSameUid_identityAvailableToActivity()
            throws Exception {
        // While an external app must opt-in to sharing its identity with a launched activity,
        // when the launching app belongs to the same uid as the launched activity, the platform
        // will grant the activity access to its own uid and package name.
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent intent = new Intent(mContext,
                ActivityNewIntentOverloadCallerShareIdentityTestActivity.class);
        intent.putExtra(TEST_ID_KEY, testId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        mContext.startActivity(intent);
        mContext.startActivity(intent);

        assertTrue("Activity was not invoked by the timeout",
                testData.countDownLatch.await(20, TimeUnit.SECONDS));
        assertEquals(
                "Expected Activity new Intent overloaded caller#getUid not obtained after launching"
                        + " activity from same uid",
                Process.myUid(), testData.fromUid);
        assertEquals(
                "Expected Activity new Intent overloaded caller#getPackage not obtained after"
                        + " launching activity from same uid",
                mContext.getPackageName(), testData.fromPackage);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onActivityResult(int,int,Intent)",
            "android.app.ComponentCaller#getUid", "android.app.ComponentCaller#getPackage"})
    @CddTest(requirement = "4/C-0-2")
    @RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    public void
    testActivityResultGetCurrentCallerShareIdentity_defaultIdentityNotShared_identityNotAvailableToActivity()
            throws Exception {
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent intent = getResultTestIntent(testId,
                DEFAULT_SHARING_TEST_CASE_ACTIVITY_RESULT_GET_CURRENT_CALLER, mContext,
                ActivityResultGetCurrentCallerShareIdentityTestActivity.class);

        mContext.startActivity(intent);

        assertTrue("Activity was not invoked by the timeout",
                testData.countDownLatch.await(15, TimeUnit.SECONDS));
        assertEquals(
                Process.INVALID_UID
                        + " Activity result #getCurrentCaller#getUid expected for app not opting-in"
                        + "to sharing identity",
                Process.INVALID_UID, testData.fromUid);
        assertNull("null Activity result #getCurrentCaller#getPackage expected for app not"
                        + " opting-in to sharing identity",
                testData.fromPackage);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onActivityResult(int,int,Intent)",
            "android.app.ComponentCaller#getUid", "android.app.ComponentCaller#getPackage"})
    @CddTest(requirement = "4/C-0-2")
    @RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    public void
    testActivityResultGetCurrentCallerShareIdentity_identityNotSharedSameUid_identityAvailableToActivity()
            throws Exception {
        // While an external app must opt-in to sharing its identity with a launched activity,
        // when the launching app belongs to the same uid as the launched activity, the platform
        // will grant the activity access to its own uid and package name.
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent intent = getResultTestIntent(testId,
                DEFAULT_SHARING_TEST_CASE_SAME_UID_ACTIVITY_RESULT_GET_CURRENT_CALLER, mContext,
                ActivityResultGetCurrentCallerShareIdentityTestActivity.class);

        mContext.startActivity(intent);

        assertTrue("Activity was not invoked by the timeout",
                testData.countDownLatch.await(15, TimeUnit.SECONDS));
        assertEquals(
                "Expected Activity result #getCurrentCaller#getUid not obtained after launching"
                        + " activity from same uid",
                Process.myUid(), testData.fromUid);
        assertEquals(
                "Expected Activity result #getCurrentCaller#getPackage not obtained after launching"
                        + " activity from same uid",
                mContext.getPackageName(), testData.fromPackage);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#onActivityResult(int,int,Intent,ComponentCaller)",
            "android.app.ComponentCaller#getUid", "android.app.ComponentCaller#getPackage"})
    @CddTest(requirement = "4/C-0-2")
    @RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    public void
    testActivityResultOverloadCallerShareIdentity_defaultIdentityNotShared_identityNotAvailableToActivity()
            throws Exception {
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent intent = getResultTestIntent(testId,
                DEFAULT_SHARING_TEST_CASE_ACTIVITY_RESULT_OVERLOAD_CALLER, mContext,
                ActivityResultOverloadCallerShareIdentityTestActivity.class);

        mContext.startActivity(intent);

        assertTrue("Activity was not invoked by the timeout",
                testData.countDownLatch.await(15, TimeUnit.SECONDS));
        assertEquals(
                Process.INVALID_UID
                        + " Activity result overloaded caller#getUid expected for app not opting-in"
                        + "to sharing identity",
                Process.INVALID_UID, testData.fromUid);
        assertNull("null Activity result overloaded caller#getPackage expected for app not"
                        + " opting-in to sharing identity",
                testData.fromPackage);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#onActivityResult(int,int,Intent,ComponentCaller)",
            "android.app.ComponentCaller#getUid", "android.app.ComponentCaller#getPackage"})
    @CddTest(requirement = "4/C-0-2")
    @RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    public void
    testActivityResultGetOverloadCallerShareIdentity_identityNotSharedSameUid_identityAvailableToActivity()
            throws Exception {
        // While an external app must opt-in to sharing its identity with a launched activity,
        // when the launching app belongs to the same uid as the launched activity, the platform
        // will grant the activity access to its own uid and package name.
        TestData testData = new TestData(new CountDownLatch(1));
        int testId = TEST_ID.getAndIncrement();
        sTestIdToData.put(testId, testData);
        Intent intent = getResultTestIntent(testId,
                DEFAULT_SHARING_TEST_CASE_SAME_UID_ACTIVITY_RESULT_OVERLOAD_CALLER, mContext,
                ActivityResultOverloadCallerShareIdentityTestActivity.class);

        mContext.startActivity(intent);

        assertTrue("Activity was not invoked by the timeout",
                testData.countDownLatch.await(15, TimeUnit.SECONDS));
        assertEquals(
                "Expected Activity result overloaded caller#getUid not obtained after launching"
                        + " activity from same uid",
                Process.myUid(), testData.fromUid);
        assertEquals(
                "Expected Activity result overloaded caller#getPackage not obtained after launching"
                        + " activity from same uid",
                mContext.getPackageName(), testData.fromPackage);
    }

    private void assertPackageVisibility(TestData testData) throws Exception {
        PackageManager packageManager = mContext.getPackageManager();
        PackageInfo packageInfo = packageManager.getPackageInfo(TEST_PACKAGE,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));
        assertEquals(
                "Expected fromUid not obtained after launching app opted-in to sharing "
                        + "identity",
                packageInfo.applicationInfo.uid,
                testData.fromUid);
        assertEquals(
                "Expected fromPackage not obtained after launching app opted-in to sharing "
                        + "identity",
                TEST_PACKAGE, testData.fromPackage);
        assertNotNull(
                "Expected SigningInfo not available after launching app opted-in to sharing "
                        + "identity",
                packageInfo.signingInfo);
        assertTrue(
                "Expected rotated signer not reported after launching app opted-in to sharing "
                        + "identity",
                packageManager.hasSigningCertificate(testData.fromUid,
                        HexEncoding.decode(TEST_PACKAGE_ROTATED_SIGNER_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                "Expected original signer not reported after launching app opted-in to sharing "
                        + "identity",
                packageManager.hasSigningCertificate(testData.fromPackage,
                        HexEncoding.decode(TEST_PACKAGE_ORIGINAL_SIGNER_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));

    }

    /**
     * Returns a default new task test Intent to launch the {@link #TEST_COMPONENT} with the
     * provided {@code testId} and {@code testCase}.
     */
    private static Intent getTestIntent(int testId, int testCase) {
        return getTestIntent(testId, testCase, Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
    }

    /**
     * Returns a test Intent to launch the {@link #TEST_COMPONENT} with the provided {@code testId},
     * {@code testCase}, and {@code flags}.
     */
    private static Intent getTestIntent(int testId, int testCase, int flags) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(TEST_COMPONENT);
        intent.setFlags(flags);
        intent.putExtra(TEST_ID_KEY, testId);
        intent.putExtra(TEST_CASE_KEY, testCase);
        return intent;
    }

    /**
     * Returns a test Intent to launch an activity for result with the provided {@code testId},
     * {@code testCase}, and a component via {@code context} and {@code cls}.
     */
    private static Intent getResultTestIntent(int testId, int testCase, Context context,
            Class<?> cls) {
        Intent intent = new Intent(context, cls);
        intent.putExtra(TEST_ID_KEY, testId);
        intent.putExtra(TEST_CASE_KEY, testCase);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    /**
     * Activity that can be launched by the {@link #TEST_COMPONENT} to verify sharing of the
     * launching app's identity works as expected based on whether the launching app opted-in
     * to sharing its identity.
     */
    public static class ShareIdentityTestActivity extends Activity {
        public void onStart() {
            super.onStart();
            int testId = getIntent().getIntExtra(TEST_ID_KEY, -1);
            if (!sTestIdToData.containsKey(testId)) {
                Log.e(TAG, "Unable to obtain test data from test ID " + testId);
                finish();
                return;
            }
            TestData testData = sTestIdToData.remove(testId);
            testData.fromUid = getTestDataUid();
            testData.fromPackage = getTestDataPackage();
            testData.countDownLatch.countDown();
            finish();
        }

        public int getTestDataUid() {
            return getLaunchedFromUid();
        }

        public String getTestDataPackage() {
            return getLaunchedFromPackage();
        }
    }

    /**
     * Activity that can be launched by the {@link #TEST_COMPONENT} to verify sharing of the
     * launching app's initial caller identity works as expected based on whether the launching app
     * opted-in to sharing its identity.
     */
    public static class ActivityInitialCallerShareIdentityTestActivity
            extends ShareIdentityTestActivity {
        @Override
        public int getTestDataUid() {
            return getInitialCaller().getUid();
        }

        @Override
        public String getTestDataPackage() {
            return getInitialCaller().getPackage();
        }
    }

    /**
     * Activity that can be launched by the {@link #TEST_COMPONENT} to verify sharing of the
     * launching app's new intent #getCurrentCaller identity works as expected based on whether the
     * launching app opted-in to sharing its identity.
     */
    public static class ActivityNewIntentGetCurrentCallerShareIdentityTestActivity
            extends Activity {
        public void onStart() {
            super.onStart();
            int testId = getIntent().getIntExtra(TEST_ID_KEY, -1);
            int testCase = getIntent().getIntExtra(TEST_CASE_KEY, -1);
            if (!sTestIdToData.containsKey(testId)) {
                Log.e(TAG, "Unable to obtain test data from test ID " + testId);
                finish();
                return;
            }
            Intent broadcastIntent = new Intent();
            broadcastIntent.putExtra(TEST_ID_KEY, testId);
            broadcastIntent.putExtra(TEST_CASE_KEY, testCase);
            broadcastIntent.setComponent(TEST_SHARED_IDENTITY_RECEIVER);
            broadcastIntent.setAction(TEST_SHARED_IDENTITY_RECEIVER_ACTION);
            sendBroadcast(broadcastIntent);
            UiDeviceUtils.pressHomeButton();
        }

        @Override
        public void onNewIntent(Intent intent) {
            int testId = getIntent().getIntExtra(TEST_ID_KEY, -1);
            if (!sTestIdToData.containsKey(testId)) {
                Log.e(TAG, "Unable to obtain test data from test ID " + testId);
                finish();
                return;
            }
            ComponentCaller caller = getCurrentCaller();
            TestData testData = sTestIdToData.remove(testId);
            testData.fromUid = caller.getUid();
            testData.fromPackage = caller.getPackage();
            testData.countDownLatch.countDown();
            finish();
        }
    }

    /**
     * Activity that can be launched by the {@link #TEST_COMPONENT} to verify sharing of the
     * launching app's new intent overload caller identity works as expected based on whether the
     * launching app opted-in to sharing its identity.
     */
    public static class ActivityNewIntentOverloadCallerShareIdentityTestActivity
            extends ActivityNewIntentGetCurrentCallerShareIdentityTestActivity {
        @Override
        public void onNewIntent(Intent intent, ComponentCaller caller) {
            int testId = getIntent().getIntExtra(TEST_ID_KEY, -1);
            if (!sTestIdToData.containsKey(testId)) {
                Log.e(TAG, "Unable to obtain test data from test ID " + testId);
                finish();
                return;
            }
            if (caller == null) {
                throw new RuntimeException("Inside onNewIntent(Intent, ComponentCaller), caller"
                        + " should not be null");
            }
            TestData testData = sTestIdToData.remove(testId);
            testData.fromUid = caller.getUid();
            testData.fromPackage = caller.getPackage();
            testData.countDownLatch.countDown();
            finish();
        }
    }

    /**
     * Activity that can be launched by this package to verify sharing of the launching app's result
     * #getCurrentCaller identity works as expected based on whether the launching app opted-in to
     * sharing its identity.
     */
    public static class ActivityResultGetCurrentCallerShareIdentityTestActivity extends Activity {
        public void onStart() {
            super.onStart();
            int testId = getIntent().getIntExtra(TEST_ID_KEY, -1);
            int testCase = getIntent().getIntExtra(TEST_CASE_KEY, -1);

            Intent intent;
            if (testCase == getDefaultSharingTestCase()) {
                intent = getTestIntent(testId, testCase, /* flags */ 0);

            } else if (testCase == getDefaultSharingTestCaseSameUid()) {
                intent = new Intent();
                intent.setComponent(SET_RESULT_SAME_UID_ACTIVITY);
                intent.putExtra(TEST_ID_KEY, testId);
                intent.putExtra(TEST_CASE_KEY, testCase);

            } else {
                throw new RuntimeException("Unexpected test case: " + testCase);
            }
            startActivityForResult(intent, 0);
        }

        protected int getDefaultSharingTestCase() {
            return DEFAULT_SHARING_TEST_CASE_ACTIVITY_RESULT_GET_CURRENT_CALLER;
        }

        protected int getDefaultSharingTestCaseSameUid() {
            return DEFAULT_SHARING_TEST_CASE_SAME_UID_ACTIVITY_RESULT_GET_CURRENT_CALLER;
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent intent) {
            performResult(intent, getCurrentCaller());
        }

        protected void performResult(Intent intent, ComponentCaller caller) {
            int testId = intent.getIntExtra(TEST_ID_KEY, -1);
            TestData testData = sTestIdToData.remove(testId);
            testData.fromUid = caller.getUid();
            testData.fromPackage = caller.getPackage();
            testData.countDownLatch.countDown();
            finish();
        }
    }

    /**
     * Activity that can be launched by this package to verify sharing of the launching app's result
     * overload caller identity works as expected based on whether the launching app opted-in to
     * sharing its identity.
     */
    public static class ActivityResultOverloadCallerShareIdentityTestActivity
            extends ActivityResultGetCurrentCallerShareIdentityTestActivity {
        @Override
        protected int getDefaultSharingTestCase() {
            return DEFAULT_SHARING_TEST_CASE_ACTIVITY_RESULT_OVERLOAD_CALLER;
        }

        @Override
        protected int getDefaultSharingTestCaseSameUid() {
            return DEFAULT_SHARING_TEST_CASE_SAME_UID_ACTIVITY_RESULT_OVERLOAD_CALLER;
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent intent,
                ComponentCaller caller) {
            performResult(intent, caller);
        }
    }

    /**
     * Activity that sends a result back to either
     * {@link ActivityResultGetCurrentCallerShareIdentityTestActivity} or
     * {@link ActivityResultOverloadCallerShareIdentityTestActivity} for the sharing test cases with
     * the same uid.
     */
    public static class ActivitySetResultSameUidShareIdentityTestActivity extends Activity {
        public void onStart() {
            super.onStart();
            int testId = getIntent().getIntExtra(TEST_ID_KEY, -1);
            Intent intent = new Intent();
            intent.putExtra(TEST_ID_KEY, testId);
            setResult(RESULT_OK, intent);
            finish();
        }
    }

    public static class ShareIdentityTestReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int testId = intent.getIntExtra(TEST_ID_KEY, -1);
            if (!sTestIdToData.containsKey(testId)) {
                Log.e(TAG, "Unable to obtain test data from test ID " + testId);
                return;
            }
            TestData testData = sTestIdToData.remove(testId);
            testData.fromUid = getSentFromUid();
            testData.fromPackage = getSentFromPackage();
            testData.countDownLatch.countDown();
        }
    }

    /**
     * Data class to allow synchronization and sharing of results between the test method and
     * the activity.
     */
    private static class TestData {
        CountDownLatch countDownLatch;
        int fromUid;
        String fromPackage;

        TestData(CountDownLatch countDownLatch) {
            this.countDownLatch = countDownLatch;
        }
    }
}
