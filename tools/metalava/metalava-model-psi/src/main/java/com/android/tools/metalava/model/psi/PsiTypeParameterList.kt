/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.metalava.model.DefaultTypeParameterList
import com.android.tools.metalava.model.TypeParameterList
import com.intellij.psi.PsiTypeParameterListOwner

internal object PsiTypeParameterList {

    fun create(
        codebase: PsiBasedCodebase,
        enclosingTypeItemFactory: PsiTypeItemFactory,
        scopeDescription: String,
        psiOwner: PsiTypeParameterListOwner
    ): Pair<TypeParameterList, PsiTypeItemFactory> {
        val psiTypeParameterList =
            psiOwner.typeParameterList
                ?: return Pair(TypeParameterList.NONE, enclosingTypeItemFactory)

        val (typeParameters, typeItemFactory) =
            DefaultTypeParameterList.createTypeParameterItemsAndFactory(
                enclosingTypeItemFactory,
                scopeDescription,
                psiTypeParameterList.typeParameters.toList(),
                { PsiTypeParameterItem.create(codebase, it).apply { finishInitialization() } },
                // Create bounds and store it in the [PsiTypeParameterItem.bounds] property.
                { typeItemFactory, item, psiTypeParameter ->
                    val refs = psiTypeParameter.extendsList.referencedTypes
                    val bounds =
                        if (refs.isEmpty()) {
                            emptyList()
                        } else {
                            refs.mapNotNull { typeItemFactory.getBoundsType(PsiTypeInfo(it)) }
                        }
                    item.bounds = bounds
                    bounds
                },
            )

        return Pair(DefaultTypeParameterList(typeParameters), typeItemFactory)
    }
}
