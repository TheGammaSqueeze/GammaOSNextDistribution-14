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

package com.android.tradefed.targetprep;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.UserInfo;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

/** Unit tests for {@link SwitchUserTargetPreparer}. */
@RunWith(JUnit4.class)
public class SwitchUserTargetPreparerTest {

    @Mock private ITestDevice mMockDevice;

    private TestInformation mTestInformation;
    private SwitchUserTargetPreparer mSwitchUserTargetPreparer;
    private OptionSetter mOptionSetter;

    @Before
    public void setUp() throws ConfigurationException {
        MockitoAnnotations.initMocks(this);
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice(ConfigurationDef.DEFAULT_DEVICE_NAME, mMockDevice);
        mTestInformation = TestInformation.newBuilder().setInvocationContext(context).build();
        mSwitchUserTargetPreparer = new SwitchUserTargetPreparer();
        mOptionSetter = new OptionSetter(mSwitchUserTargetPreparer);
    }

    @Test
    public void testSetUpRunAsPrimary_ifAlreadyInPrimary_noSwitch() throws Exception {
        mOptionSetter.setOptionValue("user-type", "primary");

        // setup
        when(mMockDevice.getCurrentUser()).thenReturn(11);
        mockListUsersInfo(
                mMockDevice,
                /* userIds= */ new Integer[] {0, 11},
                /* flags= */ new Integer[] {0, UserInfo.FLAG_PRIMARY});

        // act
        mSwitchUserTargetPreparer.setUp(mTestInformation);

        // assert
        verify(mMockDevice, never()).switchUser(anyInt());
    }

    @Test
    public void testSetUpRunAsSystem_ifAlreadyInSystem_noSwitch() throws Exception {
        mOptionSetter.setOptionValue("user-type", "system");

        // setup
        when(mMockDevice.getCurrentUser()).thenReturn(0);
        mockListUsersInfo(
                mMockDevice,
                /* userIds= */ new Integer[] {0, 11},
                /* flags= */ new Integer[] {0, UserInfo.FLAG_PRIMARY});

        // act
        mSwitchUserTargetPreparer.setUp(mTestInformation);

        // assert
        verify(mMockDevice, never()).switchUser(0);
    }

    @Test
    public void testSetUpRunAsSystem_ifSystemSwitchIsNotAllowed_switchToMain() throws Exception {
        mOptionSetter.setOptionValue("user-type", "system");

        // setup
        when(mMockDevice.getCurrentUser()).thenReturn(11);
        mockListUsersInfo(
                mMockDevice,
                /* userIds= */ new Integer[] {0, 10, 11},
                /* flags= */ new Integer[] {
                    UserInfo.FLAG_PRIMARY, UserInfo.FLAG_MAIN, UserInfo.FLAG_GUEST
                });

        when(mMockDevice.isHeadlessSystemUserMode()).thenReturn(true);
        when(mMockDevice.canSwitchToHeadlessSystemUser()).thenReturn(false);
        when(mMockDevice.switchUser(10)).thenReturn(true);

        // act
        mSwitchUserTargetPreparer.setUp(mTestInformation);

        // assert
        verify(mMockDevice, times(1)).switchUser(10);
    }

    @Test
    public void testSetUpRunAsSystem_ifSystemSwitchIsNotAllowed_noSwitchIfAlreadyOnMainUser()
            throws Exception {
        mOptionSetter.setOptionValue("user-type", "system");

        // setup
        when(mMockDevice.getCurrentUser()).thenReturn(10);
        mockListUsersInfo(
                mMockDevice,
                /* userIds= */ new Integer[] {0, 10, 11},
                /* flags= */ new Integer[] {
                    UserInfo.FLAG_PRIMARY, UserInfo.FLAG_MAIN, UserInfo.FLAG_GUEST
                });

        when(mMockDevice.isHeadlessSystemUserMode()).thenReturn(true);
        when(mMockDevice.canSwitchToHeadlessSystemUser()).thenReturn(false);

        // act
        mSwitchUserTargetPreparer.setUp(mTestInformation);

        // assert
        verify(mMockDevice, never()).switchUser(10);
    }

