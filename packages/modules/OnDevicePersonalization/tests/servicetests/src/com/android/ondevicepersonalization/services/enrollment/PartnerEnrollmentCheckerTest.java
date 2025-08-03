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

package com.android.ondevicepersonalization.services.enrollment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.modules.utils.testing.TestableDeviceConfig;
import com.android.ondevicepersonalization.services.PhFlagsTestUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PartnerEnrollmentCheckerTest {
    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule mDeviceConfigRule =
            new TestableDeviceConfig.TestableDeviceConfigRule();

    @Before
    public void setup() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
    }

    @Test
    public void testIsCallerAppEnrolled() {
        PhFlagsTestUtil.setCallerAppAllowList("app1,app2,app3");
        assertTrue(PartnerEnrollmentChecker.isCallerAppEnrolled("app1"));
        assertFalse(PartnerEnrollmentChecker.isCallerAppEnrolled("app"));
        assertFalse(PartnerEnrollmentChecker.isCallerAppEnrolled("app4"));
        assertFalse(PartnerEnrollmentChecker.isCallerAppEnrolled(""));
        assertFalse(PartnerEnrollmentChecker.isCallerAppEnrolled(null));

        PhFlagsTestUtil.setCallerAppAllowList("*");
        assertTrue(PartnerEnrollmentChecker.isCallerAppEnrolled("random"));
        assertTrue(PartnerEnrollmentChecker.isCallerAppEnrolled(""));
        assertTrue(PartnerEnrollmentChecker.isCallerAppEnrolled(null));

        PhFlagsTestUtil.setCallerAppAllowList("");
        assertFalse(PartnerEnrollmentChecker.isCallerAppEnrolled("random"));
        assertFalse(PartnerEnrollmentChecker.isCallerAppEnrolled(""));
        assertFalse(PartnerEnrollmentChecker.isCallerAppEnrolled(null));
    }

    @Test
    public void testIsIsolatedServiceEnrolled() {
        PhFlagsTestUtil.setIsolatedServiceAllowList("svc1,svc2,svc3");
        assertTrue(PartnerEnrollmentChecker.isIsolatedServiceEnrolled("svc1"));
        assertFalse(PartnerEnrollmentChecker.isIsolatedServiceEnrolled("svc"));
        assertFalse(PartnerEnrollmentChecker.isIsolatedServiceEnrolled("svc4"));
        assertFalse(PartnerEnrollmentChecker.isIsolatedServiceEnrolled(""));
        assertFalse(PartnerEnrollmentChecker.isIsolatedServiceEnrolled(null));

        PhFlagsTestUtil.setIsolatedServiceAllowList("*");
        assertTrue(PartnerEnrollmentChecker.isIsolatedServiceEnrolled("random"));
        assertTrue(PartnerEnrollmentChecker.isIsolatedServiceEnrolled(""));
        assertTrue(PartnerEnrollmentChecker.isIsolatedServiceEnrolled(null));

        PhFlagsTestUtil.setIsolatedServiceAllowList("");
        assertFalse(PartnerEnrollmentChecker.isIsolatedServiceEnrolled("random"));
        assertFalse(PartnerEnrollmentChecker.isIsolatedServiceEnrolled(""));
        assertFalse(PartnerEnrollmentChecker.isIsolatedServiceEnrolled(null));
    }

}
