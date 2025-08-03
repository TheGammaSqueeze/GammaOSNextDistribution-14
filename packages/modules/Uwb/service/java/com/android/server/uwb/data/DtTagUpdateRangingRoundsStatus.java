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

package com.android.server.uwb.data;

import java.util.Arrays;

public class DtTagUpdateRangingRoundsStatus {
    private final int mStatus;
    private final int mNoOfRangingRounds;
    private final byte[] mRangingRoundIndexes;

    public DtTagUpdateRangingRoundsStatus(int status, int noOfRangingRounds,
            byte[] rangingRoundIndexes) {
        mStatus = status;
        mNoOfRangingRounds = noOfRangingRounds;
        mRangingRoundIndexes = rangingRoundIndexes;
    }

    public int getStatus() {
        return mStatus;
    }

    public int getNoOfRangingRounds() {
        return mNoOfRangingRounds;
    }

    public byte[] getRangingRoundIndexes() {
        return mRangingRoundIndexes;
    }

    @Override
    public String toString() {
        return "DtTagActiveRoundsStatus { "
                + "Status = " + mStatus
                + ", NoOfRangingRounds =" + mNoOfRangingRounds
                + ", RangingRoundIndexes = " + Arrays.toString(mRangingRoundIndexes)
                + '}';
    }
}
