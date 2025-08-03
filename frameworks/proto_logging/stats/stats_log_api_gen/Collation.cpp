/*
 * Copyright (C) 2017, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "Collation.h"

#include <google/protobuf/descriptor.h>
#include <stdarg.h>
#include <stdio.h>

#include <map>

#include "frameworks/proto_logging/stats/atom_field_options.pb.h"
#include "frameworks/proto_logging/stats/atoms.pb.h"
#include "frameworks/proto_logging/stats/attribution_node.pb.h"
#include "utils.h"

namespace android {
namespace stats_log_api_gen {

using google::protobuf::EnumDescriptor;
using google::protobuf::FieldDescriptor;
using google::protobuf::FileDescriptor;
using google::protobuf::SourceLocation;
using std::make_shared;
using std::map;

const bool dbg = false;

const int PLATFORM_PULLED_ATOMS_START = 10000;
const int PLATFORM_PULLED_ATOMS_END = 99999;
const int VENDOR_PULLED_ATOMS_START = 150000;
const int VENDOR_PULLED_ATOMS_END = 199999;

//
// AtomDecl class
//

AtomDecl::AtomDecl() : code(0), name(), atomType(ATOM_TYPE_PUSHED) {
}

AtomDecl::AtomDecl(const AtomDecl& that)
    : code(that.code),
      name(that.name),
      message(that.message),
      fields(that.fields),
      atomType(that.atomType),
      fieldNumberToAnnotations(that.fieldNumberToAnnotations),
      primaryFields(that.primaryFields),
      exclusiveField(that.exclusiveField),
      defaultState(that.defaultState),
      triggerStateReset(that.triggerStateReset),
      nested(that.nested) {
}

AtomDecl::AtomDecl(int c, const string& n, const string& m, AtomType a)
    : code(c), name(n), message(m), atomType(a) {
}

AtomDecl::~AtomDecl() {
}

/**
 * Print an error message for a FieldDescriptor, including the file name and
 * line number.
 */
// NOLINTNEXTLINE(cert-dcl50-cpp)
static void print_error(const FieldDescriptor& field, const char* format, ...) {
    const Descriptor* message = field.containing_type();
    const FileDescriptor* file = message->file();

    SourceLocation loc;
    if (field.GetSourceLocation(&loc)) {
        // TODO(b/162454173): this will work if we can figure out how to pass
        // --include_source_info to protoc
        fprintf(stderr, "%s:%d: ", file->name().c_str(), loc.start_line);
    } else {
        fprintf(stderr, "%s: ", file->name().c_str());
    }
    va_list args;
    va_start(args, format);
    vfprintf(stderr, format, args);
    va_end(args);
}

/**
 * Convert a protobuf type into a java type.
 */
static java_type_t java_type(const FieldDescriptor& field) {
    const int protoType = field.type();
    const bool isRepeatedField = field.is_repeated();

    switch (protoType) {
        case FieldDescriptor::TYPE_FLOAT:
            return isRepeatedField ? JAVA_TYPE_FLOAT_ARRAY : JAVA_TYPE_FLOAT;
        case FieldDescriptor::TYPE_INT64:
            return isRepeatedField ? JAVA_TYPE_LONG_ARRAY : JAVA_TYPE_LONG;
        case FieldDescriptor::TYPE_INT32:
            return isRepeatedField ? JAVA_TYPE_INT_ARRAY : JAVA_TYPE_INT;
        case FieldDescriptor::TYPE_BOOL:
            return isRepeatedField ? JAVA_TYPE_BOOLEAN_ARRAY : JAVA_TYPE_BOOLEAN;
        case FieldDescriptor::TYPE_STRING:
            return isRepeatedField ? JAVA_TYPE_STRING_ARRAY : JAVA_TYPE_STRING;
        case FieldDescriptor::TYPE_ENUM:
            return isRepeatedField ? JAVA_TYPE_ENUM_ARRAY : JAVA_TYPE_ENUM;
        case FieldDescriptor::TYPE_GROUP:
            return JAVA_TYPE_UNKNOWN_OR_INVALID;
        case FieldDescriptor::TYPE_MESSAGE:
            if (field.message_type()->full_name() == "android.os.statsd.AttributionNode") {
                return JAVA_TYPE_ATTRIBUTION_CHAIN;
            } else if ((field.options().GetExtension(os::statsd::log_mode) ==
                        os::statsd::LogMode::MODE_BYTES) &&
                       !isRepeatedField) {
                return JAVA_TYPE_BYTE_ARRAY;
            } else {
                return isRepeatedField ? JAVA_TYPE_UNKNOWN_OR_INVALID : JAVA_TYPE_OBJECT;
            }
        case FieldDescriptor::TYPE_BYTES:
            return isRepeatedField ? JAVA_TYPE_UNKNOWN_OR_INVALID : JAVA_TYPE_BYTE_ARRAY;
        case FieldDescriptor::TYPE_UINT64:
            return isRepeatedField ? JAVA_TYPE_UNKNOWN_OR_INVALID : JAVA_TYPE_LONG;
        case FieldDescriptor::TYPE_UINT32:
            return isRepeatedField ? JAVA_TYPE_UNKNOWN_OR_INVALID : JAVA_TYPE_INT;
        default:
            return JAVA_TYPE_UNKNOWN_OR_INVALID;
    }
}

