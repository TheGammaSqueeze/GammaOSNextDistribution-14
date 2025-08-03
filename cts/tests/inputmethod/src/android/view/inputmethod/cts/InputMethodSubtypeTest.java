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

package android.view.inputmethod.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.icu.util.ULocale;
import android.os.Parcel;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.NonNull;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;

@MediumTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class InputMethodSubtypeTest {

    private static final CharSequence SUBTYPE_UNTRANSLATABLE_NAME = "my_new_subtype";

    private static final String NONEXISTENCE_PACKAGE = "com.android.cts.ime.nonexistentpackage";

    private static final String NONEXISTENCE_RELATIVE_NAME = ".NonexistentIme";

    @NonNull
    private final InputMethodManager mImm = Objects.requireNonNull(
            InstrumentationRegistry.getInstrumentation().getTargetContext().getSystemService(
                    InputMethodManager.class));

    /**
     * Verifies that
     * {@link InputMethodManager#setExplicitlyEnabledInputMethodSubtypes(String, int[])} must throw
     * {@link SecurityException} if {@code null} is specified for {@code imeId}.
     */
    @Test
    public void testSetExplicitlyEnabledInputMethodSubtypesForNullImeId() {
        assertThrows(SecurityException.class,
                () -> mImm.setExplicitlyEnabledInputMethodSubtypes(null, null));
    }

    /**
     * Verifies that
     * {@link InputMethodManager#setExplicitlyEnabledInputMethodSubtypes(String, int[])} must throw
     * {@link SecurityException} if the caller is not allowed to do so.
     *
     * <p>Note that to avoid side-channel attacks about app package visibility, it must always
     * throw the same {@link SecurityException} no matter whether the specified IME ID exists or
     * not.</p>
     */
    @Test
    public void testSetExplicitlyEnabledInputMethodSubtypesForNotOwningIme() {
        final String notOwningImeId =
                ComponentName.createRelative(NONEXISTENCE_PACKAGE, NONEXISTENCE_RELATIVE_NAME)
                                .flattenToShortString();
        assertThrows(SecurityException.class,
                () -> mImm.setExplicitlyEnabledInputMethodSubtypes(notOwningImeId, null));
    }

    /**
     * Verifies that
     * {@link InputMethodManager#setExplicitlyEnabledInputMethodSubtypes(String, int[])} does not
     * throw any {@link Exception} if the specified IME ID means that the IME belongs to the calling
     * package but the IME itself does not exist.
     */
    @Test
    public void testSetEnabledInputMethodSubtypesForNotExistingImeInTheSamePackage() {
        final String myPackageName =
                InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
        final String nonexistenceImeId =
                ComponentName.createRelative(myPackageName, NONEXISTENCE_RELATIVE_NAME)
                        .flattenToShortString();
        mImm.setExplicitlyEnabledInputMethodSubtypes(nonexistenceImeId, new int[]{});
    }

    /**
     * Verifies that
     * {@link InputMethodManager#setExplicitlyEnabledInputMethodSubtypes(String, int[])} must throw
     * {@link NullPointerException} if {@code null} is specified for {@code subtypeHashCodes}.
     */
    @Test
    public void testSetExplicitlyEnabledInputMethodSubtypesForNullSubtypeHashCodes() {
        final String myPackageName =
                InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
        final String nonexistenceImeId =
                ComponentName.createRelative(myPackageName, NONEXISTENCE_RELATIVE_NAME)
                        .flattenToShortString();
        assertThrows(NullPointerException.class,
                () -> mImm.setExplicitlyEnabledInputMethodSubtypes(nonexistenceImeId, null));
    }

    /**
     * Verifies the subtype with name override can be parcelled and un-parcelled correctly.
     */
    @Test
    public void testSubtypeNameOverrideParcel() {
        final SpannableStringBuilder expectedSubtypeName =
                new SpannableStringBuilder(SUBTYPE_UNTRANSLATABLE_NAME);
        expectedSubtypeName.setSpan(new ForegroundColorSpan(Color.RED), 3, 5, 0);
        final InputMethodSubtype newSubtype =
                new InputMethodSubtype.InputMethodSubtypeBuilder()
                        .setSubtypeNameOverride(expectedSubtypeName)
                        .build();

        assertThat(newSubtype.getNameOverride()).isEqualTo(expectedSubtypeName);

        final Parcel parcel = Parcel.obtain();
        newSubtype.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final Spannable subtypeNameSpannable =
                (Spannable) InputMethodSubtype.CREATOR.createFromParcel(parcel).getNameOverride();
        parcel.recycle();

        assertThat(subtypeNameSpannable.toString()).isEqualTo(expectedSubtypeName.toString());
        Object[] spans =
                subtypeNameSpannable.getSpans(0, expectedSubtypeName.length(), Object.class);
        assertThat(spans.length).isEqualTo(1);
        assertThat(subtypeNameSpannable.getSpanStart(spans[0])).isEqualTo(3);
        assertThat(subtypeNameSpannable.getSpanEnd(spans[0])).isEqualTo(5);
    }

    /**
     * Verifies the subtype without the name override can be parcelled and un-parcelled correctly.
     */
    @Test
    public void testSubtypeNoNameOverrideParcel() {
        final InputMethodSubtype newSubtype =
                new InputMethodSubtype.InputMethodSubtypeBuilder().build();

        assertThat(newSubtype.getNameOverride().length()).isEqualTo(0);

        final Parcel parcel = Parcel.obtain();
        newSubtype.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final CharSequence subtypeName =
                InputMethodSubtype.CREATOR.createFromParcel(parcel).getNameOverride();
        parcel.recycle();

        assertThat(subtypeName.length()).isEqualTo(0);
    }

    /**
     * Verifies that
     * {@link InputMethodSubtypeBuilder#setSubtypeNameOverride(String)} can correctly set the
     * display name for the subtype.
     */
    @Test
    public void testSetSubtypeNameOverride() {
        final CharSequence expectedSubtypeName = SUBTYPE_UNTRANSLATABLE_NAME;
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final Context context = instrumentation.getTargetContext();
        final InputMethodSubtype newSubtype =
                new InputMethodSubtype.InputMethodSubtypeBuilder()
                        .setSubtypeNameOverride(expectedSubtypeName)
                        .build();

        assertThat(newSubtype.getNameOverride()).isEqualTo(expectedSubtypeName);

        final String actualSubtypeName = newSubtype.getDisplayName(
                context, context.getPackageName(), null).toString();

        assertThat(actualSubtypeName).isEqualTo(expectedSubtypeName);
    }

    /**
     * Verifies that
     * {@link InputMethodSubtypeBuilder#setSubtypeNameOverride(String)} won't impact the
     * display name if the subtype's name has been configured by
     * {@link InputMethodSubtypeBuilder#setSubtypeNameResId(int)}.
     */
    @Test
    public void testSetSubtypeNameOverrideWithNameResId() {
        final CharSequence expectedSubtypeName = SUBTYPE_UNTRANSLATABLE_NAME;
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final Context context = instrumentation.getTargetContext();
        final InputMethodSubtype newSubtype =
                new InputMethodSubtype.InputMethodSubtypeBuilder()
                        .setSubtypeNameOverride(expectedSubtypeName)
                        .setSubtypeNameResId(R.string.new_subtype_name)
                        .build();

        assertThat(newSubtype.getNameOverride()).isEqualTo(expectedSubtypeName);

        String actualSubtypeName = newSubtype.getDisplayName(
                context, context.getPackageName(), null).toString();

        assertThat(actualSubtypeName).isEqualTo("new_subtype_name");
    }

    /**
     * Verifies that
     * {@link InputMethodSubtypeBuilder#setPhysicalKeyboardHint(String, String)} can save the hint
     * information into the subtype.
     */
    @Test
    public void testSetPhysicalKeyboardHint() {
        final ULocale expectedPkLanguageTag = new ULocale("en_US");
        final String expectedPkLayout = "qwerty";
        final InputMethodSubtype newSubtype =
                new InputMethodSubtype.InputMethodSubtypeBuilder()
                        .setPhysicalKeyboardHint(expectedPkLanguageTag, expectedPkLayout)
                        .build();

        assertThat(newSubtype.getPhysicalKeyboardHintLanguageTag()).isEqualTo(
                expectedPkLanguageTag);
        assertThat(newSubtype.getPhysicalKeyboardHintLayoutType()).isEqualTo(expectedPkLayout);
    }

    /**
     * Verifies the subtype with PK hint info can be parcelled and un-parcelled correctly.
     */
    @Test
    public void testPhysicalKeyboardHintParcel() {
        final ULocale expectedPkLanguageTag = new ULocale("en_US");
        final String expectedPkLayout = "dvorak";
        final InputMethodSubtype newSubtype =
                new InputMethodSubtype.InputMethodSubtypeBuilder()
                        .setPhysicalKeyboardHint(expectedPkLanguageTag, expectedPkLayout)
                        .build();

        final Parcel parcel = Parcel.obtain();
        newSubtype.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final InputMethodSubtype subtypeFromParcel =
                InputMethodSubtype.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(subtypeFromParcel.getPhysicalKeyboardHintLanguageTag()).isEqualTo(
                expectedPkLanguageTag);
        assertThat(subtypeFromParcel.getPhysicalKeyboardHintLayoutType()).isEqualTo(
                expectedPkLayout);
    }

    /**
     * Verifies that
     * {@link InputMethodSubtypeBuilder#setPhysicalKeyboardHint(String, String)} can handle null as
     * the parameters.
     */
    @Test
    public void testSetPhysicalKeyboardHintNull() {
        final InputMethodSubtype newSubtype =
                new InputMethodSubtype.InputMethodSubtypeBuilder()
                        .setPhysicalKeyboardHint(null, "")
                        .build();
        assertThat(newSubtype.getPhysicalKeyboardHintLanguageTag()).isNull();
        assertThat(newSubtype.getPhysicalKeyboardHintLayoutType()).isEqualTo("");

        assertThrows(NullPointerException.class,
                () -> new InputMethodSubtype.InputMethodSubtypeBuilder()
                        .setPhysicalKeyboardHint(null, null));
    }
}
