/*
 * Copyright (C) 2015 The Android Open Source Project
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

#include <sys/mman.h>

#include "base/common_art_test.h"
#include "base/utils.h"
#include "gc/collector/immune_spaces.h"
#include "gc/space/image_space.h"
#include "gc/space/space-inl.h"
#include "oat/oat_file.h"
#include "thread-current-inl.h"

namespace art HIDDEN {
namespace mirror {
class Object;
}  // namespace mirror
namespace gc {
namespace collector {

class FakeOatFile : public OatFile {
 public:
  FakeOatFile(uint8_t* begin, uint8_t* end) : OatFile("Location", /*executable=*/ false) {
    begin_ = begin;
    end_ = end;
  }
};

class FakeImageSpace : public space::ImageSpace {
 public:
  FakeImageSpace(MemMap&& map,
                 accounting::ContinuousSpaceBitmap&& live_bitmap,
                 std::unique_ptr<FakeOatFile>&& oat_file,
                 MemMap&& oat_map)
      : ImageSpace("FakeImageSpace",
                   /*image_location=*/"",
                   /*profile_files=*/{},
                   std::move(map),
                   std::move(live_bitmap),
                   map.End()),
        oat_map_(std::move(oat_map)) {
    oat_file_ = std::move(oat_file);
    oat_file_non_owned_ = oat_file_.get();
  }

 private:
  MemMap oat_map_;
};

class ImmuneSpacesTest : public CommonArtTest {
  static constexpr size_t kMaxBitmaps = 10;

 public:
  ImmuneSpacesTest() {}

  void ReserveBitmaps() {
    const size_t page_size = MemMap::GetPageSize();

    // Create a bunch of fake bitmaps since these are required to create image spaces. The bitmaps
    // do not need to cover the image spaces though.
    for (size_t i = 0; i < kMaxBitmaps; ++i) {
      accounting::ContinuousSpaceBitmap bitmap(
          accounting::ContinuousSpaceBitmap::Create(
              "bitmap", reinterpret_cast<uint8_t*>(static_cast<size_t>(page_size)), page_size));
      CHECK(bitmap.IsValid());
      live_bitmaps_.push_back(std::move(bitmap));
    }
  }

  MemMap ReserveImage(size_t image_size, /*out*/ std::string* error_str) {
    // If the image is aligned to the current runtime page size, it will already
    // be naturally aligned. On the other hand, MayAnonymousAligned() requires
    // that the requested alignment is higher.
    DCHECK_LE(MemMap::GetPageSize(), kElfSegmentAlignment);
    if (MemMap::GetPageSize() == kElfSegmentAlignment) {
      return MemMap::MapAnonymous("reserve",
                                  image_size,
                                  PROT_READ | PROT_WRITE,
                                  /*low_4gb=*/true,
                                  error_str);
    }
    return MemMap::MapAnonymousAligned("reserve",
                                       image_size,
                                       PROT_READ | PROT_WRITE,
                                       /*low_4gb=*/true,
                                       kElfSegmentAlignment,
                                       error_str);
  }

