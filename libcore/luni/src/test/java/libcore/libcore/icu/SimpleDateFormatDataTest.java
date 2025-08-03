/*
 * Copyright (C) 2012 The Android Open Source Project
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

package libcore.libcore.icu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.icu.util.VersionInfo;

import java.text.DateFormat;
import java.util.Locale;
import libcore.icu.LocaleData;
import libcore.icu.SimpleDateFormatData;
import libcore.test.annotation.NonCts;
import libcore.test.reasons.NonCtsReasons;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@NonCts(reason = NonCtsReasons.INTERNAL_APIS)
@RunWith(JUnit4.class)
public class SimpleDateFormatDataTest {

    // http://b/7924970
    @Test
    public void testTimeFormat12And24() {
        char sep = ' ';

        Boolean originalSetting = DateFormat.is24Hour;
        try {
            SimpleDateFormatData en_US = SimpleDateFormatData.getInstance(Locale.US);
            DateFormat.is24Hour = false;
            assertEquals("h:mm" + sep + "a", en_US.getTimeFormat(DateFormat.SHORT));
            DateFormat.is24Hour = true;
            assertEquals("HH:mm", en_US.getTimeFormat(DateFormat.SHORT));

            SimpleDateFormatData ja_JP = SimpleDateFormatData.getInstance(Locale.JAPAN);
            DateFormat.is24Hour = false;
            assertEquals("aK:mm", ja_JP.getTimeFormat(DateFormat.SHORT));
            DateFormat.is24Hour = true;
            assertEquals("H:mm", ja_JP.getTimeFormat(DateFormat.SHORT));
        } finally {
            DateFormat.is24Hour = originalSetting;
        }
    }

    // http://b/26397197
    @Test
    public void testPatternWithOverride() throws Exception {
        SimpleDateFormatData haw = SimpleDateFormatData.getInstance(new Locale("haw"));
        assertFalse(haw.getDateFormat(DateFormat.SHORT).isEmpty());
    }

}
