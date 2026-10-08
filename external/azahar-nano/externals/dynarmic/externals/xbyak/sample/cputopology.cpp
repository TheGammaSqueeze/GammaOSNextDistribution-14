/*
 * Test program for Xbyak CPU Cache Topology API
 * Demonstrates the CpuTopology, CpuCache, LogicalCpu, and CpuMask classes
 */

#include <stdio.h>
#include <string.h>
#include <map>
#include <vector>
#include "xbyak/xbyak_util.h"

using namespace Xbyak::util;

void printSeparator()
{
	printf("========================================\n");
}

void printSystemTopology(const CpuTopology& cpuTopo)
{
	printSeparator();
	printf("CpuTopology Class - System CPU topology\n");
	printSeparator();

	printf("System Configuration:\n");
	printf("  Logical CPUs:   %zu\n", cpuTopo.getLogicalCpuNum());
	printf("  Physical Cores: %zu\n", cpuTopo.getPhysicalCoreNum());
	printf("  Cache Line Size:%u bytes\n", cpuTopo.getLineSize());
	printf("  Hybrid System:  %s\n", cpuTopo.isHybrid() ? "Yes (P-cores + E-cores)" : "No");
	printf("\n");
}

void printLogicalCpuDetails(const CpuTopology& cpuTopo)
{
	printSeparator();
	printf("LogicalCpu Class - Per-CPU topology information\n");
	printSeparator();

	printf("Detailed CPU Topology (showing upto 32 Logical CPUs):\n");
	size_t maxCpusToPrint = 32;
	size_t numCpus = cpuTopo.getLogicalCpuNum();

	for (size_t i = 0; i < numCpus && i < maxCpusToPrint; i++) {
		const LogicalCpu& logCpu = cpuTopo.getLogicalCpu(i);
		printf("  CPU %3zu: Core=%u Type=%s Siblings=", i, logCpu.coreId, getCoreTypeStr(logCpu.coreType));
		logCpu.getSiblings().put();
	}

	if (numCpus > maxCpusToPrint) {
		printf("  ... (%zu more CPUs not shown)\n", numCpus - maxCpusToPrint);
	}
	printf("\n");
}

// Print cache size in appropriate unit (MB, KB, or B)
void printCacheSize(uint32_t size)
{
	if (size >= 1024 * 1024) {
		printf("%.2f MB", size / (1024.0 * 1024.0));
	} else if (size >= 1024) {
		printf("%.2f KB", size / 1024.0);
	} else {
		printf("%u B", size);
	}
}

// Comparator to group CPUs by their cache topology
struct CacheTopologyComparator {
	const CpuTopology& cpuTopo;

	CacheTopologyComparator(const CpuTopology& topo) : cpuTopo(topo) {}

	bool operator()(size_t cpu1, size_t cpu2) const {
		const LogicalCpu& logi1 = cpuTopo.getLogicalCpu(cpu1);
		const LogicalCpu& logi2 = cpuTopo.getLogicalCpu(cpu2);

		// Sort by core type (E-core before P-core)
		if (logi1.coreType != logi2.coreType) return logi1.coreType > logi2.coreType;

		// Compare cache properties
		for (int cType = L1i; cType < CACHE_TYPE_NUM; cType++) {
			const CpuCache& cache1 = cpuTopo.getCache(cpu1, (CacheType)cType);
			const CpuCache& cache2 = cpuTopo.getCache(cpu2, (CacheType)cType);

			if (cache1.size != cache2.size) return cache1.size < cache2.size;
			if (cache1.associativity != cache2.associativity) return cache1.associativity < cache2.associativity;
			size_t num1 = cache1.getSharedCpuNum();
			size_t num2 = cache2.getSharedCpuNum();
			if (num1 != num2) return num1 < num2;
		}
		return false;
	}
};

typedef std::map<size_t, CpuMask, CacheTopologyComparator> TopologyGroupMap;

// Group CPUs by their cache topology
TopologyGroupMap groupCpusByTopology(const CpuTopology& cpuTopo)
{
	TopologyGroupMap group((CacheTopologyComparator(cpuTopo)));

	for (uint32_t cpuIdx = 0; cpuIdx < cpuTopo.getLogicalCpuNum(); cpuIdx++) {
		group[cpuIdx].append(cpuIdx);
	}
	return group;
}

