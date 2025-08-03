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

package com.android.tradefed.suite.checker.baseline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.android.tradefed.device.ITestDevice;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DeviceBaselineSetter}. */
@RunWith(JUnit4.class)
public final class DeviceBaselineSetterTest {
    private static final String SETTING_NAME = "test";

    private static class Setter extends DeviceBaselineSetter {
        public Setter(JSONObject object, String name) throws JSONException {
            super(object, name);
        }

        @Override
        public boolean setBaseline(ITestDevice mDevice) {
            return true;
        }
    }

    /** Test that the experimental flag is set to false when the input filed is null or false. */
    @Test
    public void isExperimental_withoutTrueExperimentalField_returnFalse() throws Exception {
        assertFalse(new Setter(new JSONObject("{}"), SETTING_NAME).isExperimental());
        assertFalse(
                new Setter(new JSONObject("{\"experimental\": false}"), SETTING_NAME)
                        .isExperimental());
    }

    /** Test that the experimental flag is set to true when the input filed is true. */
    @Test
    public void isExperimental_withTrueExperimentalField_returnTrue() throws Exception {
        assertTrue(
                new Setter(new JSONObject("{\"experimental\": true}"), SETTING_NAME)
                        .isExperimental());
    }

    /** Test that the minimal api level is set to the value of input filed. */
    @Test
    public void getMinimalApiLevel_withApiTestField_returnApiLevel() throws Exception {
        assertEquals(
                new Setter(new JSONObject("{\"min_api_level\": \"28\"}"), SETTING_NAME)
                        .getMinimalApiLevel(),
                28);
    }

    /** Test that the api level is set to default value when the input field is null. */
    @Test
    public void getMinimalApiLevel_withoutApiTestField_returnDefaultApiLevel() throws Exception {
        assertEquals(new Setter(new JSONObject("{}"), SETTING_NAME).getMinimalApiLevel(), 30);
    }
}
