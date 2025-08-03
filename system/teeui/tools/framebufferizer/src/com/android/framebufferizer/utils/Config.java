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
 * limitations under the License.
 */

package com.android.framebufferizer.utils;

import java.io.*;
import java.lang.Runtime;
import java.nio.file.*;
import java.util.Map;
import java.util.HashMap;
import org.json.JSONObject;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import org.json.JSONException;

public class Config {
    public static final String KEY_MAGNIFIED = "magnified";
    public static final String KEY_INVERTED = "inverted";
    public static final String KEY_LAYOUT = "layout";
    public static final String KEY_LOCALE = "locale";
    public static final String KEY_DEVICE = "device";
    public static final String KEY_MESSAGE = "message";
    private static final String fileName = "config.json";
    private static final String fileLock = ".lock";
    private static Config _instance = null;
    private static final String fileDir = System.getProperty("user.home") + File.separator + ".teeui";
    private static Map<String, Object> configMap = null;
    private Config() {
        configMap = new HashMap<>();
    }

    public static Config getInstance() {
        if (_instance == null)
            _instance = new Config();

        return _instance;
    }

    public void setValue(String key, Object value){
        configMap.put(key, value);
    }

    public Map<String, Object> getConfiguration() {
        Map<String, Object> config = null;
        try {
            String content = new String(Files.readAllBytes(Paths.get(fileDir + File.separator + fileName)));
            Gson gson = new Gson();
            config = gson.fromJson(content, Map.class);
        } catch (NoSuchFileException e) {
            System.err.println("" + fileDir + File.separator + fileName + " file does not exists because the tool is started for the first time.");
        } catch (SecurityException e) {
            e.printStackTrace();
            System.err.println("" + fileDir + File.separator + fileName + " file read/write permission denied.");
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Exception while opening file " + fileDir + File.separator + fileName + ".");
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
            System.err.println("Invalid json format for file " + fileDir + File.separator + fileName + ".");
        } catch (JsonParseException e) {
            e.printStackTrace();
            System.err.println("Exception while parsing file " + fileDir + File.separator + fileName + ".");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Exception while getting configuration.");
        }
        return config;
    }

    private void saveConfiguration() {
        try {
            File directory = new File(fileDir);
            directory.mkdirs();
            File file = new File(directory, fileName);
            JSONObject jsonObject = new JSONObject(configMap);
            PrintWriter pw = new PrintWriter(file);
            pw.write(jsonObject.toString());
            pw.flush();
            pw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.err.println("Exception while saving configuration, " + fileDir + File.separator + fileName + " file not found.");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Exception while saving configuration.");
        }
    }

    public boolean createLockFile() {
        boolean fileCreated = false;
        try {
            File directory = new File(fileDir);
            directory.mkdirs();
            File file = new File(directory, fileLock);
            fileCreated = file.createNewFile();
            if(fileCreated){
                Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                    public void run() {
                        try {
                            saveConfiguration();
                            file.delete();
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.err.println("Exception while deleting file " + fileDir + File.separator + fileName + ".");
                        }
                    }
                }));
            } else {
                System.out.println("The lock file already exists. Another instance may be running. If no other instance is running, consider removing the lock file " + fileDir + File.separator + fileLock + " manually and try again.");
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Exception while creating file " + fileDir + File.separator + fileLock + ", exiting application.");
        }  catch (Exception e) {
            e.printStackTrace();
            System.err.println("Exception in createLockFile, exiting application.");
        }
        return fileCreated;
    }
}
