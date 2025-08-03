# Build environment for Floss
#
# This dockerfile generates the build environment required to build Floss, which
# is the Linux build for the Fluoride Bluetooth stack.

# Inherit from a recent Debian version. The slim version is a smaller variant
# meant for containers.
# This digest is taken from the tag debian:bookworm-slim (if you want to update)
FROM debian@sha256:b66f66d473ef3128436ba2812198edcae86c268eb530dff44ff6ae26f9a2ee30

# First install all required apt packages.
RUN apt-get update && \
    apt-get install -y \
    bison \
    build-essential \
    clang \
    cmake \
    curl \
    debmake \
    flatbuffers-compiler \
    flex \
    g++-multilib \
    gcc-multilib \
    generate-ninja \
    gnupg \
    gperf \
    libabsl-dev \
    libc++abi-dev \
    libc++-dev \
    libdbus-1-dev \
    libdouble-conversion-dev \
    libevent-dev \
    libflatbuffers-dev \
    libfmt-dev \
    libgl1-mesa-dev \
    libglib2.0-dev \
    libgtest-dev \
    libgmock-dev \
    liblc3-dev \
    liblz4-tool \
    libncurses5 \
    libnss3-dev \
    libprotobuf-dev \
    libre2-9 \
    libre2-dev \
    libssl-dev \
    libtinyxml2-dev \
    libx11-dev \
    libxml2-utils \
    ninja-build \
    openssl \
    protobuf-compiler \
    python3 \
    unzip \
    x11proto-core-dev \
    xsltproc \
    zip \
    zlib1g-dev \
    ;

# Next install the Rust toolchain. Download the toolchain to the local folder
# using curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs
ADD rustup/rustup.sh /tmp
RUN /tmp/rustup.sh -y --default-toolchain 1.68.2

# Add .cargo/bin to $PATH
ENV PATH="/root/.cargo/bin:${PATH}"

# Install cargo packages required on build image.
RUN cargo install --locked cxxbridge-cmd@1.0.94 pdl-compiler@0.1.1 grpcio-compiler@0.13.0

# Rename llvm packages. By default, they are named 11vm-ar-13, etc. which won't
# work properly with the build.
ADD llvm-rename.sh /tmp
RUN /tmp/llvm-rename.sh 14

# At this point, the base container is ready. Now we need to build and install
# both libchrome and modp-b64. If you ran this via `docker-build-image.py`, this
# will be done after the image is created and tagged. Otherwise, you need to
# manually mount the source and run the dpkg builders in `system/build/dpkg`.
