/*
 * Copyright (C) 2008 The Android Open Source Project
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

package libcore.java.io;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.FilePermission;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class FilePermissionTest {

    @Test
    public void testImplies() {
        FilePermission permission = new FilePermission("none", null);
        // Implementation is stubbed in Android
        assertTrue(permission.implies(null));
    }

    @Test
    public void testGetActions() {
        FilePermission permission = new FilePermission("none", null);
        // Implementation is stubbed in Android
        assertNull(permission.getActions());
    }
}
