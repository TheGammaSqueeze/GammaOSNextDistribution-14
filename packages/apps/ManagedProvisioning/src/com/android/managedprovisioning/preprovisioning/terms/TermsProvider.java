/*
 * Copyright 2017, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.preprovisioning.terms;

import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DISCLAIMER_CONTENT;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DISCLAIMER_HEADER;

import static java.util.Objects.requireNonNull;

import android.annotation.IntDef;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.StoreUtils;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.DisclaimersParam;
import com.android.managedprovisioning.model.ProvisioningParams;

import com.google.android.setupdesign.util.DeviceHelper;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Sources all available {@link TermsDocument}s:
 * <ul>
 * <li> hardcoded 'General' terms,
 * <li> terms exposed via installed apps,
 * <li> terms passed from DPC.
 * </ul>
 */
public class TermsProvider {
    private final Context mContext;
    private final StoreUtils.TextFileReader mTextFileReader;
    private final ProvisioningParams mParams;
    private final Utils mUtils;
    private final Injector mInjector;

    /**
     * Sources all available {@link TermsDocument}s:
     * <ul>
     * <li> hardcoded 'General' terms,
     * <li> terms exposed via installed apps,
     * <li> terms passed from DPC.
     * </ul>
     */
    public TermsProvider(Context context, StoreUtils.TextFileReader textFileReader,
            ProvisioningParams params, Utils utils, Injector injector) {
        mContext = requireNonNull(context);
        mTextFileReader = requireNonNull(textFileReader);
        mParams = requireNonNull(params);
        mUtils = requireNonNull(utils);
        mInjector = requireNonNull(injector);
    }

    /**
     * Sources all available {@link TermsDocument}s:
     * <ul>
     * <li> hardcoded 'General' terms,
     * <li> terms exposed via installed apps,
     * <li> terms passed from DPC.
     * </ul>
     */
    public List<TermsDocument> getTerms() {
        List<TermsDocument> result = new ArrayList<>();
        int provisioningCase = determineProvisioningCase(mParams);

        if (provisioningCase == ProvisioningCase.DEVICE_OWNER) {
            result.addAll(getSystemAppTerms());
        }

        result.addAll(getExtraDisclaimers(mParams));

        return result.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * Returns a generic disclaimer relative to the provisioning mode.
     */
    public TermsDocument getGeneralDisclaimer() {
        int provisioningCase = determineProvisioningCase(mParams);
        CharSequence deviceName = DeviceHelper.getDeviceName(mContext);
        String heading =
                mContext.getString(
                        provisioningCase == ProvisioningCase.PROFILE_OWNER
                                ? R.string.work_profile_info
                                : R.string.managed_device_info);
        String content =
                mContext.getString(
                        provisioningCase == ProvisioningCase.PROFILE_OWNER
                                ? R.string.admin_has_ability_to_monitor_profile
                                : R.string.admin_has_ability_to_monitor_device,
                        deviceName);
        return TermsDocument.createInstance(heading, content);
    }

    private int determineProvisioningCase(ProvisioningParams params) {
        if (mUtils.isDeviceOwnerAction(params.provisioningAction)) {
            return ProvisioningCase.DEVICE_OWNER;
        }
        return ProvisioningCase.PROFILE_OWNER;
    }

    private List<TermsDocument> getSystemAppTerms() {
        List<TermsDocument> terms = new ArrayList<>();
        List<ApplicationInfo> appInfos = mInjector.getInstalledApplications();
        for (ApplicationInfo appInfo : appInfos) {
            String header = getStringMetaData(appInfo, EXTRA_PROVISIONING_DISCLAIMER_HEADER);
            String content = getStringMetaData(appInfo, EXTRA_PROVISIONING_DISCLAIMER_CONTENT);
            if (header != null && content != null) {
                terms.add(TermsDocument.createInstance(header, content));
            }
        }
        return terms;
    }

    private List<TermsDocument> getExtraDisclaimers(ProvisioningParams params) {
        List<TermsDocument> result = new ArrayList<>();

        DisclaimersParam.Disclaimer[] disclaimers = params.disclaimersParam == null ? null
                : params.disclaimersParam.mDisclaimers;
        if (disclaimers != null) {
            for (DisclaimersParam.Disclaimer disclaimer : disclaimers) {
                try {
                    String htmlContent = mTextFileReader.read(
                            new File(disclaimer.mContentFilePath));
                    result.add(TermsDocument.createInstance(disclaimer.mHeader, htmlContent));
                } catch (IOException e) {
                    ProvisionLogger.loge("Failed to read disclaimer", e);
                }
            }
        }

        return result;
    }

    private String getStringMetaData(ApplicationInfo appInfo, String key) {
        if (appInfo.metaData != null) {
            int resId = appInfo.metaData.getInt(key);
            if (resId != 0) {
                try {
                    return mContext.getPackageManager().getResourcesForApplication(
                            appInfo).getString(resId);
                } catch (PackageManager.NameNotFoundException | Resources.NotFoundException e) {
                    ProvisionLogger.loge("NameNotFoundException", e);
                }
            }
        }
        return null;
    }

    // TODO: move somewhere more general
    @IntDef(value = {
            ProvisioningCase.PROFILE_OWNER,
            ProvisioningCase.DEVICE_OWNER,
    })
    @Retention(RetentionPolicy.SOURCE)
    private @interface ProvisioningCase {
        int PROFILE_OWNER = 1;
        int DEVICE_OWNER = 2;
    }

    interface Injector {
        List<ApplicationInfo> getInstalledApplications();
    }
}
