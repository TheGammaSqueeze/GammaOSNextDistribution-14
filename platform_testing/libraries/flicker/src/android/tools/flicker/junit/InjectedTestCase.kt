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

package android.tools.flicker.junit

import java.lang.reflect.Method
import org.junit.runner.Description
import org.junit.runners.model.FrameworkMethod

abstract class InjectedTestCase(
    method: Method,
    private val _name: String,
    val injectedBy: IFlickerJUnitDecorator
) : FrameworkMethod(method) {
    override fun invokeExplosively(target: Any?, vararg params: Any?): Any {
        error("Shouldn't have reached here")
    }

    override fun getName(): String = _name

    abstract fun execute(description: Description)

    override fun toString() = _name
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InjectedTestCase) return false
        if (!super.equals(other)) return false

        if (_name != other._name) return false

        return true
    }

    override fun hashCode() = _name.hashCode()
}
