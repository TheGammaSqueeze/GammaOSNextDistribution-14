#include <gtest/gtest.h>
#include <stdio.h>

#include <meminspect.h>

using namespace std;

/**
 * This test is meant to be ran by directly pushing the test binary
 * into the device as using atest will not provide sufficient privileges
 * to execute drop_caches command.
 */
TEST(meminspect_test, inspect_matches_resident) {
    // Create test file
    string test_file = "/data/local/tmp/meminspect_test";
    // If for any reason a test file already existed from a previous test, remove it.
    remove(test_file.c_str());

    int test_file_fd = open(test_file.c_str(), O_RDWR | O_CREAT, "w");
    unsigned int page_size = sysconf(_SC_PAGESIZE);
    if (test_file_fd == -1) {
        ADD_FAILURE() << "Failed to open test file for writing. errno: " << std::strerror(errno);
        close(test_file_fd);
        remove(test_file.c_str());
        return;
    }

    uint8_t* page_data = new uint8_t[page_size];
    for (unsigned int i = 0; i < page_size; ++i) {
        page_data[i] = i + 1;
    }
    int pages_to_write = 100;
    for (int page = 0; page < pages_to_write; ++page) {
        write(test_file_fd, page_data, page_size);
    }
    // fsync to ensure the data is flushed to disk.
    if (fsync(test_file_fd) == -1) {
        ADD_FAILURE() << "fsync failed errno: " << std::strerror(errno);
        close(test_file_fd);
        remove(test_file.c_str());
        return;
    }
    close(test_file_fd);

    // Drop the pagecache to ensure we do not have memory due to it staying there after write.
    int drop_cache_fd = open("/proc/sys/vm/drop_caches", O_WRONLY);
    if (drop_cache_fd == -1) {
        ADD_FAILURE() << "failed opening drop caches fd errno: " << std::strerror(errno);
        close(test_file_fd);
        remove(test_file.c_str());
        return;
    }
    write(drop_cache_fd, "3", 1);
    fsync(drop_cache_fd);
    close(drop_cache_fd);

    // Open file and page in some memory
    test_file_fd = open(test_file.c_str(), O_RDONLY, "r");
    if (test_file_fd == -1) {
        ADD_FAILURE() << "Failed to open test file for reading after creation. errno: "
                      << std::strerror(errno);
        close(test_file_fd);
        remove(test_file.c_str());
        return;
    }

    char* base_address = (char*)mmap(0, page_size * pages_to_write, PROT_READ, MAP_SHARED,
                                     test_file_fd, /*offset*/ 0);
    if (base_address == (char*)-1) {
        ADD_FAILURE() << "Failed to mmap file for reading after creation. errno: "
                      << std::strerror(errno);
        close(test_file_fd);
        remove(test_file.c_str());
        return;
    }

    VmaRangeGroup vmas_resident;
    int res = probe_resident_memory(test_file, vmas_resident, 1);
    EXPECT_TRUE(res == 0);

    // Probing the file without reading anything yields no resident memory
    EXPECT_TRUE(vmas_resident.ranges.empty());

    // Clear our to start fresh for next probe.
    vmas_resident = VmaRangeGroup();

    int pages_to_read = 1;
    char* read_data = new char[pages_to_read];
    for (int page = 0; page < pages_to_read; ++page) {
        // Read 1 byte from each page to page it in.
        read_data[page] = base_address[page * page_size];
    }
    res = probe_resident_memory(test_file, vmas_resident, 1);
    EXPECT_TRUE(res == 0);

    // The amount of memory paged in is outside our control, but we should have some.
    uint64_t resident_total_size = vmas_resident.compute_total_size();
    EXPECT_TRUE(resident_total_size > 0);
    EXPECT_TRUE(vmas_resident.ranges.size() == 1);
    EXPECT_TRUE(vmas_resident.ranges[0].offset == 0);
    EXPECT_TRUE((uint64_t)vmas_resident.ranges[0].length == resident_total_size);

    close(test_file_fd);
    remove(test_file.c_str());
}

