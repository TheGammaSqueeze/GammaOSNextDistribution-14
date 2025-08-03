/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.telephony.cts.util.TelephonyUtils;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.HashMap;
import java.util.Map;

public class MockCentralizedNetworkAgent {
    private static final String TAG = "MCNA";

    // The Preferred data phone: slot0 = 0, slot1 = 1.
    private static int sPreferredDataPhone = -1;

    // The shell command to get the NetworkAgent information.
    private static String sQueryTelephonyDebugServiceCommand =
            "dumpsys activity service com.android.phone.TelephonyDebugService";

    private static IRadioDataImpl[] sIRadioDataImpls;

    private static Map<Integer, String> sDataCalls = new HashMap<>();

    private static String sImsPhone0 = new String();
    private static String sImsPhone1 = new String();
    private static String sInternetInfo = new String();

    public static int getPreferredDataPhone() {
        Log.e(TAG, "getPreferredDataPhone(): enter");
        return sPreferredDataPhone;
    }

    public static void setPreferredDataPhone(int phoneId) {
        Log.e(TAG, "setPreferredDataPhone(): enter");
        Log.d(TAG, "setPreferredDataPhone: " + phoneId);
        sPreferredDataPhone =  phoneId;
        resetCapability();
        setInternetToPreferredDataPhone();
    }

    public static void setNetworkAgentInfo(
            IRadioDataImpl[] iRadioDataImpls, int numOfPhone) throws Exception {
        Log.e(TAG, "setNetworkAgentInfo(): enter");
        sIRadioDataImpls = iRadioDataImpls;
        String result =
                TelephonyUtils.executeShellCommand(
                        InstrumentationRegistry.getInstrumentation(),
                        sQueryTelephonyDebugServiceCommand);

        for (int numSim = 0; numSim < numOfPhone; numSim++) {
            String targetString = "DataNetworkController-" + Integer.toString(numSim);
            String tmpString = targetString
                    + result.split(targetString)[1].split("Pending tear down data networks:")[0];
            sDataCalls.put(numSim, tmpString);
            iRadioDataImpls[numSim]
                .getMockDataServiceInstance().setBridgeTheDataConnection(tmpString);
            MockCentralizedNetworkAgent.storeDataCall(numSim, tmpString);
            // Single SIM case
            if (numOfPhone == 1) {
                sPreferredDataPhone = numSim;
                resetCapability();
                setInternetToPreferredDataPhone();
            }
        }
    }


    public static synchronized void storeDataCall(int phoneId, String string) {
        Log.e(TAG, "storeDataCall(): enter");
        String patternDataNetworkController = "DataNetworkController-" + phoneId;
        String patternAllTtelephonyNetworkRequests = "All telephony network requests:";
        String patternCurrentState = "curState=ConnectedState";
        try {
            String[] lines = new String[] {};
            String line =
                    string.split(patternDataNetworkController)[1]
                            .split(patternAllTtelephonyNetworkRequests)[0];
            if (line.contains(patternCurrentState)) {
                lines = line.split((patternCurrentState));
            }
            for (String str : lines) {
                String capabilities = getCapabilities(str);
                if (capabilities.contains("INTERNET")) {
                    sInternetInfo =
                        patternCurrentState
                        + " " + str
                        + " " + patternAllTtelephonyNetworkRequests;
                    Log.d(TAG, "sInternetInfo: " + sInternetInfo);
                } else if (capabilities.contains("IMS")) {
                    if (phoneId == 0) {
                        sImsPhone0 =
                            patternDataNetworkController
                            + " " + patternCurrentState
                            + " " + str
                            + " " + patternAllTtelephonyNetworkRequests;
                    }
                    if (phoneId == 1) {
                        sImsPhone1 =
                            patternDataNetworkController
                            + " " + patternCurrentState
                            + " " + str
                            + " " + patternAllTtelephonyNetworkRequests;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception error: [No NetworkAgentInfo]" + e);
        }
    }

    private static String getCapabilities(String string) {
        Log.e(TAG, "getCapabilities(): enter");
        String capabilities = "";
        try {
            capabilities = string.trim().split("Capabilities:")[1].split("LinkUpBandwidth")[0];
            Log.d(TAG, "getCapabilities: " + capabilities);
        } catch (Exception e) {
            Log.e(TAG, "getCapabilities(): Exception error: " + e);
        }
        return capabilities;
    }

    private static void resetCapability() {
        Log.e(TAG, "resetCapability(): enter");
        for (IRadioDataImpl iRadioData : sIRadioDataImpls) {
            iRadioData.getMockDataServiceInstance().resetCapability();
        }
    }

    private static void setInternetToPreferredDataPhone() {
        Log.e(TAG, "setInternetToPreferredDataPhone(): enter");
        String DataNetworkControllerPattern = "DataNetworkController-" + sPreferredDataPhone;
        // ims
        for (int i = 0; i < sIRadioDataImpls.length; i++) {
            sIRadioDataImpls[i]
                .getMockDataServiceInstance()
                .setBridgeTheDataConnection(i == 0 ? sImsPhone0 : sImsPhone1);
        }
        // internet
        String tmp = DataNetworkControllerPattern + " " + sInternetInfo;
        sIRadioDataImpls[sPreferredDataPhone]
            .getMockDataServiceInstance()
            .setBridgeTheDataConnection(tmp);
    }
}
