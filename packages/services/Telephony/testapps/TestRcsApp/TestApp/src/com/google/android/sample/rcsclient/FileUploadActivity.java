/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.google.android.sample.rcsclient;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.provider.OpenableColumns;
import android.telephony.CarrierConfigManager;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.ProvisioningManager;
import android.telephony.ims.ProvisioningManager.RcsProvisioningCallback;
import android.telephony.ims.RcsClientConfiguration;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Xml;
import android.view.MenuItem;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.libraries.rcs.simpleclient.filetransfer.FileTransferController;
import com.android.libraries.rcs.simpleclient.filetransfer.FileTransferControllerImpl;
import com.android.libraries.rcs.simpleclient.filetransfer.requestexecutor.GbaAuthenticationProvider;
import com.android.libraries.rcs.simpleclient.filetransfer.requestexecutor.GbaRequestExecutor;
import com.android.libraries.rcs.simpleclient.filetransfer.requestexecutor.HttpRequestExecutor;

import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/** An activity to verify file upload with GBA authentication. */
public class FileUploadActivity extends AppCompatActivity {

    private static final String TAG = "TestRcsApp.FileUploadActivity";
    private static final String NAF_PREFIX = "https://3GPP-bootstrapping@";
    private static final int PICKFILE_RESULT = 1;
    private static final String HTTP_URI = "ftHTTPCSURI";
    private static final String PARM = "parm";
    private static final String NAME = "name";
    private static final String VALUE = "value";


