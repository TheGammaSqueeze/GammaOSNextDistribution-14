/*
 * Copyright (C) 2020 Google Inc.
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

package com.android.car.carlauncher.homescreen.audio.telecom;

import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.os.IBinder;
import android.telecom.Call;
import android.telecom.CallAudioState;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.rule.ServiceTestRule;

import com.android.car.carlauncher.homescreen.audio.InCallModel;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.TimeoutException;

@RunWith(JUnit4.class)
public class InCallServiceImplTest {

    private InCallServiceImpl mService;
    private Call mCall = null;

    @Mock
    private InCallModel mInCallModel;

    @Rule
    public final ServiceTestRule mServiceTestRule = new ServiceTestRule();

    @Before
    public void setUp() throws TimeoutException {
        MockitoAnnotations.initMocks(this);

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                InCallServiceImpl.class);
        intent.setAction(InCallServiceImpl.ACTION_LOCAL_BIND);
        IBinder binder = mServiceTestRule.bindService(intent);
        mService = ((InCallServiceImpl.LocalBinder) binder).getService();
    }

    @Test
    public void onCallAdded_callsListener() {
        mService.addListener(mInCallModel);
        mService.onCallAdded(mCall);

        verify(mInCallModel).onCallAdded(mCall);
    }

    @Test
    public void onCallRemoved_callsListener() {
        mService.addListener(mInCallModel);
        mService.onCallRemoved(mCall);

        verify(mInCallModel).onCallRemoved(mCall);
    }

    @Test
    public void onCallAudioStateChanged_callsListeners() {
        CallAudioState callAudioState = new CallAudioState(false,
                CallAudioState.ROUTE_WIRED_OR_EARPIECE, CallAudioState.ROUTE_WIRED_OR_EARPIECE);
        mService.addListener(mInCallModel);
        mService.onCallAudioStateChanged(callAudioState);

        verify(mInCallModel).onCallAudioStateChanged(callAudioState);
    }
}
