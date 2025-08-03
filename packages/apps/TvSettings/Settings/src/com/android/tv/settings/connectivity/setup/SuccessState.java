/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tv.settings.connectivity.setup;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProviders;

import com.android.tv.settings.R;
import com.android.tv.settings.connectivity.NetworkChangeDetectionConfigs;
import com.android.tv.settings.connectivity.NetworkChangeStateManager;
import com.android.tv.settings.connectivity.util.State;
import com.android.tv.settings.connectivity.util.StateMachine;

/**
 * State responsible for showing the connection successful page.
 */
public class SuccessState implements State {
    private final FragmentActivity mActivity;
    private final SharedPreferences sharedPref;
    private SharedPreferences.Editor mEditor;
    private Fragment mFragment;

    public SuccessState(FragmentActivity activity) {
        mActivity = activity;
        sharedPref = mActivity.getApplicationContext().getSharedPreferences(
                NetworkChangeDetectionConfigs.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public void processForward() {
        mFragment = SuccessFragment.newInstance(
                mActivity.getString(R.string.wifi_setup_connection_success));
        FragmentChangeListener listener = (FragmentChangeListener) mActivity;
        if (listener != null) {
            NetworkChangeStateManager manager = NetworkChangeStateManager.getInstance();
            if (!manager.getIsNetworkStateKnown()) {
                int currentNetworkCount = sharedPref.getInt(
                        NetworkChangeDetectionConfigs.PREFERENCE_KEY, 0);
                mEditor = sharedPref.edit();
                mEditor.putInt(NetworkChangeDetectionConfigs.PREFERENCE_KEY,
                        currentNetworkCount + 1);
                mEditor.commit();

                // Notify NetworkChange observers of change to the sharedpreferences data.
                mActivity.getApplicationContext().getContentResolver().notifyChange(
                        NetworkChangeDetectionConfigs.CONTENT_URI, null);
            }

            manager.setIsNetworkStateKnown(false);
            listener.onFragmentChange(mFragment, true);
        }
    }

    @Override
    public void processBackward() {
        StateMachine stateMachine = ViewModelProviders.of(mActivity).get(StateMachine.class);
        stateMachine.back();
    }

    @Override
    public Fragment getFragment() {
        return mFragment;
    }

    /**
     * Fragment that shows network is successfully connected.
     */
    public static class SuccessFragment extends MessageFragment {
        private static final int MSG_TIME_OUT = 1;
        private static final int TIME_OUT_MS = 3 * 1000;
        private static final String KEY_TIME_OUT_DURATION = "time_out_duration";
        private Handler mTimeoutHandler;

        /**
         * Get the fragment based on the title.
         *
         * @param title title of the fragment.
         * @return the fragment.
         */
        public static SuccessFragment newInstance(String title) {
            SuccessFragment fragment = new SuccessFragment();
            Bundle args = new Bundle();
            addArguments(args, title, false);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            StateMachine stateMachine = ViewModelProviders
                    .of(getActivity())
                    .get(StateMachine.class);
            mTimeoutHandler = new Handler(getActivity().getMainLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case MSG_TIME_OUT:
                            stateMachine.finish(Activity.RESULT_OK);
                            break;
                        default:
                            break;
                    }
                }
            };
            super.onCreate(savedInstanceState);
        }

        @Override
        public void onResume() {
            super.onResume();
            mTimeoutHandler.sendEmptyMessageDelayed(MSG_TIME_OUT,
                    getArguments().getInt(KEY_TIME_OUT_DURATION, TIME_OUT_MS));
        }

        @Override
        public void onPause() {
            super.onPause();
            mTimeoutHandler.removeMessages(MSG_TIME_OUT);
        }
    }
}
