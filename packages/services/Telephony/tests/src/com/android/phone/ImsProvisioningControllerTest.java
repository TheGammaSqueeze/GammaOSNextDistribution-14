/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.phone;

import static android.telephony.ims.ImsRcsManager.CAPABILITY_TYPE_PRESENCE_UCE;
import static android.telephony.ims.ProvisioningManager.KEY_EAB_PROVISIONING_STATUS;
import static android.telephony.ims.ProvisioningManager.KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE;
import static android.telephony.ims.ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS;
import static android.telephony.ims.ProvisioningManager.KEY_VT_PROVISIONING_STATUS;
import static android.telephony.ims.ProvisioningManager.PROVISIONING_VALUE_DISABLED;
import static android.telephony.ims.ProvisioningManager.PROVISIONING_VALUE_ENABLED;
import static android.telephony.ims.feature.ImsFeature.FEATURE_MMTEL;
import static android.telephony.ims.feature.ImsFeature.FEATURE_RCS;
import static android.telephony.ims.feature.MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_CALL_COMPOSER;
import static android.telephony.ims.feature.MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_SMS;
import static android.telephony.ims.feature.MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_UT;
import static android.telephony.ims.feature.MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VIDEO;
import static android.telephony.ims.feature.MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_CROSS_SIM;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_LTE;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_NR;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyRegistryManager;
import android.telephony.ims.ProvisioningManager;
import android.telephony.ims.aidl.IFeatureProvisioningCallback;
import android.telephony.ims.aidl.IImsConfig;
import android.telephony.ims.aidl.IImsConfigCallback;
import android.telephony.ims.feature.MmTelFeature.MmTelCapabilities;
import android.telephony.ims.feature.RcsFeature.RcsImsCapabilities;
import android.telephony.ims.stub.ImsConfigImplBase;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.testing.TestableLooper;
import android.util.Log;

import androidx.test.filters.SmallTest;

import com.android.ims.FeatureConnector;
import com.android.ims.ImsConfig;
import com.android.ims.ImsManager;
import com.android.ims.RcsFeatureManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Unit tests for ImsProvisioningContorller
 */
public class ImsProvisioningControllerTest {
    private static final String TAG = "ImsProvisioningControllerTest";
    private static final int[] MMTEL_CAPAS = new int[]{
            CAPABILITY_TYPE_VOICE,
            CAPABILITY_TYPE_VIDEO,
            CAPABILITY_TYPE_UT,
            CAPABILITY_TYPE_SMS,
            CAPABILITY_TYPE_CALL_COMPOSER
    };
    private static final int MMTEL_CAPA_INVALID = 0;

    private static final int RCS_CAPA_INVALID = RcsImsCapabilities.CAPABILITY_TYPE_NONE;

    private static final int[] RADIO_TECHS = new int[]{
            REGISTRATION_TECH_LTE,
            REGISTRATION_TECH_IWLAN,
            REGISTRATION_TECH_CROSS_SIM,
            REGISTRATION_TECH_NR
    };
    private static final int RADIO_TECH_INVALID = ImsRegistrationImplBase.REGISTRATION_TECH_NONE;

    @Mock
    Context mContext;

    @Mock
    PhoneGlobals mPhone;
    @Mock
    CarrierConfigManager mCarrierConfigManager;
    private PersistableBundle mPersistableBundle0;
    private PersistableBundle mPersistableBundle1;
    @Mock
    SubscriptionManager mSubscriptionManager;
    @Mock
    TelephonyRegistryManager mTelephonyRegistryManager;
    @Mock
    ImsProvisioningLoader mImsProvisioningLoader;

    @Mock
    ImsManager mImsManager;
    @Mock
    ImsConfig mImsConfig;
    @Mock
    ImsProvisioningController.MmTelFeatureConnector mMmTelFeatureConnector;
    @Mock
    FeatureConnector<ImsManager> mMmTelFeatureConnector0;
    @Mock
    FeatureConnector<ImsManager> mMmTelFeatureConnector1;
    @Captor
    ArgumentCaptor<FeatureConnector.Listener<ImsManager>> mMmTelConnectorListener0;
    @Captor
    ArgumentCaptor<FeatureConnector.Listener<ImsManager>> mMmTelConnectorListener1;

    @Mock
    RcsFeatureManager mRcsFeatureManager;
    @Mock
    ImsProvisioningController.RcsFeatureConnector mRcsFeatureConnector;
    @Mock
    FeatureConnector<RcsFeatureManager> mRcsFeatureConnector0;
    @Mock
    FeatureConnector<RcsFeatureManager> mRcsFeatureConnector1;
    @Captor
    ArgumentCaptor<FeatureConnector.Listener<RcsFeatureManager>> mRcsConnectorListener0;
    @Captor
    ArgumentCaptor<FeatureConnector.Listener<RcsFeatureManager>> mRcsConnectorListener1;

    @Mock
    IFeatureProvisioningCallback mIFeatureProvisioningCallback0;
    @Mock
    IFeatureProvisioningCallback mIFeatureProvisioningCallback1;

    @Captor
    ArgumentCaptor<IImsConfigCallback> mIImsConfigCallback;

    @Mock
    IBinder mIbinder0;
    @Mock
    IBinder mIbinder1;

    private SubscriptionManager.OnSubscriptionsChangedListener mSubChangedListener;

    private Handler mHandler;
    private HandlerThread mHandlerThread;
    private TestableLooper mLooper;

    TestImsProvisioningController mTestImsProvisioningController;

    int mPhoneId0 = 0;
    int mPhoneId1 = 1;
    int mSubId0 = 1234;
    int mSubId1 = 5678;

    int[][] mMmTelProvisioningStorage;
    int[][] mRcsProvisioningStorage;
    int[][] mImsConfigStorage;

    private class TestImsProvisioningController extends ImsProvisioningController {
        boolean mIsValidSubId = true;

        TestImsProvisioningController() {
            super(mPhone, 2, mHandlerThread.getLooper(),
                    mMmTelFeatureConnector, mRcsFeatureConnector,
                    mImsProvisioningLoader);
        }

        protected int getSubId(int slotId) {
            return (slotId == mPhoneId0) ? mSubId0 : mSubId1;
        }

        protected int getSlotId(int subId) {
            return (subId == mSubId0) ? mPhoneId0 : mPhoneId1;
        }

        protected ImsConfig getImsConfig(ImsManager imsManager) {
            return mImsConfig;
        }

        protected ImsConfig getImsConfig(IImsConfig iImsConfig) {
            return mImsConfig;
        }

        protected boolean isValidSubId(int subId) {
            return mIsValidSubId;
        }

        public void setValidSubId(boolean valid) {
            mIsValidSubId = valid;
        }
    }