/**
 * Gather the enums info.
 */
void collate_enums(const EnumDescriptor& enumDescriptor, AtomField& atomField) {
    for (int i = 0; i < enumDescriptor.value_count(); i++) {
        atomField.enumValues[enumDescriptor.value(i)->number()] = enumDescriptor.value(i)->name();
    }
}

static void addAnnotationToAtomDecl(AtomDecl& atomDecl, const int fieldNumber,
                                    const AnnotationId annotationId,
                                    const AnnotationType annotationType,
                                    const AnnotationValue annotationValue) {
    if (dbg) {
        printf("   Adding annotation to %s: [%d] = {id: %d, type: %d}\n", atomDecl.name.c_str(),
               fieldNumber, annotationId, annotationType);
    }
    atomDecl.fieldNumberToAnnotations[fieldNumber].insert(
            make_shared<Annotation>(annotationId, atomDecl.code, annotationType, annotationValue));
}

static int collate_field_restricted_annotations(AtomDecl& atomDecl, const FieldDescriptor& field,
                                                const int fieldNumber) {
    int errorCount = 0;

    if (field.options().HasExtension(os::statsd::field_restriction_option)) {
        if (!atomDecl.restricted) {
            print_error(field,
                        "field_restriction_option annotations must be from an atom with "
                        "a restriction_category annotation: '%s'\n",
                        atomDecl.message.c_str());
            errorCount++;
        }

        const os::statsd::FieldRestrictionOption& fieldRestrictionOption =
                field.options().GetExtension(os::statsd::field_restriction_option);

        if (fieldRestrictionOption.peripheral_device_info()) {
            addAnnotationToAtomDecl(atomDecl, fieldNumber,
                                    ANNOTATION_ID_FIELD_RESTRICTION_PERIPHERAL_DEVICE_INFO,
                                    ANNOTATION_TYPE_BOOL, AnnotationValue(true));
        }

        if (fieldRestrictionOption.app_usage()) {
            addAnnotationToAtomDecl(atomDecl, fieldNumber,
                                    ANNOTATION_ID_FIELD_RESTRICTION_APP_USAGE, ANNOTATION_TYPE_BOOL,
                                    AnnotationValue(true));
        }

        if (fieldRestrictionOption.app_activity()) {
            addAnnotationToAtomDecl(atomDecl, fieldNumber,
                                    ANNOTATION_ID_FIELD_RESTRICTION_APP_ACTIVITY,
                                    ANNOTATION_TYPE_BOOL, AnnotationValue(true));
        }

        if (fieldRestrictionOption.health_connect()) {
            addAnnotationToAtomDecl(atomDecl, fieldNumber,
                                    ANNOTATION_ID_FIELD_RESTRICTION_HEALTH_CONNECT,
                                    ANNOTATION_TYPE_BOOL, AnnotationValue(true));
        }

        if (fieldRestrictionOption.accessibility()) {
            addAnnotationToAtomDecl(atomDecl, fieldNumber,
                                    ANNOTATION_ID_FIELD_RESTRICTION_ACCESSIBILITY,
                                    ANNOTATION_TYPE_BOOL, AnnotationValue(true));
        }

        if (fieldRestrictionOption.system_search()) {
            addAnnotationToAtomDecl(atomDecl, fieldNumber,
                                    ANNOTATION_ID_FIELD_RESTRICTION_SYSTEM_SEARCH,
                                    ANNOTATION_TYPE_BOOL, AnnotationValue(true));
        }

        if (fieldRestrictionOption.user_engagement()) {
            addAnnotationToAtomDecl(atomDecl, fieldNumber,
                                    ANNOTATION_ID_FIELD_RESTRICTION_USER_ENGAGEMENT,
                                    ANNOTATION_TYPE_BOOL, AnnotationValue(true));
        }

        if (fieldRestrictionOption.ambient_sensing()) {
            addAnnotationToAtomDecl(atomDecl, fieldNumber,
                                    ANNOTATION_ID_FIELD_RESTRICTION_AMBIENT_SENSING,
                                    ANNOTATION_TYPE_BOOL, AnnotationValue(true));
        }

        if (fieldRestrictionOption.demographic_classification()) {
            addAnnotationToAtomDecl(atomDecl, fieldNumber,
                                    ANNOTATION_ID_FIELD_RESTRICTION_DEMOGRAPHIC_CLASSIFICATION,
                                    ANNOTATION_TYPE_BOOL, AnnotationValue(true));
        }
    }

    if (field.options().HasExtension(os::statsd::restriction_category)) {
        print_error(field, "restriction_category must be an atom-level annotation: '%s'\n",
                    atomDecl.message.c_str());
        errorCount++;
    }

    return errorCount;
}

