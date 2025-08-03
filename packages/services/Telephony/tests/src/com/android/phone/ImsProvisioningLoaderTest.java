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

package com.android.phone;

import static junit.framework.Assert.assertEquals;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.PersistableBundle;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.feature.RcsFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Unit Test for ImsProvisioningLoader.
 */
public class ImsProvisioningLoaderTest {
    private static final String LOG_TAG = ImsProvisioningLoaderTest.class.getSimpleName();

    private static final int IMS_FEATURE_MMTEL = ImsProvisioningLoader.IMS_FEATURE_MMTEL;
    private static final int IMS_FEATURE_RCS = ImsProvisioningLoader.IMS_FEATURE_RCS;

    private static final int TECH_LTE = ImsRegistrationImplBase.REGISTRATION_TECH_LTE;
    private static final int TECH_IWLAN = ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN;
    private static final int TECH_NEW = Integer.MAX_VALUE;

    private static final int CAPA_VOICE = MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE;
    private static final int CAPA_VIDEO = MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VIDEO;
    private static final int CAPA_UT = MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_UT;
    private static final int CAPA_PRESENCE =
            RcsFeature.RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE;
    private static final int CAPA_NEW = Integer.MAX_VALUE;

    private static final int STATUS_NOT_PROVISIONED = ImsProvisioningLoader.STATUS_NOT_PROVISIONED;
    private static final int STATUS_PROVISIONED = ImsProvisioningLoader.STATUS_PROVISIONED;

    private static final int SUB_ID_1 = 111111;
    private static final int SUB_ID_2 = 222222;

    @Mock
    Context mContext;
    @Mock
    SharedPreferences mSharedPreferences;
    private ImsProvisioningLoader mImsProvisioningLoader;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(mSharedPreferences).when(mContext).getSharedPreferences(anyString(), anyInt());
        doReturn(InstrumentationRegistry.getTargetContext().getFilesDir()).when(
                mContext).getFilesDir();

