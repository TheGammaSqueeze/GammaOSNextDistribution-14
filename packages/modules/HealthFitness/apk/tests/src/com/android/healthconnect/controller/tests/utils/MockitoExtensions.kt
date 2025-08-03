/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.tests.utils

import org.mockito.Matchers.eq
import org.mockito.Mockito.`when`
import org.mockito.stubbing.OngoingStubbing
import org.mockito.stubbing.Stubber

fun <T : Any> safeEq(value: T): T = eq(value) ?: value

/**
 * Helper function for stubbing methods without the need to use backticks.
 *
 * @see Mockito.when
 */
fun <T> whenever(methodCall: T): OngoingStubbing<T> = `when`(methodCall)

fun <T> Stubber.whenever(mock: T): T = `when`(mock)
