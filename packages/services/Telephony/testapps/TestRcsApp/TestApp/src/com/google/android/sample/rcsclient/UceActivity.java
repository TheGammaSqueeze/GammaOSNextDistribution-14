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
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsRcsManager;
import android.telephony.ims.RcsContactPresenceTuple;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsUceAdapter;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.sample.rcsclient.util.NumberUtils;

import java.util.ArrayList;
import java.util.List;

/** An activity to verify UCE. */
public class UceActivity extends AppCompatActivity {

    private static final String TAG = "TestRcsApp.UceActivity";
    private static final String TELURI_PREFIX = "tel:";
    private Button mCapabilityButton;
    private Button mAvailabilityButton;
    private TextView mCapabilityResult;
    private EditText mNumbers;
    private int mDefaultSmsSubId;
    private ImsRcsManager mImsRcsManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.uce_layout);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        initLayout();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mDefaultSmsSubId = SmsManager.getDefaultSmsSubscriptionId();
        Log.i(TAG, "defaultSmsSubId:" + mDefaultSmsSubId);
        if (SubscriptionManager.isValidSubscriptionId(mDefaultSmsSubId)) {
            mImsRcsManager = getImsRcsManager(mDefaultSmsSubId);
            if (mImsRcsManager != null) {
                initLayout();
            }
        }
    }

    private void initLayout() {
        mCapabilityButton = findViewById(R.id.capability_btn);
        mAvailabilityButton = findViewById(R.id.availability_btn);
        mCapabilityResult = findViewById(R.id.capability_result);
        mCapabilityResult.setMovementMethod(new ScrollingMovementMethod());

        mCapabilityButton.setOnClickListener(view -> {
            List<Uri> contactList = getContectList();
            if (contactList.size() == 0) {
                Log.i(TAG, "empty contact list");
                return;
            }
            mCapabilityResult.setText("pending...\n");
            try {
                mImsRcsManager.getUceAdapter().requestCapabilities(contactList, getMainExecutor(),
                        new RcsUceAdapter.CapabilitiesCallback() {
                            public void onCapabilitiesReceived(
                                    List<RcsContactUceCapability> contactCapabilities) {
                                StringBuilder b = new StringBuilder("onCapabilitiesReceived:\n");
                                for (RcsContactUceCapability c : contactCapabilities) {
                                    b.append(getReadableCapability(c));
                                    b.append("\n");
                                }
                                mCapabilityResult.append(b.toString() + "\n");
                                Log.i(TAG, b.toString());
                            }

                            public void onComplete() {
                                Log.i(TAG, "onComplete()");
                                mCapabilityResult.append("onComplete");

                            }

                            public void onError(int errorCode, long retryAfterMilliseconds) {
                                String result =
                                        "onError() errorCode:" + errorCode + " retryAfterMs:"
                                                + retryAfterMilliseconds + "\n";
                                Log.i(TAG, result);
                                mCapabilityResult.append(result);
                            }
                        });
            } catch (ImsException e) {
                mCapabilityResult.setText("ImsException:" + e);
            }
        });

        mAvailabilityButton.setOnClickListener(view -> {
            List<Uri> contactList = getContectList();
            if (contactList.size() == 0) {
                Log.i(TAG, "empty contact list");
                return;
            }
            mCapabilityResult.setText("pending...\n");
            try {
                mImsRcsManager.getUceAdapter().requestAvailability(contactList.get(0),
                        getMainExecutor(), new RcsUceAdapter.CapabilitiesCallback() {
                            public void onCapabilitiesReceived(
                                    List<RcsContactUceCapability> contactCapabilities) {
                                StringBuilder b = new StringBuilder("onCapabilitiesReceived:\n");
                                for (RcsContactUceCapability c : contactCapabilities) {
                                    b.append(getReadableCapability(c));
                                    b.append("\n");
                                }
                                mCapabilityResult.append(b.toString() + "\n");
                                Log.i(TAG, b.toString());
                            }

                            public void onComplete() {
                                Log.i(TAG, "onComplete()");
                                mCapabilityResult.append("onComplete");

                            }

                            public void onError(int errorCode, long retryAfterMilliseconds) {
                                String result =
                                        "onError() errorCode:" + errorCode + " retryAfterMs:"
                                                + retryAfterMilliseconds + "\n";
                                Log.i(TAG, result);
                                mCapabilityResult.append(result);
                            }
                        });
            } catch (ImsException e) {
                mCapabilityResult.setText("ImsException:" + e);
            }
        });
    }

    private List<Uri> getContectList() {
        mNumbers = findViewById(R.id.number_list);
        String[] numbers;
        ArrayList<Uri> contactList = new ArrayList<>();
        if (!TextUtils.isEmpty(mNumbers.getText().toString())) {
            String numberList = mNumbers.getText().toString().trim();
            numbers = numberList.split(",");
            for (String number : numbers) {
                String formattedNumber = NumberUtils.formatNumber(this, number);
                if (formattedNumber != null) {
                    contactList.add(Uri.parse(TELURI_PREFIX + formattedNumber));
                } else {
                    Log.w(TAG, "number formatted improperly, skipping: " + number);
                }
            }
        }

        return contactList;
    }

    private ImsRcsManager getImsRcsManager(int subId) {
        ImsManager imsManager = getSystemService(ImsManager.class);
        if (imsManager != null) {
            try {
                return imsManager.getImsRcsManager(subId);
            } catch (Exception e) {
                Log.e(TAG, "fail to getImsRcsManager " + e.getMessage());
                return null;
            }
        }
        return null;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private String getReadableCapability(RcsContactUceCapability c) {
        StringBuilder b = new StringBuilder("RcsContactUceCapability: uri=");
        b.append(c.getContactUri());
        b.append(", requestResult=");
        b.append(c.getRequestResult());
        b.append(", sourceType=");
        b.append(c.getSourceType());
        if (c.getCapabilityMechanism() == RcsContactUceCapability.CAPABILITY_MECHANISM_PRESENCE) {
            b.append(", tuples={");
            for (RcsContactPresenceTuple t : c.getCapabilityTuples()) {
                b.append("[uri=");
                b.append(t.getContactUri());
                b.append(", serviceId=");
                b.append(t.getServiceId());
                b.append(", serviceVersion=");
                b.append(t.getServiceVersion());
                if (t.getServiceCapabilities() != null) {
                    RcsContactPresenceTuple.ServiceCapabilities servCaps =
                            t.getServiceCapabilities();
                    b.append(", servCaps=(audio=");
                    b.append(servCaps.isAudioCapable());
                    b.append(", video=");
                    b.append(servCaps.isVideoCapable());
                    b.append(", servCaps=(supported=");
                    b.append(servCaps.getSupportedDuplexModes());
                    b.append("), servCaps=(unsupported=");
                    b.append(servCaps.getUnsupportedDuplexModes());
                    b.append("))");
                }
                b.append("]");
            }
            b.append("}");
        }
        return b.toString();
    }
}
