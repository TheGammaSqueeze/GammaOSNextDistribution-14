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

package com.android.bedstead.nene.users;

import static com.android.bedstead.nene.utils.ParserUtils.extractIndentedSections;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.bedstead.nene.exceptions.AdbParseException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Parser for "adb dumpsys user" on Android 30+
 *
 * <p>Example output:
 * {@code
 * Current user: 0
 * Users:
 *   UserInfo{0:null:c13} serialNo=0 isPrimary=true
 *     Type: android.os.usertype.full.SYSTEM
 *     Flags: 3091 (ADMIN|FULL|INITIALIZED|PRIMARY|SYSTEM)
 *     State: RUNNING_UNLOCKED
 *     Created: <unknown>
 *     Last logged in: +10m10s675ms ago
 *     Last logged in fingerprint: generic/cf_x86_phone/vsoc_x86:11/RP1A.201005.004
 *     .A1/6934943:userdebug/dev-keys
 *     Start time: +12m26s184ms ago
 *     Unlock time: +12m7s388ms ago
 *     Has profile owner: false
 *     Restrictions:
 *       none
 *     Device policy global restrictions:
 *       null
 *     Device policy local restrictions:
 *       none
 *     Effective restrictions:
 *       none
 *   UserInfo{10:managedprofileuser:1020} serialNo=10 isPrimary=false
 *     Type: android.os.usertype.profile.MANAGED
 *     Flags: 4128 (MANAGED_PROFILE|PROFILE)
 *     State: -1
 *     Created: +2s690ms ago
 *     Last logged in: <unknown>
 *     Last logged in fingerprint: generic/cf_x86_phone/vsoc_x86:11/RP1A.201005.004
 *     .A1/6934943:userdebug/dev-keys
 *     Start time: <unknown>
 *     Unlock time: <unknown>
 *     Has profile owner: false
 *     Restrictions:
 *       null
 *     Device policy global restrictions:
 *       null
 *     Device policy local restrictions:
 *       none
 *     Effective restrictions:
 *       null
 *
 *   Device owner id:-10000
 *
 *   Guest restrictions:
 *     no_sms
 *     no_install_unknown_sources
 *     no_config_wifi
 *     no_outgoing_calls
 *
 *   Device managed: false
 *   Started users state: {0=3}
 *
 *   Max users: 4 (limit reached: false)
 *   Supports switchable users: false
 *   All guests ephemeral: false
 *   Force ephemeral users: false
 *   Is split-system user: false
 *   Is headless-system mode: false
 *   User version: 9
 *
 *   User types (7 types):
 *     android.os.usertype.full.GUEST:
 *         mName: android.os.usertype.full.GUEST
 *         mBaseType: FULL
 *         mEnabled: true
 *         mMaxAllowed: 1
 *         mMaxAllowedPerParent: -1
 *         mDefaultUserInfoFlags: GUEST
 *         mLabel: 0
 *         mDefaultRestrictions:
 *             no_sms
 *             no_install_unknown_sources
 *             no_config_wifi
 *             no_outgoing_calls
 *         mIconBadge: 0
 *         mBadgePlain: 0
 *         mBadgeNoBackground: 0
 *         mBadgeLabels.length: 0(null)
 *         mBadgeColors.length: 0(null)
 *         mDarkThemeBadgeColors.length: 0(null)
 *     android.os.usertype.profile.MANAGED:
 *         mName: android.os.usertype.profile.MANAGED
 *         mBaseType: PROFILE
 *         mEnabled: true
 *         mMaxAllowed: -1
 *         mMaxAllowedPerParent: 1
 *         mDefaultUserInfoFlags: MANAGED_PROFILE
 *         mLabel: 0
 *         mDefaultRestrictions:
 *             null
 *         mIconBadge: 17302387
 *         mBadgePlain: 17302382
 *         mBadgeNoBackground: 17302384
 *         mBadgeLabels.length: 3
 *         mBadgeColors.length: 3
 *         mDarkThemeBadgeColors.length: 3
 *     android.os.usertype.system.HEADLESS:
 *         mName: android.os.usertype.system.HEADLESS
 *         mBaseType: SYSTEM
 *         mEnabled: true
 *         mMaxAllowed: -1
 *         mMaxAllowedPerParent: -1
 *         mDefaultUserInfoFlags: 0
 *         mLabel: 0
 *         config_defaultFirstUserRestrictions:
 *             none
 *         mIconBadge: 0
 *         mBadgePlain: 0
 *         mBadgeNoBackground: 0
 *         mBadgeLabels.length: 0(null)
 *         mBadgeColors.length: 0(null)
 *         mDarkThemeBadgeColors.length: 0(null)
 *     android.os.usertype.full.SYSTEM:
 *         mName: android.os.usertype.full.SYSTEM
 *         mBaseType: FULL|SYSTEM
 *         mEnabled: true
 *         mMaxAllowed: -1
 *         mMaxAllowedPerParent: -1
 *         mDefaultUserInfoFlags: 0
 *         mLabel: 0
 *         config_defaultFirstUserRestrictions:
 *             none
 *         mIconBadge: 0
 *         mBadgePlain: 0
 *         mBadgeNoBackground: 0
 *         mBadgeLabels.length: 0(null)
 *         mBadgeColors.length: 0(null)
 *         mDarkThemeBadgeColors.length: 0(null)
 *     android.os.usertype.full.SECONDARY:
 *         mName: android.os.usertype.full.SECONDARY
 *         mBaseType: FULL
 *         mEnabled: true
 *         mMaxAllowed: -1
 *         mMaxAllowedPerParent: -1
 *         mDefaultUserInfoFlags: 0
 *         mLabel: 0
 *         mDefaultRestrictions:
 *             no_sms
 *             no_outgoing_calls
 *         mIconBadge: 0
 *         mBadgePlain: 0
 *         mBadgeNoBackground: 0
 *         mBadgeLabels.length: 0(null)
 *         mBadgeColors.length: 0(null)
 *         mDarkThemeBadgeColors.length: 0(null)
 *     android.os.usertype.full.RESTRICTED:
 *         mName: android.os.usertype.full.RESTRICTED
 *         mBaseType: FULL
 *         mEnabled: true
 *         mMaxAllowed: -1
 *         mMaxAllowedPerParent: -1
 *         mDefaultUserInfoFlags: RESTRICTED
 *         mLabel: 0
 *         mDefaultRestrictions:
 *             null
 *         mIconBadge: 0
 *         mBadgePlain: 0
 *         mBadgeNoBackground: 0
 *         mBadgeLabels.length: 0(null)
 *         mBadgeColors.length: 0(null)
 *         mDarkThemeBadgeColors.length: 0(null)
 *     android.os.usertype.full.DEMO:
 *         mName: android.os.usertype.full.DEMO
 *         mBaseType: FULL
 *         mEnabled: true
 *         mMaxAllowed: -1
 *         mMaxAllowedPerParent: -1
 *         mDefaultUserInfoFlags: DEMO
 *         mLabel: 0
 *         mDefaultRestrictions:
 *             null
 *         mIconBadge: 0
 *         mBadgePlain: 0
 *         mBadgeNoBackground: 0
 *         mBadgeLabels.length: 0(null)
 *         mBadgeColors.length: 0(null)
 *         mDarkThemeBadgeColors.length: 0(null)
 *
 * Allowlisted packages per user type
 *     Mode: 13 (enforced) (implicit)
 *     Legend
 *         0 -> android.os.usertype.full.DEMO
 *         1 -> android.os.usertype.full.GUEST
 *         2 -> android.os.usertype.full.RESTRICTED
 *         3 -> android.os.usertype.full.SECONDARY
 *         4 -> android.os.usertype.full.SYSTEM
 *         5 -> android.os.usertype.profile.MANAGED
 *         6 -> android.os.usertype.system.HEADLESS
 *     20 packages:
 *         com.android.internal.display.cutout.emulation.corner: 0 1 2 3 4
 *         com.android.internal.display.cutout.emulation.double: 0 1 2 3 4
 *         com.android.internal.systemui.navbar.gestural_wide_back: 0 1 2 3 4
 *         com.android.wallpapercropper: 0 1 2 3 4
 *         com.android.internal.display.cutout.emulation.tall: 0 1 2 3 4
 *         com.android.internal.systemui.navbar.threebutton: 0 1 2 3 4
 *         android: 0 1 2 3 4 5 6
 *         com.google.android.deskclock: 0 1 2 3 4
 *         com.android.internal.systemui.navbar.twobutton: 0 1 2 3 4
 *         com.android.internal.systemui.navbar.gestural_extra_wide_back: 0 1 2 3 4
 *         com.android.providers.settings: 0 1 2 3 4 5 6
 *         com.google.android.calculator: 0 1 2 3 4
 *         com.google.android.apps.wallpaper.nexus: 0 1 2 3 4
 *         com.google.android.apps.nexuslauncher: 0 1 2 3 4
 *         com.android.wallpaper.livepicker: 0 1 2 3 4
 *         com.google.android.apps.wallpaper: 0 1 2 3 4
 *         com.android.wallpaperbackup: 0 1 2 3 4
 *         com.android.internal.systemui.navbar.gestural: 0 1 2 3 4
 *         com.android.pixellogger: 0 1 2 3 4
 *         com.android.internal.systemui.navbar.gestural_narrow_back: 0 1 2 3 4
 *     No errors
 *     2 warnings
 *         com.android.wallpapercropper is allowlisted but not present.
 *         com.google.android.apps.wallpaper.nexus is allowlisted but not present.
 * }
 */
