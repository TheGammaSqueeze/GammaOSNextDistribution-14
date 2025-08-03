/*
 * Copyright 2023 The Android Open Source Project
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

#pragma once

// Synchronize main handler for tests
//
// NOTE: This mechanism works ONLY when it is known that a single unit
// of execution under test does not spawn or otherwise extend entries
// into the main loop execution queue.
//
// If the execution under test reposts additional execution units to
// the main loop queue then this synchronization mechanism is unreliable
// and alternative methods must be used to properly sync within the
// multithreaded environment.

void sync_main_handler();
