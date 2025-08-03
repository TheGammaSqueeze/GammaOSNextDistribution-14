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

#ifndef CHRE_PLATFORM_SLPI_QSH_QSH_PROTO_SHIM_H_
#define CHRE_PLATFORM_SLPI_QSH_QSH_PROTO_SHIM_H_

namespace chre {

/**
 * Opens the QSH proto-shim. This must be invoked prior to the CHRE event loop
 * being started to ensure QSH is initialized.
 */
void openQsh();

/**
 * Closes the QSH proto-shim and allows it to clean up any resources it has
 * allocated. This must be invoked directly after chre::deinit to ensure
 * resources are torn down only after nanoapps have been unloaded.
 */
void closeQsh();

}  // namespace chre

#endif  // CHRE_PLATFORM_SLPI_QSH_QSH_PROTO_SHIM_H_
