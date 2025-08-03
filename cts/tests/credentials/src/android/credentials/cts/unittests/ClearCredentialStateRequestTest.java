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

package android.credentials.cts.unittests;

import static android.credentials.cts.unittests.TestUtils.assertEquals;
import static android.credentials.cts.unittests.TestUtils.cloneParcelable;

import static org.testng.Assert.assertThrows;

import android.credentials.ClearCredentialStateRequest;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;


@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class ClearCredentialStateRequestTest {

    @Test
    public void testConstructor_nullData() {
        assertThrows(NullPointerException.class, () ->
                new ClearCredentialStateRequest(null));
    }

    @Test
    public void testConstructor() {
        final Bundle data = new Bundle();
        data.putString("foo", "bar");

        final ClearCredentialStateRequest req =
                new ClearCredentialStateRequest(data);

        assertEquals(req.getData(), data);
    }

    @Test
    public void testWriteToParcel() {
        final Bundle data = new Bundle();
        data.putString("foo", "bar");

        final ClearCredentialStateRequest req1 = new ClearCredentialStateRequest(data);
        final ClearCredentialStateRequest req2 = cloneParcelable(req1);

        assertEquals(req2.getData(), req1.getData());
    }
}
