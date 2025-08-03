/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <BufferAllocator/dmabufheap-defs.h>

#include <android-base/unique_fd.h>
#include <linux/ion_4.12.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>

#include <cstdint>
#include <shared_mutex>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>


class BufferAllocator {
  public:
    BufferAllocator();
    ~BufferAllocator() {}

    /* Not copyable or movable */
    BufferAllocator(const BufferAllocator&) = delete;
    BufferAllocator& operator=(const BufferAllocator&) = delete;

    /**
     * Maps a dmabuf heap to an equivalent ion heap configuration. This method is required since
     * dmabuf heaps do not support heap flags. This means that a single ion heap may encompass the
     * functionality of multiple dmabuf heaps by using heap flags. If the interface being used is
     * non-legacy ion, the mapping from dmabuf heap name to non-legacy ion heap name will be created
     * and the legacy parameters will be ignored.
     * The method can be deprecated once all devices have
     * migrated to dmabuf heaps from ion. Returns an error code when the
     * interface used is non-legacy ion and the @ion_heap_name parameter is non-empty and
     * invalid or if the interface used is legacy ion and @legacy_ion_heap_mask
     * is invalid(0);
     * @heap_name: dmabuf heap name.
     * @ion_heap_name: name of the equivalent ion heap - if empty ("") legacy heap mask will be used
     * @ion_heap_flags: flags to be passed to the ion heap @ion_heap_name for it to function
     * equivalently to the dmabuf heap @heap_name. Ignored if ion_heap_name is empty("").
     * @legacy_ion_heap_mask: heap mask for the equivalent legacy ion heap.
     * @legacy_ion_heap_flags: flags to be passed to the legacy ion heap for it
     * to function equivalently to dmabuf heap @heap_name.
     */
    int MapNameToIonHeap(const std::string& heap_name, const std::string& ion_heap_name,
                         unsigned int ion_heap_flags = 0, unsigned int legacy_ion_heap_mask = 0,
                         unsigned int legacy_ion_heap_flags = 0);

    /* *
     * Returns a dmabuf fd if the allocation in one of the specified heaps is successful and
     * an error code otherwise. If dmabuf heaps are supported, tries to allocate in the
     * specified dmabuf heap. If allocation fails in the specified dmabuf heap and ion_fd is a
     * valid fd, goes through saved heap data to find a heap ID/mask to match the specified heap
     * names and allocates memory as per the specified parameters. For vendor defined heaps with a
     * legacy ION interface(no heap query support), MapNameToIonMask() must be called prior to
     * invocation of Alloc() to map a heap name to an equivalent heap mask and heap flag
     * configuration.
     * @heap_name: name of the heap to allocate in.
     * @len: size of the allocation.
     * @heap_flags: flags passed to heap.
     * @legacy_align: alignment value used only by legacy ION
     */
    int Alloc(const std::string& heap_name, size_t len, unsigned int heap_flags = 0, size_t legacy_align = 0);

    /* *
     * Returns a dmabuf fd if the allocation in system heap(cached/uncached) is successful and
     * an error code otherwise. Allocates in the 'system' heap if CPU access of
     * the buffer is expected and 'system-uncached' otherwise. If the 'system-uncached'
     * heap is not supported, falls back to the 'system' heap.
     * For vendor defined heaps with a legacy ION interface(no heap query support),
     * MapNameToIonMask() must be called prior to invocation of AllocSystem() to
     * map names 'system'(and optionally 'system-uncached' if applicable) to an
     * equivalent heap mask and heap flag configuration;
     * configuration.
     * @cpu_access: indicates if CPU access of the buffer is expected.
     * @len: size of the allocation.
     * @heap_flags: flags passed to heap.
     * @legacy_align: alignment value used only by legacy ION
     */
    int AllocSystem(bool cpu_access, size_t len, unsigned int heap_flags = 0,
                    size_t legacy_align = 0);

    /**
     * Optional custom callback for legacy ion implementation that can be specified as a
     * parameter to CpuSyncStart() and CpuSyncEnd(). Its first argument is an fd to /dev/ion.
     * The callback MUST NOT assume ownership of the fd.  The fd will be closed once the
     * callback returns.  The second argument is a dma_buf fd, upon which the custom sync IOCTL
     * should be called.  The third argument is a void pointer that can be used to pass data
     * to be used in the IOCTL.
     * If provided as an argument to CpuSyncStart() and CpuSyncEnd(), the callback will be used
     * for syncing a shared dmabuf fd with memory(instead of ion_sync_fd()). It will be invoked
     * with a dup of ion_fd_ as its first argument. Return 0 on success and error code otherwise
     * which will become the return value for CpuSyncStart() and CpuSyncEnd().
     */
    typedef std::function<int(int, int, void *)> CustomCpuSyncLegacyIon;

