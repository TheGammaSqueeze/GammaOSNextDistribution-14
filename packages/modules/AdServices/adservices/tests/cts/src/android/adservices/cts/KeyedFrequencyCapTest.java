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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.KeyedFrequencyCap;
import android.adservices.common.KeyedFrequencyCapFixture;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;

/** Unit tests for {@link KeyedFrequencyCap}. */
@SmallTest
public class KeyedFrequencyCapTest {
    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildValidKeyedFrequencyCap_success() {
        final KeyedFrequencyCap originalCap =
                new KeyedFrequencyCap.Builder(
                                KeyedFrequencyCapFixture.KEY1,
                                KeyedFrequencyCapFixture.VALID_COUNT,
                                KeyedFrequencyCapFixture.ONE_DAY_DURATION)
                        .build();

        assertThat(originalCap.getAdCounterKey()).isEqualTo(KeyedFrequencyCapFixture.KEY1);
        assertThat(originalCap.getMaxCount()).isEqualTo(KeyedFrequencyCapFixture.VALID_COUNT);
        assertThat(originalCap.getInterval()).isEqualTo(KeyedFrequencyCapFixture.ONE_DAY_DURATION);
    }

    @Test
    public void testParcelKeyedFrequencyCap_success() {
        final KeyedFrequencyCap originalCap =
                new KeyedFrequencyCap.Builder(
                                KeyedFrequencyCapFixture.KEY1,
                                KeyedFrequencyCapFixture.VALID_COUNT,
                                KeyedFrequencyCapFixture.ONE_DAY_DURATION)
                        .build();

        Parcel targetParcel = Parcel.obtain();
        originalCap.writeToParcel(targetParcel, 0);
        targetParcel.setDataPosition(0);
        final KeyedFrequencyCap capFromParcel =
                KeyedFrequencyCap.CREATOR.createFromParcel(targetParcel);

        assertThat(capFromParcel.getAdCounterKey()).isEqualTo(KeyedFrequencyCapFixture.KEY1);
        assertThat(capFromParcel.getMaxCount()).isEqualTo(KeyedFrequencyCapFixture.VALID_COUNT);
        assertThat(capFromParcel.getInterval())
                .isEqualTo(KeyedFrequencyCapFixture.ONE_DAY_DURATION);
    }

    @Test
    public void testEqualsIdentical_success() {
        final KeyedFrequencyCap originalCap =
                KeyedFrequencyCapFixture.getValidKeyedFrequencyCapBuilderOncePerDay(
                                KeyedFrequencyCapFixture.KEY1)
                        .build();
        final KeyedFrequencyCap identicalCap =
                KeyedFrequencyCapFixture.getValidKeyedFrequencyCapBuilderOncePerDay(
                                KeyedFrequencyCapFixture.KEY1)
                        .build();

        assertThat(originalCap.equals(identicalCap)).isTrue();
    }

    @Test
    public void testEqualsDifferent_success() {
        final KeyedFrequencyCap originalCap =
                KeyedFrequencyCapFixture.getValidKeyedFrequencyCapBuilderOncePerDay(
                                KeyedFrequencyCapFixture.KEY1)
                        .build();
        final KeyedFrequencyCap differentCap =
                KeyedFrequencyCapFixture.getValidKeyedFrequencyCapBuilderOncePerDay(
                                KeyedFrequencyCapFixture.KEY2)
                        .build();

        assertThat(originalCap.equals(differentCap)).isFalse();
    }

    @Test
    public void testEqualsNull_success() {
        final KeyedFrequencyCap originalCap =
                KeyedFrequencyCapFixture.getValidKeyedFrequencyCapBuilderOncePerDay(
                                KeyedFrequencyCapFixture.KEY1)
                        .build();
        final KeyedFrequencyCap nullCap = null;

        assertThat(originalCap.equals(nullCap)).isFalse();
    }

    @Test
    public void testHashCodeIdentical_success() {
        final KeyedFrequencyCap originalCap =
                KeyedFrequencyCapFixture.getValidKeyedFrequencyCapBuilderOncePerDay(
                                KeyedFrequencyCapFixture.KEY1)
                        .build();
        final KeyedFrequencyCap identicalCap =
                KeyedFrequencyCapFixture.getValidKeyedFrequencyCapBuilderOncePerDay(
                                KeyedFrequencyCapFixture.KEY1)
                        .build();

        assertThat(originalCap.hashCode()).isEqualTo(identicalCap.hashCode());
    }

