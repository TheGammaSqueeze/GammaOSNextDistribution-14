/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cellbroadcastservice.tests;

import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.cellbroadcastservice.UserData;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class UserDataTest extends CellBroadcastServiceTestBase {
    @Test
    public void testToString() throws Exception {
        // construct two Userdata for comparison.
        UserData userdata = new UserData();
        userdata.msgEncodingSet = false;
        userdata.msgEncoding = UserData.ENCODING_OCTET;
        userdata.msgType = UserData.ENCODING_GSM_DCS_7BIT;
        userdata.payloadStr = "this is payload 1";
        userdata.paddingBits =  1;
        userdata.payload = userdata.payloadStr.getBytes();

        UserData userdataForComp = new UserData();
        userdataForComp.msgEncodingSet = true;
        userdataForComp.msgEncoding = UserData.ENCODING_7BIT_ASCII;
        userdataForComp.msgType = UserData.ENCODING_GSM_DCS_8BIT;
        userdataForComp.payloadStr = "this is payload 2";
        userdataForComp.paddingBits = 2;
        userdataForComp.payload = userdataForComp.payloadStr.getBytes();

        assertNotSame(userdata.toString(), userdataForComp.toString());
    }
}

