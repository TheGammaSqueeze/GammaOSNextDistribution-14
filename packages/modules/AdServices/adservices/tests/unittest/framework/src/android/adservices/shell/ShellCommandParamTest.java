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

package android.adservices.shell;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public final class ShellCommandParamTest extends AdServicesUnitTestCase {

    @Test
    public void testEqualsHashCode() {
        String[] commandArgs = new String[] {"Cmd", "arg"};
        ShellCommandParam param1 = new ShellCommandParam(commandArgs);
        ShellCommandParam param2 = new ShellCommandParam(commandArgs);

        expectEquals(param1, param2);

        ShellCommandParam param3 = new ShellCommandParam(new String[] {"Cmd3", "arg"});

        expectNotEquals(param1, param3);
        expectNotEquals(param2, param3);
    }

    private void expectEquals(ShellCommandParam param1, ShellCommandParam param2) {
        expect.withMessage("equals()").that(param1).isEqualTo(param2);
        expect.withMessage("equals()").that(param2).isEqualTo(param1);
        expect.withMessage("hashcode(%s, %s)", param1, param2)
                .that(param1.hashCode())
                .isEqualTo(param2.hashCode());
    }

    private void expectNotEquals(ShellCommandParam param1, ShellCommandParam param2) {
        expect.withMessage("equals()").that(param1).isNotEqualTo(param2);
        expect.withMessage("equals()").that(param2).isNotEqualTo(param1);
        expect.withMessage("hashcode(%s, %s)", param1, param2)
                .that(param1.hashCode())
                .isNotEqualTo(param2.hashCode());
    }
}