    @Test
    public void testHashCodeDifferent_success() {
        final KeyedFrequencyCap originalCap =
                KeyedFrequencyCapFixture.getValidKeyedFrequencyCapBuilderOncePerDay(
                                KeyedFrequencyCapFixture.KEY1)
                        .build();
        final KeyedFrequencyCap differentCap =
                KeyedFrequencyCapFixture.getValidKeyedFrequencyCapBuilderOncePerDay(
                                KeyedFrequencyCapFixture.KEY2)
                        .build();

        assertThat(originalCap.hashCode()).isNotEqualTo(differentCap.hashCode());
    }

    @Test
    public void testToString() {
        final KeyedFrequencyCap originalCap =
                new KeyedFrequencyCap.Builder(
                                KeyedFrequencyCapFixture.KEY1,
                                KeyedFrequencyCapFixture.VALID_COUNT,
                                KeyedFrequencyCapFixture.ONE_DAY_DURATION)
                        .build();

        final String expectedString =
                String.format(
                        "KeyedFrequencyCap{mAdCounterKey=%d, mMaxCount=%d, mInterval=%s}",
                        KeyedFrequencyCapFixture.KEY1,
                        KeyedFrequencyCapFixture.VALID_COUNT,
                        KeyedFrequencyCapFixture.ONE_DAY_DURATION);
        assertThat(originalCap.toString()).isEqualTo(expectedString);
    }

    @Test
    public void testBuildValidKeyedFrequencyCap_allSettersOverwriteSuccess() {
        final Duration twoDays = KeyedFrequencyCapFixture.ONE_DAY_DURATION.plusDays(1);

        final KeyedFrequencyCap originalCap =
                new KeyedFrequencyCap.Builder(
                                KeyedFrequencyCapFixture.KEY1,
                                KeyedFrequencyCapFixture.VALID_COUNT,
                                KeyedFrequencyCapFixture.ONE_DAY_DURATION)
                        .setAdCounterKey(KeyedFrequencyCapFixture.KEY2)
                        .setMaxCount(KeyedFrequencyCapFixture.FILTER_EXCEED_COUNT)
                        .setInterval(twoDays)
                        .build();

        assertThat(originalCap.getAdCounterKey()).isEqualTo(KeyedFrequencyCapFixture.KEY2);
        assertThat(originalCap.getMaxCount())
                .isEqualTo(KeyedFrequencyCapFixture.FILTER_EXCEED_COUNT);
        assertThat(originalCap.getInterval()).isEqualTo(twoDays);
    }