    @Test
    public void testSetUpRunAsPrimary_ifNotInPrimary_switchToPrimary() throws Exception {
        mOptionSetter.setOptionValue("user-type", "primary");

        // setup
        when(mMockDevice.getCurrentUser()).thenReturn(11);
        mockListUsersInfo(
                mMockDevice,
                /* userIds= */ new Integer[] {0, 10, 11},
                /* flags= */ new Integer[] {0, UserInfo.FLAG_PRIMARY, 0});
        when(mMockDevice.switchUser(10)).thenReturn(true);

        // act
        mSwitchUserTargetPreparer.setUp(mTestInformation);

        // assert
        verify(mMockDevice, times(1)).switchUser(10);
    }

    @Test
    public void testSetUpRunAsGuest_ifNotInGuest_switchToGuest() throws Exception {
        mOptionSetter.setOptionValue("user-type", "guest");

        // setup
        when(mMockDevice.getCurrentUser()).thenReturn(11);
        mockListUsersInfo(
                mMockDevice,
                /* userIds= */ new Integer[] {0, 10, 11},
                /* flags= */ new Integer[] {0, UserInfo.FLAG_GUEST, 0});
        when(mMockDevice.switchUser(10)).thenReturn(true);

        // act
        mSwitchUserTargetPreparer.setUp(mTestInformation);

        // assert
        verify(mMockDevice, times(1)).switchUser(10);
    }

    @Test
    public void testSetUpRunAsSystem_ifNotInSystem_switchToSystem() throws Exception {
        mOptionSetter.setOptionValue("user-type", "system");

        // setup
        when(mMockDevice.getCurrentUser()).thenReturn(10);
        mockListUsersInfo(
                mMockDevice,
                /* userIds= */ new Integer[] {0, 10},
                /* flags= */ new Integer[] {0, 0});
        when(mMockDevice.switchUser(0)).thenReturn(true);

        // act
        mSwitchUserTargetPreparer.setUp(mTestInformation);

        // assert
        verify(mMockDevice, times(1)).switchUser(0);
    }

    @Test
    public void testTearDown_ifStartedInSecondary_switchesBackToSecondary() throws Exception {
        mOptionSetter.setOptionValue("user-type", "system");

        // setup
        when(mMockDevice.getCurrentUser()).thenReturn(10);
        mockListUsersInfo(
                mMockDevice,
                /* userIds= */ new Integer[] {0, 10},
                /* flags= */ new Integer[] {0, 0});
        when(mMockDevice.switchUser(0)).thenReturn(true);
        when(mMockDevice.switchUser(10)).thenReturn(true);

        // first switches to primary
        mSwitchUserTargetPreparer.setUp(mTestInformation);
        verify(mMockDevice, times(1)).switchUser(0);

        // then switches back to secondary
        mSwitchUserTargetPreparer.tearDown(mTestInformation, null);
        verify(mMockDevice, times(1)).switchUser(10);

    }

    @Test
    public void testSetUp_ifNoSwitchToSpecified_noUserSwitch() throws Exception {
        // setup
        when(mMockDevice.getCurrentUser()).thenReturn(10);
        mockListUsersInfo(
                mMockDevice,
                /* userIds= */ new Integer[] {0, 10},
                /* flags= */ new Integer[] {0, 0});

        // act
        mSwitchUserTargetPreparer.setUp(mTestInformation);

        // assert
        verify(mMockDevice, never()).switchUser(anyInt());
    }

    @Test
    public void testSetUp_ifSwitchFails_throwsTargetSetupError() throws Exception {
        mOptionSetter.setOptionValue("user-type", "primary");

        // setup
        when(mMockDevice.getCurrentUser()).thenReturn(10);
        mockListUsersInfo(
                mMockDevice,
                /* userIds= */ new Integer[] {0, 10},
                /* flags= */ new Integer[] {UserInfo.FLAG_PRIMARY, 0});
        when(mMockDevice.switchUser(0)).thenReturn(false);

        // act
        try {
            mSwitchUserTargetPreparer.setUp(mTestInformation);
            fail("Should have thrown TargetSetupError exception.");
        } catch (TargetSetupError e) {
            // do nothing
        }
    }

    private void mockListUsersInfo(ITestDevice device, Integer[] userIds, Integer[] flags)
            throws DeviceNotAvailableException {
        Map<Integer, UserInfo> result = new HashMap<>();
        for (int i = 0; i < userIds.length; i++) {
            int userId = userIds[i];
            result.put(
                    userId,
                    new UserInfo(
                            /* userId= */ userId,
                            /* userName= */ "usr" + userId,
                            /* flag= */ flags[i],
                            /* isRunning= */ false));
        }
        when(device.getUserInfos()).thenReturn(result);
    }
}
