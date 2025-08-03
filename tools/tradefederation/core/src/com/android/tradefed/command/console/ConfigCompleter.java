/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tradefed.command.console;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.ArrayList;
import java.util.List;

/** Implementation of the {@link Completer} for our TF configurations. */
public class ConfigCompleter implements Completer {
    private List<Candidate> mListCandidates = new ArrayList<>();
    private static final String RUN_COMMAND = "run";

    public ConfigCompleter(List<String> listConfig) {
        for (String config : listConfig) {
            mListCandidates.add(new Candidate(config));
        }
    }

    /** {@inheritDoc} */
    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        // The list of candidates will be sorted and filtered by the LineReader. Thus it is not
        // necessary for the completer to do any matching based on the current buffer.
        if (RUN_COMMAND.contains(line.line())) {
            candidates.add(new Candidate(RUN_COMMAND));
        } else if (line.line().startsWith(RUN_COMMAND)) {
            candidates.addAll(mListCandidates);
        }
    }
}
