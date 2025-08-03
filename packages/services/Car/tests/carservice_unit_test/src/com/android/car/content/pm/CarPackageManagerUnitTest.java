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
package com.android.car.content.pm;

import static android.car.testapi.CarMockitoHelper.mockHandleRemoteExceptionFromCarServiceWithDefaultValue;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.CarVersion;
import android.car.content.pm.CarPackageManager;
import android.car.content.pm.ICarPackageManager;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.RemoteException;
import android.os.ServiceSpecificException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class CarPackageManagerUnitTest extends AbstractExtendedMockitoTestCase {

    @Mock
    private Car mCar;

    @Mock
    private ICarPackageManager mService;

    private CarPackageManager mMgr;

    @Before
    public void setFixtures() {
        mMgr = new CarPackageManager(mCar, mService);
    }

    @Test
    public void testGetTargetCarVersion_self_ok() throws Exception {
        mockPackageName("dr.evil");
        CarVersion apiVersion = CarVersion.forMajorAndMinorVersions(66, 6);
        when(mService.getSelfTargetCarVersion("dr.evil")).thenReturn(apiVersion);

        assertThat(mMgr.getTargetCarVersion()).isSameInstanceAs(apiVersion);
    }

    @Test
    public void testGetTargetCarVersion_self_remoteException() throws Exception {
        mockPackageName("the.meaning.of.life");
        RemoteException cause = new RemoteException("D'OH!");
        when(mService.getSelfTargetCarVersion("the.meaning.of.life")).thenThrow(cause);

        RuntimeException e = assertThrows(RuntimeException.class, () -> mMgr.getTargetCarVersion());

        assertThat(e.getCause()).isSameInstanceAs(cause);
    }

    @Test
    public void testGetTargetCarVersion_ok() throws Exception {
        CarVersion apiVersion = CarVersion.forMajorAndMinorVersions(66, 6);
        when(mService.getTargetCarVersion("dr.evil")).thenReturn(apiVersion);

        assertThat(mMgr.getTargetCarVersion("dr.evil")).isSameInstanceAs(apiVersion);
    }

    @Test
    public void testGetTargetCarVersion_runtimeException() throws Exception {
        mockHandleRemoteExceptionFromCarServiceWithDefaultValue(mCar);
        RemoteException cause = new RemoteException("D'OH!");
        when(mService.getTargetCarVersion("the.meaning.of.life")).thenThrow(cause);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> mMgr.getTargetCarVersion("the.meaning.of.life"));

        assertThat(e.getCause()).isSameInstanceAs(cause);
    }

    @Test
    public void testGetTargetCarVersion_serviceException_unexpectedErrorCode() throws Exception {
        mockHandleRemoteExceptionFromCarServiceWithDefaultValue(mCar);
        ServiceSpecificException cause = new ServiceSpecificException(666, "D'OH!");
        when(mService.getTargetCarVersion("the.meaning.of.life")).thenThrow(cause);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> mMgr.getTargetCarVersion("the.meaning.of.life"));

        assertThat(e.getCause()).isSameInstanceAs(cause);
    }

    @Test
    public void testGetTargetCarVersion_serviceException_notFound() throws Exception {
        mockHandleRemoteExceptionFromCarServiceWithDefaultValue(mCar);
        when(mService.getTargetCarVersion("the.meaning.of.life"))
                .thenThrow(new ServiceSpecificException(CarPackageManager.ERROR_CODE_NO_PACKAGE,
                        "D'OH!"));

        NameNotFoundException e = assertThrows(NameNotFoundException.class,
                () -> mMgr.getTargetCarVersion("the.meaning.of.life"));

        assertThat(e.getMessage()).contains("the.meaning.of.life");
        assertThat(e.getMessage()).doesNotContain("D'OH!");
    }

    private void mockPackageName(String name) {
        Context context = mock(Context.class);
        when(context.getPackageName()).thenReturn(name);
        when(mCar.getContext()).thenReturn(context);
    }
}
