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

package com.android.testutils

/**
 * Indicates that the test covers functionality that was rolled out in a connectivity module update.
 *
 * Annotated MTS tests will typically only be run in Connectivity/Tethering module MTS, and not when
 * only other modules (such as NetworkStack) have been updated.
 * Annotated CTS tests will always be run, as the Connectivity module should be at least newer than
 * the CTS suite.
 */
annotation class ConnectivityModuleTest
