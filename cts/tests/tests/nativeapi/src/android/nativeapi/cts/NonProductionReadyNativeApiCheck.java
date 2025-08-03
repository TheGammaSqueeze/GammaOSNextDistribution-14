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

package android.nativeapi.cts;

import com.android.compatibility.common.util.BusinessLogicTestCase;
import com.android.compatibility.common.util.CddTest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class NonProductionReadyNativeApiCheck extends BusinessLogicTestCase {
    static {
        System.loadLibrary("native-api-check-jni");
    }

    /**
     * Checks any non production ready Native API is exposed.
     *
     * This method will be called via the BusinessLogic config. The non production ready native API
     * list is also configured via BusinessLogic.
     */
    public void testNonProductionReadyNativeApis(String... nonProductionReadyNativeApis) {
        List<String> exposedNonProductionReadyNativeApis = new ArrayList<>();
        for (String s : checkNonProductionReadyNativeApis(nonProductionReadyNativeApis)) {
            if (!s.isEmpty()) {
                exposedNonProductionReadyNativeApis.add(s);
            }
        }

        Assert.assertTrue(
                String.format(
                        "Native APIs %s are not production ready and can not be exposed",
                        exposedNonProductionReadyNativeApis),
                exposedNonProductionReadyNativeApis.isEmpty());
    }

    @Test
    @CddTest(requirements = {"3.3.1/C-0-8"})
    public void checkNonProductionReadyNativeApis_shouldNotExposed() {
    }

    private native String[] checkNonProductionReadyNativeApis(String[] nonProductionApis);
}
