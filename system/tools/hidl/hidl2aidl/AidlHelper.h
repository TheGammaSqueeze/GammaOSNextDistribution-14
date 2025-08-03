/*
 * Copyright (C) 2019 The Android Open Source Project
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

#pragma once

#include <map>
#include <set>
#include <string>
#include <vector>
#include "Reference.h"

namespace android {

struct CompoundType;
struct Coordinator;
struct Formatter;
struct FQName;
struct Interface;
struct Method;
struct NamedType;
struct Scope;
struct Type;

struct FieldWithVersion {
    const NamedReference<Type>* field;
    // name of the field appended by the
    std::string fullName;
    std::pair<size_t, size_t> version;
};

struct ProcessedCompoundType {
    // map modified name to field. This modified name is old.new
    std::vector<FieldWithVersion> fields;
    std::set<const NamedType*> subTypes;
};

struct ReplacedTypeInfo {
    // if a HIDL type is replaced, this returns the new AIDL type
    // android.hardware.safe_enum@1.0::Monostate -> boolean
    std::string aidlReplacedType;
    // if a HIDL type is replaced, this is the FQName of the new AIDL type
    // android.hardware.safe_enum@1.0::Monostate -> std::nullopt
    std::optional<std::string> aidlReplacedFQName;
    // if a HIDL type is replaced, this returns the function needed to generate translation
    std::optional<std::function<void(Formatter&)>> translateField;
};

enum class AidlBackend { UNKNOWN, NDK, CPP, JAVA };

struct AidlHelper {
    /* FQName helpers */
    // getAidlName returns the type names
    // backend == UNKNOWN
    // android.hardware.foo@1.0::IBar::Baz -> Baz
    // backend == CPP || backend == NDK
    // android.hardware.foo@1.0::IBar::Baz -> IBar::Baz
    // backend == JAVA
    // android.hardware.foo@1.0::IBar::Baz -> IBar.Baz
    static std::string getAidlName(const FQName& fqName,
                                   AidlBackend backend = AidlBackend::UNKNOWN);

    // getAidlPackage returns the AIDL package
    // android.hardware.foo@1.x -> android.hardware.foo
    // android.hardware.foo@2.x -> android.hardware.foo2
    static std::string getAidlPackage(const FQName& fqName);
    // returns getAidlPackage(fqName) with '.' replaced by '/'
    // android.hardware.foo@1.x -> android/hardware/foo
    static std::string getAidlPackagePath(const FQName& fqName);

    // getAidlFQName = getAidlPackage + "." + getAidlName
    static std::optional<std::string> getAidlFQName(const FQName& fqName);

    // if a HIDL type is replaced, this returns the ReplacedTypeInfo for the new AIDL type
    static std::optional<const ReplacedTypeInfo> getAidlReplacedType(const FQName& fqName);

    static void emitFileHeader(
            Formatter& out, const NamedType& type,
            const std::map<const NamedType*, const ProcessedCompoundType>& processedTypes);
    static Formatter getFileWithHeader(
            const NamedType& namedType, const Coordinator& coordinator,
            const std::map<const NamedType*, const ProcessedCompoundType>& processedTypes);

    /* Methods for Type */
    static std::string getAidlType(const Type& type, const FQName& relativeTo,
                                   AidlBackend backend = AidlBackend::UNKNOWN);

    /* Methods for NamedType */
    static void emitAidl(
            const NamedType& namedType, Formatter& out,
            const std::map<const NamedType*, const ProcessedCompoundType>& processedTypes);

    // Walk through all of the type's parents until we reach the top and return.
    static const NamedType* getTopLevelType(const NamedType* type);

    /* Methods for Interface */
    static void emitAidl(const Interface& interface, Formatter& out,
                         const std::map<const NamedType*, const ProcessedCompoundType>&);
    // Returns all methods that would exist in an AIDL equivalent interface
    static std::vector<const Method*> getUserDefinedMethods(Formatter& out,
                                                            const Interface& interface);

    static void processCompoundType(const CompoundType& compoundType,
                                    ProcessedCompoundType* processedType,
                                    const std::string& fieldNamePrefix);

    static Formatter& notes();
    static void setNotes(Formatter* formatter);

    // return the full file names for the header/source files based on the backend
    static std::string translateHeaderFile(const FQName& fqName, AidlBackend backend);
    static std::string translateSourceFile(const FQName& fqName, AidlBackend backend);

    static void emitTranslation(
            const Coordinator& coordinator, const FQName& fqName,
            const std::set<const NamedType*>& namedTypesInPackage,
            const std::map<const NamedType*, const ProcessedCompoundType>& processedTypes);
    static void setFileHeader(const std::string& file);
    static void emitFileHeader(Formatter& out);
    static void setExpandExtended(bool expand) { expandExtended = expand; };
    static bool isExpandExtended() { return expandExtended; };
    static bool shouldBeExpanded(const FQName& source, const FQName& extended);

  private:
    // This is the formatter to use for additional conversion output
    static Formatter* notesFormatter;
    static std::string fileHeader;
    static bool expandExtended;
};

}  // namespace android
