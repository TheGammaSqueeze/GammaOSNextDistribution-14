package com.example.imsmediatestingapp;

import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

/**
 * Used as a helper for storing data of different types into the SharedPreferences and handling
 * converting that data for storage.
 */
public class SharedPrefsHandler {
    SharedPreferences prefs;
    SharedPreferences.Editor editor;
    static final String CODECS_PREF = "CODECS";
    static final String AMR_MODES_PREF = "AMR_MODES";
    static final String EVS_BANDS_PREF = "EVS_BANDS";
    static final String EVS_MODES_PREF = "EVS_MODES";

    public SharedPrefsHandler(SharedPreferences prefs) {
        this.prefs = prefs;
        editor = prefs.edit();
    }

    /**
     * As SharedPreferences can not save Integer sets, this will convert the Integer set into a
     * string so it can be saved in the SharedPreferences and converted back into a set when needed.
     * @param prefKey the key to be used to save the set under
     * @param set the Integer set to be saved in ShredPreferences
     */
    public void saveIntegerSetToPrefs(String prefKey, Set<Integer> set) {
        StringBuilder integerSet = new StringBuilder();
        for (int item : set) {
            integerSet.append(item).append(",");
        }

        editor.putString(prefKey, integerSet.toString()).apply();
    }

    /**
     * This will convert the String which was previously a set back into a set and return it.
     * @param prefKey the String value of the SharedPreferences key to retrieve the set data
     * @return a set containing data from the SharedPreferences
     */
    public Set<Integer> getIntegerSetFromPrefs(String prefKey) {
        Set<Integer> set = new HashSet<>();
        String integerSetString = prefs.getString(prefKey, "");
        if(!integerSetString.isEmpty()) {
            String[] integerSet = integerSetString.split(",");
            for (String item : integerSet) {
                set.add(Integer.parseInt(item));
            }
        }
        return set;
    }

}
