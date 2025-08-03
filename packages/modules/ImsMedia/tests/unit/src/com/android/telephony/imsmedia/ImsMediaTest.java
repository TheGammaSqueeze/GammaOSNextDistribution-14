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

package com.android.telephony.imsmedia;

import static org.junit.Assert.fail;

import android.testing.TestableLooper;

import org.junit.After;
import org.junit.Before;

import java.util.ArrayList;
import java.util.List;

public class ImsMediaTest {
    protected List<TestableLooper> mTestableLoopers = new ArrayList<>();
    protected TestableLooper mTestableLooper;
    protected Object mTestClass;

    @Before
    public void setUp() {
        mTestableLooper = TestableLooper.get(mTestClass);
        if (mTestableLooper != null) {
            monitorTestableLooper(mTestableLooper);
        }
    }

    @After
    public void tearDown() throws Exception {
        if (!mTestableLoopers.isEmpty()) {
            for (TestableLooper looper : mTestableLoopers) {
                looper.getLooper().quit();
            }
        }
        // Unmonitor TestableLooper for ImsMediaTest class
        if (mTestableLooper != null) {
            unmonitorTestableLooper(mTestableLooper);
        }
        // Destroy all newly created TestableLoopers so they can be reused
        for (TestableLooper looper : mTestableLoopers) {
            looper.destroy();
        }
        TestableLooper.remove(mTestClass);

    }

    private void monitorTestableLooper(TestableLooper looper) {
        if (!mTestableLoopers.contains(looper)) {
            mTestableLoopers.add(looper);
        }
    }

    private void unmonitorTestableLooper(TestableLooper looper) {
        if (mTestableLoopers.contains(looper)) {
            mTestableLoopers.remove(looper);
        }
    }

    private boolean areAllTestableLoopersIdle() {
        for (TestableLooper looper : mTestableLoopers) {
            if (!looper.getLooper().getQueue().isIdle()) return false;
        }
        return true;
    }

    public void processAllMessages() {
        if (mTestableLoopers.isEmpty()) {
            fail("mTestableLoopers is empty. Please make sure to add @RunWithLooper annotation");
        }
        while (!areAllTestableLoopersIdle()) {
            for (TestableLooper looper : mTestableLoopers) looper.processAllMessages();
        }
    }
}
