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

package com.android.car;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.os.UserHandle;

import com.android.car.internal.ICarServiceHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class CarServiceHelperWrapperUnitTest extends AbstractExtendedMockitoTestCase {
    private static final long CAR_SERVICE_WAIT_SHORT_TIMEOUT_MS = 1;

    @Mock
    private ICarServiceHelper mICarServiceHelper;
    @Mock
    private Runnable mRunnable;

    private CarServiceHelperWrapper mDefaultWrapper;

    /**
     * Creates {@link CarServiceHelperWrapper} with very short connection time out.
     */
    public static CarServiceHelperWrapper createWithImmediateTimeout() {
        CarLocalServices.removeServiceForTest(CarServiceHelperWrapper.class);

        return CarServiceHelperWrapper.create(CAR_SERVICE_WAIT_SHORT_TIMEOUT_MS);
    }

    @Before
    public void setUp() throws Exception {
        mDefaultWrapper = CarServiceHelperWrapper.create();
    }

    @After
    public void tearDown() throws Exception {
        CarLocalServices.removeServiceForTest(CarServiceHelperWrapper.class);
    }

    @Test
    public void testRunOnConnection_alreadyConnected() {
        mDefaultWrapper.setCarServiceHelper(mICarServiceHelper);

        mDefaultWrapper.runOnConnection(mRunnable);

        verify(mRunnable).run();
    }

    @Test
    public void testRunOnConnection_connectLater() {
        mDefaultWrapper.runOnConnection(mRunnable);

        verify(mRunnable, never()).run();

        mDefaultWrapper.setCarServiceHelper(mICarServiceHelper);

        verify(mRunnable).run();
    }

    @Test
    public void testThrowWhenNotConnected() {
        CarServiceHelperWrapper wrapper = createWithImmediateTimeout();

        // Check exception when not connected. All arguments do not matter.
        assertThrows(IllegalStateException.class,
                () -> wrapper.createUserEvenWhenDisallowed("", "", 0));
        assertThrows(IllegalStateException.class, () -> wrapper.getMainDisplayAssignedToUser(0));
        assertThrows(IllegalStateException.class, () -> wrapper.getProcessGroup(0));
        assertThrows(IllegalStateException.class, () -> wrapper.getUserAssignedToDisplay(0));
        assertThrows(IllegalStateException.class,
                () -> wrapper.setDisplayAllowlistForUser(0, null));
        assertThrows(IllegalStateException.class, () -> wrapper.sendInitialUser(UserHandle.SYSTEM));
        assertThrows(IllegalStateException.class, () -> wrapper.setPassengerDisplays(null));
        assertThrows(IllegalStateException.class, () -> wrapper.setPersistentActivity(null, 0, 0));
        assertThrows(IllegalStateException.class, () -> wrapper.setProcessGroup(0, 0));
        assertThrows(IllegalStateException.class, () -> wrapper.setProcessProfile(0, 0, ""));
        assertThrows(IllegalStateException.class, () -> wrapper.setSafetyMode(true));
        assertThrows(IllegalStateException.class,
                () -> wrapper.startUserInBackgroundVisibleOnDisplay(0, 0));
    }
}
