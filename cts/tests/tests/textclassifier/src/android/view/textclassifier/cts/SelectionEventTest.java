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

import static com.google.common.truth.Truth.assertThat;

import android.view.textclassifier.SelectionEvent;
import android.view.textclassifier.TextClassifier;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SelectionEventTest {

    @Test
    public void testSelectionEvent() {
        SelectionEvent event = SelectionEvent.createSelectionActionEvent(0, 1,
                SelectionEvent.ACTION_COPY);
        assertThat(event.getEventType()).isEqualTo(SelectionEvent.ACTION_COPY);
        assertThat(event.getStart()).isEqualTo(0);
        assertThat(event.getEnd()).isEqualTo(0);
        assertThat(event.getInvocationMethod()).isEqualTo(SelectionEvent.INVOCATION_UNKNOWN);
        assertThat(event.getEntityType()).isEqualTo(TextClassifier.TYPE_UNKNOWN);
        assertThat(event.getEventIndex()).isEqualTo(0);
        assertThat(event.getPackageName()).isEqualTo("");
        assertThat(event.getSmartStart()).isEqualTo(0);
        assertThat(event.getSmartEnd()).isEqualTo(0);
        assertThat(event.getWidgetType()).isEqualTo(TextClassifier.WIDGET_TYPE_UNKNOWN);
        assertThat(event.getWidgetVersion()).isNull();
        assertThat(event.getResultId()).isEqualTo("");
        assertThat(event.getEventTime()).isEqualTo(0);
        assertThat(event.getDurationSinceSessionStart()).isEqualTo(0);
        assertThat(event.getDurationSincePreviousEvent()).isEqualTo(0);
    }
}
