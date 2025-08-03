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

import com.google.doclava.annotation.Unused;
import com.google.doclava.annotation.Used;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ParameterizedType;
import com.sun.javadoc.Type;
import java.util.Objects;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

class ParameterizedTypeImpl extends TypeImpl implements ParameterizedType {

    private final DeclaredType declaredType;

    // Cached fields
    private Type[] typeArguments;

    protected ParameterizedTypeImpl(DeclaredType declaredType, Context context) {
        super(declaredType, context);
        this.declaredType = declaredType;
    }

    static ParameterizedTypeImpl create(DeclaredType declaredType, Context context) {
        return context.caches.types.parameterized.computeIfAbsent(declaredType,
                el -> new ParameterizedTypeImpl(el, context));
    }

    @Override
    @Used(implemented = true)
    public ParameterizedType asParameterizedType() {
        return this;
    }

    @Override
    @Used(implemented = true)
    public Type[] typeArguments() {
        if (typeArguments == null) {
            typeArguments = declaredType.getTypeArguments()
                    .stream()
                    .map(typeMirror -> TypeImpl.create(typeMirror, context))
                    .toArray(Type[]::new);
        }
        return typeArguments;
    }

    @Override
    @Unused
    public Type superclassType() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Unused
    public Type[] interfaceTypes() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Unused
    public Type containingType() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public ClassDoc asClassDoc() {
        TypeMirror erasure = context.environment.getTypeUtils().erasure(declaredType);
        Type type = create(erasure, context);
        Objects.requireNonNull(type);
        return type.asClassDoc();
    }
}
