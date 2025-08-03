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

package com.android.car.tool;

import androidx.annotation.Nullable;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;
import com.github.javaparser.javadoc.description.JavadocDescription;
import com.github.javaparser.javadoc.description.JavadocDescriptionElement;
import com.github.javaparser.javadoc.description.JavadocInlineTag;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A parser for VehiclePropertyIds.java.
 *
 * It will parse the vehicle property ID definitions, comments and annotations and generate property
 * config file.
 */
public final class VehiclePropertyIdsParser {

    private static final int CONFIG_FILE_SCHEMA_VERSION = 1;

    private static final String USAGE =
            "VehiclePropertyIdsParser [path_to_CarLibSrcFolder] [output]";
    private static final String VEHICLE_PROPERTY_IDS_JAVA_PATH =
            "/android/car/VehiclePropertyIds.java";

    private static final String ACCESS_MODE_READ_LINK =
            "{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ}";
    private static final String ACCESS_MODE_WRITE_LINK =
            "{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_WRITE}";
    private static final String ACCESS_MODE_READ_WRITE_LINK =
            "{@link android.car.hardware.CarPropertyConfig#VEHICLE_PROPERTY_ACCESS_READ_WRITE}";

    // A map from property name to VHAL property ID if we use different property ID in car service
    // and in VHAL.
    private static final Map<String, Integer> VHAL_PROP_ID_MAP = Map.ofEntries(
            // VehicleProperty.VEHICLE_SPEED_DISPLAY_UNITS
            Map.entry("VEHICLE_SPEED_DISPLAY_UNITS", 0x11400605)
    );

    // A map to store permissions that are not defined in Car.java. It is not trivial to cross-ref
    // these so just hard-code them here.
    private static final Map<String, String> NON_CAR_PERMISSION_MAP = Map.ofEntries(
            Map.entry("ACCESS_FINE_LOCATION", "android.permission.ACCESS_FINE_LOCATION")
    );

    private static final class PropertyConfig {
        public String propertyName;
        public int propertyId;
        public String description = "";
        public PermissionType readPermission;
        public PermissionType writePermission;
        public boolean deprecated;
        public boolean systemApi;
        public boolean hide;
        public int vhalPropertyId;
        public List<Integer> dataEnums;
        public List<Integer> dataFlag;
        public String featureFlag;

        @Override
        public String toString() {
            StringBuilder s = new StringBuilder().append("PropertyConfig{")
                    .append("\n    propertyName: ").append(propertyName)
                    .append("\n    propertyId: ").append(propertyId)
                    .append("\n    description: ").append(description)
                    .append("\n    readPermission: ").append(readPermission)
                    .append("\n    writePermission: ").append(writePermission)
                    .append("\n    deprecated: ").append(deprecated)
                    .append("\n    hide: ").append(hide)
                    .append("\n    systemApi: ").append(systemApi)
                    .append("\n    dataEnums: ").append(dataEnums)
                    .append("\n    dataFlag: ").append(dataFlag)
                    .append("\n    featureFlag: ").append(featureFlag);

            if (vhalPropertyId != 0) {
                s.append("\n    vhalPropertyId: ").append(vhalPropertyId);
            }

            return s.append("\n}").toString();
        }
    }

    private enum ACCESS_MODE {
        READ, WRITE, READ_WRITE
    }

    private static final class PermissionType {
        public String type;
        public String value;
        public List<PermissionType> subPermissions = new ArrayList<>();

        public OrderedJSONObject toJson() throws JSONException {
            OrderedJSONObject jsonPerm = new OrderedJSONObject();
            jsonPerm.put("type", type);
            if (type.equals("single")) {
                jsonPerm.put("value", value);
                return jsonPerm;
            }
            List<OrderedJSONObject> subObjects = new ArrayList<>();
            for (int i = 0; i < subPermissions.size(); i++) {
                subObjects.add(subPermissions.get(i).toJson());
            }
            jsonPerm.put("value", new JSONArray(subObjects));
            return jsonPerm;
        }
    };

