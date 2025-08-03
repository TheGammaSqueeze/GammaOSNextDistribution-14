//
// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#include <gflags/gflags.h>

#include <map>
#include <memory>
#include <string>
#include <vector>

#include "catalog.h"
#include "codegen_java.h"

constexpr char DEFAULT_CONFIG_DIR[] = "frameworks/proto_logging/stats/express/catalog";

DEFINE_string(configDir, DEFAULT_CONFIG_DIR, "path to cfg files");
DEFINE_string(java, "", "path to the generated Java class file");
DEFINE_string(javaPackage, "", "generated Java package name");
DEFINE_string(javaClass, "", "generated Java class name");

namespace android {
namespace express {

std::vector<std::unique_ptr<CodeGenerator>> createCodeGenerators() {
    std::vector<std::unique_ptr<CodeGenerator>> result;

    if (FLAGS_java.size()) {
        result.push_back(std::make_unique<CodeGeneratorJava>(
                CodeGeneratorJava(FLAGS_java, FLAGS_javaPackage, FLAGS_javaClass)));
    }

    return result;
}

bool generateLoggingCode(const MetricInfoMap& metricsIds) {
    const auto codeGenerators = createCodeGenerators();
    for (const auto& codeGen : codeGenerators) {
        if (!codeGen->generateCode(metricsIds)) return false;
    }
    return true;
}

/**
 * Do the argument parsing and execute the tasks.
 */
static int run() {
    std::map<std::string, ExpressMetric> metrics;
    if (!readCatalog(FLAGS_configDir.c_str(), metrics)) {
        return -1;
    }

    MetricInfoMap metricsIds;
    if (!generateMetricsIds(metrics, metricsIds)) {
        return -1;
    }

    if (!generateLoggingCode(metricsIds)) {
        return -1;
    }

    return 0;
}

}  // namespace express
}  // namespace android

int main(int argc, char** argv) {
    GOOGLE_PROTOBUF_VERIFY_VERSION;
    google::ParseCommandLineFlags(&argc, &argv, false);
    return android::express::run();
}
