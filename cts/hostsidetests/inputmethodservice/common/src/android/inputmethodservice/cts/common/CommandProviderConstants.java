/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.inputmethodservice.cts.common;

/**
 * Constants used by {@code CommandContentProvider} and its clients.
 */
public final class CommandProviderConstants {
    /**
     * Used as {@code command} parameter in
     * {@link android.content.ContentResolver#call(String, String, String, android.os.Bundle)} to
     * let the test IME invoke
     * {@link android.view.inputmethod.InputMethodManager#setAdditionalInputMethodSubtypes(String,
     * android.view.inputmethod.InputMethodSubtype[])}.
     */
    public static final String SET_ADDITIONAL_SUBTYPES_COMMAND = "setAdditionalSubtypes";

    /**
     * Used to pass {@code imeId} parameter of
     * {@link android.view.inputmethod.InputMethodManager#setAdditionalInputMethodSubtypes(String,
     * android.view.inputmethod.InputMethodSubtype[])} as a {@link android.os.Bundle} value when
     * calling
     * {@link android.content.ContentResolver#call(String, String, String, android.os.Bundle)}.
     */
    public static final String SET_ADDITIONAL_SUBTYPES_IMEID_KEY = "imeId";

    /**
     * Used to pass {@code subtypes} parameter of
     * {@link android.view.inputmethod.InputMethodManager#setAdditionalInputMethodSubtypes(String,
     * android.view.inputmethod.InputMethodSubtype[])} as a {@link android.os.Bundle} value when
     * calling
     * {@link android.content.ContentResolver#call(String, String, String, android.os.Bundle)}.
     */
    public static final String SET_ADDITIONAL_SUBTYPES_SUBTYPES_KEY = "subtypes";
}
