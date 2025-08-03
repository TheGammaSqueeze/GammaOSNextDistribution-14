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

package com.android.phone;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;
import android.telephony.ims.ProvisioningManager;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Provides a function to set/get Ims feature provisioning status in storage.
 */
public class ImsProvisioningLoader {
    private static final String LOG_TAG = ImsProvisioningLoader.class.getSimpleName();

    public static final int STATUS_NOT_SET = -1;
    public static final int STATUS_NOT_PROVISIONED =
            ProvisioningManager.PROVISIONING_VALUE_DISABLED;
    public static final int STATUS_PROVISIONED =
            ProvisioningManager.PROVISIONING_VALUE_ENABLED;

    public static final int IMS_FEATURE_MMTEL = ImsFeature.FEATURE_MMTEL;
    public static final int IMS_FEATURE_RCS = ImsFeature.FEATURE_RCS;

    private static final String PROVISIONING_FILE_NAME_PREF = "imsprovisioningstatus_";
    private static final String PREF_PROVISION_IMS_MMTEL_PREFIX = "provision_ims_mmtel_";

    private Context mContext;
    private SharedPreferences mTelephonySharedPreferences;
    // key : sub Id, value : read from sub Id's xml and it's in-memory cache
    private SparseArray<PersistableBundle> mSubIdBundleArray = new SparseArray<>();
    private final Object mLock = new Object();

