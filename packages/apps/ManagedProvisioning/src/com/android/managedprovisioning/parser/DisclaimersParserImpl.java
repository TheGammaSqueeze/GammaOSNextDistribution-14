/*
 * Copyright 2016, The Android Open Source Project
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
package com.android.managedprovisioning.parser;

import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DISCLAIMER_CONTENT;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DISCLAIMER_HEADER;
import static com.android.managedprovisioning.common.StoreUtils.DIR_PROVISIONING_PARAMS_FILE_CACHE;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import android.os.Binder;

import android.text.TextUtils;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.StoreUtils;
import com.android.managedprovisioning.model.DisclaimersParam;
import com.android.managedprovisioning.model.DisclaimersParam.Disclaimer;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Parser for {@link EXTRA_PROVISIONING_DISCLAIMERS} into {@link DisclaimersParam}
 * It also saves the disclaimer content into files
 */
public class DisclaimersParserImpl implements DisclaimerParser {
    private static final int MAX_LENGTH = 3;
    private static final String SCHEME_ANDROID_RESOURCE = "android.resource";
    private static final String SCHEME_CONTENT = "content";


    private final Context mContext;
    private final long mProvisioningId;
    private final File mDisclaimerDir;

    public DisclaimersParserImpl(Context context, long provisioningId) {
        mContext = context;
        mProvisioningId = provisioningId;
        mDisclaimerDir =  new File(mContext.getFilesDir(), DIR_PROVISIONING_PARAMS_FILE_CACHE);
    }

    @Nullable
    public DisclaimersParam parse(Parcelable[] parcelables) throws ClassCastException {
        if (parcelables == null) {
            return null;
        }

        List<Disclaimer> disclaimers = new ArrayList<>(MAX_LENGTH);
        for (int i = 0; i < parcelables.length; i++) {
            // maximum 3 disclaimers are accepted in the EXTRA_PROVISIONING_DISCLAIMERS API
            if (disclaimers.size() >= MAX_LENGTH) {
                break;
            }
            final Bundle disclaimerBundle = (Bundle) parcelables[i];
            final String header = disclaimerBundle.getString(EXTRA_PROVISIONING_DISCLAIMER_HEADER);
            final Uri uri = disclaimerBundle.getParcelable(EXTRA_PROVISIONING_DISCLAIMER_CONTENT);
            if (TextUtils.isEmpty(header)) {
                ProvisionLogger.logw("Empty disclaimer header in " + i + " element");
                continue;
            }

            if (uri == null) {
                ProvisionLogger.logw("Null disclaimer content uri in " + i + " element");
                continue;
            }
            try {
                validateUriSchemeAndPermission(uri);
            } catch (SecurityException e) {
                ProvisionLogger.loge(
                    "Skipping disclaimer in "
                        + i
                        + " element due to URI validation failure: "
                        + e.getMessage(),
                    e);
                continue;
            }
            File disclaimerFile = saveDisclaimerContentIntoFile(uri, i);

            if (disclaimerFile == null) {
                ProvisionLogger.logw("Failed to copy disclaimer uri in " + i + " element");
                continue;
            }

            disclaimers.add(new Disclaimer(header, disclaimerFile.getPath()));
        }
        return disclaimers.isEmpty() ? null : new DisclaimersParam.Builder()
                .setDisclaimers(disclaimers.toArray(new Disclaimer[disclaimers.size()])).build();
    }

    /**
     * Validates a {@link Uri} extra pointing to disclaimer content.
     *
     * <p>It checks that the URI scheme is one of {@code content} ({@link
     * android.content.ContentResolver#SCHEME_CONTENT}) or {@code
     * android.resource} ({@link
     * android.content.ContentResolver#SCHEME_ANDROID_RESOURCE}). If a {@code
     * content:} URI is passed, it also checks that the caller has grant read
     * permission ({@link Intent#FLAG_GRANT_READ_URI_PERMISSION}).
     *
     * @throws SecurityException if the URI scheme is invalid or the caller
     * does not have permission to access the URI.
     */
    private void validateUriSchemeAndPermission(Uri uri) throws SecurityException {
        ProvisionLogger.logd("validateUriSchemeAndPermission: " + uri);
        String scheme = uri.getScheme();
        if (!Objects.equals(scheme, SCHEME_ANDROID_RESOURCE)
            && !Objects.equals(scheme, SCHEME_CONTENT)) {
            String errorMessage = "Invalid URI scheme: " + scheme;
            throw new SecurityException(errorMessage);
        }
        int permissionCheck =
            mContext.checkUriPermission(
                uri,
                Binder.getCallingPid(),
                Binder.getCallingUid(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            String errorMessage = "Caller does not have permission to access"
                + " disclaimer URI: " + uri;
            throw new SecurityException(errorMessage);
        }
    }

    /**
     * @return {@link File} if the uri content is saved into the file successfully. Otherwise,
     * return null.
     */
    private File saveDisclaimerContentIntoFile(Uri uri, int index) {
        if (!mDisclaimerDir.exists()) {
            mDisclaimerDir.mkdirs();
        }

        String filename = "disclaimer_content_" + mProvisioningId + "_" + index + ".txt";
        File outputFile = new File(mDisclaimerDir, filename);

        boolean success = StoreUtils.copyUriIntoFile(mContext.getContentResolver(), uri,
                outputFile);
        return success ? outputFile : null;
    }
}
