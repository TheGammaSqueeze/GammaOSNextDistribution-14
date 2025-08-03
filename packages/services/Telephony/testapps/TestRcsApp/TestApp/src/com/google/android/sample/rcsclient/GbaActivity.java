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

package com.google.android.sample.rcsclient;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManager.BootstrapAuthenticationCallback;
import android.telephony.gba.UaSecurityProtocolIdentifier;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** An activity to verify GBA authentication. */
public class GbaActivity extends AppCompatActivity {

    private static final String TAG = "TestRcsApp.GbaActivity";
    private static final String NAF_PREFIX = "https://3GPP-bootstrapping@";

    private static final int MSG_RESULT = 1;
    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();
    private Button mGbaButton;
    private TextView mCallbackResult;
    private Spinner mOrganizationSpinner, mProtocolSpinner, mUiccSpinner;
    private EditText mTlsCs;
    private EditText mNaf;
    private Handler mHandler;
    private int mOrganization;
    private int mProtocol;
    private int mUiccType;

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte aByte : bytes) {
            result.append(String.format(Locale.US, "%02X", aByte));
        }
        return result.toString();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gba_layout);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        initLayout();
        mHandler = new Handler() {
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_RESULT:
                        mCallbackResult.setText(message.obj.toString());
                        break;
                }
            }
        };
    }

    private void initLayout() {
        mGbaButton = findViewById(R.id.gba_btn);
        mCallbackResult = findViewById(R.id.gba_result);
        mCallbackResult.setMovementMethod(new ScrollingMovementMethod());
        mTlsCs = findViewById(R.id.tls_id);
        mNaf = findViewById(R.id.naf_url);

        initOrganization();
        initProtocol();
        initUicctype();

        int defaultSmsSubId = SmsManager.getDefaultSmsSubscriptionId();
        if (!SubscriptionManager.isValidSubscriptionId(defaultSmsSubId)) {
            Log.i(TAG, "invalid subId:" + defaultSmsSubId);
            return;
        }
        TelephonyManager telephonyManager = getSystemService(
                TelephonyManager.class).createForSubscriptionId(defaultSmsSubId);
        PersistableBundle carrierConfig = telephonyManager.getCarrierConfig();
        String uploadUrl = carrierConfig.getString(
                CarrierConfigManager.KEY_CALL_COMPOSER_PICTURE_SERVER_URL_STRING);
        mNaf.setText(NAF_PREFIX + uploadUrl);

        mGbaButton.setOnClickListener(view -> {
            Log.i(TAG, "trigger bootstrapAuthenticationRequest");
            UaSecurityProtocolIdentifier.Builder builder =
                    new UaSecurityProtocolIdentifier.Builder();
            try {
                builder.setOrg(mOrganization)
                        .setProtocol(mProtocol)
                        .setTlsCipherSuite(Integer.parseInt(mTlsCs.getText().toString()));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, e.getMessage());
                return;
            }
            UaSecurityProtocolIdentifier spId = builder.build();
            telephonyManager.bootstrapAuthenticationRequest(mUiccType,
                    Uri.parse(mNaf.getText().toString()),
                    spId,
                    true,
                    mExecutorService,
                    new BootstrapAuthenticationCallback() {
                        @Override
                        public void onKeysAvailable(byte[] gbaKey, String btId) {
                            String result = "OnKeysAvailable key:" + bytesToHex(gbaKey)
                                    + "\r\n btId:" + btId;
                            Log.i(TAG, result);
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_RESULT, result));
                        }

                        @Override
                        public void onAuthenticationFailure(int reason) {
                            String result = "OnAuthenticationFailure reason: " + reason;
                            Log.i(TAG, result);
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_RESULT, result));
                        }
                    });
        });
    }

    private void initOrganization() {
        mOrganizationSpinner = findViewById(R.id.organization_list);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.organization, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mOrganizationSpinner.setAdapter(adapter);
        mOrganizationSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.i(TAG, "Organization position:" + position);
                switch (position) {
                    case 0:
                        mOrganization = UaSecurityProtocolIdentifier.ORG_NONE;
                        break;
                    case 1:
                        mOrganization = UaSecurityProtocolIdentifier.ORG_3GPP;
                        break;
                    case 2:
                        mOrganization = UaSecurityProtocolIdentifier.ORG_3GPP2;
                        break;
                    case 3:
                        mOrganization = UaSecurityProtocolIdentifier.ORG_GSMA;
                        break;
                    case 4:
                        mOrganization = UaSecurityProtocolIdentifier.ORG_LOCAL;
                        break;
                    default:
                        Log.e(TAG, "invalid position:" + position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // TODO Auto-generated method stub
            }
        });
        mOrganizationSpinner.setSelection(1);
    }

    private void initProtocol() {
        mProtocolSpinner = findViewById(R.id.protocol_list);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.protocol, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mProtocolSpinner.setAdapter(adapter);
        mProtocolSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.i(TAG, "Protocol position:" + position);
                switch (position) {
                    case 0:
                        mProtocol = UaSecurityProtocolIdentifier
                                .UA_SECURITY_PROTOCOL_3GPP_SUBSCRIBER_CERTIFICATE;
                        break;
                    case 1:
                        mProtocol = UaSecurityProtocolIdentifier
                                .UA_SECURITY_PROTOCOL_3GPP_MBMS;
                        break;
                    case 2:
                        mProtocol = UaSecurityProtocolIdentifier
                                .UA_SECURITY_PROTOCOL_3GPP_HTTP_DIGEST_AUTHENTICATION;
                        break;
                    case 3:
                        mProtocol = UaSecurityProtocolIdentifier
                                .UA_SECURITY_PROTOCOL_3GPP_HTTP_BASED_MBMS;
                        break;
                    case 4:
                        mProtocol = UaSecurityProtocolIdentifier
                                .UA_SECURITY_PROTOCOL_3GPP_SIP_BASED_MBMS;
                        break;
                    case 5:
                        mProtocol = UaSecurityProtocolIdentifier
                                .UA_SECURITY_PROTOCOL_3GPP_GENERIC_PUSH_LAYER;
                        break;
                    case 6:
                        mProtocol = UaSecurityProtocolIdentifier
                                .UA_SECURITY_PROTOCOL_3GPP_IMS_MEDIA_PLANE;
                        break;
                    case 7:
                        mProtocol = UaSecurityProtocolIdentifier
                                .UA_SECURITY_PROTOCOL_3GPP_GENERATION_TMPI;
                        break;
                    case 8:
                        mProtocol = UaSecurityProtocolIdentifier
                                .UA_SECURITY_PROTOCOL_3GPP_TLS_DEFAULT;
                        break;
                    case 9:
                        mProtocol = UaSecurityProtocolIdentifier
                                .UA_SECURITY_PROTOCOL_3GPP_TLS_BROWSER;
                        break;
                    default:
                        Log.e(TAG, "invalid position:" + position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // TODO Auto-generated method stub
            }
        });
        mProtocolSpinner.setSelection(8);
    }

    private void initUicctype() {
        mUiccSpinner = findViewById(R.id.uicc_list);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.uicc_type, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mUiccSpinner.setAdapter(adapter);
        mUiccSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.i(TAG, "uicc position:" + position);
                switch (position) {
                    case 0:
                        mUiccType = TelephonyManager.APPTYPE_UNKNOWN;
                        break;
                    case 1:
                        mUiccType = TelephonyManager.APPTYPE_SIM;
                        break;
                    case 2:
                        mUiccType = TelephonyManager.APPTYPE_USIM;
                        break;
                    case 3:
                        mUiccType = TelephonyManager.APPTYPE_RUIM;
                        break;
                    case 4:
                        mUiccType = TelephonyManager.APPTYPE_CSIM;
                        break;
                    case 5:
                        mUiccType = TelephonyManager.APPTYPE_ISIM;
                        break;
                    default:
                        Log.e(TAG, "invalid position:" + position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // TODO Auto-generated method stub
            }
        });
        mUiccSpinner.setSelection(5);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }
}
