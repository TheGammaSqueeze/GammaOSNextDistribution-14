/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.voicerecognition.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.Bundle;
import android.speech.AlternativeSpan;
import android.speech.AlternativeSpans;
import android.speech.SpeechRecognizer;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class AlternativeSpansTest {
    /**
     * Test that an AlternativeSpan built without a valid start position throws a RuntimeException.
     */
    @Test
    public void testInvalidAlternativeSpanStartPosition() {
        // Fails because the range start is negative.
        assertThrows(RuntimeException.class,
                () -> new AlternativeSpan(-1, 10, List.of("alt")));
    }

    /**
     * Test that an AlternativeSpan built without a valid end position throws a RuntimeException.
     */
    @Test
    public void testInvalidAlternativeSpanEndPosition() {
        // Fails because the range end is not greater than the range start.
        assertThrows(RuntimeException.class,
                () -> new AlternativeSpan(1, 1, List.of("alt")));
    }

    /**
     * Test that an AlternativeSpan built with an empty
     * list of alternative strings throws a RuntimeException.
     */
    @Test
    public void testInvalidAlternativesList() {
        // Fails because the set (immutable) list of alternatives is empty.
        assertThrows(RuntimeException.class,
                () -> new AlternativeSpan(1, 5, List.of()));

        // Fails because the set (mutable) list of alternatives is empty.
        assertThrows(RuntimeException.class,
                () -> new AlternativeSpan(5, 10, new ArrayList<>()));
    }

    /**
     * Test that set AlternativeSpan fields are returned by the generated getters.
     */
    @Test
    public void testAlternativeSpanSetAndGet() {
        int start = 1;
        int end = 10;

        AlternativeSpan altSpan = new AlternativeSpan(start, end, List.of("alt1", "alt2", "alt3"));

        assertThat(altSpan.getStartPosition()).isEqualTo(start);
        assertThat(altSpan.getEndPosition()).isEqualTo(end);
        assertThat(altSpan.getAlternatives())
                .containsExactly("alt1", "alt2", "alt3").inOrder();
    }

    /**
     * Test that set AlternativeSpans field is returned by the generated getter.
     */
    @Test
    public void testAlternativeSpansSetAndGet() {
        AlternativeSpan altSpan1 = new AlternativeSpan(1, 2, List.of("alt1", "alt2"));
        AlternativeSpan altSpan2 = new AlternativeSpan(3, 4, List.of("alt3", "alt4"));
        AlternativeSpan altSpan3 = new AlternativeSpan(5, 10, List.of("alt5", "alt6"));

        AlternativeSpans altSpans = new AlternativeSpans(List.of(altSpan1, altSpan2, altSpan3));

        assertThat(altSpans.getSpans()).containsExactly(altSpan1, altSpan2, altSpan3).inOrder();
    }

    /**
     * Test that an ArrayList&lt;{@link AlternativeSpans}&gt; can be written to a {@link Bundle}
     * and then read and remain the same value. This represents the scenario of how the test value
     * types are planned to be used as part of {@link android.speech.SpeechRecognizer} result.
     */
    @Test
    public void testBundleWriteAndRead() {
        final String bundleKey = SpeechRecognizer.RESULTS_ALTERNATIVES;

        AlternativeSpan altSpan1 = new AlternativeSpan(1, 2, List.of("alt1", "alt2"));
        AlternativeSpan altSpan2 = new AlternativeSpan(3, 4, List.of("alt3", "alt4"));
        AlternativeSpan altSpan3 = new AlternativeSpan(5, 10, List.of("alt5", "alt6"));

        AlternativeSpans altSpans1 = new AlternativeSpans(List.of(altSpan1));
        AlternativeSpans altSpans2 = new AlternativeSpans(List.of(altSpan2, altSpan3));

        Bundle bundle = new Bundle();
        bundle.putParcelableArrayList(bundleKey, new ArrayList<>(List.of(altSpans1, altSpans2)));

        assertThat(bundle.getParcelableArrayList(bundleKey, AlternativeSpans.class))
                .containsExactly(altSpans1, altSpans2).inOrder();
    }
}
