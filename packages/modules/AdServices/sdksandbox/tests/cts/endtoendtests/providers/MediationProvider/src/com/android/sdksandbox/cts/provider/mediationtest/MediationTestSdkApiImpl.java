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

package com.android.sdksandbox.cts.provider.mediationtest;


import android.app.sdksandbox.AppOwnedSdkSandboxInterface;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.sdkprovider.SdkSandboxController;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.ctssdkprovider.ICtsSdkProviderApi;

import java.util.List;

public class MediationTestSdkApiImpl extends IMediationTestSdkApi.Stub {
    private final Context mContext;

    public MediationTestSdkApiImpl(Context sdkContext) {
        mContext = sdkContext;
    }

    @Override
    public List<AppOwnedSdkSandboxInterface> getAppOwnedSdkSandboxInterfaces() {
        return mContext.getSystemService(SdkSandboxController.class)
                .getAppOwnedSdkSandboxInterfaces();
    }

    @Override
    public List<SandboxedSdk> getSandboxedSdks() {
        return mContext.getSystemService(SdkSandboxController.class).getSandboxedSdks();
    }

    @Override
    public void loadSdkBySdk(String sdkName) {
        Bundle params = new Bundle();
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        try {
            mContext.getSystemService(SdkSandboxController.class)
                    .loadSdk(sdkName, params, Runnable::run, callback);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load Mediatee SDK " + e.getMessage());
        }
        try {
            callback.assertLoadSdkIsSuccessful();
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Checks that the mediator can invoke an interface defined by a mediatee, either inside or
     * outside the sandbox.
     */
    @Override
    public void checkCanCallMediateeInterface(boolean inSandbox, String expectedValue) {
        ICtsSdkProviderApi sdk = getMediateeInterface(inSandbox);
        String clientPackageName;
        try {
            clientPackageName = sdk.getClientPackageName();
        } catch (RemoteException e) {
            throw new IllegalStateException("Cannot invoke interface: " + e.getMessage());
        }
        if (!clientPackageName.equals(expectedValue)) {
            throw new IllegalStateException(
                    "String "
                            + clientPackageName
                            + " did not match expected value "
                            + expectedValue);
        }
    }

    private ICtsSdkProviderApi getMediateeInterface(boolean inSandbox) {
        if (inSandbox) {
            List<SandboxedSdk> sandboxedSdks = getSandboxedSdks();
            for (SandboxedSdk sandboxedSdk : sandboxedSdks) {
                IBinder iBinder = sandboxedSdk.getInterface();
                if (iBinder.queryLocalInterface(ICtsSdkProviderApi.DESCRIPTOR) != null) {
                    return ICtsSdkProviderApi.Stub.asInterface(iBinder);
                }
            }
        } else {
            List<AppOwnedSdkSandboxInterface> appOwnedInterfaces =
                    getAppOwnedSdkSandboxInterfaces();
            for (AppOwnedSdkSandboxInterface appOwnedInterface : appOwnedInterfaces) {
                IBinder iBinder = appOwnedInterface.getInterface();
                if (iBinder.queryLocalInterface(ICtsSdkProviderApi.DESCRIPTOR) != null) {
                    return ICtsSdkProviderApi.Stub.asInterface(iBinder);
                }
            }
        }
        throw new IllegalStateException("No mediatee interface found.");
    }
}
