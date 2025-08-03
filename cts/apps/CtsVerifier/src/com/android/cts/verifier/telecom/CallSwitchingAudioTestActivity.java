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

package com.android.cts.verifier.telecom;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.android.compatibility.common.util.ApiTest;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.List;

/**
 * Test verifies there is not an issue with call audio for both a self-managed and sim based call.
 */
@ApiTest(apis = {"android.telecom.ConnectionService"})
public class CallSwitchingAudioTestActivity extends PassFailButtons.Activity {
    // Constants
    private static final String TAG = "CallSwitchingAudioTestActivity";
    private static final String EMERGENCY_HANDLE_ID = "E";
    private static final Uri TEST_DIAL_NUMBER =
            Uri.fromParts("tel", "5551212", null);
    // UI components
    private EditText mDialOutNumber;
    // Buttons
    private Button mNavigateToCallAccountSettings;
    private Button mConfirmStartState;
    private Button mInitSelfManagedCall;
    private Button mConfirmSelfManagedCallAudio;
    private Button mPlaceSimCall;
    private Button mConfirmNoSimCallAudioIssues;
    private Button mConfirmNoSelfManagedCallAudioIssues;
    // ImageViews
    private ImageView mStep1Status;
    private ImageView mStep2Status;
    private ImageView mStep3Status;
    private ImageView mStep4Status;
    private ImageView mStep5Status;
    private ImageView mStep6Status;
    // dynamic vars
    private TelecomManager mTelecomManager;
    private CtsConnection mSelfManagedConnection;

    // Connection Listener
    private CtsConnection.Listener mConnectionListener = new CtsConnection.Listener() {
        @Override
        void onShowIncomingCallUi(CtsConnection connection) {
            Log.d(TAG, "onShowIncomingCallUi");
            super.onShowIncomingCallUi(connection);
            connection.onAnswer(0);
        }

        @Override
        void onHold(CtsConnection connection) {
            Log.d(TAG, "onHold");
            super.onHold(connection);
            mStep4Status.setImageResource(R.drawable.fs_good);
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.telecom_audio_call_activity);
        setInfoResources(R.string.telecom_audio_call_test, R.string.telecom_audio_call_test_info,
                -1);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        // Get Telecom Service and cleanup any SM Connections
        mTelecomManager = getSystemService(TelecomManager.class);
        PhoneAccountUtils.cleanupConnectionServices(this);
        // init UI and define actions
        initAllUiComponents();
        defineTestButtonActions();
    }


    @Override
    protected void onDestroy() {
        PhoneAccountUtils.cleanupConnectionServices(this);
        super.onDestroy();
    }

    private void initAllUiComponents() {
        mNavigateToCallAccountSettings = findViewById(R.id.telecom_set_default_button);
        mConfirmStartState = findViewById(R.id.telecom_verify_start_state_button);
        mInitSelfManagedCall = findViewById(R.id.telecom_incoming_call_dial_button);
        mConfirmSelfManagedCallAudio = findViewById(R.id.telecom_incoming_call_confirm_button);
        mDialOutNumber = findViewById(R.id.dial_out_number);
        mPlaceSimCall = findViewById(R.id.telecom_place_sim_call);
        mConfirmNoSimCallAudioIssues = findViewById(R.id.telecom_place_sim_call_confirm);
        mConfirmNoSelfManagedCallAudioIssues = findViewById(
                R.id.verifySelfManagedAudioAfterSimBasedCallButton);

        mStep1Status = findViewById(R.id.step_1_status);
        mStep2Status = findViewById(R.id.step_2_status);
        mStep3Status = findViewById(R.id.step_3_status);
        mStep4Status = findViewById(R.id.step_4_status);
        mStep5Status = findViewById(R.id.step_5_status);
        mStep6Status = findViewById(R.id.step_6_status);

        mStep1Status.setImageResource(R.drawable.fs_indeterminate);
        mStep2Status.setImageResource(R.drawable.fs_indeterminate);
        mStep3Status.setImageResource(R.drawable.fs_indeterminate);
        mStep4Status.setImageResource(R.drawable.fs_indeterminate);
        mStep6Status.setImageResource(R.drawable.fs_indeterminate);
    }

