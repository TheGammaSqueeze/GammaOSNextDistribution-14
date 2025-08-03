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

package com.android.phone;

import static com.android.internal.telephony.TelephonyStatsLog.RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__PROVISIONING_XML;
import static com.android.internal.telephony.TelephonyStatsLog.RCS_CLIENT_PROVISIONING_STATS__EVENT__DMA_CHANGED;
import static com.android.internal.telephony.TelephonyStatsLog.RCS_CLIENT_PROVISIONING_STATS__EVENT__TRIGGER_RCS_RECONFIGURATION;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.role.OnRoleHoldersChangedListener;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.provider.Telephony.SimInfo;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyRegistryManager;
import android.telephony.ims.ProvisioningManager;
import android.telephony.ims.RcsConfig;
import android.telephony.ims.aidl.IImsConfig;
import android.telephony.ims.aidl.IRcsConfigCallback;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.testing.TestableLooper;
import android.util.Log;

import androidx.test.filters.SmallTest;

import com.android.ims.FeatureConnector;
import com.android.ims.RcsFeatureManager;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.metrics.RcsStats;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Unit tests for RcsProvisioningMonitor
 */
public class RcsProvisioningMonitorTest {
    private static final String TAG = "RcsProvisioningMonitorTest";
    private static final String CONFIG_DEFAULT = "<?xml version=\"1.0\"?>\n"
            + "<wap-provisioningdoc version=\"1.1\">\n"
            + "\t<characteristic type=\"APPLICATION\">\n"
            + "\t\t<parm name=\"AppID\" value=\"urn:oma:mo:ext-3gpp-ims:1.0\"/>\n"
            + "\t\t<characteristic type=\"3GPP_IMS\">\n"
            + "\t\t\t<parm name=\"AppID\" value=\"ap2001\"/>\n"
            + "\t\t\t<parm name=\"Name\" value=\"RCS IMS Settings\"/>\n"
            + "\t\t\t<characteristic type=\"Ext\">\n"
            + "\t\t\t\t<characteristic type=\"GSMA\">\n"
            + "\t\t\t\t\t<parm name=\"AppRef\" value=\"IMS-Setting\"/>\n"
            + "\t\t\t\t\t<parm name=\"rcsVolteSingleRegistration\" value=\"1\"/>\n"
            + "\t\t\t\t</characteristic>\n"
            + "\t\t\t</characteristic>\n"
            + "\t\t</characteristic>\n"
            + "\t\t<characteristic type=\"SERVICES\">\n"
            + "\t\t\t<parm name=\"SupportedRCSProfileVersions\" value=\"UP2.3\"/>\n"
            + "\t\t\t<parm name=\"ChatAuth\" value=\"1\"/>\n"
            + "\t\t\t<parm name=\"GroupChatAuth\" value=\"1\"/>\n"
            + "\t\t\t<parm name=\"ftAuth\" value=\"1\"/>\n"
            + "\t\t\t<parm name=\"standaloneMsgAuth\" value=\"1\"/>\n"
            + "\t\t\t<parm name=\"geolocPushAuth\" value=\"1\"/>\n"
            + "\t\t\t<characteristic type=\"Ext\">\n"
            + "\t\t\t\t<characteristic type=\"DataOff\">\n"
            + "\t\t\t\t\t<parm name=\"rcsMessagingDataOff\" value=\"1\"/>\n"
            + "\t\t\t\t\t<parm name=\"fileTransferDataOff\" value=\"1\"/>\n"
            + "\t\t\t\t\t<parm name=\"mmsDataOff\" value=\"1\"/>\n"
            + "\t\t\t\t\t<parm name=\"syncDataOff\" value=\"1\"/>\n"
            + "\t\t\t\t\t<characteristic type=\"Ext\"/>\n"
            + "\t\t\t\t</characteristic>\n"
            + "\t\t\t</characteristic>\n"
            + "\t\t</characteristic>\n"
            + "\t</characteristic>\n"
            + "</wap-provisioningdoc>\n";

    private static final String CONFIG_SINGLE_REGISTRATION_DISABLED = "<?xml version=\"1.0\"?>\n"
            + "<wap-provisioningdoc version=\"1.1\">\n"
            + "\t<characteristic type=\"APPLICATION\">\n"
            + "\t\t<parm name=\"AppID\" value=\"urn:oma:mo:ext-3gpp-ims:1.0\"/>\n"
            + "\t\t<characteristic type=\"3GPP_IMS\">\n"
            + "\t\t\t<parm name=\"AppID\" value=\"ap2001\"/>\n"
            + "\t\t\t<parm name=\"Name\" value=\"RCS IMS Settings\"/>\n"
            + "\t\t\t<characteristic type=\"Ext\">\n"
            + "\t\t\t\t<characteristic type=\"GSMA\">\n"
            + "\t\t\t\t\t<parm name=\"AppRef\" value=\"IMS-Setting\"/>\n"
            + "\t\t\t\t\t<parm name=\"rcsVolteSingleRegistration\" value=\"0\"/>\n"
            + "\t\t\t\t</characteristic>\n"
            + "\t\t\t</characteristic>\n"
            + "\t\t</characteristic>\n"
            + "\t</characteristic>\n"
            + "</wap-provisioningdoc>\n";
    private static final int FAKE_SUB_ID_BASE = 0x0FFFFFF0;
    private static final String DEFAULT_MESSAGING_APP1 = "DMA1";
    private static final String DEFAULT_MESSAGING_APP2 = "DMA2";

