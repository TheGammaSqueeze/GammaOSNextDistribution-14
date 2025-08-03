#ifndef HIDL_GENERATED_ANDROID_HARDWARE_INPUT_COMMON_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_INPUT_COMMON_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace input {
namespace common {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class Axis : uint64_t;
enum class ToolType : uint8_t;
struct PointerProperties;
struct PointerCoords;
enum class SourceClass : uint8_t;
enum class Source : uint32_t;
enum class Action : int32_t;
enum class EdgeFlag : int32_t;
enum class PolicyFlag : uint32_t;
enum class Button : int32_t;
enum class Meta : int32_t;
enum class Flag : int32_t;
struct VideoFrame;
struct MotionEvent;
enum class Classification : uint8_t;

/**
 * Constants that identify each individual axis of a motion event.
 */
enum class Axis : uint64_t {
    /**
     * Axis constant: X axis of a motion event.
     *
     * - For a touch screen, reports the absolute X screen position of the center of
     * the touch contact area.  The units are display pixels.
     * - For a touch pad, reports the absolute X surface position of the center of the touch
     * contact area. The units are device-dependent.
     * - For a mouse, reports the absolute X screen position of the mouse pointer.
     * The units are display pixels.
     * - For a trackball, reports the relative horizontal displacement of the trackball.
     * The value is normalized to a range from -1.0 (left) to 1.0 (right).
     * - For a joystick, reports the absolute X position of the joystick.
     * The value is normalized to a range from -1.0 (left) to 1.0 (right).
     */
    X = 0ull,
    /**
     * Axis constant: Y axis of a motion event.
     *
     * - For a touch screen, reports the absolute Y screen position of the center of
     * the touch contact area.  The units are display pixels.
     * - For a touch pad, reports the absolute Y surface position of the center of the touch
     * contact area. The units are device-dependent.
     * - For a mouse, reports the absolute Y screen position of the mouse pointer.
     * The units are display pixels.
     * - For a trackball, reports the relative vertical displacement of the trackball.
     * The value is normalized to a range from -1.0 (up) to 1.0 (down).
     * - For a joystick, reports the absolute Y position of the joystick.
     * The value is normalized to a range from -1.0 (up or far) to 1.0 (down or near).
     */
    Y = 1ull,
    /**
     * Axis constant: Pressure axis of a motion event.
     *
     * - For a touch screen or touch pad, reports the approximate pressure applied to the surface
     * by a finger or other tool.  The value is normalized to a range from
     * 0 (no pressure at all) to 1 (normal pressure), although values higher than 1
     * may be generated depending on the calibration of the input device.
     * - For a trackball, the value is set to 1 if the trackball button is pressed
     * or 0 otherwise.
     * - For a mouse, the value is set to 1 if the primary mouse button is pressed
     * or 0 otherwise.
     */
    PRESSURE = 2ull,
    /**
     * Axis constant: Size axis of a motion event.
     *
     * - For a touch screen or touch pad, reports the approximate size of the contact area in
     * relation to the maximum detectable size for the device.  The value is normalized
     * to a range from 0 (smallest detectable size) to 1 (largest detectable size),
     * although it is not a linear scale. This value is of limited use.
     * To obtain calibrated size information, see
     * {@link TOUCH_MAJOR} or {@link TOOL_MAJOR}.
     */
    SIZE = 3ull,
    /**
     * Axis constant: TouchMajor axis of a motion event.
     *
     * - For a touch screen, reports the length of the major axis of an ellipse that
     * represents the touch area at the point of contact.
     * The units are display pixels.
     * - For a touch pad, reports the length of the major axis of an ellipse that
     * represents the touch area at the point of contact.
     * The units are device-dependent.
     */
    TOUCH_MAJOR = 4ull,
    /**
     * Axis constant: TouchMinor axis of a motion event.
     *
     * - For a touch screen, reports the length of the minor axis of an ellipse that
     * represents the touch area at the point of contact.
     * The units are display pixels.
     * - For a touch pad, reports the length of the minor axis of an ellipse that
     * represents the touch area at the point of contact.
     * The units are device-dependent.
     *
     * When the touch is circular, the major and minor axis lengths will be equal to one another.
     */
    TOUCH_MINOR = 5ull,
    /**
     * Axis constant: ToolMajor axis of a motion event.
     *
     * - For a touch screen, reports the length of the major axis of an ellipse that
     * represents the size of the approaching finger or tool used to make contact.
     * - For a touch pad, reports the length of the major axis of an ellipse that
     * represents the size of the approaching finger or tool used to make contact.
     * The units are device-dependent.
     *
     * When the touch is circular, the major and minor axis lengths will be equal to one another.
     *
     * The tool size may be larger than the touch size since the tool may not be fully
     * in contact with the touch sensor.
     */
    TOOL_MAJOR = 6ull,
    /**
     * Axis constant: ToolMinor axis of a motion event.
     *
     * - For a touch screen, reports the length of the minor axis of an ellipse that
     * represents the size of the approaching finger or tool used to make contact.
     * - For a touch pad, reports the length of the minor axis of an ellipse that
     * represents the size of the approaching finger or tool used to make contact.
     * The units are device-dependent.
     *
     * When the touch is circular, the major and minor axis lengths will be equal to one another.
     *
     * The tool size may be larger than the touch size since the tool may not be fully
     * in contact with the touch sensor.
     */
    TOOL_MINOR = 7ull,
    /**
     * Axis constant: Orientation axis of a motion event.
     *
     * - For a touch screen or touch pad, reports the orientation of the finger
     * or tool in radians relative to the vertical plane of the device.
     * An angle of 0 radians indicates that the major axis of contact is oriented
     * upwards, is perfectly circular or is of unknown orientation.  A positive angle
     * indicates that the major axis of contact is oriented to the right.  A negative angle
     * indicates that the major axis of contact is oriented to the left.
     * The full range is from -PI/2 radians (finger pointing fully left) to PI/2 radians
     * (finger pointing fully right).
     * - For a stylus, the orientation indicates the direction in which the stylus
     * is pointing in relation to the vertical axis of the current orientation of the screen.
     * The range is from -PI radians to PI radians, where 0 is pointing up,
     * -PI/2 radians is pointing left, -PI or PI radians is pointing down, and PI/2 radians
     * is pointing right.  See also {@link TILT}.
     */
    ORIENTATION = 8ull,
    /**
     * Axis constant: Vertical Scroll axis of a motion event.
     *
     * - For a mouse, reports the relative movement of the vertical scroll wheel.
     * The value is normalized to a range from -1.0 (down) to 1.0 (up).
     *
     * The framework may use this axis to scroll views vertically.
     */
    VSCROLL = 9ull,
    /**
     * Axis constant: Horizontal Scroll axis of a motion event.
     *
     * - For a mouse, reports the relative movement of the horizontal scroll wheel.
     * The value is normalized to a range from -1.0 (left) to 1.0 (right).
     *
     * The framework may use this axis to scroll views horizontally.
     */
    HSCROLL = 10ull,
    /**
     * Axis constant: Z axis of a motion event.
     *
     * - For a joystick, reports the absolute Z position of the joystick.
     * The value is normalized to a range from -1.0 (high) to 1.0 (low).
     * <em>On game pads with two analog joysticks, this axis is often reinterpreted
     * to report the absolute X position of the second joystick instead.</em>
     */
    Z = 11ull,
    /**
     * Axis constant: X Rotation axis of a motion event.
     *
     * - For a joystick, reports the absolute rotation angle about the X axis.
     * The value is normalized to a range from -1.0 (counter-clockwise) to 1.0 (clockwise).
     */
    RX = 12ull,
    /**
     * Axis constant: Y Rotation axis of a motion event.
     *
     * - For a joystick, reports the absolute rotation angle about the Y axis.
     * The value is normalized to a range from -1.0 (counter-clockwise) to 1.0 (clockwise).
     */
    RY = 13ull,
    /**
     * Axis constant: Z Rotation axis of a motion event.
     *
     * - For a joystick, reports the absolute rotation angle about the Z axis.
     * The value is normalized to a range from -1.0 (counter-clockwise) to 1.0 (clockwise).
     * On game pads with two analog joysticks, this axis is often reinterpreted
     * to report the absolute Y position of the second joystick instead.
     */
    RZ = 14ull,
    /**
     * Axis constant: Hat X axis of a motion event.
     *
     * - For a joystick, reports the absolute X position of the directional hat control.
     * The value is normalized to a range from -1.0 (left) to 1.0 (right).
     */
    HAT_X = 15ull,
    /**
     * Axis constant: Hat Y axis of a motion event.
     *
     * - For a joystick, reports the absolute Y position of the directional hat control.
     * The value is normalized to a range from -1.0 (up) to 1.0 (down).
     */
    HAT_Y = 16ull,
    /**
     * Axis constant: Left Trigger axis of a motion event.
     *
     * - For a joystick, reports the absolute position of the left trigger control.
     * The value is normalized to a range from 0.0 (released) to 1.0 (fully pressed).
     */
    LTRIGGER = 17ull,
    /**
     * Axis constant: Right Trigger axis of a motion event.
     *
     * - For a joystick, reports the absolute position of the right trigger control.
     * The value is normalized to a range from 0.0 (released) to 1.0 (fully pressed).
     */
    RTRIGGER = 18ull,
    /**
     * Axis constant: Throttle axis of a motion event.
     *
     * - For a joystick, reports the absolute position of the throttle control.
     * The value is normalized to a range from 0.0 (fully open) to 1.0 (fully closed).
     */
    THROTTLE = 19ull,
    /**
     * Axis constant: Rudder axis of a motion event.
     *
     * - For a joystick, reports the absolute position of the rudder control.
     * The value is normalized to a range from -1.0 (turn left) to 1.0 (turn right).
     */
    RUDDER = 20ull,
    /**
     * Axis constant: Wheel axis of a motion event.
     *
     * - For a joystick, reports the absolute position of the steering wheel control.
     * The value is normalized to a range from -1.0 (turn left) to 1.0 (turn right).
     */
    WHEEL = 21ull,
    /**
     * Axis constant: Gas axis of a motion event.
     *
     * - For a joystick, reports the absolute position of the gas (accelerator) control.
     * The value is normalized to a range from 0.0 (no acceleration)
     * to 1.0 (maximum acceleration).
     */
    GAS = 22ull,
    /**
     * Axis constant: Brake axis of a motion event.
     *
     * - For a joystick, reports the absolute position of the brake control.
     * The value is normalized to a range from 0.0 (no braking) to 1.0 (maximum braking).
     */
    BRAKE = 23ull,
    /**
     * Axis constant: Distance axis of a motion event.
     *
     * - For a stylus, reports the distance of the stylus from the screen.
     * A value of 0.0 indicates direct contact and larger values indicate increasing
     * distance from the surface.
     */
    DISTANCE = 24ull,
    /**
     * Axis constant: Tilt axis of a motion event.
     *
     * - For a stylus, reports the tilt angle of the stylus in radians where
     * 0 radians indicates that the stylus is being held perpendicular to the
     * surface, and PI/2 radians indicates that the stylus is being held flat
     * against the surface.
     */
    TILT = 25ull,
    /**
     * Axis constant:  Generic scroll axis of a motion event.
     *
     * - This is used for scroll axis motion events that can't be classified as strictly
     *   vertical or horizontal. The movement of a rotating scroller is an example of this.
     */
    SCROLL = 26ull,
    /**
     * Axis constant: The movement of x position of a motion event.
     *
     * - For a mouse, reports a difference of x position between the previous position.
     * This is useful when pointer is captured, in that case the mouse pointer doesn't
     * change the location but this axis reports the difference which allows the app
     * to see how the mouse is moved.
     */
    RELATIVE_X = 27ull,
    /**
     * Axis constant: The movement of y position of a motion event.
     *
     * Same as {@link RELATIVE_X}, but for y position.
     */
    RELATIVE_Y = 28ull,
    /**
     * Axis constant: Generic 1 axis of a motion event.
     * The interpretation of a generic axis is device-specific.
     */
    GENERIC_1 = 32ull,
    /**
     * Axis constant: Generic 2 axis of a motion event.
     * The interpretation of a generic axis is device-specific.
     */
    GENERIC_2 = 33ull,
    /**
     * Axis constant: Generic 3 axis of a motion event.
     * The interpretation of a generic axis is device-specific.
     */
    GENERIC_3 = 34ull,
    /**
     * Axis constant: Generic 4 axis of a motion event.
     * The interpretation of a generic axis is device-specific.
     */
    GENERIC_4 = 35ull,
    /**
     * Axis constant: Generic 5 axis of a motion event.
     * The interpretation of a generic axis is device-specific.
     */
    GENERIC_5 = 36ull,
    /**
     * Axis constant: Generic 6 axis of a motion event.
     * The interpretation of a generic axis is device-specific.
     */
    GENERIC_6 = 37ull,
    /**
     * Axis constant: Generic 7 axis of a motion event.
     * The interpretation of a generic axis is device-specific.
     */
    GENERIC_7 = 38ull,
    /**
     * Axis constant: Generic 8 axis of a motion event.
     * The interpretation of a generic axis is device-specific.
     */
    GENERIC_8 = 39ull,
    /**
     * Axis constant: Generic 9 axis of a motion event.
     * The interpretation of a generic axis is device-specific.
     */
    GENERIC_9 = 40ull,
    /**
     * Axis constant: Generic 10 axis of a motion event.
     * The interpretation of a generic axis is device-specific.
     */
    GENERIC_10 = 41ull,
    /**
     * Axis constant: Generic 11 axis of a motion event.
     * The interpretation of a generic axis is device-specific.
     */
    GENERIC_11 = 42ull,
    /**
     * Axis constant: Generic 12 axis of a motion event.
     * The interpretation of a generic axis is device-specific.
     */
    GENERIC_12 = 43ull,
    /**
     * Axis constant: Generic 13 axis of a motion event.
     * The interpretation of a generic axis is device-specific.
     */
    GENERIC_13 = 44ull,
    /**
     * Axis constant: Generic 14 axis of a motion event.
     * The interpretation of a generic axis is device-specific.
     */
    GENERIC_14 = 45ull,
    /**
     * Axis constant: Generic 15 axis of a motion event.
     * The interpretation of a generic axis is device-specific.
     */
    GENERIC_15 = 46ull,
    /**
     * Axis constant: Generic 16 axis of a motion event.
     * The interpretation of a generic axis is device-specific.
     */
    GENERIC_16 = 47ull,
};

/**
 * Tool type of a pointer
 */
enum class ToolType : uint8_t {
    UNKNOWN = 0,
    FINGER = 1,
    STYLUS = 2,
    MOUSE = 3,
    ERASER = 4,
};

/**
 * Properties of a particular pointer. Analogous to Android's PointerProperties.
 */
struct PointerProperties final {
    /**
     * A number identifying a specific pointer. When a pointer is lifted,
     * this value may be reused by another new pointer, even during the
     * same gesture. For example, if there are two pointers touching the screen
     * at the same time, they might have pointer ID's of 0 and 1. If the
     * pointer with id = 0 is lifted, while the pointer with id = 1 remains, and
     * a new pointer is placed on the screen, then the new pointer may receive
     * an id of 0. While a pointer is active, it is guaranteed to keep the same
     * id.
     */
    int32_t id __attribute__ ((aligned(4)));
    /**
     * Type of tool used to make contact, such as a finger or stylus, if known.
     */
    ::android::hardware::input::common::V1_0::ToolType toolType __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::input::common::V1_0::PointerProperties, id) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::input::common::V1_0::PointerProperties, toolType) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::input::common::V1_0::PointerProperties) == 8, "wrong size");
