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

package com.android.internal.net.ipsec.test.ike.shim;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.net.ipsec.test.ike.exceptions.IkeException;
import android.net.ipsec.test.ike.exceptions.IkeInternalException;

import org.junit.Test;

import java.io.IOException;

public class ShimUtilsRAndSTest {
    private ShimUtilsRAndS mShim = new ShimUtilsRAndS();

    @Test
    public void testGetWrappedIkeException() {
        final IOException ioException = new IOException("test");
        final IkeException ikeException = mShim.getWrappedIkeException(ioException);

        assertTrue(ikeException instanceof IkeInternalException);
        assertSame(ikeException, mShim.getWrappedIkeException(ikeException));
    }

    @Test
    public void testGetRetransmissionFailedException() {
        final Exception exception =
                mShim.getRetransmissionFailedException("testGetRetransmissionFailedException");
        assertTrue(exception instanceof IOException);
    }

    @Test
    public void testGetDnsFailedException() {
        final IOException exception = mShim.getDnsFailedException("testGetDnsFailedException");
        assertTrue(exception instanceof IOException);
    }
}
