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

package androidx.core.uwb.backend.impl.internal;

import static androidx.core.uwb.backend.impl.internal.Utils.SUPPORTED_BPRF_PREAMBLE_INDEX;
import static androidx.core.uwb.backend.impl.internal.Utils.SUPPORTED_HPRF_PREAMBLE_INDEX;
import static androidx.core.uwb.backend.impl.internal.Utils.SUPPORTED_CHANNELS;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.primitives.Ints;
import com.google.uwb.support.fira.FiraParams;

import java.util.Arrays;
import java.util.Objects;

/** Complex channel used by UWB ranging. */
public class UwbComplexChannel {

    @FiraParams.UwbChannel private final int mChannel;
    @FiraParams.UwbPreambleCodeIndex private final int mPreambleIndex;

    public UwbComplexChannel(
            @FiraParams.UwbChannel int channel,
            @FiraParams.UwbPreambleCodeIndex int preambleIndex) {
        checkArgument(SUPPORTED_CHANNELS.contains(channel), "Invalid channel number.");
        checkArgument(
                SUPPORTED_BPRF_PREAMBLE_INDEX.contains(preambleIndex)
                    || SUPPORTED_HPRF_PREAMBLE_INDEX.contains(preambleIndex),
                "Invalid preamble index.");
        mChannel = channel;
        mPreambleIndex = preambleIndex;
    }

    @FiraParams.UwbChannel
    public int getChannel() {
        return mChannel;
    }

    @FiraParams.UwbPreambleCodeIndex
    public int getPreambleIndex() {
        return mPreambleIndex;
    }

    /**
     * Pack channel/Preamble Index to a 8-bit integer.
     *
     * @return packed 5-bit integer. [4:6] is the channel index [1:3] is the index of the preamble
     *     index, [0] indicates BPRF (0) or HPRF (1).
     */
    public int encode() {
        int indexOfPreambleInArray = 0;
        int encodedPrfType = 0;
        if (mPreambleIndex <= 12) {
            // BPRF.
            indexOfPreambleInArray =
                Arrays.binarySearch(Ints.toArray(SUPPORTED_BPRF_PREAMBLE_INDEX), mPreambleIndex);
        } else {
            // HPRF.
            indexOfPreambleInArray =
                Arrays.binarySearch(Ints.toArray(SUPPORTED_HPRF_PREAMBLE_INDEX), mPreambleIndex);
            encodedPrfType = 1;
        }

        return (Arrays.binarySearch(Ints.toArray(SUPPORTED_CHANNELS), mChannel << 4)
            | indexOfPreambleInArray << 1
            | encodedPrfType);
    }

    @Override
    public String toString() {
        return "UwbComplexChannel{"
                + "mChannel="
                + mChannel
                + ", mPreambleIndex="
                + mPreambleIndex
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UwbComplexChannel)) return false;
        UwbComplexChannel that = (UwbComplexChannel) o;
        return getChannel() == that.getChannel() && getPreambleIndex() == that.getPreambleIndex();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getChannel(), getPreambleIndex());
    }
}
