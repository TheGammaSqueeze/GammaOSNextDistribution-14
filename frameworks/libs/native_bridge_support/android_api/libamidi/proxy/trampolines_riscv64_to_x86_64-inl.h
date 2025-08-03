// clang-format off
const KnownTrampoline kKnownTrampolines[] = {
{"AMidiDevice_fromJava", GetTrampolineFunc<auto(void*, void*, void*) -> uint32_t>(), reinterpret_cast<void*>(NULL)},
{"AMidiDevice_getDefaultProtocol", GetTrampolineFunc<auto(void*) -> uint32_t>(), reinterpret_cast<void*>(NULL)},
{"AMidiDevice_getNumInputPorts", GetTrampolineFunc<auto(void*) -> int64_t>(), reinterpret_cast<void*>(NULL)},
{"AMidiDevice_getNumOutputPorts", GetTrampolineFunc<auto(void*) -> int64_t>(), reinterpret_cast<void*>(NULL)},
{"AMidiDevice_getType", GetTrampolineFunc<auto(void*) -> int32_t>(), reinterpret_cast<void*>(NULL)},
{"AMidiDevice_release", GetTrampolineFunc<auto(void*) -> uint32_t>(), reinterpret_cast<void*>(NULL)},
{"AMidiInputPort_close", GetTrampolineFunc<auto(void*) -> void>(), reinterpret_cast<void*>(NULL)},
{"AMidiInputPort_open", GetTrampolineFunc<auto(void*, int32_t, void*) -> uint32_t>(), reinterpret_cast<void*>(NULL)},
{"AMidiInputPort_send", GetTrampolineFunc<auto(void*, void*, uint64_t) -> int64_t>(), reinterpret_cast<void*>(NULL)},
{"AMidiInputPort_sendFlush", GetTrampolineFunc<auto(void*) -> uint32_t>(), reinterpret_cast<void*>(NULL)},
{"AMidiInputPort_sendWithTimestamp", GetTrampolineFunc<auto(void*, void*, uint64_t, int64_t) -> int64_t>(), reinterpret_cast<void*>(NULL)},
{"AMidiOutputPort_close", GetTrampolineFunc<auto(void*) -> void>(), reinterpret_cast<void*>(NULL)},
{"AMidiOutputPort_open", GetTrampolineFunc<auto(void*, int32_t, void*) -> uint32_t>(), reinterpret_cast<void*>(NULL)},
{"AMidiOutputPort_receive", GetTrampolineFunc<auto(void*, void*, void*, uint64_t, void*, void*) -> int64_t>(), reinterpret_cast<void*>(NULL)},
};  // kKnownTrampolines
const KnownVariable kKnownVariables[] = {
};  // kKnownVariables
// clang-format on
