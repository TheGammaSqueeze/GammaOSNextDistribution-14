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

#include <android-base/logging.h>
#include <android-base/strings.h>
#include <hidl-util/FQName.h>
#include <hidl-util/Formatter.h>

#include <algorithm>
#include <iostream>
#include <vector>

#include "AST.h"
#include "AidlHelper.h"
#include "CompoundType.h"
#include "Coordinator.h"
#include "DocComment.h"
#include "Interface.h"

using namespace android;

static void usage(const char* me) {
    Formatter out(stderr);

    out << "Usage: " << me << " [-fh] [-o <output path>] [-l <header file>] ";
    Coordinator::emitOptionsUsageString(out);
    out << " FQNAME\n\n";

    out << "Converts FQNAME, PACKAGE(.SUBPACKAGE)*@[0-9]+.[0-9]+(::TYPE)? to an aidl "
           "equivalent.\n\n";

    out.indent();
    out.indent();

    out << "-f: Force hidl2aidl to convert older packages\n";
    out << "-e: Used for expanding extensions and types from other packages\n";
    out << "-h: Prints this menu.\n";
    out << "-o <output path>: Location to output files.\n";
    out << "-l <header file>: File containing a header to prepend to generated files.\n";
    Coordinator::emitOptionsDetailString(out);

    out.unindent();
    out.unindent();
}

static const FQName& getNewerFQName(const FQName& lhs, const FQName& rhs) {
    CHECK(lhs.package() == rhs.package());
    CHECK(lhs.name() == rhs.name());

    if (lhs.getPackageMajorVersion() > rhs.getPackageMajorVersion()) return lhs;
    if (lhs.getPackageMajorVersion() < rhs.getPackageMajorVersion()) return rhs;

    if (lhs.getPackageMinorVersion() > rhs.getPackageMinorVersion()) return lhs;
    return rhs;
}

// If similar FQName is not found, the same one is returned
static FQName getLatestMinorVersionFQNameFromList(const FQName& fqName,
                                                  const std::vector<FQName>& list) {
    FQName currentCandidate = fqName;
    bool found = false;
    for (const FQName& current : list) {
        if (current.package() == currentCandidate.package() &&
            current.name() == currentCandidate.name() &&
            current.getPackageMajorVersion() == currentCandidate.getPackageMajorVersion()) {
            // Prioritize elements in the list over the provided fqName
            currentCandidate = found ? getNewerFQName(current, currentCandidate) : current;
            found = true;
        }
    }

    return currentCandidate;
}

static FQName getLatestMinorVersionNamedTypeFromList(const FQName& fqName,
                                                     const std::set<const NamedType*>& list) {
    FQName currentCandidate = fqName;
    bool found = false;
    for (const NamedType* currentNamedType : list) {
        const FQName& current = currentNamedType->fqName();
        if (current.package() == currentCandidate.package() &&
            current.name() == currentCandidate.name() &&
            current.getPackageMajorVersion() == currentCandidate.getPackageMajorVersion()) {
            // Prioritize elements in the list over the provided fqName
            currentCandidate = found ? getNewerFQName(current, currentCandidate) : current;
            found = true;
        }
    }

    return currentCandidate;
}

static bool packageExists(const Coordinator& coordinator, const FQName& fqName) {
    bool result;
    status_t err = coordinator.packageExists(fqName, &result);
    if (err != OK) {
        std::cerr << "Error trying to find package " << fqName.string() << std::endl;
        exit(1);
    }

    return result;
}

// assuming fqName exists, find oldest version which does exist
// e.g. android.hardware.foo@1.7 -> android.hardware.foo@1.1 (if foo@1.0 doesn't
// exist)
static FQName getLowestExistingFqName(const Coordinator& coordinator, const FQName& fqName) {
    FQName lowest(fqName);
    while (lowest.getPackageMinorVersion() != 0) {
        if (!packageExists(coordinator, lowest.downRev())) break;

        lowest = lowest.downRev();
    }
    return lowest;
}

