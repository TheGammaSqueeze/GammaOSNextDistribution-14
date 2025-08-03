/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.telecom.tests;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertNull;

import android.app.UiModeManager;

import com.android.server.telecom.CarModeTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CarModeTrackerTest extends TelecomTestCase {
    private static final String CAR_MODE_APP1_PACKAGE_NAME = "com.android.carmode1";
    private static final String CAR_MODE_APP2_PACKAGE_NAME = "com.android.carmode2";
    private static final String CAR_MODE_APP3_PACKAGE_NAME = "com.android.carmode3";
    private static final String CAR_MODE_APP4_PACKAGE_NAME = "com.android.carmode4";

    private CarModeTracker mCarModeTracker;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        mCarModeTracker = new CarModeTracker();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Basic test; see if we can enter car mode.
     */
    @Test
    public void testEnterCarModeBasic() {
        // At start, no car mode apps.
        assertNull(mCarModeTracker.getCurrentCarModePackage());
        assertEquals(0, mCarModeTracker.getCarModeApps().size());

        mCarModeTracker.handleEnterCarMode(100, CAR_MODE_APP1_PACKAGE_NAME);

        // We should be tracking our car mode app.
        assertEquals(1, mCarModeTracker.getCarModeApps().size());
        assertTrue(mCarModeTracker.isInCarMode());
    }

    /**
     * Ensure when we enter car mode with a package which has already entered car mode, the extra
     * request is ignored.
     */
    @Test
    public void testEnterCarModeAlreadyInCarMode() {
        testEnterCarModeBasic();

        mCarModeTracker.handleEnterCarMode(100, CAR_MODE_APP1_PACKAGE_NAME);
        // Still should just be one.
        assertEquals(CAR_MODE_APP1_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());
        assertEquals(1, mCarModeTracker.getCarModeApps().size());
    }

    /**
     * Ensure when we enter car mode with a package which has already entered car mode, the extra
     * request is ignored.
     */
    @Test
    public void testEnterCarModePriorityInUse() {
        testEnterCarModeBasic();

        mCarModeTracker.handleEnterCarMode(100, CAR_MODE_APP2_PACKAGE_NAME);

        // We should be tracking car mode with the first package; the 2nd should have been ignored.
        assertEquals(1, mCarModeTracker.getCarModeApps().size());
        assertEquals(CAR_MODE_APP1_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());
        assertEquals(CAR_MODE_APP1_PACKAGE_NAME, mCarModeTracker.getCarModeApps().get(0));
    }

    /**
     * Ensure that we don't keep a package around after it's been removed from the device
     */
    @Test
    public void testForceExitCarMode() {
        testEnterCarModeBasic();
        mCarModeTracker.forceRemove(CAR_MODE_APP1_PACKAGE_NAME);
        assertFalse(mCarModeTracker.isInCarMode());
        assertNull(mCarModeTracker.getCurrentCarModePackage());
    }

    /**
     * Verifies only the first app at the default priority gets tracked.
     */
    @Test
    public void testEnterCarModeDefaultPriority() {
        assertEquals(0, mCarModeTracker.getCarModeApps().size());

        mCarModeTracker.handleEnterCarMode(UiModeManager.DEFAULT_PRIORITY,
                CAR_MODE_APP1_PACKAGE_NAME);
        assertEquals(1, mCarModeTracker.getCarModeApps().size());
        assertEquals(CAR_MODE_APP1_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());

        mCarModeTracker.handleEnterCarMode(UiModeManager.DEFAULT_PRIORITY,
                CAR_MODE_APP2_PACKAGE_NAME);
        assertEquals(1, mCarModeTracker.getCarModeApps().size());
        assertEquals(CAR_MODE_APP1_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());
    }

    /**
     * Verifies that multiple apps can enter car mode at the default priority and a higher priority.
     * Verifies that the priority ordering is retained as expected.
     */
    @Test
    public void testEnterCarModeDefaultAndHigherPriority() {
        testEnterCarModeDefaultPriority();

        mCarModeTracker.handleEnterCarMode(100, CAR_MODE_APP3_PACKAGE_NAME);
        assertEquals(2, mCarModeTracker.getCarModeApps().size());
        assertEquals(CAR_MODE_APP3_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());
        assertEquals(CAR_MODE_APP3_PACKAGE_NAME, mCarModeTracker.getCarModeApps().get(0));
        assertEquals(CAR_MODE_APP1_PACKAGE_NAME, mCarModeTracker.getCarModeApps().get(1));

        mCarModeTracker.handleEnterCarMode(50, CAR_MODE_APP4_PACKAGE_NAME);
        assertEquals(3, mCarModeTracker.getCarModeApps().size());
        assertEquals(CAR_MODE_APP3_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());
        assertEquals(CAR_MODE_APP3_PACKAGE_NAME, mCarModeTracker.getCarModeApps().get(0));
        assertEquals(CAR_MODE_APP4_PACKAGE_NAME, mCarModeTracker.getCarModeApps().get(1));
        assertEquals(CAR_MODE_APP1_PACKAGE_NAME, mCarModeTracker.getCarModeApps().get(2));
    }

    /**
     * Verifies entering and exiting car mode at default priority.
     */
    @Test
    public void testEnterExitCarModeDefaultPriority() {
        mCarModeTracker.handleEnterCarMode(UiModeManager.DEFAULT_PRIORITY,
                CAR_MODE_APP1_PACKAGE_NAME);
        assertEquals(CAR_MODE_APP1_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());
        mCarModeTracker.handleExitCarMode(UiModeManager.DEFAULT_PRIORITY,
                CAR_MODE_APP1_PACKAGE_NAME);
        assertNull(mCarModeTracker.getCurrentCarModePackage());
    }

    /**
     * Verifies entering and exiting car mode at default priority.
     * Ensures a different app can exit car mode at the default priority than the one which
     * entered car mode at that priority.
     */
    @Test
    public void testEnterExitCarModeDefaultPriorityDifferentApp() {
        mCarModeTracker.handleEnterCarMode(UiModeManager.DEFAULT_PRIORITY,
                CAR_MODE_APP1_PACKAGE_NAME);
        assertEquals(CAR_MODE_APP1_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());
        mCarModeTracker.handleExitCarMode(UiModeManager.DEFAULT_PRIORITY,
                CAR_MODE_APP2_PACKAGE_NAME);
        assertNull(mCarModeTracker.getCurrentCarModePackage());
    }

    /**
     * Verifies entering and exiting car mode at higher priority.
     */
    @Test
    public void testEnterExitCarModeHigherPriority() {
        mCarModeTracker.handleEnterCarMode(100, CAR_MODE_APP1_PACKAGE_NAME);
        assertEquals(CAR_MODE_APP1_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());
        mCarModeTracker.handleExitCarMode(100, CAR_MODE_APP1_PACKAGE_NAME);
        assertNull(mCarModeTracker.getCurrentCarModePackage());
    }

    /**
     * Verifies entering and exiting car mode at higher priority, except the exiting app differs.
     */
    @Test
    public void testEnterExitCarModeHigherPriorityDifferentApp() {
        mCarModeTracker.handleEnterCarMode(100, CAR_MODE_APP1_PACKAGE_NAME);
        assertEquals(CAR_MODE_APP1_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());
        mCarModeTracker.handleExitCarMode(100, CAR_MODE_APP2_PACKAGE_NAME);
        assertEquals(CAR_MODE_APP1_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());
    }

    /**
     * Verifies entering and exiting car mode at a bunch of priorities with a bunch of apps.
     */
    @Test
    public void testEnterExitCarModeMultiple() {
        mCarModeTracker.handleEnterCarMode(100, CAR_MODE_APP1_PACKAGE_NAME);
        assertEquals(CAR_MODE_APP1_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());
        mCarModeTracker.handleEnterCarMode(50, CAR_MODE_APP2_PACKAGE_NAME);
        assertEquals(CAR_MODE_APP1_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());
        mCarModeTracker.handleEnterCarMode(UiModeManager.DEFAULT_PRIORITY,
                CAR_MODE_APP3_PACKAGE_NAME);
        assertEquals(CAR_MODE_APP1_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());

        mCarModeTracker.handleExitCarMode(100, CAR_MODE_APP1_PACKAGE_NAME);
        assertEquals(CAR_MODE_APP2_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());
        mCarModeTracker.handleExitCarMode(50, CAR_MODE_APP2_PACKAGE_NAME);
        assertEquals(CAR_MODE_APP3_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());
        mCarModeTracker.handleExitCarMode(UiModeManager.DEFAULT_PRIORITY,
                CAR_MODE_APP3_PACKAGE_NAME);
        assertNull(mCarModeTracker.getCurrentCarModePackage());
    }

    /**
     * Verifies that setting automotive projection by itself works.
     */
    @Test
    public void testSetAutomotiveProjectionBasic() {
        mCarModeTracker.handleSetAutomotiveProjection(CAR_MODE_APP1_PACKAGE_NAME);
        assertEquals(CAR_MODE_APP1_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());
        // We should be tracking our car mode app.
        assertEquals(1, mCarModeTracker.getCarModeApps().size());
        assertTrue(mCarModeTracker.isInCarMode());
    }

    /**
     * Verifies that setting automotive projection overrides entering car mode with the highest
     * priority of 0. Also ensures exiting car mode doesn't interfere with the automotive
     * projection being set.
     */
    @Test
    public void testInterleaveCarModeAndProjectionMode() {
        mCarModeTracker.handleEnterCarMode(0, CAR_MODE_APP1_PACKAGE_NAME);
        assertEquals(CAR_MODE_APP1_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());
        assertTrue(mCarModeTracker.isInCarMode());

        mCarModeTracker.handleSetAutomotiveProjection(CAR_MODE_APP2_PACKAGE_NAME);
        assertEquals(CAR_MODE_APP2_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());
        assertTrue(mCarModeTracker.isInCarMode());

        mCarModeTracker.handleExitCarMode(0, CAR_MODE_APP1_PACKAGE_NAME);
        assertEquals(CAR_MODE_APP2_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());
        assertTrue(mCarModeTracker.isInCarMode());

        mCarModeTracker.handleReleaseAutomotiveProjection();
    }

    /**
     * Verifies that if we set automotive projection more than once with the same package, nothing
     * changes.
     */
    @Test
    public void testSetAutomotiveProjectionMultipleTimes() {
        mCarModeTracker.handleSetAutomotiveProjection(CAR_MODE_APP1_PACKAGE_NAME);
        mCarModeTracker.handleSetAutomotiveProjection(CAR_MODE_APP1_PACKAGE_NAME);
        // Should still only have one app.
        assertEquals(1, mCarModeTracker.getCarModeApps().size());
        assertTrue(mCarModeTracker.isInCarMode());
        // It should be the same one.
        assertEquals(CAR_MODE_APP1_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());
    }

    /**
     * Verifies that if we set automotive projection more than once, the new package overrides.
     */
    @Test
    public void testSetAutomotiveProjectionMultipleTimesDifferentPackages() {
        mCarModeTracker.handleSetAutomotiveProjection(CAR_MODE_APP1_PACKAGE_NAME);
        mCarModeTracker.handleSetAutomotiveProjection(CAR_MODE_APP2_PACKAGE_NAME);
        // Should still only have one app.
        assertEquals(1, mCarModeTracker.getCarModeApps().size());
        assertTrue(mCarModeTracker.isInCarMode());
        // It should be the newer one.
        assertEquals(CAR_MODE_APP2_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());
    }

    /**
     * Verifies that releasing automotive projection works as expected.
     */
    @Test
    public void testReleaseAutomotiveProjectionBasic() {
        // Releasing before something's set shouldn't break anything.
        mCarModeTracker.handleReleaseAutomotiveProjection();
        assertEquals(0, mCarModeTracker.getCarModeApps().size());
        assertFalse(mCarModeTracker.isInCarMode());

        mCarModeTracker.handleSetAutomotiveProjection(CAR_MODE_APP1_PACKAGE_NAME);
        mCarModeTracker.handleReleaseAutomotiveProjection();
        // Should be gone now.
        assertEquals(0, mCarModeTracker.getCarModeApps().size());
        assertFalse(mCarModeTracker.isInCarMode());
    }

    /**
     * Verifies that setting automotive projection overrides but doesn't overwrite car mode apps.
     */
    @Test
    public void testAutomotiveProjectionOverridesCarMode() {
        mCarModeTracker.handleEnterCarMode(50, CAR_MODE_APP1_PACKAGE_NAME);
        mCarModeTracker.handleSetAutomotiveProjection(CAR_MODE_APP4_PACKAGE_NAME);

        // Should have two apps now, the car mode and the automotive projection one.
        assertEquals(2, mCarModeTracker.getCarModeApps().size());
        assertTrue(mCarModeTracker.isInCarMode());

        // Automotive projection takes priority.
        assertEquals(CAR_MODE_APP4_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());

        // If we add another car mode app, automotive projection still has priority.
        mCarModeTracker.handleEnterCarMode(Integer.MAX_VALUE, CAR_MODE_APP2_PACKAGE_NAME);
        assertEquals(3, mCarModeTracker.getCarModeApps().size());
        assertTrue(mCarModeTracker.isInCarMode());
        assertEquals(CAR_MODE_APP4_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());

        // If we release automotive projection, we go back to the prioritized list of plain car
        // mode apps.
        mCarModeTracker.handleReleaseAutomotiveProjection();
        assertEquals(2, mCarModeTracker.getCarModeApps().size());
        assertTrue(mCarModeTracker.isInCarMode());
        assertEquals(CAR_MODE_APP2_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());

        // Make sure we didn't mess with the first app that was added.
        mCarModeTracker.handleExitCarMode(Integer.MAX_VALUE, CAR_MODE_APP2_PACKAGE_NAME);
        assertEquals(1, mCarModeTracker.getCarModeApps().size());
        assertTrue(mCarModeTracker.isInCarMode());
        assertEquals(CAR_MODE_APP1_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());
    }

    /**
     * Verifies that releasing automotive projection doesn't interfere with plain car mode apps.
     */
    @Test
    public void testReleaseAutomotiveProjectionNoopForCarModeApps() {
        mCarModeTracker.handleEnterCarMode(50, CAR_MODE_APP1_PACKAGE_NAME);
        mCarModeTracker.handleReleaseAutomotiveProjection();
        assertEquals(1, mCarModeTracker.getCarModeApps().size());
        assertTrue(mCarModeTracker.isInCarMode());
        assertEquals(CAR_MODE_APP1_PACKAGE_NAME, mCarModeTracker.getCurrentCarModePackage());
    }
}
