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

package com.android.adservices;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.test.filters.SmallTest;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.ImmutableLongArray;

import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Unit tests for {@link AdServicesParcelableUtil}. */
@SmallTest
public class AdServicesParcelableUtilTest {
    @Test
    public void testWriteNullableToParcel_nullParcelThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        AdServicesParcelableUtil.writeNullableToParcel(
                                null, "test", Parcel::writeString));
    }

    @Test
    public void testWriteNullableToParcel_nullParcelWriterThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        AdServicesParcelableUtil.writeNullableToParcel(
                                Parcel.obtain(), "test", null));
    }

    @Test
    public void testReadNullableFromParcel_nullParcelThrows() {
        assertThrows(
                NullPointerException.class,
                () -> AdServicesParcelableUtil.readNullableFromParcel(null, Parcel::readString));
    }

    @Test
    public void testReadNullableFromParcel_nullParcelReaderThrows() {
        assertThrows(
                NullPointerException.class,
                () -> AdServicesParcelableUtil.readNullableFromParcel(Parcel.obtain(), null));
    }

    @Test
    public void testWriteNullableToParcelThenRead_nonNullSuccess() {
        final int originalInt = 123;
        Parcel targetParcel = Parcel.obtain();

        AdServicesParcelableUtil.writeNullableToParcel(targetParcel, originalInt, Parcel::writeInt);
        targetParcel.setDataPosition(0);
        final int intFromParcel =
                Objects.requireNonNull(
                        AdServicesParcelableUtil.readNullableFromParcel(
                                targetParcel, Parcel::readInt));

        assertThat(intFromParcel).isEqualTo(originalInt);
    }

    @Test
    public void testWriteNullableToParcelThenRead_nullNonParcelableSuccess() {
        Parcel targetParcel = Parcel.obtain();

        AdServicesParcelableUtil.writeNullableToParcel(targetParcel, null, Parcel::writeString);
        targetParcel.setDataPosition(0);
        final String stringFromParcel =
                AdServicesParcelableUtil.readNullableFromParcel(targetParcel, Parcel::readString);

        assertThat(stringFromParcel).isNull();
    }

    @Test
    public void testWriteNullableToParcelThenRead_nullParcelableSuccess() {
        Parcel targetParcel = Parcel.obtain();

        AdServicesParcelableUtil.writeNullableToParcel(
                targetParcel,
                null,
                (AdServicesParcelableUtil.ParcelWriter<TestParcelable>)
                        (targetParcel1, sourceObject) ->
                                targetParcel1.writeParcelable(sourceObject, 0));
        targetParcel.setDataPosition(0);
        final TestParcelable parcelableFromParcel =
                AdServicesParcelableUtil.readNullableFromParcel(
                        targetParcel, TestParcelable.CREATOR::createFromParcel);

        assertThat(parcelableFromParcel).isNull();
    }

    @Test
    public void testWriteMapToParcel_nullParcelThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        AdServicesParcelableUtil.writeMapToParcel(
                                null, new HashMap<Integer, TestParcelable>()));
    }

    @Test
    public void testWriteMapToParcel_nullMapThrows() {
        assertThrows(
                NullPointerException.class,
                () -> AdServicesParcelableUtil.writeMapToParcel(Parcel.obtain(), null));
    }

    @Test
    public void testReadMapFromParcel_nullParcelThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        AdServicesParcelableUtil.readMapFromParcel(
                                null, Integer::valueOf, TestParcelable.class));
    }

    @Test
    public void testReadMapFromParcel_nullConverterThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        AdServicesParcelableUtil.readMapFromParcel(
                                Parcel.obtain(), null, TestParcelable.class));
    }

    @Test
    public void testReadMapFromParcel_nullClassThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        AdServicesParcelableUtil.readMapFromParcel(
                                Parcel.obtain(), Integer::valueOf, null));
    }

    @Test
    public void testWriteMapToParcelThenRead_success() {
        final ImmutableMap<Integer, TestParcelable> originalMap =
                ImmutableMap.of(1, new TestParcelable("one", 1), 2, new TestParcelable("two", 2));
        Parcel targetParcel = Parcel.obtain();

        AdServicesParcelableUtil.writeMapToParcel(targetParcel, originalMap);
        targetParcel.setDataPosition(0);
        final ImmutableMap<Integer, TestParcelable> mapFromParcel =
                ImmutableMap.copyOf(
                        AdServicesParcelableUtil.readMapFromParcel(
                                targetParcel, Integer::parseInt, TestParcelable.class));

        assertThat(mapFromParcel).containsExactlyEntriesIn(originalMap);
    }

    @Test
    public void testWriteSetToParcel_nullParcelThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        AdServicesParcelableUtil.writeSetToParcel(
                                null, new HashSet<TestParcelable>()));
    }

    @Test
    public void testWriteSetToParcel_nullSetThrows() {
        assertThrows(
                NullPointerException.class,
                () -> AdServicesParcelableUtil.writeSetToParcel(Parcel.obtain(), null));
    }

    @Test
    public void testReadSetFromParcel_nullParcelThrows() {
        assertThrows(
                NullPointerException.class,
                () -> AdServicesParcelableUtil.readSetFromParcel(null, TestParcelable.CREATOR));
    }

    @Test
    public void testReadSetFromParcel_nullCreatorThrows() {
        assertThrows(
                NullPointerException.class,
                () -> AdServicesParcelableUtil.readSetFromParcel(Parcel.obtain(), null));
    }

    @Test
    public void testWriteSetToParcelThenRead_success() {
        final ImmutableSet<TestParcelable> originalSet =
                ImmutableSet.of(new TestParcelable("one", 1), new TestParcelable("two", 2));
        Parcel targetParcel = Parcel.obtain();

        AdServicesParcelableUtil.writeSetToParcel(targetParcel, originalSet);
        targetParcel.setDataPosition(0);
        final ImmutableSet<TestParcelable> setFromParcel =
                ImmutableSet.copyOf(
                        AdServicesParcelableUtil.readSetFromParcel(
                                targetParcel, TestParcelable.CREATOR));

        assertThat(setFromParcel).containsExactlyElementsIn(originalSet);
    }

    @Test
    public void testWriteStringSetToParcel_nullParcelThrows() {
        assertThrows(
                NullPointerException.class,
                () -> AdServicesParcelableUtil.writeStringSetToParcel(null, new HashSet<>()));
    }

    @Test
    public void testWriteStringSetToParcel_nullSetThrows() {
        assertThrows(
                NullPointerException.class,
                () -> AdServicesParcelableUtil.writeStringSetToParcel(Parcel.obtain(), null));
    }

    @Test
    public void testReadStringSetFromParcel_nullParcelThrows() {
        assertThrows(
                "Null Parcel should have thrown NPE",
                NullPointerException.class,
                () -> AdServicesParcelableUtil.readStringSetFromParcel(null));
    }

    @Test
    public void testWriteStringSetToParcelThenRead_success() {
        final ImmutableSet<String> originalSet = ImmutableSet.of("one", "two");
        Parcel targetParcel = Parcel.obtain();

        AdServicesParcelableUtil.writeStringSetToParcel(targetParcel, originalSet);
        targetParcel.setDataPosition(0);
        final ImmutableSet<String> setFromParcel =
                ImmutableSet.copyOf(AdServicesParcelableUtil.readStringSetFromParcel(targetParcel));

        assertThat(setFromParcel).containsExactlyElementsIn(originalSet);
    }

    @Test
    public void testWriteIntegerSetToParcel_nullParcelThrows() {
        assertThrows(
                NullPointerException.class,
                () -> AdServicesParcelableUtil.writeIntegerSetToParcel(null, new HashSet<>()));
    }

    @Test
    public void testWriteIntegerSetToParcel_nullSetThrows() {
        assertThrows(
                NullPointerException.class,
                () -> AdServicesParcelableUtil.writeIntegerSetToParcel(Parcel.obtain(), null));
    }

    @Test
    public void testReadIntegerSetFromParcel_nullParcelThrows() {
        assertThrows(
                "Null Parcel should have thrown NPE",
                NullPointerException.class,
                () -> AdServicesParcelableUtil.readIntegerSetFromParcel(null));
    }

    @Test
    public void testWriteIntegerSetToParcelThenRead_success() {
        final ImmutableSet<Integer> originalSet = ImmutableSet.of(1, 2);
        Parcel targetParcel = Parcel.obtain();

        AdServicesParcelableUtil.writeIntegerSetToParcel(targetParcel, originalSet);
        targetParcel.setDataPosition(0);
        final ImmutableSet<Integer> setFromParcel =
                ImmutableSet.copyOf(
                        AdServicesParcelableUtil.readIntegerSetFromParcel(targetParcel));

        assertThat(setFromParcel).containsExactlyElementsIn(originalSet);
    }

    @Test
    public void testWriteIntegerSetToParcelThenRead_nullValueSkippedSuccess() {
        final Set<Integer> originalSet = new HashSet<>(Arrays.asList(1, null, 2));
        Parcel targetParcel = Parcel.obtain();

        AdServicesParcelableUtil.writeIntegerSetToParcel(targetParcel, originalSet);
        targetParcel.setDataPosition(0);
        final Set<Integer> setFromParcel =
                AdServicesParcelableUtil.readIntegerSetFromParcel(targetParcel);

        assertThat(setFromParcel).doesNotContain(null);
        assertThat(setFromParcel).containsExactly(1, 2);
    }

    @Test
    public void testWriteInstantListToParcel_nullParcelThrows() {
        assertThrows(
                NullPointerException.class,
                () -> AdServicesParcelableUtil.writeInstantListToParcel(null, new ArrayList<>()));
    }

    @Test
    public void testWriteInstantListToParcel_nullListThrows() {
        assertThrows(
                NullPointerException.class,
                () -> AdServicesParcelableUtil.writeInstantListToParcel(Parcel.obtain(), null));
    }

    @Test
    public void testReadInstantListFromParcel_nullParcelThrows() {
        assertThrows(
                "Null Parcel should have thrown NPE",
                NullPointerException.class,
                () -> AdServicesParcelableUtil.readInstantListFromParcel(null));
    }

    @Test
    public void testWriteInstantListToParcelThenRead_success() {
        final ImmutableList<Instant> originalList =
                ImmutableList.of(
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.plusMillis(500));
        Parcel targetParcel = Parcel.obtain();

        AdServicesParcelableUtil.writeInstantListToParcel(targetParcel, originalList);
        targetParcel.setDataPosition(0);
        final ImmutableList<Instant> listFromParcel =
                ImmutableList.copyOf(
                        AdServicesParcelableUtil.readInstantListFromParcel(targetParcel));

        assertThat(listFromParcel).containsExactlyElementsIn(originalList);
    }

    @Test
    public void testWriteInstantListToParcel_skipsErrors() {
        final ImmutableList<Instant> originalList =
                ImmutableList.of(
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        Instant.MAX,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.plusMillis(500));
        final ImmutableLongArray expectedList =
                ImmutableLongArray.of(
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.toEpochMilli(),
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.plusMillis(500).toEpochMilli());
        Parcel targetParcel = Parcel.obtain();

        AdServicesParcelableUtil.writeInstantListToParcel(targetParcel, originalList);
        targetParcel.setDataPosition(0);

        final int writtenArraySize = targetParcel.readInt();
        assertThat(writtenArraySize).isEqualTo(expectedList.length());

        final long[] writtenArray = new long[writtenArraySize];
        targetParcel.readLongArray(writtenArray);
        assertThat(writtenArray).asList().containsExactlyElementsIn(expectedList.asList());
    }

    public static class TestParcelable implements Parcelable {
        @NonNull private final String mString;
        private final long mLong;

        @NonNull
        public static final Creator<TestParcelable> CREATOR =
                new Creator<TestParcelable>() {
                    @Override
                    public TestParcelable createFromParcel(@NonNull Parcel source) {
                        Objects.requireNonNull(source);
                        return new TestParcelable(source);
                    }

                    @Override
                    public TestParcelable[] newArray(int size) {
                        return new TestParcelable[size];
                    }
                };

        public TestParcelable(@NonNull String inString, long inLong) {
            Objects.requireNonNull(inString);
            mString = inString;
            mLong = inLong;
        }

        public TestParcelable(@NonNull Parcel in) {
            Objects.requireNonNull(in);

            mString = in.readString();
            mLong = in.readLong();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TestParcelable)) return false;
            TestParcelable that = (TestParcelable) o;
            return mLong == that.mLong && mString.equals(that.mString);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mString, mLong);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            Objects.requireNonNull(dest);

            dest.writeString(mString);
            dest.writeLong(mLong);
        }
    }
}