// assuming fqName exists, find newest version which does exist
// e.g. android.hardware.foo@1.1 -> android.hardware.foo@1.7 if that's the
// newest
static FQName getHighestExistingFqName(const Coordinator& coordinator, const FQName& fqName) {
    FQName highest(fqName);
    while (packageExists(coordinator, highest.upRev())) {
        highest = highest.upRev();
    }
    return highest;
}

static AST* parse(const Coordinator& coordinator, const FQName& target) {
    AST* ast = coordinator.parse(target);
    if (ast == nullptr) {
        std::cerr << "ERROR: Could not parse " << target.name() << ". Aborting." << std::endl;
        exit(1);
    }

    if (!ast->getUnhandledComments().empty()) {
        AidlHelper::notes()
                << "Unhandled comments from " << target.string()
                << " follow. Consider using hidl-lint to locate these and fixup as many "
                << "as possible.\n";
        for (const DocComment* docComment : ast->getUnhandledComments()) {
            docComment->emit(AidlHelper::notes());
        }
        AidlHelper::notes() << "\n";
    }

    return ast;
}

static void getSubTypes(const NamedType& namedType, std::set<const NamedType*>* types) {
    if (namedType.isScope()) {
        const Scope& compoundType = static_cast<const Scope&>(namedType);
        for (const NamedType* subType : compoundType.getSubTypes()) {
            types->insert(subType);
            getSubTypes(*subType, types);
        }
    }
}

static void emitAidlSharedLibs(Formatter& out, FQName fqName, AidlBackend backend) {
    if (backend == AidlBackend::NDK) {
        out << "        \"libbinder_ndk\",\n";
        out << "        \"libhidlbase\",\n";
        out << "        \"" << AidlHelper::getAidlPackage(fqName) << "-V1-ndk\",\n";
    } else if (backend == AidlBackend::CPP) {
        out << "        \"libbinder\",\n";
        out << "        \"libhidlbase\",\n";
        out << "        \"" << AidlHelper::getAidlPackage(fqName) << "-V1-cpp\",\n";
        out << "        \"libutils\",\n";
    } else {
        out << "        \"" << AidlHelper::getAidlPackage(fqName) << "-V1-java\",\n";
    }
}

static void emitHidlSharedLibs(Formatter& out, std::vector<FQName>& targets, AidlBackend backend) {
    std::set<std::string> uniquePackages;
    for (const auto& target : targets) {
        if (backend == AidlBackend::JAVA) {
            uniquePackages.insert(
                    android::base::StringReplace(target.getPackageAndVersion().string(), "@", "-V",
                                                 false /* all */) +
                    "-java");
        } else {
            uniquePackages.insert(target.getPackageAndVersion().string());
        }
    }
    for (const auto& package : uniquePackages) {
        out << "        \"" << package << "\",\n";
    }
}

static std::string aidlTranslateLibraryName(FQName fqName, AidlBackend backend) {
    std::string postfix;
    if (backend == AidlBackend::NDK) {
        postfix = "-ndk";
    } else if (backend == AidlBackend::CPP) {
        postfix = "-cpp";
    } else {
        postfix = "-java";
    }
    return AidlHelper::getAidlPackage(fqName) + "-translate" + postfix;
}