static int collate_field_annotations(AtomDecl& atomDecl, const FieldDescriptor& field,
                                     const int fieldNumber, const java_type_t& javaType) {
    int errorCount = 0;

    if (field.options().HasExtension(os::statsd::state_field_option)) {
        if (is_repeated_field(javaType)) {
            print_error(field,
                        "State field annotations are not allowed for repeated fields: '%s'\n",
                        atomDecl.message.c_str());
            errorCount++;
            return errorCount;
        }

        const os::statsd::StateAtomFieldOption& stateFieldOption =
                field.options().GetExtension(os::statsd::state_field_option);
        const bool primaryField = stateFieldOption.primary_field();
        const bool exclusiveState = stateFieldOption.exclusive_state();
        const bool primaryFieldFirstUid = stateFieldOption.primary_field_first_uid();

        // Check the field is only one of primaryField, exclusiveState, or primaryFieldFirstUid.
        if (primaryField + primaryFieldFirstUid + exclusiveState > 1) {
            print_error(field,
                        "Field can be max 1 of primary_field, exclusive_state, "
                        "or primary_field_first_uid: '%s'\n",
                        atomDecl.message.c_str());
            errorCount++;
        }

        if (primaryField) {
            if (javaType == JAVA_TYPE_ATTRIBUTION_CHAIN || javaType == JAVA_TYPE_OBJECT ||
                javaType == JAVA_TYPE_BYTE_ARRAY) {
                print_error(field, "Invalid primary state field: '%s'\n", atomDecl.message.c_str());
                errorCount++;
            } else {
                atomDecl.primaryFields.push_back(fieldNumber);
                addAnnotationToAtomDecl(atomDecl, fieldNumber, ANNOTATION_ID_PRIMARY_FIELD,
                                        ANNOTATION_TYPE_BOOL, AnnotationValue(true));
            }
        }

        if (primaryFieldFirstUid) {
            if (javaType != JAVA_TYPE_ATTRIBUTION_CHAIN) {
                print_error(field,
                            "PRIMARY_FIELD_FIRST_UID annotation is only for AttributionChains: "
                            "'%s'\n",
                            atomDecl.message.c_str());
                errorCount++;
            } else {
                atomDecl.primaryFields.push_back(FIRST_UID_IN_CHAIN_ID);
                addAnnotationToAtomDecl(atomDecl, fieldNumber,
                                        ANNOTATION_ID_PRIMARY_FIELD_FIRST_UID, ANNOTATION_TYPE_BOOL,
                                        AnnotationValue(true));
            }
        }

        if (exclusiveState) {
            if (javaType == JAVA_TYPE_ATTRIBUTION_CHAIN || javaType == JAVA_TYPE_OBJECT ||
                javaType == JAVA_TYPE_BYTE_ARRAY) {
                print_error(field, "Invalid exclusive state field: '%s'\n",
                            atomDecl.message.c_str());
                errorCount++;
            }

            if (atomDecl.exclusiveField != 0) {
                print_error(field,
                            "Cannot have more than one exclusive state field in an "
                            "atom: '%s'\n",
                            atomDecl.message.c_str());
                errorCount++;
            } else {
                atomDecl.exclusiveField = fieldNumber;
                addAnnotationToAtomDecl(atomDecl, fieldNumber, ANNOTATION_ID_EXCLUSIVE_STATE,
                                        ANNOTATION_TYPE_BOOL, AnnotationValue(true));
            }

            if (stateFieldOption.has_default_state_value()) {
                const int defaultState = stateFieldOption.default_state_value();
                atomDecl.defaultState = defaultState;

                addAnnotationToAtomDecl(atomDecl, fieldNumber, ANNOTATION_ID_DEFAULT_STATE,
                                        ANNOTATION_TYPE_INT, AnnotationValue(defaultState));
            }

            if (stateFieldOption.has_trigger_state_reset_value()) {
                const int triggerStateReset = stateFieldOption.trigger_state_reset_value();

                atomDecl.triggerStateReset = triggerStateReset;
                addAnnotationToAtomDecl(atomDecl, fieldNumber, ANNOTATION_ID_TRIGGER_STATE_RESET,
                                        ANNOTATION_TYPE_INT, AnnotationValue(triggerStateReset));
            }

            if (stateFieldOption.has_nested()) {
                const bool nested = stateFieldOption.nested();
                atomDecl.nested = nested;

                addAnnotationToAtomDecl(atomDecl, fieldNumber, ANNOTATION_ID_STATE_NESTED,
                                        ANNOTATION_TYPE_BOOL, AnnotationValue(nested));
            }
        }
    }

    errorCount += collate_field_restricted_annotations(atomDecl, field, fieldNumber);

    if (field.options().GetExtension(os::statsd::is_uid) == true) {
        if (javaType != JAVA_TYPE_INT && javaType != JAVA_TYPE_INT_ARRAY) {
            print_error(field,
                        "is_uid annotation can only be applied to int32 fields and repeated int32 "
                        "fields: '%s'\n",
                        atomDecl.message.c_str());
            errorCount++;
        }

        addAnnotationToAtomDecl(atomDecl, fieldNumber, ANNOTATION_ID_IS_UID, ANNOTATION_TYPE_BOOL,
                                AnnotationValue(true));
    }

    return errorCount;
}

