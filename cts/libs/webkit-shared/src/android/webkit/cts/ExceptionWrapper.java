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

package android.webkit.cts;

import android.os.RemoteException;

/**
 * Binder only handles a few exceptions. Runtime exceptions are silently ignored and any errors
 * thrown will result in crashing the entire process and meaning no further tests will pass. We deal
 * with this in a bit of a sneaky sneaky way and wrap any throwables in one of the supported
 * exception types (IllegalStateException seems fairly representative of if the host app environment
 * is broken), and then catch that and re-expose it in the SharedSdkWebServer where we strip that
 * exception type out to avoid confusion.
 *
 * <p>The wrap/unwrap methods from this class should be used on either side of IPC calls by the
 * IHostAppInvoker and IWebServer.
 *
 * <p>This allows JUnit to deal with any major broken program flows gracefully instead of moving on
 * or crashing the rest of the tests.
 *
 * <p>It should be noted that binder parcel will take the cause and stringify the stack trace so the
 * type information of these exceptions is lost in the journey. This means that the test code will
 * not be able to react to these exception types. This is already a limitation of communicating
 * through binder.
 */
class ExceptionWrapper {
    public static <T> T wrap(WrappedTypedCall<T> c) {
        try {
            return c.wrap();
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    public static void wrap(WrappedVoidCall r) {
        wrap(() -> {
            r.wrap();
            return null;
        });
    }

    public static <T> T unwrap(UnwrappedTypedCall<T> c) {
        try {
            return c.unwrap();
        } catch (RemoteException e) {
            // We are handling the remote exception separately from the IllegalStateException
            // because this is happening binder proxy side so we would like to preserve the
            // exception information.
            // We still wrap this in a runtime exception so that the WebServer tests don't need to
            // throw inside the Webkit utils run on main sync method as that would mean those
            // functions would all have to return null (it would turn them into callables instead of
            // runnables).
            throw new RuntimeException(e);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static void unwrap(UnwrappedVoidCall c) {
        unwrap(() -> {
            c.unwrap();
            return null;
        });
    }

    interface WrappedTypedCall<T> {
        T wrap() throws Exception;
    }

    interface WrappedVoidCall {
        void wrap() throws Exception;
    }

    interface UnwrappedTypedCall<T> {
        T unwrap() throws RemoteException;
    }

    interface UnwrappedVoidCall {
        void unwrap() throws RemoteException;
    }
}