static void emitBuildFile(Formatter& out, const FQName& fqName, std::vector<FQName>& targets,
                          bool needsTranslation) {
    out << "// This is the expected build file, but it may not be right in all cases\n";
    out << "\n";
    out << "aidl_interface {\n";
    out << "    name: \"" << AidlHelper::getAidlPackage(fqName) << "\",\n";
    out << "    vendor_available: true,\n";
    out << "    host_supported: true,\n";
    out << "    frozen: false,\n";
    out << "    srcs: [\"" << AidlHelper::getAidlPackagePath(fqName) << "/*.aidl\"],\n";
    out << "    stability: \"vintf\",\n";
    out << "    backend: {\n";
    out << "        cpp: {\n";
    out << "            // FIXME should this be disabled?\n";
    out << "            // prefer NDK backend which can be used anywhere\n";
    out << "            // If you disable this, you also need to delete the C++\n";
    out << "            // translate code.\n";
    out << "            enabled: true,\n";
    out << "        },\n";
    out << "        java: {\n";
    out << "            sdk_version: \"system_current\",\n";
    out << "        },\n";
    out << "    },\n";
    out << "}\n\n";

    if (!needsTranslation) return;

    for (auto backend : {AidlBackend::CPP, AidlBackend::NDK}) {
        out << "cc_library {\n";
        out << "    name: \"" << aidlTranslateLibraryName(fqName, backend) << +"\",\n";
        if (backend == AidlBackend::NDK) {
            out << "    vendor_available: true,\n";
        }
        out << "    srcs: [\"" << AidlHelper::translateSourceFile(fqName, backend) + "\"],\n";
        out << "    shared_libs: [\n";
        emitAidlSharedLibs(out, fqName, backend);
        emitHidlSharedLibs(out, targets, backend);
        out << "    ],\n";
        out << "    export_include_dirs: [\"include\"],\n";
        out << "}\n\n";
    }

    out << "java_library {\n";
    out << "    name: \"" << aidlTranslateLibraryName(fqName, AidlBackend::JAVA) << +"\",\n";
    out << "    srcs: [\"" << AidlHelper::translateSourceFile(fqName, AidlBackend::JAVA) + "\"],\n";
    out << "    libs: [\n";
    emitAidlSharedLibs(out, fqName, AidlBackend::JAVA);
    emitHidlSharedLibs(out, targets, AidlBackend::JAVA);
    out << "    ],\n";
    out << "    sdk_version: \"system_current\",\n";
    out << "}\n\n";
}

// hidl is intentionally leaky. Turn off LeakSanitizer by default.
extern "C" const char* __asan_default_options() {
    return "detect_leaks=0";
}

