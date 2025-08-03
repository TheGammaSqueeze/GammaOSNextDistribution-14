/*
 * Copyright (C) 2021 The Android Open Source Project
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

package libcore.java.lang.reflect;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.reflect.ReflectPermission;

@RunWith(JUnit4.class)
public class ReflectPermissionTest {
    private static final String NAME = "message";
    private static final String ACTIONS = "No action";

    private void checkFieldsAreEmpty(ReflectPermission permission) {
        // The super-class of ReflectPermission overrides the name and actions provided to
        // the ReflectPermission constructors with empty strings.
        //
        // This is legacy code that is not intended for use.
        assertTrue(permission.getName().isEmpty());
        assertTrue(permission.getActions().isEmpty());
    }

    @Test
    public void reflectPermissionWithName() {
        ReflectPermission permission = new ReflectPermission(NAME);
        checkFieldsAreEmpty(permission);
    }

    @Test
    public void reflectPermissionWithNameAndActions() {
        ReflectPermission permission = new ReflectPermission(NAME, ACTIONS);
        checkFieldsAreEmpty(permission);
    }
}
