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

#include <google/protobuf/compiler/importer.h>
#include <gtest/gtest.h>
#include <stdio.h>

#include <filesystem>

#include "Collation.h"
#include "frameworks/proto_logging/stats/stats_log_api_gen/test.pb.h"

namespace android {
namespace stats_log_api_gen {

using std::map;
using std::vector;

namespace fs = std::filesystem;

/**
 * Return whether the map contains a vector of the elements provided.
 */
static bool map_contains_vector(const SignatureInfoMap& s, int count, ...) {
    va_list args;
    vector<java_type_t> v(count);

    va_start(args, count);
    for (int i = 0; i < count; i++) {
        v[i] = static_cast<java_type_t>(va_arg(args, int));
    }
    va_end(args);

    return s.find(v) != s.end();
}

/**
 * Expect that the provided map contains the elements provided.
 */
#define EXPECT_MAP_CONTAINS_SIGNATURE(s, ...)                    \
    do {                                                         \
        int count = sizeof((int[]){__VA_ARGS__}) / sizeof(int);  \
        EXPECT_TRUE(map_contains_vector(s, count, __VA_ARGS__)); \
    } while (0)

/** Expects that the provided atom has no enum values for any field. */
#define EXPECT_NO_ENUM_FIELD(atom)                                           \
    do {                                                                     \
        for (vector<AtomField>::const_iterator field = atom->fields.begin(); \
             field != atom->fields.end(); field++) {                         \
            EXPECT_TRUE(field->enumValues.empty());                          \
        }                                                                    \
    } while (0)

/** Expects that exactly one specific field has expected enum values. */
#define EXPECT_HAS_ENUM_FIELD(atom, field_name, values)                      \
    do {                                                                     \
        for (vector<AtomField>::const_iterator field = atom->fields.begin(); \
             field != atom->fields.end(); field++) {                         \
            if (field->name == field_name) {                                 \
                EXPECT_EQ(field->enumValues, values);                        \
            } else {                                                         \
                EXPECT_TRUE(field->enumValues.empty());                      \
            }                                                                \
        }                                                                    \
    } while (0)

// Setup for test fixture.
class CollationTest : public testing::TestWithParam<bool> {
    class MFErrorCollector : public google::protobuf::compiler::MultiFileErrorCollector {
    public:
        void AddError(const std::string& filename, int line, int column,
                      const std::string& message) override {
            fprintf(stdout, "[Error] %s:%d:%d - %s", filename.c_str(), line, column,
                    message.c_str());
        }
    };

public:
    CollationTest() : mImporter(&mSourceTree, &mErrorCollector) {
        mSourceTree.MapPath("", fs::current_path().c_str());
        mFileDescriptor = mImporter.Import("test_external.proto");
    }

protected:
    void SetUp() override {
        if (GetParam()) {
            mEvent = Event::descriptor();
            mIntAtom = IntAtom::descriptor();
            mBadTypesEvent = BadTypesEvent::descriptor();
            mBadSkippedFieldSingle = BadSkippedFieldSingle::descriptor();
            mBadSkippedFieldMultiple = BadSkippedFieldMultiple::descriptor();
            mBadAttributionNodePosition = BadAttributionNodePosition::descriptor();
            mBadStateAtoms = BadStateAtoms::descriptor();
            mGoodStateAtoms = GoodStateAtoms::descriptor();
            mBadUidAtoms = BadUidAtoms::descriptor();
            mGoodUidAtoms = GoodUidAtoms::descriptor();
            mGoodEventWithBinaryFieldAtom = GoodEventWithBinaryFieldAtom::descriptor();
            mBadEventWithBinaryFieldAtom = BadEventWithBinaryFieldAtom::descriptor();
            mModuleAtoms = ModuleAtoms::descriptor();

            mPushedAndPulledAtoms = PushedAndPulledAtoms::descriptor();
            mVendorAtoms = VendorAtoms::descriptor();
            mGoodRestrictedAtoms = GoodRestrictedAtoms::descriptor();
            mBadRestrictedAtoms1 = BadRestrictedAtoms1::descriptor();
            mBadRestrictedAtoms2 = BadRestrictedAtoms2::descriptor();
            mBadRestrictedAtoms3 = BadRestrictedAtoms3::descriptor();
            mBadRestrictedAtoms4 = BadRestrictedAtoms4::descriptor();
            mBadRestrictedAtoms5 = BadRestrictedAtoms5::descriptor();
        } else {
            mEvent = mFileDescriptor->FindMessageTypeByName("Event");
            mIntAtom = mFileDescriptor->FindMessageTypeByName("IntAtom");
            mBadTypesEvent = mFileDescriptor->FindMessageTypeByName("BadTypesEvent");
            mBadSkippedFieldSingle =
                    mFileDescriptor->FindMessageTypeByName("BadSkippedFieldSingle");
            mBadSkippedFieldMultiple =
                    mFileDescriptor->FindMessageTypeByName("BadSkippedFieldMultiple");
            mBadAttributionNodePosition =
                    mFileDescriptor->FindMessageTypeByName("BadAttributionNodePosition");
            mBadStateAtoms = mFileDescriptor->FindMessageTypeByName("BadStateAtoms");
            mGoodStateAtoms = mFileDescriptor->FindMessageTypeByName("GoodStateAtoms");
            mBadUidAtoms = mFileDescriptor->FindMessageTypeByName("BadUidAtoms");
            mGoodUidAtoms = mFileDescriptor->FindMessageTypeByName("GoodUidAtoms");
            mGoodEventWithBinaryFieldAtom =
                    mFileDescriptor->FindMessageTypeByName("GoodEventWithBinaryFieldAtom");
            mBadEventWithBinaryFieldAtom =
                    mFileDescriptor->FindMessageTypeByName("BadEventWithBinaryFieldAtom");
            mModuleAtoms = mFileDescriptor->FindMessageTypeByName("ModuleAtoms");
            mPushedAndPulledAtoms = mFileDescriptor->FindMessageTypeByName("PushedAndPulledAtoms");
            mVendorAtoms = mFileDescriptor->FindMessageTypeByName("VendorAtoms");
            mGoodRestrictedAtoms = mFileDescriptor->FindMessageTypeByName("GoodRestrictedAtoms");
            mBadRestrictedAtoms1 = mFileDescriptor->FindMessageTypeByName("BadRestrictedAtoms1");
            mBadRestrictedAtoms2 = mFileDescriptor->FindMessageTypeByName("BadRestrictedAtoms2");
            mBadRestrictedAtoms3 = mFileDescriptor->FindMessageTypeByName("BadRestrictedAtoms3");
            mBadRestrictedAtoms4 = mFileDescriptor->FindMessageTypeByName("BadRestrictedAtoms4");
            mBadRestrictedAtoms5 = mFileDescriptor->FindMessageTypeByName("BadRestrictedAtoms5");
        }
    }

