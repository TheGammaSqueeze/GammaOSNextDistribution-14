// NanoCtrPack - see NanoCtrPack.h.
#include "NanoCtrPack.h"

#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

namespace android {
namespace ctr {

static inline uint32_t rd32(const uint8_t* p) { uint32_t v; memcpy(&v, p, 4); return v; }

size_t BjValue::encodedSize(const uint8_t* p) {
    switch (p[0]) {
        case Null: case False: case True: return 1;
        case Int: case Float: case String: return 5;
        case Array: case Object: return 9 + rd32(p + 5);
        default: return 1;
    }
}

std::string_view BjValue::asString() const {
    if (tag() != String || !mDoc) return {};
    return mDoc->string(rd32(mP + 1));
}

BjValue BjValue::operator[](uint32_t index) const {
    if (tag() != Array && tag() != Object) return {};
    const bool obj = tag() == Object;
    if (index >= rd32(mP + 1)) return {};
    const uint8_t* p = mP + 9;
    for (uint32_t i = 0; i < index; i++) {
        if (obj) p += 4;
        p += encodedSize(p);
    }
    return BjValue(mDoc, obj ? p + 4 : p);
}

BjValue BjValue::operator[](std::string_view key) const {
    if (tag() != Object || !mDoc) return {};
    const int32_t want = mDoc->find(key);
    if (want < 0) return {};
    const uint32_t n = rd32(mP + 1);
    const uint8_t* p = mP + 9;
    for (uint32_t i = 0; i < n; i++) {
        const uint32_t k = rd32(p);
        if ((int32_t)k == want) return BjValue(mDoc, p + 4);
        p += 4;
        p += encodedSize(p);
    }
    return {};
}

std::string_view BjValue::keyAt(uint32_t index) const {
    if (tag() != Object || !mDoc || index >= rd32(mP + 1)) return {};
    const uint8_t* p = mP + 9;
    for (uint32_t i = 0; i < index; i++) { p += 4; p += encodedSize(p); }
    return mDoc->string(rd32(p));
}

BjValue BjValue::valueAt(uint32_t index) const {
    if (tag() != Object) return {};
    return (*this)[index];
}

std::string_view BjValue::Iter::key() const {
    return mObj && mDoc ? mDoc->string(rd32(mP)) : std::string_view();
}

BjValue::Iter& BjValue::Iter::operator++() {
    const uint8_t* p = mP;
    if (mObj) p += 4;
    mP = p + BjValue::encodedSize(p);
    return *this;
}

BjValue::Iter BjValue::begin() const {
    if (tag() != Array && tag() != Object) return Iter(mDoc, nullptr, false);
    return Iter(mDoc, mP + 9, tag() == Object);
}

BjValue::Iter BjValue::end() const {
    if (tag() != Array && tag() != Object) return Iter(mDoc, nullptr, false);
    return Iter(mDoc, mP + 9 + rd32(mP + 5), tag() == Object);
}

bool BjDoc::open(const uint8_t* data, size_t size) {
    if (size < 8 || memcmp(data, "BJS1", 4) != 0) return false;
    mCount = rd32(data + 4);
    const size_t offsBytes = (size_t)(mCount + 1) * 4;
    if (8 + offsBytes > size) return false;
    mOffs = data + 8;
    mBlob = mOffs + offsBytes;
    const uint32_t blobSize = rd32(mOffs + mCount * 4);
    const uint8_t* root = mBlob + blobSize;
    if (root >= data + size) return false;
    mRoot = BjValue(this, root);
    mIndex.clear();
    return true;
}

int32_t BjDoc::find(std::string_view s) const {
    if (mIndex.empty() && mCount) {
        mIndex.reserve(mCount);
        for (uint32_t i = 0; i < mCount; i++) mIndex.emplace(std::string(string(i)), (int32_t)i);
    }
    auto it = mIndex.find(std::string(s));
    return it == mIndex.end() ? -1 : it->second;
}

Pack::~Pack() {
    if (mData && mOwned.empty()) munmap((void*)mData, mSize);
}

bool Pack::openMemory(std::vector<uint8_t> bytes) {
    if (mData || bytes.size() < 8 || memcmp(bytes.data(), "CPK1", 4) != 0) return false;
    const uint32_t count = rd32(bytes.data() + 4);
    if (8 + (size_t)count * 16 > bytes.size()) return false;
    mPath = "<memory>";
    mOwned = std::move(bytes);
    mData = mOwned.data();
    mSize = mOwned.size();
    mCount = count;
    mDocs.clear();
    return true;
}

bool Pack::open(const std::string& path) {
    mPath = path;
    int fd = ::open(path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) return false;
    struct stat st;
    if (fstat(fd, &st) != 0 || st.st_size < 8) { close(fd); return false; }
    void* p = mmap(nullptr, (size_t)st.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
    close(fd);
    if (p == MAP_FAILED) return false;
    const uint8_t* d = (const uint8_t*)p;
    if (memcmp(d, "CPK1", 4) != 0) { munmap(p, (size_t)st.st_size); return false; }
    mData = d;
    mSize = (size_t)st.st_size;
    mCount = rd32(d + 4);
    if (8 + (size_t)mCount * 16 > mSize) { munmap(p, mSize); mData = nullptr; return false; }
    return true;
}

bool Pack::blob(std::string_view name, const uint8_t** data, size_t* size) const {
    if (!mData) return false;
    // entries are sorted by name: binary search
    uint32_t lo = 0, hi = mCount;
    while (lo < hi) {
        const uint32_t mid = (lo + hi) / 2;
        const uint8_t* e = mData + 8 + mid * 16;
        const std::string_view n((const char*)mData + rd32(e), rd32(e + 4));
        const int c = n.compare(name);
        if (c == 0) {
            const uint32_t off = rd32(e + 8), len = rd32(e + 12);
            if ((size_t)off + len > mSize) return false;
            *data = mData + off;
            *size = len;
            return true;
        }
        if (c < 0) lo = mid + 1; else hi = mid;
    }
    return false;
}

const BjDoc* Pack::doc(std::string_view name) const {
    std::string key(name);
    auto it = mDocs.find(key);
    if (it != mDocs.end()) return it->second.get();
    const uint8_t* d; size_t n;
    std::unique_ptr<BjDoc> doc;
    if (blob(name, &d, &n)) {
        doc = std::make_unique<BjDoc>();
        if (!doc->open(d, n)) doc.reset();
    }
    const BjDoc* out = doc.get();
    mDocs.emplace(std::move(key), std::move(doc));
    return out;
}

}  // namespace ctr
}  // namespace android