    private ProvisioningManager mProvisioningManager;
    private int mDefaultSmsSubId;
    private File mFile;
    private Button mUpload, mBrowse;
    private TextView mUploadResult;
    private TextView mFileName;
    private EditText mServerUri;
    private RcsProvisioningCallback mCallback =
            new RcsProvisioningCallback() {
                @Override
                public void onConfigurationChanged(@NonNull byte[] configXml) {
                    String configResult = new String(configXml);
                    String server = getFtServerUri(configXml);
                    Log.i(TAG, "RcsProvisioningCallback.onConfigurationChanged called with xml:");
                    Log.i(TAG, configResult);
                    Log.i(TAG, "serverUri:" + server);
                    mServerUri.setText(server);
                }

                @Override
                public void onConfigurationReset() {
                    Log.i(TAG, "RcsProvisioningCallback.onConfigurationReset called.");
                }

                @Override
                public void onRemoved() {
                    Log.i(TAG, "RcsProvisioningCallback.onRemoved called.");
                }
            };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.file_upload_layout);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        initLayout();
        registerProvisioning();
    }

    private void initLayout() {
        mServerUri = findViewById(R.id.ft_uri);
        mUpload = findViewById(R.id.upload_btn);
        mBrowse = findViewById(R.id.browse_btn);
        mFileName = findViewById(R.id.file_name);
        mUploadResult = findViewById(R.id.upload_file_result);
        mUploadResult.setMovementMethod(new ScrollingMovementMethod());

        mBrowse.setOnClickListener(view -> {
            Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
            chooseFile.setType("*/*");
            chooseFile = Intent.createChooser(chooseFile, "Choose a file");
            startActivityForResult(chooseFile, PICKFILE_RESULT);
        });

        mUpload.setOnClickListener(view -> {
            if (TextUtils.isEmpty(mServerUri.getText())) {
                Toast.makeText(FileUploadActivity.this,
                        getResources().getString(R.string.server_empty),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (mFile == null) {
                Toast.makeText(FileUploadActivity.this,
                        getResources().getString(R.string.file_empty),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            Log.i(TAG, "upload file");
            try {
                FileTransferController fileTransferController = initFileTransferController();
                if (fileTransferController == null) {
                    Log.i(TAG, "FileTransferController null");
                    return;
                }
                mUploadResult.setText("");
                Futures.addCallback(
                        fileTransferController.uploadFile(UUID.randomUUID().toString(),
                                mFile),
                        new FutureCallback<String>() {
                            @Override
                            public void onSuccess(String xml) {
                                String text;
                                if (TextUtils.isEmpty(xml)) {
                                    text = "onFailure: Empty Xml";
                                    Log.i(TAG, text);
                                    mUploadResult.setText(text);
                                    return;
                                }
                                text = "onSuccess\r\n" + xml;
                                Log.i(TAG, text);
                                mUploadResult.setText(text);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                String text = "onFailure:" + t;
                                Log.i(TAG, text);
                                mUploadResult.setText(text);
                            }
                        },
                        getMainExecutor());
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
        });
    }

    @SuppressWarnings("MissingSuperCall") // TODO: fix me
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case PICKFILE_RESULT:
                if (resultCode == RESULT_OK) {
                    Uri fileUri = data.getData();
                    String fileName = getFileName(fileUri);
                    mFileName.setText(fileName);
                    try {
                        mFile = uriToFile(fileUri);
                        Log.i(TAG, "mFile:" + mFile);
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage());
                        e.printStackTrace();
                    }
                }
                break;
        }
    }

    private void registerProvisioning() {
        mDefaultSmsSubId = SmsManager.getDefaultSmsSubscriptionId();
        Log.i(TAG, "mDefaultSmsSubId:" + mDefaultSmsSubId);
        if (SubscriptionManager.isValidSubscriptionId(mDefaultSmsSubId)) {
            try {
                mProvisioningManager = ProvisioningManager.createForSubscriptionId(
                        mDefaultSmsSubId);
                mProvisioningManager.setRcsClientConfiguration(getDefaultClientConfiguration());
                mProvisioningManager.registerRcsProvisioningCallback(getMainExecutor(), mCallback);
            } catch (ImsException e) {
                Log.e(TAG, e.getMessage());
            }
        }
    }

    private RcsClientConfiguration getDefaultClientConfiguration() {
        SharedPreferences pref = getSharedPreferences("CONFIG", MODE_PRIVATE);

        return new RcsClientConfiguration(
                /*rcsVersion=*/ pref.getString("RCS_VERSION", "6.0"),
                /*rcsProfile=*/ pref.getString("RCS_PROFILE", "UP_1.0"),
                /*clientVendor=*/ "Goog",
                /*clientVersion=*/ "RCSAndrd-1.0");
    }

    private FileTransferController initFileTransferController() {
        mDefaultSmsSubId = SmsManager.getDefaultSmsSubscriptionId();
        if (SubscriptionManager.isValidSubscriptionId(mDefaultSmsSubId)) {
            TelephonyManager telephonyManager = getSystemService(
                    TelephonyManager.class).createForSubscriptionId(mDefaultSmsSubId);
            PersistableBundle carrierConfig = telephonyManager.getCarrierConfig();
            String uploadUrl = carrierConfig.getString(
                    CarrierConfigManager.KEY_CALL_COMPOSER_PICTURE_SERVER_URL_STRING);
            String carrierName = telephonyManager.getSimOperatorName();

            HttpRequestExecutor executor = new GbaRequestExecutor(
                    new GbaAuthenticationProvider(getSystemService(TelephonyManager.class),
                            NAF_PREFIX + uploadUrl, getMainExecutor()));
            return new FileTransferControllerImpl(executor, mServerUri.getText().toString(),
                    carrierName);
        } else {
            Log.i(TAG, "Invalid subId:" + mDefaultSmsSubId);
            return null;
        }
    }

    private String getFileName(Uri uri) throws IllegalArgumentException {
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);

        if (cursor.getCount() <= 0) {
            cursor.close();
            throw new IllegalArgumentException("Can't obtain file name, cursor is empty");
        }
        cursor.moveToFirst();
        String fileName = cursor.getString(
                cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
        cursor.close();

        return fileName;
    }

    private File uriToFile(Uri uri) {
        File file = null;
        if (uri == null) return file;
        if (uri.getScheme().equals(ContentResolver.SCHEME_FILE)) {
            file = new File(uri.getPath());
        } else if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
            ContentResolver contentResolver = getContentResolver();
            String cachedName = System.currentTimeMillis() + Math.round((Math.random() + 1) * 1000)
                    + "." + MimeTypeMap.getSingleton().getExtensionFromMimeType(
                    contentResolver.getType(uri));

            try {
                InputStream is = contentResolver.openInputStream(uri);
                File cache = new File(getExternalCacheDir().getAbsolutePath(), cachedName);
                FileOutputStream fos = new FileOutputStream(cache);
                ByteStreams.copy(is, fos);
                file = cache;
                fos.close();
                is.close();
            } catch (IOException e) {
                Log.i(TAG, e.getMessage());
            }
        }
        return file;
    }

    private String getContentType(Uri uri) {
        MimeTypeMap mime = MimeTypeMap.getSingleton();
        return mime.getExtensionFromMimeType(getContentResolver().getType(uri));
    }


    /**
     * According GSMA RCC.72, get FileTransfer URI from the config xml whose content includes the
     * following parameter.
     * <parm name="ftHTTPCSURI"
     * value="https://ftcontentserver.rcs.mnc008.mcc123.pub.3gppnetwork.org/content/"/>
     */
    private String getFtServerUri(byte[] xml) {
        try {
            InputStream inputStream = new ByteArrayInputStream(xml);
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(inputStream, "utf-8");

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if (parser.getName().equals(PARM)) {
                            String name = parser.getAttributeValue(null, NAME);
                            if (HTTP_URI.equalsIgnoreCase(name)) {
                                return parser.getAttributeValue(null, VALUE);
                            }
                        }
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return "";
        }
        return "";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //delete cache files
        File cache = new File(getExternalCacheDir().getAbsolutePath());
        File[] files = cache.listFiles();
        for (File file : files) {
            file.delete();
        }
        if (mProvisioningManager != null) {
            mProvisioningManager.unregisterRcsProvisioningCallback(mCallback);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }
}
