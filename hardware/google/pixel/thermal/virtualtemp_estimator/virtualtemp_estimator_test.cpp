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

/**
 *@file  virtualtemp_estimator_test.cc
 * Test application to verify virtualtemp estimator
 *
 */
// Test application to run and verify virtualtemp estimator interface unit tests

#include "virtualtemp_estimator.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/parsedouble.h>
#include <android-base/properties.h>
#include <android-base/strings.h>
#include <cutils/properties.h>
#include <cutils/trace.h>
#include <json/reader.h>
#include <json/value.h>
#include <json/writer.h>
#include <log/log.h>
#include <malloc.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/time.h>

#include <climits>
#include <fstream>
#include <iostream>

constexpr std::string_view kDefaultModel("/vendor/etc/vt_estimation_model.tflite");
constexpr std::string_view kConfigProperty("vendor.thermal.config");
constexpr std::string_view kConfigDefaultFileName("thermal_info_config.json");
constexpr int kmillion = 1000000;
constexpr int klog_interval_usec = 10 * kmillion;

static inline unsigned long get_elapsed_time_usec(struct timeval start, struct timeval end) {
    unsigned long elapsed_time = (end.tv_sec - start.tv_sec) * kmillion;
    elapsed_time += (end.tv_usec - start.tv_usec);

    return elapsed_time;
}

static std::vector<std::string> get_input_combination(std::string_view thermal_config_path) {
    std::vector<std::string> result;
    std::string json_doc;
    if (!android::base::ReadFileToString(thermal_config_path.data(), &json_doc)) {
        std::cout << "Failed to read JSON config from " << thermal_config_path.data();
        return result;
    }

    Json::Value root;
    Json::CharReaderBuilder reader_builder;
    std::unique_ptr<Json::CharReader> reader(reader_builder.newCharReader());
    std::string errorMessage;

    if (!reader->parse(&*json_doc.begin(), &*json_doc.end(), &root, &errorMessage)) {
        std::cout << "Failed to parse JSON config: " << errorMessage;
        return result;
    }

    Json::Value sensors = root["Sensors"];
    std::cout << "Sensors size: " << sensors.size() << std::endl;

    for (Json::Value::ArrayIndex i = 0; i < sensors.size(); ++i) {
        const std::string &name = sensors[i]["Name"].asString();
        if (name == "VIRTUAL-SKIN-MODEL") {
            Json::Value values = sensors[i]["Combination"];
            if (values.size() == 0) {
                return result;
            }

            std::cout << "Combination : [";
            for (Json::Value::ArrayIndex j = 0; j < values.size(); ++j) {
                result.push_back(values[j].asString());
                std::cout << result.back() << ", ";
            }
            std::cout << "]" << std::endl;
        }
    }

    return result;
}

