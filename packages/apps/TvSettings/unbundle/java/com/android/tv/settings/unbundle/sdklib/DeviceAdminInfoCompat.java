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

import android.app.admin.DeviceAdminInfo;
import android.content.Context;
import android.content.pm.ActivityInfo;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DeviceAdminInfoCompat {
    public DeviceAdminInfo mDeviceAdminInfo;

    public DeviceAdminInfoCompat(DeviceAdminInfo deviceAdminInfo) {
        mDeviceAdminInfo = deviceAdminInfo;
    }

    public DeviceAdminInfoCompat(Context context, ActivityInfo activityInfo)
            throws XmlPullParserException, IOException {
        mDeviceAdminInfo = new DeviceAdminInfo(context, activityInfo);
    }

    public List<PolicyInfo> getUsedPolicies() {
        List<DeviceAdminInfo.PolicyInfo> obj = mDeviceAdminInfo.getUsedPolicies();
        List<PolicyInfo> result = new ArrayList<>();
        for (Object o : obj) {
            result.add(new PolicyInfo(o));
        }
        return result;
    }

    public static class PolicyInfo {
        private final Object mPolicyInfo;

        public final int mIdent;
        public final int mLabel;
        public final int mDescription;
        public final int mLabelForSecondaryUsers;
        public final int mDescriptionForSecondaryUsers;

        public PolicyInfo(Object policyInfo) {
            mPolicyInfo = policyInfo;
            mIdent = ((DeviceAdminInfo.PolicyInfo) policyInfo).ident;
            mLabel = ((DeviceAdminInfo.PolicyInfo) policyInfo).label;
            mDescription = ((DeviceAdminInfo.PolicyInfo) policyInfo).description;
            mLabelForSecondaryUsers =
                    ((DeviceAdminInfo.PolicyInfo) policyInfo).labelForSecondaryUsers;
            mDescriptionForSecondaryUsers =
                    ((DeviceAdminInfo.PolicyInfo) policyInfo).descriptionForSecondaryUsers;
        }
    }
}