    @Test
    public void testBuildNegativeCount_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new KeyedFrequencyCap.Builder(
                                KeyedFrequencyCapFixture.KEY1,
                                -1,
                                KeyedFrequencyCapFixture.ONE_DAY_DURATION));
    }

    @Test
    public void testSetNegativeCount_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new KeyedFrequencyCap.Builder(
                                        KeyedFrequencyCapFixture.KEY1,
                                        KeyedFrequencyCapFixture.VALID_COUNT,
                                        KeyedFrequencyCapFixture.ONE_DAY_DURATION)
                                .setMaxCount(-1));
    }

    @Test
    public void testBuildZeroCount_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new KeyedFrequencyCap.Builder(
                                KeyedFrequencyCapFixture.KEY1,
                                0,
                                KeyedFrequencyCapFixture.ONE_DAY_DURATION));
    }

    @Test
    public void testSetZeroCount_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new KeyedFrequencyCap.Builder(
                                        KeyedFrequencyCapFixture.KEY1,
                                        KeyedFrequencyCapFixture.VALID_COUNT,
                                        KeyedFrequencyCapFixture.ONE_DAY_DURATION)
                                .setMaxCount(0));
    }

    @Test
    public void testBuildNegativeInterval_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new KeyedFrequencyCap.Builder(
                                KeyedFrequencyCapFixture.KEY1,
                                KeyedFrequencyCapFixture.VALID_COUNT,
                                Duration.ofSeconds(-1)));
    }

    @Test
    public void testSetNegativeInterval_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new KeyedFrequencyCap.Builder(
                                        KeyedFrequencyCapFixture.KEY1,
                                        KeyedFrequencyCapFixture.VALID_COUNT,
                                        KeyedFrequencyCapFixture.ONE_DAY_DURATION)
                                .setInterval(Duration.ofSeconds(-1)));
    }

    @Test
    public void testBuildZeroInterval_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new KeyedFrequencyCap.Builder(
                                KeyedFrequencyCapFixture.KEY1,
                                KeyedFrequencyCapFixture.VALID_COUNT,
                                Duration.ofSeconds(0)));
    }

    @Test
    public void testSetZeroInterval_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new KeyedFrequencyCap.Builder(
                                        KeyedFrequencyCapFixture.KEY1,
                                        KeyedFrequencyCapFixture.VALID_COUNT,
                                        KeyedFrequencyCapFixture.ONE_DAY_DURATION)
                                .setInterval(Duration.ofSeconds(0)));
    }

    @Test
    public void testBuildIntervalLessThanSecond_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new KeyedFrequencyCap.Builder(
                                KeyedFrequencyCapFixture.KEY1,
                                KeyedFrequencyCapFixture.VALID_COUNT,
                                Duration.ofMillis(50)));
    }

    @Test
    public void testSetIntervalLessThanSecond_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new KeyedFrequencyCap.Builder(
                                        KeyedFrequencyCapFixture.KEY1,
                                        KeyedFrequencyCapFixture.VALID_COUNT,
                                        KeyedFrequencyCapFixture.ONE_DAY_DURATION)
                                .setInterval(Duration.ofMillis(50)));
    }

    @Test
    public void testBuildIntervalMoreThanLimit_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new KeyedFrequencyCap.Builder(
                                KeyedFrequencyCapFixture.KEY1,
                                KeyedFrequencyCapFixture.VALID_COUNT,
                                Duration.ofDays(9999)));
    }

    @Test
    public void testSetIntervalMoreThanLimit_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new KeyedFrequencyCap.Builder(
                                        KeyedFrequencyCapFixture.KEY1,
                                        KeyedFrequencyCapFixture.VALID_COUNT,
                                        KeyedFrequencyCapFixture.ONE_DAY_DURATION)
                                .setInterval(Duration.ofDays(9999)));
    }

    @Test
    public void testGetSizeInBytes() {
        assertEquals(
                20,
                KeyedFrequencyCapFixture.getValidKeyedFrequencyCapBuilderOncePerDay(
                                KeyedFrequencyCapFixture.KEY1)
                        .build()
                        .getSizeInBytes());
    }

    @Test
    public void testJsonSerialization() throws JSONException {
        KeyedFrequencyCap toSerialize =
                KeyedFrequencyCapFixture.getValidKeyedFrequencyCapBuilderOncePerDay(
                                KeyedFrequencyCapFixture.KEY1)
                        .build();
        assertEquals(toSerialize, KeyedFrequencyCap.fromJson(toSerialize.toJson()));
    }

    @Test
    public void testJsonSerializationInvalidKeyThrows() throws JSONException {
        KeyedFrequencyCap toSerialize =
                KeyedFrequencyCapFixture.getValidKeyedFrequencyCapBuilderOncePerDay(
                                KeyedFrequencyCapFixture.KEY1)
                        .build();
        JSONObject json = toSerialize.toJson();
        json.put(KeyedFrequencyCap.AD_COUNTER_KEY_FIELD_NAME, new Object());
        assertThrows(
                JSONException.class,
                () -> KeyedFrequencyCap.fromJson(json));
    }

    @Test
    public void testJsonSerializationMissingAdCounterKey() throws JSONException {
        KeyedFrequencyCap toSerialize =
                KeyedFrequencyCapFixture.getValidKeyedFrequencyCapBuilderOncePerDay(
                                KeyedFrequencyCapFixture.KEY1)
                        .build();
        JSONObject json = toSerialize.toJson();
        json.remove(KeyedFrequencyCap.AD_COUNTER_KEY_FIELD_NAME);
        assertThrows(JSONException.class, () -> KeyedFrequencyCap.fromJson(json));
    }

    @Test
    public void testJsonSerializationMissingMaxCount() throws JSONException {
        KeyedFrequencyCap toSerialize =
                KeyedFrequencyCapFixture.getValidKeyedFrequencyCapBuilderOncePerDay(
                                KeyedFrequencyCapFixture.KEY1)
                        .build();
        JSONObject json = toSerialize.toJson();
        json.remove(KeyedFrequencyCap.MAX_COUNT_FIELD_NAME);
        assertThrows(JSONException.class, () -> KeyedFrequencyCap.fromJson(json));
    }

    @Test
    public void testJsonSerializationMissingIntervalInSeconds() throws JSONException {
        KeyedFrequencyCap toSerialize =
                KeyedFrequencyCapFixture.getValidKeyedFrequencyCapBuilderOncePerDay(
                                KeyedFrequencyCapFixture.KEY1)
                        .build();
        JSONObject json = toSerialize.toJson();
        json.remove(KeyedFrequencyCap.INTERVAL_FIELD_NAME);
        assertThrows(JSONException.class, () -> KeyedFrequencyCap.fromJson(json));
    }
}
