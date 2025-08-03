/*
 * Copyright (C) 2021 The Android Open Source Project
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
 * limitations under the License
 */
package android.multiuser.cts;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import android.app.Instrumentation;
import android.content.Context;
import android.os.UserHandle;
import android.util.Log;

import com.android.bedstead.nene.TestApis;

import java.io.IOException;

final class TestingUtils {
    private static final String TAG = TestingUtils.class.getSimpleName();

    static final Context sContext = TestApis.context().instrumentedContext();

    public static boolean getBooleanProperty(Instrumentation instrumentation, String property)
            throws IOException {
        String value = trim(runShellCommand(instrumentation, "getprop " + property));
        return "y".equals(value) || "yes".equals(value) || "1".equals(value) || "true".equals(value)
                || "on".equals(value);
    }

    static Context getContextForOtherUser() {
        // TODO(b/240207590): TestApis.context().instrumentedContextForUser(TestApis.users()
        // .nonExisting() doesn't work, it throws:
        // IllegalStateException: Own package not found for user 1: package=android.multiuser.cts
        // There might be some bug (or WAI :-) on ContextImpl that makes it behave different for
        // negative user ids. Anyways, for the purpose of this test, this workaround is fine (i.e.
        // the context user id is passed to the binder call and the service checks if it matches the
        // caller or the caller has the proper permission when it doesn't.
        return getContextForUser(-42);
    }

    static Context getContextForUser(int userId) {
        Log.d(TAG, "Getting context for user " + userId);
        Context context = sContext.createContextAsUser(UserHandle.of(userId), /* flags= */ 0);
        Log.d(TAG, "Got it: " + context);
        return context;
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private TestingUtils() {
        throw new UnsupportedOperationException("contains only static methods");
    }
}
