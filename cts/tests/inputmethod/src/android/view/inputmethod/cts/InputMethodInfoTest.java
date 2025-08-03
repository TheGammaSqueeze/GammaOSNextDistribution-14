/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.view.inputmethod.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.icu.util.ULocale;
import android.os.Bundle;
import android.os.Parcel;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Printer;
import android.view.inputmethod.Flags;
import android.view.inputmethod.InputMethod;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.PropertyUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class InputMethodInfoTest {
    private static final String MOCK_IME_ID = "com.android.cts.mockime/.MockIme";
    private static final String HIDDEN_FROM_PICKER_IME_ID =
            "com.android.cts.hiddenfrompickerime/.HiddenFromPickerIme";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;
    private InputMethodManager mImManager;

    private InputMethodInfo mInputMethodInfo;
    private String mPackageName;
    private String mClassName;
    private CharSequence mLabel;
    private String mSettingsActivity;
    private String mLanguageSettingsActivity;

    private int mSubtypeNameResId;
    private int mSubtypeIconResId;
    private String mSubtypeLocale;
    private String mSubtypeMode;
    private String mSubtypeExtraValueKey;
    private String mSubtypeExtraValueValue;
    private String mSubtypeExtraValue;
    private boolean mSubtypeIsAuxiliary;
    private boolean mSubtypeOverridesImplicitlyEnabledSubtype;
    private int mSubtypeId;
    private boolean mSupportsStylusHandwriting;
    private boolean mSupportsConnectionlessStylusHandwriting;
    private String mSettingsActivityActionForStylusHandwriting;
    private InputMethodSubtype mInputMethodSubtype;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mImManager = mContext.getSystemService(InputMethodManager.class);
        mPackageName = mContext.getPackageName();
        mClassName = InputMethodSettingsActivityStub.class.getName();
        mLabel = "test";
        mSettingsActivity = "android.view.inputmethod.cts.InputMethodSettingsActivityStub";
        mLanguageSettingsActivity = "MockInputMethodLanguageSettingsActivity";
        mSupportsStylusHandwriting = true;
        mSupportsConnectionlessStylusHandwriting = true;
        mSettingsActivityActionForStylusHandwriting = mSettingsActivity;
        mInputMethodInfo = new InputMethodInfo(mPackageName, mClassName, mLabel, mSettingsActivity,
                mLanguageSettingsActivity, mSupportsStylusHandwriting,
                mSupportsConnectionlessStylusHandwriting,
                mSettingsActivityActionForStylusHandwriting);

        mSubtypeNameResId = 0;
        mSubtypeIconResId = 0;
        mSubtypeLocale = "en_US";
        mSubtypeMode = "keyboard";
        mSubtypeExtraValueKey = "key1";
        mSubtypeExtraValueValue = "value1";
        mSubtypeExtraValue = "tag," + mSubtypeExtraValueKey + "=" + mSubtypeExtraValueValue;
        mSubtypeIsAuxiliary = false;
        mSubtypeOverridesImplicitlyEnabledSubtype = false;
        mSubtypeId = 99;

        mInputMethodSubtype = new InputMethodSubtype(mSubtypeNameResId, mSubtypeIconResId,
                mSubtypeLocale, mSubtypeMode, mSubtypeExtraValue, mSubtypeIsAuxiliary,
                mSubtypeOverridesImplicitlyEnabledSubtype, mSubtypeId);
    }

    @Test
    public void testInputMethodInfoProperties() throws XmlPullParserException, IOException {
        assertEquals(0, mInputMethodInfo.describeContents());
        assertNotNull(mInputMethodInfo.toString());

        assertInfo(mInputMethodInfo);
        assertEquals(0, mInputMethodInfo.getIsDefaultResourceId());

        Intent intent = new Intent(InputMethod.SERVICE_INTERFACE);
        intent.setClass(mContext, InputMethodSettingsActivityStub.class);
        PackageManager pm = mContext.getPackageManager();
        List<ResolveInfo> ris = pm.queryIntentServices(intent, PackageManager.GET_META_DATA);
        for (int i = 0; i < ris.size(); i++) {
            ResolveInfo resolveInfo = ris.get(i);
            mInputMethodInfo = new InputMethodInfo(mContext, resolveInfo);
            assertService(resolveInfo.serviceInfo, mInputMethodInfo.getServiceInfo());
            assertInfo(mInputMethodInfo);
        }
    }

    @Test
    public void testInputMethodSubtypeProperties() {
        // TODO: Test InputMethodSubtype.getDisplayName()
        assertEquals(mSubtypeNameResId, mInputMethodSubtype.getNameResId());
        assertEquals(mSubtypeIconResId, mInputMethodSubtype.getIconResId());
        assertEquals(mSubtypeLocale, mInputMethodSubtype.getLocale());
        assertEquals(mSubtypeMode, mInputMethodSubtype.getMode());
        assertEquals(mSubtypeExtraValue, mInputMethodSubtype.getExtraValue());
        assertTrue(mInputMethodSubtype.containsExtraValueKey(mSubtypeExtraValueKey));
        assertEquals(mSubtypeExtraValueValue,
                mInputMethodSubtype.getExtraValueOf(mSubtypeExtraValueKey));
        assertEquals(mSubtypeIsAuxiliary, mInputMethodSubtype.isAuxiliary());
        assertEquals(mSubtypeOverridesImplicitlyEnabledSubtype,
                mInputMethodSubtype.overridesImplicitlyEnabledSubtype());
        assertEquals(mSubtypeId, mInputMethodSubtype.hashCode());
    }

    private void assertService(ServiceInfo expected, ServiceInfo actual) {
        assertEquals(expected.getIconResource(), actual.getIconResource());
        assertEquals(expected.labelRes, actual.labelRes);
        assertEquals(expected.nonLocalizedLabel, actual.nonLocalizedLabel);
        assertEquals(expected.icon, actual.icon);
        assertEquals(expected.permission, actual.permission);
    }

    private void assertInfo(InputMethodInfo info) {
        assertEquals(mPackageName, info.getPackageName());
        assertEquals(mSettingsActivity, info.getSettingsActivity());
        if (Flags.imeSwitcherRevamp()) {
            assertEquals(mLanguageSettingsActivity,
                    info.createImeLanguageSettingsActivityIntent().getComponent().getClassName());
        }
        ComponentName component = info.getComponent();
        assertEquals(mClassName, component.getClassName());
        String expectedId = component.flattenToShortString();
        assertEquals(expectedId, info.getId());
        assertEquals(mClassName, info.getServiceName());
        assertEquals(mSupportsStylusHandwriting, info.supportsStylusHandwriting());
        assertEquals(mSupportsConnectionlessStylusHandwriting,
                info.supportsConnectionlessStylusHandwriting());
        assertEquals(mSettingsActivityActionForStylusHandwriting,
                info.createStylusHandwritingSettingsActivityIntent().getComponent().getClassName());
    }

    @Test
    public void testDump() {
        Printer printer = mock(Printer.class);
        String prefix = "test";
        mInputMethodInfo.dump(printer, prefix);
        verify(printer, atLeastOnce()).println(anyString());
    }

    @Test
    public void testLoadIcon() {
        PackageManager pm = mContext.getPackageManager();
        assertNotNull(mInputMethodInfo.loadIcon(pm));
    }

    @Test
    public void testEquals() {
        InputMethodInfo inputMethodInfo = new InputMethodInfo(mPackageName, mClassName, mLabel,
                mSettingsActivity);
        assertTrue(inputMethodInfo.equals(mInputMethodInfo));
    }

    @Test
    public void testLoadLabel() {
        CharSequence expected = "test";
        PackageManager pm = mContext.getPackageManager();
        assertEquals(expected.toString(), mInputMethodInfo.loadLabel(pm).toString());
    }

    /**
     * Verifies the created IME language settings activity intent.
     */
    @ApiTest(apis = {
            "android.view.inputmethod.InputMethodInfo#ACTION_IME_LANGUAGE_SETTINGS",
            "android.view.inputmethod.InputMethodInfo#createImeLanguageSettingsActivityIntent"
    })
    @RequiresFlagsEnabled(Flags.FLAG_IME_SWITCHER_REVAMP)
    @Test
    public void testLanguageSettingsInfo() {
        final List<InputMethodInfo> imis = mImManager.getInputMethodList();

        final InputMethodInfo mockIme = getImi(imis, MOCK_IME_ID);
        assertThat(mockIme).isNotNull();

        final Intent languageSettingsIntent = mockIme.createImeLanguageSettingsActivityIntent();
        assertNotNull(languageSettingsIntent);
        assertEquals(InputMethodInfo.ACTION_IME_LANGUAGE_SETTINGS,
                languageSettingsIntent.getAction());
        assertEquals("language_settings", languageSettingsIntent.getComponent().getClassName());
    }

    @Test
    public void testInputMethodInfoWriteToParcel() {
        final Parcel p = Parcel.obtain();
        mInputMethodInfo.writeToParcel(p, 0);
        p.setDataPosition(0);
        final InputMethodInfo imi = InputMethodInfo.CREATOR.createFromParcel(p);
        p.recycle();

        assertEquals(mInputMethodInfo.getPackageName(), imi.getPackageName());
        assertEquals(mInputMethodInfo.getServiceName(), imi.getServiceName());
        assertEquals(mInputMethodInfo.getSettingsActivity(), imi.getSettingsActivity());
        if (Flags.imeSwitcherRevamp()) {
            assertEquals(mInputMethodInfo.createImeLanguageSettingsActivityIntent().getComponent()
                            .getClassName(),
                    imi.createImeLanguageSettingsActivityIntent().getComponent().getClassName());
        }
        assertEquals(mInputMethodInfo.getId(), imi.getId());
        assertEquals(mInputMethodInfo.getIsDefaultResourceId(), imi.getIsDefaultResourceId());
        assertEquals(mInputMethodInfo.supportsStylusHandwriting(), imi.supportsStylusHandwriting());
        assertEquals(mInputMethodInfo.createStylusHandwritingSettingsActivityIntent().getComponent()
                        .getClassName(),
                imi.createStylusHandwritingSettingsActivityIntent().getComponent().getClassName());
        assertService(mInputMethodInfo.getServiceInfo(), imi.getServiceInfo());
    }

    @Test
    public void testInputMethodSubtypeWriteToParcel() {
        final Parcel p = Parcel.obtain();
        mInputMethodSubtype.writeToParcel(p, 0);
        p.setDataPosition(0);
        final InputMethodSubtype subtype = InputMethodSubtype.CREATOR.createFromParcel(p);
        p.recycle();

        assertEquals(mInputMethodSubtype.containsExtraValueKey(mSubtypeExtraValueKey),
                subtype.containsExtraValueKey(mSubtypeExtraValueKey));
        assertEquals(mInputMethodSubtype.getExtraValue(), subtype.getExtraValue());
        assertEquals(mInputMethodSubtype.getExtraValueOf(mSubtypeExtraValueKey),
                subtype.getExtraValueOf(mSubtypeExtraValueKey));
        assertEquals(mInputMethodSubtype.getIconResId(), subtype.getIconResId());
        assertEquals(mInputMethodSubtype.getLocale(), subtype.getLocale());
        assertEquals(mInputMethodSubtype.getMode(), subtype.getMode());
        assertEquals(mInputMethodSubtype.getNameResId(), subtype.getNameResId());
        assertEquals(mInputMethodSubtype.hashCode(), subtype.hashCode());
        assertEquals(mInputMethodSubtype.isAuxiliary(), subtype.isAuxiliary());
        assertEquals(mInputMethodSubtype.overridesImplicitlyEnabledSubtype(),
                subtype.overridesImplicitlyEnabledSubtype());
    }

    @Test
    public void testAtLeastOneEncryptionAwareInputMethodIsAvailable() {
        assumeFalse(FeatureUtil.isWatch());

        if (!mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_INPUT_METHODS)) {
            return;
        }

        // If the device doesn't use FBE, skip the test.
        if (!PropertyUtil.propertyEquals("ro.crypto.type", "file")) {
            return;
        }

        final List<InputMethodInfo> imis = mImManager.getInputMethodList();
        boolean hasEncryptionAwareInputMethod = false;
        for (final InputMethodInfo imi : imis) {
            final ServiceInfo serviceInfo = imi.getServiceInfo();
            if (serviceInfo == null) {
                continue;
            }
            if ((serviceInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM)
                    != ApplicationInfo.FLAG_SYSTEM) {
                continue;
            }
            if (serviceInfo.directBootAware) {
                hasEncryptionAwareInputMethod = true;
                break;
            }
        }
        assertTrue(hasEncryptionAwareInputMethod);
    }

    @Test
    public void testShowInInputMethodPicker() {
        final List<InputMethodInfo> imis = mImManager.getInputMethodList();

        final InputMethodInfo shown = getImi(imis, MOCK_IME_ID);
        assertThat(shown).isNotNull();
        assertThat(shown.shouldShowInInputMethodPicker()).isTrue();

        final InputMethodInfo hidden = getImi(imis, HIDDEN_FROM_PICKER_IME_ID);
        assertThat(hidden).isNotNull();
        assertThat(hidden.shouldShowInInputMethodPicker()).isFalse();
    }

    @Nullable
    private static InputMethodInfo getImi(List<InputMethodInfo> imis, String id) {
        for (final InputMethodInfo imi : imis) {
            if (id.equals(imi.getId())) {
                return imi;
            }
        }
        return null;
    }

    @Test
    public void testParsingPhysicalKeyboardAttributes() throws Exception {
        final ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.applicationInfo = mContext.getApplicationInfo();
        serviceInfo.packageName = mContext.getPackageName();
        serviceInfo.name = "TestIme";
        serviceInfo.metaData = new Bundle();
        serviceInfo.metaData.putInt(InputMethod.SERVICE_META_DATA, R.xml.ime_meta_subtypes);
        final ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = serviceInfo;
        InputMethodInfo imi = new InputMethodInfo(mContext, resolveInfo);

        assertEquals(2, imi.getSubtypeCount());

        InputMethodSubtype subtype = imi.getSubtypeAt(0);
        assertEquals("en-US", subtype.getLanguageTag());
        assertNull(subtype.getPhysicalKeyboardHintLanguageTag());
        assertEquals("", subtype.getPhysicalKeyboardHintLayoutType());

        subtype = imi.getSubtypeAt(1);
        assertEquals("zh-CN", subtype.getLanguageTag());
        assertEquals(new ULocale("en-US"), subtype.getPhysicalKeyboardHintLanguageTag());
        assertEquals("qwerty", subtype.getPhysicalKeyboardHintLayoutType());
    }
}
