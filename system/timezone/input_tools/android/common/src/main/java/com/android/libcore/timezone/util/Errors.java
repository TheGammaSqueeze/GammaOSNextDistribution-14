/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.libcore.timezone.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Stores context, errors and error severity for logging and flow control. This class distinguishes
 * between warnings (just info), errors (may not be immediately fatal) and fatal (immediately
 * fatal).
 */
public final class Errors {

    private final static int LEVEL_WARNING = 1;
    private final static int LEVEL_ERROR = 2;
    private final static int LEVEL_FATAL = 3;

    private int level = 0;

    private final LinkedList<String> scopes = new LinkedList<>();
    private final List<String> messages = new ArrayList<>();

    public Errors() {
    }

    public void pushScope(String name) {
        scopes.add(name);
    }

    public String popScope() {
        return scopes.removeLast();
    }

    /** Adds a fatal error, and immediately throws {@link HaltExecutionException}. */
    public HaltExecutionException addFatalAndHalt(String msg) throws HaltExecutionException {
        addInternal(msg, null, LEVEL_FATAL);
        throw new HaltExecutionException("Fatal error");
    }

    /** Adds a fatal error, and immediately throws {@link HaltExecutionException}. */
    public HaltExecutionException addFatalAndHalt(String msg, Throwable t)
            throws HaltExecutionException {
        addInternal(msg, t, LEVEL_FATAL);
        throw new HaltExecutionException("Fatal error");
    }

    public void addError(String msg) {
        addInternal(msg, null, LEVEL_ERROR);
    }

    public void addError(String msg, Throwable t) {
        addInternal(msg, t, LEVEL_ERROR);
    }

    public void addWarning(String msg) {
        addInternal(msg, null, LEVEL_WARNING);
    }

    public String asString() {
        StringBuilder sb = new StringBuilder();
        for (String message : messages) {
            sb.append(message);
            sb.append("\n");
        }
        return sb.toString();
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    /** True if there are error or fatal messages. */
    public boolean hasError() {
        return level >= LEVEL_ERROR;
    }

    /** True if there are fatal messages. */
    public boolean hasFatal() {
        return level >= LEVEL_FATAL;
    }

    private void addInternal(String msg, Throwable t, int level) {
        this.level = Math.max(this.level, level);
        addMessage(msg);
        if (t != null) {
            try (StringWriter out = new StringWriter();
                    PrintWriter printWriter = new PrintWriter(out)) {
                t.printStackTrace(printWriter);
                addMessage(out.toString());
            } catch (IOException e) {
                // Impossible - this is actually a compiler bug. Nothing throws IOException above.
                throw new AssertionError("Impossible exception thrown", e);
            }
        }
    }

    private void addMessage(String msg) {
        messages.add(scopes.toString() + ": " + msg);
    }

    /** Throws a {@link HaltExecutionException} if there are any error or fatal messages. */
    public void throwIfError(String why) throws HaltExecutionException {
        if (hasError()) {
            throw new HaltExecutionException(why);
        }
    }

    /** Thrown to halt execution. */
    public static class HaltExecutionException extends Exception {
        HaltExecutionException(String why) {
            super(why);
        }
    }
}