TEST(meminspect_test, custom_probe_coverage_matches_with_probe) {
    ZipMemInspector inspector("");
    VmaRangeGroup* probe = new VmaRangeGroup();
    probe->ranges.push_back(VmaRange(0, 500));
    probe->ranges.push_back(VmaRange(700, 100));
    probe->ranges.push_back(VmaRange(1000, 500));
    probe->ranges.push_back(VmaRange(2000, 100));
    // Probed Resident Memory Offset ranges:
    // [0,500],[700,800],[1000,1500],[2000,2100]
    EXPECT_EQ(probe->compute_total_size(), (unsigned long long)1200);
    inspector.set_existing_probe(probe);

    // Emulate reading some files from the zip to compute their coverages
    // fake1 memory offset ranges [100,300]
    ZipEntryInfo info;
    info.name = "fake1";
    info.offset_in_zip = 100;
    info.file_size_bytes = 200;
    inspector.add_file_info(info);

    // fake2 memory offset ranges [600,1200]
    ZipEntryInfo info2;
    info2.name = "fake2";
    info2.offset_in_zip = 600;
    info2.file_size_bytes = 600;
    inspector.add_file_info(info2);

    inspector.compute_per_file_coverage();
    std::vector<ZipEntryCoverage> coverages = inspector.get_file_coverages();
    EXPECT_EQ(coverages.size(), (size_t)2);

    // Result coverage for fake1 should be: [100,300]
    EXPECT_EQ(coverages[0].coverage.ranges[0].offset, (uint32_t)100);
    EXPECT_EQ(coverages[0].coverage.ranges[0].length, (uint32_t)200);
    EXPECT_EQ(coverages[0].coverage.compute_total_size(), (unsigned long long)200);
    EXPECT_EQ(coverages[0].info.name, "fake1");
    EXPECT_EQ(coverages[0].info.offset_in_zip, (uint32_t)100);
    EXPECT_EQ(coverages[0].info.file_size_bytes, (uint32_t)200);

    // coverage coverage for fake2 should be: [700,800] and [1000,1200]
    EXPECT_EQ(coverages[1].coverage.ranges[0].offset, (uint32_t)700);
    EXPECT_EQ(coverages[1].coverage.ranges[0].length, (uint32_t)100);
    EXPECT_EQ(coverages[1].coverage.ranges[1].offset, (uint32_t)1000);
    EXPECT_EQ(coverages[1].coverage.ranges[1].length, (uint32_t)200);
    EXPECT_EQ(coverages[1].coverage.compute_total_size(), (unsigned long long)300);  // 100 +
                                                                                     // 200
    EXPECT_EQ(coverages[1].info.name, "fake2");
    EXPECT_EQ(coverages[1].info.offset_in_zip, (uint32_t)600);
    EXPECT_EQ(coverages[1].info.file_size_bytes, (uint32_t)600);
}

TEST(meminspect_test, whole_file_coverage_against_probe) {
    ZipMemInspector inspector("");

    // Emulate reading some files from the zip to compute their coverages
    // fake1 memory offset ranges [100,300]
    ZipEntryInfo info;
    info.name = "fake1";
    info.offset_in_zip = 100;
    info.file_size_bytes = 200;
    inspector.add_file_info(info);

    // fake2 memory offset ranges [600,1200]
    ZipEntryInfo info2;
    info2.name = "fake2";
    info2.offset_in_zip = 600;
    info2.file_size_bytes = 600;
    inspector.add_file_info(info2);

    inspector.compute_per_file_coverage();
    std::vector<ZipEntryCoverage> coverages = inspector.get_file_coverages();
    EXPECT_EQ(coverages.size(), (size_t)2);

    // Check that coverage matches entire file sizes
    EXPECT_EQ(coverages[0].coverage.ranges[0].offset, (uint32_t)100);
    EXPECT_EQ(coverages[0].coverage.ranges[0].length, (uint32_t)200);
    EXPECT_EQ(coverages[0].coverage.compute_total_size(), (unsigned long long)200);
    EXPECT_EQ(coverages[0].info.name, "fake1");
    EXPECT_EQ(coverages[0].info.offset_in_zip, (uint32_t)100);
    EXPECT_EQ(coverages[0].info.file_size_bytes, (uint32_t)200);

    EXPECT_EQ(coverages[1].coverage.ranges[0].offset, (uint32_t)600);
    EXPECT_EQ(coverages[1].coverage.ranges[0].length, (uint32_t)600);
    EXPECT_EQ(coverages[1].coverage.compute_total_size(), (unsigned long long)600);
    EXPECT_EQ(coverages[1].info.name, "fake2");
    EXPECT_EQ(coverages[1].info.offset_in_zip, (uint32_t)600);
    EXPECT_EQ(coverages[1].info.file_size_bytes, (uint32_t)600);
}

