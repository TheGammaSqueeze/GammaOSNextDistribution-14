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

package com.android.ims.rcs.uce.presence.pidfparser;


import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.telephony.ims.RcsContactPresenceTuple;
import android.telephony.ims.RcsContactUceCapability;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RcsContactUceCapabilityWrapperTest extends ImsTestBase {


    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testMalformedStatus() throws Exception {
        RcsContactUceCapabilityWrapper capabilityWrapper = getRcsContactUceCapabilityWrapper();
        capabilityWrapper.setMalformedContents();

        assertTrue(capabilityWrapper.isMalformed());

        RcsContactPresenceTuple.Builder tupleBuilder = new RcsContactPresenceTuple.Builder(
                "open", "test", "1.0");

        capabilityWrapper.addCapabilityTuple(tupleBuilder.build());
        assertFalse(capabilityWrapper.isMalformed());
    }

    private RcsContactUceCapabilityWrapper getRcsContactUceCapabilityWrapper() {
        final Uri contact = Uri.fromParts("sip", "test", null);
        RcsContactUceCapabilityWrapper wrapper = new RcsContactUceCapabilityWrapper(contact,
                RcsContactUceCapability.SOURCE_TYPE_NETWORK,
                RcsContactUceCapability.REQUEST_RESULT_FOUND);

        return wrapper;
    }
}
