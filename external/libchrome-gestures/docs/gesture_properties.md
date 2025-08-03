# Gesture Properties

The Gestures library has many parameters which affect its operation. Examples
include acceleration curves, whether to reverse the scroll direction, and
thresholds for determining whether a touch is from a palm or a finger.

These parameters are represented by gesture properties, values which can be
changed by Chromium (though not always through the settings UI). This document
describes the gesture properties system and how it can be used by developers.

[TOC]

## Property representation

Each pointing device (touchpad, mouse, etc.) connected to a system has its own
set of gesture properties. Properties are identified by human-readable names,
such as "Scroll Accel Curve", "Mouse Reverse Scrolling", or "Palm Pressure".

Each property has a type (integer, double, boolean, or string), and a size,
which is the number of values of that type that it stores. (For strings, the
size is always 1.)

Chromium creates some read-only properties, which normally means that modifying
them wouldn't make sense (for example, the "Device Vendor ID" property, which
reflects a hardware ID). However, the Gestures library itself does not do this,
so other clients don't need to support them.

## Changing properties without recompiling

All properties have default values which are set in source code. These are
sometimes overridden by one of the configuration files in the [xorg-conf
repository][xorg-conf] (normally to tune a touchpad or set the resolution for a
mouse). However, since most of the time these are things you want to tweak many
times or toggle quickly, it's easiest to change them using the Gesture
Properties Service.

[xorg-conf]: https://chromium.googlesource.com/chromiumos/platform/xorg-conf/

### Enabling the Gesture Properties Service

First, enable the service by going to
`chrome://flags/#gesture-properties-dbus-service` (which has to be typed or
pasted into the address bar for security reasons), set the "Enable gesture
properties service" flag to "Enabled", and restart Chromium. You should only
have to do this once per Chromium profile.

### From crosh

Now that the service is enabled, you can change property values from `crosh`. To
open it, press Ctrl+Alt+T.

To determine the ID number of the input device you want to change properties
for, run `gesture_prop devices` (or `gesture_prop devs` for short). Here's some
example output:

```
crosh> gesture_prop devices
method return time=1576605764.119024 sender=:1.65 -> destination=:1.77 serial=330 reply_serial=2
   uint32 1
   array [
      dict entry(
         int32 9
         string "Atmel maXTouch Touchpad"
      )
   ]
```

In this case, there is one input device with gesture properties, the maXTouch
Touchpad with ID 9. We can see all of the properties it supports with
`gesture_prop list 9`:

```
crosh> gesture_prop list 9
method return time=1576606086.727507 sender=:1.65 -> destination=:1.81 serial=503 reply_serial=2
   uint32 205
   array [
      string "Two Finger Vertical Close Distance Thresh"
      string "Two Finger Horizontal Close Distance Thresh"
      string "Logging Notify"
      string "Damp Scroll Min Move Factor"
      string "No-Pinch Certain Ratio"
      string "Merged Finger Suspicious Angle Min Displacement"
      string "Merged Finger X Jump Min Disp"
      string "Finger Merge Maximum Pressure"
      string "Device Touchpad"
      string "Merged Finger Max Age"
      string "Mouse CPI"
      ...
      string "Australian Scrolling"
      ...
   ]
```

Let's find the value of the "Australian Scrolling" property, which determines
whether the scrolling direction is inverted:

```
crosh> gesture_prop get 9 "Australian Scrolling"
method return time=1576606330.909750 sender=:1.65 -> destination=:1.86 serial=574 reply_serial=2
   boolean false
   uint32 1
   variant       array [
         boolean true
      ]
```

The output tells us multiple things:

*   `boolean false` tells us that the property is not read-only;
*   `uint32 1` is the number of values this property has, in this case 1; and
*   the lines inside `variant array [` are the values of the property.

At the moment, "Australian Scrolling" is true, meaning that scroll direction is
inverted. If we move two fingers up on the pad, the content will scroll down.
Let's change the value:

```
crosh> gesture_prop set 9 "Australian Scrolling" array:boolean:false
method return time=1576606314.847606 sender=:1.65 -> destination=:1.84 serial=566 reply_serial=2
```

