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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.topics.classifier.ClassifierInputConfig.ClassifierInputField;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link com.android.adservices.service.topics.classifier.ClassifierInputManager}.
 */
public class ClassifierInputManagerTest {
    private static final String TEST_PACKAGE_NAME = "com.sample.package.name";
    private static final String TEST_APP_NAME = "Name for App";
    private static final CharSequence TEST_APP_DESCRIPTION = "Description for App";

    private static final int DEFAULT_DESCRIPTION_MAX_LENGTH = 2500;
    private static final int DEFAULT_DESCRIPTION_MAX_WORDS = 500;

    private ClassifierInputManager mClassifierInputManager;

    @Mock private PackageManager mPackageManager;
    @Mock private ApplicationInfo mApplicationInfo;
    @Mock private Resources mAppResources;
    @Mock private Resources mContextResources;
    @Mock private Context mContext;
    @Mock private Context mApplicationContext;
    @Mock private Preprocessor mPreprocessor;
    @Mock private Flags mFlags;
    private MockitoSession mStaticMockSession;

    // We are not expecting to launch Topics API on Android R. Hence, skipping this test on
    // Android R since some tests require handling of unsupported PackageManager APIs.
    // TODO(b/290839573) - Remove rule if Topics is enabled on R in the future.
    @Rule public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(SdkLevel.class)
                        .spyStatic(Preprocessor.class)
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .startMocking();

        doReturn(mApplicationContext).when(mContext).getApplicationContext();
        doReturn(mPackageManager).when(mApplicationContext).getPackageManager();
        doReturn(mApplicationInfo)
                .when(mPackageManager)
                .getApplicationInfo(any(String.class), anyInt());
        doReturn(mAppResources)
                .when(mPackageManager)
                .getResourcesForApplication(any(ApplicationInfo.class), any(Configuration.class));

        doReturn(mContextResources).when(mApplicationContext).getResources();
        doReturn(new Configuration()).when(mContextResources).getConfiguration();

        // Mock default flag values and return mocked flags from factory.
        doReturn(DEFAULT_DESCRIPTION_MAX_LENGTH).when(mFlags).getClassifierDescriptionMaxLength();
        doReturn(DEFAULT_DESCRIPTION_MAX_WORDS).when(mFlags).getClassifierDescriptionMaxWords();
        doReturn(mFlags).when(FlagsFactory::getFlags);

        // Skip preprocessing by default.
        doAnswer(returnsFirstArg()).when(() -> Preprocessor.preprocessAppDescription(anyString()));
        doAnswer(returnsFirstArg()).when(mPreprocessor).removeStopWords(anyString());
        doAnswer(returnsFirstArg())
                .when(() -> Preprocessor.limitDescriptionSize(anyString(), anyInt(), anyInt()));

        mClassifierInputManager = new ClassifierInputManager(mContext, mPreprocessor);
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testGetClassifierInput_packageName() {
        ClassifierInputConfig classifierInputConfig =
                new ClassifierInputConfig(
                        "%s", ImmutableList.of(ClassifierInputField.PACKAGE_NAME));

        String classifierInput =
                mClassifierInputManager.getClassifierInput(
                        classifierInputConfig, TEST_PACKAGE_NAME);

        assertThat(classifierInput).isEqualTo(TEST_PACKAGE_NAME);
    }

    @Test
    public void testGetClassifierInput_splitPackageName() {
        ClassifierInputConfig classifierInputConfig =
                new ClassifierInputConfig(
                        "%s", ImmutableList.of(ClassifierInputField.SPLIT_PACKAGE_NAME));

        String classifierInput =
                mClassifierInputManager.getClassifierInput(
                        classifierInputConfig, TEST_PACKAGE_NAME);

        assertThat(classifierInput).isEqualTo("sample package name");
    }

