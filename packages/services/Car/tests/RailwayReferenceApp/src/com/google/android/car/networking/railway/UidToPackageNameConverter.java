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

package com.google.android.car.networking.railway;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArraySet;

import java.util.List;
import java.util.Set;

public final class UidToPackageNameConverter {

    public static Set<Integer> convertToUids(Context applicationContext, String packageNames)
            throws PackageManager.NameNotFoundException {
        final PackageManager packageManager = applicationContext.getPackageManager();
        final UserManager userManager = applicationContext.getSystemService(UserManager.class);

        final Set<Integer> uids = new ArraySet<>();
        final List<UserHandle> users = userManager.getUserHandles(true);

        String[] packageNamesArray = packageNames.split(",");
        for (String packageName : packageNamesArray) {
            boolean nameNotFound = true;
            packageName = packageName.trim();
            for (final UserHandle user : users) {
                try {
                    final int uid =
                            packageManager.getApplicationInfoAsUser(packageName, 0, user).uid;
                    uids.add(uid);
                    nameNotFound = false;
                } catch (PackageManager.NameNotFoundException e) {
                    // Although this may seem like an error scenario, it is ok as all packages are
                    // not expected to be installed for all users.
                    continue;
                }
            }

            if (nameNotFound) {
                throw new PackageManager.NameNotFoundException("Not installed: " + packageName);
            }
        }
        return uids;
    }

    public static String convertToPackageNames(Context applicationContext, Set<Integer> uids) {
        final PackageManager packageManager = applicationContext.getPackageManager();

        StringBuilder sb = new StringBuilder();
        for (int uid : uids) {
            sb.append(packageManager.getNameForUid(uid)).append(", ");
        }

        return sb.toString();
    }

    private UidToPackageNameConverter() {}
}