int main(int argc, char** argv) {
    const char* me = argv[0];
    if (argc == 1) {
        usage(me);
        std::cerr << "ERROR: no fqname specified." << std::endl;
        exit(1);
    }

    Coordinator coordinator;
    std::string outputPath;
    std::string fileHeader;
    bool forceConvertOldInterfaces = false;
    coordinator.parseOptions(argc, argv, "fho:l:e", [&](int res, char* arg) {
        switch (res) {
            case 'o': {
                if (!outputPath.empty()) {
                    fprintf(stderr, "ERROR: -o <output path> can only be specified once.\n");
                    exit(1);
                }
                outputPath = arg;
                break;
            }
            case 'l':
                if (!fileHeader.empty()) {
                    fprintf(stderr, "ERROR: -l <header file> can only be specified once.\n");
                    exit(1);
                }
                fileHeader = arg;
                break;
            case 'f':
                forceConvertOldInterfaces = true;
                break;
            case 'e':
                AidlHelper::setExpandExtended(true);
                break;
            case 'h':
            case '?':
            default: {
                usage(me);
                exit(1);
                break;
            }
        }
    });

    if (!outputPath.empty() && outputPath.back() != '/') {
        outputPath += "/";
    }
    coordinator.setOutputPath(outputPath);
    AidlHelper::setFileHeader(fileHeader);

    argc -= optind;
    argv += optind;

    if (argc == 0) {
        usage(me);
        std::cerr << "ERROR: no fqname specified." << std::endl;
        exit(1);
    }

    if (argc > 1) {
        usage(me);
        std::cerr << "ERROR: only one fqname can be specified." << std::endl;
        exit(1);
    }

    const char* arg = argv[0];

    FQName fqName;
    if (!FQName::parse(arg, &fqName)) {
        std::cerr << "ERROR: Invalid fully-qualified name as argument: " << arg << "." << std::endl;
        exit(1);
    }

    if (fqName.isFullyQualified()) {
        std::cerr << "ERROR: hidl2aidl only supports converting an entire package, try "
                     "converting "
                  << fqName.getPackageAndVersion().string() << " instead." << std::endl;
        exit(1);
    }

    if (!packageExists(coordinator, fqName)) {
        std::cerr << "ERROR: Could not get sources for: " << arg << "." << std::endl;
        exit(1);
    }

    if (!forceConvertOldInterfaces) {
        const FQName highestFqName = getHighestExistingFqName(coordinator, fqName);
        if (fqName != highestFqName) {
            std::cerr << "ERROR: A newer minor version of " << fqName.string() << " exists ("
                      << highestFqName.string()
                      << "). In general, prefer to convert that instead. If you really mean to "
                         "use an old minor version use '-f'."
                      << std::endl;
            exit(1);
        }
    }

    // This is the list of all targets which should be converted
    std::vector<FQName> targets;
    for (FQName version = getLowestExistingFqName(coordinator, fqName);
         version.getPackageMinorVersion() <= fqName.getPackageMinorVersion();
         version = version.upRev()) {
        std::vector<FQName> newTargets;
        status_t err = coordinator.appendPackageInterfacesToVector(version, &newTargets);
        if (err != OK) exit(1);

        targets.insert(targets.end(), newTargets.begin(), newTargets.end());
    }

    // targets should not contain duplicates since appendPackageInterfaces is only called once
    // per version. now remove all the elements that are not the "newest"
    const auto& newEnd =
            std::remove_if(targets.begin(), targets.end(), [&](const FQName& fqName) -> bool {
                if (fqName.name() == "types") return false;

                return getLatestMinorVersionFQNameFromList(fqName, targets) != fqName;
            });
    targets.erase(newEnd, targets.end());

    // Set up AIDL conversion log
    Formatter err =
            coordinator.getFormatter(fqName, Coordinator::Location::DIRECT, "conversion.log");
    err << "Notes relating to hidl2aidl conversion of " << fqName.string() << " to "
        << AidlHelper::getAidlPackage(fqName) << " (if any) follow:\n";
    AidlHelper::setNotes(&err);

    // Gather all the types and interfaces
    std::set<const NamedType*> namedTypesInPackage;
    for (const FQName& target : targets) {

        AST* ast = parse(coordinator, target);
        CHECK(ast);

        const Interface* iface = ast->getInterface();
        if (iface) {
            namedTypesInPackage.insert(iface);

            // Get all of the types defined in the interface chain(includes self)
            for (const Interface* interface : iface->typeChain()) {
                if (!AidlHelper::shouldBeExpanded(iface->fqName(), interface->fqName())) {
                    break;
                }
                getSubTypes(*interface, &namedTypesInPackage);
            }
        } else {
            getSubTypes(ast->getRootScope(), &namedTypesInPackage);
        }
    }

    // Remove all of the older versions of types and keep the latest
    for (auto it = namedTypesInPackage.begin(); it != namedTypesInPackage.end();) {
        if (getLatestMinorVersionNamedTypeFromList((*it)->fqName(), namedTypesInPackage) !=
            (*it)->fqName()) {
            it = namedTypesInPackage.erase(it);
        } else {
            it++;
        }
    }

    // Process and flatten all of the types. Many types include fields of older
    // versions of that type.
    // This step recursively finds all of those fields and adds their fields to
    // the most recent top level type.
    std::map<const NamedType*, const ProcessedCompoundType> processedTypesInPackage;
    for (const auto& namedType : namedTypesInPackage) {
        if (namedType->isCompoundType()) {
            ProcessedCompoundType processed;
            AidlHelper::processCompoundType(static_cast<const CompoundType&>(*namedType),
                                            &processed, std::string());
            processedTypesInPackage.insert(
                    std::pair<const NamedType*, const ProcessedCompoundType>(namedType, processed));
        }
    }

    Formatter buildFile =
            coordinator.getFormatter(fqName, Coordinator::Location::DIRECT, "Android.bp");
    emitBuildFile(buildFile, fqName, targets, !processedTypesInPackage.empty());
    AidlHelper::emitTranslation(coordinator, fqName, namedTypesInPackage, processedTypesInPackage);

    // Emit all types and interfaces
    // The interfaces and types are still be further manipulated inside
    // emitAidl. The interfaces are consolidating methods from their typechains
    // and the composite types are being flattened.
    for (const auto& namedType : namedTypesInPackage) {
        // Nested types do not get their own files
        if (namedType->fqName().names().size() > 1) continue;
        Formatter out =
                AidlHelper::getFileWithHeader(*namedType, coordinator, processedTypesInPackage);
        AidlHelper::emitAidl(*namedType, out, processedTypesInPackage);
    }

    err << "END OF LOG\n";

    return 0;
}
