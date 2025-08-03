/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef MINIKIN_LAYOUT_CACHE_H
#define MINIKIN_LAYOUT_CACHE_H

#include <utils/LruCache.h>

#include <mutex>

#include "minikin/Constants.h"
#include "minikin/FontCollection.h"
#include "minikin/Hasher.h"
#include "minikin/LayoutCore.h"
#include "minikin/MinikinPaint.h"

#ifdef _WIN32
#include <io.h>
#endif

namespace minikin {
// Layout cache datatypes
class LayoutCacheKey {
public:
    LayoutCacheKey(const U16StringPiece& text, const Range& range, const MinikinPaint& paint,
                   bool dir, StartHyphenEdit startHyphen, EndHyphenEdit endHyphen)
            : mChars(text.data()),
              mNchars(text.size()),
              mStart(range.getStart()),
              mCount(range.getLength()),
              mId(paint.font->getId()),
              mStyle(paint.fontStyle),
              mSize(paint.size),
              mScaleX(paint.scaleX),
              mSkewX(paint.skewX),
              mLetterSpacing(paint.letterSpacing),
              mWordSpacing(paint.wordSpacing),
              mFontFlags(paint.fontFlags),
              mLocaleListId(paint.localeListId),
              mFamilyVariant(paint.familyVariant),
              mStartHyphen(startHyphen),
              mEndHyphen(endHyphen),
              mIsRtl(dir),
              mFontFeatureSettings(paint.fontFeatureSettings),
              mHash(computeHash()) {}

    bool operator==(const LayoutCacheKey& o) const {
        return mId == o.mId && mStart == o.mStart && mCount == o.mCount && mStyle == o.mStyle &&
               mSize == o.mSize && mScaleX == o.mScaleX && mSkewX == o.mSkewX &&
               mLetterSpacing == o.mLetterSpacing && mWordSpacing == o.mWordSpacing &&
               mFontFlags == o.mFontFlags && mLocaleListId == o.mLocaleListId &&
               mFamilyVariant == o.mFamilyVariant && mStartHyphen == o.mStartHyphen &&
               mEndHyphen == o.mEndHyphen && mIsRtl == o.mIsRtl && mNchars == o.mNchars &&
               mFontFeatureSettings == o.mFontFeatureSettings &&
               !memcmp(mChars, o.mChars, mNchars * sizeof(uint16_t));
    }

    android::hash_t hash() const { return mHash; }

    void copyText() {
        uint16_t* charsCopy = new uint16_t[mNchars];
        memcpy(charsCopy, mChars, mNchars * sizeof(uint16_t));
        mChars = charsCopy;
    }
    void freeText() {
        delete[] mChars;
        mChars = NULL;
        mFontFeatureSettings.clear();
    }

    uint32_t getMemoryUsage() const { return sizeof(LayoutCacheKey) + sizeof(uint16_t) * mNchars; }

private:
    const uint16_t* mChars;
    uint32_t mNchars;
    uint32_t mStart;
    uint32_t mCount;
    uint32_t mId;  // for the font collection
    FontStyle mStyle;
    float mSize;
    float mScaleX;
    float mSkewX;
    float mLetterSpacing;
    float mWordSpacing;
    int32_t mFontFlags;
    uint32_t mLocaleListId;
    FamilyVariant mFamilyVariant;
    StartHyphenEdit mStartHyphen;
    EndHyphenEdit mEndHyphen;
    bool mIsRtl;
    std::vector<FontFeature> mFontFeatureSettings;
    // Note: any fields added to MinikinPaint must also be reflected here.
    // TODO: language matching (possibly integrate into style)
    android::hash_t mHash;

    android::hash_t computeHash() const {
        return Hasher()
                .update(mId)
                .update(mStart)
                .update(mCount)
                .update(mStyle.identifier())
                .update(mSize)
                .update(mScaleX)
                .update(mSkewX)
                .update(mLetterSpacing)
                .update(mWordSpacing)
                .update(mFontFlags)
                .update(mLocaleListId)
                .update(static_cast<uint8_t>(mFamilyVariant))
                .update(packHyphenEdit(mStartHyphen, mEndHyphen))
                .update(mIsRtl)
                .updateShorts(mChars, mNchars)
                .update(mFontFeatureSettings)
                .hash();
    }
};

// A class holds a layout information and bounding box of it. The bounding box can be invalid if not
// calculated.
class LayoutSlot {
public:
    LayoutSlot(LayoutPiece&& layout)
            : mLayout(std::move(layout)), mBounds(MinikinRect::makeInvalid()) {}
    LayoutSlot(LayoutPiece&& layout, MinikinRect&& bounds)
            : mLayout(std::move(layout)), mBounds(std::move(bounds)) {}
    LayoutSlot(const LayoutPiece& layout, const MinikinRect& bounds)
            : mLayout(layout), mBounds(bounds) {}

