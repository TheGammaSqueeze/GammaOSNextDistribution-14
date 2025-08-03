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

package android.telephony.mockmodem;

import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_DEFAULT;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.radio.RadioError;
import android.hardware.radio.RadioResponseInfo;
import android.hardware.radio.RadioResponseType;
import android.os.Binder;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Pair;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MockModemService extends Service {
    private static final String TAG = "MockModemService";
    private static final String RESOURCE_PACKAGE_NAME = "android";

    public static final int TEST_TIMEOUT_MS = 30000;
    public static final String IRADIOCONFIG_INTERFACE = "android.telephony.mockmodem.iradioconfig";
    public static final String IRADIOMODEM_INTERFACE = "android.telephony.mockmodem.iradiomodem";
    public static final String IRADIOSIM_INTERFACE = "android.telephony.mockmodem.iradiosim";
    public static final String IRADIONETWORK_INTERFACE =
            "android.telephony.mockmodem.iradionetwork";
    public static final String IRADIODATA_INTERFACE = "android.telephony.mockmodem.iradiodata";
    public static final String IRADIOMESSAGING_INTERFACE =
            "android.telephony.mockmodem.iradiomessaging";
    public static final String IRADIOVOICE_INTERFACE = "android.telephony.mockmodem.iradiovoice";
    public static final String IRADIOIMS_INTERFACE = "android.telephony.mockmodem.iradioims";
    public static final String PHONE_ID = "phone_id";

    private static MockModemConfigInterface sMockModemConfigInterface;
    private static MockCentralizedNetworkAgent sMockCentralizedNetworkAgent;
    private static IRadioConfigImpl sIRadioConfigImpl;
    private static IRadioModemImpl[] sIRadioModemImpl;
    private static IRadioSimImpl[] sIRadioSimImpl;
    private static IRadioNetworkImpl[] sIRadioNetworkImpl;
    private static IRadioDataImpl[] sIRadioDataImpl;
    private static IRadioMessagingImpl[] sIRadioMessagingImpl;
    private static IRadioVoiceImpl[] sIRadioVoiceImpl;
    private static IRadioImsImpl[] sIRadioImsImpl;

    public static final byte PHONE_ID_0 = 0x00;
    public static final byte PHONE_ID_1 = 0x01;
    public static final byte PHONE_ID_2 = 0x02;
    public static final byte MAX_PHONE_NUM = 3;

    public static final int LATCH_MOCK_MODEM_SERVICE_READY = 0;
    public static final int LATCH_RADIO_INTERFACES_READY = 1;
    public static final int LATCH_MAX = 2;

    private static final int IRADIO_CONFIG_INTERFACE_NUMBER = 1;
    private static final int IRADIO_INTERFACE_NUMBER = 6;

    private Context mContext;
    private TelephonyManager mTelephonyManager;
    private int mNumOfSim;
    private int mNumOfPhone;
    private static final int DEFAULT_SUB_ID = 0;

    private Object mLock;
    protected static CountDownLatch[] sLatches;
    private LocalBinder mBinder;

    // For local access of this Service.
    class LocalBinder extends Binder {
        MockModemService getService() {
            return MockModemService.this;
        }
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "Mock Modem Service Created");

        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mNumOfSim = getNumPhysicalSlots();
        mNumOfPhone = mTelephonyManager.getActiveModemCount();
        Log.d(TAG, "Support number of phone = " + mNumOfPhone + ", number of SIM = " + mNumOfSim);

        if (mNumOfPhone > MAX_PHONE_NUM) {
            mNumOfPhone = MAX_PHONE_NUM;
        }

        // Number of physical Sim slot should be equals to or greater than number of phone.
        if (mNumOfSim < mNumOfPhone) {
            mNumOfSim = mNumOfPhone;
        }

        mLock = new Object();
        sLatches = new CountDownLatch[LATCH_MAX];

        for (int i = 0; i < LATCH_MAX; i++) {
            sLatches[i] = new CountDownLatch(1);
            if (i == LATCH_RADIO_INTERFACES_READY) {
                int radioServiceSupportedNumber = 0;
                int radioInterfaceNumber = 0;

                try {
                    for (int j = TelephonyManager.HAL_SERVICE_DATA;
                            j <= TelephonyManager.HAL_SERVICE_IMS;
                            j++) {
                        Pair<Integer, Integer> halVersion = mTelephonyManager.getHalVersion(j);
                        if (halVersion.first == -2 && halVersion.second == -2) {
                            Log.d(TAG, "Service: " + j + " unsupported");
                        } else {
                            radioServiceSupportedNumber++;
                        }
                    }
                } catch (NoSuchMethodError | IllegalStateException e) {
                    Log.e(TAG, "Use the default number of interfaces - " + IRADIO_INTERFACE_NUMBER);
                    radioServiceSupportedNumber = IRADIO_INTERFACE_NUMBER;
                }

                radioInterfaceNumber =
                        IRADIO_CONFIG_INTERFACE_NUMBER + mNumOfPhone * radioServiceSupportedNumber;
                sLatches[i] = new CountDownLatch(radioInterfaceNumber);
            } else {
                sLatches[i] = new CountDownLatch(1);
            }
        }

        sMockModemConfigInterface = new MockModemConfigBase(mContext, mNumOfSim, mNumOfPhone);
        sIRadioConfigImpl = new IRadioConfigImpl(this,
            sMockModemConfigInterface,
            sMockCentralizedNetworkAgent,
            PHONE_ID_0);
        sIRadioModemImpl = new IRadioModemImpl[mNumOfPhone];
        sIRadioSimImpl = new IRadioSimImpl[mNumOfPhone];
        sIRadioNetworkImpl = new IRadioNetworkImpl[mNumOfPhone];
        sIRadioDataImpl = new IRadioDataImpl[mNumOfPhone];
        sIRadioMessagingImpl = new IRadioMessagingImpl[mNumOfPhone];
        sIRadioVoiceImpl = new IRadioVoiceImpl[mNumOfPhone];
        sIRadioImsImpl = new IRadioImsImpl[mNumOfPhone];
        for (int i = 0; i < mNumOfPhone; i++) {
            sIRadioModemImpl[i] = new IRadioModemImpl(this, sMockModemConfigInterface, i);
            sIRadioSimImpl[i] = new IRadioSimImpl(this, sMockModemConfigInterface, i);
            sIRadioNetworkImpl[i] =
                    new IRadioNetworkImpl(this, mContext, sMockModemConfigInterface, i);
            sIRadioDataImpl[i] = new IRadioDataImpl(this, mContext, sMockModemConfigInterface, i);
            sIRadioMessagingImpl[i] = new IRadioMessagingImpl(this, sMockModemConfigInterface, i);
            sIRadioVoiceImpl[i] = new IRadioVoiceImpl(this, sMockModemConfigInterface, i);
            sIRadioImsImpl[i] = new IRadioImsImpl(this, sMockModemConfigInterface, i);
        }
        try {
            sMockCentralizedNetworkAgent.setNetworkAgentInfo(sIRadioDataImpl, mNumOfPhone);
        } catch (Exception e) {
            Log.e(TAG, "Exception error: " + e);
        }
        mBinder = new LocalBinder();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Mock Modem Service on distory");
        if (sMockModemConfigInterface != null) {
            sMockModemConfigInterface.destroy();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {

        String action = intent.getAction();
        if (action == null) {
            countDownLatch(LATCH_MOCK_MODEM_SERVICE_READY);
            Log.i(TAG, "onBind-Local");
            return mBinder;
        }

        byte phoneId = intent.getByteExtra(PHONE_ID, PHONE_ID_0);

        if (phoneId >= MAX_PHONE_NUM) {
            Log.e(TAG, "Not suuport for phone " + phoneId);
            return null;
        }

        if (action.startsWith(IRADIOCONFIG_INTERFACE)) {
            Log.i(TAG, "onBind-IRadioConfig " + phoneId);
            return sIRadioConfigImpl;
        } else if (action.startsWith(IRADIOMODEM_INTERFACE)) {
            Log.i(TAG, "onBind-IRadioModem " + phoneId);
            return sIRadioModemImpl[phoneId];
        } else if (action.startsWith(IRADIOSIM_INTERFACE)) {
            Log.i(TAG, "onBind-IRadioSim " + phoneId);
            return sIRadioSimImpl[phoneId];
        } else if (action.startsWith(IRADIONETWORK_INTERFACE)) {
            Log.i(TAG, "onBind-IRadioNetwork " + phoneId);
            return sIRadioNetworkImpl[phoneId];
        } else if (action.startsWith(IRADIODATA_INTERFACE)) {
            Log.i(TAG, "onBind-IRadioData " + phoneId);
            return sIRadioDataImpl[phoneId];
        } else if (action.startsWith(IRADIOMESSAGING_INTERFACE)) {
            Log.i(TAG, "onBind-IRadioMessaging " + phoneId);
            return sIRadioMessagingImpl[phoneId];
        } else if (action.startsWith(IRADIOVOICE_INTERFACE)) {
            Log.i(TAG, "onBind-IRadioVoice " + phoneId);
            return sIRadioVoiceImpl[phoneId];
        } else if (action.startsWith(IRADIOIMS_INTERFACE)) {
            Log.i(TAG, "onBind-IRadioIms " + phoneId);
            return sIRadioImsImpl[phoneId];
        }

        return null;
    }

    public boolean waitForLatchCountdown(int latchIndex) {
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (mLock) {
                latch = sLatches[latchIndex];
            }
            complete = latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        }
        synchronized (mLock) {
            sLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    public boolean waitForLatchCountdown(int latchIndex, long waitMs) {
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (mLock) {
                latch = sLatches[latchIndex];
            }
            complete = latch.await(waitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        }
        synchronized (mLock) {
            sLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    public void countDownLatch(int latchIndex) {
        synchronized (mLock) {
            sLatches[latchIndex].countDown();
        }
    }

    public int getNumPhysicalSlots() {
        int numPhysicalSlots = MockSimService.MOCK_SIM_SLOT_MIN;
        int resourceId =
                mContext.getResources()
                        .getIdentifier(
                                "config_num_physical_slots", "integer", RESOURCE_PACKAGE_NAME);

        if (resourceId > 0) {
            numPhysicalSlots = mContext.getResources().getInteger(resourceId);
        } else {
            Log.d(TAG, "Fail to get the resource Id, using default: " + numPhysicalSlots);
        }

        if (numPhysicalSlots > MockSimService.MOCK_SIM_SLOT_MAX) {
            Log.d(
                    TAG,
                    "Number of physical Slot ("
                            + numPhysicalSlots
                            + ") > mock sim slot support. Reset to max number supported ("
                            + MockSimService.MOCK_SIM_SLOT_MAX
                            + ").");
            numPhysicalSlots = MockSimService.MOCK_SIM_SLOT_MAX;
        } else if (numPhysicalSlots < MockSimService.MOCK_SIM_SLOT_MIN) {
            Log.d(
                    TAG,
                    "Number of physical Slot ("
                            + numPhysicalSlots
                            + ") < mock sim slot support. Reset to min number supported ("
                            + MockSimService.MOCK_SIM_SLOT_MIN
                            + ").");
            numPhysicalSlots = MockSimService.MOCK_SIM_SLOT_MIN;
        }

        return numPhysicalSlots;
    }

    public RadioResponseInfo makeSolRsp(int serial) {
        RadioResponseInfo rspInfo = new RadioResponseInfo();
        rspInfo.type = RadioResponseType.SOLICITED;
        rspInfo.serial = serial;
        rspInfo.error = RadioError.NONE;

        return rspInfo;
    }

    public RadioResponseInfo makeSolRsp(int serial, int error) {
        RadioResponseInfo rspInfo = new RadioResponseInfo();
        rspInfo.type = RadioResponseType.SOLICITED;
        rspInfo.serial = serial;
        rspInfo.error = error;

        return rspInfo;
    }

    public boolean initialize(int[] simprofiles) {
        Log.d(TAG, "initialize for num of Sim = " + simprofiles.length);
        boolean result = true;

        // Sync mock modem status between modules
        for (int i = 0; i < mNumOfPhone; i++) {
            // Set initial SIM profiles
            if (simprofiles != null && i < simprofiles.length) {
                result = sMockModemConfigInterface.changeSimProfile(i, simprofiles[i], TAG);
            } else {
                result =
                        sMockModemConfigInterface.changeSimProfile(
                                i, MOCK_SIM_PROFILE_ID_DEFAULT, TAG);
            }

            // Sync modem configurations to radio modules
            sMockModemConfigInterface.notifyAllRegistrantNotifications();

            // Connect to telephony framework
            sIRadioModemImpl[i].rilConnected();
        }

        return result;
    }

    public MockModemConfigInterface getMockModemConfigInterface() {
        return sMockModemConfigInterface;
    }

    public IRadioConfigImpl getIRadioConfig() {
        return sIRadioConfigImpl;
    }

    public IRadioModemImpl getIRadioModem() {
        return getIRadioModem(PHONE_ID_0);
    }

    public IRadioModemImpl getIRadioModem(byte phoneId) {
        return sIRadioModemImpl[phoneId];
    }

    public IRadioSimImpl getIRadioSim() {
        return getIRadioSim(PHONE_ID_0);
    }

    public IRadioSimImpl getIRadioSim(byte phoneId) {
        return sIRadioSimImpl[phoneId];
    }

    public IRadioNetworkImpl getIRadioNetwork() {
        return getIRadioNetwork(PHONE_ID_0);
    }

    public IRadioNetworkImpl getIRadioNetwork(byte phoneId) {
        return sIRadioNetworkImpl[phoneId];
    }

    public IRadioVoiceImpl getIRadioVoice() {
        return getIRadioVoice(PHONE_ID_0);
    }

    public IRadioVoiceImpl getIRadioVoice(byte phoneId) {
        return sIRadioVoiceImpl[phoneId];
    }

    public IRadioMessagingImpl getIRadioMessaging() {
        return getIRadioMessaging(PHONE_ID_0);
    }

    public IRadioMessagingImpl getIRadioMessaging(byte phoneId) {
        return sIRadioMessagingImpl[phoneId];
    }

    public IRadioDataImpl getIRadioData() {
        return getIRadioData(PHONE_ID_0);
    }

    public IRadioDataImpl getIRadioData(byte phoneId) {
        return sIRadioDataImpl[phoneId];
    }

    public IRadioImsImpl getIRadioIms() {
        return getIRadioIms(PHONE_ID_0);
    }

    public IRadioImsImpl getIRadioIms(byte phoneId) {
        return sIRadioImsImpl[phoneId];
    }

    public int getActiveMockModemCount() {
        return mNumOfPhone;
    }
}
