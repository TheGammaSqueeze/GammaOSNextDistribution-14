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
import com.sun.javadoc.AnnotationValue;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor14;

class AnnotationValueImpl implements AnnotationValue {

    protected final javax.lang.model.element.AnnotationValue annotationValue;
    protected final Context context;

    protected AnnotationValueImpl(@Nonnull javax.lang.model.element.AnnotationValue av,
            Context context) {
        this.context = context;
        annotationValue = av;
    }

    public static @Nullable
    AnnotationValueImpl create(@Nullable javax.lang.model.element.AnnotationValue av,
            Context context) {
        // av could be null if it's a result of ExecutableElement#getDefaultValue()
        if (av == null) {
            return null;
        }
        return context.caches.annotationValues.computeIfAbsent(av,
                el -> new AnnotationValueImpl(el, context));
    }

    private Object value;

    @Override
    @Used(implemented = true)
    public Object value() {
        if (value == null) {
            value = valueVisitor.visit(annotationValue, context);
        }
        return value;
    }

    private static final SimpleAnnotationValueVisitor14<Object, Context> valueVisitor =
            new SimpleAnnotationValueVisitor14<>() {
                @Override
                public Object visitBoolean(boolean b, Context ctx) {
                    return b;
                }

                @Override
                public Object visitByte(byte b, Context ctx) {
                    return b;
                }

                @Override
                public Object visitChar(char c, Context ctx) {
                    return c;
                }

                @Override
                public Object visitDouble(double d, Context ctx) {
                    return d;
                }

                @Override
                public Object visitFloat(float f, Context ctx) {
                    return f;
                }

                @Override
                public Object visitInt(int i, Context ctx) {
                    return i;
                }

                @Override
                public Object visitLong(long l, Context ctx) {
                    return l;
                }

                @Override
                public Object visitShort(short s, Context ctx) {
                    return s;
                }

                @Override
                public Object visitString(String s, Context ctx) {
                    return s;
                }

                @Override
                public Object visitType(TypeMirror m, Context ctx) {
                    var e = ctx.environment.getTypeUtils().asElement(m);
                    return switch (e.getKind()) {
                        case CLASS, INTERFACE, ENUM -> ClassDocImpl.create((TypeElement) e, ctx);
                        case ANNOTATION_TYPE -> AnnotationTypeDocImpl.create((TypeElement) e, ctx);
                        default -> throw new UnsupportedOperationException(
                                e.getKind() + " is not not yet implemented");
                    };
                }

                @Override
                public Object visitEnumConstant(VariableElement c, Context context) {
                    return null;
                }

                @Override
                public Object visitAnnotation(AnnotationMirror m, Context ctx) {
                    return null;
                }

                @Override
                public Object visitArray(
                        List<? extends javax.lang.model.element.AnnotationValue> vals,
                        Context ctx) {
                    AnnotationValueImpl[] ret = new AnnotationValueImpl[vals.size()];
                    for (int i = 0; i < vals.size(); i++) {
                        ret[i] = AnnotationValueImpl.create(vals.get(i), ctx);
                    }
                    return ret;
                }

                @Override
                protected Object defaultAction(Object o, Context context) {
                    throw new UnsupportedOperationException("Unexpected annotation value: " + o);
                }
            };
}
