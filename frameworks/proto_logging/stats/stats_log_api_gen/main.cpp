
#include <google/protobuf/compiler/importer.h>
#include <google/protobuf/descriptor.h>
#include <google/protobuf/stubs/common.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <cstdlib>
#include <filesystem>

#include "Collation.h"
#include "frameworks/proto_logging/stats/atoms.pb.h"
#include "frameworks/proto_logging/stats/attribution_node.pb.h"
#include "java_writer.h"
#include "native_writer.h"
#include "rust_writer.h"
#include "utils.h"

#ifdef WITH_VENDOR
#include "java_writer_vendor.h"
#include "native_writer_vendor.h"
#endif

namespace android {
namespace stats_log_api_gen {

namespace fs = std::filesystem;
using android::os::statsd::Atom;

static void print_usage() {
    fprintf(stderr, "usage: stats-log-api-gen OPTIONS\n");
    fprintf(stderr, "\n");
    fprintf(stderr, "OPTIONS\n");
    fprintf(stderr, "  --cpp FILENAME       the cpp file to output for write helpers\n");
    fprintf(stderr, "  --header FILENAME    the header file to output for write helpers\n");
    fprintf(stderr, "  --help               this message\n");
    fprintf(stderr, "  --java FILENAME      the java file to output\n");
    fprintf(stderr, "  --rust FILENAME      the rust file to output\n");
    fprintf(stderr, "  --rustHeader FILENAME the rust file to output for write helpers\n");
    fprintf(stderr,
            "  --rustHeaderCrate NAME        header crate to be used while "
            "generating the code. Note: this should be the same as the crate_name "
            "created by rust_library for the header \n");
    fprintf(stderr, "  --module NAME        optional, module name to generate outputs for\n");
    fprintf(stderr,
            "  --namespace COMMA,SEP,NAMESPACE   required for cpp/header with "
            "module\n");
    fprintf(stderr,
            "                                    comma separated namespace of "
            "the files\n");
    fprintf(stderr,
            "  --importHeader NAME  required for cpp/jni to say which header to "
            "import "
            "for write helpers\n");
    fprintf(stderr, "  --javaPackage PACKAGE             the package for the java file.\n");
    fprintf(stderr, "                                    required for java with module\n");
    fprintf(stderr, "  --javaClass CLASS    the class name of the java class.\n");
    fprintf(stderr, "  --minApiLevel API_LEVEL           lowest API level to support.\n");
    fprintf(stderr, "                                    Default is \"current\".\n");
    fprintf(stderr,
            "  --worksource         Include support for logging WorkSource "
            "objects.\n");
    fprintf(stderr, "                                        Default is \"current\".\n");
    fprintf(stderr,
            "  --bootstrap          If this logging is from a bootstrap process. "
            "Only supported for cpp. Do not use unless necessary.\n");
#ifdef WITH_VENDOR
    fprintf(stderr,
            "  --vendor-proto       Path to the proto file for vendor atoms logging\n"
            "code generation.\n");
#endif
}

/**
 * Do the argument parsing and execute the tasks.
 */
static int run(int argc, char const* const* argv) {
    string cppFilename;
    string headerFilename;
    string javaFilename;
    string javaPackage;
    string javaClass;
    string rustFilename;
    string rustHeaderFilename;
    string rustHeaderCrate;
    string moduleName = DEFAULT_MODULE_NAME;
    string cppNamespace = DEFAULT_CPP_NAMESPACE;
    string cppHeaderImport = DEFAULT_CPP_HEADER_IMPORT;
    string vendorProto;
    bool supportWorkSource = false;
    int minApiLevel = API_LEVEL_CURRENT;
    bool bootstrap = false;

    int index = 1;
    while (index < argc) {
        if (0 == strcmp("--help", argv[index])) {
            print_usage();
            return 0;
        } else if (0 == strcmp("--cpp", argv[index])) {
            index++;
            if (index >= argc) {
                print_usage();
                return 1;
            }
            cppFilename = argv[index];
        } else if (0 == strcmp("--header", argv[index])) {
            index++;
            if (index >= argc) {
                print_usage();
                return 1;
            }
            headerFilename = argv[index];
        } else if (0 == strcmp("--java", argv[index])) {
            index++;
            if (index >= argc) {
                print_usage();
                return 1;
            }
            javaFilename = argv[index];
        } else if (0 == strcmp("--rust", argv[index])) {
            index++;
            if (index >= argc) {
                print_usage();
                return 1;
            }
            rustFilename = argv[index];
        } else if (0 == strcmp("--rustHeader", argv[index])) {
            index++;
            if (index >= argc) {
                print_usage();
                return 1;
            }
            rustHeaderFilename = argv[index];
        } else if (0 == strcmp("--rustHeaderCrate", argv[index])) {
            index++;
            if (index >= argc) {
                print_usage();
                return 1;
            }
            rustHeaderCrate = argv[index];
        } else if (0 == strcmp("--module", argv[index])) {
            index++;
            if (index >= argc) {
                print_usage();
                return 1;
            }
            moduleName = argv[index];
        } else if (0 == strcmp("--namespace", argv[index])) {
            index++;
            if (index >= argc) {
                print_usage();
                return 1;
            }
            cppNamespace = argv[index];
        } else if (0 == strcmp("--importHeader", argv[index])) {
            index++;
            if (index >= argc) {
                print_usage();
                return 1;
            }
            cppHeaderImport = argv[index];
        } else if (0 == strcmp("--javaPackage", argv[index])) {
            index++;
            if (index >= argc) {
                print_usage();
                return 1;
            }
            javaPackage = argv[index];
        } else if (0 == strcmp("--javaClass", argv[index])) {
            index++;
            if (index >= argc) {
                print_usage();
                return 1;
            }
            javaClass = argv[index];
        } else if (0 == strcmp("--supportQ", argv[index])) {
            minApiLevel = API_Q;
        } else if (0 == strcmp("--worksource", argv[index])) {
            supportWorkSource = true;
        } else if (0 == strcmp("--minApiLevel", argv[index])) {
            index++;
            if (index >= argc) {
                print_usage();
                return 1;
            }
            if (0 != strcmp("current", argv[index])) {
                minApiLevel = atoi(argv[index]);
            }
        } else if (0 == strcmp("--bootstrap", argv[index])) {
            bootstrap = true;
#ifdef WITH_VENDOR
        } else if (0 == strcmp("--vendor-proto", argv[index])) {
            index++;
            if (index >= argc) {
                print_usage();
                return 1;
            }

            vendorProto = argv[index];
#endif
        }

        index++;
    }
    if (index < argc) {
        fprintf(stderr, "Error: Unknown command line argument\n");
        print_usage();
        return 1;
    }

    if (cppFilename.empty() && headerFilename.empty() && javaFilename.empty() &&
        rustFilename.empty() && rustHeaderFilename.empty()) {
        print_usage();
        return 1;
    }
    if (DEFAULT_MODULE_NAME == moduleName && minApiLevel != API_LEVEL_CURRENT) {
        // Default module only supports current API level.
        fprintf(stderr, "%s cannot support older API levels\n", moduleName.c_str());
        return 1;
    }

    if (minApiLevel < API_Q) {
        // Cannot support pre-Q.
        fprintf(stderr, "minApiLevel must be %d or higher.\n", API_Q);
        return 1;
    }

    if (bootstrap) {
        if (cppFilename.empty() && headerFilename.empty()) {
            fprintf(stderr, "Bootstrap flag can only be used for cpp/header files.\n");
            return 1;
        }
        if (supportWorkSource) {
            fprintf(stderr, "Bootstrap flag does not support worksources");
            return 1;
        }
        if (minApiLevel != API_LEVEL_CURRENT) {
            fprintf(stderr, "Bootstrap flag does not support older API levels");
            return 1;
        }
    }

    // Collate the parameters
    int errorCount = 0;

    Atoms atoms;

    MFErrorCollector errorCollector;
    google::protobuf::compiler::DiskSourceTree sourceTree;
    google::protobuf::compiler::Importer importer(&sourceTree, &errorCollector);

    if (vendorProto.empty()) {
        errorCount = collate_atoms(*Atom::descriptor(), moduleName, atoms);
    } else {
        const google::protobuf::FileDescriptor* fileDescriptor;
        sourceTree.MapPath("", fs::current_path().c_str());

        const char* androidBuildTop = std::getenv("ANDROID_BUILD_TOP");

        fs::path protobufSrc = androidBuildTop != nullptr ? androidBuildTop : fs::current_path();
        protobufSrc /= "external/protobuf/src";
        sourceTree.MapPath("", protobufSrc.c_str());

        if (androidBuildTop != nullptr) {
            sourceTree.MapPath("", androidBuildTop);
        }

        fileDescriptor = importer.Import(vendorProto);
        errorCount =
                collate_atoms(*fileDescriptor->FindMessageTypeByName("Atom"), moduleName, atoms);
    }

    if (errorCount != 0) {
        return 1;
    }

    AtomDecl attributionDecl;
    vector<java_type_t> attributionSignature;
    collate_atom(*android::os::statsd::AttributionNode::descriptor(), attributionDecl,
                 attributionSignature);

    // Write the .cpp file
    if (!cppFilename.empty()) {
        // If this is for a specific module, the namespace must also be provided.
        if (moduleName != DEFAULT_MODULE_NAME && cppNamespace == DEFAULT_CPP_NAMESPACE) {
            fprintf(stderr, "Must supply --namespace if supplying a specific module\n");
            return 1;
        }
        // If this is for a specific module, the header file to import must also be
        // provided.
        if (moduleName != DEFAULT_MODULE_NAME && cppHeaderImport == DEFAULT_CPP_HEADER_IMPORT) {
            fprintf(stderr, "Must supply --headerImport if supplying a specific module\n");
            return 1;
        }
        FILE* out = fopen(cppFilename.c_str(), "we");
        if (out == nullptr) {
            fprintf(stderr, "Unable to open file for write: %s\n", cppFilename.c_str());
            return 1;
        }
        if (vendorProto.empty()) {
            errorCount = android::stats_log_api_gen::write_stats_log_cpp(
                    out, atoms, attributionDecl, cppNamespace, cppHeaderImport, minApiLevel,
                    bootstrap);
        } else {
#ifdef WITH_VENDOR
            errorCount = android::stats_log_api_gen::write_stats_log_cpp_vendor(
                    out, atoms, attributionDecl, cppNamespace, cppHeaderImport);
#endif
        }
        fclose(out);
    }

    // Write the .h file
    if (!headerFilename.empty()) {
        // If this is for a specific module, the namespace must also be provided.
        if (moduleName != DEFAULT_MODULE_NAME && cppNamespace == DEFAULT_CPP_NAMESPACE) {
            fprintf(stderr, "Must supply --namespace if supplying a specific module\n");
        }
        FILE* out = fopen(headerFilename.c_str(), "we");
        if (out == nullptr) {
            fprintf(stderr, "Unable to open file for write: %s\n", headerFilename.c_str());
            return 1;
        }

        if (vendorProto.empty()) {
            errorCount = android::stats_log_api_gen::write_stats_log_header(
                    out, atoms, attributionDecl, cppNamespace, minApiLevel, bootstrap);
        } else {
#ifdef WITH_VENDOR
            errorCount = android::stats_log_api_gen::write_stats_log_header_vendor(
                    out, atoms, attributionDecl, cppNamespace);
#endif
        }
        fclose(out);
    }

    // Write the .java file
    if (!javaFilename.empty()) {
        if (javaClass.empty()) {
            fprintf(stderr, "Must supply --javaClass if supplying a Java filename");
            return 1;
        }

        if (javaPackage.empty()) {
            fprintf(stderr, "Must supply --javaPackage if supplying a Java filename");
            return 1;
        }

        if (moduleName.empty() || moduleName == DEFAULT_MODULE_NAME) {
            fprintf(stderr, "Must supply --module if supplying a Java filename");
            return 1;
        }

        FILE* out = fopen(javaFilename.c_str(), "we");
        if (out == nullptr) {
            fprintf(stderr, "Unable to open file for write: %s\n", javaFilename.c_str());
            return 1;
        }

        if (vendorProto.empty()) {
            errorCount = android::stats_log_api_gen::write_stats_log_java(
                    out, atoms, attributionDecl, javaClass, javaPackage, minApiLevel,
                    supportWorkSource);
        } else {
#ifdef WITH_VENDOR
            if (supportWorkSource) {
                fprintf(stderr, "The attribution chain is not supported for vendor atoms");
                return 1;
            }

            errorCount = android::stats_log_api_gen::write_stats_log_java_vendor(out, atoms,
                    javaClass, javaPackage);
#endif
        }

        fclose(out);
    }

    // Write the main .rs file
    if (!rustFilename.empty()) {
        if (rustHeaderCrate.empty()) {
            fprintf(stderr, "rustHeaderCrate flag is either not passed or is empty");
            return 1;
        }

        FILE* out = fopen(rustFilename.c_str(), "we");
        if (out == nullptr) {
            fprintf(stderr, "Unable to open file for write: %s\n", rustFilename.c_str());
            return 1;
        }

        errorCount += android::stats_log_api_gen::write_stats_log_rust(
                out, atoms, attributionDecl, minApiLevel, rustHeaderCrate.c_str());

        fclose(out);
    }

    // Write the header .rs file
    if (!rustHeaderFilename.empty()) {
        if (rustHeaderCrate.empty()) {
            fprintf(stderr, "rustHeaderCrate flag is either not passed or is empty");
            return 1;
        }

        FILE* out = fopen(rustHeaderFilename.c_str(), "we");
        if (out == nullptr) {
            fprintf(stderr, "Unable to open file for write: %s\n", rustHeaderFilename.c_str());
            return 1;
        }

        android::stats_log_api_gen::write_stats_log_rust_header(out, atoms, attributionDecl,
                                                                rustHeaderCrate.c_str());

        fclose(out);
    }

    return errorCount;
}

}  // namespace stats_log_api_gen
}  // namespace android

/**
 * Main.
 */
int main(int argc, char const* const* argv) {
    GOOGLE_PROTOBUF_VERIFY_VERSION;

    return android::stats_log_api_gen::run(argc, argv);
}