static int run_random_input_inference(std::string_view model_path,
                                      std::string_view thermal_config_path, int min_inference_count,
                                      int inference_delay_sec, int prev_samples_order) {
    float output;
    unsigned long prev_log_time = 0;
    thermal::vtestimator::VtEstimatorStatus ret;
    std::vector<std::string> input_combination = get_input_combination(thermal_config_path.data());
    int input_size = input_combination.size();
    thermal::vtestimator::VirtualTempEstimator vt_estimator_(thermal::vtestimator::kUseMLModel,
                                                             input_size);
    ::thermal::vtestimator::VtEstimationInitData init_data(thermal::vtestimator::kUseMLModel);
    init_data.ml_model_init_data.model_path = model_path;
    init_data.ml_model_init_data.prev_samples_order = prev_samples_order;
    init_data.ml_model_init_data.use_prev_samples = (prev_samples_order > 1) ? true : false;

    std::cout << "Initialize estimator\n";
    ret = vt_estimator_.Initialize(init_data);
    if (ret != thermal::vtestimator::kVtEstimatorOk) {
        std::cout << "Failed to Initialize estimator (ret: " << ret << ")\n";
        return -1;
    }

    struct timeval start_loop_time;
    int inference_count = 0;
    unsigned long max_inference_time = 0, min_inference_time = ULONG_MAX;
    unsigned long sum_inference_time = 0;
    float avg_inference_time = 0;
    std::vector<unsigned long> inference_times;

    std::srand(time(NULL));
    gettimeofday(&start_loop_time, nullptr);
    do {
        struct timeval begin, end;
        std::vector<float> thermistors;

        // preparing random inputs with starting temperature between 0C to 50C
        int r = std::rand() % 50000;
        for (int i = 0; i < input_size; ++i) {
            thermistors.push_back(r + i * 1000);
        }

        gettimeofday(&begin, nullptr);
        ret = vt_estimator_.Estimate(thermistors, &output);
        gettimeofday(&end, nullptr);
        if (ret != thermal::vtestimator::kVtEstimatorOk) {
            std::cout << "Failed to run estimator (ret: " << ret << ")\n";
            return -1;
        }

        std::cout << "inference_count: " << inference_count << " random_value (r): " << r
                  << " output: " << output << "\n";

        if (output > 55000) {
            std::cout << "Temperature above 55C observed\n";
            return -1;
        }

        unsigned long inference_time_usec = get_elapsed_time_usec(begin, end);

        inference_count++;
        max_inference_time = std::max(max_inference_time, inference_time_usec);
        min_inference_time = std::min(min_inference_time, inference_time_usec);
        sum_inference_time += inference_time_usec;
        avg_inference_time = sum_inference_time / inference_count;
        inference_times.push_back(inference_time_usec);

        unsigned long elapsed_time = get_elapsed_time_usec(start_loop_time, end);
        if (elapsed_time - prev_log_time >= klog_interval_usec) {
            std::cout << "elapsed_time_sec: " << elapsed_time / kmillion
                      << " inference_count: " << inference_count
                      << " min_inference_time: " << min_inference_time
                      << " max_inference_time: " << max_inference_time
                      << " avg_inference_time: " << avg_inference_time << std::endl;
            prev_log_time = elapsed_time;
        }

        if (inference_delay_sec)
            sleep(inference_delay_sec);
    } while (inference_count < min_inference_count);

    std::cout << "\n\ntotal inference count: " << inference_count << std::endl;
    std::cout << "total inference time: " << sum_inference_time << std::endl;
    std::cout << "avg_inference_time: " << avg_inference_time << std::endl;
    std::cout << "min_inference_time: " << min_inference_time << std::endl;
    std::cout << "max_inference_time: " << max_inference_time << std::endl;

    std::sort(inference_times.begin(), inference_times.end());
    std::cout << "\n\n";
    std::cout << "p50: " << inference_times[inference_count * 0.5] << std::endl;
    std::cout << "p90: " << inference_times[inference_count * 0.9] << std::endl;

    return 0;
}

static int run_single_inference(std::string_view model_path, char *input, int prev_samples_order) {
    if (!input) {
        std::cout << "input is nullptr" << std::endl;
        return -1;
    }

    std::vector<float> thermistors;
    char *ip = input;
    char *saveptr;

    std::cout << "Parsing thermistors from input string: ";
    ip = strtok_r(ip, " ", &saveptr);
    while (ip) {
        float thermistor_value;

        if (sscanf(ip, "%f", &thermistor_value) != 1) {
            std::cout << "inputs parsing failed";
        }

        std::cout << thermistor_value << " ";
        thermistors.push_back(thermistor_value);

        ip = strtok_r(NULL, " ", &saveptr);
    }
    std::cout << std::endl;
    std::cout << "thermistors.size(): " << thermistors.size() << std::endl;

    float output;
    thermal::vtestimator::VtEstimatorStatus ret;
    thermal::vtestimator::VirtualTempEstimator vt_estimator_(thermal::vtestimator::kUseMLModel,
                                                             thermistors.size());
    ::thermal::vtestimator::VtEstimationInitData init_data(thermal::vtestimator::kUseMLModel);
    init_data.ml_model_init_data.model_path = model_path;
    init_data.ml_model_init_data.prev_samples_order = prev_samples_order;
    init_data.ml_model_init_data.use_prev_samples = (prev_samples_order > 1) ? true : false;

    std::cout << "Initialize estimator\n";
    ret = vt_estimator_.Initialize(init_data);
    if (ret != thermal::vtestimator::kVtEstimatorOk) {
        std::cout << "Failed to Initialize estimator (ret: " << ret << ")\n";
        return -1;
    }

    std::cout << "run estimator\n";
    ret = vt_estimator_.Estimate(thermistors, &output);
    if (ret != thermal::vtestimator::kVtEstimatorOk) {
        std::cout << "Failed to run estimator (ret: " << ret << ")\n";
        return -1;
    }

    std::cout << "output: " << output << std::endl;
    return 0;
}

