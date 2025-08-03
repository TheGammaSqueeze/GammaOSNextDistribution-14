/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.view.textclassifier.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.icu.util.ULocale;
import android.os.Bundle;
import android.view.textclassifier.TextLanguage;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TextLanguageTest {
    private static final float EPSILON = 0.000001f;
    private static final String BUNDLE_KEY = "key";
    private static final String BUNDLE_VALUE = "value";
    private static final Bundle BUNDLE = new Bundle();
    static {
        BUNDLE.putString(BUNDLE_KEY, BUNDLE_VALUE);
    }
    private static final String ID = "id123";
    private static final String TEXT = "abcdefghijklmnopqrstuvwxyz";

    @Test
    public void testTextLanguage() {
        final TextLanguage language = new TextLanguage.Builder()
                .setId(ID)
                .putLocale(ULocale.ENGLISH, 0.6f)
                .putLocale(ULocale.CHINESE, 0.3f)
                .putLocale(ULocale.JAPANESE, 0.1f)
                .setExtras(BUNDLE)
                .build();

        assertEquals(ID, language.getId());
        assertEquals(3, language.getLocaleHypothesisCount());
        assertEquals(ULocale.ENGLISH, language.getLocale(0));
        assertEquals(ULocale.CHINESE, language.getLocale(1));
        assertEquals(ULocale.JAPANESE, language.getLocale(2));
        assertEquals(0.6f, language.getConfidenceScore(ULocale.ENGLISH), EPSILON);
        assertEquals(0.3f, language.getConfidenceScore(ULocale.CHINESE), EPSILON);
        assertEquals(0.1f, language.getConfidenceScore(ULocale.JAPANESE), EPSILON);
        assertEquals(BUNDLE_VALUE, language.getExtras().getString(BUNDLE_KEY));
    }

    @Test
    public void testTextLanguage_clippedScore() {
        final TextLanguage language = new TextLanguage.Builder()
                .putLocale(ULocale.ENGLISH, 2f)
                .putLocale(ULocale.CHINESE, -2f)
                .build();

        assertEquals(1, language.getLocaleHypothesisCount());
        assertEquals(ULocale.ENGLISH, language.getLocale(0));
        assertEquals(1f, language.getConfidenceScore(ULocale.ENGLISH), EPSILON);
        assertNull(language.getId());
    }

    @Test
    public void testTextLanguageRequest() {
        final TextLanguage.Request request = new TextLanguage.Request.Builder(TEXT)
                .setExtras(BUNDLE)
                .build();

        assertEquals(TEXT, request.getText());
        assertEquals(BUNDLE_VALUE, request.getExtras().getString(BUNDLE_KEY));
        assertNull(request.getCallingPackageName());
    }
}
