# Copyright 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.

# Netsim - a network simulator for discovery, ranging and communication

set(INSTALL_NETSIM_UI_DIR "${CMAKE_INSTALL_PREFIX}/netsim-ui"
    CACHE PATH "The directory the netsim web UI is installed in")

install(DIRECTORY ${CMAKE_CURRENT_LIST_DIR}/dist/
        DESTINATION ${INSTALL_NETSIM_UI_DIR})

if(NOT ANDROID_EMULATOR_BUILD)
  # Add netsim-ui into objs
  set(NETSIM_UI_DEPENDENCIES
      ${CMAKE_CURRENT_LIST_DIR}/dist/index.html>netsim-ui/index.html;
      ${CMAKE_CURRENT_LIST_DIR}/dist/dev.html>netsim-ui/dev.html;
      ${CMAKE_CURRENT_LIST_DIR}/dist/assets/grid-background.svg>netsim-ui/assets/grid-background.svg;
      ${CMAKE_CURRENT_LIST_DIR}/dist/assets/hexagonal-background.png>netsim-ui/assets/hexagonal-background.png;
      ${CMAKE_CURRENT_LIST_DIR}/dist/assets/netsim-logo-b.svg>netsim-ui/assets/netsim-logo-b.svg;
      ${CMAKE_CURRENT_LIST_DIR}/dist/assets/netsim-logo.svg>netsim-ui/assets/netsim-logo.svg;
      ${CMAKE_CURRENT_LIST_DIR}/dist/assets/polar-background.svg>netsim-ui/assets/polar-background.svg;
      ${CMAKE_CURRENT_LIST_DIR}/dist/js/cube-sprite.js>netsim-ui/js/cube-sprite.js;
      ${CMAKE_CURRENT_LIST_DIR}/dist/js/customize-map-button.js>netsim-ui/js/customize-map-button.js;
      ${CMAKE_CURRENT_LIST_DIR}/dist/js/device-dragzone.js>netsim-ui/js/device-dragzone.js;
      ${CMAKE_CURRENT_LIST_DIR}/dist/js/device-dropzone.js>netsim-ui/js/device-dropzone.js;
      ${CMAKE_CURRENT_LIST_DIR}/dist/js/device-info.js>netsim-ui/js/device-info.js;
      ${CMAKE_CURRENT_LIST_DIR}/dist/js/device-list.js>netsim-ui/js/device-list.js;
      ${CMAKE_CURRENT_LIST_DIR}/dist/js/device-map.js>netsim-ui/js/device-map.js;
      ${CMAKE_CURRENT_LIST_DIR}/dist/js/device-observer.js>netsim-ui/js/device-observer.js;
      ${CMAKE_CURRENT_LIST_DIR}/dist/js/license-info.js>netsim-ui/js/license-info.js;
      ${CMAKE_CURRENT_LIST_DIR}/dist/js/netsim/model.js>netsim-ui/js/netsim/model.js;
      ${CMAKE_CURRENT_LIST_DIR}/dist/js/navigation-bar.js>netsim-ui/js/navigation-bar.js;
      ${CMAKE_CURRENT_LIST_DIR}/dist/js/netsim-app.js>netsim-ui/js/netsim-app.js;
      ${CMAKE_CURRENT_LIST_DIR}/dist/js/packet-info.js>netsim-ui/js/packet-info.js;
      ${CMAKE_CURRENT_LIST_DIR}/dist/js/pyramid-sprite.js>netsim-ui/js/pyramid-sprite.js;
      ${CMAKE_CURRENT_LIST_DIR}/dist/node_modules/tslib/tslib.es6.js>netsim-ui/node_modules/tslib/tslib.es6.js;
  )
  android_license(TARGET "NETSIM_UI_DEPENDENCIES" LIBNAME None SPDX None
                  LICENSE None LOCAL None)
  android_target_dependency(netsimd all NETSIM_UI_DEPENDENCIES)
  android_install_dependency_force_exec(all NETSIM_UI_DEPENDENCIES)
endif()