@RequiresApi(Build.VERSION_CODES.R)
public class AdbUserParser30 extends AdbUserParser26 {

    static int USER_TYPES_LIST_BASE_INDENTATION = 4;

    private Map<String, UserType> mUserTypes;

    AdbUserParser30() {
        super();
    }

    @Override
    public ParseResult parse(String dumpsysUsersOutput) throws AdbParseException {
        mUserTypes = parseUserTypes(dumpsysUsersOutput);

        ParseResult parseResult = super.parse(dumpsysUsersOutput);
        parseResult.mUserTypes = mUserTypes;

        return parseResult;
    }

    @Override
    AdbUser.MutableUser parseUser(String userString) throws AdbParseException {
        // This will be called after parseUserTypes, so the user types are already accessible
        AdbUser.MutableUser user = super.parseUser(userString);

        try {
            user.mIsPrimary = Boolean.parseBoolean(
                    userString.split("isPrimary=", 2)[1].split("[ \n]", 2)[0]);
            user.mType = mUserTypes.get(userString.split("Type: ", 2)[1].split("\n", 2)[0]);
        } catch (IndexOutOfBoundsException e) {
            throw new AdbParseException("Error parsing user", userString, e);
        }

        return user;
    }

    Map<String, UserType> parseUserTypes(String dumpsysUsersOutput) throws AdbParseException {
        String userTypesList = extractUserTypesList(dumpsysUsersOutput);
        Set<String> userTypeStrings = extractUserTypesStrings(userTypesList);

        Map<String, UserType> userTypes = new HashMap<>();
        for (String userTypeString : userTypeStrings) {
            UserType userType = new UserType(parseUserType(userTypeString));
            userTypes.put(userType.name(), userType);
        }

        return userTypes;
    }