    /**
     * Sets the read/write permission for the config.
     */
    private static void setPermission(PropertyConfig config, ACCESS_MODE accessMode,
            PermissionType permission, boolean forRead, boolean forWrite) {
        if (forRead) {
            if (accessMode == ACCESS_MODE.READ || accessMode == ACCESS_MODE.READ_WRITE) {
                config.readPermission = permission;
            }
        }
        if (forWrite) {
            if (accessMode == ACCESS_MODE.WRITE || accessMode == ACCESS_MODE.READ_WRITE) {
                config.writePermission = permission;
            }
        }
    }

    // A hacky way to make the key in-order in the JSON object.
    private static final class OrderedJSONObject extends JSONObject {
        OrderedJSONObject() {
            try {
                Field map = JSONObject.class.getDeclaredField("nameValuePairs");
                map.setAccessible(true);
                map.set(this, new LinkedHashMap<>());
                map.setAccessible(false);
            } catch (IllegalAccessException | NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Parses the enum field declaration as an int value.
     */
    private static int parseIntEnumField(FieldDeclaration fieldDecl) {
        VariableDeclarator valueDecl = fieldDecl.getVariables().get(0);
        Expression expr = valueDecl.getInitializer().get();
        if (expr.isIntegerLiteralExpr()) {
            return expr.asIntegerLiteralExpr().asInt();
        }
        // For case like -123
        if (expr.isUnaryExpr()
                && expr.asUnaryExpr().getOperator() == UnaryExpr.Operator.MINUS) {
            return -expr.asUnaryExpr().getExpression().asIntegerLiteralExpr().asInt();
        }
        System.out.println("Unsupported expression: " + expr);
        System.exit(1);
        return 0;
    }

    private static String getFieldName(FieldDeclaration fieldDecl) {
        VariableDeclarator valueDecl = fieldDecl.getVariables().get(0);
        return valueDecl.getName().asString();
    }

    /**
     * Whether this field is an internal-only hidden field.
     */
    private static boolean isInternal(FieldDeclaration fieldDecl) {
        Optional<Comment> maybeComment = fieldDecl.getComment();
        boolean hide = false;
        boolean systemApi = false;
        if (maybeComment.isPresent()) {
            Javadoc doc = maybeComment.get().asJavadocComment().parse();
            for (JavadocBlockTag tag : doc.getBlockTags()) {
                if (tag.getTagName().equals("hide")) {
                    hide = true;
                    break;
                }
            }
        }
        List<AnnotationExpr> annotations = fieldDecl.getAnnotations();
        for (AnnotationExpr annotation : annotations) {
            if (annotation.getName().asString().equals("SystemApi")) {
                systemApi = true;
                break;
            }
        }
        return hide && !systemApi;
    }

    /**
     * Gets all the int enum values for this enum type.
     */
    private static List<Integer> getEnumValues(ResolvedReferenceTypeDeclaration typeDecl) {
        List<Integer> enumValues = new ArrayList<>();
        for (ResolvedFieldDeclaration resolvedFieldDecl : typeDecl.getAllFields()) {
            if (!resolvedFieldDecl.isField()) {
                continue;
            }
            FieldDeclaration fieldDecl = ((JavaParserFieldDeclaration) resolvedFieldDecl.asField())
                    .getWrappedNode();
            if (!isPublicAndStatic(fieldDecl) || isInternal(fieldDecl)) {
                continue;
            }
            enumValues.add(parseIntEnumField(fieldDecl));
        }
        return enumValues;
    }

    private static boolean isPublicAndStatic(FieldDeclaration fieldDecl) {
        return fieldDecl.isPublic() && fieldDecl.isStatic();
    }

    private final CompilationUnit mCu;
    private final Map<String, String> mCarPermissionMap = new HashMap<>();

    VehiclePropertyIdsParser(CompilationUnit cu) {
        this.mCu = cu;
        populateCarPermissionMap();
    }

    /**
     * Parses the Car.java class and stores all car specific permission into a map.
     */
    private void populateCarPermissionMap() {
        ResolvedReferenceTypeDeclaration typeDecl = parseClassName("Car");
        for (ResolvedFieldDeclaration resolvedFieldDecl : typeDecl.getAllFields()) {
            if (!resolvedFieldDecl.isField()) {
                continue;
            }
            FieldDeclaration fieldDecl = ((JavaParserFieldDeclaration) resolvedFieldDecl.asField())
                    .getWrappedNode();
            if (!isPublicAndStatic(fieldDecl)) {
                continue;
            }
            if (!isPublicAndStatic(fieldDecl) || isInternal(fieldDecl)) {
                continue;
            }
            String fieldName = getFieldName(fieldDecl);
            if (!fieldName.startsWith("PERMISSION_")) {
                continue;
            }
            VariableDeclarator valueDecl = fieldDecl.getVariables().get(0);
            mCarPermissionMap.put("Car." + fieldName,
                    valueDecl.getInitializer().get().asStringLiteralExpr().asString());
        }
    }

    /**
     * Maps the permission class to the actual permission string.
     */
    @Nullable
    private String permNameToValue(String permName) {
        String permStr = mCarPermissionMap.get(permName);
        if (permStr != null) {
            return permStr;
        }
        permStr = NON_CAR_PERMISSION_MAP.get(permName);
        if (permStr != null) {
            return permStr;
        }
        System.out.println("Permission: " + permName + " unknown, if it is not defined in"
                + " Car.java, you need to add it to NON_CAR_PERMISSION_MAP in parser");
        return null;
    }

    /**
     * Parses a class name and returns the class declaration.
     */
    private ResolvedReferenceTypeDeclaration parseClassName(String className) {
        ClassOrInterfaceType type = StaticJavaParser.parseClassOrInterfaceType(className);
        // Must associate the type with a compilation unit.
        type.setParentNode(mCu);
        return type.resolve().getTypeDeclaration();
    }

    /**
     * Parses a javadoc {@link XXX} annotation.
     */
    @Nullable
    private ResolvedReferenceTypeDeclaration parseClassLink(JavadocDescription linkElement) {
        List<JavadocDescriptionElement> elements = linkElement.getElements();
        if (elements.size() != 1) {
            System.out.println("expected one doc element in: " + linkElement);
            return null;
        }
        JavadocInlineTag tag = (JavadocInlineTag) elements.get(0);
        String className = tag.getContent().strip();
        try {
            return parseClassName(className);
        } catch (Exception e) {
            System.out.println("failed to parse class name: " + className);
            return null;
        }
    }

    /**
     * Parses a permission annotation.
     */
    @Nullable
    private PermissionType parsePermAnnotation(AnnotationExpr annotation) {
        PermissionType permission = new PermissionType();
        if (annotation.isSingleMemberAnnotationExpr()) {
            permission.type = "single";
            SingleMemberAnnotationExpr single =
                    annotation.asSingleMemberAnnotationExpr();
            Expression member = single.getMemberValue();
            String permName = permNameToValue(member.toString());
            if (permName == null) {
                return null;
            }
            permission.value = permName;
            return permission;
        } else if (annotation.isNormalAnnotationExpr()) {
            NormalAnnotationExpr normal = annotation.asNormalAnnotationExpr();
            boolean any = false;
            String name = normal.getPairs().get(0).getName().toString();
            if (name.equals("anyOf")) {
                permission.type = "anyOf";
            } else if (name.equals("allOf")) {
                permission.type = "allOf";
            } else {
                return null;
            }
            ArrayInitializerExpr expr = normal.getPairs().get(0).getValue()
                    .asArrayInitializerExpr();
            for (Expression permExpr : expr.getValues()) {
                PermissionType subPermission = new PermissionType();
                subPermission.type = "single";
                String permName = permNameToValue(permExpr.toString());
                if (permName == null) {
                    return null;
                }
                subPermission.value = permName;
                permission.subPermissions.add(subPermission);
            }
            return permission;
        }
        System.out.println("The permission annotation is not single or normal expression");
        return null;
    }

    /**
     * Parses the permission annotation and sets the config's permission accordingly.
     */
    private void parseAndSetPermAnnotation(AnnotationExpr annotation, PropertyConfig config,
            ACCESS_MODE accessMode, boolean forRead, boolean forWrite) {
        if (accessMode == null) {
            return;
        }
        PermissionType permission = parsePermAnnotation(annotation);
        if (permission == null) {
            System.out.println("Invalid RequiresPermission annotation: "
                        + annotation + " for property: " + config.propertyName);
            System.exit(1);
        }
        setPermission(config, accessMode, permission, forRead, forWrite);
    }

    /**
     * Main logic for parsing VehiclePropertyIds.java to a list of property configs.
     */
    private List<PropertyConfig> parse() {
        List<PropertyConfig> propertyConfigs = new ArrayList<>();
        ClassOrInterfaceDeclaration vehiclePropertyIdsClass =
                mCu.getClassByName("VehiclePropertyIds").get();

        List<FieldDeclaration> variables = vehiclePropertyIdsClass.findAll(FieldDeclaration.class);
        for (int i = 0; i < variables.size(); i++) {
            ACCESS_MODE accessMode = null;
            PropertyConfig propertyConfig = new PropertyConfig();

            FieldDeclaration propertyDef = variables.get(i).asFieldDeclaration();
            if (!isPublicAndStatic(propertyDef)) {
                continue;
            }
            String propertyName = getFieldName(propertyDef);
            if (propertyName.equals("INVALID")) {
                continue;
            }

            int propertyId = parseIntEnumField(propertyDef);
            propertyConfig.propertyName = propertyName;
            propertyConfig.propertyId = propertyId;

            if (VHAL_PROP_ID_MAP.get(propertyName) != null) {
                propertyConfig.vhalPropertyId = VHAL_PROP_ID_MAP.get(propertyName);
            }

            Optional<Comment> maybeComment = propertyDef.getComment();
            if (!maybeComment.isPresent()) {
                System.out.println("missing comment for property: " + propertyName);
                System.exit(1);
            }

            Javadoc doc = maybeComment.get().asJavadocComment().parse();
            List<JavadocBlockTag> blockTags = doc.getBlockTags();
            boolean deprecated = false;
            boolean hide = false;
            List<Integer> dataEnums = new ArrayList<>();
            List<Integer> dataFlag = new ArrayList<>();
            for (int j = 0; j < blockTags.size(); j++) {
                String commentTagName = blockTags.get(j).getTagName();
                if (commentTagName.equals("deprecated")
                        || commentTagName.equals("to_be_deprecated")) {
                    deprecated = true;
                }
                if (commentTagName.equals("hide")) {
                    hide = true;
                }
                String commentTagContent = blockTags.get(j).getContent().toText();
                ResolvedReferenceTypeDeclaration enumType = null;
                if (commentTagName.equals("data_enum") || commentTagName.equals("data_flag")) {
                    enumType = parseClassLink(blockTags.get(j).getContent());
                    if (enumType == null) {
                        System.out.println("Invalid comment block: " + commentTagContent
                                + " for property: " + propertyName);
                        System.exit(1);
                    }
                }
                if (commentTagName.equals("data_enum")) {
                    dataEnums.addAll(getEnumValues(enumType));
                }
                if (commentTagName.equals("data_flag")) {
                    if (dataFlag.size() != 0) {
                        System.out.println("Duplicated data_flag annotation for one property: "
                                + propertyName);
                        System.exit(1);
                    }
                    dataFlag = getEnumValues(enumType);
                }
            }

            String docText = doc.toText();
            propertyConfig.description = (docText.split("\n"))[0];
            propertyConfig.deprecated = deprecated;
            propertyConfig.hide = hide;
            propertyConfig.dataEnums = dataEnums;
            propertyConfig.dataFlag = dataFlag;

            if (docText.indexOf(ACCESS_MODE_READ_WRITE_LINK) != -1) {
                accessMode = ACCESS_MODE.READ_WRITE;
            } else if (docText.indexOf(ACCESS_MODE_READ_LINK) != -1) {
                accessMode = ACCESS_MODE.READ;
            } else if (docText.indexOf(ACCESS_MODE_WRITE_LINK) != -1) {
                accessMode = ACCESS_MODE.WRITE;
            } else {
                if (!deprecated) {
                    System.out.println("missing access mode for property: " + propertyName);
                    System.exit(1);
                }
            }

            List<AnnotationExpr> annotations = propertyDef.getAnnotations();
            for (int j = 0; j < annotations.size(); j++) {
                AnnotationExpr annotation = annotations.get(j);
                String annotationName = annotation.getName().asString();
                if (annotationName.equals("RequiresPermission")) {
                    parseAndSetPermAnnotation(annotation, propertyConfig, accessMode,
                            /* forRead= */ true, /* forWrite= */ true);
                }
                if (annotationName.equals("RequiresPermission.Read")) {
                    AnnotationExpr requireAnnotation = annotation.asSingleMemberAnnotationExpr()
                            .getMemberValue().asAnnotationExpr();
                    parseAndSetPermAnnotation(requireAnnotation, propertyConfig, accessMode,
                            /* forRead= */ true, /* forWrite= */ false);
                }
                if (annotationName.equals("RequiresPermission.Write")) {
                    AnnotationExpr requireAnnotation = annotation.asSingleMemberAnnotationExpr()
                            .getMemberValue().asAnnotationExpr();
                    parseAndSetPermAnnotation(requireAnnotation, propertyConfig, accessMode,
                            /* forRead= */ false, /* forWrite= */ true);
                }
                if (annotationName.equals("SystemApi")) {
                    propertyConfig.systemApi = true;
                }
                if (annotationName.equals("FlaggedApi")) {
                    SingleMemberAnnotationExpr single =
                            annotation.asSingleMemberAnnotationExpr();
                    Expression member = single.getMemberValue();
                    propertyConfig.featureFlag = member.toString();
                }
            }
            if (propertyConfig.systemApi || !propertyConfig.hide) {
                // We do not generate config for hidden APIs since they are not exposed to public.
                propertyConfigs.add(propertyConfig);
            }
        }
        return propertyConfigs;
    }

    /**
     * Main function.
     */
    public static void main(final String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println(USAGE);
            System.exit(1);
        }
        String carLib = args[0];
        String output = args[1];
        String vehiclePropertyIdsJava = carLib + VEHICLE_PROPERTY_IDS_JAVA_PATH;

        TypeSolver typeSolver = new CombinedTypeSolver(
                new ReflectionTypeSolver(),
                new JavaParserTypeSolver(carLib));
        StaticJavaParser.getConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver));

