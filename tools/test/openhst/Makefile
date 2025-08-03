# Copyright 2020 Google LLC
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

.PHONY: help start

SHELL := /bin/bash
SRC_DIR := .
DST_DIR := .
PROTO_SRC_FILE := stress_test.proto
PROTOC_DIR := ./env

ifeq ($(OS),Windows_NT)
	detected_OS := Windows
	USER_NAME ?=
else
    detected_OS := $(shell uname)
	USER_NAME ?= $(shell whoami)
	ENV_PATH ?= $(shell pwd)
endif

ifeq ($(detected_OS),Darwin)
	PROTOC_FILE := protoc-3.11.4-osx-x86_64.zip
endif
ifeq ($(detected_OS),Linux)
	PROTOC_FILE := protoc-3.11.4-linux-x86_64.zip
endif

.DEFAULT: help
help:
	@echo "make start"
	@echo "       prepare development environment, use only once"
	@echo "make proto-compile"
	@echo "       compile protubuf"
	@echo "make clean"
	@echo "       delete test result and cache directories"

start:
ifeq ($(detected_OS),Windows)
	@echo "please install python3.7.5 and pytyon3-pip manually"
	py -m pip install --upgrade pip
	python -m venv .\env
endif
ifeq ($(detected_OS),Darwin)
	/usr/bin/ruby -e "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install)"
	sudo chown -R $(USER_NAME) /usr/local/bin /usr/local/etc /usr/local/sbin /usr/local/share
	chmod u+w /usr/local/bin /usr/local/etc /usr/local/sbin /usr/local/share
	brew install sox
	python -m venv env
endif
ifeq ($(detected_OS),Linux)
	sudo apt-get install sox
	python -m venv env
endif

proto-compile: ${PROTO_SRC_FILE}
ifeq ($(detected_OS),Windows)
	@echo "Download protoc-3.11.4-win64.zip from https://github.com/protocolbuffers/protobuf/releases/download/v3.11.4/protoc-3.11.4-win64.zip"
	@echo "Decompress protoc-3.11.4-win64.zip and save proto.exe under env/bin folder"
	@echo "Decompress protoc-3.11.4-win64.zip and copy include/* under env/include folder"
	$(PROTOC_DIR)/protoc.exe -I=${SRC_DIR} --proto_path=$(PROTOC_DIR)/include --python_out=${DST_DIR} ${SRC_DIR}/${PROTO_SRC_FILE}
else
	curl -OL https://github.com/protocolbuffers/protobuf/releases/download/v3.11.4/$(PROTOC_FILE)
	sudo unzip -o $(PROTOC_FILE) -d $(PROTOC_DIR) bin/protoc
	sudo unzip -o $(PROTOC_FILE) -d $(PROTOC_DIR) 'include/*'
	rm -f $(PROTOC_FILE)
	$(PROTOC_DIR)/bin/protoc -I=${SRC_DIR} --proto_path=$(PROTOC_DIR)/include --python_out=${DST_DIR} ${SRC_DIR}/${PROTO_SRC_FILE}
endif

clean:
ifeq ($(detected_OS),Windows)
	@for /d %%x in (dsp_*) do rd /s /q "%%x"
	@for /d %%x in (enroll_*) do rd /s /q "%%x"
	@for /d %%x in (__pycache*) do rd /s /q "%%x"
else
	@rm -rf __pycache__
	@rm -rf dsp_*
	@rm -rf enroll*
endif
	@echo "cleanning completed"
