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

package android.wifi.mockwifi.nl80211;

import android.net.wifi.nl80211.IClientInterface;
import android.net.wifi.nl80211.ISendMgmtFrameEvent;
import android.net.wifi.nl80211.IWifiScannerImpl;
import android.util.ArraySet;
import android.util.Log;

import java.util.Collections;
import java.util.Set;

public class IClientInterfaceImp extends IClientInterface.Stub {
    private static final String TAG = "IClientInterfaceImp";

    private IWifiScannerImp mIWifiScannerImp;
    private String mIfaceName = null;
    private ClientInterfaceMock mClientInterfaceMock;

    public interface ClientInterfaceMock {
        default int[] signalPoll() {
            return null;
        }

        default int[] getPacketCounters() {
            return null;
        }

        default byte[] getMacAddress() {
            return null;
        }

        default String getInterfaceName() {
            return null;
        }
    }

    public IClientInterfaceImp(String ifaceName) {
        mIfaceName = ifaceName;
        mIWifiScannerImp = new IWifiScannerImp(ifaceName);
    }

    private boolean isMethodOverridden(ClientInterfaceMock clientInterfaceMock,
            String methodName) throws NoSuchMethodException {
        return !clientInterfaceMock.getClass().getMethod(methodName).getDeclaringClass().equals(
                ClientInterfaceMock.class);
    }

    public Set<String> setClientInterfaceMock(ClientInterfaceMock clientInterfaceMock) {
        Set<String> overriddenMethods = new ArraySet<>();
        try {
            if (isMethodOverridden(clientInterfaceMock, "signalPoll")) {
                overriddenMethods.add("signalPoll");
            }
            if (isMethodOverridden(clientInterfaceMock, "getPacketCounters")) {
                overriddenMethods.add("getPacketCounters");
            }
            if (isMethodOverridden(clientInterfaceMock, "getMacAddress")) {
                overriddenMethods.add("getMacAddress");
            }
            if (isMethodOverridden(clientInterfaceMock, "getInterfaceName")) {
                overriddenMethods.add("getInterfaceName");
            }
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Reflection error: " + e);
            return Collections.emptySet();
        }

        mClientInterfaceMock = clientInterfaceMock;
        return overriddenMethods;
    }

    // Supported methods in IClientInterface.aidl
    @Override
    public int[] signalPoll() {
        Log.d(TAG, "signalPoll");
        if (mClientInterfaceMock == null) {
            Log.e(TAG, "signalPoll: null mock!");
            return null;
        }
        return mClientInterfaceMock.signalPoll();
    }

    @Override
    public int[] getPacketCounters() {
        Log.d(TAG, "getPacketCounters");
        // TODO: Mock it when we have a use (test) case.
        return null;
    }

    @Override
    public byte[] getMacAddress() {
        Log.d(TAG, "getMacAddress");
        // TODO: Mock it when we have a use (test) case.
        return null;
    }

    @Override
    public String getInterfaceName() {
        Log.d(TAG, "getInterfaceName");
        // TODO: Mock it when we have a use (test) case.
        return null;
    }

    @Override
    public IWifiScannerImpl getWifiScannerImpl() {
        Log.d(TAG, "getWifiScannerImpl");
        return mIWifiScannerImp;
    }

    // CHECKSTYLE:OFF Generated code
    @Override
    public void SendMgmtFrame(byte[] frame, ISendMgmtFrameEvent callback, int mcs) {
        Log.d(TAG, "SendMgmtFrame");
        // TODO: Mock it when we have a use (test) case.
    }
    // CHECKSTYLE:ON Generated code
}
