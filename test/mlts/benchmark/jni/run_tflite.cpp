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

#include "run_tflite.h"

#include <android/log.h>
#include <dirent.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <ftw.h>
#include <sys/time.h>
#include <unistd.h>

#include <cstdio>
#include <fstream>

#include "tensorflow/lite/delegates/nnapi/nnapi_delegate.h"
#include "tensorflow/lite/nnapi/NeuralNetworksTypes.h"

#include "tensorflow/lite/kernels/register.h"
#include "tensorflow/lite/nnapi/sl/include/SupportLibrary.h"

#define LOG_TAG "NN_BENCHMARK"

#define FATAL(fmt, ...)                                                  \
  do {                                                                   \
    __android_log_print(ANDROID_LOG_FATAL, LOG_TAG, fmt, ##__VA_ARGS__); \
    assert(false);                                                       \
  } while (0)

namespace {

long long currentTimeInUsec() {
  timeval tv;
  gettimeofday(&tv, NULL);
  return ((tv.tv_sec * 1000000L) + tv.tv_usec);
}

// Workaround for build systems that make difficult to pick the correct NDK API
// level. NDK tracing methods are dynamically loaded from libandroid.so.
typedef void* (*fp_ATrace_beginSection)(const char* sectionName);
typedef void* (*fp_ATrace_endSection)();
struct TraceFunc {
  fp_ATrace_beginSection ATrace_beginSection;
  fp_ATrace_endSection ATrace_endSection;
};
TraceFunc setupTraceFunc() {
  void* lib = dlopen("libandroid.so", RTLD_NOW | RTLD_LOCAL);
  if (lib == nullptr) {
    FATAL("unable to open libandroid.so");
  }
  return {
      reinterpret_cast<fp_ATrace_beginSection>(
          dlsym(lib, "ATrace_beginSection")),
      reinterpret_cast<fp_ATrace_endSection>(dlsym(lib, "ATrace_endSection"))};
}
static TraceFunc kTraceFunc{setupTraceFunc()};

// Returns the number of partitions associated, as result of a call to
// ModifyGraphWithDelegate, to the given delegate.
int CountPartitionsDelegatedTo(tflite::Subgraph* subgraph,
                               const TfLiteDelegate* delegate) {
  return std::count_if(
      subgraph->nodes_and_registration().begin(),
      subgraph->nodes_and_registration().end(),
      [delegate](
          std::pair<TfLiteNode, TfLiteRegistration> node_and_registration) {
        return node_and_registration.first.delegate == delegate;
      });
}

// Returns the number of partitions associated, as result of a call to
// ModifyGraphWithDelegate, to the given delegate.
int CountPartitionsDelegatedTo(tflite::Interpreter* interpreter,
                               const TfLiteDelegate* delegate) {
  int result = 0;
  for (int i = 0; i < interpreter->subgraphs_size(); i++) {
    tflite::Subgraph* subgraph = interpreter->subgraph(i);

    result += CountPartitionsDelegatedTo(subgraph, delegate);
  }

  return result;
}

}  // namespace

BenchmarkModel* BenchmarkModel::create(const char* modelfile, int tfliteBackend,
                                       bool enable_intermediate_tensors_dump, int* nnapiErrno,
                                       const char* nnapi_device_name, bool mmapModel,
                                       const char* nnapi_cache_dir,
                                       const tflite::nnapi::NnApiSupportLibrary* nnApiSl) {
  BenchmarkModel* model = new BenchmarkModel();
  if (!model->init(modelfile, tfliteBackend, enable_intermediate_tensors_dump, nnapiErrno,
                   nnapi_device_name, mmapModel, nnapi_cache_dir, nnApiSl)) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to init model %s", modelfile);
    delete model;
    return nullptr;
  }
  return model;
}

