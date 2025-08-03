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

package android.adservices.measurement;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.util.Collections;

/** Unit test for {@link android.adservices.measurement.DeletionRequest} */
@SmallTest
public class DeletionRequestTest {
    private static final Uri ORIGIN_URI = Uri.parse("https://a.foo.com");
    private static final Uri DOMAIN_URI = Uri.parse("https://foo.com");
    private static final Instant START = Instant.ofEpochSecond(0);
    private static final Instant END = Instant.now();

    @Test
    public void testNonNullParams() {
        DeletionRequest request =
                new DeletionRequest.Builder()
                        .setDeletionMode(DeletionRequest.DELETION_MODE_EXCLUDE_INTERNAL_DATA)
                        .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_PRESERVE)
                        .setOriginUris(Collections.singletonList(ORIGIN_URI))
                        .setDomainUris(Collections.singletonList(DOMAIN_URI))
                        .setStart(START)
                        .setEnd(END)
                        .build();

        Assert.assertEquals(START, request.getStart());
        Assert.assertEquals(END, request.getEnd());
        Assert.assertEquals(1, request.getOriginUris().size());
        Assert.assertEquals(ORIGIN_URI, request.getOriginUris().get(0));
        Assert.assertEquals(1, request.getDomainUris().size());
        Assert.assertEquals(DOMAIN_URI, request.getDomainUris().get(0));
        Assert.assertEquals(
                DeletionRequest.DELETION_MODE_EXCLUDE_INTERNAL_DATA, request.getDeletionMode());
        Assert.assertEquals(DeletionRequest.MATCH_BEHAVIOR_PRESERVE, request.getMatchBehavior());
    }

    @Test
    public void testNullParams() {
        DeletionRequest request =
                new DeletionRequest.Builder()
                        .setDomainUris(null)
                        .setOriginUris(null)
                        .setStart(START)
                        .setEnd(END)
                        .build();
        Assert.assertTrue(request.getOriginUris().isEmpty());
        Assert.assertTrue(request.getDomainUris().isEmpty());
        Assert.assertEquals(DeletionRequest.DELETION_MODE_ALL, request.getDeletionMode());
        Assert.assertEquals(DeletionRequest.MATCH_BEHAVIOR_DELETE, request.getMatchBehavior());
    }

    @Test
    public void testDefaultParams() {
        DeletionRequest request = new DeletionRequest.Builder().build();
        Assert.assertEquals(Instant.MIN, request.getStart());
        Assert.assertEquals(Instant.MAX, request.getEnd());
        Assert.assertTrue(request.getOriginUris().isEmpty());
        Assert.assertTrue(request.getDomainUris().isEmpty());
        Assert.assertEquals(DeletionRequest.DELETION_MODE_ALL, request.getDeletionMode());
        Assert.assertEquals(DeletionRequest.MATCH_BEHAVIOR_DELETE, request.getMatchBehavior());
    }

    @Test
    public void testNullStartThrowsException() {
        Assert.assertThrows(
                NullPointerException.class,
                () -> new DeletionRequest.Builder().setStart(null).setEnd(END).build());
    }

    @Test
    public void testNullEndThrowsException() {
        Assert.assertThrows(
                NullPointerException.class,
                () -> new DeletionRequest.Builder().setStart(START).setEnd(null).build());
    }

    @Test
    public void testMinAndMaxInstants() {
        DeletionRequest request =
                new DeletionRequest.Builder().setStart(Instant.MIN).setEnd(Instant.MAX).build();
        Assert.assertEquals(Instant.MIN, request.getStart());
        Assert.assertEquals(Instant.MAX, request.getEnd());
    }
}
