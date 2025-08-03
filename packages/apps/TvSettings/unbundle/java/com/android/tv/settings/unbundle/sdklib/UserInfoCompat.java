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

import android.content.pm.UserInfo;
import android.os.UserHandle;

import java.util.ArrayList;
import java.util.List;

public class UserInfoCompat {
    UserInfo mUserInfo;
    public int id;
    public int restrictedProfileParentId;
    public static final int NO_PROFILE_GROUP_ID = UserInfo.NO_PROFILE_GROUP_ID;


    public UserHandle getUserHandle() {
        return mUserInfo.getUserHandle();
    }

    UserInfoCompat(UserInfo userInfo) {
        mUserInfo = userInfo;
        id = mUserInfo.id;
        restrictedProfileParentId = userInfo.restrictedProfileParentId;
    }

    public int getId() {
        return mUserInfo.id;
    }

    public boolean isManagedProfile() {
        return mUserInfo.isManagedProfile();
    }

    public boolean isRestricted() {
        return mUserInfo.isRestricted();
    }

    public boolean isAdmin() {
        return mUserInfo.isAdmin();
    }

    static List<UserInfoCompat> convert(List<UserInfo> infos) {
        List<UserInfoCompat> list = new ArrayList<>();
        for (UserInfo info : infos) {
            list.add(new UserInfoCompat(info));
        }
        return list;
    }
}
