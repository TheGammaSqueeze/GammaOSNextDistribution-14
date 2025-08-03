/*
 * Copyright (C) 2023 The Android Open Source Project
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
import android.speech.RecognitionPart;
import android.speech.SpeechRecognizer;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class RecognitionPartTest {
    /**
     * Test that a {@link RecognitionPart} built with default optional parameters is valid.
     */
    @Test
    public void test_recognitionPartWithDefaultParameters_isValid() {
        final RecognitionPart part = new RecognitionPart.Builder("part").build();

        // Check that the formatted list is null.
        assertThat(part.getFormattedText()).isNull();

        // Check that the default timestamp is non-negative.
        assertThat(part.getTimestampMillis()).isAtLeast(0);

        // Check that the default confidence level is CONFIDENCE_LEVEL_UNKNOWN.
        assertThat(part.getConfidenceLevel()).isEqualTo(RecognitionPart.CONFIDENCE_LEVEL_UNKNOWN);
    }

    /**
     * Test that a {@link RecognitionPart} built with a {@code null} raw text
     * throws a {@link NullPointerException}.
     */
    @Test
    public void test_recognitionPartWithNullRawText_isInvalid() {
        // Fails because the given raw text is null.
        assertThrows(NullPointerException.class,
                () -> new RecognitionPart.Builder(null).build());
    }

    /**
     * Test that a {@link RecognitionPart} built with a set {@code null} formatted text
     * throws a {@link NullPointerException}.
     */
    @Test
    public void test_recognitionPartWithSetNullFormattedText_isInvalid() {
        // Fails because the set formatted text is null. The default null value would be valid.
        assertThrows(NullPointerException.class,
                () -> new RecognitionPart.Builder("part").setFormattedText(null).build());
    }

    /**
     * Test that an {@link RecognitionPart} built with a negative timestamp.
     * throws an {@link IllegalArgumentException}.
     */
    @Test
    public void test_recognitionPartWithNegativeTimestamp_isInvalid() {
        // Fails because the timestamp is negative.
        assertThrows(IllegalArgumentException.class,
                () -> new RecognitionPart.Builder("part").setTimestampMillis(-123).build());
    }

    /**
     * Test that an {@link RecognitionPart} built with an undefined confidence level.
     * throws an {@link IllegalArgumentException}.
     */
    @Test
    public void test_recognitionPartWithUndefinedConfidenceLevel_isInvalid() {
        // Fails because the confidence level is undefined.
        assertThrows(IllegalArgumentException.class,
                () -> new RecognitionPart.Builder("part").setConfidenceLevel(100).build());
    }

    /**
     * Test that set {@link RecognitionPart} fields are returned by the generated getters.
     */
    @Test
    public void test_recognitionPartSetAndGet_equalValues() {
        String rawText = "part";
        String formattedText = "Part.";
        long timestamp = 500;
        int confidenceLevel = RecognitionPart.CONFIDENCE_LEVEL_HIGH;

        // A recognition part with rawText set in the Builder constructor.
        final RecognitionPart part1 = new RecognitionPart.Builder(rawText)
                .setFormattedText(formattedText)
                .setTimestampMillis(timestamp)
                .setConfidenceLevel(confidenceLevel)
                .build();

        // Check that the retrieved values are the same as the provided ones.
        assertThat(part1.getRawText()).isEqualTo(rawText);
        assertThat(part1.getFormattedText()).isEqualTo(formattedText);
        assertThat(part1.getTimestampMillis()).isEqualTo(timestamp);
        assertThat(part1.getConfidenceLevel()).isEqualTo(confidenceLevel);

        // A RecognitionPart with rawText set in the Builder setter.
        final RecognitionPart part2 = new RecognitionPart.Builder("").setRawText(rawText).build();

        // Check that the retrieved values are the same as the provided ones.
        assertThat(part2.getRawText()).isEqualTo(rawText);
    }

    /**
     * Test that an ArrayList&lt;{@link RecognitionPart}&gt; can be written to a {@link Bundle}
     * and then read and remain of the same value. This represents the scenario of how the value
     * types are to be used as part of {@link SpeechRecognizer} result.
     */
    @Test
    public void test_bundleWrittenAndRead_equalValues() {
        final String bundleKey = SpeechRecognizer.RECOGNITION_PARTS;

        final RecognitionPart part1 =
                new RecognitionPart.Builder("hello")
                        .setFormattedText("Hello,")
                        .setTimestampMillis(500)
                        .setConfidenceLevel(RecognitionPart.CONFIDENCE_LEVEL_MEDIUM_HIGH)
                        .build();
        final RecognitionPart part2 =
                new RecognitionPart.Builder("world")
                        .setFormattedText("World!")
                        .setTimestampMillis(1000)
                        .setConfidenceLevel(RecognitionPart.CONFIDENCE_LEVEL_HIGH)
                        .build();

        Bundle bundle = new Bundle();
        bundle.putParcelableArrayList(bundleKey, new ArrayList<>(List.of(part1, part2)));

        // Check that the retrieved values are the same as the provided ones.
        assertThat(bundle.getParcelableArrayList(bundleKey, RecognitionPart.class))
                .containsExactly(part1, part2).inOrder();
    }
}
