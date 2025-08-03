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

package android.adservices.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.signals.UpdateSignalsInput;
import android.adservices.signals.UpdateSignalsRequest;
import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

/**
 * Unit tests for {@link UpdateSignalsInput}
 *
 * <p>If this class is un-ignored {@link android.adservices.signals.UpdateSignalsInputTest} should
 * be deleted.
 */
@Ignore
public class UpdateSignalsRequestTest {

    private static final Uri URI = Uri.parse("https://example.com/somecoolsignals");
    private static final Uri OTHER_URI = Uri.parse("https://example.com/lesscoolsignals");

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuild() {
        UpdateSignalsRequest request = new UpdateSignalsRequest.Builder(URI).build();
        assertEquals(URI, request.getUpdateUri());
    }

    @Test
    public void testBuildNullUri_throws() {
        assertThrows(
                NullPointerException.class, () -> new UpdateSignalsRequest.Builder(null).build());
    }

    @Test
    public void testEqualsEqual() {
        UpdateSignalsRequest identical1 = new UpdateSignalsRequest.Builder(URI).build();
        UpdateSignalsRequest identical2 = new UpdateSignalsRequest.Builder(URI).build();
        assertEquals(identical1, identical2);
    }

    @Test
    public void testEqualsNotEqualSameClass() {
        UpdateSignalsRequest different1 = new UpdateSignalsRequest.Builder(URI).build();
        UpdateSignalsRequest different2 = new UpdateSignalsRequest.Builder(OTHER_URI).build();
        assertNotEquals(different1, different2);
    }

    @Test
    public void testEqualsNotEqualDifferentClass() {
        UpdateSignalsRequest input1 = new UpdateSignalsRequest.Builder(URI).build();
        assertNotEquals(input1, new Object());
    }

    @Test
    public void testHash() {
        UpdateSignalsRequest identical1 = new UpdateSignalsRequest.Builder(URI).build();
        UpdateSignalsRequest identical2 = new UpdateSignalsRequest.Builder(URI).build();
        assertEquals(identical1.hashCode(), identical2.hashCode());
    }

    @Test
    public void testToString() {
        UpdateSignalsRequest input = new UpdateSignalsRequest.Builder(URI).build();
        assertEquals("UpdateSignalsRequest{" + "updateUri=" + URI + '}', input.toString());
    }
}
