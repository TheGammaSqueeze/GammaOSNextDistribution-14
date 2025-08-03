/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.car.audio;

import static org.junit.Assert.assertThrows;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.car.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.InputStream;
import java.util.MissingResourceException;

@RunWith(MockitoJUnitRunner.class)
public class CarAudioFadeConfigurationHelperTest extends AbstractExtendedMockitoTestCase {

    private static final String TEST_RELAXED_FADING = "relaxed fading";
    private static final String TEST_AGGRESSIVE_FADING = "aggressive fading";
    private static final String TEST_DISABLED_FADING = "disabled fading";
    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void constructor() throws Exception {
        try (InputStream inputStream = mContext.getResources().openRawResource(
                R.raw.car_audio_fade_configuration)) {

            CarAudioFadeConfigurationHelper helper =
                    new CarAudioFadeConfigurationHelper(inputStream);

            expectWithMessage("Car Audio Fade configuration size").that(helper.getAllConfigNames())
                    .hasSize(3);
            expectWithMessage("Relaxed fading config")
                    .that(helper.isConfigAvailable(TEST_RELAXED_FADING)).isTrue();
            expectWithMessage("Aggressive fading config")
                    .that(helper.isConfigAvailable(TEST_AGGRESSIVE_FADING)).isTrue();
            expectWithMessage("Disabled fading config")
                    .that(helper.isConfigAvailable(TEST_DISABLED_FADING)).isTrue();
        }
    }

    @Test
    public void constructor_withNoConfigs_fails() throws Exception {
        try (InputStream inputStream = mContext.getResources().openRawResource(
                R.raw.car_audio_fade_configuration_withNoConfigs)) {

            MissingResourceException thrown = assertThrows(MissingResourceException.class,
                    () -> new CarAudioFadeConfigurationHelper(inputStream));

            expectWithMessage("Car audio fade config with no configs exception").that(thrown)
                    .hasMessageThat().contains("is missing from configuration");
        }
    }

    @Test
    public void constructor_configWithNoName_fails() throws Exception {
        try (InputStream inputStream = mContext.getResources().openRawResource(
                R.raw.car_audio_fade_configuration_withNullConfigName)) {

            NullPointerException thrown = assertThrows(NullPointerException.class,
                    () -> new CarAudioFadeConfigurationHelper(inputStream));

            expectWithMessage("Car audio fade config with no name exception").that(thrown)
                    .hasMessageThat().contains("can not be null");
        }
    }

    @Test
    public void constructor_configWithEmptyName_fails() throws Exception {
        try (InputStream inputStream = mContext.getResources().openRawResource(
                R.raw.car_audio_fade_configuration_withEmptyConfigName)) {

            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> new CarAudioFadeConfigurationHelper(inputStream));

            expectWithMessage("Car audio fade config with empty name exception").that(thrown)
                    .hasMessageThat().contains("can not be empty");
        }
    }
}
