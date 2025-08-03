// clang-format off
const KnownTrampoline kKnownTrampolines[] = {
{"JNI_OnLoad", DoBadTrampoline, reinterpret_cast<void*>(DoBadThunk)},
{"_ZN7android17GraphicBufferImpl11UnmapStaticEl", DoBadTrampoline, reinterpret_cast<void*>(DoBadThunk)},
{"_ZN7android17GraphicBufferImpl15GetStrideStaticEl", DoBadTrampoline, reinterpret_cast<void*>(DoBadThunk)},
{"_ZN7android17GraphicBufferImpl21GetNativeBufferStaticEl", DoBadTrampoline, reinterpret_cast<void*>(DoBadThunk)},
{"_ZN7android17GraphicBufferImpl3MapE9AwMapModePPv", DoBadTrampoline, reinterpret_cast<void*>(DoBadThunk)},
{"_ZN7android17GraphicBufferImpl5UnmapEv", DoBadTrampoline, reinterpret_cast<void*>(DoBadThunk)},
{"_ZN7android17GraphicBufferImpl6CreateEii", DoBadTrampoline, reinterpret_cast<void*>(DoBadThunk)},
{"_ZN7android17GraphicBufferImpl7ReleaseEl", DoBadTrampoline, reinterpret_cast<void*>(DoBadThunk)},
{"_ZN7android17GraphicBufferImpl9MapStaticEl9AwMapModePPv", DoBadTrampoline, reinterpret_cast<void*>(DoBadThunk)},
{"_ZN7android17GraphicBufferImplC2Ejj", DoBadTrampoline, reinterpret_cast<void*>(DoBadThunk)},
{"_ZN7android17GraphicBufferImplD2Ev", DoBadTrampoline, reinterpret_cast<void*>(DoBadThunk)},
{"_ZN7android19RegisterDrawFunctorEP7_JNIEnv", DoBadTrampoline, reinterpret_cast<void*>(DoBadThunk)},
{"_ZN7android20RaiseFileNumberLimitEv", DoBadTrampoline, reinterpret_cast<void*>(DoBadThunk)},
{"_ZN7android21RegisterDrawGLFunctorEP7_JNIEnv", DoBadTrampoline, reinterpret_cast<void*>(DoBadThunk)},
{"_ZN7android21RegisterGraphicsUtilsEP7_JNIEnv", DoBadTrampoline, reinterpret_cast<void*>(DoBadThunk)},
{"_ZNK7android17GraphicBufferImpl15GetNativeBufferEv", DoBadTrampoline, reinterpret_cast<void*>(DoBadThunk)},
{"_ZNK7android17GraphicBufferImpl9GetStrideEv", DoBadTrampoline, reinterpret_cast<void*>(DoBadThunk)},
{"_ZNK7android17GraphicBufferImpl9InitCheckEv", DoBadTrampoline, reinterpret_cast<void*>(DoBadThunk)},
};  // kKnownTrampolines
const KnownVariable kKnownVariables[] = {
};  // kKnownVariables
// clang-format on