static_assert(__alignof(::android::hardware::input::common::V1_0::PointerProperties) == 4, "wrong alignment");

/**
 * Pointer coordinate data. Analogous to Android's PointerCoords.
 */
struct PointerCoords final {
    /**
     * Bitfield of axes that are present in this structure.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::input::common::V1_0::Axis> bits __attribute__ ((aligned(8)));
    /**
     * The values corresponding to each non-zero axis. This vector only
     * contains non-zero entries. If an axis that is not currently specified
     * in "bits" is requested, a zero value is returned.
     * There are only as many values stored here
     * as there are non-zero bits in the "bits" field.
     * The values are position-packed. So the first non-zero axis will be
     * at position 0, the next non-zero axis will be at position 1, and so on.
     */
    ::android::hardware::hidl_vec<float> values __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::input::common::V1_0::PointerCoords, bits) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::input::common::V1_0::PointerCoords, values) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::input::common::V1_0::PointerCoords) == 24, "wrong size");
static_assert(__alignof(::android::hardware::input::common::V1_0::PointerCoords) == 8, "wrong alignment");

enum class SourceClass : uint8_t {
    NONE = 0 /* 0 << 0 */,
    BUTTON = 1 /* 1 << 0 */,
    POINTER = 2 /* 1 << 1 */,
    NAVIGATION = 4 /* 1 << 2 */,
    POSITION = 8 /* 1 << 3 */,
    JOYSTICK = 16 /* 1 << 4 */,
};

/**
 * Input sources
 */
enum class Source : uint32_t {
    UNKNOWN = 0u,
    KEYBOARD = 257u /* (1 << 8) | SourceClass:BUTTON */,
    DPAD = 513u /* (1 << 9) | SourceClass:BUTTON */,
    GAMEPAD = 1025u /* (1 << 10) | SourceClass:BUTTON */,
    TOUCHSCREEN = 4098u /* (1 << 12) | SourceClass:POINTER */,
    MOUSE = 8194u /* (1 << 13) | SourceClass:POINTER */,
    STYLUS = 16386u /* (1 << 14) | SourceClass:POINTER */,
    BLUETOOTH_STYLUS = 49154u /* (1 << 15) | STYLUS */,
    TRACKBALL = 65540u /* (1 << 16) | SourceClass:NAVIGATION */,
    MOUSE_RELATIVE = 131076u /* (1 << 17) | SourceClass:NAVIGATION */,
    TOUCHPAD = 1048584u /* (1 << 20) | SourceClass:POSITION */,
    TOUCH_NAVIGATION = 2097152u /* (1 << 21) | SourceClass:NONE */,
    ROTARY_ENCODER = 4194304u /* (1 << 22) | SourceClass:NONE */,
    JOYSTICK = 16777232u /* (1 << 24) | SourceClass:JOYSTICK */,
    ANY = 4294967040u /* 0xFFFFFF00 */,
};

/**
 * Motion event actions
 */
enum class Action : int32_t {
    /**
     * A pressed gesture has started, the motion contains the initial starting location.
     */
    DOWN = 0,
    /**
     * A pressed gesture has finished, the motion contains the final release location
     * as well as any intermediate points since the last down or move event.
     */
    UP = 1,
    /**
     * A change has happened during a press gesture (between AMOTION_EVENT_ACTION_DOWN and
     * AMOTION_EVENT_ACTION_UP). The motion contains the most recent point.
     */
    MOVE = 2,
    /**
     * The current gesture has been aborted.
     * You will not receive any more points in it. You must treat this as
     * an up event, but not perform any action that you normally would.
     */
    CANCEL = 3,
    /**
     * A movement has happened outside of the normal bounds of the UI element.
     * This does not provide a full gesture, but only the initial location of the movement/touch.
     */
    OUTSIDE = 4,
    /**
     * A non-primary pointer has gone down.
     */
    POINTER_DOWN = 5,
    /**
     * A non-primary pointer has gone up.
     */
    POINTER_UP = 6,
    /**
     * A change happened but the pointer is not down (unlike AMOTION_EVENT_ACTION_MOVE).
     * The motion contains the most recent point, as well as any intermediate points since
     * the last hover move event.
     */
    HOVER_MOVE = 7,
    /**
     * The motion event contains relative vertical and/or horizontal scroll offsets.
     * Use getAxisValue to retrieve the information from AMOTION_EVENT_AXIS_VSCROLL
     * and AMOTION_EVENT_AXIS_HSCROLL.
     * The pointer may or may not be down when this event is dispatched.
     * The framework will always deliver this action to the window under the pointer, which
     * may not be the window currently touched.
     */
    SCROLL = 8,
    /**
     * The pointer is not down but has entered the boundaries of a window or view.
     */
    HOVER_ENTER = 9,
    /**
     * The pointer is not down but has exited the boundaries of a window or view.
     */
    HOVER_EXIT = 10,
    /**
     * One or more buttons have been pressed.
     */
    BUTTON_PRESS = 11,
    /**
     * One or more buttons have been released.
     */
    BUTTON_RELEASE = 12,
};

/**
 * Edge flags
 */
enum class EdgeFlag : int32_t {
    /**
     * No edges are intersected
     */
    NONE = 0,
    /**
     * Motion intersected top edge of the screen
     */
    TOP = 1 /* 1 << 0 */,
    /**
     * Motion intersected bottom edge of the screen
     */
    BOTTOM = 2 /* 1 << 1 */,
    /**
     * Motion intersected left edge of the screen
     */
    LEFT = 4 /* 1 << 2 */,
    /**
     * Motion intersected right edge of the screen
     */
    RIGHT = 8 /* 1 << 3 */,
};

/**
 * Policy flags
 */
enum class PolicyFlag : uint32_t {
    /**
     * Event should wake the device
     */
    WAKE = 1u /* 1 << 0 */,
    /**
     * Key is virtual, and should generate haptic feedback
     */
    VIRTUAL = 2u /* 1 << 1 */,
    /**
     * Key is the special function modifier
     */
    FUNCTION = 4u /* 1 << 2 */,
    /**
     * Key represents a special gesture that has been detected
     * by the touch firmware or driver.
     */
    GESTURE = 8u /* 1 << 3 */,
    /**
     * Event was injected
     */
    INJECTED = 16777216u /* 1 << 24 */,
    /**
     * Event comes from a trusted source, such as a directly attached input
     * device or an application with system-wide event injection permission.
     */
    TRUSTED = 33554432u /* 1 << 25 */,
    /**
     * Event has passed through an input filter.
     */
    FILTERED = 67108864u /* 1 << 26 */,
    /**
     * Disable automatic key repeating behaviour.
     */
    DISABLE_KEY_REPEAT = 134217728u /* 1 << 27 */,
    /**
     * Device was in an interactive state when the event was intercepted
     */
    INTERACTIVE = 536870912u /* 1 << 29 */,
    /**
     * Event should be dispatched to applications
     */
    PASS_TO_USER = 1073741824u /* 1 << 30 */,
};

/**
 * Buttons that are associated with motion events.
 */
enum class Button : int32_t {
    NONE = 0,
    PRIMARY = 1 /* 1 << 0 */,
    SECONDARY = 2 /* 1 << 1 */,
    TERTIARY = 4 /* 1 << 2 */,
    BACK = 8 /* 1 << 3 */,
    FORWARD = 16 /* 1 << 4 */,
    STYLUS_PRIMARY = 32 /* 1 << 5 */,
    STYLUS_SECONDARY = 64 /* 1 << 6 */,
};

/**
 * Meta key / modifier state
 */
enum class Meta : int32_t {
    NONE = 0,
    /**
     * One of the ALT meta keys is pressed.
     */
    ALT_ON = 2 /* 1 << 1 */,
    /**
     * The left ALT meta key is pressed.
     */
    ALT_LEFT_ON = 16 /* 1 << 4 */,
    /**
     * The right ALT meta key is pressed.
     */
    ALT_RIGHT_ON = 32 /* 1 << 5 */,
    /**
     * One of the SHIFT meta keys is pressed.
     */
    SHIFT_ON = 1 /* 1 << 0 */,
    /**
     * The left SHIFT meta key is pressed.
     */
    SHIFT_LEFT_ON = 64 /* 1 << 6 */,
    /**
     * The right SHIFT meta key is pressed.
     */
    SHIFT_RIGHT_ON = 128 /* 1 << 7 */,
    /**
     * The SYM meta key is pressed.
     */
    SYM_ON = 4 /* 1 << 2 */,
    /**
     * The FUNCTION meta key is pressed.
     */
    FUNCTION_ON = 8 /* 1 << 3 */,
    /**
     * One of the CTRL meta keys is pressed.
     */
    CTRL_ON = 4096 /* 1 << 12 */,
    /**
     * The left CTRL meta key is pressed.
     */
    CTRL_LEFT_ON = 8192 /* 1 << 13 */,
    /**
     * The right CTRL meta key is pressed.
     */
    CTRL_RIGHT_ON = 16384 /* 1 << 14 */,
    /**
     * One of the META meta keys is pressed.
     */
    META_ON = 65536 /* 1 << 16 */,
    /**
     * The left META meta key is pressed.
     */
    META_LEFT_ON = 131072 /* 1 << 17 */,
    /**
     * The right META meta key is pressed.
     */
    META_RIGHT_ON = 262144 /* 1 << 18 */,
    /**
     * The CAPS LOCK meta key is on.
     */
    CAPS_LOCK_ON = 1048576 /* 1 << 20 */,
    /**
     * The NUM LOCK meta key is on.
     */
    NUM_LOCK_ON = 2097152 /* 1 << 21 */,
    /**
     * The SCROLL LOCK meta key is on.
     */
    SCROLL_LOCK_ON = 4194304 /* 1 << 22 */,
};

/**
 * Motion event flags
 */
