/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.ims.rcs.uce.eab;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsRcsManager;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.aidl.IRcsUceControllerCallback;

import com.android.ims.ImsTestBase;
import com.android.ims.rcs.uce.UceController;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

public class EabBulkCapabilityUpdaterTest extends ImsTestBase {

    private final int mSubId = 1;

    private Handler mHandler;
    private HandlerThread mHandlerThread;

    @Mock
    private UceController.UceControllerCallback mMockUceControllerCallback;
    @Mock
    private EabControllerImpl mMockEabControllerImpl;
    @Mock
    private ImsRcsManager mImsRcsManager;
    @Mock
    private RcsUceAdapter mRcsUceAdapter;
    @Mock
    private SharedPreferences mSharedPreferences;
    @Mock
    private SharedPreferences.Editor mSharedPreferencesEditor;
    @Mock
    private EabContactSyncController mEabContactSyncController;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        mHandlerThread = new HandlerThread("TestThread");
        mHandlerThread.start();
        mHandler = mHandlerThread.getThreadHandler();

        doReturn(mSharedPreferences).when(mContext).getSharedPreferences(anyString(), anyInt());
        doReturn(0L).when(mSharedPreferences).getLong(anyString(), anyInt());
        doReturn(mSharedPreferencesEditor).when(mSharedPreferences).edit();
        doReturn(mSharedPreferencesEditor).when(mSharedPreferencesEditor).putLong(anyString(),
                anyLong());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        mHandlerThread.quit();
    }

    @Test
    public void testRefreshCapabilities() throws Exception {
        // mock user settings
        mockUceUserSettings(true);
        mockBulkCapabilityCarrierConfig(true);
        // mock expired contact list
        List<Uri> expiredContactList = new ArrayList<>();
        expiredContactList.add(Uri.parse("test"));
        doReturn(expiredContactList)
                .when(mEabContactSyncController)
                .syncContactToEabProvider(any());

        new EabBulkCapabilityUpdater(
                mContext,
                mSubId,
                mMockEabControllerImpl,
                mEabContactSyncController,
                mMockUceControllerCallback,
                mHandler);

        waitHandlerThreadFinish();

        verify(mMockUceControllerCallback).refreshCapabilities(
                anyList(),
                any(IRcsUceControllerCallback.class));
    }

    @Test
    public void testUceSettingsDisabled() throws Exception {
        // mock user settings
        mockUceUserSettings(false);
        mockBulkCapabilityCarrierConfig(true);
        // mock expired contact list
        List<Uri> expiredContactList = new ArrayList<>();
        expiredContactList.add(Uri.parse("test"));
        doReturn(expiredContactList)
                .when(mEabContactSyncController)
                .syncContactToEabProvider(any());

        new EabBulkCapabilityUpdater(
                mContext,
                mSubId,
                mMockEabControllerImpl,
                mEabContactSyncController,
                mMockUceControllerCallback,
                mHandler);

        waitHandlerThreadFinish();

        verify(mMockUceControllerCallback, never()).refreshCapabilities(
                any(),
                any(IRcsUceControllerCallback.class));
    }

    @Test
    public void testCarrierConfigDisabled() throws Exception {
        // mock user settings
        mockUceUserSettings(true);
        mockBulkCapabilityCarrierConfig(false);
        // mock expired contact list
        List<Uri> expiredContactList = new ArrayList<>();
        expiredContactList.add(Uri.parse("test"));
        doReturn(expiredContactList)
                .when(mEabContactSyncController)
                .syncContactToEabProvider(any());

        new EabBulkCapabilityUpdater(
                mContext,
                mSubId,
                mMockEabControllerImpl,
                mEabContactSyncController,
                mMockUceControllerCallback,
                mHandler);

        waitHandlerThreadFinish();

        verify(mMockUceControllerCallback, never()).refreshCapabilities(
                anyList(),
                any(IRcsUceControllerCallback.class));
    }

    @Test
    public void testCarrierConfigEnabled() throws Exception {
        // mock user settings
        mockUceUserSettings(true);
        mockBulkCapabilityCarrierConfig(false);
        // mock expired contact list
        List<Uri> expiredContactList = new ArrayList<>();
        expiredContactList.add(Uri.parse("test"));
        doReturn(expiredContactList)
                .when(mEabContactSyncController)
                .syncContactToEabProvider(any());

        EabBulkCapabilityUpdater mEabBulkCapabilityUpdater = new EabBulkCapabilityUpdater(
                mContext,
                mSubId,
                mMockEabControllerImpl,
                mEabContactSyncController,
                mMockUceControllerCallback,
                mHandler);

        mockBulkCapabilityCarrierConfig(true);
        mEabBulkCapabilityUpdater.onCarrierConfigChanged();
        waitHandlerThreadFinish();

        verify(mMockUceControllerCallback).refreshCapabilities(
                anyList(),
                any(IRcsUceControllerCallback.class));
    }

    private void mockBulkCapabilityCarrierConfig(boolean isEnabled) {
        PersistableBundle persistableBundle = new PersistableBundle();
        persistableBundle.putBoolean(
                CarrierConfigManager.Ims.KEY_RCS_BULK_CAPABILITY_EXCHANGE_BOOL, isEnabled);
        CarrierConfigManager carrierConfigManager =
                mContext.getSystemService(CarrierConfigManager.class);
        doReturn(persistableBundle).when(carrierConfigManager).getConfigForSubId(anyInt());
    }

    private void mockUceUserSettings(boolean isEnabled) throws ImsException {
        // mock uce user settings
        ImsManager imsManager = mContext.getSystemService(ImsManager.class);
        doReturn(mImsRcsManager).when(imsManager).getImsRcsManager(eq(mSubId));
        doReturn(mRcsUceAdapter).when(mImsRcsManager).getUceAdapter();
        doReturn(isEnabled).when(mRcsUceAdapter).isUceSettingEnabled();
    }

    private void waitHandlerThreadFinish() throws Exception {
        int retryTimes = 0;
        do {
            Thread.sleep(1000);
            retryTimes++;
        } while(mHandler.hasMessagesOrCallbacks() && retryTimes < 2);
    }
}