    @Test
    public void testGetClassifierInput_appName() {
        doReturn(TEST_APP_NAME).when(mAppResources).getString(anyInt());

        ClassifierInputConfig classifierInputConfig =
                new ClassifierInputConfig("%s", ImmutableList.of(ClassifierInputField.APP_NAME));

        String classifierInput =
                mClassifierInputManager.getClassifierInput(
                        classifierInputConfig, TEST_PACKAGE_NAME);

        assertThat(classifierInput).isEqualTo(TEST_APP_NAME);
    }

    @Test
    public void testGetClassifierInput_appDescription() {
        doReturn(TEST_APP_DESCRIPTION).when(mAppResources).getString(anyInt());

        ClassifierInputConfig classifierInputConfig =
                new ClassifierInputConfig(
                        "%s", ImmutableList.of(ClassifierInputField.APP_DESCRIPTION));

        String classifierInput =
                mClassifierInputManager.getClassifierInput(
                        classifierInputConfig, TEST_PACKAGE_NAME);

        assertThat(classifierInput).isEqualTo(TEST_APP_DESCRIPTION.toString());
    }

    @Test
    public void testGetClassifierInput_multipleFields() {
        doReturn(TEST_APP_NAME, TEST_APP_DESCRIPTION).when(mAppResources).getString(anyInt());

        String classifierInputFormat = "%s %s -> %s";
        ClassifierInputConfig classifierInputConfig =
                new ClassifierInputConfig(
                        classifierInputFormat,
                        ImmutableList.of(
                                ClassifierInputField.PACKAGE_NAME,
                                ClassifierInputField.APP_NAME,
                                ClassifierInputField.APP_DESCRIPTION));

        String classifierInput =
                mClassifierInputManager.getClassifierInput(
                        classifierInputConfig, TEST_PACKAGE_NAME);

        assertThat(classifierInput)
                .isEqualTo(
                        String.format(
                                classifierInputFormat,
                                TEST_PACKAGE_NAME,
                                TEST_APP_NAME,
                                TEST_APP_DESCRIPTION));
    }

    @Test
    public void testGetClassifierInput_applicationInfoNotFound_returnsEmptyInput()
            throws NameNotFoundException {
        doThrow(new NameNotFoundException())
                .when(mPackageManager)
                .getApplicationInfo(any(String.class), anyInt());

        ClassifierInputConfig classifierInputConfig =
                new ClassifierInputConfig(
                        "%s", ImmutableList.of(ClassifierInputField.PACKAGE_NAME));

        String classifierInput =
                mClassifierInputManager.getClassifierInput(
                        classifierInputConfig, TEST_PACKAGE_NAME);

        assertThat(classifierInput).isEmpty();
    }

    @Test
    public void testGetClassifierInput_invalidInputFormat_returnsEmptyInput() {
        ClassifierInputConfig classifierInputConfig =
                new ClassifierInputConfig(
                        "%s %s", ImmutableList.of(ClassifierInputField.PACKAGE_NAME));

        String classifierInput =
                mClassifierInputManager.getClassifierInput(
                        classifierInputConfig, TEST_PACKAGE_NAME);

        assertThat(classifierInput).isEmpty();
    }

    @Test
    public void testGetClassifierInput_oldSdkVersion_returnsLocalAppResource()
            throws NameNotFoundException {
        String localAppName = "Local Name for App";
        Resources resources = mock(Resources.class);

        doReturn(false).when(SdkLevel::isAtLeastS);
        doReturn(localAppName).when(resources).getString(anyInt());
        doReturn(resources)
                .when(mPackageManager)
                .getResourcesForApplication(any(ApplicationInfo.class));

        ClassifierInputConfig classifierInputConfig =
                new ClassifierInputConfig("%s", ImmutableList.of(ClassifierInputField.APP_NAME));

        String classifierInput =
                mClassifierInputManager.getClassifierInput(
                        classifierInputConfig, TEST_PACKAGE_NAME);

        assertThat(classifierInput).isEqualTo(localAppName);
    }
}
