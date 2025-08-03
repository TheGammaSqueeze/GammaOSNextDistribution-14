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

import android.app.Instrumentation;
import android.os.Bundle;
import androidx.test.runner.AndroidJUnit4;
import com.android.helpers.PinnerHelper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Android Unit tests for {@link PinnerSnapshotListener}.
 *
 * To run:
 * atest CollectorDeviceLibTest:android.device.collectors.PinnerSnapshotListenerTest
 */
@RunWith(AndroidJUnit4.class)
public class PinnerSnapshotListenerTest {
    @Mock
    private Instrumentation mInstrumentation;
    @Mock
    private PinnerHelper mPinnerHelper;

    private PinnerSnapshotListener mListener;
    private static final Description RUN_DESC = Description.createSuiteDescription("run");

    private static final String VALID_OUTPUT_DIR = "sdcard/abc";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    private PinnerSnapshotListener initListener(Bundle b) {
        PinnerSnapshotListener listener = new PinnerSnapshotListener(b, mPinnerHelper);
        listener.setInstrumentation(mInstrumentation);
        return listener;
    }

    @Test
    public void testHelperReceivesCustomDirectory() throws Exception {
        Bundle b = new Bundle();
        mListener = initListener(b);
        mListener.testRunStarted(RUN_DESC);
    }
}
