/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tests.sdksandbox.endtoend;

import android.app.sdksandbox.AppOwnedSdkSandboxInterface;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AppOwnedSdkSandboxInterfaceTest extends SandboxKillerBeforeTest {
    private static final String APP_OWNED_SDK_SANDBOX_INTERFACE_NAME =
            "com.android.ctsappownedsdksandboxinterface";

    @Test
    public void testGetName() {
        final AppOwnedSdkSandboxInterface appOwnedSdkSandboxInterface =
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME, /*version=*/ 0, new Binder());
        assertThat(appOwnedSdkSandboxInterface.getName())
                .isEqualTo(APP_OWNED_SDK_SANDBOX_INTERFACE_NAME);
    }

    @Test
    public void testGetVersion() {
        final AppOwnedSdkSandboxInterface appOwnedSdkSandboxInterface =
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME, /*version=*/ 1, new Binder());
        assertThat(appOwnedSdkSandboxInterface.getVersion()).isEqualTo(1);
    }

    @Test
    public void testGetInterface() {
        final IBinder iBinder = new Binder();
        final AppOwnedSdkSandboxInterface appOwnedSdkSandboxInterface =
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME, /*version=*/ 0, iBinder);
        assertThat(appOwnedSdkSandboxInterface.getInterface()).isSameInstanceAs(iBinder);
    }

    @Test
    public void testDescribeContents() {
        final AppOwnedSdkSandboxInterface appOwnedSdkSandboxInterface =
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME, /*version=*/ 0, new Binder());
        assertThat(appOwnedSdkSandboxInterface.describeContents()).isEqualTo(0);
    }

    @Test
    public void testWriteToParcel() {
        final IBinder iBinder = new Binder();
        final AppOwnedSdkSandboxInterface appOwnedSdkSandboxInterface =
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME, /*version=*/ 0, iBinder);

        final Parcel parcel = Parcel.obtain();
        appOwnedSdkSandboxInterface.writeToParcel(parcel, /*flags=*/ 0);

        // Create AppOwnedSdkSandboxInterface with the same parcel
        parcel.setDataPosition(0); // rewind
        final AppOwnedSdkSandboxInterface appOwnedSdkSandboxInterfaceCheck =
                AppOwnedSdkSandboxInterface.CREATOR.createFromParcel(parcel);

        assertThat(appOwnedSdkSandboxInterfaceCheck.getName())
                .isEqualTo(appOwnedSdkSandboxInterfaceCheck.getName());
        assertThat(appOwnedSdkSandboxInterfaceCheck.getVersion())
                .isEqualTo(appOwnedSdkSandboxInterface.getVersion());
        assertThat(appOwnedSdkSandboxInterfaceCheck.getInterface())
                .isEqualTo(appOwnedSdkSandboxInterface.getInterface());
    }
}
