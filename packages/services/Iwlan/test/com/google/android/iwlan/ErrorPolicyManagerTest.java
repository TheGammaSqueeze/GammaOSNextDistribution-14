/*
 * Copyright 2020 The Android Open Source Project
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

package com.google.android.iwlan;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetManager;
import android.net.ipsec.ike.exceptions.IkeProtocolException;
import android.os.PersistableBundle;
import android.os.test.TestLooper;
import android.telephony.CarrierConfigManager;
import android.telephony.DataFailCause;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.data.DataService;

import androidx.test.InstrumentationRegistry;

import com.google.auto.value.AutoValue;

import org.json.JSONArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ErrorPolicyManagerTest {
    @AutoValue
    abstract static class ErrorPolicyString {
        abstract String errorType();

        abstract List<String> errorDetails();

        abstract List<String> retryArray();

        abstract List<String> unthrottlingEvents();

        abstract Optional<String> numAttemptsPerFqdn();

        abstract Optional<String> handoverAttemptCount();

        static Builder builder() {
            return new AutoValue_ErrorPolicyManagerTest_ErrorPolicyString.Builder();
        }

        @AutoValue.Builder
        abstract static class Builder {
            abstract Builder setErrorType(String errorType);

            abstract Builder setErrorDetails(List<String> errorDetails);

            abstract Builder setRetryArray(List<String> retryArray);

            abstract Builder setUnthrottlingEvents(List<String> unthrottlingEvents);

            abstract Builder setNumAttemptsPerFqdn(String numAttemptsPerFqdn);

            abstract Builder setHandoverAttemptCount(String handoverAttemptCount);

            abstract ErrorPolicyString build();
        }

        String getErrorPolicyInString() {
            StringBuilder errorPolicy =
                    new StringBuilder(
                            "\"ErrorType\": \""
                                    + errorType()
                                    + "\","
                                    + "\"ErrorDetails\": [\""
                                    + String.join("\", \"", errorDetails())
                                    + "\"],"
                                    + "\"RetryArray\": [\""
                                    + String.join("\", \"", retryArray())
                                    + "\"],"
                                    + "\"UnthrottlingEvents\": [\""
                                    + String.join("\", \"", unthrottlingEvents())
                                    + "\"]");

            numAttemptsPerFqdn()
                    .ifPresent(
                            numAttemptsPerFqdn ->
                                    errorPolicy
                                            .append(",\"NumAttemptsPerFqdn\": \"")
                                            .append(numAttemptsPerFqdn)
                                            .append("\""));
            handoverAttemptCount()
                    .ifPresent(
                            handoverAttemptCount ->
                                    errorPolicy
                                            .append(",\"HandoverAttemptCount\": \"")
                                            .append(handoverAttemptCount)
                                            .append("\""));
            return errorPolicy.toString();
        }
    }

    private static final String TAG = "ErrorPolicyManagerTest";

    // @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    private ErrorPolicyManager mErrorPolicyManager;
    private static final int DEFAULT_SLOT_INDEX = 0;
    private static final int DEFAULT_SUBID = 0;
    private static final int TEST_CARRIER_ID = 1;

    private TestLooper mTestLooper = new TestLooper();
    private long mMockedClockTime = 0;

    @Mock private Context mMockContext;
    @Mock CarrierConfigManager mMockCarrierConfigManager;
    @Mock SubscriptionManager mMockSubscriptionManager;
    @Mock TelephonyManager mMockTelephonyManager;
    @Mock SubscriptionInfo mMockSubscriptionInfo;
    @Mock DataService.DataServiceProvider mMockDataServiceProvider;
    @Mock private ContentResolver mMockContentResolver;
    MockitoSession mStaticMockSession;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                mockitoSession()
                        .mockStatic(IwlanDataService.class)
                        .spyStatic(IwlanHelper.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        when(IwlanDataService.getDataServiceProvider(anyInt()))
                .thenReturn(mMockDataServiceProvider);
        when(IwlanHelper.elapsedRealtime()).thenAnswer(i -> mMockedClockTime);
        AssetManager mockAssetManager = mock(AssetManager.class);
        Context context = InstrumentationRegistry.getTargetContext();
        InputStream is = context.getResources().getAssets().open("defaultiwlanerrorconfig.json");
        doReturn(mockAssetManager).when(mMockContext).getAssets();
        doReturn(is).when(mockAssetManager).open(any());
        setupMockForCarrierConfig(null);
        ErrorPolicyManager.resetAllInstances();
        mErrorPolicyManager = spy(ErrorPolicyManager.getInstance(mMockContext, DEFAULT_SLOT_INDEX));
        doReturn(mTestLooper.getLooper()).when(mErrorPolicyManager).getLooper();
        mErrorPolicyManager.initHandler();
    }

    @After
    public void cleanUp() throws Exception {
        mStaticMockSession.finishMocking();
        mErrorPolicyManager.releaseInstance();
    }

    private static IwlanError buildIwlanIkeProtocolError(int errorCode, byte[] errorData) {
        final IkeProtocolException exception = mock(IkeProtocolException.class);
        when(exception.getErrorType()).thenReturn(errorCode);
        when(exception.getErrorData()).thenReturn(errorData);
        return new IwlanError(exception);
    }

    private static IwlanError buildIwlanIkeProtocolError(int errorCode) {
        return buildIwlanIkeProtocolError(errorCode, new byte[0]);
    }

    private static IwlanError buildIwlanIkeAuthFailedError() {
        return buildIwlanIkeProtocolError(IkeProtocolException.ERROR_TYPE_AUTHENTICATION_FAILED);
    }

    private static IwlanError buildIwlanIkeChildSaNotFoundError() {
        return buildIwlanIkeProtocolError(IkeProtocolException.ERROR_TYPE_CHILD_SA_NOT_FOUND);
    }

    private static IwlanError buildIwlanIkeInternalAddressFailure() {
        return buildIwlanIkeProtocolError(IkeProtocolException.ERROR_TYPE_INTERNAL_ADDRESS_FAILURE);
    }

    @Test
    public void testValidCarrierConfig() throws Exception {
        String apn = "ims";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + ErrorPolicyString.builder()
                                .setErrorType("IKE_PROTOCOL_ERROR_TYPE")
                                .setErrorDetails(List.of("24", "34", "9000-9050"))
                                .setRetryArray(List.of("4", "8", "16"))
                                .setUnthrottlingEvents(
                                        List.of("APM_ENABLE_EVENT", "WIFI_AP_CHANGED_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}, {"
                        + ErrorPolicyString.builder()
                                .setErrorType("GENERIC_ERROR_TYPE")
                                .setErrorDetails(List.of("SERVER_SELECTION_FAILED"))
                                .setRetryArray(List.of("0"))
                                .setUnthrottlingEvents(List.of("APM_ENABLE_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}]"
                        + "}]";

        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfig(bundle);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();
        mTestLooper.dispatchAll();

        // IKE_PROTOCOL_ERROR_TYPE(24) and retryArray = 4,8,16
        IwlanError iwlanError = buildIwlanIkeAuthFailedError();
        long time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(4, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(8, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(16, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(86400, time);

        // Validate the range error detail.
        iwlanError = buildIwlanIkeProtocolError(9030, new byte[] {0x00, 0x01});
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(4, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(8, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(16, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(86400, time);

        // GENERIC_PROTOCOL_ERROR_TYPE - SERVER_SELECTION_FAILED and retryArray = 0
        iwlanError = new IwlanError(IwlanError.EPDG_SELECTOR_SERVER_SELECTION_FAILED);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(0, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(86400, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(86400, time);

        // Fallback case IKE_INIT_TIMEOUT and retryArray is 1, 2, 2, 10, 20
        // Should start at RetryIndex 0
        iwlanError = new IwlanError(IwlanError.IKE_INIT_TIMEOUT);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(1, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(2, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(2, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(10, time);

        // Fallback case GENERIC_PROTOCOL_ERROR_TYPE(44) and retryArray is 5, 10, -1 as in
        // DEFAULT_CONFIG
        iwlanError = buildIwlanIkeChildSaNotFoundError();
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(5, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(10, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(10, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(20, time);
    }

    @Test
    public void testShouldKeepRetryIndexForDifferentErrorCause() {
        String apn = "ims";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + ErrorPolicyString.builder()
                                .setErrorType("IKE_PROTOCOL_ERROR_TYPE")
                                .setErrorDetails(List.of("24", "34", "9000-9050"))
                                .setRetryArray(List.of("4", "8", "16"))
                                .setUnthrottlingEvents(
                                        List.of("APM_ENABLE_EVENT", "WIFI_AP_CHANGED_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}, {"
                        + ErrorPolicyString.builder()
                                .setErrorType("GENERIC_ERROR_TYPE")
                                .setErrorDetails(List.of("SERVER_SELECTION_FAILED"))
                                .setRetryArray(List.of("0"))
                                .setUnthrottlingEvents(List.of("APM_ENABLE_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}]"
                        + "}]";

        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfig(bundle);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();
        mTestLooper.dispatchAll();

        // IKE_PROTOCOL_ERROR_TYPE(24) and retryArray = 4,8,16
        IwlanError authFailedIwlanError = buildIwlanIkeAuthFailedError();
        IwlanError customProtocolIwlanError = buildIwlanIkeProtocolError(9030);
        IwlanError serverSelectionIwlanError =
                new IwlanError(IwlanError.EPDG_SELECTOR_SERVER_SELECTION_FAILED);

        long time = mErrorPolicyManager.reportIwlanError(apn, authFailedIwlanError);
        assertEquals(4, time);

        // Different protocol error should have its own Index (i.e. not shared with auth error)
        time = mErrorPolicyManager.reportIwlanError(apn, customProtocolIwlanError);
        assertEquals(4, time);
        time = mErrorPolicyManager.reportIwlanError(apn, customProtocolIwlanError);
        assertEquals(8, time);
        time = mErrorPolicyManager.reportIwlanError(apn, customProtocolIwlanError);
        assertEquals(16, time);

        // Different IwlanError type should have its own index
        time = mErrorPolicyManager.reportIwlanError(apn, serverSelectionIwlanError);
        assertEquals(0, time);
        time = mErrorPolicyManager.reportIwlanError(apn, serverSelectionIwlanError);
        assertEquals(86400, time);

        // Retry index should not be cleared, should start from last same cause index (auth error)
        time = mErrorPolicyManager.reportIwlanError(apn, authFailedIwlanError);
        assertEquals(8, time);
        time = mErrorPolicyManager.reportIwlanError(apn, authFailedIwlanError);
        assertEquals(16, time);

        time = mErrorPolicyManager.reportIwlanError(apn, serverSelectionIwlanError);
        assertEquals(86400, time);

        // if no error, it should clear the retry index
        time = mErrorPolicyManager.reportIwlanError(apn, new IwlanError(IwlanError.NO_ERROR));
        assertEquals(-1, time);

        // if backoff time is specified, should use the specified time instead
        time = mErrorPolicyManager.reportIwlanError(apn, authFailedIwlanError);
        assertEquals(4, time);
        time = mErrorPolicyManager.reportIwlanError(apn, authFailedIwlanError, 5);
        assertEquals(5, time);
    }

    @Test
    public void testDefaultPolicyFallback() throws Exception {
        String apn = "ims";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + ErrorPolicyString.builder()
                                .setErrorType("IKE_PROTOCOL_ERROR_TYPE")
                                .setErrorDetails(List.of("WRONG_ERROR_DETAIL"))
                                .setRetryArray(List.of("4", "8", "16"))
                                .setUnthrottlingEvents(
                                        List.of("APM_ENABLE_EVENT", "WIFI_AP_CHANGED_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}]"
                        + "}]";

        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfig(bundle);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();
        mTestLooper.dispatchAll();

        // Fallback to default Iwlan error policy for IKE_PROTOCOL_ERROR_TYPE(24) because of failed
        // parsing (or lack of explicit carrier-defined policy).
        IwlanError iwlanError = buildIwlanIkeAuthFailedError();
        long time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(5, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(10, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(10, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(20, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(40, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(80, time);

        iwlanError = buildIwlanIkeProtocolError(9002);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(5, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(10, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(10, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(20, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(40, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(80, time);

        iwlanError = buildIwlanIkeInternalAddressFailure();
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(0, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(0, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(0, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(10, time);
    }

    @Test
    public void testChoosingFallbackPolicy() throws Exception {
        String apn = "ims";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + ErrorPolicyString.builder()
                                .setErrorType("IKE_PROTOCOL_ERROR_TYPE")
                                .setErrorDetails(List.of("24", "34"))
                                .setRetryArray(List.of("4", "8", "16"))
                                .setUnthrottlingEvents(
                                        List.of("APM_ENABLE_EVENT", "WIFI_AP_CHANGED_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}, {"
                        + ErrorPolicyString.builder()
                                .setErrorType("IKE_PROTOCOL_ERROR_TYPE")
                                .setErrorDetails(List.of("*"))
                                .setRetryArray(List.of("0"))
                                .setUnthrottlingEvents(List.of("APM_ENABLE_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}]"
                        + "}]";
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfig(bundle);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();
        mTestLooper.dispatchAll();

        mErrorPolicyManager.logErrorPolicies();

        // IKE_PROTOCOL_ERROR_TYPE(24) and retryArray = 4, 8, 16
        IwlanError iwlanError = buildIwlanIkeAuthFailedError();
        long time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(4, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(8, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(16, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(86400, time);

        // IKE_PROTOCOL_ERROR_TYPE(44) and retryArray = 0 as it will fallback to
        // IKE_PROTOCOL_ERROR_TYPE generic fallback first.
        iwlanError = buildIwlanIkeChildSaNotFoundError();
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(0, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(86400, time);
    }

    @Test
    public void testNullCarrierConfig() throws Exception {
        String apn = "ims";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + ErrorPolicyString.builder()
                                .setErrorType("IKE_PROTOCOL_ERROR_TYPE")
                                .setErrorDetails(List.of("24"))
                                .setRetryArray(List.of("100"))
                                .setUnthrottlingEvents(
                                        List.of("APM_ENABLE_EVENT", "WIFI_AP_CHANGED_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}]"
                        + "}]";

        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfigWithCarrierId(bundle, 1 /* carrierId */);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();
        mTestLooper.dispatchAll();

        // IKE_PROTOCOL_ERROR_TYPE(24) and retry time = 100.
        IwlanError iwlanError = buildIwlanIkeAuthFailedError();
        long time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(100, time);

        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, null);
        setupMockForCarrierConfigWithCarrierId(bundle, 2 /* carrierId */);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();
        mTestLooper.dispatchAll();

        // IKE_PROTOCOL_ERROR_TYPE(24). Fall back to default error policy, retry time = 5.
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(5, time);
    }

    @Test
    public void testGetLastError() {
        String apn1 = "ims";
        String apn2 = "mms";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn1
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + ErrorPolicyString.builder()
                                .setErrorType("IKE_PROTOCOL_ERROR_TYPE")
                                .setErrorDetails(List.of("*"))
                                .setRetryArray(List.of("4", "8", "16"))
                                .setUnthrottlingEvents(
                                        List.of("APM_DISABLE_EVENT", "WIFI_AP_CHANGED_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}]"
                        + "}]";

        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfig(bundle);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();
        mTestLooper.dispatchAll();

        IwlanError iwlanAuthError = buildIwlanIkeAuthFailedError();
        mErrorPolicyManager.reportIwlanError(apn1, iwlanAuthError);

        assertEquals(iwlanAuthError, mErrorPolicyManager.getLastError(apn1));
        assertEquals(new IwlanError(IwlanError.NO_ERROR), mErrorPolicyManager.getLastError(apn2));

        mErrorPolicyManager.reportIwlanError(apn2, iwlanAuthError);
        assertEquals(iwlanAuthError, mErrorPolicyManager.getLastError(apn1));
        assertEquals(iwlanAuthError, mErrorPolicyManager.getLastError(apn2));

        IwlanError iwlanInternalAddressFailure = buildIwlanIkeInternalAddressFailure();
        mErrorPolicyManager.reportIwlanError(apn1, iwlanInternalAddressFailure);
        assertEquals(iwlanInternalAddressFailure, mErrorPolicyManager.getLastError(apn1));
        assertEquals(iwlanAuthError, mErrorPolicyManager.getLastError(apn2));

        // After APM enable, apn2 should have no last error and apn1 should keep last error
        // Because apn1 protocol error config have no APM_ENABLE_EVENT in unthrottling events
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.APM_ENABLE_EVENT)
                .sendToTarget();
        mTestLooper.dispatchAll();

        assertEquals(iwlanInternalAddressFailure, mErrorPolicyManager.getLastError(apn1));
        assertEquals(new IwlanError(IwlanError.NO_ERROR), mErrorPolicyManager.getLastError(apn2));

        // After CarrierConfigChanged, all last error should be cleared
        setupMockForCarrierConfigWithCarrierId(bundle, 2 /* carrierId */);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();
        mTestLooper.dispatchAll();

        assertEquals(new IwlanError(IwlanError.NO_ERROR), mErrorPolicyManager.getLastError(apn1));
        assertEquals(new IwlanError(IwlanError.NO_ERROR), mErrorPolicyManager.getLastError(apn2));
    }

    @Test
    public void testCanBringUpTunnel() throws Exception {
        String apn = "ims";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + ErrorPolicyString.builder()
                                .setErrorType("IKE_PROTOCOL_ERROR_TYPE")
                                .setErrorDetails(List.of("24", "34"))
                                .setRetryArray(List.of("4", "8", "16"))
                                .setUnthrottlingEvents(
                                        List.of("APM_ENABLE_EVENT", "WIFI_AP_CHANGED_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}, {"
                        + ErrorPolicyString.builder()
                                .setErrorType("GENERIC_ERROR_TYPE")
                                .setErrorDetails(List.of("SERVER_SELECTION_FAILED"))
                                .setRetryArray(List.of("0"))
                                .setUnthrottlingEvents(List.of("APM_ENABLE_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}]"
                        + "}]";
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfig(bundle);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();
        mTestLooper.dispatchAll();

        // IKE_PROTOCOL_ERROR_TYPE(24) and retryArray = 4,8,16
        IwlanError iwlanError = buildIwlanIkeAuthFailedError();
        long time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(4, time);

        boolean bringUpTunnel = mErrorPolicyManager.canBringUpTunnel(apn);
        assertFalse(bringUpTunnel);

        advanceClockByTimeMs(4000);

        bringUpTunnel = mErrorPolicyManager.canBringUpTunnel(apn);
        assertTrue(bringUpTunnel);

        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(8, time);

        bringUpTunnel = mErrorPolicyManager.canBringUpTunnel(apn);
        assertFalse(bringUpTunnel);
    }

    @Test
    public void testNoErrorScenario() throws Exception {
        String apn = "ims";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + ErrorPolicyString.builder()
                                .setErrorType("IKE_PROTOCOL_ERROR_TYPE")
                                .setErrorDetails(List.of("24", "34"))
                                .setRetryArray(List.of("4", "8", "16"))
                                .setUnthrottlingEvents(
                                        List.of("APM_ENABLE_EVENT", "WIFI_AP_CHANGED_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}, {"
                        + ErrorPolicyString.builder()
                                .setErrorType("GENERIC_ERROR_TYPE")
                                .setErrorDetails(List.of("SERVER_SELECTION_FAILED"))
                                .setRetryArray(List.of("0"))
                                .setUnthrottlingEvents(List.of("APM_ENABLE_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}]"
                        + "}]";
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfig(bundle);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();
        mTestLooper.dispatchAll();

        // IKE_PROTOCOL_ERROR_TYPE(24) and retryArray = 4,8,16
        IwlanError iwlanError = buildIwlanIkeAuthFailedError();
        long time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(4, time);

        // report no error
        iwlanError = new IwlanError(IwlanError.NO_ERROR);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(-1, time);

        // Check whether the error is cleared after NO_ERROR is reported
        boolean bringUpTunnel = mErrorPolicyManager.canBringUpTunnel(apn);
        assertTrue(bringUpTunnel);
    }

    @Test
    public void testWifiDisableUnthrottle() throws Exception {
        String apn = "ims";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + ErrorPolicyString.builder()
                                .setErrorType("IKE_PROTOCOL_ERROR_TYPE")
                                .setErrorDetails(List.of("24", "34"))
                                .setRetryArray(List.of("6", "12", "24"))
                                .setUnthrottlingEvents(
                                        List.of("APM_ENABLE_EVENT", "WIFI_DISABLE_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}, {"
                        + ErrorPolicyString.builder()
                                .setErrorType("GENERIC_ERROR_TYPE")
                                .setErrorDetails(List.of("SERVER_SELECTION_FAILED"))
                                .setRetryArray(List.of("0"))
                                .setUnthrottlingEvents(List.of("APM_ENABLE_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}]"
                        + "}]";
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfig(bundle);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();
        mTestLooper.dispatchAll();

        // IKE_PROTOCOL_ERROR_TYPE(24) and retryArray = 6, 12, 24
        IwlanError iwlanError = buildIwlanIkeAuthFailedError();
        long time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(6, time);

        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.WIFI_DISABLE_EVENT)
                .sendToTarget();
        advanceClockByTimeMs(500);
        verify(mMockDataServiceProvider, times(1)).notifyApnUnthrottled(eq(apn));

        boolean bringUpTunnel = mErrorPolicyManager.canBringUpTunnel(apn);
        assertTrue(bringUpTunnel);

        iwlanError = buildIwlanIkeAuthFailedError();
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(6, time);
    }

    @Test
    public void testWFCDisableUnthrottle() throws Exception {
        String apn = "ims";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + ErrorPolicyString.builder()
                                .setErrorType("IKE_PROTOCOL_ERROR_TYPE")
                                .setErrorDetails(List.of("24", "34"))
                                .setRetryArray(List.of("6", "12", "24"))
                                .setUnthrottlingEvents(
                                        List.of("WIFI_CALLING_DISABLE_EVENT", "WIFI_DISABLE_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}, {"
                        + ErrorPolicyString.builder()
                                .setErrorType("GENERIC_ERROR_TYPE")
                                .setErrorDetails(List.of("SERVER_SELECTION_FAILED"))
                                .setRetryArray(List.of("0"))
                                .setUnthrottlingEvents(List.of("APM_ENABLE_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}]"
                        + "}]";
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfig(bundle);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();
        mTestLooper.dispatchAll();

        // IKE_PROTOCOL_ERROR_TYPE(24) and retryArray = 6, 12, 24
        IwlanError iwlanError = buildIwlanIkeAuthFailedError();
        long time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(6, time);

        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.WIFI_CALLING_DISABLE_EVENT)
                .sendToTarget();
        advanceClockByTimeMs(500);
        verify(mMockDataServiceProvider, times(1)).notifyApnUnthrottled(eq(apn));

        boolean bringUpTunnel = mErrorPolicyManager.canBringUpTunnel(apn);
        assertTrue(bringUpTunnel);

        iwlanError = buildIwlanIkeAuthFailedError();
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(6, time);
    }

    @Test
    public void testAPMUnthrottle() throws Exception {
        String apn = "ims";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + ErrorPolicyString.builder()
                                .setErrorType("IKE_PROTOCOL_ERROR_TYPE")
                                .setErrorDetails(List.of("24", "34"))
                                .setRetryArray(List.of("4", "8", "16"))
                                .setUnthrottlingEvents(
                                        List.of("APM_ENABLE_EVENT", "WIFI_AP_CHANGED_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}, {"
                        + ErrorPolicyString.builder()
                                .setErrorType("GENERIC_ERROR_TYPE")
                                .setErrorDetails(List.of("SERVER_SELECTION_FAILED"))
                                .setRetryArray(List.of("0"))
                                .setUnthrottlingEvents(List.of("APM_ENABLE_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}]"
                        + "}]";
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfig(bundle);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();
        mTestLooper.dispatchAll();

        // IKE_PROTOCOL_ERROR_TYPE(24) and retryArray = 4,8,16
        IwlanError iwlanError = buildIwlanIkeAuthFailedError();
        long time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(4, time);

        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.APM_ENABLE_EVENT)
                .sendToTarget();
        advanceClockByTimeMs(500);
        verify(mMockDataServiceProvider, times(1)).notifyApnUnthrottled(eq(apn));

        boolean bringUpTunnel = mErrorPolicyManager.canBringUpTunnel(apn);
        assertTrue(bringUpTunnel);

        iwlanError = buildIwlanIkeAuthFailedError();
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(4, time);
    }

    @Test
    public void testWifiApChangedUnthrottle() throws Exception {
        String apn = "ims";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + ErrorPolicyString.builder()
                                .setErrorType("IKE_PROTOCOL_ERROR_TYPE")
                                .setErrorDetails(List.of("24", "34"))
                                .setRetryArray(List.of("6", "12", "24"))
                                .setUnthrottlingEvents(
                                        List.of(
                                                "WIFI_CALLING_DISABLE_EVENT",
                                                "WIFI_DISABLE_EVENT",
                                                "WIFI_AP_CHANGED_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}, {"
                        + ErrorPolicyString.builder()
                                .setErrorType("GENERIC_ERROR_TYPE")
                                .setErrorDetails(List.of("SERVER_SELECTION_FAILED"))
                                .setRetryArray(List.of("0"))
                                .setUnthrottlingEvents(List.of("APM_ENABLE_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}]"
                        + "}]";
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfig(bundle);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();
        mTestLooper.dispatchAll();

        // IKE_PROTOCOL_ERROR_TYPE(24) and retryArray = 6, 12, 24
        IwlanError iwlanError = buildIwlanIkeAuthFailedError();
        long time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(6, time);

        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.WIFI_AP_CHANGED_EVENT)
                .sendToTarget();
        advanceClockByTimeMs(500);
        verify(mMockDataServiceProvider, times(1)).notifyApnUnthrottled(eq(apn));

        boolean bringUpTunnel = mErrorPolicyManager.canBringUpTunnel(apn);
        assertTrue(bringUpTunnel);

        iwlanError = buildIwlanIkeAuthFailedError();
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(6, time);
    }

    @Test
    public void testGetDataFailCauseRetryTime() throws Exception {
        String apn1 = "ims";
        String apn2 = "mms";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn1
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + ErrorPolicyString.builder()
                                .setErrorType("IKE_PROTOCOL_ERROR_TYPE")
                                .setErrorDetails(List.of("24", "34"))
                                .setRetryArray(List.of("4", "8", "16"))
                                .setUnthrottlingEvents(
                                        List.of("APM_ENABLE_EVENT", "WIFI_AP_CHANGED_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}, {"
                        + ErrorPolicyString.builder()
                                .setErrorType("GENERIC_ERROR_TYPE")
                                .setErrorDetails(List.of("SERVER_SELECTION_FAILED"))
                                .setRetryArray(List.of("0"))
                                .setUnthrottlingEvents(List.of("APM_ENABLE_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}]"
                        + "}]";
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfig(bundle);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();
        mTestLooper.dispatchAll();

        // IKE_PROTOCOL_ERROR_TYPE(24) and retryArray = 4,8,16
        IwlanError iwlanError = buildIwlanIkeAuthFailedError();
        long time = mErrorPolicyManager.reportIwlanError(apn1, iwlanError);
        assertEquals(4, time);

        iwlanError =
                buildIwlanIkeProtocolError(
                        8192 /*PDN_CONNECTION_REJECTION*/, new byte[] {0x00, 0x01});
        time = mErrorPolicyManager.reportIwlanError(apn2, iwlanError);
        assertEquals(5, time);

        int failCause = mErrorPolicyManager.getDataFailCause(apn1);
        assertEquals(DataFailCause.IWLAN_IKEV2_AUTH_FAILURE, failCause);

        failCause = mErrorPolicyManager.getDataFailCause(apn2);
        assertEquals(DataFailCause.IWLAN_PDN_CONNECTION_REJECTION, failCause);

        long retryTime =
                Math.round((double) mErrorPolicyManager.getRemainingRetryTimeMs(apn1) / 1000);
        assertEquals(4, retryTime);

        retryTime = Math.round((double) mErrorPolicyManager.getRemainingRetryTimeMs(apn2) / 1000);
        assertEquals(5, retryTime);
    }

    @Test
    public void testBackOffTime() throws Exception {
        String apn = "ims";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + ErrorPolicyString.builder()
                                .setErrorType("IKE_PROTOCOL_ERROR_TYPE")
                                .setErrorDetails(List.of("24", "34"))
                                .setRetryArray(List.of("10", "15", "20"))
                                .setUnthrottlingEvents(
                                        List.of("APM_ENABLE_EVENT", "WIFI_AP_CHANGED_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}, {"
                        + ErrorPolicyString.builder()
                                .setErrorType("GENERIC_ERROR_TYPE")
                                .setErrorDetails(List.of("SERVER_SELECTION_FAILED"))
                                .setRetryArray(List.of("0"))
                                .setUnthrottlingEvents(List.of("APM_ENABLE_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}]"
                        + "}]";

        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfig(bundle);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();
        mTestLooper.dispatchAll();

        // IKE_PROTOCOL_ERROR_TYPE(24) and retryArray = 4,8,16
        IwlanError iwlanError = buildIwlanIkeAuthFailedError();
        long time = mErrorPolicyManager.reportIwlanError(apn, iwlanError, 2);

        time = Math.round((double) mErrorPolicyManager.getRemainingRetryTimeMs(apn) / 1000);
        assertEquals(time, 2);

        // advanceClockByTimeMs for 2 seconds and make sure that we can bring up tunnel after 2 secs
        // as back off time - 2 secs should override the retry time in policy - 10 secs
        advanceClockByTimeMs(2000);
        boolean bringUpTunnel = mErrorPolicyManager.canBringUpTunnel(apn);
        assertTrue(bringUpTunnel);

        // test whether the same error reported later uses the right policy
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(10, time);

        bringUpTunnel = mErrorPolicyManager.canBringUpTunnel(apn);
        assertFalse(bringUpTunnel);

        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError, 5);
        time = Math.round((double) mErrorPolicyManager.getRemainingRetryTimeMs(apn) / 1000);
        assertEquals(time, 5);

        // test whether the same error reported later starts from the beginning of retry array
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(10, time);

        // retry time should be -1 when no error
        time = mErrorPolicyManager.reportIwlanError(apn, new IwlanError(IwlanError.NO_ERROR), 5);
        assertEquals(time, -1);
    }

    @Test
    public void testErrorPolicyWithNumAttemptsPerFqdn() throws Exception {
        // ErrorPolicyManager#getCurrentFqdnIndex() is tested when the ErrorType
        // parameter "NumAttemptsPerFqdn" is configured.
        String apn = "ims";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + ErrorPolicyString.builder()
                                .setErrorType("IKE_PROTOCOL_ERROR_TYPE")
                                .setErrorDetails(List.of("15500")) /* CONGESTION */
                                .setRetryArray(
                                        List.of(
                                                "0", "0", "300", "600", "1200", "0", "0", "0",
                                                "300", "600", "1200", "-1"))
                                .setUnthrottlingEvents(
                                        List.of(
                                                "APM_ENABLE_EVENT",
                                                "WIFI_DISABLE_EVENT",
                                                "WIFI_CALLING_DISABLE_EVENT"))
                                .setNumAttemptsPerFqdn("6")
                                .build()
                                .getErrorPolicyInString()
                        + "}]"
                        + "}]";

        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfig(bundle);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();
        mTestLooper.dispatchAll();
        assertEquals(DataFailCause.NONE, mErrorPolicyManager.getMostRecentDataFailCause());

        // IKE_PROTOCOL_ERROR_TYPE(15500)
        // UE constructs 2 PLMN FQDNs.
        IwlanError iwlanError = buildIwlanIkeProtocolError(15500 /* CONGESTION */);

        long time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(0, time);
        assertEquals(
                DataFailCause.IWLAN_CONGESTION, mErrorPolicyManager.getMostRecentDataFailCause());

        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(0, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(300, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(600, time);

        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(1200, time);
        assertEquals(0, mErrorPolicyManager.getCurrentFqdnIndex(2));

        // Cycles to next FQDN
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(0, time);
        assertEquals(1, mErrorPolicyManager.getCurrentFqdnIndex(2));

        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(0, time);
        assertEquals(1, mErrorPolicyManager.getCurrentFqdnIndex(2));

        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(0, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(300, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(600, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(1200, time);

        // Steady state retry duration, cycles back to 1st FQDN.
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(1200, time);
        assertEquals(0, mErrorPolicyManager.getCurrentFqdnIndex(2));
    }

    @Test
    public void testShouldRetryWithInitialAttach() throws Exception {
        String apn = "ims";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + ErrorPolicyString.builder()
                                .setErrorType("IKE_PROTOCOL_ERROR_TYPE")
                                .setErrorDetails(List.of("24", "34"))
                                .setRetryArray(List.of("4", "8", "16"))
                                .setUnthrottlingEvents(
                                        List.of("APM_ENABLE_EVENT", "WIFI_AP_CHANGED_EVENT"))
                                .setHandoverAttemptCount("2")
                                .build()
                                .getErrorPolicyInString()
                        + "}]"
                        + "}]";

        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfig(bundle);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();
        mTestLooper.dispatchAll();

        // IKE_PROTOCOL_ERROR_TYPE(24) and retryArray = 4,8,16
        IwlanError iwlanError = buildIwlanIkeAuthFailedError();
        long time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(4, time);
        assertFalse(mErrorPolicyManager.shouldRetryWithInitialAttach(apn));

        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(8, time);
        // Reached handover attempt count and error is IKE protocol error
        assertTrue(mErrorPolicyManager.shouldRetryWithInitialAttach(apn));
    }

    @Test
    public void testShouldRetryWithInitialAttachForInternalError() throws Exception {
        String apn = "ims";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + ErrorPolicyString.builder()
                                .setErrorType("IKE_PROTOCOL_ERROR_TYPE")
                                .setErrorDetails(List.of("24", "34"))
                                .setRetryArray(List.of("4", "8", "16"))
                                .setUnthrottlingEvents(
                                        List.of("APM_ENABLE_EVENT", "WIFI_AP_CHANGED_EVENT"))
                                .setHandoverAttemptCount("2")
                                .build()
                                .getErrorPolicyInString()
                        + "}, {"
                        + ErrorPolicyString.builder()
                                .setErrorType("GENERIC_ERROR_TYPE")
                                .setErrorDetails(List.of("SERVER_SELECTION_FAILED"))
                                .setRetryArray(List.of("0", "0"))
                                .setUnthrottlingEvents(List.of("APM_ENABLE_EVENT"))
                                .build()
                                .getErrorPolicyInString()
                        + "}]"
                        + "}]";

        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfig(bundle);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();
        mTestLooper.dispatchAll();

        // GENERIC_PROTOCOL_ERROR_TYPE - SERVER_SELECTION_FAILED and retryArray = 0, 0
        IwlanError iwlanError = new IwlanError(IwlanError.EPDG_SELECTOR_SERVER_SELECTION_FAILED);
        long time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(0, time);
        assertFalse(mErrorPolicyManager.shouldRetryWithInitialAttach(apn));

        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(0, time);
        // Should not retry with initial attach as the errors are not IKE_PROTOCOL_ERROR_TYPE
        assertFalse(mErrorPolicyManager.shouldRetryWithInitialAttach(apn));
    }

    @Test
    public void testHandoverAttemptCountInvalidErrorType() throws Exception {
        String apn = "ims";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + ErrorPolicyString.builder()
                                .setErrorType("GENERIC_ERROR_TYPE")
                                .setErrorDetails(List.of("*"))
                                .setRetryArray(List.of("4", "8", "16"))
                                .setUnthrottlingEvents(
                                        List.of("APM_ENABLE_EVENT", "WIFI_AP_CHANGED_EVENT"))
                                .setHandoverAttemptCount("2")
                                .build()
                                .getErrorPolicyInString()
                        + "}]"
                        + "}]";

        assertThrows(
                IllegalArgumentException.class,
                () -> mErrorPolicyManager.readErrorPolicies(new JSONArray(config)));
    }

    @Test
    public void testErrorStats() throws Exception {
        String apn1 = "ims";
        String apn2 = "mms";

        setupMockForCarrierConfig(null);

        IwlanError iwlanError1 = buildIwlanIkeAuthFailedError();
        long ikeAuthCountApn1 = 4L;
        long ikeAuthCountApn2 = 5L;
        for (int i = 0; i < ikeAuthCountApn1; i++) {
            mErrorPolicyManager.reportIwlanError(apn1, iwlanError1);
        }
        for (int i = 0; i < ikeAuthCountApn2; i++) {
            mErrorPolicyManager.reportIwlanError(apn2, iwlanError1);
        }

        long serverSelectionCountApn1 = 3L;
        long serverSelectionCountApn2 = 6L;
        IwlanError iwlanError2 = new IwlanError(IwlanError.EPDG_SELECTOR_SERVER_SELECTION_FAILED);
        for (int i = 0; i < serverSelectionCountApn1; i++) {
            mErrorPolicyManager.reportIwlanError(apn1, iwlanError2);
        }
        // calling backoff timer api
        for (int i = 0; i < serverSelectionCountApn2; i++) {
            mErrorPolicyManager.reportIwlanError(apn2, iwlanError2, 3);
        }

        Map<String, Long> apn1Stats = mErrorPolicyManager.getErrorStats().mStats.get(apn1);
        Map<String, Long> apn2Stats = mErrorPolicyManager.getErrorStats().mStats.get(apn2);

        long resultAuthApn1 = apn1Stats.get(iwlanError1.toString());
        long resultAuthApn2 = apn2Stats.get(iwlanError1.toString());
        long resultServerApn1 = apn1Stats.get(iwlanError2.toString());
        long resultServerApn2 = apn2Stats.get(iwlanError2.toString());
        assertEquals(resultAuthApn1, ikeAuthCountApn1);
        assertEquals(resultAuthApn2, ikeAuthCountApn2);
        assertEquals(resultServerApn1, serverSelectionCountApn1);
        assertEquals(resultServerApn2, serverSelectionCountApn2);
    }

    @Test
    public void testGetLastRetryIndex() {
        String apn = "ims";

        setupMockForCarrierConfig(null);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();
        mTestLooper.dispatchAll();

        // error count should be 0 when no error yet
        assertEquals(0, mErrorPolicyManager.getLastErrorCountOfSameCause(apn));

        // same error reported, should accumulate
        IwlanError iwlanAuthError = buildIwlanIkeAuthFailedError();
        mErrorPolicyManager.reportIwlanError(apn, iwlanAuthError);
        assertEquals(1, mErrorPolicyManager.getLastErrorCountOfSameCause(apn));
        mErrorPolicyManager.reportIwlanError(apn, iwlanAuthError);
        assertEquals(2, mErrorPolicyManager.getLastErrorCountOfSameCause(apn));
        mErrorPolicyManager.reportIwlanError(apn, iwlanAuthError);
        assertEquals(3, mErrorPolicyManager.getLastErrorCountOfSameCause(apn));

        // different error reported, should start with 1 then accumulate
        IwlanError iwlanOtherProtocolError = buildIwlanIkeProtocolError(9002);
        mErrorPolicyManager.reportIwlanError(apn, iwlanOtherProtocolError);
        assertEquals(1, mErrorPolicyManager.getLastErrorCountOfSameCause(apn));
        mErrorPolicyManager.reportIwlanError(apn, iwlanOtherProtocolError);
        assertEquals(2, mErrorPolicyManager.getLastErrorCountOfSameCause(apn));

        // different error with specific backoff time reported, should start with 1 then accumulate
        IwlanError iwlanInternalAddressFailure = buildIwlanIkeInternalAddressFailure();
        mErrorPolicyManager.reportIwlanError(apn, iwlanInternalAddressFailure, 5);
        assertEquals(1, mErrorPolicyManager.getLastErrorCountOfSameCause(apn));
        mErrorPolicyManager.reportIwlanError(apn, iwlanInternalAddressFailure, 5);
        assertEquals(2, mErrorPolicyManager.getLastErrorCountOfSameCause(apn));

        // same error with specific backoff time, should continue from last error count (2)
        mErrorPolicyManager.reportIwlanError(apn, iwlanOtherProtocolError, 5);
        assertEquals(3, mErrorPolicyManager.getLastErrorCountOfSameCause(apn));
        // error without backoff time again, should continue from last error count (3)
        mErrorPolicyManager.reportIwlanError(apn, iwlanOtherProtocolError);
        assertEquals(4, mErrorPolicyManager.getLastErrorCountOfSameCause(apn));

        // the retry index from iwlan auth error should be reserved
        mErrorPolicyManager.reportIwlanError(apn, iwlanAuthError);
        assertEquals(4, mErrorPolicyManager.getLastErrorCountOfSameCause(apn));

        // Unthrottle event should reset the error count
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.APM_ENABLE_EVENT)
                .sendToTarget();
        mTestLooper.dispatchAll();

        assertEquals(0, mErrorPolicyManager.getLastErrorCountOfSameCause(apn));

        mErrorPolicyManager.reportIwlanError(apn, iwlanAuthError);
        assertEquals(1, mErrorPolicyManager.getLastErrorCountOfSameCause(apn));

        mErrorPolicyManager.reportIwlanError(apn, iwlanOtherProtocolError, 5);
        assertEquals(1, mErrorPolicyManager.getLastErrorCountOfSameCause(apn));

        mErrorPolicyManager.reportIwlanError(apn, new IwlanError(IwlanError.NO_ERROR));
        // NO_ERROR should reset the error count
        assertEquals(0, mErrorPolicyManager.getLastErrorCountOfSameCause(apn));

        mErrorPolicyManager.reportIwlanError(apn, iwlanAuthError);
        assertEquals(1, mErrorPolicyManager.getLastErrorCountOfSameCause(apn));

        mErrorPolicyManager.reportIwlanError(apn, iwlanOtherProtocolError, 5);
        assertEquals(1, mErrorPolicyManager.getLastErrorCountOfSameCause(apn));
    }

    private void advanceClockByTimeMs(long time) {
        mMockedClockTime += time;
        mTestLooper.dispatchAll();
    }

    @Test
    public void testShouldNotThrowWhenDefaultConfigInvalid() throws Exception {
        String apn = "ims";
        AssetManager mockAssetManager = mock(AssetManager.class);
        doReturn(mockAssetManager).when(mMockContext).getAssets();

        // when the default config do not match all error with "ErrorType": "*"
        String defaultConfigErrorTypeJson =
                "{\"ErrorType\": \"IKE_PROTOCOL_ERROR_TYPE\", \"ErrorDetails\": [\"*\"], "
                        + "\"RetryArray\": [\"0\"], \"UnthrottlingEvents\": []}";
        String defaultConfigJson =
                "[{\"ApnName\": \"*\", \"ErrorTypes\": [" + defaultConfigErrorTypeJson + "]}]";
        InputStream defaultConfigInputStream =
                new ByteArrayInputStream(defaultConfigJson.getBytes(StandardCharsets.UTF_8));
        doReturn(defaultConfigInputStream).when(mockAssetManager).open(any());
        PersistableBundle bundle = new PersistableBundle();

        // need to reconstruct error policy manager with the mocked default config
        mErrorPolicyManager.releaseInstance();
        mErrorPolicyManager = spy(ErrorPolicyManager.getInstance(mMockContext, DEFAULT_SLOT_INDEX));
        doReturn(mTestLooper.getLooper()).when(mErrorPolicyManager).getLooper();
        mErrorPolicyManager.initHandler();

        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, null);
        setupMockForCarrierConfig(bundle);

        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();
        mTestLooper.dispatchAll();

        // if some error happen and no policy in carrier config match, and the default config do
        // not have at least 1 policy can apply to all error, it should not throw error
        IwlanError iwlanError = new IwlanError(IwlanError.EPDG_SELECTOR_SERVER_SELECTION_FAILED);
        mErrorPolicyManager.reportIwlanError(apn, iwlanError);
    }

    private void setupMockForCarrierConfig(PersistableBundle bundle) {
        setupMockForCarrierConfigWithCarrierId(bundle, TEST_CARRIER_ID);
    }

    private void setupMockForCarrierConfigWithCarrierId(PersistableBundle bundle, int carrierId) {
        doReturn(mMockCarrierConfigManager)
                .when(mMockContext)
                .getSystemService(eq(CarrierConfigManager.class));
        doReturn(mMockSubscriptionManager)
                .when(mMockContext)
                .getSystemService(eq(SubscriptionManager.class));
        doReturn(mMockTelephonyManager).when(mMockContext).getSystemService(TelephonyManager.class);
        doReturn(mMockTelephonyManager)
                .when(mMockTelephonyManager)
                .createForSubscriptionId(anyInt());
        doReturn(carrierId).when(mMockTelephonyManager).getSimCarrierId();
        SubscriptionInfo mockSubInfo = mock(SubscriptionInfo.class);
        doReturn(mockSubInfo)
                .when(mMockSubscriptionManager)
                .getActiveSubscriptionInfoForSimSlotIndex(DEFAULT_SLOT_INDEX);
        doReturn(DEFAULT_SUBID).when(mockSubInfo).getSubscriptionId();
        doReturn(bundle).when(mMockCarrierConfigManager).getConfigForSubId(DEFAULT_SLOT_INDEX);
        when(mMockContext.getContentResolver()).thenReturn(mMockContentResolver);
    }
}
