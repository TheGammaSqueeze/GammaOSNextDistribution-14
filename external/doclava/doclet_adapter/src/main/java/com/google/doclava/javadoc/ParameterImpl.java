/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.google.doclava.javadoc;

import com.google.doclava.annotation.Used;
import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.Type;
import com.sun.javadoc.TypeVariable;
import javax.lang.model.element.VariableElement;

class ParameterImpl implements Parameter {

    private final String name;
    private final Type type;
    private final AnnotationDescImpl[] annotations;

    public ParameterImpl(VariableElement variableElement, Context context) {
        this.name = variableElement.getSimpleName().toString();
        this.type = TypeImpl.create(variableElement.asType(), context);
        this.annotations = variableElement.getAnnotationMirrors()
                .stream()
                .map(am -> new AnnotationDescImpl(am, context))
                .toArray(AnnotationDescImpl[]::new);
    }

    static ParameterImpl create(VariableElement ve, Context context) {
        return context.caches.parameters.computeIfAbsent(ve, el -> new ParameterImpl(el, context));
    }

    @Override
    @Used(implemented = true)
    public Type type() {
        return type;
    }

    @Override
    @Used(implemented = true)
    public String name() {
        return name;
    }

    @Override
    @Used(implemented = true)
    public String typeName() {
        String result;
        if (type instanceof ClassDocImpl || type instanceof TypeVariable) {
            result = type.typeName() + type.dimension();
        } else {
            result = type.qualifiedTypeName() + type.dimension();
        }
        return result;
    }

    @Override
    @Used(implemented = true)
    public AnnotationDesc[] annotations() {
        return annotations;
    }
}
