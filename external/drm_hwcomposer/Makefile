#!/usr/bin/make

DOCKER_BIN := $(shell command -v docker 2> /dev/null)
NPROCS:=$(shell grep -c ^processor /proc/cpuinfo)

DOCKERFILE := .ci/Dockerfile
IMAGE_NAME := drmhwc_ci

# We can't run style and bpfmt check in docker
# when repo is within AOSP tree, will run it locally.
GIT_IS_SYMLINK:=$(shell test -L .git && echo true)

define print_no_docker_err
$(warning Please install docker, e.g. for Ubuntu:)
$(warning $$ sudo apt install docker.io)
$(warning $$ sudo usermod -aG docker $$USER)
$(warning and reboot your PC)
$(error Aborting...)
endef

.PHONY : help prepare shell ci_fast ci ci_cleanup build_deploy bd local_presubmit local_cleanup
.DEFAULT_GOAL := help

help: ## Show this help
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

PREPARE:=.out/prepare_docker.timestamp
$(PREPARE): $(DOCKERFILE)
	$(if $(DOCKER_BIN),,$(call print_no_docker_err))
	mkdir -p $(dir $@)
	$(DOCKER_BIN) build -t local/build-env -f $(DOCKERFILE) .;
	$(DOCKER_BIN) stop $(IMAGE_NAME) || true
	$(DOCKER_BIN) rm $(IMAGE_NAME) || true
	$(DOCKER_BIN) run -itd --name $(IMAGE_NAME) --network="host" -v $(shell pwd):/home/user/drm_hwcomposer local/build-env
	@touch $@

prepare: $(PREPARE)
prepare: ## Build and run Docker image

shell: $(PREPARE)
shell: ## Start shell into a container
	$(DOCKER_BIN) exec -it $(IMAGE_NAME) bash

ci_fast: $(PREPARE)
ci_fast: ## Run meson build for arm64 in docker container
	@echo "Run meson cross-build for Android:"
	$(DOCKER_BIN) exec -it $(IMAGE_NAME) bash -c "make -C ~/aospless all"

ci: $(PREPARE)
ci: ## Run presubmit within the docker container
	@echo "Run native build:"
	$(DOCKER_BIN) exec -it $(IMAGE_NAME) bash -c "make -f .ci/Makefile -j$(NPROCS)"
	@echo "Run meson cross-build for Android:"
	$(DOCKER_BIN) exec -it $(IMAGE_NAME) bash -c "make -C ~/aospless all"
	@echo "Run style check:"
	$(if $(GIT_IS_SYMLINK), \
		./.ci/.gitlab-ci-checkcommit.sh, \
		$(DOCKER_BIN) exec -it $(IMAGE_NAME) bash -c "./.ci/.gitlab-ci-checkcommit.sh")
	@echo "\n\e[32m --- SUCCESS ---\n"

ci_cleanup: ## Cleanup after 'make ci'
	$(DOCKER_BIN) exec -it $(IMAGE_NAME) bash -c "make local_cleanup"
	$(DOCKER_BIN) exec -it $(IMAGE_NAME) bash -c "rm -rf ~/aospless/build"
	$(DOCKER_BIN) exec -it $(IMAGE_NAME) bash -c "rm -rf ~/aospless/install"
	$(DOCKER_BIN) exec -it $(IMAGE_NAME) bash -c "rm -rf ~/aospless/out_src"

build_deploy: $(PREPARE)
build_deploy: ## Build for Andoid and deploy onto the target device (require active adb device connected)
	$(if $(filter $(shell adb shell getprop ro.bionic.arch),arm64),,$(error arm64 only is supported at the moment))
	adb root && adb remount vendor
	mkdir -p .out/arm64
	$(DOCKER_BIN) exec -it $(IMAGE_NAME) bash -c "make -C ~/aospless all"
	$(DOCKER_BIN) exec -it $(IMAGE_NAME) bash -c "cp -r ~/aospless/install/* ~/drm_hwcomposer/.out/arm64"
	adb push .out/arm64/vendor/lib64/hw/hwcomposer.drm.so /vendor/lib64/hw/hwcomposer.drm.so
	adb shell stop
	adb shell stop vendor.hwcomposer-2-1 && adb shell start vendor.hwcomposer-2-1 || true
	adb shell stop vendor.hwcomposer-2-2 && adb shell start vendor.hwcomposer-2-2 || true
	adb shell stop vendor.hwcomposer-2-3 && adb shell start vendor.hwcomposer-2-3 || true
	adb shell stop vendor.hwcomposer-2-4 && adb shell start vendor.hwcomposer-2-4 || true
	bash -c '[[ "$$HWCLOG" -eq "1" ]] && adb logcat -c || true'
	adb shell start
	bash -c '[[ "$$HWCLOG" -eq "1" ]] && adb logcat | grep -i hwc || true'

bd: build_deploy
bd: ## Alias for build_deploy

local_presubmit: ## Run local presubmit script (requires latest Ubuntu + additional packages). Consider 'make ci' instead
	@echo "Run native build:"
	make -f .ci/Makefile -j12
	@echo "Run style check:"
	./.ci/.gitlab-ci-checkcommit.sh
	@echo "\n\e[32m --- SUCCESS ---\n"

local_cleanup: ## Cleanup after 'make local_presubmit'
	make -f .ci/Makefile clean