    private RcsProvisioningMonitor mRcsProvisioningMonitor;
    private Handler mHandler;
    private HandlerThread mHandlerThread;
    private TestableLooper mLooper;
    private PersistableBundle mBundle;
    private MockContentResolver mContentResolver = new MockContentResolver();
    private SimInfoContentProvider mProvider;
    private BroadcastReceiver mReceiver;
    private static final int TEST_SUB_ID = 1;
    @Mock
    private Cursor mCursor;
    @Mock
    private SubscriptionManager mSubscriptionManager;
    private SubscriptionManager.OnSubscriptionsChangedListener mSubChangedListener;
    @Mock
    private TelephonyRegistryManager mTelephonyRegistryManager;
    @Mock
    private CarrierConfigManager mCarrierConfigManager;
    private OnRoleHoldersChangedListener mRoleHolderChangedListener;
    @Mock
    private RcsProvisioningMonitor.RoleManagerAdapter mRoleManager;
    @Mock
    private ITelephony.Stub mITelephony;
    @Mock
    private RcsFeatureManager mFeatureManager;
    @Mock
    private RcsProvisioningMonitor.FeatureConnectorFactory<RcsFeatureManager> mFeatureFactory;
    @Mock
    private FeatureConnector<RcsFeatureManager> mFeatureConnector;
    @Captor
    ArgumentCaptor<FeatureConnector.Listener<RcsFeatureManager>> mConnectorListener;
    @Mock
    private IImsConfig.Stub mIImsConfig;
    @Mock
    private Resources mResources;
    @Mock
    private PhoneGlobals mPhone;
    @Mock
    private IRcsConfigCallback mCallback;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private RcsStats mRcsStats;
    @Mock
    private RcsStats.RcsProvisioningCallback mRcsProvisioningCallback;

    private Executor mExecutor = new Executor() {
        @Override
        public void execute(Runnable r) {
            r.run();
        }
    };

    private class SimInfoContentProvider extends MockContentProvider {
        private Cursor mCursor;
        private ContentValues mValues;

        SimInfoContentProvider(Context context) {
            super(context);
        }

        public void setCursor(Cursor cursor) {
            mCursor = cursor;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection,
                String[] selectionArgs, String sortOrder) {
            return mCursor;
        }

        @Override
        public int update(Uri uri, ContentValues values,
                String selection, String[] selectionArgs) {
            mValues = values;
            return 1;
        }

