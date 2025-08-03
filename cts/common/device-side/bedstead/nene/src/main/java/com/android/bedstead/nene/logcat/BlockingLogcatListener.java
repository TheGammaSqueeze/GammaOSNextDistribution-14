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

package com.android.bedstead.nene.logcat;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.utils.Poll;

import java.util.function.Predicate;

public final class BlockingLogcatListener implements AutoCloseable {

    private final Predicate<String> mFilter;

    BlockingLogcatListener(Predicate<String> filter) {
        mFilter = filter;
    }

    public void awaitMatch() {
        Poll.forValue("matching lines", () -> TestApis.logcat().dump(mFilter))
                .toMeet(s -> !s.isBlank())
                .await();
    }

    @Override
    public void close() {
        awaitMatch();
    }
}
