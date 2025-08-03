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

package android.telephony.mockmodem;

import android.content.Context;
import android.hardware.radio.network.CellConnectionStatus;
import android.hardware.radio.network.CellIdentityLte;
import android.hardware.radio.network.CellIdentityWcdma;
import android.hardware.radio.network.CellInfo;
import android.hardware.radio.network.CellInfoLte;
import android.hardware.radio.network.CellInfoRatSpecificInfo;
import android.hardware.radio.network.CellInfoWcdma;
import android.hardware.radio.network.LteSignalStrength;
import android.hardware.radio.network.OperatorInfo;
import android.hardware.radio.network.WcdmaSignalStrength;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class MockNetworkConfig {
    private static final String TAG = "MockNetworkConfig";

    private static final String MOCK_NETWORK_TAG = "MockNetwork";
    private static final String MOCK_NETWORK_PROFILE_TAG = "MockNetworkProfile";
    private static final String MOCK_CELL_PROPERTY_TAG = "MockCellProperty";
    private static final String MOCK_CELL_IDENTITY_TAG = "MockCellIdentity";
    private static final String MOCK_CELL_SIGNAL_STRENGTH_TAG = "MockCellSignalStrength";

    private final Context mContext;
    private MockCellProperty mMockCellProperty;
    private ArrayList<MockNetworkProfile> mMockNetworkProfiles =
            new ArrayList<MockNetworkProfile>();

    private int mCarrierId;

    private static class MockCellProperty {
        private String[] mEHPlmnList;
        private String[] mAllowRoamingList;

        MockCellProperty(XmlPullParser parser) {
            try {
                loadCellProperty(parser);
            } catch (Exception e) {
                Log.e(TAG, "Failed to loadCellProperty: " + e);
            }
        }

        private void loadCellProperty(XmlPullParser parser)
                throws IOException, XmlPullParserException {
            int outerDepth = parser.getDepth();
            int type;

            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                String parameter = parser.getName();
                if (parameter == null || type == XmlPullParser.END_TAG) {
                    continue;
                }

                switch (parameter) {
                    case "EHPLMNLIST":
                        mEHPlmnList = parser.nextText().replace(" ", "").split(",");
                        break;
                    case "AllowRoamingList":
                        mAllowRoamingList = parser.nextText().replace(" ", "").split(",");
                        break;
                    default:
                        Log.d(TAG, "Cell property: " + parameter + " Not Support");
                        break;
                }
            }
        }

        public String[] getEHPlmnList() {
            return mEHPlmnList;
        }

        public String[] getAllowRoamingList() {
            return mAllowRoamingList;
        }
    }

    private static class MockNetworkProfile {
        private int mId;
        private String mRat;
        private CellInfo mCell;

        MockNetworkProfile(XmlPullParser parser) {
            mId = Integer.parseInt(parser.getAttributeValue(null, "id").trim());
            Log.d(TAG, "Load: " + mId);
            mRat = parser.getAttributeValue(null, "rat").trim();
            String connectionStatus = parser.getAttributeValue(null, "connection");

            mCell = new CellInfo();
            mCell.registered = false;

            if (connectionStatus != null && connectionStatus.trim().equals("primary")) {
                mCell.connectionStatus = CellConnectionStatus.PRIMARY_SERVING;
            } else {
                mCell.connectionStatus = CellConnectionStatus.SECONDARY_SERVING;
            }
            mCell.ratSpecificInfo = new CellInfoRatSpecificInfo();

            loadNetworkCellParameters(parser);
        }

        private void loadLteCellIdentity(XmlPullParser parser, CellInfoLte lte)
                throws IOException, XmlPullParserException {
            int outerDepth = parser.getDepth();
            int type;

            lte.cellIdentityLte = new CellIdentityLte();
            lte.cellIdentityLte.operatorNames = new OperatorInfo();
            lte.cellIdentityLte.additionalPlmns = new String[0];
            lte.cellIdentityLte.bands = new int[0];

            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                String parameter = parser.getName();
                if (parameter == null || type == XmlPullParser.END_TAG) {
                    continue;
                }

                switch (parameter) {
                    case "MCC":
                        lte.cellIdentityLte.mcc = parser.nextText().trim();
                        break;
                    case "MNC":
                        lte.cellIdentityLte.mnc = parser.nextText().trim();
                        break;
                    case "CI":
                        lte.cellIdentityLte.ci = Integer.parseInt(parser.nextText().trim());
                        break;
                    case "PCI":
                        lte.cellIdentityLte.pci = Integer.parseInt(parser.nextText().trim());
                        break;
                    case "TAC":
                        lte.cellIdentityLte.tac = Integer.parseInt(parser.nextText().trim());
                        break;
                    case "EARFCN":
                        lte.cellIdentityLte.earfcn = Integer.parseInt(parser.nextText().trim());
                        break;
                    case "OperatorInfo":
                        // lte.cellIdentityLte.operatorNames = new OperatorInfo();
                        break;
                    case "AlphaLong":
                        lte.cellIdentityLte.operatorNames.alphaLong = parser.nextText().trim();
                        break;
                    case "AlphaShort":
                        lte.cellIdentityLte.operatorNames.alphaShort = parser.nextText().trim();
                        break;
                    case "OperatorNumeric":
                        lte.cellIdentityLte.operatorNames.operatorNumeric =
                                parser.nextText().trim();
                        break;
                    default:
                        Log.d(TAG, "LTE Cell Identity: " + parameter + " Not Support");
                        break;
                }
            }
        }

        private void loadLteSignalStrength(XmlPullParser parser, CellInfoLte lte)
                throws IOException, XmlPullParserException {
            int outerDepth = parser.getDepth();
            int type;

            lte.signalStrengthLte = new LteSignalStrength();
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                String parameter = parser.getName();
                if (parameter == null || type == XmlPullParser.END_TAG) {
                    continue;
                }

                switch (parameter) {
                    case "SignalStrength":
                        lte.signalStrengthLte.signalStrength =
                                Integer.parseInt(parser.nextText().trim());
                        break;
                    case "RSRP":
                        lte.signalStrengthLte.rsrp = Integer.parseInt(parser.nextText().trim());
                        break;
                    case "RSRQ":
                        lte.signalStrengthLte.rsrq = Integer.parseInt(parser.nextText().trim());
                        break;
                    case "RSSNR":
                        lte.signalStrengthLte.rssnr = Integer.parseInt(parser.nextText().trim());
                        break;
                    case "CQI":
                        lte.signalStrengthLte.cqi = Integer.parseInt(parser.nextText().trim());
                        break;
                    case "TimingAdvance":
                        lte.signalStrengthLte.timingAdvance =
                                Integer.parseInt(parser.nextText().trim());
                        break;
                    case "CqiTableIndex":
                        lte.signalStrengthLte.cqiTableIndex =
                                Integer.parseInt(parser.nextText().trim());
                        break;
                    default:
                        Log.d(TAG, "LTE Cell Signal: " + parameter + " Not Support");
                        break;
                }
            }
        }

        private void loadLteCellInfo(XmlPullParser parser, CellInfoLte lte)
                throws IOException, XmlPullParserException {
            int outerDepth = parser.getDepth();
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                String name = parser.getName();
                if (name == null) {
                    continue;
                }

                if (MOCK_CELL_IDENTITY_TAG.equals(name)) {
                    loadLteCellIdentity(parser, lte);
                    Log.d(TAG, "LTE Cell ID: " + lte.cellIdentityLte.toString());
                } else if (MOCK_CELL_SIGNAL_STRENGTH_TAG.equals(name)) {
                    loadLteSignalStrength(parser, lte);
                    Log.d(TAG, "LTE Cell Signal: " + lte.signalStrengthLte.toString());
                }
            }
        }

        private void loadWcdmaCellIdentity(XmlPullParser parser, CellInfoWcdma wcdma)
                throws IOException, XmlPullParserException {
            int outerDepth = parser.getDepth();
            int type;

            wcdma.cellIdentityWcdma = new CellIdentityWcdma();
            wcdma.cellIdentityWcdma.operatorNames = new OperatorInfo();
            wcdma.cellIdentityWcdma.additionalPlmns = new String[0];

            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                String parameter = parser.getName();
                if (parameter == null || type == XmlPullParser.END_TAG) {
                    continue;
                }

                switch (parameter) {
                    case "MCC":
                        wcdma.cellIdentityWcdma.mcc = parser.nextText().trim();
                        break;
                    case "MNC":
                        wcdma.cellIdentityWcdma.mnc = parser.nextText().trim();
                        break;
                    case "LAC":
                        wcdma.cellIdentityWcdma.lac = Integer.parseInt(parser.nextText().trim());
                        break;
                    case "CID":
                        wcdma.cellIdentityWcdma.cid = Integer.parseInt(parser.nextText().trim());
                        break;
                    case "PSC":
                        wcdma.cellIdentityWcdma.psc = Integer.parseInt(parser.nextText().trim());
                        break;
                    case "UARFCN":
                        wcdma.cellIdentityWcdma.uarfcn = Integer.parseInt(parser.nextText().trim());
                        break;
                    case "OperatorInfo":
                        // lte.cellIdentityLte.operatorNames = new OperatorInfo();
                        break;
                    case "AlphaLong":
                        wcdma.cellIdentityWcdma.operatorNames.alphaLong = parser.nextText().trim();
                        break;
                    case "AlphaShort":
                        wcdma.cellIdentityWcdma.operatorNames.alphaShort = parser.nextText().trim();
                        break;
                    case "OperatorNumeric":
                        wcdma.cellIdentityWcdma.operatorNames.operatorNumeric =
                                parser.nextText().trim();
                        break;
                    default:
                        Log.d(TAG, "WCDMA Cell Identity: " + parameter + " Not Support");
                        break;
                }
            }
        }

        private void loadWcdmaSignalStrength(XmlPullParser parser, CellInfoWcdma wcdma)
                throws IOException, XmlPullParserException {
            int outerDepth = parser.getDepth();
            int type;

            wcdma.signalStrengthWcdma = new WcdmaSignalStrength();
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                String parameter = parser.getName();
                if (parameter == null || type == XmlPullParser.END_TAG) {
                    continue;
                }

                switch (parameter) {
                    case "SignalStrength":
                        wcdma.signalStrengthWcdma.signalStrength =
                                Integer.parseInt(parser.nextText().trim());
                        break;
                    case "BitErrorRate":
                        wcdma.signalStrengthWcdma.bitErrorRate =
                                Integer.parseInt(parser.nextText().trim());
                        break;
                    case "RSCP":
                        wcdma.signalStrengthWcdma.rscp = Integer.parseInt(parser.nextText().trim());
                        break;
                    case "ECNO":
                        wcdma.signalStrengthWcdma.ecno = Integer.parseInt(parser.nextText().trim());
                        break;
                    default:
                        Log.d(TAG, "WCDMA Cell Signal: " + parameter + " Not Support");
                        break;
                }
            }
        }

        private void loadWcdmaCellInfo(XmlPullParser parser, CellInfoWcdma wcdma)
                throws IOException, XmlPullParserException {
            int outerDepth = parser.getDepth();
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                String name = parser.getName();
                if (name == null) {
                    continue;
                }

                if (MOCK_CELL_IDENTITY_TAG.equals(name)) {
                    loadWcdmaCellIdentity(parser, wcdma);
                    Log.d(TAG, "WCDMA Cell ID: " + wcdma.cellIdentityWcdma.toString());
                } else if (MOCK_CELL_SIGNAL_STRENGTH_TAG.equals(name)) {
                    loadWcdmaSignalStrength(parser, wcdma);
                    Log.d(TAG, "WCDMA Cell Signal: " + wcdma.signalStrengthWcdma.toString());
                }
            }
        }

        private void loadNetworkCellParameters(XmlPullParser parser) {
            try {
                switch (mRat) {
                    case "LTE":
                        CellInfoLte lte = new CellInfoLte();
                        loadLteCellInfo(parser, lte);
                        mCell.ratSpecificInfo.setLte(lte);
                        break;
                    case "WCDMA":
                        CellInfoWcdma wcdma = new CellInfoWcdma();
                        loadWcdmaCellInfo(parser, wcdma);
                        mCell.ratSpecificInfo.setWcdma(wcdma);
                        break;
                    default:
                        Log.e(TAG, "RAT " + mRat + " Cell Parameter Not Support");
                        break;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to loadNetworkCellParameters: " + e);
            }
        }

        public CellInfo getCell() {
            return mCell;
        }
    }

    public MockNetworkConfig(Context context) {
        mContext = context;
    }

    private void readConfigFromXml(XmlPullParser parser)
            throws IOException, XmlPullParserException {
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
            String name = parser.getName();
            switch (type) {
                case XmlPullParser.START_TAG:
                    if (MOCK_NETWORK_TAG.equals(name)) {
                        mCarrierId =
                                Integer.parseInt(
                                        parser.getAttributeValue(null, "carrierid").trim());
                    } else if (MOCK_NETWORK_PROFILE_TAG.equals(name)) {
                        mMockNetworkProfiles.add(new MockNetworkProfile(parser));
                    } else if (MOCK_CELL_PROPERTY_TAG.equals(name)) {
                        mMockCellProperty = new MockCellProperty(parser);
                    } else {
                        Log.e(TAG, "Type " + name + " Not Support.");
                    }
                    break;
                case XmlPullParser.END_TAG:
                    break;
            }
        }
    }

    public void getConfigFromAssets(String fileName) {
        try {
            XmlPullParser parser = Xml.newPullParser();
            InputStream input = mContext.getAssets().open(fileName);
            parser.setInput(input, "utf-8");
            readConfigFromXml(parser);
            input.close();
        } catch (Exception e) {
            Log.e(TAG, "Failed to read config: " + e);
        }
    }

    public int getCarrierId() {
        return mCarrierId;
    }

    public String[] getEHPlmnList() {
        return mMockCellProperty.getEHPlmnList();
    }

    public String[] getAllowRoamingList() {
        return mMockCellProperty.getAllowRoamingList();
    }

    public int getCellNum() {
        return mMockNetworkProfiles.size();
    }

    public CellInfo getCellInfo(int index) {
        if (index > getCellNum()) {
            return null;
        }
        return mMockNetworkProfiles.get(index).getCell();
    }
}
