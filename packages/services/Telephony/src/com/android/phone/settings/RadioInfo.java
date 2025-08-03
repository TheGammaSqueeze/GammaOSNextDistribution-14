/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.phone.settings;

import static android.net.ConnectivityManager.NetworkCallback;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.hardware.radio.modem.ImeiInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.os.UserManager;
import android.telephony.AccessNetworkConstants;
import android.telephony.CarrierConfigManager;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.DataSpecificRegistrationInfo;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PhysicalChannelConfig;
import android.telephony.RadioAccessFamily;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.telephony.data.NetworkSlicingConfig;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsRcsManager;
import android.telephony.ims.ProvisioningManager;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AlertDialog.Builder;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.euicc.EuiccConnector;
import com.android.internal.telephony.util.TelephonyUtils;
import com.android.phone.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Radio Information Class
 *
 * Allows user to read and alter some of the radio related information.
 *
 */
public class RadioInfo extends CollapsingToolbarBaseActivity {
    private static final String TAG = "RadioInfo";

    private static final boolean IS_USER_BUILD = "user".equals(Build.TYPE);

    private static final String[] PREFERRED_NETWORK_LABELS = {
            "GSM/WCDMA preferred",
            "GSM only",
            "WCDMA only",
            "GSM/WCDMA auto (PRL)",
            "CDMA/EvDo auto (PRL)",
            "CDMA only",
            "EvDo only",
            "CDMA/EvDo/GSM/WCDMA (PRL)",
            "CDMA + LTE/EvDo (PRL)",
            "GSM/WCDMA/LTE (PRL)",
            "LTE/CDMA/EvDo/GSM/WCDMA (PRL)",
            "LTE only",
            "LTE/WCDMA",
            "TDSCDMA only",
            "TDSCDMA/WCDMA",
            "LTE/TDSCDMA",
            "TDSCDMA/GSM",
            "LTE/TDSCDMA/GSM",
            "TDSCDMA/GSM/WCDMA",
            "LTE/TDSCDMA/WCDMA",
            "LTE/TDSCDMA/GSM/WCDMA",
            "TDSCDMA/CDMA/EvDo/GSM/WCDMA ",
            "LTE/TDSCDMA/CDMA/EvDo/GSM/WCDMA",
            "NR only",
            "NR/LTE",
            "NR/LTE/CDMA/EvDo",
            "NR/LTE/GSM/WCDMA",
            "NR/LTE/CDMA/EvDo/GSM/WCDMA",
            "NR/LTE/WCDMA",
            "NR/LTE/TDSCDMA",
            "NR/LTE/TDSCDMA/GSM",
            "NR/LTE/TDSCDMA/WCDMA",
            "NR/LTE/TDSCDMA/GSM/WCDMA",
            "NR/LTE/TDSCDMA/CDMA/EvDo/GSM/WCDMA",
            "Unknown"
    };

    private static String[] sPhoneIndexLabels;

    private static final int sCellInfoListRateDisabled = Integer.MAX_VALUE;
    private static final int sCellInfoListRateMax = 0;

    private static final String OEM_RADIO_INFO_INTENT =
            "com.android.phone.settings.OEM_RADIO_INFO";

    private static final String DSDS_MODE_PROPERTY = "ro.boot.hardware.dsds";

    /**
     * A value indicates the device is always on dsds mode.
     * @see {@link #DSDS_MODE_PROPERTY}
     */
    private static final int ALWAYS_ON_DSDS_MODE = 1;

    //Values in must match CELL_INFO_REFRESH_RATES
    private static final String[] CELL_INFO_REFRESH_RATE_LABELS = {
            "Disabled",
            "Immediate",
            "Min 5s",
            "Min 10s",
            "Min 60s"
    };

    //Values in seconds, must match CELL_INFO_REFRESH_RATE_LABELS
    private static final int [] CELL_INFO_REFRESH_RATES = {
        sCellInfoListRateDisabled,
        sCellInfoListRateMax,
        5000,
        10000,
        60000
    };

    private static void log(String s) {
        Log.d(TAG, s);
    }

    private static final int EVENT_QUERY_SMSC_DONE = 1005;
    private static final int EVENT_UPDATE_SMSC_DONE = 1006;
    private static final int EVENT_PHYSICAL_CHANNEL_CONFIG_CHANGED = 1007;
    private static final int EVENT_UPDATE_NR_STATS = 1008;

    private static final int MENU_ITEM_VIEW_ADN            = 1;
    private static final int MENU_ITEM_VIEW_FDN            = 2;
    private static final int MENU_ITEM_VIEW_SDN            = 3;
    private static final int MENU_ITEM_GET_IMS_STATUS      = 4;
    private static final int MENU_ITEM_TOGGLE_DATA         = 5;

    private static final String CARRIER_PROVISIONING_ACTION =
            "com.android.phone.settings.CARRIER_PROVISIONING";
    private static final String TRIGGER_CARRIER_PROVISIONING_ACTION =
            "com.android.phone.settings.TRIGGER_CARRIER_PROVISIONING";

    private static final String ACTION_REMOVABLE_ESIM_AS_DEFAULT =
            "android.telephony.euicc.action.REMOVABLE_ESIM_AS_DEFAULT";

    private TextView mDeviceId; //DeviceId is the IMEI in GSM and the MEID in CDMA
    private TextView mLine1Number;
    private TextView mSubscriptionId;
    private TextView mDds;
    private TextView mSubscriberId;
    private TextView mCallState;
    private TextView mOperatorName;
    private TextView mRoamingState;
    private TextView mGsmState;
    private TextView mGprsState;
    private TextView mVoiceNetwork;
    private TextView mDataNetwork;
    private TextView mVoiceRawReg;
    private TextView mDataRawReg;
    private TextView mWlanDataRawReg;
    private TextView mOverrideNetwork;
    private TextView mDBm;
    private TextView mMwi;
    private TextView mCfi;
    private TextView mCellInfo;
    private TextView mSent;
    private TextView mReceived;
    private TextView mPingHostnameV4;
    private TextView mPingHostnameV6;
    private TextView mHttpClientTest;
    private TextView mPhyChanConfig;
    private TextView mDnsCheckState;
    private TextView mDownlinkKbps;
    private TextView mUplinkKbps;
    private TextView mEndcAvailable;
    private TextView mDcnrRestricted;
    private TextView mNrAvailable;
    private TextView mNrState;
    private TextView mNrFrequency;
    private TextView mNetworkSlicingConfig;
    private EditText mSmsc;
    private Switch mRadioPowerOnSwitch;
    private Switch mSimulateOutOfServiceSwitch;
    private Button mDnsCheckToggleButton;
    private Button mPingTestButton;
    private Button mUpdateSmscButton;
    private Button mRefreshSmscButton;
    private Button mOemInfoButton;
    private Button mCarrierProvisioningButton;
    private Button mTriggerCarrierProvisioningButton;
    private Switch mImsVolteProvisionedSwitch;
    private Switch mImsVtProvisionedSwitch;
    private Switch mImsWfcProvisionedSwitch;
    private Switch mEabProvisionedSwitch;
    private Switch mCbrsDataSwitch;
    private Switch mDsdsSwitch;
    private Switch mRemovableEsimSwitch;
    private Spinner mPreferredNetworkType;
    private Spinner mSelectPhoneIndex;
    private Spinner mCellInfoRefreshRateSpinner;

    private static final long RUNNABLE_TIMEOUT_MS = 5 * 60 * 1000L;

    private ThreadPoolExecutor mQueuedWork;

    private ConnectivityManager mConnectivityManager;
    private TelephonyManager mTelephonyManager;
    private ImsManager mImsManager = null;
    private Phone mPhone = null;
    private ProvisioningManager mProvisioningManager = null;

    private String mPingHostnameResultV4;
    private String mPingHostnameResultV6;
    private String mHttpClientTestResult;
    private boolean mMwiValue = false;
    private boolean mCfiValue = false;

    private List<CellInfo> mCellInfoResult = null;
    private final boolean[] mSimulateOos = new boolean[2];

    private int mPreferredNetworkTypeResult;
    private int mCellInfoRefreshRateIndex;
    private int mSelectedPhoneIndex;

    private final NetworkRequest mDefaultNetworkRequest = new NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build();

    private final NetworkCallback mNetworkCallback = new NetworkCallback() {
        public void onCapabilitiesChanged(Network n, NetworkCapabilities nc) {
            int dlbw = nc.getLinkDownstreamBandwidthKbps();
            int ulbw = nc.getLinkUpstreamBandwidthKbps();
            updateBandwidths(dlbw, ulbw);
        }
    };

    private static final int DEFAULT_TIMEOUT_MS = 1000;

    // not final because we need to recreate this object to register on a new subId (b/117555407)
    private TelephonyCallback mTelephonyCallback = new RadioInfoTelephonyCallback();
    private class RadioInfoTelephonyCallback extends TelephonyCallback implements
            TelephonyCallback.DataConnectionStateListener,
            TelephonyCallback.DataActivityListener,
            TelephonyCallback.CallStateListener,
            TelephonyCallback.MessageWaitingIndicatorListener,
            TelephonyCallback.CallForwardingIndicatorListener,
            TelephonyCallback.CellInfoListener,
            TelephonyCallback.SignalStrengthsListener,
            TelephonyCallback.ServiceStateListener,
            TelephonyCallback.DisplayInfoListener {

        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
            updateDataState();
            updateNetworkType();
        }

        @Override
        public void onDataActivity(int direction) {
            updateDataStats2();
        }

        @Override
        public void onCallStateChanged(int state) {
            updateNetworkType();
            updatePhoneState(state);
        }

        @Override
        public void onMessageWaitingIndicatorChanged(boolean mwi) {
            mMwiValue = mwi;
            updateMessageWaiting();
        }

        @Override
        public void onCallForwardingIndicatorChanged(boolean cfi) {
            mCfiValue = cfi;
            updateCallRedirect();
        }

