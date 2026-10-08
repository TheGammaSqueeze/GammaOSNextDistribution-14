# Xbyak Sample Programs

This directory contains sample programs demonstrating various features of the Xbyak JIT assembler library.

## Sample Programs

- **test0.cpp** (test/test64) - Basic Xbyak functionality tests
- **bf.cpp** (bf/bf64) - Brainf*ck interpreter using JIT compilation
- **calc.cpp** (calc/calc64) - Calculator using Boost.Spirit parser (requires Boost)
- **memfunc.cpp** (memfunc/memfunc64) - Member function JIT compilation example
- **test_util.cpp** (test_util/test_util64) - CPU feature detection and cache topology
- **cputopology.cpp** (cputopology) - CPU cache topology API demonstration (CpuTopology, CpuCache, LogicalCpu, CpuMask)
- **jmp_table.cpp** (jmp_table/jmp_table64) - Jump table demonstration
- **zero_upper.cpp** (zero_upper) - AVX zero upper demonstration
- **ccmp.cpp** (ccmp) - Conditional compare instructions
- **no_flags.cpp** (no_flags) - Instructions without flag modifications
- **quantize.cpp** (quantize) - Quantization example
- **toyvm.cpp** (toyvm) - Simple toy virtual machine with JIT
- **profiler.cpp** (profiler) - Performance profiling example
- **static_buf.cpp** (static_buf/static_buf64) - Static buffer example (Linux only)
- **memfd.cpp** (memfd) - Memory file descriptor example (Linux only)

## Building with CMake (Recommended)

CMake provides cross-platform build support for Windows, Linux, and macOS.

### Basic Build (64-bit targets)

```bash
mkdir build
cd build
cmake ..
cmake --build . --config Release
```

### Clean build
```bash
#clean build
cmake --build . --target clean
#clean then build (Recommended)
cmake --build . --clean-first --config Release
```

### Clean Fresh start (most thorough)
```bash
# Delete build directory and reconfigure
rm -rf build
mkdir build
cd build
cmake ..
cmake --build . --config Release
```

### Running Examples

**On Windows:**
```bash
.\bin\Release\test_util64.exe
.\bin\Release\test64.exe
.\bin\Release\bf64.exe
```

**On Linux/macOS:**
```bash
./bin/test_util64
./bin/test64
./bin/bf64
```

### Building Specific Targets

```bash
# Build only test_util64
cmake --build . --config Release --target test_util64

# Build only profiler
cmake --build . --config Release --target profiler
```

### Optional 32-bit Targets (Linux only)

To build 32-bit targets on a 64-bit Linux system, you need multilib support:

```bash
# Install multilib (Ubuntu/Debian)
sudo apt-get install gcc-multilib g++-multilib

# Configure with 32-bit targets enabled
cmake -DBUILD_32BIT_TARGETS=ON ..
cmake --build . --config Release
```

This will build additional 32-bit versions: `test`, `bf`, `toyvm`, `test_util`, `memfunc`, `static_buf`, `jmp_table`, and `calc` (if Boost is available).

### Boost Support

The `calc` and `calc64` targets require Boost libraries. CMake will automatically detect Boost and build these targets if available.

**Installing Boost:**
- **Windows**: Download from https://www.boost.org/ or use vcpkg
- **Linux**: `sudo apt-get install libboost-all-dev`
- **macOS**: `brew install boost`

### CMake Options

- `BUILD_32BIT_TARGETS=ON/OFF` - Enable 32-bit target builds (default: OFF, Linux 64-bit only)
- `BUILD_PROFILER_VTUNE=ON/OFF` - Build profiler with VTune support (default: OFF, requires Intel VTune)

```bash
# Example with options
cmake -DBUILD_32BIT_TARGETS=ON -DBUILD_PROFILER_VTUNE=ON ..
```

## Building with Make (Linux/macOS)

```bash
# Build all targets
make

# Build specific target
make test64
make bf64
make test_util64

# Clean
make clean
```

The Makefile automatically detects:
- System architecture (32-bit vs 64-bit)
- Boost availability
- Platform (Linux vs macOS)

## Building with Visual Studio (Windows)

Individual Visual Studio project files are provided for quick builds on Windows.

### Using Visual Studio IDE

1. Open the desired `.vcxproj` file in Visual Studio
2. Select configuration (Debug/Release) and platform (x86/x64)
3. Build → Build Solution (or press F7)
4. Run the executable from the output directory

**Available project files:**
- `bf.vcxproj` - Brainf*ck interpreter
- `calc.vcxproj` - Calculator (requires Boost)
- `calc2.vcxproj` - Alternative calculator (requires Boost)
- `quantize.vcxproj` - Quantization example
- `test0.vcxproj` - Basic tests
- `test_util.vcxproj` - CPU utility
- `toyvm.vcxproj` - Toy VM

### Using MSBuild (Command Line)

```powershell
# Build with MSBuild
msbuild test0.vcxproj /p:Configuration=Release /p:Platform=x64

# Or use the solution file if available
msbuild xbyak_samples.sln /p:Configuration=Release
```

## Troubleshooting

### CMake: "Cannot find Boost"
If Boost is installed but not detected:
```bash
cmake -DBOOST_ROOT=/path/to/boost ..
```

### Linux: "cannot find -lstdc++"
Install the C++ standard library:
```bash
sudo apt-get install libstdc++-dev
```

### Linux: "undefined reference to pthread"
This should be handled automatically by CMake. If issues persist, ensure pthreads is available:
```bash
sudo apt-get install libpthread-stubs0-dev
```

### Windows: Static buffer and memfd targets fail
These targets are Linux-specific and use Linux memory mapping APIs. They are automatically excluded on Windows.

## Notes

- **64-bit vs 32-bit**: On 64-bit systems, the `*64` suffix indicates 64-bit builds. 32-bit builds have no suffix.
- **Platform-specific**: Some samples (like `static_buf` and `memfd`) only work on Linux
- **Boost dependency**: The `calc` samples require Boost.Spirit and are only built if Boost is detected
- **Warning flags**: The build reads compiler warnings from `../test/CFLAGS_WARN.cfg` (GCC/Clang only)

## Running Brainf*ck Examples

Several Brainf*ck programs are included:

```bash
# On Windows
.\build\bin\Release\bf64.exe echo.bf
.build\bin\Release\bf64.exe hello.bf
.build\bin\Release\bf64.exe fizzbuzz.bf

# On Linux
./build/bin/bf64 echo.bf
./build/bin/bf64 hello.bf
./build/bin/bf64 fizzbuzz.bf
```

## Additional Resources

- [Xbyak Documentation](https://github.com/herumi/xbyak)
- [Main README](../readme.md)
- [Usage Guide](../doc/usage.md)
