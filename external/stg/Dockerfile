# SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
#
# Copyright 2022-2023 Google LLC
#
# Licensed under the Apache License v2.0 with LLVM Exceptions (the "License");
# you may not use this file except in compliance with the License.  You may
# obtain a copy of the License at
#
# https://llvm.org/LICENSE.txt
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
#
# Author: Vanessa Sochat
# Author: Aleksei Vetrov

ARG debian_version=stable-slim
FROM debian:${debian_version} as builder
# docker build -t stg .
RUN apt-get update && \
    apt-get install -y \
        build-essential \
        pkg-config \
        cmake \
        libelf-dev \
        libdw-dev \
        libxml2-dev \
        libprotobuf-dev \
        protobuf-compiler \
        libjemalloc-dev
WORKDIR /src
COPY . /src
RUN mkdir -p build && \
    cd build && \
    cmake -DCMAKE_BUILD_TYPE=Release .. && \
    cmake --build . --parallel && \
    cmake --install . --strip
# second stage
FROM debian:${debian_version}
RUN apt-get update && \
    apt-get install -y \
        libc6 \
        libgcc-s1 \
        libstdc++6 \
        libdw1 \
        libelf1 \
        libjemalloc2 \
        libprotobuf32 \
        libxml2 && \
    rm -rf /var/lib/apt/lists/*
COPY --from=builder /usr/local /usr/local
