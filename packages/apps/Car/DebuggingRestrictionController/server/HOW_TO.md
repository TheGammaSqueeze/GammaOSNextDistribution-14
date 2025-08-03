# Debugging Access Token Issuer

This sample demonstrates a Firebase Cloud Function that issues debugging access
tokens to authenticated and authorized users.

The directory structure looks like this:

```shell
server/
 |
 +- firebase.json  # Describes properties of the project
 |
 +- genkey.sh      # Optional script to generate token signing
                   # key and certificates with a self-signed CA
 |
 + package.json    # npm package file
 |
 +- functions/     # Directory containing the function code
      |
      +- api_config.SAMPLE.json # A sample configuration
      |
      +- .eslintrc.json  # Rules for JavaScript linting
      |
      +- package.json  # npm package file
      |
      +- index.js      # main source file
      |
      +- node_modules/ # directory where the dependencies (declared in
                       # package.json) are installed
```

## Running the Samples

*   Setup Firebase Cloud Functions. See Step 1-3 of the guide at
    [https://firebase.google.com/docs/functions/get-started](https://firebase.google.com/docs/functions/get-started).
*   Prepare the token signing key and certificate. The key MUST be a RSA key
    with at least 2048 bits. The certificate MUST contain at least one Subject
    Alternative Name
    ([SAN](https://en.wikipedia.org/wiki/Subject_Alternative_Name)). Use
    `genkey.sh` if signing the certificate with a self-signed CA.
*   Configure the token issuer in a config file. See `api_config.SAMPLE.json`
    for an example. The config file contains secret information; do NOT commit
    it in git.
*   Deploy the config file by running `firebase functions:config:set
    api_config="$(cat YOUR_CONFIG.json)"`.
*   Deploy the Cloud Function. See the instructions at
    [https://firebase.google.com/docs/functions/manage-functions](hhttps://firebase.google.com/docs/functions/manage-functions).

## License

Copyright 2021 Google LLC

Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements. See the NOTICE file distributed with this work for
additional information regarding copyright ownership. The ASF licenses this file
to you under the Apache License, Version 2.0 (the "License"); you may not use
this file except in compliance with the License. You may obtain a copy of the
License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed
under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
CONDITIONS OF ANY KIND, either express or implied. See the License for the
specific language governing permissions and limitations under the License.