enum class Flag : int32_t {
    /**
     * Indicates that the window that received this motion event is partly
     * or wholly obscured by another visible window above it. This flag is set to true
     * even if the event did not directly pass through the obscured area.
     * A security sensitive application can check this flag to identify situations in which
     * a malicious application may have covered up part of its content for the purpose
     * of misleading the user or hijacking touches. An appropriate response might be
     * to drop the suspect touches or to take additional precautions to confirm the user's
     * actual intent.
     */
    WINDOW_IS_OBSCURED = 1 /* 1 << 0 */,
    /**
     * This flag indicates that the event has been generated by a gesture generator. It
     * could be used, for example, to determine whether touch slop should be applied.
     */
    IS_GENERATED_GESTURE = 8 /* 1 << 3 */,
    /**
     * Motion event is inconsistent with previously sent motion events.
     */
    TAINTED = -2147483648 /* 1 << 31 */,
};

/**
 * Touch heatmap.
 *
 * The array is a 2-D row-major matrix with dimensions (height, width).
 * The heatmap data is rotated when device orientation changes.
 *
 * Example:
 *
 * If the data in the array is:
 * data[i] = i for i in 0 .. 59,
 * then it can be represented as a 10 x 6 matrix:
 *
 *  <--   width   -->
 *   0  1  2  3  4  5   ^
 *   6  7  8  9 10 11   |
 *  12 13 14 15 16 17   |
 *  18    ...      23   |
 *  24    ...      29   | height
 *  30    ...      35   |
 *  36    ...      41   |
 *  42    ...      47   |
 *  48    ...      53   |
 *  54    ...      59   v
 *
 * Looking at the device in standard portrait orientation,
 * the element "0" is the top left of the screen,
 * "5" is at the top right, and "59" is the bottom right.
 * Here height=10 and width=6.
 *
 * If the screen orientation changes to landscape (a 90 degree orientation
 * change), the frame's dimensions will become 6 x 10
 * and the data will look as follows:
 * 54 48 42 36 30 24 18 12  6  0     ^
 * ...                  13  7  1     |
 * ...                  14  8  2     | height
 * ...                  15  9  3     |
 * ...                  16 10  4     |
 * 59 53 47 41 35 29 23 17 11  5     v
 * <--        width          -->
 *
 * Here the element "0" is at the physical top left of the unrotated screen.
 *
 * Since the coordinates of a MotionEvent are also adjusted based on the
 * orientation, the rotation of the video frame data ensures that
 * the axes for MotionEvent and VideoFrame data are consistent.
 */
struct VideoFrame final {
    /**
     * Video frame data.
     * Size of the data is height * width.
     */
    ::android::hardware::hidl_vec<int16_t> data __attribute__ ((aligned(8)));
    uint32_t height __attribute__ ((aligned(4)));
    uint32_t width __attribute__ ((aligned(4)));
    /**
     * Time at which the frame was collected, in nanoseconds.
     * Measured with the same clock that is used to populate MotionEvent times.
     */
    uint64_t timestamp __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::input::common::V1_0::VideoFrame, data) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::input::common::V1_0::VideoFrame, height) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::input::common::V1_0::VideoFrame, width) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::input::common::V1_0::VideoFrame, timestamp) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::input::common::V1_0::VideoFrame) == 32, "wrong size");
static_assert(__alignof(::android::hardware::input::common::V1_0::VideoFrame) == 8, "wrong alignment");

/**
 * Analogous to Android's native MotionEvent / NotifyMotionArgs.
 * Stores the basic information about pointer movements.
 */
struct MotionEvent final {
    /**
     * The id of the device which produced this event.
     */
    int32_t deviceId __attribute__ ((aligned(4)));
    /**
     * The source type of this event.
     */
    ::android::hardware::input::common::V1_0::Source source __attribute__ ((aligned(4)));
    /**
     * The display id associated with this event.
     */
    int32_t displayId __attribute__ ((aligned(4)));
    /**
     * Time when the initial touch down occurred, in nanoseconds.
     */
    int64_t downTime __attribute__ ((aligned(8)));
    /**
     * Time when this event occurred, in nanoseconds.
     */
    int64_t eventTime __attribute__ ((aligned(8)));
    /**
     * The kind of action being performed.
     */
    ::android::hardware::input::common::V1_0::Action action __attribute__ ((aligned(4)));
    /**
     * For ACTION_POINTER_DOWN or ACTION_POINTER_UP, this contains the associated pointer index.
     * The index may be used to get information about the pointer that has gone down or up.
     */
    uint8_t actionIndex __attribute__ ((aligned(1)));
    /**
     * The button that has been modified during a press or release action.
     */
    ::android::hardware::input::common::V1_0::Button actionButton __attribute__ ((aligned(4)));
    /**
     * The motion event flags.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::input::common::V1_0::Flag> flags __attribute__ ((aligned(4)));
    /**
     * The motion event policy flags.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::input::common::V1_0::PolicyFlag> policyFlags __attribute__ ((aligned(4)));
    /**
     * The edges, if any, that were touched by this motion event.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::input::common::V1_0::EdgeFlag> edgeFlags __attribute__ ((aligned(4)));
    /**
     * The state of any meta / modifier keys that were in effect when the event was generated.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::input::common::V1_0::Meta> metaState __attribute__ ((aligned(4)));
    /**
     * The state of buttons that are pressed.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::input::common::V1_0::Button> buttonState __attribute__ ((aligned(4)));
    /**
     * The precision of the X coordinate being reported.
     */
    float xPrecision __attribute__ ((aligned(4)));
    /**
     * The precision of the Y coordinate being reported.
     */
    float yPrecision __attribute__ ((aligned(4)));
    /**
     * The properties of each pointer present in this motion event.
     */
    ::android::hardware::hidl_vec<::android::hardware::input::common::V1_0::PointerProperties> pointerProperties __attribute__ ((aligned(8)));
    /**
     * The coordinates of each pointer.
     */
    ::android::hardware::hidl_vec<::android::hardware::input::common::V1_0::PointerCoords> pointerCoords __attribute__ ((aligned(8)));
    /**
     * Device time at which the event occurred, in microseconds.
     * Will wrap after a little over an hour.
     */
    uint32_t deviceTimestamp __attribute__ ((aligned(4)));
    /**
     * The video frames, if any, associated with the current or previous motion events.
     */
    ::android::hardware::hidl_vec<::android::hardware::input::common::V1_0::VideoFrame> frames __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::input::common::V1_0::MotionEvent, deviceId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::input::common::V1_0::MotionEvent, source) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::input::common::V1_0::MotionEvent, displayId) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::input::common::V1_0::MotionEvent, downTime) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::input::common::V1_0::MotionEvent, eventTime) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::input::common::V1_0::MotionEvent, action) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::input::common::V1_0::MotionEvent, actionIndex) == 36, "wrong offset");
static_assert(offsetof(::android::hardware::input::common::V1_0::MotionEvent, actionButton) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::input::common::V1_0::MotionEvent, flags) == 44, "wrong offset");
static_assert(offsetof(::android::hardware::input::common::V1_0::MotionEvent, policyFlags) == 48, "wrong offset");
static_assert(offsetof(::android::hardware::input::common::V1_0::MotionEvent, edgeFlags) == 52, "wrong offset");
static_assert(offsetof(::android::hardware::input::common::V1_0::MotionEvent, metaState) == 56, "wrong offset");
static_assert(offsetof(::android::hardware::input::common::V1_0::MotionEvent, buttonState) == 60, "wrong offset");
static_assert(offsetof(::android::hardware::input::common::V1_0::MotionEvent, xPrecision) == 64, "wrong offset");
static_assert(offsetof(::android::hardware::input::common::V1_0::MotionEvent, yPrecision) == 68, "wrong offset");
static_assert(offsetof(::android::hardware::input::common::V1_0::MotionEvent, pointerProperties) == 72, "wrong offset");
static_assert(offsetof(::android::hardware::input::common::V1_0::MotionEvent, pointerCoords) == 88, "wrong offset");
static_assert(offsetof(::android::hardware::input::common::V1_0::MotionEvent, deviceTimestamp) == 104, "wrong offset");
static_assert(offsetof(::android::hardware::input::common::V1_0::MotionEvent, frames) == 112, "wrong offset");
static_assert(sizeof(::android::hardware::input::common::V1_0::MotionEvent) == 128, "wrong size");
static_assert(__alignof(::android::hardware::input::common::V1_0::MotionEvent) == 8, "wrong alignment");

