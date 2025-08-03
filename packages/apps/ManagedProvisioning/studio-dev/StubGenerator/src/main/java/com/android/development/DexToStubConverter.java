package com.android.development;


import static org.jf.dexlib2.AccessFlags.PUBLIC;
import static org.jf.dexlib2.AccessFlags.STATIC;

import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.ReferenceType;
import org.jf.dexlib2.ValueType;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedField;
import org.jf.dexlib2.dexbacked.DexBackedMethod;
import org.jf.dexlib2.iface.Annotatable;
import org.jf.dexlib2.iface.Annotation;
import org.jf.dexlib2.iface.AnnotationElement;
import org.jf.dexlib2.iface.Member;
import org.jf.dexlib2.iface.MethodParameter;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.value.AnnotationEncodedValue;
import org.jf.dexlib2.iface.value.ArrayEncodedValue;
import org.jf.dexlib2.iface.value.BooleanEncodedValue;
import org.jf.dexlib2.iface.value.ByteEncodedValue;
import org.jf.dexlib2.iface.value.CharEncodedValue;
import org.jf.dexlib2.iface.value.DoubleEncodedValue;
import org.jf.dexlib2.iface.value.EncodedValue;
import org.jf.dexlib2.iface.value.EnumEncodedValue;
import org.jf.dexlib2.iface.value.FloatEncodedValue;
import org.jf.dexlib2.iface.value.IntEncodedValue;
import org.jf.dexlib2.iface.value.LongEncodedValue;
import org.jf.dexlib2.iface.value.ShortEncodedValue;
import org.jf.dexlib2.iface.value.StringEncodedValue;
import org.jf.dexlib2.iface.value.TypeEncodedValue;
import org.jf.dexlib2.immutable.value.ImmutableEncodedValueFactory;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A converter which takes a dex file and creates a jar containing all the classes, and methods
 * and fields stubbed out.
 */
public class DexToStubConverter {

    private static final Logger LOGGER = Logger.getLogger(DexToStubConverter.class.toString());

    private static final Pattern INNER_OR_LAMBDA = Pattern.compile("\\$[0-9\\$]");

    private static final int STATIC_FINAL_CODE = Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
    private static final int ABS_INTERFACE_CODE = Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE;

    // Default dalvik annotations which store extra meta-data about the member
    private static final String ANNOTATION_INNER_CLASS = "Ldalvik/annotation/InnerClass;";
    private static final String ANNOTATION_DEFAULT_VALUE = "Ldalvik/annotation/AnnotationDefault;";
    private static final String ANNOTATION_SIGNATURE = "Ldalvik/annotation/Signature;";
    private static final String ANNOTATION_MEMBER_CLASS = "Ldalvik/annotation/MemberClasses;";
    private static final String ANNOTATION_THROWS = "Ldalvik/annotation/Throws;";

    private static final String INTERFACE_I_INTERFACE = "Landroid/os/IInterface;";

    private static final String INTERFACE_PARCELABLE = "Landroid/os/Parcelable;";

    private static final String FIELD_CREATOR_NAME = "CREATOR";
    private static final String FIELD_CREATOR_TYPE = "Landroid/os/Parcelable$Creator;";

    // Opcodes for operations that can be used in the static init block to initialize fields.
    // Fields that are initialized in the static block should not get a default value assigned.
    // These fields are not constants and get their value in runtime. Assigning a default value
    // in the SDK would cause the compiler to inline these values. Skipping the runtime evaluation.
    private static final HashSet<Opcode> STATIC_INIT_OPCODES = new HashSet<>(Arrays.asList(
            Opcode.SPUT,
            Opcode.SPUT_WIDE,
            Opcode.SPUT_OBJECT,
            Opcode.SPUT_BOOLEAN,
            Opcode.SPUT_BYTE,
            Opcode.SPUT_CHAR,
            Opcode.SPUT_SHORT,
            Opcode.SPUT_VOLATILE,
            Opcode.SPUT_WIDE_VOLATILE,
            Opcode.SPUT_OBJECT_VOLATILE
    ));

    // Map between parent class and subclass information
    private final HashMap<String, InnerClassData> mInnerClassMap = new HashMap<>();
    private final ZipOutputStream mOut;

    // Look for dupes
    private final HashSet<String> mZipEntries = new HashSet<>();

    private int mNextLineNumber = 0;

    private final HashSet<String> mParcelables = new HashSet<>();
    private final HashSet<String> mInterfaces = new HashSet<>();

    public DexToStubConverter(ZipOutputStream out) {
        mOut = out;
        mZipEntries.clear();
    }