  // Create an image space, the oat file is optional.
  FakeImageSpace* CreateImageSpace(size_t image_size,
                                   size_t oat_size,
                                   MemMap* image_reservation,
                                   MemMap* oat_reservation) {
    DCHECK(image_reservation != nullptr);
    DCHECK(oat_reservation != nullptr);
    std::string error_str;
    MemMap image_map = MemMap::MapAnonymous("FakeImageSpace",
                                            image_size,
                                            PROT_READ | PROT_WRITE,
                                            /*low_4gb=*/ true,
                                            /*reservation=*/ image_reservation,
                                            &error_str);
    if (!image_map.IsValid()) {
      LOG(ERROR) << error_str;
      return nullptr;
    }
    CHECK(!live_bitmaps_.empty());
    accounting::ContinuousSpaceBitmap live_bitmap(std::move(live_bitmaps_.back()));
    live_bitmaps_.pop_back();
    MemMap oat_map = MemMap::MapAnonymous("OatMap",
                                          oat_size,
                                          PROT_READ | PROT_WRITE,
                                          /*low_4gb=*/ true,
                                          /*reservation=*/ oat_reservation,
                                          &error_str);
    if (!oat_map.IsValid()) {
      LOG(ERROR) << error_str;
      return nullptr;
    }
    std::unique_ptr<FakeOatFile> oat_file(new FakeOatFile(oat_map.Begin(), oat_map.End()));
    // Create image header.
    ImageSection sections[ImageHeader::kSectionCount];
    new (image_map.Begin()) ImageHeader(
        /*image_reservation_size=*/ image_size,
        /*component_count=*/ 1u,
        /*image_begin=*/ PointerToLowMemUInt32(image_map.Begin()),
        /*image_size=*/ image_size,
        sections,
        /*image_roots=*/ PointerToLowMemUInt32(image_map.Begin()) + 1,
        /*oat_checksum=*/ 0u,
        // The oat file data in the header is always right after the image space.
        /*oat_file_begin=*/ PointerToLowMemUInt32(oat_map.Begin()),
        /*oat_data_begin=*/ PointerToLowMemUInt32(oat_map.Begin()),
        /*oat_data_end=*/ PointerToLowMemUInt32(oat_map.Begin() + oat_size),
        /*oat_file_end=*/ PointerToLowMemUInt32(oat_map.Begin() + oat_size),
        /*boot_image_begin=*/ 0u,
        /*boot_image_size=*/ 0u,
        /*boot_image_component_count=*/ 0u,
        /*boot_image_checksum=*/ 0u,
        /*pointer_size=*/ sizeof(void*));
    return new FakeImageSpace(std::move(image_map),
                              std::move(live_bitmap),
                              std::move(oat_file),
                              std::move(oat_map));
  }

 private:
  // Bitmap pool for pre-allocated fake bitmaps. We need to pre-allocate them since we don't want
  // them to randomly get placed somewhere where we want an image space.
  std::vector<accounting::ContinuousSpaceBitmap> live_bitmaps_;
};

class FakeSpace : public space::ContinuousSpace {
 public:
  FakeSpace(uint8_t* begin, uint8_t* end)
      : ContinuousSpace("FakeSpace",
                        space::kGcRetentionPolicyNeverCollect,
                        begin,
                        end,
                        /*limit=*/end) {}

  space::SpaceType GetType() const override {
    return space::kSpaceTypeMallocSpace;
  }

  bool CanMoveObjects() const override {
    return false;
  }

  accounting::ContinuousSpaceBitmap* GetLiveBitmap() override {
    return nullptr;
  }

  accounting::ContinuousSpaceBitmap* GetMarkBitmap() override {
    return nullptr;
  }
};

TEST_F(ImmuneSpacesTest, AppendBasic) {
  ImmuneSpaces spaces;
  uint8_t* const base = reinterpret_cast<uint8_t*>(0x1000);
  FakeSpace a(base, base + 45 * KB);
  FakeSpace b(a.Limit(), a.Limit() + 813 * KB);
  {
    WriterMutexLock mu(Thread::Current(), *Locks::heap_bitmap_lock_);
    spaces.AddSpace(&a);
    spaces.AddSpace(&b);
  }
  EXPECT_TRUE(spaces.ContainsSpace(&a));
  EXPECT_TRUE(spaces.ContainsSpace(&b));
  EXPECT_EQ(reinterpret_cast<uint8_t*>(spaces.GetLargestImmuneRegion().Begin()), a.Begin());
  EXPECT_EQ(reinterpret_cast<uint8_t*>(spaces.GetLargestImmuneRegion().End()), b.Limit());
}