enum class Classification : uint8_t {
    NONE = 0,
    /**
     * Too early to classify the gesture, need more events.
     */
    AMBIGUOUS_GESTURE = 1,
    /**
     * User is force-pressing the screen.
     */
    DEEP_PRESS = 2,
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint64_t o);
static inline std::string toString(::android::hardware::input::common::V1_0::Axis o);
static inline void PrintTo(::android::hardware::input::common::V1_0::Axis o, ::std::ostream* os);
constexpr uint64_t operator|(const ::android::hardware::input::common::V1_0::Axis lhs, const ::android::hardware::input::common::V1_0::Axis rhs) {
    return static_cast<uint64_t>(static_cast<uint64_t>(lhs) | static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator|(const uint64_t lhs, const ::android::hardware::input::common::V1_0::Axis rhs) {
    return static_cast<uint64_t>(lhs | static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator|(const ::android::hardware::input::common::V1_0::Axis lhs, const uint64_t rhs) {
    return static_cast<uint64_t>(static_cast<uint64_t>(lhs) | rhs);
}
constexpr uint64_t operator&(const ::android::hardware::input::common::V1_0::Axis lhs, const ::android::hardware::input::common::V1_0::Axis rhs) {
    return static_cast<uint64_t>(static_cast<uint64_t>(lhs) & static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator&(const uint64_t lhs, const ::android::hardware::input::common::V1_0::Axis rhs) {
    return static_cast<uint64_t>(lhs & static_cast<uint64_t>(rhs));
}
constexpr uint64_t operator&(const ::android::hardware::input::common::V1_0::Axis lhs, const uint64_t rhs) {
    return static_cast<uint64_t>(static_cast<uint64_t>(lhs) & rhs);
}
constexpr uint64_t &operator|=(uint64_t& v, const ::android::hardware::input::common::V1_0::Axis e) {
    v |= static_cast<uint64_t>(e);
    return v;
}
constexpr uint64_t &operator&=(uint64_t& v, const ::android::hardware::input::common::V1_0::Axis e) {
    v &= static_cast<uint64_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::input::common::V1_0::ToolType o);
static inline void PrintTo(::android::hardware::input::common::V1_0::ToolType o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::input::common::V1_0::ToolType lhs, const ::android::hardware::input::common::V1_0::ToolType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::input::common::V1_0::ToolType rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::input::common::V1_0::ToolType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::input::common::V1_0::ToolType lhs, const ::android::hardware::input::common::V1_0::ToolType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::input::common::V1_0::ToolType rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::input::common::V1_0::ToolType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::input::common::V1_0::ToolType e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::input::common::V1_0::ToolType e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::input::common::V1_0::PointerProperties& o);
static inline void PrintTo(const ::android::hardware::input::common::V1_0::PointerProperties& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::input::common::V1_0::PointerProperties& lhs, const ::android::hardware::input::common::V1_0::PointerProperties& rhs);
static inline bool operator!=(const ::android::hardware::input::common::V1_0::PointerProperties& lhs, const ::android::hardware::input::common::V1_0::PointerProperties& rhs);

static inline std::string toString(const ::android::hardware::input::common::V1_0::PointerCoords& o);
static inline void PrintTo(const ::android::hardware::input::common::V1_0::PointerCoords& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::input::common::V1_0::PointerCoords& lhs, const ::android::hardware::input::common::V1_0::PointerCoords& rhs);
static inline bool operator!=(const ::android::hardware::input::common::V1_0::PointerCoords& lhs, const ::android::hardware::input::common::V1_0::PointerCoords& rhs);

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::input::common::V1_0::SourceClass o);
static inline void PrintTo(::android::hardware::input::common::V1_0::SourceClass o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::input::common::V1_0::SourceClass lhs, const ::android::hardware::input::common::V1_0::SourceClass rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::input::common::V1_0::SourceClass rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::input::common::V1_0::SourceClass lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::input::common::V1_0::SourceClass lhs, const ::android::hardware::input::common::V1_0::SourceClass rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::input::common::V1_0::SourceClass rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::input::common::V1_0::SourceClass lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::input::common::V1_0::SourceClass e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::input::common::V1_0::SourceClass e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::input::common::V1_0::Source o);
static inline void PrintTo(::android::hardware::input::common::V1_0::Source o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::input::common::V1_0::Source lhs, const ::android::hardware::input::common::V1_0::Source rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::input::common::V1_0::Source rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::input::common::V1_0::Source lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::input::common::V1_0::Source lhs, const ::android::hardware::input::common::V1_0::Source rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::input::common::V1_0::Source rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::input::common::V1_0::Source lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::input::common::V1_0::Source e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::input::common::V1_0::Source e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::input::common::V1_0::Action o);
static inline void PrintTo(::android::hardware::input::common::V1_0::Action o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::input::common::V1_0::Action lhs, const ::android::hardware::input::common::V1_0::Action rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::input::common::V1_0::Action rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::input::common::V1_0::Action lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::input::common::V1_0::Action lhs, const ::android::hardware::input::common::V1_0::Action rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::input::common::V1_0::Action rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::input::common::V1_0::Action lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::input::common::V1_0::Action e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::input::common::V1_0::Action e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::input::common::V1_0::EdgeFlag o);
static inline void PrintTo(::android::hardware::input::common::V1_0::EdgeFlag o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::input::common::V1_0::EdgeFlag lhs, const ::android::hardware::input::common::V1_0::EdgeFlag rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::input::common::V1_0::EdgeFlag rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::input::common::V1_0::EdgeFlag lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::input::common::V1_0::EdgeFlag lhs, const ::android::hardware::input::common::V1_0::EdgeFlag rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::input::common::V1_0::EdgeFlag rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::input::common::V1_0::EdgeFlag lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::input::common::V1_0::EdgeFlag e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::input::common::V1_0::EdgeFlag e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::input::common::V1_0::PolicyFlag o);
static inline void PrintTo(::android::hardware::input::common::V1_0::PolicyFlag o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::input::common::V1_0::PolicyFlag lhs, const ::android::hardware::input::common::V1_0::PolicyFlag rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::input::common::V1_0::PolicyFlag rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::input::common::V1_0::PolicyFlag lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::input::common::V1_0::PolicyFlag lhs, const ::android::hardware::input::common::V1_0::PolicyFlag rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::input::common::V1_0::PolicyFlag rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::input::common::V1_0::PolicyFlag lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::input::common::V1_0::PolicyFlag e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::input::common::V1_0::PolicyFlag e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::input::common::V1_0::Button o);
static inline void PrintTo(::android::hardware::input::common::V1_0::Button o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::input::common::V1_0::Button lhs, const ::android::hardware::input::common::V1_0::Button rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::input::common::V1_0::Button rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::input::common::V1_0::Button lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::input::common::V1_0::Button lhs, const ::android::hardware::input::common::V1_0::Button rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::input::common::V1_0::Button rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::input::common::V1_0::Button lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::input::common::V1_0::Button e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::input::common::V1_0::Button e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::input::common::V1_0::Meta o);
static inline void PrintTo(::android::hardware::input::common::V1_0::Meta o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::input::common::V1_0::Meta lhs, const ::android::hardware::input::common::V1_0::Meta rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::input::common::V1_0::Meta rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::input::common::V1_0::Meta lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::input::common::V1_0::Meta lhs, const ::android::hardware::input::common::V1_0::Meta rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::input::common::V1_0::Meta rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::input::common::V1_0::Meta lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::input::common::V1_0::Meta e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::input::common::V1_0::Meta e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::input::common::V1_0::Flag o);
static inline void PrintTo(::android::hardware::input::common::V1_0::Flag o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::input::common::V1_0::Flag lhs, const ::android::hardware::input::common::V1_0::Flag rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::input::common::V1_0::Flag rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::input::common::V1_0::Flag lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::input::common::V1_0::Flag lhs, const ::android::hardware::input::common::V1_0::Flag rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::input::common::V1_0::Flag rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::input::common::V1_0::Flag lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::input::common::V1_0::Flag e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::input::common::V1_0::Flag e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::input::common::V1_0::VideoFrame& o);
static inline void PrintTo(const ::android::hardware::input::common::V1_0::VideoFrame& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::input::common::V1_0::VideoFrame& lhs, const ::android::hardware::input::common::V1_0::VideoFrame& rhs);
static inline bool operator!=(const ::android::hardware::input::common::V1_0::VideoFrame& lhs, const ::android::hardware::input::common::V1_0::VideoFrame& rhs);

static inline std::string toString(const ::android::hardware::input::common::V1_0::MotionEvent& o);
static inline void PrintTo(const ::android::hardware::input::common::V1_0::MotionEvent& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::input::common::V1_0::MotionEvent& lhs, const ::android::hardware::input::common::V1_0::MotionEvent& rhs);
static inline bool operator!=(const ::android::hardware::input::common::V1_0::MotionEvent& lhs, const ::android::hardware::input::common::V1_0::MotionEvent& rhs);

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::input::common::V1_0::Classification o);
static inline void PrintTo(::android::hardware::input::common::V1_0::Classification o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::input::common::V1_0::Classification lhs, const ::android::hardware::input::common::V1_0::Classification rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::input::common::V1_0::Classification rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::input::common::V1_0::Classification lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::input::common::V1_0::Classification lhs, const ::android::hardware::input::common::V1_0::Classification rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::input::common::V1_0::Classification rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::input::common::V1_0::Classification lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::input::common::V1_0::Classification e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::input::common::V1_0::Classification e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::input::common::V1_0::Axis>(uint64_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::input::common::V1_0::Axis> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::input::common::V1_0::Axis::X) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::X)) {
        os += (first ? "" : " | ");
        os += "X";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::X;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::Y) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::Y)) {
        os += (first ? "" : " | ");
        os += "Y";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::Y;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::PRESSURE) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::PRESSURE)) {
        os += (first ? "" : " | ");
        os += "PRESSURE";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::PRESSURE;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::SIZE) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::SIZE)) {
        os += (first ? "" : " | ");
        os += "SIZE";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::SIZE;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::TOUCH_MAJOR) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::TOUCH_MAJOR)) {
        os += (first ? "" : " | ");
        os += "TOUCH_MAJOR";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::TOUCH_MAJOR;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::TOUCH_MINOR) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::TOUCH_MINOR)) {
        os += (first ? "" : " | ");
        os += "TOUCH_MINOR";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::TOUCH_MINOR;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::TOOL_MAJOR) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::TOOL_MAJOR)) {
        os += (first ? "" : " | ");
        os += "TOOL_MAJOR";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::TOOL_MAJOR;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::TOOL_MINOR) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::TOOL_MINOR)) {
        os += (first ? "" : " | ");
        os += "TOOL_MINOR";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::TOOL_MINOR;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::ORIENTATION) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::ORIENTATION)) {
        os += (first ? "" : " | ");
        os += "ORIENTATION";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::ORIENTATION;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::VSCROLL) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::VSCROLL)) {
        os += (first ? "" : " | ");
        os += "VSCROLL";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::VSCROLL;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::HSCROLL) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::HSCROLL)) {
        os += (first ? "" : " | ");
        os += "HSCROLL";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::HSCROLL;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::Z) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::Z)) {
        os += (first ? "" : " | ");
        os += "Z";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::Z;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::RX) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::RX)) {
        os += (first ? "" : " | ");
        os += "RX";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::RX;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::RY) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::RY)) {
        os += (first ? "" : " | ");
        os += "RY";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::RY;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::RZ) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::RZ)) {
        os += (first ? "" : " | ");
        os += "RZ";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::RZ;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::HAT_X) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::HAT_X)) {
        os += (first ? "" : " | ");
        os += "HAT_X";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::HAT_X;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::HAT_Y) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::HAT_Y)) {
        os += (first ? "" : " | ");
        os += "HAT_Y";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::HAT_Y;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::LTRIGGER) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::LTRIGGER)) {
        os += (first ? "" : " | ");
        os += "LTRIGGER";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::LTRIGGER;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::RTRIGGER) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::RTRIGGER)) {
        os += (first ? "" : " | ");
        os += "RTRIGGER";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::RTRIGGER;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::THROTTLE) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::THROTTLE)) {
        os += (first ? "" : " | ");
        os += "THROTTLE";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::THROTTLE;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::RUDDER) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::RUDDER)) {
        os += (first ? "" : " | ");
        os += "RUDDER";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::RUDDER;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::WHEEL) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::WHEEL)) {
        os += (first ? "" : " | ");
        os += "WHEEL";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::WHEEL;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::GAS) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::GAS)) {
        os += (first ? "" : " | ");
        os += "GAS";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::GAS;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::BRAKE) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::BRAKE)) {
        os += (first ? "" : " | ");
        os += "BRAKE";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::BRAKE;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::DISTANCE) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::DISTANCE)) {
        os += (first ? "" : " | ");
        os += "DISTANCE";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::DISTANCE;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::TILT) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::TILT)) {
        os += (first ? "" : " | ");
        os += "TILT";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::TILT;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::SCROLL) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::SCROLL)) {
        os += (first ? "" : " | ");
        os += "SCROLL";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::SCROLL;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::RELATIVE_X) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::RELATIVE_X)) {
        os += (first ? "" : " | ");
        os += "RELATIVE_X";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::RELATIVE_X;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::RELATIVE_Y) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::RELATIVE_Y)) {
        os += (first ? "" : " | ");
        os += "RELATIVE_Y";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::RELATIVE_Y;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::GENERIC_1) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::GENERIC_1)) {
        os += (first ? "" : " | ");
        os += "GENERIC_1";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::GENERIC_1;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::GENERIC_2) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::GENERIC_2)) {
        os += (first ? "" : " | ");
        os += "GENERIC_2";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::GENERIC_2;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::GENERIC_3) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::GENERIC_3)) {
        os += (first ? "" : " | ");
        os += "GENERIC_3";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::GENERIC_3;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::GENERIC_4) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::GENERIC_4)) {
        os += (first ? "" : " | ");
        os += "GENERIC_4";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::GENERIC_4;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::GENERIC_5) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::GENERIC_5)) {
        os += (first ? "" : " | ");
        os += "GENERIC_5";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::GENERIC_5;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::GENERIC_6) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::GENERIC_6)) {
        os += (first ? "" : " | ");
        os += "GENERIC_6";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::GENERIC_6;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::GENERIC_7) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::GENERIC_7)) {
        os += (first ? "" : " | ");
        os += "GENERIC_7";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::GENERIC_7;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::GENERIC_8) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::GENERIC_8)) {
        os += (first ? "" : " | ");
        os += "GENERIC_8";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::GENERIC_8;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::GENERIC_9) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::GENERIC_9)) {
        os += (first ? "" : " | ");
        os += "GENERIC_9";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::GENERIC_9;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::GENERIC_10) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::GENERIC_10)) {
        os += (first ? "" : " | ");
        os += "GENERIC_10";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::GENERIC_10;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::GENERIC_11) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::GENERIC_11)) {
        os += (first ? "" : " | ");
        os += "GENERIC_11";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::GENERIC_11;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::GENERIC_12) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::GENERIC_12)) {
        os += (first ? "" : " | ");
        os += "GENERIC_12";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::GENERIC_12;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::GENERIC_13) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::GENERIC_13)) {
        os += (first ? "" : " | ");
        os += "GENERIC_13";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::GENERIC_13;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::GENERIC_14) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::GENERIC_14)) {
        os += (first ? "" : " | ");
        os += "GENERIC_14";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::GENERIC_14;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::GENERIC_15) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::GENERIC_15)) {
        os += (first ? "" : " | ");
        os += "GENERIC_15";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::GENERIC_15;
    }
    if ((o & ::android::hardware::input::common::V1_0::Axis::GENERIC_16) == static_cast<uint64_t>(::android::hardware::input::common::V1_0::Axis::GENERIC_16)) {
        os += (first ? "" : " | ");
        os += "GENERIC_16";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Axis::GENERIC_16;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::input::common::V1_0::Axis o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::input::common::V1_0::Axis::X) {
        return "X";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::Y) {
        return "Y";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::PRESSURE) {
        return "PRESSURE";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::SIZE) {
        return "SIZE";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::TOUCH_MAJOR) {
        return "TOUCH_MAJOR";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::TOUCH_MINOR) {
        return "TOUCH_MINOR";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::TOOL_MAJOR) {
        return "TOOL_MAJOR";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::TOOL_MINOR) {
        return "TOOL_MINOR";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::ORIENTATION) {
        return "ORIENTATION";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::VSCROLL) {
        return "VSCROLL";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::HSCROLL) {
        return "HSCROLL";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::Z) {
        return "Z";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::RX) {
        return "RX";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::RY) {
        return "RY";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::RZ) {
        return "RZ";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::HAT_X) {
        return "HAT_X";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::HAT_Y) {
        return "HAT_Y";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::LTRIGGER) {
        return "LTRIGGER";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::RTRIGGER) {
        return "RTRIGGER";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::THROTTLE) {
        return "THROTTLE";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::RUDDER) {
        return "RUDDER";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::WHEEL) {
        return "WHEEL";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::GAS) {
        return "GAS";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::BRAKE) {
        return "BRAKE";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::DISTANCE) {
        return "DISTANCE";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::TILT) {
        return "TILT";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::SCROLL) {
        return "SCROLL";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::RELATIVE_X) {
        return "RELATIVE_X";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::RELATIVE_Y) {
        return "RELATIVE_Y";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::GENERIC_1) {
        return "GENERIC_1";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::GENERIC_2) {
        return "GENERIC_2";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::GENERIC_3) {
        return "GENERIC_3";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::GENERIC_4) {
        return "GENERIC_4";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::GENERIC_5) {
        return "GENERIC_5";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::GENERIC_6) {
        return "GENERIC_6";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::GENERIC_7) {
        return "GENERIC_7";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::GENERIC_8) {
        return "GENERIC_8";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::GENERIC_9) {
        return "GENERIC_9";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::GENERIC_10) {
        return "GENERIC_10";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::GENERIC_11) {
        return "GENERIC_11";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::GENERIC_12) {
        return "GENERIC_12";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::GENERIC_13) {
        return "GENERIC_13";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::GENERIC_14) {
        return "GENERIC_14";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::GENERIC_15) {
        return "GENERIC_15";
    }
    if (o == ::android::hardware::input::common::V1_0::Axis::GENERIC_16) {
        return "GENERIC_16";
    }
    std::string os;
    os += toHexString(static_cast<uint64_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::input::common::V1_0::Axis o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::input::common::V1_0::ToolType>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::input::common::V1_0::ToolType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::input::common::V1_0::ToolType::UNKNOWN) == static_cast<uint8_t>(::android::hardware::input::common::V1_0::ToolType::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::ToolType::UNKNOWN;
    }
    if ((o & ::android::hardware::input::common::V1_0::ToolType::FINGER) == static_cast<uint8_t>(::android::hardware::input::common::V1_0::ToolType::FINGER)) {
        os += (first ? "" : " | ");
        os += "FINGER";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::ToolType::FINGER;
    }
    if ((o & ::android::hardware::input::common::V1_0::ToolType::STYLUS) == static_cast<uint8_t>(::android::hardware::input::common::V1_0::ToolType::STYLUS)) {
        os += (first ? "" : " | ");
        os += "STYLUS";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::ToolType::STYLUS;
    }
    if ((o & ::android::hardware::input::common::V1_0::ToolType::MOUSE) == static_cast<uint8_t>(::android::hardware::input::common::V1_0::ToolType::MOUSE)) {
        os += (first ? "" : " | ");
        os += "MOUSE";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::ToolType::MOUSE;
    }
    if ((o & ::android::hardware::input::common::V1_0::ToolType::ERASER) == static_cast<uint8_t>(::android::hardware::input::common::V1_0::ToolType::ERASER)) {
        os += (first ? "" : " | ");
        os += "ERASER";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::ToolType::ERASER;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::input::common::V1_0::ToolType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::input::common::V1_0::ToolType::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::input::common::V1_0::ToolType::FINGER) {
        return "FINGER";
    }
    if (o == ::android::hardware::input::common::V1_0::ToolType::STYLUS) {
        return "STYLUS";
    }
    if (o == ::android::hardware::input::common::V1_0::ToolType::MOUSE) {
        return "MOUSE";
    }
    if (o == ::android::hardware::input::common::V1_0::ToolType::ERASER) {
        return "ERASER";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::input::common::V1_0::ToolType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::input::common::V1_0::PointerProperties& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".id = ";
    os += ::android::hardware::toString(o.id);
    os += ", .toolType = ";
    os += ::android::hardware::input::common::V1_0::toString(o.toolType);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::input::common::V1_0::PointerProperties& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::input::common::V1_0::PointerProperties& lhs, const ::android::hardware::input::common::V1_0::PointerProperties& rhs) {
    if (lhs.id != rhs.id) {
        return false;
    }
    if (lhs.toolType != rhs.toolType) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::input::common::V1_0::PointerProperties& lhs, const ::android::hardware::input::common::V1_0::PointerProperties& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::input::common::V1_0::PointerCoords& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".bits = ";
    os += ::android::hardware::input::common::V1_0::toString<::android::hardware::input::common::V1_0::Axis>(o.bits);
    os += ", .values = ";
    os += ::android::hardware::toString(o.values);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::input::common::V1_0::PointerCoords& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::input::common::V1_0::PointerCoords& lhs, const ::android::hardware::input::common::V1_0::PointerCoords& rhs) {
    if (lhs.bits != rhs.bits) {
        return false;
    }
    if (lhs.values != rhs.values) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::input::common::V1_0::PointerCoords& lhs, const ::android::hardware::input::common::V1_0::PointerCoords& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::input::common::V1_0::SourceClass>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::input::common::V1_0::SourceClass> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::input::common::V1_0::SourceClass::NONE) == static_cast<uint8_t>(::android::hardware::input::common::V1_0::SourceClass::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::SourceClass::NONE;
    }
    if ((o & ::android::hardware::input::common::V1_0::SourceClass::BUTTON) == static_cast<uint8_t>(::android::hardware::input::common::V1_0::SourceClass::BUTTON)) {
        os += (first ? "" : " | ");
        os += "BUTTON";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::SourceClass::BUTTON;
    }
    if ((o & ::android::hardware::input::common::V1_0::SourceClass::POINTER) == static_cast<uint8_t>(::android::hardware::input::common::V1_0::SourceClass::POINTER)) {
        os += (first ? "" : " | ");
        os += "POINTER";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::SourceClass::POINTER;
    }
    if ((o & ::android::hardware::input::common::V1_0::SourceClass::NAVIGATION) == static_cast<uint8_t>(::android::hardware::input::common::V1_0::SourceClass::NAVIGATION)) {
        os += (first ? "" : " | ");
        os += "NAVIGATION";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::SourceClass::NAVIGATION;
    }
    if ((o & ::android::hardware::input::common::V1_0::SourceClass::POSITION) == static_cast<uint8_t>(::android::hardware::input::common::V1_0::SourceClass::POSITION)) {
        os += (first ? "" : " | ");
        os += "POSITION";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::SourceClass::POSITION;
    }
    if ((o & ::android::hardware::input::common::V1_0::SourceClass::JOYSTICK) == static_cast<uint8_t>(::android::hardware::input::common::V1_0::SourceClass::JOYSTICK)) {
        os += (first ? "" : " | ");
        os += "JOYSTICK";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::SourceClass::JOYSTICK;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::input::common::V1_0::SourceClass o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::input::common::V1_0::SourceClass::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::input::common::V1_0::SourceClass::BUTTON) {
        return "BUTTON";
    }
    if (o == ::android::hardware::input::common::V1_0::SourceClass::POINTER) {
        return "POINTER";
    }
    if (o == ::android::hardware::input::common::V1_0::SourceClass::NAVIGATION) {
        return "NAVIGATION";
    }
    if (o == ::android::hardware::input::common::V1_0::SourceClass::POSITION) {
        return "POSITION";
    }
    if (o == ::android::hardware::input::common::V1_0::SourceClass::JOYSTICK) {
        return "JOYSTICK";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::input::common::V1_0::SourceClass o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::input::common::V1_0::Source>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::input::common::V1_0::Source> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::input::common::V1_0::Source::UNKNOWN) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::Source::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Source::UNKNOWN;
    }
    if ((o & ::android::hardware::input::common::V1_0::Source::KEYBOARD) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::Source::KEYBOARD)) {
        os += (first ? "" : " | ");
        os += "KEYBOARD";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Source::KEYBOARD;
    }
    if ((o & ::android::hardware::input::common::V1_0::Source::DPAD) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::Source::DPAD)) {
        os += (first ? "" : " | ");
        os += "DPAD";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Source::DPAD;
    }
    if ((o & ::android::hardware::input::common::V1_0::Source::GAMEPAD) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::Source::GAMEPAD)) {
        os += (first ? "" : " | ");
        os += "GAMEPAD";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Source::GAMEPAD;
    }
    if ((o & ::android::hardware::input::common::V1_0::Source::TOUCHSCREEN) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::Source::TOUCHSCREEN)) {
        os += (first ? "" : " | ");
        os += "TOUCHSCREEN";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Source::TOUCHSCREEN;
    }
    if ((o & ::android::hardware::input::common::V1_0::Source::MOUSE) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::Source::MOUSE)) {
        os += (first ? "" : " | ");
        os += "MOUSE";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Source::MOUSE;
    }
    if ((o & ::android::hardware::input::common::V1_0::Source::STYLUS) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::Source::STYLUS)) {
        os += (first ? "" : " | ");
        os += "STYLUS";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Source::STYLUS;
    }
    if ((o & ::android::hardware::input::common::V1_0::Source::BLUETOOTH_STYLUS) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::Source::BLUETOOTH_STYLUS)) {
        os += (first ? "" : " | ");
        os += "BLUETOOTH_STYLUS";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Source::BLUETOOTH_STYLUS;
    }
    if ((o & ::android::hardware::input::common::V1_0::Source::TRACKBALL) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::Source::TRACKBALL)) {
        os += (first ? "" : " | ");
        os += "TRACKBALL";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Source::TRACKBALL;
    }
    if ((o & ::android::hardware::input::common::V1_0::Source::MOUSE_RELATIVE) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::Source::MOUSE_RELATIVE)) {
        os += (first ? "" : " | ");
        os += "MOUSE_RELATIVE";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Source::MOUSE_RELATIVE;
    }
    if ((o & ::android::hardware::input::common::V1_0::Source::TOUCHPAD) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::Source::TOUCHPAD)) {
        os += (first ? "" : " | ");
        os += "TOUCHPAD";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Source::TOUCHPAD;
    }
    if ((o & ::android::hardware::input::common::V1_0::Source::TOUCH_NAVIGATION) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::Source::TOUCH_NAVIGATION)) {
        os += (first ? "" : " | ");
        os += "TOUCH_NAVIGATION";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Source::TOUCH_NAVIGATION;
    }
    if ((o & ::android::hardware::input::common::V1_0::Source::ROTARY_ENCODER) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::Source::ROTARY_ENCODER)) {
        os += (first ? "" : " | ");
        os += "ROTARY_ENCODER";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Source::ROTARY_ENCODER;
    }
    if ((o & ::android::hardware::input::common::V1_0::Source::JOYSTICK) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::Source::JOYSTICK)) {
        os += (first ? "" : " | ");
        os += "JOYSTICK";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Source::JOYSTICK;
    }
    if ((o & ::android::hardware::input::common::V1_0::Source::ANY) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::Source::ANY)) {
        os += (first ? "" : " | ");
        os += "ANY";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Source::ANY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::input::common::V1_0::Source o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::input::common::V1_0::Source::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::input::common::V1_0::Source::KEYBOARD) {
        return "KEYBOARD";
    }
    if (o == ::android::hardware::input::common::V1_0::Source::DPAD) {
        return "DPAD";
    }
    if (o == ::android::hardware::input::common::V1_0::Source::GAMEPAD) {
        return "GAMEPAD";
    }
    if (o == ::android::hardware::input::common::V1_0::Source::TOUCHSCREEN) {
        return "TOUCHSCREEN";
    }
    if (o == ::android::hardware::input::common::V1_0::Source::MOUSE) {
        return "MOUSE";
    }
    if (o == ::android::hardware::input::common::V1_0::Source::STYLUS) {
        return "STYLUS";
    }
    if (o == ::android::hardware::input::common::V1_0::Source::BLUETOOTH_STYLUS) {
        return "BLUETOOTH_STYLUS";
    }
    if (o == ::android::hardware::input::common::V1_0::Source::TRACKBALL) {
        return "TRACKBALL";
    }
    if (o == ::android::hardware::input::common::V1_0::Source::MOUSE_RELATIVE) {
        return "MOUSE_RELATIVE";
    }
    if (o == ::android::hardware::input::common::V1_0::Source::TOUCHPAD) {
        return "TOUCHPAD";
    }
    if (o == ::android::hardware::input::common::V1_0::Source::TOUCH_NAVIGATION) {
        return "TOUCH_NAVIGATION";
    }
    if (o == ::android::hardware::input::common::V1_0::Source::ROTARY_ENCODER) {
        return "ROTARY_ENCODER";
    }
    if (o == ::android::hardware::input::common::V1_0::Source::JOYSTICK) {
        return "JOYSTICK";
    }
    if (o == ::android::hardware::input::common::V1_0::Source::ANY) {
        return "ANY";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::input::common::V1_0::Source o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::input::common::V1_0::Action>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::input::common::V1_0::Action> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::input::common::V1_0::Action::DOWN) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Action::DOWN)) {
        os += (first ? "" : " | ");
        os += "DOWN";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Action::DOWN;
    }
    if ((o & ::android::hardware::input::common::V1_0::Action::UP) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Action::UP)) {
        os += (first ? "" : " | ");
        os += "UP";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Action::UP;
    }
    if ((o & ::android::hardware::input::common::V1_0::Action::MOVE) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Action::MOVE)) {
        os += (first ? "" : " | ");
        os += "MOVE";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Action::MOVE;
    }
    if ((o & ::android::hardware::input::common::V1_0::Action::CANCEL) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Action::CANCEL)) {
        os += (first ? "" : " | ");
        os += "CANCEL";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Action::CANCEL;
    }
    if ((o & ::android::hardware::input::common::V1_0::Action::OUTSIDE) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Action::OUTSIDE)) {
        os += (first ? "" : " | ");
        os += "OUTSIDE";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Action::OUTSIDE;
    }
    if ((o & ::android::hardware::input::common::V1_0::Action::POINTER_DOWN) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Action::POINTER_DOWN)) {
        os += (first ? "" : " | ");
        os += "POINTER_DOWN";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Action::POINTER_DOWN;
    }
    if ((o & ::android::hardware::input::common::V1_0::Action::POINTER_UP) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Action::POINTER_UP)) {
        os += (first ? "" : " | ");
        os += "POINTER_UP";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Action::POINTER_UP;
    }
    if ((o & ::android::hardware::input::common::V1_0::Action::HOVER_MOVE) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Action::HOVER_MOVE)) {
        os += (first ? "" : " | ");
        os += "HOVER_MOVE";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Action::HOVER_MOVE;
    }
    if ((o & ::android::hardware::input::common::V1_0::Action::SCROLL) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Action::SCROLL)) {
        os += (first ? "" : " | ");
        os += "SCROLL";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Action::SCROLL;
    }
    if ((o & ::android::hardware::input::common::V1_0::Action::HOVER_ENTER) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Action::HOVER_ENTER)) {
        os += (first ? "" : " | ");
        os += "HOVER_ENTER";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Action::HOVER_ENTER;
    }
    if ((o & ::android::hardware::input::common::V1_0::Action::HOVER_EXIT) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Action::HOVER_EXIT)) {
        os += (first ? "" : " | ");
        os += "HOVER_EXIT";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Action::HOVER_EXIT;
    }
    if ((o & ::android::hardware::input::common::V1_0::Action::BUTTON_PRESS) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Action::BUTTON_PRESS)) {
        os += (first ? "" : " | ");
        os += "BUTTON_PRESS";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Action::BUTTON_PRESS;
    }
    if ((o & ::android::hardware::input::common::V1_0::Action::BUTTON_RELEASE) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Action::BUTTON_RELEASE)) {
        os += (first ? "" : " | ");
        os += "BUTTON_RELEASE";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Action::BUTTON_RELEASE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::input::common::V1_0::Action o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::input::common::V1_0::Action::DOWN) {
        return "DOWN";
    }
    if (o == ::android::hardware::input::common::V1_0::Action::UP) {
        return "UP";
    }
    if (o == ::android::hardware::input::common::V1_0::Action::MOVE) {
        return "MOVE";
    }
    if (o == ::android::hardware::input::common::V1_0::Action::CANCEL) {
        return "CANCEL";
    }
    if (o == ::android::hardware::input::common::V1_0::Action::OUTSIDE) {
        return "OUTSIDE";
    }
    if (o == ::android::hardware::input::common::V1_0::Action::POINTER_DOWN) {
        return "POINTER_DOWN";
    }
    if (o == ::android::hardware::input::common::V1_0::Action::POINTER_UP) {
        return "POINTER_UP";
    }
    if (o == ::android::hardware::input::common::V1_0::Action::HOVER_MOVE) {
        return "HOVER_MOVE";
    }
    if (o == ::android::hardware::input::common::V1_0::Action::SCROLL) {
        return "SCROLL";
    }
    if (o == ::android::hardware::input::common::V1_0::Action::HOVER_ENTER) {
        return "HOVER_ENTER";
    }
    if (o == ::android::hardware::input::common::V1_0::Action::HOVER_EXIT) {
        return "HOVER_EXIT";
    }
    if (o == ::android::hardware::input::common::V1_0::Action::BUTTON_PRESS) {
        return "BUTTON_PRESS";
    }
    if (o == ::android::hardware::input::common::V1_0::Action::BUTTON_RELEASE) {
        return "BUTTON_RELEASE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::input::common::V1_0::Action o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::input::common::V1_0::EdgeFlag>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::input::common::V1_0::EdgeFlag> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::input::common::V1_0::EdgeFlag::NONE) == static_cast<int32_t>(::android::hardware::input::common::V1_0::EdgeFlag::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::EdgeFlag::NONE;
    }
    if ((o & ::android::hardware::input::common::V1_0::EdgeFlag::TOP) == static_cast<int32_t>(::android::hardware::input::common::V1_0::EdgeFlag::TOP)) {
        os += (first ? "" : " | ");
        os += "TOP";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::EdgeFlag::TOP;
    }
    if ((o & ::android::hardware::input::common::V1_0::EdgeFlag::BOTTOM) == static_cast<int32_t>(::android::hardware::input::common::V1_0::EdgeFlag::BOTTOM)) {
        os += (first ? "" : " | ");
        os += "BOTTOM";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::EdgeFlag::BOTTOM;
    }
    if ((o & ::android::hardware::input::common::V1_0::EdgeFlag::LEFT) == static_cast<int32_t>(::android::hardware::input::common::V1_0::EdgeFlag::LEFT)) {
        os += (first ? "" : " | ");
        os += "LEFT";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::EdgeFlag::LEFT;
    }
    if ((o & ::android::hardware::input::common::V1_0::EdgeFlag::RIGHT) == static_cast<int32_t>(::android::hardware::input::common::V1_0::EdgeFlag::RIGHT)) {
        os += (first ? "" : " | ");
        os += "RIGHT";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::EdgeFlag::RIGHT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::input::common::V1_0::EdgeFlag o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::input::common::V1_0::EdgeFlag::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::input::common::V1_0::EdgeFlag::TOP) {
        return "TOP";
    }
    if (o == ::android::hardware::input::common::V1_0::EdgeFlag::BOTTOM) {
        return "BOTTOM";
    }
    if (o == ::android::hardware::input::common::V1_0::EdgeFlag::LEFT) {
        return "LEFT";
    }
    if (o == ::android::hardware::input::common::V1_0::EdgeFlag::RIGHT) {
        return "RIGHT";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::input::common::V1_0::EdgeFlag o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::input::common::V1_0::PolicyFlag>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::input::common::V1_0::PolicyFlag> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::input::common::V1_0::PolicyFlag::WAKE) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::PolicyFlag::WAKE)) {
        os += (first ? "" : " | ");
        os += "WAKE";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::PolicyFlag::WAKE;
    }
    if ((o & ::android::hardware::input::common::V1_0::PolicyFlag::VIRTUAL) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::PolicyFlag::VIRTUAL)) {
        os += (first ? "" : " | ");
        os += "VIRTUAL";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::PolicyFlag::VIRTUAL;
    }
    if ((o & ::android::hardware::input::common::V1_0::PolicyFlag::FUNCTION) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::PolicyFlag::FUNCTION)) {
        os += (first ? "" : " | ");
        os += "FUNCTION";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::PolicyFlag::FUNCTION;
    }
    if ((o & ::android::hardware::input::common::V1_0::PolicyFlag::GESTURE) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::PolicyFlag::GESTURE)) {
        os += (first ? "" : " | ");
        os += "GESTURE";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::PolicyFlag::GESTURE;
    }
    if ((o & ::android::hardware::input::common::V1_0::PolicyFlag::INJECTED) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::PolicyFlag::INJECTED)) {
        os += (first ? "" : " | ");
        os += "INJECTED";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::PolicyFlag::INJECTED;
    }
    if ((o & ::android::hardware::input::common::V1_0::PolicyFlag::TRUSTED) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::PolicyFlag::TRUSTED)) {
        os += (first ? "" : " | ");
        os += "TRUSTED";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::PolicyFlag::TRUSTED;
    }
    if ((o & ::android::hardware::input::common::V1_0::PolicyFlag::FILTERED) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::PolicyFlag::FILTERED)) {
        os += (first ? "" : " | ");
        os += "FILTERED";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::PolicyFlag::FILTERED;
    }
    if ((o & ::android::hardware::input::common::V1_0::PolicyFlag::DISABLE_KEY_REPEAT) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::PolicyFlag::DISABLE_KEY_REPEAT)) {
        os += (first ? "" : " | ");
        os += "DISABLE_KEY_REPEAT";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::PolicyFlag::DISABLE_KEY_REPEAT;
    }
    if ((o & ::android::hardware::input::common::V1_0::PolicyFlag::INTERACTIVE) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::PolicyFlag::INTERACTIVE)) {
        os += (first ? "" : " | ");
        os += "INTERACTIVE";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::PolicyFlag::INTERACTIVE;
    }
    if ((o & ::android::hardware::input::common::V1_0::PolicyFlag::PASS_TO_USER) == static_cast<uint32_t>(::android::hardware::input::common::V1_0::PolicyFlag::PASS_TO_USER)) {
        os += (first ? "" : " | ");
        os += "PASS_TO_USER";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::PolicyFlag::PASS_TO_USER;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::input::common::V1_0::PolicyFlag o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::input::common::V1_0::PolicyFlag::WAKE) {
        return "WAKE";
    }
    if (o == ::android::hardware::input::common::V1_0::PolicyFlag::VIRTUAL) {
        return "VIRTUAL";
    }
    if (o == ::android::hardware::input::common::V1_0::PolicyFlag::FUNCTION) {
        return "FUNCTION";
    }
    if (o == ::android::hardware::input::common::V1_0::PolicyFlag::GESTURE) {
        return "GESTURE";
    }
    if (o == ::android::hardware::input::common::V1_0::PolicyFlag::INJECTED) {
        return "INJECTED";
    }
    if (o == ::android::hardware::input::common::V1_0::PolicyFlag::TRUSTED) {
        return "TRUSTED";
    }
    if (o == ::android::hardware::input::common::V1_0::PolicyFlag::FILTERED) {
        return "FILTERED";
    }
    if (o == ::android::hardware::input::common::V1_0::PolicyFlag::DISABLE_KEY_REPEAT) {
        return "DISABLE_KEY_REPEAT";
    }
    if (o == ::android::hardware::input::common::V1_0::PolicyFlag::INTERACTIVE) {
        return "INTERACTIVE";
    }
    if (o == ::android::hardware::input::common::V1_0::PolicyFlag::PASS_TO_USER) {
        return "PASS_TO_USER";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::input::common::V1_0::PolicyFlag o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::input::common::V1_0::Button>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::input::common::V1_0::Button> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::input::common::V1_0::Button::NONE) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Button::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Button::NONE;
    }
    if ((o & ::android::hardware::input::common::V1_0::Button::PRIMARY) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Button::PRIMARY)) {
        os += (first ? "" : " | ");
        os += "PRIMARY";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Button::PRIMARY;
    }
    if ((o & ::android::hardware::input::common::V1_0::Button::SECONDARY) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Button::SECONDARY)) {
        os += (first ? "" : " | ");
        os += "SECONDARY";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Button::SECONDARY;
    }
    if ((o & ::android::hardware::input::common::V1_0::Button::TERTIARY) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Button::TERTIARY)) {
        os += (first ? "" : " | ");
        os += "TERTIARY";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Button::TERTIARY;
    }
    if ((o & ::android::hardware::input::common::V1_0::Button::BACK) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Button::BACK)) {
        os += (first ? "" : " | ");
        os += "BACK";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Button::BACK;
    }
    if ((o & ::android::hardware::input::common::V1_0::Button::FORWARD) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Button::FORWARD)) {
        os += (first ? "" : " | ");
        os += "FORWARD";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Button::FORWARD;
    }
    if ((o & ::android::hardware::input::common::V1_0::Button::STYLUS_PRIMARY) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Button::STYLUS_PRIMARY)) {
        os += (first ? "" : " | ");
        os += "STYLUS_PRIMARY";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Button::STYLUS_PRIMARY;
    }
    if ((o & ::android::hardware::input::common::V1_0::Button::STYLUS_SECONDARY) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Button::STYLUS_SECONDARY)) {
        os += (first ? "" : " | ");
        os += "STYLUS_SECONDARY";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Button::STYLUS_SECONDARY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::input::common::V1_0::Button o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::input::common::V1_0::Button::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::input::common::V1_0::Button::PRIMARY) {
        return "PRIMARY";
    }
    if (o == ::android::hardware::input::common::V1_0::Button::SECONDARY) {
        return "SECONDARY";
    }
    if (o == ::android::hardware::input::common::V1_0::Button::TERTIARY) {
        return "TERTIARY";
    }
    if (o == ::android::hardware::input::common::V1_0::Button::BACK) {
        return "BACK";
    }
    if (o == ::android::hardware::input::common::V1_0::Button::FORWARD) {
        return "FORWARD";
    }
    if (o == ::android::hardware::input::common::V1_0::Button::STYLUS_PRIMARY) {
        return "STYLUS_PRIMARY";
    }
    if (o == ::android::hardware::input::common::V1_0::Button::STYLUS_SECONDARY) {
        return "STYLUS_SECONDARY";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::input::common::V1_0::Button o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::input::common::V1_0::Meta>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::input::common::V1_0::Meta> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::input::common::V1_0::Meta::NONE) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Meta::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Meta::NONE;
    }
    if ((o & ::android::hardware::input::common::V1_0::Meta::ALT_ON) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Meta::ALT_ON)) {
        os += (first ? "" : " | ");
        os += "ALT_ON";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Meta::ALT_ON;
    }
    if ((o & ::android::hardware::input::common::V1_0::Meta::ALT_LEFT_ON) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Meta::ALT_LEFT_ON)) {
        os += (first ? "" : " | ");
        os += "ALT_LEFT_ON";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Meta::ALT_LEFT_ON;
    }
    if ((o & ::android::hardware::input::common::V1_0::Meta::ALT_RIGHT_ON) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Meta::ALT_RIGHT_ON)) {
        os += (first ? "" : " | ");
        os += "ALT_RIGHT_ON";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Meta::ALT_RIGHT_ON;
    }
    if ((o & ::android::hardware::input::common::V1_0::Meta::SHIFT_ON) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Meta::SHIFT_ON)) {
        os += (first ? "" : " | ");
        os += "SHIFT_ON";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Meta::SHIFT_ON;
    }
    if ((o & ::android::hardware::input::common::V1_0::Meta::SHIFT_LEFT_ON) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Meta::SHIFT_LEFT_ON)) {
        os += (first ? "" : " | ");
        os += "SHIFT_LEFT_ON";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Meta::SHIFT_LEFT_ON;
    }
    if ((o & ::android::hardware::input::common::V1_0::Meta::SHIFT_RIGHT_ON) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Meta::SHIFT_RIGHT_ON)) {
        os += (first ? "" : " | ");
        os += "SHIFT_RIGHT_ON";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Meta::SHIFT_RIGHT_ON;
    }
    if ((o & ::android::hardware::input::common::V1_0::Meta::SYM_ON) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Meta::SYM_ON)) {
        os += (first ? "" : " | ");
        os += "SYM_ON";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Meta::SYM_ON;
    }
    if ((o & ::android::hardware::input::common::V1_0::Meta::FUNCTION_ON) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Meta::FUNCTION_ON)) {
        os += (first ? "" : " | ");
        os += "FUNCTION_ON";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Meta::FUNCTION_ON;
    }
    if ((o & ::android::hardware::input::common::V1_0::Meta::CTRL_ON) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Meta::CTRL_ON)) {
        os += (first ? "" : " | ");
        os += "CTRL_ON";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Meta::CTRL_ON;
    }
    if ((o & ::android::hardware::input::common::V1_0::Meta::CTRL_LEFT_ON) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Meta::CTRL_LEFT_ON)) {
        os += (first ? "" : " | ");
        os += "CTRL_LEFT_ON";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Meta::CTRL_LEFT_ON;
    }
    if ((o & ::android::hardware::input::common::V1_0::Meta::CTRL_RIGHT_ON) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Meta::CTRL_RIGHT_ON)) {
        os += (first ? "" : " | ");
        os += "CTRL_RIGHT_ON";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Meta::CTRL_RIGHT_ON;
    }
    if ((o & ::android::hardware::input::common::V1_0::Meta::META_ON) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Meta::META_ON)) {
        os += (first ? "" : " | ");
        os += "META_ON";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Meta::META_ON;
    }
    if ((o & ::android::hardware::input::common::V1_0::Meta::META_LEFT_ON) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Meta::META_LEFT_ON)) {
        os += (first ? "" : " | ");
        os += "META_LEFT_ON";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Meta::META_LEFT_ON;
    }
    if ((o & ::android::hardware::input::common::V1_0::Meta::META_RIGHT_ON) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Meta::META_RIGHT_ON)) {
        os += (first ? "" : " | ");
        os += "META_RIGHT_ON";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Meta::META_RIGHT_ON;
    }
    if ((o & ::android::hardware::input::common::V1_0::Meta::CAPS_LOCK_ON) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Meta::CAPS_LOCK_ON)) {
        os += (first ? "" : " | ");
        os += "CAPS_LOCK_ON";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Meta::CAPS_LOCK_ON;
    }
    if ((o & ::android::hardware::input::common::V1_0::Meta::NUM_LOCK_ON) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Meta::NUM_LOCK_ON)) {
        os += (first ? "" : " | ");
        os += "NUM_LOCK_ON";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Meta::NUM_LOCK_ON;
    }
    if ((o & ::android::hardware::input::common::V1_0::Meta::SCROLL_LOCK_ON) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Meta::SCROLL_LOCK_ON)) {
        os += (first ? "" : " | ");
        os += "SCROLL_LOCK_ON";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Meta::SCROLL_LOCK_ON;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::input::common::V1_0::Meta o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::input::common::V1_0::Meta::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::input::common::V1_0::Meta::ALT_ON) {
        return "ALT_ON";
    }
    if (o == ::android::hardware::input::common::V1_0::Meta::ALT_LEFT_ON) {
        return "ALT_LEFT_ON";
    }
    if (o == ::android::hardware::input::common::V1_0::Meta::ALT_RIGHT_ON) {
        return "ALT_RIGHT_ON";
    }
    if (o == ::android::hardware::input::common::V1_0::Meta::SHIFT_ON) {
        return "SHIFT_ON";
    }
    if (o == ::android::hardware::input::common::V1_0::Meta::SHIFT_LEFT_ON) {
        return "SHIFT_LEFT_ON";
    }
    if (o == ::android::hardware::input::common::V1_0::Meta::SHIFT_RIGHT_ON) {
        return "SHIFT_RIGHT_ON";
    }
    if (o == ::android::hardware::input::common::V1_0::Meta::SYM_ON) {
        return "SYM_ON";
    }
    if (o == ::android::hardware::input::common::V1_0::Meta::FUNCTION_ON) {
        return "FUNCTION_ON";
    }
    if (o == ::android::hardware::input::common::V1_0::Meta::CTRL_ON) {
        return "CTRL_ON";
    }
    if (o == ::android::hardware::input::common::V1_0::Meta::CTRL_LEFT_ON) {
        return "CTRL_LEFT_ON";
    }
    if (o == ::android::hardware::input::common::V1_0::Meta::CTRL_RIGHT_ON) {
        return "CTRL_RIGHT_ON";
    }
    if (o == ::android::hardware::input::common::V1_0::Meta::META_ON) {
        return "META_ON";
    }
    if (o == ::android::hardware::input::common::V1_0::Meta::META_LEFT_ON) {
        return "META_LEFT_ON";
    }
    if (o == ::android::hardware::input::common::V1_0::Meta::META_RIGHT_ON) {
        return "META_RIGHT_ON";
    }
    if (o == ::android::hardware::input::common::V1_0::Meta::CAPS_LOCK_ON) {
        return "CAPS_LOCK_ON";
    }
    if (o == ::android::hardware::input::common::V1_0::Meta::NUM_LOCK_ON) {
        return "NUM_LOCK_ON";
    }
    if (o == ::android::hardware::input::common::V1_0::Meta::SCROLL_LOCK_ON) {
        return "SCROLL_LOCK_ON";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::input::common::V1_0::Meta o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::input::common::V1_0::Flag>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::input::common::V1_0::Flag> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::input::common::V1_0::Flag::WINDOW_IS_OBSCURED) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Flag::WINDOW_IS_OBSCURED)) {
        os += (first ? "" : " | ");
        os += "WINDOW_IS_OBSCURED";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Flag::WINDOW_IS_OBSCURED;
    }
    if ((o & ::android::hardware::input::common::V1_0::Flag::IS_GENERATED_GESTURE) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Flag::IS_GENERATED_GESTURE)) {
        os += (first ? "" : " | ");
        os += "IS_GENERATED_GESTURE";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Flag::IS_GENERATED_GESTURE;
    }
    if ((o & ::android::hardware::input::common::V1_0::Flag::TAINTED) == static_cast<int32_t>(::android::hardware::input::common::V1_0::Flag::TAINTED)) {
        os += (first ? "" : " | ");
        os += "TAINTED";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Flag::TAINTED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::input::common::V1_0::Flag o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::input::common::V1_0::Flag::WINDOW_IS_OBSCURED) {
        return "WINDOW_IS_OBSCURED";
    }
    if (o == ::android::hardware::input::common::V1_0::Flag::IS_GENERATED_GESTURE) {
        return "IS_GENERATED_GESTURE";
    }
    if (o == ::android::hardware::input::common::V1_0::Flag::TAINTED) {
        return "TAINTED";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::input::common::V1_0::Flag o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::input::common::V1_0::VideoFrame& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".data = ";
    os += ::android::hardware::toString(o.data);
    os += ", .height = ";
    os += ::android::hardware::toString(o.height);
    os += ", .width = ";
    os += ::android::hardware::toString(o.width);
    os += ", .timestamp = ";
    os += ::android::hardware::toString(o.timestamp);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::input::common::V1_0::VideoFrame& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::input::common::V1_0::VideoFrame& lhs, const ::android::hardware::input::common::V1_0::VideoFrame& rhs) {
    if (lhs.data != rhs.data) {
        return false;
    }
    if (lhs.height != rhs.height) {
        return false;
    }
    if (lhs.width != rhs.width) {
        return false;
    }
    if (lhs.timestamp != rhs.timestamp) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::input::common::V1_0::VideoFrame& lhs, const ::android::hardware::input::common::V1_0::VideoFrame& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::input::common::V1_0::MotionEvent& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".deviceId = ";
    os += ::android::hardware::toString(o.deviceId);
    os += ", .source = ";
    os += ::android::hardware::input::common::V1_0::toString(o.source);
    os += ", .displayId = ";
    os += ::android::hardware::toString(o.displayId);
    os += ", .downTime = ";
    os += ::android::hardware::toString(o.downTime);
    os += ", .eventTime = ";
    os += ::android::hardware::toString(o.eventTime);
    os += ", .action = ";
    os += ::android::hardware::input::common::V1_0::toString(o.action);
    os += ", .actionIndex = ";
    os += ::android::hardware::toString(o.actionIndex);
    os += ", .actionButton = ";
    os += ::android::hardware::input::common::V1_0::toString(o.actionButton);
    os += ", .flags = ";
    os += ::android::hardware::input::common::V1_0::toString<::android::hardware::input::common::V1_0::Flag>(o.flags);
    os += ", .policyFlags = ";
    os += ::android::hardware::input::common::V1_0::toString<::android::hardware::input::common::V1_0::PolicyFlag>(o.policyFlags);
    os += ", .edgeFlags = ";
    os += ::android::hardware::input::common::V1_0::toString<::android::hardware::input::common::V1_0::EdgeFlag>(o.edgeFlags);
    os += ", .metaState = ";
    os += ::android::hardware::input::common::V1_0::toString<::android::hardware::input::common::V1_0::Meta>(o.metaState);
    os += ", .buttonState = ";
    os += ::android::hardware::input::common::V1_0::toString<::android::hardware::input::common::V1_0::Button>(o.buttonState);
    os += ", .xPrecision = ";
    os += ::android::hardware::toString(o.xPrecision);
    os += ", .yPrecision = ";
    os += ::android::hardware::toString(o.yPrecision);
    os += ", .pointerProperties = ";
    os += ::android::hardware::toString(o.pointerProperties);
    os += ", .pointerCoords = ";
    os += ::android::hardware::toString(o.pointerCoords);
    os += ", .deviceTimestamp = ";
    os += ::android::hardware::toString(o.deviceTimestamp);
    os += ", .frames = ";
    os += ::android::hardware::toString(o.frames);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::input::common::V1_0::MotionEvent& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::input::common::V1_0::MotionEvent& lhs, const ::android::hardware::input::common::V1_0::MotionEvent& rhs) {
    if (lhs.deviceId != rhs.deviceId) {
        return false;
    }
    if (lhs.source != rhs.source) {
        return false;
    }
    if (lhs.displayId != rhs.displayId) {
        return false;
    }
    if (lhs.downTime != rhs.downTime) {
        return false;
    }
    if (lhs.eventTime != rhs.eventTime) {
        return false;
    }
    if (lhs.action != rhs.action) {
        return false;
    }
    if (lhs.actionIndex != rhs.actionIndex) {
        return false;
    }
    if (lhs.actionButton != rhs.actionButton) {
        return false;
    }
    if (lhs.flags != rhs.flags) {
        return false;
    }
    if (lhs.policyFlags != rhs.policyFlags) {
        return false;
    }
    if (lhs.edgeFlags != rhs.edgeFlags) {
        return false;
    }
    if (lhs.metaState != rhs.metaState) {
        return false;
    }
    if (lhs.buttonState != rhs.buttonState) {
        return false;
    }
    if (lhs.xPrecision != rhs.xPrecision) {
        return false;
    }
    if (lhs.yPrecision != rhs.yPrecision) {
        return false;
    }
    if (lhs.pointerProperties != rhs.pointerProperties) {
        return false;
    }
    if (lhs.pointerCoords != rhs.pointerCoords) {
        return false;
    }
    if (lhs.deviceTimestamp != rhs.deviceTimestamp) {
        return false;
    }
    if (lhs.frames != rhs.frames) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::input::common::V1_0::MotionEvent& lhs, const ::android::hardware::input::common::V1_0::MotionEvent& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::input::common::V1_0::Classification>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::input::common::V1_0::Classification> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::input::common::V1_0::Classification::NONE) == static_cast<uint8_t>(::android::hardware::input::common::V1_0::Classification::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Classification::NONE;
    }
    if ((o & ::android::hardware::input::common::V1_0::Classification::AMBIGUOUS_GESTURE) == static_cast<uint8_t>(::android::hardware::input::common::V1_0::Classification::AMBIGUOUS_GESTURE)) {
        os += (first ? "" : " | ");
        os += "AMBIGUOUS_GESTURE";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Classification::AMBIGUOUS_GESTURE;
    }
    if ((o & ::android::hardware::input::common::V1_0::Classification::DEEP_PRESS) == static_cast<uint8_t>(::android::hardware::input::common::V1_0::Classification::DEEP_PRESS)) {
        os += (first ? "" : " | ");
        os += "DEEP_PRESS";
        first = false;
        flipped |= ::android::hardware::input::common::V1_0::Classification::DEEP_PRESS;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::input::common::V1_0::Classification o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::input::common::V1_0::Classification::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::input::common::V1_0::Classification::AMBIGUOUS_GESTURE) {
        return "AMBIGUOUS_GESTURE";
    }
    if (o == ::android::hardware::input::common::V1_0::Classification::DEEP_PRESS) {
        return "DEEP_PRESS";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::input::common::V1_0::Classification o, ::std::ostream* os) {
    *os << toString(o);
}


}  // namespace V1_0
}  // namespace common
}  // namespace input
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::input::common::V1_0::Axis, 45> hidl_enum_values<::android::hardware::input::common::V1_0::Axis> = {
    ::android::hardware::input::common::V1_0::Axis::X,
    ::android::hardware::input::common::V1_0::Axis::Y,
    ::android::hardware::input::common::V1_0::Axis::PRESSURE,
    ::android::hardware::input::common::V1_0::Axis::SIZE,
    ::android::hardware::input::common::V1_0::Axis::TOUCH_MAJOR,
    ::android::hardware::input::common::V1_0::Axis::TOUCH_MINOR,
    ::android::hardware::input::common::V1_0::Axis::TOOL_MAJOR,
    ::android::hardware::input::common::V1_0::Axis::TOOL_MINOR,
    ::android::hardware::input::common::V1_0::Axis::ORIENTATION,
    ::android::hardware::input::common::V1_0::Axis::VSCROLL,
    ::android::hardware::input::common::V1_0::Axis::HSCROLL,
    ::android::hardware::input::common::V1_0::Axis::Z,
    ::android::hardware::input::common::V1_0::Axis::RX,
    ::android::hardware::input::common::V1_0::Axis::RY,
    ::android::hardware::input::common::V1_0::Axis::RZ,
    ::android::hardware::input::common::V1_0::Axis::HAT_X,
    ::android::hardware::input::common::V1_0::Axis::HAT_Y,
    ::android::hardware::input::common::V1_0::Axis::LTRIGGER,
    ::android::hardware::input::common::V1_0::Axis::RTRIGGER,
    ::android::hardware::input::common::V1_0::Axis::THROTTLE,
    ::android::hardware::input::common::V1_0::Axis::RUDDER,
    ::android::hardware::input::common::V1_0::Axis::WHEEL,
    ::android::hardware::input::common::V1_0::Axis::GAS,
    ::android::hardware::input::common::V1_0::Axis::BRAKE,
    ::android::hardware::input::common::V1_0::Axis::DISTANCE,
    ::android::hardware::input::common::V1_0::Axis::TILT,
    ::android::hardware::input::common::V1_0::Axis::SCROLL,
    ::android::hardware::input::common::V1_0::Axis::RELATIVE_X,
    ::android::hardware::input::common::V1_0::Axis::RELATIVE_Y,
    ::android::hardware::input::common::V1_0::Axis::GENERIC_1,
    ::android::hardware::input::common::V1_0::Axis::GENERIC_2,
    ::android::hardware::input::common::V1_0::Axis::GENERIC_3,
    ::android::hardware::input::common::V1_0::Axis::GENERIC_4,
    ::android::hardware::input::common::V1_0::Axis::GENERIC_5,
    ::android::hardware::input::common::V1_0::Axis::GENERIC_6,
    ::android::hardware::input::common::V1_0::Axis::GENERIC_7,
    ::android::hardware::input::common::V1_0::Axis::GENERIC_8,
    ::android::hardware::input::common::V1_0::Axis::GENERIC_9,
    ::android::hardware::input::common::V1_0::Axis::GENERIC_10,
    ::android::hardware::input::common::V1_0::Axis::GENERIC_11,
    ::android::hardware::input::common::V1_0::Axis::GENERIC_12,
    ::android::hardware::input::common::V1_0::Axis::GENERIC_13,
    ::android::hardware::input::common::V1_0::Axis::GENERIC_14,
    ::android::hardware::input::common::V1_0::Axis::GENERIC_15,
    ::android::hardware::input::common::V1_0::Axis::GENERIC_16,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::input::common::V1_0::ToolType, 5> hidl_enum_values<::android::hardware::input::common::V1_0::ToolType> = {
    ::android::hardware::input::common::V1_0::ToolType::UNKNOWN,
    ::android::hardware::input::common::V1_0::ToolType::FINGER,
    ::android::hardware::input::common::V1_0::ToolType::STYLUS,
    ::android::hardware::input::common::V1_0::ToolType::MOUSE,
    ::android::hardware::input::common::V1_0::ToolType::ERASER,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::input::common::V1_0::SourceClass, 6> hidl_enum_values<::android::hardware::input::common::V1_0::SourceClass> = {
    ::android::hardware::input::common::V1_0::SourceClass::NONE,
    ::android::hardware::input::common::V1_0::SourceClass::BUTTON,
    ::android::hardware::input::common::V1_0::SourceClass::POINTER,
    ::android::hardware::input::common::V1_0::SourceClass::NAVIGATION,
    ::android::hardware::input::common::V1_0::SourceClass::POSITION,
    ::android::hardware::input::common::V1_0::SourceClass::JOYSTICK,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::input::common::V1_0::Source, 15> hidl_enum_values<::android::hardware::input::common::V1_0::Source> = {
    ::android::hardware::input::common::V1_0::Source::UNKNOWN,
    ::android::hardware::input::common::V1_0::Source::KEYBOARD,
    ::android::hardware::input::common::V1_0::Source::DPAD,
    ::android::hardware::input::common::V1_0::Source::GAMEPAD,
    ::android::hardware::input::common::V1_0::Source::TOUCHSCREEN,
    ::android::hardware::input::common::V1_0::Source::MOUSE,
    ::android::hardware::input::common::V1_0::Source::STYLUS,
    ::android::hardware::input::common::V1_0::Source::BLUETOOTH_STYLUS,
    ::android::hardware::input::common::V1_0::Source::TRACKBALL,
    ::android::hardware::input::common::V1_0::Source::MOUSE_RELATIVE,
    ::android::hardware::input::common::V1_0::Source::TOUCHPAD,
    ::android::hardware::input::common::V1_0::Source::TOUCH_NAVIGATION,
    ::android::hardware::input::common::V1_0::Source::ROTARY_ENCODER,
    ::android::hardware::input::common::V1_0::Source::JOYSTICK,
    ::android::hardware::input::common::V1_0::Source::ANY,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::input::common::V1_0::Action, 13> hidl_enum_values<::android::hardware::input::common::V1_0::Action> = {
    ::android::hardware::input::common::V1_0::Action::DOWN,
    ::android::hardware::input::common::V1_0::Action::UP,
    ::android::hardware::input::common::V1_0::Action::MOVE,
    ::android::hardware::input::common::V1_0::Action::CANCEL,
    ::android::hardware::input::common::V1_0::Action::OUTSIDE,
    ::android::hardware::input::common::V1_0::Action::POINTER_DOWN,
    ::android::hardware::input::common::V1_0::Action::POINTER_UP,
    ::android::hardware::input::common::V1_0::Action::HOVER_MOVE,
    ::android::hardware::input::common::V1_0::Action::SCROLL,
    ::android::hardware::input::common::V1_0::Action::HOVER_ENTER,
    ::android::hardware::input::common::V1_0::Action::HOVER_EXIT,
    ::android::hardware::input::common::V1_0::Action::BUTTON_PRESS,
    ::android::hardware::input::common::V1_0::Action::BUTTON_RELEASE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::input::common::V1_0::EdgeFlag, 5> hidl_enum_values<::android::hardware::input::common::V1_0::EdgeFlag> = {
    ::android::hardware::input::common::V1_0::EdgeFlag::NONE,
    ::android::hardware::input::common::V1_0::EdgeFlag::TOP,
    ::android::hardware::input::common::V1_0::EdgeFlag::BOTTOM,
    ::android::hardware::input::common::V1_0::EdgeFlag::LEFT,
    ::android::hardware::input::common::V1_0::EdgeFlag::RIGHT,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::input::common::V1_0::PolicyFlag, 10> hidl_enum_values<::android::hardware::input::common::V1_0::PolicyFlag> = {
    ::android::hardware::input::common::V1_0::PolicyFlag::WAKE,
    ::android::hardware::input::common::V1_0::PolicyFlag::VIRTUAL,
    ::android::hardware::input::common::V1_0::PolicyFlag::FUNCTION,
    ::android::hardware::input::common::V1_0::PolicyFlag::GESTURE,
    ::android::hardware::input::common::V1_0::PolicyFlag::INJECTED,
    ::android::hardware::input::common::V1_0::PolicyFlag::TRUSTED,
    ::android::hardware::input::common::V1_0::PolicyFlag::FILTERED,
    ::android::hardware::input::common::V1_0::PolicyFlag::DISABLE_KEY_REPEAT,
    ::android::hardware::input::common::V1_0::PolicyFlag::INTERACTIVE,
    ::android::hardware::input::common::V1_0::PolicyFlag::PASS_TO_USER,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::input::common::V1_0::Button, 8> hidl_enum_values<::android::hardware::input::common::V1_0::Button> = {
    ::android::hardware::input::common::V1_0::Button::NONE,
    ::android::hardware::input::common::V1_0::Button::PRIMARY,
    ::android::hardware::input::common::V1_0::Button::SECONDARY,
    ::android::hardware::input::common::V1_0::Button::TERTIARY,
    ::android::hardware::input::common::V1_0::Button::BACK,
    ::android::hardware::input::common::V1_0::Button::FORWARD,
    ::android::hardware::input::common::V1_0::Button::STYLUS_PRIMARY,
    ::android::hardware::input::common::V1_0::Button::STYLUS_SECONDARY,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::input::common::V1_0::Meta, 18> hidl_enum_values<::android::hardware::input::common::V1_0::Meta> = {
    ::android::hardware::input::common::V1_0::Meta::NONE,
    ::android::hardware::input::common::V1_0::Meta::ALT_ON,
    ::android::hardware::input::common::V1_0::Meta::ALT_LEFT_ON,
    ::android::hardware::input::common::V1_0::Meta::ALT_RIGHT_ON,
    ::android::hardware::input::common::V1_0::Meta::SHIFT_ON,
    ::android::hardware::input::common::V1_0::Meta::SHIFT_LEFT_ON,
    ::android::hardware::input::common::V1_0::Meta::SHIFT_RIGHT_ON,
    ::android::hardware::input::common::V1_0::Meta::SYM_ON,
    ::android::hardware::input::common::V1_0::Meta::FUNCTION_ON,
    ::android::hardware::input::common::V1_0::Meta::CTRL_ON,
    ::android::hardware::input::common::V1_0::Meta::CTRL_LEFT_ON,
    ::android::hardware::input::common::V1_0::Meta::CTRL_RIGHT_ON,
    ::android::hardware::input::common::V1_0::Meta::META_ON,
    ::android::hardware::input::common::V1_0::Meta::META_LEFT_ON,
    ::android::hardware::input::common::V1_0::Meta::META_RIGHT_ON,
    ::android::hardware::input::common::V1_0::Meta::CAPS_LOCK_ON,
    ::android::hardware::input::common::V1_0::Meta::NUM_LOCK_ON,
    ::android::hardware::input::common::V1_0::Meta::SCROLL_LOCK_ON,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::input::common::V1_0::Flag, 3> hidl_enum_values<::android::hardware::input::common::V1_0::Flag> = {
    ::android::hardware::input::common::V1_0::Flag::WINDOW_IS_OBSCURED,
    ::android::hardware::input::common::V1_0::Flag::IS_GENERATED_GESTURE,
    ::android::hardware::input::common::V1_0::Flag::TAINTED,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::input::common::V1_0::Classification, 3> hidl_enum_values<::android::hardware::input::common::V1_0::Classification> = {
    ::android::hardware::input::common::V1_0::Classification::NONE,
    ::android::hardware::input::common::V1_0::Classification::AMBIGUOUS_GESTURE,
    ::android::hardware::input::common::V1_0::Classification::DEEP_PRESS,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_INPUT_COMMON_V1_0_TYPES_H
