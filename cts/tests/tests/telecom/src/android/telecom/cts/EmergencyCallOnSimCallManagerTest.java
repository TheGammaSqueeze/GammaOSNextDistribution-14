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

package android.telecom.cts;

import static android.telecom.cts.TestUtils.ACCOUNT_ID_1;
import static android.telecom.cts.TestUtils.ACCOUNT_ID_EMERGENCY;
import static android.telecom.cts.TestUtils.ACCOUNT_LABEL;
import static android.telecom.cts.TestUtils.PACKAGE;

import android.Manifest;
import android.content.ComponentName;
import android.graphics.Color;
import android.location.Location;
import android.location.provider.ProviderProperties;
import android.net.Uri;
import android.os.OutcomeReceiver;
import android.telecom.Connection;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.QueryLocationException;
import android.util.Log;

import com.android.compatibility.common.util.LocationUtils;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class EmergencyCallOnSimCallManagerTest extends BaseTelecomTestWithMockServices {
    private static final String TAG = "EmergencyCallOnSimCallManagerTest";
    public static final String SIM_CALL_MANAGER_COMPONENT =
            CtsConnectionService.class.getCanonicalName();
    public static final PhoneAccountHandle TEST_SIM_CALL_MANAGER_PHONE_ACCOUNT_HANDLE =
            new PhoneAccountHandle(new ComponentName(PACKAGE, SIM_CALL_MANAGER_COMPONENT),
                    ACCOUNT_ID_1);

    public static final PhoneAccountHandle TEST_SIM_EMERGENCY_PHONE_ACCOUNT_HANDLE =
            new PhoneAccountHandle(new ComponentName(PACKAGE, SIM_CALL_MANAGER_COMPONENT),
                    ACCOUNT_ID_EMERGENCY);
    public static final PhoneAccount TEST_SIM_CALL_MANAGER_ACCOUNT = PhoneAccount.builder(
                    TEST_SIM_CALL_MANAGER_PHONE_ACCOUNT_HANDLE, ACCOUNT_LABEL)
            .setAddress(Uri.parse("tel:555-TEST"))
            .setSubscriptionAddress(Uri.parse("tel:555-TEST"))
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER
                    | PhoneAccount.CAPABILITY_CONNECTION_MANAGER)
            .setHighlightColor(Color.RED)
            .setShortDescription(ACCOUNT_LABEL)
            .setSupportedUriSchemes(Arrays.asList("tel"))
            .build();

    private static final String TEST_PROVIDER = "test_provider";

    @Override
    public void setUp() throws Exception {
        boolean isSetUpComplete = false;
        super.setUp();
        NewOutgoingCallBroadcastReceiver.reset();
        if (!mShouldTestTelecom  || !TestUtils.hasTelephonyFeature(mContext)) return;

        try {
            setupConnectionService(null, 0);
            mTelecomManager.registerPhoneAccount(TEST_SIM_CALL_MANAGER_ACCOUNT);
            TestUtils.enablePhoneAccount(getInstrumentation(),
                    TEST_SIM_CALL_MANAGER_PHONE_ACCOUNT_HANDLE);
            assertPhoneAccountEnabled(TEST_SIM_CALL_MANAGER_PHONE_ACCOUNT_HANDLE);
            isSetUpComplete = true;
        } finally {
            // Force tearDown if setUp errors out to ensure unused listeners are cleaned up.
            if (!isSetUpComplete) {
                tearDown();
            }
        }
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (mShouldTestTelecom && TestUtils.hasTelephonyFeature(mContext)) {
                mTelecomManager.unregisterPhoneAccount(TEST_SIM_CALL_MANAGER_PHONE_ACCOUNT_HANDLE);
            }
        } finally {
            super.tearDown();
        }
    }

    public void testQueryLocationException() {
        if (!mShouldTestTelecom  || !TestUtils.hasTelephonyFeature(mContext)) return;

        String message = "QueryLocationException";
        Throwable cause = new Throwable();

        try {
            throw new QueryLocationException(message);
        } catch (QueryLocationException e) {
            assertEquals(QueryLocationException.ERROR_UNSPECIFIED, e.getCode());
        }

        try {
            throw new QueryLocationException(message,
                    QueryLocationException.ERROR_REQUEST_TIME_OUT);
        } catch (QueryLocationException e) {
            assertEquals(QueryLocationException.ERROR_REQUEST_TIME_OUT, e.getCode());
        }

        try {
            throw new QueryLocationException(message,
                    QueryLocationException.ERROR_PREVIOUS_REQUEST_EXISTS, cause);
        } catch (QueryLocationException e) {
            assertEquals(QueryLocationException.ERROR_PREVIOUS_REQUEST_EXISTS, e.getCode());
        }
    }

    /**
     * Caller gets onError when queryLocationForEmergency is called for normal calls.
     * {@link android.telecom.Connection#queryLocationForEmergency(long, String, Executor,
     * OutcomeReceiver)}
     */
    public void testQueryLocationForEmergencyTryNormalCall() throws Exception {
        if (!mShouldTestTelecom  || !TestUtils.hasTelephonyFeature(mContext)) return;

        try {
            placeAndVerifyCall();
            final MockConnection connection = verifyConnectionForOutgoingCall();
            assertNotNull("Connection should NOT be null.", connection);

            CountDownLatch latch = new CountDownLatch(1);

            // Add Permission
            getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                    Manifest.permission.UPDATE_APP_OPS_STATS);

            // Test queryLocationForEmergency API
            connection.queryLocationForEmergency(3000L, TEST_PROVIDER,
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<Location, QueryLocationException>() {
                        @Override
                        public void onResult(Location result) {
                            // Do nothing
                        }
                        @Override
                        public void onError(QueryLocationException e) {
                            Log.i(TAG, "queryLocationForEmergency: onError: " + e.getMessage());
                            latch.countDown();
                        }
                    });

            // Verify Test result
            assertTrue(latch.await(5000L, TimeUnit.MILLISECONDS));
        } finally {
            // Drop Permission
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
    }

    /**
     * Test the ability to query location and get a response.
     * {@link android.telecom.Connection#queryLocationForEmergency(long, String, Executor,
     * OutcomeReceiver)}
     */
    public void testQueryLocationForEmergencyReturnLocation() throws Exception {
        if (!mShouldTestTelecom  || !TestUtils.hasTelephonyFeature(mContext)) return;

        try {
            // Add Test Provider
            LocationUtils.registerMockLocationProvider(getInstrumentation(), true);

            mLocationManager.addTestProvider(TEST_PROVIDER,
                    new ProviderProperties.Builder().build());
            mLocationManager.setTestProviderEnabled(TEST_PROVIDER, true);

            Location loc = LocationUtils.createLocation(TEST_PROVIDER,
                    new Random(System.currentTimeMillis()));

            mLocationManager.setTestProviderLocation(TEST_PROVIDER, loc);

            // Setup emergency calling
            setupForEmergencyCalling(TEST_EMERGENCY_NUMBER);
            Connection connection = placeAndVerifyEmergencyCall(true /*supportsHold*/);
            assertNotNull("Connection should NOT be null.", connection);

            CountDownLatch latch = new CountDownLatch(1);

            // Add Permission
            getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                    Manifest.permission.UPDATE_APP_OPS_STATS);

            // Test queryLocationForEmergency API
            connection.queryLocationForEmergency(3000L, TEST_PROVIDER,
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<Location, QueryLocationException>() {
                        @Override
                        public void onResult(Location result) {
                            Log.i(TAG, "queryLocationForEmergency: result: " + result);
                            latch.countDown();
                        }
                        @Override
                        public void onError(QueryLocationException e) {
                            Log.i(TAG, "queryLocationForEmergency: onError: " + e.getMessage());
                        }
                    });

            // Verify Test result
            assertTrue(latch.await(5000L, TimeUnit.MILLISECONDS));
        } finally {
            // Teardown emergency calling
            tearDownEmergencyCalling();

            // Drop Permission
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();

            // Remove Test Provider
            mLocationManager.removeTestProvider(TEST_PROVIDER);
            LocationUtils.registerMockLocationProvider(getInstrumentation(), false);
        }
    }

    /**
     * Test the ability to query location and get a response.
     * {@link android.telecom.Connection#queryLocationForEmergency(long, String, Executor,
     * OutcomeReceiver)}
     */
    public void testQueryLocationForEmergencyReturnTimeoutException() throws Exception {
        if (!mShouldTestTelecom  || !TestUtils.hasTelephonyFeature(mContext)) return;

        try {
            // Add Test Provider
            LocationUtils.registerMockLocationProvider(getInstrumentation(), true);

            mLocationManager.addTestProvider(TEST_PROVIDER,
                    new ProviderProperties.Builder().build());
            mLocationManager.setTestProviderEnabled(TEST_PROVIDER, true);


            // Note that unlike {@link #testQueryLocationForEmergencyReturnLocation},
            // In this test, we do not set the location

            // Setup emergency calling
            setupForEmergencyCalling(TEST_EMERGENCY_NUMBER);
            Connection connection = placeAndVerifyEmergencyCall(true /*supportsHold*/);
            assertNotNull("Connection should NOT be null.", connection);

            CountDownLatch latch = new CountDownLatch(1);

            // Add Permission
            getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                    Manifest.permission.UPDATE_APP_OPS_STATS);

            // Test queryLocationForEmergency API
            connection.queryLocationForEmergency(500L, TEST_PROVIDER,
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<Location, QueryLocationException>() {
                        @Override
                        public void onResult(Location result) {
                            // Do nothing
                        }
                        @Override
                        public void onError(QueryLocationException e) {
                            Log.i(TAG, "queryLocationForEmergency: onError: " + e.getMessage());
                            latch.countDown();
                        }
                    });

            // Verify Test result
            assertTrue(latch.await(5000L, TimeUnit.MILLISECONDS));
        } finally {
            // Teardown emergency calling
            tearDownEmergencyCalling();

            // Drop Permission
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();

            // Remove Test Provider
            mLocationManager.removeTestProvider(TEST_PROVIDER);
            LocationUtils.registerMockLocationProvider(getInstrumentation(), false);
        }
    }

    public void setupForEmergencyCalling(String testNumber) throws Exception {
        TestUtils.setSystemDialerOverride(getInstrumentation());
        TestUtils.addTestEmergencyNumber(getInstrumentation(), testNumber);
        TestUtils.setTestEmergencyPhoneAccountPackageFilter(getInstrumentation(), mContext);
        // Emergency calls require special capabilities.
        TestUtils.registerEmergencyPhoneAccount(getInstrumentation(),
                TEST_SIM_EMERGENCY_PHONE_ACCOUNT_HANDLE,
                TestUtils.ACCOUNT_LABEL + "E", "tel:555-EMER");
        mIsEmergencyCallingSetup = true;
    }

    public void tearDownEmergencyCalling() throws Exception {
        if (!mIsEmergencyCallingSetup) return;

        TestUtils.clearSystemDialerOverride(getInstrumentation());
        TestUtils.clearTestEmergencyNumbers(getInstrumentation());
        TestUtils.clearTestEmergencyPhoneAccountPackageFilter(getInstrumentation());
        mTelecomManager.unregisterPhoneAccount(TEST_SIM_EMERGENCY_PHONE_ACCOUNT_HANDLE);
    }
}