    @Before
    public void setUp() throws Exception {
        logd("setUp");
        MockitoAnnotations.initMocks(this);

        when(mPhone.getSystemServiceName(eq(CarrierConfigManager.class)))
                .thenReturn(Context.CARRIER_CONFIG_SERVICE);
        when(mPhone.getSystemService(eq(Context.CARRIER_CONFIG_SERVICE)))
                .thenReturn(mCarrierConfigManager);

        mPersistableBundle0 = new PersistableBundle();
        mPersistableBundle1 = new PersistableBundle();

        when(mCarrierConfigManager.getConfigForSubId(eq(mSubId0)))
                .thenReturn(mPersistableBundle0);
        when(mCarrierConfigManager.getConfigForSubId(eq(mSubId1)))
                .thenReturn(mPersistableBundle1);

        when(mPhone.getSystemServiceName(eq(SubscriptionManager.class)))
                .thenReturn(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        when(mPhone.getSystemService(eq(Context.TELEPHONY_SUBSCRIPTION_SERVICE)))
                .thenReturn(mSubscriptionManager);


        when(mPhone.getSystemServiceName(eq(TelephonyRegistryManager.class)))
                .thenReturn(Context.TELEPHONY_REGISTRY_SERVICE);
        when(mPhone.getSystemService(eq(Context.TELEPHONY_REGISTRY_SERVICE)))
                .thenReturn(mTelephonyRegistryManager);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                mSubChangedListener = (SubscriptionManager.OnSubscriptionsChangedListener)
                        invocation.getArguments()[0];
                return null;
            }
        }).when(mTelephonyRegistryManager).addOnSubscriptionsChangedListener(
                any(SubscriptionManager.OnSubscriptionsChangedListener.class),
                any());

        mHandlerThread = new HandlerThread("ImsStateCallbackControllerTest");
        mHandlerThread.start();

        initializeDefaultData();
    }

    @After
    public void tearDown() throws Exception {
        logd("tearDown");
        if (mTestImsProvisioningController != null) {
            mTestImsProvisioningController.destroy();
            mTestImsProvisioningController = null;
        }

        if (mLooper != null) {
            mLooper.destroy();
            mLooper = null;
        }
    }

    @Test
    @SmallTest
    public void addFeatureProvisioningChangedCallback_withCallback() throws Exception {
        createImsProvisioningController();

        // add callback with valid obj
        try {
            mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                    mSubId0, mIFeatureProvisioningCallback0);
        } catch (Exception e) {
            throw new AssertionError("not expected exception", e);
        }
        // add callback with invalid obj
        try {
            mTestImsProvisioningController.addFeatureProvisioningChangedCallback(mSubId0, null);
        } catch (IllegalArgumentException e) {
            // expected result
        } catch (Exception e) {
            throw new AssertionError("not expected exception", e);
        }
        // remove callback with valid obj
        try {
            mTestImsProvisioningController.removeFeatureProvisioningChangedCallback(
                    mSubId0, mIFeatureProvisioningCallback0);
        } catch (Exception e) {
            throw new AssertionError("not expected exception", e);
        }
        // remove callback with invalid obj
        try {
            mTestImsProvisioningController.removeFeatureProvisioningChangedCallback(mSubId0, null);
        } catch (IllegalArgumentException e) {
            // expected result
        } catch (Exception e) {
            throw new AssertionError("not expected exception", e);
        }
    }

    @Test
    @SmallTest
    public void connectionReady_MmTelFeatureListener() throws Exception {
        createImsProvisioningController();

        // provisioning required capability
        // voice, all tech
        // video, all tech
        setCarrierConfig(mSubId0, CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VOICE_INT_ARRAY,
                RADIO_TECHS);
        setCarrierConfig(mSubId0, CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VIDEO_INT_ARRAY,
                RADIO_TECHS);

        try {
            mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                    mSubId0, mIFeatureProvisioningCallback0);
            mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                    mSubId1, mIFeatureProvisioningCallback1);
        } catch (Exception e) {
            throw new AssertionError("not expected exception", e);
        }
        clearInvocations(mIFeatureProvisioningCallback0);
        clearInvocations(mIFeatureProvisioningCallback1);

        // change subId to be invalid
        mTestImsProvisioningController.setValidSubId(false);

        mMmTelConnectorListener0.getValue().connectionReady(mImsManager, mSubId0);

        // setConfig not called, wait until subId is valid
        verify(mImsConfig, times(0)).setConfig(anyInt(), anyInt());

        // change subId
        mSubChangedListener.onSubscriptionsChanged();
        processAllMessages();

        verify(mImsConfig, times(1)).addConfigCallback((IImsConfigCallback) any());

        int[] keys = {
                ProvisioningManager.KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE,
                ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS,
                ProvisioningManager.KEY_VT_PROVISIONING_STATUS};

        // verify # of read data times from storage : # of MmTel storage length
        verify(mImsProvisioningLoader, atLeast(keys.length))
                .getProvisioningStatus(eq(mSubId0), eq(FEATURE_MMTEL), anyInt(), anyInt());

        for (int index = 0; index < keys.length; index++) {
            // verify function call vendor interface
            verify(mImsConfig, times(1)).setConfig(eq(keys[index]), anyInt());
        }

        // verify other interactions
        verifyNoMoreInteractions(mIFeatureProvisioningCallback0);
        verifyNoMoreInteractions(mIFeatureProvisioningCallback1);
        verifyNoMoreInteractions(mImsConfig);
    }

    @Test
    @SmallTest
    public void connectionReady_RcsFeatureListener() throws Exception {
        createImsProvisioningController();

        // provisioning required capability : PRESENCE, tech : all
        setCarrierConfig(mSubId0,
                CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_PRESENCE_UCE_INT_ARRAY, RADIO_TECHS);

        try {
            mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                    mSubId0, mIFeatureProvisioningCallback0);
            mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                    mSubId1, mIFeatureProvisioningCallback1);
        } catch (Exception e) {
            throw new AssertionError("not expected exception", e);
        }
        clearInvocations(mIFeatureProvisioningCallback0);
        clearInvocations(mIFeatureProvisioningCallback1);

        mRcsConnectorListener0.getValue().connectionReady(mRcsFeatureManager, mSubId0);
        processAllMessages();

        verify(mImsConfig, times(1)).addConfigCallback((IImsConfigCallback) any());

        // verify # of read data times from storage : # of Rcs storage length
        verify(mImsProvisioningLoader, times(1))
                .getProvisioningStatus(eq(mSubId0), eq(FEATURE_RCS), anyInt(), anyInt());

        int key = ProvisioningManager.KEY_EAB_PROVISIONING_STATUS;
        // verify function call vendor interface
        verify(mImsConfig, times(1)).setConfig(eq(key), anyInt());

        // verify other interactions
        verifyNoMoreInteractions(mIFeatureProvisioningCallback0);
        verifyNoMoreInteractions(mIFeatureProvisioningCallback1);
        verifyNoMoreInteractions(mImsConfig);
    }

    @Test
    @SmallTest
    public void isImsProvisioningRequiredForCapability_withInvalidCapabilityTech()
            throws Exception {
        createImsProvisioningController();

        // invalid Capa. and valid Radio tech - IllegalArgumentException OK
        try {
            mTestImsProvisioningController.isImsProvisioningRequiredForCapability(
                    mSubId0, MMTEL_CAPA_INVALID, RADIO_TECHS[0]);
        } catch (IllegalArgumentException e) {
            // expected result
        } catch (Exception e) {
            throw new AssertionError("not expected exception", e);
        }

        // valid Capa. and invalid Radio tech - IllegalArumentException OK
        try {
            mTestImsProvisioningController.isImsProvisioningRequiredForCapability(
                    mSubId0, MMTEL_CAPAS[0], RADIO_TECH_INVALID);
        } catch (IllegalArgumentException e) {
            // expected result
        } catch (Exception e) {
            throw new AssertionError("not expected exception", e);
        }
    }

    @Test
    @SmallTest
    public void isImsProvisioningRequiredForCapability_withValidCapabilityTech() throws Exception {
        createImsProvisioningController();

        // provisioning required capability
        // voice, all tech
        // video, all tech
        // UT, all tech
        setCarrierConfig(mSubId0, CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VOICE_INT_ARRAY,
                RADIO_TECHS);
        setCarrierConfig(mSubId0, CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VIDEO_INT_ARRAY,
                RADIO_TECHS);
        setCarrierConfig(mSubId0, CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_UT_INT_ARRAY,
                RADIO_TECHS);

        // provisioning required for each capability
        boolean[][] expectedRequired = new boolean[][] {
                //voice - LTE, WLAN, CROSS-SIM, NR
                {true, true, true, true},
                //video - LTE, WLAN, CROSS-SIM, NR
                {true, true, true, true},
                //UT - LTE, WLAN, CROSS-SIM, NR
                {true, true, true, true},
                //SMS not required
                {false, false, false, false},
                //Call composer not required
                {false, false, false, false}
        };

        boolean isRequired;
        for (int i = 0; i < MMTEL_CAPAS.length; i++) {
            for (int j = 0; j < RADIO_TECHS.length; j++) {
                isRequired = mTestImsProvisioningController
                .isImsProvisioningRequiredForCapability(
                        mSubId0, MMTEL_CAPAS[i], RADIO_TECHS[j]);
                assertEquals(expectedRequired[i][j], isRequired);
            }
        }
    }

    @Test
    @SmallTest
    public void isImsProvisioningRequiredForCapability_withDeprecatedKey() throws Exception {
        createImsProvisioningController();

        // provisioning required capability
        // KEY_MMTEL_REQUIRES_PROVISIONING_BUNDLE is not defined
        // but KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL and
        // KEY_CARRIER_SUPPORTS_SS_OVER_UT_BOOL are defined
        setDeprecatedCarrierConfig(
                CarrierConfigManager.KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL, true);
        setDeprecatedCarrierConfig(
                CarrierConfigManager.KEY_CARRIER_UT_PROVISIONING_REQUIRED_BOOL, true);

        // provisioning required for each capability
        boolean[][] expectedRequired = new boolean[][] {
                //voice - LTE, WLAN, CROSS-SIM, NR
                {true, true, true, true},
                //video - LTE, WLAN, CROSS-SIM, NR
                {true, true, true, true},
                //UT - LTE, WLAN, CROSS-SIM, NR
                {true, true, true, true},
                //SMS not required
                {false, false, false, false},
                //Call composer not required
                {false, false, false, false}
        };

        boolean isRequired;
        for (int i = 0; i < MMTEL_CAPAS.length; i++) {
            for (int j = 0; j < RADIO_TECHS.length; j++) {
                isRequired = mTestImsProvisioningController
                        .isImsProvisioningRequiredForCapability(
                                mSubId0, MMTEL_CAPAS[i], RADIO_TECHS[j]);
                assertEquals(expectedRequired[i][j], isRequired);
            }
        }
    }

    @Test
    @SmallTest
    public void isRcsProvisioningRequiredForCapability_withInvalidCapabilityTech()
            throws Exception {
        createImsProvisioningController();

        // invalid Capa. and valid Radio tech - IllegalArgumentException OK
        try {
            mTestImsProvisioningController.isRcsProvisioningRequiredForCapability(
                    mSubId0, RCS_CAPA_INVALID, RADIO_TECHS[0]);
        } catch (IllegalArgumentException e) {
            // expected result
        } catch (Exception e) {
            throw new AssertionError("not expected exception", e);
        }

        // valid Capa. and invalid Radio tech - IllegalArumentException OK
        try {
            mTestImsProvisioningController.isRcsProvisioningRequiredForCapability(
                    mSubId0, CAPABILITY_TYPE_PRESENCE_UCE, RADIO_TECH_INVALID);
        } catch (IllegalArgumentException e) {
            // expected result
        } catch (Exception e) {
            throw new AssertionError("not expected exception", e);
        }
    }

    @Test
    @SmallTest
    public void isRcsProvisioningRequiredForCapability_withValidCapabilityTech() throws Exception {
        createImsProvisioningController();

        // provisioning required capability : PRESENCE, tech : all
        setCarrierConfig(mSubId0,
                CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_PRESENCE_UCE_INT_ARRAY, RADIO_TECHS);

        // PRESENCE provisioning required on
        boolean[] expectedRequired = new boolean[]
                //LTE, WLAN, CROSS-SIM, NR
                {true, true, true, true};

        boolean isRequired;
        for (int i = 0; i < RADIO_TECHS.length; i++) {
            isRequired = mTestImsProvisioningController
            .isRcsProvisioningRequiredForCapability(
                    mSubId0, CAPABILITY_TYPE_PRESENCE_UCE, RADIO_TECHS[i]);
            assertEquals(expectedRequired[i], isRequired);
        }
    }

    @Test
    @SmallTest
    public void isRcsProvisioningRequiredForCapability_withDeprecatedKey() throws Exception {
        createImsProvisioningController();

        // provisioning required capability
        // KEY_MMTEL_REQUIRES_PROVISIONING_BUNDLE is not defined
        // but KEY_CARRIER_RCS_PROVISIONING_REQUIRED_BOOL is defined
        setDeprecatedCarrierConfig(
                CarrierConfigManager.KEY_CARRIER_RCS_PROVISIONING_REQUIRED_BOOL, true);

        // PRESENCE provisioning required on
        boolean[] expectedRequired = new boolean[]
                //LTE, WLAN, CROSS-SIM, NR
                {true, true, true, true};

        boolean isRequired;
        for (int i = 0; i < RADIO_TECHS.length; i++) {
            isRequired = mTestImsProvisioningController
                    .isRcsProvisioningRequiredForCapability(
                            mSubId0, CAPABILITY_TYPE_PRESENCE_UCE, RADIO_TECHS[i]);
            assertEquals(expectedRequired[i], isRequired);
        }
    }

    @Test
    @SmallTest
    public void getImsProvisioningRequiredForCapability_withVoiceVideoUt() throws Exception {
        createImsProvisioningController();

        // provisioning required capability
        // voice, all tech
        // video, all tech
        // UT, all tech
        setCarrierConfig(mSubId0, CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VOICE_INT_ARRAY,
                RADIO_TECHS);
        setCarrierConfig(mSubId0, CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VIDEO_INT_ARRAY,
                RADIO_TECHS);
        setCarrierConfig(mSubId0, CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_UT_INT_ARRAY,
                RADIO_TECHS);

        // provisioning Status
        mMmTelProvisioningStorage = new int[][] {
                {CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_LTE, 0},
                {CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_IWLAN, 1},
                {CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_CROSS_SIM, 1},
                {CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_NR, 1},
                {CAPABILITY_TYPE_VIDEO, REGISTRATION_TECH_LTE, 0},
                {CAPABILITY_TYPE_VIDEO, REGISTRATION_TECH_IWLAN, 0},
                {CAPABILITY_TYPE_VIDEO, REGISTRATION_TECH_CROSS_SIM, 1},
                {CAPABILITY_TYPE_VIDEO, REGISTRATION_TECH_NR, 1},
                {CAPABILITY_TYPE_UT, REGISTRATION_TECH_LTE, 0},
                {CAPABILITY_TYPE_UT, REGISTRATION_TECH_IWLAN, 0},
                {CAPABILITY_TYPE_UT, REGISTRATION_TECH_CROSS_SIM, 0},
                {CAPABILITY_TYPE_UT, REGISTRATION_TECH_NR, 0},
        };

        boolean[] expectedVoiceProvisioningStatus = new boolean[] {false, true, true, true};
        boolean[] expectedVideoProvisioningStatus = new boolean[] {false, false, true, true};
        boolean[] expectedUtProvisioningStatus = new boolean[] {false, false, false, false};

        boolean provisioned = false;
        int capability = CAPABILITY_TYPE_VOICE;
        for (int i = 0; i < RADIO_TECHS.length; i++) {
            // get provisioning status
            provisioned = mTestImsProvisioningController
                    .getImsProvisioningStatusForCapability(mSubId0, capability, RADIO_TECHS[i]);

            // verify return value
            assertEquals(expectedVoiceProvisioningStatus[i], provisioned);

            // verify whether ImsProvisioningLoader is called or not
            verify(mImsProvisioningLoader, times(1))
                    .getProvisioningStatus(eq(mSubId0), eq(FEATURE_MMTEL), eq(capability),
                            eq(RADIO_TECHS[i]));
        }

        capability = CAPABILITY_TYPE_VIDEO;
        for (int i = 0; i < RADIO_TECHS.length; i++) {
            // get provisioning status
            provisioned = mTestImsProvisioningController
                    .getImsProvisioningStatusForCapability(mSubId0, capability, RADIO_TECHS[i]);

            // verify return value
            assertEquals(expectedVideoProvisioningStatus[i], provisioned);

            // verify whether ImsProvisioningLoader is called or not
            verify(mImsProvisioningLoader, times(1))
                    .getProvisioningStatus(eq(mSubId0), eq(FEATURE_MMTEL), eq(capability),
                            eq(RADIO_TECHS[i]));
        }

        capability = CAPABILITY_TYPE_UT;
        for (int i = 0; i < RADIO_TECHS.length; i++) {
            // get provisioning status
            provisioned = mTestImsProvisioningController
                    .getImsProvisioningStatusForCapability(mSubId0, capability, RADIO_TECHS[i]);

            // verify return value
            assertEquals(expectedUtProvisioningStatus[i], provisioned);

            // verify whether ImsProvisioningLoader is called or not
            verify(mImsProvisioningLoader, times(1))
                    .getProvisioningStatus(eq(mSubId0), eq(FEATURE_MMTEL), eq(capability),
                            eq(RADIO_TECHS[i]));
        }

        verifyNoMoreInteractions(mImsProvisioningLoader);
    }

    @Test
    @SmallTest
    public void getImsProvisioningRequiredForCapability_withNotSet() throws Exception {
        createImsProvisioningController();

        mMmTelConnectorListener0.getValue().connectionReady(mImsManager, mSubId0);
        processAllMessages();

        clearInvocations(mImsConfig);
        clearInvocations(mImsProvisioningLoader);

        // provisioning required capability
        // voice, LTE, IWLAN
        // video, LTE
        setCarrierConfig(mSubId0, CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VOICE_INT_ARRAY,
                REGISTRATION_TECH_LTE, REGISTRATION_TECH_IWLAN);
        setCarrierConfig(mSubId0, CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VIDEO_INT_ARRAY,
                REGISTRATION_TECH_LTE);

        // provisioning StatusP, all of provisioning status is not provisioned
        mMmTelProvisioningStorage = new int[][]{
                {CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_LTE, -1},
                {CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_IWLAN, -1},
                {CAPABILITY_TYPE_VIDEO, REGISTRATION_TECH_LTE, -1}
        };
        // provisioning status in vendor ImsService
        mImsConfigStorage = new int[][] {
                {KEY_VOLTE_PROVISIONING_STATUS, 1},
                {KEY_VT_PROVISIONING_STATUS, 0},
                {KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE, 1},
        };

        boolean provisioned;

        // for KEY_VOLTE_PROVISIONING_STATUS
        int capability = CAPABILITY_TYPE_VOICE;
        int tech = REGISTRATION_TECH_LTE;
        provisioned = mTestImsProvisioningController
                .getImsProvisioningStatusForCapability(mSubId0, capability, tech);

        // verify return value default false - not provisioned
        assertEquals(true, provisioned);

        verify(mImsProvisioningLoader, times(1))
                .getProvisioningStatus(eq(mSubId0), eq(FEATURE_MMTEL), eq(capability), eq(tech));

        // verify whether ImsProvisioningLoader is called or not
        verify(mImsConfig, times(1)).getConfigInt(eq(KEY_VOLTE_PROVISIONING_STATUS));

        // verify whether ImsProvisioningLoader is called or not
        verify(mImsProvisioningLoader, times(1))
                .setProvisioningStatus(eq(mSubId0), eq(FEATURE_MMTEL), eq(capability), eq(tech),
                        eq(provisioned));

        clearInvocations(mImsConfig);
        clearInvocations(mImsProvisioningLoader);

        // for KEY_VT_PROVISIONING_STATUS
        capability = CAPABILITY_TYPE_VIDEO;
        tech = REGISTRATION_TECH_LTE;
        provisioned = mTestImsProvisioningController
                .getImsProvisioningStatusForCapability(mSubId0, capability, tech);

        // verify return value default false - not provisioned
        assertEquals(false, provisioned);

        verify(mImsProvisioningLoader, times(1))
                .getProvisioningStatus(eq(mSubId0), eq(FEATURE_MMTEL), eq(capability), eq(tech));

        // verify whether ImsProvisioningLoader is called or not
        verify(mImsConfig, times(1)).getConfigInt(eq(KEY_VT_PROVISIONING_STATUS));

        // verify whether ImsProvisioningLoader is called or not
        verify(mImsProvisioningLoader, times(1))
                .setProvisioningStatus(eq(mSubId0), eq(FEATURE_MMTEL), eq(capability), eq(tech),
                        eq(provisioned));

        verifyNoMoreInteractions(mImsConfig);
        verifyNoMoreInteractions(mImsProvisioningLoader);
    }

    @Test
    @SmallTest
    public void getRcsProvisioningRequiredForCapability_withPresence() throws Exception {
        createImsProvisioningController();

        // provisioning required capability
        // PRESENCE, all tech
        setCarrierConfig(mSubId0,
                CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_PRESENCE_UCE_INT_ARRAY, RADIO_TECHS);

        // provisioning Status
        mRcsProvisioningStorage = new int[][] {
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_LTE, 1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_IWLAN, 1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_CROSS_SIM, 1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_NR, 1}
        };

        boolean[] expectedPresenceProvisioningStatus = new boolean[] {true, true, true, true};

        boolean provisioned = false;
        int capability = CAPABILITY_TYPE_PRESENCE_UCE;
        for (int i = 0; i < RADIO_TECHS.length; i++) {
            // get provisioning status
            provisioned = mTestImsProvisioningController
                    .getRcsProvisioningStatusForCapability(mSubId0, capability, RADIO_TECHS[i]);

            // verify return value
            assertEquals(expectedPresenceProvisioningStatus[i], provisioned);

            // verify whether ImsProvisioningLoader is called or not
            verify(mImsProvisioningLoader, times(1)).getProvisioningStatus(
                    eq(mSubId0), eq(FEATURE_RCS), eq(capability), eq(RADIO_TECHS[i]));
        }

        verifyNoMoreInteractions(mImsProvisioningLoader);
    }

    @Test
    @SmallTest
    public void getRcsProvisioningRequiredForCapability_withNotSet() throws Exception {
        createImsProvisioningController();

        mRcsConnectorListener0.getValue().connectionReady(mRcsFeatureManager, mSubId0);
        processAllMessages();

        clearInvocations(mImsConfig);
        clearInvocations(mImsProvisioningLoader);

        // provisioning required capability
        // PRESENCE, LTE, IWLAN
        setCarrierConfig(mSubId0,
                CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_PRESENCE_UCE_INT_ARRAY,
                REGISTRATION_TECH_LTE, REGISTRATION_TECH_IWLAN);

        // provisioning Status, all of provisioning status is not provisioned
        mRcsProvisioningStorage = new int[][]{
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_LTE, -1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_IWLAN, -1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_CROSS_SIM, -1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_NR, -1}
        };
        // provisioning status in vendor ImsService
        mImsConfigStorage = new int[][] {
                {KEY_EAB_PROVISIONING_STATUS, 1}
        };

        boolean provisioned;

        // for KEY_EAB_PROVISIONING_STATUS
        int capability = CAPABILITY_TYPE_PRESENCE_UCE;
        int tech = REGISTRATION_TECH_LTE;
        provisioned = mTestImsProvisioningController
                .getRcsProvisioningStatusForCapability(mSubId0, capability, tech);

        // verify return value default false - not provisioned
        assertEquals(true, provisioned);

        verify(mImsProvisioningLoader, times(1)).getProvisioningStatus(
                eq(mSubId0), eq(FEATURE_RCS), eq(capability), eq(tech));

        // verify whether ImsProvisioningLoader is called or not
        verify(mImsConfig, times(1)).getConfigInt(eq(KEY_EAB_PROVISIONING_STATUS));

        // verify whether ImsProvisioningLoader is called or not
        verify(mImsProvisioningLoader, times(RADIO_TECHS.length)).setProvisioningStatus(
                eq(mSubId0), eq(FEATURE_RCS), eq(capability), anyInt(), eq(provisioned));

        verifyNoMoreInteractions(mImsConfig);
        verifyNoMoreInteractions(mImsProvisioningLoader);
    }

    @Test
    @SmallTest
    public void setImsProvisioningRequiredForCapability_withVoice() throws Exception {
        createImsProvisioningController();

        mMmTelConnectorListener0.getValue().connectionReady(mImsManager, mSubId0);
        processAllMessages();

        // register callbacks
        mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                mSubId0, mIFeatureProvisioningCallback0);
        mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                mSubId1, mIFeatureProvisioningCallback1);

        clearInvocations(mIFeatureProvisioningCallback0);
        clearInvocations(mIFeatureProvisioningCallback1);
        clearInvocations(mImsConfig);
        clearInvocations(mImsProvisioningLoader);

        // provisioning required capability
        // voice, all tech
        setCarrierConfig(mSubId0, CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VOICE_INT_ARRAY,
                RADIO_TECHS);

        // provisioning Status, all of provisioning status is not provisioned
        mMmTelProvisioningStorage = new int[][] {
                {CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_LTE, 0},
                {CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_IWLAN, 0},
                {CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_CROSS_SIM, 0},
                {CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_NR, 0}
        };

        boolean provisionedFirst = false;
        boolean provisionedSecond = false;
        int capability = CAPABILITY_TYPE_VOICE;
        for (int i = 0; i < RADIO_TECHS.length; i++) {
            // get provisioning status
            provisionedFirst = mTestImsProvisioningController
                    .getImsProvisioningStatusForCapability(mSubId0, capability, RADIO_TECHS[i]);

            // verify return value default false - not provisioned
            assertEquals(false, provisionedFirst);

            mTestImsProvisioningController.setImsProvisioningStatusForCapability(
                    mSubId0, capability, RADIO_TECHS[i], !provisionedFirst);
            processAllMessages();

            provisionedSecond = mTestImsProvisioningController
                    .getImsProvisioningStatusForCapability(mSubId0, capability, RADIO_TECHS[i]);

            // verify return value default false - provisioned
            assertEquals(!provisionedFirst, provisionedSecond);

            // verify whether ImsProvisioningLoader is called or not
            verify(mImsProvisioningLoader, times(2))
                    .getProvisioningStatus(eq(mSubId0), eq(FEATURE_MMTEL), eq(capability),
                            eq(RADIO_TECHS[i]));
            verify(mImsProvisioningLoader, times(1))
                    .setProvisioningStatus(eq(mSubId0), eq(FEATURE_MMTEL), eq(capability),
                            eq(RADIO_TECHS[i]), eq(provisionedSecond));

            // verify whether Callback is called or not
            verify(mIFeatureProvisioningCallback0, times(1))
                    .onFeatureProvisioningChanged(eq(capability), eq(RADIO_TECHS[i]),
                            eq(provisionedSecond));
        }

        // verify whether ImsConfig is called or not
        verify(mImsConfig, times(1)).setConfig(
                eq(KEY_VOLTE_PROVISIONING_STATUS), eq(PROVISIONING_VALUE_ENABLED));
        verify(mImsConfig, times(1)).setConfig(
                eq(KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE), eq(PROVISIONING_VALUE_ENABLED));

        verifyNoMoreInteractions(mIFeatureProvisioningCallback0);
        verifyNoMoreInteractions(mIFeatureProvisioningCallback1);
        verifyNoMoreInteractions(mImsConfig);
        verifyNoMoreInteractions(mImsProvisioningLoader);
    }

    @Test
    @SmallTest
    public void setImsProvisioningRequiredForCapability_withVideo() throws Exception {
        createImsProvisioningController();

        mMmTelConnectorListener0.getValue().connectionReady(mImsManager, mSubId0);
        processAllMessages();

        // register callbacks
        mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                mSubId0, mIFeatureProvisioningCallback0);
        mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                mSubId1, mIFeatureProvisioningCallback1);

        clearInvocations(mIFeatureProvisioningCallback0);
        clearInvocations(mIFeatureProvisioningCallback1);
        clearInvocations(mImsConfig);
        clearInvocations(mImsProvisioningLoader);

        // provisioning required capability
        // video, all tech
        setCarrierConfig(mSubId0, CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VIDEO_INT_ARRAY,
                RADIO_TECHS);

        // provisioning Status, all of provisioning status is not provisioned
        mMmTelProvisioningStorage = new int[][] {
                {CAPABILITY_TYPE_VIDEO, REGISTRATION_TECH_LTE, 0},
                {CAPABILITY_TYPE_VIDEO, REGISTRATION_TECH_IWLAN, 0},
                {CAPABILITY_TYPE_VIDEO, REGISTRATION_TECH_CROSS_SIM, 0},
                {CAPABILITY_TYPE_VIDEO, REGISTRATION_TECH_NR, 0}
        };

        boolean provisionedFirst = false;
        boolean provisionedSecond = false;
        int capability = CAPABILITY_TYPE_VIDEO;
        for (int i = 0; i < RADIO_TECHS.length; i++) {
            // get provisioning status
            provisionedFirst = mTestImsProvisioningController
                    .getImsProvisioningStatusForCapability(mSubId0, capability, RADIO_TECHS[i]);

            // verify return value default false - not provisioned
            assertEquals(false, provisionedFirst);

            mTestImsProvisioningController.setImsProvisioningStatusForCapability(
                    mSubId0, capability, RADIO_TECHS[i], !provisionedFirst);
            processAllMessages();

            provisionedSecond = mTestImsProvisioningController
                    .getImsProvisioningStatusForCapability(mSubId0, capability, RADIO_TECHS[i]);

            // verify return value default false - provisioned
            assertEquals(!provisionedFirst, provisionedSecond);

            // verify whether ImsProvisioningLoader is called or not
            verify(mImsProvisioningLoader, times(2))
                    .getProvisioningStatus(eq(mSubId0), eq(FEATURE_MMTEL), eq(capability),
                            eq(RADIO_TECHS[i]));
            verify(mImsProvisioningLoader, times(1))
                    .setProvisioningStatus(eq(mSubId0), eq(FEATURE_MMTEL), eq(capability),
                            eq(RADIO_TECHS[i]), eq(provisionedSecond));

            // verify whether Callback is called or not
            verify(mIFeatureProvisioningCallback0, times(1))
                    .onFeatureProvisioningChanged(eq(capability), eq(RADIO_TECHS[i]),
                            eq(provisionedSecond));
        }

        // verify whether ImsConfig is called or not
        verify(mImsConfig, times(1)).setConfig(
                eq(KEY_VT_PROVISIONING_STATUS), eq(PROVISIONING_VALUE_ENABLED));

        verifyNoMoreInteractions(mIFeatureProvisioningCallback0);
        verifyNoMoreInteractions(mIFeatureProvisioningCallback1);
        verifyNoMoreInteractions(mImsConfig);
        verifyNoMoreInteractions(mImsProvisioningLoader);
    }

    @Test
    @SmallTest
    public void setRcsProvisioningRequiredForCapability_withPresence() throws Exception {
        createImsProvisioningController();

        mMmTelConnectorListener0.getValue().connectionReady(mImsManager, mSubId0);
        mRcsConnectorListener0.getValue().connectionReady(mRcsFeatureManager, mSubId0);
        processAllMessages();

        // register callbacks
        mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                mSubId0, mIFeatureProvisioningCallback0);
        mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                mSubId1, mIFeatureProvisioningCallback1);

        clearInvocations(mIFeatureProvisioningCallback0);
        clearInvocations(mIFeatureProvisioningCallback1);
        clearInvocations(mImsConfig);
        clearInvocations(mImsProvisioningLoader);

        // provisioning required capability
        // PRESENCE, all tech
        setCarrierConfig(mSubId0,
                CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_PRESENCE_UCE_INT_ARRAY, RADIO_TECHS);

        // provisioning Status, all of provisioning status is not provisioned
        mRcsProvisioningStorage = new int[][] {
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_LTE, 0},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_IWLAN, 0},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_CROSS_SIM, 0},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_NR, 0}
        };

        boolean provisionedFirst;
        boolean provisionedSecond;
        int capability = CAPABILITY_TYPE_PRESENCE_UCE;

        // get provisioning status
        provisionedFirst = mTestImsProvisioningController
                .getRcsProvisioningStatusForCapability(mSubId0, capability, REGISTRATION_TECH_LTE);

        // verify return value default false - not provisioned
        assertEquals(false, provisionedFirst);

        mTestImsProvisioningController.setRcsProvisioningStatusForCapability(
                mSubId0, capability, REGISTRATION_TECH_LTE, !provisionedFirst);
        processAllMessages();

        provisionedSecond = mTestImsProvisioningController
                .getRcsProvisioningStatusForCapability(mSubId0, capability, REGISTRATION_TECH_LTE);

        // verify return value default false - provisioned
        assertEquals(!provisionedFirst, provisionedSecond);

        // verify whether ImsProvisioningLoader is called or not
        verify(mImsProvisioningLoader, times(2)).getProvisioningStatus(
                eq(mSubId0), eq(FEATURE_RCS), eq(capability), eq(REGISTRATION_TECH_LTE));
        // verify setProvisioningStatus is called RADIO_TECHS.length times for all tech or not
        verify(mImsProvisioningLoader, times(1)).setProvisioningStatus(
                eq(mSubId0), eq(FEATURE_RCS), eq(capability), anyInt(), eq(provisionedSecond));

        // verify whether Callback is called RADIO_TECHS.length times for all tech or not
        verify(mIFeatureProvisioningCallback0, times(1))
                .onRcsFeatureProvisioningChanged(eq(capability), eq(REGISTRATION_TECH_LTE),
                        eq(provisionedSecond));

        // verify whether ImsConfig is called or not
        // EAB provisioning status should be updated to both the Rcs and MmTel ImsService
        verify(mImsConfig, times(2)).setConfig(
                eq(KEY_EAB_PROVISIONING_STATUS), eq(PROVISIONING_VALUE_ENABLED));

        // verify reset
        clearInvocations(mImsProvisioningLoader);

        // only CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_LTE - provisioned
        boolean[] expected = {true, false, false, false};
        for (int i = 0; i < RADIO_TECHS.length; i++) {
            provisionedSecond = mTestImsProvisioningController
                    .getRcsProvisioningStatusForCapability(mSubId0, capability, RADIO_TECHS[i]);

            // verify return value
            assertEquals(expected[i], provisionedSecond);

            // verify whether ImsProvisioningLoader is called or not
            verify(mImsProvisioningLoader, times(1)).getProvisioningStatus(
                    eq(mSubId0), eq(FEATURE_RCS), eq(capability), eq(RADIO_TECHS[i]));
        }

        verifyNoMoreInteractions(mIFeatureProvisioningCallback0);
        verifyNoMoreInteractions(mIFeatureProvisioningCallback1);
        verifyNoMoreInteractions(mImsConfig);
        verifyNoMoreInteractions(mImsProvisioningLoader);
    }

    @Test
    @SmallTest
    public void setProvisioningValue_withMmTelKey() throws Exception {
        createImsProvisioningController();

        mMmTelConnectorListener0.getValue().connectionReady(mImsManager, mSubId0);
        processAllMessages();

        // add callback with valid obj
        mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                mSubId0, mIFeatureProvisioningCallback0);
        mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                mSubId1, mIFeatureProvisioningCallback1);

        clearInvocations(mIFeatureProvisioningCallback0);
        clearInvocations(mIFeatureProvisioningCallback1);
        clearInvocations(mImsConfig);
        clearInvocations(mImsProvisioningLoader);

        // provisioning required capability
        // voice, all tech
        // video, all tech
        setCarrierConfig(mSubId0, CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VIDEO_INT_ARRAY,
                RADIO_TECHS);
        setCarrierConfig(mSubId0, CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VIDEO_INT_ARRAY,
                RADIO_TECHS);

        // provisioning Status, all of provisioning status is not set
        mMmTelProvisioningStorage = new int[][] {
                {CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_LTE, -1},
                {CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_IWLAN, -1},
                {CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_CROSS_SIM, -1},
                {CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_NR, -1},
                {CAPABILITY_TYPE_VIDEO, REGISTRATION_TECH_LTE, -1},
                {CAPABILITY_TYPE_VIDEO, REGISTRATION_TECH_IWLAN, -1},
                {CAPABILITY_TYPE_VIDEO, REGISTRATION_TECH_CROSS_SIM, -1},
                {CAPABILITY_TYPE_VIDEO, REGISTRATION_TECH_NR, -1}
        };

        // MmTel valid
        int[] keys = {
                ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS,
                ProvisioningManager.KEY_VT_PROVISIONING_STATUS,
                ProvisioningManager.KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE
        };
        int[] capas = {
                MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                MmTelCapabilities.CAPABILITY_TYPE_VIDEO,
                MmTelCapabilities.CAPABILITY_TYPE_VOICE
        };
        int[] techs = {
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE,
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE,
                ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN
        };

        int result;
        for (int i = 0; i < keys.length; i++) {
            clearInvocations(mIFeatureProvisioningCallback0);
            result = mTestImsProvisioningController.setProvisioningValue(
                    mSubId0, keys[i], PROVISIONING_VALUE_ENABLED);
            processAllMessages();

            // check return value
            assertEquals(ImsConfig.OperationStatusConstants.SUCCESS, result);

            // check whether to save
            verify(mImsProvisioningLoader, times(1)).setProvisioningStatus(
                    eq(mSubId0), eq(FEATURE_MMTEL), eq(capas[i]), eq(techs[i]), eq(true));

            verify(mIFeatureProvisioningCallback0, times(1))
                    .onFeatureProvisioningChanged(eq(capas[i]), eq(techs[i]), eq(true));

            verify(mImsConfig, times(1)).setConfig(eq(keys[i]), eq(PROVISIONING_VALUE_ENABLED));
        }

        verifyNoMoreInteractions(mIFeatureProvisioningCallback0);
        verifyNoMoreInteractions(mIFeatureProvisioningCallback1);
        verifyNoMoreInteractions(mImsConfig);
        verifyNoMoreInteractions(mImsProvisioningLoader);
    }

    @Test
    @SmallTest
    public void setProvisioningValue_withRcsKey() throws Exception {
        createImsProvisioningController();

        mMmTelConnectorListener0.getValue().connectionReady(mImsManager, mSubId0);
        mRcsConnectorListener0.getValue().connectionReady(mRcsFeatureManager, mSubId0);
        processAllMessages();

        // add callback with valid obj
        mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                mSubId0, mIFeatureProvisioningCallback0);
        mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                mSubId1, mIFeatureProvisioningCallback1);

        clearInvocations(mIFeatureProvisioningCallback0);
        clearInvocations(mIFeatureProvisioningCallback1);
        clearInvocations(mImsConfig);
        clearInvocations(mImsProvisioningLoader);

        // provisioning required capability
        // presence, all tech
        setCarrierConfig(mSubId0,
                CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_PRESENCE_UCE_INT_ARRAY, RADIO_TECHS);

        // provisioning Status, all of provisioning status is not set
        mRcsProvisioningStorage = new int[][]{
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_LTE, -1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_IWLAN, -1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_CROSS_SIM, -1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_NR, -1}
        };

        int key = KEY_EAB_PROVISIONING_STATUS;
        int capa = CAPABILITY_TYPE_PRESENCE_UCE;

        int result = mTestImsProvisioningController.setProvisioningValue(
                    mSubId0, key, PROVISIONING_VALUE_ENABLED);
        processAllMessages();

        // check return value
        assertEquals(ImsConfig.OperationStatusConstants.SUCCESS, result);

        // check to save, for all techs 4 times
        verify(mImsProvisioningLoader, times(RADIO_TECHS.length)).setProvisioningStatus(
                eq(mSubId0), eq(FEATURE_RCS), eq(capa), anyInt(), eq(true));

        verify(mIFeatureProvisioningCallback0, times(RADIO_TECHS.length))
                .onRcsFeatureProvisioningChanged(eq(capa), anyInt(), eq(true));

        // verify whether ImsConfig is called or not
        // EAB provisioning status should be updated to both the Rcs and MmTel ImsService
        verify(mImsConfig, times(2)).setConfig(eq(key), eq(PROVISIONING_VALUE_ENABLED));

        verifyNoMoreInteractions(mIFeatureProvisioningCallback0);
        verifyNoMoreInteractions(mIFeatureProvisioningCallback1);
        verifyNoMoreInteractions(mImsConfig);
        verifyNoMoreInteractions(mImsProvisioningLoader);
    }

    @Test
    @SmallTest
    public void setProvisioningValue_withInvalidKey() throws Exception {
        createImsProvisioningController();

        mMmTelConnectorListener0.getValue().connectionReady(mImsManager, mSubId0);
        mRcsConnectorListener0.getValue().connectionReady(mRcsFeatureManager, mSubId0);
        processAllMessages();

        // add callback with valid obj
        mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                mSubId0, mIFeatureProvisioningCallback0);
        mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                mSubId1, mIFeatureProvisioningCallback1);

        clearInvocations(mIFeatureProvisioningCallback0);
        clearInvocations(mIFeatureProvisioningCallback1);
        clearInvocations(mImsConfig);
        clearInvocations(mImsProvisioningLoader);

        // invalid key
        int[] keys = {
                ProvisioningManager.KEY_SIP_SESSION_TIMER_SEC,
                ProvisioningManager.KEY_MINIMUM_SIP_SESSION_EXPIRATION_TIMER_SEC,
                ProvisioningManager.KEY_TF_TIMER_VALUE_MS
        };
        for (int key : keys) {
            int result = mTestImsProvisioningController.setProvisioningValue(
                    mSubId0, key, PROVISIONING_VALUE_ENABLED);
            processAllMessages();

            // check return value
            assertEquals(ImsConfigImplBase.CONFIG_RESULT_UNKNOWN, result);
        }

        verifyNoMoreInteractions(mIFeatureProvisioningCallback0);
        verifyNoMoreInteractions(mIFeatureProvisioningCallback1);
        verifyNoMoreInteractions(mImsConfig);
        verifyNoMoreInteractions(mImsProvisioningLoader);
    }

    @Test
    @SmallTest
    public void getProvisioningValue_withValidKey() throws Exception {
        createImsProvisioningController();

        mMmTelConnectorListener0.getValue().connectionReady(mImsManager, mSubId0);
        mRcsConnectorListener0.getValue().connectionReady(mRcsFeatureManager, mSubId0);
        processAllMessages();

        // add callback with valid obj
        mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                mSubId0, mIFeatureProvisioningCallback0);
        mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                mSubId1, mIFeatureProvisioningCallback1);

        clearInvocations(mIFeatureProvisioningCallback0);
        clearInvocations(mIFeatureProvisioningCallback1);
        clearInvocations(mImsConfig);
        clearInvocations(mImsProvisioningLoader);

        // provisioning required capability
        // voice, LTE, IWLAN
        // video, LTE
        setCarrierConfig(mSubId0, CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VOICE_INT_ARRAY,
                REGISTRATION_TECH_LTE, REGISTRATION_TECH_IWLAN);
        setCarrierConfig(mSubId0, CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VIDEO_INT_ARRAY,
                REGISTRATION_TECH_LTE);

        // provisioning Status, all of provisioning status is not set
        mMmTelProvisioningStorage = new int[][] {
                {CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_LTE, 1},
                {CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_IWLAN, 1},
                {CAPABILITY_TYPE_VIDEO, REGISTRATION_TECH_LTE, 1}
        };

        // provisioning required capability
        // presence, all tech
        setCarrierConfig(mSubId0,
                CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_PRESENCE_UCE_INT_ARRAY, RADIO_TECHS);

        // provisioning Status, all of provisioning status is not set
        mRcsProvisioningStorage = new int[][]{
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_LTE, 1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_IWLAN, 1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_CROSS_SIM, 1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_NR, 1}
        };

        // MmTel keys
        int[] keys = {
                KEY_VOLTE_PROVISIONING_STATUS,
                KEY_VT_PROVISIONING_STATUS,
                KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE,
        };
        int[] capas = {
                CAPABILITY_TYPE_VOICE,
                CAPABILITY_TYPE_VIDEO,
                CAPABILITY_TYPE_VOICE
        };
        int[] techs = {
                REGISTRATION_TECH_LTE,
                REGISTRATION_TECH_LTE,
                REGISTRATION_TECH_IWLAN
        };
        for (int i = 0; i < keys.length; i++) {
            int result = mTestImsProvisioningController.getProvisioningValue(mSubId0, keys[i]);
            processAllMessages();

            // check return value
            assertEquals(PROVISIONING_VALUE_ENABLED, result);

            // verify whether ImsProvisioningLoader is called or not
            verify(mImsProvisioningLoader, times(1)).getProvisioningStatus(eq(mSubId0),
                    eq(FEATURE_MMTEL), eq(capas[i]), eq(techs[i]));
        }
        clearInvocations(mImsProvisioningLoader);

        // Rcs keys
        int key = KEY_EAB_PROVISIONING_STATUS;
        int capa = CAPABILITY_TYPE_PRESENCE_UCE;

        int result = mTestImsProvisioningController.getProvisioningValue(mSubId0, key);
        processAllMessages();

        // check return value
        assertEquals(PROVISIONING_VALUE_ENABLED, result);

        // verify whether ImsProvisioningLoader is called or not
        verify(mImsProvisioningLoader, times(1)).getProvisioningStatus(
                eq(mSubId0), eq(FEATURE_RCS), eq(capa), anyInt());

        verifyNoMoreInteractions(mIFeatureProvisioningCallback0);
        verifyNoMoreInteractions(mIFeatureProvisioningCallback1);
        verifyNoMoreInteractions(mImsConfig);
        verifyNoMoreInteractions(mImsProvisioningLoader);
    }

    @Test
    @SmallTest
    public void getProvisioningValue_withNotSet() throws Exception {
        createImsProvisioningController();

        mMmTelConnectorListener0.getValue().connectionReady(mImsManager, mSubId0);
        mRcsConnectorListener0.getValue().connectionReady(mRcsFeatureManager, mSubId0);
        processAllMessages();

        // add callback with valid obj
        mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                mSubId0, mIFeatureProvisioningCallback0);
        mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                mSubId1, mIFeatureProvisioningCallback1);

        clearInvocations(mIFeatureProvisioningCallback0);
        clearInvocations(mIFeatureProvisioningCallback1);
        clearInvocations(mImsConfig);
        clearInvocations(mImsProvisioningLoader);

        // provisioning required capability
        // voice, LTE, IWLAN
        // video, LTE
        setCarrierConfig(mSubId0, CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VOICE_INT_ARRAY,
                REGISTRATION_TECH_LTE, REGISTRATION_TECH_IWLAN);
        setCarrierConfig(mSubId0, CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VIDEO_INT_ARRAY,
                REGISTRATION_TECH_LTE);

        // provisioning Status, all of provisioning status is not set
        mMmTelProvisioningStorage = new int[][] {
                {CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_LTE, -1},
                {CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_IWLAN, -1},
                {CAPABILITY_TYPE_VIDEO, REGISTRATION_TECH_LTE, -1}
        };

        // provisioning required capability
        // presence, all tech
        setCarrierConfig(mSubId0,
                CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_PRESENCE_UCE_INT_ARRAY, RADIO_TECHS);

        // provisioning Status, all of provisioning status is not set
        mRcsProvisioningStorage = new int[][]{
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_LTE, -1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_IWLAN, -1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_CROSS_SIM, -1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_NR, -1}
        };
        // provisioning status in ImsService
        mImsConfigStorage = new int[][] {
                {KEY_VOLTE_PROVISIONING_STATUS, 1},
                {KEY_VT_PROVISIONING_STATUS, 1},
                {KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE, 1},
                {KEY_EAB_PROVISIONING_STATUS, 1}
        };

        // MmTel keys
        int[] keys = {
                KEY_VOLTE_PROVISIONING_STATUS,
                KEY_VT_PROVISIONING_STATUS,
                KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE,
        };
        int[] capas = {
                CAPABILITY_TYPE_VOICE,
                CAPABILITY_TYPE_VIDEO,
                CAPABILITY_TYPE_VOICE
        };
        int[] techs = {
                REGISTRATION_TECH_LTE,
                REGISTRATION_TECH_LTE,
                REGISTRATION_TECH_IWLAN
        };
        for (int i = 0; i < keys.length; i++) {
            int result = mTestImsProvisioningController.getProvisioningValue(mSubId0, keys[i]);
            processAllMessages();

            // check return value
            assertEquals(PROVISIONING_VALUE_ENABLED, result);

            // verify whether ImsProvisioningLoader is called or not
            verify(mImsProvisioningLoader, times(1)).getProvisioningStatus(eq(mSubId0),
                    eq(FEATURE_MMTEL), eq(capas[i]), eq(techs[i]));

            // verify whether ImsConfig is called or not
            verify(mImsConfig, times(1)).getConfigInt(eq(keys[i]));

            // verify whether ImsProvisioningLoader is called or not
            verify(mImsProvisioningLoader, times(1)).setProvisioningStatus(eq(mSubId0),
                    eq(FEATURE_MMTEL), eq(capas[i]), eq(techs[i]), eq(true));

            // verify whether callback is called or not
            verify(mIFeatureProvisioningCallback0, times(1)).onFeatureProvisioningChanged(
                    eq(capas[i]), eq(techs[i]), eq(true));
        }
        clearInvocations(mImsConfig);
        clearInvocations(mImsProvisioningLoader);

        // Rcs keys
        int key = KEY_EAB_PROVISIONING_STATUS;
        int capa = CAPABILITY_TYPE_PRESENCE_UCE;

        int result = mTestImsProvisioningController.getProvisioningValue(mSubId0, key);
        processAllMessages();

        // check return value
        assertEquals(PROVISIONING_VALUE_ENABLED, result);

        // verify whether ImsProvisioningLoader is called or not
        verify(mImsProvisioningLoader, times(1)).getProvisioningStatus(
                eq(mSubId0), eq(FEATURE_RCS), eq(capa), anyInt());

        // verify whether ImsConfig is called or not
        verify(mImsConfig, times(1)).getConfigInt(eq(key));

        // verify whether ImsProvisioningLoader is called or not
        verify(mImsProvisioningLoader, times(RADIO_TECHS.length)).setProvisioningStatus(
                eq(mSubId0), eq(FEATURE_RCS), eq(capa), anyInt(), eq(true));

        // verify whether callback is called or not
        verify(mIFeatureProvisioningCallback0, times(RADIO_TECHS.length))
                .onRcsFeatureProvisioningChanged(eq(capa), anyInt(), eq(true));

        verifyNoMoreInteractions(mIFeatureProvisioningCallback0);
        verifyNoMoreInteractions(mIFeatureProvisioningCallback1);
        verifyNoMoreInteractions(mImsConfig);
        verifyNoMoreInteractions(mImsProvisioningLoader);
    }

    @Test
    @SmallTest
    public void onMultiSimConfigChanged() throws Exception {
        createImsProvisioningController();

        // provisioning required capability
        // voice, all tech
        // video, all tech
        setCarrierConfig(mSubId0, CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VOICE_INT_ARRAY,
                RADIO_TECHS);
        setCarrierConfig(mSubId0, CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VIDEO_INT_ARRAY,
                RADIO_TECHS);

        // change number of slot 2 -> 1
        mHandler.sendMessage(mHandler.obtainMessage(
                mTestImsProvisioningController.EVENT_MULTI_SIM_CONFIGURATION_CHANGE,
                0, 0, (Object) new AsyncResult(null, 1, null)));
        processAllMessages();

        // add callback with mSubId0, mPhoneId0 : Ok.
        try {
            mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                    mSubId0, mIFeatureProvisioningCallback0);
        } catch (Exception e) {
            throw new AssertionError("not expected exception", e);
        }

        // add callbacks with new mSubId1, mPhoneId1 : IllegalArgumentException.
        try {
            mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                    mSubId1, mIFeatureProvisioningCallback1);
        } catch (IllegalArgumentException e) {
            // expected result
        } catch (Exception e) {
            throw new AssertionError("not expected exception", e);
        }
        // check isImsProvisioningRequiredForCapability with mSubId1 : IllegalArgumentException
        try {
            mTestImsProvisioningController.isImsProvisioningRequiredForCapability(
                    mSubId1, CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_LTE);
        } catch (IllegalArgumentException e) {
            // expected result
        } catch (Exception e) {
            throw new AssertionError("not expected exception", e);
        }
        clearInvocations(mIFeatureProvisioningCallback0);
        clearInvocations(mIFeatureProvisioningCallback1);

        // change number of slot 1 -> 2
        mHandler.sendMessage(mHandler.obtainMessage(
                mTestImsProvisioningController.EVENT_MULTI_SIM_CONFIGURATION_CHANGE,
                0, 0, (Object) new AsyncResult(null, 2, null)));
        processAllMessages();

        // add callback with mSubId0, mPhoneId0 : Ok.
        try {
            mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                    mSubId0, mIFeatureProvisioningCallback0);
        } catch (Exception e) {
            throw new AssertionError("not expected exception", e);
        }

        // add callbacks with new mSubId1, mPhoneId1 : Ok.
        try {
            mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                    mSubId1, mIFeatureProvisioningCallback1);
        } catch (Exception e) {
            throw new AssertionError("not expected exception", e);
        }
        clearInvocations(mIFeatureProvisioningCallback0);
        clearInvocations(mIFeatureProvisioningCallback1);

        // provisioning required capability
        // voice, all tech
        setCarrierConfig(mSubId1, CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VOICE_INT_ARRAY,
                RADIO_TECHS);

        // check get,setImsProvisioningRequiredForCapability with mSubId1, mPhoneId1 : Ok
        int capability = CAPABILITY_TYPE_VOICE;
        int tech = REGISTRATION_TECH_LTE;
        boolean provisioned;
        provisioned = mTestImsProvisioningController.getImsProvisioningStatusForCapability(
                mSubId1, capability, tech);
        mTestImsProvisioningController.setImsProvisioningStatusForCapability(mSubId1,
                capability, tech, !provisioned);
        processAllMessages();

        // verify whether Callback is called or not
        verify(mIFeatureProvisioningCallback1, times(1))
                .onFeatureProvisioningChanged(eq(capability), eq(tech), eq(!provisioned));

        clearInvocations(mIFeatureProvisioningCallback0);
        clearInvocations(mIFeatureProvisioningCallback1);
        clearInvocations(mImsConfig);
    }

    @Test
    @SmallTest
    public void eabProvisioningStatus_onlyMmTelConnectionReady() throws Exception {
        createImsProvisioningController();

        mMmTelConnectorListener0.getValue().connectionReady(mImsManager, mSubId0);
        processAllMessages();

        // add callback with valid obj
        mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                mSubId0, mIFeatureProvisioningCallback0);

        clearInvocations(mIFeatureProvisioningCallback0);
        clearInvocations(mImsConfig);
        clearInvocations(mImsProvisioningLoader);

        // provisioning required capability
        // presence, all tech
        setCarrierConfig(mSubId0,
                CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_PRESENCE_UCE_INT_ARRAY, RADIO_TECHS);

        // provisioning Status, all of provisioning status is not set
        mRcsProvisioningStorage = new int[][]{
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_LTE, -1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_IWLAN, -1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_CROSS_SIM, -1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_NR, -1}
        };

        // provisioning status in ImsService
        mImsConfigStorage = new int[][] {
                {KEY_EAB_PROVISIONING_STATUS, 1}
        };

        // Rcs keys
        int key = KEY_EAB_PROVISIONING_STATUS;
        int capa = CAPABILITY_TYPE_PRESENCE_UCE;
        int tech = REGISTRATION_TECH_LTE;

        int result = mTestImsProvisioningController.getProvisioningValue(mSubId0, key);
        processAllMessages();

        // check return value
        assertEquals(PROVISIONING_VALUE_ENABLED, result);

        // verify whether ImsProvisioningLoader is called or not
        verify(mImsProvisioningLoader, times(1)).getProvisioningStatus(
                eq(mSubId0), eq(FEATURE_RCS), eq(capa), anyInt());

        // even if ImsConfig is not available in RcsFeatureListener, ImsConfig in
        // MmTelFeatureListener will be called.
        verify(mImsConfig, times(1)).getConfigInt(
                eq(KEY_EAB_PROVISIONING_STATUS));

        // verify whether ImsProvisioningLoader is called or not
        verify(mImsProvisioningLoader, times(RADIO_TECHS.length)).setProvisioningStatus(
                eq(mSubId0), eq(FEATURE_RCS), eq(capa), anyInt(), eq(true));

        verifyNoMoreInteractions(mImsConfig);
        verifyNoMoreInteractions(mImsProvisioningLoader);

        clearInvocations(mImsConfig);
        clearInvocations(mImsProvisioningLoader);

        mTestImsProvisioningController.setProvisioningValue(mSubId0, key,
                PROVISIONING_VALUE_DISABLED);
        processAllMessages();

        // verify whether ImsProvisioningLoader is called or not
        verify(mImsProvisioningLoader, times(RADIO_TECHS.length)).setProvisioningStatus(
                eq(mSubId0), eq(FEATURE_RCS), eq(capa), anyInt(), eq(false));

        // even if ImsConfig is not available in RcsFeatureListener, ImsConfig in
        // MmTelFeatureListener will be called.
        verify(mImsConfig, times(1)).setConfig(
                eq(KEY_EAB_PROVISIONING_STATUS), eq(PROVISIONING_VALUE_DISABLED));

        verifyNoMoreInteractions(mImsConfig);
        verifyNoMoreInteractions(mImsProvisioningLoader);

        clearInvocations(mImsConfig);
        clearInvocations(mImsProvisioningLoader);

        // reset provisioning status, all of provisioning status is not set
        mRcsProvisioningStorage = new int[][]{
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_LTE, -1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_IWLAN, -1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_CROSS_SIM, -1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_NR, -1}
        };

        // reset provisioning status in ImsService
        mImsConfigStorage = new int[][] {
                {KEY_EAB_PROVISIONING_STATUS, 1}
        };

        boolean expected = true;
        boolean provisioned = mTestImsProvisioningController.getRcsProvisioningStatusForCapability(
                mSubId0, capa, tech);
        processAllMessages();

        assertEquals(expected, provisioned);

        // verify whether ImsProvisioningLoader is called or not
        verify(mImsProvisioningLoader, times(1)).getProvisioningStatus(
                eq(mSubId0), eq(FEATURE_RCS), eq(capa), eq(tech));

        // even if ImsConfig is not available in RcsFeatureListener, ImsConfig in
        // MmTelFeatureListener will be called.
        verify(mImsConfig, times(1)).getConfigInt(
                eq(KEY_EAB_PROVISIONING_STATUS));

        // verify whether ImsProvisioningLoader is called or not
        verify(mImsProvisioningLoader, times(RADIO_TECHS.length)).setProvisioningStatus(
                eq(mSubId0), eq(FEATURE_RCS), eq(capa), anyInt(), eq(true));

        verifyNoMoreInteractions(mImsConfig);
        verifyNoMoreInteractions(mImsProvisioningLoader);

        clearInvocations(mImsConfig);
        clearInvocations(mImsProvisioningLoader);

        mTestImsProvisioningController.setRcsProvisioningStatusForCapability(
                mSubId0, capa, tech, !expected);
        processAllMessages();

        // verify whether ImsProvisioningLoader is called or not
        verify(mImsProvisioningLoader, times(1)).setProvisioningStatus(
                eq(mSubId0), eq(FEATURE_RCS), eq(capa), eq(tech), eq(!expected));

        // even if ImsConfig is not available in RcsFeatureListener, ImsConfig in
        // MmTelFeatureListener will be called.
        verify(mImsConfig, times(1)).setConfig(
                eq(KEY_EAB_PROVISIONING_STATUS), eq(PROVISIONING_VALUE_DISABLED));

        verifyNoMoreInteractions(mImsConfig);
        verifyNoMoreInteractions(mImsProvisioningLoader);
    }

    @Test
    @SmallTest
    public void changedProvisioningValue_withMmTel() throws Exception {
        createImsProvisioningController();

        // provisioning required capability
        // voice, all tech
        // video, all tech
        setCarrierConfig(mSubId0, CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VOICE_INT_ARRAY,
                RADIO_TECHS);
        setCarrierConfig(mSubId0, CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_VIDEO_INT_ARRAY,
                RADIO_TECHS);

        try {
            mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                    mSubId0, mIFeatureProvisioningCallback0);
        } catch (Exception e) {
            throw new AssertionError("not expected exception", e);
        }

        mMmTelConnectorListener0.getValue().connectionReady(mImsManager, mSubId0);

        // clear interactions
        clearInvocations(mIFeatureProvisioningCallback0);
        clearInvocations(mImsConfig);
        clearInvocations(mImsProvisioningLoader);

        // MmTel valid
        int[] keys = {
                ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS,
                ProvisioningManager.KEY_VT_PROVISIONING_STATUS,
                ProvisioningManager.KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE
        };
        int[] capas = {
                MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                MmTelCapabilities.CAPABILITY_TYPE_VIDEO,
                MmTelCapabilities.CAPABILITY_TYPE_VOICE
        };
        int[] techs = {
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE,
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE,
                ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN
        };

        for (int index = 0; index < keys.length; index++) {
            mIImsConfigCallback.getValue().onIntConfigChanged(keys[index],
                    PROVISIONING_VALUE_DISABLED);
            processAllMessages();

            // verify # of read data times from storage : # of MmTel storage length
            verify(mImsProvisioningLoader, times(1))
                    .setProvisioningStatus(eq(mSubId0), eq(FEATURE_MMTEL), eq(capas[index]),
                            eq(techs[index]), eq(false));

            verify(mIFeatureProvisioningCallback0, times(1))
                    .onFeatureProvisioningChanged(eq(capas[index]), eq(techs[index]), eq(false));
        }

        verifyNoMoreInteractions(mImsProvisioningLoader);
        verifyNoMoreInteractions(mIFeatureProvisioningCallback0);
        verifyNoMoreInteractions(mImsConfig);
    }

    @Test
    @SmallTest
    public void changedProvisioningValue_withRcs() throws Exception {
        createImsProvisioningController();

        // provisioning required capability : PRESENCE, tech : all
        setCarrierConfig(mSubId0,
                CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_PRESENCE_UCE_INT_ARRAY, RADIO_TECHS);

        try {
            mTestImsProvisioningController.addFeatureProvisioningChangedCallback(
                    mSubId0, mIFeatureProvisioningCallback0);
        } catch (Exception e) {
            throw new AssertionError("not expected exception", e);
        }

        mRcsConnectorListener0.getValue().connectionReady(mRcsFeatureManager, mSubId0);

        // clear interactions
        clearInvocations(mIFeatureProvisioningCallback0);
        clearInvocations(mImsConfig);
        clearInvocations(mImsProvisioningLoader);

        mIImsConfigCallback.getValue().onIntConfigChanged(KEY_EAB_PROVISIONING_STATUS,
                PROVISIONING_VALUE_DISABLED);
        processAllMessages();

        // verify # of read data times from storage : # of MmTel storage length
        verify(mImsProvisioningLoader, times(RADIO_TECHS.length))
                .setProvisioningStatus(eq(mSubId0), eq(FEATURE_RCS),
                        eq(CAPABILITY_TYPE_PRESENCE_UCE), anyInt(), eq(false));

        verify(mIFeatureProvisioningCallback0, times(RADIO_TECHS.length))
                .onRcsFeatureProvisioningChanged(eq(CAPABILITY_TYPE_PRESENCE_UCE), anyInt(),
                        eq(false));

        verifyNoMoreInteractions(mImsProvisioningLoader);
        verifyNoMoreInteractions(mIFeatureProvisioningCallback0);
        verifyNoMoreInteractions(mImsConfig);
    }

    private void createImsProvisioningController() throws Exception {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        when(mMmTelFeatureConnector
                .create(any(), eq(0), any(), mMmTelConnectorListener0.capture(), any()))
                .thenReturn(mMmTelFeatureConnector0);
        when(mMmTelFeatureConnector
                .create(any(), eq(1), any(), mMmTelConnectorListener1.capture(), any()))
                .thenReturn(mMmTelFeatureConnector1);

        when(mRcsFeatureConnector
                .create(any(), eq(0), mRcsConnectorListener0.capture(), any(), any()))
                .thenReturn(mRcsFeatureConnector0);
        when(mRcsFeatureConnector
                .create(any(), eq(1), mRcsConnectorListener1.capture(), any(), any()))
                .thenReturn(mRcsFeatureConnector1);

        doNothing().when(mImsConfig).addConfigCallback(mIImsConfigCallback.capture());
        doNothing().when(mImsConfig).removeConfigCallback(any());

        when(mImsConfig.getConfigInt(anyInt()))
                .thenAnswer(invocation -> {
                    int i = (Integer) (invocation.getArguments()[0]);
                    return getImsConfigValue(i);
                });
        when(mImsConfig.setConfig(anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    int i = (Integer) (invocation.getArguments()[0]);
                    int j = (Integer) (invocation.getArguments()[1]);
                    return setImsConfigValue(i, j);
                });

        when(mImsProvisioningLoader.getProvisioningStatus(anyInt(), eq(FEATURE_MMTEL), anyInt(),
                anyInt()))
                .thenAnswer(invocation -> {
                    int i = (Integer) (invocation.getArguments()[2]);
                    int j = (Integer) (invocation.getArguments()[3]);
                    return getProvisionedValue(i, j);
                });
        when(mImsProvisioningLoader.getProvisioningStatus(anyInt(), eq(FEATURE_RCS), anyInt(),
                anyInt()))
                .thenAnswer(invocation -> {
                    int i = (Integer) (invocation.getArguments()[2]);
                    int j = (Integer) (invocation.getArguments()[3]);
                    return getRcsProvisionedValue(i, j);
                });
        when(mImsProvisioningLoader
                .setProvisioningStatus(anyInt(), eq(FEATURE_MMTEL), anyInt(), anyInt(),
                        anyBoolean()))
                .thenAnswer(invocation -> {
                    int i = (Integer) (invocation.getArguments()[2]);
                    int j = (Integer) (invocation.getArguments()[3]);
                    int k = (Boolean) (invocation.getArguments()[4]) ? 1 : 0;
                    return setProvisionedValue(i, j, k);
                });
        when(mImsProvisioningLoader
                .setProvisioningStatus(anyInt(), eq(FEATURE_RCS), anyInt(), anyInt(),
                        anyBoolean()))
                .thenAnswer(invocation -> {
                    int i = (Integer) (invocation.getArguments()[2]);
                    int j = (Integer) (invocation.getArguments()[3]);
                    int k = (Boolean) (invocation.getArguments()[4]) ? 1 : 0;
                    return setRcsProvisionedValue(i, j, k);
                });

        when(mIFeatureProvisioningCallback0.asBinder()).thenReturn(mIbinder0);
        when(mIFeatureProvisioningCallback1.asBinder()).thenReturn(mIbinder1);

        doNothing().when(mIFeatureProvisioningCallback0)
                .onFeatureProvisioningChanged(anyInt(), anyInt(), anyBoolean());
        doNothing().when(mIFeatureProvisioningCallback0)
                .onRcsFeatureProvisioningChanged(anyInt(), anyInt(), anyBoolean());
        doNothing().when(mIFeatureProvisioningCallback1)
                .onFeatureProvisioningChanged(anyInt(), anyInt(), anyBoolean());
        doNothing().when(mIFeatureProvisioningCallback1)
                .onRcsFeatureProvisioningChanged(anyInt(), anyInt(), anyBoolean());

        mTestImsProvisioningController = new TestImsProvisioningController();

        mHandler = mTestImsProvisioningController.getHandler();
        try {
            mLooper = new TestableLooper(mHandler.getLooper());
        } catch (Exception e) {
            logd("create looper from handler failed");
        }

        verify(mRcsFeatureConnector0, atLeastOnce()).connect();
        verify(mMmTelFeatureConnector0, atLeastOnce()).connect();

        verify(mRcsFeatureConnector1, atLeastOnce()).connect();
        verify(mMmTelFeatureConnector1, atLeastOnce()).connect();
    }

    private void initializeDefaultData() throws Exception {
        mPersistableBundle0.clear();
        mPersistableBundle0.putPersistableBundle(
                CarrierConfigManager.Ims.KEY_MMTEL_REQUIRES_PROVISIONING_BUNDLE,
                new PersistableBundle());
        mPersistableBundle0.putPersistableBundle(
                CarrierConfigManager.Ims.KEY_RCS_REQUIRES_PROVISIONING_BUNDLE,
                new PersistableBundle());

        mPersistableBundle1.clear();
        mPersistableBundle1.putPersistableBundle(
                CarrierConfigManager.Ims.KEY_MMTEL_REQUIRES_PROVISIONING_BUNDLE,
                new PersistableBundle());
        mPersistableBundle1.putPersistableBundle(
                CarrierConfigManager.Ims.KEY_RCS_REQUIRES_PROVISIONING_BUNDLE,
                new PersistableBundle());

        mMmTelProvisioningStorage = new int[][]{
                {CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_LTE, 1},
                {CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_IWLAN, 1},
                {CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_CROSS_SIM, 1},
                {CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_NR, 1},
                {CAPABILITY_TYPE_VIDEO, REGISTRATION_TECH_LTE, 1},
                {CAPABILITY_TYPE_VIDEO, REGISTRATION_TECH_IWLAN, 1},
                {CAPABILITY_TYPE_VIDEO, REGISTRATION_TECH_CROSS_SIM, 1},
                {CAPABILITY_TYPE_VIDEO, REGISTRATION_TECH_NR, 1},
                {CAPABILITY_TYPE_UT, REGISTRATION_TECH_LTE, 1},
                {CAPABILITY_TYPE_UT, REGISTRATION_TECH_IWLAN, 1},
                {CAPABILITY_TYPE_UT, REGISTRATION_TECH_CROSS_SIM, 1},
                {CAPABILITY_TYPE_UT, REGISTRATION_TECH_NR, 1},
                {CAPABILITY_TYPE_SMS, REGISTRATION_TECH_LTE, 1},
                {CAPABILITY_TYPE_SMS, REGISTRATION_TECH_IWLAN, 1},
                {CAPABILITY_TYPE_SMS, REGISTRATION_TECH_CROSS_SIM, 1},
                {CAPABILITY_TYPE_SMS, REGISTRATION_TECH_NR, 1},
                {CAPABILITY_TYPE_CALL_COMPOSER, REGISTRATION_TECH_LTE, 1},
                {CAPABILITY_TYPE_CALL_COMPOSER, REGISTRATION_TECH_IWLAN, 1},
                {CAPABILITY_TYPE_CALL_COMPOSER, REGISTRATION_TECH_CROSS_SIM, 1},
                {CAPABILITY_TYPE_CALL_COMPOSER, REGISTRATION_TECH_NR, 1}
        };

        mRcsProvisioningStorage = new int[][]{
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_LTE, 1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_IWLAN, 1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_CROSS_SIM, 1},
                {CAPABILITY_TYPE_PRESENCE_UCE, REGISTRATION_TECH_NR, 1}
        };

        mImsConfigStorage = new int[][] {
                {KEY_VOLTE_PROVISIONING_STATUS, 1},
                {KEY_VT_PROVISIONING_STATUS, 1},
                {KEY_VOICE_OVER_WIFI_ENABLED_OVERRIDE, 1},
                {KEY_EAB_PROVISIONING_STATUS, 1}
        };
    }

    private void setCarrierConfig(int subId, String capabilityKey, int... techs) {
        PersistableBundle imsCarrierConfig = mPersistableBundle0;
        if (subId == mSubId1) {
            imsCarrierConfig = mPersistableBundle1;
        }

        PersistableBundle requiredBundle;
        if (capabilityKey.equals(
                CarrierConfigManager.Ims.KEY_CAPABILITY_TYPE_PRESENCE_UCE_INT_ARRAY)) {
            requiredBundle = imsCarrierConfig.getPersistableBundle(
                    CarrierConfigManager.Ims.KEY_RCS_REQUIRES_PROVISIONING_BUNDLE);
        } else {
            requiredBundle = imsCarrierConfig.getPersistableBundle(
                    CarrierConfigManager.Ims.KEY_MMTEL_REQUIRES_PROVISIONING_BUNDLE);
        }

        requiredBundle.putIntArray(capabilityKey, techs);
    }

    private void setDeprecatedCarrierConfig(String key, boolean value) {
        mPersistableBundle0.putBoolean(key, value);
    }

    private int getProvisionedValue(int i, int j) {
        for (int[] data : mMmTelProvisioningStorage) {
            if (data[0] == i && data[1] == j) {
                return data[2];
            }
        }
        return 0;
    }

    private int getRcsProvisionedValue(int i, int j) {
        for (int[] data : mRcsProvisioningStorage) {
            if (data[0] == i && data[1] == j) {
                return data[2];
            }
        }
        return 0;
    }

    private boolean setProvisionedValue(int i, int j, int k) {
        boolean retVal = false;
        for (int[] data : mMmTelProvisioningStorage) {
            if (data[0] == i && data[1] == j) {
                if (data[2] != k) {
                    data[2] = k;
                    return true;
                }
                return false;
            }
        }
        return retVal;
    }

    private boolean setRcsProvisionedValue(int i, int j, int k) {
        boolean retVal = false;
        for (int[] data : mRcsProvisioningStorage) {
            if (data[0] == i && data[1] == j) {
                if (data[2] != k) {
                    data[2] = k;
                    return true;
                }
                return false;
            }
        }
        return retVal;
    }

    private int getImsConfigValue(int i) {
        for (int[] data : mImsConfigStorage) {
            if (data[0] == i) {
                return data[1];
            }
        }
        return -1;
    }

    private int setImsConfigValue(int i, int j) {
        for (int[] data : mImsConfigStorage) {
            if (data[0] == i) {
                data[1] = j;
                return ImsConfig.OperationStatusConstants.SUCCESS;
            }
        }
        return ImsConfig.OperationStatusConstants.SUCCESS;
    }

    private void processAllMessages() {
        while (!mLooper.getLooper().getQueue().isIdle()) {
            mLooper.processAllMessages();
        }
    }

    private static void logd(String str) {
        Log.d(TAG, str);
    }
}
