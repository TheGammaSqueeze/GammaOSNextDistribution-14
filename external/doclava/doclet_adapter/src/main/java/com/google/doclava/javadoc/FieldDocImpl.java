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
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.SerialFieldTag;
import com.sun.javadoc.Type;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements.Origin;

class FieldDocImpl extends MemberDocImpl<VariableElement> implements FieldDoc {

    protected final VariableElement variableElement;

    protected FieldDocImpl(VariableElement e, Context context) {
        super(e, context);
        variableElement = e;
    }

    static FieldDocImpl create(VariableElement e, Context context) {
        return context.caches.fields.computeIfAbsent(e, el -> new FieldDocImpl(el, context));
    }

    @Override
    @Used(implemented = true)
    public boolean isSynthetic() {
        return context.environment.getElementUtils().getOrigin(variableElement) == Origin.SYNTHETIC;
    }

    @Override
    @Used(implemented = true)
    public String name() {
        return variableElement.getSimpleName().toString();
    }

    @Override
    @Used(implemented = true)
    public String qualifiedName() {
        var enclosingClass = variableElement.getEnclosingElement();
        return switch (enclosingClass.getKind()) {
            case CLASS, INTERFACE, ANNOTATION_TYPE, ENUM ->
                    ((TypeElement) enclosingClass).getQualifiedName().toString() + "."
                            + variableElement.getSimpleName();
            default -> throw new UnsupportedOperationException("Expected CLASS, INTERFACE, "
                    + "ANNOTATION_TYPE or ENUM, but got " + enclosingClass.getKind());
        };
    }

    @Override
    @Used(implemented = true)
    public boolean isIncluded() {
        return context.environment.isIncluded(variableElement);
    }

    @Override
    @Used(implemented = true)
    public Type type() {
        return TypeImpl.create(variableElement.asType(), context);
    }

    @Override
    @Used(implemented = true)
    public boolean isTransient() {
        return java.lang.reflect.Modifier.isTransient(reflectModifiers);
    }

    @Override
    @Used(implemented = true)
    public boolean isVolatile() {
        return java.lang.reflect.Modifier.isVolatile(reflectModifiers);
    }

    @Override
    @Unused
    public SerialFieldTag[] serialFieldTags() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Used(implemented = true)
    public Object constantValue() {
        var cv = variableElement.getConstantValue();
        if (cv == null) {
            return null;
        }
        // cv is either a primitive type or a String
        if (cv instanceof String) {
            return cv;
        } else if (cv instanceof Boolean b) {
            return b;
        } else if (cv instanceof Byte b) {
            return (int) b;
        } else if (cv instanceof Character c) {
            return (int) c;
        } else if (cv instanceof Double d) {
            return d;
        } else if (cv instanceof Float f) {
            return f;
        } else if (cv instanceof Integer i) {
            return i;
        } else if (cv instanceof Long l) {
            return l;
        } else if (cv instanceof Short s) {
            return (int) s;
        } else {
            throw new IllegalArgumentException("Unexpected constant value of type " + cv.getClass()
                    + " when expected java.lang.String or primitive type");
        }
    }

    @Override
    @Unused
    public String constantValueExpression() {
        throw new UnsupportedOperationException("not yet implemented");
    }
}
