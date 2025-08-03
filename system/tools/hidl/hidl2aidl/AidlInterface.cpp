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
#include <android-base/parseint.h>
#include <android-base/strings.h>
#include <hidl-util/FQName.h>
#include <hidl-util/Formatter.h>
#include <hidl-util/StringHelper.h>
#include <cstddef>
#include <vector>

#include "AidlHelper.h"
#include "Coordinator.h"
#include "DocComment.h"
#include "FormattingConstants.h"
#include "Interface.h"
#include "Location.h"
#include "Method.h"
#include "NamedType.h"
#include "Reference.h"
#include "Type.h"

namespace android {

static void emitAidlMethodParams(WrappedOutput* wrappedOutput,
                                 const std::vector<NamedReference<Type>*> args,
                                 const std::string& prefix, const std::string& attachToLast,
                                 const Interface& iface) {
    if (args.size() == 0) {
        *wrappedOutput << attachToLast;
        return;
    }

    for (size_t i = 0; i < args.size(); i++) {
        const NamedReference<Type>* arg = args[i];
        std::string out =
                prefix + AidlHelper::getAidlType(*arg->get(), iface.fqName()) + " " + arg->name();
        wrappedOutput->group([&] {
            if (i != 0) wrappedOutput->printUnlessWrapped(" ");
            *wrappedOutput << out;
            if (i == args.size() - 1) {
                if (!attachToLast.empty()) *wrappedOutput << attachToLast;
            } else {
                *wrappedOutput << ",";
            }
        });
    }
}

std::vector<const Method*> AidlHelper::getUserDefinedMethods(Formatter& out,
                                                             const Interface& interface) {
    std::vector<const Method*> methods;
    for (const Interface* iface : interface.typeChain()) {
        if (!AidlHelper::shouldBeExpanded(interface.fqName(), iface->fqName()) &&
            iface->fqName() != gIBaseFqName) {
            out << "// Types from " << iface->fqName().string()
                << " are not included because it is in a separate package, and it is expected to "
                   "be a separate AIDL interface. To include these methods, use the '-e' argument. "
                   "\n";
            break;
        }
        const std::vector<Method*> userDefined = iface->userDefinedMethods();
        methods.insert(methods.end(), userDefined.begin(), userDefined.end());
    }

    return methods;
}

// Represents a node which is potentially overriding another node.
// e.g. if this is 'foo_1_4'
template <class NODE>
struct NodeWithVersion {
    size_t major;          // 1
    size_t minor;          // 4
    const NODE* node;      // HIDL object representing foo_1_4.
    std::string baseName;  // foo
};

std::string getBaseName(const std::string& rawName) {
    size_t underscore = rawName.find('_');
    if (underscore != std::string::npos) {
        std::string version = rawName.substr(underscore + 1);  // don't include _
        std::string baseName = rawName.substr(0, underscore);
        underscore = version.find('_');

        size_t major, minor;
        if (underscore != std::string::npos &&
            base::ParseUint(version.substr(0, underscore), &major) &&
            base::ParseUint(version.substr(underscore + 1), &minor)) {
            // contains major and minor version. consider it's baseName now.
            return baseName;
        }
    }
    return rawName;
}

template <class NODE>
static void pushVersionedNodeOntoMap(const NODE& versionedNode,
                                     std::map<std::string, NODE>* latestNodeForBaseName,
                                     std::vector<const NODE>* supersededNode) {
    // attempt to push name onto latestNodeForBaseName
    auto [it, inserted] =
            latestNodeForBaseName->emplace(std::move(versionedNode.baseName), versionedNode);
    if (!inserted) {
        auto* current = &it->second;

        // Node in the latestNodeForBaseName is more recent
        if ((current->major > versionedNode.major) ||
            (current->major == versionedNode.major && current->minor > versionedNode.minor)) {
            supersededNode->push_back(versionedNode);
            return;
        }

        // Either current.major < versioned.major OR versioned.minor >= current.minor
        supersededNode->push_back(*current);
        *current = std::move(versionedNode);
    }
}

struct ResultTransformation {
    enum class TransformType {
        MOVED,    // Moved to the front of the method name
        REMOVED,  // Removed the result
    };

