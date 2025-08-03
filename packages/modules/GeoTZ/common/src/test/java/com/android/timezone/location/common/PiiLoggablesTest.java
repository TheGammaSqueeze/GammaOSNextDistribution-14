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

package com.android.timezone.location.common;

import static com.android.timezone.location.common.PiiLoggables.NULL_STRING;
import static com.android.timezone.location.common.PiiLoggables.REDACTED_STRING;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import androidx.test.runner.AndroidJUnit4;

import com.android.timezone.location.common.PiiLoggables.PiiLoggableValue;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.function.Function;

/**
 * Tests for {@link PiiLoggables}.
 */
@RunWith(AndroidJUnit4.class)
public class PiiLoggablesTest {

    @Test
    public void fromString_null() {
        PiiLoggable piiLoggable = PiiLoggables.fromString(null);
        assertEquals(NULL_STRING, piiLoggable.toString());
        assertEquals(NULL_STRING, piiLoggable.toPiiString());
        assertEquals(piiLoggable, PiiLoggables.fromString(null));
        assertNotEquals(piiLoggable, PiiLoggables.fromString("Hello"));
    }

    @Test
    public void fromString() {
        String string1 = "Hello";
        String string2 = "Goodbye";
        PiiLoggable piiLoggable = PiiLoggables.fromString(string1);
        assertEquals(string1, piiLoggable.toString());
        assertEquals(string1, piiLoggable.toPiiString());
        assertEquals(piiLoggable, PiiLoggables.fromString(string1));
        assertNotEquals(piiLoggable, PiiLoggables.fromString(string2));
    }

    @Test
    public void fromPiiValue_null() {
        PiiLoggableValue<String> piiLoggableValue = PiiLoggables.fromPiiValue(null);
        assertEquals(NULL_STRING, piiLoggableValue.toString());
        assertEquals(NULL_STRING, piiLoggableValue.toPiiString());
        assertEquals(piiLoggableValue, PiiLoggables.fromPiiValue(null));
        assertNotEquals(piiLoggableValue, PiiLoggables.fromPiiValue("Hello"));
        assertNull(piiLoggableValue.get());
    }

    @Test
    public void fromPiiValue() {
        Object piiValue = "Classified Info";
        Object differentPiiValue = "Different classified Info";
        PiiLoggableValue<Object> piiLoggableValue = PiiLoggables.fromPiiValue(piiValue);
        assertEquals(REDACTED_STRING, piiLoggableValue.toString());
        assertEquals(piiValue.toString(), piiLoggableValue.toPiiString());
        assertEquals(piiLoggableValue, PiiLoggables.fromPiiValue(piiValue));
        assertNotEquals(piiLoggableValue, PiiLoggables.fromPiiValue(differentPiiValue));
        assertEquals(piiValue, piiLoggableValue.get());
    }

    @Test
    public void toPiiStringFunction() {
        String piiValue = "pii";
        PiiLoggable piiLoggable = new TestPiiLoggable("no pii", piiValue);
        Function<PiiLoggable, String> function = PiiLoggables.toPiiStringFunction();
        assertEquals(NULL_STRING, function.apply(null));
        assertEquals(piiValue, function.apply(piiLoggable));
    }

    @Test
    public void formatPiiString() {
        PiiLoggable piiLoggable = new TestPiiLoggable("no pii", "pii");
        String result = PiiLoggables.formatPiiString(
                "1=%s,2=%s,3=%s", piiLoggable, null, piiLoggable);
        assertEquals("1=pii,2=null,3=pii", result);

    }

    @Test
    public void fromTemplate() {
        PiiLoggable piiLoggable = new TestPiiLoggable("no pii", "pii");
        PiiLoggable result =
                PiiLoggables.fromTemplate("1=%s,2=%s,3=%s", piiLoggable, null, piiLoggable);
        assertEquals("1=pii,2=null,3=pii", result.toPiiString());
        assertEquals("1=no pii,2=null,3=no pii", result.toString());
    }

    @Test
    public void fromTemplate_withPercentS() {
        PiiLoggable piiLoggable = new TestPiiLoggable("no pii{%s}", "pii{%s}");
        PiiLoggable result =
                PiiLoggables.fromTemplate("1=%s,2=%s,3=%s", piiLoggable, null, piiLoggable);
        assertEquals("1=pii{%s},2=null,3=pii{%s}", result.toPiiString());
        assertEquals("1=no pii{%s},2=null,3=no pii{%s}", result.toString());
    }

    static class TestPiiLoggable implements PiiLoggable {

        private final String mNoPii;
        private final String mPii;

        TestPiiLoggable(String noPii, String pii) {
            mNoPii = noPii;
            mPii = pii;
        }

        @Override
        public String toPiiString() {
            return mPii;
        }

        @Override
        public String toString() {
            return mNoPii;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TestPiiLoggable that = (TestPiiLoggable) o;
            return mNoPii.equals(that.mNoPii)
                    && mPii.equals(that.mPii);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mNoPii, mPii);
        }
    }
}
