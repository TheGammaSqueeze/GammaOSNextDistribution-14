/*
 * Copyright (C) 2020 The Android Open Source Project
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
 * limitations under the License
 */

package android.compat.sjp.cts;

import static android.compat.testing.Classpaths.ClasspathType.BOOTCLASSPATH;
import static android.compat.testing.Classpaths.ClasspathType.SYSTEMSERVERCLASSPATH;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.compat.testing.Classpaths;
import android.compat.testing.SharedLibraryInfo;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.modules.utils.build.testing.DeviceSdkLevel;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.INativeDevice;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;
import com.android.tradefed.util.FileUtil;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Tests for detecting no duplicate class files are present on BOOTCLASSPATH and
 * SYSTEMSERVERCLASSPATH.
 *
 * <p>Duplicate class files are not safe as some of the jars on *CLASSPATH are updated outside of
 * the main dessert release cycle; they also contribute to unnecessary disk space usage.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class StrictJavaPackagesTest extends BaseHostJUnit4Test {

    private static final String ANDROID_TEST_MOCK_JAR = "/system/framework/android.test.mock.jar";
    private static final String TEST_HELPER_PACKAGE = "android.compat.sjp.app";
    private static final String TEST_HELPER_APK = "StrictJavaPackagesTestApp.apk";

    private static final Pattern APEX_JAR_PATTERN =
            Pattern.compile("\\/apex\\/(?<apexName>[^\\/]+)\\/.*\\.(jar|apk)");

    private static ImmutableList<String> sBootclasspathJars;
    private static ImmutableList<String> sSystemserverclasspathJars;
    private static ImmutableList<String> sSharedLibJars;
    private static ImmutableList<SharedLibraryInfo> sSharedLibs;
    private static ImmutableMultimap<String, String> sSharedLibsPathsToName;
    private static ImmutableMultimap<String, String> sJarsToClasses;
    private static ImmutableMultimap<String, String> sJarsToFiles;

    private DeviceSdkLevel mDeviceSdkLevel;

    /**
     * This is the list of classes that are currently duplicated and should be addressed.
     *
     * <p> DO NOT ADD CLASSES TO THIS LIST!
     */
    private static final Set<String> BCP_AND_SSCP_OVERLAP_BURNDOWN_LIST =
            ImmutableSet.of(
                    "Landroid/annotation/AnimatorRes;",
                    "Landroid/annotation/AnimRes;",
                    "Landroid/annotation/AnyRes;",
                    "Landroid/annotation/AnyThread;",
                    "Landroid/annotation/AppIdInt;",
                    "Landroid/annotation/ArrayRes;",
                    "Landroid/annotation/AttrRes;",
                    "Landroid/annotation/BinderThread;",
                    "Landroid/annotation/BoolRes;",
                    "Landroid/annotation/BroadcastBehavior;",
                    "Landroid/annotation/BytesLong;",
                    "Landroid/annotation/CallbackExecutor;",
                    "Landroid/annotation/CallSuper;",
                    "Landroid/annotation/CheckResult;",
                    "Landroid/annotation/ColorInt;",
                    "Landroid/annotation/ColorLong;",
                    "Landroid/annotation/ColorRes;",
                    "Landroid/annotation/Condemned;",
                    "Landroid/annotation/CurrentTimeMillisLong;",
                    "Landroid/annotation/CurrentTimeSecondsLong;",
                    "Landroid/annotation/DimenRes;",
                    "Landroid/annotation/Dimension;",
                    "Landroid/annotation/Discouraged;",
                    "Landroid/annotation/DisplayContext;",
                    "Landroid/annotation/DrawableRes;",
                    "Landroid/annotation/DurationMillisLong;",
                    "Landroid/annotation/ElapsedRealtimeLong;",
                    "Landroid/annotation/EnforcePermission;",
                    "Landroid/annotation/FloatRange;",
                    "Landroid/annotation/FontRes;",
                    "Landroid/annotation/FractionRes;",
                    "Landroid/annotation/HalfFloat;",
                    "Landroid/annotation/Hide;",
                    "Landroid/annotation/IdRes;",
                    "Landroid/annotation/IntDef;",
                    "Landroid/annotation/IntegerRes;",
                    "Landroid/annotation/InterpolatorRes;",
                    "Landroid/annotation/IntRange;",
                    "Landroid/annotation/LayoutRes;",
                    "Landroid/annotation/LongDef;",
                    "Landroid/annotation/MainThread;",
                    "Landroid/annotation/MenuRes;",
                    "Landroid/annotation/NavigationRes;",
                    "Landroid/annotation/NonNull;",
                    "Landroid/annotation/NonUiContext;",
                    "Landroid/annotation/Nullable;",
                    "Landroid/annotation/PluralsRes;",
                    "Landroid/annotation/Px;",
                    "Landroid/annotation/RawRes;",
                    "Landroid/annotation/RequiresFeature;",
                    "Landroid/annotation/RequiresNoPermission;",
                    "Landroid/annotation/RequiresPermission;",
                    "Landroid/annotation/SdkConstant;",
                    "Landroid/annotation/Size;",
                    "Landroid/annotation/StringDef;",
                    "Landroid/annotation/StringRes;",
                    "Landroid/annotation/StyleableRes;",
                    "Landroid/annotation/StyleRes;",
                    "Landroid/annotation/SuppressAutoDoc;",
                    "Landroid/annotation/SuppressLint;",
                    "Landroid/annotation/SystemApi;",
                    "Landroid/annotation/SystemService;",
                    "Landroid/annotation/TargetApi;",
                    "Landroid/annotation/TestApi;",
                    "Landroid/annotation/TransitionRes;",
                    "Landroid/annotation/UiContext;",
                    "Landroid/annotation/UiThread;",
                    "Landroid/annotation/UptimeMillisLong;",
                    "Landroid/annotation/UserHandleAware;",
                    "Landroid/annotation/UserIdInt;",
                    "Landroid/annotation/Widget;",
                    "Landroid/annotation/WorkerThread;",
                    "Landroid/annotation/XmlRes;",
                    "Landroid/gsi/AvbPublicKey;",
                    "Landroid/gsi/GsiProgress;",
                    "Landroid/gsi/IGsiService;",
                    "Landroid/gsi/IGsiServiceCallback;",
                    "Landroid/gsi/IImageService;",
                    "Landroid/gsi/IProgressCallback;",
                    "Landroid/gsi/MappedImage;",
                    "Landroid/gui/TouchOcclusionMode;",
                    // TODO(b/227752875): contexthub V1 APIs can be removed
                    // from T+ with the fix in aosp/2050305.
                    "Landroid/hardware/contexthub/V1_0/AsyncEventType;",
                    "Landroid/hardware/contexthub/V1_0/ContextHub;",
                    "Landroid/hardware/contexthub/V1_0/ContextHubMsg;",
                    "Landroid/hardware/contexthub/V1_0/HostEndPoint;",
                    "Landroid/hardware/contexthub/V1_0/HubAppInfo;",
                    "Landroid/hardware/contexthub/V1_0/HubMemoryFlag;",
                    "Landroid/hardware/contexthub/V1_0/HubMemoryType;",
                    "Landroid/hardware/contexthub/V1_0/IContexthub;",
                    "Landroid/hardware/contexthub/V1_0/IContexthubCallback;",
                    "Landroid/hardware/contexthub/V1_0/MemRange;",
                    "Landroid/hardware/contexthub/V1_0/NanoAppBinary;",
                    "Landroid/hardware/contexthub/V1_0/NanoAppFlags;",
                    "Landroid/hardware/contexthub/V1_0/PhysicalSensor;",
                    "Landroid/hardware/contexthub/V1_0/Result;",
                    "Landroid/hardware/contexthub/V1_0/SensorType;",
                    "Landroid/hardware/contexthub/V1_0/TransactionResult;",
                    "Landroid/hardware/usb/gadget/V1_0/GadgetFunction;",
                    "Landroid/hardware/usb/gadget/V1_0/IUsbGadget;",
                    "Landroid/hardware/usb/gadget/V1_0/IUsbGadgetCallback;",
                    "Landroid/hardware/usb/gadget/V1_0/Status;",
                    "Landroid/os/IDumpstate;",
                    "Landroid/os/IDumpstateListener;",
                    "Landroid/os/IInstalld;",
                    "Landroid/os/IStoraged;",
                    "Landroid/os/IVold;",
                    "Landroid/os/IVoldListener;",
                    "Landroid/os/IVoldMountCallback;",
                    "Landroid/os/IVoldTaskListener;",
                    "Landroid/os/TouchOcclusionMode;",
                    "Landroid/os/storage/CrateMetadata;",
                    "Landroid/view/LayerMetadataKey;",
                    "Lcom/android/internal/annotations/CompositeRWLock;",
                    "Lcom/android/internal/annotations/GuardedBy;",
                    "Lcom/android/internal/annotations/Immutable;",
                    "Lcom/android/internal/annotations/VisibleForNative;",
                    "Lcom/android/internal/annotations/VisibleForTesting;",
                    // TODO(b/173649240): due to an oversight, some new overlaps slipped through
                    // in S.
                    "Landroid/hardware/usb/gadget/V1_1/IUsbGadget;",
                    "Landroid/hardware/usb/gadget/V1_2/GadgetFunction;",
                    "Landroid/hardware/usb/gadget/V1_2/IUsbGadget;",
                    "Landroid/hardware/usb/gadget/V1_2/IUsbGadgetCallback;",
                    "Landroid/hardware/usb/gadget/V1_2/UsbSpeed;",
                    "Landroid/os/CreateAppDataArgs;",
                    "Landroid/os/CreateAppDataResult;",
                    "Landroid/os/ReconcileSdkDataArgs;",
                    "Lcom/android/internal/util/FrameworkStatsLog;",
                    // Extra Pixel specific S oversights
                    "Landroid/os/BlockUntrustedTouchesMode;",
                    "Landroid/os/IInputConstants;",
                    "Landroid/os/InputEventInjectionResult;",
                    "Landroid/os/InputEventInjectionSync;",
                    // TODO(b/242741880): Remove duplication between sdksandbox-service and
                    // sdk-sandbox-framework
                    "Landroid/app/sdksandbox/ILoadSdkCallback;",
                    "Landroid/app/sdksandbox/IRequestSurfacePackageCallback;",
                    "Landroid/app/sdksandbox/ISdkSandboxManager;",
                    "Landroid/app/sdksandbox/ISdkSandboxLifecycleCallback;",
                    "Landroid/app/sdksandbox/ISdkSandboxProcessDeathCallback;",
                    "Landroid/app/sdksandbox/ISendDataCallback;",
                    "Landroid/app/sdksandbox/ISharedPreferencesSyncCallback;",
                    "Landroid/app/sdksandbox/ISdkToServiceCallback;",
                    "Landroid/app/sdksandbox/IUnloadSdkCallback;",
                    // b/325060980 : Remove duplication between telephony-common.jar and
                    // services.jar
                    "Lcom/android/server/updates/ConfigUpdateInstallReceiver;"
            );

    private static final String FEATURE_WEARABLE = "android.hardware.type.watch";
    private static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";

    private static final Set<String> WEAR_HIDL_OVERLAP_BURNDOWN_LIST =
            ImmutableSet.of(
                    "Landroid/hidl/base/V1_0/DebugInfo$Architecture;",
                    "Landroid/hidl/base/V1_0/IBase;",
                    "Landroid/hidl/base/V1_0/IBase$Proxy;",
                    "Landroid/hidl/base/V1_0/IBase$Stub;",
                    "Landroid/hidl/base/V1_0/DebugInfo;",
                    "Landroid/hidl/safe_union/V1_0/Monostate;"
            );

    private static final Set<String> AUTOMOTIVE_HIDL_OVERLAP_BURNDOWN_LIST =
            ImmutableSet.of(
                    "Landroid/hidl/base/V1_0/DebugInfo$Architecture;",
                    "Landroid/hidl/base/V1_0/IBase;",
                    "Landroid/hidl/base/V1_0/IBase$Proxy;",
                    "Landroid/hidl/base/V1_0/IBase$Stub;",
                    "Landroid/hidl/base/V1_0/DebugInfo;"
            );

    /**
     * TODO(b/199529199): Address these.
     * List of duplicate classes between bootclasspath and shared libraries.
     *
     * <p> DO NOT ADD CLASSES TO THIS LIST!
     */
    private static final Set<String> BCP_AND_SHARED_LIB_BURNDOWN_LIST =
            ImmutableSet.of(
                    "Landroid/hidl/base/V1_0/DebugInfo;",
                    "Landroid/hidl/base/V1_0/IBase;",
                    "Landroid/hidl/manager/V1_0/IServiceManager;",
                    "Landroid/hidl/manager/V1_0/IServiceNotification;",
                    "Landroidx/annotation/Keep;",
                    "Lcom/google/android/embms/nano/EmbmsProtos;",
                    "Lcom/google/protobuf/nano/android/ParcelableExtendableMessageNano;",
                    "Lcom/google/protobuf/nano/android/ParcelableMessageNano;",
                    "Lcom/google/protobuf/nano/android/ParcelableMessageNanoCreator;",
                    "Lcom/google/protobuf/nano/CodedInputByteBufferNano;",
                    "Lcom/google/protobuf/nano/CodedOutputByteBufferNano;",
                    "Lcom/google/protobuf/nano/ExtendableMessageNano;",
                    "Lcom/google/protobuf/nano/Extension;",
                    "Lcom/google/protobuf/nano/FieldArray;",
                    "Lcom/google/protobuf/nano/FieldData;",
                    "Lcom/google/protobuf/nano/InternalNano;",
                    "Lcom/google/protobuf/nano/InvalidProtocolBufferNanoException;",
                    "Lcom/google/protobuf/nano/MapFactories;",
                    "Lcom/google/protobuf/nano/MessageNano;",
                    "Lcom/google/protobuf/nano/MessageNanoPrinter;",
                    "Lcom/google/protobuf/nano/UnknownFieldData;",
                    "Lcom/google/protobuf/nano/WireFormatNano;",
                    "Lcom/qualcomm/qcrilhook/BaseQmiTypes;",
                    "Lcom/qualcomm/qcrilhook/CSignalStrength;",
                    "Lcom/qualcomm/qcrilhook/EmbmsOemHook;",
                    "Lcom/qualcomm/qcrilhook/EmbmsProtoUtils;",
                    "Lcom/qualcomm/qcrilhook/IOemHookCallback;",
                    "Lcom/qualcomm/qcrilhook/IQcRilHook;",
                    "Lcom/qualcomm/qcrilhook/IQcRilHookExt;",
                    "Lcom/qualcomm/qcrilhook/OemHookCallback;",
                    "Lcom/qualcomm/qcrilhook/PresenceMsgBuilder;",
                    "Lcom/qualcomm/qcrilhook/PresenceMsgParser;",
                    "Lcom/qualcomm/qcrilhook/PresenceOemHook;",
                    "Lcom/qualcomm/qcrilhook/PrimitiveParser;",
                    "Lcom/qualcomm/qcrilhook/QcRilHook;",
                    "Lcom/qualcomm/qcrilhook/QcRilHookCallback;",
                    "Lcom/qualcomm/qcrilhook/QcRilHookCallbackExt;",
                    "Lcom/qualcomm/qcrilhook/QcRilHookExt;",
                    "Lcom/qualcomm/qcrilhook/QmiOemHook;",
                    "Lcom/qualcomm/qcrilhook/QmiOemHookConstants;",
                    "Lcom/qualcomm/qcrilhook/QmiPrimitiveTypes;",
                    "Lcom/qualcomm/qcrilhook/TunerOemHook;",
                    "Lcom/qualcomm/qcrilmsgtunnel/IQcrilMsgTunnel;",
                    "Lcom/qualcomm/utils/CommandException;",
                    "Lcom/qualcomm/utils/RILConstants;",
                    "Lorg/codeaurora/telephony/utils/CommandException;",
                    "Lorg/codeaurora/telephony/utils/Log;",
                    "Lorg/codeaurora/telephony/utils/RILConstants;",
                    "Lorg/chromium/net/ApiVersion;",
                    "Lorg/chromium/net/BidirectionalStream;",
                    "Lorg/chromium/net/CallbackException;",
                    "Lorg/chromium/net/CronetEngine;",
                    "Lorg/chromium/net/CronetException;",
                    "Lorg/chromium/net/CronetProvider;",
                    "Lorg/chromium/net/EffectiveConnectionType;",
                    "Lorg/chromium/net/ExperimentalBidirectionalStream;",
                    "Lorg/chromium/net/ExperimentalCronetEngine;",
                    "Lorg/chromium/net/ExperimentalUrlRequest;",
                    "Lorg/chromium/net/ICronetEngineBuilder;",
                    "Lorg/chromium/net/InlineExecutionProhibitedException;",
                    "Lorg/chromium/net/NetworkException;",
                    "Lorg/chromium/net/NetworkQualityRttListener;",
                    "Lorg/chromium/net/NetworkQualityThroughputListener;",
                    "Lorg/chromium/net/QuicException;",
                    "Lorg/chromium/net/RequestFinishedInfo;",
                    "Lorg/chromium/net/RttThroughputValues;",
                    "Lorg/chromium/net/ThreadStatsUid;",
                    "Lorg/chromium/net/UploadDataProvider;",
                    "Lorg/chromium/net/UploadDataProviders;",
                    "Lorg/chromium/net/UploadDataSink;",
                    "Lorg/chromium/net/UrlRequest;",
                    "Lorg/chromium/net/UrlResponseInfo;"
            );
    private static final ImmutableSet<String> PERMISSION_CONTROLLER_APK_IN_APEX_BURNDOWN_LIST =
            ImmutableSet.of(
                "Lcom/android/modules/utils/build/SdkLevel;",
                "Lcom/android/settingslib/RestrictedLockUtils;",
                "Lcom/android/safetycenter/resources/SafetyCenterResourcesContext;"
            );
    // TODO: b/223837599
    private static final ImmutableSet<String> TETHERING_APK_IN_APEX_BURNDOWN_LIST =
            ImmutableSet.of(
                "Landroid/hidl/base/V1_0/DebugInfo;",
                // /system/framework/services.jar
                "Landroid/net/DataStallReportParcelable;",
                "Landroid/net/DhcpResultsParcelable;",
                "Landroid/net/INetd;",
                "Landroid/net/INetdUnsolicitedEventListener;",
                "Landroid/net/INetworkStackConnector;",
                "Landroid/net/INetworkStackStatusCallback;",
                "Landroid/net/InformationElementParcelable;",
                "Landroid/net/InitialConfigurationParcelable;",
                "Landroid/net/InterfaceConfigurationParcel;",
                "Landroid/net/Layer2InformationParcelable;",
                "Landroid/net/Layer2PacketParcelable;",
                "Landroid/net/MarkMaskParcel;",
                "Landroid/net/NativeNetworkConfig;",
                "Landroid/net/NattKeepalivePacketDataParcelable;",
                "Landroid/net/NetworkTestResultParcelable;",
                "Landroid/net/PrivateDnsConfigParcel;",
                "Landroid/net/ProvisioningConfigurationParcelable;",
                "Landroid/net/RouteInfoParcel;",
                "Landroid/net/ScanResultInfoParcelable;",
                "Landroid/net/TcpKeepalivePacketDataParcelable;",
                "Landroid/net/TetherConfigParcel;",
                "Landroid/net/TetherOffloadRuleParcel;",
                "Landroid/net/TetherStatsParcel;",
                "Landroid/net/UidRangeParcel;",
                "Landroid/net/dhcp/DhcpLeaseParcelable;",
                "Landroid/net/dhcp/DhcpServingParamsParcel;",
                "Landroid/net/dhcp/IDhcpEventCallbacks;",
                "Landroid/net/dhcp/IDhcpServer;",
                "Landroid/net/dhcp/IDhcpServerCallbacks;",
                "Landroid/net/ipmemorystore/Blob;",
                "Landroid/net/ipmemorystore/NetworkAttributesParcelable;",
                "Landroid/net/ipmemorystore/SameL3NetworkResponseParcelable;",
                "Landroid/net/ipmemorystore/StatusParcelable;",
                "Landroid/net/netd/aidl/NativeUidRangeConfig;",
                "Landroid/net/networkstack/aidl/NetworkMonitorParameters;",
                "Landroid/net/networkstack/aidl/dhcp/DhcpOption;",
                "Landroid/net/networkstack/aidl/ip/ReachabilityLossInfoParcelable;",
                "Landroid/net/networkstack/aidl/quirks/IPv6ProvisioningLossQuirkParcelable;",
                "Landroid/net/shared/NetdUtils;",
                "Landroid/net/util/NetworkConstants;",
                "Landroid/net/util/SharedLog;",
                "Lcom/android/modules/utils/build/SdkLevel;",
                ///system/framework/framework.jar
                "Landroid/util/IndentingPrintWriter;",
                "Lcom/android/internal/annotations/Keep;"
            );
    // TODO: b/223836161
    private static final ImmutableSet<String> EXTSERVICES_APK_IN_APEX_BURNDOWN_LIST =
            ImmutableSet.of(
                ///system/framework/framework.jar
                "Landroid/view/displayhash/DisplayHashResultCallback;",
                "Landroid/view/displayhash/DisplayHash;",
                "Landroid/view/displayhash/VerifiedDisplayHash;"
            );
    // TODO: b/223836163
    private static final ImmutableSet<String> ODA_APK_IN_APEX_BURNDOWN_LIST =
            ImmutableSet.of(
                // /apex/com.android.ondevicepersonalization/javalib/framework-ondevicepersonalization.jar
                "Landroid/ondevicepersonalization/aidl/IInitOnDevicePersonalizationCallback;",
                "Landroid/ondevicepersonalization/aidl/IOnDevicePersonalizationManagerService;"
            );
    // TODO: b/223837017
    private static final ImmutableSet<String> CELLBROADCAST_APK_IN_APEX_BURNDOWN_LIST =
            ImmutableSet.of(
                // /system/framework/telephony-common.jar
                "Lcom/android/cellbroadcastservice/CellBroadcastStatsLog;",
                // /system/framework/framework.jar
                "Lcom/android/internal/util/IState;",
                "Lcom/android/internal/util/StateMachine;",
                "Lcom/android/internal/util/State;"
            );

    // TODO: b/234557765
    private static final ImmutableSet<String> ADSERVICES_SANDBOX_APK_IN_APEX_BURNDOWN_LIST =
            ImmutableSet.of(
                // /apex/com.android.adservices/javalib/service-sdksandbox.jar
                "Landroid/app/sdksandbox/ISharedPreferencesSyncCallback;",
                "Lcom/android/sdksandbox/IDataReceivedCallback;",
                "Lcom/android/sdksandbox/ILoadSdkInSandboxCallback;",
                "Lcom/android/sdksandbox/IRequestSurfacePackageFromSdkCallback;",
                "Lcom/android/sdksandbox/ISdkSandboxDisabledCallback;",
                "Lcom/android/sdksandbox/ISdkSandboxManagerToSdkSandboxCallback;",
                "Lcom/android/sdksandbox/ISdkSandboxService;",
                "Lcom/android/sdksandbox/SandboxLatencyInfo-IA;",
                "Lcom/android/sdksandbox/SandboxLatencyInfo;",
                "Lcom/android/sdksandbox/IComputeSdkStorageCallback;"
            );

    private static final ImmutableMap<String, ImmutableSet<String>> FULL_APK_IN_APEX_BURNDOWN =
        new ImmutableMap.Builder<String, ImmutableSet<String>>()
            .put("/apex/com.android.permission/priv-app/PermissionController/PermissionController.apk",
                PERMISSION_CONTROLLER_APK_IN_APEX_BURNDOWN_LIST)
            .put("/apex/com.android.permission/priv-app/GooglePermissionController/GooglePermissionController.apk",
                PERMISSION_CONTROLLER_APK_IN_APEX_BURNDOWN_LIST)
            .put("/apex/com.android.tethering/priv-app/InProcessTethering/InProcessTethering.apk",
                TETHERING_APK_IN_APEX_BURNDOWN_LIST)
            .put("/apex/com.android.tethering/priv-app/TetheringNextGoogle/TetheringNextGoogle.apk",
                TETHERING_APK_IN_APEX_BURNDOWN_LIST)
            .put("/apex/com.android.tethering/priv-app/TetheringGoogle/TetheringGoogle.apk",
                TETHERING_APK_IN_APEX_BURNDOWN_LIST)
            .put("/apex/com.android.tethering/priv-app/TetheringNext/TetheringNext.apk",
                TETHERING_APK_IN_APEX_BURNDOWN_LIST)
            .put("/apex/com.android.tethering/priv-app/Tethering/Tethering.apk",
                TETHERING_APK_IN_APEX_BURNDOWN_LIST)
            .put("/apex/com.android.extservices/priv-app/GoogleExtServices/GoogleExtServices.apk",
                EXTSERVICES_APK_IN_APEX_BURNDOWN_LIST)
            .put("/apex/com.android.extservices/priv-app/ExtServices/ExtServices.apk",
                EXTSERVICES_APK_IN_APEX_BURNDOWN_LIST)
            .put("/apex/com.android.ondevicepersonalization/app/OnDevicePersonalizationGoogle/OnDevicePersonalizationGoogle.apk",
                ODA_APK_IN_APEX_BURNDOWN_LIST)
            .put("/apex/com.android.ondevicepersonalization/app/OnDevicePersonalization/OnDevicePersonalization.apk",
                ODA_APK_IN_APEX_BURNDOWN_LIST)
            .put("/apex/com.android.cellbroadcast/priv-app/GoogleCellBroadcastServiceModule/GoogleCellBroadcastServiceModule.apk",
                CELLBROADCAST_APK_IN_APEX_BURNDOWN_LIST)
            .put("/apex/com.android.cellbroadcast/priv-app/CellBroadcastServiceModule/CellBroadcastServiceModule.apk",
                CELLBROADCAST_APK_IN_APEX_BURNDOWN_LIST)
            .put("/apex/com.android.adservices/app/SdkSandbox/SdkSandbox.apk",
                ADSERVICES_SANDBOX_APK_IN_APEX_BURNDOWN_LIST)
            .put("/apex/com.android.adservices/app/SdkSandboxGoogle/SdkSandboxGoogle.apk",
                ADSERVICES_SANDBOX_APK_IN_APEX_BURNDOWN_LIST)
            .build();

    // Bluetooth has not been updated on pre-u device
    private static ImmutableSet<String> PRE_U_APK_IN_APEX_BLUETOOTH_BURNDOWN_LIST =
            ImmutableSet.of(
                    // b/310322439
                    "Lcom/android/bluetooth/x/android/sysprop/AdbProperties;",
                    "Lcom/android/bluetooth/x/android/sysprop/ApkVerityProperties;",
                    "Lcom/android/bluetooth/x/android/sysprop/BluetoothProperties;",
                    "Lcom/android/bluetooth/x/android/sysprop/CarProperties;",
                    "Lcom/android/bluetooth/x/android/sysprop/ContactsProperties;",
                    "Lcom/android/bluetooth/x/android/sysprop/CryptoProperties;",
                    "Lcom/android/bluetooth/x/android/sysprop/DeviceProperties;",
                    "Lcom/android/bluetooth/x/android/sysprop/DisplayProperties;",
                    "Lcom/android/bluetooth/x/android/sysprop/HdmiProperties;",
                    "Lcom/android/bluetooth/x/android/sysprop/HypervisorProperties;",
                    "Lcom/android/bluetooth/x/android/sysprop/InputProperties;",
                    "Lcom/android/bluetooth/x/android/sysprop/MediaProperties;",
                    "Lcom/android/bluetooth/x/android/sysprop/NetworkProperties;",
                    "Lcom/android/bluetooth/x/android/sysprop/OtaProperties;",
                    "Lcom/android/bluetooth/x/android/sysprop/PowerProperties;",
                    "Lcom/android/bluetooth/x/android/sysprop/SetupWizardProperties;",
                    "Lcom/android/bluetooth/x/android/sysprop/SocProperties;",
                    "Lcom/android/bluetooth/x/android/sysprop/TelephonyProperties;",
                    "Lcom/android/bluetooth/x/android/sysprop/TraceProperties;",
                    "Lcom/android/bluetooth/x/android/sysprop/VndkProperties;",
                    "Lcom/android/bluetooth/x/android/sysprop/VoldProperties;",
                    "Lcom/android/bluetooth/x/android/sysprop/WifiProperties;");

    /**
     * Lists of known failures when running testApkInApex_nonClasspathClasses against pre-U devices.
     *
     * <p>Add the new item into this list only if the failure is caused by base device image (not
     * the mainline train).
     */
    private static final ImmutableMap<String, ImmutableSet<String>>
            PRE_U_APK_IN_APEX_BURNDOWN_LIST =
                    new ImmutableMap.Builder<String, ImmutableSet<String>>()
                            .put(
                                    "/apex/com.android.btservices/app/BluetoothGoogle/BluetoothGoogle.apk",
                                    PRE_U_APK_IN_APEX_BLUETOOTH_BURNDOWN_LIST)
                            .put(
                                    "/apex/com.android.btservices/app/Bluetooth/Bluetooth.apk",
                                    PRE_U_APK_IN_APEX_BLUETOOTH_BURNDOWN_LIST)
                            .build();

    /**
     * Lists of known failures when running testApkInApex_nonClasspathClasses against pre-T devices.
     *
     * <p> Add the new item into this list only if the failure is caused by base device image (not the mainline train).
     */
    private static final ImmutableMap<String, ImmutableSet<String>> PRE_T_APK_IN_APEX_BURNDOWN_LIST =
        new ImmutableMap.Builder<String, ImmutableSet<String>>()
            .put("/apex/com.android.cellbroadcast/priv-app/GoogleCellBroadcastServiceModule/GoogleCellBroadcastServiceModule.apk",
                ImmutableSet.of(
                    // b/303732833
                    "Lcom/android/internal/util/Preconditions;"
                ))
            .build();

    /**
     * Fetch all jar files in BCP, SSCP and shared libs and extract all the classes.
     *
     * <p>This method cannot be static, as there are no static equivalents for {@link #getDevice()}
     * and {@link #getBuild()}.
     */
    @BeforeClassWithInfo
    public static void setupOnce(TestInformation testInfo) throws Exception {
        if (testInfo.getDevice() == null || testInfo.getBuildInfo() == null) {
            throw new RuntimeException("No device and/or build type specified!");
        }
        DeviceSdkLevel deviceSdkLevel = new DeviceSdkLevel(testInfo.getDevice());

        sBootclasspathJars = Classpaths.getJarsOnClasspath(testInfo.getDevice(), BOOTCLASSPATH);
        sSystemserverclasspathJars =
                Classpaths.getJarsOnClasspath(testInfo.getDevice(), SYSTEMSERVERCLASSPATH);
        sSharedLibs = deviceSdkLevel.isDeviceAtLeastS()
                ? Classpaths.getSharedLibraryInfos(testInfo.getDevice(), testInfo.getBuildInfo())
                : ImmutableList.of();
        sSharedLibJars = sSharedLibs.stream()
                .map(sharedLibraryInfo -> sharedLibraryInfo.paths)
                .flatMap(ImmutableCollection::stream)
                .filter(file -> doesFileExist(file, testInfo.getDevice()))
                // GmsCore should not contribute to *classpath.
                .filter(file -> !file.contains("GmsCore"))
                .filter(file -> !file.contains("com.google.android.gms"))
                .collect(ImmutableList.toImmutableList());
        final ImmutableSetMultimap.Builder<String, String> sharedLibsPathsToName =
                ImmutableSetMultimap.builder();
        sSharedLibs.forEach(sharedLibraryInfo -> {
                sharedLibraryInfo.paths.forEach(path ->
                        sharedLibsPathsToName.putAll(path, sharedLibraryInfo.name));
        });
        sSharedLibsPathsToName = sharedLibsPathsToName.build();

        final ImmutableSetMultimap.Builder<String, String> jarsToFiles =
                ImmutableSetMultimap.builder();
        final ImmutableSetMultimap.Builder<String, String> jarsToClasses =
                ImmutableSetMultimap.builder();
        Stream.of(sBootclasspathJars.stream(),
                        sSystemserverclasspathJars.stream(),
                        sSharedLibJars.stream())
                .reduce(Stream::concat).orElseGet(Stream::empty)
                .parallel()
                .forEach(jarPath -> {
                    File jar = null;
                    try {
                        jar = pullJarFromDevice(testInfo.getDevice(), jarPath);

                        ImmutableSet<String> files = getJarFileContents(jar);
                        synchronized (jarsToFiles) {
                            jarsToFiles.putAll(jarPath, files);
                        }

                        ImmutableSet<String> classes =
                                Classpaths.getClassDefsFromJar(jar).stream()
                                        .map(ClassDef::getType)
                                        // Inner classes always go with their parent.
                                        .filter(className -> !className.contains("$"))
                                        .collect(ImmutableSet.toImmutableSet());
                        synchronized (jarsToClasses) {
                            jarsToClasses.putAll(jarPath, classes);
                        }
                    } catch (DeviceNotAvailableException | IOException e) {
                        throw new RuntimeException(e);
                    } finally {
                        FileUtil.deleteFile(jar);
                    }
                });
        sJarsToFiles = jarsToFiles.build();
        sJarsToClasses = jarsToClasses.build();
    }

    @Before
    public void setup() {
        mDeviceSdkLevel = new DeviceSdkLevel(getDevice());
    }

    /**
     * Pretty prints a multimap to make it easier for a person to read it.
     *
     * It makes assumptions about the inputs: it assumes the keys are classes and the values are jar
     * files where they exist. It also assumes that for any given class there will be 2 or more jar
     * files where they are found.
     *
     * @return  the string pretty formatted
     */
    private String prettyPrint(Multimap<String, String> classesToJars) {
        if (classesToJars.isEmpty()) {
            return "No findings";
        }

        final HashMultimap<Collection<String>, String> jarsToClasses = HashMultimap.create();
        classesToJars.asMap().forEach((className, jarFiles) ->
                jarsToClasses.put(jarFiles, className)
        );

        StringBuilder sb = new StringBuilder();
        jarsToClasses.asMap().forEach((jars, classes) -> {
                    sb.append("The following jar files:\n");
                    jars.forEach((jar) -> sb.append("    ").append(jar).append('\n'));
                    sb.append("Contain the following duplicate classes:\n");
                    classes.forEach((klass) -> sb.append("    ").append(klass).append('\n'));
                    sb.append("End of duplications.\n\n");
                }
        );
        sb.append("This can result in runtime breakages (now or in a future release)."
                + " Read more at http://go/fixing-strict-java-packages\n");
        return sb.toString();
    }

    /**
     * Pretty prints a nested multimap to make it easier for a person to read it.
     *
     * It makes assumptions about the inputs: it assumes the outer keys are apk files (coming from
     * APK in apexes) and the outer values are a Multimap with keys being a jar file and values
     * classes that are defined in that jar and that also exist in the apk file.
     *
     * @return  the string pretty formatted
     */
    private String prettyPrint(
            HashMultimap<String, Multimap<String, String>> apkToJarToClasses) {
        if (apkToJarToClasses.isEmpty()) {
            return "No findings";
        }
        StringBuilder sb = new StringBuilder();
        apkToJarToClasses.forEach((apk, jarToClasses) -> {
            jarToClasses.asMap().forEach((jar, classes) -> {
                sb.append("The apk in apex and jar file:\n");
                sb.append("    ").append(apk).append('\n');
                sb.append("    ").append(jar).append('\n');
                sb.append("contain the following duplicate class definitions:\n");
                classes.forEach(klass -> sb.append("     ").append(klass).append('\n'));
                sb.append("End of duplications.\n\n");
            });
        });
        sb.append("This can result in runtime breakages (now or in a future release)."
                + " Read more at http://go/fixing-strict-java-packages\n");
        return sb.toString();
    }

    /**
     * Ensure that there are no duplicate classes among jars listed in BOOTCLASSPATH.
     */
    @Test
    public void testBootclasspath_nonDuplicateClasses() throws Exception {
        assumeTrue(mDeviceSdkLevel.isDeviceAtLeastT());
        assertThat(getDuplicateClasses(sBootclasspathJars)).isEmpty();
    }

    /**
     * Ensure that there are no duplicate classes among jars listed in SYSTEMSERVERCLASSPATH.
     */
    @Test
    public void testSystemServerClasspath_nonDuplicateClasses() throws Exception {
        assumeTrue(mDeviceSdkLevel.isDeviceAtLeastT());
        ImmutableSet<String> overlapBurndownList;
        if (hasFeature(FEATURE_AUTOMOTIVE)) {
            overlapBurndownList = ImmutableSet.copyOf(AUTOMOTIVE_HIDL_OVERLAP_BURNDOWN_LIST);
        } else if (hasFeature(FEATURE_WEARABLE)) {
            overlapBurndownList = ImmutableSet.copyOf(WEAR_HIDL_OVERLAP_BURNDOWN_LIST);
        } else {
            overlapBurndownList = ImmutableSet.of();
        }
        Multimap<String, String> duplicates = getDuplicateClasses(sSystemserverclasspathJars);
        Multimap<String, String> filtered = Multimaps.filterKeys(duplicates,
                duplicate -> !overlapBurndownList.contains(duplicate));

        assertWithMessage(prettyPrint(filtered))
                .that(filtered)
                .isEmpty();
    }

    /**
     * Ensure that there are no duplicate classes among jars listed in BOOTCLASSPATH and
     * SYSTEMSERVERCLASSPATH.
     */
    @Test
    public void testBootClasspathAndSystemServerClasspath_nonDuplicateClasses() throws Exception {
        assumeTrue(mDeviceSdkLevel.isDeviceAtLeastT());
        ImmutableList.Builder<String> jars = ImmutableList.builder();
        jars.addAll(sBootclasspathJars);
        jars.addAll(sSystemserverclasspathJars);
        ImmutableSet<String> overlapBurndownList;
        if (hasFeature(FEATURE_AUTOMOTIVE)) {
            overlapBurndownList = ImmutableSet.<String>builder()
                    .addAll(BCP_AND_SSCP_OVERLAP_BURNDOWN_LIST)
                    .addAll(AUTOMOTIVE_HIDL_OVERLAP_BURNDOWN_LIST).build();
        } else if (hasFeature(FEATURE_WEARABLE)) {
            overlapBurndownList = ImmutableSet.<String>builder()
                    .addAll(BCP_AND_SSCP_OVERLAP_BURNDOWN_LIST)
                    .addAll(WEAR_HIDL_OVERLAP_BURNDOWN_LIST).build();
        } else {
            overlapBurndownList = ImmutableSet.copyOf(BCP_AND_SSCP_OVERLAP_BURNDOWN_LIST);
        }
        Multimap<String, String> duplicates = getDuplicateClasses(jars.build());
        Multimap<String, String> filtered = Multimaps.filterKeys(duplicates,
                duplicate -> !overlapBurndownList.contains(duplicate)
                        && !jarsInSameApex(duplicates.get(duplicate)));

        assertWithMessage(prettyPrint(filtered))
                .that(filtered)
                .isEmpty();
    }

    /**
     * Ensure that there are no duplicate classes among APEX jars listed in BOOTCLASSPATH.
     */
    @Test
    public void testBootClasspath_nonDuplicateApexJarClasses() throws Exception {
        Multimap<String, String> duplicates = getDuplicateClasses(sBootclasspathJars);
        Multimap<String, String> filtered =
                Multimaps.filterValues(duplicates, jar -> jar.startsWith("/apex/"));

        assertWithMessage(prettyPrint(filtered))
                .that(filtered)
                .isEmpty();
    }

    /**
     * Ensure that there are no duplicate classes among APEX jars listed in SYSTEMSERVERCLASSPATH.
     */
    @Test
    public void testSystemServerClasspath_nonDuplicateApexJarClasses() throws Exception {
        Multimap<String, String> duplicates = getDuplicateClasses(sSystemserverclasspathJars);
        Multimap<String, String> filtered =
                Multimaps.filterValues(duplicates, jar -> jar.startsWith("/apex/"));

        assertWithMessage(prettyPrint(filtered))
                .that(filtered)
                .isEmpty();
    }

    /**
     * Ensure that there are no duplicate classes among APEX jars listed in BOOTCLASSPATH and
     * SYSTEMSERVERCLASSPATH.
     */
    @Test
    public void testBootClasspathAndSystemServerClasspath_nonApexDuplicateClasses()
            throws Exception {
        ImmutableList.Builder<String> jars = ImmutableList.builder();
        jars.addAll(sBootclasspathJars);
        jars.addAll(sSystemserverclasspathJars);

        Multimap<String, String> duplicates = getDuplicateClasses(jars.build());
        Multimap<String, String> filtered = Multimaps.filterKeys(duplicates,
                duplicate -> !BCP_AND_SSCP_OVERLAP_BURNDOWN_LIST.contains(duplicate));
        filtered = Multimaps.filterValues(filtered, jar -> jar.startsWith("/apex/"));

        assertWithMessage(prettyPrint(filtered))
                .that(filtered)
                .isEmpty();
    }

    /**
     * Ensure that there are no duplicate classes among jars listed in BOOTCLASSPATH and
     * shared library jars.
     */
    @Test
    public void testBootClasspathAndSharedLibs_nonDuplicateClasses() throws Exception {
        assumeTrue(mDeviceSdkLevel.isDeviceAtLeastS());
        final ImmutableList.Builder<String> jars = ImmutableList.builder();
        jars.addAll(sBootclasspathJars);
        jars.addAll(sSharedLibJars);
        final Multimap<String, String> duplicates = getDuplicateClasses(jars.build());
        final Multimap<String, String> filtered = Multimaps.filterKeys(duplicates,
                dupeClass -> {
                    try {
                        final Collection<String> dupeJars = duplicates.get(dupeClass);
                        // Duplicate is already known.
                        if (BCP_AND_SHARED_LIB_BURNDOWN_LIST.contains(dupeClass)) {
                            return false;
                        }
                        // Duplicate is only between different versions of the same shared library.
                        if (isSameLibrary(dupeJars)) {
                            return false;
                        }
                        // Pre-T, the Android test mock library included some platform classes.
                        if (!mDeviceSdkLevel.isDeviceAtLeastT()
                                && dupeJars.contains(ANDROID_TEST_MOCK_JAR)) {
                            return false;
                        }
                        // Different versions of the same library may have different names, and
                        // there's
                        // no reliable way to dedupe them. Ignore duplicates if they do not
                        // include apex jars.
                        if (dupeJars.stream().noneMatch(lib -> lib.startsWith("/apex/"))) {
                            return false;
                        }
                    } catch (DeviceNotAvailableException e) {
                        throw new RuntimeException(e);
                    }
                    return true;
                });
        assertWithMessage(prettyPrint(filtered))
                .that(filtered)
                .isEmpty();
    }

    /**
     * Ensure that no apk-in-apex bundles classes that could be eclipsed by jars in
     * BOOTCLASSPATH.
     */
    @Test
    public void testApkInApex_nonClasspathClasses() throws Exception {
        HashMultimap<String, Multimap<String, String>> perApkClasspathDuplicates =
                HashMultimap.create();
        Arrays.stream(collectApkInApexPaths())
                .filter(apk -> apk != null && !apk.isEmpty())
                .parallel()
                .forEach(apk -> {
                    File apkFile = null;
                    try {
                        apkFile = pullJarFromDevice(getDevice(), apk);
                        final ImmutableSet<String> apkClasses =
                                Classpaths.getClassDefsFromJar(apkFile).stream()
                                        .map(ClassDef::getType)
                                        .collect(ImmutableSet.toImmutableSet());
                        // b/226559955: The directory paths containing APKs contain the build ID,
                        // so strip out the @BUILD_ID portion.
                        // e.g. /apex/com.android.btservices/app/Bluetooth@SC-DEV/Bluetooth.apk ->
                        //      /apex/com.android.btservices/app/Bluetooth/Bluetooth.apk
                        apk = apk.replaceFirst("@[^/]*", "");
                        ImmutableSet<String> burndownClasses;
                        if (mDeviceSdkLevel.isDeviceAtLeastU()) {
                            burndownClasses = ImmutableSet.<String>builder()
                                    .addAll(FULL_APK_IN_APEX_BURNDOWN.getOrDefault(apk, ImmutableSet.of())).build();
                        } else if (mDeviceSdkLevel.isDeviceAtLeastT()) {
                            burndownClasses = ImmutableSet.<String>builder()
                                    .addAll(FULL_APK_IN_APEX_BURNDOWN.getOrDefault(apk, ImmutableSet.of()))
                                    .addAll(PRE_U_APK_IN_APEX_BURNDOWN_LIST.getOrDefault(apk, ImmutableSet.of())).build();
                        } else {
                            // testApkInApex_nonClasspathClasses is not part of CTS until T
                            // therefore, running this for pre-T devices with additional list of known failures.
                            // Another option would be to skip this test entirely for pre-T devices.
                            burndownClasses = ImmutableSet.<String>builder()
                                    .addAll(FULL_APK_IN_APEX_BURNDOWN.getOrDefault(apk, ImmutableSet.of()))
                                    .addAll(PRE_T_APK_IN_APEX_BURNDOWN_LIST.getOrDefault(apk, ImmutableSet.of())).build();
                        }
                        final Multimap<String, String> duplicates =
                                Multimaps.filterValues(sJarsToClasses, apkClasses::contains);
                        final Multimap<String, String> filteredDuplicates =
                                Multimaps.filterValues(duplicates,
                                    className -> !burndownClasses.contains(className)
                                            // TODO: b/225341497
                                            && !className.equals("Landroidx/annotation/Keep;"));
                        final Multimap<String, String> bcpOnlyDuplicates =
                                Multimaps.filterKeys(filteredDuplicates,
                                    sBootclasspathJars::contains);
                        if (!bcpOnlyDuplicates.isEmpty()) {
                            synchronized (perApkClasspathDuplicates) {
                                perApkClasspathDuplicates.put(apk, bcpOnlyDuplicates);
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        FileUtil.deleteFile(apkFile);
                    }
                });

        assertWithMessage(prettyPrint(perApkClasspathDuplicates))
                .that(perApkClasspathDuplicates)
                .isEmpty();
    }

    /**
     * Ensure that there are no androidx dependencies in BOOTCLASSPATH, SYSTEMSERVERCLASSPATH
     * and shared library jars.
     */
    @Test
    public void testBootClasspathAndSystemServerClasspathAndSharedLibs_noAndroidxDependencies()
            throws Exception {
        assumeTrue(mDeviceSdkLevel.isDeviceAtLeastT());
        // WARNING: Do not add more exceptions here, no androidx should be in bootclasspath.
        // See go/androidx-api-guidelines#module-naming for more details.
        final ImmutableMap<String, ImmutableSet<String>>
                LegacyExemptAndroidxSharedLibsNamesToClasses =
                new ImmutableMap.Builder<String, ImmutableSet<String>>()
                .put("androidx.camera.extensions.impl",
                    ImmutableSet.of("Landroidx/camera/extensions/impl/",
                    "Landroidx/camera/extensions/impl/advanced/", "Landroidx/annotation"))
                .put("androidx.window.extensions",
                    ImmutableSet.of("Landroidx/window/common/", "Landroidx/window/extensions/",
                        "Landroidx/window/util/"))
                .put("androidx.window.sidecar",
                    ImmutableSet.of("Landroidx/window/common/", "Landroidx/window/sidecar",
                        "Landroidx/window/util"))
                .put("com.google.android.camera.experimental2019",
                    ImmutableSet.of("Landroidx/annotation"))
                .put("com.google.android.camera.experimental2020_midyear",
                    ImmutableSet.of("Landroidx/annotation"))
                .build();
        assertWithMessage("There must not be any androidx classes on the "
            + "bootclasspath. Please use alternatives provided by the platform instead. "
            + "See go/androidx-api-guidelines#module-naming.")
                .that(sJarsToClasses.entries().stream()
                        .filter(e -> e.getKey().endsWith(".jar"))
                        .filter(e -> e.getValue().startsWith("Landroidx/"))
                        .filter(e -> !isLegacyAndroidxDependency(
                            LegacyExemptAndroidxSharedLibsNamesToClasses, e.getKey(), e.getValue()))
                        .collect(Collectors.toList())
                ).isEmpty();
    }

    /**
     * Ensure that there are no kotlin files in BOOTCLASSPATH, SYSTEMSERVERCLASSPATH
     * and shared library jars.
     */
    @Test
    public void testNoKotlinFilesInClasspaths() throws Exception {
        // This test was not in CTS until U.
        assumeTrue(mDeviceSdkLevel.isDeviceAtLeastU());
        ImmutableList<String> kotlinFiles =
                Stream.of(sBootclasspathJars.stream(),
                        sSystemserverclasspathJars.stream(),
                        sSharedLibJars.stream())
                .reduce(Stream::concat).orElseGet(Stream::empty)
                .parallel()
                .filter(jarPath -> {
                    // Exclude shared library apks.
                    return jarPath.endsWith(".jar")
                            && sJarsToFiles.get(jarPath)
                                .stream()
                                .anyMatch(file -> file.contains(".kotlin_builtins")
                                        || file.contains(".kotlin_module"));
                })
                .collect(ImmutableList.toImmutableList());
        assertThat(kotlinFiles).isEmpty();
    }

    /**
     * Ensure that all classes from protobuf libraries are jarjared before
     * included in BOOTCLASSPATH, SYSTEMSERVERCLASSPATH and shared library jars
     */
    @Test
    public void testNoProtobufClassesWithoutJarjar() throws Exception {
        assumeTrue(mDeviceSdkLevel.isDeviceAtLeastU());
        assertWithMessage("Classes from protobuf libraries must not be included in bootclasspath "
            + "and systemserverclasspath without being jarjared.")
                .that(Stream.of(sBootclasspathJars.stream(),
                                sSystemserverclasspathJars.stream(),
                                sSharedLibJars.stream())
                        .reduce(Stream::concat).orElseGet(Stream::empty)
                        .parallel()
                        .filter(jarPath -> {
                            return sJarsToClasses
                                    .get(jarPath)
                                    .stream()
                                    .anyMatch(cls -> cls.startsWith("Lcom/google/protobuf/"));
                        })
                        .collect(ImmutableList.toImmutableList())
                ).isEmpty();
    }

    private static File pullJarFromDevice(INativeDevice device,
            String remoteJarPath) throws DeviceNotAvailableException {
        File jar = device.pullFile(remoteJarPath);
        if (jar == null) {
            throw new IllegalStateException("could not pull remote file " + remoteJarPath);
        }
        return jar;
    }

    private static ImmutableSet<String> getJarFileContents(File jar) throws IOException {
        try (JarFile jarFile = new JarFile(jar)) {
            return jarFile.stream()
                    .map(JarEntry::getName)
                    .collect(ImmutableSet.toImmutableSet());
        }
    }

    private boolean isLegacyAndroidxDependency(
            ImmutableMap<String, ImmutableSet<String>> legacyExemptAndroidxSharedLibsNamesToClasses,
            String path, String className) {
        return sSharedLibsPathsToName.get(path).stream()
                .filter(legacyExemptAndroidxSharedLibsNamesToClasses::containsKey)
                .flatMap(name -> legacyExemptAndroidxSharedLibsNamesToClasses.get(name).stream())
                .anyMatch(className::startsWith);
    }

    private String[] collectApkInApexPaths() {
        try {
            final CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
            final String installError = getDevice().installPackage(
                    buildHelper.getTestFile(TEST_HELPER_APK), false);
            assertWithMessage("Failed to install %s due to: %s", TEST_HELPER_APK, installError)
                    .that(installError).isNull();
            runDeviceTests(new DeviceTestRunOptions(TEST_HELPER_PACKAGE)
                    .setDevice(getDevice())
                    .setTestClassName(TEST_HELPER_PACKAGE + ".ApexDeviceTest")
                    .setTestMethodName("testCollectApkInApexPaths"));
            final String remoteFile = "/sdcard/apk-in-apex-paths.txt";
            String content;
            try {
                content = getDevice().pullFileContents(remoteFile);
            } finally {
                getDevice().deleteFile(remoteFile);
            }
            return content.split("\n");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                getDevice().uninstallPackage(TEST_HELPER_PACKAGE);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Gets the duplicate classes within a list of jar files.
     *
     * @param jars a list of jar files.
     * @return a multimap with the class name as a key and the jar files as a value.
     */
    private Multimap<String, String> getDuplicateClasses(ImmutableCollection<String> jars) {
        final HashMultimap<String, String> allClasses = HashMultimap.create();
        Multimaps.invertFrom(Multimaps.filterKeys(sJarsToClasses, jars::contains), allClasses);
        return Multimaps.filterKeys(allClasses, key -> allClasses.get(key).size() > 1);
    }

    private boolean jarsInSameApex(Collection<String> jars) {
        return jars.stream()
            .map(path -> apexForJar(path).orElse(path))
            .distinct()
            .count() <= 1;
    }

    private Optional<String> apexForJar(String jar) {
        Matcher m = APEX_JAR_PATTERN.matcher(jar);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(m.group("apexName"));
    }

    private static boolean doesFileExist(String path, ITestDevice device) {
        assertThat(path).isNotNull();
        try {
            return device.doesFileExist(path);
        } catch (DeviceNotAvailableException e) {
            throw new RuntimeException("Could not check whether " + path + " exists on device", e);
        }
    }

    /**
     * Get the name of a shared library.
     *
     * @return the shared library name or the jar's path if it's not a shared library.
     */
    private String getSharedLibraryNameOrPath(String jar) {
        return sSharedLibs.stream()
                .filter(sharedLib -> sharedLib.paths.contains(jar))
                .map(sharedLib -> sharedLib.name)
                .findFirst().orElse(jar);
    }

    /**
     * Check whether a list of jars are all different versions of the same library.
     */
    private boolean isSameLibrary(Collection<String> jars) {
        return jars.stream()
                .map(this::getSharedLibraryNameOrPath)
                .distinct()
                .count() == 1;
    }

    private boolean hasFeature(String featureName) throws DeviceNotAvailableException {
        return getDevice().executeShellCommand("pm list features").contains(featureName);
    }
}
