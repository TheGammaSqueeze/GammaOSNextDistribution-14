/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.telephony.euicc.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.TelephonyManager;
import android.telephony.UiccCardInfo;
import android.telephony.UiccPortInfo;
import android.telephony.cts.util.TelephonyUtils;
import android.telephony.euicc.DownloadableSubscription;
import android.telephony.euicc.EuiccCardManager;
import android.telephony.euicc.EuiccInfo;
import android.telephony.euicc.EuiccManager;
import android.text.TextUtils;
import android.util.ArraySet;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.internal.telephony.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class EuiccManagerTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final int REQUEST_CODE = 0;
    private static final int CALLBACK_TIMEOUT_MILLIS = 2000;
    // starting activities might take extra time
    private static final int ACTIVITY_CALLBACK_TIMEOUT_MILLIS = 5000;
    private static final String ACTION_DOWNLOAD_SUBSCRIPTION = "cts_download_subscription";
    private static final String ACTION_DELETE_SUBSCRIPTION = "cts_delete_subscription";
    private static final String ACTION_SWITCH_TO_SUBSCRIPTION = "cts_switch_to_subscription";
    private static final String ACTION_ERASE_SUBSCRIPTIONS = "cts_erase_subscriptions";
    private static final String ACTION_START_TEST_RESOLUTION_ACTIVITY =
            "cts_start_test_resolution_activity";
    private static final String ACTIVATION_CODE = "1$LOCALHOST$04386-AGYFT-A74Y8-3F815";

    // Test EuiccManager test callback actions
    public static final String ACTION_PROVISION_EMBEDDED_SUBSCRIPTION =
            "cts_provision_embedded_subscription";
    public static final String ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS =
            "cts_manage_embedded_subscription";
    public static final String ACTION_TRANSFER_EMBEDDED_SUBSCRIPTIONS =
            "cts_transfer_embedded_subscription";
    public static final String ACTION_CONVERT_TO_EMBEDDED_SUBSCRIPTIONS =
            "cts_convert_to_embedded_subscription";
    // Command to set Euicc Ui-Component
    private static final String COMMAND_UPDATE_EUICC_UI_PACKAGE =
            "cmd phone euicc set-euicc-uicomponent ";
    private static final String TEST_EUICC_UI_COMPONENT =
            "android.telephony.euicc.cts.EuiccTestServiceActionResolutionActivity ";

    private static final String[] sCallbackActions =
            new String[]{
                    ACTION_DOWNLOAD_SUBSCRIPTION,
                    ACTION_DELETE_SUBSCRIPTION,
                    ACTION_SWITCH_TO_SUBSCRIPTION,
                    ACTION_ERASE_SUBSCRIPTIONS,
                    ACTION_START_TEST_RESOLUTION_ACTIVITY,
                    ACTION_PROVISION_EMBEDDED_SUBSCRIPTION,
                    ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS,
                    ACTION_TRANSFER_EMBEDDED_SUBSCRIPTIONS,
                    ACTION_CONVERT_TO_EMBEDDED_SUBSCRIPTIONS,
            };
    private static final String SWITCH_WITHOUT_PORT_INDEX_EXCEPTION_ON_DISABLE_STRING =
            "SWITCH_WITHOUT_PORT_INDEX_EXCEPTION_ON_DISABLE";

    private EuiccManager mEuiccManager;
    private CallbackReceiver mCallbackReceiver;

    @Before
    public void setUp() throws Exception {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(EuiccUtil.hasEuiccFeature());
        }

        mEuiccManager = (EuiccManager) getContext().getSystemService(Context.EUICC_SERVICE);
    }

    @After
    public void tearDown() throws Exception {
        if (mCallbackReceiver != null) {
            getContext().unregisterReceiver(mCallbackReceiver);

            if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
                mCallbackReceiver = null;
            }
        }
    }

    @Test
    public void testGetEid() {
        // test disabled state only for now
        if (mEuiccManager.isEnabled()) {
            return;
        }

        // call getEid()
        String eid = mEuiccManager.getEid();

        // verify result is null
        assertNull(eid);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ESIM_AVAILABLE_MEMORY)
    public void testGetAvailableMemoryInBytes_euiccManagerDisabled() {
        if (mEuiccManager.isEnabled()) {
            return;
        }

        long availableMemoryInBytes = mEuiccManager.getAvailableMemoryInBytes();
        assertEquals(EuiccManager.EUICC_MEMORY_FIELD_UNAVAILABLE, availableMemoryInBytes);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ESIM_AVAILABLE_MEMORY)
    public void testGetAvailableMemoryInBytes_euiccManagerEnabled() {
        if (!mEuiccManager.isEnabled()) {
            return;
        }

        long availableMemoryInBytes;
        try {
            availableMemoryInBytes = mEuiccManager.getAvailableMemoryInBytes();
        } catch (UnsupportedOperationException e) {
            String message = e.getMessage();
            assertTrue(message != null && !message.isEmpty());
            return;
        } catch (Exception e) {
            fail("Exception occurred when retrieving the available memory: " + e.toString());
            return;
        }

        // EuiccManager.EUICC_MEMORY_FIELD_UNAVAILABLE: when it fails to retrieve available
        // memory because eUICC is not ready
        // >= 0: the eUICC memory size can vary from [0, x]
        assertTrue(availableMemoryInBytes == EuiccManager.EUICC_MEMORY_FIELD_UNAVAILABLE
                    || availableMemoryInBytes >= 0);
    }


    @Test
    public void testCreateForCardId() {
        // just verify that this does not crash
        mEuiccManager = mEuiccManager.createForCardId(TelephonyManager.UNINITIALIZED_CARD_ID);
        mEuiccManager = mEuiccManager.createForCardId(TelephonyManager.UNSUPPORTED_CARD_ID);
    }

    @Test
    public void testDownloadSubscription() {
        // test disabled state only for now
        if (mEuiccManager.isEnabled()) {
            return;
        }

        // set up CountDownLatch and receiver
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mCallbackReceiver = new CallbackReceiver(countDownLatch);
        getContext()
                .registerReceiver(
                        mCallbackReceiver, new IntentFilter(ACTION_DOWNLOAD_SUBSCRIPTION),
                        Context.RECEIVER_EXPORTED_UNAUDITED);

        // call downloadSubscription()
        DownloadableSubscription subscription = createDownloadableSubscription();
        PendingIntent callbackIntent = createCallbackIntent(ACTION_DOWNLOAD_SUBSCRIPTION);
        mEuiccManager.downloadSubscription(
                subscription, false /* switchAfterDownload */, callbackIntent);

        // wait for callback
        try {
            countDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        // verify correct result code is received
        assertEquals(
                EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR, mCallbackReceiver.getResultCode());
    }

    @Test
    public void testGetEuiccInfo() {
        // test disabled state only for now
        if (mEuiccManager.isEnabled()) {
            return;
        }

        // call getEuiccInfo()
        EuiccInfo euiccInfo = mEuiccManager.getEuiccInfo();

        // verify result is null
        assertNull(euiccInfo);
    }

    @Test
    public void testDeleteSubscription() {
        // test disabled state only for now
        if (mEuiccManager.isEnabled()) {
            return;
        }

        // set up CountDownLatch and receiver
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mCallbackReceiver = new CallbackReceiver(countDownLatch);
        getContext()
                .registerReceiver(mCallbackReceiver, new IntentFilter(ACTION_DELETE_SUBSCRIPTION),
                        Context.RECEIVER_EXPORTED_UNAUDITED);

        // call deleteSubscription()
        PendingIntent callbackIntent = createCallbackIntent(ACTION_DELETE_SUBSCRIPTION);
        mEuiccManager.deleteSubscription(3, callbackIntent);

        // wait for callback
        try {
            countDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        // verify correct result code is received
        assertEquals(
                EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR, mCallbackReceiver.getResultCode());
    }

    @Ignore("the compatibility framework does not currently support changing compatibility flags"
            + " on user builds for device side CTS tests. Ignore this test until support is added")
    @Test
    public void testSwitchToSubscritionDisableWithNoPortAndChangesCompatDisabled()
            throws Exception {
        // Only test it when EuiccManager is enabled.
        if (!mEuiccManager.isEnabled()) {
            return;
        }
        // disable compact change
        TelephonyUtils.disableCompatCommand(InstrumentationRegistry.getInstrumentation(),
                TelephonyUtils.CTS_APP_PACKAGE,
                SWITCH_WITHOUT_PORT_INDEX_EXCEPTION_ON_DISABLE_STRING);

        // set up CountDownLatch and receiver
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mCallbackReceiver = new CallbackReceiver(countDownLatch);
        getContext()
                .registerReceiver(
                        mCallbackReceiver, new IntentFilter(ACTION_SWITCH_TO_SUBSCRIPTION),
                        Context.RECEIVER_EXPORTED_UNAUDITED);

        // call switchToSubscription()
        PendingIntent callbackIntent = createCallbackIntent(ACTION_SWITCH_TO_SUBSCRIPTION);
        mEuiccManager.switchToSubscription(-1, callbackIntent);

        // wait for callback
        try {
            countDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        // verify correct result code is received
        assertEquals(
                EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR, mCallbackReceiver.getResultCode());

        // reset compat change
        TelephonyUtils.resetCompatCommand(InstrumentationRegistry.getInstrumentation(),
                TelephonyUtils.CTS_APP_PACKAGE,
                SWITCH_WITHOUT_PORT_INDEX_EXCEPTION_ON_DISABLE_STRING);
    }

    @Test
    public void testSwitchToSubscriptionDisableWithNoPort() throws Exception {
        // Only test it when EuiccManager is enabled.
        if (!mEuiccManager.isEnabled()) {
            return;
        }

        PendingIntent callbackIntent = createCallbackIntent(ACTION_SWITCH_TO_SUBSCRIPTION);

        try {
            mEuiccManager.switchToSubscription(-1, callbackIntent);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            // expected for android T and beyond
        }
    }

    @Test
    public void testSwitchToSubscription() {
        // test disabled state only for now
        if (mEuiccManager.isEnabled()) {
            return;
        }

        // set up CountDownLatch and receiver
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mCallbackReceiver = new CallbackReceiver(countDownLatch);
        getContext()
                .registerReceiver(
                        mCallbackReceiver, new IntentFilter(ACTION_SWITCH_TO_SUBSCRIPTION),
                        Context.RECEIVER_EXPORTED_UNAUDITED);

        // call switchToSubscription()
        PendingIntent callbackIntent = createCallbackIntent(ACTION_SWITCH_TO_SUBSCRIPTION);
        mEuiccManager.switchToSubscription(4, callbackIntent);

        // wait for callback
        try {
            countDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        // verify correct result code is received
        assertEquals(
                EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR, mCallbackReceiver.getResultCode());
    }

    @Test
    public void testSwitchToSubscriptionWithCallback() {
        // test disabled state only for now
        if (mEuiccManager.isEnabled()) {
            return;
        }

        // set up CountDownLatch and receiver
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mCallbackReceiver = new CallbackReceiver(countDownLatch);
        getContext()
                .registerReceiver(
                        mCallbackReceiver, new IntentFilter(ACTION_SWITCH_TO_SUBSCRIPTION),
                        Context.RECEIVER_EXPORTED_UNAUDITED);

        // call switchToSubscription()
        PendingIntent callbackIntent = createCallbackIntent(ACTION_SWITCH_TO_SUBSCRIPTION);
        mEuiccManager.switchToSubscription(4, TelephonyManager.DEFAULT_PORT_INDEX, callbackIntent);

        // wait for callback
        try {
            countDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        // verify correct result code is received
        assertEquals(
                EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR, mCallbackReceiver.getResultCode());
    }

    @Test
    public void testEraseSubscriptions() {
        // test disabled state only for now
        if (mEuiccManager.isEnabled()) {
            return;
        }

        // set up CountDownLatch and receiver
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mCallbackReceiver = new CallbackReceiver(countDownLatch);
        getContext()
                .registerReceiver(
                        mCallbackReceiver, new IntentFilter(ACTION_ERASE_SUBSCRIPTIONS),
                        Context.RECEIVER_EXPORTED_UNAUDITED);

        // call eraseSubscriptions()
        PendingIntent callbackIntent = createCallbackIntent(ACTION_ERASE_SUBSCRIPTIONS);
        mEuiccManager.eraseSubscriptions(EuiccCardManager.RESET_OPTION_DELETE_OPERATIONAL_PROFILES,
                callbackIntent);

        // wait for callback
        try {
            countDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        // verify correct result code is received
        assertEquals(
                EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR, mCallbackReceiver.getResultCode());
    }

    @Test
    public void testStartResolutionActivity() {
        // set up CountDownLatch and receiver
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mCallbackReceiver = new CallbackReceiver(countDownLatch);
        getContext()
                .registerReceiver(
                        mCallbackReceiver, new IntentFilter(ACTION_START_TEST_RESOLUTION_ACTIVITY),
                        Context.RECEIVER_EXPORTED_UNAUDITED);

        /*
         * Start EuiccTestResolutionActivity to test EuiccManager#startResolutionActivity(), since
         * it requires a foreground activity. EuiccTestResolutionActivity will report the test
         * result to the callback receiver.
         */
        Intent testResolutionActivityIntent =
                new Intent(getContext(), EuiccTestResolutionActivity.class);
        PendingIntent callbackIntent = createCallbackIntent(ACTION_START_TEST_RESOLUTION_ACTIVITY);
        testResolutionActivityIntent.putExtra(
                EuiccTestResolutionActivity.EXTRA_ACTIVITY_CALLBACK_INTENT, callbackIntent);
        testResolutionActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(testResolutionActivityIntent);

        // wait for callback
        try {
            countDownLatch.await(ACTIVITY_CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }

        // verify test result reported by EuiccTestResolutionActivity
        assertEquals(
                EuiccTestResolutionActivity.RESULT_CODE_TEST_PASSED,
                mCallbackReceiver.getResultCode());
    }

    @Test
    public void testOperationCode() {
        // Ensure if platform source code is updated, these constants stays the same.
        assertEquals(EuiccManager.OPERATION_SYSTEM, 1);
        assertEquals(EuiccManager.OPERATION_SIM_SLOT, 2);
        assertEquals(EuiccManager.OPERATION_EUICC_CARD, 3);
        assertEquals(EuiccManager.OPERATION_SWITCH, 4);
        assertEquals(EuiccManager.OPERATION_DOWNLOAD, 5);
        assertEquals(EuiccManager.OPERATION_METADATA, 6);
        assertEquals(EuiccManager.OPERATION_EUICC_GSMA, 7);
        assertEquals(EuiccManager.OPERATION_APDU, 8);
        assertEquals(EuiccManager.OPERATION_SMDX, 9);
        assertEquals(EuiccManager.OPERATION_SMDX_SUBJECT_REASON_CODE, 10);
        assertEquals(EuiccManager.OPERATION_HTTP, 11);
    }

    @Test
    public void testErrorCode() {
        // Ensure if platform source code is updated, these constants stays the same.
        assertEquals(EuiccManager.ERROR_CARRIER_LOCKED, 10000);
        assertEquals(EuiccManager.ERROR_INVALID_ACTIVATION_CODE, 10001);
        assertEquals(EuiccManager.ERROR_INVALID_CONFIRMATION_CODE, 10002);
        assertEquals(EuiccManager.ERROR_INCOMPATIBLE_CARRIER, 10003);
        assertEquals(EuiccManager.ERROR_EUICC_INSUFFICIENT_MEMORY, 10004);
        assertEquals(EuiccManager.ERROR_TIME_OUT, 10005);
        assertEquals(EuiccManager.ERROR_EUICC_MISSING, 10006);
        assertEquals(EuiccManager.ERROR_UNSUPPORTED_VERSION, 10007);
        assertEquals(EuiccManager.ERROR_SIM_MISSING, 10008);
        assertEquals(EuiccManager.ERROR_INSTALL_PROFILE, 10009);
        assertEquals(EuiccManager.ERROR_DISALLOWED_BY_PPR, 10010);
        assertEquals(EuiccManager.ERROR_ADDRESS_MISSING, 10011);
        assertEquals(EuiccManager.ERROR_CERTIFICATE_ERROR, 10012);
        assertEquals(EuiccManager.ERROR_NO_PROFILES_AVAILABLE, 10013);
        assertEquals(EuiccManager.ERROR_CONNECTION_ERROR, 10014);
        assertEquals(EuiccManager.ERROR_INVALID_RESPONSE, 10015);
        assertEquals(EuiccManager.ERROR_OPERATION_BUSY, 10016);
    }

    @Test
    public void testSetSupportedCountries() {
        // Only test it when EuiccManager is enabled.
        if (!mEuiccManager.isEnabled()) {
            return;
        }

        // Get country list for restoring later.
        List<String> originalSupportedCountry = mEuiccManager.getSupportedCountries();

        List<String> expectedCountries = Arrays.asList("US", "SG");
        // Sets supported countries
        mEuiccManager.setSupportedCountries(expectedCountries);

        // Verify supported countries are expected
        assertEquals(expectedCountries, mEuiccManager.getSupportedCountries());

        // Restore the original country list
        mEuiccManager.setSupportedCountries(originalSupportedCountry);
    }

    @Test
    public void testSetUnsupportedCountries() {
        // Only test it when EuiccManager is enabled.
        if (!mEuiccManager.isEnabled()) {
            return;
        }

        // Get country list for restoring later.
        List<String> originalUnsupportedCountry = mEuiccManager.getUnsupportedCountries();

        List<String> expectedCountries = Arrays.asList("US", "SG");
        // Sets unsupported countries
        mEuiccManager.setUnsupportedCountries(expectedCountries);

        // Verify unsupported countries are expected
        assertEquals(expectedCountries, mEuiccManager.getUnsupportedCountries());

        // Restore the original country list
        mEuiccManager.setUnsupportedCountries(originalUnsupportedCountry);
    }

    @Test
    public void testIsSupportedCountry_returnsTrue_ifCountryIsOnSupportedList() {
        // Only test it when EuiccManager is enabled.
        if (!mEuiccManager.isEnabled()) {
            return;
        }

        // Get country list for restoring later.
        List<String> originalSupportedCountry = mEuiccManager.getSupportedCountries();

        // Sets supported countries
        mEuiccManager.setSupportedCountries(Arrays.asList("US", "SG"));

        // Verify the country is supported
        assertTrue(mEuiccManager.isSupportedCountry("US"));

        // Restore the original country list
        mEuiccManager.setSupportedCountries(originalSupportedCountry);
    }

    @Test
    public void testIsSupportedCountry_returnsTrue_ifCountryIsNotOnUnsupportedList() {
        // Only test it when EuiccManager is enabled.
        if (!mEuiccManager.isEnabled()) {
            return;
        }

        // Get country list for restoring later.
        List<String> originalSupportedCountry = mEuiccManager.getSupportedCountries();
        List<String> originalUnsupportedCountry = mEuiccManager.getUnsupportedCountries();

        // Sets supported countries
        mEuiccManager.setSupportedCountries(new ArrayList<>());
        // Sets unsupported countries
        mEuiccManager.setUnsupportedCountries(Arrays.asList("SG"));

        // Verify the country is supported
        assertTrue(mEuiccManager.isSupportedCountry("US"));

        // Restore the original country list
        mEuiccManager.setSupportedCountries(originalSupportedCountry);
        mEuiccManager.setUnsupportedCountries(originalUnsupportedCountry);
    }

    @Test
    public void testIsSupportedCountry_returnsFalse_ifCountryIsNotOnSupportedList() {
        // Only test it when EuiccManager is enabled.
        if (!mEuiccManager.isEnabled()) {
            return;
        }

        // Get country list for restoring later.
        List<String> originalSupportedCountry = mEuiccManager.getSupportedCountries();

        // Sets supported countries
        mEuiccManager.setSupportedCountries(Arrays.asList("SG"));

        // Verify the country is not supported
        assertFalse(mEuiccManager.isSupportedCountry("US"));

        // Restore the original country list
        mEuiccManager.setSupportedCountries(originalSupportedCountry);
    }

    @Test
    public void testIsSupportedCountry_returnsFalse_ifCountryIsOnUnsupportedList() {
        // Only test it when EuiccManager is enabled.
        if (!mEuiccManager.isEnabled()) {
            return;
        }

        // Get country list for restoring later.
        List<String> originalSupportedCountry = mEuiccManager.getSupportedCountries();
        List<String> originalUnsupportedCountry = mEuiccManager.getUnsupportedCountries();

        // Sets supported countries
        mEuiccManager.setSupportedCountries(new ArrayList<>());
        // Sets unsupported countries
        mEuiccManager.setUnsupportedCountries(Arrays.asList("US"));

        // Verify the country is not supported
        assertFalse(mEuiccManager.isSupportedCountry("US"));

        // Restore the original country list
        mEuiccManager.setSupportedCountries(originalSupportedCountry);
        mEuiccManager.setUnsupportedCountries(originalUnsupportedCountry);
    }

    @Test
    public void testIsSupportedCountry_returnsFalse_ifBothListsAreEmpty() {
        // Only test it when EuiccManager is enabled.
        if (!mEuiccManager.isEnabled()) {
            return;
        }

        // Get country list for restoring later.
        List<String> originalSupportedCountry = mEuiccManager.getSupportedCountries();
        List<String> originalUnsupportedCountry = mEuiccManager.getUnsupportedCountries();

        // Sets supported countries
        mEuiccManager.setSupportedCountries(new ArrayList<>());
        // Sets unsupported countries
        mEuiccManager.setUnsupportedCountries(new ArrayList<>());

        // Verify the country is supported
        assertTrue(mEuiccManager.isSupportedCountry("US"));

        // Restore the original country list
        mEuiccManager.setSupportedCountries(originalSupportedCountry);
        mEuiccManager.setUnsupportedCountries(originalUnsupportedCountry);
    }

    @Test
    public void testIsSimPortAvailableWithInvalidPortIndex() throws Exception {
        // Only test it when EuiccManager is enabled.
        if (!mEuiccManager.isEnabled()) {
            return;
        }

        boolean result = mEuiccManager.isSimPortAvailable(/* portIndex= */ -1);

        assertFalse(result);
    }

    @Test
    public void testIsSimPortAvailableWithValidPorts() throws Exception {
        // Only test it when EuiccManager is enabled.
        if (!mEuiccManager.isEnabled()) {
            return;
        }
        // Get all the available UiccCardInfos.
        TelephonyManager telephonyManager = getContext().getSystemService(TelephonyManager.class);
        List<UiccCardInfo> uiccCardInfos =
                ShellIdentityUtils.invokeMethodWithShellPermissions(telephonyManager,
                        (tm) -> tm.getUiccCardsInfo());
        for (UiccCardInfo cardInfo : uiccCardInfos) {
            List<UiccPortInfo> portInfoList = (List<UiccPortInfo>) cardInfo.getPorts();
            if (cardInfo.isEuicc()) {
                for (UiccPortInfo portInfo : portInfoList) {
                    // Check if port is active and no profile install on it.
                    if (portInfo.isActive() && TextUtils.isEmpty(portInfo.getIccId())) {
                        boolean result = mEuiccManager.isSimPortAvailable(portInfo.getPortIndex());
                        assertTrue(result);
                    }
                }
            }
        }
    }

    @Test
    public void testTransferEmbeddedSubscriptionsAction() {
        // Only test it when EuiccManager is enabled.
        if (!mEuiccManager.isEnabled()) {
            return;
        }
        Intent testActionIntent =
                new Intent(EuiccManager.ACTION_TRANSFER_EMBEDDED_SUBSCRIPTIONS);
        testActionIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        assertThrows(SecurityException.class, () -> getContext().startActivity(testActionIntent));
    }

    @Test
    public void testConvertToEmbeddedSubscriptionAction() {
        // Only test it when EuiccManager is enabled.
        if (!mEuiccManager.isEnabled()) {
            return;
        }
        Intent testActionIntent =
                new Intent(EuiccManager.ACTION_CONVERT_TO_EMBEDDED_SUBSCRIPTION);
        testActionIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        assertThrows(SecurityException.class, () -> getContext().startActivity(testActionIntent));
    }

    @Test
    public void testEuiccProvisionAction() {
        // Only test it when EuiccManager is enabled.
        if (!mEuiccManager.isEnabled()) {
            return;
        }
        setTestEuiccUiComponent();
        // set up CountDownLatch and receiver
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mCallbackReceiver = new CallbackReceiver(countDownLatch);
        getContext()
                .registerReceiver(
                        mCallbackReceiver,
                        new IntentFilter(ACTION_PROVISION_EMBEDDED_SUBSCRIPTION),
                        Context.RECEIVER_EXPORTED_UNAUDITED);
        // This confirms EuiccManager Action handled
        assertTrue(launchActivity(new
                Intent(EuiccManager.ACTION_PROVISION_EMBEDDED_SUBSCRIPTION)));
        // wait for callback
        try {
            countDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }
        // This confirms the EuiccService action mapped with the respective EuiccManager action
        assertEquals(ACTION_PROVISION_EMBEDDED_SUBSCRIPTION, mCallbackReceiver.getResultData());
    }

    @Test
    public void testEuiccManageAction() {
        // Only test it when EuiccManager is enabled.
        if (!mEuiccManager.isEnabled()) {
            return;
        }
        setTestEuiccUiComponent();
        // set up CountDownLatch and receiver
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mCallbackReceiver = new CallbackReceiver(countDownLatch);
        getContext()
                .registerReceiver(
                        mCallbackReceiver,
                        new IntentFilter(ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS),
                        Context.RECEIVER_EXPORTED_UNAUDITED);
        // This confirms EuiccManager Action handled
        assertTrue(launchActivity(new
                Intent(EuiccManager.ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS)));
        // wait for callback
        try {
            countDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }
        // This confirms the EuiccService action mapped with the respective EuiccManager action
        assertEquals(ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS, mCallbackReceiver.getResultData());
    }

    @Test
    public void testEuiccTransferAction() {
        // Only test it when EuiccManager is enabled.
        if (!mEuiccManager.isEnabled()) {
            return;
        }
        setTestEuiccUiComponent();
        // set up CountDownLatch and receiver
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mCallbackReceiver = new CallbackReceiver(countDownLatch);
        getContext()
                .registerReceiver(
                        mCallbackReceiver,
                        new IntentFilter(ACTION_TRANSFER_EMBEDDED_SUBSCRIPTIONS),
                        Context.RECEIVER_EXPORTED_UNAUDITED);
        // This confirms EuiccManager Action handled
        assertTrue(launchActivity(new
                Intent(EuiccManager.ACTION_TRANSFER_EMBEDDED_SUBSCRIPTIONS)));
        // wait for callback
        try {
            countDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }
        // This confirms the EuiccService action mapped with the respective EuiccManager action
        assertEquals(ACTION_TRANSFER_EMBEDDED_SUBSCRIPTIONS, mCallbackReceiver.getResultData());
    }

    @Test
    public void testEuiccConvertAction() {
        // Only test it when EuiccManager is enabled.
        if (!mEuiccManager.isEnabled()) {
            return;
        }
        setTestEuiccUiComponent();
        // set up CountDownLatch and receiver
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mCallbackReceiver = new CallbackReceiver(countDownLatch);
        getContext()
                .registerReceiver(
                        mCallbackReceiver,
                        new IntentFilter(ACTION_CONVERT_TO_EMBEDDED_SUBSCRIPTIONS),
                        Context.RECEIVER_EXPORTED_UNAUDITED);
        // This confirms EuiccManager Action handled
        assertTrue(launchActivity(new
                Intent(EuiccManager.ACTION_CONVERT_TO_EMBEDDED_SUBSCRIPTION)));
        // wait for callback
        try {
            countDownLatch.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.toString());
        }
        // This confirms the EuiccService action mapped with the respective EuiccManager action
        assertEquals(ACTION_CONVERT_TO_EMBEDDED_SUBSCRIPTIONS, mCallbackReceiver.getResultData());
    }

    @Test
    public void testSetPsimConversionSupportedCarriers() {
        if (!Flags.supportPsimToEsimConversion()) {
            return;
        }
        // Only test it when EuiccManager is enabled.
        try {
            if (!mEuiccManager.isEnabled()) {
                return;
            }
        } catch (UnsupportedOperationException e) {
            fail(e.toString());
        }

        // Sets supported countries
        Set<Integer> carrierIds = new ArraySet<>();
        carrierIds.add(1);
        mEuiccManager.setPsimConversionSupportedCarriers(carrierIds);

        assertTrue(mEuiccManager.isPsimConversionSupported(1));
        assertFalse(mEuiccManager.isPsimConversionSupported(2));
    }


    private void setTestEuiccUiComponent() {
        try {
            TelephonyUtils.executeShellCommand(InstrumentationRegistry.getInstrumentation(),
                    COMMAND_UPDATE_EUICC_UI_PACKAGE  +
                            TEST_EUICC_UI_COMPONENT + getContext().getPackageName());

        } catch (Exception e){
            fail(e.toString());
        }
    }

    private boolean launchActivity(Intent intent) {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity();
        boolean activityFound = true;
        try {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            getContext().startActivity(intent);
        } catch (Exception e){
            activityFound = false;
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
        return activityFound;
    }

    private Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    private DownloadableSubscription createDownloadableSubscription() {
        return DownloadableSubscription.forActivationCode(ACTIVATION_CODE);
    }

    private PendingIntent createCallbackIntent(String action) {
        Intent intent = new Intent(action).setPackage(getContext().getPackageName());
        return PendingIntent.getBroadcast(
                getContext(), REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    private static class CallbackReceiver extends BroadcastReceiver {

        private CountDownLatch mCountDownLatch;

        public CallbackReceiver(CountDownLatch latch) {
            mCountDownLatch = latch;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String callBackAction = intent.getAction();
            for (String callbackAction : sCallbackActions) {
                if (callbackAction.equals(intent.getAction())) {
                    int resultCode = getResultCode();
                    setResultData(callBackAction);
                    mCountDownLatch.countDown();
                    break;
                }
            }
        }
    }
}
