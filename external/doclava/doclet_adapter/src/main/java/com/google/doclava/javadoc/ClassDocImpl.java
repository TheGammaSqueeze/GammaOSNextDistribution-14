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
import com.sun.javadoc.ConstructorDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.ParamTag;
import com.sun.javadoc.ParameterizedType;
import com.sun.javadoc.Type;
import com.sun.javadoc.TypeVariable;
import com.sun.javadoc.WildcardType;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.util.Names;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import jdk.javadoc.internal.tool.DocEnvImpl;
import jdk.javadoc.internal.tool.ToolEnvironment;

import static com.sun.tools.javac.code.Kinds.Kind.*;

class ClassDocImpl extends ProgramElementDocImpl<TypeElement> implements ClassDoc {

    protected final TypeElement typeElement;

    // Cached fields
    private ConstructorDoc[] constructorsFiltered;
    private ConstructorDoc[] constructorsAll;
    private Type[] interfaceTypes;
    private ClassDoc[] interfaces;
    private TypeVariable[] typeParameters;
    private MethodDoc[] methodsFiltered;
    private MethodDoc[] methodsAll;
    private FieldDoc[] fieldsFiltered;
    private FieldDoc[] fieldsAll;
    private FieldDoc[] enumConstants;
    private ClassDoc[] innerClassesFiltered;
    private ClassDoc[] innerClassesAll;

    protected ClassDocImpl(TypeElement c, Context context) {
        super(c, context);
        typeElement = c;

        if (c.getKind().isInterface()) {
            reflectModifiers |= java.lang.reflect.Modifier.INTERFACE;
        }
    }

    static ClassDocImpl create(TypeElement e, Context context) {
        return context.caches.classes.computeIfAbsent(e, el -> new ClassDocImpl(el, context));
    }

    @Override
    @Unused(implemented = true)
    public String modifiers() {
        return java.lang.reflect.Modifier.toString(modifierSpecifier());
    }

    @Override
    @Unused(implemented = true)
    public int modifierSpecifier() {
        if (isInterface() || isAnnotationType()) {
            return reflectModifiers & ~java.lang.reflect.Modifier.ABSTRACT;
        }
        return reflectModifiers;
    }

    private Boolean isClass;

    @Override
    @Unused(implemented = true)
    public boolean isClass() {
        if (isClass == null) {
            isClass = typeElement.getKind().isClass();
        }
        return isClass;
    }

    private Boolean isOrdinaryClass;

    @Override
    @Used(implemented = true)
    public boolean isOrdinaryClass() {
        if (isOrdinaryClass == null) {
            isOrdinaryClass = (!isEnum() &&
                    !isInterface() &&
                    !isAnnotationType() &&
                    !isError() &&
                    !isException()
            );
        }
        return isOrdinaryClass;
    }

    private Boolean isEnum;

    @Override
    @Used(implemented = true)
    public boolean isEnum() {
        if (isEnum == null) {
            isEnum = (typeElement.getKind() == ElementKind.ENUM);
        }
        return isEnum;
    }

    private Boolean isInterface;

    @Override
    @Used(implemented = true)
    public boolean isInterface() {
        if (isInterface == null) {
            isInterface = (typeElement.getKind() == ElementKind.INTERFACE);
        }
        return isInterface;
    }

    private Boolean isException;

    @Override
    @Used(implemented = true)
    public boolean isException() {
        if (isException == null) {
            isException = context.docletElementUtils.isException(typeElement);
        }
        return isException;
    }

    private Boolean isError;

    @Override
    @Used(implemented = true)
    public boolean isError() {
        if (isError == null) {
            isError = context.docletElementUtils.isError(typeElement);
        }
        return isError;
    }

    private String name;

    @Override
    @Used(implemented = true)
    public String name() {
        if (name == null) {
            name = context.docletElementUtils.getClassNameUntilNotNested(typeElement);
        }
        return name;
    }

    private String qualifiedName;

