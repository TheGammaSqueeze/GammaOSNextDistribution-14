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

package com.android.tradefed.targetprep;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.invoker.TestInformation;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;

@RunWith(JUnit4.class)
public class DeviceOwnerTargetPreparerTest {

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private TestInformation mTestInfo;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private IConfiguration mConfiguration;

    private static final String TEST_DEVICE_OWNER_COMPONENT_NAME =
            "com.android.tradefed.targetprep/.TestOwner";
    private DeviceOwnerTargetPreparer mPreparer;
    private OptionSetter mOptionSetter;

    @Before
    public void setUp() throws Exception {
        mPreparer = new DeviceOwnerTargetPreparer();
        mOptionSetter = new OptionSetter(mPreparer);
        mPreparer.setConfiguration(mConfiguration);

        mOptionSetter.setOptionValue(
                DeviceOwnerTargetPreparer.DEVICE_OWNER_COMPONENT_NAME_OPTION,
                null,
                TEST_DEVICE_OWNER_COMPONENT_NAME);

        ArrayList<Integer> userIds = new ArrayList<>();
        userIds.add(0, 1);

        when(mTestInfo.getDevice().listUsers()).thenReturn(userIds);
    }

    @Test
    public void testSetUp_removesDeviceOwners() throws Exception {
        mPreparer.setUp(mTestInfo);
        verify(mTestInfo.getDevice()).removeOwners();
    }

    @Test
    public void testSetUp_switchesToSystemUser() throws Exception {
        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice()).switchUser(0);
    }

    @Test
    public void testSetUp_headless_switchesToPrimaryUser() throws Exception {
        when(mTestInfo
                        .getDevice()
                        .getBooleanProperty(
                                eq(DeviceOwnerTargetPreparer.HEADLESS_SYSTEM_USER_PROPERTY),
                                anyBoolean()))
                .thenReturn(true);
        when(mTestInfo.getDevice().getPrimaryUserId()).thenReturn(10);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice()).switchUser(10);
    }

    @Test
    public void testSetUp_removeSecondaryUsers() throws Exception {
        ArrayList<Integer> userIds = new ArrayList<>();
        userIds.add(0);
        userIds.add(10);
        userIds.add(11);
        when(mTestInfo.getDevice().listUsers()).thenReturn(userIds);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice(), never()).removeUser(0);
        verify(mTestInfo.getDevice()).removeUser(10);
        verify(mTestInfo.getDevice()).removeUser(11);
    }

    @Test
    public void testSetUp_headless_removeSecondaryUsers() throws Exception {
        when(mTestInfo
                        .getDevice()
                        .getBooleanProperty(
                                eq(DeviceOwnerTargetPreparer.HEADLESS_SYSTEM_USER_PROPERTY),
                                anyBoolean()))
                .thenReturn(true);
        when(mTestInfo.getDevice().getPrimaryUserId()).thenReturn(10);
        ArrayList<Integer> userIds = new ArrayList<>();
        userIds.add(0);
        userIds.add(10);
        userIds.add(11);
        when(mTestInfo.getDevice().listUsers()).thenReturn(userIds);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice(), never()).removeUser(0);
        verify(mTestInfo.getDevice(), never()).removeUser(10);
        verify(mTestInfo.getDevice()).removeUser(11);
    }

    @Test
    public void testSetUp_setsDeviceOwner() throws Exception {
        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice()).setDeviceOwner(TEST_DEVICE_OWNER_COMPONENT_NAME, 0);
    }

    @Test
    public void testSetUp_headless_setsDeviceOwner() throws Exception {
        when(mTestInfo
                        .getDevice()
                        .getBooleanProperty(
                                eq(DeviceOwnerTargetPreparer.HEADLESS_SYSTEM_USER_PROPERTY),
                                anyBoolean()))
                .thenReturn(true);
        when(mTestInfo.getDevice().getPrimaryUserId()).thenReturn(10);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice()).setDeviceOwner(TEST_DEVICE_OWNER_COMPONENT_NAME, 10);
    }

    @Test
    public void testTearDown_removesDeviceOwner() throws Exception {
        mPreparer.setUp(mTestInfo);
        mPreparer.tearDown(mTestInfo, /* throwable= */ null);

        verify(mTestInfo.getDevice()).removeAdmin(TEST_DEVICE_OWNER_COMPONENT_NAME, 0);
    }

    @Test
    public void testTearDown_headless_removesDeviceOwner() throws Exception {
        when(mTestInfo
                        .getDevice()
                        .getBooleanProperty(
                                eq(DeviceOwnerTargetPreparer.HEADLESS_SYSTEM_USER_PROPERTY),
                                anyBoolean()))
                .thenReturn(true);
        when(mTestInfo.getDevice().getPrimaryUserId()).thenReturn(10);

        mPreparer.setUp(mTestInfo);
        mPreparer.tearDown(mTestInfo, /* throwable= */ null);

        verify(mTestInfo.getDevice()).removeAdmin(TEST_DEVICE_OWNER_COMPONENT_NAME, 10);
    }
}
