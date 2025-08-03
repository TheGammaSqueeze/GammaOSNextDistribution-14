/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.shared.usecase

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Base class for UseCase to offload UseCase work to a background dispatcher. */
abstract class BaseUseCase<in Input, Output>(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend operator fun invoke(input: Input): UseCaseResults<Output> =
        withContext(dispatcher) {
            try {
                UseCaseResults.Success(execute(input))
            } catch (exception: Exception) {
                UseCaseResults.Failed(exception)
            }
        }

    protected abstract suspend fun execute(input: Input): Output
}
