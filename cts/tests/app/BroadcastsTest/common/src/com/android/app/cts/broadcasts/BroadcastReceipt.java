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
package com.android.app.cts.broadcasts;

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;

public final class BroadcastReceipt implements Parcelable {
    public final long timestampMs;
    public final Intent intent;

    public static BroadcastReceipt create(long timestampMs, Intent intent) {
        return new BroadcastReceipt(timestampMs, intent);
    }

    public BroadcastReceipt(long timestampMs, Intent intent) {
        this.timestampMs = timestampMs;
        this.intent = intent;
    }

    public BroadcastReceipt(Parcel in) {
        this(in.readLong(), in.readParcelable(Intent.class.getClassLoader(), Intent.class));
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(timestampMs);
        dest.writeParcelable(intent, 0);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return formatRealtime(timestampMs) + ":" + intent;
    }

    public static final Creator<BroadcastReceipt> CREATOR = new Creator<>() {
        @Override
        public BroadcastReceipt createFromParcel(Parcel source) {
            return new BroadcastReceipt(source);
        }

        @Override
        public BroadcastReceipt[] newArray(int size) {
            return new BroadcastReceipt[size];
        }
    };

    public static String formatRealtime(long time) {
        return formatTime(time, SystemClock.elapsedRealtime());
    }

    public static String formatTime(long time, long referenceTime) {
        long diff = time - referenceTime;
        if (diff > 0) {
            return time + " (in " + diff + " ms)";
        }
        if (diff < 0) {
            return time + " (" + -diff + " ms ago)";
        }
        return time + " (now)";
    }
}