bool BenchmarkModel::init(const char* modelfile, int tfliteBackend,
                          bool enable_intermediate_tensors_dump, int* nnapiErrno,
                          const char* nnapi_device_name, bool mmapModel,
                          const char* nnapi_cache_dir,
                          const tflite::nnapi::NnApiSupportLibrary* nnApiSl) {
  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "BenchmarkModel %s",
                      modelfile);
  mModelFile = modelfile;
  if (nnapi_cache_dir) {
    mCacheDir = nnapi_cache_dir;
  }
  if (nnapi_device_name) {
    mNnApiDeviceName = nnapi_device_name;
  }

  if (mmapModel) {
    // Memory map the model. NOTE this needs lifetime greater than or equal
    // to interpreter context.
    mTfliteModel = tflite::FlatBufferModel::BuildFromFile(modelfile);
  } else {
    std::ifstream t(modelfile);
    mModelBuffer = std::string((std::istreambuf_iterator<char>(t)), std::istreambuf_iterator<char>());
    mTfliteModel = tflite::FlatBufferModel::BuildFromBuffer(mModelBuffer.c_str(), mModelBuffer.size());
  }
  if (!mTfliteModel) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to load model %s",
                        modelfile);
    return false;
  }

  tflite::ops::builtin::BuiltinOpResolver resolver;
  tflite::InterpreterBuilder(*mTfliteModel, resolver)(&mTfliteInterpreter);
  if (!mTfliteInterpreter) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                        "Failed to create TFlite interpreter");
    return false;
  }

  if (enable_intermediate_tensors_dump) {
    // Make output of every op a model output. This way we will be able to
    // fetch each intermediate tensor when running with delegates.
    outputs.clear();
    for (size_t node = 0; node < mTfliteInterpreter->nodes_size(); ++node) {
      auto node_outputs =
          mTfliteInterpreter->node_and_registration(node)->first.outputs;
      outputs.insert(outputs.end(), node_outputs->data,
                     node_outputs->data + node_outputs->size);
    }
    mTfliteInterpreter->SetOutputs(outputs);
  }

  // Allow Fp16 precision for all models
  mTfliteInterpreter->SetAllowFp16PrecisionForFp32(true);

  mTfliteBackend = tfliteBackend;
  switch (mTfliteBackend) {
    case TFLITE_NNAPI: {
      tflite::StatefulNnApiDelegate::Options nnapi_options;
      nnapi_options.accelerator_name = mNnApiDeviceName.empty() ? nullptr : mNnApiDeviceName.c_str();
      __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
          "Delegating to NNAPI device '%s'", mNnApiDeviceName.c_str());
      if (nnApiSl) {
        mNnApiSl = nnApiSl;
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Using NNAPI SL");
      }
      mTfliteNnapiDelegate =
          nnApiSl
              ? std::make_unique<tflite::StatefulNnApiDelegate>(nnApiSl->getFL5(), nnapi_options)
              : std::make_unique<tflite::StatefulNnApiDelegate>(nnapi_options);
      int delegationStatus = mTfliteInterpreter->ModifyGraphWithDelegate(mTfliteNnapiDelegate.get());
      *nnapiErrno = mTfliteNnapiDelegate->GetNnApiErrno();
      if ((delegationStatus == kTfLiteOk) &&
          (*nnapiErrno == ANEURALNETWORKS_NO_ERROR)) {
        int nnapiPartitions =
          CountPartitionsDelegatedTo(mTfliteInterpreter.get(), mTfliteNnapiDelegate.get());
        if (nnapiPartitions == 0) {
          __android_log_print(
              ANDROID_LOG_ERROR, LOG_TAG,
              "NNAPI Delegate (%s) for model %s was delegated with %d partitions delegated to NNAPI!!",
              nnapi_device_name, modelfile, nnapiPartitions);

              return false;
        } else {
          __android_log_print(
              ANDROID_LOG_INFO, LOG_TAG,
              "NNAPI Delegate (%s) for model %s initialized successfully with %d partitions delegated to NNAPI",
              nnapi_device_name, modelfile, nnapiPartitions);
        }
      } else {
        __android_log_print(
              ANDROID_LOG_ERROR, LOG_TAG,
              "Failed to initialize NNAPI Delegate for model %s, nnapi_errno is %d",
              modelfile, *nnapiErrno);
          return false;
      }

    } break;
    case TFLITE_GPU: {
#if defined(NN_BENCHMARK_ENABLE_GPU)
      mGpuDelegate = TfLiteGpuDelegateV2Create(/*default options=*/nullptr);
      if (mTfliteInterpreter->ModifyGraphWithDelegate(mGpuDelegate) !=
          kTfLiteOk) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                            "Failed to initialize GPU Delegate");
        return false;
      } else {
        int gpuPartitions =
          CountPartitionsDelegatedTo(mTfliteInterpreter.get(), mGpuDelegate.get());
        if (gpuPartitions == 0) {
              ANDROID_LOG_INFO, LOG_TAG,
                "GPU Delegate (%s) for model %s initialized successfully with %d partitions delegated",
                nnapi_device_name, modelfile, gpuPartitions);
          return false;
        }
      }
