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
package android.os.cts;

import android.os.OperationCanceledException;

import com.android.compatibility.common.util.ApiTest;

import junit.framework.TestCase;

@ApiTest(apis = {"android.os.OperationCanceledException#OperationCanceledException"})
public class OperationCanceledExceptionTest extends TestCase {
    public void testOperationCanceledException() {
        OperationCanceledException e = null;
        boolean isThrowed = false;

        try {
            e = new OperationCanceledException("OperationCanceledException");
            throw e;
        } catch (OperationCanceledException ex) {
            assertSame(e, ex);
            isThrowed = true;
        } finally {
            if (!isThrowed) {
                fail("should throw out OperationCanceledException");
            }
        }

        isThrowed = false;

        try {
            e = new OperationCanceledException();
            throw e;
        } catch (OperationCanceledException ex) {
            assertSame(e, ex);
            isThrowed = true;
        } finally {
            if (!isThrowed) {
                fail("should throw out OperationCanceledException");
            }
        }
    }

}
