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
import com.sun.javadoc.AnnotationTypeDoc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ParameterizedType;
import com.sun.javadoc.Type;
import com.sun.javadoc.TypeVariable;
import com.sun.javadoc.WildcardType;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor14;

abstract class TypeImpl implements Type {

    protected final TypeMirror typeMirror;
    protected final Context context;

    protected TypeImpl(TypeMirror typeMirror, Context context) {
        this.typeMirror = typeMirror;
        this.context = context;
    }

    protected static Type create(TypeMirror m, Context context) {
         return create(m, context, /* underlyingType= */ false);
    }

    protected static Type create(TypeMirror m, Context context, boolean underlyingType) {
        return switch (m.getKind()) {
            // primitive types
            case BOOLEAN -> PrimitiveTypeImpl.BOOLEAN;
            case BYTE -> PrimitiveTypeImpl.BYTE;
            case CHAR -> PrimitiveTypeImpl.CHAR;
            case DOUBLE -> PrimitiveTypeImpl.DOUBLE;
            case FLOAT -> PrimitiveTypeImpl.FLOAT;
            case INT -> PrimitiveTypeImpl.INT;
            case LONG -> PrimitiveTypeImpl.LONG;
            case SHORT -> PrimitiveTypeImpl.SHORT;
            // void is also a "primitive type"
            case VOID -> PrimitiveTypeImpl.VOID;
            case NULL -> PrimitiveTypeImpl.NULL;
            // arrays
            case ARRAY -> {
                yield ArrayTypeImpl.create((ArrayType) m, context);
            }
            // complex types
            case WILDCARD -> {
                var wildcardType = (javax.lang.model.type.WildcardType) m;
                yield WildcardTypeImpl.create(wildcardType, context);
            }
            case TYPEVAR -> {
                var typeVar = (javax.lang.model.type.TypeVariable) m;
                yield TypeVariableImpl.create(typeVar, context);
            }
            case DECLARED -> {
                var dt = (DeclaredType) m;
                // Order matters! AnnotatedTypeImpl goes first as it has an "underlying type"
                // which will be constructed by this method in create() routine.
                if (!underlyingType && !dt.getAnnotationMirrors().isEmpty()) {
                    yield AnnotatedTypeImpl.create(dt, context);
                }
                if (!dt.getTypeArguments().isEmpty()) {
                    yield ParameterizedTypeImpl.create(dt, context);
                }
                //TODO: check that it will cast.
                var el = (TypeElement) dt.asElement();
                if (el.getKind() == ElementKind.ANNOTATION_TYPE) {
                    yield AnnotationTypeDocImpl.create(el, context);
                }
                yield ClassDocImpl.create(el, context);
            }
            case NONE -> {
                // e.g. Object.superclass()
                yield null;
            }
            case ERROR -> {
                var errorType = (ErrorType) m;
                var el = (TypeElement) errorType.asElement();
                if (el.getKind() == ElementKind.ANNOTATION_TYPE) {
                    yield AnnotationTypeDocImpl.create(el, context);
                }
                yield ErrorTypeImpl.create(errorType, context);
            }
            default -> throw new IllegalArgumentException(
                    "Unexpected type of kind: " + m.getKind());
        };
    }

    @Override
    @Unused
    public String typeName() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Used(implemented = false) // Not all cases covered yet in QUALIFIED_NAME_VISITOR
    public String qualifiedTypeName() {
        return QUALIFIED_NAME_VISITOR.visit(typeMirror, context);
    }

    @Override
    @Unused(implemented = true)
    public Type getElementType() {
        return null;
    }

    @Override
    @Used(implemented = true)
    public String simpleTypeName() {
        var qualifiedTypeName = QUALIFIED_NAME_VISITOR.visit(typeMirror, context);
        return qualifiedTypeName.substring(qualifiedTypeName.lastIndexOf('.') + 1);
    }

    @Override
    @Used(implemented = true)
    public String dimension() {
        return "";
    }

    @Override
    @Used(implemented = true)
    public boolean isPrimitive() {
        return false;
    }

    @Override
    @Used(implemented = true)
    public ClassDoc asClassDoc() {
        return null;
    }

    @Override
    @Used(implemented = true)
    public TypeVariable asTypeVariable() {
        return null;
    }

    @Override
    @Used(implemented = true)
    public WildcardType asWildcardType() {
        return null;
    }

    @Override
    @Used(implemented = true)
    public ParameterizedType asParameterizedType() {
        return null;
    }

    @Override
    @Unused(implemented = true)
    public AnnotationTypeDoc asAnnotationTypeDoc() {
        return null;
    }

    @Override
    @Used(implemented = true)
    public AnnotatedType asAnnotatedType() {
        return null;
    }

    private static final SimpleTypeVisitor14<String, Context> QUALIFIED_NAME_VISITOR = new SimpleTypeVisitor14<>() {
        @Override
        public String visitPrimitive(PrimitiveType t, Context context) {
            return switch (t.getKind()) {
                case BOOLEAN -> "boolean";
                case BYTE -> "byte";
                case CHAR -> "char";
                case DOUBLE -> "double";
                case FLOAT -> "float";
                case INT -> "int";
                case LONG -> "long";
                case SHORT -> "short";
                default -> throw new IllegalArgumentException("Unexpected primitive type with "
                        + "kind: " + t.getKind());
            };
        }

        @Override
        public String visitTypeVariable(javax.lang.model.type.TypeVariable t, Context context) {
            return t.asElement().toString();
        }

        @Override
        public String visitDeclared(DeclaredType t, Context context) {
            final String typeName = t.asElement().toString();

            if (typeName.contains("<")) {
                throw new RuntimeException();
            }

            return typeName;
        }

        @Override
        public String visitArray(ArrayType t, Context context) {
            return this.visit(t.getComponentType(), context);
        }

        @Override
        public String visitWildcard(javax.lang.model.type.WildcardType t, Context context) {
            //TODO: hack
            return "?";
            //return super.visitWildcard(t, context);
        }

        @Override
        protected String defaultAction(TypeMirror e, Context context) {
            throw new UnsupportedOperationException(
                    "Name visitor is not yet implemented for " + e.getKind());
        }
    };
}