#else  // !defined(NN_BENCHMARK_ENABLE_GPU)
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                            "GPU delegate requested but not enabled with "
                            "NN_BENCHMARK_ENABLE_GPU");
        return false;
#endif  // defined(NN_BENCHMARK_ENABLE_GPU)
    } break;
    default:
      break;
  }
  return true;
}

BenchmarkModel::~BenchmarkModel() {
  switch (mTfliteBackend) {
    case TFLITE_GPU: {
#if defined(NN_BENCHMARK_ENABLE_GPU)  // !defined(NN_BENCHMARK_ENABLE_GPU)
      TfLiteGpuDelegateV2Delete(mGpuDelegate);
#endif  // !defined(NN_BENCHMARK_ENABLE_GPU)
    } break;
    default:
      break;
  }
}

bool BenchmarkModel::setInput(const uint8_t* dataPtr, size_t length) {
  int input = mTfliteInterpreter->inputs()[0];
  auto* input_tensor = mTfliteInterpreter->tensor(input);

  switch (input_tensor->type) {
    case kTfLiteFloat32:
    case kTfLiteUInt8: {
      void* raw = input_tensor->data.raw;
      memcpy(raw, dataPtr, length);
      break;
    }
    default:
      __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                          "Input tensor type not supported");
      return false;
  }
  return true;
}
void BenchmarkModel::saveInferenceOutput(InferenceResult* result,
                                         int output_index) {
  int output = mTfliteInterpreter->outputs()[output_index];
  auto* output_tensor = mTfliteInterpreter->tensor(output);
  auto& sink = result->inferenceOutputs[output_index];
  sink.insert(sink.end(), output_tensor->data.uint8,
              output_tensor->data.uint8 + output_tensor->bytes);
}

void BenchmarkModel::getOutputError(const uint8_t* expected_data, size_t length,
                                    InferenceResult* result, int output_index) {
  int output = mTfliteInterpreter->outputs()[output_index];
  auto* output_tensor = mTfliteInterpreter->tensor(output);
  if (output_tensor->bytes != length) {
    FATAL("Wrong size of output tensor, expected %zu, is %zu",
          output_tensor->bytes, length);
  }

  size_t elements_count = 0;
  float err_sum = 0.0;
  float max_error = 0.0;
  switch (output_tensor->type) {
    case kTfLiteUInt8: {
      uint8_t* output_raw = mTfliteInterpreter->typed_tensor<uint8_t>(output);
      elements_count = output_tensor->bytes;
      for (size_t i = 0; i < output_tensor->bytes; ++i) {
        float err = ((float)output_raw[i]) - ((float)expected_data[i]);
        if (err > max_error) max_error = err;
        err_sum += err * err;
      }
      break;
    }
    case kTfLiteFloat32: {
      const float* expected = reinterpret_cast<const float*>(expected_data);
      float* output_raw = mTfliteInterpreter->typed_tensor<float>(output);
      elements_count = output_tensor->bytes / sizeof(float);
      for (size_t i = 0; i < output_tensor->bytes / sizeof(float); ++i) {
        float err = output_raw[i] - expected[i];
        if (err > max_error) max_error = err;
        err_sum += err * err;
      }
      break;
    }
    default:
      FATAL("Output sensor type %d not supported", output_tensor->type);
  }
  result->meanSquareErrors[output_index] = err_sum / elements_count;
  result->maxSingleErrors[output_index] = max_error;
}

