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

package com.android.car.telemetry.publisher.statsconverters;

import static com.android.car.telemetry.publisher.Constants.STATS_BUNDLE_KEY_PREFIX;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.PersistableBundle;
import android.util.SparseArray;

import com.android.car.telemetry.AtomsProto.Atom;
import com.android.car.telemetry.StatsLogProto.DimensionsValue;

import com.google.protobuf.MessageLite;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base class for converters from StatsD atom list to {@link PersistableBundle}. PersistableBundle
 * will be sent to {@code ScriptExecutor} to be consumed by scripts, thus its structure is simple
 * and lightweight.
 *
 * <p> Resulting fields in the PersistableBundle will be arrays of primitive values: int, long,
 * double, boolean and String. The keys to the arrays will be the same as atom field names in the
 * proto definitions.
 *
 * <p> Example resulting PersistableBundle format:
 *
 * {
 *   "uid": [1000, 10000, 11000],
 *   "process_name": ["A", "B", "C"],
 *   "rss_in_bytes": [11111L, 222222L, 3333333L],
 *   ...
 * }
 *
 * @param <T> the atom data type.
 */
public abstract class AbstractAtomConverter<T extends MessageLite> {
    AbstractAtomConverter() {}

    /**
     * Gets the parser config that's a mapping of the field id to {@link AtomFieldAccessor} for atom
     * data of type T.
     *
     * @return the atom fields parser config.
     */
    @NonNull
    abstract SparseArray<AtomFieldAccessor<T, ?>> getAtomFieldAccessorMap();

    /**
     * Gets atom data of type T from atom proto.
     *
     * @param atom the proto that contains the atom data.
     * @return atom data.
     * @throws IllegalArgumentException if the atom doesn't contain data.
     */
    @NonNull
    abstract T getAtomData(@NonNull Atom atom);

    /**
     * Gets the name of the atom data class as string.
     *
     * @return atom data class name string.
     */
    @NonNull
    abstract String getAtomDataClassName();

    /**
     * Converts the atom fields to be set as fields in the returned {@link PersistableBundle}.
     *
     * <p> Atom list is parsed into field value arrays.
     *
     * <p> Dimension values are extracted, dehashed if necessary, and parsed into arrays.
     *
     * <p> The resulting primitive arrays are put into the returned {@link PersistableBundle}
     * with the atom field names as keys.
     *
     * @param atoms list of atoms with data type T to be converted to PersistableBundle formats.
     * @param dimensionsFieldsIds list of ids for the atom fields that are encoded in dimensions.
     * @param dimensionsValuesList dimension value groups matching mDimensionsFieldsIds.
     * @param hashToStringMap hash mapping used to de-hash hash string type dimension values.
     * @return {@link PersistableBundle} with the converted atom fields arrays.
     * @throws StatsConversionException if atom field mismatch or can't convert dimension value.
     */
    @NonNull
    PersistableBundle convert(
            @NonNull List<Atom> atoms,
            @Nullable List<Integer> dimensionsFieldsIds,
            @Nullable List<List<DimensionsValue>> dimensionsValuesList,
            @Nullable Map<Long, String> hashToStringMap) throws StatsConversionException {
        PersistableBundle bundle = new PersistableBundle();
        SparseArray<AtomFieldAccessor<T, ?>> parserConfig = getAtomFieldAccessorMap();
        // For each field, if set, add the values from all atoms to list and convert
        for (int i = 0; i < parserConfig.size(); ++i) {
            AtomFieldAccessor<T, ?> atomFieldAccessor = parserConfig.valueAt(i);
            // All atoms are expected to have the same fields set
            // If the first atom does not have a field, that field is skipped
            if (atomFieldAccessor.hasField(getAtomData(atoms.get(0)))) {
                List<Object> valueList = new ArrayList<>(atoms.size());
                for (Atom atom : atoms) {
                    T atomData = getAtomData(atom);
                    if (!atomFieldAccessor.hasField(atomData)) {
                        throw new StatsConversionException(
                                "Atom field inconsistency in atom list. "
                                + "A field is unset for atom of type "
                                + getAtomDataClassName());
                    }
                    valueList.add(atomFieldAccessor.getField(atomData));
                }
                setPersistableBundleArrayField(
                        createBundleKey(atomFieldAccessor.getFieldName()), valueList, bundle);
            }
        }
        // Check if there are dimension fields needing conversion
        if (dimensionsFieldsIds == null || dimensionsValuesList == null) {
            return bundle;
        }
        // Create conversions for fields encoded in dimension fields
        // Atom fields encoded in dimension values are not set, thus not extracted above
        for (int i = 0; i < dimensionsFieldsIds.size(); ++i) {
            Integer fieldId = dimensionsFieldsIds.get(i);
            List<Object> valueList = new ArrayList<>();
            for (List<DimensionsValue> dvList : dimensionsValuesList) {
                valueList.add(extractDimensionsValue(dvList.get(i), hashToStringMap));
            }
            setPersistableBundleArrayField(
                    createBundleKey(getAtomFieldAccessorMap().get(fieldId).getFieldName()),
                    valueList,
                    bundle);
        }
        return bundle;
    }

