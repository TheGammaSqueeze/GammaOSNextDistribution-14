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

package android.net.ipsec.test.ike;

import static org.junit.Assert.assertEquals;

import android.net.ipsec.test.ike.exceptions.IkeException;
import android.net.ipsec.test.ike.exceptions.IkeProtocolException;
import android.net.ipsec.test.ike.exceptions.InvalidIkeSpiException;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class IkeSessionCallbackTest {
    private OldOnErrorIkeSessionCallback mOldCallback;
    private UpdatedOnErrorIkeSessionCallback mUpdatedCallback;
    private IkeProtocolException mIkeException;

    @Before
    public void setUp() {
        mOldCallback = new OldOnErrorIkeSessionCallback();
        mUpdatedCallback = new UpdatedOnErrorIkeSessionCallback();
        mIkeException = new InvalidIkeSpiException();
    }

    @Test
    public void testOnErrorIkeExceptionNotOverridden() {
        mOldCallback.onError((IkeException) mIkeException);
        assertEquals(Arrays.asList(mIkeException), mOldCallback.mOnErrorIkeProtocolExceptions);
    }

    @Test
    public void testOnErrorIkeExceptionOverridden() {
        mUpdatedCallback.onError((IkeException) mIkeException);
        assertEquals(Arrays.asList(mIkeException), mUpdatedCallback.mOnErrorIkeExceptions);
    }

    private abstract class TestIkeSessionCallbackBase implements IkeSessionCallback {
        @Override
        public void onOpened(IkeSessionConfiguration sessionConfiguration) {}

        @Override
        public void onClosed() {}

        @Override
        public void onClosedExceptionally(IkeException exception) {}

        @Override
        public void onIkeSessionConnectionInfoChanged(IkeSessionConnectionInfo connectionInfo) {}
    }

    private final class OldOnErrorIkeSessionCallback extends TestIkeSessionCallbackBase {
        List<IkeProtocolException> mOnErrorIkeProtocolExceptions = new ArrayList<>();

        @Override
        public void onError(IkeProtocolException exception) {
            mOnErrorIkeProtocolExceptions.add(exception);
        }
    }

    private final class UpdatedOnErrorIkeSessionCallback extends TestIkeSessionCallbackBase {
        List<IkeException> mOnErrorIkeExceptions = new ArrayList<>();

        @Override
        public void onError(IkeProtocolException exception) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onError(IkeException exception) {
            mOnErrorIkeExceptions.add(exception);
        }
    }
}
