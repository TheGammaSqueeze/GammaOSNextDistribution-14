#include <hwtrust/hwtrust.h>
#include <hwtrust/lib.rs.h>

using android::base::Error;
using android::base::Result;

namespace hwtrust {

struct BoxedDiceChain {
    ::rust::Box<rust::DiceChain> chain;
};

// Define with a full definition of BoxedDiceChain to satisfy unique_ptr.
DiceChain::~DiceChain() {}

DiceChain::DiceChain(std::unique_ptr<BoxedDiceChain> chain, size_t size) noexcept
      : chain_(std::move(chain)), size_(size) {}

Result<DiceChain> DiceChain::Verify(const std::vector<uint8_t>& chain, DiceChain::Kind kind) noexcept {
  rust::DiceChainKind chainKind;
  switch (kind) {
    case DiceChain::Kind::kVsr13:
      chainKind = rust::DiceChainKind::Vsr13;
      break;
    case DiceChain::Kind::kVsr14:
      chainKind = rust::DiceChainKind::Vsr14;
      break;
    case DiceChain::Kind::kVsr15:
      chainKind = rust::DiceChainKind::Vsr15;
      break;
    case DiceChain::Kind::kVsr16:
      chainKind = rust::DiceChainKind::Vsr16;
      break;
  }
  auto res = rust::VerifyDiceChain({chain.data(), chain.size()}, chainKind);
  if (!res.error.empty()) {
      return Error() << static_cast<std::string>(res.error);
  }
  BoxedDiceChain boxedChain = { std::move(res.chain) };
  auto diceChain = std::make_unique<BoxedDiceChain>(std::move(boxedChain));
  return DiceChain(std::move(diceChain), res.len);
}

Result<std::vector<std::vector<uint8_t>>> DiceChain::CosePublicKeys() const noexcept {
  std::vector<std::vector<uint8_t>> result;
  for (auto i = 0; i < size_; ++i) {
    auto key = rust::GetDiceChainPublicKey(*chain_->chain, i);
    if (key.empty()) {
      return Error() << "Failed to get public key from chain entry " << i;
    }
    result.emplace_back(key.begin(), key.end());
  }
  return result;
}

} // namespace hwtrust