    /**
     * Must be invoked before CPU access of the allocated memory.
     * For a legacy ion interface, syncs a shared dmabuf fd with memory either using
     * ION_IOC_SYNC ioctl or using callback @legacy_ion_cpu_sync if specified. For
     * non-legacy ION and dmabuf heap interfaces, DMA_BUF_IOCTL_SYNC is used.
     * @dmabuf_fd: dmabuf fd. When the legacy version of ion is in use and a callback
     * function is supplied, this is passed as the second argument to legacy_ion_cpu_sync.
     * @sync_type: specifies if the sync is for read, write or read/write.
     * @legacy_ion_cpu_sync: optional callback for legacy ion interfaces. If
     * specified, will be invoked instead of ion_sync_fd()
     * to sync dmabuf_fd with memory. The paremeter will be ignored if the interface being
     * used is not legacy ion.
     * @legacy_ion_custom_data: When the legacy version of ion is in use and a callback
     * function is supplied, this pointer is passed as the third argument to
     * legacy_ion_cpu_sync. It is intended to point to data for performing the callback.
     *
     * Returns 0  on success and an error code otherwise.
     */
    int CpuSyncStart(unsigned int dmabuf_fd, SyncType sync_type = kSyncRead,
                     const CustomCpuSyncLegacyIon& legacy_ion_cpu_sync = nullptr,
                     void *legacy_ion_custom_data = nullptr);

    /**
     * Must be invoked once CPU is done accessing the allocated memory.
     * For a legacy ion interface, syncs a shared dmabuf fd with memory using
     * either ION_IOC_SYNC ioctl or using callback @legacy_ion_cpu_sync if
     * specified. For non-legacy ION and dmabuf heap interfaces,
     * DMA_BUF_IOCTL_SYNC is used.
     * @dmabuf_fd: dmabuf fd. When the legacy version of ion is in use and a callback
     * function is supplied, this is passed as the second argument to legacy_ion_cpu_sync.
     * @sync_type: specifies if sync_type is for read, write or read/write.
     * @legacy_ion_cpu_sync: optional callback for legacy ion interfaces. If
     * specified, will be invoked instead of ion_sync_fd with a dup of ion_fd_ as its
     * argument. The parameter will be ignored if the interface being used is
     * not legacy ion.
     * @legacy_ion_custom_data: When the legacy version of ion is in use and a callback
     * function is supplied, this pointer is passed as the third argument to
     * legacy_ion_cpu_sync. It is intended to point to data for performing the callback.
     *
     * Returns 0 on success and an error code otherwise.
     */
    int CpuSyncEnd(unsigned int dmabuf_fd, SyncType sync_type = kSyncRead,
                   const CustomCpuSyncLegacyIon& legacy_ion_cpu_sync = nullptr,
                   void* legacy_ion_custom_data = nullptr);

    /**
     * Query supported DMA-BUF heaps.
     *
     * @return the list of supported DMA-BUF heap names.
     */
    static std::unordered_set<std::string> GetDmabufHeapList();

    /**
     *
     * Check if ION is supported on the device.
     *
     * @return true if /dev/ion is present on the device, otherwise false.
     */
    static bool CheckIonSupport();

    /**
     * Set the name of a dma buffer.
     *
     * @dmabuf_fd: dmabuf file descriptor.
     * @name: The name for the dmabuf. Length should not exceed DMA_BUF_NAME_LEN.
     *
     * @return Returns 0 on success, otherwise -1 and sets errno.
     */
    static int DmabufSetName(unsigned int dmabuf_fd, const std::string& name);

  private:
    int OpenDmabufHeap(const std::string& name);
    void QueryIonHeaps();
    int GetDmabufHeapFd(const std::string& name);
    bool DmabufHeapsSupported() { return !dmabuf_heap_fds_.empty(); }
    int GetIonHeapIdByName(const std::string& heap_name, unsigned int* heap_id);
    int MapNameToIonMask(const std::string& heap_name, unsigned int ion_heap_mask,
                         unsigned int ion_heap_flags = 0);
    int MapNameToIonName(const std::string& heap_name, const std::string& ion_heap_name,
                         unsigned int ion_heap_flags = 0);
    void LogInterface(const std::string& interface);
    int IonAlloc(const std::string& heap_name, size_t len, unsigned int heap_flags = 0, size_t legacy_align = 0);
    int DmabufAlloc(const std::string& heap_name, size_t len, int fd);

    struct IonHeapConfig {
        unsigned int mask;
        unsigned int flags;
    };
    int GetIonConfig(const std::string& heap_name, IonHeapConfig& heap_config);
    int LegacyIonCpuSync(unsigned int fd, const CustomCpuSyncLegacyIon& legacy_ion_cpu_sync_custom,
                         void *custom_data);
    int DoSync(unsigned int dmabuf_fd, bool start, SyncType sync_type,
               const CustomCpuSyncLegacyIon& legacy_ion_cpu_sync_custom,
               void *custom_data);

    /* Stores all open dmabuf_heap handles. */
    std::unordered_map<std::string, android::base::unique_fd> dmabuf_heap_fds_;
    /* Protects dma_buf_heap_fd_ from concurrent access */
    std::shared_mutex dmabuf_heap_fd_mutex_;

    /* saved handle to /dev/ion. */
    android::base::unique_fd ion_fd_;
    /**
     * Stores the queried ion heap data. Struct ion_heap_date is defined
     * as part of the ION UAPI as follows.
     * struct ion_heap_data {
     *   char name[MAX_HEAP_NAME];
     *    __u32 type;
     *    __u32 heap_id;
     *    __u32 reserved0;
     *    __u32 reserved1;
     *    __u32 reserved2;
     * };
     */
    bool uses_legacy_ion_iface_ = false;
    std::vector<struct ion_heap_data> ion_heap_info_;
    inline static bool logged_interface_ = false;
    /* stores a map of dmabuf heap names to equivalent ion heap configurations. */
    std::unordered_map<std::string, struct IonHeapConfig> heap_name_to_config_;
    /* protects heap_name_to_config_ from concurrent access */
    std::shared_mutex heap_name_to_config_mutex_;
};
