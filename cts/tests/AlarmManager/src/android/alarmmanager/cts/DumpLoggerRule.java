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

package android.alarmmanager.cts;

import android.util.Log;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class DumpLoggerRule extends TestWatcher {

    private final String mTag;

    DumpLoggerRule(String tag) {
        mTag = tag;
    }

    @Override
    protected void failed(Throwable e, Description description) {
        Log.i(mTag, "Debugging info for failed test: " + description.getMethodName());
        Log.i(mTag, SystemUtil.runShellCommand("dumpsys alarm"));
    }
}