bool BenchmarkModel::resizeInputTensors(std::vector<int> shape) {
  // The benchmark only expects single input tensor, hardcoded as 0.
  int input = mTfliteInterpreter->inputs()[0];
  mTfliteInterpreter->ResizeInputTensor(input, shape);
  if (mTfliteInterpreter->AllocateTensors() != kTfLiteOk) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                        "Failed to allocate tensors!");
    return false;
  }
  return true;
}

bool BenchmarkModel::runInference() {
  auto status = mTfliteInterpreter->Invoke();
  auto nnapi_errno = mTfliteNnapiDelegate
                         ? mTfliteNnapiDelegate->GetNnApiErrno()
                         : ANEURALNETWORKS_NO_ERROR;
  if (status != kTfLiteOk || nnapi_errno != ANEURALNETWORKS_NO_ERROR) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                        "Failed to invoke, tflite status: %d, nnapi errno: %d!",
                        (int)status, nnapi_errno);
    return false;
  }
  return true;
}

bool BenchmarkModel::resetStates() {
  auto status = mTfliteInterpreter->ResetVariableTensors();
  if (status != kTfLiteOk) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                        "Failed to reset variable tensors: %d!", (int)status);
    return false;
  }
  return true;
}

bool BenchmarkModel::benchmark(
    const std::vector<InferenceInOutSequence>& inOutData,
    int seqInferencesMaxCount, float timeout, int flags,
    std::vector<InferenceResult>* results) {
  if (inOutData.empty()) {
    __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
                        "Input/output vector is empty");
    return true;
  }

  float inferenceTotal = 0.0;
  for (int seqInferenceIndex = 0; seqInferenceIndex < seqInferencesMaxCount;
       ++seqInferenceIndex) {
    resetStates();

    const int inputOutputSequenceIndex = seqInferenceIndex % inOutData.size();
    const InferenceInOutSequence& seq = inOutData[inputOutputSequenceIndex];
    const bool sampleResults = (flags & FLAG_SAMPLE_BENCHMARK_RESULTS) != 0;
    for (int i = 0; i < seq.size(); ++i) {
      const InferenceInOut& data = seq[i];

      // For NNAPI systrace usage documentation, see
      // frameworks/ml/nn/common/include/Tracing.h.
      kTraceFunc.ATrace_beginSection("[NN_LA_PE]BenchmarkModel::benchmark");
      kTraceFunc.ATrace_beginSection("[NN_LA_PIO]BenchmarkModel::input");
      if (data.input) {
        setInput(data.input, data.input_size);
      } else {
        int input = mTfliteInterpreter->inputs()[0];
        auto* input_tensor = mTfliteInterpreter->tensor(input);
        if (!data.createInput((uint8_t*)input_tensor->data.raw,
                              input_tensor->bytes)) {
          __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                              "Input creation %d failed", i);
          return false;
        }
      }
      kTraceFunc.ATrace_endSection();
      long long startTime = currentTimeInUsec();
      const bool success = runInference();
      kTraceFunc.ATrace_endSection();
      long long endTime = currentTimeInUsec();
      if (!success) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Inference %d failed",
                            i);
        return false;
      }

      float inferenceTime =
          static_cast<float>(endTime - startTime) / 1000000.0f;
      size_t outputsCount = mTfliteInterpreter->outputs().size();
      InferenceResult result{
          inferenceTime, {}, {}, {}, inputOutputSequenceIndex, i};
      result.meanSquareErrors.resize(outputsCount);
      result.maxSingleErrors.resize(outputsCount);
      result.inferenceOutputs.resize(outputsCount);

      if ((flags & FLAG_IGNORE_GOLDEN_OUTPUT) == 0) {
        if (outputsCount != data.outputs.size()) {
          __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                              "Golden/actual outputs (%zu/%zu) count mismatch",
                              data.outputs.size(), outputsCount);
          return false;
        }
        for (int j = 0; j < outputsCount; ++j) {
          getOutputError(data.outputs[j].ptr, data.outputs[j].size, &result, j);
        }
      }

      if ((flags & FLAG_DISCARD_INFERENCE_OUTPUT) == 0) {
        for (int j = 0; j < outputsCount; ++j) {
          saveInferenceOutput(&result, j);
        }
      }

      if (!sampleResults || (seqInferenceIndex % INFERENCE_OUT_SAMPLE_RATE) == 0) {
        results->push_back(result);
      }
      inferenceTotal += inferenceTime;
    }

    // Timeout?
    if (timeout > 0.001 && inferenceTotal > timeout) {
      return true;
    }
  }
  return true;
}

