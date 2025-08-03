#include <HalInterfaces.h>

namespace V1_0 = ::android::hardware::neuralnetworks::V1_0;

#if defined(ARMNN_ANDROID_NN_V1_1) || defined(ARMNN_ANDROID_NN_V1_2)
namespace V1_1 = ::android::hardware::neuralnetworks::V1_1;
#endif

#ifdef ARMNN_ANDROID_NN_V1_2
namespace V1_2 = ::android::hardware::neuralnetworks::V1_2;
#endif

using android::sp;
using android::hardware::Return;
using android::hardware::Void;