This should immediately take effect, meaning that moving two fingers up on the
pad scrolls the content up. Unless you change it again, the value will remain
the same until Chromium next restarts.

#### Value syntax

In the example above, we specified the value as `array:boolean:false`, which may
seem rather obtuse for setting a simple flag. This is because all properties
(except strings) are counted as arrays, hence the `array:` prefix, and we have
to specify the type of value we're setting, in this case `boolean`. (The
available types are `int32`, `double`, `boolean`, and `string`.) To specify
multiple values, separate them by commas:

```
crosh> gesture_prop set 9 "Some array property" array:int32:1,2,3,4
```

This syntax is actually borrowed from the Linux `dbus-send` command, so see [its
`man` page](https://dbus.freedesktop.org/doc/dbus-send.1.html#description) for a
full description.

### Over D-Bus

On Chromebooks with developer mode enabled, it's also possible to use the
service over D-Bus from your own program. This could be useful if you need to
write more advanced tooling to change certain properties, such as the
acceleration curves, which would be tedious to modify manually. See the
`cmd_gesture_prop` function in the [`crosh` script][crosh] and the handlers in
[`gesture_properties_service_provider.cc`][service-provider] as references for
the D-Bus API.

[crosh]: https://source.chromium.org/chromiumos/chromiumos/codesearch/+/HEAD:src/platform2/crosh/crosh?q=cmd_gesture_prop
[service-provider]: https://source.chromium.org/chromium/chromium/src/+/HEAD:ash/dbus/gesture_properties_service_provider.cc

## Pointer acceleration curves

Perhaps the most important gesture properties are those that represent
acceleration curves. These define how fast the mouse pointer moves in relation
to the speed at which the user moves the mouse or their finger. While a default
for each curve is compiled in to Chromium, a custom curve can be activated by
setting a corresponding boolean property (e.g. "Use Custom Mouse Pointer Accel
Curve").

(Note that mouse wheel scrolling curves are defined more simply, as a single
polynomial with five terms, so they aren't covered here.)

Acceleration curves are defined by up to 20 quadratic curve segments. Each curve
segment has four parameters defining a quadratic, where *x* is the magnitude of
the motion:

*   the maximum value of *x* for which this segment will be applied (default
    infinity, called `x_` in code)
*   *a*, the *x²* multiplier (default 0, called `sqr_`)
*   *b*, the *x* multiplier (default 1, called `mul_`)
*   *c*, the constant (default 0, called `int_`)

So, each quadratic (of the form *ax² + bx + c*) applies to values of
*x* between the maximum value of the previous segment and its own maximum value.

In the gesture properties, the four parameters from each curve segment are
concatenated together into an array of 80 doubles. (These are then cast to
[`struct CurveSegment`][struct-curvesegment]s by `AccelFilterInterpreter`.)

Putting all of this together, we might change the mouse pointer acceleration
"curve" into a straight line where *y = -x* (therefore inverting pointer
movement) with the following commands:

```
crosh> gesture_prop set 12 "Mouse Pointer Accel Curve" array:double:inf,0,-1,0,inf,0,-1,0,inf,0,-1,0,inf,0,-1,0,inf,0,-1,0,inf,0,-1,0,inf,0,-1,0,inf,0,-1,0,inf,0,-1,0,inf,0,-1,0,inf,0,-1,0,inf,0,-1,0,inf,0,-1,0,inf,0,-1,0,inf,0,-1,0,inf,0,-1,0,inf,0,-1,0,inf,0,-1,0,inf,0,-1,0,inf,0,-1,0
crosh> gesture_prop set 12 "Use Custom Mouse Pointer Accel Curve" array:boolean:true
```

(Of course, really only the first four values of the curve matter here, as the
maximum *x* of the first segment is infinite.)

[struct-curvesegment]: https://source.chromium.org/chromiumos/chromiumos/codesearch/+/HEAD:src/platform/gestures/include/accel_filter_interpreter.h?q=%22struct%20CurveSegment%22
