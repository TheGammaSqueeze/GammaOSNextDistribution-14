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
import com.sun.javadoc.AnnotatedType;
import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.AnnotationTypeDoc;
import com.sun.javadoc.Type;
import javax.lang.model.type.DeclaredType;

class AnnotatedTypeImpl extends TypeImpl implements AnnotatedType {

    private final DeclaredType declaredType;

    private Type underlyingType;

    private AnnotationDesc[] annotations;

    protected AnnotatedTypeImpl(DeclaredType declaredType, Context context) {
        super(declaredType, context);
        this.declaredType = declaredType;
    }

    static AnnotatedTypeImpl create(DeclaredType declaredType, Context context) {
        return context.caches.types.annotated.computeIfAbsent(declaredType,
                el -> new AnnotatedTypeImpl(el, context));
    }

    /**
     * @return annotations
     * @implNote Implemented as used in {@code ParameterImplTests#annotations()}.
     */
    @Override
    @Unused(implemented = true)
    public AnnotationDesc[] annotations() {
        if (annotations == null) {
            annotations = declaredType.getAnnotationMirrors()
                    .stream()
                    .map(am -> new AnnotationDescImpl(am, context))
                    .toArray(AnnotationDescImpl[]::new);
        }
        return annotations;
    }

    @Override
    @Used(implemented = true)
    public Type underlyingType() {
        if (underlyingType == null) {
            underlyingType = TypeImpl.create(declaredType, context, /* underlyingType= */ true);
        }
        return underlyingType;
    }

    @Override
    public AnnotatedType asAnnotatedType() {
        return this;
    }
}