    MFErrorCollector mErrorCollector;
    google::protobuf::compiler::DiskSourceTree mSourceTree;
    google::protobuf::compiler::Importer mImporter;
    const google::protobuf::FileDescriptor* mFileDescriptor;

    const Descriptor* mEvent;
    const Descriptor* mIntAtom;
    const Descriptor* mBadTypesEvent;
    const Descriptor* mBadSkippedFieldSingle;
    const Descriptor* mBadSkippedFieldMultiple;
    const Descriptor* mBadAttributionNodePosition;
    const Descriptor* mBadStateAtoms;
    const Descriptor* mGoodStateAtoms;
    const Descriptor* mBadUidAtoms;
    const Descriptor* mGoodUidAtoms;
    const Descriptor* mGoodEventWithBinaryFieldAtom;
    const Descriptor* mBadEventWithBinaryFieldAtom;
    const Descriptor* mModuleAtoms;
    const Descriptor* mPushedAndPulledAtoms;
    const Descriptor* mVendorAtoms;
    const Descriptor* mGoodRestrictedAtoms;
    const Descriptor* mBadRestrictedAtoms1;
    const Descriptor* mBadRestrictedAtoms2;
    const Descriptor* mBadRestrictedAtoms3;
    const Descriptor* mBadRestrictedAtoms4;
    const Descriptor* mBadRestrictedAtoms5;
};

INSTANTIATE_TEST_SUITE_P(ProtoProvider, CollationTest, testing::Values(true, false));

/**
 * Test a correct collation, with all the types.
 */
TEST_P(CollationTest, CollateStats) {
    Atoms atoms;
    const int errorCount = collate_atoms(*mEvent, DEFAULT_MODULE_NAME, atoms);

    EXPECT_EQ(0, errorCount);
    EXPECT_EQ(4ul, atoms.signatureInfoMap.size());

    // IntAtom, AnotherIntAtom
    EXPECT_MAP_CONTAINS_SIGNATURE(atoms.signatureInfoMap, JAVA_TYPE_INT);

    // OutOfOrderAtom
    EXPECT_MAP_CONTAINS_SIGNATURE(atoms.signatureInfoMap, JAVA_TYPE_INT, JAVA_TYPE_INT);

    // AllTypesAtom
    EXPECT_MAP_CONTAINS_SIGNATURE(atoms.signatureInfoMap,
                                  JAVA_TYPE_ATTRIBUTION_CHAIN,  // AttributionChain
                                  JAVA_TYPE_FLOAT,              // float
                                  JAVA_TYPE_LONG,               // int64
                                  JAVA_TYPE_LONG,               // uint64
                                  JAVA_TYPE_INT,                // int32
                                  JAVA_TYPE_BOOLEAN,            // bool
                                  JAVA_TYPE_STRING,             // string
                                  JAVA_TYPE_INT,                // uint32
                                  JAVA_TYPE_INT,                // AnEnum
                                  JAVA_TYPE_FLOAT_ARRAY,        // repeated float
                                  JAVA_TYPE_LONG_ARRAY,         // repeated int64
                                  JAVA_TYPE_INT_ARRAY,          // repeated int32
                                  JAVA_TYPE_BOOLEAN_ARRAY,      // repeated bool
                                  JAVA_TYPE_STRING_ARRAY        // repeated string
    );

    // RepeatedEnumAtom
    EXPECT_MAP_CONTAINS_SIGNATURE(atoms.signatureInfoMap, JAVA_TYPE_INT_ARRAY);

    EXPECT_EQ(5ul, atoms.decls.size());

    AtomDeclSet::const_iterator atomIt = atoms.decls.begin();
    EXPECT_EQ(1, (*atomIt)->code);
    EXPECT_EQ("int_atom", (*atomIt)->name);
    EXPECT_EQ("IntAtom", (*atomIt)->message);
    EXPECT_NO_ENUM_FIELD((*atomIt));
    atomIt++;

    EXPECT_EQ(2, (*atomIt)->code);
    EXPECT_EQ("out_of_order_atom", (*atomIt)->name);
    EXPECT_EQ("OutOfOrderAtom", (*atomIt)->message);
    EXPECT_NO_ENUM_FIELD((*atomIt));
    atomIt++;

    EXPECT_EQ(3, (*atomIt)->code);
    EXPECT_EQ("another_int_atom", (*atomIt)->name);
    EXPECT_EQ("AnotherIntAtom", (*atomIt)->message);
    EXPECT_NO_ENUM_FIELD((*atomIt));
    atomIt++;

    EXPECT_EQ(4, (*atomIt)->code);
    EXPECT_EQ("all_types_atom", (*atomIt)->name);
    EXPECT_EQ("AllTypesAtom", (*atomIt)->message);
    map<int, string> enumValues;
    enumValues[0] = "VALUE0";
    enumValues[1] = "VALUE1";
    EXPECT_HAS_ENUM_FIELD((*atomIt), "enum_field", enumValues);
    atomIt++;

    EXPECT_EQ(5, (*atomIt)->code);
    EXPECT_EQ("repeated_enum_atom", (*atomIt)->name);
    EXPECT_EQ("RepeatedEnumAtom", (*atomIt)->message);
    enumValues[0] = "VALUE0";
    enumValues[1] = "VALUE1";
    EXPECT_HAS_ENUM_FIELD((*atomIt), "repeated_enum_field", enumValues);
    atomIt++;

    EXPECT_EQ(atoms.decls.end(), atomIt);
}

/**
 * Test that event class that contains stuff other than the atoms is rejected.
 */
TEST_P(CollationTest, NonMessageTypeFails) {
    Atoms atoms;
    const int errorCount = collate_atoms(*mIntAtom, DEFAULT_MODULE_NAME, atoms);

    EXPECT_EQ(1, errorCount);
}

/**
 * Test that atoms that have unsupported field types are rejected.
 */
TEST_P(CollationTest, FailOnBadTypes) {
    Atoms atoms;
    const int errorCount = collate_atoms(*mBadTypesEvent, DEFAULT_MODULE_NAME, atoms);

    EXPECT_EQ(20, errorCount);
}

/**
 * Test that atoms that skip field numbers (in the first position) are rejected.
 */
TEST_P(CollationTest, FailOnSkippedFieldsSingle) {
    Atoms atoms;
    const int errorCount = collate_atoms(*mBadSkippedFieldSingle, DEFAULT_MODULE_NAME, atoms);

    EXPECT_EQ(1, errorCount);
}

/**
 * Test that atoms that skip field numbers (not in the first position, and
 * multiple times) are rejected.
 */
TEST_P(CollationTest, FailOnSkippedFieldsMultiple) {
    Atoms atoms;
    const int errorCount = collate_atoms(*mBadSkippedFieldMultiple, DEFAULT_MODULE_NAME, atoms);

    EXPECT_EQ(2, errorCount);
}

/**
 * Test that atoms that have an attribution chain not in the first position are
 * rejected.
 */
TEST_P(CollationTest, FailBadAttributionNodePosition) {
    Atoms atoms;
    const int errorCount = collate_atoms(*mBadAttributionNodePosition, DEFAULT_MODULE_NAME, atoms);

    EXPECT_EQ(1, errorCount);
}

TEST_P(CollationTest, FailOnBadStateAtomOptions) {
    Atoms atoms;
    const int errorCount = collate_atoms(*mBadStateAtoms, DEFAULT_MODULE_NAME, atoms);

    EXPECT_EQ(4, errorCount);
}

TEST_P(CollationTest, PassOnGoodStateAtomOptions) {
    Atoms atoms;
    const int errorCount = collate_atoms(*mGoodStateAtoms, DEFAULT_MODULE_NAME, atoms);
    EXPECT_EQ(0, errorCount);
}

TEST_P(CollationTest, FailOnBadUidAtomOptions) {
    Atoms atoms;
    const int errorCount = collate_atoms(*mBadUidAtoms, DEFAULT_MODULE_NAME, atoms);

    EXPECT_EQ(2, errorCount);
}

TEST_P(CollationTest, PassOnGoodUidAtomOptions) {
    Atoms atoms;
    const int errorCount = collate_atoms(*mGoodUidAtoms, DEFAULT_MODULE_NAME, atoms);
    EXPECT_EQ(0, errorCount);
}

TEST_P(CollationTest, PassOnGoodBinaryFieldAtom) {
    Atoms atoms;
    const int errorCount =
            collate_atoms(*mGoodEventWithBinaryFieldAtom, DEFAULT_MODULE_NAME, atoms);
    EXPECT_EQ(0, errorCount);
}

TEST_P(CollationTest, FailOnBadBinaryFieldAtom) {
    Atoms atoms;
    const int errorCount = collate_atoms(*mBadEventWithBinaryFieldAtom, DEFAULT_MODULE_NAME, atoms);
    EXPECT_GT(errorCount, 0);
}

TEST_P(CollationTest, PassOnLogFromModuleAtom) {
    Atoms atoms;
    const int errorCount = collate_atoms(*mModuleAtoms, DEFAULT_MODULE_NAME, atoms);
    EXPECT_EQ(errorCount, 0);
    EXPECT_EQ(atoms.decls.size(), 4ul);
}

TEST_P(CollationTest, RecognizeModuleAtom) {
    Atoms atoms;
    const int errorCount = collate_atoms(*mModuleAtoms, DEFAULT_MODULE_NAME, atoms);
    EXPECT_EQ(errorCount, 0);
    EXPECT_EQ(atoms.decls.size(), 4ul);
    EXPECT_EQ(atoms.signatureInfoMap.size(), 2u);
    EXPECT_MAP_CONTAINS_SIGNATURE(atoms.signatureInfoMap, JAVA_TYPE_INT);
    EXPECT_MAP_CONTAINS_SIGNATURE(atoms.signatureInfoMap, JAVA_TYPE_STRING);

    SignatureInfoMap::const_iterator signatureInfoMapIt;
    const vector<java_type_t>* signature;
    const FieldNumberToAtomDeclSet* fieldNumberToAtomDeclSet;
    FieldNumberToAtomDeclSet::const_iterator fieldNumberToAtomDeclSetIt;
    const AtomDeclSet* atomDeclSet;
    AtomDeclSet::const_iterator atomDeclSetIt;
    AtomDecl* atomDecl;
    FieldNumberToAnnotations* fieldNumberToAnnotations;
    FieldNumberToAnnotations::const_iterator fieldNumberToAnnotationsIt;
    const AnnotationSet* annotationSet;
    AnnotationSet::const_iterator annotationSetIt;
    Annotation* annotation;

    signatureInfoMapIt = atoms.signatureInfoMap.begin();
    signature = &(signatureInfoMapIt->first);
    fieldNumberToAtomDeclSet = &signatureInfoMapIt->second;
    EXPECT_EQ(1ul, signature->size());
    EXPECT_EQ(JAVA_TYPE_INT, signature->at(0));
    EXPECT_EQ(1ul, fieldNumberToAtomDeclSet->size());
    fieldNumberToAtomDeclSetIt = fieldNumberToAtomDeclSet->begin();
    EXPECT_EQ(1, fieldNumberToAtomDeclSetIt->first);
    atomDeclSet = &fieldNumberToAtomDeclSetIt->second;
    EXPECT_EQ(2ul, atomDeclSet->size());
    atomDeclSetIt = atomDeclSet->begin();
    atomDecl = atomDeclSetIt->get();
    EXPECT_EQ(1, atomDecl->code);
    fieldNumberToAnnotations = &atomDecl->fieldNumberToAnnotations;
    fieldNumberToAnnotationsIt = fieldNumberToAnnotations->find(1);
    EXPECT_NE(fieldNumberToAnnotations->end(), fieldNumberToAnnotationsIt);
    annotationSet = &fieldNumberToAnnotationsIt->second;
    EXPECT_EQ(1ul, annotationSet->size());
    annotationSetIt = annotationSet->begin();
    annotation = annotationSetIt->get();
    EXPECT_EQ(ANNOTATION_ID_IS_UID, annotation->annotationId);
    EXPECT_EQ(1, annotation->atomId);
    EXPECT_EQ(ANNOTATION_TYPE_BOOL, annotation->type);
    EXPECT_TRUE(annotation->value.boolValue);

    atomDeclSetIt++;
    atomDecl = atomDeclSetIt->get();
    EXPECT_EQ(3, atomDecl->code);
    fieldNumberToAnnotations = &atomDecl->fieldNumberToAnnotations;
    fieldNumberToAnnotationsIt = fieldNumberToAnnotations->find(1);
    EXPECT_NE(fieldNumberToAnnotations->end(), fieldNumberToAnnotationsIt);
    annotationSet = &fieldNumberToAnnotationsIt->second;
    EXPECT_EQ(1ul, annotationSet->size());
    annotationSetIt = annotationSet->begin();
    annotation = annotationSetIt->get();
    EXPECT_EQ(ANNOTATION_ID_EXCLUSIVE_STATE, annotation->annotationId);
    EXPECT_EQ(3, annotation->atomId);
    EXPECT_EQ(ANNOTATION_TYPE_BOOL, annotation->type);
    EXPECT_TRUE(annotation->value.boolValue);

    signatureInfoMapIt++;
    signature = &signatureInfoMapIt->first;
    fieldNumberToAtomDeclSet = &signatureInfoMapIt->second;
    EXPECT_EQ(1ul, signature->size());
    EXPECT_EQ(JAVA_TYPE_STRING, signature->at(0));
    EXPECT_EQ(0ul, fieldNumberToAtomDeclSet->size());
}

TEST_P(CollationTest, RecognizeModule1Atom) {
    Atoms atoms;
    const string moduleName = "module1";
    const int errorCount = collate_atoms(*mModuleAtoms, moduleName, atoms);
    EXPECT_EQ(errorCount, 0);
    EXPECT_EQ(atoms.decls.size(), 2ul);
    EXPECT_EQ(atoms.signatureInfoMap.size(), 1u);
    EXPECT_MAP_CONTAINS_SIGNATURE(atoms.signatureInfoMap, JAVA_TYPE_INT);

    SignatureInfoMap::const_iterator signatureInfoMapIt;
    const vector<java_type_t>* signature;
    const FieldNumberToAtomDeclSet* fieldNumberToAtomDeclSet;
    FieldNumberToAtomDeclSet::const_iterator fieldNumberToAtomDeclSetIt;
    const AtomDeclSet* atomDeclSet;
    AtomDeclSet::const_iterator atomDeclSetIt;
    AtomDecl* atomDecl;
    FieldNumberToAnnotations* fieldNumberToAnnotations;
    FieldNumberToAnnotations::const_iterator fieldNumberToAnnotationsIt;
    const AnnotationSet* annotationSet;
    AnnotationSet::const_iterator annotationSetIt;
    Annotation* annotation;

    signatureInfoMapIt = atoms.signatureInfoMap.begin();
    signature = &(signatureInfoMapIt->first);
    fieldNumberToAtomDeclSet = &signatureInfoMapIt->second;
    EXPECT_EQ(1ul, signature->size());
    EXPECT_EQ(JAVA_TYPE_INT, signature->at(0));
    EXPECT_EQ(1ul, fieldNumberToAtomDeclSet->size());
    fieldNumberToAtomDeclSetIt = fieldNumberToAtomDeclSet->begin();
    EXPECT_EQ(1, fieldNumberToAtomDeclSetIt->first);
    atomDeclSet = &fieldNumberToAtomDeclSetIt->second;
    EXPECT_EQ(2ul, atomDeclSet->size());
    atomDeclSetIt = atomDeclSet->begin();
    atomDecl = atomDeclSetIt->get();
    EXPECT_EQ(1, atomDecl->code);
    fieldNumberToAnnotations = &atomDecl->fieldNumberToAnnotations;
    fieldNumberToAnnotationsIt = fieldNumberToAnnotations->find(1);
    EXPECT_NE(fieldNumberToAnnotations->end(), fieldNumberToAnnotationsIt);
    annotationSet = &fieldNumberToAnnotationsIt->second;
    EXPECT_EQ(1ul, annotationSet->size());
    annotationSetIt = annotationSet->begin();
    annotation = annotationSetIt->get();
    EXPECT_EQ(ANNOTATION_ID_IS_UID, annotation->annotationId);
    EXPECT_EQ(1, annotation->atomId);
    EXPECT_EQ(ANNOTATION_TYPE_BOOL, annotation->type);
    EXPECT_TRUE(annotation->value.boolValue);

    atomDeclSetIt++;
    atomDecl = atomDeclSetIt->get();
    EXPECT_EQ(3, atomDecl->code);
    fieldNumberToAnnotations = &atomDecl->fieldNumberToAnnotations;
    fieldNumberToAnnotationsIt = fieldNumberToAnnotations->find(1);
    EXPECT_NE(fieldNumberToAnnotations->end(), fieldNumberToAnnotationsIt);
    annotationSet = &fieldNumberToAnnotationsIt->second;
    EXPECT_EQ(1ul, annotationSet->size());
    annotationSetIt = annotationSet->begin();
    annotation = annotationSetIt->get();
    EXPECT_EQ(ANNOTATION_ID_EXCLUSIVE_STATE, annotation->annotationId);
    EXPECT_EQ(3, annotation->atomId);
    EXPECT_EQ(ANNOTATION_TYPE_BOOL, annotation->type);
    EXPECT_TRUE(annotation->value.boolValue);
}

/**
 * Test a correct collation with pushed and pulled atoms.
 */
TEST_P(CollationTest, CollatePushedAndPulledAtoms) {
    Atoms atoms;
    const int errorCount = collate_atoms(*mPushedAndPulledAtoms, DEFAULT_MODULE_NAME, atoms);

    EXPECT_EQ(0, errorCount);
    EXPECT_EQ(1ul, atoms.signatureInfoMap.size());
    EXPECT_EQ(2ul, atoms.pulledAtomsSignatureInfoMap.size());

    // IntAtom
    EXPECT_MAP_CONTAINS_SIGNATURE(atoms.signatureInfoMap, JAVA_TYPE_INT);

    // AnotherIntAtom
    EXPECT_MAP_CONTAINS_SIGNATURE(atoms.pulledAtomsSignatureInfoMap, JAVA_TYPE_INT);

    // OutOfOrderAtom
    EXPECT_MAP_CONTAINS_SIGNATURE(atoms.pulledAtomsSignatureInfoMap, JAVA_TYPE_INT, JAVA_TYPE_INT);

    EXPECT_EQ(3ul, atoms.decls.size());

    AtomDeclSet::const_iterator atomIt = atoms.decls.begin();
    EXPECT_EQ(1, (*atomIt)->code);
    EXPECT_EQ("int_atom_1", (*atomIt)->name);
    EXPECT_EQ("IntAtom", (*atomIt)->message);
    EXPECT_NO_ENUM_FIELD((*atomIt));
    atomIt++;

    EXPECT_EQ(10000, (*atomIt)->code);
    EXPECT_EQ("another_int_atom", (*atomIt)->name);
    EXPECT_EQ("AnotherIntAtom", (*atomIt)->message);
    EXPECT_NO_ENUM_FIELD((*atomIt));
    atomIt++;

    EXPECT_EQ(99999, (*atomIt)->code);
    EXPECT_EQ("out_of_order_atom", (*atomIt)->name);
    EXPECT_EQ("OutOfOrderAtom", (*atomIt)->message);
    EXPECT_NO_ENUM_FIELD((*atomIt));
    atomIt++;

    EXPECT_EQ(atoms.decls.end(), atomIt);
}

TEST_P(CollationTest, CollateVendorAtoms) {
    Atoms atoms;
    const int errorCount = collate_atoms(*mVendorAtoms, DEFAULT_MODULE_NAME, atoms);

    EXPECT_EQ(0, errorCount);
    EXPECT_EQ(1ul, atoms.signatureInfoMap.size());
    EXPECT_EQ(1ul, atoms.pulledAtomsSignatureInfoMap.size());

    // IntAtom
    EXPECT_MAP_CONTAINS_SIGNATURE(atoms.signatureInfoMap, JAVA_TYPE_INT);

    // AnotherIntAtom
    EXPECT_MAP_CONTAINS_SIGNATURE(atoms.pulledAtomsSignatureInfoMap, JAVA_TYPE_INT);

    EXPECT_EQ(2ul, atoms.decls.size());

    AtomDeclSet::const_iterator atomIt = atoms.decls.begin();
    EXPECT_EQ(100000, (*atomIt)->code);
    EXPECT_EQ("pushed_atom_100000", (*atomIt)->name);
    EXPECT_EQ("IntAtom", (*atomIt)->message);
    EXPECT_NO_ENUM_FIELD((*atomIt));
    atomIt++;

    EXPECT_EQ(199999, (*atomIt)->code);
    EXPECT_EQ("pulled_atom_199999", (*atomIt)->name);
    EXPECT_EQ("AnotherIntAtom", (*atomIt)->message);
    EXPECT_NO_ENUM_FIELD((*atomIt));
    atomIt++;

    EXPECT_EQ(atoms.decls.end(), atomIt);
}

TEST(CollationTest, CollateExtensionAtoms) {
    Atoms atoms;
    const int errorCount = collate_atoms(*ExtensionAtoms::descriptor(), "test_feature", atoms);

    EXPECT_EQ(0, errorCount);
    EXPECT_EQ(1ul, atoms.signatureInfoMap.size());
    EXPECT_EQ(1ul, atoms.pulledAtomsSignatureInfoMap.size());

    // ExtensionAtomPushed
    EXPECT_MAP_CONTAINS_SIGNATURE(atoms.signatureInfoMap, JAVA_TYPE_INT, JAVA_TYPE_LONG);

    // ExtensionAtomPulled
    EXPECT_MAP_CONTAINS_SIGNATURE(atoms.pulledAtomsSignatureInfoMap, JAVA_TYPE_LONG);

    EXPECT_EQ(2ul, atoms.decls.size());

    AtomDeclSet::const_iterator atomIt = atoms.decls.begin();
    EXPECT_EQ(9999, (*atomIt)->code);
    EXPECT_EQ("extension_atom_pushed", (*atomIt)->name);
    EXPECT_EQ("ExtensionAtomPushed", (*atomIt)->message);
    EXPECT_NO_ENUM_FIELD((*atomIt));
    FieldNumberToAnnotations* fieldNumberToAnnotations = &(*atomIt)->fieldNumberToAnnotations;
    FieldNumberToAnnotations::const_iterator fieldNumberToAnnotationsIt =
            fieldNumberToAnnotations->find(1);
    EXPECT_NE(fieldNumberToAnnotations->end(), fieldNumberToAnnotationsIt);
    const AnnotationSet* annotationSet = &fieldNumberToAnnotationsIt->second;
    EXPECT_EQ(1ul, annotationSet->size());
    Annotation* annotation = annotationSet->begin()->get();
    EXPECT_EQ(ANNOTATION_ID_IS_UID, annotation->annotationId);
    EXPECT_EQ(9999, annotation->atomId);
    EXPECT_EQ(ANNOTATION_TYPE_BOOL, annotation->type);
    EXPECT_TRUE(annotation->value.boolValue);
    atomIt++;

    EXPECT_EQ(99999, (*atomIt)->code);
    EXPECT_EQ("extension_atom_pulled", (*atomIt)->name);
    EXPECT_EQ("ExtensionAtomPulled", (*atomIt)->message);
    EXPECT_NO_ENUM_FIELD((*atomIt));
    atomIt++;

    EXPECT_EQ(atoms.decls.end(), atomIt);
}

TEST_P(CollationTest, CollateGoodRestrictedAtoms) {
    Atoms atoms;
    const int errorCount = collate_atoms(*mGoodRestrictedAtoms, DEFAULT_MODULE_NAME, atoms);

    EXPECT_EQ(0, errorCount);
    ASSERT_EQ(1ul, atoms.signatureInfoMap.size());
    ASSERT_EQ(0ul, atoms.pulledAtomsSignatureInfoMap.size());

    EXPECT_MAP_CONTAINS_SIGNATURE(atoms.signatureInfoMap, JAVA_TYPE_LONG,
                                                          JAVA_TYPE_LONG,
                                                          JAVA_TYPE_INT,
                                                          JAVA_TYPE_BOOLEAN,
                                                          JAVA_TYPE_STRING,
                                                          JAVA_TYPE_INT,
                                                          JAVA_TYPE_INT,
                                                          JAVA_TYPE_FLOAT,
                                                          JAVA_TYPE_INT);

    // Validate signatureInfoMap
    FieldNumberToAtomDeclSet fieldNumberToAtomDeclSet = atoms.signatureInfoMap.begin()->second;
    ASSERT_EQ(10ul, fieldNumberToAtomDeclSet.size());
    const AtomDeclSet* atomDeclSet = &fieldNumberToAtomDeclSet[ATOM_ID_FIELD_NUMBER];
    ASSERT_EQ(2ul, atomDeclSet->size());
    AtomDeclSet::const_iterator atomDeclSetIt = atomDeclSet->begin();

    const AtomDecl* atomDecl = atomDeclSetIt->get();
    EXPECT_EQ(1, atomDecl->code);
    EXPECT_EQ("pushed_atom_1", atomDecl->name);
    EXPECT_EQ("GoodRestrictedAtom", atomDecl->message);
    FieldNumberToAnnotations fieldNumberToAnnotations = atomDecl->fieldNumberToAnnotations;
    ASSERT_EQ(10ul, fieldNumberToAnnotations.size());

    const AnnotationSet* annotationSet = &fieldNumberToAnnotations[ATOM_ID_FIELD_NUMBER];
    ASSERT_EQ(1ul, annotationSet->size());
    Annotation* annotation = annotationSet->begin()->get();
    EXPECT_EQ(ANNOTATION_ID_RESTRICTION_CATEGORY, annotation->annotationId);
    EXPECT_EQ(ANNOTATION_TYPE_INT, annotation->type);
    EXPECT_EQ(os::statsd::RESTRICTION_DIAGNOSTIC, annotation->value.intValue);

    annotationSet = &fieldNumberToAnnotations[1];
    ASSERT_EQ(1ul, annotationSet->size());
    annotation = annotationSet->begin()->get();
    EXPECT_EQ(ANNOTATION_ID_FIELD_RESTRICTION_APP_USAGE, annotation->annotationId);
    EXPECT_EQ(ANNOTATION_TYPE_BOOL, annotation->type);
    EXPECT_TRUE(annotation->value.boolValue);

    annotationSet = &fieldNumberToAnnotations[2];
    ASSERT_EQ(1ul, annotationSet->size());
    annotation = annotationSet->begin()->get();
    EXPECT_EQ(ANNOTATION_ID_FIELD_RESTRICTION_APP_ACTIVITY, annotation->annotationId);
    EXPECT_EQ(ANNOTATION_TYPE_BOOL, annotation->type);
    EXPECT_TRUE(annotation->value.boolValue);

    annotationSet = &fieldNumberToAnnotations[3];
    ASSERT_EQ(1ul, annotationSet->size());
    annotation = annotationSet->begin()->get();
    EXPECT_EQ(ANNOTATION_ID_FIELD_RESTRICTION_HEALTH_CONNECT, annotation->annotationId);
    EXPECT_EQ(ANNOTATION_TYPE_BOOL, annotation->type);
    EXPECT_TRUE(annotation->value.boolValue);

    annotationSet = &fieldNumberToAnnotations[4];
    ASSERT_EQ(1ul, annotationSet->size());
    annotation = annotationSet->begin()->get();
    EXPECT_EQ(ANNOTATION_ID_FIELD_RESTRICTION_ACCESSIBILITY, annotation->annotationId);
    EXPECT_EQ(ANNOTATION_TYPE_BOOL, annotation->type);
    EXPECT_TRUE(annotation->value.boolValue);

    annotationSet = &fieldNumberToAnnotations[5];
    ASSERT_EQ(1ul, annotationSet->size());
    annotation = annotationSet->begin()->get();
    EXPECT_EQ(ANNOTATION_ID_FIELD_RESTRICTION_SYSTEM_SEARCH, annotation->annotationId);
    EXPECT_EQ(ANNOTATION_TYPE_BOOL, annotation->type);
    EXPECT_TRUE(annotation->value.boolValue);

    annotationSet = &fieldNumberToAnnotations[6];
    ASSERT_EQ(1ul, annotationSet->size());
    annotation = annotationSet->begin()->get();
    EXPECT_EQ(ANNOTATION_ID_FIELD_RESTRICTION_AMBIENT_SENSING, annotation->annotationId);
    EXPECT_EQ(ANNOTATION_TYPE_BOOL, annotation->type);
    EXPECT_TRUE(annotation->value.boolValue);

    annotationSet = &fieldNumberToAnnotations[7];
    ASSERT_EQ(1ul, annotationSet->size());
    annotation = annotationSet->begin()->get();
    EXPECT_EQ(ANNOTATION_ID_FIELD_RESTRICTION_USER_ENGAGEMENT, annotation->annotationId);
    EXPECT_EQ(ANNOTATION_TYPE_BOOL, annotation->type);
    EXPECT_TRUE(annotation->value.boolValue);

    annotationSet = &fieldNumberToAnnotations[8];
    ASSERT_EQ(1ul, annotationSet->size());
    annotation = annotationSet->begin()->get();
    EXPECT_EQ(ANNOTATION_ID_FIELD_RESTRICTION_PERIPHERAL_DEVICE_INFO, annotation->annotationId);
    EXPECT_EQ(ANNOTATION_TYPE_BOOL, annotation->type);
    EXPECT_TRUE(annotation->value.boolValue);

    annotationSet = &fieldNumberToAnnotations[9];
    ASSERT_EQ(1ul, annotationSet->size());
    annotation = annotationSet->begin()->get();
    EXPECT_EQ(ANNOTATION_ID_FIELD_RESTRICTION_DEMOGRAPHIC_CLASSIFICATION, annotation->annotationId);
    EXPECT_EQ(ANNOTATION_TYPE_BOOL, annotation->type);
    EXPECT_TRUE(annotation->value.boolValue);
    atomDeclSetIt++;

    atomDecl = atomDeclSetIt->get();
    EXPECT_EQ(2, atomDecl->code);
    EXPECT_EQ("pushed_atom_2", atomDecl->name);
    EXPECT_EQ("GoodRestrictedAtom", atomDecl->message);
    fieldNumberToAnnotations = atomDecl->fieldNumberToAnnotations;
    ASSERT_EQ(10ul, fieldNumberToAnnotations.size());
    annotationSet = &fieldNumberToAnnotations[ATOM_ID_FIELD_NUMBER];
    ASSERT_EQ(1ul, annotationSet->size());
    annotation = annotationSet->begin()->get();
    EXPECT_EQ(ANNOTATION_ID_RESTRICTION_CATEGORY, annotation->annotationId);
    EXPECT_EQ(ANNOTATION_TYPE_INT, annotation->type);
    EXPECT_EQ(os::statsd::RESTRICTION_SYSTEM_INTELLIGENCE, annotation->value.intValue);
    atomDeclSetIt++;
    EXPECT_EQ(atomDeclSet->end(), atomDeclSetIt);

    // Validate decls
    ASSERT_EQ(2ul, atoms.decls.size());
    AtomDeclSet::const_iterator atomIt = atoms.decls.begin();

    EXPECT_EQ(1, (*atomIt)->code);
    EXPECT_EQ("pushed_atom_1", (*atomIt)->name);
    EXPECT_EQ("GoodRestrictedAtom", (*atomIt)->message);
    fieldNumberToAnnotations = (*atomIt)->fieldNumberToAnnotations;
    ASSERT_EQ(10ul, fieldNumberToAnnotations.size());

    annotationSet = &fieldNumberToAnnotations[ATOM_ID_FIELD_NUMBER];
    ASSERT_EQ(1ul, annotationSet->size());
    annotation = annotationSet->begin()->get();
    EXPECT_EQ(ANNOTATION_ID_RESTRICTION_CATEGORY, annotation->annotationId);
    EXPECT_EQ(ANNOTATION_TYPE_INT, annotation->type);
    EXPECT_EQ(os::statsd::RESTRICTION_DIAGNOSTIC, annotation->value.intValue);
    atomIt++;

    EXPECT_EQ(2, (*atomIt)->code);
    EXPECT_EQ("pushed_atom_2", (*atomIt)->name);
    EXPECT_EQ("GoodRestrictedAtom", (*atomIt)->message);
    fieldNumberToAnnotations = (*atomIt)->fieldNumberToAnnotations;
    ASSERT_EQ(10ul, fieldNumberToAnnotations.size());
    annotationSet = &fieldNumberToAnnotations[ATOM_ID_FIELD_NUMBER];
    ASSERT_EQ(1ul, annotationSet->size());
    annotation = annotationSet->begin()->get();
    EXPECT_EQ(ANNOTATION_ID_RESTRICTION_CATEGORY, annotation->annotationId);
    EXPECT_EQ(ANNOTATION_TYPE_INT, annotation->type);
    EXPECT_EQ(os::statsd::RESTRICTION_SYSTEM_INTELLIGENCE, annotation->value.intValue);
    atomIt++;
    EXPECT_EQ(atoms.decls.end(), atomIt);

    // Validate non_chained_decls
    ASSERT_EQ(0ul, atoms.non_chained_decls.size());

    // Validate nonChainedSignatureInfoMap
    ASSERT_EQ(0ul, atoms.nonChainedSignatureInfoMap.size());
}

TEST_P(CollationTest, CollateBadRestrictedAtoms) {
    Atoms atoms;
    // Nonprimitive fields
    int errorCount = collate_atoms(*mBadRestrictedAtoms1, DEFAULT_MODULE_NAME, atoms);
    EXPECT_EQ(6, errorCount);

    // Restriction category on atom field
    errorCount = collate_atoms(*mBadRestrictedAtoms2, DEFAULT_MODULE_NAME, atoms);
    EXPECT_EQ(1, errorCount);

    // Field restriction without restriction category
    errorCount = collate_atoms(*mBadRestrictedAtoms3, DEFAULT_MODULE_NAME, atoms);
    EXPECT_EQ(9, errorCount);

    // Field restriction option on top level atom field
    errorCount = collate_atoms(*mBadRestrictedAtoms4, DEFAULT_MODULE_NAME, atoms);
    EXPECT_EQ(1, errorCount);

    // Pulled restricted atoms
    errorCount = collate_atoms(*mBadRestrictedAtoms5, DEFAULT_MODULE_NAME, atoms);
    EXPECT_EQ(2, errorCount);
}

}  // namespace stats_log_api_gen
}  // namespace android