// Tests [image][oat][space] producing a single large immune region.
TEST_F(ImmuneSpacesTest, AppendAfterImage) {
  ReserveBitmaps();
  ImmuneSpaces spaces;
  constexpr size_t kImageSize = 123 * kElfSegmentAlignment;
  constexpr size_t kImageOatSize = 321 * kElfSegmentAlignment;
  constexpr size_t kOtherSpaceSize = 100 * kElfSegmentAlignment;

  std::string error_str;
  MemMap reservation = ReserveImage(kImageSize + kImageOatSize + kOtherSpaceSize, &error_str);
  ASSERT_TRUE(reservation.IsValid()) << "Failed to allocate memory region " << error_str;
  MemMap image_reservation = reservation.TakeReservedMemory(kImageSize);
  ASSERT_TRUE(image_reservation.IsValid());
  ASSERT_TRUE(reservation.IsValid());

  std::unique_ptr<FakeImageSpace> image_space(CreateImageSpace(kImageSize,
                                                               kImageOatSize,
                                                               &image_reservation,
                                                               &reservation));
  ASSERT_TRUE(image_space != nullptr);
  ASSERT_FALSE(image_reservation.IsValid());
  ASSERT_TRUE(reservation.IsValid());

  const ImageHeader& image_header = image_space->GetImageHeader();
  FakeSpace space(image_header.GetOatFileEnd(), image_header.GetOatFileEnd() + kOtherSpaceSize);

  EXPECT_EQ(image_header.GetImageSize(), kImageSize);
  EXPECT_EQ(static_cast<size_t>(image_header.GetOatFileEnd() - image_header.GetOatFileBegin()),
            kImageOatSize);
  EXPECT_EQ(image_space->GetOatFile()->Size(), kImageOatSize);
  // Check that we do not include the oat if there is no space after.
  {
    WriterMutexLock mu(Thread::Current(), *Locks::heap_bitmap_lock_);
    spaces.AddSpace(image_space.get());
  }
  EXPECT_EQ(reinterpret_cast<uint8_t*>(spaces.GetLargestImmuneRegion().Begin()),
            image_space->Begin());
  EXPECT_EQ(reinterpret_cast<uint8_t*>(spaces.GetLargestImmuneRegion().End()),
            image_space->Limit());
  // Add another space and ensure it gets appended.
  EXPECT_NE(image_space->Limit(), space.Begin());
  {
    WriterMutexLock mu(Thread::Current(), *Locks::heap_bitmap_lock_);
    spaces.AddSpace(&space);
  }
  EXPECT_TRUE(spaces.ContainsSpace(image_space.get()));
  EXPECT_TRUE(spaces.ContainsSpace(&space));
  // CreateLargestImmuneRegion should have coalesced the two spaces since the oat code after the
  // image prevents gaps.
  // Check that we have a continuous region.
  EXPECT_EQ(reinterpret_cast<uint8_t*>(spaces.GetLargestImmuneRegion().Begin()),
            image_space->Begin());
  EXPECT_EQ(reinterpret_cast<uint8_t*>(spaces.GetLargestImmuneRegion().End()), space.Limit());
}

