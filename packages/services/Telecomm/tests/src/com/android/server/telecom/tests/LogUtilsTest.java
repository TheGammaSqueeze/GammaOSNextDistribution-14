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

package com.android.server.telecom.tests;

import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;

import com.android.server.telecom.LogUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LogUtilsTest extends TelecomTestCase {


    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Tests LogUtils#initLogging(Context) listeners cannot be initialized more than once by calling
     * the init function multiple times.  If the listeners are ever re-initialized, log spewing
     * will occur.
     *
     * Note, LogUtils will already be initialized at the start of the testing framework,
     * so you cannot assume it is 0 at the start of this testing class.
     */
    @SmallTest
    @Test
    public void testLogUtilsIsNotReInitialized() {

        // assert the listeners of LogUtils are never re-initialized
        assertTrue(LogUtils.getInitializedCounter() <= 1);
        // call initLogging an arbitrary amount of times...
        LogUtils.initLogging(mContext);
        LogUtils.initLogging(mContext);
        LogUtils.initLogging(mContext);
        // assert the listeners of LogUtils are never re-initialized
        assertTrue(LogUtils.getInitializedCounter() <= 1);
    }
}