    /**
     * Initializes any subclass information about this class
     */
    public void expectClass(DexBackedClassDef classDef) {
        String classDefType = classDef.getType();
        if (INNER_OR_LAMBDA.matcher(classDefType).find()) {
            return;
        }

        String className = typeToPath(classDefType);
        int accessFlags = classDef.getAccessFlags();
        for (AnnotationElement ae : findAnnotation(classDef, ANNOTATION_INNER_CLASS)) {
            if ("accessFlags".equals(ae.getName())
                    && ae.getValue().getValueType() == ValueType.INT) {
                accessFlags = ((IntEncodedValue) ae.getValue()).getValue();
            }
        }

        if (className.contains("$")) {
            mInnerClassMap.put(className,  new InnerClassData(className, accessFlags));
        } else {
            List<String> interfaces = classDef.getInterfaces();
            if (PUBLIC.isSet(accessFlags)) {
                if (interfaces.contains(INTERFACE_I_INTERFACE)) {
                    mInterfaces.add(Type.getType(classDefType).getClassName() + ";");
                } else if (interfaces.contains(INTERFACE_PARCELABLE)) {
                    if (StreamSupport.stream(classDef.getStaticFields().spliterator(), false)
                            .filter(d -> PUBLIC.isSet(d.accessFlags)
                                    && STATIC.isSet(d.accessFlags)
                                    && FIELD_CREATOR_NAME.equals(d.getName())
                                    && FIELD_CREATOR_TYPE.equals(d.getType()))
                            .findFirst().isPresent()) {
                        mParcelables.add(Type.getType(classDefType).getClassName() + ";");
                    }
                }
            }
        }
    }

    /**
     * Parses a line from the aidl file and updates internal state accordingly
     */
    public void expectAidlDef(String def) {
        String[] parts = def.split(" ");
        if (parts.length < 2) {
            return;
        }
        if ("parcelable".equals(parts[0].trim())) {
            mParcelables.remove(parts[1].trim());
        } else if ("interface".equals(parts[0].trim())) {
            mInterfaces.remove(parts[1].trim());
        }
    }

    /**
     * Prints any missing aidl definitions to {@code out}
     */
    public void printInterfaces(PrintStream out) {
        for (String s : mParcelables) {
            out.println("parcelable " + s);
        }
        for (String s : mInterfaces) {
            out.println("interface " + s);
        }
    }

