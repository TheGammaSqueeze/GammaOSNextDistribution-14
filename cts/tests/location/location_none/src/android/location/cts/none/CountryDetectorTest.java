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

package android.location.cts.none;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;

import android.content.Context;
import android.location.Country;
import android.location.CountryDetector;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class CountryDetectorTest {
    @Mock Consumer<Country> mMockCountryCallback;
    private CountryDetector mCountryDetector;

    private final Executor mTestExecutor = Runnable::run;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Context context = InstrumentationRegistry.getTargetContext();
        mCountryDetector = context.getSystemService(CountryDetector.class);
        assertNotNull(mCountryDetector);
    }

    // Validate registerCountryDetectorCallback() and unregisterCountryDetectorCallback
    @Test
    public void testRegisterCountryDetectorCallback() {
        mCountryDetector.registerCountryDetectorCallback(mTestExecutor, mMockCountryCallback);

        // Validate for Callback on Registration
        ArgumentCaptor<Country> callbackCountry = ArgumentCaptor.forClass(Country.class);
        Mockito.verify(mMockCountryCallback, timeout(100)).accept(callbackCountry.capture());
        Country countryCallback = callbackCountry.getValue();
        assertNotNull(countryCallback);
        assertNotNull(countryCallback.getCountryCode());
        assertNotEquals(-1, countryCallback.getSource());

        // Callback is Unregistered , cannot receive callback from now
        Mockito.clearInvocations(mMockCountryCallback);
        mCountryDetector.unregisterCountryDetectorCallback(mMockCountryCallback);
        Mockito.verify(mMockCountryCallback, never()).accept(any(Country.class));
    }
}