/**
 * Gather the info about an atom proto.
 */
int collate_atom(const Descriptor& atom, AtomDecl& atomDecl, vector<java_type_t>& signature) {
    int errorCount = 0;

    // Build a sorted list of the fields. Descriptor has them in source file
    // order.
    map<int, const FieldDescriptor*> fields;
    for (int j = 0; j < atom.field_count(); j++) {
        const FieldDescriptor* field = atom.field(j);
        fields[field->number()] = field;
    }

    // Check that the parameters start at 1 and go up sequentially.
    int expectedNumber = 1;
    for (map<int, const FieldDescriptor*>::const_iterator it = fields.begin(); it != fields.end();
         it++) {
        const int number = it->first;
        const FieldDescriptor& field = *it->second;
        if (number != expectedNumber) {
            print_error(field,
                        "Fields must be numbered consecutively starting at 1:"
                        " '%s' is %d but should be %d\n",
                        field.name().c_str(), number, expectedNumber);
            errorCount++;
            expectedNumber = number;
            continue;
        }
        expectedNumber++;
    }

    // Check that only allowed types are present. Remove any invalid ones.
    for (map<int, const FieldDescriptor*>::const_iterator it = fields.begin(); it != fields.end();
         it++) {
        const FieldDescriptor& field = *it->second;
        const bool isBinaryField = field.options().GetExtension(os::statsd::log_mode) ==
                                   os::statsd::LogMode::MODE_BYTES;

        const java_type_t javaType = java_type(field);

        if (javaType == JAVA_TYPE_UNKNOWN_OR_INVALID) {
            if (field.is_repeated()) {
                print_error(field, "Repeated field type %d is not allowed for field: %s\n",
                            field.type(), field.name().c_str());
            } else {
                print_error(field, "Field type %d is not allowed for field: %s\n", field.type(),
                            field.name().c_str());
            }
            errorCount++;
            continue;
        } else if (javaType == JAVA_TYPE_OBJECT) {
            // Allow attribution chain, but only at position 1.
            print_error(field, "Message type not allowed for field without mode_bytes: %s\n",
                        field.name().c_str());
            errorCount++;
            continue;
        } else if (javaType == JAVA_TYPE_BYTE_ARRAY && !isBinaryField) {
            print_error(field, "Raw bytes type not allowed for field: %s\n", field.name().c_str());
            errorCount++;
            continue;
        }

        if (isBinaryField && javaType != JAVA_TYPE_BYTE_ARRAY) {
            print_error(field, "Cannot mark field %s as bytes.\n", field.name().c_str());
            errorCount++;
            continue;
        }

        if (atomDecl.restricted && !is_primitive_field(javaType)) {
            print_error(field, "Restricted atom '%s' cannot have nonprimitive field: '%s'\n",
                        atomDecl.message.c_str(), field.name().c_str());
            errorCount++;
            continue;
        }
    }

    // Check that if there's an attribution chain, it's at position 1.
    for (map<int, const FieldDescriptor*>::const_iterator it = fields.begin(); it != fields.end();
         it++) {
        const int number = it->first;
        if (number != 1) {
            const FieldDescriptor& field = *it->second;
            const java_type_t javaType = java_type(field);
            if (javaType == JAVA_TYPE_ATTRIBUTION_CHAIN) {
                print_error(field,
                            "AttributionChain fields must have field id 1, in message: '%s'\n",
                            atom.name().c_str());
                errorCount++;
            }
        }
    }

    // Build the type signature and the atom data.
    for (map<int, const FieldDescriptor*>::const_iterator it = fields.begin(); it != fields.end();
         it++) {
        const FieldDescriptor& field = *it->second;
        const java_type_t javaType = java_type(field);
        const bool isBinaryField = field.options().GetExtension(os::statsd::log_mode) ==
                                   os::statsd::LogMode::MODE_BYTES;

        AtomField atField(field.name(), javaType);

        if (javaType == JAVA_TYPE_ENUM || javaType == JAVA_TYPE_ENUM_ARRAY) {
            atField.enumTypeName = field.enum_type()->name();
            // All enums are treated as ints when it comes to function signatures.
            collate_enums(*field.enum_type(), atField);
        }

        // Generate signature for atom.
        if (javaType == JAVA_TYPE_ENUM) {
            // All enums are treated as ints when it comes to function signatures.
            signature.push_back(JAVA_TYPE_INT);
        } else if (javaType == JAVA_TYPE_ENUM_ARRAY) {
            signature.push_back(JAVA_TYPE_INT_ARRAY);
        } else if (javaType == JAVA_TYPE_OBJECT && isBinaryField) {
            signature.push_back(JAVA_TYPE_BYTE_ARRAY);
        } else {
            signature.push_back(javaType);
        }

        atomDecl.fields.push_back(atField);

        errorCount += collate_field_annotations(atomDecl, field, it->first, javaType);
    }

    return errorCount;
}

