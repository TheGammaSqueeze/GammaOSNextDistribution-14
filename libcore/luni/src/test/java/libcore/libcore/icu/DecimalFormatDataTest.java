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

import java.util.Locale;
import libcore.icu.DecimalFormatData;
import libcore.test.annotation.NonCts;
import libcore.test.reasons.NonCtsReasons;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@NonCts(reason = NonCtsReasons.INTERNAL_APIS)
@RunWith(JUnit4.class)
public class DecimalFormatDataTest {

    // http://code.google.com/p/android/issues/detail?id=38844
    @Test
    public void testDecimalFormatSymbols_es() {
        DecimalFormatData es = DecimalFormatData.getInstance(new Locale("es"));
        assertEquals(',', es.getDecimalSeparator());
        assertEquals('.', es.getGroupingSeparator());

        DecimalFormatData es_419 = DecimalFormatData.getInstance(new Locale("es", "419"));
        assertEquals('.', es_419.getDecimalSeparator());
        assertEquals(',', es_419.getGroupingSeparator());

        DecimalFormatData es_US = DecimalFormatData.getInstance(new Locale("es", "US"));
        assertEquals('.', es_US.getDecimalSeparator());
        assertEquals(',', es_US.getGroupingSeparator());

        DecimalFormatData es_MX = DecimalFormatData.getInstance(new Locale("es", "MX"));
        assertEquals('.', es_MX.getDecimalSeparator());
        assertEquals(',', es_MX.getGroupingSeparator());

        DecimalFormatData es_AR = DecimalFormatData.getInstance(new Locale("es", "AR"));
        assertEquals(',', es_AR.getDecimalSeparator());
        assertEquals('.', es_AR.getGroupingSeparator());
    }
}
