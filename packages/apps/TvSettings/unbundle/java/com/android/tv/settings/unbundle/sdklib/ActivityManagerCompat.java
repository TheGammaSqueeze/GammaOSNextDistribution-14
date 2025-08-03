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

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.IPackageDataObserver;

public class ActivityManagerCompat {
    private final ActivityManager mActivityManager;

    public interface PackageDataObserver {
        void onRemoveCompleted(String packageName, boolean succeeded);
    }

    public ActivityManagerCompat(Context context) {
        mActivityManager = (ActivityManager) context.getSystemService(
                Context.ACTIVITY_SERVICE);
    }

    public static boolean isLowRamDeviceStatic() {
        return ActivityManager.isLowRamDeviceStatic();
    }

    public boolean switchUser(int userid) {
        return mActivityManager.switchUser(userid);
    }

    public static Object getService() {
        return ActivityManager.getService();
    }

    public boolean clearApplicationUserData(String packageName, PackageDataObserver observer) {
        return mActivityManager.clearApplicationUserData(packageName,
                new IPackageDataObserver.Stub() {
                    public void onRemoveCompleted(final String packageName,
                            final boolean succeeded) {
                        observer.onRemoveCompleted(packageName, succeeded);
                    }
                });
    }
}