// If cacheDir is not nullptr, compilation caching will be used with NNAPI.
bool BenchmarkModel::runCompilation(const char* cacheDir, bool useNnapiSl) {
  std::unique_ptr<tflite::StatefulNnApiDelegate> delegate;
  std::unique_ptr<tflite::Interpreter> interpreter;
  tflite::ops::builtin::BuiltinOpResolver resolver;
  tflite::InterpreterBuilder(*mTfliteModel, resolver)(&interpreter);
  if (!interpreter) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to create TFlite interpreter");
    return false;
  }

  // Allow Fp16 precision for all models
  interpreter->SetAllowFp16PrecisionForFp32(true);

  if (mTfliteBackend == TFLITE_NNAPI) {
    tflite::StatefulNnApiDelegate::Options nnapi_options;
    nnapi_options.accelerator_name = mNnApiDeviceName.empty() ? nullptr : mNnApiDeviceName.c_str();
    if (cacheDir) {
      nnapi_options.cache_dir = cacheDir;
      nnapi_options.model_token = mModelFile.c_str();
    }
    if (useNnapiSl) {
      __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                          "Use NNAPI SL in compilation caching benchmark.");
      if (!mNnApiSl) {
        __android_log_print(ANDROID_LOG_ERROR,
                            LOG_TAG,
                            "NNAPI SL is null pointer when running compilation caching benchmark.");
        return false;
      }
      delegate = std::make_unique<tflite::StatefulNnApiDelegate>(mNnApiSl->getFL5(), nnapi_options);
    } else {
      delegate = std::make_unique<tflite::StatefulNnApiDelegate>(nnapi_options);
    }
    int delegationStatus = interpreter->ModifyGraphWithDelegate(delegate.get());
    auto nnapiErrno = delegate->GetNnApiErrno();
    if (delegationStatus != kTfLiteOk || nnapiErrno != ANEURALNETWORKS_NO_ERROR) {
      __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                          "Failed to initialize NNAPI Delegate for model %s, nnapi_errno is %d",
                          mModelFile.c_str(), nnapiErrno);
      return false;
    } else {
      int nnapiPartitions =
        CountPartitionsDelegatedTo(interpreter.get(), delegate.get());
      if (nnapiPartitions == 0) {
        __android_log_print(
            ANDROID_LOG_ERROR, LOG_TAG,
            "NNAPI Delegate (%s) for model %s was delegated with %d partitions delegated to NNAPI!!",
            mNnApiDeviceName.c_str(), mModelFile.c_str(), nnapiPartitions);
            return false;
      }
    }
  }
  return true;
}

// A helper class to manage the lifetime of a temporary cache directory.
class ScopedTempDirectory {
 public:
  ScopedTempDirectory(std::string base) : mBase(std::move(base)) {}
  ~ScopedTempDirectory() { cleanup(); }

  // Create a new temp directory, remove the old one if needed.
  void recreate() {
    cleanup();
    mTempDir = mBase + "/XXXXXX";
    mkdtemp(&mTempDir[0]);
  }

  // Get the path to the temp directory.
  const char* get() const { return mTempDir.empty() ? nullptr : mTempDir.c_str(); }

 private:
  void cleanup() {
    if (mTempDir.empty()) {
      return;
    }
    auto callback = [](const char* entry, const struct stat*, int, struct FTW*) {
      return remove(entry);
    };
    nftw(mTempDir.c_str(), callback, 128, FTW_DEPTH | FTW_MOUNT | FTW_PHYS);
    mTempDir.clear();
  }

