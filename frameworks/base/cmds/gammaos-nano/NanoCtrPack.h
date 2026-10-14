// NanoCtrPack - read only access to the 3DS theme asset packs (tools/ctr_pack.py).
//
// A pack (CPK1) is mapped from disk and holds named blobs; a blob is either a BJS1 value tree
// (the 3ds-web pipeline JSON in a compact binary form) or raw bytes (PNG textures). BjValue is
// a zero copy cursor into a BJS1 blob: lookups walk the encoded bytes, nothing is allocated.
#pragma once

#include <cstdint>
#include <cstring>
#include <memory>
#include <string>
#include <string_view>
#include <unordered_map>
#include <vector>

namespace android {
namespace ctr {

class BjDoc;

// One value inside a BJS1 blob. A default constructed value is null.
class BjValue {
public:
    enum Tag : uint8_t { Null = 0, False = 1, True = 2, Int = 3, Float = 4, String = 5, Array = 6, Object = 7 };

    BjValue() = default;
    BjValue(const BjDoc* doc, const uint8_t* p) : mDoc(doc), mP(p) {}

    Tag tag() const { return mP ? (Tag)mP[0] : Null; }
    bool isNull() const { return tag() == Null; }
    bool isNumber() const { return tag() == Int || tag() == Float; }
    bool isString() const { return tag() == String; }
    bool isArray() const { return tag() == Array; }
    bool isObject() const { return tag() == Object; }

    bool asBool(bool def = false) const {
        Tag t = tag();
        if (t == True) return true;
        if (t == False) return false;
        if (t == Int) return asInt() != 0;
        return def;
    }
    int32_t asInt(int32_t def = 0) const {
        Tag t = tag();
        if (t == Int) { int32_t v; memcpy(&v, mP + 1, 4); return v; }
        if (t == Float) { float f; memcpy(&f, mP + 1, 4); return (int32_t)f; }
        return def;
    }
    float asFloat(float def = 0.0f) const {
        Tag t = tag();
        if (t == Float) { float f; memcpy(&f, mP + 1, 4); return f; }
        if (t == Int) { int32_t v; memcpy(&v, mP + 1, 4); return (float)v; }
        return def;
    }
    std::string_view asString() const;
    std::string str() const { return std::string(asString()); }

    // Arrays and objects.
    uint32_t size() const {
        Tag t = tag();
        if (t != Array && t != Object) return 0;
        uint32_t n; memcpy(&n, mP + 1, 4); return n;
    }
    BjValue operator[](uint32_t index) const;               // array element, null when out of range
    BjValue operator[](std::string_view key) const;         // object member, null when absent
    BjValue get(std::string_view key) const { return (*this)[key]; }
    std::string_view keyAt(uint32_t index) const;           // object key by position
    BjValue valueAt(uint32_t index) const;                  // object value by position

    // Iteration over array elements (or object values) in order.
    class Iter {
    public:
        Iter(const BjDoc* d, const uint8_t* p, bool obj) : mDoc(d), mP(p), mObj(obj) {}
        BjValue operator*() const { return BjValue(mDoc, mObj ? mP + 4 : mP); }
        std::string_view key() const;
        Iter& operator++();
        bool operator!=(const Iter& o) const { return mP != o.mP; }
    private:
        const BjDoc* mDoc; const uint8_t* mP; bool mObj;
    };
    Iter begin() const;
    Iter end() const;

    // Byte size of an encoded value starting at p.
    static size_t encodedSize(const uint8_t* p);

private:
    const BjDoc* mDoc = nullptr;
    const uint8_t* mP = nullptr;
};

// A BJS1 blob: string table + root value. Holds no ownership of the bytes (the pack does).
class BjDoc {
public:
    bool open(const uint8_t* data, size_t size);
    BjValue root() const { return mRoot; }
    std::string_view string(uint32_t index) const {
        if (index >= mCount) return {};
        uint32_t a, b;
        memcpy(&a, mOffs + index * 4, 4);
        memcpy(&b, mOffs + (index + 1) * 4, 4);
        return std::string_view((const char*)mBlob + a, b - a);
    }
    // Index of a string in the table, or -1 (lets object lookups compare integers).
    int32_t find(std::string_view s) const;
private:
    const uint8_t* mOffs = nullptr;
    const uint8_t* mBlob = nullptr;
    uint32_t mCount = 0;
    BjValue mRoot;
    mutable std::unordered_map<std::string, int32_t> mIndex;
};

// A mapped CPK1 pack.
class Pack {
public:
    ~Pack();
    bool open(const std::string& path);
    // A pack built in memory (NanoCtrRom's ROM banner model packs); the pack keeps the bytes.
    bool openMemory(std::vector<uint8_t> bytes);
    bool valid() const { return mData != nullptr; }
    // Raw blob by name; false when absent.
    bool blob(std::string_view name, const uint8_t** data, size_t* size) const;
    // Parsed BJS1 blob by name (cached per pack); nullptr when absent or malformed.
    const BjDoc* doc(std::string_view name) const;
    const std::string& path() const { return mPath; }
private:
    std::string mPath;
    const uint8_t* mData = nullptr;
    size_t mSize = 0;
    uint32_t mCount = 0;
    std::vector<uint8_t> mOwned;   // openMemory: the bytes mData points into (not mapped)
    mutable std::unordered_map<std::string, std::unique_ptr<BjDoc>> mDocs;
};

}  // namespace ctr
}  // namespace android
