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

package com.example.fields;

import java.util.List;

public class Arrays<T> {

    public int[] arr_int_1;

    // ClassDoc
    public String[][] arr_String_2;

    // TypeVariable
    public T[][][] arr_T_3;

    // Parameterized type
    public List<String>[][][][] arr_ListOfString_4;

    // Annotation type
    public Override[][][][][] arr_Override_5;
}
