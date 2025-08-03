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

package libcore.javax.security.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.security.AllPermission;
import javax.security.auth.AuthPermission;
import javax.security.auth.PrivateCredentialPermission;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Android does not support {@link java.lang.SecurityManager} and has its implementations stubbed.
 * See comments in {@link java.lang.SecurityManager} for details.
 *
 * This test is added primarily for code coverage.
 */
@RunWith(JUnit4.class)
public class PrivateCredentialPermissionTest {

    private PrivateCredentialPermission permission;

    @Before
    public void setUp() {
        permission = new PrivateCredentialPermission("name", "actions");
    }

    @Test
    public void constructorWithStringAndString() {
        assertNotNull(permission);
        assertEquals("", permission.getName());
        assertNull(permission.getActions());
    }

    @Test
    public void getActions() {
        assertNull(permission.getActions());
    }

    @Test
    public void getCredentialClass() {
        assertNull(permission.getCredentialClass());
    }

    @Test
    public void getPrincipals() {
        assertNull(permission.getPrincipals());
    }

    @Test
    public void implies() {
        assertTrue(permission.implies(new AuthPermission("")));
        assertTrue(permission.implies(new AllPermission()));
    }
}
