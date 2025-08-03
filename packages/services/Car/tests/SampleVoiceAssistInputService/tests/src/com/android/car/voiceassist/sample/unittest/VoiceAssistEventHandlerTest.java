/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.car.voiceassistinput.sample.unittest;

import static android.car.CarOccupantZoneManager.DISPLAY_TYPE_MAIN;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.CarOccupantZoneManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

import androidx.test.runner.AndroidJUnit4;

import com.android.car.voiceassistinput.sample.R;
import com.android.car.voiceassistinput.sample.SampleVoiceAssistInputService;
import com.android.car.voiceassistinput.sample.VoiceAssistEventHandler;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class VoiceAssistEventHandlerTest {

    // Some arbitrary display type
    private static final int SOME_DISPLAY_TYPE = CarOccupantZoneManager.DISPLAY_TYPE_MAIN;

    // Some arbitrary user TOS redirect activity URI
    private static final String SOME_USER_ACTIVITY_INTENT =
            "intent:#Intent;action=com.example.SAMPLE_ACTION;end";

    // Intent action of the arbitrary user TOS redirect activity URI
    private static final String SOME_USER_ACTIVITY_INTENT_ACTION = "com.example.SAMPLE_ACTION";


    @Mock
    private Context mContext;

    @Mock
    private SampleVoiceAssistInputService mService;

    @Mock
    private CarOccupantZoneManager mCarOccupantZoneManager;
    private VoiceAssistEventHandler mEventHandler;


    private static final List<KeyEvent> mEvents = new ArrayList<>() {
    };

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mContext.getString(R.string.config_userTosActivityIntentUri)).thenReturn(
                SOME_USER_ACTIVITY_INTENT);
        when(mCarOccupantZoneManager.getDisplayIdForDriver(eq(DISPLAY_TYPE_MAIN))).thenReturn(0);

        mEventHandler = new VoiceAssistEventHandler(mContext, mService, mCarOccupantZoneManager);
    }

    @Test
    public void testOnKeyEvents_startsUserTosRedirectActivity() {
        // Arrange
        mEvents.add(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOICE_ASSIST));

        // Act
        mEventHandler.onKeyEvents(SOME_DISPLAY_TYPE, mEvents);

        // Assert
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mService).startActivity(intentCaptor.capture(), any(Bundle.class));
        assertThat(intentCaptor.getValue().getAction()).isEqualTo(SOME_USER_ACTIVITY_INTENT_ACTION);
    }

}