static int run_batch_process(std::string_view model_path, std::string_view thermal_config_path,
                             const char *input_file, const char *output_file,
                             int prev_samples_order) {
    if (!input_file || !output_file) {
        std::cout << "input and output files required for batch process\n";
        return -1;
    }

    std::cout << "get_input_combination(): ";
    std::vector<std::string> input_combination = get_input_combination(thermal_config_path.data());
    if (input_combination.size() == 0) {
        LOG(ERROR) << "Invalid input_combination";
        return -1;
    }

    thermal::vtestimator::VtEstimatorStatus ret;
    thermal::vtestimator::VirtualTempEstimator vt_estimator_(thermal::vtestimator::kUseMLModel,
                                                             input_combination.size());
    ::thermal::vtestimator::VtEstimationInitData init_data(thermal::vtestimator::kUseMLModel);
    init_data.ml_model_init_data.model_path = model_path;
    init_data.ml_model_init_data.prev_samples_order = prev_samples_order;
    init_data.ml_model_init_data.use_prev_samples = (prev_samples_order > 1) ? true : false;

    std::cout << "Initialize estimator\n";
    ret = vt_estimator_.Initialize(init_data);
    if (ret != thermal::vtestimator::kVtEstimatorOk) {
        std::cout << "Failed to Initialize estimator (ret: " << ret << ")\n";
        return -1;
    }

    std::string json_doc;
    if (!android::base::ReadFileToString(input_file, &json_doc)) {
        LOG(ERROR) << "Failed to read JSON config from " << input_file;
        return -1;
    }
    Json::Value root;
    Json::CharReaderBuilder reader_builder;
    std::unique_ptr<Json::CharReader> reader(reader_builder.newCharReader());
    std::string errorMessage;

    if (!reader->parse(&*json_doc.begin(), &*json_doc.end(), &root, &errorMessage)) {
        LOG(ERROR) << "Failed to parse JSON config: " << errorMessage;
        return -1;
    }

    std::cout << "Number of testcases " << root.size() << std::endl;

    for (auto const &testcase_name : root.getMemberNames()) {
        if (testcase_name == "Metadata") {
            continue;
        }

        Json::Value testcase = root[testcase_name];
        Json::Value model_vt_outputs;
        int loop_count = testcase[input_combination[0]].size();

        std::cout << "tc: " << testcase_name << " count: " << loop_count << std::endl;
        for (int i = 0; i < loop_count; ++i) {
            std::vector<float> model_inputs;
            float model_output;
            int num_inputs = input_combination.size();
            constexpr int kCelsius2mC = 1000;

            for (int j = 0; j < num_inputs; ++j) {
                std::string input_name = input_combination[j];
                std::string value_str = testcase[input_name][std::to_string(i)].asString();

                std::cout << "tc[" << testcase_name << "] entry[" << i << "] input[" << input_name
                          << "] value_str[" << value_str << "]\n";

                float value;
                if (android::base::ParseFloat(value_str, &value) == false) {
                    std::cout << "Failed to parse value_str : " << value_str << " to float\n";
                }

                model_inputs.push_back(value * kCelsius2mC);
            }

            ret = vt_estimator_.Estimate(model_inputs, &model_output);
            if (ret != thermal::vtestimator::kVtEstimatorOk) {
                std::cout << "Failed to run estimator (ret: " << ret << ")\n";
                return -1;
            }

            model_output /= kCelsius2mC;

            model_vt_outputs[std::to_string(i)] = std::to_string(model_output);
        }

        testcase["model_vt"] = model_vt_outputs;
        root[testcase_name] = testcase;
        std::cout << "completed testcase_name: " << testcase_name << std::endl;
    }

    Json::StreamWriterBuilder writer_builder;
    writer_builder["indentation"] = "";
    std::unique_ptr<Json::StreamWriter> writer(writer_builder.newStreamWriter());
    std::ofstream output_stream(output_file, std::ofstream::out);
    writer->write(root, &output_stream);

    return 0;
}

