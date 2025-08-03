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

package com.android.layoutlib.bridge.android;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.IntentSender.SendIntentException;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.UserHandle;
import android.view.Display;
import android.view.DisplayAdjustments;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.Executor;

public class ApplicationContext extends Context {
    private final WeakReference<Context> mContextRef;

    public ApplicationContext(Context context) {
        mContextRef = new WeakReference<>(context);
    }

    @Override
    public AssetManager getAssets() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getAssets();
        }
        return null;
    }

    @Override
    public Resources getResources() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getResources();
        }
        return null;
    }

    @Override
    public PackageManager getPackageManager() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getPackageManager();
        }
        return null;
    }

    @Override
    public ContentResolver getContentResolver() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getContentResolver();
        }
        return null;
    }

    @Override
    public Looper getMainLooper() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getMainLooper();
        }
        return null;
    }

    @Override
    public Context getApplicationContext() {
        return this;
    }

    @Override
    public void setTheme(int resid) {
        Context context = mContextRef.get();
        if (context != null) {
            context.setTheme(resid);
        }
    }

    @Override
    public Theme getTheme() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getTheme();
        }
        return null;
    }

    @Override
    public ClassLoader getClassLoader() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getClassLoader();
        }
        return null;
    }

    @Override
    public String getPackageName() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getPackageName();
        }
        return null;
    }

    @Override
    public String getBasePackageName() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getBasePackageName();
        }
        return null;
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getApplicationInfo();
        }
        return null;
    }

    @Override
    public String getPackageResourcePath() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getPackageResourcePath();
        }
        return null;
    }

    @Override
    public String getPackageCodePath() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getPackageCodePath();
        }
        return null;
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getSharedPreferences(name, mode);
        }
        return null;
    }

    @Override
    public SharedPreferences getSharedPreferences(File file, int mode) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getSharedPreferences(file, mode);
        }
        return null;
    }

    @Override
    public boolean moveSharedPreferencesFrom(Context sourceContext, String name) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.moveSharedPreferencesFrom(sourceContext, name);
        }
        return false;
    }

    @Override
    public boolean deleteSharedPreferences(String name) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.deleteSharedPreferences(name);
        }
        return false;
    }

    @Override
    public void reloadSharedPreferences() {
        Context context = mContextRef.get();
        if (context != null) {
            context.reloadSharedPreferences();
        }
    }

    @Override
    public FileInputStream openFileInput(String name) throws FileNotFoundException {
        Context context = mContextRef.get();
        if (context != null) {
            return context.openFileInput(name);
        }
        return null;
    }

    @Override
    public FileOutputStream openFileOutput(String name, int mode) throws FileNotFoundException {
        Context context = mContextRef.get();
        if (context != null) {
            return context.openFileOutput(name, mode);
        }
        return null;
    }

    @Override
    public boolean deleteFile(String name) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.deleteFile(name);
        }
        return false;
    }

    @Override
    public File getFileStreamPath(String name) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getFileStreamPath(name);
        }
        return null;
    }

    @Override
    public File getSharedPreferencesPath(String name) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getSharedPreferencesPath(name);
        }
        return null;
    }

    @Override
    public File getDataDir() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getDataDir();
        }
        return null;
    }

    @Override
    public File getFilesDir() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getFilesDir();
        }
        return null;
    }

    @Override
    public File getNoBackupFilesDir() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getNoBackupFilesDir();
        }
        return null;
    }

    @Override
    public File getExternalFilesDir(String type) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getExternalFilesDir(type);
        }
        return null;
    }

    @Override
    public File[] getExternalFilesDirs(String type) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getExternalFilesDirs(type);
        }
        return new File[0];
    }

    @Override
    public File getObbDir() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getObbDir();
        }
        return null;
    }

    @Override
    public File[] getObbDirs() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getObbDirs();
        }
        return new File[0];
    }

    @Override
    public File getCacheDir() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getCacheDir();
        }
        return null;
    }

    @Override
    public File getCodeCacheDir() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getCodeCacheDir();
        }
        return null;
    }

    @Override
    public File getExternalCacheDir() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getExternalCacheDir();
        }
        return null;
    }

    @Override
    public File getPreloadsFileCache() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getPreloadsFileCache();
        }
        return null;
    }

    @Override
    public File[] getExternalCacheDirs() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getExternalCacheDirs();
        }
        return new File[0];
    }

    @Override
    public File[] getExternalMediaDirs() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getExternalMediaDirs();
        }
        return new File[0];
    }

    @Override
    public String[] fileList() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.fileList();
        }
        return new String[0];
    }

    @Override
    public File getDir(String name, int mode) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getDir(name, mode);
        }
        return null;
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, CursorFactory factory) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.openOrCreateDatabase(name, mode, factory);
        }
        return null;
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, CursorFactory factory,
            DatabaseErrorHandler errorHandler) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.openOrCreateDatabase(name, mode, factory);
        }
        return null;
    }

    @Override
    public boolean moveDatabaseFrom(Context sourceContext, String name) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.moveDatabaseFrom(sourceContext, name);
        }
        return false;
    }

    @Override
    public boolean deleteDatabase(String name) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.deleteDatabase(name);
        }
        return false;
    }

    @Override
    public File getDatabasePath(String name) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getDatabasePath(name);
        }
        return null;
    }

    @Override
    public String[] databaseList() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.databaseList();
        }
        return new String[0];
    }

    @Override
    public Drawable getWallpaper() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getWallpaper();
        }
        return null;
    }

    @Override
    public Drawable peekWallpaper() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.peekWallpaper();
        }
        return null;
    }

    @Override
    public int getWallpaperDesiredMinimumWidth() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getWallpaperDesiredMinimumWidth();
        }
        return 0;
    }

    @Override
    public int getWallpaperDesiredMinimumHeight() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getWallpaperDesiredMinimumHeight();
        }
        return 0;
    }

    @Override
    public void setWallpaper(Bitmap bitmap) throws IOException {
        Context context = mContextRef.get();
        if (context != null) {
            context.setWallpaper(bitmap);
        }
    }

    @Override
    public void setWallpaper(InputStream data) throws IOException {
        Context context = mContextRef.get();
        if (context != null) {
            context.setWallpaper(data);
        }
    }

    @Override
    public void clearWallpaper() throws IOException {
        Context context = mContextRef.get();
        if (context != null) {
            context.clearWallpaper();
        }
    }

    @Override
    public void startActivity(Intent intent) {
        Context context = mContextRef.get();
        if (context != null) {
            context.startActivity(intent);
        }
    }

    @Override
    public void startActivity(Intent intent, Bundle options) {
        Context context = mContextRef.get();
        if (context != null) {
            context.startActivity(intent, options);
        }
    }

    @Override
    public void startActivities(Intent[] intents) {
        Context context = mContextRef.get();
        if (context != null) {
            context.startActivities(intents);
        }
    }

    @Override
    public void startActivities(Intent[] intents, Bundle options) {
        Context context = mContextRef.get();
        if (context != null) {
            context.startActivities(intents, options);
        }
    }

    @Override
    public void startIntentSender(IntentSender intent, Intent fillInIntent, int flagsMask,
            int flagsValues, int extraFlags) throws SendIntentException {
        Context context = mContextRef.get();
        if (context != null) {
            context.startIntentSender(intent, fillInIntent, flagsMask, flagsValues, extraFlags);
        }
    }

    @Override
    public void startIntentSender(IntentSender intent, Intent fillInIntent, int flagsMask,
            int flagsValues, int extraFlags, Bundle options) throws SendIntentException {
        Context context = mContextRef.get();
        if (context != null) {
            context.startIntentSender(intent, fillInIntent, flagsMask, flagsValues, extraFlags, options);
        }
    }

    @Override
    public void sendBroadcast(Intent intent) {
        Context context = mContextRef.get();
        if (context != null) {
            context.sendBroadcast(intent);
        }
    }

    @Override
    public void sendBroadcast(Intent intent, String receiverPermission) {
        Context context = mContextRef.get();
        if (context != null) {
            context.sendBroadcast(intent, receiverPermission);
        }
    }

    @Override
    public void sendBroadcastAsUserMultiplePermissions(Intent intent, UserHandle user,
            String[] receiverPermissions) {
        Context context = mContextRef.get();
        if (context != null) {
            context.sendBroadcastAsUserMultiplePermissions(intent, user, receiverPermissions);
        }
    }

    @Override
    public void sendBroadcast(Intent intent, String receiverPermission, Bundle options) {
        Context context = mContextRef.get();
        if (context != null) {
            context.sendBroadcast(intent, receiverPermission, options);
        }
    }

    @Override
    public void sendBroadcast(Intent intent, String receiverPermission, int appOp) {
        Context context = mContextRef.get();
        if (context != null) {
            context.sendBroadcast(intent, receiverPermission, appOp);
        }
    }

    @Override
    public void sendOrderedBroadcast(Intent intent, String receiverPermission) {
        Context context = mContextRef.get();
        if (context != null) {
            context.sendOrderedBroadcast(intent, receiverPermission);
        }
    }

    @Override
    public void sendOrderedBroadcast(Intent intent, String receiverPermission,
            BroadcastReceiver resultReceiver, Handler scheduler, int initialCode,
            String initialData, Bundle initialExtras) {
        Context context = mContextRef.get();
        if (context != null) {
            context.sendOrderedBroadcast(intent, receiverPermission, resultReceiver, scheduler, initialCode, initialData, initialExtras);
        }
    }

    @Override
    public void sendOrderedBroadcast(Intent intent, String receiverPermission, Bundle options,
            BroadcastReceiver resultReceiver, Handler scheduler, int initialCode,
            String initialData, Bundle initialExtras) {
        Context context = mContextRef.get();
        if (context != null) {
            context.sendOrderedBroadcast(intent, receiverPermission, options, resultReceiver, scheduler, initialCode, initialData, initialExtras);
        }
    }

    @Override
    public void sendOrderedBroadcast(Intent intent, String receiverPermission, int appOp,
            BroadcastReceiver resultReceiver, Handler scheduler, int initialCode,
            String initialData, Bundle initialExtras) {
        Context context = mContextRef.get();
        if (context != null) {
            context.sendOrderedBroadcast(intent, receiverPermission, appOp, resultReceiver, scheduler, initialCode, initialData, initialExtras);
        }
    }

    @Override
    public void sendBroadcastAsUser(Intent intent, UserHandle user) {
        Context context = mContextRef.get();
        if (context != null) {
            context.sendBroadcastAsUser(intent, user);
        }
    }

    @Override
    public void sendBroadcastAsUser(Intent intent, UserHandle user, String receiverPermission) {
        Context context = mContextRef.get();
        if (context != null) {
            context.sendBroadcastAsUser(intent, user, receiverPermission);
        }
    }

    @Override
    public void sendBroadcastAsUser(Intent intent, UserHandle user, String receiverPermission,
            Bundle options) {
        Context context = mContextRef.get();
        if (context != null) {
            context.sendBroadcastAsUser(intent, user, receiverPermission, options);
        }
    }

    @Override
    public void sendBroadcastAsUser(Intent intent, UserHandle user, String receiverPermission,
            int appOp) {
        Context context = mContextRef.get();
        if (context != null) {
            context.sendBroadcastAsUser(intent, user, receiverPermission, appOp);
        }
    }

    @Override
    public void sendOrderedBroadcastAsUser(Intent intent, UserHandle user,
            String receiverPermission, BroadcastReceiver resultReceiver, Handler scheduler,
            int initialCode, String initialData, Bundle initialExtras) {
        Context context = mContextRef.get();
        if (context != null) {
            context.sendOrderedBroadcastAsUser(intent, user, receiverPermission, resultReceiver, scheduler, initialCode, initialData, initialExtras);
        }
    }

    @Override
    public void sendOrderedBroadcastAsUser(Intent intent, UserHandle user,
            String receiverPermission, int appOp, BroadcastReceiver resultReceiver,
            Handler scheduler, int initialCode, String initialData, Bundle initialExtras) {
        Context context = mContextRef.get();
        if (context != null) {
            context.sendOrderedBroadcastAsUser(intent, user, receiverPermission, appOp, resultReceiver, scheduler, initialCode, initialData, initialExtras);
        }
    }

    @Override
    public void sendOrderedBroadcastAsUser(Intent intent, UserHandle user,
            String receiverPermission, int appOp, Bundle options, BroadcastReceiver resultReceiver,
            Handler scheduler, int initialCode, String initialData, Bundle initialExtras) {
        Context context = mContextRef.get();
        if (context != null) {
            context.sendOrderedBroadcastAsUser(intent, user, receiverPermission, appOp, options, resultReceiver, scheduler, initialCode, initialData, initialExtras);
        }
    }

    @Override
    public void sendStickyBroadcast(Intent intent) {
        Context context = mContextRef.get();
        if (context != null) {
            context.sendStickyBroadcast(intent);
        }
    }

    @Override
    public void sendStickyOrderedBroadcast(Intent intent, BroadcastReceiver resultReceiver,
            Handler scheduler, int initialCode, String initialData, Bundle initialExtras) {
        Context context = mContextRef.get();
        if (context != null) {
            context.sendStickyOrderedBroadcast(intent, resultReceiver, scheduler, initialCode, initialData, initialExtras);
        }
    }

    @Override
    public void removeStickyBroadcast(Intent intent) {
        Context context = mContextRef.get();
        if (context != null) {
            context.removeStickyBroadcast(intent);
        }
    }

    @Override
    public void sendStickyBroadcastAsUser(Intent intent, UserHandle user) {
        Context context = mContextRef.get();
        if (context != null) {
            context.sendStickyBroadcastAsUser(intent, user);
        }
    }

    @Override
    public void sendStickyBroadcastAsUser(Intent intent, UserHandle user, Bundle options) {
        Context context = mContextRef.get();
        if (context != null) {
            context.sendStickyBroadcastAsUser(intent, user, options);
        }
    }

    @Override
    public void sendStickyOrderedBroadcastAsUser(Intent intent, UserHandle user,
            BroadcastReceiver resultReceiver, Handler scheduler, int initialCode,
            String initialData, Bundle initialExtras) {
        Context context = mContextRef.get();
        if (context != null) {
            context.sendStickyOrderedBroadcastAsUser(intent, user, resultReceiver, scheduler, initialCode, initialData, initialExtras);
        }
    }

    @Override
    public void removeStickyBroadcastAsUser(Intent intent, UserHandle user) {
        Context context = mContextRef.get();
        if (context != null) {
            context.removeStickyBroadcastAsUser(intent, user);
        }
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.registerReceiver(receiver, filter);
        }
        return null;
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int flags) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.registerReceiver(receiver, filter, flags);
        }
        return null;
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter,
            String broadcastPermission, Handler scheduler) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.registerReceiver(receiver, filter, broadcastPermission, scheduler);
        }
        return null;
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter,
            String broadcastPermission, Handler scheduler, int flags) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.registerReceiver(receiver, filter, broadcastPermission, scheduler, flags);
        }
        return null;
    }

    @Override
    public Intent registerReceiverAsUser(BroadcastReceiver receiver, UserHandle user,
            IntentFilter filter, String broadcastPermission, Handler scheduler) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.registerReceiverAsUser(receiver, user, filter, broadcastPermission, scheduler);
        }
        return null;
    }

    @Override
    public Intent registerReceiverAsUser(BroadcastReceiver receiver, UserHandle user,
            IntentFilter filter, String broadcastPermission, Handler scheduler, int flags) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.registerReceiverAsUser(receiver, user, filter, broadcastPermission, scheduler, flags);
        }
        return null;
    }

    @Override
    public void unregisterReceiver(BroadcastReceiver receiver) {
        Context context = mContextRef.get();
        if (context != null) {
            context.unregisterReceiver(receiver);
        }
    }

    @Override
    public ComponentName startService(Intent service) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.startService(service);
        }
        return null;
    }

    @Override
    public ComponentName startForegroundService(Intent service) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.startForegroundService(service);
        }
        return null;
    }

    @Override
    public ComponentName startForegroundServiceAsUser(Intent service, UserHandle user) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.startForegroundServiceAsUser(service, user);
        }
        return null;
    }

    @Override
    public boolean stopService(Intent service) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.stopService(service);
        }
        return false;
    }

    @Override
    public ComponentName startServiceAsUser(Intent service, UserHandle user) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.startServiceAsUser(service, user);
        }
        return null;
    }

    @Override
    public boolean stopServiceAsUser(Intent service, UserHandle user) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.stopServiceAsUser(service, user);
        }
        return false;
    }

    @Override
    public boolean bindService(Intent service, ServiceConnection conn, int flags) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.bindService(service, conn, flags);
        }
        return false;
    }

    @Override
    public void unbindService(ServiceConnection conn) {
        Context context = mContextRef.get();
        if (context != null) {
            context.unbindService(conn);
        }
    }

    @Override
    public boolean startInstrumentation(ComponentName className, String profileFile,
            Bundle arguments) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.startInstrumentation(className, profileFile, arguments);
        }
        return false;
    }

    @Override
    public Object getSystemService(String name) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getSystemService(name);
        }
        return null;
    }

    @Override
    public String getSystemServiceName(Class<?> serviceClass) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getSystemServiceName(serviceClass);
        }
        return null;
    }

    @Override
    public int checkPermission(String permission, int pid, int uid) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.checkPermission(permission, pid, uid);
        }
        return 0;
    }

    @Override
    public int checkPermission(String permission, int pid, int uid, IBinder callerToken) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.checkPermission(permission, pid, uid, callerToken);
        }
        return 0;
    }

    @Override
    public int checkCallingPermission(String permission) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.checkCallingPermission(permission);
        }
        return 0;
    }

    @Override
    public int checkCallingOrSelfPermission(String permission) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.checkCallingOrSelfPermission(permission);
        }
        return 0;
    }

    @Override
    public int checkSelfPermission(String permission) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.checkSelfPermission(permission);
        }
        return 0;
    }

    @Override
    public void enforcePermission(String permission, int pid, int uid, String message) {
        Context context = mContextRef.get();
        if (context != null) {
            context.enforcePermission(permission, pid, uid, message);
        }
    }

    @Override
    public void enforceCallingPermission(String permission, String message) {
        Context context = mContextRef.get();
        if (context != null) {
            context.enforceCallingPermission(permission, message);
        }
    }

    @Override
    public void enforceCallingOrSelfPermission(String permission, String message) {
        Context context = mContextRef.get();
        if (context != null) {
            context.enforceCallingOrSelfPermission(permission, message);
        }
    }

    @Override
    public void grantUriPermission(String toPackage, Uri uri, int modeFlags) {
        Context context = mContextRef.get();
        if (context != null) {
            context.grantUriPermission(toPackage, uri, modeFlags);
        }
    }

    @Override
    public void revokeUriPermission(Uri uri, int modeFlags) {
        Context context = mContextRef.get();
        if (context != null) {
            context.revokeUriPermission(uri, modeFlags);
        }
    }

    @Override
    public void revokeUriPermission(String toPackage, Uri uri, int modeFlags) {
        Context context = mContextRef.get();
        if (context != null) {
            context.revokeUriPermission(toPackage, uri, modeFlags);
        }
    }

    @Override
    public int checkUriPermission(Uri uri, int pid, int uid, int modeFlags) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.checkUriPermission(uri, pid, uid, modeFlags);
        }
        return 0;
    }

    @Override
    public int checkUriPermission(Uri uri, int pid, int uid, int modeFlags, IBinder callerToken) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.checkUriPermission(uri, pid, uid, modeFlags, callerToken);
        }
        return 0;
    }

    @Override
    public int checkCallingUriPermission(Uri uri, int modeFlags) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.checkCallingUriPermission(uri, modeFlags);
        }
        return 0;
    }

    @Override
    public int checkCallingOrSelfUriPermission(Uri uri, int modeFlags) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.checkCallingOrSelfUriPermission(uri, modeFlags);
        }
        return 0;
    }

    @Override
    public int checkUriPermission(Uri uri, String readPermission, String writePermission, int pid,
            int uid, int modeFlags) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.checkUriPermission(uri, readPermission, writePermission, pid, uid, modeFlags);
        }
        return 0;
    }

    @Override
    public void enforceUriPermission(Uri uri, int pid, int uid, int modeFlags, String message) {
        Context context = mContextRef.get();
        if (context != null) {
            context.enforceUriPermission(uri, pid, uid, modeFlags, message);
        }
    }

    @Override
    public void enforceCallingUriPermission(Uri uri, int modeFlags, String message) {
        Context context = mContextRef.get();
        if (context != null) {
            context.enforceCallingUriPermission(uri, modeFlags, message);
        }
    }

    @Override
    public void enforceCallingOrSelfUriPermission(Uri uri, int modeFlags, String message) {
        Context context = mContextRef.get();
        if (context != null) {
            context.enforceCallingOrSelfUriPermission(uri, modeFlags, message);
        }
    }

    @Override
    public void enforceUriPermission(Uri uri, String readPermission, String writePermission,
            int pid, int uid, int modeFlags, String message) {
        Context context = mContextRef.get();
        if (context != null) {
            context.enforceUriPermission(uri, readPermission, writePermission, pid, uid, modeFlags, message);
        }
    }

    @Override
    public Context createPackageContext(String packageName, int flags)
            throws NameNotFoundException {
        Context context = mContextRef.get();
        if (context != null) {
            return context.createPackageContext(packageName, flags);
        }
        return null;
    }

    @Override
    public Context createApplicationContext(ApplicationInfo application, int flags)
            throws NameNotFoundException {
        Context context = mContextRef.get();
        if (context != null) {
            return context.createApplicationContext(application, flags);
        }
        return null;
    }

    @Override
    public Context createContextForSplit(String splitName) throws NameNotFoundException {
        Context context = mContextRef.get();
        if (context != null) {
            return context.createContextForSplit(splitName);
        }
        return null;
    }

    @Override
    public Context createConfigurationContext(Configuration overrideConfiguration) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.createConfigurationContext(overrideConfiguration);
        }
        return null;
    }

    @Override
    public Context createDisplayContext(Display display) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.createDisplayContext(display);
        }
        return null;
    }

    @Override
    public Context createDeviceProtectedStorageContext() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.createDeviceProtectedStorageContext();
        }
        return null;
    }

    @Override
    public Context createCredentialProtectedStorageContext() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.createCredentialProtectedStorageContext();
        }
        return null;
    }

    @Override
    public DisplayAdjustments getDisplayAdjustments(int displayId) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getDisplayAdjustments(displayId);
        }
        return null;
    }

    @Override
    public int getDisplayId() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getDisplayId();
        }
        return 0;
    }

    @Override
    public void updateDisplay(int displayId) {
        Context context = mContextRef.get();
        if (context != null) {
            context.updateDisplay(displayId);
        }
    }

    @Override
    public boolean isDeviceProtectedStorage() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.isDeviceProtectedStorage();
        }
        return false;
    }

    @Override
    public boolean isCredentialProtectedStorage() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.isCredentialProtectedStorage();
        }
        return false;
    }

    @Override
    public boolean canLoadUnsafeResources() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.canLoadUnsafeResources();
        }
        return false;
    }

    @Override
    public String getOpPackageName() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getOpPackageName();
        }
        return null;
    }

    @Override
    public boolean bindService(Intent arg0, int arg1, Executor arg2, ServiceConnection arg3) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.bindService(arg0, arg1, arg2, arg3);
        }
        return false;
    }

    @Override
    public boolean bindIsolatedService(Intent arg0,
            int arg1, String arg2, Executor arg3, ServiceConnection arg4) {
        Context context = mContextRef.get();
        if (context != null) {
            return context.bindIsolatedService(arg0, arg1, arg2, arg3, arg4);
        }
        return false;
    }

    @Override
    public Context createPackageContextAsUser(String arg0, int arg1, UserHandle user)
            throws NameNotFoundException {
        Context context = mContextRef.get();
        if (context != null) {
            return context.createPackageContextAsUser(arg0, arg1, user);
        }
        return null;
    }

    @Override
    public void sendBroadcastMultiplePermissions(Intent intent, String[] receiverPermissions) {
        Context context = mContextRef.get();
        if (context != null) {
            context.sendBroadcastMultiplePermissions(intent, receiverPermissions);
        }
    }

    @Override
    public void updateServiceGroup(@NonNull ServiceConnection conn, int group,
            int importance) {
        Context context = mContextRef.get();
        if (context != null) {
            context.updateServiceGroup(conn, group, importance);
        }
    }

    @Override
    public Display getDisplay() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.getDisplay();
        }
        return null;
    }

    @Override
    public boolean isUiContext() {
        Context context = mContextRef.get();
        if (context != null) {
            return context.isUiContext();
        }
        return true;
    }

    @Override
    public void registerComponentCallbacks(ComponentCallbacks callback) {
        Context context = mContextRef.get();
        if (context != null) {
            context.registerComponentCallbacks(callback);
        }
    }

    @Override
    public void unregisterComponentCallbacks(ComponentCallbacks callback) {
        Context context = mContextRef.get();
        if (context != null) {
            context.unregisterComponentCallbacks(callback);
        }
    }
}
