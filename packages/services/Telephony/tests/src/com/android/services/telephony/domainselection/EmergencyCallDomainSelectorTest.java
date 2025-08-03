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

package com.android.services.telephony.domainselection;

import static android.telephony.AccessNetworkConstants.TRANSPORT_TYPE_WLAN;
import static android.telephony.AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.EUTRAN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.GERAN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.NGRAN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.UNKNOWN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.UTRAN;
import static android.telephony.BarringInfo.BARRING_SERVICE_TYPE_EMERGENCY;
import static android.telephony.BarringInfo.BarringServiceInfo.BARRING_TYPE_UNCONDITIONAL;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_CALL_SETUP_TIMER_ON_CURRENT_NETWORK_SEC_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_CDMA_PREFERRED_NUMBERS_STRING_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_DOMAIN_PREFERENCE_ROAMING_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_LTE_PREFERRED_AFTER_NR_FAILED_BOOL;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_NETWORK_SCAN_TYPE_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_OVER_CS_ROAMING_SUPPORTED_ACCESS_NETWORK_TYPES_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_OVER_CS_SUPPORTED_ACCESS_NETWORK_TYPES_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_OVER_IMS_ROAMING_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_OVER_IMS_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_REQUIRES_IMS_REGISTRATION_BOOL;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_REQUIRES_VOLTE_ENABLED_BOOL;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_SCAN_TIMER_SEC_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_VOWIFI_REQUIRES_CONDITION_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_MAXIMUM_CELLULAR_SEARCH_TIMER_SEC_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_MAXIMUM_NUMBER_OF_EMERGENCY_TRIES_OVER_VOWIFI_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_PREFER_IMS_EMERGENCY_WHEN_VOICE_CALLS_ON_CS_BOOL;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_SCAN_LIMITED_SERVICE_AFTER_VOLTE_FAILURE_BOOL;
import static android.telephony.CarrierConfigManager.ImsEmergency.SCAN_TYPE_FULL_SERVICE;
import static android.telephony.CarrierConfigManager.ImsEmergency.SCAN_TYPE_FULL_SERVICE_FOLLOWED_BY_LIMITED_SERVICE;
import static android.telephony.CarrierConfigManager.ImsEmergency.SCAN_TYPE_NO_PREFERENCE;
import static android.telephony.CarrierConfigManager.ImsEmergency.VOWIFI_REQUIRES_NONE;
import static android.telephony.CarrierConfigManager.ImsEmergency.VOWIFI_REQUIRES_SETTING_ENABLED;
import static android.telephony.CarrierConfigManager.ImsEmergency.VOWIFI_REQUIRES_VALID_EID;
import static android.telephony.CarrierConfigManager.ImsWfc.KEY_EMERGENCY_CALL_OVER_EMERGENCY_PDN_BOOL;
import static android.telephony.DomainSelectionService.SELECTOR_TYPE_CALLING;
import static android.telephony.NetworkRegistrationInfo.DOMAIN_CS;
import static android.telephony.NetworkRegistrationInfo.DOMAIN_PS;
import static android.telephony.NetworkRegistrationInfo.REGISTRATION_STATE_HOME;
import static android.telephony.NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING;
import static android.telephony.NetworkRegistrationInfo.REGISTRATION_STATE_UNKNOWN;
import static android.telephony.PreciseDisconnectCause.SERVICE_OPTION_NOT_AVAILABLE;
import static android.telephony.TelephonyManager.DATA_CONNECTED;
import static android.telephony.TelephonyManager.SIM_ACTIVATION_STATE_DEACTIVATED;

import static com.android.services.telephony.domainselection.EmergencyCallDomainSelector.MSG_MAX_CELLULAR_TIMEOUT;
import static com.android.services.telephony.domainselection.EmergencyCallDomainSelector.MSG_NETWORK_SCAN_TIMEOUT;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyVararg;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IPowerManager;
import android.os.IThermalService;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.telecom.PhoneAccount;
import android.telephony.AccessNetworkConstants;
import android.telephony.BarringInfo;
import android.telephony.CarrierConfigManager;
import android.telephony.CellIdentityLte;
import android.telephony.DisconnectCause;
import android.telephony.DomainSelectionService;
import android.telephony.DomainSelectionService.SelectionAttributes;
import android.telephony.EmergencyRegistrationResult;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PreciseDisconnectCause;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.TransportSelectorCallback;
import android.telephony.WwanSelectorCallback;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ProvisioningManager;
import android.testing.TestableLooper;
import android.util.Log;
import android.util.SparseArray;

import androidx.test.filters.SmallTest;

import com.android.TestContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;

/**
 * Unit tests for EmergencyCallDomainSelector
 */
public class EmergencyCallDomainSelectorTest {
    private static final String TAG = "EmergencyCallDomainSelectorTest";

    private static final int SLOT_0 = 0;
    private static final int SLOT_0_SUB_ID = 1;
    private static final Uri TEST_URI = Uri.fromParts(PhoneAccount.SCHEME_TEL, "911", null);

    @Mock private CarrierConfigManager mCarrierConfigManager;
    @Mock private ConnectivityManager mConnectivityManager;
    @Mock private TelephonyManager mTelephonyManager;
    @Mock private WwanSelectorCallback mWwanSelectorCallback;
    @Mock private TransportSelectorCallback mTransportSelectorCallback;
    @Mock private ImsMmTelManager mMmTelManager;
    @Mock private ImsStateTracker mImsStateTracker;
    @Mock private DomainSelectorBase.DestroyListener mDestroyListener;
    @Mock private ProvisioningManager mProvisioningManager;
    @Mock private CrossSimRedialingController mCsrdCtrl;
    @Mock private CarrierConfigHelper mCarrierConfigHelper;
    @Mock private EmergencyCallbackModeHelper mEcbmHelper;
    @Mock private Resources mResources;

    private Context mContext;

    private HandlerThread mHandlerThread;
    private TestableLooper mLooper;
    private EmergencyCallDomainSelector mDomainSelector;
    private SelectionAttributes mSelectionAttributes;
    private @AccessNetworkConstants.RadioAccessNetworkType List<Integer> mAccessNetwork;
    private PowerManager mPowerManager;
    private ConnectivityManager.NetworkCallback mNetworkCallback;
    private Consumer<EmergencyRegistrationResult> mResultConsumer;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = new TestContext() {
            @Override
            public String getSystemServiceName(Class<?> serviceClass) {
                if (serviceClass == ImsManager.class) {
                    return Context.TELEPHONY_IMS_SERVICE;
                } else if (serviceClass == TelephonyManager.class) {
                    return Context.TELEPHONY_SERVICE;
                } else if (serviceClass == CarrierConfigManager.class) {
                    return Context.CARRIER_CONFIG_SERVICE;
                } else if (serviceClass == PowerManager.class) {
                    return Context.POWER_SERVICE;
                } else if (serviceClass == ConnectivityManager.class) {
                    return Context.CONNECTIVITY_SERVICE;
                }
                return super.getSystemServiceName(serviceClass);
            }

            @Override
            public Object getSystemService(String name) {
                switch (name) {
                    case (Context.POWER_SERVICE) : {
                        return mPowerManager;
                    }
                    case (Context.CONNECTIVITY_SERVICE) : {
                        return mConnectivityManager;
                    }
                }
                return super.getSystemService(name);
            }

            @Override
            public String getOpPackageName() {
                return "";
            }

            @Override
            public Resources getResources() {
                return mResources;
            }
        };

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        mHandlerThread = new HandlerThread("EmergencyCallDomainSelectorTest");
        mHandlerThread.start();

        try {
            mLooper = new TestableLooper(mHandlerThread.getLooper());
        } catch (Exception e) {
            logd("Unable to create looper from handler.");
        }

        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        when(mTelephonyManager.createForSubscriptionId(anyInt()))
                .thenReturn(mTelephonyManager);
        when(mTelephonyManager.getNetworkCountryIso()).thenReturn("");
        when(mTelephonyManager.getSimState(anyInt())).thenReturn(TelephonyManager.SIM_STATE_READY);
        when(mTelephonyManager.getActiveModemCount()).thenReturn(1);

