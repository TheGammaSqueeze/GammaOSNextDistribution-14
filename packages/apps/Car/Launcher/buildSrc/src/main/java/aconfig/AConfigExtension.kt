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

package aconfig

import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

interface AConfigDeclaration {
    val packageName: Property<String>
    val srcFile: ConfigurableFileCollection
}

open class AConfigExtension(private val objectFactory: ObjectFactory) {

    val declarations: DomainObjectSet<AConfigDeclaration> = objectFactory.domainObjectSet(
        AConfigDeclaration::class.java
    )

    fun aconfigDeclaration(action: Action<AConfigDeclaration>) {
        val declaration = objectFactory.newInstance(AConfigDeclaration::class.java)
        action.execute(declaration)
        declarations.add(declaration)
    }
}
