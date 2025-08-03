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

package com.android.adservices.service.signals.updateprocessors;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

public class UpdateProcessorSelectorTest {

    public UpdateProcessorSelector mUpdateProcessorSelector = new UpdateProcessorSelector();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Test
    public void testInvalid() {
        assertThrows(
                "Selector should throw an exception when given an invalid processor name",
                IllegalArgumentException.class,
                () -> mUpdateProcessorSelector.getUpdateProcessor("Not a valid command"));
    }

    @Test
    public void testValidCommands() {
        UpdateProcessor[] processors = {
            new Append(), new Put(), new PutIfNotPresent(), new Remove(), new UpdateEncoder()
        };
        for (int i = 0; i < processors.length; i++) {
            UpdateProcessor fetchedProcessor =
                    mUpdateProcessorSelector.getUpdateProcessor(processors[i].getName());
            assertTrue(processors[i].getClass().isInstance(fetchedProcessor));
        }
    }
}
