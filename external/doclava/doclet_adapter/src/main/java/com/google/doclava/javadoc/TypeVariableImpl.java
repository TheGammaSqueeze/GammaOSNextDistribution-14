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
import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.javadoc.Type;
import com.sun.javadoc.TypeVariable;
import java.util.Objects;
import java.util.stream.Stream;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor8;

class TypeVariableImpl extends TypeImpl implements TypeVariable {

    private final javax.lang.model.type.TypeVariable typeVariable;
    private Type[] bounds;

    protected TypeVariableImpl(javax.lang.model.type.TypeVariable typeVariable, Context context) {
        super(typeVariable, context);
        this.typeVariable = typeVariable;
    }

    static TypeVariableImpl create(javax.lang.model.type.TypeVariable typeVariable,
            Context context) {
        return context.caches.types.typevar.computeIfAbsent(typeVariable,
                el -> new TypeVariableImpl(typeVariable, context));
    }

    @Override
    @Used(implemented = true)
    public Type[] bounds() {
        if (bounds == null) {
            TypeMirror bound = typeVariable.getUpperBound();
            bounds = bound.accept(new SimpleTypeVisitor8<Stream<? extends TypeMirror>, Void>(Stream.of(bound)) {
                        @Override
                        public Stream<? extends TypeMirror> visitIntersection(IntersectionType t, Void o) {
                            return t.getBounds().stream();
                        }
                    }, null)
                    .map(typeMirror -> TypeImpl.create(typeMirror, context))
                    .filter(type -> !type.qualifiedTypeName().equals("java.lang.Object"))
                    .toArray(Type[]::new);
        }
        return bounds;
    }

    @Override
    @Used(implemented = true)
    public ClassDoc asClassDoc() {
        TypeMirror erasure = context.environment.getTypeUtils().erasure(typeVariable);
        Type type = create(erasure, context);
        Objects.requireNonNull(type);
        return type.asClassDoc();
    }

    @Override
    @Unused
    public ProgramElementDoc owner() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Used(implemented = true)
    public TypeVariable asTypeVariable() {
        return this;
    }

    @Override
    @Unused
    public AnnotationDesc[] annotations() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Used(implemented = true)
    public String typeName() {
        TypeMirror erasure = context.environment.getTypeUtils().erasure(typeVariable);
        Type type = create(erasure, context);
        return type.typeName();
    }
}