// This function flattens the fields of the AttributionNode proto in an Atom
// proto and generates the corresponding atom decl and signature.
bool get_non_chained_node(const Descriptor& atom, AtomDecl& atomDecl,
                          vector<java_type_t>& signature) {
    // Build a sorted list of the fields. Descriptor has them in source file
    // order.
    map<int, const FieldDescriptor*> fields;
    for (int j = 0; j < atom.field_count(); j++) {
        const FieldDescriptor& field = *atom.field(j);
        fields[field.number()] = &field;
    }

    AtomDecl attributionDecl;
    vector<java_type_t> attributionSignature;
    collate_atom(*android::os::statsd::AttributionNode::descriptor(), attributionDecl,
                 attributionSignature);

    // Build the type signature and the atom data.
    bool has_attribution_node = false;
    for (map<int, const FieldDescriptor*>::const_iterator it = fields.begin(); it != fields.end();
         it++) {
        const FieldDescriptor& field = *it->second;
        const java_type_t javaType = java_type(field);
        if (javaType == JAVA_TYPE_ATTRIBUTION_CHAIN) {
            atomDecl.fields.insert(atomDecl.fields.end(), attributionDecl.fields.begin(),
                                   attributionDecl.fields.end());
            signature.insert(signature.end(), attributionSignature.begin(),
                             attributionSignature.end());
            has_attribution_node = true;

        } else {
            AtomField atField(field.name(), javaType);
            if (javaType == JAVA_TYPE_ENUM) {
                // All enums are treated as ints when it comes to function signatures.
                signature.push_back(JAVA_TYPE_INT);
                collate_enums(*field.enum_type(), atField);
            } else {
                signature.push_back(javaType);
            }
            atomDecl.fields.push_back(atField);
        }
    }
    return has_attribution_node;
}

