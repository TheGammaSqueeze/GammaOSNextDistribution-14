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

import android.app.AppOpsManager;
import android.app.AppProtoEnums;
import android.content.Context;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.List;

public class AppOpsManagerCompat {
    private final AppOpsManager mAppOpsManager;

    public static class PackageOpsCompat {
        private final AppOpsManager.PackageOps mPackageOps;

        private PackageOpsCompat(AppOpsManager.PackageOps packageOps) {
            this.mPackageOps = packageOps;
        }

        public int getUid() {
            return mPackageOps.getUid();
        }

        public String getPackageName() {
            return mPackageOps.getPackageName();
        }
    }

    public List<PackageOpsCompat> getPackagesForOps(int[] ops) {
        List<PackageOpsCompat> list = new ArrayList<>();
        for (AppOpsManager.PackageOps op : mAppOpsManager.getPackagesForOps(ops)) {
            list.add(new PackageOpsCompat(op));
        }
        return list;
    }

    public AppOpsManagerCompat(AppOpsManager appOpsManager) {
        mAppOpsManager = appOpsManager;
    }

    public AppOpsManagerCompat(Context context) {
        mAppOpsManager = context.getSystemService(AppOpsManager.class);
    }


    public void setMode(int code, int uid, String packageName, @AppOpsManager.Mode int mode) {
        mAppOpsManager.setMode(code, uid, packageName, mode);
    }

    public void setUidMode(int code, int uid, @AppOpsManager.Mode int mode) {
        mAppOpsManager.setUidMode(code, uid, mode);
    }

    public int checkOpNoThrow(int op, int uid, String packageName) {
        return mAppOpsManager.checkOpNoThrow(op, uid, packageName);
    }

    public static final int OP_WRITE_SETTINGS = AppOpsManager.OP_WRITE_SETTINGS;
    public static final int OP_PICTURE_IN_PICTURE = AppOpsManager.OP_PICTURE_IN_PICTURE;

    public static final int OP_GET_USAGE_STATS = AppOpsManager.OP_GET_USAGE_STATS;

    public static final int OP_SCHEDULE_EXACT_ALARM = AppOpsManager.OP_SCHEDULE_EXACT_ALARM;

    public static final int OP_MONITOR_LOCATION = AppOpsManager.OP_MONITOR_LOCATION;
    public static final int OP_MONITOR_HIGH_POWER_LOCATION =
            AppOpsManager.OP_MONITOR_HIGH_POWER_LOCATION;

    public static final int OP_FINE_LOCATION = AppOpsManager.OP_FINE_LOCATION;
    public static final int OP_COARSE_LOCATION = AppOpsManager.OP_COARSE_LOCATION;

    public static final int OP_CAMERA = AppOpsManager.OP_CAMERA;
    public static final int OP_PHONE_CALL_CAMERA = AppOpsManager.OP_PHONE_CALL_CAMERA;

    public static final int OP_RECORD_AUDIO = AppOpsManager.OP_RECORD_AUDIO;
    public static final int OP_PHONE_CALL_MICROPHONE = AppOpsManager.OP_PHONE_CALL_MICROPHONE;

    public static final int OP_FLAGS_ALL = AppOpsManager.OP_FLAGS_ALL;
    public static final int OP_SYSTEM_ALERT_WINDOW = AppOpsManager.OP_SYSTEM_ALERT_WINDOW;

    public static final int OP_TOAST_WINDOW = AppProtoEnums.APP_OP_TOAST_WINDOW;
    public static final int OP_MOCK_LOCATION = AppProtoEnums.APP_OP_MOCK_LOCATION;

    public static final int OP_REQUEST_INSTALL_PACKAGES =
            AppProtoEnums.APP_OP_REQUEST_INSTALL_PACKAGES;

    public void setUserRestriction(int code, boolean restricted, IBinder token) {
        mAppOpsManager.setUserRestriction(code, restricted, token);
    }
}
