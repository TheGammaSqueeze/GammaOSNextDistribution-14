/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.autofillservice.cts.unittests;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.platform.test.annotations.AppModeFull;
import android.view.autofill.AutofillValue;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@AppModeFull(reason = "Unit test")
@RunWith(AndroidJUnit4.class)
public class AutofillValueTest {

    @Test
    public void createTextValue() throws Exception {
        assertThat(AutofillValue.forText(null)).isNull();

        assertThat(AutofillValue.forText("").isText()).isTrue();
        assertThat(AutofillValue.forText("").isToggle()).isFalse();
        assertThat(AutofillValue.forText("").isList()).isFalse();
        assertThat(AutofillValue.forText("").isDate()).isFalse();

        AutofillValue emptyV = AutofillValue.forText("");
        assertThat(emptyV.getTextValue().toString()).isEqualTo("");

        final AutofillValue v = AutofillValue.forText("someText");
        assertThat(v.getTextValue()).isEqualTo("someText");

        assertThrows(IllegalStateException.class, v::getToggleValue);
        assertThrows(IllegalStateException.class, v::getListValue);
        assertThrows(IllegalStateException.class, v::getDateValue);
    }

    @Test
    public void createToggleValue() throws Exception {
        assertThat(AutofillValue.forToggle(true).getToggleValue()).isTrue();
        assertThat(AutofillValue.forToggle(false).getToggleValue()).isFalse();

        assertThat(AutofillValue.forToggle(true).isText()).isFalse();
        assertThat(AutofillValue.forToggle(true).isToggle()).isTrue();
        assertThat(AutofillValue.forToggle(true).isList()).isFalse();
        assertThat(AutofillValue.forToggle(true).isDate()).isFalse();


        final AutofillValue v = AutofillValue.forToggle(true);

        assertThrows(IllegalStateException.class, v::getTextValue);
        assertThrows(IllegalStateException.class, v::getListValue);
        assertThrows(IllegalStateException.class, v::getDateValue);
    }

    @Test
    public void createListValue() throws Exception {
        assertThat(AutofillValue.forList(-1).getListValue()).isEqualTo(-1);
        assertThat(AutofillValue.forList(0).getListValue()).isEqualTo(0);
        assertThat(AutofillValue.forList(1).getListValue()).isEqualTo(1);

        assertThat(AutofillValue.forList(0).isText()).isFalse();
        assertThat(AutofillValue.forList(0).isToggle()).isFalse();
        assertThat(AutofillValue.forList(0).isList()).isTrue();
        assertThat(AutofillValue.forList(0).isDate()).isFalse();

        final AutofillValue v = AutofillValue.forList(0);

        assertThrows(IllegalStateException.class, v::getTextValue);
        assertThrows(IllegalStateException.class, v::getToggleValue);
        assertThrows(IllegalStateException.class, v::getDateValue);
    }

    @Test
    public void createDateValue() throws Exception {
        assertThat(AutofillValue.forDate(-1).getDateValue()).isEqualTo(-1);
        assertThat(AutofillValue.forDate(0).getDateValue()).isEqualTo(0);
        assertThat(AutofillValue.forDate(1).getDateValue()).isEqualTo(1);

        assertThat(AutofillValue.forDate(0).isText()).isFalse();
        assertThat(AutofillValue.forDate(0).isToggle()).isFalse();
        assertThat(AutofillValue.forDate(0).isList()).isFalse();
        assertThat(AutofillValue.forDate(0).isDate()).isTrue();

        final AutofillValue v = AutofillValue.forDate(0);

        assertThrows(IllegalStateException.class, v::getTextValue);
        assertThrows(IllegalStateException.class, v::getToggleValue);
        assertThrows(IllegalStateException.class, v::getListValue);
    }
}
