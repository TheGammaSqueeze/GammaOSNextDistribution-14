#include <gtest/gtest.h>
#include <stdio.h>
#include <list>

#include <pin_utils.h>

using namespace std;

TEST(pintool_test, pinlist_matches_memranges) {
    vector<VmaRange> vma_ranges;
    unsigned int page_size = sysconf(_SC_PAGESIZE);
    vma_ranges.push_back(VmaRange(0, 500));
    vma_ranges.push_back(VmaRange(5000, 5500));
    vma_ranges.push_back(VmaRange(21000, 13000));
    vma_ranges.push_back(VmaRange(50000, 35000));

    string test_file = "/data/local/tmp/pintool_test";
    write_pinlist_file(test_file, vma_ranges);

    vector<VmaRange> read_ranges;
    read_pinlist_file(test_file, read_ranges);

    EXPECT_EQ(vma_ranges.size(), read_ranges.size());
    for (size_t i = 0; i < vma_ranges.size(); ++i) {
        // We expect to write pinlists that are page-aligned, so
        // we compare against page aligned offsets.
        uint64_t unaligned_bytes = vma_ranges[i].offset % page_size;
        EXPECT_EQ(vma_ranges[i].offset - unaligned_bytes, read_ranges[i].offset);
        EXPECT_EQ(vma_ranges[i].length + unaligned_bytes, read_ranges[i].length);
    }

    remove(test_file.c_str());
}

TEST(pintool_test, pinlist_quota_applied) {
    vector<VmaRange> vma_ranges;
    unsigned int page_size = sysconf(_SC_PAGESIZE);
    vma_ranges.push_back(VmaRange(0, 100));
    vma_ranges.push_back(VmaRange(page_size, 500));
    vma_ranges.push_back(VmaRange(page_size * 2, 300));
    vma_ranges.push_back(VmaRange(page_size * 3, 200));

    const int ranges_to_write = 700;
    string test_file = "/data/local/tmp/pintool_test";
    write_pinlist_file(test_file, vma_ranges, ranges_to_write);

    vector<VmaRange> read_ranges;
    read_pinlist_file(test_file, read_ranges);

    int total_length = 0;
    for (size_t i = 0; i < read_ranges.size(); ++i) {
        total_length += read_ranges[i].length;
    }
    EXPECT_EQ(total_length, ranges_to_write);

    remove(test_file.c_str());
}

TEST(pintool_test, pinconfig_filter_coverage_matches) {
    VmaRangeGroup* probe = new VmaRangeGroup();
    probe->ranges.push_back(VmaRange(0, 500));
    probe->ranges.push_back(VmaRange(1000, 5000));

    ZipMemInspector* inspector = new ZipMemInspector("");

    // Probed Resident Memory Offset ranges:
    // [0,500],[1000,6000]
    probe->compute_total_size();
    inspector->set_existing_probe(probe);

    // Emulate reading some files from the zip to compute their coverages
    // fake1 memory offset ranges [100,400]
    ZipEntryInfo info;
    info.name = "fake1";
    info.offset_in_zip = 100;
    info.file_size_bytes = 300;
    inspector->add_file_info(info);

    // fake2 memory offset ranges [600,3000]
    ZipEntryInfo info2;
    info2.name = "fake2";
    info2.offset_in_zip = 600;
    info2.file_size_bytes = 2400;
    inspector->add_file_info(info2);

    ZipEntryInfo info3;
    info2.name = "fake3";
    info2.offset_in_zip = 3100;
    info2.file_size_bytes = 200;
    inspector->add_file_info(info3);

    // Create a fake pinconfig file
    PinConfig* pinconfig = new PinConfig();

    // First file we want it entirely so don't provide ranges
    PinConfigFile pinconfig_file_1;
    pinconfig_file_1.filename = "fake1";
    pinconfig->files_.push_back(pinconfig_file_1);

    // Add a partially matched file
    PinConfigFile pinconfig_file_2;
    pinconfig_file_2.filename = "fake2";
    pinconfig_file_2.ranges.push_back(VmaRange(100, 500));
    pinconfig_file_2.ranges.push_back(VmaRange(800, 200));
    pinconfig->files_.push_back(pinconfig_file_2);

    // Add a file that does not exist
    PinConfigFile pinconfig_file_3;
    pinconfig_file_3.filename = "fake4";
    pinconfig_file_3.ranges.push_back(VmaRange(0, 1000));
    pinconfig->files_.push_back(pinconfig_file_3);

    PinTool pintool("");
    pintool.set_custom_zip_inspector(inspector);
    pintool.compute_zip_entry_coverages();
    pintool.filter_zip_entry_coverages(pinconfig);

    std::vector<ZipEntryCoverage> filtered = pintool.get_filtered_zip_entries();

    // We only matched 2 files, one should not have matched to any filter.
    EXPECT_EQ(filtered.size(), (unsigned long)2);
    EXPECT_EQ(filtered[0].info.name, "fake1");
    EXPECT_EQ(filtered[0].coverage.ranges[0].offset, (unsigned long)100);
    EXPECT_EQ(filtered[0].coverage.ranges[0].length, (unsigned long)300);

    // Probe Resident has [0,500],[1000,6000].
    // fake2 file lives within [600,3000]
    // fake2 relative offsets from pinconfig [100,600],[800,1000]
    // fake2 absolute zip offsets are [700,1200],[1400,1600]
    // then matching absolute offsets against resident yields [1000,1200],[1400,1600]
    EXPECT_EQ(filtered[1].info.name, "fake2");
    EXPECT_EQ(filtered[1].info.offset_in_zip, (unsigned long)600);
    EXPECT_EQ(filtered[1].coverage.ranges[0].offset, (unsigned long)1000);
    EXPECT_EQ(filtered[1].coverage.ranges[0].length, (unsigned long)200);
    EXPECT_EQ(filtered[1].coverage.ranges[1].offset, (unsigned long)1400);
    EXPECT_EQ(filtered[1].coverage.ranges[1].length, (unsigned long)200);
}