    const LayoutPiece mLayout;
    MinikinRect mBounds;

private:
    LayoutSlot(const LayoutSlot&) = delete;
    LayoutSlot& operator=(const LayoutSlot&) = delete;
    LayoutSlot(LayoutSlot&&) = delete;
    LayoutSlot& operator=(LayoutSlot&&) = delete;
};

class LayoutCache : private android::OnEntryRemoved<LayoutCacheKey, LayoutSlot*> {
public:
    void clear() {
        std::lock_guard<std::mutex> lock(mMutex);
        mCache.clear();
    }

    // Do not use LayoutCache inside the callback function, otherwise dead-lock may happen.
    template <typename F>
    void getOrCreate(const U16StringPiece& text, const Range& range, const MinikinPaint& paint,
                     bool dir, StartHyphenEdit startHyphen, EndHyphenEdit endHyphen,
                     bool boundsCalculation, F& f) {
        LayoutCacheKey key(text, range, paint, dir, startHyphen, endHyphen);
        if (paint.skipCache() || range.getLength() >= CHAR_LIMIT_FOR_CACHE) {
            LayoutPiece piece(text, range, dir, paint, startHyphen, endHyphen);
            if (boundsCalculation) {
                f(piece, paint, LayoutPiece::calculateBounds(piece, paint));
            } else {
                f(piece, paint, MinikinRect::makeInvalid());
            }
            return;
        }

        LayoutSlot* cachedSlot;
        {
            std::lock_guard<std::mutex> lock(mMutex);
            cachedSlot = mCache.get(key);

            if (cachedSlot != nullptr) {
                if (boundsCalculation && !cachedSlot->mBounds.isValid()) {
                    MinikinRect bounds = LayoutPiece::calculateBounds(cachedSlot->mLayout, paint);
                    LayoutPiece lp = cachedSlot->mLayout;
                    f(lp, paint, bounds);
                    cachedSlot->mBounds = bounds;
                } else {
                    f(cachedSlot->mLayout, paint, cachedSlot->mBounds);
                }
                return;
            }
        }
        // Doing text layout takes long time, so releases the mutex during doing layout.
        // Don't care even if we do the same layout in other thred.
        key.copyText();

        std::unique_ptr<LayoutSlot> slot;
        if (boundsCalculation) {
            LayoutPiece lp = LayoutPiece(text, range, dir, paint, startHyphen, endHyphen);
            MinikinRect rect = LayoutPiece::calculateBounds(lp, paint);

            slot = std::make_unique<LayoutSlot>(std::move(lp), std::move(rect));
        } else {
            slot = std::make_unique<LayoutSlot>(
                    LayoutPiece(text, range, dir, paint, startHyphen, endHyphen));
        }

        f(slot->mLayout, paint, slot->mBounds);
        {
            std::lock_guard<std::mutex> lock(mMutex);
            mCache.put(key, slot.release());
        }
    }

    static LayoutCache& getInstance() {
        static LayoutCache cache(kMaxEntries);
        return cache;
    }

protected:
    LayoutCache(uint32_t maxEntries) : mCache(maxEntries) {
        mCache.setOnEntryRemovedListener(this);
    }

    uint32_t getCacheSize() {
        std::lock_guard<std::mutex> lock(mMutex);
        return mCache.size();
    }

private:
    // callback for OnEntryRemoved
    void operator()(LayoutCacheKey& key, LayoutSlot*& value) {
        key.freeText();
        delete value;
    }

    android::LruCache<LayoutCacheKey, LayoutSlot*> mCache GUARDED_BY(mMutex);

    // static const size_t kMaxEntries = LruCache<LayoutCacheKey, Layout*>::kUnlimitedCapacity;

    // TODO: eviction based on memory footprint; for now, we just use a constant
    // number of strings
    static const size_t kMaxEntries = 5000;

    std::mutex mMutex;
};

inline android::hash_t hash_type(const LayoutCacheKey& key) {
    return key.hash();
}

}  // namespace minikin
#endif  // MINIKIN_LAYOUT_CACHE_H
