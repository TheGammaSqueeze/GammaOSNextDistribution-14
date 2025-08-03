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

package libcore.java.lang.stackwalker;

import java.lang.StackWalker.StackFrame;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class Test1 {

    public static List<StackFrame> test() {
        List<StackFrame> result = invokeWalker();
        return result;
    }

    private static List<StackFrame> invokeWalker() {
        StackWalker walker = StackWalker.getInstance();
        return walker.walk(s -> s.collect(toList()));
    }

}