static void populateFieldNumberToAtomDeclSet(const shared_ptr<AtomDecl>& atomDecl,
                                             FieldNumberToAtomDeclSet& fieldNumberToAtomDeclSet) {
    for (FieldNumberToAnnotations::const_iterator it = atomDecl->fieldNumberToAnnotations.begin();
         it != atomDecl->fieldNumberToAnnotations.end(); it++) {
        const int fieldNumber = it->first;
        fieldNumberToAtomDeclSet[fieldNumber].insert(atomDecl);
    }
}

static AtomType getAtomType(const FieldDescriptor& atomField) {
    const int atomId = atomField.number();
    if ((atomId >= PLATFORM_PULLED_ATOMS_START && atomId <= PLATFORM_PULLED_ATOMS_END) ||
        (atomId >= VENDOR_PULLED_ATOMS_START && atomId <= VENDOR_PULLED_ATOMS_END)) {
        return ATOM_TYPE_PULLED;
    } else {
        return ATOM_TYPE_PUSHED;
    }
}

static int collate_from_field_descriptor(const FieldDescriptor& atomField, const string& moduleName,
                                         Atoms& atoms) {
    int errorCount = 0;

    if (moduleName != DEFAULT_MODULE_NAME) {
        const int moduleCount = atomField.options().ExtensionSize(os::statsd::module);
        bool moduleFound = false;
        for (int j = 0; j < moduleCount; ++j) {
            const string atomModuleName = atomField.options().GetExtension(os::statsd::module, j);
            if (atomModuleName == moduleName) {
                moduleFound = true;
                break;
            }
        }

        // This atom is not in the module we're interested in; skip it.
        if (!moduleFound) {
            if (dbg) {
                printf("   Skipping %s (%d)\n", atomField.name().c_str(), atomField.number());
            }
            return errorCount;
        }
    }

    if (dbg) {
        printf("   %s (%d)\n", atomField.name().c_str(), atomField.number());
    }

    // StatsEvent only has one oneof, which contains only messages. Don't allow
    // other types.
    if (atomField.type() != FieldDescriptor::TYPE_MESSAGE) {
        print_error(atomField,
                    "Bad type for atom. StatsEvent can only have message type "
                    "fields: %s\n",
                    atomField.name().c_str());
        errorCount++;
        return errorCount;
    }

    const AtomType atomType = getAtomType(atomField);

    const Descriptor& atom = *atomField.message_type();
    const shared_ptr<AtomDecl> atomDecl =
            make_shared<AtomDecl>(atomField.number(), atomField.name(), atom.name(), atomType);

    if (atomField.options().GetExtension(os::statsd::truncate_timestamp)) {
        addAnnotationToAtomDecl(*atomDecl, ATOM_ID_FIELD_NUMBER, ANNOTATION_ID_TRUNCATE_TIMESTAMP,
                                ANNOTATION_TYPE_BOOL, AnnotationValue(true));
        if (dbg) {
            printf("%s can have timestamp truncated\n", atomField.name().c_str());
        }
    }

    if (atomField.options().HasExtension(os::statsd::restriction_category)) {
        if (atomType == ATOM_TYPE_PULLED) {
            print_error(atomField, "Restricted atoms cannot be pulled: '%s'\n",
                        atomField.name().c_str());
            errorCount++;
            return errorCount;
        }
        const int restrictionCategory =
                atomField.options().GetExtension(os::statsd::restriction_category);
        atomDecl->restricted = true;
        addAnnotationToAtomDecl(*atomDecl, ATOM_ID_FIELD_NUMBER, ANNOTATION_ID_RESTRICTION_CATEGORY,
                                ANNOTATION_TYPE_INT, AnnotationValue(restrictionCategory));
    }

    vector<java_type_t> signature;
    errorCount += collate_atom(atom, *atomDecl, signature);
    if (!atomDecl->primaryFields.empty() && atomDecl->exclusiveField == 0) {
        print_error(atomField, "Cannot have a primary field without an exclusive field: %s\n",
                    atomField.name().c_str());
        errorCount++;
        return errorCount;
    }

    FieldNumberToAtomDeclSet& fieldNumberToAtomDeclSet =
            atomType == ATOM_TYPE_PUSHED ? atoms.signatureInfoMap[signature]
                                         : atoms.pulledAtomsSignatureInfoMap[signature];
    populateFieldNumberToAtomDeclSet(atomDecl, fieldNumberToAtomDeclSet);

    atoms.decls.insert(atomDecl);

    const shared_ptr<AtomDecl> nonChainedAtomDecl =
            make_shared<AtomDecl>(atomField.number(), atomField.name(), atom.name(), atomType);
    vector<java_type_t> nonChainedSignature;
    if (get_non_chained_node(atom, *nonChainedAtomDecl, nonChainedSignature)) {
        FieldNumberToAtomDeclSet& nonChainedFieldNumberToAtomDeclSet =
                atoms.nonChainedSignatureInfoMap[nonChainedSignature];
        populateFieldNumberToAtomDeclSet(nonChainedAtomDecl, nonChainedFieldNumberToAtomDeclSet);

        atoms.non_chained_decls.insert(nonChainedAtomDecl);
    }

    if (atomField.options().HasExtension(os::statsd::field_restriction_option)) {
        print_error(atomField, "field_restriction_option must be a field-level annotation: '%s'\n",
                    atomField.name().c_str());
        errorCount++;
    }

    return errorCount;
}