    String extractUserTypesList(String dumpsysUsersOutput) throws AdbParseException {
        try {
            return dumpsysUsersOutput.split(
                    "User types \\(\\d+ types\\):\n", 2)[1].split("\n\n", 2)[0];
        } catch (IndexOutOfBoundsException e) {
            throw new AdbParseException("Error extracting user types list", dumpsysUsersOutput, e);
        }
    }

    Set<String> extractUserTypesStrings(String userTypesList) throws AdbParseException {
        return extractIndentedSections(userTypesList, USER_TYPES_LIST_BASE_INDENTATION);
    }

    UserType.MutableUserType parseUserType(String userTypeString) throws AdbParseException {
        try {
            UserType.MutableUserType userType = new UserType.MutableUserType();

            userType.mName = userTypeString.split("mName: ", 2)[1].split("\n")[0];
            userType.mBaseType = new HashSet<>();
            for (String baseType : userTypeString.split("mBaseType: ", 2)[1]
                    .split("\n")[0].split("\\|")) {
                if (!baseType.isEmpty()) {
                    userType.mBaseType.add(baseType);
                }
            }

            userType.mEnabled = Boolean.parseBoolean(
                    userTypeString.split("mEnabled: ", 2)[1].split("\n")[0]);
            userType.mMaxAllowed = Integer.parseInt(
                    userTypeString.split("mMaxAllowed: ", 2)[1].split("\n")[0]);
            userType.mMaxAllowedPerParent = Integer.parseInt(
                    userTypeString.split("mMaxAllowedPerParent: ", 2)[1].split("\n")[0]);

            return userType;
        } catch (IndexOutOfBoundsException e) {
            throw new AdbParseException("Error parsing userType", userTypeString, e);
        }
    }
}