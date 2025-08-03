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

package android.content.pm.cts;

import static org.junit.Assert.assertEquals;

import android.content.Intent;
import android.content.pm.IPackageDeleteObserver2;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.UninstallCompleteCallback;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeNonSdkSandbox;

import org.junit.Test;

@AppModeFull
@AppModeNonSdkSandbox
public class UninstallCallbackTest {

    private static final String DUMMY_PACKAGE_NAME = "com.example.app";

    @Test
    public void testUninstallCallback_binderReceivesCorrectParams() {
        PackageDeleteObserver observer = new PackageDeleteObserver();
        UninstallCompleteCallback callback = new UninstallCompleteCallback(
                observer.getBinder().asBinder());
        callback.onUninstallComplete(DUMMY_PACKAGE_NAME, PackageManager.DELETE_SUCCEEDED, "");
        assertEquals(observer.getPackageName(), DUMMY_PACKAGE_NAME);
    }

    static class PackageDeleteObserver {

        private String mPackageName;
        private final IPackageDeleteObserver2.Stub mBinder = new IPackageDeleteObserver2.Stub() {

            @Override
            public void onUserActionRequired(Intent intent) {
                // no-op
            }

            @Override
            public void onPackageDeleted(String basePackageName, int returnCode, String msg) {
                PackageDeleteObserver.this.onPackageDeleted(basePackageName, returnCode, msg);
            }
        };

        public void onPackageDeleted(String packageName, int returnCode, String msg) {
            mPackageName = packageName;
        }

        public IPackageDeleteObserver2 getBinder() {
            return mBinder;
        }

        public String getPackageName() {
            return mPackageName;
        }

    }
}
