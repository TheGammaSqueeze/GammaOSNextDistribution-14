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

package android.telephony.mockmodem;

import android.content.Context;
import android.hardware.radio.RadioError;
import android.hardware.radio.data.DataCallFailCause;
import android.hardware.radio.data.DataProfileInfo;
import android.hardware.radio.data.LinkAddress;
import android.hardware.radio.data.PdpProtocolType;
import android.hardware.radio.data.Qos;
import android.hardware.radio.data.QosSession;
import android.hardware.radio.data.SetupDataCallResult;
import android.hardware.radio.data.SliceInfo;
import android.hardware.radio.data.TrafficDescriptor;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class MockDataService {
    private static final String TAG = "MockDataService";
    private String mTag;
    private Context mContext;

    // Data Profile
    List<DataProfileInfo> mDataProfileInfo = new ArrayList<>();
    DataProfileInfo mInitialAttachProfile;
    int mInitialAttachProfileId;

    // setup data call
    public static final int APN_TYPE_IMS = 0;
    public static final int APN_TYPE_DEFAULT = 1;
    public static final int PHONE0 = 0;
    public static final int PHONE1 = 1;

    SetupDataCallResult mSetupDataCallResultIms = new SetupDataCallResult();
    SetupDataCallResult mSetupDataCallResultDefault = new SetupDataCallResult();

    // data call list
    static List<SetupDataCallResult> sDataCallLists = new ArrayList<>();

    // Supported capability in physical layer
    static List<String> sSupportedCapabilities = new ArrayList<>();

    /* Mock Data Config XML TAG definition */
    private static final String MOCK_DATA_CONFIG_TAG = "MockDataConfig";
    private static final String MOCK_CUTTLEFISH_CONFIG_TAG = "CuttlefishConfig";
    private static final String MOCK_CUTTLEFISH_TYPE = "Cuttlefish";
    private static final String MOCK_INTERFACE_NAME_TAG = "InterfaceName";
    private static final String MOCK_IP_ADDRESS_TAG = "IpAddress";
    private static final String MOCK_DNS_ADDRESS_TAG = "DnsAddress";
    private static final String MOCK_GATEWAY_ADDRESS_TAG = "GatewayAddress";
    private static final String MOCK_MTU_V4_TAG = "MtuV4";

    // Setup data call result parameteres
    int mDataCallFailCause;
    int mSuggestedRetryTime;
    int mImsCid;
    int mImsType;
    String mImsIfname;
    String mImsAddress;
    String[] mImsGateways;
    String[] mImsPcscf;
    int mImsMtuV4;
    int mImsMtuV6;
    int mInternetCid;
    int mInternetType;
    String mInternetIfname;
    String mInternetAddress;
    String[] mInternetDnses;
    String[] mInternetGateways;
    int mInternetMtuV4;
    int mInternetMtuV6;
    int mAddressProperties;
    long mLaDeprecationTime;
    long mLaExpirationTime;
    Qos mDefaultQos;
    QosSession[] mQosSessions;
    byte mHandoverFailureMode;
    int mPduSessionId;
    SliceInfo mSliceInfo;
    TrafficDescriptor[] mTrafficDescriptors;
    private final Object mDataCallListLock = new Object();
    LinkAddress[] mImsLinkAddress;
    LinkAddress[] mDefaultLinkAddress;
    int mPhoneId;

    public MockDataService(Context context, int instanceId) {
        mTag = TAG + "-" + instanceId;
        mContext = context;
        mPhoneId = instanceId;
        initializeParameter();
    }

    /* Default value definition */
    private void initializeParameter() {
        this.mDataCallFailCause = DataCallFailCause.NONE;
        this.mSuggestedRetryTime = -1;

        this.mImsType = PdpProtocolType.IP;
        this.mImsIfname = "mock_network0";
        this.mImsAddress = "192.168.66.2";
        this.mImsGateways = new String[] {"0.0.0.0"};
        this.mImsPcscf = new String[] {"192.168.66.100", "192.168.66.101"};
        this.mImsMtuV4 = 1280;
        this.mImsMtuV6 = 0;
        this.mImsLinkAddress = new LinkAddress[1];

        this.mInternetType = PdpProtocolType.IP;
        this.mInternetIfname = "mock_network1";
        this.mInternetAddress = "192.168.66.1";
        this.mInternetDnses = new String[] {"8.8.8.8"};
        this.mInternetGateways = new String[] {"0.0.0.0"};
        this.mInternetMtuV4 = 1280;
        this.mInternetMtuV6 = 0;
        this.mDefaultLinkAddress = new LinkAddress[1];

        this.mAddressProperties = LinkAddress.ADDRESS_PROPERTY_NONE;
        this.mLaDeprecationTime = 0x7FFFFFFF;
        this.mLaExpirationTime = 0x7FFFFFFF;

        this.mDefaultQos = new Qos();
        this.mQosSessions = new QosSession[0];
        this.mHandoverFailureMode =
                SetupDataCallResult.HANDOVER_FAILURE_MODE_NO_FALLBACK_RETRY_HANDOVER;
        this.mPduSessionId = 0;
        this.mSliceInfo = null;
        this.mTrafficDescriptors = new TrafficDescriptor[0];
    }

    public int setDataProfileInfo(DataProfileInfo[] dataProfilesInfo) {
        int result = RadioError.NONE;
        if (dataProfilesInfo != null) {
            mDataProfileInfo.clear();
            for (DataProfileInfo dp : dataProfilesInfo) {
                mDataProfileInfo.add(dp);
                Log.d(mTag, "setDataProfileInfo: profileId=" + dp.profileId + ", " + dp.apn);
            }
            return result;
        }
        return RadioError.INVALID_ARGUMENTS;
    }

    public int setInitialAttachProfile(DataProfileInfo dataProfileInfo) {
        int result = RadioError.NONE;
        if (dataProfileInfo != null) {
            Log.d(mTag, "setInitialAttachProfile: profileId=" + dataProfileInfo.profileId);
            mInitialAttachProfile = dataProfileInfo;
            mInitialAttachProfileId = dataProfileInfo.profileId;
            return result;
        }
        return RadioError.INVALID_ARGUMENTS;
    }

    SetupDataCallResult setupDataCall(int apnType) {
        checkExistDataCall(apnType);

        SetupDataCallResult dc = new SetupDataCallResult();
        dc.cause = this.mDataCallFailCause;
        dc.suggestedRetryTime = this.mSuggestedRetryTime;

        dc.active = SetupDataCallResult.DATA_CONNECTION_STATUS_ACTIVE;
        LinkAddress[] arrayLinkAddress = new LinkAddress[1];
        LinkAddress linkAddress = new LinkAddress();

        switch (apnType) {
            case APN_TYPE_IMS:
                dc.cid = this.mImsCid;
                dc.type = this.mImsType;
                dc.ifname = this.mImsIfname;
                linkAddress.address = this.mImsAddress;
                linkAddress.addressProperties = this.mAddressProperties;
                linkAddress.deprecationTime = this.mLaDeprecationTime;
                linkAddress.expirationTime = this.mLaExpirationTime;
                arrayLinkAddress[0] = linkAddress;
                dc.addresses = this.mImsLinkAddress;
                dc.gateways = this.mImsGateways;
                dc.pcscf = this.mImsPcscf;
                dc.mtuV4 = this.mImsMtuV4;
                dc.mtuV6 = this.mImsMtuV6;
                break;
            case APN_TYPE_DEFAULT:
                dc.cid = this.mInternetCid;
                dc.type = this.mInternetType;
                dc.ifname = this.mInternetIfname;
                linkAddress.address = this.mInternetAddress;
                linkAddress.addressProperties = this.mAddressProperties;
                linkAddress.deprecationTime = this.mLaDeprecationTime;
                linkAddress.expirationTime = this.mLaExpirationTime;
                arrayLinkAddress[0] = linkAddress;
                dc.addresses = this.mDefaultLinkAddress;
                dc.dnses = this.mInternetDnses;
                dc.gateways = this.mInternetGateways;
                dc.mtuV4 = this.mInternetMtuV4;
                dc.mtuV6 = this.mInternetMtuV6;
                break;
            default:
                Log.d(mTag, "Unexpected APN type: " + apnType);
                return new SetupDataCallResult();
        }
        dc.defaultQos = this.mDefaultQos;
        dc.qosSessions = this.mQosSessions;
        dc.handoverFailureMode = this.mHandoverFailureMode;
        dc.pduSessionId = this.mPduSessionId;
        dc.sliceInfo = this.mSliceInfo;
        dc.trafficDescriptors = this.mTrafficDescriptors;
        synchronized (mDataCallListLock) {
            sDataCallLists.add(dc);
        }
        return dc;
    }

    /**
     * Get current data call list
     *
     * @return The SetupDataCallResult array list.
     */
    List<SetupDataCallResult> getDataCallList() {
        List<SetupDataCallResult> dataCallLists;
        synchronized (mDataCallListLock) {
            dataCallLists = sDataCallLists;
        }
        return dataCallLists;
    }

    void deactivateDataCall(int cid, int reason) {
        synchronized (mDataCallListLock) {
            Iterator<SetupDataCallResult> it = sDataCallLists.iterator();
            while (it.hasNext()) {
                SetupDataCallResult dc = it.next();
                if (dc.cid == cid) {
                    it.remove();
                }
            }
        }
    }

    void checkExistDataCall(int apnType) {
        synchronized (mDataCallListLock) {
            int cid = (apnType == APN_TYPE_IMS) ? mImsCid : mInternetCid;
            Iterator<SetupDataCallResult> it = sDataCallLists.iterator();
            while (it.hasNext()) {
                SetupDataCallResult dc = it.next();
                if (dc.cid == cid) {
                    it.remove();
                }
            }
        }
    }

    private int convertToMtuV4(String mtuv4) {
        int value = 0;
        try {
            value = Integer.parseInt(mtuv4);
        } catch (NumberFormatException ex) {
            Log.e(mTag, "Exception error: " + ex);
        }
        return value;
    }

    private String getInterfaceName(String string) {
        String interfaceName = "";
        try {
            interfaceName = string.split("InterfaceName: ")[1].split(" LinkAddresses")[0];
            Log.d(mTag, "getInterfaceName: " + interfaceName);
        } catch (Exception e) {
            Log.e(mTag, "Exception error: " + e);
        }
        return interfaceName;
    }

    private LinkAddress[] getIpAddress(String string) {
        String[] ipaddress = new String[] {};
        LinkAddress[] arrayLinkAddress = new LinkAddress[0];
        try {
            ipaddress =
                    string.split("LinkAddresses: \\[ ")[1].split(" ] DnsAddresses")[0].split(",");
            arrayLinkAddress = new LinkAddress[ipaddress.length];
            for (int idx = 0; idx < ipaddress.length; idx++) {
                if (ipaddress[idx].equals("LinkAddresses: [ ]")) {
                    throw new Exception("Not valid ip address");
                }
                LinkAddress linkAddress = new LinkAddress();
                linkAddress.address = ipaddress[idx];
                linkAddress.addressProperties = this.mAddressProperties;
                linkAddress.deprecationTime = this.mLaDeprecationTime;
                linkAddress.expirationTime = this.mLaExpirationTime;
                arrayLinkAddress[idx] = linkAddress;
                Log.d(mTag, "getIpAddress:" + linkAddress.address);
            }
        } catch (Exception e) {
            Log.e(mTag, "Exception error: " + e);
        }
        return arrayLinkAddress;
    }

    private String[] getDnses(String string) {
        String[] dnses = new String[] {};
        try {
            dnses =
                    string.split("DnsAddresses: \\[ ")[1]
                            .split(" ] Domains:")[0]
                            .replace("/", "")
                            .split(",");
            Log.d(mTag, "getDnses: " + Arrays.toString(dnses));
        } catch (Exception e) {
            Log.e(mTag, "Exception error: " + e);
        }
        return dnses;
    }

    private String[] getGateways(String string) {
        ArrayList<String> gateways = new ArrayList<String>();
        try {
            gateways.add(
                    string.split("Routes: \\[ ")[1]
                            .split("-> ")[1]
                            .split(" ")[0]);
            Log.d(mTag, "getGateways: " + gateways);
        } catch (Exception e) {
            Log.e(mTag, "Exception error: " + e);
        }
        return gateways.toArray(new String[gateways.size()]);
    }

    private int getMtu(String string) {
        String mtu = "";
        try {
            mtu = string.split(" MTU: ")[1].split(" TcpBufferSizes:")[0];
            Log.d(mTag, "getMtu: " + mtu);
        } catch (Exception e) {
            Log.e(mTag, "Exception error: " + e);
        }
        return Integer.valueOf(mtu);
    }

    private String[] getPcscf(String string) {
        String[] pcscf = new String[] {};
        try {
            pcscf =
                    string.split(" PcscfAddresses: \\[ ")[1]
                            .split(" ] Domains:")[0]
                            .replace("/", "")
                            .split(",");
            Log.d(mTag, "getPcscf: " + Arrays.toString(pcscf));
        } catch (Exception e) {
            Log.e(mTag, "Exception error: " + e);
        }
        return pcscf;
    }

    private String getCapabilities(String string) {
        String capabilities = "";
        try {
            capabilities = string.trim().split("Capabilities:")[1].split("LinkUpBandwidth")[0];
            Log.d(mTag, "getCapabilities: " + capabilities);
        } catch (Exception e) {
            Log.e(mTag, "getCapabilities(): Exception error: " + e);
        }
        return capabilities;
    }

    private int getCid(String string) {
        int cid = 0;
        try {
            String strCid = string.split("WWAN cid=")[1].split("WLAN cid")[0].trim();
            cid = Integer.parseInt(strCid);
            Log.d(mTag, "getCid: " + strCid);
        } catch (Exception e) {
            Log.e(mTag, "getCid(): Exception error: " + e);
        }
        return cid;
    }

    public synchronized void setBridgeTheDataConnection(String string) {
        try {
            String[] lines = new String[] {};
            String line =
                    string.split("DataNetworkController-" + mPhoneId)[1]
                            .split("All telephony network requests:")[0];
            if (line.contains("curState=ConnectedState")) {
                lines = line.split(("curState=ConnectedState"));
            }
            for (String str : lines) {
                String capabilities = getCapabilities(str);
                if (capabilities.contains("INTERNET")) {
                    Log.d(mTag, "[internet]:" + str);
                    sSupportedCapabilities.add("internet");
                    this.mInternetCid = getCid(str);
                    this.mInternetIfname = getInterfaceName(str);
                    this.mDefaultLinkAddress = getIpAddress(str);
                    this.mInternetDnses = getDnses(str);
                    this.mInternetGateways = getGateways(str);
                    this.mInternetMtuV4 = getMtu(str);
                    this.mInternetMtuV6 = getMtu(str);
                } else if (capabilities.contains("IMS")) {
                    Log.d(mTag, "[ims]:" + str);
                    sSupportedCapabilities.add("ims");
                    this.mImsCid = getCid(str);
                    this.mImsIfname = getInterfaceName(str);
                    this.mImsLinkAddress = getIpAddress(str);
                    this.mImsGateways = getGateways(str);
                    this.mImsPcscf = getPcscf(str);
                    this.mImsMtuV4 = getMtu(str);
                    this.mImsMtuV6 = getMtu(str);
                }
            }
        } catch (Exception e) {
            Log.e(mTag, "Exception error: [No NetworkAgentInfo]" + e);
        }
    }

    public synchronized void resetCapability() {
        sSupportedCapabilities = new ArrayList<>();
    }

    public synchronized boolean isSupportedCapability(String capability) {
        for (String cap : sSupportedCapabilities) {
            Log.d(mTag, "Supported Capability:" + cap + ", Requested Capability:" + capability);
            if (cap.contains(capability)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void setDataCallFailCause(int failcause) {
        this.mDataCallFailCause = failcause;
    }

    public synchronized void setSuggestedRetryTime(int retrytime) {
        this.mSuggestedRetryTime = retrytime;
    }

    public synchronized void setImsMtuV4(int mtusize) {
        this.mImsMtuV4 = mtusize;
    }

    public synchronized void setImsMtuV6(int mtusize) {
        this.mImsMtuV6 = mtusize;
    }

    public synchronized void setInternetMtuV4(int mtusize) {
        this.mInternetMtuV4 = mtusize;
    }

    public synchronized void setInternetMtuV6(int mtusize) {
        this.mInternetMtuV6 = mtusize;
    }
}
