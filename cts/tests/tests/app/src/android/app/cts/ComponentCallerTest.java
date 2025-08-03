/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.app.cts.testcomponentcaller.Constants.PUT_EXTRA_UNKNOWN_REMOVE_EXTRA_STREAM_SET_RESULT_ACTION_ID;
import static android.app.cts.testcomponentcaller.Constants.PUT_MODE_FLAGS_TO_CHECK_SET_RESULT_ACTION_ID;
import static android.app.cts.testcomponentcaller.Constants.GRANT_FLAGS_SET_RESULT_ACTION_ID;
import static android.app.cts.testcomponentcaller.Constants.NO_ACTION_NEEDED_SET_RESULT_ACTION_ID;
import static android.app.cts.testcomponentcaller.Constants.NO_URI_PROVIDED_SET_RESULT_ACTION_ID;
import static android.app.cts.testcomponentcaller.Constants.PUT_NON_URI_EXTRA_STREAM_SET_RESULT_ACTION_ID;
import static android.app.cts.testcomponentcaller.Constants.RESULT_EXTRA_REFERRER_NAME;
import static android.app.cts.testcomponentcaller.Constants.RESULT_NON_URI_EXTRA_STREAM;
import static android.app.cts.testcomponentcaller.Constants.SET_RESULT_ACTION_ID;
import static android.app.cts.testcomponentcaller.Constants.TEST_ACTION_ID;
import static android.app.cts.testcomponentcaller.Constants.EXTRA_CHECK_CONTENT_URI_PERMISSION_RESULT;
import static android.app.cts.testcomponentcaller.Constants.EXTRA_ILLEGAL_ARG_EXCEPTION_CAUGHT;
import static android.app.cts.testcomponentcaller.Constants.EXTRA_UNKNOWN;
import static android.app.cts.testcomponentcaller.Constants.GRANT_MODE_FLAGS;
import static android.app.cts.testcomponentcaller.Constants.HELPER_APP_NEW_INTENT_GET_CURRENT_CALLER_ACTIVITY;
import static android.app.cts.testcomponentcaller.Constants.HELPER_APP_NEW_INTENT_OVERLOAD_CALLER_ACTIVITY;
import static android.app.cts.testcomponentcaller.Constants.HELPER_APP_RESULT_GET_CURRENT_CALLER_ACTIVITY;
import static android.app.cts.testcomponentcaller.Constants.HELPER_APP_RESULT_OVERLOAD_CALLER_ACTIVITY;
import static android.app.cts.testcomponentcaller.Constants.HELPER_APP_URI;
import static android.app.cts.testcomponentcaller.Constants.IS_NEW_INTENT;
import static android.app.cts.testcomponentcaller.Constants.IS_RESULT;
import static android.app.cts.testcomponentcaller.Constants.MODE_FLAGS_TO_CHECK;
import static android.app.cts.testcomponentcaller.Constants.EXTRA_SECURITY_EXCEPTION_CAUGHT;
import static android.app.cts.testcomponentcaller.Constants.PROVIDER_RESULT_URI_TYPE;
import static android.app.cts.testcomponentcaller.Constants.NO_PERMISSION_URI_TYPE;
import static android.app.cts.testcomponentcaller.Constants.READ_PERMISSION_URI_TYPE;
import static android.app.cts.testcomponentcaller.Constants.PUT_MODE_FLAGS;
import static android.app.cts.testcomponentcaller.Constants.SEND_TEST_BROADCAST_ACTION_ID;
import static android.app.cts.testcomponentcaller.Constants.START_TEST_ACTIVITY_ACTION_ID;
import static android.app.cts.testcomponentcaller.Constants.TEST_SET_RESULT_ACTIVITY;
import static android.app.cts.testcomponentcaller.Constants.TRY_TO_RETRIEVE_EXTRA_STREAM_REFERRER_NAME;
import static android.app.cts.testcomponentcaller.Constants.URI_IN_ARRAY_LIST_EXTRA_STREAMS_LOCATION_ID;
import static android.app.cts.testcomponentcaller.Constants.URI_IN_EXTRA_STREAM_LOCATION_ID;
import static android.app.cts.testcomponentcaller.Constants.URI_IN_EXTRA_UNKNOWN_LOCATION_ID;
import static android.app.cts.testcomponentcaller.Constants.URI_LOCATION_ID;
import static android.app.cts.testcomponentcaller.Constants.HELPER_APP_INITIAL_CALLER_ACTIVITY;
import static android.app.cts.testcomponentcaller.Constants.HELPER_APP_PACKAGE;
import static android.app.cts.testcomponentcaller.Constants.INVALID_PERMISSION_RESULT;
import static android.app.cts.testcomponentcaller.Constants.URI_IN_CLIP_DATA_LOCATION_ID;
import static android.app.cts.testcomponentcaller.Constants.URI_IN_DATA_LOCATION_ID;
import static android.app.cts.testcomponentcaller.Constants.TEST_RECEIVER_ACTION;
import static android.app.cts.testcomponentcaller.Constants.NONE_PROVIDED_USE_HELPER_APP_URI_LOCATION_ID;
import static android.app.cts.testcomponentcaller.Constants.RESULT_URI_TYPE;
import static android.app.cts.testcomponentcaller.Constants.URI_PROVIDED_SET_RESULT_ACTION_ID;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.ComponentCaller;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import com.google.common.collect.ImmutableList;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameter.TestParameterValuesProvider;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests for {@link android.app.ComponentCaller#checkContentUriPermission}.
 *
 * To imitate a caller-callee relationship between apps, this test uses a helper app
 * CtsTestComponentCallerApp, which calls the tested API and then sends the relevant results
 * (exceptions, permission results) back to the test via broadcast.
 */
@RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
@RunWith(TestParameterInjector.class)
public class ComponentCallerTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    /** This test package doesn't have access to this content URI */
    private static final Uri CONTENT_URI_NO_PERMISSION =
            Uri.parse("content://" + HELPER_APP_PACKAGE + ".provider");

    /** This test package has read access to this content URI via a permission */
    private static final Uri CONTENT_URI_READ_PERMISSION =
            Uri.parse("content://" + HELPER_APP_PACKAGE + ".provider/path");

    private Context mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

    private TestReceiver mReceiver;

    @Before
    public final void setUp() {
        TestResults.reset();
        mReceiver = new TestReceiver();
        IntentFilter filter = new IntentFilter(TEST_RECEIVER_ACTION);
        mContext.registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    @After
    public final void tearDown() {
        mContext.unregisterReceiver(mReceiver);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_throwsIfCallerOfApiDoesNotHaveTheSameAccessToContentUri(
            @TestParameter ModeFlags modeFlagsToCheck) throws Exception {
        Intent intent = getUriInDataSendBroadcastTestIntent(TestProvider.getContentUri(),
                modeFlagsToCheck, HELPER_APP_INITIAL_CALLER_ACTIVITY);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertTrue("Should throw a SecurityException because the caller of the API doesn't have the"
                        + " same " + modeFlagsToString(modeFlagsToCheck) + " access to a content"
                        + " URI",
                TestResults.sIsSecurityExceptionCaught);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_throwsIfContentUriWasNotPassedAtLaunch(
            @TestParameter ModeFlags modeFlagsToCheck) throws Exception {
        Intent intent = getSendBroadcastTestIntent(NONE_PROVIDED_USE_HELPER_APP_URI_LOCATION_ID,
                modeFlagsToCheck, HELPER_APP_INITIAL_CALLER_ACTIVITY);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertTrue("Should throw an IllegalArgumentException because the supplied content URI was"
                        + " not passed at launch", TestResults.sIsIllegalArgumentExceptionCaught);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_returnsCorrectResultEvenIfCallerOfActivityGrantsAndDies(
            @TestParameter ModeFlags modeFlagsToCheck) throws Exception {
        Intent intent = getStartActivityTestIntent(NONE_PROVIDED_USE_HELPER_APP_URI_LOCATION_ID,
                modeFlagsToCheck, HELPER_APP_INITIAL_CALLER_ACTIVITY);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return granted with " + modeFlagsToString(modeFlagsToCheck) + " even"
                        + " if the caller of the activity dies",
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_getDataContentUriViaPermission_noPermission(
            @TestParameter ModeFlags modeFlagsToCheck) throws Exception {
        Intent intent = getUriInDataSendBroadcastTestIntent(
                CONTENT_URI_NO_PERMISSION, modeFlagsToCheck, HELPER_APP_INITIAL_CALLER_ACTIVITY);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return denied with " + modeFlagsToString(modeFlagsToCheck)
                        + " because we have no access to the content URI",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_getDataContentUriViaPermission_hasRead()
            throws Exception {
        Intent intent = getUriInDataSendBroadcastTestIntent(CONTENT_URI_READ_PERMISSION,
                ModeFlags.READ, HELPER_APP_INITIAL_CALLER_ACTIVITY);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return granted because we have the read permission",
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_getDataContentUriViaPermission_hasReadButNoWrite(
            @TestParameter(valuesProvider = WriteModeFlagsProvider.class)
            ModeFlags modeFlagsToCheck) throws Exception {
        Intent intent = getUriInDataSendBroadcastTestIntent(
                CONTENT_URI_READ_PERMISSION, modeFlagsToCheck, HELPER_APP_INITIAL_CALLER_ACTIVITY);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return denied because we don't have the write permission",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_clipDataContentUri_noPermission(
            @TestParameter ModeFlags modeFlagsToCheck) throws Exception {
        Intent intent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID,
                modeFlagsToCheck, HELPER_APP_INITIAL_CALLER_ACTIVITY);
        intent.setClipData(ClipData.newRawUri("", CONTENT_URI_NO_PERMISSION));

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return denied with " + modeFlagsToString(modeFlagsToCheck)
                        + " because we have no access to the content URI",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_clipDataContentUri_hasRead()
            throws Exception {
        Intent intent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID, ModeFlags.READ,
                HELPER_APP_INITIAL_CALLER_ACTIVITY);
        intent.setClipData(ClipData.newRawUri("", CONTENT_URI_READ_PERMISSION));

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return granted because we have the read permission",
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_clipDataContentUri_hasReadButNoWrite(
            @TestParameter(valuesProvider = WriteModeFlagsProvider.class)
            ModeFlags modeFlagsToCheck) throws Exception {
        Intent intent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID,
                modeFlagsToCheck, HELPER_APP_INITIAL_CALLER_ACTIVITY);
        intent.setClipData(ClipData.newRawUri("", CONTENT_URI_READ_PERMISSION));

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return denied because we don't have the write permission",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_extraStreamContentUri_noPermission(
            @TestParameter ModeFlags modeFlagsToCheck) throws Exception {
        Intent intent = getSendBroadcastTestIntent(URI_IN_EXTRA_STREAM_LOCATION_ID,
                modeFlagsToCheck, HELPER_APP_INITIAL_CALLER_ACTIVITY);
        intent.putExtra(Intent.EXTRA_STREAM, CONTENT_URI_NO_PERMISSION);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return denied with " + modeFlagsToString(modeFlagsToCheck)
                        + " because we have no access to the content URI",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_extraStreamContentUri_hasRead()
            throws Exception {
        Intent intent = getSendBroadcastTestIntent(URI_IN_EXTRA_STREAM_LOCATION_ID, ModeFlags.READ,
                HELPER_APP_INITIAL_CALLER_ACTIVITY);
        intent.putExtra(Intent.EXTRA_STREAM, CONTENT_URI_READ_PERMISSION);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return granted because we have the read permission",
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_extraStreamContentUri_hasReadButNoWrite(
            @TestParameter(valuesProvider = WriteModeFlagsProvider.class)
            ModeFlags modeFlagsToCheck) throws Exception {
        Intent intent = getSendBroadcastTestIntent(URI_IN_EXTRA_STREAM_LOCATION_ID,
                modeFlagsToCheck, HELPER_APP_INITIAL_CALLER_ACTIVITY);
        intent.putExtra(Intent.EXTRA_STREAM, CONTENT_URI_READ_PERMISSION);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return denied because we don't have the write permission",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_arrayListExtraStreamsContentUri_noPermission(
            @TestParameter ModeFlags modeFlagsToCheck) throws Exception {
        Intent intent = getSendBroadcastTestIntent(URI_IN_ARRAY_LIST_EXTRA_STREAMS_LOCATION_ID,
                modeFlagsToCheck, HELPER_APP_INITIAL_CALLER_ACTIVITY);
        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(CONTENT_URI_NO_PERMISSION);
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return denied with " + modeFlagsToString(modeFlagsToCheck)
                        + " because we have no access to the content URI",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_arrayListExtraStreamsContentUri_hasRead()
            throws Exception {
        Intent intent = getSendBroadcastTestIntent(URI_IN_ARRAY_LIST_EXTRA_STREAMS_LOCATION_ID,
                ModeFlags.READ, HELPER_APP_INITIAL_CALLER_ACTIVITY);
        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(CONTENT_URI_READ_PERMISSION);
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return granted because we have the read permission",
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_arrayListExtraStreamsContentUri_hasReadButNoWrite(
            @TestParameter(valuesProvider = WriteModeFlagsProvider.class)
            ModeFlags modeFlagsToCheck) throws Exception {
        Intent intent = getSendBroadcastTestIntent(URI_IN_ARRAY_LIST_EXTRA_STREAMS_LOCATION_ID,
                modeFlagsToCheck, HELPER_APP_INITIAL_CALLER_ACTIVITY);
        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(CONTENT_URI_READ_PERMISSION);
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return denied because we don't have the write permission",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_unknownExtraContentUri_throwsIllegalArgumentException(
            @TestParameter ModeFlags modeFlagsToCheck) throws Exception {
        Intent intent = getSendBroadcastTestIntent(URI_IN_EXTRA_UNKNOWN_LOCATION_ID,
                modeFlagsToCheck, HELPER_APP_INITIAL_CALLER_ACTIVITY);
        intent.putExtra(EXTRA_UNKNOWN, CONTENT_URI_NO_PERMISSION);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertTrue("Should throw an IllegalArgumentException for an unknown EXTRA",
                TestResults.sIsIllegalArgumentExceptionCaught);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityInitialCaller_checkContentUriPermission_passingNonUriExtraStreamDoesNotAffectRetrievalOfExtras()
            throws Exception {
        Intent intent = getTryToRetrieveExtrasTestIntent(HELPER_APP_INITIAL_CALLER_ACTIVITY);
        String nonUriExtraStream = "non-uri";
        String referrerName = "ComponentCaller";
        intent.putExtra(Intent.EXTRA_STREAM, nonUriExtraStream);
        intent.putExtra(Intent.EXTRA_REFERRER_NAME, referrerName);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Passing a non-URI item as EXTRA_STREAM should not affect the retrieval of"
                + " EXTRA_STREAM", nonUriExtraStream, TestResults.sExtraStreamString);
        assertEquals("Passing a non-URI item as EXTRA_STREAM should not affect the retrieval of"
                + " other extras, such as EXTRA_REFERRER_NAME",
                referrerName, TestResults.sReferrerName);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getInitialCaller",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void testActivityInitialCaller_checkContentUriPermission_contentUriViaGrant(
            @TestParameter ModeFlags modeFlagsToCheck) throws Exception {
        Intent intent = getUriInDataSendBroadcastTestIntent(TestProvider.getContentUri(),
                modeFlagsToCheck, HELPER_APP_INITIAL_CALLER_ACTIVITY);
        intent.addFlags(modeFlagsToCheck.mValue);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return granted because we granted "
                        + modeFlagsToString(modeFlagsToCheck),
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_throwsIfCallerOfApiDoesNotHaveTheSameAccessToContentUri(
            @TestParameter ModeFlags modeFlagsToCheck,
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is not supposed to throw a
        // SecurityException. This is to verify the new intent caller is correct.
        Intent intent = getUriInDataSendBroadcastTestIntent(HELPER_APP_URI, modeFlagsToCheck,
                newIntentCallerActivity.mComponent);

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getUriInDataSendBroadcastTestIntent(TestProvider.getContentUri(),
                modeFlagsToCheck, newIntentCallerActivity.mComponent);
        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertTrue("Should throw a SecurityException because the new intent caller of the API"
                        + "doesn't have the same " + modeFlagsToString(modeFlagsToCheck)
                        + " access to a content URI",
                TestResults.sIsSecurityExceptionCaught);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_throwsIfContentUriWasNotPassedAtLaunch(
            @TestParameter ModeFlags modeFlagsToCheck,
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is not supposed to throw an
        // IllegalArgumentException. This is to verify the new intent caller is correct.
        Intent intent = getUriInDataSendBroadcastTestIntent(HELPER_APP_URI, modeFlagsToCheck,
                newIntentCallerActivity.mComponent);

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getSendBroadcastTestIntent(NONE_PROVIDED_USE_HELPER_APP_URI_LOCATION_ID,
                modeFlagsToCheck, newIntentCallerActivity.mComponent);

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertTrue("Should throw an IllegalArgumentException because the supplied content URI was"
                        + " not passed at launch",
                TestResults.sIsIllegalArgumentExceptionCaught);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_returnsCorrectResultEvenIfCallerOfActivityGrantsAndDies(
            @TestParameter ModeFlags modeFlagsToCheck,
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a denied
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getStartActivityTestIntent(NONE_PROVIDED_USE_HELPER_APP_URI_LOCATION_ID,
                modeFlagsToCheck, newIntentCallerActivity.mComponent);

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getStartActivityTestIntent(NONE_PROVIDED_USE_HELPER_APP_URI_LOCATION_ID,
                modeFlagsToCheck, newIntentCallerActivity.mComponent);

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertEquals("Should return granted with " + modeFlagsToString(modeFlagsToCheck) + " even"
                        + " if the caller of the activity dies",
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_getDataContentUriViaPermission_noPermission(
            @TestParameter ModeFlags modeFlagsToCheck,
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a granted
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getUriInDataSendBroadcastTestIntent(
                CONTENT_URI_READ_PERMISSION, modeFlagsToCheck, newIntentCallerActivity.mComponent);

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getUriInDataSendBroadcastTestIntent(
                CONTENT_URI_NO_PERMISSION, modeFlagsToCheck, newIntentCallerActivity.mComponent);

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertEquals("Should return denied with " + modeFlagsToString(modeFlagsToCheck)
                        + " because we have no access to the content URI",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_getDataContentUriViaPermission_hasRead(
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a denied
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getUriInDataSendBroadcastTestIntent(
                CONTENT_URI_NO_PERMISSION, ModeFlags.READ, newIntentCallerActivity.mComponent);

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getUriInDataSendBroadcastTestIntent(CONTENT_URI_READ_PERMISSION,
                ModeFlags.READ,  newIntentCallerActivity.mComponent);

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertEquals("Should return granted because we have the read permission",
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_getDataContentUriViaPermission_hasReadButNoWrite(
            @TestParameter(valuesProvider = WriteModeFlagsProvider.class)
            ModeFlags modeFlagsToCheck,
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a granted
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getUriInDataSendBroadcastTestIntent(
                CONTENT_URI_READ_PERMISSION, ModeFlags.READ, newIntentCallerActivity.mComponent);

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getUriInDataSendBroadcastTestIntent(
                CONTENT_URI_READ_PERMISSION, modeFlagsToCheck, newIntentCallerActivity.mComponent);

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertEquals("Should return denied because we don't have the write permission",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_clipDataContentUri_noPermission(
            @TestParameter ModeFlags modeFlagsToCheck,
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a granted
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID,
                modeFlagsToCheck, newIntentCallerActivity.mComponent);
        intent.setClipData(ClipData.newRawUri("", CONTENT_URI_READ_PERMISSION));

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID,
                modeFlagsToCheck, newIntentCallerActivity.mComponent);
        newIntent.setClipData(ClipData.newRawUri("", CONTENT_URI_NO_PERMISSION));

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertEquals("Should return denied with " + modeFlagsToString(modeFlagsToCheck)
                        + " because we have no access to the content URI",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_clipDataContentUri_hasRead(
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a denied
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID, ModeFlags.READ,
                newIntentCallerActivity.mComponent);
        intent.setClipData(ClipData.newRawUri("", CONTENT_URI_NO_PERMISSION));

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID, ModeFlags.READ,
                newIntentCallerActivity.mComponent);
        newIntent.setClipData(ClipData.newRawUri("", CONTENT_URI_READ_PERMISSION));

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertEquals("Should return granted because we have the read permission",
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_clipDataContentUri_hasReadButNoWrite(
            @TestParameter(valuesProvider = WriteModeFlagsProvider.class)
            ModeFlags modeFlagsToCheck,
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a granted
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID, ModeFlags.READ,
                newIntentCallerActivity.mComponent);
        intent.setClipData(ClipData.newRawUri("", CONTENT_URI_READ_PERMISSION));

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID,
                modeFlagsToCheck, newIntentCallerActivity.mComponent);
        newIntent.setClipData(ClipData.newRawUri("", CONTENT_URI_READ_PERMISSION));

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertEquals("Should return denied because we don't have the write permission",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_extraStreamContentUri_noPermission(
            @TestParameter ModeFlags modeFlagsToCheck,
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a granted
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getSendBroadcastTestIntent(URI_IN_EXTRA_STREAM_LOCATION_ID,
                modeFlagsToCheck, newIntentCallerActivity.mComponent);
        intent.putExtra(Intent.EXTRA_STREAM, CONTENT_URI_READ_PERMISSION);

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getSendBroadcastTestIntent(URI_IN_EXTRA_STREAM_LOCATION_ID,
                modeFlagsToCheck, newIntentCallerActivity.mComponent);
        newIntent.putExtra(Intent.EXTRA_STREAM, CONTENT_URI_NO_PERMISSION);

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertEquals("Should return denied with " + modeFlagsToString(modeFlagsToCheck)
                        + " because we have no access to the content URI",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_extraStreamContentUri_hasRead(
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a denied
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getSendBroadcastTestIntent(URI_IN_EXTRA_STREAM_LOCATION_ID, ModeFlags.READ,
                newIntentCallerActivity.mComponent);
        intent.putExtra(Intent.EXTRA_STREAM, CONTENT_URI_NO_PERMISSION);

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getSendBroadcastTestIntent(URI_IN_EXTRA_STREAM_LOCATION_ID,
                ModeFlags.READ, newIntentCallerActivity.mComponent);
        newIntent.putExtra(Intent.EXTRA_STREAM, CONTENT_URI_READ_PERMISSION);

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertEquals("Should return granted because we have the read permission",
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_extraStreamContentUri_hasReadButNoWrite(
            @TestParameter(valuesProvider = WriteModeFlagsProvider.class)
            ModeFlags modeFlagsToCheck,
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a granted
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getSendBroadcastTestIntent(URI_IN_EXTRA_STREAM_LOCATION_ID, ModeFlags.READ,
                newIntentCallerActivity.mComponent);
        intent.putExtra(Intent.EXTRA_STREAM, CONTENT_URI_READ_PERMISSION);

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getSendBroadcastTestIntent(URI_IN_EXTRA_STREAM_LOCATION_ID,
                modeFlagsToCheck, newIntentCallerActivity.mComponent);
        newIntent.putExtra(Intent.EXTRA_STREAM, CONTENT_URI_READ_PERMISSION);

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertEquals("Should return denied because we don't have the write permission",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_arrayListExtraStreamsContentUri_noPermission(
            @TestParameter ModeFlags modeFlagsToCheck,
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a granted
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getSendBroadcastTestIntent(URI_IN_ARRAY_LIST_EXTRA_STREAMS_LOCATION_ID,
                modeFlagsToCheck, newIntentCallerActivity.mComponent);
        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(CONTENT_URI_READ_PERMISSION);
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getSendBroadcastTestIntent(URI_IN_ARRAY_LIST_EXTRA_STREAMS_LOCATION_ID,
                modeFlagsToCheck, newIntentCallerActivity.mComponent);
        ArrayList<Uri> newUris = new ArrayList<>();
        newUris.add(CONTENT_URI_NO_PERMISSION);
        newIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, newUris);

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertEquals("Should return denied with " + modeFlagsToString(modeFlagsToCheck)
                        + " because we have no access to the content URI",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_arrayListExtraStreamsContentUri_hasRead(
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a denied
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getSendBroadcastTestIntent(URI_IN_ARRAY_LIST_EXTRA_STREAMS_LOCATION_ID,
                ModeFlags.READ, newIntentCallerActivity.mComponent);
        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(CONTENT_URI_NO_PERMISSION);
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getSendBroadcastTestIntent(URI_IN_ARRAY_LIST_EXTRA_STREAMS_LOCATION_ID,
                ModeFlags.READ, newIntentCallerActivity.mComponent);
        ArrayList<Uri> newUris = new ArrayList<>();
        newUris.add(CONTENT_URI_READ_PERMISSION);
        newIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, newUris);

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertEquals("Should return granted because we have the read permission",
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_arrayListExtraStreamsContentUri_hasReadButNoWrite(
            @TestParameter(valuesProvider = WriteModeFlagsProvider.class)
            ModeFlags modeFlagsToCheck,
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a granted
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getSendBroadcastTestIntent(URI_IN_ARRAY_LIST_EXTRA_STREAMS_LOCATION_ID,
                ModeFlags.READ, newIntentCallerActivity.mComponent);
        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(CONTENT_URI_READ_PERMISSION);
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getSendBroadcastTestIntent(URI_IN_ARRAY_LIST_EXTRA_STREAMS_LOCATION_ID,
                modeFlagsToCheck, newIntentCallerActivity.mComponent);
        ArrayList<Uri> newUris = new ArrayList<>();
        newUris.add(CONTENT_URI_READ_PERMISSION);
        newIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, newUris);

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertEquals("Should return denied because we don't have the write permission",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_unknownExtraContentUri_throwsIllegalArgumentException(
            @TestParameter ModeFlags modeFlagsToCheck,
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a denied
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getSendBroadcastTestIntent(URI_IN_EXTRA_STREAM_LOCATION_ID,
                modeFlagsToCheck, newIntentCallerActivity.mComponent);
        intent.putExtra(Intent.EXTRA_STREAM, CONTENT_URI_NO_PERMISSION);

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getSendBroadcastTestIntent(URI_IN_EXTRA_UNKNOWN_LOCATION_ID,
                modeFlagsToCheck, newIntentCallerActivity.mComponent);
        newIntent.putExtra(EXTRA_UNKNOWN, CONTENT_URI_NO_PERMISSION);

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertTrue("Should throw an IllegalArgumentException for an unknown EXTRA",
                TestResults.sIsIllegalArgumentExceptionCaught);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityNewIntentCaller_checkContentUriPermission_passingNonUriExtraStreamDoesNotAffectRetrievalOfExtras(
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // Notice that the values of EXTRA_STREAM and EXTRA_REFERRER_NAME are different between the
        // first and the second launch. This is to verify the call into onNewIntent does not affect
        // retrieval of extras.
        Intent intent = getTryToRetrieveExtrasTestIntent(newIntentCallerActivity.mComponent);
        String referrerName = "ComponentCaller1";
        intent.putExtra(Intent.EXTRA_STREAM, CONTENT_URI_NO_PERMISSION);
        intent.putExtra(Intent.EXTRA_REFERRER_NAME, referrerName);

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getTryToRetrieveExtrasTestIntent(newIntentCallerActivity.mComponent);
        String nonUriExtraStream = "non-uri";
        String newReferrerName = "ComponentCaller2";
        newIntent.putExtra(Intent.EXTRA_STREAM, nonUriExtraStream);
        newIntent.putExtra(Intent.EXTRA_REFERRER_NAME, newReferrerName);

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertEquals("Passing a non-URI item as EXTRA_STREAM should not affect the retrieval of"
                + " EXTRA_STREAM", nonUriExtraStream, TestResults.sExtraStreamString);
        assertEquals("Passing a non-URI item as EXTRA_STREAM should not affect the retrieval of"
                        + " other extras, such as EXTRA_REFERRER_NAME",
                newReferrerName, TestResults.sReferrerName);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onNewIntent(Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void testActivityNewIntentCaller_checkContentUriPermission_contentUriViaGrant(
            @TestParameter ModeFlags modeFlagsToCheck,
            @TestParameter NewIntentCallerActivity newIntentCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a denied
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getUriInDataSendBroadcastTestIntent(CONTENT_URI_NO_PERMISSION,
                modeFlagsToCheck, newIntentCallerActivity.mComponent);

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        Intent newIntent = getUriInDataSendBroadcastTestIntent(TestProvider.getContentUri(),
                modeFlagsToCheck, newIntentCallerActivity.mComponent);
        newIntent.addFlags(modeFlagsToCheck.mValue);

        mContext.startActivity(newIntent);

        assertActivityWasInvoked();
        assertEquals("Should return granted because we granted "
                        + modeFlagsToString(modeFlagsToCheck),
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCaller", "android.app.Activity#getIntent",
            "android.app.Activity#setIntent"})
    @CddTest(requirements = {"4/C-0-2"})
    public void testActivityGetSetNewIntentCaller(
            @TestParameter NewIntentGetSetCallerActivity activity) throws Exception {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(mContext, activity.mCls));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

        mContext.startActivity(intent);

        assertActivityWasInvoked();

        TestResults.reset();
        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertTrue("The caller in #setIntent(Intent, ComponentCaller) does not equal to"
                + " #getCaller()", TestResults.sAreSetGetCallerEqual);
        assertTrue("The intent in #setIntent(Intent, ComponentCaller) does not equal to"
                + " #getIntent()", TestResults.sAreSetGetIntentEqual);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller"})
    @CddTest(requirements = {"4/C-0-2"})
    public void testActivityGetCurrentCaller_throwsIfCalledOutsideOnNewIntent() throws Exception {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(mContext,
                NewIntentGetSetIntentCurrentCallerTestActivity.class));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertTrue("#getCurrentCaller should throw an IllegalStateException outside #onNewIntent",
                TestResults.sGetCurrentCallerThrowsIllegalStateExceptionInOnStart);

        TestResults.reset();
        mContext.startActivity(intent);

        assertActivityWasInvoked();
        Assert.assertFalse("#getCurrentCaller should not throw an IllegalStateException inside"
                + " #onNewIntent",
                TestResults.sGetCurrentCallerThrowsIllegalStateExceptionInOnNewIntent);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onActivityResult(int,int,Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityResultCaller_checkContentUriPermission_throwsIfCallerOfApiDoesNotHaveTheSameAccessToContentUri(
            @TestParameter ModeFlags modeFlagsToCheck,
            @TestParameter ResultCallerActivity resultCallerActivity) throws Exception {
        // We launch the test activity with an URI that is not supposed to throw a SecurityException.
        // This is to verify that the initial caller is not confused with the result caller. The
        // test activity will then call {@code startActivityForResult} on the
        // {@link SetResultTestActivity}, which will modify the result intent and set the correct
        // URI based on the extras below. Once the test app receives the result, it will send back a
        // broadcast with the result caller's test results.
        Intent intent = getUriInDataSendBroadcastTestIntent(HELPER_APP_URI, modeFlagsToCheck,
                resultCallerActivity.mComponent);

        intent.putExtra(SET_RESULT_ACTION_ID, URI_PROVIDED_SET_RESULT_ACTION_ID);
        intent.putExtra(RESULT_URI_TYPE, PROVIDER_RESULT_URI_TYPE);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertTrue("Should throw a SecurityException because the result caller of the API doesn't"
                        + " have the same " + modeFlagsToString(modeFlagsToCheck)
                        + " access to a content URI",
                TestResults.sIsSecurityExceptionCaught);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onActivityResult(int,int,Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityResultCaller_checkContentUriPermission_throwsIfContentUriWasNotPassedAtLaunch(
            @TestParameter ModeFlags modeFlagsToCheck,
            @TestParameter ResultCallerActivity resultCallerActivity) throws Exception {
        // We launch the test activity with an URI that is not supposed to throw an
        // IllegalArgumentException. This is to verify that the initial caller is not confused with
        // the result caller. The test activity will then call {@code startActivityForResult} on the
        // {@link SetResultTestActivity}, which will modify the result intent and set the correct
        // URI based on the extras below. Once the test app receives the result, it will send back a
        // broadcast with the result caller's test results.
        Intent intent = getUriInDataSendBroadcastTestIntent(HELPER_APP_URI, modeFlagsToCheck,
                resultCallerActivity.mComponent);

        intent.putExtra(SET_RESULT_ACTION_ID, NO_URI_PROVIDED_SET_RESULT_ACTION_ID);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertTrue("Should throw an IllegalArgumentException because the supplied content URI was"
                        + " not passed at launch",
                TestResults.sIsIllegalArgumentExceptionCaught);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onActivityResult(int,int,Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityResultCaller_checkContentUriPermission_getDataContentUriViaPermission_noPermission(
            @TestParameter ModeFlags modeFlagsToCheck,
            @TestParameter ResultCallerActivity resultCallerActivity) throws Exception {
        // We launch the test activity with an URI that is supposed to return a granted permission
        // result. This is to verify that the initial caller is not confused with the result caller.
        // The test activity will then call {@code startActivityForResult} on the
        // {@link SetResultTestActivity}, which will modify the result intent and set the correct
        // URI based on the extras below. Once the test app receives the result, it will send back a
        // broadcast with the result caller's test results.
        Intent intent = getUriInDataSendBroadcastTestIntent(CONTENT_URI_READ_PERMISSION,
                modeFlagsToCheck, resultCallerActivity.mComponent);

        intent.putExtra(SET_RESULT_ACTION_ID, URI_PROVIDED_SET_RESULT_ACTION_ID);
        intent.putExtra(RESULT_URI_TYPE, NO_PERMISSION_URI_TYPE);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return denied with " + modeFlagsToString(modeFlagsToCheck)
                        + " because we have no access to the content URI",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onActivityResult(int,int,Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityResultCaller_checkContentUriPermission_getDataContentUriViaPermission_hasRead(
            @TestParameter ResultCallerActivity resultCallerActivity) throws Exception {
        // We launch the test activity with an URI that is supposed to return a denied permission
        // result. This is to verify that the initial caller is not confused with the result caller.
        // The test activity will then call {@code startActivityForResult} on the
        // {@link SetResultTestActivity}, which will modify the result intent and set the correct
        // URI based on the extras below. Once the test app receives the result, it will send back a
        // broadcast with the result caller's test results.
        Intent intent = getUriInDataSendBroadcastTestIntent(CONTENT_URI_NO_PERMISSION,
                ModeFlags.READ,  resultCallerActivity.mComponent);

        intent.putExtra(SET_RESULT_ACTION_ID, URI_PROVIDED_SET_RESULT_ACTION_ID);
        intent.putExtra(RESULT_URI_TYPE, READ_PERMISSION_URI_TYPE);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return granted because we have the read permission",
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onActivityResult(int,int,Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityResultCaller_checkContentUriPermission_getDataContentUriViaPermission_hasReadButNoWrite(
            @TestParameter(valuesProvider = WriteModeFlagsProvider.class)
            ModeFlags modeFlagsToCheck,
            @TestParameter ResultCallerActivity resultCallerActivity) throws Exception {
        // We launch the test activity with an URI that is supposed to return a granted permission
        // result. This is to verify that the initial caller is not confused with the result caller.
        // The test activity will then call {@code startActivityForResult} on the
        // {@link SetResultTestActivity}, which will modify the result intent and set the correct
        // URI based on the extras below. Once the test app receives the result, it will send back a
        // broadcast with the result caller's test results.
        Intent intent = getUriInDataSendBroadcastTestIntent(
                CONTENT_URI_READ_PERMISSION, ModeFlags.READ, resultCallerActivity.mComponent);

        intent.putExtra(SET_RESULT_ACTION_ID, PUT_MODE_FLAGS_TO_CHECK_SET_RESULT_ACTION_ID);
        intent.putExtra(PUT_MODE_FLAGS, modeFlagsToCheck.mValue);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return denied because we don't have the write permission",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onActivityResult(int,int,Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityResultCaller_checkContentUriPermission_clipDataContentUri_noPermission(
            @TestParameter ModeFlags modeFlagsToCheck,
            @TestParameter ResultCallerActivity resultCallerActivity) throws Exception {
        // We launch the test activity with an URI that is supposed to return a granted permission
        // result. This is to verify that the initial caller is not confused with the result caller.
        // The test activity will then call {@code startActivityForResult} on the
        // {@link SetResultTestActivity}, which will modify the result intent and set the correct
        // URI based on the extras below. Once the test app receives the result, it will send back a
        // broadcast with the result caller's test results.
        Intent intent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID,
                modeFlagsToCheck, resultCallerActivity.mComponent);
        intent.setClipData(ClipData.newRawUri("", CONTENT_URI_READ_PERMISSION));

        intent.putExtra(SET_RESULT_ACTION_ID, URI_PROVIDED_SET_RESULT_ACTION_ID);
        intent.putExtra(RESULT_URI_TYPE, NO_PERMISSION_URI_TYPE);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return denied with "
                        + modeFlagsToString(modeFlagsToCheck) + " because we have no access to"
                        + " the content URI",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onActivityResult(int,int,Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityResultCaller_checkContentUriPermission_clipDataContentUri_hasRead(
            @TestParameter ResultCallerActivity resultCallerActivity) throws Exception {
        // We launch the test activity with an URI that is supposed to return a denied permission
        // result. This is to verify that the initial caller is not confused with the result caller.
        // The test activity will then call {@code startActivityForResult} on the
        // {@link SetResultTestActivity}, which will modify the result intent and set the correct
        // URI based on the extras below. Once the test app receives the result, it will send back a
        // broadcast with the result caller's test results.
        Intent intent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID, ModeFlags.READ,
                resultCallerActivity.mComponent);
        intent.setClipData(ClipData.newRawUri("", CONTENT_URI_NO_PERMISSION));

        intent.putExtra(SET_RESULT_ACTION_ID, URI_PROVIDED_SET_RESULT_ACTION_ID);
        intent.putExtra(RESULT_URI_TYPE, READ_PERMISSION_URI_TYPE);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return granted because we have the read permission",
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onActivityResult(int,int,Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityResultCaller_checkContentUriPermission_clipDataContentUri_hasReadButNoWrite(
            @TestParameter(valuesProvider = WriteModeFlagsProvider.class)
            ModeFlags modeFlagsToCheck,
            @TestParameter ResultCallerActivity resultCallerActivity) throws Exception {
        // We launch the test activity with an URI that is supposed to return a granted permission
        // result. This is to verify that the initial caller is not confused with the result caller.
        // The test activity will then call {@code startActivityForResult} on the
        // {@link SetResultTestActivity}, which will modify the result intent and set the correct
        // URI based on the extras below. Once the test app receives the result, it will send back a
        // broadcast with the result caller's test results.
        Intent intent = getSendBroadcastTestIntent(URI_IN_CLIP_DATA_LOCATION_ID,
                ModeFlags.READ, resultCallerActivity.mComponent);
        intent.setClipData(ClipData.newRawUri("", CONTENT_URI_READ_PERMISSION));

        intent.putExtra(SET_RESULT_ACTION_ID, PUT_MODE_FLAGS_TO_CHECK_SET_RESULT_ACTION_ID);
        intent.putExtra(PUT_MODE_FLAGS, modeFlagsToCheck.mValue);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return denied because we don't have the write permission",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onActivityResult(int,int,Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityResultCaller_checkContentUriPermission_extraStreamContentUri_noPermission(
            @TestParameter ModeFlags modeFlagsToCheck,
            @TestParameter ResultCallerActivity resultCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a granted
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getSendBroadcastTestIntent(URI_IN_EXTRA_STREAM_LOCATION_ID,
                modeFlagsToCheck, resultCallerActivity.mComponent);
        intent.putExtra(Intent.EXTRA_STREAM, CONTENT_URI_READ_PERMISSION);

        // The activity will call startActivityForResult on the {@link SetResultTestActivity}, which
        // will set the correct URI to send as the result based on the extras below.
        intent.putExtra(SET_RESULT_ACTION_ID, URI_PROVIDED_SET_RESULT_ACTION_ID);
        intent.putExtra(RESULT_URI_TYPE, NO_PERMISSION_URI_TYPE);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return denied with " + modeFlagsToString(modeFlagsToCheck)
                        + " because we have no access to the content URI",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onActivityResult(int,int,Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityResultCaller_checkContentUriPermission_extraStreamContentUri_hasRead(
            @TestParameter ResultCallerActivity resultCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a denied
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getSendBroadcastTestIntent(URI_IN_EXTRA_STREAM_LOCATION_ID, ModeFlags.READ,
                resultCallerActivity.mComponent);
        intent.putExtra(Intent.EXTRA_STREAM, CONTENT_URI_NO_PERMISSION);

        // The activity will call startActivityForResult on the {@link SetResultTestActivity}, which
        // will set the correct URI to send as the result based on the extras below.
        intent.putExtra(SET_RESULT_ACTION_ID, URI_PROVIDED_SET_RESULT_ACTION_ID);
        intent.putExtra(RESULT_URI_TYPE, READ_PERMISSION_URI_TYPE);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return granted because we have the read permission",
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onActivityResult(int,int,Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityResultCaller_checkContentUriPermission_extraStreamContentUri_hasReadButNoWrite(
            @TestParameter(valuesProvider = WriteModeFlagsProvider.class)
            ModeFlags modeFlagsToCheck,
            @TestParameter ResultCallerActivity resultCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a granted
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getSendBroadcastTestIntent(URI_IN_EXTRA_STREAM_LOCATION_ID, ModeFlags.READ,
                resultCallerActivity.mComponent);
        intent.putExtra(Intent.EXTRA_STREAM, CONTENT_URI_READ_PERMISSION);

        // The activity will call startActivityForResult on the {@link SetResultTestActivity}, which
        // will set the correct URI to send as the result based on the extras below.
        intent.putExtra(SET_RESULT_ACTION_ID, PUT_MODE_FLAGS_TO_CHECK_SET_RESULT_ACTION_ID);
        intent.putExtra(PUT_MODE_FLAGS, modeFlagsToCheck.mValue);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return denied because we don't have the write permission",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onActivityResult(int,int,Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityResultCaller_checkContentUriPermission_arrayListExtraStreamsContentUri_noPermission(
            @TestParameter ModeFlags modeFlagsToCheck,
            @TestParameter ResultCallerActivity resultCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a granted
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getSendBroadcastTestIntent(URI_IN_ARRAY_LIST_EXTRA_STREAMS_LOCATION_ID,
                modeFlagsToCheck, resultCallerActivity.mComponent);
        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(CONTENT_URI_READ_PERMISSION);
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

        // The activity will call startActivityForResult on the {@link SetResultTestActivity}, which
        // will set the correct URI to send as the result based on the extras below.
        intent.putExtra(SET_RESULT_ACTION_ID, URI_PROVIDED_SET_RESULT_ACTION_ID);
        intent.putExtra(RESULT_URI_TYPE, NO_PERMISSION_URI_TYPE);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return denied with " + modeFlagsToString(modeFlagsToCheck)
                        + " because we have no access to the content URI",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onActivityResult(int,int,Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityResultCaller_checkContentUriPermission_arrayListExtraStreamsContentUri_hasRead(
            @TestParameter ResultCallerActivity resultCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a denied
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getSendBroadcastTestIntent(URI_IN_ARRAY_LIST_EXTRA_STREAMS_LOCATION_ID,
                ModeFlags.READ, resultCallerActivity.mComponent);
        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(CONTENT_URI_NO_PERMISSION);
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

        // The activity will call startActivityForResult on the {@link SetResultTestActivity}, which
        // will set the correct URI to send as the result based on the extras below.
        intent.putExtra(SET_RESULT_ACTION_ID, URI_PROVIDED_SET_RESULT_ACTION_ID);
        intent.putExtra(RESULT_URI_TYPE, READ_PERMISSION_URI_TYPE);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return granted because we have the read permission",
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onActivityResult(int,int,Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityResultCaller_checkContentUriPermission_arrayListExtraStreamsContentUri_hasReadButNoWrite(
            @TestParameter(valuesProvider = WriteModeFlagsProvider.class)
            ModeFlags modeFlagsToCheck,
            @TestParameter ResultCallerActivity resultCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a granted
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getSendBroadcastTestIntent(URI_IN_ARRAY_LIST_EXTRA_STREAMS_LOCATION_ID,
                ModeFlags.READ, resultCallerActivity.mComponent);
        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(CONTENT_URI_READ_PERMISSION);
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

        // The activity will call startActivityForResult on the {@link SetResultTestActivity}, which
        // will set the correct URI to send as the result based on the extras below.
        intent.putExtra(SET_RESULT_ACTION_ID, PUT_MODE_FLAGS_TO_CHECK_SET_RESULT_ACTION_ID);
        intent.putExtra(PUT_MODE_FLAGS, modeFlagsToCheck.mValue);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return denied because we don't have the write permission",
                PERMISSION_DENIED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onActivityResult(int,int,Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityResultCaller_checkContentUriPermission_unknownExtraContentUri_throwsIllegalArgumentException(
            @TestParameter ModeFlags modeFlagsToCheck,
            @TestParameter ResultCallerActivity resultCallerActivity) throws Exception {
        // The first time we launch the activity, the URI passed is supposed return a denied
        // permission result. This is to verify the new intent caller is correct.
        Intent intent = getSendBroadcastTestIntent(URI_IN_EXTRA_STREAM_LOCATION_ID,
                modeFlagsToCheck, resultCallerActivity.mComponent);
        intent.putExtra(Intent.EXTRA_STREAM, CONTENT_URI_NO_PERMISSION);

        // The activity will call startActivityForResult on the {@link SetResultTestActivity}, which
        // will set the correct URI to send as the result based on the extras below.
        intent.putExtra(SET_RESULT_ACTION_ID,
                PUT_EXTRA_UNKNOWN_REMOVE_EXTRA_STREAM_SET_RESULT_ACTION_ID);
        intent.putExtra(RESULT_URI_TYPE, NO_PERMISSION_URI_TYPE);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertTrue("Should throw an IllegalArgumentException for an unknown EXTRA",
                TestResults.sIsIllegalArgumentExceptionCaught);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onActivityResult(int,int,Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void
    testActivityResultCaller_checkContentUriPermission_passingNonUriExtraStreamDoesNotAffectRetrievalOfExtras(
            @TestParameter ResultCallerActivity resultCallerActivity) throws Exception {
        // Notice that the values of EXTRA_STREAM and EXTRA_REFERRER_NAME are different between the
        // first and the second launch. This is to verify the call into onNewIntent does not affect
        // retrieval of extras.
        Intent intent = getTryToRetrieveExtrasTestIntent(resultCallerActivity.mComponent);
        String referrerName = "ComponentCaller1";
        intent.putExtra(Intent.EXTRA_STREAM, CONTENT_URI_NO_PERMISSION);
        intent.putExtra(Intent.EXTRA_REFERRER_NAME, referrerName);

        // The activity will call startActivityForResult on the {@link SetResultTestActivity}, which
        // will set the correct URI to send as the result based on the extras below.
        String nonUriExtraStream = "non-uri";
        String newReferrerName = "ComponentCaller2";
        intent.putExtra(SET_RESULT_ACTION_ID, PUT_NON_URI_EXTRA_STREAM_SET_RESULT_ACTION_ID);
        intent.putExtra(RESULT_NON_URI_EXTRA_STREAM, nonUriExtraStream);
        intent.putExtra(RESULT_EXTRA_REFERRER_NAME, newReferrerName);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Passing a non-URI item as EXTRA_STREAM should not affect the retrieval of"
                + " EXTRA_STREAM", nonUriExtraStream, TestResults.sExtraStreamString);
        assertEquals("Passing a non-URI item as EXTRA_STREAM should not affect the retrieval of"
                        + " other extras, such as EXTRA_REFERRER_NAME",
                newReferrerName, TestResults.sReferrerName);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller",
            "android.app.Activity#onActivityResult(int,int,Intent,ComponentCaller)",
            "android.app.ComponentCaller#checkContentUriPermission"})
    @CddTest(requirements = {"4/C-0-2"})
    public void testActivityResultCaller_checkContentUriPermission_contentUriViaGrant(
            @TestParameter ModeFlags modeFlagsToCheck,
            @TestParameter ResultCallerActivity resultCallerActivity) throws Exception {
        // We launch the test activity with an URI that is supposed to return a denied permission
        // result. This is to verify that the initial caller is not confused with the result caller.
        // The test activity will then call {@code startActivityForResult} on the
        // {@link SetResultTestActivity}, which will modify the result intent and set the correct
        // URI based on the extras below. Once the test app receives the result, it will send back a
        // broadcast with the result caller's test results.
        Intent intent = getUriInDataSendBroadcastTestIntent(TestProvider.getContentUri(),
                modeFlagsToCheck, resultCallerActivity.mComponent);

        intent.putExtra(SET_RESULT_ACTION_ID, GRANT_FLAGS_SET_RESULT_ACTION_ID);
        intent.putExtra(GRANT_MODE_FLAGS, modeFlagsToCheck.mValue);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Should return granted because we granted "
                        + modeFlagsToString(modeFlagsToCheck),
                PERMISSION_GRANTED, TestResults.sCheckContentUriPermissionRes);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCaller", "android.app.Activity#getIntent",
            "android.app.Activity#setIntent"})
    @CddTest(requirements = {"4/C-0-2"})
    public void testActivityGetSetIntentResultCaller(
            @TestParameter ResultGetSetCallerActivity activity) throws Exception {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(mContext, activity.mCls));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertTrue("The caller in #setIntent(Intent, ComponentCaller) does not equal to"
                + " #getCaller()", TestResults.sAreSetGetCallerEqual);
        assertTrue("The intent in #setIntent(Intent, ComponentCaller) does not equal to"
                + " #getIntent()", TestResults.sAreSetGetIntentEqual);
    }

    @Test
    @ApiTest(apis = {"android.app.Activity#getCurrentCaller"})
    @CddTest(requirements = {"4/C-0-2"})
    public void testActivityGetCurrentCaller_throwsIfCalledOutsideOnActivityResult()
            throws Exception {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(mContext,
                ResultGetSetIntentCurrentCallerTestActivity.class));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

        mContext.startActivity(intent);

        assertActivityWasInvoked();
        assertTrue("#getCurrentCaller should throw an IllegalStateException outside"
                        + " #onActivityResult",
                TestResults.sGetCurrentCallerThrowsIllegalStateExceptionInOnStart);
        Assert.assertFalse("#getCurrentCaller should not throw an IllegalStateException inside"
                        + " #onActivityResult",
                TestResults.sGetCurrentCallerThrowsIllegalStateExceptionInOnActivityResult);
    }

    private Intent getSendBroadcastTestIntent(int uriLocationId, ModeFlags modeFlagsToCheck,
            ComponentName component) {
        Intent intent = new Intent();
        intent.setComponent(component);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        intent.putExtra(URI_LOCATION_ID, uriLocationId);
        intent.putExtra(TEST_ACTION_ID, SEND_TEST_BROADCAST_ACTION_ID);
        intent.putExtra(MODE_FLAGS_TO_CHECK, modeFlagsToCheck.mValue);
        intent.putExtra(IS_NEW_INTENT, isNewIntentComponent(component));
        intent.putExtra(IS_RESULT, isResultComponent(component));
        return intent;
    }

    private Intent getUriInDataSendBroadcastTestIntent(Uri uri, ModeFlags modeFlagsToCheck,
            ComponentName component) {
        Intent intent = getSendBroadcastTestIntent(URI_IN_DATA_LOCATION_ID,
                modeFlagsToCheck, component);
        intent.setAction(Intent.ACTION_ATTACH_DATA);
        intent.setData(uri);
        return intent;
    }

    private Intent getStartActivityTestIntent(int uriLocationId, ModeFlags modeFlagsToCheck,
            ComponentName component) {
        Intent intent = getSendBroadcastTestIntent(uriLocationId, modeFlagsToCheck, component);
        intent.putExtra(TEST_ACTION_ID, START_TEST_ACTIVITY_ACTION_ID);
        return intent;
    }

    private Intent getTryToRetrieveExtrasTestIntent(ComponentName component) {
        Intent intent = new Intent();
        intent.setComponent(component);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        intent.putExtra(TEST_ACTION_ID, TRY_TO_RETRIEVE_EXTRA_STREAM_REFERRER_NAME);
        intent.putExtra(IS_NEW_INTENT, isNewIntentComponent(component));
        intent.putExtra(IS_RESULT, isResultComponent(component));
        return intent;
    }

    private String modeFlagsToString(ModeFlags modeFlags) {
        return switch (modeFlags.mValue) {
            case Intent.FLAG_GRANT_READ_URI_PERMISSION -> "read";
            case Intent.FLAG_GRANT_WRITE_URI_PERMISSION -> "write";
            case Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION ->
                    "read and write";
            default -> throw new RuntimeException("Invalid modeFlags");
        };
    }

    private void assertActivityWasInvoked() throws Exception {
        assertTrue("Activity was not invoked by the timeout",
                TestResults.sLatch.await(10, TimeUnit.SECONDS));
    }

    private boolean isNewIntentComponent(ComponentName component) {
        return component.equals(HELPER_APP_NEW_INTENT_GET_CURRENT_CALLER_ACTIVITY)
                || component.equals(HELPER_APP_NEW_INTENT_OVERLOAD_CALLER_ACTIVITY);
    }

    private boolean isResultComponent(ComponentName component) {
        return component.equals(HELPER_APP_RESULT_GET_CURRENT_CALLER_ACTIVITY)
                || component.equals(HELPER_APP_RESULT_OVERLOAD_CALLER_ACTIVITY);
    }

    /** Results for each test. Use {@link #reset()} to reset all results. */
    private static final class TestResults {
        static CountDownLatch sLatch;
        // checkContentUriPermission() results
        static boolean sIsSecurityExceptionCaught;
        static boolean sIsIllegalArgumentExceptionCaught;
        static Uri sReceivedUri;
        static int sCheckContentUriPermissionRes;
        // Activity related get-set results
        static boolean sAreSetGetCallerEqual;
        static boolean sAreSetGetIntentEqual;
        static boolean sGetCurrentCallerThrowsIllegalStateExceptionInOnStart;
        static boolean sGetCurrentCallerThrowsIllegalStateExceptionInOnNewIntent;
        static boolean sGetCurrentCallerThrowsIllegalStateExceptionInOnActivityResult;
        // TRY_TO_RETRIEVE_EXTRA_STREAM_REFERRER_NAME data
        static String sExtraStreamString;
        static String sReferrerName;

        static void reset() {
            sLatch = new CountDownLatch(1);
            sIsSecurityExceptionCaught = false;
            sIsIllegalArgumentExceptionCaught = false;
            sReceivedUri = null;
            sCheckContentUriPermissionRes = INVALID_PERMISSION_RESULT;
            sAreSetGetCallerEqual = false;
            sAreSetGetIntentEqual = false;
            sGetCurrentCallerThrowsIllegalStateExceptionInOnStart = false;
            sGetCurrentCallerThrowsIllegalStateExceptionInOnNewIntent = true;
            sGetCurrentCallerThrowsIllegalStateExceptionInOnActivityResult = true;
            sExtraStreamString = null;
            sReferrerName = null;
        }
    }

    public static final class TestReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            TestResults.sIsSecurityExceptionCaught =
                    intent.getBooleanExtra(EXTRA_SECURITY_EXCEPTION_CAUGHT, false);
            TestResults.sIsIllegalArgumentExceptionCaught =
                    intent.getBooleanExtra(EXTRA_ILLEGAL_ARG_EXCEPTION_CAUGHT, false);
            TestResults.sCheckContentUriPermissionRes =
                    intent.getIntExtra(EXTRA_CHECK_CONTENT_URI_PERMISSION_RESULT,
                            INVALID_PERMISSION_RESULT);
            TestResults.sExtraStreamString = intent.getStringExtra(Intent.EXTRA_STREAM);
            TestResults.sReferrerName = intent.getStringExtra(Intent.EXTRA_REFERRER_NAME);
            TestResults.sLatch.countDown();
        }
    }

    public static class InitialCallerTestActivity extends Activity {
        private static final String TAG = "InitialCallerTestActivity";
        @Override
        public void onStart() {
            super.onStart();
            Log.i(TAG, "onStart: " + getIntent());
            performTest(getIntent(), getInitialCaller());
        }

        protected void performTest(Intent intent, ComponentCaller caller) {
            TestResults.sReceivedUri = intent.getData();
            int modeFlags = intent.getIntExtra(MODE_FLAGS_TO_CHECK, -1);
            boolean isNewIntent = intent.getBooleanExtra(IS_NEW_INTENT, false);
            if (TestResults.sReceivedUri != null) {
                TestResults.sCheckContentUriPermissionRes = caller
                        .checkContentUriPermission(TestResults.sReceivedUri,
                                modeFlags);
            }
            TestResults.sLatch.countDown();
            if (!isNewIntent) {
                finish();
            }
        }
    }

    public static final class NewIntentGetCurrentCallerTestActivity
            extends InitialCallerTestActivity {
        private static final String TAG = "NewIntentGetCurrentCallerTestActivity";
        @Override
        public void onNewIntent(Intent intent) {
            Log.i(TAG, "onNewIntent: " + intent);
            performTest(intent, getCurrentCaller());
            finish();
        }
    }

    public static final class NewIntentOverloadCallerTestActivity
            extends InitialCallerTestActivity {
        private static final String TAG = "NewIntentOverloadCallerTestActivity";
        @Override
        public void onNewIntent(Intent intent, ComponentCaller caller) {
            Log.i(TAG, "onNewIntent: " + intent);
            performTest(intent, caller);
            finish();
        }
    }

    public static class NewIntentGetSetIntentCurrentCallerTestActivity extends Activity {
        private static final String TAG = "NewIntentGetSetIntentCurrentCallerTestActivity";
        @Override
        public void onStart() {
            super.onStart();
            Log.i(TAG, "onStart: " + getIntent());
            try {
                getCurrentCaller();
            } catch (IllegalStateException e) {
                TestResults.sGetCurrentCallerThrowsIllegalStateExceptionInOnStart = true;
            }
            TestResults.sLatch.countDown();
        }

        @Override
        public void onNewIntent(Intent intent) {
            Log.i(TAG, "onNewIntent: " + intent);
            ComponentCaller caller = getCurrentCaller();
            setIntent(intent, caller);
            TestResults.sAreSetGetCallerEqual = caller.equals(getCaller());
            TestResults.sAreSetGetIntentEqual = intent.equals(getIntent());
            TestResults.sGetCurrentCallerThrowsIllegalStateExceptionInOnNewIntent = false;
            TestResults.sLatch.countDown();
            finish();
        }
    }

    public static final class NewIntentGetSetIntentOverloadCallerTestActivity
            extends NewIntentGetSetIntentCurrentCallerTestActivity {
        private static final String TAG = "NewIntentGetSetIntentOverloadCallerTestActivity";
        @Override
        public void onNewIntent(Intent intent, ComponentCaller caller) {
            Log.i(TAG, "onNewIntent: " + intent);
            setIntent(intent, caller);
            TestResults.sAreSetGetCallerEqual = caller.equals(getCaller());
            TestResults.sAreSetGetIntentEqual = intent.equals(getIntent());
            TestResults.sLatch.countDown();
            finish();
        }
    }

    public static class ResultGetSetIntentCurrentCallerTestActivity extends Activity {
        private static final String TAG = "ResultGetSetIntentCurrentCallerTestActivity";
        @Override
        public void onStart() {
            super.onStart();
            Log.i(TAG, "onStart: " + getIntent());
            try {
                getCurrentCaller();
            } catch (IllegalStateException e) {
                TestResults.sGetCurrentCallerThrowsIllegalStateExceptionInOnStart = true;
            }
            Intent intent = new Intent();
            intent.setComponent(TEST_SET_RESULT_ACTIVITY);
            intent.putExtra(SET_RESULT_ACTION_ID, NO_ACTION_NEEDED_SET_RESULT_ACTION_ID);
            startActivityForResult(intent, 0);
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent intent) {
            Log.i(TAG, "onActivityResult: " + intent);
            ComponentCaller caller = getCurrentCaller();
            setIntent(intent, caller);
            TestResults.sAreSetGetCallerEqual = caller.equals(getCaller());
            TestResults.sAreSetGetIntentEqual = intent.equals(getIntent());
            TestResults.sGetCurrentCallerThrowsIllegalStateExceptionInOnActivityResult = false;
            TestResults.sLatch.countDown();
            finish();
        }
    }

    public static final class ResultGetSetIntentOverloadCallerTestActivity
            extends ResultGetSetIntentCurrentCallerTestActivity {
        private static final String TAG = "ResultGetSetIntentOverloadCallerTestActivity";
        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent intent,
                ComponentCaller caller) {
            Log.i(TAG, "onActivityResult: " + intent);
            setIntent(intent, caller);
            TestResults.sAreSetGetCallerEqual = caller.equals(getCaller());
            TestResults.sAreSetGetIntentEqual = intent.equals(getIntent());
            TestResults.sLatch.countDown();
            finish();
        }
    }

    public static final class SetResultTestActivity extends Activity {
        private static final String TAG = "SetResultTestActivity";
        @Override
        public void onStart() {
            super.onStart();
            Intent intent = getIntent();
            Log.i(TAG, "onStart: " + intent);
            setResult(RESULT_OK, buildResultIntent(intent));
            finish();
        }

        private Intent buildResultIntent(Intent intent) {
            Intent resultIntent = new Intent(intent);
            resultIntent.setComponent(null);
            resultIntent.setFlags(0);

            int actionId = resultIntent.getIntExtra(SET_RESULT_ACTION_ID, -1);
            switch (actionId) {
                case URI_PROVIDED_SET_RESULT_ACTION_ID -> substituteUri(resultIntent);
                case NO_URI_PROVIDED_SET_RESULT_ACTION_ID -> {
                    resultIntent.setAction(null);
                    resultIntent.putExtra(URI_LOCATION_ID,
                            NONE_PROVIDED_USE_HELPER_APP_URI_LOCATION_ID);
                    resultIntent.setData(null);
                }
                case PUT_MODE_FLAGS_TO_CHECK_SET_RESULT_ACTION_ID -> {
                    int modeFlags = resultIntent.getIntExtra(PUT_MODE_FLAGS, -1);
                    resultIntent.putExtra(MODE_FLAGS_TO_CHECK, modeFlags);
                }
                case GRANT_FLAGS_SET_RESULT_ACTION_ID -> {
                    int modeFlags = resultIntent.getIntExtra(GRANT_MODE_FLAGS, 0);
                    resultIntent.addFlags(modeFlags);
                }
                case NO_ACTION_NEEDED_SET_RESULT_ACTION_ID -> { }
                case PUT_EXTRA_UNKNOWN_REMOVE_EXTRA_STREAM_SET_RESULT_ACTION_ID -> {
                    Uri providedUri = getProvidedUri(resultIntent);
                    resultIntent.putExtra(EXTRA_UNKNOWN, providedUri);
                    resultIntent.removeExtra(Intent.EXTRA_STREAM);
                    resultIntent.putExtra(URI_LOCATION_ID, URI_IN_EXTRA_UNKNOWN_LOCATION_ID);
                }
                case PUT_NON_URI_EXTRA_STREAM_SET_RESULT_ACTION_ID -> {
                    String nonUriExtraStream = resultIntent.getStringExtra(
                            RESULT_NON_URI_EXTRA_STREAM);
                    resultIntent.putExtra(Intent.EXTRA_STREAM, nonUriExtraStream);
                    String referrerName = resultIntent.getStringExtra(RESULT_EXTRA_REFERRER_NAME);
                    resultIntent.putExtra(Intent.EXTRA_REFERRER_NAME, referrerName);
                }
                default -> throw new RuntimeException("Invalid result action ID: " + actionId);
            }
            return resultIntent;
        }

        private void substituteUri(Intent resultIntent) {
            Uri providedUri = getProvidedUri(resultIntent);
            if (resultIntent.getData() != null) {
                resultIntent.setData(providedUri);
            } else if (resultIntent.getClipData() != null) {
                resultIntent.setClipData(ClipData.newRawUri("", providedUri));
            } else if (resultIntent.hasExtra(Intent.EXTRA_STREAM)) {
                ArrayList<Uri> uris =
                        resultIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM,
                                Uri.class);
                if (uris != null) {
                    ArrayList<Uri> newUris = new ArrayList<>();
                    newUris.add(providedUri);
                    resultIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, newUris);
                } else {
                    resultIntent.putExtra(Intent.EXTRA_STREAM, providedUri);
                }
            } else {
                throw new RuntimeException("The Uri wasn't provided in any known location");
            }
        }

        private Uri getProvidedUri(Intent intent) {
            int uriType = intent.getIntExtra(RESULT_URI_TYPE, -1);
            return switch (uriType) {
                case PROVIDER_RESULT_URI_TYPE -> TestProvider.getContentUri();
                case NO_PERMISSION_URI_TYPE -> CONTENT_URI_NO_PERMISSION;
                case READ_PERMISSION_URI_TYPE -> CONTENT_URI_READ_PERMISSION;
                default -> throw new RuntimeException("Invalid URI type: " + uriType);
            };
        }
    }

    public static final class TestProvider extends ContentProvider {
        private static final AtomicInteger sId = new AtomicInteger(1);
        private static final String AUTHORITY = "android.app.cts.componentcaller.provider";
        private static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

        public static Uri getContentUri() {
            return ContentUris.withAppendedId(CONTENT_URI, sId.getAndIncrement());
        }
        @Override
        public boolean onCreate() {
            return false;
        }

        @Nullable
        @Override
        public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
                @Nullable String selection, @Nullable String[] selectionArgs,
                            @Nullable String sortOrder) {
            return null;
        }

        @Nullable
        @Override
        public String getType(@NonNull Uri uri) {
            return null;
        }

        @Nullable
        @Override
        public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
            return null;
        }

        @Override
        public int delete(@NonNull Uri uri, @Nullable String selection,
                @Nullable String[] selectionArgs) {
            return 0;
        }

        @Override
        public int update(@NonNull Uri uri, @Nullable ContentValues values,
                @Nullable String selection, @Nullable String[] selectionArgs) {
            return 0;
        }
    }

    public enum ModeFlags {
        READ(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        WRITE(Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
        READ_AND_WRITE(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        final int mValue;

        ModeFlags(int value) {
            this.mValue = value;
        }
    }

    private static final class WriteModeFlagsProvider implements TestParameterValuesProvider {
        @Override
        public List<ModeFlags> provideValues() {
            return ImmutableList.of(ModeFlags.WRITE, ModeFlags.READ_AND_WRITE);
        }
    }

    public enum NewIntentCallerActivity {
        GET_CURRENT_CALLER(HELPER_APP_NEW_INTENT_GET_CURRENT_CALLER_ACTIVITY),
        OVERLOAD_CALLER(HELPER_APP_NEW_INTENT_OVERLOAD_CALLER_ACTIVITY);

        @SuppressWarnings("ImmutableEnumChecker") // ComponentName fields are final
        final ComponentName mComponent;

        NewIntentCallerActivity(ComponentName component) {
            this.mComponent = component;
        }
    }

    public enum NewIntentGetSetCallerActivity {
        GET_CURRENT_CALLER(NewIntentGetSetIntentCurrentCallerTestActivity.class),
        OVERLOAD_CALLER(NewIntentGetSetIntentOverloadCallerTestActivity.class);

        final Class<? extends Activity> mCls;

        NewIntentGetSetCallerActivity(Class<? extends Activity> cls) {
            this.mCls = cls;
        }
    }

    public enum ResultCallerActivity {
        GET_CURRENT_CALLER(HELPER_APP_RESULT_GET_CURRENT_CALLER_ACTIVITY),
        OVERLOAD_CALLER(HELPER_APP_RESULT_OVERLOAD_CALLER_ACTIVITY);

        @SuppressWarnings("ImmutableEnumChecker") // ComponentName fields are final
        final ComponentName mComponent;

        ResultCallerActivity(ComponentName component) {
            this.mComponent = component;
        }
    }

    public enum ResultGetSetCallerActivity {
        GET_CURRENT_CALLER(ResultGetSetIntentCurrentCallerTestActivity.class),
        OVERLOAD_CALLER(ResultGetSetIntentOverloadCallerTestActivity.class);

        final Class<? extends Activity> mCls;

        ResultGetSetCallerActivity(Class<? extends Activity> cls) {
            this.mCls = cls;
        }
    }
}
