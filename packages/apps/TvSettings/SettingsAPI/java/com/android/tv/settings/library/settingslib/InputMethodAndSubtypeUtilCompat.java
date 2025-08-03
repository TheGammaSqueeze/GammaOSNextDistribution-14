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

package com.android.tv.settings.library.settingslib;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.icu.text.ListFormatter;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.internal.app.LocaleHelper;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.data.PreferenceCompatManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class InputMethodAndSubtypeUtilCompat {

    private static final boolean DEBUG = false;
    private static final String TAG = "InputMethdAndSubtypeUtlCompat";

    private static final String SUBTYPE_MODE_KEYBOARD = "keyboard";
    private static final char INPUT_METHOD_SEPARATER = ':';
    private static final char INPUT_METHOD_SUBTYPE_SEPARATER = ';';
    private static final int NOT_A_SUBTYPE_ID = -1;

    private static final TextUtils.SimpleStringSplitter sStringInputMethodSplitter
            = new TextUtils.SimpleStringSplitter(INPUT_METHOD_SEPARATER);

    private static final TextUtils.SimpleStringSplitter sStringInputMethodSubtypeSplitter
            = new TextUtils.SimpleStringSplitter(INPUT_METHOD_SUBTYPE_SEPARATER);

    // InputMethods and subtypes are saved in the settings as follows:
    // ime0;subtype0;subtype1:ime1;subtype0:ime2:ime3;subtype0;subtype1
    public static String buildInputMethodsAndSubtypesString(
            final HashMap<String, HashSet<String>> imeToSubtypesMap) {
        final StringBuilder builder = new StringBuilder();
        for (final String imi : imeToSubtypesMap.keySet()) {
            if (builder.length() > 0) {
                builder.append(INPUT_METHOD_SEPARATER);
            }
            final HashSet<String> subtypeIdSet = imeToSubtypesMap.get(imi);
            builder.append(imi);
            for (final String subtypeId : subtypeIdSet) {
                builder.append(INPUT_METHOD_SUBTYPE_SEPARATER).append(subtypeId);
            }
        }
        return builder.toString();
    }

    private static String buildInputMethodsString(final HashSet<String> imiList) {
        final StringBuilder builder = new StringBuilder();
        for (final String imi : imiList) {
            if (builder.length() > 0) {
                builder.append(INPUT_METHOD_SEPARATER);
            }
            builder.append(imi);
        }
        return builder.toString();
    }

    private static int getInputMethodSubtypeSelected(ContentResolver resolver) {
        try {
            return Settings.Secure.getInt(resolver,
                    Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE);
        } catch (SettingNotFoundException e) {
            return NOT_A_SUBTYPE_ID;
        }
    }

    private static boolean isInputMethodSubtypeSelected(ContentResolver resolver) {
        return getInputMethodSubtypeSelected(resolver) != NOT_A_SUBTYPE_ID;
    }

    private static void putSelectedInputMethodSubtype(ContentResolver resolver, int hashCode) {
        Settings.Secure.putInt(resolver, Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE, hashCode);
    }

    // Needs to modify InputMethodManageService if you want to change the format of saved string.
    static HashMap<String, HashSet<String>> getEnabledInputMethodsAndSubtypeList(
            ContentResolver resolver) {
        final String enabledInputMethodsStr = Settings.Secure.getString(
                resolver, Settings.Secure.ENABLED_INPUT_METHODS);
        if (DEBUG) {
            Log.d(TAG, "--- Load enabled input methods: " + enabledInputMethodsStr);
        }
        return parseInputMethodsAndSubtypesString(enabledInputMethodsStr);
    }

    public static HashMap<String, HashSet<String>> parseInputMethodsAndSubtypesString(
            final String inputMethodsAndSubtypesString) {
        final HashMap<String, HashSet<String>> subtypesMap = new HashMap<>();
        if (TextUtils.isEmpty(inputMethodsAndSubtypesString)) {
            return subtypesMap;
        }
        sStringInputMethodSplitter.setString(inputMethodsAndSubtypesString);
        while (sStringInputMethodSplitter.hasNext()) {
            final String nextImsStr = sStringInputMethodSplitter.next();
            sStringInputMethodSubtypeSplitter.setString(nextImsStr);
            if (sStringInputMethodSubtypeSplitter.hasNext()) {
                final HashSet<String> subtypeIdSet = new HashSet<>();
                // The first element is {@link InputMethodInfoId}.
                final String imiId = sStringInputMethodSubtypeSplitter.next();
                while (sStringInputMethodSubtypeSplitter.hasNext()) {
                    subtypeIdSet.add(sStringInputMethodSubtypeSplitter.next());
                }
                subtypesMap.put(imiId, subtypeIdSet);
            }
        }
        return subtypesMap;
    }

    private static HashSet<String> getDisabledSystemIMEs(ContentResolver resolver) {
        HashSet<String> set = new HashSet<>();
        String disabledIMEsStr = Settings.Secure.getString(
                resolver, Settings.Secure.DISABLED_SYSTEM_INPUT_METHODS);
        if (TextUtils.isEmpty(disabledIMEsStr)) {
            return set;
        }
        sStringInputMethodSplitter.setString(disabledIMEsStr);
        while (sStringInputMethodSplitter.hasNext()) {
            set.add(sStringInputMethodSplitter.next());
        }
        return set;
    }

    public static void saveInputMethodSubtypeList(Context context,
            PreferenceCompatManager preferenceCompatManager,
            ContentResolver resolver, List<InputMethodInfo> inputMethodInfos,
            boolean hasHardKeyboard) {
        String currentInputMethodId = Settings.Secure.getString(resolver,
                Settings.Secure.DEFAULT_INPUT_METHOD);
        final int selectedInputMethodSubtype = getInputMethodSubtypeSelected(resolver);
        final HashMap<String, HashSet<String>> enabledIMEsAndSubtypesMap =
                getEnabledInputMethodsAndSubtypeList(resolver);
        final HashSet<String> disabledSystemIMEs = getDisabledSystemIMEs(resolver);

        boolean needsToResetSelectedSubtype = false;
        for (final InputMethodInfo imi : inputMethodInfos) {
            final String imiId = imi.getId();
            final PreferenceCompat pref = preferenceCompatManager.getOrCreatePrefCompat(imiId);
            if (pref == null) {
                continue;
            }
            // In the choose input method screen or in the subtype enabler screen,
            // <code>pref</code> is an instance of TwoStatePreference.
            final boolean isImeChecked = pref.getType() == PreferenceCompat.TYPE_SWITCH
                    ? pref.getChecked() == PreferenceCompat.STATUS_ON
                    : enabledIMEsAndSubtypesMap.containsKey(imiId);
            final boolean isCurrentInputMethod = imiId.equals(currentInputMethodId);
            final boolean systemIme = imi.isSystem();
            if ((!hasHardKeyboard && InputMethodSettingValuesWrapper.getInstance(
                    context).isAlwaysCheckedIme(imi))
                    || isImeChecked) {
                if (!enabledIMEsAndSubtypesMap.containsKey(imiId)) {
                    // imiId has just been enabled
                    enabledIMEsAndSubtypesMap.put(imiId, new HashSet<>());
                }
                final HashSet<String> subtypesSet = enabledIMEsAndSubtypesMap.get(imiId);

                boolean subtypePrefFound = false;
                final int subtypeCount = imi.getSubtypeCount();
                for (int i = 0; i < subtypeCount; ++i) {
                    final InputMethodSubtype subtype = imi.getSubtypeAt(i);
                    final String subtypeHashCodeStr = String.valueOf(subtype.hashCode());
                    final PreferenceCompat subtypePref = preferenceCompatManager
                            .getOrCreatePrefCompat(imiId + subtypeHashCodeStr);
                    // In the Configure input method screen which does not have subtype preferences.
                    if (subtypePref == null) {
                        continue;
                    }
                    if (!subtypePrefFound) {
                        // Once subtype preference is found, subtypeSet needs to be cleared.
                        // Because of system change, hashCode value could have been changed.
                        subtypesSet.clear();
                        // If selected subtype preference is disabled, needs to reset.
                        needsToResetSelectedSubtype = true;
                        subtypePrefFound = true;
                    }
                    // Checking <code>subtypePref.isEnabled()</code> is insufficient to determine
                    // whether the user manually enabled this subtype or not.  Implicitly-enabled
                    // subtypes are also checked just as an indicator to users.  We also need to
                    // check <code>subtypePref.isEnabled()</code> so that only manually enabled
                    // subtypes can be saved here.
                    if (subtypePref.getEnabled() == PreferenceCompat.STATUS_ON
                            && subtypePref.getChecked() == PreferenceCompat.STATUS_OFF) {
                        subtypesSet.add(subtypeHashCodeStr);
                        if (isCurrentInputMethod) {
                            if (selectedInputMethodSubtype == subtype.hashCode()) {
                                // Selected subtype is still enabled, there is no need to reset
                                // selected subtype.
                                needsToResetSelectedSubtype = false;
                            }
                        }
                    } else {
                        subtypesSet.remove(subtypeHashCodeStr);
                    }
                }
            } else {
                enabledIMEsAndSubtypesMap.remove(imiId);
                if (isCurrentInputMethod) {
                    // We are processing the current input method, but found that it's not enabled.
                    // This means that the current input method has been uninstalled.
                    // If currentInputMethod is already uninstalled, InputMethodManagerService will
                    // find the applicable IME from the history and the system locale.
                    if (DEBUG) {
                        Log.d(TAG, "Current IME was uninstalled or disabled.");
                    }
                    currentInputMethodId = null;
                }
            }
            // If it's a disabled system ime, add it to the disabled list so that it
            // doesn't get enabled automatically on any changes to the package list
            if (systemIme && hasHardKeyboard) {
                if (disabledSystemIMEs.contains(imiId)) {
                    if (isImeChecked) {
                        disabledSystemIMEs.remove(imiId);
                    }
                } else {
                    if (!isImeChecked) {
                        disabledSystemIMEs.add(imiId);
                    }
                }
            }
        }

        final String enabledIMEsAndSubtypesString = buildInputMethodsAndSubtypesString(
                enabledIMEsAndSubtypesMap);
        final String disabledSystemIMEsString = buildInputMethodsString(disabledSystemIMEs);
        if (DEBUG) {
            Log.d(TAG, "--- Save enabled inputmethod settings. :" + enabledIMEsAndSubtypesString);
            Log.d(TAG, "--- Save disabled system inputmethod settings. :"
                    + disabledSystemIMEsString);
            Log.d(TAG, "--- Save default inputmethod settings. :" + currentInputMethodId);
            Log.d(TAG, "--- Needs to reset the selected subtype :" + needsToResetSelectedSubtype);
            Log.d(TAG, "--- Subtype is selected :" + isInputMethodSubtypeSelected(resolver));
        }

        // Redefines SelectedSubtype when all subtypes are unchecked or there is no subtype
        // selected. And if the selected subtype of the current input method was disabled,
        // We should reset the selected input method's subtype.
        if (needsToResetSelectedSubtype || !isInputMethodSubtypeSelected(resolver)) {
            if (DEBUG) {
                Log.d(TAG, "--- Reset inputmethod subtype because it's not defined.");
            }
            putSelectedInputMethodSubtype(resolver, NOT_A_SUBTYPE_ID);
        }

        Settings.Secure.putString(resolver,
                Settings.Secure.ENABLED_INPUT_METHODS, enabledIMEsAndSubtypesString);
        if (disabledSystemIMEsString.length() > 0) {
            Settings.Secure.putString(resolver, Settings.Secure.DISABLED_SYSTEM_INPUT_METHODS,
                    disabledSystemIMEsString);
        }
        // If the current input method is unset, InputMethodManagerService will find the applicable
        // IME from the history and the system locale.
        Settings.Secure.putString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD,
                currentInputMethodId != null ? currentInputMethodId : "");
    }


    @NonNull
    public static String getSubtypeLocaleNameAsSentence(@Nullable InputMethodSubtype subtype,
            @NonNull final Context context, @NonNull final InputMethodInfo inputMethodInfo) {
        if (subtype == null) {
            return "";
        }
        final Locale locale = getDisplayLocale(context);
        final CharSequence subtypeName = subtype.getDisplayName(context,
                inputMethodInfo.getPackageName(), inputMethodInfo.getServiceInfo()
                        .applicationInfo);
        return LocaleHelper.toSentenceCase(subtypeName.toString(), locale);
    }

    @NonNull
    public static String getSubtypeLocaleNameListAsSentence(
            @NonNull final List<InputMethodSubtype> subtypes, @NonNull final Context context,
            @NonNull final InputMethodInfo inputMethodInfo) {
        if (subtypes.isEmpty()) {
            return "";
        }
        final Locale locale = getDisplayLocale(context);
        final int subtypeCount = subtypes.size();
        final CharSequence[] subtypeNames = new CharSequence[subtypeCount];
        for (int i = 0; i < subtypeCount; i++) {
            subtypeNames[i] = subtypes.get(i).getDisplayName(context,
                    inputMethodInfo.getPackageName(), inputMethodInfo.getServiceInfo()
                            .applicationInfo);
        }
        return LocaleHelper.toSentenceCase(
                ListFormatter.getInstance(locale).format((Object[]) subtypeNames), locale);
    }

    @NonNull
    private static Locale getDisplayLocale(@Nullable final Context context) {
        if (context == null) {
            return Locale.getDefault();
        }
        if (context.getResources() == null) {
            return Locale.getDefault();
        }
        final Configuration configuration = context.getResources().getConfiguration();
        if (configuration == null) {
            return Locale.getDefault();
        }
        final Locale configurationLocale = configuration.getLocales().get(0);
        if (configurationLocale == null) {
            return Locale.getDefault();
        }
        return configurationLocale;
    }

    public static boolean isValidSystemNonAuxAsciiCapableIme(InputMethodInfo imi) {
        if (imi.isAuxiliaryIme() || !imi.isSystem()) {
            return false;
        }
        final int subtypeCount = imi.getSubtypeCount();
        for (int i = 0; i < subtypeCount; ++i) {
            final InputMethodSubtype subtype = imi.getSubtypeAt(i);
            if (SUBTYPE_MODE_KEYBOARD.equalsIgnoreCase(subtype.getMode())
                    && subtype.isAsciiCapable()) {
                return true;
            }
        }
        return false;
    }
}

