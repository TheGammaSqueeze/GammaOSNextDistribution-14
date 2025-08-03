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

/*
 * Because the Trusty rules.mk build system associates .o files with their
 * corresponding sources, this file serves to provide an app-specific source
 * file which we build independently from the original wrappers_c.cpp and
 * wrappers_cpp.cpp files to provide custom defines/includes, namely
 * `USE_CUSTOM_SCUDO_CONFIG` and the custom_scudo_config.h file for each app.
 * This lets us have a separate copy of scudo.a linked together for each app,
 * using the contents of these two .cpp files built with that app's custom
 * scudo config header.
 */

#include "wrappers_c.cpp"
#include "wrappers_cpp.cpp"
