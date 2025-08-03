/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.icu.extratest.text;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.icu.testsharding.MainTestShard;
import android.icu.text.Transliterator;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@MainTestShard
@RunWith(JUnit4.class)
public class TransliteratorTest {
    @Test
    public void test_Unknown() {
        try {
            Transliterator t = Transliterator.getInstance("Unknown");
            fail();
        } catch (RuntimeException expected) {
        }
    }

    @Test
    public void test_null_id() {
        try {
            Transliterator t = Transliterator.getInstance(null);
            fail();
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void test_Any_Upper() {
        Transliterator t = Transliterator.getInstance("Any-Upper");
        assertEquals("HELLO WORLD!", t.transliterate("HeLlO WoRlD!"));
        assertEquals("STRASSE", t.transliterate("Straße"));
    }

    @Test
    public void test_Any_Lower() {
        Transliterator t = Transliterator.getInstance("Any-Lower");
        assertEquals("hello world!", t.transliterate("HeLlO WoRlD!"));
    }

    @Test
    public void test_Greek_Latin() {
        String greek = "Καλημέρα κόσμε!";

        // Transliterate Greek to Latin, then to plain ASCII.
        Transliterator t = Transliterator.getInstance("Greek-Latin");
        String latin = t.transliterate(greek);
        t = Transliterator.getInstance("Latin-Ascii");
        String ascii = t.transliterate(latin);
        assertEquals("Kalēméra kósme!", latin);
        assertEquals("Kalemera kosme!", ascii);

        // Use alternative transliteration variants.
        t = Transliterator.getInstance("Greek-Latin/BGN");
        assertEquals("Kaliméra kósme!", t.transliterate(greek));
        t = Transliterator.getInstance("Greek-Latin/UNGEGN");
        assertEquals("Kali̱méra kósme!",t.transliterate(greek));
    }

    @Test
    public void test_Han_Latin() {
        Transliterator t = Transliterator.getInstance("Han-Latin");
        assertEquals("hàn zì/hàn zì", t.transliterate("汉字/漢字"));

        assertEquals("shěn", t.transliterate("\u6c88"));
        assertEquals("shěn", t.transliterate("\u700b"));
        assertEquals("jiǎ", t.transliterate("\u8d3e"));

        t = Transliterator.getInstance("Han-Latin/Names");
        assertEquals("shěn", t.transliterate("\u6c88"));
        assertEquals("shěn", t.transliterate("\u700b"));
        assertEquals("jiǎ", t.transliterate("\u8d3e"));

        t = Transliterator.getInstance("Han-Latin/Names; Latin-Ascii; Any-Upper");
        assertEquals("SHEN", t.transliterate("\u6c88"));
        assertEquals("SHEN", t.transliterate("\u700b"));
        assertEquals("JIA", t.transliterate("\u8d3e"));
    }
}
