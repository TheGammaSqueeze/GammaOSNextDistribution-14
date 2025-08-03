# Bluetooth Stack Fuzzers

## Overview
Bluetooth stack implements very complex wireless communication protocols and
scenarios. It's been a hotspot for security researchers and attackers. Fuzzing
has been used as a popular approach to look for security vulnerabilities in
Bluetooth stack.

Due to the complex architecture of the Android Bluetooth stack, fuzzing the
entire stack with pure software is very difficult and impractical. Instead,
multiple fuzzers are created to target different areas of the BT stack. Fuzzers
in this directory focuses on the components under `system/stack`.

## Attack surface selection
For security purpose, remote attack surfaces usually take higher priority since
they can cause much severe damage comparing to local attacks. This makes the
incoming BT message handlers our focus. The goal is to be able to pipe randomly
generated data packets to those message handlers to explore the code path each
component contains. This helps flushing out any memory/logic issues in the
remote message handling routine.

Components requiring no authentication, or dealing with messages before
authentication have a higher fuzzing priority. This includes the SDP, GATT, SMP
and L2CAP components. A couple post authentication components such as BNEP,
AVRC, AVCT are also covered by different fuzzers.

## Bluetooth stack overview
According to Bluetooth spec and the source code, most of the components we care
here work above the L2CAP layer. In general they work with the following
sequences:
1. At initialization, a component registers itself to L2CAP with a set of
callback functions, which, usually contains at least one function handling the
incoming Bluetooth packets.
2. Each component also exposes certain APIs to upper layers, which can be higher
level Bluetooth framework, or even applications. Bluetooth framework or
applications use these APIs to configure the stack, and issue requests.
3. Upper layer also registers callbacks into each component. When a component
receives a response, it parses and validates the response, extracts the payload
data, and passes data to upper layer using those callbacks.
4. Many Bluetooth components work in both server mode and client mode with
different sets of APIs and processing logics.
5. It's common for a Bluetooth stack component to use state machines. The state
transition happens when APIs are called, or incoming packets are handled.

## Fuzzer design
The fuzzers are designed to simulate how a component is used in the real world,
but with a lot of simplifications. Here is how they work in general:
1. First a fuzzer should mock the L2CAP APIs to capture the registration call
from the target component.
2. At each fuzzing iteration, the fuzzer initializes the target component using
its initialization function. This will cause the component to register itself to
L2CAP. Because L2CAP APIs are mocked, the fuzzer will capture the registration
information, most importantly, the message handler callback function.
3. The fuzzer then calls necessary APIs and callbacks exposed to L2CAP to
further initialize the target component into either server mode or client mode.
4. Starting from here, the fuzzer splits the input data into multiple packets,
and feeds them to the target component using the previously captured message
handler callback.
5. It's common that a fuzzer also needs to call certain APIs to trigger state
transition of the target component. The fuzzer might use fixed data or data
derived from fuzzing input to make those API calls.
6. Once all the data is consumed, the target is cleaned up so next iteration can
start cleanly. It's important to cleanup all the data so there is no state
pollution between two iterations, otherwise it will be very difficult to
reproduce a crash.

## Mocking dependencies
For maximium fuzzing efficiency, the fuzzers are created to include the target
component and minimium number of other Bluetooth components. This means any
dependencies from other Bluetooth components need to be mocked. The mocks are
implemented with a balance of reaching maximium target code coverage and
minimium development effort. Some of the mocks are simply not implemented.

## Future improvement
These fuzzers are still far from perfect, with the following possible
improvements:
1. Code coverage

    It's very important to review the code coverage of each fuzzer. Any big
    coverage gaps should be analyzed and improved. This can be done by adding
    additional logic in the fuzzing loop, such as calling certain APIs,
    providing upper layer callbacks, or changing the mock behaviors.

2. Performance

    The fuzzers are designed to run as fast as possible. But there might still
    be some room to improve the performance. Profiling can be done to figure
    out the performance bottlenecks, which might be sleeps, tight for loops, or
    computational heavy operations, such as crypto functions.

3. Component coverage

    Currently only 3 fuzzers are created. More should be added so we can cover
    most of the stack components. With the mocks and design patterns it
    shouldn't be too difficult.