TEST(meminspect_test, file_multiple_ranges_matches_probe) {
    VmaRangeGroup probe;
    probe.ranges.push_back(VmaRange(0, 500));
    probe.ranges.push_back(VmaRange(700, 100));
    probe.ranges.push_back(VmaRange(1000, 500));
    probe.ranges.push_back(VmaRange(2000, 100));
    // Probed Resident Memory Offset ranges:
    // [0,500],[700,800],[1000,1500],[2000,2100]
    EXPECT_EQ(probe.compute_total_size(), (unsigned long long)1200);

    std::vector<ZipEntryCoverage> desired_coverages;

    // fake1 file resides between [100,1100]
    // desired ranges are [100,200],[400,710],[820,850]
    ZipEntryCoverage file1_mem;
    file1_mem.info.name = "fake1";
    file1_mem.info.offset_in_zip = 100;
    file1_mem.info.file_size_bytes = 1000;
    file1_mem.coverage.ranges.push_back(VmaRange(100, 100));
    file1_mem.coverage.ranges.push_back(VmaRange(400, 310));
    file1_mem.coverage.ranges.push_back(VmaRange(820, 30));
    desired_coverages.push_back(file1_mem);

    // fake2 memory offset ranges [1300,2100]
    // desired ranges are [1400,1500],[1600,1650],[1800,2050]
    ZipEntryCoverage file2_mem;
    file2_mem.info.name = "fake2";
    file2_mem.info.offset_in_zip = 1300;
    file2_mem.info.file_size_bytes = 750;
    file2_mem.coverage.ranges.push_back(VmaRange(1400, 100));
    file2_mem.coverage.ranges.push_back(VmaRange(1600, 50));
    file2_mem.coverage.ranges.push_back(VmaRange(1800, 250));
    desired_coverages.push_back(file2_mem);

    std::vector<ZipEntryCoverage> coverages =
            ZipMemInspector::compute_coverage(desired_coverages, &probe);

    EXPECT_EQ(coverages.size(), (size_t)2);

    // Result coverage for fake1 should be: [100,200],[400,500],[700,710]
    EXPECT_EQ(coverages[0].coverage.ranges[0].offset, (uint32_t)100);
    EXPECT_EQ(coverages[0].coverage.ranges[0].length, (uint32_t)100);
    EXPECT_EQ(coverages[0].coverage.ranges[1].offset, (uint32_t)400);
    EXPECT_EQ(coverages[0].coverage.ranges[1].length, (uint32_t)100);
    EXPECT_EQ(coverages[0].coverage.ranges[2].offset, (uint32_t)700);
    EXPECT_EQ(coverages[0].coverage.ranges[2].length, (uint32_t)10);

    EXPECT_EQ(coverages[0].coverage.compute_total_size(), (unsigned long long)210);
    EXPECT_EQ(coverages[0].info.name, "fake1");
    EXPECT_EQ(coverages[0].info.offset_in_zip, (uint32_t)100);
    EXPECT_EQ(coverages[0].info.file_size_bytes, (uint32_t)1000);

    // coverage coverage for fake2 should be: [1400,1500],[2000,2050]
    EXPECT_EQ(coverages[1].coverage.ranges[0].offset, (uint32_t)1400);
    EXPECT_EQ(coverages[1].coverage.ranges[0].length, (uint32_t)100);
    EXPECT_EQ(coverages[1].coverage.ranges[1].offset, (uint32_t)2000);
    EXPECT_EQ(coverages[1].coverage.ranges[1].length, (uint32_t)50);
    EXPECT_EQ(coverages[1].coverage.compute_total_size(), (unsigned long long)150);
    EXPECT_EQ(coverages[1].info.name, "fake2");
    EXPECT_EQ(coverages[1].info.offset_in_zip, (uint32_t)1300);
    EXPECT_EQ(coverages[1].info.file_size_bytes, (uint32_t)750);
}

TEST(meminspect_test, range_alignment_and_merge_matches) {
    ZipMemInspector inspector("");
    VmaRangeGroup* probe = new VmaRangeGroup();
    probe->ranges.push_back(VmaRange(0, 500));
    probe->ranges.push_back(VmaRange(700, 100));
    int page_size = 4096;

    // Probed Resident Memory Offset ranges:
    // [0,500],[700,800]
    inspector.set_existing_probe(probe);

    // When we page align, we should end up with [0,500],[0,800]
    align_ranges(probe->ranges, page_size);
    EXPECT_EQ(probe->ranges[0].offset, (uint32_t)0);
    EXPECT_EQ(probe->ranges[0].length, (uint32_t)500);
    EXPECT_EQ(probe->ranges[1].offset, (uint32_t)0);
    EXPECT_EQ(probe->ranges[1].length, (uint32_t)800);
    EXPECT_EQ(probe->ranges.size(), (uint32_t)2);

    // Because we have overlapping ranges, a union-merge should
    // skip duplication of intersections and end up with [0,800]
    std::vector<VmaRange> merged = merge_ranges(probe->ranges);
    EXPECT_EQ(merged[0].offset, (uint32_t)0);
    EXPECT_EQ(merged[0].length, (uint32_t)800);
    EXPECT_EQ(merged.size(), (uint32_t)1);
}