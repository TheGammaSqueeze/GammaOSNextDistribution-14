#!/usr/bin/env bash

# Copyright 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Update the Rust protobufs on emu-master-dev branch
#
# scripts/cmake_setup.sh
# ninja -C objs netsimd
# repo start proto-update
# scripts/proto_update.sh
# git add rust/proto
#
# You may need to install protobuf-compiler
#
# Linux: sudo apt-get install protobuf-compiler
# Mac:   brew install protobuf

# Absolute path to tools/netsim using this scripts directory
REPO=$(dirname $(readlink -f "$0"))/..
CARGO=$REPO/rust/proto/Cargo.toml

# uncomment out lines
sed -i 's/^##//g' $CARGO

# depends on emu-master-dev branch
export CARGO_HOME=$REPO/objs/rust/.cargo

cd $REPO
cargo build --manifest-path $CARGO

# run protoc command to generate grpc proto rust files
# Possibly need to install compilers:
# $ cargo install protobuf-codegen
# $ cargo install grpcio-compiler
# TODO: Need to add required crate mappings to work in emu-master-dev
# protoc --rust_out=./rust/proto/src --grpc_out=./rust/proto/src --plugin=protoc-gen-grpc=`which grpc_rust_plugin` -I./proto -I../../external/protobuf/src -I../../packages/modules/Bluetooth/tools/rootcanal/proto ./proto/netsim/frontend.proto

# Undo changed to Cargo.toml
git checkout $CARGO

# The possible values are "linux" and "darwin".
OS=$(uname | tr '[:upper:]' '[:lower:]')

# Find the most recent rustfmt installed
RUSTFMT=`ls -d ../../prebuilts/rust/$OS-x86/*/bin/rustfmt | tail -1`

# Format rust code
find $REPO/rust/proto -name '*.rs' -exec $RUSTFMT -v {} \;

rm rust/Cargo.lock
