/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.device.collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import android.app.Instrumentation;
import android.os.Bundle;

import androidx.test.runner.AndroidJUnit4;

import com.android.helpers.TimeInStateHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.mockito.Mock;

/**
 * Android Unit tests for {@link TimeInStateListener}.
 *
 * <p>To run: atest CollectorDeviceLibPlatformTest:android.device.collectors.TimeInStateListenerTest
 */
@RunWith(AndroidJUnit4.class)
public class TimeInStateListenerTest {

    @Mock private TimeInStateHelper mTimeInStateHelper;
    @Mock private Instrumentation mInstrumentation;

    private Description mRunDesc;

    @Before
    public void setup() {
        initMocks(this);
        mRunDesc = Description.createSuiteDescription("run");
    }

    @Test
    public void testListener_noSource() throws Exception {
        TimeInStateListener listener = initListener(new Bundle(), mTimeInStateHelper);
        listener.testRunStarted(mRunDesc);
        verify(mTimeInStateHelper, never()).setUp(any());
    }

    @Test
    public void testListener_withSources() throws Exception {
        Bundle bundle = new Bundle();
        bundle.putString(
                TimeInStateListener.ARG_KEY,
                String.join(TimeInStateListener.ARG_SEPARATOR, "key1@temp1", "key2@temp2"));
        TimeInStateListener listener = initListener(bundle, mTimeInStateHelper);
        listener.testRunStarted(mRunDesc);
        verify(mTimeInStateHelper).setUp("key1@temp1", "key2@temp2");
    }

    private TimeInStateListener initListener(Bundle bundle, TimeInStateHelper helper) {
        TimeInStateListener listener = new TimeInStateListener(bundle, helper);
        listener.setInstrumentation(mInstrumentation);
        return listener;
    }
}