        mCarrierConfigManager = mContext.getSystemService(CarrierConfigManager.class);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg()))
            .thenReturn(getDefaultPersistableBundle());

        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                mNetworkCallback = (ConnectivityManager.NetworkCallback)
                        invocation.getArguments()[1];
                return null;
            }
        }).when(mConnectivityManager).registerNetworkCallback(
                any(NetworkRequest.class), any(ConnectivityManager.NetworkCallback.class));

        IPowerManager powerManager = mock(IPowerManager.class);
        mPowerManager = new PowerManager(mContext, powerManager, mock(IThermalService.class),
                new Handler(mHandlerThread.getLooper()));

        ImsManager imsManager = mContext.getSystemService(ImsManager.class);
        when(imsManager.getImsMmTelManager(anyInt())).thenReturn(mMmTelManager);
        when(mMmTelManager.isAdvancedCallingSettingEnabled()).thenReturn(true);
        doReturn(mProvisioningManager).when(imsManager).getProvisioningManager(anyInt());
        doReturn(null).when(mProvisioningManager).getProvisioningStringValue(anyInt());

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Consumer<WwanSelectorCallback> consumer =
                        (Consumer<WwanSelectorCallback>) invocation.getArguments()[0];
                consumer.accept(mWwanSelectorCallback);
                return null;
            }
        }).when(mTransportSelectorCallback).onWwanSelected(any());

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                mAccessNetwork = (List<Integer>) invocation.getArguments()[0];
                mResultConsumer =
                        (Consumer<EmergencyRegistrationResult>) invocation.getArguments()[4];
                return null;
            }
        }).when(mWwanSelectorCallback).onRequestEmergencyNetworkScan(
                any(), anyInt(), anyBoolean(), any(), any());

        when(mResources.getStringArray(anyInt())).thenReturn(null);

        doReturn(false).when(mCsrdCtrl).isThereOtherSlot();
    }

    @After
    public void tearDown() throws Exception {
        if (mDomainSelector != null) {
            mDomainSelector.destroy();
            mDomainSelector = null;
        }

        if (mLooper != null) {
            mLooper.destroy();
            mLooper = null;
        }
    }

    @SmallTest
    @Test
    public void testInit() throws Exception {
        createSelector(SLOT_0_SUB_ID);

        verify(mWwanSelectorCallback, times(0)).onRequestEmergencyNetworkScan(
                any(), anyInt(), anyBoolean(), any(), any());
        verify(mWwanSelectorCallback, times(0)).onDomainSelected(anyInt(), eq(true));
    }

    @Test
    public void testDestroyed() throws Exception {
        createSelector(SLOT_0_SUB_ID);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        mDomainSelector.destroy();
        mDomainSelector.handleMessage(mDomainSelector.obtainMessage(Integer.MAX_VALUE));
        unsolBarringInfoChanged(false);

        verify(mTransportSelectorCallback, never()).onWwanSelected(any());
    }

    @Test
    public void testDomainPreferenceConfigurationError() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_NON_3GPP,
                };
        bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(
                UNKNOWN, REGISTRATION_STATE_UNKNOWN, 0, false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyScanCsPreferred();
    }

    @Test
    public void testNullEmergencyRegistrationResult() throws Exception {
        doReturn(2).when(mTelephonyManager).getActiveModemCount();

        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, null);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyScanPsPreferred();
    }

    @Test
    public void testNoRedundantDomainSelectionFromInitialState() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();
        unsolBarringInfoChanged(false);

        processAllMessages();

        verify(mTransportSelectorCallback, times(1)).onWwanSelected(any());
        verify(mWwanSelectorCallback, times(1)).onDomainSelected(anyInt(), anyBoolean());
    }

    @Test
    public void testNoUnexpectedTransportChangeFromInitialState() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_NON_3GPP,
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP,
                CarrierConfigManager.ImsEmergency.DOMAIN_CS
                };
        bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();
        bindImsService(true);

        processAllMessages();

        verify(mTransportSelectorCallback, times(1)).onWwanSelected(any());
        verify(mTransportSelectorCallback, times(0)).onWlanSelected(anyBoolean());
    }

    @Test
    public void testNoRedundantScanRequestFromInitialState() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(
                UNKNOWN, REGISTRATION_STATE_UNKNOWN, 0, false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();
        unsolBarringInfoChanged(false);

        processAllMessages();

        verify(mTransportSelectorCallback, times(1)).onWwanSelected(any());
        verify(mWwanSelectorCallback, times(1)).onRequestEmergencyNetworkScan(
                any(), anyInt(), anyBoolean(), any(), any());
    }

    @Test
    public void testNoRedundantTerminationFromInitialState() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);
        doReturn(2).when(mTelephonyManager).getActiveModemCount();
        doReturn(TelephonyManager.SIM_STATE_PIN_REQUIRED)
                .when(mTelephonyManager).getSimState(anyInt());
        doReturn(true).when(mCsrdCtrl).isThereOtherSlot();
        doReturn(new String[] {"jp"}).when(mResources).getStringArray(anyInt());

        EmergencyRegistrationResult regResult = getEmergencyRegResult(
                UNKNOWN, REGISTRATION_STATE_UNKNOWN, 0, false, false, 0, 0, "", "", "jp");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();
        unsolBarringInfoChanged(false);

        verify(mTransportSelectorCallback, times(0)).onWlanSelected(anyBoolean());
        verify(mTransportSelectorCallback, times(0)).onWwanSelected(any());
        verify(mTransportSelectorCallback, times(1)).onSelectionTerminated(anyInt());
    }

    @Test
    public void testDefaultCombinedImsRegisteredBarredSelectCs() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();

        verifyCsDialed();
    }

    @Test
    public void testDefaultCombinedImsRegisteredSelectPs() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();

        verifyPsDialed();
    }

    @Test
    public void testDefaultCombinedImsRegisteredSelectPsThenCsfb() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();

        verifyPsDialed();

        mDomainSelector.reselectDomain(attr);
        processAllMessages();

        verifyCsDialed();
    }

    @Test
    public void testDefaultCombinedImsRegisteredSelectPsThenExtendedServiceRequestFails()
            throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();

        verifyPsDialed();

        mDomainSelector.reselectDomain(attr);
        processAllMessages();

        verifyCsDialed();

        //Extended service request failed
        SelectionAttributes.Builder builder =
                new SelectionAttributes.Builder(SLOT_0, SLOT_0_SUB_ID, SELECTOR_TYPE_CALLING)
                .setAddress(TEST_URI)
                .setCsDisconnectCause(SERVICE_OPTION_NOT_AVAILABLE)
                .setEmergency(true)
                .setEmergencyRegistrationResult(regResult);
        attr = builder.build();
        mDomainSelector.reselectDomain(attr);
        processAllMessages();

        verifyScanCsPreferred();
    }

    @Test
    public void testDefaultCombinedImsRegisteredSelectPsThenNotExtendedServiceRequestFails()
            throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();

        verifyPsDialed();

        mDomainSelector.reselectDomain(attr);
        processAllMessages();

        verifyCsDialed();

        SelectionAttributes.Builder builder =
                new SelectionAttributes.Builder(SLOT_0, SLOT_0_SUB_ID, SELECTOR_TYPE_CALLING)
                .setEmergency(true)
                .setEmergencyRegistrationResult(regResult);
        attr = builder.build();
        mDomainSelector.reselectDomain(attr);
        processAllMessages();

        verifyScanPsPreferred();
    }

    @Test
    public void testDefaultCombinedImsNotRegisteredDeactivatedSimSelectPs() throws Exception {
        doReturn(SIM_ACTIVATION_STATE_DEACTIVATED).when(mTelephonyManager).getDataActivationState();
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyPsDialed();
    }

    @Test
    public void testDefaultCombinedImsNotRegisteredSelectCs() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyCsDialed();
    }

    @Test
    public void testAirplaneDefaultCombinedImsNotRegisteredSelectPs() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = new SelectionAttributes.Builder(
                        SLOT_0, SLOT_0_SUB_ID, SELECTOR_TYPE_CALLING)
                .setAddress(TEST_URI)
                .setEmergency(true)
                .setEmergencyRegistrationResult(regResult)
                .setExitedFromAirplaneMode(true)
                .build();
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyPsDialed();
    }

    @Test
    public void testAirplaneRequiresRegCombinedImsNotRegisteredSelectPs() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putBoolean(KEY_EMERGENCY_REQUIRES_IMS_REGISTRATION_BOOL, true);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = new SelectionAttributes.Builder(
                        SLOT_0, SLOT_0_SUB_ID, SELECTOR_TYPE_CALLING)
                .setAddress(TEST_URI)
                .setEmergency(true)
                .setEmergencyRegistrationResult(regResult)
                .setExitedFromAirplaneMode(true)
                .build();
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyPsDialed();
    }

    @Test
    public void testNoCsCombinedImsNotRegisteredSelectPs() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putIntArray(KEY_EMERGENCY_OVER_CS_SUPPORTED_ACCESS_NETWORK_TYPES_INT_ARRAY,
                new int[0]);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyPsDialed();
    }

    @Test
    public void testDefaultCombinedImsNotRegisteredBarredSelectCs() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyCsDialed();
    }

    @Test
    public void testDefaultCombinedImsRegisteredEmsOffBarredSelectCs() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();

        verifyCsDialed();
    }

    @Test
    public void testDefaultCombinedImsRegisteredEmsOffSelectCs() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();

        verifyCsDialed();
    }

    @Test
    public void testDefaultCombinedImsNotRegisteredEmsOffSelectCs() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyCsDialed();
    }

    @Test
    public void testDefaultCombinedImsNotRegisteredEmsOffBarredSelectCs() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyCsDialed();
    }

    @Test
    public void testDefaultCombinedImsRegisteredVopsOffBarredSelectCs() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                false, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();

        verifyCsDialed();
    }

    @Test
    public void testDefaultCombinedImsRegisteredVopsOffSelectCs() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                false, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();

        verifyCsDialed();
    }

    @Test
    public void testDefaultCombinedImsNotRegisteredVopsOffSelectCs() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                false, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyCsDialed();
    }

    @Test
    public void testDefaultCombinedImsNotRegisteredVopsOffBarredSelectCs() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                false, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyCsDialed();
    }

    @Test
    public void testDefaultCombinedImsRegisteredVopsOffEmsOffBarredSelectCs() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();

        verifyCsDialed();
    }

    @Test
    public void testDefaultCombinedImsRegisteredVopsOffEmsOffSelectCs() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();

        verifyCsDialed();
    }

    @Test
    public void testDefaultCombinedImsNotRegisteredVopsOffEmsOffSelectCs() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyCsDialed();
    }

    @Test
    public void testDefaultCombinedImsNotRegisteredVopsOffEmsOffBarredSelectCs() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyCsDialed();
    }

    @Test
    public void testDefaultCsSelectCs() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyCsDialed();
    }

    @Test
    public void testNotSupportPsEmergency() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_CS
                };
        bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS,
                false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyCsDialed();

        mDomainSelector.reselectDomain(attr);
        processAllMessages();

        verify(mWwanSelectorCallback, times(1)).onRequestEmergencyNetworkScan(
                any(), anyInt(), anyBoolean(), any(), any());
        assertEquals(2, mAccessNetwork.size());
        assertEquals(UTRAN, (int) mAccessNetwork.get(0));
        assertEquals(GERAN, (int) mAccessNetwork.get(1));
    }

    @Test
    public void testNotSupportPsCombinedImsRegisteredSelectCs() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_CS
                };
        bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();

        verifyCsDialed();
    }

    @Test
    public void testNotSupportCsCombinedImsNotRegisteredSelectPs() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP
                };
        bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyPsDialed();
    }

    @Test
    public void testDefaultEpsImsRegisteredBarredScanPsPreferred() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();

        verifyScanPsPreferred();
    }

    @Test
    public void testDefaultEpsImsRegisteredSelectPs() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();

        verifyPsDialed();
    }

    @Test
    public void testDefaultEpsImsNotRegisteredSelectPs() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyPsDialed();
    }

    @Test
    public void testDefaultEpsImsNotRegisteredBarredSelectScanPsPreferred() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyScanPsPreferred();
    }

    @Test
    public void testDefaultEpsImsRegisteredEmsOffBarredScanPsPreferred() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();

        verifyScanPsPreferred();
    }

    @Test
    public void testDefaultEpsImsRegisteredEmsOffScanPsPreferred() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();

        verifyScanPsPreferred();
    }

    @Test
    public void testDefaultEpsImsNotRegisteredEmsOffScanPsPreferred() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyScanPsPreferred();
    }

    @Test
    public void testDefaultEpsImsNotRegisteredEmsOffBarredScanPsPreferred() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyScanPsPreferred();
    }

    @Test
    public void testDefaultEpsImsRegisteredVopsOffBarredScanPsPreferred() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                false, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();

        verifyScanPsPreferred();
    }

    @Test
    public void testDefaultEpsImsRegisteredVopsOffScanPsPreferred() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                false, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();

        verifyScanPsPreferred();
    }

    @Test
    public void testDefaultEpsImsNotRegisteredVopsOffScanPsPreferred() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                false, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyScanPsPreferred();
    }

    @Test
    public void testDefaultEpsImsNotRegisteredVopsOffBarredScanPsPreferred() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                false, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyScanPsPreferred();
    }

    @Test
    public void testDefaultEpsImsRegisteredVopsOffEmsOffBarredScanPsPreferred() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();

        verifyScanPsPreferred();
    }

    @Test
    public void testDefaultEpsImsRegisteredVopsOffEmsOffScanPsPreferred() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();

        verifyScanPsPreferred();
    }

    @Test
    public void testDefaultEpsImsNotRegisteredVopsOffEmsOffScanPsPreferred() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyScanPsPreferred();
    }

    @Test
    public void testDefaultEpsNotRegisteredVopsOffEmsOffBarredScanPsPreferred() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyScanPsPreferred();
    }

    @Test
    public void testDefaultOutOfServiceScanPsPreferred() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(
                UNKNOWN, REGISTRATION_STATE_UNKNOWN, 0, false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyScanPsPreferred();
    }

    @Test
    public void testVoLteOnEpsImsNotRegisteredSelectPs() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putBoolean(KEY_EMERGENCY_REQUIRES_VOLTE_ENABLED_BOOL, true);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        // Requires VoLTE enabled and VoLTE is enabled.
        verifyPsDialed();
    }

    @Test
    public void testVoLteOffEpsImsNotRegisteredScanCsPreferred() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putBoolean(KEY_EMERGENCY_REQUIRES_VOLTE_ENABLED_BOOL, true);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        // Disable VoLTE.
        when(mMmTelManager.isAdvancedCallingSettingEnabled()).thenReturn(false);

        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        // Requires VoLTE enabled but VoLTE is'nt enabled.
        verifyScanCsPreferred();
    }

    @Test
    public void testRequiresRegEpsImsNotRegisteredScanCsPreferred() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putBoolean(KEY_EMERGENCY_REQUIRES_IMS_REGISTRATION_BOOL, true);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyScanCsPreferred();
    }

    @Test
    public void testRequiresRegEpsImsNotRegisteredDeactivatedSimSelectPs() throws Exception {
        doReturn(SIM_ACTIVATION_STATE_DEACTIVATED).when(mTelephonyManager).getDataActivationState();

        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putBoolean(KEY_EMERGENCY_REQUIRES_IMS_REGISTRATION_BOOL, true);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyPsDialed();
    }

    @Test
    public void testRequiresRegEpsImsNotRegisteredEmcNotSupportedScanCsPreferred()
            throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putBoolean(KEY_EMERGENCY_REQUIRES_IMS_REGISTRATION_BOOL, true);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyScanCsPreferred();
    }

    @Test
    public void testDefaultEpsImsRegisteredBarredScanTimeoutWifi() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putBoolean(KEY_EMERGENCY_CALL_OVER_EMERGENCY_PDN_BOOL, true);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        mResultConsumer = null;
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService(true);

        verifyScanPsPreferred();

        assertTrue(mDomainSelector.hasMessages(MSG_NETWORK_SCAN_TIMEOUT));

        // Wi-Fi is not connected.
        verify(mTransportSelectorCallback, times(0)).onWlanSelected(anyBoolean());

        // Wi-Fi is connected.
        mNetworkCallback.onAvailable(null);
        mDomainSelector.handleMessage(mDomainSelector.obtainMessage(MSG_NETWORK_SCAN_TIMEOUT));

        verify(mTransportSelectorCallback, times(1)).onWlanSelected(eq(true));

        assertNotNull(mResultConsumer);

        mResultConsumer.accept(regResult);
        processAllMessages();

        // Ignore the stale result
        verify(mWwanSelectorCallback, never()).onDomainSelected(anyInt(), anyBoolean());
    }

    @Test
    public void testSimLockEpsImsRegisteredBarredScanNoTimeoutWifi() throws Exception {
        when(mTelephonyManager.getSimState(anyInt())).thenReturn(
                TelephonyManager.SIM_STATE_PIN_REQUIRED);
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putBoolean(KEY_EMERGENCY_CALL_OVER_EMERGENCY_PDN_BOOL, true);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService(true);

        verifyScanPsPreferred();

        assertFalse(mDomainSelector.hasMessages(MSG_NETWORK_SCAN_TIMEOUT));
    }

    @Test
    public void testVoWifiSosPdnRequiresSettingEnabled() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putBoolean(KEY_EMERGENCY_CALL_OVER_EMERGENCY_PDN_BOOL, true);
        bundle.putInt(KEY_EMERGENCY_VOWIFI_REQUIRES_CONDITION_INT, VOWIFI_REQUIRES_SETTING_ENABLED);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        bindImsServiceUnregistered();
        processAllMessages();

        assertTrue(mDomainSelector.hasMessages(MSG_NETWORK_SCAN_TIMEOUT));

        mDomainSelector.handleMessage(mDomainSelector.obtainMessage(MSG_NETWORK_SCAN_TIMEOUT));

        // Wi-Fi is not connected.
        verify(mTransportSelectorCallback, times(0)).onWlanSelected(anyBoolean());

        // Wi-Fi is connected. But Wi-Fi calling setting is disabled.
        mNetworkCallback.onAvailable(null);
        when(mMmTelManager.isVoWiFiRoamingSettingEnabled()).thenReturn(false);
        mDomainSelector.handleMessage(mDomainSelector.obtainMessage(MSG_NETWORK_SCAN_TIMEOUT));

        verify(mTransportSelectorCallback, times(0)).onWlanSelected(anyBoolean());

        // Wi-Fi is connected and Wi-Fi calling setting is enabled.
        when(mMmTelManager.isVoWiFiSettingEnabled()).thenReturn(true);
        mDomainSelector.handleMessage(mDomainSelector.obtainMessage(MSG_NETWORK_SCAN_TIMEOUT));

        verify(mTransportSelectorCallback, times(1)).onWlanSelected(eq(true));
    }

    @Test
    public void testVoWifiSosPdnRequiresValidEid() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putBoolean(KEY_EMERGENCY_CALL_OVER_EMERGENCY_PDN_BOOL, true);
        bundle.putInt(KEY_EMERGENCY_VOWIFI_REQUIRES_CONDITION_INT, VOWIFI_REQUIRES_VALID_EID);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        bindImsServiceUnregistered();
        processAllMessages();

        assertTrue(mDomainSelector.hasMessages(MSG_NETWORK_SCAN_TIMEOUT));

        mDomainSelector.handleMessage(mDomainSelector.obtainMessage(MSG_NETWORK_SCAN_TIMEOUT));

        // Wi-Fi is not connected.
        verify(mTransportSelectorCallback, times(0)).onWlanSelected(anyBoolean());

        // Wi-Fi is connected. But Wi-Fi calling s not activated.
        mNetworkCallback.onAvailable(null);
        mDomainSelector.handleMessage(mDomainSelector.obtainMessage(MSG_NETWORK_SCAN_TIMEOUT));

        verify(mTransportSelectorCallback, times(0)).onWlanSelected(anyBoolean());

        // Wi-Fi is connected and Wi-Fi calling is activated.
        doReturn("1").when(mProvisioningManager).getProvisioningStringValue(anyInt());
        mDomainSelector.handleMessage(mDomainSelector.obtainMessage(MSG_NETWORK_SCAN_TIMEOUT));

        verify(mTransportSelectorCallback, times(1)).onWlanSelected(eq(true));
    }

    @Test
    public void testVoWifiImsPdnRequiresNone() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        bindImsServiceUnregistered();
        processAllMessages();

        assertTrue(mDomainSelector.hasMessages(MSG_NETWORK_SCAN_TIMEOUT));

        mDomainSelector.handleMessage(mDomainSelector.obtainMessage(MSG_NETWORK_SCAN_TIMEOUT));

        // Wi-Fi is not connected.
        verify(mTransportSelectorCallback, times(0)).onWlanSelected(anyBoolean());

        // Wi-Fi is connected but IMS is not registered over Wi-Fi.
        mNetworkCallback.onAvailable(null);
        mDomainSelector.handleMessage(mDomainSelector.obtainMessage(MSG_NETWORK_SCAN_TIMEOUT));

        verify(mTransportSelectorCallback, times(0)).onWlanSelected(anyBoolean());

        // IMS is registered over Wi-Fi.
        bindImsService(true);
        mDomainSelector.handleMessage(mDomainSelector.obtainMessage(MSG_NETWORK_SCAN_TIMEOUT));

        verify(mTransportSelectorCallback, times(1)).onWlanSelected(eq(false));
    }

    @Test
    public void testIgnoreDuplicatedCallbacks() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService(true);

        verify(mTransportSelectorCallback, times(1)).onWwanSelected(any());

        // duplicated event
        unsolBarringInfoChanged(true);

        // ignore duplicated callback, no change in interaction
        verify(mTransportSelectorCallback, times(1)).onWwanSelected(any());

        mDomainSelector.handleMessage(mDomainSelector.obtainMessage(MSG_NETWORK_SCAN_TIMEOUT));

        verify(mTransportSelectorCallback, times(1)).onWlanSelected(eq(false));

        // duplicated event
        mDomainSelector.handleMessage(mDomainSelector.obtainMessage(MSG_NETWORK_SCAN_TIMEOUT));

        // ignore duplicated callback, no change in interaction
        verify(mTransportSelectorCallback, times(1)).onWlanSelected(anyBoolean());
    }

    @Test
    public void testDualSimInvalidSubscription() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);
        doReturn(2).when(mTelephonyManager).getActiveModemCount();
        doReturn(TelephonyManager.SIM_STATE_PIN_REQUIRED)
                .when(mTelephonyManager).getSimState(anyInt());
        doReturn(true).when(mCsrdCtrl).isThereOtherSlot();
        doReturn(new String[] {"jp"}).when(mResources).getStringArray(anyInt());

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_UNKNOWN,
                0, false, false, 0, 0, "", "", "jp");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();
        processAllMessages();

        verify(mTransportSelectorCallback, times(1))
                .onSelectionTerminated(eq(DisconnectCause.EMERGENCY_PERM_FAILURE));
    }

    @Test
    public void testDualSimInvalidSubscriptionAfterScan() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);
        doReturn(2).when(mTelephonyManager).getActiveModemCount();
        doReturn(TelephonyManager.SIM_STATE_PIN_REQUIRED)
                .when(mTelephonyManager).getSimState(anyInt());
        doReturn(true).when(mCsrdCtrl).isThereOtherSlot();
        doReturn(new String[] {"jp"}).when(mResources).getStringArray(anyInt());

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UNKNOWN,
                REGISTRATION_STATE_UNKNOWN,
                0, false, false, 0, 0, "", "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();
        processAllMessages();

        assertNotNull(mResultConsumer);

        regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_UNKNOWN,
                0, false, false, 0, 0, "", "", "jp");
        mResultConsumer.accept(regResult);
        processAllMessages();

        verify(mTransportSelectorCallback, times(1))
                .onSelectionTerminated(eq(DisconnectCause.EMERGENCY_PERM_FAILURE));
    }

    @Test
    public void testDualSimInvalidSubscriptionButNoOtherSlot() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);
        doReturn(2).when(mTelephonyManager).getActiveModemCount();
        doReturn(TelephonyManager.SIM_STATE_PIN_REQUIRED)
                .when(mTelephonyManager).getSimState(anyInt());
        doReturn(false).when(mCsrdCtrl).isThereOtherSlot();
        doReturn(new String[] {"jp"}).when(mResources).getStringArray(anyInt());

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_UNKNOWN,
                0, false, false, 0, 0, "", "", "jp");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();
        processAllMessages();

        verify(mTransportSelectorCallback, times(0))
                .onSelectionTerminated(eq(DisconnectCause.EMERGENCY_PERM_FAILURE));
        verifyScanPsPreferred();
    }

    @Test
    public void testDualSimNormalServiceOnTheOtherSubscription() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);
        doReturn(2).when(mTelephonyManager).getActiveModemCount();
        doReturn(true).when(mCsrdCtrl).isThereOtherSlotInService();
        doReturn(new String[] {"in"}).when(mResources).getStringArray(anyInt());

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_UNKNOWN,
                0, false, false, 0, 0, "", "", "in");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();
        processAllMessages();

        verify(mTransportSelectorCallback, times(1))
                .onSelectionTerminated(eq(DisconnectCause.EMERGENCY_TEMP_FAILURE));
    }

    @Test
    public void testEutranWithCsDomainOnly() throws Exception {
        setupForHandleScanResult();

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                DOMAIN_CS, false, false, 0, 0, "", "");
        mResultConsumer.accept(regResult);
        processAllMessages();

        verifyCsDialed();
    }

    @Test
    public void testEutranWithPsDomainOnly() throws Exception {
        setupForHandleScanResult();

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                DOMAIN_PS, false, false, 0, 0, "", "");
        mResultConsumer.accept(regResult);
        processAllMessages();

        verifyPsDialed();
    }

    @Test
    public void testUtran() throws Exception {
        setupForHandleScanResult();

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UTRAN,
                REGISTRATION_STATE_HOME,
                DOMAIN_CS, false, false, 0, 0, "", "");
        mResultConsumer.accept(regResult);
        processAllMessages();

        verifyCsDialed();
    }

    @Test
    public void testFullService() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putInt(KEY_EMERGENCY_NETWORK_SCAN_TYPE_INT, SCAN_TYPE_FULL_SERVICE);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        mResultConsumer = null;
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UNKNOWN,
                REGISTRATION_STATE_UNKNOWN,
                0, false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();
        processAllMessages();

        verify(mWwanSelectorCallback, times(1)).onRequestEmergencyNetworkScan(
                any(), eq(DomainSelectionService.SCAN_TYPE_FULL_SERVICE), eq(false), any(), any());
        assertNotNull(mResultConsumer);

        mResultConsumer.accept(regResult);
        processAllMessages();

        verify(mWwanSelectorCallback, times(2)).onRequestEmergencyNetworkScan(
                any(), eq(DomainSelectionService.SCAN_TYPE_FULL_SERVICE), eq(false), any(), any());
    }

    @Test
    public void testFullServiceInRoaming() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putInt(KEY_EMERGENCY_NETWORK_SCAN_TYPE_INT, SCAN_TYPE_FULL_SERVICE);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_ROAMING,
                0, true, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();
        processAllMessages();

        verify(mWwanSelectorCallback).onRequestEmergencyNetworkScan(
                any(), eq(DomainSelectionService.SCAN_TYPE_NO_PREFERENCE),
                anyBoolean(), any(), any());
    }

    @Test
    public void testFullServiceThenLimitedService() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putInt(KEY_EMERGENCY_NETWORK_SCAN_TYPE_INT,
                SCAN_TYPE_FULL_SERVICE_FOLLOWED_BY_LIMITED_SERVICE);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        mResultConsumer = null;
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UNKNOWN,
                REGISTRATION_STATE_UNKNOWN,
                0, false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();
        processAllMessages();

        verify(mWwanSelectorCallback, times(1)).onRequestEmergencyNetworkScan(
                any(), eq(DomainSelectionService.SCAN_TYPE_FULL_SERVICE), eq(false), any(), any());
        assertNotNull(mResultConsumer);

        mResultConsumer.accept(regResult);
        processAllMessages();

        verify(mWwanSelectorCallback, times(1)).onRequestEmergencyNetworkScan(
                any(), eq(DomainSelectionService.SCAN_TYPE_LIMITED_SERVICE),
                eq(false), any(), any());
    }

    @Test
    public void testCsThenPsPreference() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_CS,
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP,
                };
        bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);

        setupForScanListTest(bundle);

        verifyCsPreferredScanList(mDomainSelector.getNextPreferredNetworks(false, false));
    }

    @Test
    public void testPsThenCsPreference() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP,
                CarrierConfigManager.ImsEmergency.DOMAIN_CS,
                };
        bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);

        setupForScanListTest(bundle);

        verifyPsPreferredScanList(mDomainSelector.getNextPreferredNetworks(false, false));
    }

    @Test
    public void testPsOnlyPreference() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP,
                };
        bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);
        bundle.putIntArray(KEY_EMERGENCY_OVER_CS_SUPPORTED_ACCESS_NETWORK_TYPES_INT_ARRAY,
                new int[0]);

        setupForScanListTest(bundle);

        verifyPsOnlyScanList(mDomainSelector.getNextPreferredNetworks(false, false));
    }

    @Test
    public void testCsOnlyPreference() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_CS,
                };
        bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);
        bundle.putIntArray(KEY_EMERGENCY_OVER_IMS_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY,
                new int[0]);

        setupForScanListTest(bundle);

        verifyCsOnlyScanList(mDomainSelector.getNextPreferredNetworks(false, false));

    }

    @Test
    public void testCsThenPsPreferenceCsPreferred() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_CS,
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP,
                };
        bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);

        setupForScanListTest(bundle);

        verifyCsPreferredScanList(mDomainSelector.getNextPreferredNetworks(true, false));
    }

    @Test
    public void testPsThenCsPreferenceCsPreferred() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP,
                CarrierConfigManager.ImsEmergency.DOMAIN_CS,
                };
        bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);

        setupForScanListTest(bundle);

        verifyCsPreferredScanList(mDomainSelector.getNextPreferredNetworks(true, false));
    }

    @Test
    public void testPsOnlyPreferenceCsPreferred() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP,
                CarrierConfigManager.ImsEmergency.DOMAIN_CS,
                };
        bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);
        bundle.putIntArray(KEY_EMERGENCY_OVER_CS_SUPPORTED_ACCESS_NETWORK_TYPES_INT_ARRAY,
                new int[0]);

        setupForScanListTest(bundle);

        verifyPsOnlyScanList(mDomainSelector.getNextPreferredNetworks(true, false));
    }

    @Test
    public void testCsOnlyPreferenceCsPreferred() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_CS,
                };
        bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);
        bundle.putIntArray(KEY_EMERGENCY_OVER_IMS_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY,
                new int[0]);

        setupForScanListTest(bundle);

        verifyCsOnlyScanList(mDomainSelector.getNextPreferredNetworks(true, false));
    }

    @Test
    public void testCsThenPsPreferencePsFail() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_CS,
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP,
                };
        bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);

        setupForScanListTest(bundle, true);

        bindImsService();
        processAllMessages();

        verifyCsPreferredScanList(mDomainSelector.getNextPreferredNetworks(false, false));
    }

    @Test
    public void testPsThenCsPreferencePsFail() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP,
                CarrierConfigManager.ImsEmergency.DOMAIN_CS,
                };
        bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);

        setupForScanListTest(bundle, true);

        bindImsService();
        processAllMessages();

        verifyCsPreferredScanList(mDomainSelector.getNextPreferredNetworks(false, false));
    }

    @Test
    public void testPsOnlyPreferencePsFail() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP,
                };
        bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);
        bundle.putIntArray(KEY_EMERGENCY_OVER_CS_SUPPORTED_ACCESS_NETWORK_TYPES_INT_ARRAY,
                new int[0]);

        setupForScanListTest(bundle, true);

        bindImsService();
        processAllMessages();

        verifyPsOnlyScanList(mDomainSelector.getNextPreferredNetworks(false, false));
    }

    @Test
    public void testCsThenPsPreferencePsFailCsPreferred() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_CS,
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP,
                };
        bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);

        setupForScanListTest(bundle, true);

        bindImsService();
        processAllMessages();

        verifyCsPreferredScanList(mDomainSelector.getNextPreferredNetworks(true, false));
    }

    @Test
    public void testPsThenCsPreferencePsFailCsPreferred() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP,
                CarrierConfigManager.ImsEmergency.DOMAIN_CS,
                };
        bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);

        setupForScanListTest(bundle, true);

        bindImsService();
        processAllMessages();

        verifyCsPreferredScanList(mDomainSelector.getNextPreferredNetworks(true, false));
    }

    @Test
    public void testPsOnlyPreferencePsFailCsPreferred() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP,
                CarrierConfigManager.ImsEmergency.DOMAIN_CS,
                };
        bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);
        bundle.putIntArray(KEY_EMERGENCY_OVER_CS_SUPPORTED_ACCESS_NETWORK_TYPES_INT_ARRAY,
                new int[0]);

        setupForScanListTest(bundle, true);

        bindImsService();
        processAllMessages();

        verifyPsOnlyScanList(mDomainSelector.getNextPreferredNetworks(true, false));
    }

    @Test
    public void testEpsFallbackThenCsPreference() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP,
                CarrierConfigManager.ImsEmergency.DOMAIN_CS,
                };
        bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);
        bundle.putIntArray(KEY_EMERGENCY_OVER_IMS_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY,
                    new int[] { NGRAN, EUTRAN });

        setupForScanListTest(bundle);

        List<Integer> networks = mDomainSelector.getNextPreferredNetworks(false, true);

        assertFalse(networks.isEmpty());
        assertTrue(networks.contains(EUTRAN));
        assertTrue(networks.contains(NGRAN));
        assertTrue(networks.contains(UTRAN));
        assertTrue(networks.contains(GERAN));
        assertTrue(networks.indexOf(EUTRAN) < networks.indexOf(UTRAN));
        assertTrue(networks.indexOf(UTRAN) < networks.indexOf(GERAN));
        assertTrue(networks.indexOf(GERAN) < networks.indexOf(NGRAN));
    }

    @Test
    public void testScanLimitedOnlyAfterVoLteFailure() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putBoolean(KEY_SCAN_LIMITED_SERVICE_AFTER_VOLTE_FAILURE_BOOL,
                true);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();
        processAllMessages();

        verify(mWwanSelectorCallback).onDomainSelected(eq(DOMAIN_PS), anyBoolean());

        mDomainSelector.reselectDomain(attr);
        processAllMessages();

        verify(mWwanSelectorCallback).onRequestEmergencyNetworkScan(
                any(), eq(DomainSelectionService.SCAN_TYPE_LIMITED_SERVICE),
                anyBoolean(), any(), any());
    }

    @Test
    public void testStartCrossStackTimer() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);
        doReturn(2).when(mTelephonyManager).getActiveModemCount();

        EmergencyRegistrationResult regResult = getEmergencyRegResult(
                UNKNOWN, REGISTRATION_STATE_UNKNOWN, 0, false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        processAllMessages();
        verify(mCsrdCtrl).startTimer(any(), eq(mDomainSelector), any(),
                any(), anyBoolean(), anyBoolean(), anyInt());
    }

    @Test
    public void testStopCrossStackTimerOnFinish() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        mDomainSelector.finishSelection();

        verify(mCsrdCtrl).stopTimer();
    }

    @Test
    public void testCrossStackTimerTempFailure() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);
        doReturn(true).when(mCsrdCtrl).isThereOtherSlot();

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyCsDialed();

        attr = new SelectionAttributes.Builder(SLOT_0, SLOT_0_SUB_ID, SELECTOR_TYPE_CALLING)
                .setAddress(TEST_URI)
                .setEmergency(true)
                .setEmergencyRegistrationResult(regResult)
                .setCsDisconnectCause(PreciseDisconnectCause.EMERGENCY_TEMP_FAILURE)
                .build();

        mDomainSelector.reselectDomain(attr);
        processAllMessages();

        verify(mCsrdCtrl).notifyCallFailure(eq(PreciseDisconnectCause.EMERGENCY_TEMP_FAILURE));
        verify(mTransportSelectorCallback)
                .onSelectionTerminated(eq(DisconnectCause.EMERGENCY_TEMP_FAILURE));
    }

    @Test
    public void testCrossStackTimerTempFailureNoValidSubscription() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);
        doReturn(false).when(mCsrdCtrl).isThereOtherSlot();

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyCsDialed();

        attr = new SelectionAttributes.Builder(SLOT_0, SLOT_0_SUB_ID, SELECTOR_TYPE_CALLING)
                .setAddress(TEST_URI)
                .setEmergency(true)
                .setEmergencyRegistrationResult(regResult)
                .setCsDisconnectCause(PreciseDisconnectCause.EMERGENCY_TEMP_FAILURE)
                .build();

        mDomainSelector.reselectDomain(attr);
        processAllMessages();

        verify(mCsrdCtrl).notifyCallFailure(eq(PreciseDisconnectCause.EMERGENCY_TEMP_FAILURE));
        verify(mTransportSelectorCallback, never())
                .onSelectionTerminated(eq(DisconnectCause.EMERGENCY_TEMP_FAILURE));
    }

    @Test
    public void testCrossStackTimerPermFailure() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);
        doReturn(true).when(mCsrdCtrl).isThereOtherSlot();

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyCsDialed();

        attr = new SelectionAttributes.Builder(SLOT_0, SLOT_0_SUB_ID, SELECTOR_TYPE_CALLING)
                .setAddress(TEST_URI)
                .setEmergency(true)
                .setEmergencyRegistrationResult(regResult)
                .setCsDisconnectCause(PreciseDisconnectCause.EMERGENCY_PERM_FAILURE)
                .build();

        mDomainSelector.reselectDomain(attr);
        processAllMessages();

        verify(mCsrdCtrl).notifyCallFailure(eq(PreciseDisconnectCause.EMERGENCY_PERM_FAILURE));
        verify(mTransportSelectorCallback)
                .onSelectionTerminated(eq(DisconnectCause.EMERGENCY_PERM_FAILURE));
    }

    @Test
    public void testCrossStackTimerPermFailureNoValidSubscription() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);
        doReturn(false).when(mCsrdCtrl).isThereOtherSlot();

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyCsDialed();

        attr = new SelectionAttributes.Builder(SLOT_0, SLOT_0_SUB_ID, SELECTOR_TYPE_CALLING)
                .setAddress(TEST_URI)
                .setEmergency(true)
                .setEmergencyRegistrationResult(regResult)
                .setCsDisconnectCause(PreciseDisconnectCause.EMERGENCY_PERM_FAILURE)
                .build();

        mDomainSelector.reselectDomain(attr);
        processAllMessages();

        verify(mCsrdCtrl).notifyCallFailure(eq(PreciseDisconnectCause.EMERGENCY_PERM_FAILURE));
        verify(mTransportSelectorCallback, never())
                .onSelectionTerminated(eq(DisconnectCause.EMERGENCY_PERM_FAILURE));
    }

    @Test
    public void testCrossStackTimerExpired() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(
                UNKNOWN, REGISTRATION_STATE_UNKNOWN, 0, false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();
        processAllMessages();

        ArgumentCaptor<CancellationSignal> cancelCaptor =
                ArgumentCaptor.forClass(CancellationSignal.class);

        verify(mWwanSelectorCallback, times(1)).onRequestEmergencyNetworkScan(
                any(), eq(DomainSelectionService.SCAN_TYPE_NO_PREFERENCE),
                anyBoolean(), cancelCaptor.capture(), any());
        assertEquals(EUTRAN, (int) mAccessNetwork.get(0));

        mDomainSelector.notifyCrossStackTimerExpired();

        verify(mTransportSelectorCallback)
                .onSelectionTerminated(eq(DisconnectCause.EMERGENCY_TEMP_FAILURE));

        CancellationSignal cancelSignal = cancelCaptor.getValue();
        assertNotNull(cancelSignal);
        assertFalse(cancelSignal.isCanceled());
    }

    @Test
    public void testCrossStackTimerExpiredAfterDomainSelected() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyCsDialed();

        mDomainSelector.notifyCrossStackTimerExpired();

        verify(mTransportSelectorCallback, times(0))
                .onSelectionTerminated(eq(DisconnectCause.EMERGENCY_TEMP_FAILURE));

        mDomainSelector.reselectDomain(attr);
        processAllMessages();

        verify(mTransportSelectorCallback)
                .onSelectionTerminated(eq(DisconnectCause.EMERGENCY_TEMP_FAILURE));
    }

    @Test
    public void testMaxCellularTimeout() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putBoolean(KEY_EMERGENCY_CALL_OVER_EMERGENCY_PDN_BOOL, true);
        bundle.putInt(KEY_MAXIMUM_CELLULAR_SEARCH_TIMER_SEC_INT, 20);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        setupForHandleScanResult();

        assertTrue(mDomainSelector.hasMessages(MSG_NETWORK_SCAN_TIMEOUT));
        assertTrue(mDomainSelector.hasMessages(MSG_MAX_CELLULAR_TIMEOUT));

        verify(mTransportSelectorCallback, times(0)).onWlanSelected(anyBoolean());

        // Wi-Fi is connected.
        mNetworkCallback.onAvailable(null);

        // Max cellular timer expired
        mDomainSelector.removeMessages(MSG_MAX_CELLULAR_TIMEOUT);
        mDomainSelector.handleMessage(mDomainSelector.obtainMessage(MSG_MAX_CELLULAR_TIMEOUT));

        assertFalse(mDomainSelector.hasMessages(MSG_NETWORK_SCAN_TIMEOUT));
        verify(mTransportSelectorCallback, times(1)).onWlanSelected(anyBoolean());
    }

    @Test
    public void testMaxCellularTimeoutWifiNotAvailable() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putBoolean(KEY_EMERGENCY_CALL_OVER_EMERGENCY_PDN_BOOL, true);
        bundle.putInt(KEY_MAXIMUM_CELLULAR_SEARCH_TIMER_SEC_INT, 20);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        setupForHandleScanResult();

        assertTrue(mDomainSelector.hasMessages(MSG_NETWORK_SCAN_TIMEOUT));
        assertTrue(mDomainSelector.hasMessages(MSG_MAX_CELLULAR_TIMEOUT));

        verify(mTransportSelectorCallback, never()).onWlanSelected(anyBoolean());

        // Max cellular timer expired
        mDomainSelector.removeMessages(MSG_MAX_CELLULAR_TIMEOUT);
        mDomainSelector.handleMessage(mDomainSelector.obtainMessage(MSG_MAX_CELLULAR_TIMEOUT));

        assertTrue(mDomainSelector.hasMessages(MSG_NETWORK_SCAN_TIMEOUT));
        verify(mTransportSelectorCallback, never()).onWlanSelected(anyBoolean());
    }

    @Test
    public void testSimLockNoMaxCellularTimeout() throws Exception {
        when(mTelephonyManager.getSimState(anyInt())).thenReturn(
                TelephonyManager.SIM_STATE_PIN_REQUIRED);
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putBoolean(KEY_EMERGENCY_CALL_OVER_EMERGENCY_PDN_BOOL, true);
        bundle.putInt(KEY_MAXIMUM_CELLULAR_SEARCH_TIMER_SEC_INT, 20);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        setupForHandleScanResult();

        assertFalse(mDomainSelector.hasMessages(MSG_NETWORK_SCAN_TIMEOUT));
        assertFalse(mDomainSelector.hasMessages(MSG_MAX_CELLULAR_TIMEOUT));
    }

    @Test
    public void testMaxCellularTimeoutScanTimeout() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putBoolean(KEY_EMERGENCY_CALL_OVER_EMERGENCY_PDN_BOOL, true);
        bundle.putInt(KEY_MAXIMUM_CELLULAR_SEARCH_TIMER_SEC_INT, 20);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        setupForHandleScanResult();

        assertTrue(mDomainSelector.hasMessages(MSG_NETWORK_SCAN_TIMEOUT));
        assertTrue(mDomainSelector.hasMessages(MSG_MAX_CELLULAR_TIMEOUT));

        verify(mTransportSelectorCallback, times(0)).onWlanSelected(anyBoolean());

        // Wi-Fi is connected.
        mNetworkCallback.onAvailable(null);

        // Scan timer expired
        mDomainSelector.removeMessages(MSG_NETWORK_SCAN_TIMEOUT);
        mDomainSelector.handleMessage(mDomainSelector.obtainMessage(MSG_NETWORK_SCAN_TIMEOUT));

        assertFalse(mDomainSelector.hasMessages(MSG_MAX_CELLULAR_TIMEOUT));
        verify(mTransportSelectorCallback, times(1)).onWlanSelected(anyBoolean());
    }

    @Test
    public void testMaxCellularTimeoutWhileDialingOnCellular() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putBoolean(KEY_EMERGENCY_CALL_OVER_EMERGENCY_PDN_BOOL, true);
        bundle.putInt(KEY_MAXIMUM_CELLULAR_SEARCH_TIMER_SEC_INT, 5);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyCsDialed();

        assertFalse(mDomainSelector.hasMessages(MSG_NETWORK_SCAN_TIMEOUT));
        assertFalse(mDomainSelector.hasMessages(MSG_MAX_CELLULAR_TIMEOUT));

        mDomainSelector.reselectDomain(attr);
        processAllMessages();

        verify(mWwanSelectorCallback, times(1)).onRequestEmergencyNetworkScan(
                any(), anyInt(), anyBoolean(), any(), any());
        assertTrue(mDomainSelector.hasMessages(MSG_NETWORK_SCAN_TIMEOUT));
        assertTrue(mDomainSelector.hasMessages(MSG_MAX_CELLULAR_TIMEOUT));

        // Wi-Fi is connected.
        mNetworkCallback.onAvailable(null);
        processAllMessages();

        verify(mTransportSelectorCallback, times(0)).onWlanSelected(anyBoolean());

        // Max cellular timer expired
        mDomainSelector.removeMessages(MSG_MAX_CELLULAR_TIMEOUT);
        mDomainSelector.handleMessage(mDomainSelector.obtainMessage(MSG_MAX_CELLULAR_TIMEOUT));
        processAllMessages();

        assertFalse(mDomainSelector.hasMessages(MSG_MAX_CELLULAR_TIMEOUT));
        verify(mTransportSelectorCallback, times(1)).onWlanSelected(anyBoolean());

        mDomainSelector.reselectDomain(attr);
        processAllMessages();

        verify(mWwanSelectorCallback, times(2)).onRequestEmergencyNetworkScan(
                any(), anyInt(), anyBoolean(), any(), any());
    }

    @Test
    public void testMaxCellularTimeoutWhileDialingOnCellularWhileDialing() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putBoolean(KEY_EMERGENCY_CALL_OVER_EMERGENCY_PDN_BOOL, true);
        bundle.putInt(KEY_MAXIMUM_CELLULAR_SEARCH_TIMER_SEC_INT, 5);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyCsDialed();

        assertFalse(mDomainSelector.hasMessages(MSG_NETWORK_SCAN_TIMEOUT));
        assertFalse(mDomainSelector.hasMessages(MSG_MAX_CELLULAR_TIMEOUT));

        mResultConsumer = null;
        mDomainSelector.reselectDomain(attr);
        processAllMessages();

        verify(mWwanSelectorCallback, times(1)).onRequestEmergencyNetworkScan(
                any(), anyInt(), anyBoolean(), any(), any());
        assertTrue(mDomainSelector.hasMessages(MSG_NETWORK_SCAN_TIMEOUT));
        assertTrue(mDomainSelector.hasMessages(MSG_MAX_CELLULAR_TIMEOUT));

        assertNotNull(mResultConsumer);

        // Scan result received and redialing on cellular
        mResultConsumer.accept(regResult);
        processAllMessages();

        // Wi-Fi is connected.
        mNetworkCallback.onAvailable(null);
        processAllMessages();

        verify(mTransportSelectorCallback, times(0)).onWlanSelected(anyBoolean());

        // Max cellular timer expired
        mDomainSelector.removeMessages(MSG_MAX_CELLULAR_TIMEOUT);
        mDomainSelector.handleMessage(mDomainSelector.obtainMessage(MSG_MAX_CELLULAR_TIMEOUT));
        processAllMessages();

        assertFalse(mDomainSelector.hasMessages(MSG_MAX_CELLULAR_TIMEOUT));

        // Waiting for reselectDomain since there is a dialing on going.
        verify(mTransportSelectorCallback, times(0)).onWlanSelected(anyBoolean());

        // Wi-Fi is disconnected.
        mNetworkCallback.onUnavailable();
        processAllMessages();

        mResultConsumer = null;
        mDomainSelector.reselectDomain(attr);
        processAllMessages();

        verify(mTransportSelectorCallback, times(0)).onWlanSelected(anyBoolean());
        verify(mWwanSelectorCallback, times(2)).onRequestEmergencyNetworkScan(
                any(), anyInt(), anyBoolean(), any(), any());

        // Wi-Fi is re-connected.
        mNetworkCallback.onAvailable(null);
        processAllMessages();

        mResultConsumer = null;
        mDomainSelector.reselectDomain(attr);
        processAllMessages();

        verify(mTransportSelectorCallback, times(1)).onWlanSelected(anyBoolean());
    }

    @Test
    public void testMaxCellularTimeoutWileDialingOnWlan() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putBoolean(KEY_EMERGENCY_CALL_OVER_EMERGENCY_PDN_BOOL, true);
        bundle.putInt(KEY_MAXIMUM_CELLULAR_SEARCH_TIMER_SEC_INT, 20);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        setupForHandleScanResult();

        assertTrue(mDomainSelector.hasMessages(MSG_NETWORK_SCAN_TIMEOUT));
        assertTrue(mDomainSelector.hasMessages(MSG_MAX_CELLULAR_TIMEOUT));

        verify(mTransportSelectorCallback, times(0)).onWlanSelected(anyBoolean());

        // Wi-Fi is connected.
        mNetworkCallback.onAvailable(null);

        // Network scan timer expired
        mDomainSelector.removeMessages(MSG_NETWORK_SCAN_TIMEOUT);
        mDomainSelector.handleMessage(mDomainSelector.obtainMessage(MSG_NETWORK_SCAN_TIMEOUT));

        verify(mTransportSelectorCallback, times(1)).onWlanSelected(anyBoolean());
        assertFalse(mDomainSelector.hasMessages(MSG_MAX_CELLULAR_TIMEOUT));
    }

    @Test
    public void testMaxCellularTimeoutWileDialingOnWlanAllowMultipleTries() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putBoolean(KEY_EMERGENCY_CALL_OVER_EMERGENCY_PDN_BOOL, true);
        bundle.putInt(KEY_MAXIMUM_CELLULAR_SEARCH_TIMER_SEC_INT, 20);
        bundle.putInt(KEY_MAXIMUM_NUMBER_OF_EMERGENCY_TRIES_OVER_VOWIFI_INT, 2);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        setupForHandleScanResult();

        assertTrue(mDomainSelector.hasMessages(MSG_NETWORK_SCAN_TIMEOUT));
        assertTrue(mDomainSelector.hasMessages(MSG_MAX_CELLULAR_TIMEOUT));

        verify(mTransportSelectorCallback, times(0)).onWlanSelected(anyBoolean());

        // Wi-Fi is connected.
        mNetworkCallback.onAvailable(null);

        // Network scan timer expired
        mDomainSelector.removeMessages(MSG_NETWORK_SCAN_TIMEOUT);
        mDomainSelector.handleMessage(mDomainSelector.obtainMessage(MSG_NETWORK_SCAN_TIMEOUT));

        verify(mTransportSelectorCallback, times(1)).onWlanSelected(anyBoolean());
        assertFalse(mDomainSelector.hasMessages(MSG_MAX_CELLULAR_TIMEOUT));

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UTRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS,
                true, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.reselectDomain(attr);
        processAllMessages();

        assertTrue(mDomainSelector.hasMessages(MSG_MAX_CELLULAR_TIMEOUT));

        // Max cellular timer expired
        mDomainSelector.removeMessages(MSG_MAX_CELLULAR_TIMEOUT);
        mDomainSelector.handleMessage(mDomainSelector.obtainMessage(MSG_MAX_CELLULAR_TIMEOUT));
        processAllMessages();

        verify(mTransportSelectorCallback, times(2)).onWlanSelected(anyBoolean());
    }

    @Test
    public void testSimLockScanPsPreferredWithNr() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        // The last valid subscription supported NR.
        doReturn(true).when(mCarrierConfigHelper).isVoNrEmergencySupported(eq(SLOT_0));
        when(mTelephonyManager.getSimState(anyInt())).thenReturn(
                TelephonyManager.SIM_STATE_PIN_REQUIRED);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(
                UNKNOWN, REGISTRATION_STATE_UNKNOWN, 0, false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();
        processAllMessages();

        verify(mWwanSelectorCallback, times(1)).onRequestEmergencyNetworkScan(
                any(), anyInt(), anyBoolean(), any(), any());
        assertEquals(4, mAccessNetwork.size());
        assertEquals(EUTRAN, (int) mAccessNetwork.get(0));
        assertEquals(NGRAN, (int) mAccessNetwork.get(1));
        assertEquals(UTRAN, (int) mAccessNetwork.get(2));
        assertEquals(GERAN, (int) mAccessNetwork.get(3));
    }

    @Test
    public void testSimLockScanPsPreferredWithNrAtTheEnd() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        when(mTelephonyManager.getSimState(anyInt())).thenReturn(
                TelephonyManager.SIM_STATE_PIN_REQUIRED);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(
                UNKNOWN, REGISTRATION_STATE_UNKNOWN, 0, false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();
        processAllMessages();

        verify(mWwanSelectorCallback, times(1)).onRequestEmergencyNetworkScan(
                any(), anyInt(), anyBoolean(), any(), any());
        assertEquals(4, mAccessNetwork.size());
        assertEquals(EUTRAN, (int) mAccessNetwork.get(0));
        assertEquals(UTRAN, (int) mAccessNetwork.get(1));
        assertEquals(GERAN, (int) mAccessNetwork.get(2));
        assertEquals(NGRAN, (int) mAccessNetwork.get(3));
    }

    @Test
    public void testInvalidSubscriptionScanPsPreferredWithNrAtTheEnd() throws Exception {
        createSelector(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(
                UNKNOWN, REGISTRATION_STATE_UNKNOWN, 0, false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();
        processAllMessages();

        verify(mWwanSelectorCallback, times(1)).onRequestEmergencyNetworkScan(
                any(), anyInt(), anyBoolean(), any(), any());
        assertEquals(4, mAccessNetwork.size());
        assertEquals(EUTRAN, (int) mAccessNetwork.get(0));
        assertEquals(UTRAN, (int) mAccessNetwork.get(1));
        assertEquals(GERAN, (int) mAccessNetwork.get(2));
        assertEquals(NGRAN, (int) mAccessNetwork.get(3));
    }

    @Test
    public void testInvalidSubscriptionScanPsPreferredWithNr() throws Exception {
        createSelector(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        unsolBarringInfoChanged(false);

        // The last valid subscription supported NR.
        doReturn(true).when(mCarrierConfigHelper).isVoNrEmergencySupported(eq(SLOT_0));

        EmergencyRegistrationResult regResult = getEmergencyRegResult(
                UNKNOWN, REGISTRATION_STATE_UNKNOWN, 0, false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();
        processAllMessages();

        verify(mWwanSelectorCallback, times(1)).onRequestEmergencyNetworkScan(
                any(), anyInt(), anyBoolean(), any(), any());
        assertEquals(4, mAccessNetwork.size());
        assertEquals(EUTRAN, (int) mAccessNetwork.get(0));
        assertEquals(NGRAN, (int) mAccessNetwork.get(1));
        assertEquals(UTRAN, (int) mAccessNetwork.get(2));
        assertEquals(GERAN, (int) mAccessNetwork.get(3));
    }

    @Test
    public void testDefaultLimitedServiceEutran() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_UNKNOWN,
                0, false, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyPsDialed();
    }

    @Test
    public void testDefaultLimitedServiceScanTypeFullService() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putInt(KEY_EMERGENCY_NETWORK_SCAN_TYPE_INT, SCAN_TYPE_FULL_SERVICE);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_UNKNOWN,
                0, false, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyScanPreferred(DomainSelectionService.SCAN_TYPE_FULL_SERVICE, EUTRAN);
    }

    @Test
    public void testScanLtePreferredAfterNgranFailure() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        bundle.putIntArray(KEY_EMERGENCY_OVER_IMS_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY,
                new int[] { NGRAN, EUTRAN });
        bundle.putBoolean(KEY_EMERGENCY_LTE_PREFERRED_AFTER_NR_FAILED_BOOL, true);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(NGRAN,
                REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_PS, true, false, 1, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyPsDialed();

        mDomainSelector.reselectDomain(attr);
        processAllMessages();

        verify(mWwanSelectorCallback, times(1)).onRequestEmergencyNetworkScan(
                any(), anyInt(), anyBoolean(), any(), any());
        assertEquals(4, mAccessNetwork.size());
        assertEquals(EUTRAN, (int) mAccessNetwork.get(0));
        assertEquals(UTRAN, (int) mAccessNetwork.get(1));
        assertEquals(GERAN, (int) mAccessNetwork.get(2));
        assertEquals(NGRAN, (int) mAccessNetwork.get(3));
    }

    @Test
    public void testDefaultLimitedServiceNgran() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(NGRAN,
                REGISTRATION_STATE_UNKNOWN,
                0, false, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyScanPsPreferred();
    }

    @Test
    public void testTestEmergencyNumberOverCs() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UTRAN,
                REGISTRATION_STATE_UNKNOWN,
                0, false, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID,
                true /*isTestEmergencyNumber*/, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService();

        verifyCsDialed();
    }

    @Test
    public void testTestEmergencyNumberOverPs() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_UNKNOWN,
                0, false, true, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID,
                true /*isTestEmergencyNumber*/, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyPsDialed();
    }

    @Test
    public void testTestEmergencyNumberScanRequest() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UNKNOWN,
                REGISTRATION_STATE_UNKNOWN,
                0, false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID,
                true /*isTestEmergencyNumber*/, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsService(true);
        processAllMessages();

        verifyScanPsPreferred();
    }

    @Test
    public void testLimitedServiceDialCs() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UTRAN,
                REGISTRATION_STATE_UNKNOWN,
                0, false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();

        verifyCsDialed();
    }

    @Test
    public void testWhileInEcbmOnWwan() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        doReturn(true).when(mEcbmHelper).isInEmergencyCallbackMode(anyInt());
        doReturn(TRANSPORT_TYPE_WWAN).when(mEcbmHelper).getTransportType(anyInt());
        doReturn(DATA_CONNECTED).when(mEcbmHelper).getDataConnectionState(anyInt());

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Consumer<WwanSelectorCallback> consumer =
                        (Consumer<WwanSelectorCallback>) invocation.getArguments()[0];
                consumer.accept(mWwanSelectorCallback);
                return null;
            }
        }).when(mTransportSelectorCallback).onWwanSelected(any());

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UNKNOWN,
                REGISTRATION_STATE_UNKNOWN,
                0, false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();
        processAllMessages();

        verify(mTransportSelectorCallback, never()).onWlanSelected(anyBoolean());
        verify(mTransportSelectorCallback).onWwanSelected(any());
        verify(mWwanSelectorCallback).onDomainSelected(eq(DOMAIN_PS), eq(true));
    }

    @Test
    public void testWhileInEcbmOnWlanConnected() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        doReturn(true).when(mEcbmHelper).isInEmergencyCallbackMode(anyInt());
        doReturn(TRANSPORT_TYPE_WLAN).when(mEcbmHelper).getTransportType(anyInt());
        doReturn(DATA_CONNECTED).when(mEcbmHelper).getDataConnectionState(anyInt());

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UNKNOWN,
                REGISTRATION_STATE_UNKNOWN,
                0, false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();
        processAllMessages();

        verify(mTransportSelectorCallback).onWlanSelected(anyBoolean());
        verify(mTransportSelectorCallback, never()).onWwanSelected(any());
    }

    @Test
    public void testWhileInEcbmOnWlanNotConnected() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        doReturn(true).when(mEcbmHelper).isInEmergencyCallbackMode(anyInt());
        doReturn(TRANSPORT_TYPE_WLAN).when(mEcbmHelper).getTransportType(anyInt());

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UNKNOWN,
                REGISTRATION_STATE_UNKNOWN,
                0, false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();
        processAllMessages();

        verify(mTransportSelectorCallback, never()).onWlanSelected(anyBoolean());
        verify(mTransportSelectorCallback).onWwanSelected(any());
    }

    @Test
    public void testNotInEcbmOnWlanConnected() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        doReturn(false).when(mEcbmHelper).isInEmergencyCallbackMode(anyInt());
        doReturn(TRANSPORT_TYPE_WLAN).when(mEcbmHelper).getTransportType(anyInt());
        doReturn(DATA_CONNECTED).when(mEcbmHelper).getDataConnectionState(anyInt());

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UNKNOWN,
                REGISTRATION_STATE_UNKNOWN,
                0, false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();
        processAllMessages();

        verify(mTransportSelectorCallback, never()).onWlanSelected(anyBoolean());
        verify(mTransportSelectorCallback).onWwanSelected(any());
    }

    @Test
    public void testNotInEcbmOnWwanConnected() throws Exception {
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);

        doReturn(false).when(mEcbmHelper).isInEmergencyCallbackMode(anyInt());
        doReturn(TRANSPORT_TYPE_WLAN).when(mEcbmHelper).getTransportType(anyInt());
        doReturn(DATA_CONNECTED).when(mEcbmHelper).getDataConnectionState(anyInt());

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Consumer<WwanSelectorCallback> consumer =
                        (Consumer<WwanSelectorCallback>) invocation.getArguments()[0];
                consumer.accept(mWwanSelectorCallback);
                return null;
            }
        }).when(mTransportSelectorCallback).onWwanSelected(any());

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UNKNOWN,
                REGISTRATION_STATE_UNKNOWN,
                0, false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();
        processAllMessages();

        verify(mTransportSelectorCallback, never()).onWlanSelected(anyBoolean());
        verify(mTransportSelectorCallback).onWwanSelected(any());
        verify(mWwanSelectorCallback, never()).onDomainSelected(anyInt(), anyBoolean());
        verify(mWwanSelectorCallback).onRequestEmergencyNetworkScan(
                any(), anyInt(), anyBoolean(), any(), any());
    }

    @Test
    public void testIsInRoaming() throws Exception {
        PersistableBundle bundle = getDefaultPersistableBundle();
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP,
                };
        bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);
        int[] domainPreferenceRoam = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP,
                CarrierConfigManager.ImsEmergency.DOMAIN_CS,
                };
        bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_ROAMING_INT_ARRAY, domainPreferenceRoam);
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);
        doReturn("").when(mTelephonyManager).getNetworkCountryIso();
        doReturn("us").when(mTelephonyManager).getSimCountryIso();

        mResultConsumer = null;
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(UNKNOWN,
                REGISTRATION_STATE_UNKNOWN,
                0, false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();
        processAllMessages();

        verify(mWwanSelectorCallback, times(1)).onRequestEmergencyNetworkScan(
                any(), anyInt(), eq(false), any(), any());
        assertEquals(1, mAccessNetwork.size());
        assertEquals(EUTRAN, (int) mAccessNetwork.get(0));
        assertNotNull(mResultConsumer);

        regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_UNKNOWN,
                0, false, false, 0, 0, "", "", "zz");
        mResultConsumer.accept(regResult);
        processAllMessages();

        mDomainSelector.reselectDomain(attr);
        processAllMessages();

        verify(mWwanSelectorCallback, times(2)).onRequestEmergencyNetworkScan(
                any(), anyInt(), eq(false), any(), any());
        assertEquals(3, mAccessNetwork.size());
        assertEquals(UTRAN, (int) mAccessNetwork.get(0));
        assertEquals(GERAN, (int) mAccessNetwork.get(1));
        assertEquals(EUTRAN, (int) mAccessNetwork.get(2));
    }

    private void setupForScanListTest(PersistableBundle bundle) throws Exception {
        setupForScanListTest(bundle, false);
    }

    private void setupForScanListTest(PersistableBundle bundle, boolean psFailed) throws Exception {
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), anyVararg())).thenReturn(bundle);

        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(false);
        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_UNKNOWN,
                0, false, false, 0, 0, "", "");
        if (psFailed) {
            regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                    NetworkRegistrationInfo.DOMAIN_PS, true, true, 0, 0, "", "");
        }

        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();
    }

    private void verifyCsPreferredScanList(List<Integer> networks) {
        assertFalse(networks.isEmpty());
        assertTrue(networks.contains(EUTRAN));
        assertTrue(networks.contains(UTRAN));
        assertTrue(networks.contains(GERAN));
        assertTrue(networks.indexOf(UTRAN) < networks.indexOf(EUTRAN));
    }

    private void verifyPsPreferredScanList(List<Integer> networks) {
        assertFalse(networks.isEmpty());
        assertTrue(networks.contains(EUTRAN));
        assertTrue(networks.contains(UTRAN));
        assertTrue(networks.contains(GERAN));
        assertTrue(networks.indexOf(EUTRAN) < networks.indexOf(UTRAN));
    }

    private void verifyPsOnlyScanList(List<Integer> networks) {
        assertFalse(networks.isEmpty());
        assertTrue(networks.contains(EUTRAN));
        assertFalse(networks.contains(UTRAN));
        assertFalse(networks.contains(GERAN));
    }

    private void verifyCsOnlyScanList(List<Integer> networks) {
        assertFalse(networks.isEmpty());
        assertFalse(networks.contains(EUTRAN));
        assertTrue(networks.contains(UTRAN));
        assertTrue(networks.contains(GERAN));
    }

    private void setupForHandleScanResult() throws Exception {
        mResultConsumer = null;
        createSelector(SLOT_0_SUB_ID);
        unsolBarringInfoChanged(true);

        EmergencyRegistrationResult regResult = getEmergencyRegResult(EUTRAN,
                REGISTRATION_STATE_UNKNOWN,
                0, false, false, 0, 0, "", "");
        SelectionAttributes attr = getSelectionAttributes(SLOT_0, SLOT_0_SUB_ID, regResult);
        mDomainSelector.selectDomain(attr, mTransportSelectorCallback);
        processAllMessages();

        bindImsServiceUnregistered();
        processAllMessages();

        verify(mWwanSelectorCallback, times(1)).onRequestEmergencyNetworkScan(
                any(), anyInt(), anyBoolean(), any(), any());
        assertNotNull(mResultConsumer);
    }

    private void createSelector(int subId) throws Exception {
        mDomainSelector = new EmergencyCallDomainSelector(
                mContext, SLOT_0, subId, mHandlerThread.getLooper(),
                mImsStateTracker, mDestroyListener, mCsrdCtrl, mCarrierConfigHelper, mEcbmHelper);
        mDomainSelector.clearResourceConfiguration();
        replaceInstance(DomainSelectorBase.class,
                "mWwanSelectorCallback", mDomainSelector, mWwanSelectorCallback);
    }

    private void verifyCsDialed() {
        processAllMessages();
        verify(mWwanSelectorCallback, times(1)).onDomainSelected(eq(DOMAIN_CS), eq(false));
    }

    private void verifyPsDialed() {
        processAllMessages();
        verify(mWwanSelectorCallback, times(1)).onDomainSelected(eq(DOMAIN_PS), eq(true));
    }

    private void verifyScanPsPreferred() {
        verifyScanPreferred(DomainSelectionService.SCAN_TYPE_NO_PREFERENCE, EUTRAN);
    }

    private void verifyScanCsPreferred() {
        verifyScanPreferred(DomainSelectionService.SCAN_TYPE_NO_PREFERENCE, UTRAN);
    }

    private void verifyScanPreferred(int scanType, int expectedPreferredAccessNetwork) {
        processAllMessages();
        verify(mWwanSelectorCallback, times(1)).onRequestEmergencyNetworkScan(
                any(), eq(scanType), anyBoolean(), any(), any());
        assertEquals(expectedPreferredAccessNetwork, (int) mAccessNetwork.get(0));
    }

    private void unsolBarringInfoChanged(boolean barred) {
        SparseArray<BarringInfo.BarringServiceInfo> serviceInfos = new SparseArray<>();
        if (barred) {
            serviceInfos.put(BARRING_SERVICE_TYPE_EMERGENCY,
                    new BarringInfo.BarringServiceInfo(BARRING_TYPE_UNCONDITIONAL, false, 0, 0));
        }
        mDomainSelector.onBarringInfoUpdated(new BarringInfo(new CellIdentityLte(), serviceInfos));
    }

    private void bindImsService() {
        bindImsService(false);
    }

    private void bindImsService(boolean isWifi) {
        doReturn(isWifi).when(mImsStateTracker).isImsRegisteredOverWlan();
        doReturn(true).when(mImsStateTracker).isImsRegistered();
        mDomainSelector.onImsRegistrationStateChanged();
        doReturn(true).when(mImsStateTracker).isImsVoiceCapable();
        mDomainSelector.onImsMmTelCapabilitiesChanged();
    }

    private void bindImsServiceUnregistered() {
        doReturn(false).when(mImsStateTracker).isImsRegistered();
        mDomainSelector.onImsRegistrationStateChanged();
        doReturn(false).when(mImsStateTracker).isImsVoiceCapable();
        mDomainSelector.onImsMmTelCapabilitiesChanged();
    }

    private static EmergencyRegistrationResult getEmergencyRegResult(
            @AccessNetworkConstants.RadioAccessNetworkType int accessNetwork,
            @NetworkRegistrationInfo.RegistrationState int regState,
            @NetworkRegistrationInfo.Domain int domain,
            boolean isVopsSupported, boolean isEmcBearerSupported, int emc, int emf,
            @NonNull String mcc, @NonNull String mnc) {
        return getEmergencyRegResult(accessNetwork, regState, domain, isVopsSupported,
                isEmcBearerSupported, emc, emf, mcc, mnc, "");
    }

    private static EmergencyRegistrationResult getEmergencyRegResult(
            @AccessNetworkConstants.RadioAccessNetworkType int accessNetwork,
            @NetworkRegistrationInfo.RegistrationState int regState,
            @NetworkRegistrationInfo.Domain int domain,
            boolean isVopsSupported, boolean isEmcBearerSupported, int emc, int emf,
            @NonNull String mcc, @NonNull String mnc, @NonNull String iso) {
        return new EmergencyRegistrationResult(accessNetwork, regState,
                domain, isVopsSupported, isEmcBearerSupported,
                emc, emf, mcc, mnc, iso);
    }

    private static PersistableBundle getDefaultPersistableBundle() {
        int[] imsRats = new int[] { EUTRAN };
        int[] csRats = new int[] { UTRAN, GERAN };
        int[] imsRoamRats = new int[] { EUTRAN };
        int[] csRoamRats = new int[] { UTRAN, GERAN };
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP,
                CarrierConfigManager.ImsEmergency.DOMAIN_CS,
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_NON_3GPP
                };
        int[] roamDomainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP,
                CarrierConfigManager.ImsEmergency.DOMAIN_CS,
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_NON_3GPP
                };
        boolean imsWhenVoiceOnCs = false;
        int voWifiRequiresCondition = VOWIFI_REQUIRES_NONE;
        int maxRetriesOverWiFi = 1;
        int cellularScanTimerSec = 10;
        int maxCellularTimerSec = 0;
        boolean voWifiOverEmergencyPdn = false;
        int scanType = SCAN_TYPE_NO_PREFERENCE;
        boolean requiresImsRegistration = false;
        boolean requiresVoLteEnabled = false;
        boolean ltePreferredAfterNrFailed = false;
        String[] cdmaPreferredNumbers = new String[] {};

        return getPersistableBundle(imsRats, csRats, imsRoamRats, csRoamRats,
                domainPreference, roamDomainPreference, imsWhenVoiceOnCs,
                voWifiRequiresCondition, maxRetriesOverWiFi, cellularScanTimerSec,
                maxCellularTimerSec, scanType, voWifiOverEmergencyPdn, requiresImsRegistration,
                requiresVoLteEnabled, ltePreferredAfterNrFailed, cdmaPreferredNumbers);
    }

    private static PersistableBundle getPersistableBundle(
            @Nullable int[] imsRats, @Nullable int[] csRats,
            @Nullable int[] imsRoamRats, @Nullable int[] csRoamRats,
            @Nullable int[] domainPreference, @Nullable int[] roamDomainPreference,
            boolean imsWhenVoiceOnCs, int voWifiRequiresCondition,
            int maxRetriesOverWiFi, int cellularScanTimerSec,
            int maxCellularTimerSec, int scanType,
            boolean voWifiOverEmergencyPdn, boolean requiresImsRegistration,
            boolean requiresVoLteEnabled, boolean ltePreferredAfterNrFailed,
            @Nullable String[] cdmaPreferredNumbers) {

        PersistableBundle bundle  = new PersistableBundle();
        if (imsRats != null) {
            bundle.putIntArray(KEY_EMERGENCY_OVER_IMS_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY,
                    imsRats);
        }
        if (imsRoamRats != null) {
            bundle.putIntArray(
                    KEY_EMERGENCY_OVER_IMS_ROAMING_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY,
                    imsRoamRats);
        }
        if (csRats != null) {
            bundle.putIntArray(KEY_EMERGENCY_OVER_CS_SUPPORTED_ACCESS_NETWORK_TYPES_INT_ARRAY,
                    csRats);
        }
        if (csRoamRats != null) {
            bundle.putIntArray(
                    KEY_EMERGENCY_OVER_CS_ROAMING_SUPPORTED_ACCESS_NETWORK_TYPES_INT_ARRAY,
                    csRoamRats);
        }
        if (domainPreference != null) {
            bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);
        }
        if (roamDomainPreference != null) {
            bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_ROAMING_INT_ARRAY,
                    roamDomainPreference);
        }
        bundle.putBoolean(KEY_PREFER_IMS_EMERGENCY_WHEN_VOICE_CALLS_ON_CS_BOOL, imsWhenVoiceOnCs);
        bundle.putInt(KEY_EMERGENCY_VOWIFI_REQUIRES_CONDITION_INT, voWifiRequiresCondition);
        bundle.putInt(KEY_MAXIMUM_NUMBER_OF_EMERGENCY_TRIES_OVER_VOWIFI_INT, maxRetriesOverWiFi);
        bundle.putInt(KEY_EMERGENCY_SCAN_TIMER_SEC_INT, cellularScanTimerSec);
        bundle.putInt(KEY_MAXIMUM_CELLULAR_SEARCH_TIMER_SEC_INT, maxCellularTimerSec);
        bundle.putBoolean(KEY_EMERGENCY_CALL_OVER_EMERGENCY_PDN_BOOL, voWifiOverEmergencyPdn);
        bundle.putInt(KEY_EMERGENCY_NETWORK_SCAN_TYPE_INT, scanType);
        bundle.putBoolean(KEY_EMERGENCY_REQUIRES_IMS_REGISTRATION_BOOL, requiresImsRegistration);
        bundle.putBoolean(KEY_EMERGENCY_REQUIRES_VOLTE_ENABLED_BOOL, requiresVoLteEnabled);
        bundle.putInt(KEY_EMERGENCY_CALL_SETUP_TIMER_ON_CURRENT_NETWORK_SEC_INT, 0);
        bundle.putBoolean(KEY_EMERGENCY_LTE_PREFERRED_AFTER_NR_FAILED_BOOL,
                ltePreferredAfterNrFailed);
        bundle.putStringArray(KEY_EMERGENCY_CDMA_PREFERRED_NUMBERS_STRING_ARRAY,
                cdmaPreferredNumbers);

        return bundle;
    }

    private static SelectionAttributes getSelectionAttributes(int slotId, int subId,
            EmergencyRegistrationResult regResult) {
        return getSelectionAttributes(slotId, subId, false, regResult);
    }

    private static SelectionAttributes getSelectionAttributes(int slotId, int subId,
            boolean isTestEmergencyNumber, EmergencyRegistrationResult regResult) {
        SelectionAttributes.Builder builder =
                new SelectionAttributes.Builder(slotId, subId, SELECTOR_TYPE_CALLING)
                .setAddress(TEST_URI)
                .setEmergency(true)
                .setTestEmergencyNumber(isTestEmergencyNumber)
                .setEmergencyRegistrationResult(regResult);
        return builder.build();
    }

    private static void replaceInstance(final Class c,
            final String instanceName, final Object obj, final Object newValue) throws Exception {
        Field field = c.getDeclaredField(instanceName);
        field.setAccessible(true);
        field.set(obj, newValue);
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