    public ImsProvisioningLoader(Context context) {
        mContext = context;
        mTelephonySharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * Get Ims feature provisioned status in storage
     */
    public int getProvisioningStatus(int subId, @ImsFeature.FeatureType int imsFeature,
            int capability, @ImsRegistrationImplBase.ImsRegistrationTech int tech) {
        initCache(subId);
        return getImsProvisioningStatus(subId, imsFeature, tech,
                capability);
    }

    /**
     * Set Ims feature provisioned status in storage
     */
    public boolean setProvisioningStatus(int subId, @ImsFeature.FeatureType int imsFeature,
            int capability, @ImsRegistrationImplBase.ImsRegistrationTech int tech,
            boolean isProvisioned) {
        initCache(subId);
        return setImsFeatureProvisioning(subId, imsFeature, tech, capability,
                isProvisioned);
    }

    private boolean isFileExist(int subId) {
        File file = new File(mContext.getFilesDir(), getFileName(subId));
        return file.exists();
    }

    private void initCache(int subId) {
        synchronized (mLock) {
            PersistableBundle subIdBundle = mSubIdBundleArray.get(subId, null);
            if (subIdBundle != null) {
                // initCache() has already been called for the subId
                return;
            }
            if (isFileExist(subId)) {
                subIdBundle = readSubIdBundleFromXml(subId);
            } else {
                // It should read the MMTEL capability cache as part of shared prefs and migrate
                // over any configs for UT.
                final int[] regTech = {ImsRegistrationImplBase.REGISTRATION_TECH_LTE,
                        ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN,
                        ImsRegistrationImplBase.REGISTRATION_TECH_CROSS_SIM,
                        ImsRegistrationImplBase.REGISTRATION_TECH_NR};
                subIdBundle = new PersistableBundle();
                for (int tech : regTech) {
                    int UtProvisioningStatus = getUTProvisioningStatus(subId, tech);
                    logd("check UT provisioning status " + UtProvisioningStatus);

                    if (STATUS_PROVISIONED == UtProvisioningStatus) {
                        setProvisioningStatusToSubIdBundle(ImsFeature.FEATURE_MMTEL, tech,
                                MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_UT, subIdBundle,
                                UtProvisioningStatus);
                    }
                }
                saveSubIdBundleToXml(subId, subIdBundle);
            }
            mSubIdBundleArray.put(subId, subIdBundle);
        }
    }

    private int getImsProvisioningStatus(int subId, int imsFeature, int tech, int capability) {
        PersistableBundle subIdBundle = null;
        synchronized (mLock) {
            subIdBundle = mSubIdBundleArray.get(subId, null);
        }

        return getProvisioningStatusFromSubIdBundle(imsFeature, tech,
                capability, subIdBundle);
    }

    private boolean setImsFeatureProvisioning(int subId, int imsFeature, int tech, int capability,
            boolean isProvisioned) {
        synchronized (mLock) {
            int preValue = getImsProvisioningStatus(subId, imsFeature, tech, capability);
            int newValue = isProvisioned ? STATUS_PROVISIONED : STATUS_NOT_PROVISIONED;
            if (preValue == newValue) {
                logd("already stored provisioning status " + isProvisioned + " ImsFeature "
                        + imsFeature + " tech " + tech + " capa " + capability);
                return false;
            }

            PersistableBundle subIdBundle = mSubIdBundleArray.get(subId, null);
            setProvisioningStatusToSubIdBundle(imsFeature, tech, capability, subIdBundle,
                    newValue);
            saveSubIdBundleToXml(subId, subIdBundle);
        }
        return true;
    }

    private int getProvisioningStatusFromSubIdBundle(int imsFeature, int tech,
            int capability, PersistableBundle subIdBundle) {
        // If it doesn't exist in xml, return STATUS_NOT_SET
        if (subIdBundle == null || subIdBundle.isEmpty()) {
            logd("xml is empty");
            return STATUS_NOT_SET;
        }

        PersistableBundle regTechBundle = subIdBundle.getPersistableBundle(
                String.valueOf(imsFeature));
        if (regTechBundle == null) {
            logd("ImsFeature " + imsFeature + " is not exist in xml");
            return STATUS_NOT_SET;
        }

        PersistableBundle capabilityBundle = regTechBundle.getPersistableBundle(
                String.valueOf(tech));
        if (capabilityBundle == null) {
            logd("RegistrationTech " + tech + " is not exist in xml");
            return STATUS_NOT_SET;
        }

        return getIntValueFromBundle(String.valueOf(capability), capabilityBundle);
    }

    private void setProvisioningStatusToSubIdBundle(int imsFeature, int tech,
            int capability, PersistableBundle subIdBundle, int newStatus) {
        logd("set provisioning status " + newStatus + " ImsFeature "
                + imsFeature + " tech " + tech + " capa " + capability);

        PersistableBundle regTechBundle = subIdBundle.getPersistableBundle(
                String.valueOf(imsFeature));
        if (regTechBundle == null) {
            regTechBundle = new PersistableBundle();
            subIdBundle.putPersistableBundle(String.valueOf(imsFeature), regTechBundle);
        }

        PersistableBundle capabilityBundle = regTechBundle.getPersistableBundle(
                String.valueOf(tech));
        if (capabilityBundle == null) {
            capabilityBundle = new PersistableBundle();
            regTechBundle.putPersistableBundle(String.valueOf(tech), capabilityBundle);
        }

        capabilityBundle.putInt(String.valueOf(capability), newStatus);
    }

    // Default value is STATUS_NOT_SET
    private int getIntValueFromBundle(String key, PersistableBundle bundle) {
        int value = bundle.getInt(key, STATUS_NOT_SET);
        logd("get value " + value);
        return value;
    }

    // Return subIdBundle from imsprovisioningstatus_{subId}.xml
    private PersistableBundle readSubIdBundleFromXml(int subId) {
        String fileName = getFileName(subId);

        PersistableBundle subIdBundles = new PersistableBundle();
        File file = null;
        FileInputStream inFile = null;
        synchronized (mLock) {
            try {
                file = new File(mContext.getFilesDir(), fileName);
                inFile = new FileInputStream(file);
                subIdBundles = PersistableBundle.readFromStream(inFile);
                inFile.close();
            } catch (FileNotFoundException e) {
                logd(e.toString());
            } catch (IOException e) {
                loge(e.toString());
            } catch (RuntimeException e) {
                loge(e.toString());
            }
        }

        return subIdBundles;
    }

    private void saveSubIdBundleToXml(int subId, PersistableBundle subIdBundle) {
        String fileName = getFileName(subId);

        if (subIdBundle == null || subIdBundle.isEmpty()) {
            logd("subIdBundle is empty");
            return;
        }

        FileOutputStream outFile = null;
        synchronized (mLock) {
            try {
                outFile = new FileOutputStream(new File(mContext.getFilesDir(), fileName));
                subIdBundle.writeToStream(outFile);
                outFile.flush();
                outFile.close();
            } catch (IOException e) {
                loge(e.toString());
            } catch (RuntimeException e) {
                loge(e.toString());
            }
        }
    }

    private int getUTProvisioningStatus(int subId, int tech) {
        return getMmTelCapabilityProvisioningBitfield(subId, tech) > 0 ? STATUS_PROVISIONED
                : STATUS_NOT_SET;
    }

    /**
     * @return the bitfield containing the MmTel provisioning for the provided subscription and
     * technology. The bitfield should mirror the bitfield defined by
     * {@link MmTelFeature.MmTelCapabilities.MmTelCapability}.
     */
    private int getMmTelCapabilityProvisioningBitfield(int subId, int tech) {
        String key = getMmTelProvisioningKey(subId, tech);
        // Default is no capabilities are provisioned.
        return mTelephonySharedPreferences.getInt(key, 0 /*default*/);
    }

    private String getMmTelProvisioningKey(int subId, int tech) {
        // Resulting key is provision_ims_mmtel_{subId}_{tech}
        return PREF_PROVISION_IMS_MMTEL_PREFIX + subId + "_" + tech;
    }

    private String getFileName(int subId) {
        // Resulting name is imsprovisioningstatus_{subId}.xml
        return PROVISIONING_FILE_NAME_PREF + subId + ".xml";
    }

    @VisibleForTesting
    void clear() {
        synchronized (mLock) {
            mSubIdBundleArray.clear();
        }
    }

    @VisibleForTesting
    void setProvisioningToXml(int subId, PersistableBundle subIdBundle,
            String[] infoArray) {
        for (String info : infoArray) {
            String[] paramArray = info.split(",");
            setProvisioningStatusToSubIdBundle(Integer.valueOf(paramArray[0]),
                    Integer.valueOf(paramArray[1]), Integer.valueOf(paramArray[2]),
                    subIdBundle, Integer.valueOf(paramArray[3]));
        }
        saveSubIdBundleToXml(subId, subIdBundle);
    }

    private void loge(String contents) {
        Log.e(LOG_TAG, contents);
    }

    private void logd(String contents) {
        Log.d(LOG_TAG, contents);
    }

}
