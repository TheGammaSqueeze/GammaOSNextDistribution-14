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

package android.healthconnect.cts.utils;

import static android.healthconnect.cts.utils.TestUtils.getDeviceConfigValue;
import static android.healthconnect.cts.utils.TestUtils.setDeviceConfigValue;

import org.junit.rules.ExternalResource;

/** Sets a device config value for the duration of the test. */
public class DeviceConfigRule extends ExternalResource {
    private final String mKey;
    private final String mValue;
    private String mOriginalValue;

    public DeviceConfigRule(String key, String value) {
        mKey = key;
        mValue = value;
    }

    @Override
    protected void before() throws Throwable {
        mOriginalValue = getDeviceConfigValue(mKey);
        setDeviceConfigValue(mKey, mValue);
    }

    @Override
    protected void after() {
        setDeviceConfigValue(mKey, mOriginalValue);
    }
}
