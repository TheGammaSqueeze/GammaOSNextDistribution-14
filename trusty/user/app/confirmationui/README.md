# ConfirmationUI Trusted App

This is an implementation of the ConfirmationUI trusted application for Trusty.
It is meant as a reference implementation for OEMs who want to implement ConfirmationUI or
"Android Protected Confirmation" and use Trusty as TEE OS.

## Dependencies

 * Android platform/system/teeui
 * libcxx
 * freetype

## Additional integration work

You will need a touch controller driver or another trusted input method for the targeted platform.

Included in this package is a sample layout as used by Pixel3(+) phones. For phones that use button
on the right side of the phone this layout can be adjusted by configuring the context parameters.
E.g.: (see TrustyConfirmationUI.cpp)
    conv.setParam<RightEdgeOfScreen>(1440_px);
    conv.setParam<BottomOfScreen>(2960_px);
    conv.setParam<PowerButtonTop>(34.146_mm);
    conv.setParam<PowerButtonBottom>(44.146_mm);
    conv.setParam<VolUpButtonTop>(54.146_mm);
    conv.setParam<VolUpButtonBottom>(64.146_mm);

## Layouts

A default example layout is provided in examples/layouts/. To override the layout with a vendor specific
one, define CONFIRMATIONUI_LAYOUTS to point to the layouts library you want to link against.
