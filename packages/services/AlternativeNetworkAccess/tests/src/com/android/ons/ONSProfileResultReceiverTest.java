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

package com.android.ons;

import android.content.Context;
import android.content.Intent;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class ONSProfileResultReceiverTest extends ONSBaseTest {

    @Mock
    Context mMockContext;

    @Before
    public void setUp() throws Exception {
        super.setUp("ONSTest");
    }

    @Test
    public void testONSResultReceiverWithNoActionString() {
        ONSProfileResultReceiver onsReceiver = new ONSProfileResultReceiver();

        //Empty Intent with all null fields.
        Intent intent = new Intent();

        try {
            onsReceiver.onReceive(mMockContext, intent);
        } catch (Exception e) {
            fail("Exception should not be thrown");
        }

    }

    @Test
    public void testONSResultReceiverWithActionStringNullExtras() {
        ONSProfileResultReceiver onsReceiver = new ONSProfileResultReceiver();

        //Intent with action String but all null extras.
        Intent intent = new Intent();
        intent.setAction("com.android.ons.TEST_ACTION");

        try {
            onsReceiver.onReceive(mContext, intent);
        } catch (Exception e) {
            fail("Exception should not be thrown");
        }

    }
}
