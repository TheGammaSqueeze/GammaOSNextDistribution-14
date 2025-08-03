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

package com.android.car.settings.inputmethod;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class InputMethodUtilTest {
    private static final String PLACEHOLDER_PACKAGE_NAME = "placeholder package name";
    private static final String PLACEHOLDER_LABEL = "placeholder label";
    private static final String PLACEHOLDER_SETTINGS_ACTIVITY = "placeholder settings activity";
    private static final String SUBTYPES_STRING =
            "English (United States), German (Belgium), and Occitan (France)";
    private static final String PLACEHOLDER_ENABLED_INPUT_METHODS =
            "com.google.android.googlequicksearchbox/com.google.android.voicesearch.ime"
                    + ".VoiceInputMethodService:com.google.android.apps.automotive.inputmethod/"
                    + ".InputMethodService";
    private static final String PLACEHOLDER_ENABLED_INPUT_METHOD_ID_DEFAULT =
            "com.google.android.apps.automotive.inputmethod/.InputMethodService";
    private static final String PLACEHOLDER_ENABLED_INPUT_METHOD_ID =
            "com.google.android.googlequicksearchbox/com.google.android.voicesearch.ime"
                    + ".VoiceInputMethodService";
    private static final String PLACEHOLDER_DISABLED_INPUT_METHOD_ID = "disabled input method id";
    private Context mContext;
    private List<InputMethodInfo> mPlaceholderEnabledInputMethodsListAllDefaultable;
    private List<InputMethodInfo> mPlaceholderEnabledInputMethodsListOneDefaultable;

    @Mock
    private PackageManager mPackageManager;
    @Mock
    private InputMethodManager mInputMethodManager;
    @Mock
    private DevicePolicyManager mDevicePolicyManager;
    @Mock
    private Drawable mIcon;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = ApplicationProvider.getApplicationContext();

        Settings.Secure.putString(mContext.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS, PLACEHOLDER_ENABLED_INPUT_METHODS);
        Settings.Secure.putString(mContext.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD, PLACEHOLDER_ENABLED_INPUT_METHOD_ID_DEFAULT);

        mPlaceholderEnabledInputMethodsListOneDefaultable = Arrays
                .stream(PLACEHOLDER_ENABLED_INPUT_METHODS.split(
                        String.valueOf(InputMethodUtil.INPUT_METHOD_DELIMITER)))
                .collect(Collectors.toList())
                .stream().map(
                        result -> {
                            InputMethodInfo info = createMockInputMethodInfo(
                                    mPackageManager, PLACEHOLDER_PACKAGE_NAME);
                            when(info.getId()).thenReturn(result);
                            when(info.isDefault(mContext)).thenReturn(result.equals(
                                    PLACEHOLDER_ENABLED_INPUT_METHOD_ID_DEFAULT));
                            return info;
                        })
                .collect(Collectors.toList());
        mPlaceholderEnabledInputMethodsListAllDefaultable = Arrays
                .stream(PLACEHOLDER_ENABLED_INPUT_METHODS.split(String.valueOf(InputMethodUtil
                        .INPUT_METHOD_DELIMITER)))
                .collect(Collectors.toList())
                .stream().map(
                        result -> {
                            InputMethodInfo info = createMockInputMethodInfo(
                                    mPackageManager, PLACEHOLDER_PACKAGE_NAME);
                            when(info.getId()).thenReturn(result);
                            when(info.isDefault(mContext)).thenReturn(true);
                            return info;
                        })
                .collect(Collectors.toList());
    }

    @Test
    public void getPermittedAndEnabledInputMethodList_noEnabledInputMethods_returnsNull() {
        when(mInputMethodManager.getEnabledInputMethodList()).thenReturn(null);

        List<InputMethodInfo> results = InputMethodUtil.getPermittedAndEnabledInputMethodList(
                mInputMethodManager, mDevicePolicyManager);

        assertThat(results).isNull();
    }

    @Test
    public void getPermittedAndEnabledInputMethodList_noPermittedMethods_returnsEmptyList() {
        String secondPlaceholderPackageName = "Different package";
        InputMethodInfo info = createMockInputMethodInfoWithSubtypes(
                mPackageManager, mInputMethodManager, PLACEHOLDER_PACKAGE_NAME);
        when(mInputMethodManager.getEnabledInputMethodList())
                .thenReturn(Collections.singletonList(info));
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser())
                .thenReturn(Collections.singletonList(secondPlaceholderPackageName));

        List<InputMethodInfo> results = InputMethodUtil.getPermittedAndEnabledInputMethodList(
                mInputMethodManager, mDevicePolicyManager);

        assertThat(results).isNotNull();
        assertThat(results).isEmpty();
    }

    @Test
    public void getPermittedAndEnabledInputMethodList_withGoogleTypingIME_doesNotIncludeIME() {
        List<InputMethodInfo> googleVoiceTypingIMEList = new ArrayList<>();

        InputMethodInfo placeholderIME = createMockInputMethodInfoWithSubtypes(
                mPackageManager, mInputMethodManager, PLACEHOLDER_PACKAGE_NAME);

        for (String gvtPackageName : InputMethodUtil.GVT_PACKAGE_NAMES) {
            InputMethodInfo googleVoiceTypingIME = createMockInputMethodInfoWithSubtypes(
                    mPackageManager, mInputMethodManager, gvtPackageName);
            googleVoiceTypingIMEList.add(googleVoiceTypingIME);
        }
        when(mInputMethodManager.getEnabledInputMethodList())
                .thenReturn(
                        new ArrayList<InputMethodInfo>(googleVoiceTypingIMEList) {{
                            add(placeholderIME);
                        }});
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);

        List<InputMethodInfo> results = InputMethodUtil.getPermittedAndEnabledInputMethodList(
                mInputMethodManager, mDevicePolicyManager);

        assertThat(results).contains(placeholderIME);

        // Check that the result does not contain any of the statically defined Voice Typing IMEs
        for (InputMethodInfo googleVoiceTypingIME : googleVoiceTypingIMEList) {
            assertThat(results).doesNotContain(googleVoiceTypingIME);
        }
    }

    @Test
    public void getPackageIcon_hasApplicationIcon() throws PackageManager.NameNotFoundException {
        InputMethodInfo info = createMockInputMethodInfoWithSubtypes(mPackageManager,
                mInputMethodManager, PLACEHOLDER_PACKAGE_NAME);
        when(mPackageManager.getApplicationIcon(eq(info.getPackageName()))).thenReturn(mIcon);
        assertThat(InputMethodUtil.getPackageIcon(mPackageManager, info)).isEqualTo(mIcon);
    }

    @Test
    public void getPackageIcon_noApplicationIcon() throws PackageManager.NameNotFoundException {
        InputMethodInfo info = createMockInputMethodInfoWithSubtypes(mPackageManager,
                mInputMethodManager, PLACEHOLDER_PACKAGE_NAME);
        when(mPackageManager.getApplicationIcon(PLACEHOLDER_PACKAGE_NAME)).thenThrow(
                new PackageManager.NameNotFoundException());
        assertThat(InputMethodUtil.getPackageIcon(mPackageManager, info)).isEqualTo(
                InputMethodUtil.NO_ICON);
    }

    @Test
    public void getPackageLabel() {
        InputMethodInfo info = createMockInputMethodInfoWithSubtypes(mPackageManager,
                mInputMethodManager, PLACEHOLDER_PACKAGE_NAME);
        assertThat(InputMethodUtil.getPackageLabel(mPackageManager, info)).isEqualTo(
                PLACEHOLDER_LABEL);
    }

    @Test
    public void getSummaryString() {
        InputMethodInfo info = createMockInputMethodInfoWithSubtypes(mPackageManager,
                mInputMethodManager, PLACEHOLDER_PACKAGE_NAME);
        assertThat(InputMethodUtil.getSummaryString(mContext, mInputMethodManager, info)).isEqualTo(
                SUBTYPES_STRING);
    }

    @Test
    public void isInputMethodEnabled_isDisabled_returnsFalse() {
        InputMethodInfo info = createMockInputMethodInfo(mPackageManager, PLACEHOLDER_PACKAGE_NAME);
        when(info.getId()).thenReturn(PLACEHOLDER_DISABLED_INPUT_METHOD_ID);

        assertThat(InputMethodUtil.isInputMethodEnabled(mContext.getContentResolver(), info))
                .isFalse();
    }

    @Test
    public void isInputMethodEnabled_isEnabled_returnsTrue() {
        InputMethodInfo info = createMockInputMethodInfo(mPackageManager, PLACEHOLDER_PACKAGE_NAME);
        when(info.getId()).thenReturn(PLACEHOLDER_ENABLED_INPUT_METHOD_ID_DEFAULT);

        assertThat(InputMethodUtil.isInputMethodEnabled(mContext.getContentResolver(), info))
                .isTrue();
    }

    @Test
    public void enableInputMethod_alreadyEnabled_remainsUnchanged() {
        InputMethodInfo info = createMockInputMethodInfo(mPackageManager, PLACEHOLDER_PACKAGE_NAME);
        when(info.getId()).thenReturn(PLACEHOLDER_ENABLED_INPUT_METHOD_ID_DEFAULT);

        InputMethodUtil.enableInputMethod(mContext.getContentResolver(), info);

        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS)).isEqualTo(
                PLACEHOLDER_ENABLED_INPUT_METHODS);
    }

    @Test
    public void enableInputMethod_noEnabledInputMethods_addsIME() {
        Settings.Secure.putString(mContext.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS, "");
        InputMethodInfo info = createMockInputMethodInfo(mPackageManager, PLACEHOLDER_PACKAGE_NAME);
        when(info.getId()).thenReturn(PLACEHOLDER_ENABLED_INPUT_METHOD_ID_DEFAULT);

        InputMethodUtil.enableInputMethod(mContext.getContentResolver(), info);

        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS)).isEqualTo(
                PLACEHOLDER_ENABLED_INPUT_METHOD_ID_DEFAULT);
    }

    @Test
    public void enableInputMethod_someEnabledInputMethods_addsIME() {
        InputMethodInfo info = createMockInputMethodInfo(mPackageManager, PLACEHOLDER_PACKAGE_NAME);
        when(info.getId()).thenReturn(PLACEHOLDER_DISABLED_INPUT_METHOD_ID);

        InputMethodUtil.enableInputMethod(mContext.getContentResolver(), info);

        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS)).isEqualTo(
                PLACEHOLDER_ENABLED_INPUT_METHODS + ":"
                        + PLACEHOLDER_DISABLED_INPUT_METHOD_ID);
    }

    @Test
    public void disableInputMethod_notEnabled_remainsUnchanged() {
        InputMethodInfo info = createMockInputMethodInfo(mPackageManager, PLACEHOLDER_PACKAGE_NAME);
        when(info.getId()).thenReturn(PLACEHOLDER_DISABLED_INPUT_METHOD_ID);
        when(mInputMethodManager.getEnabledInputMethodList())
                .thenReturn(mPlaceholderEnabledInputMethodsListAllDefaultable);

        InputMethodUtil.disableInputMethod(mContext, mInputMethodManager, info);

        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS)).isEqualTo(
                PLACEHOLDER_ENABLED_INPUT_METHODS);
        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD)).isEqualTo(
                PLACEHOLDER_ENABLED_INPUT_METHOD_ID_DEFAULT);
    }

    @Test
    public void disableInputMethod_notDefault_removesIMEWhileDefaultRemainsSame() {
        InputMethodInfo info = createMockInputMethodInfo(mPackageManager, PLACEHOLDER_PACKAGE_NAME);
        when(info.getId()).thenReturn(PLACEHOLDER_ENABLED_INPUT_METHOD_ID);
        when(mInputMethodManager.getEnabledInputMethodList())
                .thenReturn(mPlaceholderEnabledInputMethodsListAllDefaultable);

        InputMethodUtil.disableInputMethod(mContext, mInputMethodManager, info);

        assertThat(splitConcatenatedIdsIntoSet(Settings.Secure.getString(mContext
                .getContentResolver(), Settings.Secure.ENABLED_INPUT_METHODS))).isEqualTo(
                splitConcatenatedIdsIntoSet(PLACEHOLDER_ENABLED_INPUT_METHOD_ID_DEFAULT));
        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD)).isEqualTo(
                PLACEHOLDER_ENABLED_INPUT_METHOD_ID_DEFAULT);
    }

    @Test
    public void disableInputMethod_twoDefaultableIMEsEnabled_removesIMEAndChangesDefault() {
        InputMethodInfo info = createMockInputMethodInfo(mPackageManager, PLACEHOLDER_PACKAGE_NAME);
        when(info.getId()).thenReturn(PLACEHOLDER_ENABLED_INPUT_METHOD_ID_DEFAULT);
        when(mInputMethodManager.getEnabledInputMethodList())
                .thenReturn(mPlaceholderEnabledInputMethodsListAllDefaultable);

        InputMethodUtil.disableInputMethod(mContext, mInputMethodManager, info);

        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS)).isEqualTo(
                PLACEHOLDER_ENABLED_INPUT_METHOD_ID);
        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD)).isEqualTo(
                PLACEHOLDER_ENABLED_INPUT_METHOD_ID);
    }

    @Test
    public void disableInputMethod_isDefaultWithNoOtherDefaultableEnabled_remainsUnchanged() {
        InputMethodInfo info = createMockInputMethodInfo(mPackageManager, PLACEHOLDER_PACKAGE_NAME);
        when(info.getId()).thenReturn(PLACEHOLDER_ENABLED_INPUT_METHOD_ID_DEFAULT);
        when(mInputMethodManager.getEnabledInputMethodList())
                .thenReturn(mPlaceholderEnabledInputMethodsListOneDefaultable);

        InputMethodUtil.disableInputMethod(mContext, mInputMethodManager, info);

        assertThat(splitConcatenatedIdsIntoSet(Settings.Secure.getString(mContext
                .getContentResolver(), Settings.Secure.ENABLED_INPUT_METHODS))).isEqualTo(
                splitConcatenatedIdsIntoSet(PLACEHOLDER_ENABLED_INPUT_METHODS));
        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD)).isEqualTo(
                PLACEHOLDER_ENABLED_INPUT_METHOD_ID_DEFAULT);
    }

    private static InputMethodInfo createMockInputMethodInfoWithSubtypes(
            PackageManager packageManager, InputMethodManager inputMethodManager,
            String packageName) {
        InputMethodInfo mockInfo = createMockInputMethodInfo(packageManager, packageName);
        List<InputMethodSubtype> subtypes = createSubtypes();
        when(inputMethodManager.getEnabledInputMethodSubtypeList(
                eq(mockInfo), anyBoolean())).thenReturn(subtypes);
        return mockInfo;
    }

    private static InputMethodInfo createMockInputMethodInfo(
            PackageManager packageManager, String packageName) {
        InputMethodInfo mockInfo = mock(InputMethodInfo.class);
        when(mockInfo.getPackageName()).thenReturn(packageName);
        when(mockInfo.loadLabel(packageManager)).thenReturn(PLACEHOLDER_LABEL);
        when(mockInfo.getServiceInfo()).thenReturn(new ServiceInfo());
        when(mockInfo.getSettingsActivity()).thenReturn(PLACEHOLDER_SETTINGS_ACTIVITY);
        return mockInfo;
    }

    private static List<InputMethodSubtype> createSubtypes() {
        List<InputMethodSubtype> subtypes = new ArrayList<>();
        subtypes.add(createSubtype(1, "en_US"));
        subtypes.add(createSubtype(2, "de_BE"));
        subtypes.add(createSubtype(3, "oc-FR"));
        return subtypes;
    }

    private static InputMethodSubtype createSubtype(int id, String locale) {
        return new InputMethodSubtype.InputMethodSubtypeBuilder().setSubtypeId(id).setSubtypeLocale(
                locale).setIsAuxiliary(false).setIsAsciiCapable(true).build();
    }

    private Set<String> splitConcatenatedIdsIntoSet(String ids) {
        Set<String> result = new HashSet<>();

        if (ids == null || ids.isEmpty()) {
            return result;
        }

        InputMethodUtil.sInputMethodSplitter.setString(ids);
        while (InputMethodUtil.sInputMethodSplitter.hasNext()) {
            result.add(InputMethodUtil.sInputMethodSplitter.next());
        }

        return result;
    }
}