        @Override
        public void onCellInfoChanged(List<CellInfo> arrayCi) {
            log("onCellInfoChanged: arrayCi=" + arrayCi);
            mCellInfoResult = arrayCi;
            updateCellInfo(mCellInfoResult);
        }

        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            log("onSignalStrengthChanged: SignalStrength=" + signalStrength);
            updateSignalStrength(signalStrength);
        }

        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            log("onServiceStateChanged: ServiceState=" + serviceState);
            updateServiceState(serviceState);
            updateRadioPowerState();
            updateNetworkType();
            updateRawRegistrationState(serviceState);
            updateImsProvisionedState();

            // Since update NR stats includes a ril message to get slicing information, it runs
            // as blocking during the timeout period of 1 second. if ServiceStateChanged event
            // fires consecutively, RadioInfo can run for more than 10 seconds. This can cause ANR.
            // Therefore, send event only when there is no same event being processed.
            if (!mHandler.hasMessages(EVENT_UPDATE_NR_STATS)) {
                mHandler.obtainMessage(EVENT_UPDATE_NR_STATS).sendToTarget();
            }
        }

        @Override
        public void onDisplayInfoChanged(TelephonyDisplayInfo displayInfo) {
            updateNetworkType();
        }
    }

    private void updatePhysicalChannelConfiguration(List<PhysicalChannelConfig> configs) {
        StringBuilder sb = new StringBuilder();
        String div = "";
        sb.append("{");
        if (configs != null) {
            for (PhysicalChannelConfig c : configs) {
                sb.append(div).append(c);
                div = ",";
            }
        }
        sb.append("}");
        mPhyChanConfig.setText(sb.toString());
    }

    private void updatePreferredNetworkType(int type) {
        if (type >= PREFERRED_NETWORK_LABELS.length || type < 0) {
            log("Network type: unknown type value=" + type);
            type = PREFERRED_NETWORK_LABELS.length - 1; //set to Unknown
        }
        mPreferredNetworkTypeResult = type;

        mPreferredNetworkType.setSelection(mPreferredNetworkTypeResult, true);
    }

    private void updatePhoneIndex(int phoneIndex, int subId) {
        // unregister listeners on the old subId
        unregisterPhoneStateListener();
        mTelephonyManager.setCellInfoListRate(sCellInfoListRateDisabled, mPhone.getSubId());

        if (phoneIndex == SubscriptionManager.INVALID_PHONE_INDEX) {
            log("Invalid phone index " + phoneIndex + ", subscription ID " + subId);
            return;
        }

        // update the subId
        mTelephonyManager = mTelephonyManager.createForSubscriptionId(subId);

        // update the phoneId
        mPhone = PhoneFactory.getPhone(phoneIndex);
        mImsManager = new ImsManager(mPhone.getContext());
        try {
            mProvisioningManager = ProvisioningManager.createForSubscriptionId(subId);
        } catch (IllegalArgumentException e) {
            log("updatePhoneIndex : IllegalArgumentException " + e.getMessage());
            mProvisioningManager = null;
        }

        updateAllFields();
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            switch (msg.what) {
                case EVENT_QUERY_SMSC_DONE:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        mSmsc.setText("refresh error");
                    } else {
                        mSmsc.setText((String) ar.result);
                    }
                    break;
                case EVENT_UPDATE_SMSC_DONE:
                    mUpdateSmscButton.setEnabled(true);
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        mSmsc.setText("update error");
                    }
                    break;
                case EVENT_PHYSICAL_CHANNEL_CONFIG_CHANGED:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        mPhyChanConfig.setText(("update error"));
                    }
                    updatePhysicalChannelConfiguration((List<PhysicalChannelConfig>) ar.result);
                    break;
                case EVENT_UPDATE_NR_STATS:
                    log("got EVENT_UPDATE_NR_STATS");
                    updateNrStats();
                    break;
                default:
                    super.handleMessage(msg);
                    break;

            }
        }
    };

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (!android.os.Process.myUserHandle().isSystem()) {
            Log.e(TAG, "Not run from system user, don't do anything.");
            finish();
            return;
        }

        UserManager userManager =
                (UserManager) getApplicationContext().getSystemService(Context.USER_SERVICE);
        if (userManager != null
                && userManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)) {
            Log.w(TAG, "User is restricted from configuring mobile networks.");
            finish();
            return;
        }

        setContentView(R.layout.radio_info);

        log("Started onCreate");

        mQueuedWork = new ThreadPoolExecutor(1, 1, RUNNABLE_TIMEOUT_MS, TimeUnit.MICROSECONDS,
                new LinkedBlockingDeque<Runnable>());
        mConnectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        mPhone = getPhone(SubscriptionManager.getDefaultSubscriptionId());
        mTelephonyManager = ((TelephonyManager) getSystemService(TELEPHONY_SERVICE))
                .createForSubscriptionId(mPhone.getSubId());

        mImsManager = new ImsManager(mPhone.getContext());
        try {
            mProvisioningManager = ProvisioningManager.createForSubscriptionId(mPhone.getSubId());
        } catch (IllegalArgumentException e) {
            log("onCreate : IllegalArgumentException " + e.getMessage());
            mProvisioningManager = null;
        }

        sPhoneIndexLabels = getPhoneIndexLabels(mTelephonyManager);

        mDeviceId = (TextView) findViewById(R.id.imei);
        mLine1Number = (TextView) findViewById(R.id.number);
        mSubscriptionId = (TextView) findViewById(R.id.subid);
        mDds = (TextView) findViewById(R.id.dds);
        mSubscriberId = (TextView) findViewById(R.id.imsi);
        mCallState = (TextView) findViewById(R.id.call);
        mOperatorName = (TextView) findViewById(R.id.operator);
        mRoamingState = (TextView) findViewById(R.id.roaming);
        mGsmState = (TextView) findViewById(R.id.gsm);
        mGprsState = (TextView) findViewById(R.id.gprs);
        mVoiceNetwork = (TextView) findViewById(R.id.voice_network);
        mDataNetwork = (TextView) findViewById(R.id.data_network);
        mVoiceRawReg = (TextView) findViewById(R.id.voice_raw_registration_state);
        mDataRawReg = (TextView) findViewById(R.id.data_raw_registration_state);
        mWlanDataRawReg = (TextView) findViewById(R.id.wlan_data_raw_registration_state);
        mOverrideNetwork = (TextView) findViewById(R.id.override_network);
        mDBm = (TextView) findViewById(R.id.dbm);
        mMwi = (TextView) findViewById(R.id.mwi);
        mCfi = (TextView) findViewById(R.id.cfi);
        mCellInfo = (TextView) findViewById(R.id.cellinfo);
        mCellInfo.setTypeface(Typeface.MONOSPACE);

        mSent = (TextView) findViewById(R.id.sent);
        mReceived = (TextView) findViewById(R.id.received);
        mSmsc = (EditText) findViewById(R.id.smsc);
        mDnsCheckState = (TextView) findViewById(R.id.dnsCheckState);
        mPingHostnameV4 = (TextView) findViewById(R.id.pingHostnameV4);
        mPingHostnameV6 = (TextView) findViewById(R.id.pingHostnameV6);
        mHttpClientTest = (TextView) findViewById(R.id.httpClientTest);
        mEndcAvailable = (TextView) findViewById(R.id.endc_available);
        mDcnrRestricted = (TextView) findViewById(R.id.dcnr_restricted);
        mNrAvailable = (TextView) findViewById(R.id.nr_available);
        mNrState = (TextView) findViewById(R.id.nr_state);
        mNrFrequency = (TextView) findViewById(R.id.nr_frequency);
        mPhyChanConfig = (TextView) findViewById(R.id.phy_chan_config);
        mNetworkSlicingConfig = (TextView) findViewById(R.id.network_slicing_config);

        // hide 5G stats on devices that don't support 5G
        if ((mTelephonyManager.getSupportedRadioAccessFamily()
                & TelephonyManager.NETWORK_TYPE_BITMASK_NR) == 0) {
            setNrStatsVisibility(View.GONE);
        }

        mPreferredNetworkType = (Spinner) findViewById(R.id.preferredNetworkType);
        ArrayAdapter<String> mPreferredNetworkTypeAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, PREFERRED_NETWORK_LABELS);
        mPreferredNetworkTypeAdapter
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mPreferredNetworkType.setAdapter(mPreferredNetworkTypeAdapter);

        mSelectPhoneIndex = (Spinner) findViewById(R.id.phoneIndex);
        ArrayAdapter<String> phoneIndexAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, sPhoneIndexLabels);
        phoneIndexAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSelectPhoneIndex.setAdapter(phoneIndexAdapter);

        mCellInfoRefreshRateSpinner = (Spinner) findViewById(R.id.cell_info_rate_select);
        ArrayAdapter<String> cellInfoAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, CELL_INFO_REFRESH_RATE_LABELS);
        cellInfoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCellInfoRefreshRateSpinner.setAdapter(cellInfoAdapter);

        mImsVolteProvisionedSwitch = (Switch) findViewById(R.id.volte_provisioned_switch);
        mImsVtProvisionedSwitch = (Switch) findViewById(R.id.vt_provisioned_switch);
        mImsWfcProvisionedSwitch = (Switch) findViewById(R.id.wfc_provisioned_switch);
        mEabProvisionedSwitch = (Switch) findViewById(R.id.eab_provisioned_switch);

        if (!isImsSupportedOnDevice(mPhone.getContext())) {
            mImsVolteProvisionedSwitch.setVisibility(View.GONE);
            mImsVtProvisionedSwitch.setVisibility(View.GONE);
            mImsWfcProvisionedSwitch.setVisibility(View.GONE);
            mEabProvisionedSwitch.setVisibility(View.GONE);
        }

        mCbrsDataSwitch = (Switch) findViewById(R.id.cbrs_data_switch);
        mCbrsDataSwitch.setVisibility(isCbrsSupported() ? View.VISIBLE : View.GONE);

        mDsdsSwitch = findViewById(R.id.dsds_switch);
        if (isDsdsSupported() && !dsdsModeOnly()) {
            mDsdsSwitch.setVisibility(View.VISIBLE);
            mDsdsSwitch.setOnClickListener(v -> {
                if (mTelephonyManager.doesSwitchMultiSimConfigTriggerReboot()) {
                    // Undo the click action until user clicks the confirm dialog.
                    mDsdsSwitch.toggle();
                    showDsdsChangeDialog();
                } else {
                    performDsdsSwitch();
                }
            });
            mDsdsSwitch.setChecked(isDsdsEnabled());
        } else {
            mDsdsSwitch.setVisibility(View.GONE);
        }

        mRemovableEsimSwitch = (Switch) findViewById(R.id.removable_esim_switch);
        if (!IS_USER_BUILD) {
            mRemovableEsimSwitch.setEnabled(true);
            mRemovableEsimSwitch.setChecked(mTelephonyManager.isRemovableEsimDefaultEuicc());
            mRemovableEsimSwitch.setOnCheckedChangeListener(mRemovableEsimChangeListener);
        }

        mRadioPowerOnSwitch = (Switch) findViewById(R.id.radio_power);

        mSimulateOutOfServiceSwitch = (Switch) findViewById(R.id.simulate_out_of_service);
        if (!TelephonyUtils.IS_DEBUGGABLE) {
            mSimulateOutOfServiceSwitch.setVisibility(View.GONE);
        }

        mDownlinkKbps = (TextView) findViewById(R.id.dl_kbps);
        mUplinkKbps = (TextView) findViewById(R.id.ul_kbps);
        updateBandwidths(0, 0);

        mPingTestButton = (Button) findViewById(R.id.ping_test);
        mPingTestButton.setOnClickListener(mPingButtonHandler);
        mUpdateSmscButton = (Button) findViewById(R.id.update_smsc);
        mUpdateSmscButton.setOnClickListener(mUpdateSmscButtonHandler);
        mRefreshSmscButton = (Button) findViewById(R.id.refresh_smsc);
        mRefreshSmscButton.setOnClickListener(mRefreshSmscButtonHandler);
        mDnsCheckToggleButton = (Button) findViewById(R.id.dns_check_toggle);
        mDnsCheckToggleButton.setOnClickListener(mDnsCheckButtonHandler);
        mCarrierProvisioningButton = (Button) findViewById(R.id.carrier_provisioning);
        if (!TextUtils.isEmpty(getCarrierProvisioningAppString())) {
            mCarrierProvisioningButton.setOnClickListener(mCarrierProvisioningButtonHandler);
        } else {
            mCarrierProvisioningButton.setEnabled(false);
        }

        mTriggerCarrierProvisioningButton = (Button) findViewById(
                R.id.trigger_carrier_provisioning);
        if (!TextUtils.isEmpty(getCarrierProvisioningAppString())) {
            mTriggerCarrierProvisioningButton.setOnClickListener(
                    mTriggerCarrierProvisioningButtonHandler);
        } else {
            mTriggerCarrierProvisioningButton.setEnabled(false);
        }

        mOemInfoButton = (Button) findViewById(R.id.oem_info);
        mOemInfoButton.setOnClickListener(mOemInfoButtonHandler);
        PackageManager pm = getPackageManager();
        Intent oemInfoIntent = new Intent(OEM_RADIO_INFO_INTENT);
        List<ResolveInfo> oemInfoIntentList = pm.queryIntentActivities(oemInfoIntent, 0);
        if (oemInfoIntentList.size() == 0) {
            mOemInfoButton.setEnabled(false);
        }

        mCellInfoRefreshRateIndex = 0; //disabled
        mPreferredNetworkTypeResult = PREFERRED_NETWORK_LABELS.length - 1; //Unknown
        mSelectedPhoneIndex = mPhone.getPhoneId();

        new Thread(() -> {
            int networkType = (int) mTelephonyManager.getPreferredNetworkTypeBitmask();
            runOnUiThread(() -> updatePreferredNetworkType(
                    RadioAccessFamily.getNetworkTypeFromRaf(networkType)));
        }).start();

        restoreFromBundle(icicle);
    }

    @Override
    public Intent getParentActivityIntent() {
        Intent parentActivity = super.getParentActivityIntent();
        if (parentActivity == null) {
            parentActivity = (new Intent()).setClassName("com.android.settings",
                    "com.android.settings.Settings$TestingSettingsActivity");
        }
        return parentActivity;
    }

    @Override
    protected void onResume() {
        super.onResume();

        log("Started onResume");

        updateAllFields();
    }

    private void updateAllFields() {
        updateMessageWaiting();
        updateCallRedirect();
        updateDataState();
        updateDataStats2();
        updateRadioPowerState();
        updateImsProvisionedState();
        updateProperties();
        updateDnsCheckState();
        updateNetworkType();
        updateNrStats();

        updateCellInfo(mCellInfoResult);
        updateSubscriptionIds();

        mPingHostnameV4.setText(mPingHostnameResultV4);
        mPingHostnameV6.setText(mPingHostnameResultV6);
        mHttpClientTest.setText(mHttpClientTestResult);

        mCellInfoRefreshRateSpinner.setOnItemSelectedListener(mCellInfoRefreshRateHandler);
        //set selection after registering listener to force update
        mCellInfoRefreshRateSpinner.setSelection(mCellInfoRefreshRateIndex);
        // Request cell information update from RIL.
        mTelephonyManager.setCellInfoListRate(CELL_INFO_REFRESH_RATES[mCellInfoRefreshRateIndex],
                mPhone.getSubId());

        //set selection before registering to prevent update
        mPreferredNetworkType.setSelection(mPreferredNetworkTypeResult, true);
        mPreferredNetworkType.setOnItemSelectedListener(mPreferredNetworkHandler);

        new Thread(() -> {
            int networkType = (int) mTelephonyManager.getPreferredNetworkTypeBitmask();
            runOnUiThread(() -> updatePreferredNetworkType(
                    RadioAccessFamily.getNetworkTypeFromRaf(networkType)));
        }).start();

        // set phone index
        mSelectPhoneIndex.setSelection(mSelectedPhoneIndex, true);
        mSelectPhoneIndex.setOnItemSelectedListener(mSelectPhoneIndexHandler);

        mRadioPowerOnSwitch.setOnCheckedChangeListener(mRadioPowerOnChangeListener);
        mSimulateOutOfServiceSwitch.setOnCheckedChangeListener(mSimulateOosOnChangeListener);
        mSimulateOutOfServiceSwitch.setChecked(mSimulateOos[mPhone.getPhoneId()]);
        mImsVolteProvisionedSwitch.setOnCheckedChangeListener(mImsVolteCheckedChangeListener);
        mImsVtProvisionedSwitch.setOnCheckedChangeListener(mImsVtCheckedChangeListener);
        mImsWfcProvisionedSwitch.setOnCheckedChangeListener(mImsWfcCheckedChangeListener);
        mEabProvisionedSwitch.setOnCheckedChangeListener(mEabCheckedChangeListener);

        if (isCbrsSupported()) {
            mCbrsDataSwitch.setChecked(getCbrsDataState());
            mCbrsDataSwitch.setOnCheckedChangeListener(mCbrsDataSwitchChangeListener);
        }

        unregisterPhoneStateListener();
        registerPhoneStateListener();
        mPhone.registerForPhysicalChannelConfig(mHandler,
            EVENT_PHYSICAL_CHANNEL_CONFIG_CHANGED, null);

        mConnectivityManager.registerNetworkCallback(
                mDefaultNetworkRequest, mNetworkCallback, mHandler);

        mSmsc.clearFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();

        log("onPause: unregister phone & data intents");

        mTelephonyManager.unregisterTelephonyCallback(mTelephonyCallback);
        mTelephonyManager.setCellInfoListRate(sCellInfoListRateDisabled, mPhone.getSubId());
        mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);

    }

    private void restoreFromBundle(Bundle b) {
        if (b == null) {
            return;
        }

        mPingHostnameResultV4 = b.getString("mPingHostnameResultV4", "");
        mPingHostnameResultV6 = b.getString("mPingHostnameResultV6", "");
        mHttpClientTestResult = b.getString("mHttpClientTestResult", "");

        mPingHostnameV4.setText(mPingHostnameResultV4);
        mPingHostnameV6.setText(mPingHostnameResultV6);
        mHttpClientTest.setText(mHttpClientTestResult);

        mPreferredNetworkTypeResult = b.getInt("mPreferredNetworkTypeResult",
                PREFERRED_NETWORK_LABELS.length - 1);

        mSelectedPhoneIndex = b.getInt("mSelectedPhoneIndex", 0);

        mCellInfoRefreshRateIndex = b.getInt("mCellInfoRefreshRateIndex", 0);
    }

    @SuppressWarnings("MissingSuperCall") // TODO: Fix me
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("mPingHostnameResultV4", mPingHostnameResultV4);
        outState.putString("mPingHostnameResultV6", mPingHostnameResultV6);
        outState.putString("mHttpClientTestResult", mHttpClientTestResult);

        outState.putInt("mPreferredNetworkTypeResult", mPreferredNetworkTypeResult);
        outState.putInt("mSelectedPhoneIndex", mSelectedPhoneIndex);
        outState.putInt("mCellInfoRefreshRateIndex", mCellInfoRefreshRateIndex);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Removed "select Radio band". If need it back, use setSystemSelectionChannels()
        menu.add(1, MENU_ITEM_VIEW_ADN, 0,
                R.string.radioInfo_menu_viewADN).setOnMenuItemClickListener(mViewADNCallback);
        menu.add(1, MENU_ITEM_VIEW_FDN, 0,
                R.string.radioInfo_menu_viewFDN).setOnMenuItemClickListener(mViewFDNCallback);
        menu.add(1, MENU_ITEM_VIEW_SDN, 0,
                R.string.radioInfo_menu_viewSDN).setOnMenuItemClickListener(mViewSDNCallback);
        if (isImsSupportedOnDevice(mPhone.getContext())) {
            menu.add(1, MENU_ITEM_GET_IMS_STATUS,
                    0, R.string.radioInfo_menu_getIMS).setOnMenuItemClickListener(mGetImsStatus);
        }
        menu.add(1, MENU_ITEM_TOGGLE_DATA,
                0, R.string.radio_info_data_connection_disable)
                .setOnMenuItemClickListener(mToggleData);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Get the TOGGLE DATA menu item in the right state.
        MenuItem item = menu.findItem(MENU_ITEM_TOGGLE_DATA);
        int state = mTelephonyManager.getDataState();
        boolean visible = true;

        switch (state) {
            case TelephonyManager.DATA_CONNECTED:
            case TelephonyManager.DATA_SUSPENDED:
                item.setTitle(R.string.radio_info_data_connection_disable);
                break;
            case TelephonyManager.DATA_DISCONNECTED:
                item.setTitle(R.string.radio_info_data_connection_enable);
                break;
            default:
                visible = false;
                break;
        }
        item.setVisible(visible);
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mQueuedWork != null) {
            mQueuedWork.shutdown();
        }
    }

    // returns array of string labels for each phone index. The array index is equal to the phone
    // index.
    private static String[] getPhoneIndexLabels(TelephonyManager tm) {
        int phones = tm.getPhoneCount();
        String[] labels = new String[phones];
        for (int i = 0; i < phones; i++) {
            labels[i] = "Phone " + i;
        }
        return labels;
    }

    private void unregisterPhoneStateListener() {
        mTelephonyManager.unregisterTelephonyCallback(mTelephonyCallback);
        mPhone.unregisterForPhysicalChannelConfig(mHandler);

        // clear all fields so they are blank until the next listener event occurs
        mOperatorName.setText("");
        mGprsState.setText("");
        mDataNetwork.setText("");
        mDataRawReg.setText("");
        mOverrideNetwork.setText("");
        mVoiceNetwork.setText("");
        mVoiceRawReg.setText("");
        mWlanDataRawReg.setText("");
        mSent.setText("");
        mReceived.setText("");
        mCallState.setText("");
        mMwiValue = false;
        mMwi.setText("");
        mCfiValue = false;
        mCfi.setText("");
        mCellInfo.setText("");
        mDBm.setText("");
        mGsmState.setText("");
        mRoamingState.setText("");
        mPhyChanConfig.setText("");
    }

    // register mTelephonyCallback for relevant fields using the current TelephonyManager
    private void registerPhoneStateListener() {
        mTelephonyCallback = new RadioInfoTelephonyCallback();
        mTelephonyManager.registerTelephonyCallback(new HandlerExecutor(mHandler),
                mTelephonyCallback);
    }

    private void setNrStatsVisibility(int visibility) {
        ((TextView) findViewById(R.id.endc_available_label)).setVisibility(visibility);
        mEndcAvailable.setVisibility(visibility);
        ((TextView) findViewById(R.id.dcnr_restricted_label)).setVisibility(visibility);
        mDcnrRestricted.setVisibility(visibility);
        ((TextView) findViewById(R.id.nr_available_label)).setVisibility(visibility);
        mNrAvailable.setVisibility(visibility);
        ((TextView) findViewById(R.id.nr_state_label)).setVisibility(visibility);
        mNrState.setVisibility(visibility);
        ((TextView) findViewById(R.id.nr_frequency_label)).setVisibility(visibility);
        mNrFrequency.setVisibility(visibility);
        ((TextView) findViewById(R.id.network_slicing_config_label)).setVisibility(visibility);
        mNetworkSlicingConfig.setVisibility(visibility);
    }

    private void updateDnsCheckState() {
        //FIXME: Replace with a TelephonyManager call
        mDnsCheckState.setText(mPhone.isDnsCheckDisabled()
                ? "0.0.0.0 allowed" : "0.0.0.0 not allowed");
    }

    private void updateBandwidths(int dlbw, int ulbw) {
        dlbw = (dlbw < 0 || dlbw == Integer.MAX_VALUE) ? -1 : dlbw;
        ulbw = (ulbw < 0 || ulbw == Integer.MAX_VALUE) ? -1 : ulbw;
        mDownlinkKbps.setText(String.format("%-5d", dlbw));
        mUplinkKbps.setText(String.format("%-5d", ulbw));
    }

    private void updateSignalStrength(SignalStrength signalStrength) {
        Resources r = getResources();

        int signalDbm = signalStrength.getDbm();

        int signalAsu = signalStrength.getAsuLevel();

        if (-1 == signalAsu) signalAsu = 0;

        mDBm.setText(String.valueOf(signalDbm) + " "
                + r.getString(R.string.radioInfo_display_dbm) + "   "
                + String.valueOf(signalAsu) + " "
                + r.getString(R.string.radioInfo_display_asu));
    }

    private String getCellInfoDisplayString(int i) {
        return (i != Integer.MAX_VALUE) ? Integer.toString(i) : "";
    }

    private String getCellInfoDisplayString(long i) {
        return (i != Long.MAX_VALUE) ? Long.toString(i) : "";
    }

    private String getConnectionStatusString(CellInfo ci) {
        String regStr = "";
        String connStatStr = "";
        String connector = "";

        if (ci.isRegistered()) {
            regStr = "R";
        }
        switch (ci.getCellConnectionStatus()) {
            case CellInfo.CONNECTION_PRIMARY_SERVING: connStatStr = "P"; break;
            case CellInfo.CONNECTION_SECONDARY_SERVING: connStatStr = "S"; break;
            case CellInfo.CONNECTION_NONE: connStatStr = "N"; break;
            case CellInfo.CONNECTION_UNKNOWN: /* Field is unsupported */ break;
            default: break;
        }
        if (!TextUtils.isEmpty(regStr) && !TextUtils.isEmpty(connStatStr)) {
            connector = "+";
        }

        return regStr + connector + connStatStr;
    }

    private String buildCdmaInfoString(CellInfoCdma ci) {
        CellIdentityCdma cidCdma = ci.getCellIdentity();
        CellSignalStrengthCdma ssCdma = ci.getCellSignalStrength();

        return String.format("%-3.3s %-5.5s %-5.5s %-5.5s %-6.6s %-6.6s %-6.6s %-6.6s %-5.5s",
                getConnectionStatusString(ci),
                getCellInfoDisplayString(cidCdma.getSystemId()),
                getCellInfoDisplayString(cidCdma.getNetworkId()),
                getCellInfoDisplayString(cidCdma.getBasestationId()),
                getCellInfoDisplayString(ssCdma.getCdmaDbm()),
                getCellInfoDisplayString(ssCdma.getCdmaEcio()),
                getCellInfoDisplayString(ssCdma.getEvdoDbm()),
                getCellInfoDisplayString(ssCdma.getEvdoEcio()),
                getCellInfoDisplayString(ssCdma.getEvdoSnr()));
    }

    private String buildGsmInfoString(CellInfoGsm ci) {
        CellIdentityGsm cidGsm = ci.getCellIdentity();
        CellSignalStrengthGsm ssGsm = ci.getCellSignalStrength();

        return String.format("%-3.3s %-3.3s %-3.3s %-5.5s %-5.5s %-6.6s %-4.4s %-4.4s\n",
                getConnectionStatusString(ci),
                getCellInfoDisplayString(cidGsm.getMcc()),
                getCellInfoDisplayString(cidGsm.getMnc()),
                getCellInfoDisplayString(cidGsm.getLac()),
                getCellInfoDisplayString(cidGsm.getCid()),
                getCellInfoDisplayString(cidGsm.getArfcn()),
                getCellInfoDisplayString(cidGsm.getBsic()),
                getCellInfoDisplayString(ssGsm.getDbm()));
    }

    private String buildLteInfoString(CellInfoLte ci) {
        CellIdentityLte cidLte = ci.getCellIdentity();
        CellSignalStrengthLte ssLte = ci.getCellSignalStrength();

        return String.format(
                "%-3.3s %-3.3s %-3.3s %-5.5s %-5.5s %-3.3s %-6.6s %-2.2s %-4.4s %-4.4s %-2.2s\n",
                getConnectionStatusString(ci),
                getCellInfoDisplayString(cidLte.getMcc()),
                getCellInfoDisplayString(cidLte.getMnc()),
                getCellInfoDisplayString(cidLte.getTac()),
                getCellInfoDisplayString(cidLte.getCi()),
                getCellInfoDisplayString(cidLte.getPci()),
                getCellInfoDisplayString(cidLte.getEarfcn()),
                getCellInfoDisplayString(cidLte.getBandwidth()),
                getCellInfoDisplayString(ssLte.getDbm()),
                getCellInfoDisplayString(ssLte.getRsrq()),
                getCellInfoDisplayString(ssLte.getTimingAdvance()));
    }

    private String buildNrInfoString(CellInfoNr ci) {
        CellIdentityNr cidNr = (CellIdentityNr) ci.getCellIdentity();
        CellSignalStrengthNr ssNr = (CellSignalStrengthNr) ci.getCellSignalStrength();

        return String.format(
                "%-3.3s %-3.3s %-3.3s %-5.5s %-5.5s %-3.3s %-6.6s %-4.4s %-4.4s\n",
                getConnectionStatusString(ci),
                cidNr.getMccString(),
                cidNr.getMncString(),
                getCellInfoDisplayString(cidNr.getTac()),
                getCellInfoDisplayString(cidNr.getNci()),
                getCellInfoDisplayString(cidNr.getPci()),
                getCellInfoDisplayString(cidNr.getNrarfcn()),
                getCellInfoDisplayString(ssNr.getSsRsrp()),
                getCellInfoDisplayString(ssNr.getSsRsrq()));
    }

    private String buildWcdmaInfoString(CellInfoWcdma ci) {
        CellIdentityWcdma cidWcdma = ci.getCellIdentity();
        CellSignalStrengthWcdma ssWcdma = ci.getCellSignalStrength();

        return String.format("%-3.3s %-3.3s %-3.3s %-5.5s %-5.5s %-6.6s %-3.3s %-4.4s\n",
                getConnectionStatusString(ci),
                getCellInfoDisplayString(cidWcdma.getMcc()),
                getCellInfoDisplayString(cidWcdma.getMnc()),
                getCellInfoDisplayString(cidWcdma.getLac()),
                getCellInfoDisplayString(cidWcdma.getCid()),
                getCellInfoDisplayString(cidWcdma.getUarfcn()),
                getCellInfoDisplayString(cidWcdma.getPsc()),
                getCellInfoDisplayString(ssWcdma.getDbm()));
    }

    private String buildCellInfoString(List<CellInfo> arrayCi) {
        String value = new String();
        StringBuilder cdmaCells = new StringBuilder(),
                gsmCells = new StringBuilder(),
                lteCells = new StringBuilder(),
                wcdmaCells = new StringBuilder(),
                nrCells = new StringBuilder();

        if (arrayCi != null) {
            for (CellInfo ci : arrayCi) {

                if (ci instanceof CellInfoLte) {
                    lteCells.append(buildLteInfoString((CellInfoLte) ci));
                } else if (ci instanceof CellInfoWcdma) {
                    wcdmaCells.append(buildWcdmaInfoString((CellInfoWcdma) ci));
                } else if (ci instanceof CellInfoGsm) {
                    gsmCells.append(buildGsmInfoString((CellInfoGsm) ci));
                } else if (ci instanceof CellInfoCdma) {
                    cdmaCells.append(buildCdmaInfoString((CellInfoCdma) ci));
                } else if (ci instanceof CellInfoNr) {
                    nrCells.append(buildNrInfoString((CellInfoNr) ci));
                }
            }
            if (nrCells.length() != 0) {
                value += String.format(
                        "NR\n%-3.3s %-3.3s %-3.3s %-5.5s %-5.5s %-3.3s"
                                + " %-6.6s %-4.4s %-4.4s\n",
                        "SRV", "MCC", "MNC", "TAC", "NCI", "PCI",
                        "NRARFCN", "SS-RSRP", "SS-RSRQ");
                value += nrCells.toString();
            }

            if (lteCells.length() != 0) {
                value += String.format(
                        "LTE\n%-3.3s %-3.3s %-3.3s %-5.5s %-5.5s %-3.3s"
                                + " %-6.6s %-2.2s %-4.4s %-4.4s %-2.2s\n",
                        "SRV", "MCC", "MNC", "TAC", "CID", "PCI",
                        "EARFCN", "BW", "RSRP", "RSRQ", "TA");
                value += lteCells.toString();
            }
            if (wcdmaCells.length() != 0) {
                value += String.format(
                        "WCDMA\n%-3.3s %-3.3s %-3.3s %-5.5s %-5.5s %-6.6s %-3.3s %-4.4s\n",
                        "SRV", "MCC", "MNC", "LAC", "CID", "UARFCN", "PSC", "RSCP");
                value += wcdmaCells.toString();
            }
            if (gsmCells.length() != 0) {
                value += String.format(
                        "GSM\n%-3.3s %-3.3s %-3.3s %-5.5s %-5.5s %-6.6s %-4.4s %-4.4s\n",
                        "SRV", "MCC", "MNC", "LAC", "CID", "ARFCN", "BSIC", "RSSI");
                value += gsmCells.toString();
            }
            if (cdmaCells.length() != 0) {
                value += String.format(
                        "CDMA/EVDO\n%-3.3s %-5.5s %-5.5s %-5.5s"
                                + " %-6.6s %-6.6s %-6.6s %-6.6s %-5.5s\n",
                        "SRV", "SID", "NID", "BSID",
                        "C-RSSI", "C-ECIO", "E-RSSI", "E-ECIO", "E-SNR");
                value += cdmaCells.toString();
            }
        } else {
            value = "unknown";
        }

        return value.toString();
    }

    private void updateCellInfo(List<CellInfo> arrayCi) {
        mCellInfo.setText(buildCellInfoString(arrayCi));
    }

    private void updateSubscriptionIds() {
        mSubscriptionId.setText(Integer.toString(mPhone.getSubId()));
        mDds.setText(Integer.toString(SubscriptionManager.getDefaultDataSubscriptionId()));
    }

    private void updateMessageWaiting() {
        mMwi.setText(String.valueOf(mMwiValue));
    }

    private void updateCallRedirect() {
        mCfi.setText(String.valueOf(mCfiValue));
    }


    private void updateServiceState(ServiceState serviceState) {
        int state = serviceState.getState();
        Resources r = getResources();
        String display = r.getString(R.string.radioInfo_unknown);

        switch (state) {
            case ServiceState.STATE_IN_SERVICE:
                display = r.getString(R.string.radioInfo_service_in);
                break;
            case ServiceState.STATE_OUT_OF_SERVICE:
                display = r.getString(R.string.radioInfo_service_out);
                break;
            case ServiceState.STATE_EMERGENCY_ONLY:
                display = r.getString(R.string.radioInfo_service_emergency);
                break;
            case ServiceState.STATE_POWER_OFF:
                display = r.getString(R.string.radioInfo_service_off);
                break;
        }

        mGsmState.setText(display);

        if (serviceState.getRoaming()) {
            mRoamingState.setText(R.string.radioInfo_roaming_in);
        } else {
            mRoamingState.setText(R.string.radioInfo_roaming_not);
        }

        mOperatorName.setText(serviceState.getOperatorAlphaLong());
    }

    private void updatePhoneState(int state) {
        Resources r = getResources();
        String display = r.getString(R.string.radioInfo_unknown);

        switch (state) {
            case TelephonyManager.CALL_STATE_IDLE:
                display = r.getString(R.string.radioInfo_phone_idle);
                break;
            case TelephonyManager.CALL_STATE_RINGING:
                display = r.getString(R.string.radioInfo_phone_ringing);
                break;
            case TelephonyManager.CALL_STATE_OFFHOOK:
                display = r.getString(R.string.radioInfo_phone_offhook);
                break;
        }

        mCallState.setText(display);
    }

    private void updateDataState() {
        int state = mTelephonyManager.getDataState();
        Resources r = getResources();
        String display = r.getString(R.string.radioInfo_unknown);

        switch (state) {
            case TelephonyManager.DATA_CONNECTED:
                display = r.getString(R.string.radioInfo_data_connected);
                break;
            case TelephonyManager.DATA_CONNECTING:
                display = r.getString(R.string.radioInfo_data_connecting);
                break;
            case TelephonyManager.DATA_DISCONNECTED:
                display = r.getString(R.string.radioInfo_data_disconnected);
                break;
            case TelephonyManager.DATA_SUSPENDED:
                display = r.getString(R.string.radioInfo_data_suspended);
                break;
        }

        mGprsState.setText(display);
    }

    private void updateNetworkType() {
        if (mPhone != null) {
            mDataNetwork.setText(ServiceState.rilRadioTechnologyToString(
                    mPhone.getServiceState().getRilDataRadioTechnology()));
            mVoiceNetwork.setText(ServiceState.rilRadioTechnologyToString(
                    mPhone.getServiceState().getRilVoiceRadioTechnology()));
            int overrideNetwork = mPhone.getDisplayInfoController().getTelephonyDisplayInfo()
                    .getOverrideNetworkType();
            mOverrideNetwork.setText(
                    TelephonyDisplayInfo.overrideNetworkTypeToString(overrideNetwork));
        }
    }

    private String getRawRegistrationStateText(ServiceState ss, int domain, int transportType) {
        if (ss != null) {
            NetworkRegistrationInfo nri = ss.getNetworkRegistrationInfo(domain, transportType);
            if (nri != null) {
                return NetworkRegistrationInfo.registrationStateToString(
                        nri.getNetworkRegistrationState())
                        + (nri.isEmergencyEnabled() ? "_EM" : "");
            }
        }
        return "";
    }

    private void updateRawRegistrationState(ServiceState serviceState) {
        ServiceState ss = serviceState;
        if (ss == null && mPhone != null) {
            ss = mPhone.getServiceState();
        }

        mVoiceRawReg.setText(getRawRegistrationStateText(ss, NetworkRegistrationInfo.DOMAIN_CS,
                    AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
        mDataRawReg.setText(getRawRegistrationStateText(ss, NetworkRegistrationInfo.DOMAIN_PS,
                    AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
        mWlanDataRawReg.setText(getRawRegistrationStateText(ss, NetworkRegistrationInfo.DOMAIN_PS,
                    AccessNetworkConstants.TRANSPORT_TYPE_WLAN));
    }

    private void updateNrStats() {
        if ((mTelephonyManager.getSupportedRadioAccessFamily()
                & TelephonyManager.NETWORK_TYPE_BITMASK_NR) == 0) {
            return;
        }
        ServiceState ss = (mPhone == null) ? null : mPhone.getServiceState();
        if (ss != null) {
            NetworkRegistrationInfo nri = ss.getNetworkRegistrationInfo(
                    NetworkRegistrationInfo.DOMAIN_PS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
            if (nri != null) {
                DataSpecificRegistrationInfo dsri = nri.getDataSpecificInfo();
                if (dsri != null) {
                    mEndcAvailable.setText(String.valueOf(dsri.isEnDcAvailable));
                    mDcnrRestricted.setText(String.valueOf(dsri.isDcNrRestricted));
                    mNrAvailable.setText(String.valueOf(dsri.isNrAvailable));
                }
            }
            mNrState.setText(NetworkRegistrationInfo.nrStateToString(ss.getNrState()));
            mNrFrequency.setText(ServiceState.frequencyRangeToString(ss.getNrFrequencyRange()));
        } else {
            Log.e(TAG, "Clear Nr stats by null service state");
            mEndcAvailable.setText("");
            mDcnrRestricted.setText("");
            mNrAvailable.setText("");
            mNrState.setText("");
            mNrFrequency.setText("");
        }

        CompletableFuture<NetworkSlicingConfig> resultFuture = new CompletableFuture<>();
        mTelephonyManager.getNetworkSlicingConfiguration(Runnable::run, resultFuture::complete);
        try {
            NetworkSlicingConfig networkSlicingConfig =
                    resultFuture.get(DEFAULT_TIMEOUT_MS, MILLISECONDS);
            mNetworkSlicingConfig.setText(networkSlicingConfig.toString());
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            Log.e(TAG, "Unable to get slicing config: " + e.toString());
            mNetworkSlicingConfig.setText("Unable to get slicing config.");
        }

    }

    private void updateProperties() {
        String s;
        Resources r = getResources();

        s = mPhone.getDeviceId();
        if (s == null) {
            s = r.getString(R.string.radioInfo_unknown);
        }  else if (mPhone.getImeiType() == ImeiInfo.ImeiType.PRIMARY) {
            s = s + " (" + r.getString(R.string.radioInfo_imei_primary) + ")";
        }
        mDeviceId.setText(s);

        s = mPhone.getSubscriberId();
        if (s == null) s = r.getString(R.string.radioInfo_unknown);

        mSubscriberId.setText(s);

        SubscriptionManager subMgr = getSystemService(SubscriptionManager.class);
        int subId = mPhone.getSubId();
        s = subMgr.getPhoneNumber(subId)
                + " { CARRIER:"
                + subMgr.getPhoneNumber(subId, SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER)
                + ", UICC:"
                + subMgr.getPhoneNumber(subId, SubscriptionManager.PHONE_NUMBER_SOURCE_UICC)
                + ", IMS:"
                + subMgr.getPhoneNumber(subId, SubscriptionManager.PHONE_NUMBER_SOURCE_IMS)
                + " }";
        mLine1Number.setText(s);
    }

    private void updateDataStats2() {
        Resources r = getResources();

        long txPackets = TrafficStats.getMobileTxPackets();
        long rxPackets = TrafficStats.getMobileRxPackets();
        long txBytes   = TrafficStats.getMobileTxBytes();
        long rxBytes   = TrafficStats.getMobileRxBytes();

        String packets = r.getString(R.string.radioInfo_display_packets);
        String bytes   = r.getString(R.string.radioInfo_display_bytes);

        mSent.setText(txPackets + " " + packets + ", " + txBytes + " " + bytes);
        mReceived.setText(rxPackets + " " + packets + ", " + rxBytes + " " + bytes);
    }

    /**
     *  Ping a host name
     */
    private void pingHostname() {
        try {
            try {
                Process p4 = Runtime.getRuntime().exec("ping -c 1 www.google.com");
                int status4 = p4.waitFor();
                if (status4 == 0) {
                    mPingHostnameResultV4 = "Pass";
                } else {
                    mPingHostnameResultV4 = String.format("Fail(%d)", status4);
                }
            } catch (IOException e) {
                mPingHostnameResultV4 = "Fail: IOException";
            }
            try {
                Process p6 = Runtime.getRuntime().exec("ping6 -c 1 www.google.com");
                int status6 = p6.waitFor();
                if (status6 == 0) {
                    mPingHostnameResultV6 = "Pass";
                } else {
                    mPingHostnameResultV6 = String.format("Fail(%d)", status6);
                }
            } catch (IOException e) {
                mPingHostnameResultV6 = "Fail: IOException";
            }
        } catch (InterruptedException e) {
            mPingHostnameResultV4 = mPingHostnameResultV6 = "Fail: InterruptedException";
        }
    }

    /**
     * This function checks for basic functionality of HTTP Client.
     */
    private void httpClientTest() {
        HttpURLConnection urlConnection = null;
        try {
            // TODO: Hardcoded for now, make it UI configurable
            URL url = new URL("https://www.google.com");
            urlConnection = (HttpURLConnection) url.openConnection();
            if (urlConnection.getResponseCode() == 200) {
                mHttpClientTestResult = "Pass";
            } else {
                mHttpClientTestResult = "Fail: Code: " + urlConnection.getResponseMessage();
            }
        } catch (IOException e) {
            mHttpClientTestResult = "Fail: IOException";
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    private void refreshSmsc() {
        mQueuedWork.execute(new Runnable() {
            public void run() {
                //FIXME: Replace with a TelephonyManager call
                mPhone.getSmscAddress(mHandler.obtainMessage(EVENT_QUERY_SMSC_DONE));
            }
        });
    }

    private void updateAllCellInfo() {

        mCellInfo.setText("");

        final Runnable updateAllCellInfoResults = new Runnable() {
            public void run() {
                updateCellInfo(mCellInfoResult);
            }
        };

        mQueuedWork.execute(new Runnable() {
            @Override
            public void run() {
                mCellInfoResult = mTelephonyManager.getAllCellInfo();

                mHandler.post(updateAllCellInfoResults);
            }
        });
    }

    private void updatePingState() {
        // Set all to unknown since the threads will take a few secs to update.
        mPingHostnameResultV4 = getResources().getString(R.string.radioInfo_unknown);
        mPingHostnameResultV6 = getResources().getString(R.string.radioInfo_unknown);
        mHttpClientTestResult = getResources().getString(R.string.radioInfo_unknown);

        mPingHostnameV4.setText(mPingHostnameResultV4);
        mPingHostnameV6.setText(mPingHostnameResultV6);
        mHttpClientTest.setText(mHttpClientTestResult);

        final Runnable updatePingResults = new Runnable() {
            public void run() {
                mPingHostnameV4.setText(mPingHostnameResultV4);
                mPingHostnameV6.setText(mPingHostnameResultV6);
                mHttpClientTest.setText(mHttpClientTestResult);
            }
        };

        Thread hostname = new Thread() {
            @Override
            public void run() {
                pingHostname();
                mHandler.post(updatePingResults);
            }
        };
        hostname.start();

        Thread httpClient = new Thread() {
            @Override
            public void run() {
                httpClientTest();
                mHandler.post(updatePingResults);
            }
        };
        httpClient.start();
    }

    private MenuItem.OnMenuItemClickListener mViewADNCallback =
            new MenuItem.OnMenuItemClickListener() {
        public boolean onMenuItemClick(MenuItem item) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            // XXX We need to specify the component here because if we don't
            // the activity manager will try to resolve the type by calling
            // the content provider, which causes it to be loaded in a process
            // other than the Dialer process, which causes a lot of stuff to
            // break.
            intent.setClassName("com.android.phone", "com.android.phone.SimContacts");
            startActivity(intent);
            return true;
        }
    };

    private MenuItem.OnMenuItemClickListener mViewFDNCallback =
            new MenuItem.OnMenuItemClickListener() {
        public boolean onMenuItemClick(MenuItem item) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            // XXX We need to specify the component here because if we don't
            // the activity manager will try to resolve the type by calling
            // the content provider, which causes it to be loaded in a process
            // other than the Dialer process, which causes a lot of stuff to
            // break.
            intent.setClassName("com.android.phone", "com.android.phone.settings.fdn.FdnList");
            startActivity(intent);
            return true;
        }
    };

    private MenuItem.OnMenuItemClickListener mViewSDNCallback =
            new MenuItem.OnMenuItemClickListener() {
        public boolean onMenuItemClick(MenuItem item) {
            Intent intent = new Intent(
                    Intent.ACTION_VIEW, Uri.parse("content://icc/sdn"));
            // XXX We need to specify the component here because if we don't
            // the activity manager will try to resolve the type by calling
            // the content provider, which causes it to be loaded in a process
            // other than the Dialer process, which causes a lot of stuff to
            // break.
            intent.setClassName("com.android.phone", "com.android.phone.ADNList");
            startActivity(intent);
            return true;
        }
    };

    private MenuItem.OnMenuItemClickListener mGetImsStatus =
            new MenuItem.OnMenuItemClickListener() {
        public boolean onMenuItemClick(MenuItem item) {
            boolean isImsRegistered = mPhone.isImsRegistered();
            boolean availableVolte = mPhone.isVoiceOverCellularImsEnabled();
            boolean availableWfc = mPhone.isWifiCallingEnabled();
            boolean availableVt = mPhone.isVideoEnabled();
            boolean availableUt = mPhone.isUtEnabled();

            final String imsRegString = isImsRegistered
                    ? getString(R.string.radio_info_ims_reg_status_registered)
                    : getString(R.string.radio_info_ims_reg_status_not_registered);

            final String available = getString(R.string.radio_info_ims_feature_status_available);
            final String unavailable = getString(
                    R.string.radio_info_ims_feature_status_unavailable);

            String imsStatus = getString(R.string.radio_info_ims_reg_status,
                    imsRegString,
                    availableVolte ? available : unavailable,
                    availableWfc ? available : unavailable,
                    availableVt ? available : unavailable,
                    availableUt ? available : unavailable);

            AlertDialog imsDialog = new AlertDialog.Builder(RadioInfo.this)
                    .setMessage(imsStatus)
                    .setTitle(getString(R.string.radio_info_ims_reg_status_title))
                    .create();

            imsDialog.show();

            return true;
        }
    };

    private MenuItem.OnMenuItemClickListener mToggleData =
            new MenuItem.OnMenuItemClickListener() {
        public boolean onMenuItemClick(MenuItem item) {
            int state = mTelephonyManager.getDataState();
            switch (state) {
                case TelephonyManager.DATA_CONNECTED:
                    mTelephonyManager.setDataEnabled(false);
                    break;
                case TelephonyManager.DATA_DISCONNECTED:
                    mTelephonyManager.setDataEnabled(true);
                    break;
                default:
                    // do nothing
                    break;
            }
            return true;
        }
    };

    private boolean isRadioOn() {
        return mTelephonyManager.getRadioPowerState() == TelephonyManager.RADIO_POWER_ON;
    }

    private void updateRadioPowerState() {
        //delightful hack to prevent on-checked-changed calls from
        //actually forcing the radio preference to its transient/current value.
        mRadioPowerOnSwitch.setOnCheckedChangeListener(null);
        mRadioPowerOnSwitch.setChecked(isRadioOn());
        mRadioPowerOnSwitch.setOnCheckedChangeListener(mRadioPowerOnChangeListener);
    }

    private void setImsVolteProvisionedState(boolean state) {
        Log.d(TAG, "setImsVolteProvisioned state: " + ((state) ? "on" : "off"));
        setImsConfigProvisionedState(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE, state);
    }

    private void setImsVtProvisionedState(boolean state) {
        Log.d(TAG, "setImsVtProvisioned() state: " + ((state) ? "on" : "off"));
        setImsConfigProvisionedState(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VIDEO,
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE, state);
    }

    private void setImsWfcProvisionedState(boolean state) {
        Log.d(TAG, "setImsWfcProvisioned() state: " + ((state) ? "on" : "off"));
        setImsConfigProvisionedState(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN, state);
    }

    private void setEabProvisionedState(boolean state) {
        Log.d(TAG, "setEabProvisioned() state: " + ((state) ? "on" : "off"));
        setRcsConfigProvisionedState(ImsRcsManager.CAPABILITY_TYPE_PRESENCE_UCE,
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE, state);
    }

    private void setImsConfigProvisionedState(int capability, int tech, boolean state) {
        if (mProvisioningManager != null) {
            mQueuedWork.execute(new Runnable() {
                public void run() {
                    try {
                        mProvisioningManager.setProvisioningStatusForCapability(
                                capability, tech, state);
                    } catch (RuntimeException e) {
                        Log.e(TAG, "setImsConfigProvisioned() exception:", e);
                    }
                }
            });
        }
    }

    private void setRcsConfigProvisionedState(int capability, int tech, boolean state) {
        if (mProvisioningManager != null) {
            mQueuedWork.execute(new Runnable() {
                public void run() {
                    try {
                        mProvisioningManager.setRcsProvisioningStatusForCapability(
                                capability, tech, state);
                    } catch (RuntimeException e) {
                        Log.e(TAG, "setRcsConfigProvisioned() exception:", e);
                    }
                }
            });
        }
    }

    private boolean isImsVolteProvisioningRequired() {
        return isImsConfigProvisioningRequired(
                MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE);
    }

    private boolean isImsVtProvisioningRequired() {
        return isImsConfigProvisioningRequired(
                MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VIDEO,
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE);
    }

    private boolean isImsWfcProvisioningRequired() {
        return isImsConfigProvisioningRequired(
                MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN);
    }

    private boolean isEabProvisioningRequired() {
        return isRcsConfigProvisioningRequired(
                ImsRcsManager.CAPABILITY_TYPE_PRESENCE_UCE,
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE);
    }

    private boolean isImsConfigProvisioningRequired(int capability, int tech) {
        if (mProvisioningManager != null) {
            try {
                return mProvisioningManager.isProvisioningRequiredForCapability(
                        capability, tech);
            } catch (RuntimeException e) {
                Log.e(TAG, "isImsConfigProvisioningRequired() exception:", e);
            }
        }

        return false;
    }

    private boolean isRcsConfigProvisioningRequired(int capability, int tech) {
        if (mProvisioningManager != null) {
            try {
                return mProvisioningManager.isRcsProvisioningRequiredForCapability(
                        capability, tech);
            } catch (RuntimeException e) {
                Log.e(TAG, "isRcsConfigProvisioningRequired() exception:", e);
            }
        }

        return false;
    }

    OnCheckedChangeListener mRadioPowerOnChangeListener = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            // TODO: b/145681511. Within current design, radio power on all of the phones need
            // to be controlled at the same time.
            Phone[] phones = PhoneFactory.getPhones();
            if (phones == null) {
                return;
            }
            log("toggle radio power: phone*" + phones.length + " " + (isRadioOn() ? "on" : "off"));
            for (int phoneIndex = 0; phoneIndex < phones.length; phoneIndex++) {
                if (phones[phoneIndex] != null) {
                    phones[phoneIndex].setRadioPower(isChecked);
                }
            }
        }
    };

    private final OnCheckedChangeListener mSimulateOosOnChangeListener =
            (buttonView, isChecked) -> {
        Intent intent = new Intent("com.android.internal.telephony.TestServiceState");
        if (isChecked) {
            log("Send OOS override broadcast intent.");
            intent.putExtra("data_reg_state", 1);
            mSimulateOos[mPhone.getPhoneId()] = true;
        } else {
            log("Remove OOS override.");
            intent.putExtra("action", "reset");
            mSimulateOos[mPhone.getPhoneId()] = false;
        }

        mPhone.getTelephonyTester().setServiceStateTestIntent(intent);
    };

    private boolean isImsVolteProvisioned() {
        return getImsConfigProvisionedState(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE);
    }

    OnCheckedChangeListener mImsVolteCheckedChangeListener = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            setImsVolteProvisionedState(isChecked);
        }
    };

    private boolean isImsVtProvisioned() {
        return getImsConfigProvisionedState(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VIDEO,
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE);
    }

    OnCheckedChangeListener mImsVtCheckedChangeListener = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            setImsVtProvisionedState(isChecked);
        }
    };

    private boolean isImsWfcProvisioned() {
        return getImsConfigProvisionedState(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN);
    }

    OnCheckedChangeListener mImsWfcCheckedChangeListener = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            setImsWfcProvisionedState(isChecked);
        }
    };

    private boolean isEabProvisioned() {
        return getRcsConfigProvisionedState(ImsRcsManager.CAPABILITY_TYPE_PRESENCE_UCE,
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE);
    }

    OnCheckedChangeListener mEabCheckedChangeListener = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            setEabProvisionedState(isChecked);
        }
    };

    private boolean getImsConfigProvisionedState(int capability, int tech) {
        if (mProvisioningManager != null) {
            try {
                return mProvisioningManager.getProvisioningStatusForCapability(
                        capability, tech);
            } catch (RuntimeException e) {
                Log.e(TAG, "getImsConfigProvisionedState() exception:", e);
            }
        }

        return false;
    }

    private boolean getRcsConfigProvisionedState(int capability, int tech) {
        if (mProvisioningManager != null) {
            try {
                return mProvisioningManager.getRcsProvisioningStatusForCapability(
                        capability, tech);
            } catch (RuntimeException e) {
                Log.e(TAG, "getRcsConfigProvisionedState() exception:", e);
            }
        }

        return false;
    }

    private boolean isEabEnabledByPlatform() {
        if (mPhone != null) {
            CarrierConfigManager configManager = (CarrierConfigManager)
                    mPhone.getContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
            PersistableBundle b = configManager.getConfigForSubId(mPhone.getSubId());
            if (b != null) {
                return b.getBoolean(
                        CarrierConfigManager.KEY_USE_RCS_PRESENCE_BOOL, false) || b.getBoolean(
                        CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_CAPABILITY_EXCHANGE_BOOL,
                        false);
            }
        }
        return false;
    }

    private void updateImsProvisionedState() {
        if (!isImsSupportedOnDevice(mPhone.getContext())) {
            return;
        }

        updateServiceEnabledByPlatform();

        updateEabProvisionedSwitch(isEabEnabledByPlatform());
    }

    private void updateVolteProvisionedSwitch(boolean isEnabledByPlatform) {
        boolean isProvisioned = isEnabledByPlatform && isImsVolteProvisioned();
        log("updateVolteProvisionedSwitch isProvisioned" + isProvisioned);

        mImsVolteProvisionedSwitch.setOnCheckedChangeListener(null);
        mImsVolteProvisionedSwitch.setChecked(isProvisioned);
        mImsVolteProvisionedSwitch.setOnCheckedChangeListener(mImsVolteCheckedChangeListener);
        mImsVolteProvisionedSwitch.setEnabled(!IS_USER_BUILD
                && isEnabledByPlatform && isImsVolteProvisioningRequired());
    }

    private void updateVtProvisionedSwitch(boolean isEnabledByPlatform) {
        boolean isProvisioned = isEnabledByPlatform && isImsVtProvisioned();
        log("updateVtProvisionedSwitch isProvisioned" + isProvisioned);

        mImsVtProvisionedSwitch.setOnCheckedChangeListener(null);
        mImsVtProvisionedSwitch.setChecked(isProvisioned);
        mImsVtProvisionedSwitch.setOnCheckedChangeListener(mImsVtCheckedChangeListener);
        mImsVtProvisionedSwitch.setEnabled(!IS_USER_BUILD
                && isEnabledByPlatform && isImsVtProvisioningRequired());
    }

    private void updateWfcProvisionedSwitch(boolean isEnabledByPlatform) {
        boolean isProvisioned = isEnabledByPlatform && isImsWfcProvisioned();
        log("updateWfcProvisionedSwitch isProvisioned" + isProvisioned);

        mImsWfcProvisionedSwitch.setOnCheckedChangeListener(null);
        mImsWfcProvisionedSwitch.setChecked(isProvisioned);
        mImsWfcProvisionedSwitch.setOnCheckedChangeListener(mImsWfcCheckedChangeListener);
        mImsWfcProvisionedSwitch.setEnabled(!IS_USER_BUILD
                && isEnabledByPlatform && isImsWfcProvisioningRequired());
    }

    private void updateEabProvisionedSwitch(boolean isEnabledByPlatform) {
        log("updateEabProvisionedSwitch isEabWfcProvisioned()=" + isEabProvisioned());

        mEabProvisionedSwitch.setOnCheckedChangeListener(null);
        mEabProvisionedSwitch.setChecked(isEabProvisioned());
        mEabProvisionedSwitch.setOnCheckedChangeListener(mEabCheckedChangeListener);
        mEabProvisionedSwitch.setEnabled(!IS_USER_BUILD
                && isEnabledByPlatform && isEabProvisioningRequired());
    }

    OnClickListener mDnsCheckButtonHandler = new OnClickListener() {
        public void onClick(View v) {
            //FIXME: Replace with a TelephonyManager call
            mPhone.disableDnsCheck(!mPhone.isDnsCheckDisabled());
            updateDnsCheckState();
        }
    };

    OnClickListener mOemInfoButtonHandler = new OnClickListener() {
        public void onClick(View v) {
            Intent intent = new Intent(OEM_RADIO_INFO_INTENT);
            try {
                startActivity(intent);
            } catch (android.content.ActivityNotFoundException ex) {
                log("OEM-specific Info/Settings Activity Not Found : " + ex);
                // If the activity does not exist, there are no OEM
                // settings, and so we can just do nothing...
            }
        }
    };

    OnClickListener mPingButtonHandler = new OnClickListener() {
        public void onClick(View v) {
            updatePingState();
        }
    };

    OnClickListener mUpdateSmscButtonHandler = new OnClickListener() {
        public void onClick(View v) {
            mUpdateSmscButton.setEnabled(false);
            mQueuedWork.execute(new Runnable() {
                public void run() {
                    mPhone.setSmscAddress(mSmsc.getText().toString(),
                            mHandler.obtainMessage(EVENT_UPDATE_SMSC_DONE));
                }
            });
        }
    };

    OnClickListener mRefreshSmscButtonHandler = new OnClickListener() {
        public void onClick(View v) {
            refreshSmsc();
        }
    };

    OnClickListener mCarrierProvisioningButtonHandler = v -> {
        String carrierProvisioningApp = getCarrierProvisioningAppString();
        if (!TextUtils.isEmpty(carrierProvisioningApp)) {
            final Intent intent = new Intent(CARRIER_PROVISIONING_ACTION);
            final ComponentName serviceComponent =
                    ComponentName.unflattenFromString(carrierProvisioningApp);
            intent.setComponent(serviceComponent);
            sendBroadcast(intent);
        }
    };

    OnClickListener mTriggerCarrierProvisioningButtonHandler = v -> {
        String carrierProvisioningApp = getCarrierProvisioningAppString();
        if (!TextUtils.isEmpty(carrierProvisioningApp)) {
            final Intent intent = new Intent(TRIGGER_CARRIER_PROVISIONING_ACTION);
            final ComponentName serviceComponent =
                    ComponentName.unflattenFromString(carrierProvisioningApp);
            intent.setComponent(serviceComponent);
            sendBroadcast(intent);
        }
    };

    AdapterView.OnItemSelectedListener mPreferredNetworkHandler =
            new AdapterView.OnItemSelectedListener() {

        public void onItemSelected(AdapterView parent, View v, int pos, long id) {
            if (mPreferredNetworkTypeResult != pos && pos >= 0
                    && pos <= PREFERRED_NETWORK_LABELS.length - 2) {
                mPreferredNetworkTypeResult = pos;
                new Thread(() -> {
                    mTelephonyManager.setAllowedNetworkTypesForReason(
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                            RadioAccessFamily.getRafFromNetworkType(mPreferredNetworkTypeResult));
                }).start();
            }
        }

        public void onNothingSelected(AdapterView parent) {
        }
    };

    AdapterView.OnItemSelectedListener mSelectPhoneIndexHandler =
            new AdapterView.OnItemSelectedListener() {

        public void onItemSelected(AdapterView parent, View v, int pos, long id) {
            if (pos >= 0 && pos <= sPhoneIndexLabels.length - 1) {
                // the array position is equal to the phone index
                int phoneIndex = pos;
                Phone[] phones = PhoneFactory.getPhones();
                if (phones == null || phones.length <= phoneIndex) {
                    return;
                }
                // getSubId says it takes a slotIndex, but it actually takes a phone index
                mSelectedPhoneIndex = phoneIndex;
                updatePhoneIndex(phoneIndex, SubscriptionManager.getSubscriptionId(phoneIndex));
            }
        }

        public void onNothingSelected(AdapterView parent) {
        }
    };

    AdapterView.OnItemSelectedListener mCellInfoRefreshRateHandler  =
            new AdapterView.OnItemSelectedListener() {

        public void onItemSelected(AdapterView parent, View v, int pos, long id) {
            mCellInfoRefreshRateIndex = pos;
            mTelephonyManager.setCellInfoListRate(CELL_INFO_REFRESH_RATES[pos], mPhone.getSubId());
            updateAllCellInfo();
        }

        public void onNothingSelected(AdapterView parent) {
        }
    };

    private String getCarrierProvisioningAppString() {
        if (mPhone != null) {
            CarrierConfigManager configManager =
                    mPhone.getContext().getSystemService(CarrierConfigManager.class);
            PersistableBundle b = configManager.getConfigForSubId(mPhone.getSubId());
            if (b != null) {
                return b.getString(
                        CarrierConfigManager.KEY_CARRIER_PROVISIONING_APP_STRING, "");
            }
        }
        return "";
    }

    boolean isCbrsSupported() {
        return getResources().getBoolean(
              com.android.internal.R.bool.config_cbrs_supported);
    }

    void updateCbrsDataState(boolean state) {
        Log.d(TAG, "setCbrsDataSwitchState() state:" + ((state) ? "on" : "off"));
        if (mTelephonyManager != null) {
            mQueuedWork.execute(new Runnable() {
                public void run() {
                    mTelephonyManager.setOpportunisticNetworkState(state);
                    mHandler.post(() -> mCbrsDataSwitch.setChecked(getCbrsDataState()));
                }
            });
        }
    }

    boolean getCbrsDataState() {
        boolean state = false;
        if (mTelephonyManager != null) {
            state = mTelephonyManager.isOpportunisticNetworkEnabled();
        }
        Log.d(TAG, "getCbrsDataState() state:" + ((state) ? "on" : "off"));
        return state;
    }

    OnCheckedChangeListener mCbrsDataSwitchChangeListener = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            updateCbrsDataState(isChecked);
        }
    };

    private void showDsdsChangeDialog() {
        final AlertDialog confirmDialog = new Builder(RadioInfo.this)
                .setTitle(R.string.dsds_dialog_title)
                .setMessage(R.string.dsds_dialog_message)
                .setPositiveButton(R.string.dsds_dialog_confirm, mOnDsdsDialogConfirmedListener)
                .setNegativeButton(R.string.dsds_dialog_cancel, mOnDsdsDialogConfirmedListener)
                .create();
        confirmDialog.show();
    }

    private static boolean isDsdsSupported() {
        return (TelephonyManager.getDefault().isMultiSimSupported()
            == TelephonyManager.MULTISIM_ALLOWED);
    }

    private static boolean isDsdsEnabled() {
        return TelephonyManager.getDefault().getPhoneCount() > 1;
    }

    private void performDsdsSwitch() {
        mTelephonyManager.switchMultiSimConfig(mDsdsSwitch.isChecked() ? 2 : 1);
    }

    /**
     * @return {@code True} if the device is only supported dsds mode.
     */
    private boolean dsdsModeOnly() {
        String dsdsMode = SystemProperties.get(DSDS_MODE_PROPERTY);
        return !TextUtils.isEmpty(dsdsMode) && Integer.parseInt(dsdsMode) == ALWAYS_ON_DSDS_MODE;
    }

    DialogInterface.OnClickListener mOnDsdsDialogConfirmedListener =
            new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                mDsdsSwitch.toggle();
                performDsdsSwitch();
            }
        }
    };

    OnCheckedChangeListener mRemovableEsimChangeListener = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            setRemovableEsimAsDefaultEuicc(isChecked);
        }
    };

    private void setRemovableEsimAsDefaultEuicc(boolean isChecked) {
        Log.d(TAG, "setRemovableEsimAsDefaultEuicc isChecked: " + isChecked);
        mTelephonyManager.setRemovableEsimAsDefaultEuicc(isChecked);
        // TODO(b/232528117): Instead of sending intent, add new APIs in platform,
        //  LPA can directly use the API.
        ComponentInfo componentInfo = EuiccConnector.findBestComponent(getPackageManager());
        if (componentInfo == null) {
            Log.d(TAG, "setRemovableEsimAsDefaultEuicc: unable to find suitable component info");
            return;
        }
        final Intent intent = new Intent(ACTION_REMOVABLE_ESIM_AS_DEFAULT);
        intent.setPackage(componentInfo.packageName);
        intent.putExtra("isDefault", isChecked);
        sendBroadcast(intent);
    }

    private boolean isImsSupportedOnDevice(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY_IMS);
    }

    private void updateServiceEnabledByPlatform() {
        int subId = mPhone.getSubId();
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            log("updateServiceEnabledByPlatform subscription ID is invalid");
            return;
        }

        ImsMmTelManager imsMmTelManager = mImsManager.getImsMmTelManager(subId);
        try {
            imsMmTelManager.isSupported(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                    AccessNetworkConstants.TRANSPORT_TYPE_WWAN, getMainExecutor(), (result) -> {
                        updateVolteProvisionedSwitch(result);
                    });
            imsMmTelManager.isSupported(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VIDEO,
                    AccessNetworkConstants.TRANSPORT_TYPE_WWAN, getMainExecutor(), (result) -> {
                        updateVtProvisionedSwitch(result);
                    });
            imsMmTelManager.isSupported(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,
                    AccessNetworkConstants.TRANSPORT_TYPE_WLAN, getMainExecutor(), (result) -> {
                        updateWfcProvisionedSwitch(result);
                    });
        } catch (ImsException e) {
            e.printStackTrace();
        }
    }

    private Phone getPhone(int subId) {
        log("getPhone subId = " + subId);
        Phone phone = PhoneFactory.getPhone(SubscriptionManager.getPhoneId(subId));
        if (phone == null) {
            log("return the default phone");
            return PhoneFactory.getDefaultPhone();
        }

        return phone;
    }
}
