/*
 * Copyright (C) 2023 The Android Open Source Project
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

package libcore.tools.analyzer.openjdk;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Collector of classes, fields and methods.
 */
public class SignaturesCollector {

    private final List<String> mClasses = new ArrayList<>();
    private final List<Field> mFields = new ArrayList<>();
    private final List<Method> mMethods = new ArrayList<>();
    private Predicate<String> mClassPredicate = (c) -> true;
    private Predicate<Field> mFieldPredicate = (f) -> true;
    private Predicate<Method> mMethodPredicate = (m) -> true;

    public SignaturesCollector() {}

    public SignaturesCollector add(MethodInsnNode node) {
        return add(new Method(node));
    }

    public SignaturesCollector add(InvokeDynamicInsnNode node) {
        add(new Method(node.bsm));
        for (Object obj : node.bsmArgs) {
            if (obj instanceof Handle) {
                add(new Method((Handle) obj));
            }
        }
        return this;
    }

    private SignaturesCollector add(Method method) {
        if (mMethodPredicate.test(method)) {
            mMethods.add(method);
        }
        return this;
    }
    public SignaturesCollector add(String internalClassName, MethodNode methodNode) {
        Method method = new Method(internalClassName, methodNode);
        return add(method);
    }

    public SignaturesCollector add(FieldInsnNode node) {
        return add(new Field(node));
    }

    public SignaturesCollector add(String internalClassName, FieldNode fieldNode) {
        Field field = new Field(internalClassName, fieldNode);
        return add(field);
    }


    private SignaturesCollector add(Field field) {
        if (mFieldPredicate.test(field)) {
            mFields.add(field);
        }
        return this;
    }

    public SignaturesCollector add(SignaturesCollection collection) {
        collection.mClasses.stream()
                .filter(mClassPredicate)
                .forEach(this::addClass);
        collection.mFields.stream()
                .filter(mFieldPredicate)
                .forEach(this::add);
        collection.mMethods.stream()
                .filter(mMethodPredicate)
                .forEach(this::add);
        return this;
    }

    /**
     * A primitive type is ignored.
     *
     * @param internalOrDesc internal name or type descriptor
     */
    public SignaturesCollector addClass(String internalOrDesc) {
        String internalClassName = getInternalName(internalOrDesc);
        if (internalClassName != null && mClassPredicate.test(internalClassName)) {
            mClasses.add(internalClassName);
        }
        return this;
    }

    /**
     * Add all non-primitive types of fields, method arguments, method returns, and parent classes
     * of the {@link ClassNode} into the collection.
     */
    public SignaturesCollector addClassesFromClassNode(ClassNode node) {
        addClass(node.superName);
        node.interfaces.forEach(this::addClass);
        node.fields.forEach(fieldNode -> addClass(Type.getType(fieldNode.desc).getInternalName()));

        List<MethodNode> methods = new ArrayList<>(node.methods);
        for (MethodNode method : methods) {
            addClass(Type.getReturnType(method.desc).getInternalName());
            Arrays.stream(Type.getArgumentTypes(method.desc))
                    .forEach(type -> addClass(type.getInternalName()));
        }
        return this;
    }

    private static String getInternalName(String internalOrDesc) {
        if (internalOrDesc == null || internalOrDesc.isEmpty()) {
            return null;
        }
        // Return null if it's a primitive type.
        if (internalOrDesc.length() == 1 && "VZCBSIFJD".contains(internalOrDesc)) {
            return null;
        }
        Type type = Type.getObjectType(internalOrDesc);
        if (type.getSort() == Type.ARRAY) {
            type = type.getElementType();
        }
        if (type.getSort() != Type.OBJECT) {
            // return null for primitive types.
            return null;
        }

        return type.getInternalName();
    }

    public SignaturesCollector setClassFilter(Predicate<String> predicate) {
        this.mClassPredicate = predicate;
        return this;
    }

    public SignaturesCollector setFieldFilter(Predicate<Field> predicate) {
        this.mFieldPredicate = predicate;
        return this;
    }

    public SignaturesCollector setMethodFilter(Predicate<Method> predicate) {
        this.mMethodPredicate = predicate;
        return this;
    }
    public SignaturesCollection getCollection() {
        Collections.sort(mClasses);
        Collections.sort(mFields);
        Collections.sort(mMethods);
        return new SignaturesCollection(mClasses, mFields, mMethods);
    }

