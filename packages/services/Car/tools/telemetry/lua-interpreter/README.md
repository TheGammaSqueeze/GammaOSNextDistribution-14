Lua Interpreter
=====

The Lua Interpreter is a web-based IDE tool that can run Lua scripts with the specific Android Auto telemetry callbacks.

## Prerequisites
***
Install the required node dependencies using npm with
```
sudo apt install nodejs
npm install
```

Install Bazel with
```
sudo apt install bazel
```

Change the LUA_SRC inside the WORKSPACE file to point to the directory containing the headers
of the Lua C API which should be in $ANDROID_BUILD_TOP/external/lua/src.

## Running
***
Run the following commands on the command line to start the server:
```
npm run build; bazel run server
```

Open the link provided from starting the server to access the tool (the link is accessible from the terminal window).

## Testing
***
The following commands assume you are at the root directory.

To test everything, run:
```
bazel test --test_output=all //tests:lua_interpreter_tests
```

To test the server, run:
```
bazel test --test_output=all //tests:app_test
```

To test the Lua Engine, run:
```
bazel test --test_output=all //tests:lua_engine_test
```