    /**
     * Writes the class definition in the output stream
     */
    public String writeClass(DexBackedClassDef classDef) throws IOException {
        mNextLineNumber = 0;
        String classDefType = classDef.getType();
        String className = typeToPath(classDefType);
        String entryName = className + ".class";

        if (INNER_OR_LAMBDA.matcher(classDefType).find()) {
            LOGGER.fine("Skipping " + classDefType);
            // TODO: return null?
            return entryName;
        }

        Set<String> dependentInnerClasses = new HashSet<>();
        dependentInnerClasses.add(className);

        // TODO: Can skip private classes?
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        String[] interfaces = null;
        List<String> interfaceList = classDef.getInterfaces();
        if (!interfaceList.isEmpty()) {
            interfaces = interfaceList.toArray(new String[interfaceList.size()]);
            for (int i = 0; i < interfaces.length; i++) {
                interfaces[i] = typeToPath(interfaces[i]);
                dependentInnerClasses.add(interfaces[i]);
            }
        }

        int accessCode = classDef.getAccessFlags();
        if ((accessCode & ABS_INTERFACE_CODE) != ABS_INTERFACE_CODE) {
            // Mark the class as open in case it is not an interface. This prevents inlining
            // of constants
            accessCode |= Opcodes.ACC_OPEN;
        }

        String superClass = typeToPath(classDef.getSuperclass());
        cw.visit(Opcodes.V1_8,
                accessCode,
                className,
                parseSignature(classDef),
                superClass,
                interfaces);
        dependentInnerClasses.add(superClass);

        if (classDef.getSourceFile() != null) {
            cw.visitSource(classDef.getSourceFile(), null);
        }

        // If this is an annotation interface, get default values
        HashMap<String, EncodedValue> defaultValues = null;
        if ((classDef.getAccessFlags() & Opcodes.ACC_ANNOTATION) == Opcodes.ACC_ANNOTATION) {
            defaultValues = new HashMap<>();
            for (AnnotationElement ae :
                    findAnnotation(classDef, ANNOTATION_DEFAULT_VALUE)) {
                if (!(ae.getValue() instanceof AnnotationEncodedValue)) {
                    continue;
                }
                AnnotationEncodedValue aev = (AnnotationEncodedValue) ae.getValue();
                for (AnnotationElement aa : aev.getElements()) {
                    defaultValues.put(aa.getName(), aa.getValue());
                }
            }
        }

        Set<String> staticallyInitializedFields = new HashSet<>();
        // Write methods
        for (DexBackedMethod method : classDef.getMethods()) {
            if ("<clinit>".equals(method.getName())) {
                for (Instruction i : method.getImplementation().getInstructions()) {
                    if (STATIC_INIT_OPCODES.contains(i.getOpcode())
                            && i instanceof ReferenceInstruction) {
                        ReferenceInstruction ri = (ReferenceInstruction) i;
                        if (ri.getReferenceType() == ReferenceType.FIELD) {
                            FieldReference fr = (FieldReference) ri.getReference();
                            if (classDefType.equals(fr.getDefiningClass())) {
                                staticallyInitializedFields.add(fr.getName());
                            }
                        }
                    }
                }

                // Ignore static blocks
                continue;
            }

            // Skip private methods, but keep private constructor
            if (!"<init>".equals(method.getName()) && isPrivate(method)) {
                continue;
            }
            MethodParameter[] params = method.getParameters().stream().toArray(MethodParameter[]::new);
            Type[] paramTypes = new Type[params.length];
            for (int i = 0; i < paramTypes.length; i++) {
                paramTypes[i] = Type.getType(params[i].getType());
                dependentInnerClasses.add(typeToPath(params[i].getType()));
            }
            dependentInnerClasses.add(typeToPath(method.getReturnType()));

            String descriptor = Type.getMethodDescriptor(Type.getType(method.getReturnType()), paramTypes);
            String[] exception = getExceptionList(method);
            MethodVisitor mv = cw.visitMethod(method.getAccessFlags(), method.getName(),
                    descriptor, parseSignature(method), exception);

            if ((method.getAccessFlags() & Opcodes.ACC_ABSTRACT) != Opcodes.ACC_ABSTRACT) {
                mv.visitCode();
                Label startLabel = addLabel(mv);

                if ("<init>".equals(method.getName()) && classDef.getSuperclass() != null) {
                    // Create constructor
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitMethodInsn(
                            Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                }

                insertThrowStub(mv);

                Label endLabel = new Label();
                mv.visitLabel(endLabel);
                // Add param names
                int shift = 0;
                if ((method.getAccessFlags() & Opcodes.ACC_STATIC) != Opcodes.ACC_STATIC) {
                    mv.visitLocalVariable("this", classDefType, null, startLabel, endLabel, 0);
                    shift = 1;
                }

                for (int i = 0; i < params.length; i++) {
                    String name = params[i].getName();
                    if (name != null) {
                        mv.visitLocalVariable(name,
                                paramTypes[i].getDescriptor(), null, startLabel, endLabel,
                                i + shift);
                    }
                }
                mv.visitMaxs(3, shift + paramTypes.length);
            }

            if (defaultValues != null) {
                EncodedValue ev = defaultValues.get(method.getName());
                if (ev != null) {
                    Object value = encodedValueToObject(ev);
                    if (value != null) {
                        AnnotationVisitor av = mv.visitAnnotationDefault();
                        av.visit(null, value);
                        av.visitEnd();
                    } else if (ev.getValueType() == ValueType.ARRAY) {
                        AnnotationVisitor av = mv.visitAnnotationDefault();
                        av.visitArray(null);
                        av.visitEnd();
                    } else if (ev.getValueType() == ValueType.ENUM) {
                        FieldReference fr = ((EnumEncodedValue) ev).getValue();
                        AnnotationVisitor av = mv.visitAnnotationDefault();
                        av.visitEnum(null, fr.getType(), fr.getName());
                        av.visitEnd();
                    } else if (ev.getValueType() == ValueType.ANNOTATION) {
                        AnnotationVisitor av = mv.visitAnnotationDefault();
                        av.visitAnnotation(null, ((AnnotationEncodedValue) ev).getType());
                        av.visitEnd();
                    } else {
                        LOGGER.warning("Missing type parsing: " +
                                classDefType + " " + method.getName() + " " + ev);
                    }
                }
            }
            mv.visitEnd();
        }

        // Write fields
        for (DexBackedField field : classDef.getFields()) {
            if (isPrivate(field)) {
                continue;
            }

            Object value = staticallyInitializedFields.contains(field.getName())
                    ? null : getFieldValue(field);

            cw.visitField(field.getAccessFlags(), field.getName(),
                    Type.getType(field.getType()).getDescriptor(),
                    parseSignature(field),
                    value)
                .visitEnd();
        }

        // Inner classes
        collectTypeNames(classDef, ANNOTATION_MEMBER_CLASS, dependentInnerClasses);
        for (String dependentClass : dependentInnerClasses) {
            InnerClassData icd = mInnerClassMap.get(dependentClass);
            if (icd != null) {
                icd.write(cw);
            }
        }

        if (mZipEntries.add(entryName)) {
            mOut.putNextEntry(new ZipEntry(entryName));
            mOut.write(cw.toByteArray());
            LOGGER.fine("Written " + className);
        }
        return entryName;
    }

    private Object getFieldValue(DexBackedField field) {
        if ((field.getAccessFlags() & STATIC_FINAL_CODE) != STATIC_FINAL_CODE) {
            return null;
        }
        EncodedValue value = field.getInitialValue();
        if (value == null) {
            value = ImmutableEncodedValueFactory.defaultValueForType(field.getType());
        }
        return encodedValueToObject(value);
    }

    private static Object encodedValueToObject(EncodedValue value) {
        // TODO: Can probably support more types
        switch (value.getValueType()) {
            case ValueType.BYTE:
                return ((ByteEncodedValue) value).getValue();
            case ValueType.SHORT:
                return ((ShortEncodedValue) value).getValue();
            case ValueType.CHAR:
                return ((CharEncodedValue) value).getValue();
            case ValueType.INT:
                return ((IntEncodedValue) value).getValue();
            case ValueType.LONG:
                return ((LongEncodedValue) value).getValue();
            case ValueType.FLOAT:
                return ((FloatEncodedValue) value).getValue();
            case ValueType.DOUBLE:
                return ((DoubleEncodedValue) value).getValue();
            case ValueType.STRING:
                return ((StringEncodedValue) value).getValue();
            case ValueType.BOOLEAN:
                return ((BooleanEncodedValue) value).getValue();
            case ValueType.ANNOTATION:
            case ValueType.TYPE:

            default:
                return null;
        }
    }

    /**
     * Returns the list of exceptions as type strings defined by the method or null
     */
    private String[] getExceptionList(DexBackedMethod method) {
        ArrayList<String> out = new ArrayList<>();
        collectTypeNames(method, ANNOTATION_THROWS, out);
        if (out.isEmpty()) {
            return null;
        }
        return out.toArray(new String[out.size()]);
    }

    private static void collectTypeNames(Annotatable annotatable, String type,
            Collection<String> out) {
        for (AnnotationElement e : findAnnotation(annotatable, type)) {
            collectNames(e.getValue(), out);
        }
    }

    /**
     * Recursively collect names in the encoded value
     */
    private static void collectNames(EncodedValue ev, Collection<String> out) {
        if (ev instanceof ArrayEncodedValue) {
            ArrayEncodedValue aev = (ArrayEncodedValue) ev;
            for (EncodedValue e : aev.getValue()) {
                collectNames(e, out);
            }
        } else if (ev instanceof TypeEncodedValue) {
            TypeEncodedValue dbtev = (TypeEncodedValue) ev;
            out.add(typeToPath(dbtev.getValue()));
        }
    }

    private String parseSignature(Annotatable annotatable) {
        String s = null;
        for (AnnotationElement el : findAnnotation(annotatable, ANNOTATION_SIGNATURE)) {
            ArrayEncodedValue e = (ArrayEncodedValue) el.getValue();
            for (EncodedValue ev : e.getValue()) {
                if (s == null) {
                    s = "";
                }
                s += ((StringEncodedValue) ev).getValue();
            }
        }
        return s;
    }

    /**
     * Inserts a throw statement in the method body
     */
    private void insertThrowStub(MethodVisitor mv) {
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException");
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn("stub");
        mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,          // opcode
                "java/lang/RuntimeException",   // owner
                "<init>",                       // name
                "(Ljava/lang/String;)V",        // desc
                false);
        mv.visitInsn(Opcodes.ATHROW);
    }

