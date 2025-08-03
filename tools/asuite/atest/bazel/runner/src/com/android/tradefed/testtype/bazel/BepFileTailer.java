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
package com.android.tradefed.testtype.bazel;

import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEvent;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

final class BepFileTailer implements AutoCloseable {
    private static final Duration BEP_PARSE_SLEEP_TIME = Duration.ofMillis(100);

    private final BufferedInputStream mIn;
    private volatile boolean mStop;

    static BepFileTailer create(Path bepFile) throws FileNotFoundException {
        return new BepFileTailer(new BufferedInputStream(new FileInputStream(bepFile.toFile())));
    }

    private BepFileTailer(BufferedInputStream In) {
        mIn = In;
        mStop = false;
    }

    public BuildEvent nextEvent() throws InterruptedException, IOException {
        while (true) {
            boolean stop = mStop;

            // Mark the current position in the input stream.
            mIn.mark(Integer.MAX_VALUE);

            try {
                BuildEvent event = BuildEvent.parseDelimitedFrom(mIn);

                // When event is null and we hit EOF, wait for an event to be written and try again.
                if (event != null) {
                    return event;
                }
                if (stop) {
                    return null;
                }
            } catch (InvalidProtocolBufferException e) {
                if (stop) {
                    throw e;
                }
                // Partial read. Restore the old position in the input stream.
                mIn.reset();
            }
            Thread.sleep(BEP_PARSE_SLEEP_TIME.toMillis());
        }
    }

    @Override
    public void close() throws IOException {
        mIn.close();
    }

    public void stop() {
        mStop = true;
    }
}
