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

import java.io.File;
import java.io.IOException;

public class StreamTraceParser extends BaseTraceParser {

    public void CheckTraceFileFormat(File file,
        int expectedVersion, String threadName) throws Exception {
        InitializeParser(file);

        validateTraceHeader(expectedVersion);
        boolean hasEntries = true;
        boolean seenStopTracingMethod = false;
        while (hasEntries) {
            int threadId = GetThreadID();
            if (threadId != 0) {
              String eventString = ProcessEventEntry(threadId);
              if (!ShouldCheckThread(threadId, threadName)) {
                continue;
              }
              // Ignore events after method tracing was stopped. The code that is executed
              // later could be non-deterministic.
              if (!seenStopTracingMethod) {
                UpdateThreadEvents(threadId, eventString);
              }
              if (eventString.contains("Main$VMDebug $noinline$stopMethodTracing")) {
                seenStopTracingMethod = true;
              }
            } else {
              int headerType = GetEntryHeader();
              switch (headerType) {
                case 1:
                  ProcessMethodInfoEntry();
                  break;
                case 2:
                  ProcessThreadInfoEntry();
                  break;
                case 3:
                  // TODO(mythria): Add test to also check format of trace summary.
                  hasEntries = false;
                  break;
                default:
                  System.out.println("Unexpected header in the trace " + headerType);
              }
            }
        }
        closeFile();

        // Printout the events.
        for (String str : threadEventsMap.values()) {
            System.out.println(str);
        }
    }
}
