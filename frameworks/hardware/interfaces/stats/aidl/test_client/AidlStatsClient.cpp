/*
 * Copyright (C) 2021 The Android Open Source Project
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
#include <aidl/android/frameworks/stats/IStats.h>
#include <android/binder_manager.h>
#include <getopt.h>
#include <statslog.h>

#include <iostream>

using namespace aidl::android::frameworks::stats;

void expect_message(int32_t action) {
    std::cout << "expect the following log in logcat:\n";
    std::cout << "statsd.*(" << action << ")0x10000->\n";
}

void show_help() {
    std::cout << "AIDL Stats HAL client\n";
    std::cout << " arguments:\n";
    std::cout << " -h or --help - shows help information\n";
    std::cout << " -v or --VendorAtom - tests report reportVendorAtom API\n";
    std::cout << "Please enable statsd logging using 'cmd stats print-logs'";
    std::cout << "\n\n you can use multiple arguments to trigger multiple events.\n";
}

VendorAtom buildVendorAtom() {
    std::vector<VendorAtomValue> values;
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::longValue>(70000);
    values.push_back(tmp);
    tmp.set<VendorAtomValue::intValue>(7);
    values.push_back(tmp);
    tmp.set<VendorAtomValue::floatValue>(8.5);
    values.push_back(tmp);
    tmp.set<VendorAtomValue::stringValue>("test");
    values.push_back(tmp);
    tmp.set<VendorAtomValue::intValue>(3);
    values.push_back(tmp);
    tmp.set<VendorAtomValue::boolValue>(true);
    values.push_back(tmp);
    tmp.set<VendorAtomValue::boolValue>(false);
    values.push_back(tmp);
    std::vector<int> emptyRepeatedIntValue = {};
    tmp.set<VendorAtomValue::repeatedIntValue>(emptyRepeatedIntValue);
    values.push_back(tmp);
    std::vector<int> repeatedIntValue = {3, 1, 2};
    tmp.set<VendorAtomValue::repeatedIntValue>(repeatedIntValue);
    values.push_back(tmp);
    std::vector<int64_t> repeatedLongValue = {500000, 430000, 1000001};
    tmp.set<VendorAtomValue::repeatedLongValue>(repeatedLongValue);
    values.push_back(tmp);
    std::vector<float> repeatedFloatValue = {1.5, 2.3, 7.9};
    tmp.set<VendorAtomValue::repeatedFloatValue>(repeatedFloatValue);
    values.push_back(tmp);
    std::vector<std::optional<std::string>> repeatedStringValue = {"str1", "str2", "str3"};
    tmp.set<VendorAtomValue::repeatedStringValue>(repeatedStringValue);
    values.push_back(tmp);
    std::vector<bool> repeatedBoolValue = {true, false, true};
    tmp.set<VendorAtomValue::repeatedBoolValue>(repeatedBoolValue);
    values.push_back(tmp);
    std::vector<uint8_t> byteArrayValue = {21, 50, 3};
    tmp.set<VendorAtomValue::byteArrayValue>(byteArrayValue);
    values.push_back(tmp);

    VendorAtom atom = {
        .atomId = 104999,
        .values = values,
    };

    return atom;
}

VendorAtom buildVendorAtomWithAnnotations() {
    // example of atom level annotation for VendorAtom from buildVendorAtom() API
    Annotation atomAnnotation{AnnotationId::TRUNCATE_TIMESTAMP, true};
    std::vector<std::optional<Annotation>> atomAnnotations;
    atomAnnotations.push_back(std::make_optional<Annotation>(atomAnnotation));

    // values annotation
    std::vector<std::optional<AnnotationSet>> valuesAnnotations;
    {
        AnnotationSet valueAnnotationSet;
        valueAnnotationSet.valueIndex = 0;
        valueAnnotationSet.annotations.push_back(Annotation{AnnotationId::PRIMARY_FIELD, true});
        valuesAnnotations.push_back(std::make_optional<AnnotationSet>(valueAnnotationSet));
    }
    {
        AnnotationSet valueAnnotationSet;
        valueAnnotationSet.valueIndex = 1;
        valueAnnotationSet.annotations.push_back(Annotation{AnnotationId::IS_UID, true});
        valuesAnnotations.push_back(std::make_optional<AnnotationSet>(valueAnnotationSet));
    }
    {
        AnnotationSet valueAnnotationSet;
        valueAnnotationSet.valueIndex = 4;
        valueAnnotationSet.annotations.push_back(Annotation{AnnotationId::EXCLUSIVE_STATE, true});
        valueAnnotationSet.annotations.push_back(Annotation{AnnotationId::STATE_NESTED, true});
        valueAnnotationSet.annotations.push_back(Annotation{AnnotationId::TRIGGER_STATE_RESET, 0});
        valuesAnnotations.push_back(std::make_optional<AnnotationSet>(valueAnnotationSet));
    }

    VendorAtom atom = buildVendorAtom();
    atom.atomAnnotations =
        std::make_optional<std::vector<std::optional<Annotation>>>(atomAnnotations);
    atom.valuesAnnotations =
        std::make_optional<std::vector<std::optional<AnnotationSet>>>(valuesAnnotations);
    return atom;
}

int main(int argc, char* argv[]) {
    // get instance of the aidl version
    const std::string instance = std::string() + IStats::descriptor + "/default";
    std::shared_ptr<IStats> service =
        IStats::fromBinder(ndk::SpAIBinder(AServiceManager_getService(instance.c_str())));
    if (!service) {
        std::cerr << "No Stats aidl HAL";
        return 1;
    }

    std::cout << "Service instance obtained : " << instance << std::endl;

    static struct option opts[] = {
        {"VendorAtom", no_argument, 0, 'v'},
        {"help", no_argument, 0, 'h'},
    };

    int c;
    int hal_calls = 0;
    int failed_calls = 0;
    while ((c = getopt_long(argc, argv, "vh", opts, nullptr)) != -1) {
        switch (c) {
            case 'h': {
                show_help();
                break;
            }
            case 'v': {
                // TODO: fill the vector and run through the vector
                VendorAtom sampleAtom = buildVendorAtom();
                ndk::ScopedAStatus ret = service->reportVendorAtom(sampleAtom);
                if (!ret.isOk()) {
                    std::cerr << "reportVendorAtom failed: " << ret.getServiceSpecificError()
                              << ". Message: " << ret.getMessage() << std::endl;
                    ++failed_calls;
                }
                ++hal_calls;
                VendorAtom sampleAtomWithAnnotations = buildVendorAtomWithAnnotations();
                ret = service->reportVendorAtom(sampleAtomWithAnnotations);
                if (!ret.isOk()) {
                    std::cerr << "reportVendorAtom failed: " << ret.getServiceSpecificError()
                              << ". Message: " << ret.getMessage() << std::endl;
                    ++failed_calls;
                }
                ++hal_calls;
                break;
            }
            default: {
                show_help();
                return 1;
            }
        }
    }

    if (hal_calls > 0) {
        std::cout << hal_calls << " HAL methods called.\n";
        std::cout << "try: logcat | grep \"statsd.*0x1000\"\n";
    }

    return failed_calls;
}
