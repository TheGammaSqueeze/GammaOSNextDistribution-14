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

package com.android.layoutlib.bridge.android;

import com.android.layoutlib.bridge.Bridge;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DynamicRenderResourcesTest extends RenderTestBase {
    @BeforeClass
    public static void setUp() {
        Bridge.prepareThread();
    }

    @Test
    public void createDynamicTheme() {
        Map<String, Integer> dynamicColorMap = DynamicRenderResources.createDynamicColorMap(
                "/com/android/layoutlib/testdata/wallpaper1.webp", false);
        assertNotNull(dynamicColorMap);
        assertEquals(-1, (int)dynamicColorMap.get("system_accent1_0"));
        assertEquals(-4632, (int)dynamicColorMap.get("system_accent1_50"));
        assertEquals(-1403268, (int)dynamicColorMap.get("system_accent1_300"));
        assertEquals(-11198451, (int)dynamicColorMap.get("system_accent1_800"));
        assertEquals(-1, (int)dynamicColorMap.get("system_accent2_0"));
        assertEquals(-4632, (int)dynamicColorMap.get("system_accent2_50"));
        assertEquals(-3497321, (int)dynamicColorMap.get("system_accent2_300"));
        assertEquals(-12309982, (int)dynamicColorMap.get("system_accent2_800"));
        assertEquals(-1, (int)dynamicColorMap.get("system_accent3_0"));
        assertEquals(-3900, (int)dynamicColorMap.get("system_accent3_50"));
        assertEquals(-4478092, (int)dynamicColorMap.get("system_accent3_300"));
        assertEquals(-12963835, (int)dynamicColorMap.get("system_accent3_800"));
        assertEquals(-1, (int)dynamicColorMap.get("system_neutral1_0"));
        assertEquals(-4632, (int)dynamicColorMap.get("system_neutral1_50"));
        assertEquals(-4675421, (int)dynamicColorMap.get("system_neutral1_300"));
        assertEquals(-13029845, (int)dynamicColorMap.get("system_neutral1_800"));
        assertEquals(-1, (int)dynamicColorMap.get("system_neutral2_0"));
        assertEquals(-4632, (int)dynamicColorMap.get("system_neutral2_50"));
        assertEquals(-4413535, (int)dynamicColorMap.get("system_neutral2_300"));
        assertEquals(-12899031, (int)dynamicColorMap.get("system_neutral2_800"));
    }
}
