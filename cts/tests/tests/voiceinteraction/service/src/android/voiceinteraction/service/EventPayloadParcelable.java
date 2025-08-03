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

package android.voiceinteraction.service;

import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectedResult;

public class EventPayloadParcelable implements Parcelable {
    public final HotwordDetectedResult mHotwordDetectedResult;
    public final ParcelFileDescriptor mAudioStream;

    private EventPayloadParcelable(Parcel in) {
        int flg = in.readInt();
        this.mHotwordDetectedResult =
                (flg & 0x2) == 0 ? null : (HotwordDetectedResult) in.readTypedObject(
                        HotwordDetectedResult.CREATOR);
        this.mAudioStream = (flg & 0x4) == 0 ? null : (ParcelFileDescriptor) in.readTypedObject(
                ParcelFileDescriptor.CREATOR);
    }

    public EventPayloadParcelable(AlwaysOnHotwordDetector.EventPayload eventPayload) {
        if (eventPayload == null) {
            this.mHotwordDetectedResult = null;
            this.mAudioStream = null;
            return;
        }
        this.mHotwordDetectedResult = eventPayload.getHotwordDetectedResult();
        this.mAudioStream = eventPayload.getAudioStream();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "EventPayloadParcelable { " +
                "hotwordDetectedResult = " + mHotwordDetectedResult + ", " +
                "audioStream = " + mAudioStream +
                " }";
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        int flg = 0;
        if (mHotwordDetectedResult != null) flg |= 0x2;
        if (mAudioStream != null) flg |= 0x4;

        out.writeInt(flg);
        if (mHotwordDetectedResult != null) {
            out.writeTypedObject(mHotwordDetectedResult, flags);
        }
        if (mAudioStream != null) {
            out.writeTypedObject(mAudioStream, flags);
        }
    }

    public static final Parcelable.Creator<EventPayloadParcelable> CREATOR =
            new Parcelable.Creator<EventPayloadParcelable>() {

                @Override
                public EventPayloadParcelable createFromParcel(Parcel in) {
                    return new EventPayloadParcelable(in);
                }

                @Override
                public EventPayloadParcelable[] newArray(int size) {
                    return new EventPayloadParcelable[size];
                }
            };
}
