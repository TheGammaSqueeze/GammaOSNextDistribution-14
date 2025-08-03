/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tradefed.testtype.suite.params;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Map;

/** Unit tests for {@link ModuleParametersHelper}. */
@RunWith(JUnit4.class)
public class ModuleParametersHelperTest {

    /**
     * Check that each value in {@link ModuleParameters} which is not a group parameter has a
     * handler associated.
     */
    @Test
    public void testHandlersExists() {
        for (ModuleParameters param : ModuleParameters.values()) {
            if (isGroupParameter(param)) {
                continue;
            }

            Map<ModuleParameters, IModuleParameterHandler> handler =
                    ModuleParametersHelper.resolveParam(param, /* withOptional= */ true);
            assertNotNull(handler);
        }
    }

    private boolean isGroupParameter(ModuleParameters param) {
        Map<ModuleParameters, IModuleParameterHandler> resolvedParams =
                ModuleParametersHelper.resolveParam(param, /* withOptional= */ true);

        if (resolvedParams.size() != 1) {
            return true;
        }

        return resolvedParams.keySet().iterator().next() != param;
    }

    @Test
    public void resolveParam_notGroupParam_returnsSetOfSameParam() {
        Map<ModuleParameters, IModuleParameterHandler> resolvedParams =
                ModuleParametersHelper.resolveParam(
                        ModuleParameters.INSTANT_APP, /* withOptional= */ true);

        assertEquals(resolvedParams.size(), 1);
        assertEquals(resolvedParams.keySet().iterator().next(), ModuleParameters.INSTANT_APP);
    }

    @Test
    public void resolveParam_groupParam_returnsSetOfMultipleParams() {
        Map<ModuleParameters, IModuleParameterHandler> resolvedParams =
                ModuleParametersHelper.resolveParam(
                        ModuleParameters.MULTIUSER, /* withOptional= */ true);

        assertNotEquals(resolvedParams.size(), 1);
        assertFalse(resolvedParams.keySet().contains(ModuleParameters.MULTIUSER));
    }

    @Test
    public void resolveParamString_notGroupParam_returnsSetOfSameParam() {
        Map<ModuleParameters, IModuleParameterHandler> resolvedParams =
                ModuleParametersHelper.resolveParam(
                        ModuleParameters.INSTANT_APP, /* withOptional= */ true);

        assertEquals(resolvedParams.size(), 1);
        assertEquals(resolvedParams.keySet().iterator().next(), ModuleParameters.INSTANT_APP);
    }

    @Test
    public void resolveParamString_groupParam_returnsSetOfMultipleParams() {
        Map<ModuleParameters, IModuleParameterHandler> resolvedParams =
                ModuleParametersHelper.resolveParam(
                        ModuleParameters.MULTIUSER, /* withOptional= */ true);

        assertNotEquals(resolvedParams.size(), 1);
        assertFalse(resolvedParams.keySet().contains(ModuleParameters.MULTIUSER));
    }
}
