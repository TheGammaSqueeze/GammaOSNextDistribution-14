/*
 * Copyright (C) 2022 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.car.tool.apibuilder;

import com.android.car.tool.data.ParsedData;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GenerateAPI {

    private static final boolean DBG = false;

    private static final String ANDROID_BUILD_TOP = "ANDROID_BUILD_TOP";
    private static final String CAR_API_PATH =
            "/packages/services/Car/car-lib/src/android/car";
    private static final String CAR_SERVICE_PATH =
            "/packages/services/Car/service/src";
    private static final String CAR_BUILT_IN_API_PATH =
            "/packages/services/Car/car-builtin-lib/src/android/car/builtin";
    private static final String CSHS_PATH =
            "/frameworks/opt/car/services/builtInServices/src";
    private static final String CAR_API_ANNOTATION_TEST_FILE =
            "/packages/services/Car/tests/carservice_unit_test/res/raw/car_api_classes.txt";
    private static final String CAR_BUILT_IN_ANNOTATION_TEST_FILE =
            "/packages/services/Car/tests/carservice_unit_test/res/raw/"
            + "car_built_in_api_classes.txt";
    private static final String CAR_HIDDEN_API_FILE =
            "/packages/services/Car/tests/carservice_unit_test/res/raw/"
            + "car_hidden_apis.txt";
    private static final String CAR_ADDEDINORBEFORE_API_FILE =
            "/packages/services/Car/tests/carservice_unit_test/res/raw/"
                    + "car_addedinorbefore_apis.txt";
    private static final String CSHS_NON_HIDDEN_CLASSES_FILE =
            "/frameworks/opt/car/services/builtInServices/tests/res/raw/CSHS_classes.txt";

    private static final String PRINT_CLASSES = "--print-classes";
    private static final String PRINT_NON_HIDDEN_CLASSES_CSHS = "--print-non-hidden-classes-CSHS";
    private static final String UPDATE_CLASSES = "--update-classes";
    private static final String UPDATE_NON_HIDDEN_CLASSES_CSHS = "--update-non-hidden-classes-CSHS";
    private static final String PRINT_HIDDEN_APIS = "--print-hidden-apis";
    private static final String PRINT_HIDDEN_APIS_WITH_CONSTR = "--print-hidden-apis-with-constr";
    private static final String UPDATE_HIDDEN_APIS = "--update-hidden-apis";
    private static final String PRINT_ALL_APIS = "--print-all-apis";
    private static final String PRINT_ALL_APIS_WITH_CONSTR = "--print-all-apis-with-constr";
    private static final String UPDATE_APIS_WITH_ADDEDINORBEFORE =
            "--update-apis-with-addedinorbefore";
    private static final String PLATFORM_VERSION_ASSERTION_CHECK =
            "--platform-version-assertion-check";
    private static final String PRINT_ALL_APIS_WITH_CAR_VERSION =
            "--print-all-apis-with-car-version";
    private static final String PRINT_INCORRECT_REQUIRES_API_USAGE_IN_CAR_SERVICE =
            "--print-incorrect-requires-api-usage-in-car-service";
    private static final String PRINT_ADDEDIN_WITHOUT_REQUIRES_API_IN_CAR_BUILT_IN =
            "--print-addedin-without-requires-api-in-car-built-in";
    private static final String PRINT_ADDEDIN_WITHOUT_REQUIRES_API_IN_CSHS =
            "--print-addedin-without-requires-api-in-CSHS";
    private static final String ROOT_DIR = "--root-dir";

    public static void main(final String[] args) throws Exception {
        try {
            if (args.length == 0) {
                printHelp();
                return;
            }

            boolean runForCSHS = false;
            boolean printClasses = false;
            boolean printNonHiddenClassesCSHS = false;
            boolean updateClasses = false;
            boolean updateNonHiddenClassesCSHS = false;
            boolean printHiddenApis = false;
            boolean printHiddenApisWithConstr = false;
            boolean updateHiddenApis = false;
            boolean printAllApis = false;
            boolean printAllApisWithConstr = false;
            boolean updateApisWithAddedinorbefore = false;
            boolean platformVersionCheck = false;
            boolean printAllApisWithCarVersion = false;
            boolean printIncorrectRequiresApiUsageInCarService = false;
            boolean printAddedinWithoutRequiresApiInCarBuiltIn = false;
            boolean printAddedinWithoutRequiresApiInCSHS = false;
            String rootDir = System.getenv(ANDROID_BUILD_TOP);
            // If print request is more than one. Use marker to separate data. This would be useful
            // for executing multiple requests in one go.
            int printRequests = 0;

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case PRINT_CLASSES:
                        printClasses = true;
                        printRequests++;
                        break;
                    case PRINT_NON_HIDDEN_CLASSES_CSHS:
                        printNonHiddenClassesCSHS = true;
                        runForCSHS = true;
                        printRequests++;
                        break;
                    case UPDATE_CLASSES:
                        updateClasses = true;
                        break;
                    case UPDATE_NON_HIDDEN_CLASSES_CSHS:
                        updateNonHiddenClassesCSHS = true;
                        runForCSHS = true;
                        break;
                    case PRINT_HIDDEN_APIS:
                        printHiddenApis = true;
                        printRequests++;
                        break;
                    case PRINT_HIDDEN_APIS_WITH_CONSTR:
                        printHiddenApisWithConstr = true;
                        printRequests++;
                        break;
                    case UPDATE_HIDDEN_APIS:
                        updateHiddenApis = true;
                        break;
                    case PRINT_ALL_APIS:
                        printAllApis = true;
                        printRequests++;
                        break;
                    case PRINT_ALL_APIS_WITH_CONSTR:
                        printAllApisWithConstr = true;
                        printRequests++;
                        break;
                    case UPDATE_APIS_WITH_ADDEDINORBEFORE:
                        updateApisWithAddedinorbefore = true;
                        break;
                    case ROOT_DIR:
                        rootDir = args[++i];
                        break;
                    case PLATFORM_VERSION_ASSERTION_CHECK:
                        platformVersionCheck = true;
                        break;
                    case PRINT_ALL_APIS_WITH_CAR_VERSION:
                        printAllApisWithCarVersion = true;
                        break;
                    case PRINT_INCORRECT_REQUIRES_API_USAGE_IN_CAR_SERVICE:
                        printIncorrectRequiresApiUsageInCarService = true;
                        break;
                    case PRINT_ADDEDIN_WITHOUT_REQUIRES_API_IN_CAR_BUILT_IN:
                        printAddedinWithoutRequiresApiInCarBuiltIn = true;
                        break;
                    case PRINT_ADDEDIN_WITHOUT_REQUIRES_API_IN_CSHS:
                        printAddedinWithoutRequiresApiInCSHS = true;
                        runForCSHS = true;
                        printRequests++;
                        break;
                    default:
                        System.out.println("Incorrect Arguments.");
                        printHelp();
                        return;
                }
            }

            // rootDir must be set.
            if (rootDir == null || rootDir.isEmpty()) {
                System.out.println("Root dir not set.");
                printHelp();
                return;
            }

            // Do CarServiceHelperService related stuff here
            if (runForCSHS) {
                List<File> allJavaFiles_CSHS = getAllFiles(new File(rootDir + CSHS_PATH));
                ParsedData parsedDataCSHS = new ParsedData();
                ParsedDataBuilder.populateParsedData(allJavaFiles_CSHS, parsedDataCSHS);
                if (printNonHiddenClassesCSHS) {
                    printMarker(printRequests, PRINT_NON_HIDDEN_CLASSES_CSHS);
                    print(ParsedDataHelper.getNonHiddenClassNamesOnly(parsedDataCSHS));
                }
                if (printAddedinWithoutRequiresApiInCSHS) {
                    printMarker(printRequests, PRINT_ADDEDIN_WITHOUT_REQUIRES_API_IN_CSHS);
                    print(ParsedDataHelper.getIncorrectRequiresApi(parsedDataCSHS));
                }
                if (updateNonHiddenClassesCSHS) {
                    write(rootDir + CSHS_NON_HIDDEN_CLASSES_FILE,
                            ParsedDataHelper.getNonHiddenClassNamesOnly(parsedDataCSHS));
                }
                return;
            }

            List<File> allJavaFiles_carLib = getAllFiles(new File(rootDir + CAR_API_PATH));
            List<File> allJavaFiles_carBuiltInLib = getAllFiles(
                    new File(rootDir + CAR_BUILT_IN_API_PATH));

            ParsedData parsedDataCarLib = new ParsedData();
            ParsedData parsedDataCarBuiltinLib = new ParsedData();
            ParsedDataBuilder.populateParsedData(allJavaFiles_carLib, parsedDataCarLib);
            ParsedDataBuilder.populateParsedData(allJavaFiles_carBuiltInLib,
                    parsedDataCarBuiltinLib);

            if (printClasses) {
                printMarker(printRequests, PRINT_CLASSES);
                print(ParsedDataHelper.getClassNamesOnly(parsedDataCarLib));
                print(ParsedDataHelper.getClassNamesOnly(parsedDataCarBuiltinLib));
            }

            if (updateClasses) {
                write(rootDir + CAR_API_ANNOTATION_TEST_FILE,
                        ParsedDataHelper.getClassNamesOnly(parsedDataCarLib));
                write(rootDir + CAR_BUILT_IN_ANNOTATION_TEST_FILE,
                        ParsedDataHelper.getClassNamesOnly(parsedDataCarBuiltinLib));
            }

            if (updateHiddenApis) {
                write(rootDir + CAR_HIDDEN_API_FILE,
                        ParsedDataHelper.getHiddenApisOnly(parsedDataCarLib));
            }

            if (printHiddenApis) {
                printMarker(printRequests, PRINT_HIDDEN_APIS);
                print(ParsedDataHelper.getHiddenApisOnly(parsedDataCarLib));
            }

            if (printHiddenApisWithConstr) {
                printMarker(printRequests, PRINT_HIDDEN_APIS_WITH_CONSTR);
                print(ParsedDataHelper.getHiddenApisWithHiddenConstructor(parsedDataCarLib));
            }

            if (printAllApis) {
                printMarker(printRequests, PRINT_ALL_APIS);
                print(ParsedDataHelper.getAllApis(parsedDataCarLib));
            }

            if (printAllApisWithConstr) {
                printMarker(printRequests, PRINT_ALL_APIS_WITH_CONSTR);
                print(ParsedDataHelper.getAllApisWithConstructor(parsedDataCarLib));
            }

            if (updateApisWithAddedinorbefore) {
                write(rootDir + CAR_ADDEDINORBEFORE_API_FILE,
                        ParsedDataHelper.getAddedInOrBeforeApisOnly(parsedDataCarLib));
            }
            if (platformVersionCheck) {
                printMarker(printRequests, PLATFORM_VERSION_ASSERTION_CHECK);
                print(ParsedDataHelper.checkAssertPlatformVersionAtLeast(parsedDataCarLib));
            }
            if (printAllApisWithCarVersion) {
                printMarker(printRequests, PRINT_ALL_APIS_WITH_CAR_VERSION);
                print(ParsedDataHelper.getApisWithVersion(parsedDataCarLib));
                print(ParsedDataHelper.getApisWithVersion(parsedDataCarBuiltinLib));
            }
            if (printIncorrectRequiresApiUsageInCarService) {
                printMarker(printRequests, PRINT_INCORRECT_REQUIRES_API_USAGE_IN_CAR_SERVICE);
                List<File> allJavaFiles_CarService =
                        getAllFiles(new File(rootDir + CAR_SERVICE_PATH));
                ParsedData parsedDataCarService = new ParsedData();
                ParsedDataBuilder.populateParsedData(allJavaFiles_CarService, parsedDataCarService);
                print(ParsedDataHelper.getIncorrectRequiresApiUsage(parsedDataCarService));
            }
            if (printAddedinWithoutRequiresApiInCarBuiltIn) {
                printMarker(printRequests, PRINT_ADDEDIN_WITHOUT_REQUIRES_API_IN_CAR_BUILT_IN);
                print(ParsedDataHelper.getIncorrectRequiresApi(parsedDataCarBuiltinLib));
            }
        } catch (Exception e) {
            throw e;
        }
    }

    private static void printMarker(int printRequests, String request) {
        if (printRequests > 1) {
            // Should not change this marker since it would break the other script.
            System.out.println("Start-" + request);
        }
    }

    private static void print(List<String> data) {
        for (String string : data) {
            System.out.println(string);
        }
    }

    private static void write(String filePath, List<String> data) throws IOException {
        Collections.sort(data);
        Path file = Paths.get(filePath);
        Files.write(file, data, StandardCharsets.UTF_8);
    }

    private static void printHelp() {
        System.out.println("**** Help ****");
        System.out.println("At least one argument is required. Supported arguments - ");
        System.out.println(PRINT_CLASSES + " prints the list of valid class and"
                + " interfaces.");
        System.out.println(UPDATE_CLASSES + " updates the test file with the list"
                + " of valid class and interfaces. These files are updated"
                + " tests/carservice_unit_test/res/raw/car_api_classes.txt and"
                + " tests/carservice_unit_test/res/raw/car_built_in_api_classes.txt");
        System.out.println(PRINT_HIDDEN_APIS + " prints hidden api list.");
        System.out.println(PRINT_HIDDEN_APIS_WITH_CONSTR + " generates hidden api list with"
                + " hidden constructors.");
        System.out.println(UPDATE_HIDDEN_APIS + " generates hidden api list. "
                + "Results would be updated in " + CAR_HIDDEN_API_FILE);
        System.out.println(PRINT_ALL_APIS + " prints all apis");
        System.out.println(PRINT_ALL_APIS + " prints all apis and constructors.");
        System.out.println(UPDATE_APIS_WITH_ADDEDINORBEFORE
                + " generates the api list that contains the @AddedInOrBefore annotation. "
                + "Results would be updated in " + CAR_ADDEDINORBEFORE_API_FILE);
        System.out.println(PLATFORM_VERSION_ASSERTION_CHECK
                + " : Iterates through APIs to ensure that APIs added after TIRAMISU_x have call "
                + "assertPlatformVersionAtLeast with the correct minPlatformVersion.");
        System.out.println(PRINT_ALL_APIS_WITH_CAR_VERSION
                + " : Prints a list of all apis along with their min car version.");
        System.out.println("Second argument is value of Git Root Directory. By default, "
                + "it is environment variable ANDROID_BUILD_TOP. If environment variable is not set"
                + "then provide using" + ROOT_DIR + " <directory>");
    }

    private static List<File> getAllFiles(File folderName) {
        List<File> allFiles = new ArrayList<>();
        File[] files = folderName.listFiles();

        for (int i = 0; i < files.length; i++) {
            if (files[i].isFile() && files[i].getName().endsWith(".java")) {
                if (DBG) {
                    System.out.printf("File added %s\n", files[i]);
                }
                allFiles.add(files[i]);
            }
            if (files[i].isDirectory()) {
                allFiles.addAll(getAllFiles(files[i]));
            }
        }
        // List files doesn't guarantee fixed order on all systems. It is better to sort the list.
        Collections.sort(allFiles);
        return allFiles;
    }
}
