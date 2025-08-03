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

import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.TerminalBuilder;

import java.util.ArrayList;
import java.util.List;

/** Unit tests for {@link ConfigCompleter} */
@RunWith(JUnit4.class)
public class ConfigCompleterTest {
    private ConfigCompleter mConfigCompleter;
    private List<Candidate> mCandidates;
    private LineReader reader = createLineReader();
    private Parser parser = new DefaultParser();

    @Before
    public void setUp() throws Exception {

        List<String> fakeConfigs = new ArrayList<String>();
        fakeConfigs.add("util/timewaster");
        fakeConfigs.add("util/wifi");
        fakeConfigs.add("util/wipe");
        mConfigCompleter = new ConfigCompleter(fakeConfigs);
        mCandidates = new ArrayList<>();
    }

    /** Test that it returns "run" when the command starts with part of "run". */
    @Test
    public void testCommandCompletion() {
        ParsedLine parsedLine = parser.parse("r", 1);
        mConfigCompleter.complete(reader, parsedLine, mCandidates);
        assertEquals(1, mCandidates.size());
        assertEquals("run", mCandidates.get(0).value());
    }

    /** Test that it returns all the candidates config completion. */
    @Test
    public void testConfigCompletion() {
        ParsedLine parsedLine = parser.parse("run util/wi", 11);
        mConfigCompleter.complete(reader, parsedLine, mCandidates);
        assertEquals(3, mCandidates.size());
    }

    private LineReader createLineReader() {
        // Suppress jline warning on creating a dumb terminal (to strerr).
        System.setProperty(TerminalBuilder.PROP_DUMB, "true");
        return LineReaderBuilder.builder().build();
    }
}
