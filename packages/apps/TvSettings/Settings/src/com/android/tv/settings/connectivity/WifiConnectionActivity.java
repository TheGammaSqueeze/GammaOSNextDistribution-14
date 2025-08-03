/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.connectivity;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProviders;

import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;
import com.android.settingslib.RestrictedLockUtilsInternal;
import com.android.tv.settings.R;
import com.android.tv.settings.connectivity.setup.AddStartState;
import com.android.tv.settings.connectivity.setup.AdvancedWifiOptionsFlow;
import com.android.tv.settings.connectivity.setup.ConnectFailedState;
import com.android.tv.settings.connectivity.setup.ConnectState;
import com.android.tv.settings.connectivity.setup.EnterPasswordState;
import com.android.tv.settings.connectivity.setup.KnownNetworkState;
import com.android.tv.settings.connectivity.setup.OptionsOrConnectState;
import com.android.tv.settings.connectivity.setup.SuccessState;
import com.android.tv.settings.connectivity.setup.UserChoiceInfo;
import com.android.tv.settings.connectivity.util.State;
import com.android.tv.settings.connectivity.util.StateMachine;
import com.android.tv.settings.core.instrumentation.InstrumentedActivity;
import com.android.tv.settings.library.network.AccessPoint;
import com.android.tv.settings.library.util.DataBinder;
import com.android.wifitrackerlib.WifiEntry;

/**
 * Add a wifi network where we already know the ssid/security; normal post-install settings.
 */
public class WifiConnectionActivity extends InstrumentedActivity implements
        State.FragmentChangeListener {
    private static final String TAG = "WifiConnectionActivity";

    private static final String EXTRA_WIFI_ENTRY = "wifi_entry";

    public static Intent createIntent(Context context, AccessPoint result) {
        Bundle bundle = new Bundle();
        bundle.putBinder(EXTRA_WIFI_ENTRY, DataBinder.with(result.getWifiEntry()));
        return new Intent(context, WifiConnectionActivity.class)
                .putExtras(bundle);
    }

    private StateMachine mStateMachine;
    private State mConnectFailureState;
    private State mConnectState;
    private State mEnterPasswordState;
    private State mKnownNetworkState;
    private State mSuccessState;
    private State mOptionsOrConnectState;
    private State mAddStartState;
    private State mFinishState;

    private final StateMachine.Callback mStateMachineCallback = new StateMachine.Callback() {
        @Override
        public void onFinish(int result) {
            setResult(result);
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NetworkChangeStateManager.getInstance().setIsNetworkStateKnown(false);
        final UserManager userManager = UserManager.get(this);
        if (userManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_WIFI)) {
            EnforcedAdmin admin = RestrictedLockUtilsInternal.checkIfRestrictionEnforced(this,
                        UserManager.DISALLOW_CONFIG_WIFI, UserHandle.myUserId());
            if (admin != null) {
                RestrictedLockUtils.sendShowAdminSupportDetailsIntent(this, admin);
            }
            finish();
            return;
        }

        setContentView(R.layout.wifi_container);
        mStateMachine = ViewModelProviders.of(this).get(StateMachine.class);
        mStateMachine.setCallback(mStateMachineCallback);
        mKnownNetworkState = new KnownNetworkState(this);
        mEnterPasswordState = new EnterPasswordState(this);
        mConnectState = new ConnectState(this);
        mConnectFailureState = new ConnectFailedState(this);
        mSuccessState = new SuccessState(this);
        mOptionsOrConnectState = new OptionsOrConnectState(this);
        mAddStartState = new AddStartState(this);
        mFinishState = new FinishState(this);

        /* KnownNetwork */
        mStateMachine.addState(
                mKnownNetworkState,
                StateMachine.ADD_START,
                mAddStartState);
        mStateMachine.addState(
                mKnownNetworkState,
                StateMachine.SELECT_WIFI,
                mFinishState);

        /* Add Start */
        mStateMachine.addState(
                mAddStartState,
                StateMachine.PASSWORD,
                mEnterPasswordState);
        mStateMachine.addState(
                mAddStartState,
                StateMachine.CONNECT,
                mConnectState);

        /* Enter Password */
        mStateMachine.addState(
                mEnterPasswordState,
                StateMachine.OPTIONS_OR_CONNECT,
                mOptionsOrConnectState);

        /* Option or Connect */
        mStateMachine.addState(
                mOptionsOrConnectState,
                StateMachine.CONNECT,
                mConnectState);
        mStateMachine.addState(
                mOptionsOrConnectState,
                StateMachine.RESTART,
                mEnterPasswordState);

        /* Connect */
        mStateMachine.addState(
                mConnectState,
                StateMachine.RESULT_FAILURE,
                mConnectFailureState);
        mStateMachine.addState(
                mConnectState,
                StateMachine.RESULT_SUCCESS,
                mSuccessState);

        /* Connect Failed */
        mStateMachine.addState(
                mConnectFailureState,
                StateMachine.TRY_AGAIN,
                mOptionsOrConnectState
        );
        mStateMachine.addState(
                mConnectFailureState,
                StateMachine.SELECT_WIFI,
                mFinishState
        );

        WifiEntry wifiEntry = DataBinder.getData(
                getIntent().getExtras().getBinder(EXTRA_WIFI_ENTRY));

        AdvancedWifiOptionsFlow.createFlow(
                this, false, true, null,
                mOptionsOrConnectState, mConnectState, AdvancedWifiOptionsFlow.START_DEFAULT_PAGE);
        UserChoiceInfo userChoiceInfo =
                    ViewModelProviders.of(this).get(UserChoiceInfo.class);
        userChoiceInfo.setWifiEntry(wifiEntry);
        userChoiceInfo.setWifiConfiguration(
                wifiEntry.isSaved()
                ? wifiEntry.getWifiConfiguration()
                : WifiConfigHelper.getConfiguration(wifiEntry.getSsid(),
                        wifiEntry.getSecurity()));
        userChoiceInfo.setWifiSecurity(wifiEntry.getSecurity());

        if (wifiEntry.shouldEditBeforeConnect()) {
            mStateMachine.setStartState(mEnterPasswordState);
        } else if (wifiEntry.isSaved()) {
            mStateMachine.setStartState(mKnownNetworkState);
        } else {
            mStateMachine.setStartState(mAddStartState);
        }
        mStateMachine.start(true);
    }

    @Override
    public void onBackPressed() {
        mStateMachine.back();
    }

    private void updateView(androidx.fragment.app.Fragment fragment, boolean movingForward) {
        if (fragment != null) {
            FragmentTransaction updateTransaction = getSupportFragmentManager().beginTransaction();
            if (movingForward) {
                updateTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            } else {
                updateTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
            }
            updateTransaction.replace(R.id.wifi_container, fragment, TAG);
            updateTransaction.commitAllowingStateLoss();
        }
    }

    @Override
    public void onFragmentChange(Fragment newFragment, boolean movingForward) {
        updateView(newFragment, movingForward);
    }

}
