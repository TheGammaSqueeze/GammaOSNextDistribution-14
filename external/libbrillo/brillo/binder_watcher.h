/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef LIBBRILLO_BRILLO_BINDER_WATCHER_H_
#define LIBBRILLO_BRILLO_BINDER_WATCHER_H_

#include <memory>

#include <base/files/file_descriptor_watcher_posix.h>
#include <base/macros.h>

namespace brillo {

// Bridge between libbinder and brillo::MessageLoop. Construct at startup to
// make the message loop watch for binder events and pass them to libbinder.
class BinderWatcher final {
 public:
  BinderWatcher();
  ~BinderWatcher();

  // Initializes the object, returning true on success.
  bool Init();

 private:
  std::unique_ptr<base::FileDescriptorWatcher::Controller> watcher_;

  DISALLOW_COPY_AND_ASSIGN(BinderWatcher);
};

}  // namespace brillo

#endif  // LIBBRILLO_BRILLO_BINDER_WATCHER_H_
