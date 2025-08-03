# Android ANT HAL Service v.4.0.0 - 25 Aug 2014

The ANT HAL Service functions as a JNI wrapper for the ANT HAL. 
The ANT Hal Service provides the messaging and state control interface
to the built-in adapter for the ANT Radio Service to interact with.


## License

Copyright 2009-2014 Dynastream Innovations

This product includes software developed at
Dynastream Innovations (http://www.dynastream.com/).

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and  
limitations under the License.


## Changelog

v4.0.0 - 25 Aug 2014 (changes since 3.2.0)
-------------------------------------------------------------
> * Remove ANT and ANT_ADMIN permission defines which conflict with ANT Radio Service defines with new Android L permission behaviour (INSTALL_FAILED_DUPLICATE_PERMISSIONS_ERROR)