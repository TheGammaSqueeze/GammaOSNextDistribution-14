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

package android.signature.cts.api;

import android.signature.cts.FailureType;
import android.signature.cts.ResultObserver;

import java.io.PrintWriter;
import java.io.StringWriter;
import junit.framework.Assert;
import junit.framework.TestCase;

/**
 * Keeps track of any reported failures.
 */
class TestResultObserver implements ResultObserver {

    private boolean mDidFail = false;
    private int failures = 0;

    private StringWriter mErrorString = new StringWriter();

    @Override
    public void notifyFailure(FailureType type, String name, String errorMessage,
            Throwable throwable) {
        mDidFail = true;
        failures++;
        if (failures <= 100) {
            mErrorString.append("\n");
            mErrorString.append(type.toString().toLowerCase());
            mErrorString.append(":\t");
            mErrorString.append(name);
            mErrorString.append("\tError: ");
            mErrorString.append(errorMessage);
            if (throwable != null) {
                mErrorString.append("\n");
                throwable.printStackTrace(new PrintWriter(mErrorString));
            }
        } else if (failures == 101) {
            mErrorString.append("\nMore than 100 failures, aborting test.");
            finalizeErrorString();
            Assert.fail(mErrorString.toString());
        }
    }

    private void finalizeErrorString() {
        ClassLoader classLoader = getClass().getClassLoader();
        mErrorString.append("\nClassLoader hierarchy\n");
        while (classLoader != null) {
            mErrorString.append("    ").append(classLoader.toString()).append("\n");
            classLoader = classLoader.getParent();
        }
    }

    public void onTestComplete() {
        if (mDidFail) {
            finalizeErrorString();
            Assert.fail(mErrorString.toString());
        }
    }
}