        CompilationUnit cu = StaticJavaParser.parse(new File(vehiclePropertyIdsJava));
        List<PropertyConfig> propertyConfigs = new VehiclePropertyIdsParser(cu).parse();

        JSONObject root = new JSONObject();
        root.put("version", CONFIG_FILE_SCHEMA_VERSION);
        JSONObject jsonProps = new OrderedJSONObject();
        root.put("properties", jsonProps);
        for (int i = 0; i < propertyConfigs.size(); i++) {
            JSONObject jsonProp = new OrderedJSONObject();
            PropertyConfig config = propertyConfigs.get(i);
            jsonProp.put("propertyName", config.propertyName);
            jsonProp.put("propertyId", config.propertyId);
            jsonProp.put("description", config.description);
            if (config.readPermission != null) {
                jsonProp.put("readPermission", config.readPermission.toJson());
            }
            if (config.writePermission != null) {
                jsonProp.put("writePermission", config.writePermission.toJson());
            }
            if (config.deprecated) {
                jsonProp.put("deprecated", config.deprecated);
            }
            if (config.systemApi) {
                jsonProp.put("systemApi", config.systemApi);
            }
            if (config.vhalPropertyId != 0) {
                jsonProp.put("vhalPropertyId", config.vhalPropertyId);
            }
            if (config.dataEnums.size() != 0) {
                jsonProp.put("dataEnums", new JSONArray(config.dataEnums));
            }
            if (config.dataFlag.size() != 0) {
                jsonProp.put("dataFlag", new JSONArray(config.dataFlag));
            }
            if (config.featureFlag != null) {
                jsonProp.put("featureFlag", config.featureFlag);
            }
            jsonProps.put(config.propertyName, jsonProp);
        }

        try (FileOutputStream outputStream = new FileOutputStream(output)) {
            outputStream.write(root.toString(2).getBytes());
        }
        System.out.println("Input: " + vehiclePropertyIdsJava
                + " successfully parsed. Output at: " + output);
    }
}
