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

public class AnnotationData {
    public boolean isSystemApi = false;
    public boolean hasAddedInOrBefore = false;
    public int addedInOrBeforeMajorVersion = 0;
    public int addedInOrBeforeMinorVersion = 0;
    public boolean hasDeprecatedAddedInAnnotation = false;
    public boolean hasAddedInAnnotation = false;
    public String addedInPlatformVersion = "";
    public boolean hasApiRequirementAnnotation = false;
    public String minPlatformVersion = "";
    public String minCarVersion = "";
    public boolean hasRequiresApiAnnotation = false;
    public String requiresApiVersion = "";
}
