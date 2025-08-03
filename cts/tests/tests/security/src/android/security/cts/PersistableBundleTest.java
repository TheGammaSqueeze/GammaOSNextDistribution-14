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

package android.security.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.os.PersistableBundle;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;

@RunWith(AndroidJUnit4.class)
public class PersistableBundleTest extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 247513680)
    @Test
    public void testReadFromStream_invalidType() throws Exception {
        String input = "<bundle><string name=\"key\">value</string>"
                + "<byte-array name=\"invalid\" num=\"2\">ffff</byte-array></bundle>";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes());

        // Reading from the stream with invalid type should not throw an exception
        PersistableBundle restoredBundle = PersistableBundle.readFromStream(inputStream);

        // verify invalid type is ignored
        assertFalse(restoredBundle.containsKey("invalid"));
        // verify valid type exists
        assertEquals("value", restoredBundle.getString("key"));
    }
}
