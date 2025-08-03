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

package android.healthconnect;

import static android.health.connect.Constants.DEFAULT_LONG;
import static android.health.connect.PageTokenWrapper.EMPTY_PAGE_TOKEN;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.PageTokenWrapper;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;

@RunWith(AndroidJUnit4.class)
public class PageTokenWrapperTest {
    private static final long MAX_ALLOWED_TIME_MILLIS = (1L << 44) - 1;
    private static final long MAX_ALLOWED_OFFSET = (1 << 18) - 1;

    @Test
    public void of_createInstance() {
        boolean isAscending = false;
        long timeMillis = 123;
        int offset = 456;
        PageTokenWrapper wrapper = PageTokenWrapper.of(isAscending, timeMillis, offset);

        assertThat(wrapper.isAscending()).isEqualTo(isAscending);
        assertThat(wrapper.timeMillis()).isEqualTo(timeMillis);
        assertThat(wrapper.offset()).isEqualTo(offset);
        assertThat(wrapper.isTimestampSet()).isTrue();
        assertThat(wrapper.isEmpty()).isFalse();
    }

    @Test
    public void of_offsetTooLarge_setToMax() {
        boolean isAscending = true;
        PageTokenWrapper wrapper =
                PageTokenWrapper.of(isAscending, /* timeMillis= */ 0, (int) MAX_ALLOWED_OFFSET + 1);
        assertThat(wrapper.offset()).isEqualTo(MAX_ALLOWED_OFFSET);
    }

    @Test
    public void of_invalidArgument_throws() {
        boolean isAscending = true;
        Throwable thrown;

        thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                PageTokenWrapper.of(
                                        isAscending, /* timeMillis= */ -1, /* offset= */ 0));
        assertThat(thrown.getMessage()).isEqualTo("timestamp can not be negative");

        thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                PageTokenWrapper.of(
                                        isAscending, /* timeMillis= */ 0, /* offset= */ -1));
        assertThat(thrown.getMessage()).isEqualTo("offset can not be negative");

        thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                PageTokenWrapper.of(
                                        isAscending, MAX_ALLOWED_TIME_MILLIS + 1, /* offset= */ 0));
        assertThat(thrown.getMessage()).isEqualTo("timestamp too large");
    }

    @Test
    public void ofAscending_timestampNotSet() {
        PageTokenWrapper wrapper = PageTokenWrapper.ofAscending(true);
        assertThat(wrapper.isAscending()).isTrue();
        assertThat(wrapper.isTimestampSet()).isFalse();
        assertThat(wrapper.isEmpty()).isFalse();

        wrapper = PageTokenWrapper.ofAscending(false);
        assertThat(wrapper.isAscending()).isFalse();
        assertThat(wrapper.isTimestampSet()).isFalse();
        assertThat(wrapper.isEmpty()).isFalse();
    }

    @Test
    public void from_validPageToken_expectCorrectResult() {
        boolean expectedIsAsc = true;
        boolean unusedDefaultIsAsc = false;
        long expectedTimeMillis = 1234;
        int expectedOffset = 5678;

        long validToken =
                PageTokenWrapper.of(expectedIsAsc, expectedTimeMillis, expectedOffset).encode();

        PageTokenWrapper wrapper = PageTokenWrapper.from(validToken, unusedDefaultIsAsc);

        assertThat(wrapper.isAscending()).isEqualTo(expectedIsAsc);
        assertThat(wrapper.timeMillis()).isEqualTo(expectedTimeMillis);
        assertThat(wrapper.offset()).isEqualTo(expectedOffset);
        assertThat(wrapper.isEmpty()).isFalse();
    }

    @Test
    public void from_pageTokenUnset_useDefaultIsAscending() {
        PageTokenWrapper wrapper =
                PageTokenWrapper.from(DEFAULT_LONG, /* defaultIsAscending= */ true);
        assertThat(wrapper.isAscending()).isTrue();
        assertThat(wrapper.isEmpty()).isFalse();

        wrapper = PageTokenWrapper.from(DEFAULT_LONG, /* defaultIsAscending= */ false);
        assertThat(wrapper.isAscending()).isFalse();
        assertThat(wrapper.isEmpty()).isFalse();
    }

    @Test
    public void from_negativePageToken_throws() {
        boolean unusedDefault = true;

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> PageTokenWrapper.from(-100, unusedDefault));
        assertThat(thrown).hasMessageThat().contains("pageToken cannot be negative");
    }

    @Test
    public void encodeAndFromToken_ascending_expectIsAscendingTrue() {
        boolean unusedDefault = false;
        PageTokenWrapper expected =
                PageTokenWrapper.of(/* isAscending= */ true, 1234, /* offset= */ 0);
        long token = expected.encode();

        PageTokenWrapper result = PageTokenWrapper.from(token, unusedDefault);
        assertThat(result.isAscending()).isTrue();
    }

    @Test
    public void encodeAndFromToken_descending_expectIsAscendingFalse() {
        boolean unusedDefault = true;
        PageTokenWrapper wrapper =
                PageTokenWrapper.of(/* isAscending= */ false, 5678, /* offset= */ 0);
        long token = wrapper.encode();

        PageTokenWrapper result = PageTokenWrapper.from(token, unusedDefault);
        assertThat(result.isAscending()).isFalse();
    }

    @Test
    public void encodeAndFromToken_currentTimestamp_expectCorrectTime() {
        boolean unusedDefault = true;
        long nowTimeMillis = Instant.now().toEpochMilli();
        PageTokenWrapper wrapper =
                PageTokenWrapper.of(/* isAscending= */ false, nowTimeMillis, /* offset= */ 0);
        long token = wrapper.encode();

        PageTokenWrapper result = PageTokenWrapper.from(token, unusedDefault);
        assertThat(result.timeMillis()).isEqualTo(nowTimeMillis);
    }

    @Test
    public void encodeAndFromToken_minTimestamps_expectCorrectTime() {
        boolean unusedDefault = false;
        PageTokenWrapper wrapper =
                PageTokenWrapper.of(/* isAscending= */ true, /* timeMillis= */ 0, /* offset= */ 0);
        long token = wrapper.encode();

        PageTokenWrapper result = PageTokenWrapper.from(token, unusedDefault);
        assertThat(result.timeMillis()).isEqualTo(0);
    }

    @Test
    public void encodeAndFromToken_maxTimestamps_expectCorrectTime() {
        boolean unusedDefault = true;
        PageTokenWrapper wrapper =
                PageTokenWrapper.of(
                        /* isAscending= */ false, MAX_ALLOWED_TIME_MILLIS, /* offset= */ 0);
        long token = wrapper.encode();

        PageTokenWrapper result = PageTokenWrapper.from(token, unusedDefault);
        assertThat(result.timeMillis()).isEqualTo(MAX_ALLOWED_TIME_MILLIS);
    }

    @Test
    public void encodeAndFromToken_maxOffset_expectCorrectResult() {
        boolean unusedDefault = true;
        int maxOffset = (1 << 18) - 1;
        long timestamp = Instant.now().toEpochMilli();

        PageTokenWrapper wrapper =
                PageTokenWrapper.of(/* isAscending= */ false, timestamp, maxOffset);
        long token = wrapper.encode();

        PageTokenWrapper result = PageTokenWrapper.from(token, unusedDefault);
        assertThat(result.offset()).isEqualTo(maxOffset);
    }

    @Test
    public void encodeAndFromToken_minOffset_expectCorrectResult() {
        boolean unusedDefault = false;
        int minOffset = 0;
        long timestamp = Instant.now().toEpochMilli();
        PageTokenWrapper wrapper =
                PageTokenWrapper.of(/* isAscending= */ true, timestamp, minOffset);
        long token = wrapper.encode();

        PageTokenWrapper result = PageTokenWrapper.from(token, unusedDefault);
        assertThat(result.offset()).isEqualTo(minOffset);
    }

    @Test
    public void encode_pageTokenUnset_returnsDefaultLong() {
        assertThat(EMPTY_PAGE_TOKEN.encode()).isEqualTo(DEFAULT_LONG);
        assertThat(PageTokenWrapper.ofAscending(true).encode()).isEqualTo(DEFAULT_LONG);
        assertThat(PageTokenWrapper.ofAscending(false).encode()).isEqualTo(DEFAULT_LONG);
    }

    @Test
    public void emptyPageToken_isEmpty_expectTrue() {
        assertThat(EMPTY_PAGE_TOKEN.isEmpty()).isTrue();
    }

    @Test
    public void equals_sameValue_expectTrue() {
        PageTokenWrapper wrapper1 =
                PageTokenWrapper.of(
                        /* isAscending= */ false, /* timeMillis= */ 1234, /* offset= */ 567);
        PageTokenWrapper wrapper2 =
                PageTokenWrapper.of(
                        /* isAscending= */ false, /* timeMillis= */ 1234, /* offset= */ 567);

        assertThat(wrapper1.equals(wrapper2)).isTrue();
    }

    @Test
    public void equals_differentValue_expectFalse() {
        PageTokenWrapper wrapper =
                PageTokenWrapper.of(
                        /* isAscending= */ false, /* timeMillis= */ 1234, /* offset= */ 567);
        PageTokenWrapper differentIsAscending =
                PageTokenWrapper.of(
                        /* isAscending= */ true, /* timeMillis= */ 1234, /* offset= */ 567);
        PageTokenWrapper differentTime =
                PageTokenWrapper.of(
                        /* isAscending= */ false, /* timeMillis= */ 123, /* offset= */ 567);
        PageTokenWrapper differentOffset =
                PageTokenWrapper.of(
                        /* isAscending= */ false, /* timeMillis= */ 1234, /* offset= */ 5678);

        assertThat(wrapper.equals(differentIsAscending)).isFalse();
        assertThat(wrapper.equals(differentTime)).isFalse();
        assertThat(wrapper.equals(differentOffset)).isFalse();
    }

    @Test
    public void equals_nonEmptyAndEmpty_expectFalse() {
        PageTokenWrapper ascWrapper = PageTokenWrapper.ofAscending(true);
        PageTokenWrapper descWrapper = PageTokenWrapper.ofAscending(false);

        assertThat(EMPTY_PAGE_TOKEN.equals(ascWrapper)).isFalse();
        assertThat(EMPTY_PAGE_TOKEN.equals(descWrapper)).isFalse();
    }
}
