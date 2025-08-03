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

package com.android.adservices.service.topics.classifier;

import static com.android.adservices.service.topics.classifier.Preprocessor.limitDescriptionSize;
import static com.android.adservices.service.topics.classifier.Preprocessor.preprocessAppDescription;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.topics.classifier.ClassifierInputConfig.ClassifierInputField;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableList;

import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Manager class to compose classifier input from app info given input config and package name. */
public class ClassifierInputManager {
    private static final String EMPTY_STRING = "";
    private static ClassifierInputManager sSingleton;
    private final Context mContext;
    private final Preprocessor mPreprocessor;

    ClassifierInputManager(@NonNull Context context, @NonNull Preprocessor preprocessor) {
        mContext = context.getApplicationContext();
        mPreprocessor = preprocessor;
    }

    /** Returns the singleton instance of the {@link ClassifierInputManager} given a context. */
    @NonNull
    public static ClassifierInputManager getInstance(@NonNull Context context) {
        synchronized (ClassifierInputManager.class) {
            if (sSingleton == null) {
                sSingleton = new ClassifierInputManager(context, new Preprocessor(context));
            }
        }
        return sSingleton;
    }

    /**
     * Composes the classifier input for the given config and package name.
     *
     * @param classifierInputConfig Config containing the classifier input format and input fields.
     * @param packageName The package name for which input will be created.
     * @return A classifier input string containing the given fields' values in the given format.
     */
    public String getClassifierInput(
            @NonNull ClassifierInputConfig classifierInputConfig, @NonNull String packageName) {
        String inputFormat = classifierInputConfig.getInputFormat();
        List<ClassifierInputField> inputFields = classifierInputConfig.getInputFields();
        PackageManager packageManager = mContext.getPackageManager();

        ApplicationInfo applicationInfo = getApplicationInfo(packageManager, packageName);
        if (applicationInfo == null) {
            return EMPTY_STRING;
        }

        List<String> inputFieldValues =
                getInputFieldValues(packageManager, applicationInfo, packageName, inputFields);
        if (inputFieldValues == null) {
            return EMPTY_STRING;
        }

        try {
            return String.format(inputFormat, inputFieldValues.toArray());
        } catch (IllegalFormatException e) {
            LogUtil.e("Classifier input config is incorrectly formatted");
            return EMPTY_STRING;
        }
    }

    private ApplicationInfo getApplicationInfo(PackageManager packageManager, String packageName) {
        try {
            return packageManager.getApplicationInfo(
                    packageName, /* PackageManager.ApplicationInfoFlags = */ 0);
        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.w("No applicationInfo returned from packageManager.");
            return null;
        }
    }

    private List<String> getInputFieldValues(
            PackageManager packageManager,
            ApplicationInfo applicationInfo,
            String packageName,
            List<ClassifierInputField> inputFields) {
        ImmutableList.Builder<String> inputFieldValues =
                ImmutableList.builderWithExpectedSize(inputFields.size());

        for (ClassifierInputField inputField : inputFields) {
            switch (inputField) {
                case PACKAGE_NAME:
                    inputFieldValues.add(packageName);
                    break;
                case SPLIT_PACKAGE_NAME:
                    inputFieldValues.add(getSplitPackageName(packageName));
                    break;
                case APP_NAME:
                    inputFieldValues.add(
                            getAppResource(
                                    packageManager, applicationInfo, applicationInfo.labelRes));
                    break;
                case APP_DESCRIPTION:
                    inputFieldValues.add(
                            getProcessedAppDescription(
                                    getAppResource(
                                            packageManager,
                                            applicationInfo,
                                            applicationInfo.descriptionRes)));
                    break;
                default:
                    LogUtil.e("Invalid input field in config: %s", inputField);
                    return null;
            }
        }

        return inputFieldValues.build();
    }

    private String getSplitPackageName(String packageName) {
        // Split package name into its individual fields and return as a single string.
        // TODO (b/280891778): Refine split package name processing.
        return Stream.of(packageName.split(Pattern.quote(".")))
                .skip(1) // Skip first element, typically the top-level domain (e.g., "com").
                .collect(Collectors.joining(" "));
    }

    private String getProcessedAppDescription(String appDescription) {
        // Preprocess the app description for the classifier.
        appDescription = preprocessAppDescription(appDescription);
        appDescription = mPreprocessor.removeStopWords(appDescription);
        // Limit description size.
        int maxNumberOfWords = FlagsFactory.getFlags().getClassifierDescriptionMaxWords();
        int maxNumberOfCharacters = FlagsFactory.getFlags().getClassifierDescriptionMaxLength();
        appDescription =
                limitDescriptionSize(appDescription, maxNumberOfWords, maxNumberOfCharacters);

        return appDescription;
    }

    private String getAppResource(
            PackageManager packageManager, ApplicationInfo applicationInfo, int resourceId) {
        if (!SdkLevel.isAtLeastS()) {
            LogUtil.d(
                    "English app resource not available for SDK version %d - "
                            + "returning localized app resource",
                    Build.VERSION.SDK_INT);
            return getLocalAppResource(packageManager, applicationInfo, resourceId);
        }

        return getEnglishAppResource(packageManager, applicationInfo, resourceId);
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private String getEnglishAppResource(
            PackageManager packageManager, ApplicationInfo applicationInfo, int resourceId) {
        Configuration configuration = mContext.getResources().getConfiguration();
        configuration.setLocale(Locale.ENGLISH);
        try {
            return packageManager
                    .getResourcesForApplication(applicationInfo, configuration)
                    .getString(resourceId);
        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.e("No resources returned from packageManager.");
        } catch (Resources.NotFoundException e) {
            LogUtil.e("Resource not found by packageManager - resourceId: %d", resourceId);
        }
        return EMPTY_STRING;
    }

    private String getLocalAppResource(
            PackageManager packageManager, ApplicationInfo applicationInfo, int resourceId) {
        try {
            return packageManager.getResourcesForApplication(applicationInfo).getString(resourceId);
        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.e("No resources returned from packageManager.");
        } catch (Resources.NotFoundException e) {
            LogUtil.e("Resource not found by packageManager - resourceId: %d", resourceId);
        }
        return EMPTY_STRING;
    }
}