  std::string mBase;
  std::string mTempDir;
};

bool BenchmarkModel::getCompilationCacheSize(int* cacheSizeBytes, bool useNnapiSl) {
  if (cacheSizeBytes == nullptr) return false;

  // Create cache files.
  ScopedTempDirectory tempDir(mCacheDir.value());
  tempDir.recreate();
  const bool success = runCompilation(tempDir.get(), useNnapiSl);
  if (!success) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Save to cache failed");
    return false;
  }

  // Compute total size of cache files.
  int totalSize = 0;
  DIR* dir = opendir(tempDir.get());
  if (dir == nullptr) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to open cache directory");
    return false;
  }
  struct dirent* dp = nullptr;
  while ((dp = readdir(dir)) != nullptr) {
    char fullPath[1024];
    snprintf(fullPath, 1024, "%s/%s", tempDir.get(), dp->d_name);
    struct stat st;
    int err = stat(fullPath, &st);
    if (err != 0) {
      closedir(dir);
      __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to stat %s", fullPath);
      return false;
    }
    // Only accumulate sizes of regular files. This will exclude '.' and '..'.
    if (S_ISREG(st.st_mode)) {
      totalSize += st.st_size;
    }
  }
  closedir(dir);
  *cacheSizeBytes = totalSize;
  return true;
}

bool BenchmarkModel::benchmarkSingleTypeOfCompilation(CompilationBenchmarkType type,
                                                      int maxNumIterations, float timeout,
                                                      bool useNnapiSl,
                                                      std::vector<float>* results) {
  if (results != nullptr) {
    results->clear();
  }
  ScopedTempDirectory tempDir(mCacheDir.value());

  // Initialize cache files to benchmark cache hit.
  if (type == CompilationBenchmarkType::PREPARE_FROM_CACHE) {
    tempDir.recreate();
    const bool success = runCompilation(tempDir.get(), useNnapiSl);
    if (!success) {
      __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Save to cache failed");
      return false;
    }
  }

  float compilationTotal = 0.0;
  for (int i = 0; i < maxNumIterations; i++) {
    const char* cacheDir = nullptr;
    switch (type) {
      case CompilationBenchmarkType::WITHOUT_CACHE:
        cacheDir = nullptr;
        break;
      case CompilationBenchmarkType::SAVE_TO_CACHE:
        // Remove the cache files from the last iteration to benchmark cache miss.
        tempDir.recreate();
        [[fallthrough]];
      case CompilationBenchmarkType::PREPARE_FROM_CACHE:
        cacheDir = tempDir.get();
        break;
      default:
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Unknown CompilationBenchmarkType: %d",
                            static_cast<int>(type));
        return false;
    }

    kTraceFunc.ATrace_beginSection("[NN_LA_PC]BenchmarkModel::benchmarkCompilation");
    const long long startTime = currentTimeInUsec();
    const bool success = runCompilation(cacheDir, useNnapiSl);
    const long long endTime = currentTimeInUsec();
    kTraceFunc.ATrace_endSection();
    if (!success) {
      __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Compilation %d failed", i);
      return false;
    }

    const float compilationTime = static_cast<float>(endTime - startTime) / 1000000.0f;
    if (results != nullptr) {
      results->push_back(compilationTime);
    }

    // Timeout?
    compilationTotal += compilationTime;
    if (timeout > 0.001 && compilationTotal > timeout) {
      return true;
    }
  }
  return true;
}

