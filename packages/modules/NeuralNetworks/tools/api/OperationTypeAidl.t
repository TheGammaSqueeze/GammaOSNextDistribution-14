%% Template file for generating OperationType.aidl.
%% see README.md.
/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.hardware.neuralnetworks;

%insert Operation_1.0_Comment
@VintfStability
@Backing(type="int")
enum OperationType {
%insert Operation_1.0

%insert Operation_1.1

%insert Operation_1.2

%insert Operation_1.3

%insert Operation_fl6

%insert Operation_fl7
}