void print_usage() {
    std::string message = "usage: \n";
    message += "-m : input mode (";
    message += "0: single inference ";
    message += "1: json input file ";
    message += "2: generate random inputs) \n";
    message += "-p : path to model file \n";
    message += "-t : path to thermal config file \n";
    message += "-i : input samples (mode 0), path to input file (mode 1) \n";
    message += "-o : output file (mode 1) \n";
    message += "-d : delay between inferences in seconds (mode 2) \n";
    message += "-c : inference count (mode 2)";
    message += "-s : prev_samples_order";

    std::cout << message << std::endl;
}

int main(int argc, char *argv[]) {
    int c, mode = -1;
    char *input = nullptr, *output = nullptr;
    std::string model_path, thermal_config_path;
    int min_inference_count = -1;
    int inference_delay_sec = 0;
    int prev_samples_order = 1;

    while ((c = getopt(argc, argv, "hm:p:i:c:o:d:t:s:")) != -1) switch (c) {
            case 'm':
                mode = atoi(optarg);
                std::cout << "mode: " << mode << std::endl;
                break;
            case 'p':
                model_path = optarg;
                std::cout << "model_path: " << model_path << std::endl;
                break;
            case 's':
                prev_samples_order = atoi(optarg);
                std::cout << "prev_samples_order: " << prev_samples_order << std::endl;
                break;
            case 't':
                thermal_config_path = optarg;
                std::cout << "thermal_config_path: " << thermal_config_path << std::endl;
                break;
            case 'i':
                input = optarg;
                std::cout << "input: " << input << std::endl;
                break;
            case 'o':
                output = optarg;
                std::cout << "output: " << output << std::endl;
                break;
            case 'c':
                min_inference_count = atoi(optarg);
                std::cout << "min_inference_count: " << min_inference_count << std::endl;
                break;
            case 'd':
                inference_delay_sec = atoi(optarg);
                std::cout << "inference_delay_sec : " << inference_delay_sec << std::endl;
                break;
            case 'h':
                print_usage();
                return 0;
            default:
                std::cout << "unsupported option " << c << std::endl;
                abort();
        }

    if (model_path.empty()) {
        model_path = kDefaultModel;
        std::cout << "Using default model_path: " << model_path << std::endl;
    }

    if (thermal_config_path.empty()) {
        thermal_config_path =
                "/vendor/etc/" +
                android::base::GetProperty(kConfigProperty.data(), kConfigDefaultFileName.data());
        std::cout << "Using default thermal config: " << thermal_config_path << std::endl;
    }

    int ret = -1;
    switch (mode) {
        case 0:
            ret = run_single_inference(model_path, input, prev_samples_order);
            break;
        case 1:
            ret = run_batch_process(model_path, thermal_config_path, input, output,
                                    prev_samples_order);
            break;
        case 2:
            ret = run_random_input_inference(model_path, thermal_config_path, min_inference_count,
                                             inference_delay_sec, prev_samples_order);
            break;
        default:
            std::cout << "unsupported mode" << std::endl;
            print_usage();
            break;
    }

    std::cout << "Exiting" << std::endl;
    fflush(stdout);

    return ret;
}
