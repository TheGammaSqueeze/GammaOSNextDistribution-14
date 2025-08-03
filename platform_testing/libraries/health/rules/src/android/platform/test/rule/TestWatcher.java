/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.platform.test.rule;

import android.content.Context;
import android.os.Bundle;
import android.os.Trace;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import java.io.IOException;
import java.lang.Class;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Similar to {@link org.junit.rules.TestWatcher}, but does not perform operations quietly, i.e. by
 * delaying failures and running the underlying base {@link Statement} under all circumstances. It
 * also has additional common support for platform testing.
 */
public class TestWatcher implements TestRule {
    private static final String LOG_TAG = TestWatcher.class.getSimpleName();

    private UiDevice mDevice;

    private Class clz = this.getClass();

    public Statement apply(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    Trace.beginSection(clz.getSimpleName() + ":starting");
                    starting(description);
                    Trace.endSection();
                    base.evaluate();
                    Trace.beginSection(clz.getSimpleName() + ":succeeded");
                    succeeded(description);
                    Trace.endSection();
                } catch (AssumptionViolatedException e) {
                    Trace.beginSection(clz.getSimpleName() + ":skipped");
                    skipped(e, description);
                    Trace.endSection();
                    throw e;
                } catch (Throwable e) {
                    Trace.beginSection(clz.getSimpleName() + ":failed");
                    failed(e, description);
                    Trace.endSection();
                    throw e;
                } finally {
                    Trace.beginSection(clz.getSimpleName() + ":finished");
                    finished(description);
                    Trace.endSection();
                }
            }
        };
    }

    /** Invoked when a test is about to start. */
    protected void starting(Description description) {}

    /** Invoked when a test succeeds. */
    protected void succeeded(Description description) {}

    /** Invoked when a test is skipped due to a failed assumption. */
    protected void skipped(AssumptionViolatedException e, Description description) {}

    /** Invoked when a test fails. */
    protected void failed(Throwable e, Description description) {}

    /** Invoked when a test method finishes (whether passing or failing). */
    protected void finished(Description description) {}

    /**
     * Returns the active {@link UiDevice} to interact with.
     *
     * <p>Override this for unit testing device calls.
     */
    protected UiDevice getUiDevice() {
        if (mDevice == null) {
            mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        }
        return mDevice;
    }

    /**
     * Runs a shell command, {@code cmd}, and returns the output.
     *
     * <p>Override this for unit testing shell commands.
     */
    protected String executeShellCommand(String cmd) {
        try {
            Log.v(LOG_TAG, String.format("Executing command from %s: %s", this.getClass(), cmd));
            return getUiDevice().executeShellCommand(cmd);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the {@link Bundle} containing registered arguments.
     *
     * <p>Override this for unit testing device calls.
     */
    protected Bundle getArguments() {
        return InstrumentationRegistry.getArguments();
    }

    /** Returns the {@link Context} for this application. */
    protected Context getContext() {
        return InstrumentationRegistry.getContext();
    }
}
