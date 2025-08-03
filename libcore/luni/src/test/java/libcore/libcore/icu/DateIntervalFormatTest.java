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

package libcore.libcore.icu;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeNoException;

import libcore.icu.DateIntervalFormat;
import libcore.test.annotation.NonCts;
import libcore.test.reasons.NonCtsReasons;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@NonCts(bug = 287231726, reason = NonCtsReasons.INTERNAL_APIS)
@RunWith(JUnit4.class)
public class DateIntervalFormatTest {
    private static final long MINUTE = 60 * 1000;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private static final int FORMAT_SHOW_TIME = 0x00001;

    @Test
    public void testFormatDateRange() {
        try {
            Class.forName("android.text.format.DateIntervalFormat");
        } catch (ClassNotFoundException e) {
            // JUnit should ignore this test, instead of failing the test.
            // It happens when the boot class path has frameworks.jar.
            assumeNoException(e);
        }

        String olsonId = "America/Los_Angeles";
        String result = DateIntervalFormat.formatDateRange(0, DAY, FORMAT_SHOW_TIME, olsonId);

        // We can't assert the value because it's using the default locale, which could varies
        // on different devices.
        assertNotNull(result);
    }
}
