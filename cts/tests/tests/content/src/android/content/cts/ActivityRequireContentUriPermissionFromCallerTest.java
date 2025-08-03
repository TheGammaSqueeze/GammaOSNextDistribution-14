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

package android.content.cts;

import static android.content.cts.contenturitestapp.IContentUriTestService.URI_NO_PERMISSION_ID;
import static android.content.cts.contenturitestapp.IContentUriTestService.URI_READ_PERMISSION_ID;
import static android.content.cts.contenturitestapp.IContentUriTestService.URI_WRITE_PERMISSION_ID;
import static android.content.cts.contenturitestapp.IContentUriTestService.URI_READ_WRITE_PERMISSION_ID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.cts.contenturitestapp.IContentUriTestService;
import android.net.Uri;
import android.os.IBinder;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.PollingCheck;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests to verify Activity Manifest attribute
 * {@link android.R.attr#requireContentUriPermissionFromCaller} works as intended. Depending on the
 * attribute value and the passed content URIs, activity starts should be allowed or blocked by a
 * {@link SecurityException}.
 *
 * <p>Before all the test methods are run, {@link #classSetUp()} retrieves a list of content URIs
 * that it has read, write, read and write, and no access to. It does so by binding to TestService
 * in {@link android.content.cts.contenturitestapp}, which returns such list, and then unbinds from
 * it.
 *
 * <p>Each test in this class does the following:
 * <ol>
 *     <li>Depending on the tested scenario, the test creates an intent with a content URI in
 *         {@link Intent#getData()} and {@link Intent#getClipData()}.
 *     <li>Then the test tries to start an activity in
 *         {@link android.content.cts.contenturitestapp} that has a specific attribute value.
 *     <li>If the activity start is supposed to be blocked, the test catches a
 *         {@link SecurityException} and asserts that it was caught.
 *     <li>If the activity start is supposed to be allowed, the test asserts that the activity was
 *         invoked via a broadcast sent from the activity.
 * </ol>
 */
@AppModeFull
@RequiresFlagsEnabled(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
@RunWith(TestParameterInjector.class)
public class ActivityRequireContentUriPermissionFromCallerTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static Context sContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    // Test activities that are invoked
    private static final String TEST_PACKAGE = "android.content.cts.contenturitestapp";
    private static final ComponentName TEST_NONE_ACTIVITY = new ComponentName(TEST_PACKAGE,
            TEST_PACKAGE + ".TestActivities$NoneContentUriActivity");
    private static final ComponentName TEST_READ_ACTIVITY = new ComponentName(TEST_PACKAGE,
            TEST_PACKAGE + ".TestActivities$ReadContentUriActivity");
    private static final ComponentName TEST_WRITE_ACTIVITY = new ComponentName(TEST_PACKAGE,
            TEST_PACKAGE + ".TestActivities$WriteContentUriActivity");
    private static final ComponentName TEST_READ_OR_WRITE_ACTIVITY = new ComponentName(TEST_PACKAGE,
            TEST_PACKAGE + ".TestActivities$ReadOrWriteContentUriActivity");
    private static final ComponentName TEST_READ_AND_WRITE_ACTIVITY = new ComponentName(
            TEST_PACKAGE, TEST_PACKAGE + ".TestActivities$ReadAndWriteContentUriActivity");

    private static final String TEST_RECEIVER_ACTION =
            "android.content.cts.REQUIRE_CONTENT_URI_TEST_RECEIVER_ACTION";

    private static CountDownLatch sLatch;
    /** Used to verify passing a non-Uri object into EXTRA_STREAM does not affect its retrieval. */
    private static String sExtraStreamString;
    /**
     * Used to verify passing a non-Uri object into EXTRA_STREAM does not affect retrieval of other
     * extras.
     */
    private static String sReferrerName;

    private TestReceiver mReceiver;

    // TestService details
    private static final int SERVICE_TIMEOUT = 15000;
    private static final String PKG_TEST_SERVICE = "android.content.cts.contenturitestapp";
    private static final String CLS_TEST_SERVICE = PKG_TEST_SERVICE + ".TestService";
    private static final ComponentName COMPONENT_CONTENT_URI_TEST_SERVICE =
            new ComponentName(PKG_TEST_SERVICE, CLS_TEST_SERVICE);

    private static IContentUriTestService sContentUriTestService;
    private static ServiceConnection sContentUriServiceConnection;

    private static Uri[] sUrisFromService;

    /**
     * Retrieves and saves Uris from
     * {@code android.content.cts.contenturitestapp.TestService#getContentUrisForManifest} into
     * {@link #sUrisFromService}. The Uris in the
     * array represent Uris this test has read, write, read and write, and no access to.
     */
    @BeforeClass
    public static void classSetUp() throws Exception {
        setUpContentUriTestServiceConnection();
        sUrisFromService = sContentUriTestService.getContentUrisForManifest();
        sContext.unbindService(sContentUriServiceConnection);
    }

    @Before
    public void testSetUp() throws Exception {
        resetTestResults();
        mReceiver = new TestReceiver();
        IntentFilter filter = new IntentFilter(TEST_RECEIVER_ACTION);
        sContext.registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    @After
    public void tearDown() {
        sContext.unregisterReceiver(mReceiver);
    }

    @Test
    @ApiTest(apis = {"android.R.attr#requireContentUriPermissionFromCaller"})
    @CddTest(requirements = {"4/C-0-2"})
    public void testNoneAttribute_allModeFlags_doNotThrow(
            @TestParameter({"NONE", "READ", "WRITE", "READ_AND_WRITE"}) ModeFlags modeFlags,
            @TestParameter UriLocation uriLocation) throws Exception {
        internalTestAssertDoesNotThrow(TestedAttributeActivity.NONE, modeFlags, uriLocation);
    }

    @Test
    @ApiTest(apis = {"android.R.attr#requireContentUriPermissionFromCaller"})
    @CddTest(requirements = {"4/C-0-2"})
    public void testReadAttribute_readModeFlags_doNotThrow(
            @TestParameter({"READ", "READ_AND_WRITE"}) ModeFlags modeFlags,
            @TestParameter UriLocation uriLocation) throws Exception {
        internalTestAssertDoesNotThrow(TestedAttributeActivity.READ, modeFlags, uriLocation);
    }

    @Test
    @ApiTest(apis = {"android.R.attr#requireContentUriPermissionFromCaller"})
    @CddTest(requirements = {"4/C-0-2"})
    public void testReadAttribute_noneAndWriteModeFlags_throw(
            @TestParameter({"NONE", "WRITE"}) ModeFlags modeFlags,
            @TestParameter UriLocation uriLocation) throws Exception {
        internalTestAssertThrows(TestedAttributeActivity.READ, modeFlags, uriLocation);
    }

    @Test
    @ApiTest(apis = {"android.R.attr#requireContentUriPermissionFromCaller"})
    @CddTest(requirements = {"4/C-0-2"})
    public void testWriteAttribute_writeModeFlags_doNotThrow(
            @TestParameter({"WRITE", "READ_AND_WRITE"}) ModeFlags modeFlags,
            @TestParameter UriLocation uriLocation) throws Exception {
        internalTestAssertDoesNotThrow(TestedAttributeActivity.WRITE, modeFlags, uriLocation);
    }

    @Test
    @ApiTest(apis = {"android.R.attr#requireContentUriPermissionFromCaller"})
    @CddTest(requirements = {"4/C-0-2"})
    public void testWriteAttribute_noneAndReadModeFlags_throw(
            @TestParameter({"NONE", "READ"}) ModeFlags modeFlags,
            @TestParameter UriLocation uriLocation) throws Exception {
        internalTestAssertThrows(TestedAttributeActivity.WRITE, modeFlags, uriLocation);
    }

    @Test
    @ApiTest(apis = {"android.R.attr#requireContentUriPermissionFromCaller"})
    @CddTest(requirements = {"4/C-0-2"})
    public void testReadOrWriteAttribute_allButNoneModeFlags_doNotThrow(
            @TestParameter({"READ", "WRITE", "READ_AND_WRITE"}) ModeFlags modeFlags,
            @TestParameter UriLocation uriLocation) throws Exception {
        internalTestAssertDoesNotThrow(TestedAttributeActivity.READ_OR_WRITE, modeFlags,
                uriLocation);
    }

    @Test
    @ApiTest(apis = {"android.R.attr#requireContentUriPermissionFromCaller"})
    @CddTest(requirements = {"4/C-0-2"})
    public void testReadOrWriteAttribute_noneModeFlags_throws(
            @TestParameter UriLocation uriLocation) throws Exception {
        internalTestAssertThrows(TestedAttributeActivity.READ_OR_WRITE, ModeFlags.NONE,
                uriLocation);
    }

    @Test
    @ApiTest(apis = {"android.R.attr#requireContentUriPermissionFromCaller"})
    @CddTest(requirements = {"4/C-0-2"})
    public void testReadAndWriteAttribute_readAndWriteModeFlags_doesNotThrow(
            @TestParameter UriLocation uriLocation) throws Exception {
        internalTestAssertDoesNotThrow(TestedAttributeActivity.READ_AND_WRITE,
                ModeFlags.READ_AND_WRITE, uriLocation);
    }

    @Test
    @ApiTest(apis = {"android.R.attr#requireContentUriPermissionFromCaller"})
    @CddTest(requirements = {"4/C-0-2"})
    public void testReadAndWriteAttribute_allButReadAndWriteModeFlags_throw(
            @TestParameter({"NONE", "READ", "WRITE"}) ModeFlags modeFlags,
            @TestParameter UriLocation uriLocation) throws Exception {
        internalTestAssertThrows(TestedAttributeActivity.READ_AND_WRITE, modeFlags, uriLocation);
    }

    @Test
    @ApiTest(apis = {"android.R.attr#requireContentUriPermissionFromCaller"})
    @CddTest(requirements = {"4/C-0-2"})
    public void testAllAttributes_passingAnyUriInUnknownExtra_doesNotThrow(
            @TestParameter TestedAttributeActivity testedAttributeActivity,
            @TestParameter ModeFlags modeFlags) throws Exception {
        Intent intent = getTestIntent(testedAttributeActivity.mComponent);
        Uri uri = sUrisFromService[modeFlags.mIdFromService];
        intent.putExtra("UNKNOWN_EXTRA", uri);

        boolean securityExceptionCaught = false;
        try {
            sContext.startActivity(intent);
        } catch (SecurityException e) {
            securityExceptionCaught = true;
        }

        assertFalse("Should not throw a SecurityException because the URI was passed into an"
                + " unknown extra", securityExceptionCaught);
        assertActivityWasInvoked();
    }

    @Test
    @ApiTest(apis = {"android.R.attr#requireContentUriPermissionFromCaller"})
    @CddTest(requirements = {"4/C-0-2"})
    public void testAllAttributes_passingNonUriExtraStreamDoesNotAffectRetrievalOfExtras(
            @TestParameter TestedAttributeActivity testedAttributeActivity) throws Exception {
        Intent intent = getTestIntent(testedAttributeActivity.mComponent);
        String nonUriExtraStream = "non-uri";
        String referrerName = "ComponentCaller";
        intent.putExtra(Intent.EXTRA_STREAM, nonUriExtraStream);
        intent.putExtra(Intent.EXTRA_REFERRER_NAME, referrerName);

        sContext.startActivity(intent);

        assertActivityWasInvoked();
        assertEquals("Passing a non-URI item as EXTRA_STREAM should not affect the retrieval of"
                + " EXTRA_STREAM", nonUriExtraStream, sExtraStreamString);
        assertEquals("Passing a non-URI item as EXTRA_STREAM should not affect the retrieval of"
                        + " other extras, such as EXTRA_REFERRER_NAME", referrerName,
                sReferrerName);
    }

    private void internalTestAssertDoesNotThrow(TestedAttributeActivity attributeActivity,
            ModeFlags modeFlags, UriLocation uriLocation) throws Exception {
        internalTest(attributeActivity, modeFlags, uriLocation, /* assertThrows */ false);
    }

    private void internalTestAssertThrows(TestedAttributeActivity attributeActivity,
            ModeFlags modeFlags, UriLocation uriLocation) throws Exception {
        internalTest(attributeActivity, modeFlags, uriLocation, /* assertThrows */ true);
    }

    private void internalTest(TestedAttributeActivity attributeActivity, ModeFlags modeFlags,
            UriLocation uriLocation, boolean assertThrows) throws Exception {
        Intent intent = getTestIntent(attributeActivity.mComponent, modeFlags, uriLocation);

        boolean securityExceptionCaught = false;
        try {
            sContext.startActivity(intent);
        } catch (SecurityException e) {
            securityExceptionCaught = true;
        }

        String errorMsg = getErrorMessage(modeFlags, assertThrows, attributeActivity.mIsOr);
        assertEquals(errorMsg, securityExceptionCaught, assertThrows);

        if (!assertThrows) {
            assertActivityWasInvoked();
        }
    }

    private String getErrorMessage(ModeFlags modeFlags, boolean assertThrows, boolean isOr) {
        String requiredModeFlags = switch (modeFlags) {
            case NONE -> assertThrows ? "any" : "no";
            case READ -> "read";
            case WRITE -> "write";
            case READ_AND_WRITE -> isOr ? "read or write" : "read and write";
        };
        if (assertThrows) {
            return "Should throw a SecurityException because we don't have " + requiredModeFlags
                    + " access to the content URI";
        } else {
            return "Should not throw a SecurityException because we have " + requiredModeFlags
                    + " access to the content URI";
        }
    }

    private Intent getTestIntent(ComponentName component) {
        Intent intent = new Intent();
        intent.setComponent(component);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        return intent;
    }

    private Intent getTestIntent(ComponentName component, ModeFlags modeFlags,
            UriLocation uriLocation) {
        Intent intent = getTestIntent(component);
        Uri uri = sUrisFromService[modeFlags.mIdFromService];
        switch (uriLocation) {
            case URI_IN_GET_DATA -> intent.setData(uri);
            case URI_IN_GET_CLIP_DATA -> intent.setClipData(ClipData.newRawUri("", uri));
            case URI_IN_EXTRA_STREAM -> intent.putExtra(Intent.EXTRA_STREAM, uri);
            case URI_IN_ARRAY_LIST_EXTRA_STREAM -> {
                ArrayList<Uri> uris = new ArrayList<>();
                uris.add(uri);
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            }
        }
        return intent;
    }

    private void assertActivityWasInvoked() throws Exception {
        assertTrue("Activity was not invoked by the timeout",
                sLatch.await(10, TimeUnit.SECONDS));
    }

    private void resetTestResults() {
        sLatch = new CountDownLatch(1);
        sExtraStreamString = null;
        sReferrerName = null;
    }

    private static void setUpContentUriTestServiceConnection() {
        sContentUriServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                sContentUriTestService = IContentUriTestService.Stub.asInterface(service);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                sContentUriTestService = null;
            }
        };

        Intent intent = new Intent();
        intent.setComponent(COMPONENT_CONTENT_URI_TEST_SERVICE);
        assertTrue(sContext.bindService(intent, sContentUriServiceConnection,
                Service.BIND_AUTO_CREATE));

        new PollingCheck(SERVICE_TIMEOUT) {
            protected boolean check() {
                return sContentUriTestService != null;
            }
        }.run();
    }

    public static final class TestReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            sExtraStreamString = intent.getStringExtra(Intent.EXTRA_STREAM);
            sReferrerName = intent.getStringExtra(Intent.EXTRA_REFERRER_NAME);
            sLatch.countDown();
        }
    }

    public enum ModeFlags {
        NONE(0, URI_NO_PERMISSION_ID),
        READ(Intent.FLAG_GRANT_READ_URI_PERMISSION, URI_READ_PERMISSION_ID),
        WRITE(Intent.FLAG_GRANT_WRITE_URI_PERMISSION, URI_WRITE_PERMISSION_ID),
        READ_AND_WRITE(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION, URI_READ_WRITE_PERMISSION_ID);

        final int mValue;
        final int mIdFromService;

        ModeFlags(int value, int idFromService) {
            this.mValue = value;
            this.mIdFromService = idFromService;
        }
    }

    public enum UriLocation {
        URI_IN_GET_DATA, URI_IN_GET_CLIP_DATA, URI_IN_EXTRA_STREAM, URI_IN_ARRAY_LIST_EXTRA_STREAM
    }

    public enum TestedAttributeActivity {
        NONE(TEST_NONE_ACTIVITY),
        READ(TEST_READ_ACTIVITY),
        WRITE(TEST_WRITE_ACTIVITY),
        READ_OR_WRITE(TEST_READ_OR_WRITE_ACTIVITY, /* isOr */ true),
        READ_AND_WRITE(TEST_READ_AND_WRITE_ACTIVITY);

        final ComponentName mComponent;
        final boolean mIsOr;
        TestedAttributeActivity(ComponentName component) {
            this(component, /* isOr */ false);
        }

        TestedAttributeActivity(ComponentName component, boolean isOr) {
            this.mComponent = component;
            this.mIsOr = isOr;
        }
    }
}
