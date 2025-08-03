/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.wifi.util;

import com.android.internal.util.IState;
import com.android.internal.util.StateMachine;
import com.android.internal.util.StateMachine.LogRec;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Enough information about a StateMachine to reconstruct the dump() log. This is necessary
 * because a StateMachine's logs are lost upon quitting
 * ({@link StateMachine#quit()} or {@link StateMachine#quitNow()}).
 *
 * Note: most of StateMachine's internal variables (including LogRecs) are stored in the
 * StateMachine's inner class SmHandler. StateMachine clears its reference to SmHandler
 * after quitting, thereby losing all the LogRecs as well.
 */
public class StateMachineObituary {
    private final String mName;
    /**
     * Total records processed, which may be greater than the size of mLogRecs since
     * messages could be discarded.
     */
    private final int mTotalProcessedRecords;
    private final List<String> mLogRecs = new ArrayList<>();
    private final String mLastStateName;

    public StateMachineObituary(StateMachine stateMachine) {
        mName = stateMachine.getName();
        // total number of LogRecs ever
        mTotalProcessedRecords = stateMachine.getLogRecCount();
        // number of records currently readable i.e. not yet discarded
        int currentReadableRecords = stateMachine.getLogRecSize();
        // LogRecs are mutable and StateMachine internally reuses LogRec instances instead of
        // allocating new ones. Thus, convert the LogRecs to Strings.
        for (int i = 0; i < currentReadableRecords; i++) {
            LogRec logRec = stateMachine.getLogRec(i);
            if (logRec != null) { // just in case
                mLogRecs.add(logRec.toString());
            }
        }
        final IState curState = stateMachine.getCurrentState();
        mLastStateName = curState == null ? "<QUIT>" : curState.getName();
    }

    /** Dump the same information as {@link StateMachine#dump} when the StateMachine was active. */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println(mName + ":");
        pw.println(" total records=" + mTotalProcessedRecords);
        int i = 0;
        for (String logRec : mLogRecs) {
            pw.println(" rec[" + i + "]: " + logRec);
            pw.flush();
            i++;
        }
        pw.println("curState=" + mLastStateName);
    }
}