        mImsProvisioningLoader = new ImsProvisioningLoader(mContext);
    }

    @After
    public void tearDown() throws Exception {
        if (mImsProvisioningLoader != null) {
            mImsProvisioningLoader.clear();
        }
        deleteXml(SUB_ID_1, mContext);
        deleteXml(SUB_ID_2, mContext);
    }

    @Test
    @SmallTest
    public void testSetProvisioningStatus_ExistFeature() {
        // Set MMTEL IWLAN VOICE to STATUS_PROVISIONED
        String[] info =
                new String[]{IMS_FEATURE_MMTEL + "," + TECH_IWLAN + "," + CAPA_VOICE + "," + getInt(
                        true)};
        mImsProvisioningLoader.setProvisioningToXml(SUB_ID_1, new PersistableBundle(), info);

        int curValue = mImsProvisioningLoader.getProvisioningStatus(SUB_ID_1, IMS_FEATURE_MMTEL,
                CAPA_VOICE, TECH_IWLAN);
        assertEquals(getXmlContents(SUB_ID_1), getInt(true), curValue);

        // Change MMTEL IWLAN VOICE provisioning status
        boolean saveResult = mImsProvisioningLoader.setProvisioningStatus(SUB_ID_1,
                IMS_FEATURE_MMTEL, CAPA_VOICE, TECH_IWLAN, false);
        curValue = mImsProvisioningLoader.getProvisioningStatus(SUB_ID_1,
                IMS_FEATURE_MMTEL, CAPA_VOICE, TECH_IWLAN);
        assertEquals(getXmlContents(SUB_ID_1), true, saveResult);
        assertEquals(getXmlContents(SUB_ID_1), getInt(false), curValue);

        // If set to the same provisioning status,  don't save it.
        saveResult = mImsProvisioningLoader.setProvisioningStatus(SUB_ID_1,
                IMS_FEATURE_MMTEL, CAPA_VOICE, TECH_IWLAN, false);
        curValue = mImsProvisioningLoader.getProvisioningStatus(SUB_ID_1,
                IMS_FEATURE_MMTEL, CAPA_VOICE, TECH_IWLAN);
        assertEquals(getXmlContents(SUB_ID_1), false, saveResult);
        assertEquals(getXmlContents(SUB_ID_1), getInt(false), curValue);
    }

    @Test
    @SmallTest
    public void testSetProvisioningStatus_NewFeature() {
        // Set new capability
        // Return true as a result to setProvisioningStatus()
        boolean saveResult = mImsProvisioningLoader.setProvisioningStatus(SUB_ID_1,
                IMS_FEATURE_MMTEL, CAPA_NEW, TECH_LTE, true);
        int curValue = mImsProvisioningLoader.getProvisioningStatus(SUB_ID_1,
                IMS_FEATURE_MMTEL, CAPA_NEW, TECH_LTE);
        assertEquals(getXmlContents(SUB_ID_1), true, saveResult);
        assertEquals(getXmlContents(SUB_ID_1), getInt(true), curValue);

        // Set new tech
        saveResult = mImsProvisioningLoader.setProvisioningStatus(SUB_ID_1,
                IMS_FEATURE_MMTEL, CAPA_VOICE, TECH_NEW, false);
        curValue = mImsProvisioningLoader.getProvisioningStatus(SUB_ID_1,
                IMS_FEATURE_MMTEL, CAPA_VOICE, TECH_NEW);
        assertEquals(getXmlContents(SUB_ID_1), true, saveResult);
        assertEquals(getXmlContents(SUB_ID_1), getInt(false), curValue);
    }

    @Test
    @SmallTest
    public void testSetProvisioningStatus_DifferentSim() {
        // Check whether the provisioning status does not change even if SIM is changed
        // Sub id 2, set provisioning status
        boolean prevValue = getBooleanFromProvisioningStatus(SUB_ID_2,
                IMS_FEATURE_RCS, CAPA_PRESENCE, TECH_IWLAN);
        boolean saveResult = mImsProvisioningLoader.setProvisioningStatus(
                SUB_ID_2, IMS_FEATURE_RCS, CAPA_PRESENCE, TECH_IWLAN, !prevValue);
        int curValue = mImsProvisioningLoader.getProvisioningStatus(SUB_ID_2,
                IMS_FEATURE_RCS, CAPA_PRESENCE, TECH_IWLAN);
        assertEquals(getXmlContents(SUB_ID_2), true, saveResult);
        assertEquals(getXmlContents(SUB_ID_2), getInt(!prevValue), curValue);

        // Sub id 1, set other provisioned status
        mImsProvisioningLoader.setProvisioningStatus(
                SUB_ID_1, IMS_FEATURE_RCS, CAPA_PRESENCE, TECH_IWLAN, prevValue);

        // Sub id 2, check the previous provisioning status isn't changed
        curValue = mImsProvisioningLoader.getProvisioningStatus(SUB_ID_2,
                IMS_FEATURE_RCS, CAPA_PRESENCE, TECH_IWLAN);
        assertEquals(getXmlContents(SUB_ID_2), getInt(!prevValue), curValue);
    }

    @Test
    @SmallTest
    public void testGetProvisioningStatus_UtProvisioningStatusIsExistInPref() {
        // Ut provisioning status exists in preference
        doReturn(1).when(mSharedPreferences).getInt(anyString(), anyInt());
        int curValue = mImsProvisioningLoader.getProvisioningStatus(SUB_ID_1,
                IMS_FEATURE_MMTEL, CAPA_UT, TECH_LTE);
        assertEquals(getXmlContents(SUB_ID_1), getInt(true), curValue);
    }

    @Test
    @SmallTest
    public void testGetProvisioningStatus_ExistXml() {
        // Set MMTEL LTE VOICE to STATUS_PROVISIONED, MMTEL LTE VIDEO to STATUS_NOT_PROVISIONED
        String[] info =
                new String[]{IMS_FEATURE_MMTEL + "," + TECH_LTE + "," + CAPA_VOICE + "," + getInt(
                        true),
                        IMS_FEATURE_MMTEL + "," + TECH_LTE + "," + CAPA_VIDEO + "," + getInt(
                                false)};
        mImsProvisioningLoader.setProvisioningToXml(SUB_ID_1, new PersistableBundle(), info);

        int curValue = mImsProvisioningLoader.getProvisioningStatus(SUB_ID_1,
                IMS_FEATURE_MMTEL, CAPA_VOICE, TECH_LTE);
        assertEquals(getXmlContents(SUB_ID_1), getInt(true), curValue);

        curValue = mImsProvisioningLoader.getProvisioningStatus(SUB_ID_1,
                IMS_FEATURE_MMTEL, CAPA_VIDEO, TECH_LTE);
        assertEquals(getXmlContents(SUB_ID_1), getInt(false), curValue);
    }

    private boolean getBooleanFromProvisioningStatus(int subId, int imsFeature, int capa,
            int tech) {
        // Return provisioning status to bool
        return mImsProvisioningLoader.getProvisioningStatus(
                subId, imsFeature, capa, tech) == STATUS_PROVISIONED ? true
                : false;
    }

    private int getInt(boolean isProvisioned) {
        return isProvisioned ? STATUS_PROVISIONED : STATUS_NOT_PROVISIONED;
    }

    private void deleteXml(int subId, Context context) {
        String fileName = getFileName(subId);
        File file = null;
        try {
            file = new File(context.getFilesDir(), fileName);
        } catch (Exception e) {
            logd(e.toString());
        }
        file.delete();
    }

    private String getXmlContents(int subId) {
        String fileName = getFileName(subId);

        File file = null;
        FileInputStream inFile = null;
        StringBuilder readString = new StringBuilder();
        readString.append("file name " + fileName + "\n");
        byte[] buffer = new byte[1024];
        int n = 0;
        try {
            file = new File(mContext.getFilesDir(), fileName);
            inFile = new FileInputStream(file);
            while ((n = inFile.read(buffer)) != -1) {
                readString.append(new String(buffer, 0, n));
            }
            inFile.close();
        } catch (FileNotFoundException e) {
            logd(e.toString());

        } catch (IOException e) {
            logd(e.toString());
        }
        return readString.toString();
    }

    private String getFileName(int subId) {
        // Resulting name is imsprovisioningstatus_{subId}.xml
        return "imsprovisioningstatus_" + subId + ".xml";
    }

    private static void logd(String contents) {
        Log.d(LOG_TAG, contents);
    }

}
