#!/bin/bash

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

export GFXSTREAM_DIR=$(pwd)

if [ -z "$1" ];
then
    export GFXSTREAM_GUEST_DIR=$GFXSTREAM_DIR/guest
else
    export GFXSTREAM_GUEST_DIR=$1
fi

# Detect clang-format
#if ! $WHICH clang-format > /dev/null; then
#    echo "Failed to find clang-format." 1>&2
#    exit 1
#fi

export GFXSTREAM_GUEST_ENCODER_DIR=$GFXSTREAM_GUEST_DIR/vulkan_enc
export GFXSTREAM_HOST_DECODER_DIR=$GFXSTREAM_DIR/host/vulkan
export GFXSTREAM_OUTPUT_DIR=$GFXSTREAM_HOST_DECODER_DIR/cereal
export GFXSTREAM_SCRIPTS_DIR=$GFXSTREAM_DIR/scripts

export GEN_VK=$GFXSTREAM_DIR/codegen/vulkan/vulkan-docs-next/scripts/genvk.py
export VK_XML=$GFXSTREAM_DIR/codegen/vulkan/vulkan-docs-next/xml/vk.xml
export CUSTOM_XML=$GFXSTREAM_DIR/codegen/vulkan/vulkan-docs-next/xml/vk_gfxstream.xml

python3 $GEN_VK -registry $VK_XML -registryGfxstream $CUSTOM_XML cereal -o $GFXSTREAM_OUTPUT_DIR

# Should have a unified headers dir here:
python3 $GEN_VK -registry $CUSTOM_XML vulkan_gfxstream.h -o $GFXSTREAM_GUEST_ENCODER_DIR
python3 $GEN_VK -registry $CUSTOM_XML vulkan_gfxstream.h -o $GFXSTREAM_HOST_DECODER_DIR
