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

package android.device.collectors;

import android.device.collectors.annotations.OptionClass;
import android.os.Bundle;
import androidx.annotation.VisibleForTesting;
import com.android.helpers.MemLeaksHelper;

/** A {@link MemLeaksListener} that captures and records the memory leaks in the device. */
@OptionClass(alias = "memleaks-listener")
public class MemLeaksListener extends BaseCollectionListener<Long> {

    private static final String TAG = MemLeaksListener.class.getSimpleName();
    @VisibleForTesting static final String PROCESS_SEPARATOR = ",";
    @VisibleForTesting static final String DIFF_ON = "diff-on";
    @VisibleForTesting static final String PROCESS_NAMES_KEY = "unreachable-mem-process-names";
    @VisibleForTesting
    static final String COLLECT_ALL_PROCESSES = "collect-all-processes-unreachable-mem";

    private MemLeaksHelper mMemLeaksHelper = new MemLeaksHelper();

    public MemLeaksListener() {
        createHelperInstance(mMemLeaksHelper);
    }

    @VisibleForTesting
    public MemLeaksListener(Bundle args, MemLeaksHelper helper) {
        super(args, helper);
    }

    @Override
    public void setupAdditionalArgs() {
        Bundle args = getArgsBundle();

        String diffOnFlagString = args.getString(DIFF_ON, "true");
        boolean diffOnFlag = Boolean.parseBoolean(diffOnFlagString.replace("\n", "").trim());

        String collectAllFlagString = args.getString(COLLECT_ALL_PROCESSES, "true");
        boolean collectAllProcFlag =
                Boolean.parseBoolean(collectAllFlagString.replace("\n", "").trim());

        String procsString = args.getString(PROCESS_NAMES_KEY, "");
        String[] procs = procsString.split(PROCESS_SEPARATOR);

        mMemLeaksHelper.setUp(diffOnFlag, collectAllProcFlag, procs);
    }
}
