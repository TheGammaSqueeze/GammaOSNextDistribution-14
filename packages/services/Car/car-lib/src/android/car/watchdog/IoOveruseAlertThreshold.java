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

package android.car.watchdog;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.os.Parcelable;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;

/**
 * System-wide disk I/O overuse alert threshold.
 *
 * @hide
 */
@SystemApi
public final class IoOveruseAlertThreshold implements Parcelable {
    /**
     * Duration over which the given written bytes per second should be checked against.
     *
     * <p>Non-zero duration must provided in seconds.
     */
    @SuppressLint({"MethodNameUnits"})
    private long mDurationInSeconds;

    /**
     * Alert I/O overuse on reaching the written bytes/second over {@link #mDurationInSeconds}.
     *
     * <p>Must provide non-zero bytes.
     */
    private long mWrittenBytesPerSecond;

    /**
     * Creates a new IoOveruseAlertThreshold.
     *
     * @param durationInSeconds
     *   Duration over which the given written bytes per second should be checked against.
     *
     *   <p>Non-zero duration must provided in seconds.
     * @param writtenBytesPerSecond
     *   Alert I/O overuse on reaching the written bytes/second over {@link #mDurationInSeconds}.
     *
     *   <p>Must provide non-zero bytes.
     * @hide
     */
    @TestApi
    public IoOveruseAlertThreshold(
            @SuppressLint({ "MethodNameUnits" }) long durationInSeconds,
            long writtenBytesPerSecond) {
        this.mDurationInSeconds = durationInSeconds;
        this.mWrittenBytesPerSecond = writtenBytesPerSecond;
    }

    /**
     * Duration over which the given written bytes per second should be checked against.
     *
     * <p>Non-zero duration must provided in seconds.
     */
    public @SuppressLint({ "MethodNameUnits" }) long getDurationInSeconds() {
        return mDurationInSeconds;
    }

    /**
     * Alert I/O overuse on reaching the written bytes/second over {@link #mDurationInSeconds}.
     *
     * <p>Must provide non-zero bytes.
     */
    public long getWrittenBytesPerSecond() {
        return mWrittenBytesPerSecond;
    }

    @Override
    public String toString() {
        return "IoOveruseAlertThreshold { " +
                "durationInSeconds = " + mDurationInSeconds + ", " +
                "writtenBytesPerSecond = " + mWrittenBytesPerSecond +
        " }";
    }

    @Override
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        dest.writeLong(mDurationInSeconds);
        dest.writeLong(mWrittenBytesPerSecond);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    /* package-private */ IoOveruseAlertThreshold(@NonNull android.os.Parcel in) {
        long durationInSeconds = in.readLong();
        long writtenBytesPerSecond = in.readLong();

        this.mDurationInSeconds = durationInSeconds;
        this.mWrittenBytesPerSecond = writtenBytesPerSecond;
    }

    public static final @NonNull Parcelable.Creator<IoOveruseAlertThreshold> CREATOR
            = new Parcelable.Creator<IoOveruseAlertThreshold>() {
        @Override
        public IoOveruseAlertThreshold[] newArray(int size) {
            return new IoOveruseAlertThreshold[size];
        }

        @Override
        public IoOveruseAlertThreshold createFromParcel(@NonNull android.os.Parcel in) {
            return new IoOveruseAlertThreshold(in);
        }
    };
}
