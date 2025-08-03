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

package com.example.classes;

import com.example.classes.UniversalAnnotation;

public class MethodsAccessModifiers {
    public void public_void_arg0() {}

    private int private_int_arg0() {
        return 0;
    }

    String packagePrivate_String_arg2_int_String(int a, String b) {
        return a + b;
    }

    protected void protected_void_arg0() {}
}

