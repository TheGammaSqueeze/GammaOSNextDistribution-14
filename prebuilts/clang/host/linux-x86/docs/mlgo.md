# Training Regalloc MLGO model for Android Clang

## Background

MLGO is a framework for integrating ML techniques systematically in Clang. It
replaces human-crafted optimization heuristics with machine learned models to
decide which live range to evict with Reinforcement Learning (RL) on a corpus
extracted from AOSP.

This guide goes through how to re-train MLGO models on AOSP.

## Preparation

Create a working directory (e.g. `android`) and set up the `WORKING_DIR`
environment variable.

```sh
mkdir ~/android-mlgo; cd ~/android-mlgo
export WORKING_DIR=`pwd`
```

### Get Repositories

#### ml-compiler-opt

```sh
cd $WORKING_DIR
git clone https://github.com/google/ml-compiler-opt
```

#### aosp-master-plus-llvm

```sh
cd $WORKING_DIR
mkdir aosp-master-plus-llvm; cd aosp-master-plus-llvm
repo init -u https://android.googlesource.com/platform/manifest -b master-plus-llvm --partial-clone --use-superproject --depth=1
repo sync -c
```

### Set up Tensorflow

First, install Python Virtualenv:

```
sudo apt install python3-venv
```

You only need to run the above command once.

Now, set up a Virtualenv and install Tensorflow and other dependencies:

```sh
cd $WORKING_DIR
python3 -m venv venv
source venv/bin/activate
pip install tensorflow-cpu gin-config cloudpickle psutil tf_agents
```

### Set up TFLite

```sh
mkdir $WORKING_DIR/tflite; cd $WORKING_DIR/tflite
$WORKING_DIR/ml-compiler-opt/buildbot/build_tflite.sh
```

## Build LLVM for ML training

You do not need the full toolchain for ML training.

```sh
mkdir $WORKING_DIR/llvm-build; cd $WORKING_DIR/llvm-build
export CLANG_VER=`grep ClangDefaultVersion.*clang-r $WORKING_DIR/aosp-master-plus-llvm/build/soong/cc/config/global.go | tr -s ' ' | cut -d' ' -f3 | tr -d '"'`
CC=$WORKING_DIR/aosp-master-plus-llvm/prebuilts/clang/host/linux-x86/$CLANG_VER/bin/clang \
CXX=$WORKING_DIR/aosp-master-plus-llvm/prebuilts/clang/host/linux-x86/$CLANG_VER/bin/clang++ \
$WORKING_DIR/aosp-master-plus-llvm/prebuilts/cmake/linux-x86/bin/cmake -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DLLVM_ENABLE_PROJECTS="clang" \
  -DLLVM_TARGETS_TO_BUILD="X86;ARM;AArch64" \
  -C $WORKING_DIR/tflite/tflite.cmake \
  $WORKING_DIR/aosp-master-plus-llvm/toolchain/llvm-project/llvm
$WORKING_DIR/aosp-master-plus-llvm/prebuilts/build-tools/linux-x86/bin/ninja
```

## Training

### Build AOSP

```sh
cd $WORKING_DIR/aosp-master-plus-llvm
source build/envsetup.sh
lunch aosp_arm64-trunk_staging-userdebug
USE_RBE=false \
  SOONG_GEN_COMPDB=true \
  THINLTO_EMIT_INDEXES_AND_IMPORTS=true \
  m
```

### Corpus extraction

```sh
cd $WORKING_DIR/ml_compiler_opt
PYTHONPATH=$PYTHONPATH:. python3 compiler_opt/tools/extract_ir.py \
  --cmd_filter="^-O2|-O3" \
  --llvm_objcopy_path=$WORKING_DIR/llvm-build/bin/llvm-objcopy \
  --output_dir=$WORKING_DIR/corpus \
  --thinlto_build=local \
  --obj_base_dir=$WORKING_DIR/aosp-master-plus-llvm/out
```

Edit `corpus_description.json` and add the following to the
`global_command_override` section:

```
  "-march=armv8.2-a",
  "-mcpu=cortex-a55",
  "--target=aarch64-linux-android10000",
  "-fPIC",
  "-fno-exceptions",
  "-no-canonical-prefixes",
  "-O2",
  "-mllvm",
  "-import-instr-limit=40",
  "-nostdlib++",
  "-c"
```

Search for lines with `android_arm_armv8-a` and remove them. We only train for
ARM64 targets.

```sh
sed -i "/android_arm_armv8-a/d" corpus/corpus_description.json
```

Now you should have a properly prepared AOSP ThinLTO corpus.

### Collect the Default Trace and Generate Vocab

Follow the remaining steps listed in
[the Chromium MLGO training demo](https://github.com/google/ml-compiler-opt/blob/main/docs/regalloc-demo/demo.md#collect-the-default-trace-and-generate-vocab)
, beginning from the "Collect the Default Trace and Generate Vocab" section.