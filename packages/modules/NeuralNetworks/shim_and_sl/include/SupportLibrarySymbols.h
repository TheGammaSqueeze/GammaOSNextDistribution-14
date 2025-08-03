/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <stddef.h>
#include "NeuralNetworksSupportLibraryImpl.h"

// If you are linking against SL driver implementation through DT_NEEDED,
// you can use this declaration to access its implementation instead
// of doing dlsym.

__BEGIN_DECLS

/**
 * Get the NNAPI SL Driver NnApiSLDriverImpl with all
 * driver functions.
 */
NnApiSLDriverImpl* ANeuralNetworks_getSLDriverImpl();
__END_DECLS