    private void defineTestButtonActions() {
        // ----- step 1a --------
        // Give the tester a button that allows them to change the default
        mNavigateToCallAccountSettings.setOnClickListener(v -> {
            Intent intent = new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
            startActivity(intent);
        });
        // ----- step 1b --------
        // verify the test start state:
        //   1. SM PA registered and enabled
        //   2. SIM based account is the default
        mConfirmStartState.setOnClickListener(v -> {
            PhoneAccountUtils.registerTestSelfManagedPhoneAccount(this);
            if (isSelfManagedTestAccountEnabled() && isDefaultOutgoingAccountIsSimBased()) {
                getPassButton().setEnabled(true);
                Log.i(TAG, "mConfirmStartState: pass - self-managed account is enabled &&"
                        + " SIM based phoneAccount is defaultUserSelectedOutgoing.");
                mStep1Status.setImageResource(R.drawable.fs_good);
                mConfirmStartState.setEnabled(false);
            } else {
                Log.w(TAG, "mConfirmStartState: fail - self-managed account is not enabled OR"
                        + "a SIM based phoneAccount is NOT the defaultUserSelectedOutgoing");
                mStep1Status.setImageResource(R.drawable.fs_error);
            }
        });
        // ----- step 2 ---------
        // initiate a self-managed call on a separate thread
        mInitSelfManagedCall.setOnClickListener(v -> {
            (new AsyncTask<Void, Void, Throwable>() {
                @Override
                protected Throwable doInBackground(Void... params) {
                    try {
                        placeSelfManagedCall();
                        return null;
                    } catch (Throwable t) {
                        return t;
                    }
                }
            }).execute();
        });
        // ----- step 3 --------
        // Have the tester verify the self-managed call is playing audio while double checking.
        mConfirmSelfManagedCallAudio.setOnClickListener(v -> {
            if (confirmIncomingCall()) {
                mStep3Status.setImageResource(R.drawable.fs_good);
            } else {
                mStep3Status.setImageResource(R.drawable.fs_error);
            }
        });
        // ----- step 4 -----
        // Have the tester place a SIM call
        mPlaceSimCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                placeSimCallOnDefaultHandle();
            }
        });
        // --- step 5 ---
        // Have the tester verify sim call audio
        mConfirmNoSimCallAudioIssues.setOnClickListener(v -> {
            // unHold-the self-managed call
            mSelfManagedConnection.onUnhold();
            // pass step 5
            mStep5Status.setImageResource(R.drawable.fs_good);
        });
        // --- step 6
        // Have the tester verify self-managed call audio resumes
        mConfirmNoSelfManagedCallAudioIssues.setOnClickListener(v -> {
            // clean up test
            mSelfManagedConnection.onDisconnect();
            PhoneAccountUtils.cleanupConnectionServices(this);
            // pass the final step
            mStep6Status.setImageResource(R.drawable.fs_good);
        });
    }

    private boolean isSelfManagedTestAccountEnabled() {
        PhoneAccount account = PhoneAccountUtils.getSelfManagedPhoneAccount(this);
        return account != null && account.isEnabled() && account.hasCapabilities(
                PhoneAccount.CAPABILITY_SELF_MANAGED);
    }

    private boolean isDefaultOutgoingAccountIsSimBased() {
        final PhoneAccountHandle handle = PhoneAccountUtils.getDefaultOutgoingPhoneAccount(
                getApplicationContext());
        final PhoneAccount account = PhoneAccountUtils.getSpecificPhoneAccount(
                getApplicationContext(), handle);

        Log.i(TAG, String.format("default phoneAccount=[%s]", account.toString()));

        return account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                && !(handle.getId().equals(EMERGENCY_HANDLE_ID));
    }

    private void placeSelfManagedCall() {
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, TEST_DIAL_NUMBER);

        extras.putBoolean(CtsConnection.EXTRA_PLAY_CS_AUDIO, true);


        if (mTelecomManager == null) {
            Log.w(TAG, "place_self_managed_call: fail - telecom service is null");
            mStep2Status.setImageResource(R.drawable.fs_error);
            return;
        }

        mTelecomManager.addNewIncomingCall(PhoneAccountUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_HANDLE,
                extras);

        CtsConnectionService ctsConnectionService =
                CtsConnectionService.waitForAndGetConnectionService();
        if (ctsConnectionService == null) {
            Log.w(TAG, "place_self_managed_call: fail - ctsConnectionService null");
            mStep2Status.setImageResource(R.drawable.fs_error);
            PhoneAccountUtils.cleanupConnectionServices(this);
            return;
        }

        CtsConnection connection = ctsConnectionService.waitForAndGetConnection();
        if (connection == null) {
            Log.w(TAG, "place_self_managed_call: fail - could not get connection");
            mStep2Status.setImageResource(R.drawable.fs_error);
            PhoneAccountUtils.cleanupConnectionServices(this);
            return;
        }

        connection.addListener(mConnectionListener);

        // Wait until the connection knows its audio state changed; at this point
        // Telecom knows about the connection and can answer.
        connection.waitForAudioStateChanged();
        // Make it active to simulate an answer.
        connection.setActive();
        mStep2Status.setImageResource(R.drawable.fs_good);
    }

    private boolean confirmIncomingCall() {
        if (CtsConnectionService.getConnectionService() == null) {
            return false;
        }
        List<CtsConnection> ongoingConnections =
                CtsConnectionService.getConnectionService().getConnections();
        if (ongoingConnections == null || ongoingConnections.size() != 1) {
            Log.w(TAG, "confirmIncomingCall: fail - no ongoing call found");
            return false;
        }
        mSelfManagedConnection = ongoingConnections.get(0);

        if (mSelfManagedConnection.getState() != Connection.STATE_ACTIVE) {
            Log.w(TAG, "confirmIncomingCall: fail - ongoing call is not active");
            return false;
        }

        return true;
    }

    private void placeSimCallOnDefaultHandle() {
        PhoneAccountHandle defaultHandle = PhoneAccountUtils.getDefaultOutgoingPhoneAccount(
                getApplicationContext());

        if (mDialOutNumber == null) {
            Log.w(TAG, "mDialOutNumber is null");
            mStep4Status.setImageResource(R.drawable.fs_error);
            return;
        }

        if (mDialOutNumber.getText().toString().equals("")) {
            Log.w(TAG, "mDialOutNumber is empty");
            mStep4Status.setImageResource(R.drawable.fs_error);
            return;
        }

        Uri uri = Uri.fromParts("tel", mDialOutNumber.getText().toString(), null);

        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, defaultHandle);

        mTelecomManager.placeCall(uri, extras);
    }
}
