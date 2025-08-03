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

package android.app.sdksandbox.testutils;

import android.provider.DeviceConfig;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DeviceConfigUtils {
    private final ConfigListener mConfigListener;
    private CountDownLatch mLatch;
    private final String mNamespace;

    public DeviceConfigUtils(ConfigListener configListener, String namespace) {
        mConfigListener = configListener;
        mNamespace = namespace;
    }

    public void deleteProperty(String property) throws Exception {
        if (DeviceConfig.getProperty(mNamespace, property) == null) {
            return;
        }
        mLatch = new CountDownLatch(1);
        mConfigListener.setLatchForProperty(mLatch, property);
        DeviceConfig.deleteProperty(mNamespace, property);
        mLatch.await(5, TimeUnit.SECONDS);
    }

    public void setProperty(String property, String value) throws Exception {
        mLatch = new CountDownLatch(1);
        mConfigListener.setLatchForProperty(mLatch, property);
        DeviceConfig.setProperty(mNamespace, property, value, /*makeDefault= */ false);
        mLatch.await(5, TimeUnit.SECONDS);
    }

    public void resetToInitialValue(String property, String value) throws Exception {
        if (value != null) {
            setProperty(property, value);
        } else {
            deleteProperty(property);
        }
    }
}
