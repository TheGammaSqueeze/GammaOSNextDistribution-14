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
import com.sun.javadoc.AnnotationTypeDoc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ParameterizedType;
import com.sun.javadoc.Type;
import com.sun.javadoc.TypeVariable;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

public final class ArrayTypeImpl extends TypeImpl {

    private final ArrayType arrayType;
    private final Type underlyingType;
    private final String dimension;

    private ArrayTypeImpl(ArrayType at, Context context) {
        super(at, context);
        arrayType = at;

        int dim = 0;
        TypeMirror cur = at;
        while (cur.getKind() == TypeKind.ARRAY) {
            dim++;
            cur = ((ArrayType) cur).getComponentType();
        }
        dimension = "[]".repeat(dim);
        underlyingType = TypeImpl.create(cur, context);
    }

    public static ArrayTypeImpl create(ArrayType at, Context context) {
        return context.caches.types.array.computeIfAbsent(at, x -> new ArrayTypeImpl(x, context));
    }

    @Override
    @Used(implemented = true)
    public String qualifiedTypeName() {
        return underlyingType.qualifiedTypeName();
    }

    @Override
    @Used(implemented = true)
    public String simpleTypeName() {
        return underlyingType.simpleTypeName();
    }

    @Override
    @Used(implemented = true)
    public String dimension() {
        return dimension;
    }

    @Override
    @Used(implemented = true)
    public ClassDoc asClassDoc() {
        return underlyingType.asClassDoc();
    }

    @Override
    @Used(implemented = true)
    public TypeVariable asTypeVariable() {
        return underlyingType.asTypeVariable();
    }

    @Override
    @Used(implemented = true)
    public ParameterizedType asParameterizedType() {
        return underlyingType.asParameterizedType();
    }

    @Override
    @Unused(implemented = true)
    public AnnotationTypeDoc asAnnotationTypeDoc() {
        return underlyingType.asAnnotationTypeDoc();
    }

    @Override
    @Used(implemented = true)
    public boolean isPrimitive() {
        return underlyingType.isPrimitive();
    }
}
