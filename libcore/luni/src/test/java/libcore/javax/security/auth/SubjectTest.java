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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.security.Principal;
import java.util.Set;
import javax.security.auth.Subject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import sun.security.x509.X500Name;

/**
 * Android does not support {@link java.lang.SecurityManager} and has its implementations stubbed.
 * See comments in {@link java.lang.SecurityManager} for details.
 *
 * This test is added primarily for code coverage.
 */
@RunWith(JUnit4.class)
public class SubjectTest {

    private Set<Principal> set;
    private Subject subject;

    @Before
    public void setUp() {
        set = Set.of(new PrincipalImpl());
        subject = new Subject(true, set, set, set);
    }

    @Test
    public void getPrincipals() {
        assertEquals(set, subject.getPrincipals());

        assertEquals(set, subject.getPrincipals(PrincipalImpl.class));
        assertEquals(set, subject.getPrincipals(Principal.class));
        // PrincipalImpl is not a subclass of X500Name.
        assertEquals(0, subject.getPrincipals(X500Name.class).size());
    }

    @Test
    public void getPrivateCredentials() {
        assertEquals(set, subject.getPrivateCredentials());

        assertEquals(set, subject.getPrivateCredentials(PrincipalImpl.class));
        assertEquals(set, subject.getPrivateCredentials(Principal.class));
        // PrincipalImpl is not a subclass of X500Name.
        assertEquals(0, subject.getPrivateCredentials(X500Name.class).size());
    }

    @Test
    public void getPublicCredentials() {
        assertEquals(set, subject.getPublicCredentials());

        assertEquals(set, subject.getPublicCredentials(PrincipalImpl.class));
        assertEquals(set, subject.getPublicCredentials(Principal.class));
        // PrincipalImpl is not a subclass of X500Name.
        assertEquals(0, subject.getPublicCredentials(X500Name.class).size());
    }

    @Test
    public void isReadOnly() {
        assertTrue(subject.isReadOnly());

        subject = new Subject(false, set, set, set);
        assertFalse(subject.isReadOnly());
    }

    @Test
    public void setReadOnly() {
        subject = new Subject(false, set, set, set);
        assertFalse(subject.isReadOnly());

        subject.setReadOnly();
        assertTrue(subject.isReadOnly());
    }

    private static final class PrincipalImpl implements Principal {

        @Override
        public String getName() {
            return "";
        }
    }
}
