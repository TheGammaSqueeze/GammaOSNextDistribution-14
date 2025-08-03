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

package art;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Arrays;

public class Test2243 {
    static Method default_method;
    static class DefaultImpl implements InterfaceWithDefaultMethods {}

    public static void testDefaultMethod(InterfaceWithDefaultMethods i) {
        enableSingleStep(Thread.currentThread());
        i.doSomething();
    }

    public static void run() throws Exception {
        setSingleStepCallback();
        setSingleStepUntil(InterfaceWithDefaultMethods.class.getDeclaredMethod("doSomething"));
        testDefaultMethod(new DefaultImpl());
    }

    public static native void setSingleStepCallback();
    public static native void setSingleStepUntil(Method m);
    public static native void enableSingleStep(Thread thr);
}
