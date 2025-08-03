// SPDX-License-Identifier: GPL-2.0-only
/*
 * Test checking that memory of protected guests is wiped after teardown.
 *
 * Copyright (C) 2022, Google LLC.
 */

#define _GNU_SOURCE

#include <err.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <linux/kvm.h>
#include <sys/ioctl.h>
#include <sys/mman.h>

#include "kselftest.h"

#define KVM_VM_TYPE_ARM_PROTECTED	(1UL << 31)

#define REG_X(number)	(0x6030000000100000ULL + (number) * 2UL)
#define REG_PC		0x6030000000100040ULL

static void set_one_reg(int vcpufd, uint64_t reg_id, uint64_t val)
{
	uint64_t reg_data;
	struct kvm_one_reg reg;
	int ret;

	reg.addr = (__u64) &reg_data;
	reg_data = val;
	reg.id = reg_id;

	ret = ioctl(vcpufd, KVM_SET_ONE_REG, &reg);
	if (ret < 0)
		ksft_exit_fail_msg("Failed to set reg: %d\n", ret);
}

static int get_kvm(void)
{
	size_t run_size;
	int kvm, ret;

	kvm = open("/dev/kvm", O_RDWR | O_CLOEXEC);
	if (kvm < 0)
		ksft_exit_skip("KVM not supported\n");

	ret = ioctl(kvm, KVM_GET_API_VERSION, NULL);
	if (ret != 12)
		ksft_exit_fail_msg("KVM_GET_API_VERSION %d, expected 12", ret);

	run_size = ioctl(kvm, KVM_GET_VCPU_MMAP_SIZE, NULL);
	if (run_size < sizeof(struct kvm_run))
		ksft_exit_fail_msg("KVM_GET_VCPU_MMAP_SIZE unexpectedly small\n");

	return kvm;
}

static int create_protected_vm(int kvm)
{
	int vmfd = ioctl(kvm, KVM_CREATE_VM, KVM_VM_TYPE_ARM_PROTECTED);

	if (vmfd < 0)
		ksft_exit_skip("Protected guests not supported: %d\n", vmfd);

	return vmfd;
}

static int create_vcpu(int vmfd, struct kvm_run **run)
{
	struct kvm_vcpu_init vcpu_init;
	int vcpufd, ret;

	ret = ioctl(vmfd, KVM_ARM_PREFERRED_TARGET, &vcpu_init);
	if (ret)
		ksft_exit_fail_msg("Failed to set kvm_vcpu_init %d\n", ret);

	vcpufd = ioctl(vmfd, KVM_CREATE_VCPU, (unsigned long)0);
	if (vcpufd < 0)
		ksft_exit_fail_msg("Failed to create VCPU: %d\n", vcpufd);

	*run = mmap(NULL, sizeof(**run), PROT_READ | PROT_WRITE, MAP_SHARED, vcpufd, 0);
	if (!run)
		ksft_exit_fail_msg("Failed to mmap vcpu_run struct\n");

	ret = ioctl(vcpufd, KVM_ARM_VCPU_INIT, &vcpu_init);
	if (ret)
		ksft_exit_fail_msg("Failed to initialize VCPU %d\n", ret);

	return vcpufd;
}

static void teardown(int kvm, int vmfd, int vcpufd, struct kvm_run *run)
{
	int ret = munmap(run, sizeof(*run));

	if (ret)
		ksft_exit_fail_msg("Failed to unmap vCPU run: %d\n", ret);

	ret = close(vcpufd);
	if (ret)
		ksft_exit_fail_msg("Failed to destroy VCPU: %d\n", ret);

	ret = close(vmfd);
	if (ret)
		ksft_exit_fail_msg("Failed to destroy VM: %d\n", ret);

	ret = close(kvm);
	if (ret)
		ksft_exit_fail_msg("Failed to close KVM fd: %d\n", ret);
}

int main(void)
{
	struct kvm_userspace_memory_region region;
	long page_size = sysconf(_SC_PAGESIZE);
	int ret, kvm, vmfd, vcpufd;
	uint32_t guest_code[2];
	struct kvm_run *run;
	uint8_t *guest_mem;
	size_t run_size;

	kvm = get_kvm();
	vmfd = create_protected_vm(kvm);
	vcpufd = create_vcpu(vmfd, &run);

	/* Create a one-page memslot for the guest */
	guest_mem = mmap(NULL, page_size, PROT_READ | PROT_WRITE,
			MAP_SHARED | MAP_ANONYMOUS, -1, 0);
	if (guest_mem == MAP_FAILED)
		ksft_exit_fail_msg("Failed to mmap guest memory\n");
	region = (struct kvm_userspace_memory_region) {
		.slot = 0,
		.guest_phys_addr = 1UL << 30,
		.memory_size = page_size,
		.userspace_addr = (uint64_t)guest_mem,
	};

	/* Copy some code in guest memory. */
	guest_code[0] = 0xf9400001;	/* 1:  ldr	x1, [x0]  */
	guest_code[1] = 0x17ffffff;	/*     b	1b	  */
	memcpy(guest_mem, guest_code, sizeof(guest_code));
	ret = ioctl(vmfd, KVM_SET_USER_MEMORY_REGION, &region);
	if (ret)
		ksft_exit_fail_msg("Failed to set memory region: %d\n", ret);

	/*
	 * Get the VCPU to run one instruction, to be sure the page containing
	 * the code has been faulted in.
	 */
	set_one_reg(vcpufd, REG_PC, region.guest_phys_addr);
	set_one_reg(vcpufd, REG_X(0), region.guest_phys_addr + region.memory_size);
	ret = ioctl(vcpufd, KVM_RUN, NULL);
	if (ret)
		ksft_exit_fail_msg("Failed to run vcpu: %d\n", ret);
	if (run->exit_reason != KVM_EXIT_MMIO)
		ksft_exit_fail_msg("Unexpected KVM exit reason: %u\n", run->exit_reason);

	/*
	 * Tear the guest down, and check that the donated memory has been
	 * wiped by the hypervisor.
	 */
	teardown(kvm, vmfd, vcpufd, run);
	if (!memcmp(guest_mem, guest_code, sizeof(guest_code)))
		ksft_exit_fail_msg("Protected guest memory has not been poisoned\n");

	ksft_exit_pass();
}
