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

package android.tools.traces.component

/**
 * Create a new component identifier.
 *
 * This is a version of Android's ComponentName class for flicker. This is necessary because flicker
 * codebase it also compiled into KotlinJS for use into Winscope
 *
 * @param packageName The name of the package that the component exists in. Can not be null.
 * @param className The name of the class inside <var>pkg</var> that implements the component.
 */
data class ComponentName(override val packageName: String, override val className: String) :
    IComponentName {
    /**
     * Obtains the activity name from the component name.
     *
     * See [ComponentName.toWindowName] for additional information
     */
    override fun toActivityName(): String {
        return when {
            packageName.isNotEmpty() && className.isNotEmpty() -> {
                val sb = StringBuilder(packageName.length + className.length)
                appendShortString(sb, packageName, className)
                return sb.toString()
            }
            packageName.isNotEmpty() -> packageName
            className.isNotEmpty() -> className
            else -> error("Component name should have an activity of class name")
        }
    }

    /**
     * Obtains the window name from the component name.
     *
     * [ComponentName] builds the string representation as PKG/CLASS, however this doesn't work for
     * system components such as IME, NavBar and StatusBar, Toast.
     *
     * If the component doesn't have a package name, assume it's a system component and return only
     * the class name
     */
    override fun toWindowName(): String {
        return when {
            packageName.isNotEmpty() && className.isNotEmpty() -> "$packageName/$className"
            packageName.isNotEmpty() -> packageName
            className.isNotEmpty() -> className
            else -> error("Component name should have an activity of class name")
        }
    }

    internal fun toShortWindowName(): String {
        return when {
            packageName.isNotEmpty() && className.isNotEmpty() ->
                "$packageName/${className.removePrefix(packageName)}"
            packageName.isNotEmpty() -> packageName
            className.isNotEmpty() -> className
            else -> error("Component name should have an activity of class name")
        }
    }

    /**
     * Obtains the layer name from the component name.
     *
     * See [toWindowName] for additional information
     */
    override fun toLayerName(): String {
        var result = this.toWindowName()
        if (result.contains("/") && !result.contains("#")) {
            result = "$result#"
        }

        return result
    }

    private fun appendShortString(sb: StringBuilder, packageName: String, className: String) {
        sb.append(packageName).append('/')
        appendShortClassName(sb, packageName, className)
    }

    private fun appendShortClassName(sb: StringBuilder, packageName: String, className: String) {
        if (className.startsWith(packageName)) {
            val packageNameLength = packageName.length
            val classNameLength = className.length
            if (classNameLength > packageNameLength && className[packageNameLength] == '.') {
                sb.append(className, packageNameLength, classNameLength)
                return
            }
        }
        sb.append(className)
    }

    override fun toString(): String = toShortWindowName()

    companion object {
        fun fromLayerName(layerName: String): IComponentName {
            var name = layerName
            var packageName = ""
            var className = ""
            if (name.contains("/")) {
                if (name.contains("#")) {
                    name = name.removeSuffix("#")
                }
                val splitString = name.split('/')
                packageName = splitString[0]
                className = splitString[1]
            } else {
                className = name
            }
            return ComponentName(packageName, className)
        }
    }
}