/**
 * Gather the info about the atoms.
 */
int collate_atoms(const Descriptor& descriptor, const string& moduleName, Atoms& atoms) {
    int errorCount = 0;

    // Regular field atoms in Atom
    for (int i = 0; i < descriptor.field_count(); i++) {
        const FieldDescriptor* atomField = descriptor.field(i);
        errorCount += collate_from_field_descriptor(*atomField, moduleName, atoms);
    }

    // Extension field atoms in Atom.
    vector<const FieldDescriptor*> extensions;
    descriptor.file()->pool()->FindAllExtensions(&descriptor, &extensions);
    for (const FieldDescriptor* atomField : extensions) {
        errorCount += collate_from_field_descriptor(*atomField, moduleName, atoms);
    }

    if (dbg) {
        // Signatures for pushed atoms.
        printf("signatures = [\n");
        for (SignatureInfoMap::const_iterator it = atoms.signatureInfoMap.begin();
             it != atoms.signatureInfoMap.end(); it++) {
            printf("   ");
            for (vector<java_type_t>::const_iterator jt = it->first.begin(); jt != it->first.end();
                 jt++) {
                printf(" %d", static_cast<int>(*jt));
            }
            printf("\n");
        }

        // Signatures for pull atoms.
        for (SignatureInfoMap::const_iterator it = atoms.pulledAtomsSignatureInfoMap.begin();
             it != atoms.pulledAtomsSignatureInfoMap.end(); it++) {
            printf("   ");
            for (vector<java_type_t>::const_iterator jt = it->first.begin(); jt != it->first.end();
                 jt++) {
                printf(" %d", static_cast<int>(*jt));
            }
            printf("\n");
        }
        printf("]\n");
    }

    return errorCount;
}

}  // namespace stats_log_api_gen
}  // namespace android
