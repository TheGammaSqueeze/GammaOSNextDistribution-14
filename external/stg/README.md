# Symbol-Type Graph (STG)

The STG (symbol-type graph) is an ABI representation and this
project contains tools for the creation and comparison of such
representations.

The ABI extraction tool, `stg`, emits a native ABI format. Parsers exist for
libabigail's XML format, BTF and ELF / DWARF.

The ABI diff tool, `stgdiff`, supports multiple reporting options.

STG has a versioned native file format. Older formats can be read and
rewritten as the latest.

NOTE: STG is under active developement. Tool arguments and behaviour are
subject to change.

## Getting STG

### Distributions

We intend to package STG for major distributions. Currently we have
packages as follows:

| *Distribution*   | *Package*                                             |
| ---------------- | ----------------------------------------------------- |
| Arch Linux (AUR) | [stg-git](https://aur.archlinux.org/packages/stg-git) |

### Source Code

This source code is available at
https://android.googlesource.com/platform/external/stg/.

## Building from Source

Instructions are included for local and Docker builds.

### Dependencies

STG is written in C++20. It is known to compile with GCC 11, Clang 15 or
later versions.

| *Dependency*  | *Debian*          | *RedHat*          |
| ------------- | ----------------- | ----------------- |
| build         | cmake             | cmake             |
| ELF, BTF      | libelf-dev        | elfutils-devel    |
| DWARF         | libdw-dev         | elfutils-devel    |
| XML           | libxml2-dev       | libxml2-devel     |
| BTF           | linux-libc-dev    | kernel-headers    |
| native format | libprotobuf-dev   | protobuf-devel    |
| native format | protobuf-compiler | protobuf-compiler |
| allocator[^1] | libjemalloc-dev   | jemalloc-devel    |
| catch2[^2]    | catch2            | catch2-devel      |

[^1]: jemalloc is optional, but will likely improve performance.
[^2]: catch2 is optional, but required to build the test suite.

### Local Build

Build STG using CMake as follows:

```bash
$ mkdir build && cd build
$ cmake ..
$ cmake --build . --parallel
```

Run the STG unit test suite:

```bash
$ ctest
```

### Docker Build

A [Dockerfile](Dockerfile) is provided to build a container with the
STG tools:

```bash
$ docker build -t stg .
```

And then enter the container:

```bash
$ docker run -it stg
```

Note that the Dockerfile provides only a production image. To use
Docker as a development environment, you can comment out everything
after the line `# second stage`.

After that you may bind your development code to the container:

```bash
$ docker run -it $PWD:/src -it stg
```

The source code is added to `/src`, so when your code is bound you can
edit on your host and re-compile in the container.

## Contributions

See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## Contact Information

Please send feedback, questions and bug reports to
[kernel-team@android.com](mailto:kernel-team@android.com).