void printCacheHierarchy(const CpuTopology& cpuTopo, const TopologyGroupMap& group)
{
	printSeparator();
	printf("CpuCache Class - Cache hierarchy and sharing\n");
	printSeparator();

	// Print each unique cache topology group
	printf("Cache Hierarchy by Topology:\n");

	for (TopologyGroupMap::const_iterator it = group.begin(); it != group.end(); ++it) {

		const CpuMask& cpus = it->second;
		if (cpus.empty()) continue;

		size_t firstCpu = cpus.get(0);
		const LogicalCpu& logCpu = cpuTopo.getLogicalCpu(firstCpu);

		// Print core type and CPU list
		printf("\n%s CPUs ", getCoreTypeStr(logCpu.coreType));
		cpus.put();

		// Print cache details for this topology
		for (int cType = 0; cType < CACHE_TYPE_NUM; cType++) {
			const CpuCache& cache = logCpu.cache[cType];
			if (cache.size > 0) {
				printf("  %s: ", getCacheTypeStr(cType));
				printCacheSize(cache.size);
				printf(" | %2u-way", cache.associativity);
				if (cache.isShared()) {
					printf(" | Shared by %zu CPUs", cache.getSharedCpuNum());
				}
				printf("\n");
			}
		}
	}
	printf("\n");
}

void printCacheSharingDetails(const CpuTopology& cpuTopo, const TopologyGroupMap& group)
{
	printSeparator();
	printf("Cache Sharing Analysis\n");
	printSeparator();

	// Print cache sharing analysis for each unique topology
	for (TopologyGroupMap::const_iterator it = group.begin(); it != group.end(); ++it) {

		const CpuMask& cpus = it->second;
		if (cpus.empty()) continue;

		size_t firstCpu = cpus.get(0);
		const LogicalCpu& logCpu = cpuTopo.getLogicalCpu(firstCpu);

		printf("%s Topology (representative CPU %zu):\n", getCoreTypeStr(logCpu.coreType), firstCpu);

		// Analyze each cache level
		for (int cType = 0; cType < CACHE_TYPE_NUM; cType++) {
			const CpuCache& cache = logCpu.cache[cType];
			if (cache.size > 0) {
				printf("  %s Cache:\n", getCacheTypeStr(cType));
				printf("    Size: ");
				printCacheSize(cache.size);
				printf("\n");

				if (cache.isShared()) {
					printf("    Shared by %zu CPUs: ", cache.getSharedCpuNum());
					cache.sharedCpuIndices.put();
				} else {
					printf("    Private (not shared)\n");
				}
			}
		}
		printf("\n");
	}
}

void printSmallSample(const CpuTopology& cpuTopo)
{
	printf("logical CPU num %zu %s\n", cpuTopo.getLogicalCpuNum(), cpuTopo.isHybrid() ? "hybrid" : "");
	if (!cpuTopo.isHybrid()) {
		cpuTopo.getLogicalCpu(0).put();
		return;
	}
	bool foundEcore = false;
	bool foundPcore = false;
	for (size_t i = 0; i < cpuTopo.getLogicalCpuNum(); i++) {
		const LogicalCpu& logi = cpuTopo.getLogicalCpu(i);
		if (!foundEcore && logi.coreType == Efficient) {
			logi.put();
			foundEcore = true;
			continue;
		}
		if (!foundPcore && logi.coreType == Performance) {
			logi.put();
			foundPcore = true;
			continue;
		}
		if (foundEcore && foundPcore) return;
	}
}

int main()
	try
{
	printf("\n");
	printf("Xbyak CPU Cache Topology API Test\n");
	printf("==================================\n");
	printf("\n");

	Cpu cpu;
	CpuTopology cpuTopo(cpu);

	const TopologyGroupMap group = groupCpusByTopology(cpuTopo);

	printSystemTopology(cpuTopo);
	printLogicalCpuDetails(cpuTopo);
	printCacheHierarchy(cpuTopo, group);
	printCacheSharingDetails(cpuTopo, group);

	printSeparator();
	printf("All tests completed successfully!\n");
	printSeparator();
	printf("\n");
	printSeparator();
	printSmallSample(cpuTopo);
} catch (std::exception& e) {
	printf("Error: %s\n", e.what());
	return 1;
}
