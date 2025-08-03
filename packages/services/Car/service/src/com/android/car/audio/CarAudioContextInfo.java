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

package com.android.car.audio;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.media.AudioAttributes;
import android.util.ArraySet;
import android.util.proto.ProtoOutputStream;

import com.android.car.audio.CarAudioDumpProto.CarAudioAttributesProto;
import com.android.car.audio.CarAudioDumpProto.CarAudioContextInfoProto;
import com.android.car.audio.CarAudioDumpProto.CarAudioContextProto;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.util.Preconditions;

import java.util.Arrays;
import java.util.Objects;

/**
 * Class used to encapsulate the car audio context, which is represented by a
 * list of {@link AudioAttributes}
 */
final class CarAudioContextInfo {

    private final String mName;
    private final @CarAudioContext.AudioContext int mId;

    private final AudioAttributes[] mAudioAttributes;

    CarAudioContextInfo(AudioAttributes[] audioAttributes, String name,
            @CarAudioContext.AudioContext int id) {
        Objects.requireNonNull(audioAttributes,
                "Car audio context's audio attributes can not be null");
        Preconditions.checkArgument(audioAttributes.length != 0,
                "Car audio context's audio attributes can not be empty");
        mAudioAttributes = audioAttributes;
        Objects.requireNonNull(name,
                "Car audio context's name can not be null");
        mName = Preconditions.checkStringNotEmpty(name,
                "Car audio context's name can not be empty");
        mId = Preconditions.checkArgumentNonnegative(id,
                "Car audio context's id can not be negative");
    }

    String getName() {
        return mName;
    }

    @CarAudioContext.AudioContext int getId() {
        return mId;
    }

    AudioAttributes[] getAudioAttributes() {
        return mAudioAttributes;
    }

    @Override
    public String toString() {
        return new StringBuilder().append(mName)
                .append("[").append(mId).append("] attributes: ")
                .append(Arrays.toString(mAudioAttributes)).toString();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer) {
        writer.printf("Context %s id %s\n", mName, mId);
        writer.increaseIndent();
        for (int index = 0; index < mAudioAttributes.length; index++) {
            writer.println(mAudioAttributes[index]);
        }
        writer.decreaseIndent();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dumpProto(ProtoOutputStream proto) {
        long contextInfosToken = proto.start(CarAudioContextProto.CAR_AUDIO_CONTEXT_INFOS);
        proto.write(CarAudioContextInfoProto.NAME, mName);
        proto.write(CarAudioContextInfoProto.ID, mId);
        for (int index = 0; index < mAudioAttributes.length; index++) {
            dumpCarAudioAttributesProto(mAudioAttributes[index],
                    CarAudioContextInfoProto.ATTRIBUTES, proto);
        }
        proto.end(contextInfosToken);
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    static void dumpCarAudioAttributesProto(AudioAttributes attributes, long fieldId,
            ProtoOutputStream proto) {
        long token = proto.start(fieldId);
        proto.write(CarAudioAttributesProto.USAGE, attributes.getUsage());
        proto.write(CarAudioAttributesProto.CONTENT_TYPE, attributes.getContentType());
        proto.end(token);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof CarAudioContextInfo)) {
            return false;
        }

        CarAudioContextInfo info = (CarAudioContextInfo) other;

        return mId == info.mId && mName.equals(info.mName)
                && audioAttributesMatch(info.mAudioAttributes);
    }

    private boolean audioAttributesMatch(AudioAttributes[] audioAttributes) {
        if (mAudioAttributes.length != audioAttributes.length) {
            return false;
        }

        ArraySet<AudioAttributes> attributes =
                new ArraySet<>(mAudioAttributes.length);
        for (int index = 0; index < mAudioAttributes.length; index++) {
            attributes.add(mAudioAttributes[index]);
        }

        for (int index = 0; index < audioAttributes.length; index++) {
            if (!attributes.remove(audioAttributes[index])) {
                return false;
            }
        }

        return attributes.isEmpty();
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mName, Arrays.hashCode(mAudioAttributes));
    }
}
