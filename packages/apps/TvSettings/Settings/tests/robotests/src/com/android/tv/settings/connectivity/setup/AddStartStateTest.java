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

import static org.mockito.Mockito.verify;
import static org.robolectric.shadow.api.Shadow.extract;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;

import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProviders;

import com.android.tv.settings.library.network.AccessPoint;
import com.android.tv.settings.connectivity.util.State;
import com.android.tv.settings.connectivity.util.StateMachine;
import com.android.tv.settings.library.util.ThreadUtils;
import com.android.tv.settings.testutils.ShadowStateMachine;
import com.android.wifitrackerlib.WifiEntry;
import com.android.wifitrackerlib.WifiTrackerInjector;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = ShadowStateMachine.class)
public class AddStartStateTest {
    private FragmentActivity mActivity;
    private AddStartState mAddStartState;
    private UserChoiceInfo mUserChoiceInfo;
    @Mock
    private State.StateCompleteListener mStateCompleteListener;
    @Mock private WifiManager mMockWifiManager;
    @Mock private WifiTrackerInjector mWifiTrackerInjector;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mActivity = Robolectric.buildActivity(FragmentActivity.class).get();
        StateMachine stateMachine = ViewModelProviders.of(mActivity).get(StateMachine.class);
        mUserChoiceInfo = ViewModelProviders.of(mActivity).get(UserChoiceInfo.class);
        ShadowStateMachine shadowStateMachine = extract(stateMachine);
        shadowStateMachine.setListener(mStateCompleteListener);
        mAddStartState = new AddStartState(mActivity);
    }

    @Test
    public void testForward_needsWifiConfiguration_NeedPassword() {
        mUserChoiceInfo.init();
        mUserChoiceInfo.setWifiEntry(makeWifiEntry(
                /* needsWifiConfiguration= */ true,
                /* shouldEditBeforeConnect= */ false));
        mAddStartState.processForward();
        verify(mStateCompleteListener).onComplete(StateMachine.PASSWORD);
    }

    @Test
    public void testForward_shouldEditBeforeConnect_NeedPassword() {
        mUserChoiceInfo.init();
        mUserChoiceInfo.setWifiEntry(makeWifiEntry(
                /* needsWifiConfiguration= */ false,
                /* shouldEditBeforeConnect= */ true));
        mAddStartState.processForward();
        verify(mStateCompleteListener).onComplete(StateMachine.PASSWORD);
    }

    @Test
    public void testForward_DoNotNeedPassword_Connect() {
        mUserChoiceInfo.setWifiEntry(makeWifiEntry(
                /* needsWifiConfiguration= */ false,
                /* shouldEditBeforeConnect= */ false));
        mAddStartState.processForward();
        verify(mStateCompleteListener).onComplete(StateMachine.CONNECT);
    }

    private WifiEntry makeWifiEntry(boolean needsWifiConfiguration,
                                    boolean shouldEditBeforeConnect) {
        return new WifiEntry(mWifiTrackerInjector,
                ThreadUtils.getUiThreadHandler(), mMockWifiManager, false) {
            @Override
            public boolean needsWifiConfiguration() {
                return needsWifiConfiguration;
            }

            @Override
            public boolean shouldEditBeforeConnect() {
                return shouldEditBeforeConnect;
            }
        };
    }
}