    @Override
    @Used(implemented = true)
    public String qualifiedName() {
        if (qualifiedName == null) {
            qualifiedName = typeElement.getQualifiedName().toString();
        }
        return qualifiedName;
    }

    @Override
    @Used(implemented = true)
    public boolean isIncluded() {
        return context.environment.isIncluded(typeElement);
    }

    @Override
    @Used(implemented = true)
    public boolean isAbstract() {
        return java.lang.reflect.Modifier.isAbstract(reflectModifiers);
    }

    private Boolean isSerializable;

    @Override
    @Used(implemented = true)
    public boolean isSerializable() {
        if (isSerializable == null) {
            var serializable = context.environment.getElementUtils()
                    .getTypeElement("java.io.Serializable").asType();
            isSerializable = context.environment.getTypeUtils()
                    .isSubtype(typeElement.asType(), serializable);
        }
        return isSerializable;
    }

    private Boolean isExternalizable;

    @Override
    @Unused(implemented = true)
    public boolean isExternalizable() {
        if (isExternalizable == null) {
            var externalizable = context.environment.getElementUtils()
                    .getTypeElement("java.io.Externalizable").asType();
            isExternalizable = context.environment.getTypeUtils()
                    .isSubtype(typeElement.asType(), externalizable);
        }
        return isExternalizable;
    }

    @Override
    @Unused
    public MethodDoc[] serializationMethods() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Unused
    public FieldDoc[] serializableFields() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Unused
    public boolean definesSerializableFields() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Used(implemented = true)
    public ClassDoc superclass() {
        if (isInterface()) {
            return null;
        }
        TypeMirror superclassMirror = typeElement.getSuperclass();
        if (superclassMirror.getKind() == TypeKind.NONE) {
            return null;
        }
        return TypeImpl.create(superclassMirror, context).asClassDoc();
    }

    @Override
    @Used(implemented = true)
    public Type superclassType() {
        if (isInterface()) {
            return null;
        }
        TypeMirror superclassMirror = typeElement.getSuperclass();
        if (superclassMirror.getKind() == TypeKind.NONE) {
            return null;
        }
        return TypeImpl.create(superclassMirror, context);
    }

    @Override
    @Unused(implemented = true)
    public boolean subclassOf(ClassDoc cd) {
        TypeElement other = context.environment.getElementUtils()
                .getTypeElement(cd.qualifiedName());
        if (isInterface()) {
            return other.getQualifiedName().contentEquals("java.lang.Object");
        }
        return context.environment.getTypeUtils().isSubtype(typeElement.asType(), other.asType());
    }

    @Override
    @Used(implemented = true)
    public ClassDoc[] interfaces() {
        if (interfaces == null) {
            interfaces = typeElement.getInterfaces()
                    .stream()
                    .map(typeMirror -> {
                        TypeElement asElement = (TypeElement) context.environment.getTypeUtils()
                                .asElement(typeMirror);
                        return ClassDocImpl.create(asElement, context);
                    })
                    .toArray(ClassDoc[]::new);
        }
        return interfaces;
    }

    @Override
    @Used(implemented = true)
    public Type[] interfaceTypes() {
        if (interfaceTypes == null) {
            interfaceTypes = typeElement.getInterfaces()
                    .stream()
                    .filter(typeMirror -> !typeMirror.getKind().equals(TypeKind.NONE))
                    .map(typeMirror -> TypeImpl.create(typeMirror, context))
                    .toArray(Type[]::new);
        }
        return interfaceTypes;
    }

    @Override
    @Used(implemented = true)
    public TypeVariable[] typeParameters() {
        if (typeParameters == null) {
            typeParameters = typeElement.getTypeParameters()
                    .stream()
                    .map(tp -> {
                        javax.lang.model.type.TypeVariable tv = (javax.lang.model.type.TypeVariable) tp.asType();
                        return TypeVariableImpl.create(tv, context);
                    })
                    .toArray(TypeVariable[]::new);
        }
        return typeParameters;
    }

