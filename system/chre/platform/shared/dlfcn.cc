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

#include <dlfcn.h>

#include "chre/platform/assert.h"
#include "chre/platform/shared/nanoapp_loader.h"
#include "chre/util/unique_ptr.h"

void *dlopenbuf(void *elfBinary, bool mapIntoTcm) {
  return chre::NanoappLoader::create(elfBinary, mapIntoTcm);
}

void *dlsym(void *handle, const char *symbol) {
  LOGV("Attempting to find %s", symbol);

  void *resolvedSymbol = nullptr;
  if (handle == RTLD_NEXT) {
    resolvedSymbol = chre::NanoappLoader::findExportedSymbol(symbol);
  } else if (handle != nullptr) {
    auto *loader = reinterpret_cast<chre::NanoappLoader *>(handle);
    resolvedSymbol = loader->findSymbolByName(symbol);
  }

  if (resolvedSymbol == nullptr) {
    LOGE("dlsym unable to resolve %s", symbol);
  } else {
    LOGV("Found symbol at %p", resolvedSymbol);
  }

  return resolvedSymbol;
}

int dlclose(void *handle) {
  int rv = -1;

  if (handle != nullptr) {
    chre::NanoappLoader::destroy(static_cast<chre::NanoappLoader *>(handle));
    rv = 0;
  }

  return rv;
}

const char *dlerror() {
  return "Shared library load failed";
}
