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

// A collection of definitions that normally come from standard library
// headers, and are necessary for proper parsing by pyclibrary. We could direct
// pyclibrary to system includes, but since those tend to be layered across
// many files, and pyclibrary doesn't follow #include directives, we simply
// redefine only the necessary things here.

#define UINT8_MAX 255