    /**
     * Extracts the dimension value from the provided {@link DimensionsValue}.
     *
     * @param dv the {@link DimensionsValue} to extract value from.
     * @param hashToStringMap the mapping used to translate hash code to string.
     * @return extracted value object.
     * @throws StatsConversionException if it's not possible to extract dimension value.
     */
    @NonNull
    private static Object extractDimensionsValue(
            @NonNull DimensionsValue dv,
            @Nullable Map<Long, String> hashToStringMap) throws StatsConversionException {
        switch (dv.getValueCase()) {
            case VALUE_STR:
                return dv.getValueStr();
            case VALUE_INT:
                return dv.getValueInt();
            case VALUE_LONG:
                return dv.getValueLong();
            case VALUE_BOOL:
                return dv.getValueBool();
            case VALUE_FLOAT:
                return dv.getValueFloat();
            case VALUE_STR_HASH:
                if (hashToStringMap == null) {
                    throw new StatsConversionException(
                            "Could not extract dimension value, no hash to string map found.");
                }
                return hashToStringMap.get(dv.getValueStrHash());
            default:
                throw new StatsConversionException(
                    "Could not extract dimension value, value not set or type not supported.");
        }
    }

    /**
     * Sets array fields in the {@link PersistableBundle}.
     *
     * @param name key value for the bundle, corresponds to atom field name.
     * @param objList the list to be converted to {@link PersistableBundle} compatible array.
     * @param bundle the {@link PersistableBundle} to put the arrays to.
     */
    private static void setPersistableBundleArrayField(
            @NonNull String name,
            @NonNull List<?> objList,
            @NonNull PersistableBundle bundle) {
        Object e = objList.get(0);  // All elements of the list are the same type.
        if (e instanceof Integer) {
            int[] intArray = new int[objList.size()];
            for (int i = 0; i < objList.size(); ++i) {
                intArray[i] = (Integer) objList.get(i);
            }
            bundle.putIntArray(name, intArray);
        } else if (e instanceof Long) {
            long[] longArray = new long[objList.size()];
            for (int i = 0; i < objList.size(); ++i) {
                longArray[i] = (Long) objList.get(i);
            }
            bundle.putLongArray(name, longArray);
        } else if (e instanceof String) {
            bundle.putStringArray(name, objList.toArray(new String[0]));
        } else if (e instanceof Boolean) {
            boolean[] boolArray = new boolean[objList.size()];
            for (int i = 0; i < objList.size(); ++i) {
                boolArray[i] = (Boolean) objList.get(i);
            }
            bundle.putBooleanArray(name, boolArray);
        } else if (e instanceof Double) {
            double[] doubleArray = new double[objList.size()];
            for (int i = 0; i < objList.size(); ++i) {
                doubleArray[i] = (Double) objList.get(i);
            }
            bundle.putDoubleArray(name, doubleArray);
        } else if (e instanceof Float) {
            double[] doubleArray = new double[objList.size()];
            for (int i = 0; i < objList.size(); ++i) {
                doubleArray[i] = ((Float) objList.get(i)).doubleValue();
            }
            bundle.putDoubleArray(name, doubleArray);
        }
    }

    /**
     * Creates the bundle key string for a metric field.
     *
     * The bundle key differs from the original metric field name by having a namespace prefix.
     *
     * @param metricFieldName the metric field name string.
     * @return the bundle key string.
     */
    private String createBundleKey(String metricFieldName) {
        return STATS_BUNDLE_KEY_PREFIX + metricFieldName;
    }
}
