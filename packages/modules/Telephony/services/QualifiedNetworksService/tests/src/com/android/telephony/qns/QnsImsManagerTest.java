/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.telephony.qns;

import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_LTE;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.telephony.AccessNetworkConstants;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ImsRegistrationAttributes;
import android.telephony.ims.ImsStateCallback;
import android.telephony.ims.ProvisioningManager;
import android.telephony.ims.RegistrationManager;
import android.telephony.ims.SipDialogState;
import android.telephony.ims.SipDialogStateCallback;
import android.telephony.ims.feature.ImsFeature;

import com.google.android.collect.Sets;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class QnsImsManagerTest extends QnsTest {

    @Mock Resources mResources;
    @Mock PersistableBundle mBundle;
    @Mock ProvisioningManager mProvisioningManager;
    @Mock PackageManager mPackageManager;
    private MockitoSession mMockitoSession;
    private QnsImsManager mQnsImsMgr;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        super.setUp();
        when(sMockContext.getResources()).thenReturn(mResources);
        when(mMockCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(mBundle);
        mMockitoSession =
                mockitoSession()
                        .strictness(Strictness.LENIENT)
                        .mockStatic(SystemProperties.class)
                        .mockStatic(ProvisioningManager.class)
                        .mockStatic(SubscriptionManager.class)
                        .startMocking();
        when(ProvisioningManager.createForSubscriptionId(anyInt()))
                .thenReturn(mProvisioningManager);
        when(SubscriptionManager.isValidSubscriptionId(anyInt())).thenReturn(true);
        doAnswer(invocation -> null)
                .when(mMockSubscriptionManager)
                .addOnSubscriptionsChangedListener(
                        any(Executor.class),
                        any(SubscriptionManager.OnSubscriptionsChangedListener.class));

        mQnsImsMgr = new QnsImsManager(sMockContext, 0);
    }

    @After
    public void tearDown() {
        if (mMockitoSession != null) {
            mMockitoSession.finishMocking();
            mMockitoSession = null;
        }
    }

    @Test
    public void testQnsImsManagerIsWfcEnabledByPlatform() {
        when(mResources.getBoolean(com.android.internal.R.bool.config_device_wfc_ims_available))
                .thenReturn(false);
        assertFalse(mQnsImsMgr.isWfcEnabledByPlatform());

        when(SystemProperties.getInt(eq("persist.dbg.wfc_avail_ovr0"), anyInt())).thenReturn(1);
        assertTrue(mQnsImsMgr.isWfcEnabledByPlatform());
        when(SystemProperties.getInt(eq("persist.dbg.wfc_avail_ovr0"), anyInt())).thenReturn(-1);
        assertFalse(mQnsImsMgr.isWfcEnabledByPlatform());

        when(SystemProperties.getInt(eq("persist.dbg.wfc_avail_ovr"), anyInt())).thenReturn(1);
        assertTrue(mQnsImsMgr.isWfcEnabledByPlatform());
        when(SystemProperties.getInt(eq("persist.dbg.wfc_avail_ovr"), anyInt())).thenReturn(-1);
        assertFalse(mQnsImsMgr.isWfcEnabledByPlatform());

        when(mResources.getBoolean(com.android.internal.R.bool.config_device_wfc_ims_available))
                .thenReturn(true);
        when(mMockCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(mBundle);
        when(mBundle.getBoolean(eq(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL)))
                .thenReturn(true);
        when(mBundle.getBoolean(eq(CarrierConfigManager.KEY_CARRIER_IMS_GBA_REQUIRED_BOOL)))
                .thenReturn(false);
        assertTrue(mQnsImsMgr.isWfcEnabledByPlatform());
    }

    @Test
    public void testQnsImsManagerIsWfcEnabledByUser() {
        when(mMockImsMmTelManager.isVoWiFiSettingEnabled()).thenReturn(true);
        assertTrue(mQnsImsMgr.isWfcEnabledByUser());

        when(mMockImsMmTelManager.isVoWiFiSettingEnabled()).thenReturn(false);
        assertFalse(mQnsImsMgr.isWfcEnabledByUser());
    }

    @Test
    public void testQnsImsManagerIsWfcRoamingEnabledByUser() {
        when(mMockImsMmTelManager.isVoWiFiRoamingSettingEnabled()).thenReturn(true);
        assertTrue(mQnsImsMgr.isWfcRoamingEnabledByUser());

        when(mMockImsMmTelManager.isVoWiFiRoamingSettingEnabled()).thenReturn(false);
        assertFalse(mQnsImsMgr.isWfcRoamingEnabledByUser());
    }

    @Test
    public void testQnsImsManagerIsWfcProvisionedOnDevice() {
        when(mBundle.getBoolean(
                        eq(CarrierConfigManager.KEY_CARRIER_VOLTE_OVERRIDE_WFC_PROVISIONING_BOOL)))
                .thenReturn(true);
        when(mProvisioningManager.isProvisioningRequiredForCapability(anyInt(), anyInt()))
                .thenReturn(true);
        when(mProvisioningManager.getProvisioningStatusForCapability(
                        anyInt(), eq(REGISTRATION_TECH_LTE)))
                .thenReturn(false);
        when(mProvisioningManager.getProvisioningStatusForCapability(
                        anyInt(), eq(REGISTRATION_TECH_IWLAN)))
                .thenReturn(true);
        assertFalse(mQnsImsMgr.isWfcProvisionedOnDevice());

        when(mBundle.getBoolean(
                        eq(CarrierConfigManager.KEY_CARRIER_VOLTE_OVERRIDE_WFC_PROVISIONING_BOOL)))
                .thenReturn(true);
        when(mProvisioningManager.isProvisioningRequiredForCapability(anyInt(), anyInt()))
                .thenReturn(true);
        when(mProvisioningManager.getProvisioningStatusForCapability(
                        anyInt(), eq(REGISTRATION_TECH_LTE)))
                .thenReturn(true);
        when(mProvisioningManager.getProvisioningStatusForCapability(
                        anyInt(), eq(REGISTRATION_TECH_IWLAN)))
                .thenReturn(true);
        assertTrue(mQnsImsMgr.isWfcProvisionedOnDevice());

        when(mBundle.getBoolean(
                        eq(CarrierConfigManager.KEY_CARRIER_VOLTE_OVERRIDE_WFC_PROVISIONING_BOOL)))
                .thenReturn(false);
        when(mProvisioningManager.isProvisioningRequiredForCapability(anyInt(), anyInt()))
                .thenReturn(true);
        when(mProvisioningManager.getProvisioningStatusForCapability(
                        anyInt(), eq(REGISTRATION_TECH_LTE)))
                .thenReturn(true);
        when(mProvisioningManager.getProvisioningStatusForCapability(
                        anyInt(), eq(REGISTRATION_TECH_IWLAN)))
                .thenReturn(false);
        assertFalse(mQnsImsMgr.isWfcProvisionedOnDevice());

        when(mBundle.getBoolean(
                        eq(CarrierConfigManager.KEY_CARRIER_VOLTE_OVERRIDE_WFC_PROVISIONING_BOOL)))
                .thenReturn(false);
        when(mProvisioningManager.isProvisioningRequiredForCapability(anyInt(), anyInt()))
                .thenReturn(false);
        assertTrue(mQnsImsMgr.isWfcProvisionedOnDevice());
    }

    @Test
    public void testQnsImsManagerIsCrossSimCallingEnabled() throws ImsException {
        when(mMockImsMmTelManager.isCrossSimCallingEnabled()).thenReturn(false);
        assertFalse(mQnsImsMgr.isCrossSimCallingEnabled());

        when(mMockImsMmTelManager.isCrossSimCallingEnabled()).thenReturn(true);
        when(mResources.getBoolean(com.android.internal.R.bool.config_device_wfc_ims_available))
                .thenReturn(true);
        when(mMockCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(mBundle);
        when(mBundle.getBoolean(eq(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL)))
                .thenReturn(true);
        when(mBundle.getBoolean(eq(CarrierConfigManager.KEY_CARRIER_IMS_GBA_REQUIRED_BOOL)))
                .thenReturn(false);
        when(mBundle.getBoolean(eq(CarrierConfigManager.KEY_CARRIER_CROSS_SIM_IMS_AVAILABLE_BOOL)))
                .thenReturn(false);
        assertFalse(mQnsImsMgr.isCrossSimCallingEnabled());

        when(mBundle.getBoolean(eq(CarrierConfigManager.KEY_CARRIER_CROSS_SIM_IMS_AVAILABLE_BOOL)))
                .thenReturn(true);
        when(mBundle.getBoolean(
                        eq(CarrierConfigManager.KEY_CARRIER_VOLTE_OVERRIDE_WFC_PROVISIONING_BOOL)))
                .thenReturn(true);
        when(mProvisioningManager.isProvisioningRequiredForCapability(anyInt(), anyInt()))
                .thenReturn(true);
        when(mProvisioningManager.getProvisioningStatusForCapability(
                        anyInt(), eq(REGISTRATION_TECH_LTE)))
                .thenReturn(false);
        when(mProvisioningManager.getProvisioningStatusForCapability(
                        anyInt(), eq(REGISTRATION_TECH_IWLAN)))
                .thenReturn(true);
        assertFalse(mQnsImsMgr.isCrossSimCallingEnabled());

        when(mBundle.getBoolean(eq(CarrierConfigManager.KEY_CARRIER_CROSS_SIM_IMS_AVAILABLE_BOOL)))
                .thenReturn(true);
        when(mBundle.getBoolean(
                        eq(CarrierConfigManager.KEY_CARRIER_VOLTE_OVERRIDE_WFC_PROVISIONING_BOOL)))
                .thenReturn(true);
        when(mProvisioningManager.isProvisioningRequiredForCapability(anyInt(), anyInt()))
                .thenReturn(true);
        when(mProvisioningManager.getProvisioningStatusForCapability(
                        anyInt(), eq(REGISTRATION_TECH_LTE)))
                .thenReturn(true);
        when(mProvisioningManager.getProvisioningStatusForCapability(
                        anyInt(), eq(REGISTRATION_TECH_IWLAN)))
                .thenReturn(true);
        assertTrue(mQnsImsMgr.isCrossSimCallingEnabled());
    }

    @Test
    public void testQnsImsManagerGetWfcMode() {
        when(mMockImsMmTelManager.getVoWiFiModeSetting()).thenReturn(0);
        assertEquals(0, mQnsImsMgr.getWfcMode(false));

        when(mMockImsMmTelManager.getVoWiFiModeSetting()).thenReturn(1);
        assertEquals(1, mQnsImsMgr.getWfcMode(false));

        when(mMockImsMmTelManager.getVoWiFiModeSetting()).thenReturn(2);
        assertEquals(2, mQnsImsMgr.getWfcMode(false));
    }

    @Test
    public void testQnsImsManagerGetWfcRoamingMode() {
        when(mMockImsMmTelManager.getVoWiFiRoamingModeSetting()).thenReturn(0);
        assertEquals(0, mQnsImsMgr.getWfcMode(true));

        when(mMockImsMmTelManager.getVoWiFiRoamingModeSetting()).thenReturn(1);
        assertEquals(1, mQnsImsMgr.getWfcMode(true));

        when(mMockImsMmTelManager.getVoWiFiRoamingModeSetting()).thenReturn(2);
        assertEquals(2, mQnsImsMgr.getWfcMode(true));
    }

    @Test
    public void testQnsImsManagerGetWfcModeFromCarrierConfig() throws ImsException {
        doThrow(new ImsException("Test Exception"))
                .when(mMockImsMmTelManager)
                .registerImsStateCallback(any(), any());
        mQnsImsMgr = new QnsImsManager(sMockContext, 0);

        when(mMockCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(mBundle);
        when(mBundle.getInt(eq(CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_MODE_INT)))
                .thenReturn(0);
        assertEquals(0, mQnsImsMgr.getWfcMode(false));

        when(mBundle.getInt(eq(CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_MODE_INT)))
                .thenReturn(1);
        assertEquals(1, mQnsImsMgr.getWfcMode(false));

        when(mBundle.getInt(eq(CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_MODE_INT)))
                .thenReturn(2);
        assertEquals(2, mQnsImsMgr.getWfcMode(false));
    }

    @Test
    public void testQnsImsManagerGetWfcRoamingModeFromCarrierConfig() throws ImsException {
        doThrow(new ImsException("Test Exception"))
                .when(mMockImsMmTelManager)
                .registerImsStateCallback(any(), any());
        mQnsImsMgr = new QnsImsManager(sMockContext, 0);

        when(mMockCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(mBundle);
        when(mBundle.getInt(eq(CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_MODE_INT)))
                .thenReturn(0);
        assertEquals(0, mQnsImsMgr.getWfcMode(true));

        when(mBundle.getInt(eq(CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_MODE_INT)))
                .thenReturn(1);
        assertEquals(1, mQnsImsMgr.getWfcMode(true));

        when(mBundle.getInt(eq(CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_MODE_INT)))
                .thenReturn(2);
        assertEquals(2, mQnsImsMgr.getWfcMode(true));
    }

    @Test
    public void testQnsImsManagerGetImsServiceState() throws InterruptedException {
        when(sMockContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.hasSystemFeature(any())).thenReturn(false);
        boolean bException = false;
        try {
            mQnsImsMgr.getImsServiceState();
        } catch (ImsException e) {
            bException = true;
        }
        assertTrue(bException);

        bException = false;
        when(mPackageManager.hasSystemFeature(any())).thenReturn(true);
        try {
            mQnsImsMgr.getImsServiceState();
        } catch (ImsException e) {
            bException = true;
        }
        assertFalse(bException);
    }

    @Test
    public void testQnsImsManagerInit() throws NoSuchFieldException, IllegalAccessException {
        Field initializedField = QnsImsManager.class.getDeclaredField("mQnsImsManagerInitialized");
        initializedField.setAccessible(true);
        boolean qnsImsManagerInitialized = (boolean) initializedField.get(mQnsImsMgr);
        assertTrue(qnsImsManagerInitialized);

        Mockito.clearInvocations(mMockImsManager);
        when(mMockImsManager.getSipDelegateManager(anyInt())).thenReturn(null);
        mQnsImsMgr = new QnsImsManager(sMockContext, 0);
        qnsImsManagerInitialized = (boolean) initializedField.get(mQnsImsMgr);
        assertFalse(qnsImsManagerInitialized);

        Mockito.clearInvocations(mMockImsManager);
        when(mMockImsManager.getImsRcsManager(anyInt())).thenReturn(null);
        mQnsImsMgr = new QnsImsManager(sMockContext, 0);
        qnsImsManagerInitialized = (boolean) initializedField.get(mQnsImsMgr);
        assertFalse(qnsImsManagerInitialized);

        Mockito.clearInvocations(mMockImsManager);
        when(mMockImsManager.getImsMmTelManager(anyInt())).thenReturn(null);
        mQnsImsMgr = new QnsImsManager(sMockContext, 0);
        qnsImsManagerInitialized = (boolean) initializedField.get(mQnsImsMgr);
        assertFalse(qnsImsManagerInitialized);

        Mockito.clearInvocations(mMockImsManager);
        when(sMockContext.getSystemService(ImsManager.class)).thenReturn(null);
        mQnsImsMgr = new QnsImsManager(sMockContext, 0);
        qnsImsManagerInitialized = (boolean) initializedField.get(mQnsImsMgr);
        assertFalse(qnsImsManagerInitialized);

        Mockito.clearInvocations(mMockImsManager);
        when(sMockContext.getSystemService(CarrierConfigManager.class)).thenReturn(null);
        mQnsImsMgr = new QnsImsManager(sMockContext, 0);
        qnsImsManagerInitialized = (boolean) initializedField.get(mQnsImsMgr);
        assertFalse(qnsImsManagerInitialized);
    }

    @Test
    public void testQnsImsManagerGetSlotIndex() {
        assertEquals(0, mQnsImsMgr.getSlotIndex());
        QnsImsManager qnsImsManager1 = new QnsImsManager(sMockContext, 1);
        assertEquals(1, qnsImsManager1.getSlotIndex());
    }

    @Test
    public void testQnsImsManagerClear()
            throws NoSuchFieldException, IllegalAccessException, ImsException {
        Field initializedField = QnsImsManager.class.getDeclaredField("mQnsImsManagerInitialized");
        initializedField.setAccessible(true);
        boolean qnsImsManagerInitialized = (boolean) initializedField.get(mQnsImsMgr);
        assertTrue(qnsImsManagerInitialized);
        ImsStateCallback rcsStateCallback = mQnsImsMgr.mRcsStateCallback;
        rcsStateCallback.onAvailable();

        mQnsImsMgr.clearQnsImsManager();
        verify(mMockImsMmTelManager, times(1)).unregisterImsStateCallback(any());
        verify(mMockImsMmTelManager, times(1))
                .unregisterImsRegistrationCallback(
                        any(RegistrationManager.RegistrationCallback.class));
        verify(mMockImsRcsManager, times(1)).unregisterImsStateCallback(any());
        verify(mMockImsRcsManager, times(1))
                .unregisterImsRegistrationCallback(
                        any(RegistrationManager.RegistrationCallback.class));
        verify(mMockSipDelegateManager, times(1)).unregisterSipDialogStateCallback(any());
    }

    @Test
    public void testQnsImsManagerSubscriptionChanged() {
        SubscriptionManager.OnSubscriptionsChangedListener subListener =
                mQnsImsMgr.mSubscriptionsChangeListener;

        when(mMockSubscriptionInfo.getSubscriptionId()).thenReturn(1);
        subListener.onSubscriptionsChanged();
        assertNotNull(mQnsImsMgr.mMmTelStateCallback);
        assertNotNull(mQnsImsMgr.mRcsStateCallback);

        when(mMockSubscriptionInfo.getSubscriptionId())
                .thenReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        subListener.onSubscriptionsChanged();
        assertNull(mQnsImsMgr.mMmTelStateCallback);
        assertNull(mQnsImsMgr.mRcsStateCallback);
    }

    @Test
    public void testQnsImsManagerIsGbaValid()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = QnsImsManager.class.getDeclaredMethod("isGbaValid");
        method.setAccessible(true);
        assertTrue((Boolean) method.invoke(mQnsImsMgr));

        when(mBundle.getBoolean(eq(CarrierConfigManager.KEY_CARRIER_IMS_GBA_REQUIRED_BOOL)))
                .thenReturn(true);

        when(mMockTelephonyManager.getIsimIst()).thenReturn(null);
        assertTrue((Boolean) method.invoke(mQnsImsMgr));

        when(mMockTelephonyManager.getIsimIst()).thenReturn("22");
        assertTrue((Boolean) method.invoke(mQnsImsMgr));

        when(mMockTelephonyManager.getIsimIst()).thenReturn("21");
        assertFalse((Boolean) method.invoke(mQnsImsMgr));

        when(sMockContext.getSystemService(TelephonyManager.class)).thenReturn(null);
        assertFalse((Boolean) method.invoke(mQnsImsMgr));
    }

    @Test
    public void testQnsImsManagerQnsImsRegistrationState() {
        QnsImsManager.ImsRegistrationState stateReg =
                new QnsImsManager.ImsRegistrationState(
                        QnsConstants.IMS_REGISTRATION_CHANGED_REGISTERED,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                        null);
        QnsImsManager.ImsRegistrationState stateUnreg =
                new QnsImsManager.ImsRegistrationState(
                        QnsConstants.IMS_REGISTRATION_CHANGED_UNREGISTERED,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                        null);
        QnsImsManager.ImsRegistrationState stateAccessNetworkChangeFail =
                new QnsImsManager.ImsRegistrationState(
                        QnsConstants.IMS_REGISTRATION_CHANGED_ACCESS_NETWORK_CHANGE_FAILED,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                        null);
        assertTrue(stateReg.toString().contains("IMS_REG"));
        assertTrue(stateUnreg.toString().contains("IMS_UNREG"));
        assertTrue(stateAccessNetworkChangeFail.toString().contains("IMS_ACCESS"));
    }

    private void triggerMmTelCallback_onRegistered(QnsImsManager qnsImsMgr, int transportType) {
        RegistrationManager.RegistrationCallback mmtelImsRegistrationCallback =
                qnsImsMgr.mMmtelImsRegistrationCallback;
        ImsRegistrationAttributes attributes;
        if (transportType == AccessNetworkConstants.TRANSPORT_TYPE_WLAN) {
            attributes =
                    new ImsRegistrationAttributes(
                            REGISTRATION_TECH_IWLAN,
                            AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                            0,
                            Sets.newArraySet());
        } else {
            attributes =
                    new ImsRegistrationAttributes(
                            REGISTRATION_TECH_LTE,
                            AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                            0,
                            Sets.newArraySet());
        }
        mmtelImsRegistrationCallback.onRegistered(attributes);
    }

    private void triggerMmTelCallback_onUnregistered(QnsImsManager qnsImsMgr) {
        ImsReasonInfo reason = new ImsReasonInfo();
        reason.mCode = ImsReasonInfo.CODE_SIP_BUSY;
        triggerMmTelCallback_onUnregistered(qnsImsMgr, reason);
    }

    private void triggerMmTelCallback_onUnregistered(
            QnsImsManager qnsImsMgr, ImsReasonInfo reason) {
        RegistrationManager.RegistrationCallback mmtelImsRegistrationCallback =
                qnsImsMgr.mMmtelImsRegistrationCallback;
        mmtelImsRegistrationCallback.onUnregistered(reason);
    }

    @Test
    public void testQnsImsManagerQnsIsImsRegistered() {
        triggerMmTelCallback_onRegistered(mQnsImsMgr, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertTrue(mQnsImsMgr.isImsRegistered(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
        assertFalse(mQnsImsMgr.isImsRegistered(AccessNetworkConstants.TRANSPORT_TYPE_WLAN));

        triggerMmTelCallback_onRegistered(mQnsImsMgr, AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        assertFalse(mQnsImsMgr.isImsRegistered(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
        assertTrue(mQnsImsMgr.isImsRegistered(AccessNetworkConstants.TRANSPORT_TYPE_WLAN));

        triggerMmTelCallback_onUnregistered(mQnsImsMgr);
        assertFalse(mQnsImsMgr.isImsRegistered(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
        assertFalse(mQnsImsMgr.isImsRegistered(AccessNetworkConstants.TRANSPORT_TYPE_WLAN));

        mQnsImsMgr.mMmtelImsRegistrationCallback = null;
        assertFalse(mQnsImsMgr.isImsRegistered(AccessNetworkConstants.TRANSPORT_TYPE_INVALID));
    }

    private void triggerRcsCallback_onRegistered(QnsImsManager qnsImsMgr, int transportType) {
        RegistrationManager.RegistrationCallback rcsImsRegistrationCallback =
                qnsImsMgr.mRcsImsRegistrationCallback;
        ImsRegistrationAttributes attributes;
        if (transportType == AccessNetworkConstants.TRANSPORT_TYPE_WLAN) {
            attributes =
                    new ImsRegistrationAttributes(
                            REGISTRATION_TECH_IWLAN,
                            AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                            0,
                            Sets.newArraySet());
        } else {
            attributes =
                    new ImsRegistrationAttributes(
                            REGISTRATION_TECH_LTE,
                            AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                            0,
                            Sets.newArraySet());
        }
        rcsImsRegistrationCallback.onRegistered(attributes);
    }

    private void triggerRcsCallback_onUnregistered(QnsImsManager qnsImsMgr) {
        ImsReasonInfo reason = new ImsReasonInfo();
        reason.mCode = ImsReasonInfo.CODE_SIP_BUSY;
        triggerRcsCallback_onUnregistered(qnsImsMgr, reason);
    }

    private void triggerRcsCallback_onUnregistered(QnsImsManager qnsImsMgr, ImsReasonInfo reason) {
        RegistrationManager.RegistrationCallback rcsImsRegistrationCallback =
                qnsImsMgr.mRcsImsRegistrationCallback;
        rcsImsRegistrationCallback.onUnregistered(reason);
    }

    @Test
    public void testQnsImsManagerQnsIsRcsRegistered() {
        triggerRcsCallback_onRegistered(mQnsImsMgr, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertTrue(mQnsImsMgr.isRcsRegistered(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
        assertFalse(mQnsImsMgr.isRcsRegistered(AccessNetworkConstants.TRANSPORT_TYPE_WLAN));

        triggerRcsCallback_onRegistered(mQnsImsMgr, AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        assertFalse(mQnsImsMgr.isRcsRegistered(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
        assertTrue(mQnsImsMgr.isRcsRegistered(AccessNetworkConstants.TRANSPORT_TYPE_WLAN));

        triggerRcsCallback_onUnregistered(mQnsImsMgr);
        assertFalse(mQnsImsMgr.isRcsRegistered(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
        assertFalse(mQnsImsMgr.isRcsRegistered(AccessNetworkConstants.TRANSPORT_TYPE_WLAN));

        mQnsImsMgr.mRcsImsRegistrationCallback = null;
        assertFalse(mQnsImsMgr.isRcsRegistered(AccessNetworkConstants.TRANSPORT_TYPE_INVALID));
    }

    @Test
    public void testQnsImsManagerQnsIsSipDialogSessionActive() {
        ImsStateCallback rcsStateCallback = mQnsImsMgr.mRcsStateCallback;
        rcsStateCallback.onAvailable();
        SipDialogStateCallback sipDialogStateCallback =
                mQnsImsMgr.mRcsSipDialogSessionStateCallback;
        List<SipDialogState> dialogs = new ArrayList<>();
        dialogs.add(new SipDialogState.Builder(SipDialogState.STATE_EARLY).build());
        sipDialogStateCallback.onActiveSipDialogsChanged(dialogs);
        assertFalse(mQnsImsMgr.isSipDialogSessionActive());

        dialogs.clear();
        dialogs.add(new SipDialogState.Builder(SipDialogState.STATE_CONFIRMED).build());
        sipDialogStateCallback.onActiveSipDialogsChanged(dialogs);
        assertTrue(mQnsImsMgr.isSipDialogSessionActive());

        dialogs.clear();
        dialogs.add(new SipDialogState.Builder(SipDialogState.STATE_CLOSED).build());
        sipDialogStateCallback.onActiveSipDialogsChanged(dialogs);
        assertFalse(mQnsImsMgr.isSipDialogSessionActive());

        sipDialogStateCallback.onError();
        assertFalse(mQnsImsMgr.isSipDialogSessionActive());
    }

    @Test
    public void testImsStateEvents() throws InterruptedException {
        final int eventImsStateChanged = 11005;
        CountDownLatch imsAvailableLatch = new CountDownLatch(2);
        CountDownLatch imsUnavailableLatch = new CountDownLatch(2);
        HandlerThread handlerThread = new HandlerThread("testImsStateEvent");
        handlerThread.start();
        Handler handler =
                new Handler(handlerThread.getLooper()) {
                    @Override
                    public void handleMessage(@NonNull Message msg) {
                        QnsAsyncResult ar = (QnsAsyncResult) msg.obj;
                        switch (msg.what) {
                            case eventImsStateChanged:
                                if (ar != null) {
                                    QnsImsManager.ImsState state =
                                            (QnsImsManager.ImsState) ar.mResult;
                                    if (state.isImsAvailable()) {
                                        imsAvailableLatch.countDown();
                                    } else {
                                        imsUnavailableLatch.countDown();
                                    }
                                }
                                break;
                        }
                    }
                };

        ImsStateCallback imsStateCallback = mQnsImsMgr.mMmTelStateCallback;

        mQnsImsMgr.registerImsStateChanged(handler, eventImsStateChanged);
        mQnsImsMgr.notifyImsStateChanged(
                ImsFeature.FEATURE_MMTEL, new QnsImsManager.ImsState(true));
        mQnsImsMgr.notifyImsStateChanged(
                ImsFeature.FEATURE_MMTEL, new QnsImsManager.ImsState(false));

        imsStateCallback.onAvailable();
        imsStateCallback.onAvailable();
        imsStateCallback.onUnavailable(100);
        imsStateCallback.onError();

        assertTrue(imsAvailableLatch.await(100, TimeUnit.MILLISECONDS));
        assertTrue(imsUnavailableLatch.await(100, TimeUnit.MILLISECONDS));

        mQnsImsMgr.unregisterImsStateChanged(handler);
        mQnsImsMgr.notifyImsStateChanged(
                ImsFeature.FEATURE_MMTEL, new QnsImsManager.ImsState(true));
        mQnsImsMgr.notifyImsStateChanged(
                ImsFeature.FEATURE_MMTEL, new QnsImsManager.ImsState(false));

        assertTrue(imsAvailableLatch.await(100, TimeUnit.MILLISECONDS));
        assertTrue(imsUnavailableLatch.await(100, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testRcsStateEvents() throws InterruptedException {
        final int eventRcsStateChanged = 11006;
        CountDownLatch rcsAvailableLatch = new CountDownLatch(2);
        CountDownLatch rcsUnavailableLatch = new CountDownLatch(2);
        HandlerThread handlerThread = new HandlerThread("testRcsStateEvent");
        handlerThread.start();
        Handler handler =
                new Handler(handlerThread.getLooper()) {
                    @Override
                    public void handleMessage(@NonNull Message msg) {
                        QnsAsyncResult ar = (QnsAsyncResult) msg.obj;
                        switch (msg.what) {
                            case eventRcsStateChanged:
                                if (ar != null) {
                                    QnsImsManager.ImsState state =
                                            (QnsImsManager.ImsState) ar.mResult;
                                    if (state.isImsAvailable()) {
                                        rcsAvailableLatch.countDown();
                                    } else {
                                        rcsUnavailableLatch.countDown();
                                    }
                                }
                                break;
                        }
                    }
                };

        ImsStateCallback rcsStateCallback = mQnsImsMgr.mRcsStateCallback;

        mQnsImsMgr.registerRcsStateChanged(handler, eventRcsStateChanged);
        mQnsImsMgr.notifyImsStateChanged(ImsFeature.FEATURE_RCS, new QnsImsManager.ImsState(true));
        mQnsImsMgr.notifyImsStateChanged(ImsFeature.FEATURE_RCS, new QnsImsManager.ImsState(false));

        rcsStateCallback.onAvailable();
        rcsStateCallback.onAvailable();
        rcsStateCallback.onUnavailable(100);
        rcsStateCallback.onError();

        assertTrue(rcsAvailableLatch.await(100, TimeUnit.MILLISECONDS));
        assertTrue(rcsUnavailableLatch.await(100, TimeUnit.MILLISECONDS));

        mQnsImsMgr.unregisterRcsStateChanged(handler);
        mQnsImsMgr.notifyImsStateChanged(ImsFeature.FEATURE_RCS, new QnsImsManager.ImsState(true));
        mQnsImsMgr.notifyImsStateChanged(ImsFeature.FEATURE_RCS, new QnsImsManager.ImsState(false));

        assertTrue(rcsAvailableLatch.await(100, TimeUnit.MILLISECONDS));
        assertTrue(rcsUnavailableLatch.await(100, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSipDialogSessionStateEvents() throws InterruptedException {
        ImsStateCallback rcsStateCallback = mQnsImsMgr.mRcsStateCallback;
        rcsStateCallback.onAvailable();

        final int eventSipDialogSessionStateChanged = 11007;
        CountDownLatch sipDialogSessionActiveStateLatch = new CountDownLatch(2);
        CountDownLatch sipDialogSessionInactiveStateLatch = new CountDownLatch(2);
        HandlerThread handlerThread = new HandlerThread("testSipDialogSessionStateEvent");
        handlerThread.start();
        Handler handler =
                new Handler(handlerThread.getLooper()) {
                    @Override
                    public void handleMessage(@NonNull Message msg) {
                        QnsAsyncResult ar = (QnsAsyncResult) msg.obj;
                        switch (msg.what) {
                            case eventSipDialogSessionStateChanged:
                                if (ar != null) {
                                    boolean isActive = (boolean) ar.mResult;
                                    if (isActive) {
                                        sipDialogSessionActiveStateLatch.countDown();
                                    } else {
                                        sipDialogSessionInactiveStateLatch.countDown();
                                    }
                                }
                                break;
                        }
                    }
                };

        mQnsImsMgr.registerSipDialogSessionStateChanged(handler, eventSipDialogSessionStateChanged);
        mQnsImsMgr.notifySipDialogSessionStateChanged(true);
        mQnsImsMgr.notifySipDialogSessionStateChanged(false);

        SipDialogStateCallback sipDialogStateCallback =
                mQnsImsMgr.mRcsSipDialogSessionStateCallback;
        List<SipDialogState> dialogs = new ArrayList<>();
        dialogs.add(new SipDialogState.Builder(SipDialogState.STATE_CONFIRMED).build());
        sipDialogStateCallback.onActiveSipDialogsChanged(dialogs);

        dialogs.clear();
        dialogs.add(new SipDialogState.Builder(SipDialogState.STATE_CLOSED).build());
        sipDialogStateCallback.onActiveSipDialogsChanged(dialogs);

        assertTrue(sipDialogSessionActiveStateLatch.await(100, TimeUnit.MILLISECONDS));
        assertTrue(sipDialogSessionInactiveStateLatch.await(100, TimeUnit.MILLISECONDS));

        mQnsImsMgr.unregisterSipDialogSessionStateChanged(handler);
    }

    @Test
    public void testQnsImsManagerRegistrant() throws InterruptedException {
        ImsStateCallback rcsStateCallback = mQnsImsMgr.mRcsStateCallback;
        rcsStateCallback.onAvailable();

        HandlerThread handlerThread = new HandlerThread("testQnsImsManagerClose");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        mQnsImsMgr.registerImsStateChanged(handler, 1);
        mQnsImsMgr.registerImsRegistrationStatusChanged(handler, 2);
        mQnsImsMgr.registerRcsStateChanged(handler, 3);
        mQnsImsMgr.registerRcsRegistrationStatusChanged(handler, 4);
        mQnsImsMgr.registerSipDialogSessionStateChanged(handler, 5);

        assertEquals(1, mQnsImsMgr.mMmTelImsStateListener.size());
        assertEquals(1, mQnsImsMgr.mRcsImsStateListener.size());
        assertEquals(1, mQnsImsMgr.mMmTelImsRegistrationListener.size());
        assertEquals(1, mQnsImsMgr.mRcsImsRegistrationListener.size());
        assertEquals(1, mQnsImsMgr.mRcsSipDialogSessionStateListener.size());

        mQnsImsMgr.unregisterImsStateChanged(handler);
        mQnsImsMgr.unregisterImsRegistrationStatusChanged(handler);
        mQnsImsMgr.unregisterRcsStateChanged(handler);
        mQnsImsMgr.unregisterRcsRegistrationStatusChanged(handler);
        mQnsImsMgr.unregisterSipDialogSessionStateChanged(handler);

        assertEquals(0, mQnsImsMgr.mMmTelImsStateListener.size());
        assertEquals(0, mQnsImsMgr.mRcsImsStateListener.size());
        assertEquals(0, mQnsImsMgr.mMmTelImsRegistrationListener.size());
        assertEquals(0, mQnsImsMgr.mRcsImsRegistrationListener.size());
        assertEquals(0, mQnsImsMgr.mRcsSipDialogSessionStateListener.size());
    }

    @Test
    public void testQnsImsManagerClose() throws InterruptedException {
        ImsStateCallback rcsStateCallback = mQnsImsMgr.mRcsStateCallback;
        rcsStateCallback.onAvailable();

        HandlerThread handlerThread = new HandlerThread("testQnsImsManagerClose");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        mQnsImsMgr.registerImsStateChanged(handler, 1);
        mQnsImsMgr.registerImsRegistrationStatusChanged(handler, 2);
        mQnsImsMgr.registerRcsStateChanged(handler, 3);
        mQnsImsMgr.registerRcsRegistrationStatusChanged(handler, 4);
        mQnsImsMgr.registerSipDialogSessionStateChanged(handler, 5);

        assertEquals(1, mQnsImsMgr.mMmTelImsStateListener.size());
        assertEquals(1, mQnsImsMgr.mRcsImsStateListener.size());
        assertEquals(1, mQnsImsMgr.mMmTelImsRegistrationListener.size());
        assertEquals(1, mQnsImsMgr.mRcsImsRegistrationListener.size());
        assertEquals(1, mQnsImsMgr.mRcsSipDialogSessionStateListener.size());

        mQnsImsMgr.close();

        assertEquals(0, mQnsImsMgr.mMmTelImsStateListener.size());
        assertEquals(0, mQnsImsMgr.mRcsImsStateListener.size());
        assertEquals(0, mQnsImsMgr.mMmTelImsRegistrationListener.size());
        assertEquals(0, mQnsImsMgr.mRcsImsRegistrationListener.size());
        assertEquals(0, mQnsImsMgr.mRcsSipDialogSessionStateListener.size());
    }
}