    @Override
    @Unused
    public ParamTag[] typeParamTags() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Unused(implemented = true)
    public FieldDoc[] fields() {
        if (fieldsAll == null) {
            fieldsAll = getFields(true);
        }
        return fieldsAll;
    }

    @Override
    @Used(implemented = true)
    public FieldDoc[] fields(boolean filter) {
        if (filter) {
            if (fieldsFiltered == null) {
                fieldsFiltered = getFields(true);
            }
            return fieldsFiltered;
        } else {
            if (fieldsAll == null) {
                fieldsAll = getFields(false);
            }
            return fieldsAll;
        }
    }

    private FieldDoc[] getFields(boolean filter) {
        return typeElement.getEnclosedElements()
                .stream()
                .filter(e -> e.getKind() == ElementKind.FIELD)
                .filter(field -> !filter || context.environment.isSelected(field))
                .map(field -> FieldDocImpl.create((VariableElement) field, context))
                .toArray(FieldDoc[]::new);
    }

    @Override
    @Used(implemented = true)
    public FieldDoc[] enumConstants() {
        if (enumConstants == null) {
            enumConstants = typeElement.getEnclosedElements()
                    .stream()
                    .filter(e -> e.getKind() == ElementKind.ENUM_CONSTANT)
                    .map(enumConstant -> FieldDocImpl.create((VariableElement) enumConstant,
                            context))
                    .toArray(FieldDoc[]::new);
        }
        return enumConstants;
    }

    @Override
    @Unused(implemented = true)
    public MethodDoc[] methods() {
        if (methodsAll == null) {
            methodsAll = getMethods(true);
        }
        return methodsAll;
    }

    @Override
    @Used(implemented = true)
    public MethodDoc[] methods(boolean filter) {
        if (filter) {
            if (methodsFiltered == null) {
                methodsFiltered = getMethods(true);
            }
            return methodsFiltered;
        } else {
            if (methodsAll == null) {
                methodsAll = getMethods(false);
            }
            return methodsAll;
        }
    }

    private MethodDoc[] getMethods(boolean filter) {
        return typeElement.getEnclosedElements()
                .stream()
                .filter(e -> e.getKind() == ElementKind.METHOD)
                .filter(method -> !filter || context.environment.isSelected(method))
                .map(method -> MethodDocImpl.create((ExecutableElement) method, context))
                .toArray(MethodDoc[]::new);
    }

    @Override
    @Unused(implemented = true)
    public ConstructorDoc[] constructors() {
        if (constructorsFiltered == null) {
            constructorsFiltered = getConstructors(true);
        }
        return constructorsFiltered;
    }

    @Override
    @Used(implemented = true)
    public ConstructorDoc[] constructors(boolean filter) {
        if (filter) {
            if (constructorsFiltered == null) {
                constructorsFiltered = getConstructors(true);
            }
            return constructorsFiltered;
        } else {
            if (constructorsAll == null) {
                constructorsAll = getConstructors(false);
            }
            return constructorsAll;
        }
    }

    private ConstructorDoc[] getConstructors(boolean filter) {
        return typeElement.getEnclosedElements()
                .stream()
                .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
                .filter(ctor -> !filter || context.environment.isSelected(ctor))
                .map(e -> ConstructorDocImpl.create((ExecutableElement) e, context))
                .toArray(ConstructorDoc[]::new);
    }

    @Override
    @Used(implemented = true)
    public ClassDoc[] innerClasses() {
        if (innerClassesFiltered == null) {
            innerClassesFiltered = getInnerClasses(true);
        }
        return innerClassesFiltered;
    }

    @Override
    @Used(implemented = true)
    public ClassDoc[] innerClasses(boolean filter) {
        if (filter) {
            return innerClasses();
        } else {
            if (innerClassesAll == null) {
                innerClassesAll = getInnerClasses(false);
            }
            return innerClassesAll;
        }
    }

