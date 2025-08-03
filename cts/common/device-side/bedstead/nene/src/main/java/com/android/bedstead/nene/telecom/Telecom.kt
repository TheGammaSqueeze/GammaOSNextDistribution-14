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
package com.android.bedstead.nene.telecom

import com.android.bedstead.nene.annotations.Experimental
import com.android.bedstead.nene.exceptions.AdbException
import com.android.bedstead.nene.packages.Package
import com.android.bedstead.nene.utils.ShellCommand
import com.android.bedstead.nene.utils.ShellCommandUtils

/**
 * Entry point to Nene Telecom.
 */
@Experimental
object Telecom {

    /** Set the default dialer package for all users. */
    fun setDefaultDialerForAllUsers(pkg: Package): DefaultDialerContext {
        ShellCommand.builder("telecom set-default-dialer")
                .addOperand(pkg.packageName())
                .validate { output -> ShellCommandUtils.startsWithSuccess(output) }
                .executeOrThrowNeneException("Error setting default dialer for all users")
        return DefaultDialerContext()
    }
}
