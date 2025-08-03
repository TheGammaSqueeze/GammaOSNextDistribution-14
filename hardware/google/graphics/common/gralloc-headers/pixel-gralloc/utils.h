#pragma once

#include <cstdint>
#include <cstring>
#include <optional>
#include <type_traits>
#include <vector>

namespace {

// Trivial type
template <typename T, std::enable_if_t<std::is_trivially_copyable_v<T>, bool> = true>
std::vector<uint8_t> encode_helper(const T& val) {
    auto begin = reinterpret_cast<const uint8_t*>(&val);
    auto end = begin + sizeof(val);
    return {begin, end};
}

// Container type
template <typename Container,
          std::enable_if_t<!std::is_trivially_copyable_v<Container>, bool> = true>
std::vector<uint8_t> encode_helper(const Container& val) {
    // Check comment in decode_helper below
    static_assert(std::is_trivially_copyable_v<typename Container::value_type>,
                  "Can encode only a containers of trivial types currently");

    constexpr auto member_size = sizeof(typename Container::value_type);
    auto n_bytes = member_size * val.size();

    std::vector<uint8_t> out(n_bytes);
    std::memcpy(out.data(), val.data(), n_bytes);

    return out;
}

// Trivial type
template <typename T, std::enable_if_t<std::is_trivially_copyable_v<T>, bool> = true>
std::optional<T> decode_helper(const std::vector<uint8_t>& bytes) {
    T t;

    if (sizeof(t) != bytes.size()) {
        return {};
    }

    std::memcpy(&t, bytes.data(), bytes.size());
    return t;
}

// Container type
template <typename Container,
          std::enable_if_t<!std::is_trivially_copyable_v<Container>, bool> = true>
std::optional<Container> decode_helper(const std::vector<uint8_t>& bytes) {
    Container t;
    size_t member_size = sizeof(typename Container::value_type);

    // NOTE: This can only reconstruct container of trivial types, not a
    // container of non-trivial types. We can either use a standard serializer
    // (like protobuf) or roll one of our own simple ones (like prepending size
    // of the object), but have to be careful about securing such a serializer.
    // But, do we even need that? I do not see any metadata which is either not
    // trivial or a container of trivial type.
    size_t to_copy = bytes.size();
    if (to_copy % member_size != 0) {
        return {};
    }

    size_t members = to_copy / member_size;
    t.resize(members);
    std::memcpy(t.data(), bytes.data(), to_copy);
    return t;
}

} // namespace

namespace pixel::graphics::utils {

// TODO: Setup a fuzzer for encode/decode
template <typename T>
std::vector<uint8_t> encode(const T& val) {
    return encode_helper(val);
}

template <typename T>
std::optional<T> decode(const std::vector<uint8_t>& bytes) {
    return decode_helper<T>(bytes);
}

} // namespace pixel::graphics::utils
