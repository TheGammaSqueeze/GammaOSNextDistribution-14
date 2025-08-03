/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.MemberItem
import com.intellij.psi.PsiJvmMember

abstract class PsiMemberItem
internal constructor(
    codebase: PsiBasedCodebase,
    element: PsiJvmMember,
    modifiers: DefaultModifierList,
    documentation: String,
    internal val containingClass: ClassItem,
    internal val name: String,
) :
    PsiItem(
        codebase = codebase,
        modifiers = modifiers,
        documentation = documentation,
        element = element,
    ),
    MemberItem {

    final override fun name() = name

    final override fun containingClass() = containingClass
}
