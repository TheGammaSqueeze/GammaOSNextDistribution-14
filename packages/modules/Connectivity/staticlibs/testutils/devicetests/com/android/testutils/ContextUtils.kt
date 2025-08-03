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

@file:JvmName("ContextUtils")

package com.android.testutils

import android.content.Context
import android.os.UserHandle
import org.mockito.AdditionalAnswers.delegatesTo
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import java.util.function.BiConsumer

// Helper function so that Java doesn't have to pass a method that returns Unit
fun mockContextAsUser(context: Context, functor: BiConsumer<Context, UserHandle>? = null) =
    mockContextAsUser(context) { c, h -> functor?.accept(c, h) }

/**
 * Return a context with assigned user and delegate to original context.
 *
 * @param context the mock context to set up createContextAsUser on. After this function
 *                is called, client code can call createContextAsUser and expect a context that
 *                will return the correct user and userId.
 *
 * @param functor additional code to run on the created context-as-user instances, for example to
 *                set up further mocks on these contexts.
 */
fun mockContextAsUser(context: Context, functor: ((Context, UserHandle) -> Unit)? = null) {
    doAnswer { invocation ->
        val asUserContext = mock(Context::class.java, delegatesTo<Context>(context))
        val user = invocation.arguments[0] as UserHandle
        val userId = user.identifier
        doReturn(user).`when`(asUserContext).user
        doReturn(userId).`when`(asUserContext).userId
        functor?.let { it(asUserContext, user) }
        asUserContext
    }.`when`(context).createContextAsUser(any(UserHandle::class.java), anyInt() /* flags */)
}

/**
 * Helper function to mock the desired system service.
 *
 * @param context the mock context to set up the getSystemService and getSystemServiceName.
 * @param clazz the system service class that intents to mock.
 * @param service the system service name that intents to mock.
 */
fun <T> mockService(context: Context, clazz: Class<T>, name: String, service: T) {
    doReturn(service).`when`(context).getSystemService(name)
    doReturn(name).`when`(context).getSystemServiceName(clazz)
}