        ContentValues getContentValues() {
            return mValues;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mPhone.getResources()).thenReturn(mResources);
        when(mPhone.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.hasSystemFeature(
                eq(PackageManager.FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION))).thenReturn(true);
        when(mPhone.getMainExecutor()).thenReturn(mExecutor);
        when(mPhone.getSystemServiceName(eq(CarrierConfigManager.class)))
                .thenReturn(Context.CARRIER_CONFIG_SERVICE);
        when(mPhone.getSystemServiceName(eq(SubscriptionManager.class)))
                .thenReturn(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        when(mPhone.getSystemServiceName(eq(TelephonyRegistryManager.class)))
                .thenReturn(Context.TELEPHONY_REGISTRY_SERVICE);
        when(mPhone.getSystemServiceName(eq(RoleManager.class)))
                .thenReturn(Context.ROLE_SERVICE);
        when(mPhone.getSystemService(eq(Context.CARRIER_CONFIG_SERVICE)))
                .thenReturn(mCarrierConfigManager);
        when(mPhone.getSystemService(eq(Context.TELEPHONY_SUBSCRIPTION_SERVICE)))
                .thenReturn(mSubscriptionManager);
        when(mPhone.getSystemService(eq(Context.TELEPHONY_REGISTRY_SERVICE)))
                .thenReturn(mTelephonyRegistryManager);

        mBundle = new PersistableBundle();
        when(mCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(mBundle);

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                mReceiver = (BroadcastReceiver) invocation.getArguments()[0];
                return null;
            }
        }).when(mPhone).registerReceiver(any(BroadcastReceiver.class), any());

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

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                mRoleHolderChangedListener = (OnRoleHoldersChangedListener)
                        invocation.getArguments()[1];
                return null;
            }
        }).when(mRoleManager).addOnRoleHoldersChangedListenerAsUser(any(Executor.class),
                any(OnRoleHoldersChangedListener.class), any(UserHandle.class));
        List<String> dmas = new ArrayList<>();
        dmas.add(DEFAULT_MESSAGING_APP1);
        when(mRoleManager.getRoleHolders(eq(RoleManager.ROLE_SMS))).thenReturn(dmas);

        mProvider = new SimInfoContentProvider(mPhone);
        mProvider.setCursor(mCursor);
        mContentResolver.addProvider(SimInfo.CONTENT_URI.getAuthority(), mProvider);
        when(mPhone.getContentResolver()).thenReturn(mContentResolver);
        when(mCursor.moveToFirst()).thenReturn(true);
        when(mCursor.getColumnIndexOrThrow(any())).thenReturn(1);
        when(mCursor.getBlob(anyInt())).thenReturn(
                RcsConfig.compressGzip(CONFIG_DEFAULT.getBytes()));

        mHandlerThread = new HandlerThread("RcsProvisioningMonitorTest");
        mHandlerThread.start();
    }

    @After
    public void tearDown() throws Exception {
        if (mRcsProvisioningMonitor != null) {
            mRcsProvisioningMonitor.destroy();
            mRcsProvisioningMonitor = null;
        }

        if (mLooper != null) {
            mLooper.destroy();
            mLooper = null;
        }
    }

    @Test
    @SmallTest
    public void testInitWithSavedConfig() throws Exception {
        createMonitor(3);

        for (int i = 0; i < 3; i++) {
            assertTrue(Arrays.equals(CONFIG_DEFAULT.getBytes(),
                    mRcsProvisioningMonitor.getConfig(FAKE_SUB_ID_BASE + i)));
        }

        verify(mIImsConfig, times(3)).notifyRcsAutoConfigurationReceived(any(), anyBoolean());
    }

    @Test
    @SmallTest
    public void testInitWithoutSavedConfig() throws Exception {
        when(mCursor.getBlob(anyInt())).thenReturn(null);
        createMonitor(3);

        //Should not notify null config
        verify(mIImsConfig, never()).notifyRcsAutoConfigurationReceived(any(), anyBoolean());
    }

    @Test
    @SmallTest
    public void testSubInfoChanged() throws Exception {
        createMonitor(3);

        for (int i = 0; i < 3; i++) {
            assertTrue(Arrays.equals(CONFIG_DEFAULT.getBytes(),
                    mRcsProvisioningMonitor.getConfig(FAKE_SUB_ID_BASE + i)));
        }

        verify(mIImsConfig, times(3)).notifyRcsAutoConfigurationReceived(any(), anyBoolean());

        makeFakeActiveSubIds(1);
        mExecutor.execute(() -> mSubChangedListener.onSubscriptionsChanged());
        processAllMessages();

        for (int i = 1; i < 3; i++) {
            assertNull(mRcsProvisioningMonitor.getConfig(FAKE_SUB_ID_BASE + i));
        }
        verify(mIImsConfig, times(2)).notifyRcsAutoConfigurationRemoved();
    }

    @Test
    @SmallTest
    public void testDefaultMessagingApplicationChangedWithAcs() throws Exception {
        createMonitor(1);
        updateDefaultMessageApplication(DEFAULT_MESSAGING_APP2);
        mBundle.putBoolean(CarrierConfigManager.KEY_USE_ACS_FOR_RCS_BOOL, true);
        processAllMessages();
        byte[] configCached = mRcsProvisioningMonitor.getConfig(FAKE_SUB_ID_BASE);

        assertNull(configCached);
        assertNull(mProvider.getContentValues().get(SimInfo.COLUMN_RCS_CONFIG));
        verify(mIImsConfig, atLeastOnce()).notifyRcsAutoConfigurationRemoved();
        verify(mIImsConfig, atLeastOnce()).triggerRcsReconfiguration();
        // The api should only be called when monitor is initilized.
        verify(mIImsConfig, times(1))
                .notifyRcsAutoConfigurationReceived(any(), anyBoolean());
    }

    @Test
    @SmallTest
    public void testDefaultMessagingApplicationChangedWithoutAcs() throws Exception {
        createMonitor(1);
        updateDefaultMessageApplication(DEFAULT_MESSAGING_APP2);
        mBundle.putBoolean(CarrierConfigManager.KEY_USE_ACS_FOR_RCS_BOOL, false);
        processAllMessages();
        byte[] configCached = mRcsProvisioningMonitor.getConfig(FAKE_SUB_ID_BASE);

        assertTrue(Arrays.equals(CONFIG_DEFAULT.getBytes(), configCached));
        verify(mIImsConfig, times(1)).notifyRcsAutoConfigurationRemoved();
        // The api should be called 2 times, one happens when monitor is initilized,
        // Another happens when DMS is changed.
        verify(mIImsConfig, times(2))
                .notifyRcsAutoConfigurationReceived(any(), anyBoolean());
    }

    @Test
    @SmallTest
    public void testCarrierConfigChanged() throws Exception {
        createMonitor(1);
        // should not broadcast message if carrier config is not ready
        verify(mPhone, never()).sendBroadcast(any(), any());

        when(mPackageManager.hasSystemFeature(
                eq(PackageManager.FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION))).thenReturn(true);
        ArgumentCaptor<Intent> captorIntent = ArgumentCaptor.forClass(Intent.class);
        mBundle.putBoolean(CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        mBundle.putBoolean(
                CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, true);

        broadcastCarrierConfigChange(FAKE_SUB_ID_BASE);
        processAllMessages();

        verify(mPhone, times(1)).sendBroadcast(captorIntent.capture(), any());
        Intent capturedIntent = captorIntent.getValue();
        assertEquals(capturedIntent.getAction(),
                ProvisioningManager.ACTION_RCS_SINGLE_REGISTRATION_CAPABILITY_UPDATE);
        assertEquals(FAKE_SUB_ID_BASE, capturedIntent.getIntExtra(
                ProvisioningManager.EXTRA_SUBSCRIPTION_ID, -1));
        assertEquals(ProvisioningManager.STATUS_CAPABLE,
                capturedIntent.getIntExtra(ProvisioningManager.EXTRA_STATUS, -1));

        mBundle.putBoolean(
                CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, false);
        broadcastCarrierConfigChange(FAKE_SUB_ID_BASE);
        processAllMessages();

        verify(mPhone, times(2)).sendBroadcast(captorIntent.capture(), any());
        capturedIntent = captorIntent.getValue();
        assertEquals(capturedIntent.getAction(),
                ProvisioningManager.ACTION_RCS_SINGLE_REGISTRATION_CAPABILITY_UPDATE);
        assertEquals(FAKE_SUB_ID_BASE, capturedIntent.getIntExtra(
                ProvisioningManager.EXTRA_SUBSCRIPTION_ID, -1));
        assertEquals(ProvisioningManager.STATUS_CARRIER_NOT_CAPABLE,
                capturedIntent.getIntExtra(ProvisioningManager.EXTRA_STATUS, -1));


        when(mPackageManager.hasSystemFeature(
                eq(PackageManager.FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION))).thenReturn(false);
        broadcastCarrierConfigChange(FAKE_SUB_ID_BASE);
        processAllMessages();

        verify(mPhone, times(3)).sendBroadcast(captorIntent.capture(), any());
        capturedIntent = captorIntent.getValue();
        assertEquals(capturedIntent.getAction(),
                ProvisioningManager.ACTION_RCS_SINGLE_REGISTRATION_CAPABILITY_UPDATE);
        assertEquals(FAKE_SUB_ID_BASE, capturedIntent.getIntExtra(
                ProvisioningManager.EXTRA_SUBSCRIPTION_ID, -1));
        assertEquals(ProvisioningManager.STATUS_CARRIER_NOT_CAPABLE
                | ProvisioningManager.STATUS_DEVICE_NOT_CAPABLE,
                capturedIntent.getIntExtra(ProvisioningManager.EXTRA_STATUS, -1));
    }

    @Test
    @SmallTest
    public void testUpdateConfig() throws Exception {
        createMonitor(1);
        final ArgumentCaptor<byte[]> argumentBytes = ArgumentCaptor.forClass(byte[].class);

        mRcsProvisioningMonitor.updateConfig(FAKE_SUB_ID_BASE, CONFIG_DEFAULT.getBytes(), false);
        processAllMessages();

        verify(mIImsConfig, atLeastOnce()).notifyRcsAutoConfigurationReceived(
                argumentBytes.capture(), eq(false));
        assertTrue(Arrays.equals(CONFIG_DEFAULT.getBytes(), argumentBytes.getValue()));
    }

    @Test
    @SmallTest
    public void testRequestReconfig() throws Exception {
        createMonitor(1);

        mRcsProvisioningMonitor.requestReconfig(FAKE_SUB_ID_BASE);
        processAllMessages();

        verify(mIImsConfig, times(1)).notifyRcsAutoConfigurationRemoved();
        verify(mIImsConfig, times(1)).triggerRcsReconfiguration();
    }

    @Test
    @SmallTest
    public void testIsRcsVolteSingleRegistrationEnabled() throws Exception {
        createMonitor(1);

        when(mPackageManager.hasSystemFeature(
                eq(PackageManager.FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION))).thenReturn(false);
        mBundle.putBoolean(CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        mBundle.putBoolean(
                CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, false);
        broadcastCarrierConfigChange(FAKE_SUB_ID_BASE);
        processAllMessages();
        assertFalse(mRcsProvisioningMonitor.isRcsVolteSingleRegistrationEnabled(FAKE_SUB_ID_BASE));

        when(mPackageManager.hasSystemFeature(
                eq(PackageManager.FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION))).thenReturn(true);
        mBundle.putBoolean(
                CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, false);
        broadcastCarrierConfigChange(FAKE_SUB_ID_BASE);
        processAllMessages();
        assertFalse(mRcsProvisioningMonitor.isRcsVolteSingleRegistrationEnabled(FAKE_SUB_ID_BASE));


        when(mPackageManager.hasSystemFeature(
                eq(PackageManager.FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION))).thenReturn(false);
        mBundle.putBoolean(
                CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, true);
        broadcastCarrierConfigChange(FAKE_SUB_ID_BASE);
        processAllMessages();
        assertFalse(mRcsProvisioningMonitor.isRcsVolteSingleRegistrationEnabled(FAKE_SUB_ID_BASE));

        when(mPackageManager.hasSystemFeature(
                eq(PackageManager.FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION))).thenReturn(true);
        mBundle.putBoolean(
                CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, true);
        broadcastCarrierConfigChange(FAKE_SUB_ID_BASE);
        processAllMessages();
        assertTrue(mRcsProvisioningMonitor.isRcsVolteSingleRegistrationEnabled(FAKE_SUB_ID_BASE));

        mRcsProvisioningMonitor.updateConfig(FAKE_SUB_ID_BASE, null, false);
        processAllMessages();
        assertTrue(mRcsProvisioningMonitor.isRcsVolteSingleRegistrationEnabled(FAKE_SUB_ID_BASE));

        mRcsProvisioningMonitor.updateConfig(FAKE_SUB_ID_BASE, CONFIG_DEFAULT.getBytes(), false);
        processAllMessages();
        assertTrue(mRcsProvisioningMonitor.isRcsVolteSingleRegistrationEnabled(FAKE_SUB_ID_BASE));

        mRcsProvisioningMonitor.updateConfig(FAKE_SUB_ID_BASE,
                CONFIG_SINGLE_REGISTRATION_DISABLED.getBytes(), false);
        processAllMessages();
        assertTrue(mRcsProvisioningMonitor.isRcsVolteSingleRegistrationEnabled(FAKE_SUB_ID_BASE));

        assertNull(mRcsProvisioningMonitor.isRcsVolteSingleRegistrationEnabled(
                FAKE_SUB_ID_BASE + 1));
    }

    @Test
    @SmallTest
    public void testRegisterThenUnregisterCallback() throws Exception {
        createMonitor(1);

        boolean result = mRcsProvisioningMonitor.registerRcsProvisioningCallback(
                FAKE_SUB_ID_BASE, mCallback);

        assertTrue(result);
        verify(mIImsConfig, times(1)).addRcsConfigCallback(eq(mCallback));

        result = mRcsProvisioningMonitor.unregisterRcsProvisioningCallback(
                FAKE_SUB_ID_BASE, mCallback);

        assertTrue(result);
        verify(mIImsConfig, times(1)).removeRcsConfigCallback(eq(mCallback));
        verify(mCallback, times(1)).onRemoved();
    }

    @Test
    @SmallTest
    public void testCallbackRemovedWhenSubInfoChanged() throws Exception {
        createMonitor(1);

        boolean result = mRcsProvisioningMonitor.registerRcsProvisioningCallback(
                FAKE_SUB_ID_BASE, mCallback);
        makeFakeActiveSubIds(0);
        mExecutor.execute(() -> mSubChangedListener.onSubscriptionsChanged());
        processAllMessages();

        assertTrue(result);
        verify(mIImsConfig, times(1)).removeRcsConfigCallback(eq(mCallback));
        verify(mCallback, times(1)).onRemoved();
    }

    @Test
    @SmallTest
    public void testCallbackRemovedWhenDmaChanged() throws Exception {
        createMonitor(1);

        boolean result = mRcsProvisioningMonitor.registerRcsProvisioningCallback(
                FAKE_SUB_ID_BASE, mCallback);
        updateDefaultMessageApplication(DEFAULT_MESSAGING_APP2);
        processAllMessages();

        assertTrue(result);
        verify(mIImsConfig, times(1)).removeRcsConfigCallback(eq(mCallback));
        verify(mCallback, times(1)).onRemoved();
    }

    @Test
    @SmallTest
    public void testSendBroadcastWhenDmaChanged() throws Exception {
        when(mCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(null);
        mBundle.putBoolean(CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        createMonitor(1);
        updateDefaultMessageApplication(DEFAULT_MESSAGING_APP2);
        processAllMessages();

        // should not broadcast message as no carrier config change happens
        verify(mPhone, never()).sendBroadcast(any(), any());

        when(mCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(mBundle);
        when(mPackageManager.hasSystemFeature(
                eq(PackageManager.FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION))).thenReturn(true);
        ArgumentCaptor<Intent> captorIntent = ArgumentCaptor.forClass(Intent.class);
        mBundle.putBoolean(
                CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, true);

        broadcastCarrierConfigChange(FAKE_SUB_ID_BASE);
        processAllMessages();

        verify(mPhone, times(1)).sendBroadcast(captorIntent.capture(), any());
        Intent capturedIntent = captorIntent.getValue();
        assertEquals(capturedIntent.getAction(),
                ProvisioningManager.ACTION_RCS_SINGLE_REGISTRATION_CAPABILITY_UPDATE);

        updateDefaultMessageApplication(DEFAULT_MESSAGING_APP1);
        processAllMessages();

        // should broadcast message when default messaging application changed if carrier config
        // has been loaded
        verify(mPhone, times(2)).sendBroadcast(captorIntent.capture(), any());
        capturedIntent = captorIntent.getValue();
        assertEquals(capturedIntent.getAction(),
                ProvisioningManager.ACTION_RCS_SINGLE_REGISTRATION_CAPABILITY_UPDATE);
    }

    @Test
    @SmallTest
    public void testRcsConnectedAndDisconnected() throws Exception {
        createMonitor(1);
        mRcsProvisioningMonitor.registerRcsProvisioningCallback(
                FAKE_SUB_ID_BASE, mCallback);

        verify(mIImsConfig, times(1))
                .notifyRcsAutoConfigurationReceived(any(), anyBoolean());

        mConnectorListener.getValue().connectionUnavailable(0);

        verify(mCallback, times(1)).onRemoved();
    }

    @Test
    @SmallTest
    public void testOverrideDeviceSingleRegistrationEnabled() throws Exception {
        createMonitor(1);

        when(mPackageManager.hasSystemFeature(
                eq(PackageManager.FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION))).thenReturn(true);
        mBundle.putBoolean(CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        mBundle.putBoolean(
                CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, true);
        broadcastCarrierConfigChange(FAKE_SUB_ID_BASE);
        processAllMessages();
        assertTrue(mRcsProvisioningMonitor.isRcsVolteSingleRegistrationEnabled(FAKE_SUB_ID_BASE));

        mRcsProvisioningMonitor.overrideDeviceSingleRegistrationEnabled(false);
        processAllMessages();
        assertFalse(mRcsProvisioningMonitor.getDeviceSingleRegistrationEnabled());
        assertFalse(mRcsProvisioningMonitor.isRcsVolteSingleRegistrationEnabled(FAKE_SUB_ID_BASE));

        mRcsProvisioningMonitor.overrideDeviceSingleRegistrationEnabled(null);
        processAllMessages();
        assertTrue(mRcsProvisioningMonitor.getDeviceSingleRegistrationEnabled());
        assertTrue(mRcsProvisioningMonitor.isRcsVolteSingleRegistrationEnabled(FAKE_SUB_ID_BASE));

        when(mPackageManager.hasSystemFeature(
                eq(PackageManager.FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION))).thenReturn(false);
        //use carrier config change to refresh the value as system feature is static
        mBundle.putBoolean(
                CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, true);
        broadcastCarrierConfigChange(FAKE_SUB_ID_BASE);
        processAllMessages();

        assertFalse(mRcsProvisioningMonitor.isRcsVolteSingleRegistrationEnabled(FAKE_SUB_ID_BASE));

        mRcsProvisioningMonitor.overrideDeviceSingleRegistrationEnabled(true);
        processAllMessages();
        assertTrue(mRcsProvisioningMonitor.getDeviceSingleRegistrationEnabled());
        assertTrue(mRcsProvisioningMonitor.isRcsVolteSingleRegistrationEnabled(FAKE_SUB_ID_BASE));

        mRcsProvisioningMonitor.overrideDeviceSingleRegistrationEnabled(null);
        processAllMessages();
        assertFalse(mRcsProvisioningMonitor.getDeviceSingleRegistrationEnabled());
        assertFalse(mRcsProvisioningMonitor.isRcsVolteSingleRegistrationEnabled(FAKE_SUB_ID_BASE));
    }

    @Test
    @SmallTest
    public void testTestModeEnabledAndDisabled() throws Exception {
        when(mCursor.getBlob(anyInt())).thenReturn(null);
        createMonitor(1);

        verify(mCursor, times(1)).getBlob(anyInt());

        mRcsProvisioningMonitor.setTestModeEnabled(true);
        processAllMessages();

        //should not query db in test mode
        verify(mCursor, times(1)).getBlob(anyInt());
        assertNull(mRcsProvisioningMonitor.getConfig(FAKE_SUB_ID_BASE));

        mRcsProvisioningMonitor.updateConfig(FAKE_SUB_ID_BASE, CONFIG_DEFAULT.getBytes(), false);
        processAllMessages();

        //config cahced in monitor should be updated, but db should not
        assertNull(mProvider.getContentValues());
        assertTrue(Arrays.equals(CONFIG_DEFAULT.getBytes(),
                mRcsProvisioningMonitor.getConfig(FAKE_SUB_ID_BASE)));

        //verify if monitor goes back to normal mode
        mRcsProvisioningMonitor.setTestModeEnabled(false);
        processAllMessages();

        verify(mCursor, times(2)).getBlob(anyInt());
        assertNull(mRcsProvisioningMonitor.getConfig(FAKE_SUB_ID_BASE));

        mRcsProvisioningMonitor.updateConfig(FAKE_SUB_ID_BASE, CONFIG_DEFAULT.getBytes(), false);
        processAllMessages();

        assertTrue(Arrays.equals(CONFIG_DEFAULT.getBytes(),
                mRcsProvisioningMonitor.getConfig(FAKE_SUB_ID_BASE)));
        assertTrue(Arrays.equals(RcsConfig.compressGzip(CONFIG_DEFAULT.getBytes()),
                (byte[]) mProvider.getContentValues().get(SimInfo.COLUMN_RCS_CONFIG)));
    }

    @Test
    @SmallTest
    public void testOverrideCarrierSingleRegistrationEnabled() throws Exception {
        createMonitor(1);

        when(mPackageManager.hasSystemFeature(
                eq(PackageManager.FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION))).thenReturn(true);
        mBundle.putBoolean(CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        mBundle.putBoolean(
                CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, true);
        broadcastCarrierConfigChange(FAKE_SUB_ID_BASE);
        processAllMessages();
        assertTrue(mRcsProvisioningMonitor.isRcsVolteSingleRegistrationEnabled(FAKE_SUB_ID_BASE));

        mRcsProvisioningMonitor
                .overrideCarrierSingleRegistrationEnabled(FAKE_SUB_ID_BASE, false);
        processAllMessages();
        assertFalse(mRcsProvisioningMonitor.getCarrierSingleRegistrationEnabled(FAKE_SUB_ID_BASE));
        assertFalse(mRcsProvisioningMonitor.isRcsVolteSingleRegistrationEnabled(FAKE_SUB_ID_BASE));

        mRcsProvisioningMonitor
                .overrideCarrierSingleRegistrationEnabled(FAKE_SUB_ID_BASE, null);
        processAllMessages();
        assertTrue(mRcsProvisioningMonitor.getCarrierSingleRegistrationEnabled(FAKE_SUB_ID_BASE));
        assertTrue(mRcsProvisioningMonitor.isRcsVolteSingleRegistrationEnabled(FAKE_SUB_ID_BASE));

        mBundle.putBoolean(
                CarrierConfigManager.Ims.KEY_IMS_SINGLE_REGISTRATION_REQUIRED_BOOL, false);
        broadcastCarrierConfigChange(FAKE_SUB_ID_BASE);
        processAllMessages();
        assertFalse(mRcsProvisioningMonitor.isRcsVolteSingleRegistrationEnabled(FAKE_SUB_ID_BASE));

        mRcsProvisioningMonitor
                .overrideCarrierSingleRegistrationEnabled(FAKE_SUB_ID_BASE, true);
        processAllMessages();
        assertTrue(mRcsProvisioningMonitor.getCarrierSingleRegistrationEnabled(FAKE_SUB_ID_BASE));
        assertTrue(mRcsProvisioningMonitor.isRcsVolteSingleRegistrationEnabled(FAKE_SUB_ID_BASE));

        mRcsProvisioningMonitor
                .overrideCarrierSingleRegistrationEnabled(FAKE_SUB_ID_BASE, null);
        processAllMessages();
        assertFalse(mRcsProvisioningMonitor.getCarrierSingleRegistrationEnabled(FAKE_SUB_ID_BASE));
        assertFalse(mRcsProvisioningMonitor.isRcsVolteSingleRegistrationEnabled(FAKE_SUB_ID_BASE));
    }

    @Test
    @SmallTest
    public void testOverrideImsFeatureValidation() throws Exception {
        createMonitor(1);

        mRcsProvisioningMonitor.overrideImsFeatureValidation(FAKE_SUB_ID_BASE, false);
        assertFalse(mRcsProvisioningMonitor.getImsFeatureValidationOverride(FAKE_SUB_ID_BASE));

        mRcsProvisioningMonitor.overrideImsFeatureValidation(FAKE_SUB_ID_BASE, true);
        assertTrue(mRcsProvisioningMonitor.getImsFeatureValidationOverride(FAKE_SUB_ID_BASE));

        mRcsProvisioningMonitor.overrideImsFeatureValidation(FAKE_SUB_ID_BASE, null);
        assertNull(mRcsProvisioningMonitor.getImsFeatureValidationOverride(FAKE_SUB_ID_BASE));
    }

    @Test
    @SmallTest
    public void testMetricsAcsNotUsed() throws Exception {
        createMonitor(1);

        // Not used ACS
        mBundle.putBoolean(CarrierConfigManager.KEY_USE_ACS_FOR_RCS_BOOL, false);
        broadcastCarrierConfigChange(FAKE_SUB_ID_BASE);
        processAllMessages();
        mRcsProvisioningMonitor.updateConfig(FAKE_SUB_ID_BASE, CONFIG_DEFAULT.getBytes(), false);
        processAllMessages();
        verify(mRcsStats, never()).onRcsAcsProvisioningStats(anyInt(), anyInt(),
                anyInt(), anyBoolean());
    }

    @Test
    @SmallTest
    public void testMetricsAcsUsed() throws Exception {
        when(mRcsStats.getRcsProvisioningCallback(anyInt(), anyBoolean()))
                .thenReturn(mRcsProvisioningCallback);
        createMonitor(1);

        verify(mIImsConfig, times(1))
                .notifyRcsAutoConfigurationReceived(any(), anyBoolean());
        // verify RcsStats.getRcsProvisioningCallback() is called
        verify(mRcsStats, times(1)).getRcsProvisioningCallback(
                eq(FAKE_SUB_ID_BASE), anyBoolean());
        // verify registered callback obj which comes from RcsStats.getRcsProvisioningCallback()
        verify(mIImsConfig, times(1))
                .addRcsConfigCallback(eq(mRcsProvisioningCallback));

        // Config data received and ACS used
        int errorCode = 200;
        mBundle.putBoolean(CarrierConfigManager.KEY_USE_ACS_FOR_RCS_BOOL, true);
        broadcastCarrierConfigChange(FAKE_SUB_ID_BASE);
        processAllMessages();
        mRcsProvisioningMonitor.updateConfig(FAKE_SUB_ID_BASE, CONFIG_DEFAULT.getBytes(), false);
        processAllMessages();
        verify(mRcsStats, times(1)).onRcsAcsProvisioningStats(eq(FAKE_SUB_ID_BASE), eq(errorCode),
                eq(RCS_ACS_PROVISIONING_STATS__RESPONSE_TYPE__PROVISIONING_XML), anyBoolean());
    }

    @Test
    @SmallTest
    public void testMetricsClientProvisioningStats() throws Exception {
        createMonitor(1);

        // reconfig trigger
        mRcsProvisioningMonitor.requestReconfig(FAKE_SUB_ID_BASE);
        processAllMessages();
        verify(mRcsStats, times(1)).onRcsClientProvisioningStats(eq(FAKE_SUB_ID_BASE),
                eq(RCS_CLIENT_PROVISIONING_STATS__EVENT__TRIGGER_RCS_RECONFIGURATION));

        // DMA changed
        updateDefaultMessageApplication(DEFAULT_MESSAGING_APP2);
        processAllMessages();
        verify(mRcsStats, times(1)).onRcsClientProvisioningStats(eq(FAKE_SUB_ID_BASE),
                eq(RCS_CLIENT_PROVISIONING_STATS__EVENT__DMA_CHANGED));
    }

    private void createMonitor(int subCount) throws Exception {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        makeFakeActiveSubIds(subCount);
        when(mFeatureFactory.create(any(), anyInt(), mConnectorListener.capture(), any(), any()))
                .thenReturn(mFeatureConnector);
        when(mFeatureManager.getConfig()).thenReturn(mIImsConfig);
        mRcsProvisioningMonitor = new RcsProvisioningMonitor(mPhone, mHandlerThread.getLooper(),
                mRoleManager, mFeatureFactory, mRcsStats);
        mHandler = mRcsProvisioningMonitor.getHandler();
        try {
            mLooper = new TestableLooper(mHandler.getLooper());
        } catch (Exception e) {
            logd("Unable to create looper from handler.");
        }
        mConnectorListener.getValue().connectionReady(mFeatureManager, TEST_SUB_ID);

        verify(mFeatureConnector, atLeastOnce()).connect();
    }

    private void broadcastCarrierConfigChange(int subId) {
        Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        intent.putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, subId);
        mExecutor.execute(() -> {
            mReceiver.onReceive(mPhone, intent);
        });
    }

    private void makeFakeActiveSubIds(int count) {
        final int[] subIds = new int[count];
        for (int i = 0; i < count; i++) {
            subIds[i] = FAKE_SUB_ID_BASE + i;
        }
        when(mSubscriptionManager.getActiveSubscriptionIdList()).thenReturn(subIds);
    }

    private void updateDefaultMessageApplication(String packageName) {
        List<String> dmas = new ArrayList<>();
        dmas.add(packageName);
        when(mRoleManager.getRoleHolders(eq(RoleManager.ROLE_SMS))).thenReturn(dmas);
        mExecutor.execute(() -> mRoleHolderChangedListener.onRoleHoldersChanged(
                RoleManager.ROLE_SMS, UserHandle.ALL));
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
