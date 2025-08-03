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

package android.platform.test.flag.util;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FlagTest {
    @Test
    public void createFlag_legacyFlag() {
        String legacyFlag = "namespace/flag";
        Flag flag = Flag.createFlag(legacyFlag);

        assertEquals("namespace", flag.namespace());
        assertEquals("flag", flag.fullFlagName());
        assertEquals("flag", flag.simpleFlagName());
        assertNull(flag.packageName());
        assertNull(flag.flagsClassName());
    }

    @Test
    public void createFlag_aconfigFlagHasNoPackageName_throwException() {
        String aconfigFlagWithNameSpace = "my_flag";
        assertThrows(
                IllegalArgumentException.class, () -> Flag.createFlag(aconfigFlagWithNameSpace));
    }

    @Test
    public void createFlag_aconfigFlagWithoutRepackaging() {
        String aconfigFlagWithoutNamespace = "android.platform.test.flag.junit.flag1";
        Flag flag = Flag.createFlag(aconfigFlagWithoutNamespace);

        assertNull(flag.namespace());
        assertEquals("android.platform.test.flag.junit.flag1", flag.fullFlagName());
        assertEquals("flag1", flag.simpleFlagName());
        assertEquals("android.platform.test.flag.junit", flag.packageName());
        assertEquals("android.platform.test.flag.junit.Flags", flag.flagsClassName());
    }

    @Test
    public void createFlag_aconfigFlagWithRepackaging() {
        String aconfigFlagWithoutNamespace = "android.myflag.flag1";
        Flag flag = Flag.createFlag(aconfigFlagWithoutNamespace);

        assertNull(flag.namespace());
        assertEquals("android.myflag.flag1", flag.fullFlagName());
        assertEquals("flag1", flag.simpleFlagName());
        assertEquals("android.myflag", flag.packageName());
        assertEquals(
                "com.android.internal.hidden_from_bootclasspath.android.myflag.Flags",
                flag.flagsClassName());
    }
}
