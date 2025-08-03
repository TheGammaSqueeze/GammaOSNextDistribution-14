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

#ifndef BERBERIS_RUNTIME_BERBERIS_H_
#define BERBERIS_RUNTIME_BERBERIS_H_

namespace berberis {

// Berberis explicit lazy initialization.
// Might be called multiple times safely.
// TODO(b/288956745): the requirement to call initialization multiple times
// comes from our unit tests. Unfortunately, many of them are not true unit
// tests, as they use the whole berberis library. As tests might run in any
// order and probably even in parallel, the right place for one-time init is
// right before RUN_ALL_TESTS(), which is hard to get in. The remaining option
// is to make initialization lazy and to call it from every test.
// This is actually a HACK, needed while there is stuff that's initialized
// inside berberis but gets accessed from outside - which means access
// can occur before initialization. We are cleaning this up. At the end,
// initialization will be truly lazy and this function will go away.
void InitBerberis();

// Assume there is a single host thread and no guest threads running.
void PreZygoteForkUnsafe();

}  // namespace berberis

#endif  // BERBERIS_RUNTIME_BERBERIS_H_