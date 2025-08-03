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

#ifndef DISPLAY_COLOR_LOADER_H
#define DISPLAY_COLOR_LOADER_H

#include <dlfcn.h>
#include <log/log.h>
#include <string>
#include <vector>

template <typename GsInterfaceType, const char *entry>
class DisplayColorLoader {
    static_assert(entry != nullptr);
    public:
      DisplayColorLoader(const DisplayColorLoader &) = delete;
      DisplayColorLoader& operator=(const DisplayColorLoader &) = delete;

      DisplayColorLoader(const std::string &libName) {
          libHandle = dlopen(libName.c_str(), RTLD_LAZY);

          if (libHandle == nullptr) {
              ALOGE("%s: failed to load library %s\n", __func__, libName.c_str());
              getDisplayColor = nullptr;
              return;
          }

          const displaycolor::DisplayColorIntfVer *(*getVersion)();
          getVersion = reinterpret_cast<decltype(getVersion)>(dlsym(libHandle, "GetInterfaceVersion"));
          if (getVersion == nullptr) {
              ALOGE("%s: prebuilt lib is not versioned", __func__);
              return;
          }

          auto intfVer = getVersion();

          if (intfVer != nullptr &&
              displaycolor::kInterfaceVersion.Compatible(*intfVer)) {
              getDisplayColor = reinterpret_cast<decltype(getDisplayColor)>(dlsym(libHandle, entry));

              if (getDisplayColor == nullptr) {
                  ALOGE("%s: failed to get %s\n", __func__, entry);
              } else if (!(displaycolor::kInterfaceVersion == *intfVer)) {
                  ALOGW("%s: different hwc/displaycolor patch level %u.%u.%u vs .%u",
                        __func__,
                        intfVer->major,
                        intfVer->minor,
                        displaycolor::kInterfaceVersion.patch,
                        intfVer->patch);
              }
          } else {
              if (intfVer != nullptr) {
                  ALOGE("%s: prebuilt lib version %u.%u.%u expected %u.%u.%u",
                        __func__,
                        intfVer->major,
                        intfVer->minor,
                        intfVer->patch,
                        displaycolor::kInterfaceVersion.major,
                        displaycolor::kInterfaceVersion.minor,
                        displaycolor::kInterfaceVersion.patch);
              } else {
                  ALOGE("%s: prebult lib getVersion returns null", __func__);
              }
          }
      }

      GsInterfaceType *GetDisplayColor(
              const std::vector<displaycolor::DisplayInfo> &display_info) {
          if (getDisplayColor != nullptr) {
              return getDisplayColor(display_info);
          }

          return nullptr;
      }

      ~DisplayColorLoader() {
          if (libHandle != nullptr) {
              dlclose(libHandle);
          }
      }

    private:
      void *libHandle;
      GsInterfaceType *(*getDisplayColor)(const std::vector<displaycolor::DisplayInfo> &);
};

#endif //DISPLAY_COLOR_LOADER_H
