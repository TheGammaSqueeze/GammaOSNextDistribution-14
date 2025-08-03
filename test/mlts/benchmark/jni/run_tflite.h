/**
 * Copyright 2017 The Android Open Source Project
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

#ifndef COM_EXAMPLE_ANDROID_NN_BENCHMARK_RUN_TFLITE_H
#define COM_EXAMPLE_ANDROID_NN_BENCHMARK_RUN_TFLITE_H

#include "tensorflow/lite/delegates/gpu/delegate.h"
#include "tensorflow/lite/delegates/nnapi/nnapi_delegate.h"
#include "tensorflow/lite/interpreter.h"
#include "tensorflow/lite/model.h"
#include "tensorflow/lite/nnapi/sl/include/SupportLibrary.h"

#include <memory>
#include <unistd.h>
#include <vector>

struct InferenceOutput {
  uint8_t* ptr;
  size_t size;
};

// Inputs and expected outputs for inference
struct InferenceInOut {
  // Input can either be directly specified as a pointer or indirectly with
  // the createInput callback. This is needed for large datasets where
  // allocating memory for all inputs at once is not feasible.
  uint8_t* input;
  size_t input_size;

  std::vector<InferenceOutput> outputs;
  std::function<bool(uint8_t*, size_t)> createInput;
};

// Inputs and expected outputs for an inference sequence.
using InferenceInOutSequence = std::vector<InferenceInOut>;

// Result of a single inference
struct InferenceResult {
  float computeTimeSec;
  // MSE for each output
  std::vector<float> meanSquareErrors;
  // Max single error for each output
  std::vector<float> maxSingleErrors;
  // Outputs
  std::vector<std::vector<uint8_t>> inferenceOutputs;
  int inputOutputSequenceIndex;
  int inputOutputIndex;
};

struct CompilationBenchmarkResult {
  std::vector<float> compileWithoutCacheTimeSec;
  // The following optional fields have no value if compilation caching is not supported.
  std::optional<std::vector<float>> saveToCacheTimeSec;
  std::optional<std::vector<float>> prepareFromCacheTimeSec;
  // The total size of cache files. It is zero if compilation caching is not supported.
  int cacheSizeBytes = 0;
};

/** Discard inference output in inference results. */
const int FLAG_DISCARD_INFERENCE_OUTPUT = 1 << 0;
/** Do not expect golden output for inference inputs. */
const int FLAG_IGNORE_GOLDEN_OUTPUT = 1 << 1;
/** Collect only 1 benchmark result every INFERENCE_OUT_SAMPLE_RATE **/
const int FLAG_SAMPLE_BENCHMARK_RESULTS = 1 << 2;

const int INFERENCE_OUT_SAMPLE_RATE = 10;

enum class CompilationBenchmarkType {
  // Benchmark without cache
  WITHOUT_CACHE,
  // Benchmark cache miss
  SAVE_TO_CACHE,
  // Benchmark cache hit
  PREPARE_FROM_CACHE,
};

/** TFLite backend. */
constexpr int TFLITE_CPU = 0;
constexpr int TFLITE_NNAPI = 1;
constexpr int TFLITE_GPU = 2;

class BenchmarkModel {
 public:
  ~BenchmarkModel();

  static BenchmarkModel* create(const char* modelfile, int tfliteBackend,
                                bool enable_intermediate_tensors_dump,
                                int* nnapiErrno, const char* nnapi_device_name,
                                bool mmapModel, const char* nnapi_cache_dir,
                                const tflite::nnapi::NnApiSupportLibrary* nnApiSl = nullptr);

  bool resizeInputTensors(std::vector<int> shape);
  bool setInput(const uint8_t* dataPtr, size_t length);
  bool runInference();
  // Resets TFLite states (RNN/LSTM states etc).
  bool resetStates();

  bool benchmark(const std::vector<InferenceInOutSequence>& inOutData,
                 int seqInferencesMaxCount, float timeout, int flags,
                 std::vector<InferenceResult>* result);

  bool benchmarkCompilation(int maxNumIterations,
                            float warmupTimeout,
                            float runTimeout,
                            bool useNnapiSl,
                            CompilationBenchmarkResult* result);

  bool dumpAllLayers(const char* path,
                     const std::vector<InferenceInOutSequence>& inOutData);

 private:
  BenchmarkModel() = default;
  bool init(const char* modelfile, int tfliteBackend,
            bool enable_intermediate_tensors_dump,
            int* nnapiErrno, const char* nnapi_device_name,
            /* flag to choose between memory mapping the model and initializing
                the model from programs memory*/
            bool mmapModel,
            const char* nnapi_cache_dir,
            const tflite::nnapi::NnApiSupportLibrary* nnApiSl = nullptr);

  void getOutputError(const uint8_t* dataPtr, size_t length,
                      InferenceResult* result, int output_index);
  void saveInferenceOutput(InferenceResult* result, int output_index);

  bool runCompilation(const char* cacheDir, bool useNnapiSl);
  bool benchmarkSingleTypeOfCompilation(CompilationBenchmarkType type,
                                        int maxNumIterations,
                                        float timeout,
                                        bool useNnapiSl,
                                        std::vector<float>* results);
  bool benchmarkSingleTypeOfCompilationWithWarmup(CompilationBenchmarkType type,
                                                  int maxNumIterations,
                                                  float warmupTimeout,
                                                  float runTimeout,
                                                  bool useNnapiSl,
                                                  std::vector<float>* results);
  bool getCompilationCacheSize(int* cacheSizeBytes, bool useNnapiSl);

  std::string mModelBuffer;
  std::unique_ptr<tflite::FlatBufferModel> mTfliteModel;
  std::unique_ptr<tflite::StatefulNnApiDelegate> mTfliteNnapiDelegate;
  std::unique_ptr<tflite::Interpreter> mTfliteInterpreter;
  // Store indices of output tensors, used to dump intermediate tensors
  std::vector<int> outputs;

  // Parameters for compilation
  std::string mModelFile;
  std::optional<std::string> mCacheDir;
  std::string mNnApiDeviceName;
  const tflite::nnapi::NnApiSupportLibrary* mNnApiSl = nullptr;
#if defined(NN_BENCHMARK_ENABLE_GPU)
  TfLiteDelegate* mGpuDelegate;
#endif  // defined(NN_BENCHMARK_ENABLE_GPU)
  int mTfliteBackend;
};

#endif  // COM_EXAMPLE_ANDROID_NN_BENCHMARK_RUN_TFLITE_H
