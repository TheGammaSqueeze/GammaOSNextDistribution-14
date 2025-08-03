#pragma once

#include <memory>
#include <vector>

#include <android-base/result.h>

namespace hwtrust {

// Hide the details of the rust binding from clients with an opaque type.
struct BoxedDiceChain;

class DiceChain final {
public:
  enum class Kind {
    kVsr13,
    kVsr14,
    kVsr15,
    kVsr16,
  };

  static android::base::Result<DiceChain> Verify(const std::vector<uint8_t>& chain, DiceChain::Kind kind) noexcept;

  ~DiceChain();
  DiceChain(DiceChain&&) = default;

  android::base::Result<std::vector<std::vector<uint8_t>>> CosePublicKeys() const noexcept;

private:
  DiceChain(std::unique_ptr<BoxedDiceChain> chain, size_t size) noexcept;

  std::unique_ptr<BoxedDiceChain> chain_;
  size_t size_;
};

} // namespace hwtrust
