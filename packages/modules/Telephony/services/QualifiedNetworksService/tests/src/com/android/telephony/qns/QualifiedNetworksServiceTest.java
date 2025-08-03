/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.telephony.qns;

import static org.junit.Assert.assertNotEquals;

import android.util.Log;

import androidx.test.filters.LargeTest;
import androidx.test.filters.MediumTest;
import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class QualifiedNetworksServiceTest {

    private static final String TAG = QualifiedNetworksServiceTest.class.getSimpleName();

    @BeforeClass
    public static void beforeClass() {
        Log.d(TAG, "beforeClass()");
    }

    @AfterClass
    public static void afterClass() {
        Log.d(TAG, "afterClass()");
    }

    @Before
    public void before() {
        Log.d(TAG, "before()");
    }

    @After
    public void after() {
        Log.d(TAG, "after()");
    }

    @Test
    @SmallTest
    public void testMyTest1() {
        Log.d(TAG, "QualifiedNetworksServiceTest1()");
        assertNotEquals("Hello", "world");
    }

    @Test
    @MediumTest
    public void testMyTest2() {
        Log.d(TAG, "QualifiedNetworksServiceTest2()");
        assertNotEquals("Hallo", "Welt");
    }

    @Test
    @LargeTest
    public void testMyTest3() {
        Log.d(TAG, "QualifiedNetworksServiceTest3()");
        assertNotEquals("Hallo", "Welt");
    }
}
