// Copyright (C) 2022 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.android.ctssdkprovider;

import android.os.Bundle;

import com.android.ctssdkprovider.IActivityActionExecutor;
import com.android.ctssdkprovider.IActivityStarter;

interface ICtsSdkProviderApi {
    void checkClassloaders();
    void checkResourcesAndAssets();
    boolean isPermissionGranted (String permissionName, boolean useApplicationContext);
    int getContextHashCode(boolean useApplicationContext);
    int getContextUserId();
    void testStoragePaths();
    int getProcessImportance();
    void startSandboxActivityDirectlyByAction(String sandboxPackageName);
    void startSandboxActivityDirectlyByComponent(String sandboxPackageName);
    IActivityActionExecutor startActivity(IActivityStarter callback, in Bundle extras);
    String getPackageName();
    String getOpPackageName();
    String getClientPackageName();
    void checkRoomDatabaseAccess();
    void checkCanUseSharedPreferences();
    void checkReadFileDescriptor(in ParcelFileDescriptor fd, String expectedValue);
    ParcelFileDescriptor createFileDescriptor(String valueToWrite);
    void createAndRegisterSdkSandboxClientImportanceListener();
    void waitForStateChangeDetection(int expectedForegroundValue, int expectedBackgroundValue);
    void unregisterSdkSandboxClientImportanceListener();
    int getLauncherActivityCount();
}
