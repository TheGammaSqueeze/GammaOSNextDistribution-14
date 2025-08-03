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

package com.example.constructors;

public class Constructors {

    private int a;
    private String b;

    private Constructors() {

    }

    protected Constructors(int a) {
        this.a = a;
    }

    public Constructors(String b) {
        this.b = b;
    }

    Constructors(int a, String b) {
        this.a = a;
        this.b = b;
    }
}
