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

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.ims.DelegateRegistrationState;
import android.telephony.ims.DelegateRequest;
import android.telephony.ims.FeatureTagState;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.SipDelegateConfiguration;
import android.telephony.ims.SipDelegateConnection;
import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.SipMessage;
import android.telephony.ims.stub.DelegateConnectionMessageCallback;
import android.telephony.ims.stub.DelegateConnectionStateCallback;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** An activity to verify SipDelegate creation and destruction. */
public class DelegateActivity extends AppCompatActivity {

    private static final String TAG = "TestRcsApp.DelegateActivity";
    private static final String ICSI = "+g.3gpp.icsi-ref=";
    private static final String IARI = "+g.3gpp.iari-ref=";

    //https://www.gsma.com/futurenetworks/wp-content/uploads/2019/10/RCC.07-v11.0.pdf
    private static final String SESSION_TAG =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.session\"";
    private static final String STANDALONE_PAGER =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.msg\"";
    private static final String STANDALONE_LARGE =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.largemsg\"";
    private static final String STANDALONE_DEFERRED =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.oma.cpm.deferred\"";
    private static final String STANDALONE_LARGE_PAGER = "+g.gsma.rcs.cpm.pager-large";


    private static final String FILE_TRANSFER =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.fthttp\"";
    private static final String GEOLOCATION_SMS =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.geosms\"";

    private static final String CHATBOT_SESSION =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.chatbot\"";
    private static final String CHATBOT_STANDALONE =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.chatbot.sa\"";
    private static final String CHATBOT_VERSION = "+g.gsma.rcs.botversion=\"#=1,#=2\"";


