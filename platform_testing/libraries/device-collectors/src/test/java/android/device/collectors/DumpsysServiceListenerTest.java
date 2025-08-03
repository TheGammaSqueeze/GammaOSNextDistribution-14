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

import com.android.helpers.DumpsysServiceHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.mockito.Mock;

/**
 * Android Unit tests for {@link DumpsysServiceListener}.
 *
 * <p>To run: atest CollectorDeviceLibTest:android.device.collectors.DumpsysServiceListenerTest
 */
@RunWith(AndroidJUnit4.class)
public class DumpsysServiceListenerTest {

    @Mock private DumpsysServiceHelper mDumpsysServiceHelper;
    @Mock private Instrumentation mInstrumentation;

    private Description mRunDesc;

    @Before
    public void setup() {
        initMocks(this);
        mRunDesc = Description.createSuiteDescription("run");
    }

    @Test
    public void testListener_noProcessNames() throws Exception {
        DumpsysServiceListener listener = initListener(new Bundle(), mDumpsysServiceHelper);
        listener.testRunStarted(mRunDesc);
        verify(mDumpsysServiceHelper, never()).setUp(any());
    }

    @Test
    public void testListener_withProcessNames() throws Exception {
        Bundle bundle = new Bundle();
        bundle.putString(
                DumpsysServiceListener.SERVICE_NAMES_KEY,
                String.join(DumpsysServiceListener.SERVICE_SEPARATOR, "service1", "service2"));
        DumpsysServiceListener listener = initListener(bundle, mDumpsysServiceHelper);
        listener.testRunStarted(mRunDesc);
        verify(mDumpsysServiceHelper).setUp("service1", "service2");
    }

    private DumpsysServiceListener initListener(Bundle bundle, DumpsysServiceHelper helper) {
        DumpsysServiceListener listener = new DumpsysServiceListener(bundle, helper);
        listener.setInstrumentation(mInstrumentation);
        return listener;
    }
}
