/*
 * Copyright (C) 2012 The Android Open Source Project
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

import org.jline.reader.LineReader;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An OutputStream that can be used to make {@code System.out.print()} play nice with the user's
 * {@link LineReader} unfinishedLine.
 *
 * <p>In trivial performance tests, this class did not have a measurable performance impact.
 */
public class ConsoleReaderOutputStream extends OutputStream {
    private final LineReader mConsoleReader;
    private String unfinishedLine = "";

    public ConsoleReaderOutputStream(LineReader reader) {
        if (reader == null) {
            throw new NullPointerException();
        }
        mConsoleReader = reader;
    }

    /** Get the LineReader instance that we're using internally */
    public LineReader getConsoleReader() {
        return mConsoleReader;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        // Cache unfinished lines as linereader.printAbove prints lines.
        // TODO(b/197663114): Find a less-hacky alternative.
        String str = unfinishedLine + new String(b, off, len);
        int indexOfLastNewLine = str.lastIndexOf("\n");
        if (indexOfLastNewLine == -1) {
            unfinishedLine = new String(str);
        } else {
            unfinishedLine = new String(str.substring(indexOfLastNewLine + 1));
            mConsoleReader.printAbove(str.substring(0, indexOfLastNewLine));
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void write(int b) throws IOException {
        char[] str = new char[] {(char) (b & 0xff)};
        mConsoleReader.printAbove(new String(str));
    }
}