    private Label addLabel(MethodVisitor mv) {
        mNextLineNumber += 5;
        Label l = new Label();
        mv.visitLabel(l);
        mv.visitLineNumber(mNextLineNumber, l);
        return l;
    }

    private static String typeToPath(String typeDesc) {
        if (typeDesc == null) {
            return null;
        }
        String name = Type.getType(typeDesc).getClassName();
        return name.replace('.', '/');
    }

    private static boolean isPrivate(Member member) {
        return (member.getAccessFlags() & Opcodes.ACC_PRIVATE) == Opcodes.ACC_PRIVATE;
    }

    private static Set<? extends AnnotationElement> findAnnotation(
            Annotatable annotatable, String type) {
        for (Annotation a : annotatable.getAnnotations()) {
            if (type.equals(a.getType())) {
                return a.getElements();
            }
        }
        return Collections.emptySet();
    }

    private static final class InnerClassData {
        final String className;
        final String parent;
        final String child;
        final int code;

        InnerClassData(String className, int code) {
            this.className = className;
            this.code = code;

            int lastIndex = className.lastIndexOf('$');
            parent = className.substring(0, lastIndex);
            child = className.substring(lastIndex + 1, className.length());
        }

        public void write(ClassWriter cw) {
            cw.visitInnerClass(className, parent, child, code);
        }
    }
}