// Test [image1][image2][image1 oat][image2 oat][image3] producing a single large immune region.
TEST_F(ImmuneSpacesTest, MultiImage) {
  ReserveBitmaps();
  // Image 2 needs to be smaller or else it may be chosen for immune region.
  constexpr size_t kImage1Size = kElfSegmentAlignment * 17;
  constexpr size_t kImage2Size = kElfSegmentAlignment * 13;
  constexpr size_t kImage3Size = kElfSegmentAlignment * 3;
  constexpr size_t kImage1OatSize = kElfSegmentAlignment * 5;
  constexpr size_t kImage2OatSize = kElfSegmentAlignment * 8;
  constexpr size_t kImage3OatSize = kElfSegmentAlignment;
  constexpr size_t kImageBytes = kImage1Size + kImage2Size + kImage3Size;
  constexpr size_t kMemorySize = kImageBytes + kImage1OatSize + kImage2OatSize + kImage3OatSize;
  std::string error_str;
  MemMap reservation = ReserveImage(kMemorySize, &error_str);
  ASSERT_TRUE(reservation.IsValid()) << "Failed to allocate memory region " << error_str;
  MemMap image_reservation = reservation.TakeReservedMemory(kImage1Size + kImage2Size);
  ASSERT_TRUE(image_reservation.IsValid());
  ASSERT_TRUE(reservation.IsValid());

  std::unique_ptr<FakeImageSpace> space1(CreateImageSpace(kImage1Size,
                                                          kImage1OatSize,
                                                          &image_reservation,
                                                          &reservation));
  ASSERT_TRUE(space1 != nullptr);
  ASSERT_TRUE(image_reservation.IsValid());
  ASSERT_TRUE(reservation.IsValid());

  std::unique_ptr<FakeImageSpace> space2(CreateImageSpace(kImage2Size,
                                                          kImage2OatSize,
                                                          &image_reservation,
                                                          &reservation));
  ASSERT_TRUE(space2 != nullptr);
  ASSERT_FALSE(image_reservation.IsValid());
  ASSERT_TRUE(reservation.IsValid());

  // Finally put a 3rd image space.
  image_reservation = reservation.TakeReservedMemory(kImage3Size);
  ASSERT_TRUE(image_reservation.IsValid());
  ASSERT_TRUE(reservation.IsValid());
  std::unique_ptr<FakeImageSpace> space3(CreateImageSpace(kImage3Size,
                                                          kImage3OatSize,
                                                          &image_reservation,
                                                          &reservation));
  ASSERT_TRUE(space3 != nullptr);
  ASSERT_FALSE(image_reservation.IsValid());
  ASSERT_FALSE(reservation.IsValid());

  // Check that we do not include the oat if there is no space after.
  ImmuneSpaces spaces;
  {
    WriterMutexLock mu(Thread::Current(), *Locks::heap_bitmap_lock_);
    LOG(INFO) << "Adding space1 " << reinterpret_cast<const void*>(space1->Begin());
    spaces.AddSpace(space1.get());
    LOG(INFO) << "Adding space2 " << reinterpret_cast<const void*>(space2->Begin());
    spaces.AddSpace(space2.get());
  }
  // There are no more heap bytes, the immune region should only be the first 2 image spaces and
  // should exclude the image oat files.
  EXPECT_EQ(reinterpret_cast<uint8_t*>(spaces.GetLargestImmuneRegion().Begin()),
            space1->Begin());
  EXPECT_EQ(reinterpret_cast<uint8_t*>(spaces.GetLargestImmuneRegion().End()),
            space2->Limit());

  // Add another space after the oat files, now it should contain the entire memory region.
  {
    WriterMutexLock mu(Thread::Current(), *Locks::heap_bitmap_lock_);
    LOG(INFO) << "Adding space3 " << reinterpret_cast<const void*>(space3->Begin());
    spaces.AddSpace(space3.get());
  }
  EXPECT_EQ(reinterpret_cast<uint8_t*>(spaces.GetLargestImmuneRegion().Begin()),
            space1->Begin());
  EXPECT_EQ(reinterpret_cast<uint8_t*>(spaces.GetLargestImmuneRegion().End()),
            space3->Limit());

  // Add a smaller non-adjacent space and ensure it does not become part of the immune region.
  // Image size is kImageBytes - kElfSegmentAlignment
  // Oat size is kElfSegmentAlignment.
  // Guard pages to ensure it is not adjacent to an existing immune region.
  // Layout:  [guard page][image][oat][guard page]
  constexpr size_t kGuardSize = kElfSegmentAlignment;
  constexpr size_t kImage4Size = kImageBytes - kElfSegmentAlignment;
  constexpr size_t kImage4OatSize = kElfSegmentAlignment;

  reservation = ReserveImage(kImage4Size + kImage4OatSize + kGuardSize * 2, &error_str);
  ASSERT_TRUE(reservation.IsValid()) << "Failed to allocate memory region " << error_str;
  MemMap guard = reservation.TakeReservedMemory(kGuardSize);
  ASSERT_TRUE(guard.IsValid());
  ASSERT_TRUE(reservation.IsValid());
  guard.Reset();  // Release the guard memory.
  image_reservation = reservation.TakeReservedMemory(kImage4Size);
  ASSERT_TRUE(image_reservation.IsValid());
  ASSERT_TRUE(reservation.IsValid());
  std::unique_ptr<FakeImageSpace> space4(CreateImageSpace(kImage4Size,
                                                          kImage4OatSize,
                                                          &image_reservation,
                                                          &reservation));
  ASSERT_TRUE(space4 != nullptr);
  ASSERT_FALSE(image_reservation.IsValid());
  ASSERT_TRUE(reservation.IsValid());
  ASSERT_EQ(reservation.Size(), kGuardSize);
  reservation.Reset();  // Release the guard memory.
  {
    WriterMutexLock mu(Thread::Current(), *Locks::heap_bitmap_lock_);
    LOG(INFO) << "Adding space4 " << reinterpret_cast<const void*>(space4->Begin());
    spaces.AddSpace(space4.get());
  }
  EXPECT_EQ(reinterpret_cast<uint8_t*>(spaces.GetLargestImmuneRegion().Begin()),
            space1->Begin());
  EXPECT_EQ(reinterpret_cast<uint8_t*>(spaces.GetLargestImmuneRegion().End()),
            space3->Limit());

  // Add a larger non-adjacent space and ensure it becomes the new largest immune region.
  // Image size is kImageBytes + kElfSegmentAlignment
  // Oat size is kElfSegmentAlignment.
  // Guard pages to ensure it is not adjacent to an existing immune region.
  // Layout:  [guard page][image][oat][guard page]
  constexpr size_t kImage5Size = kImageBytes + kElfSegmentAlignment;
  constexpr size_t kImage5OatSize = kElfSegmentAlignment;
  reservation = ReserveImage(kImage5Size + kImage5OatSize + kGuardSize * 2, &error_str);
  ASSERT_TRUE(reservation.IsValid()) << "Failed to allocate memory region " << error_str;
  guard = reservation.TakeReservedMemory(kGuardSize);
  ASSERT_TRUE(guard.IsValid());
  ASSERT_TRUE(reservation.IsValid());
  guard.Reset();  // Release the guard memory.
  image_reservation = reservation.TakeReservedMemory(kImage5Size);
  ASSERT_TRUE(image_reservation.IsValid());
  ASSERT_TRUE(reservation.IsValid());
  std::unique_ptr<FakeImageSpace> space5(CreateImageSpace(kImage5Size,
                                                          kImage5OatSize,
                                                          &image_reservation,
                                                          &reservation));
  ASSERT_TRUE(space5 != nullptr);
  ASSERT_FALSE(image_reservation.IsValid());
  ASSERT_TRUE(reservation.IsValid());
  ASSERT_EQ(reservation.Size(), kGuardSize);
  reservation.Reset();  // Release the guard memory.
  {
    WriterMutexLock mu(Thread::Current(), *Locks::heap_bitmap_lock_);
    LOG(INFO) << "Adding space5 " << reinterpret_cast<const void*>(space5->Begin());
    spaces.AddSpace(space5.get());
  }
  EXPECT_EQ(reinterpret_cast<uint8_t*>(spaces.GetLargestImmuneRegion().Begin()), space5->Begin());
  EXPECT_EQ(reinterpret_cast<uint8_t*>(spaces.GetLargestImmuneRegion().End()), space5->Limit());
}

}  // namespace collector
}  // namespace gc
}  // namespace art
