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

package android.view.inputmethod.cts;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.os.PersistableBundle;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.view.inputmethod.TextAttribute;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class TextAttributeTest {
    private static final String SUGGESTION = "suggestion";
    private static final String EXTRAS_KEY = "extras_key";
    private static final String EXTRAS_VALUE = "extras_value";

    private static final List<String> SUGGESTIONS = Collections.singletonList(SUGGESTION);
    private static final PersistableBundle EXTRA_BUNDLE;
    static {
        final PersistableBundle bundle = new PersistableBundle();
        bundle.putString(EXTRAS_KEY, EXTRAS_VALUE);
        EXTRA_BUNDLE = bundle;
    }

    @Test
    public void testTextAttribute() {
        final TextAttribute textAttribute = new TextAttribute.Builder()
                .setTextConversionSuggestions(SUGGESTIONS)
                .setExtras(EXTRA_BUNDLE)
                .build();
        assertTextAttribute(textAttribute);
    }

    @Test
    public void testWriteToParcel() {
        final TextAttribute textAttribute = new TextAttribute.Builder()
                .setTextConversionSuggestions(SUGGESTIONS)
                .setExtras(EXTRA_BUNDLE)
                .build();

        assertTextAttribute(cloneViaParcel(textAttribute));
    }

    private static void assertTextAttribute(TextAttribute info) {
        assertThat(info.getTextConversionSuggestions()).containsExactlyElementsIn(SUGGESTIONS);
        assertThat(info.getExtras().getString(EXTRAS_KEY)).isEqualTo(EXTRAS_VALUE);
    }

    private static TextAttribute cloneViaParcel(TextAttribute src) {
        final Parcel parcel = Parcel.obtain();
        try {
            src.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return TextAttribute.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }
}