bool BenchmarkModel::benchmarkSingleTypeOfCompilationWithWarmup(CompilationBenchmarkType type,
                                                                int maxNumIterations,
                                                                float warmupTimeout,
                                                                float runTimeout,
                                                                bool useNnapiSl,
                                                                std::vector<float>* results) {
  kTraceFunc.ATrace_beginSection(
      "[NN_LA_PWM]BenchmarkModel::benchmarkSingleTypeOfCompilationWithWarmup");
  bool success = benchmarkSingleTypeOfCompilation(type,
                                                  maxNumIterations,
                                                  warmupTimeout,
                                                  useNnapiSl,
                                                  nullptr);
  kTraceFunc.ATrace_endSection();
  if (!success) return false;

  kTraceFunc.ATrace_beginSection(
      "[NN_LA_PBM]BenchmarkModel::benchmarkSingleTypeOfCompilationWithWarmup");
  success = benchmarkSingleTypeOfCompilation(type,
                                             maxNumIterations,
                                             runTimeout,
                                             useNnapiSl,
                                             results);
  kTraceFunc.ATrace_endSection();
  return success;
}

bool BenchmarkModel::benchmarkCompilation(int maxNumIterations, float warmupTimeout,
                                          float runTimeout, bool useNnapiSl,
                                          CompilationBenchmarkResult* result) {
  if (result == nullptr) return false;

  // Benchmark compile without cache.
  bool success = benchmarkSingleTypeOfCompilationWithWarmup(
          CompilationBenchmarkType::WITHOUT_CACHE, maxNumIterations,
          warmupTimeout, runTimeout, useNnapiSl,
          &result->compileWithoutCacheTimeSec);
  if (!success) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                        "Failed to benchmark compilation without cache");
    return false;
  }

  // Get compilation cache size.
  success = getCompilationCacheSize(&result->cacheSizeBytes, useNnapiSl);
  if (!success) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to retrieve compilation cache size");
    return false;
  }

  // Benchmark saving to cache and preparing from cache only if supported.
  if (result->cacheSizeBytes > 0) {
    // Benchmark saving to cache.
    auto& saveToCacheTimeSec = result->saveToCacheTimeSec.emplace();
    success = benchmarkSingleTypeOfCompilationWithWarmup(
            CompilationBenchmarkType::SAVE_TO_CACHE, maxNumIterations,
            warmupTimeout, runTimeout, useNnapiSl,
            &saveToCacheTimeSec);
    if (!success) {
      __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to benchmark saving to cache");
      return false;
    }

    // Benchmark preparing from cache.
    auto& prepareFromCacheTimeSec = result->prepareFromCacheTimeSec.emplace();
    success = benchmarkSingleTypeOfCompilationWithWarmup(
            CompilationBenchmarkType::PREPARE_FROM_CACHE, maxNumIterations,
            warmupTimeout, runTimeout, useNnapiSl,
            &prepareFromCacheTimeSec);
    if (!success) {
      __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to benchmark preparing from cache");
      return false;
    }
  }
  return result;
}

bool BenchmarkModel::dumpAllLayers(
    const char* path, const std::vector<InferenceInOutSequence>& inOutData) {
  if (inOutData.empty()) {
    FATAL("Input/output vector is empty");
  }

  for (int seqInferenceIndex = 0; seqInferenceIndex < inOutData.size();
       ++seqInferenceIndex) {
    resetStates();

    const InferenceInOutSequence& seq = inOutData[seqInferenceIndex];
    for (int i = 0; i < seq.size(); ++i) {
      const InferenceInOut& data = seq[i];
      setInput(data.input, data.input_size);
      const bool success = runInference();
      if (!success) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Inference %d failed",
                            i);
        return false;
      }

      // The order of the tensor is not sorted by the tensor index
      for (int tensor_order = 0; tensor_order < outputs.size(); ++tensor_order) {
        int tensor_index = outputs[tensor_order];
        auto* output_tensor = mTfliteInterpreter->tensor(tensor_index);
        if (output_tensor->data.raw == nullptr) {
          __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                      "output_tensor->data.raw == nullptr at index %d ", tensor_index);
          continue;
        }
        char fullpath[1024];
        snprintf(fullpath, 1024, "%s/dump_%.3d_seq_%.3d_order_%.3d_tensor_%.3d", path,
                 seqInferenceIndex, i, tensor_order, tensor_index);
        FILE* f = fopen(fullpath, "wb");
        fwrite(output_tensor->data.raw, output_tensor->bytes, 1, f);
        fclose(f);
      }
    }
  }
  return true;
}