    public static class SignaturesCollection {
        private final LinkedHashSet<String> mClasses;
        private final LinkedHashSet<Field> mFields;

        private final LinkedHashSet<Method> mMethods;

        private SignaturesCollection(List<String> classes, List<Field> fields,
                List<Method> methods) {
            mClasses = new LinkedHashSet<>(classes);
            mFields = new LinkedHashSet<>(fields);
            mMethods = new LinkedHashSet<>(methods);
        }

        public Stream<String> getClassStream() {
            return mClasses.stream();
        }
        public Stream<Field> getFieldStream() {
            return mFields.stream();
        }

        public Stream<Method> getMethodStream() {
            return mMethods.stream();
        }

        public boolean containsClass(String internalClassName) {
            return mClasses.contains(internalClassName);
        }

        public boolean contains(String internalClassName, MethodNode node) {
            return contains(new Method(internalClassName, node));
        }

        public boolean contains(MethodInsnNode node) {
            return contains(new Method(node));
        }

        public boolean contains(Handle handle) {
            return contains(new Method(handle));
        }

        public boolean contains(FieldInsnNode node) {
            return contains(new Field(node));
        }

        private boolean contains(Method method) {
            return mMethods.contains(method);
        }

        private boolean contains(Field field) {
            return mFields.contains(field);
        }

        public boolean contains(MemberInfo info) {
            if (info instanceof Method) {
                return contains(((Method) info));
            } else {
                return contains(((Field) info));
            }
        }

        public boolean containsField(String owner, String name, String desc) {
            return contains(new Field(owner, name, desc));
        }

        public boolean containsMethod(String owner, String name, String desc) {
            return contains(new Method(owner, name, desc));
        }

        public boolean isEmpty() {
            return mClasses.isEmpty() && mFields.isEmpty() && mMethods.isEmpty();
        }
    }

    public interface MemberInfo {
        String getOwner();
        String getName();
        String getDesc();
    }

    public static class Field implements Comparable<Field>, MemberInfo {
        /**
         * Internal class name using `/` separator
         */
        private final String owner;
        private final String name;
        /**
         * The type descriptor of the field.
         *
         * @see Type#getDescriptor()
         */
        private final String desc;

        private Field(String owner, String name, String desc) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
        }

        private Field(FieldInsnNode node) {
            this(node.owner, node.name, node.desc);
        }

        private Field(String internalClassName, FieldNode fieldNode) {
            this(internalClassName, fieldNode.name, fieldNode.desc);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Field)) return false;
            Field field = (Field) o;
            return owner.equals(field.owner) && name.equals(field.name) && desc.equals(field.desc);
        }

        @Override
        public int hashCode() {
            return Objects.hash(owner, name, desc);
        }

        @Override
        public String toString() {
            return owner + "." + name + " : " + desc;
        }

        @Override
        public int compareTo(Field f) {
            return toString().compareTo(f.toString());
        }

        @Override
        public String getOwner() {
            return owner;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDesc() {
            return desc;
        }
    }

    public static class Method implements Comparable<Method>, MemberInfo {
        /**
         * Internal class name using `/` separator
         */
        private final String owner;
        private final String name;

        /**
         * The method descriptor.
         *
         * @see MethodNode#desc
         * @see Type#getMethodDescriptor(Type, Type...)
         */
        private final String desc;

        private Method(String owner, String name, String desc) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
        }

        private Method(MethodInsnNode node) {
            this(node.owner, node.name, node.desc);
        }

        private Method(String internalClassName, MethodNode node) {
            this(internalClassName, node.name, node.desc);
        }

        private Method(Handle handle) {
            this(handle.getOwner(), handle.getName(), handle.getDesc());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Method)) return false;
            Method method = (Method) o;
            return owner.equals(method.owner) && name.equals(method.name) && desc.equals(
                    method.desc);
        }

        @Override
        public int hashCode() {
            return Objects.hash(owner, name, desc);
        }

        @Override
        public String toString() {
            return owner + "#" + name + desc;
        }

        @Override
        public int compareTo(Method m) {
            return toString().compareTo(m.toString());
        }

        @Override
        public String getOwner() {
            return owner;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDesc() {
            return desc;
        }
    }
}
