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

package com.android.tools.metalava.model

/**
 * An [ItemVisitor] that simply traverses an [Item] hierarchy calling [visitItem] for each [Item].
 *
 * The [visitItem] method can affect the traversal through the [TraversalAction] value it returns.
 *
 * It intentionally does not visit [ParameterItem]s.
 */
abstract class TraversingVisitor : ItemVisitor {

    enum class TraversalAction {
        /** Continue normal traversal. */
        CONTINUE,

        /** Skip the children of the current [Item] but continue with its sibling. */
        SKIP_CHILDREN,

        /** Skip the whole traversal. */
        SKIP_TRAVERSAL,
    }

    private var traversalFinished = false

    /** Visit the item returning an action for the [TraversingVisitor] to take. */
    abstract fun visitItem(item: Item): TraversalAction

    final override fun visit(cls: ClassItem) {
        when (visitItem(cls)) {
            TraversalAction.SKIP_TRAVERSAL -> {
                traversalFinished = true
            }
            TraversalAction.SKIP_CHILDREN -> {
                // Do nothing
            }
            TraversalAction.CONTINUE -> {
                for (constructor in cls.constructors()) {
                    constructor.accept(this)
                    if (traversalFinished) return
                }

                for (method in cls.methods()) {
                    method.accept(this)
                    if (traversalFinished) return
                }

                for (property in cls.properties()) {
                    property.accept(this)
                    if (traversalFinished) return
                }

                for (field in cls.fields()) {
                    field.accept(this)
                    if (traversalFinished) return
                }

                for (innerCls in cls.innerClasses()) {
                    innerCls.accept(this)
                    if (traversalFinished) return
                }
            }
        }
    }

    final override fun visit(field: FieldItem) {
        val action = visitItem(field)
        traversalFinished = action == TraversalAction.SKIP_TRAVERSAL
    }

    final override fun visit(method: MethodItem) {
        val action = visitItem(method)
        traversalFinished = action == TraversalAction.SKIP_TRAVERSAL
    }

    final override fun visit(pkg: PackageItem) {
        when (visitItem(pkg)) {
            TraversalAction.SKIP_TRAVERSAL -> {
                traversalFinished = true
            }
            TraversalAction.SKIP_CHILDREN -> {
                // Do nothing
            }
            TraversalAction.CONTINUE -> {
                for (cls in pkg.topLevelClasses()) {
                    cls.accept(this)
                    if (traversalFinished) return
                }
            }
        }
    }

    final override fun visit(packageList: PackageList) {
        for (it in packageList.packages) {
            it.accept(this)
            if (traversalFinished) return
        }
    }

    final override fun visit(parameter: ParameterItem) {
        error("parameters should not be visited")
    }

    final override fun visit(property: PropertyItem) {
        val action = visitItem(property)
        traversalFinished = action == TraversalAction.SKIP_TRAVERSAL
    }
}