    std::string resultName;
    TransformType type;
};

static bool shouldWarnStatusType(const std::string& typeName) {
    static const std::vector<std::string> kUppercaseIgnoreStatusTypes = {"ERROR", "STATUS"};

    const std::string uppercase = StringHelper::Uppercase(typeName);
    for (const std::string& ignore : kUppercaseIgnoreStatusTypes) {
        if (uppercase.find(ignore) != std::string::npos) return true;
    }
    return false;
}

static bool shouldWarnOutParam(const std::string& typeName) {
    static const std::vector<std::string> kNoOutParamTypes = {"ParcelFileDescriptor",
                                                              "FileDescriptor",
                                                              "ParcelableHolder",
                                                              "IBinder",
                                                              "String",
                                                              "CharacterSequence",
                                                              "void",
                                                              "boolean",
                                                              "byte",
                                                              "char",
                                                              "int",
                                                              "long",
                                                              "float",
                                                              "double"};
    return std::find(kNoOutParamTypes.begin(), kNoOutParamTypes.end(), typeName) !=
           kNoOutParamTypes.end();
}

void AidlHelper::emitAidl(
        const Interface& interface, Formatter& out,
        const std::map<const NamedType*, const ProcessedCompoundType>& processedTypes) {
    interface.emitDocComment(out);
    if (interface.superType() && interface.superType()->fqName() != gIBaseFqName) {
        out << "// Interface inherits from " << interface.superType()->fqName().string()
            << " but AIDL does not support interface inheritance (methods have been flattened).\n";
    }

    out << "@VintfStability\n";
    out << "interface " << getAidlName(interface.fqName()) << " ";
    out.block([&] {
        std::map<std::string, NodeWithVersion<NamedType>> latestTypeForBaseName;
        std::vector<const NodeWithVersion<NamedType>> supersededNamedTypes;
        std::map<std::string, NodeWithVersion<Method>> latestMethodForBaseName;
        std::vector<const NodeWithVersion<Method>> supersededMethods;
        for (const Interface* iface : interface.typeChain()) {
            if (!AidlHelper::shouldBeExpanded(interface.fqName(), iface->fqName())) {
                // Stop traversing extended interfaces once they leave this package
                break;
            }
            for (const Method* method : iface->userDefinedMethods()) {
                pushVersionedNodeOntoMap({iface->fqName().getPackageMajorVersion(),
                                          iface->fqName().getPackageMinorVersion(), method,
                                          getBaseName(method->name())},
                                         &latestMethodForBaseName, &supersededMethods);
            }
            // Types from other interfaces will be handled while those interfaces
            // are being emitted.
            if (iface->getBaseName() != interface.getBaseName()) {
                continue;
            }
            for (const NamedType* type : iface->getSubTypes()) {
                // The baseName for types is not being stripped of the version
                // numbers like that of the methods. If a type was named
                // BigStruct_1_1 and the previous version was named BigStruct,
                // they will be treated as two different types.
                pushVersionedNodeOntoMap({iface->fqName().getPackageMajorVersion(),
                                          iface->fqName().getPackageMinorVersion(), type,
                                          getAidlName(type->fqName())},
                                         &latestTypeForBaseName, &supersededNamedTypes);
            }
        }

        // Add comment for superseded types
        out.join(supersededNamedTypes.begin(), supersededNamedTypes.end(), "\n",
                 [&](const NodeWithVersion<NamedType>& versionedType) {
                     out << "// Ignoring type " << getAidlName(versionedType.node->fqName())
                         << " from " << versionedType.major << "." << versionedType.minor
                         << "::" << getAidlName(interface.fqName())
                         << " since a newer alternative is available.";
                 });
        if (!supersededNamedTypes.empty()) out << "\n\n";
        // Emit the nested type definitions
        for (const auto& [name, type] : latestTypeForBaseName) {
            emitAidl(*type.node, out, processedTypes);
        }
        // Add comment for superseded methods
        out.join(supersededMethods.begin(), supersededMethods.end(), "\n",
                 [&](const NodeWithVersion<Method>& versionedMethod) {
                     out << "// Ignoring method " << versionedMethod.node->name() << " from "
                         << versionedMethod.major << "." << versionedMethod.minor
                         << "::" << getAidlName(interface.fqName())
                         << " since a newer alternative is available.";
                 });
        if (!supersededMethods.empty()) out << "\n\n";

        // Emit latest methods defined for this interface
        out.join(latestMethodForBaseName.begin(), latestMethodForBaseName.end(), "\n",
                 [&](const std::pair<std::string, NodeWithVersion<Method>>& methodPair) {
                     const Method* method = methodPair.second.node;
                     const std::string& baseName = methodPair.first;
                     std::vector<NamedReference<Type>*> results;
                     std::vector<ResultTransformation> transformations;
                     for (NamedReference<Type>* res : method->results()) {
                         const std::string aidlType = getAidlType(*res->get(), interface.fqName());

                         if (shouldWarnStatusType(aidlType)) {
                             out << "// FIXME: AIDL has built-in status types. Do we need the "
                                    "status type here?\n";
                         }
                         if (method->results().size() > 1 && shouldWarnOutParam(aidlType)) {
                             out << "// FIXME: AIDL does not allow " << aidlType
                                 << " to be an out parameter.\n";
                             out << "// Move it to return, or add it to a Parcelable.\n";
                         }
                         results.push_back(res);
                     }

                     if (method->name() != baseName) {
                         out << "// Changing method name from " << method->name() << " to "
                             << baseName << "\n";
                     }

                     std::string returnType = "void";
                     if (results.size() == 1) {
                         returnType = getAidlType(*results[0]->get(), interface.fqName());

                         out << "// Adding return type to method instead of out param "
                             << returnType << " " << results[0]->name()
                             << " since there is only one return value.\n";
                         transformations.emplace_back(ResultTransformation{
                                 results[0]->name(), ResultTransformation::TransformType::MOVED});
                         results.clear();
                     }

                     if (method->getDocComment() != nullptr) {
                         std::vector<std::string> modifiedDocComment;
                         for (const std::string& line : method->getDocComment()->lines()) {
                             std::vector<std::string> tokens = base::Split(line, " ");
                             if (tokens.size() <= 1 || tokens[0] != "@return") {
                                 // unimportant line
                                 modifiedDocComment.emplace_back(line);
                                 continue;
                             }

                             const std::string& res = tokens[1];
                             bool transformed = false;
                             for (const ResultTransformation& transform : transformations) {
                                 if (transform.resultName != res) continue;

                                 // Some transform was done to it
                                 if (transform.type == ResultTransformation::TransformType::MOVED) {
                                     // remove the name
                                     tokens.erase(++tokens.begin());
                                     transformed = true;
                                 } else {
                                     CHECK(transform.type ==
                                           ResultTransformation::TransformType::REMOVED);
                                     tokens.insert(tokens.begin(),
                                                   "FIXME: The following return was removed\n");
                                     transformed = true;
                                 }
                             }

                             if (!transformed) {
                                 tokens.erase(tokens.begin());
                                 tokens.insert(tokens.begin(), "@param out");
                             }

                             modifiedDocComment.emplace_back(base::Join(tokens, " "));
                         }

                         DocComment(modifiedDocComment, HIDL_LOCATION_HERE).emit(out);
                     }

                     WrappedOutput wrappedOutput(MAX_LINE_LENGTH);

                     if (method->isOneway()) wrappedOutput << "oneway ";
                     wrappedOutput << returnType << " " << baseName << "(";

                     if (results.empty()) {
                         emitAidlMethodParams(&wrappedOutput, method->args(), /* prefix */ "in ",
                                              /* attachToLast */ ");\n", interface);
                     } else {
                         const bool emitArgs = !method->args().empty();
                         if (emitArgs) {
                             emitAidlMethodParams(&wrappedOutput, method->args(),
                                                  /* prefix */ "in ",
                                                  /* attachToLast */ ",", interface);
                         }
                         wrappedOutput.group([&] {
                             if (emitArgs) wrappedOutput.printUnlessWrapped(" ");
                             emitAidlMethodParams(&wrappedOutput, results, /* prefix */ "out ",
                                                  /* attachToLast */ ");\n", interface);
                         });
                     }

                     out << wrappedOutput;
                 });
    });
    out << "\n";
}

}  // namespace android