    private static final int MSG_RESULT = 1;
    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();
    // Callback for incoming messages on the modem connection
    private final DelegateConnectionMessageCallback mMessageCallback =
            new DelegateConnectionMessageCallback() {
                @Override
                public void onMessageReceived(@NonNull SipMessage message) {
                    Log.i(TAG, "onMessageReceived:" + message);
                }

                @Override
                public void onMessageSendFailure(@NonNull String viaTransactionId, int reason) {
                    Log.i(TAG, "onMessageSendFailure, viaTransactionId:" + viaTransactionId
                            + " reason:" + reason);
                }

                @Override
                public void onMessageSent(@NonNull String viaTransactionId) {
                    Log.i(TAG, "onMessageSent, viaTransactionId:" + viaTransactionId);
                }

            };
    private String mCallbackResultStr = "";
    private int mDefaultSmsSubId;
    private SipDelegateManager mSipDelegateManager;
    private SipDelegateConnection mSipDelegateConnection;
    private Button mInitButton;
    private Button mDestroyButton;
    private TextView mCallbackResult;
    private CheckBox mChatCb, mStandalonePagerCb, mStandaloneLargeCb, mStandaloneDeferredCb,
            mStandaloneLargePagerCb, mFileTransferCb, mGeolocationSmsCb, mChatbotSessionCb,
            mChatbotStandaloneCb, mChatbotVersionCb;
    private Handler mHandler;
    private final DelegateConnectionStateCallback mConnectionCallback =
            new DelegateConnectionStateCallback() {

                @Override
                public void onCreated(SipDelegateConnection c) {
                    mSipDelegateConnection = c;
                    mCallbackResultStr += "onCreated\r\n\r\n";
                    Log.i(TAG, mCallbackResultStr);
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_RESULT));
                }

                @Override
                public void onConfigurationChanged(
                        SipDelegateConfiguration registeredSipConfig) {
                    mCallbackResultStr += "onConfigurationChanged SipDelegateConfiguration:"
                            + "\r\n\r\n";
                    Log.i(TAG, "onConfigurationChanged: " + registeredSipConfig);
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_RESULT));
                    dumpConfig(registeredSipConfig);
                }


                @Override
                public void onFeatureTagStatusChanged(
                        @NonNull DelegateRegistrationState registrationState,
                        @NonNull Set<FeatureTagState> deniedFeatureTags) {
                    StringBuilder stringBuilder = new StringBuilder(
                            "onFeatureTagStatusChanged ").append(
                            " deniedFeatureTags:[");
                    Iterator<FeatureTagState> iterator = deniedFeatureTags.iterator();
                    while (iterator.hasNext()) {
                        FeatureTagState featureTagState = iterator.next();
                        stringBuilder.append(featureTagState.getFeatureTag()).append(" ").append(
                                featureTagState.getState());
                    }
                    Set<String> registeredFt = registrationState.getRegisteredFeatureTags();
                    Iterator<String> iteratorStr = registeredFt.iterator();
                    stringBuilder.append("] registeredFT:[");
                    while (iteratorStr.hasNext()) {
                        String ft = iteratorStr.next();
                        stringBuilder.append(ft).append(" ");
                    }
                    stringBuilder.append("]\r\n\r\n");
                    mCallbackResultStr += stringBuilder.toString();
                    Log.i(TAG, mCallbackResultStr);
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_RESULT));
                }

                @Override
                public void onDestroyed(int reason) {
                    mCallbackResultStr = "onDestroyed reason:" + reason;
                    Log.i(TAG, mCallbackResultStr);
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_RESULT));
                }
            };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.delegate_layout);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        mHandler = new Handler() {
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_RESULT:
                        mCallbackResult.setText(mCallbackResultStr);
                        break;
                }
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        init();
    }

    private void init() {
        mInitButton = findViewById(R.id.init_btn);
        mDestroyButton = findViewById(R.id.destroy_btn);
        mCallbackResult = findViewById(R.id.delegate_callback_result);
        mChatCb = findViewById(R.id.chat);
        mStandalonePagerCb = findViewById(R.id.standalone_pager);
        mStandaloneLargeCb = findViewById(R.id.standalone_large);
        mStandaloneDeferredCb = findViewById(R.id.standalone_deferred);
        mStandaloneLargePagerCb = findViewById(R.id.standalone_pager_large);

        mFileTransferCb = findViewById(R.id.file_transfer);
        mGeolocationSmsCb = findViewById(R.id.geolocation_sms);
        mChatbotSessionCb = findViewById(R.id.chatbot_session);
        mChatbotStandaloneCb = findViewById(R.id.chatbot_standalone);
        mChatbotVersionCb = findViewById(R.id.chatbot_version);

        mChatCb.setChecked(true);

        mDefaultSmsSubId = SmsManager.getDefaultSmsSubscriptionId();
        mCallbackResult.setMovementMethod(new ScrollingMovementMethod());

        ImsManager imsManager = this.getSystemService(ImsManager.class);
        if (SubscriptionManager.isValidSubscriptionId(mDefaultSmsSubId)) {
            mSipDelegateManager = imsManager.getSipDelegateManager(mDefaultSmsSubId);
        }
        setClickable(mDestroyButton, false);

        mInitButton.setOnClickListener(view -> {
            mCallbackResultStr = "";
            if (mSipDelegateManager != null) {
                Set<String> featureTags = getFeatureTags();
                try {
                    Log.i(TAG, "createSipDelegate");
                    dumpFt(featureTags);
                    mSipDelegateManager.createSipDelegate(new DelegateRequest(featureTags),
                            mExecutorService, mConnectionCallback, mMessageCallback);
                } catch (ImsException e) {
                    //e.printStackTrace();
                    mCallbackResult.setText(e.toString());
                    Log.e(TAG, e.toString());
                }
                setClickable(mInitButton, false);
                setClickable(mDestroyButton, true);
            }
        });

        mDestroyButton.setOnClickListener(view -> {
            mCallbackResultStr = "";
            if (mSipDelegateManager != null && mSipDelegateConnection != null) {
                Log.i(TAG, "destroySipDelegate");
                mSipDelegateManager.destroySipDelegate(mSipDelegateConnection,
                        SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
                setClickable(mInitButton, true);
                setClickable(mDestroyButton, false);
            }
        });
    }

    private Set<String> getFeatureTags() {
        HashSet<String> fts = new HashSet<>();
        if (mChatCb.isChecked()) {
            fts.add(SESSION_TAG);
        }
        if (mStandalonePagerCb.isChecked()) {
            fts.add(STANDALONE_PAGER);
        }
        if (mStandaloneLargeCb.isChecked()) {
            fts.add(STANDALONE_LARGE);
        }
        if (mStandaloneDeferredCb.isChecked()) {
            fts.add(STANDALONE_DEFERRED);
        }
        if (mStandaloneLargePagerCb.isChecked()) {
            fts.add(STANDALONE_LARGE_PAGER);
        }
        if (mFileTransferCb.isChecked()) {
            fts.add(FILE_TRANSFER);
        }
        if (mGeolocationSmsCb.isChecked()) {
            fts.add(GEOLOCATION_SMS);
        }
        if (mChatbotSessionCb.isChecked()) {
            fts.add(CHATBOT_SESSION);
        }
        if (mChatbotStandaloneCb.isChecked()) {
            fts.add(CHATBOT_STANDALONE);
        }
        if (mChatbotVersionCb.isChecked()) {
            fts.add(CHATBOT_VERSION);
        }
        return fts;
    }

    private void dumpFt(Set<String> fts) {
        Iterator<String> iterator = fts.iterator();
        StringBuilder res = new StringBuilder();
        while (iterator.hasNext()) {
            res.append(iterator.next()).append("\r\n");
        }
        Log.i(TAG, "FeatureTag: " + res.toString());
    }

    private void setClickable(Button button, boolean clickable) {
        if (clickable) {
            button.setAlpha(1);
            button.setClickable(true);
        } else {
            button.setAlpha(.5f);
            button.setClickable(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mSipDelegateManager != null && mSipDelegateConnection != null) {
            Log.i(TAG, "onStop() destroySipDelegate");
            mSipDelegateManager.destroySipDelegate(mSipDelegateConnection,
                    SipDelegateManager.SIP_DELEGATE_DESTROY_REASON_REQUESTED_BY_APP);
            setClickable(mInitButton, true);
            setClickable(mDestroyButton, false);
        }

    }

    private void dumpConfig(SipDelegateConfiguration config) {
        String result = "SipDelegateConfiguration{"
                + "mVersion=" + config.getVersion()
                + ", \n\tmTransportType=" + config.getTransportType()
                + ", \n\tmLocalIpAddr=" + config.getLocalAddress()
                + ", \n\tmSipServerAddr=" + config.getSipServerAddress()
                + ", \n\tmIsSipCompactFormEnabled=" + config.isSipCompactFormEnabled()
                + ", \n\tmIsSipKeepaliveEnabled=" + config.isSipKeepaliveEnabled()
                + ", \n\tmMaxUdpPayloadSize=" + config.getMaxUdpPayloadSizeBytes()
                + ", \n\tmPublicUserIdentifier=" + config.getPublicUserIdentifier()
                + ", \n\tmPrivateUserIdentifier=" + config.getPrivateUserIdentifier()
                + ", \n\tmHomeDomain=" + config.getHomeDomain()
                + ", \n\tmImei=" + config.getImei()
                + ", \n\tmGruu=" + config.getPublicGruuUri()
                + ", \n\tmSipAuthHeader=" + config.getSipAuthenticationHeader()
                + ", \n\tmSipAuthNonce=" + config.getSipAuthenticationNonce()
                + ", \n\tmServiceRouteHeader=" + config.getSipServiceRouteHeader()
                + ", \n\tmPathHeader=" + config.getSipPathHeader()
                + ", \n\tmUserAgentHeader=" + config.getSipUserAgentHeader()
                + ", \n\tmContactUserParam=" + config.getSipContactUserParameter()
                + ", \n\tmPaniHeader=" + config.getSipPaniHeader()
                + ", \n\tmPlaniHeader=" + config.getSipPlaniHeader()
                + ", \n\tmCniHeader=" + config.getSipCniHeader()
                + ", \n\tmAssociatedUriHeader=" + config.getSipAssociatedUriHeader()
                + ", \n\tmIpSecConfiguration=" + config.getIpSecConfiguration()
                + ", \n\tmNatConfiguration=" + config.getNatSocketAddress() + '}';
        Log.i(TAG, result);
    }

}
