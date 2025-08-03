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

package com.android.permissioncontroller.role.ui.behavior;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.nfc.cardemulation.ApduServiceInfo;
import android.nfc.cardemulation.CardEmulation;
import android.nfc.cardemulation.HostApduService;
import android.nfc.cardemulation.OffHostApduService;
import android.os.Build;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.util.Pair;
import androidx.preference.Preference;

import com.android.permissioncontroller.role.ui.TwoTargetPreference;
import com.android.role.controller.model.Role;
import com.android.role.controller.util.UserUtils;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/***
 * Class for UI behavior of Wallet role
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
public class WalletRoleUiBehavior implements RoleUiBehavior {

    private static final String LOG_TAG = WalletRoleUiBehavior.class.getSimpleName();

    @Override
    public void preparePreferenceAsUser(@NonNull Role role, @NonNull TwoTargetPreference preference,
            @NonNull List<ApplicationInfo> applicationInfos, @NonNull UserHandle user,
            @NonNull Context context) {
        Context userContext = UserUtils.getUserContext(context, user);
        if (!applicationInfos.isEmpty()) {
            preparePreferenceInternal(preference.asPreference(), applicationInfos.get(0),
                    false, user, userContext);
        }
    }

    @Override
    public void prepareApplicationPreferenceAsUser(@NonNull Role role,
            @NonNull Preference preference, @NonNull ApplicationInfo applicationInfo,
            @NonNull UserHandle user, @NonNull Context context) {
        Context userContext = UserUtils.getUserContext(context, user);
        preparePreferenceInternal(preference, applicationInfo, true, user, userContext);
    }

    private void preparePreferenceInternal(@NonNull Preference preference,
            @NonNull ApplicationInfo applicationInfo, boolean setTitle, @NonNull UserHandle user,
            @NonNull Context context) {
        if (isSystemApplication(applicationInfo)) {
            List<ApduServiceInfo> serviceInfos = getNfcServicesForPackage(
                    applicationInfo.packageName, user, context);

            Pair<Drawable, CharSequence> bannerAndLabel =
                    getNonPaymentServiceBannerAndLabelIfExists(serviceInfos, user, context);
            if (bannerAndLabel != null) {
                preference.setIcon(bannerAndLabel.first);
                if (setTitle) {
                    preference.setTitle(bannerAndLabel.second);
                } else {
                    preference.setSummary(bannerAndLabel.second);
                }
            }
        }
    }

    @NonNull
    private static List<ApduServiceInfo> getNfcServicesForPackage(@NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent hostApduIntent = new Intent(HostApduService.SERVICE_INTERFACE);
        Intent offHostApduIntent = new Intent(OffHostApduService.SERVICE_INTERFACE);
        hostApduIntent.setPackage(packageName);
        offHostApduIntent.setPackage(packageName);
        List<ResolveInfo> hostApduServices = packageManager.queryIntentServicesAsUser(
                hostApduIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA
                        | PackageManager.MATCH_DISABLED_COMPONENTS), user);
        List<ResolveInfo> offHostApduServices = packageManager.queryIntentServicesAsUser(
                offHostApduIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA
                        | PackageManager.MATCH_DISABLED_COMPONENTS), user);
        List<ApduServiceInfo> nfcServices = new ArrayList<>();
        int apduServiceInfoSize = hostApduServices.size();
        for (int i = 0; i < apduServiceInfoSize; i++) {
            ResolveInfo resolveInfo = hostApduServices.get(i);
            ApduServiceInfo apduServiceInfo;
            try {
                apduServiceInfo = new ApduServiceInfo(packageManager, resolveInfo, true);
            } catch (XmlPullParserException | IOException e) {
                Log.e(LOG_TAG, "Error creating the apduserviceinfo.", e);
                continue;
            }
            nfcServices.add(apduServiceInfo);
        }
        int offHostApduServiceInfoSize = offHostApduServices.size();
        for (int i = 0; i < offHostApduServiceInfoSize; i++) {
            ResolveInfo resolveInfo = offHostApduServices.get(i);
            ApduServiceInfo apduServiceInfo;
            try {
                apduServiceInfo = new ApduServiceInfo(packageManager, resolveInfo, false);
            } catch (XmlPullParserException | IOException e) {
                Log.e(LOG_TAG, "Error creating the apduserviceinfo.", e);
                continue;
            }
            nfcServices.add(apduServiceInfo);
        }
        return nfcServices;
    }

    @Nullable
    private Pair<Drawable, CharSequence> getNonPaymentServiceBannerAndLabelIfExists(
            @NonNull List<ApduServiceInfo> apduServiceInfos, @NonNull UserHandle user,
            @NonNull Context context) {
        Context userContext = UserUtils.getUserContext(context, user);
        PackageManager userPackageManager = userContext.getPackageManager();
        Pair<Drawable, CharSequence> bannerAndLabel;
        int apduServiceInfoSize = apduServiceInfos.size();
        for (int i = 0; i < apduServiceInfoSize; i++) {
            ApduServiceInfo serviceInfo = apduServiceInfos.get(i);
            if (serviceInfo.getAids().isEmpty()) {
                bannerAndLabel = loadBannerAndLabel(serviceInfo, userPackageManager);
                if (bannerAndLabel != null) {
                    return bannerAndLabel;
                }
            } else {
                List<String> aids = serviceInfo.getAids();
                int aidsSize = aids.size();
                for (int j = 0; j < aidsSize; j++) {
                    String aid = aids.get(j);
                    String category = serviceInfo.getCategoryForAid(aid);
                    if (!CardEmulation.CATEGORY_PAYMENT.equals(category)) {
                        bannerAndLabel = loadBannerAndLabel(serviceInfo, userPackageManager);
                        if (bannerAndLabel != null) {
                            return bannerAndLabel;
                        }
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private Pair<Drawable, CharSequence> loadBannerAndLabel(@NonNull ApduServiceInfo info,
            @NonNull PackageManager userPackageManager) {
        Drawable drawable = info.loadBanner(userPackageManager);
        CharSequence label = info.loadLabel(userPackageManager);
        if (drawable != null && !TextUtils.isEmpty(label)) {
            return new Pair<>(drawable, label);
        } else {
            return null;
        }
    }

    private static boolean isSystemApplication(@NonNull ApplicationInfo applicationInfo) {
        return (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }
}
