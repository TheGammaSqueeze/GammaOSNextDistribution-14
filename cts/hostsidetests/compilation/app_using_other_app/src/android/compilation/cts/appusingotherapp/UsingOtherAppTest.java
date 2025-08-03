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

package android.compilation.cts.appusingotherapp;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;

/**
 * An instrumentation test that uses another app.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class UsingOtherAppTest {
    private static final String TAG = "UsingOtherAppTest";

    @Test
    public void useOtherApp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Context otherAppContext =
                context.createPackageContext("android.compilation.cts.appusedbyotherapp",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        ClassLoader classLoader = otherAppContext.getClassLoader();
        Class<?> c = classLoader.loadClass("android.compilation.cts.appusedbyotherapp.MyActivity");
        Method m = c.getMethod("publicMethod");
        String ret = (String) m.invoke(null /* obj */);
        assertThat(ret).isEqualTo("foo");
    }
}
