/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef BERBERIS_KERNEL_API_UNISTD_EMULATION_H_
#define BERBERIS_KERNEL_API_UNISTD_EMULATION_H_

#include <unistd.h>

namespace berberis {

// Fake /proc/self/exe link for programs which use it to get files by path relative to their main
// executable.  Everything else is handled as in regular readlink(2)/readlinkat(2). See: b/34729927
ssize_t ReadLinkForGuest(const char* path, char* buf, size_t buf_size);
ssize_t ReadLinkAtForGuest(int dirfd, const char* path, char* buf, size_t buf_size);

}  // namespace berberis

#endif  // BERBERIS_KERNEL_API_UNISTD_EMULATION_H_
