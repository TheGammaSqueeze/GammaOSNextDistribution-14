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

package com.android.car.tool.data;

import java.util.HashMap;
import java.util.Map;

public class ClassData {
    public String fullyQualifiedClassName;
    public String onlyClassName;
    public String useableClassName;
    public boolean isInterface; // by default it is false, means by default it is class data.
    public boolean isClassHidden;
    public Map<String, FieldData> fields = new HashMap<>();
    public Map<String, MethodData> methods = new HashMap<>();
    public Map<String, ConstructorData> constructors  = new HashMap<>();
    public PackageData packageData;
    public AnnotationData annotationData;
}