    private ClassDoc[] getInnerClasses(boolean filter) {
        return ElementFilter.typesIn(typeElement.getEnclosedElements())
                .stream()
                .filter(te -> te.getNestingKind() == NestingKind.MEMBER &&
                        (te.getKind() == ElementKind.CLASS || te.getKind() == ElementKind.INTERFACE)
                )
                .filter(te -> !filter || context.environment.isSelected(te))
                .map(te -> ClassDocImpl.create(te, context))
                .toArray(ClassDoc[]::new);
    }

    /**
     * Note that this implementation does not search in sources!
     *
     * <p>
     *
     * {@inheritDoc}
     *
     * @implNote Does not search in sources.
     */
    @Override
    @Used(implemented = true)
    public ClassDoc findClass(String className) {
        ClassDoc result = searchClass(className);
        if (result != null) {
            return result;
        }

        ClassDoc enclosing = containingClass();
        while (enclosing != null && enclosing.containingClass() != null) {
            enclosing = enclosing.containingClass();
        }
        if (enclosing == null) {
            return null;
        }
        return ((ClassDocImpl) enclosing).searchClass(className);
    }

    private ClassDoc searchClass(String className) {
        TypeElement cls = context.environment.getElementUtils().getTypeElement(className);
        if (cls != null) {
            return ClassDocImpl.create(cls, context);
        }

        for (ClassDoc nested : innerClasses()) {
            if (nested.name().equals(className) || nested.name().endsWith("." + className)) {
                return nested;
            } else {
                ClassDoc inNested = ((ClassDocImpl) nested).searchClass(className);
                if (inNested != null) {
                    return inNested;
                }
            }
        }

        ClassDoc inPackage = containingPackage().findClass(className);
        if (inPackage != null) {
            return inPackage;
        }

        //
        if (! (typeElement instanceof ClassSymbol)) {
            return null;
        }
        ClassSymbol tsym = (ClassSymbol)typeElement;
        // make sure that this symbol has been completed
        // TODO: do we need this anymore ?
        if (tsym.completer != null) {
            tsym.complete();
        }

        // search imports
        if (tsym.sourcefile != null) {

            ToolEnvironment toolEnv = ((DocEnvImpl)(context.environment)).toolEnv;
            //### This information is available only for source classes.
            Env<AttrContext> compenv = toolEnv.getEnv(tsym);
            if (compenv == null) {
                return null;
            }
            Names names = tsym.name.table.names;
            Scope s = compenv.toplevel.namedImportScope;
            for (Symbol sym : s.getSymbolsByName(names.fromString(className))) {
                if (sym.kind == TYP) {
                    return ClassDocImpl.create((TypeElement)sym, context);
                }
            }

            s = compenv.toplevel.starImportScope;
            for (Symbol sym : s.getSymbolsByName(names.fromString(className))) {
                if (sym.kind == TYP) {
                    return ClassDocImpl.create((TypeElement)sym, context);
                }
            }
        }

        return null;
    }

    @Override
    @Unused
    public ClassDoc[] importedClasses() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Unused
    public PackageDoc[] importedPackages() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Unused(implemented = true)
    public String typeName() {
        return name();
    }

    @Override
    @Used(implemented = true)
    public String qualifiedTypeName() {
        return qualifiedName();
    }

    private String simpleTypeName;

    @Override
    @Used(implemented = true)
    public String simpleTypeName() {
        if (simpleTypeName == null) {
            simpleTypeName = typeElement.getSimpleName().toString();
        }
        return simpleTypeName;
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
        return this;
    }

    @Override
    @Used(implemented = true)
    public ParameterizedType asParameterizedType() {
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
    public AnnotatedType asAnnotatedType() {
        return null;
    }

    @Override
    @Unused(implemented = true)
    public AnnotationTypeDoc asAnnotationTypeDoc() {
        return null;
    }

    @Override
    @Used(implemented = true)
    public Type getElementType() {
        return null;
    }
}
