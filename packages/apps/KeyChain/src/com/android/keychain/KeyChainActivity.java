/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.keychain;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyEventLogger;
import android.app.admin.DevicePolicyManager;
import android.app.admin.IDevicePolicyManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserManager;
import android.security.IKeyChainAliasCallback;
import android.security.KeyChain;
import android.stats.devicepolicy.DevicePolicyEnums;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.android.internal.annotations.VisibleForTesting;
import com.android.keychain.internal.KeyInfoProvider;

import com.google.android.material.snackbar.Snackbar;

import org.bouncycastle.asn1.x509.X509Name;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.security.auth.x500.X500Principal;

import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

public class KeyChainActivity extends AppCompatActivity {
    private static final String TAG = "KeyChain";

    // The amount of time to delay showing a snackbar. If the alias is received before the snackbar
    // is shown, the activity will finish. If the certificate selection dialog is shown before the
    // snackbar, no snackbar will be shown.
    private static final long SNACKBAR_DELAY_TIME = 2000;
    // The minimum amount of time to display a snackbar while loading certificates.
    private static final long SNACKBAR_MIN_TIME = 1000;

    private int mSenderUid;
    private String mSenderPackageName;

    // beware that some of these KeyStore operations such as saw and
    // get do file I/O in the remote keystore process and while they
    // do not cause StrictMode violations, they logically should not
    // be done on the UI thread.
    private final KeyStore mKeyStore = getKeyStore();

    private static KeyStore getKeyStore() {
        try {
            final KeyStore keystore = KeyStore.getInstance("AndroidKeyStore");
            keystore.load(null);
            return keystore;
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException
                | CertificateException e) {
            Log.e(TAG, "Error opening AndroidKeyStore.", e);
            throw new RuntimeException("Error opening AndroidKeyStore.", e);
        }
    }

    // A snackbar to show the user while the KeyChain Activity is loading the certificates.
    private Snackbar mSnackbar;

    // A remote service may call {@link android.security.KeyChain#choosePrivateKeyAlias} multiple
    // times, which will result in multiple intents being sent to KeyChainActivity. The time of the
    // first received intent is recorded in order to ensure the snackbar is displayed for a
    // minimum amount of time after receiving the first intent.
    private long mFirstIntentReceivedTimeMillis = 0L;

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable mFinishActivity = KeyChainActivity.this::finish;
    private final Runnable mShowSnackBar = this::showSnackBar;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.keychain_activity);
        getWindow().addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);
    }

    /**
     * Returns the package name which the activity with {@code activityToken} is launched from.
     */
    @Nullable
    private static String getCallingAppPackageName(IBinder activityToken) {
        String pkg = null;
        try {
            pkg = ActivityManager.getService().getLaunchedFromPackage(activityToken);
        } catch (RemoteException e) {
            Log.v(TAG, "Could not talk to activity manager.", e);
        }
        return pkg;
    }

    @Override
    public void onResume() {
        super.onResume();

        final IBinder activityToken = getActivityToken();
        mSenderPackageName = getCallingAppPackageName(activityToken);
        if (mSenderPackageName == null) {
            //if no sender, bail, we need to identify the app to the user securely.
            finish(null);
            return;
        }
        try {
            mSenderUid = getPackageManager().getPackageInfo(
                    mSenderPackageName, 0).applicationInfo.uid;
        } catch (PackageManager.NameNotFoundException e) {
            // if unable to find the sender package info bail,
            // we need to identify the app to the user securely.
            finish(null);
            return;
        }

        chooseCertificate();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handler.removeCallbacks(mFinishActivity);
    }

    private void showSnackBar() {
        mFirstIntentReceivedTimeMillis = System.currentTimeMillis();
        mSnackbar = Snackbar.make(findViewById(R.id.container),
                String.format(getResources().getString(R.string.loading_certs_message),
                        getApplicationLabel()), Snackbar.LENGTH_INDEFINITE);
        mSnackbar.show();
    }

    private void finishSnackBar() {
        if (mSnackbar != null) {
            mSnackbar.dismiss();
            mSnackbar = null;
        } else {
            handler.removeCallbacks(mShowSnackBar);
        }
    }

    private void chooseCertificate() {
        // Start loading the set of certs to choose from now- if device policy doesn't return an
        // alias, having aliases loading already will save some time waiting for UI to start.
        KeyInfoProvider keyInfoProvider = new KeyInfoProvider() {
            public boolean isUserSelectable(String alias) {
                try (KeyChain.KeyChainConnection connection =
                        KeyChain.bind(KeyChainActivity.this)) {
                    return connection.getService().isUserSelectable(alias);
                }
                catch (InterruptedException ignored) {
                    Log.e(TAG, "interrupted while checking if key is user-selectable", ignored);
                    Thread.currentThread().interrupt();
                    return false;
                } catch (Exception | AssertionError ignored) {
                    Log.e(TAG, "error while checking if key is user-selectable", ignored);
                    return false;
                }
            }
        };

        Log.i(TAG, String.format("Requested by app uid %d to provide a private key alias",
                mSenderUid));

        String[] keyTypes = getIntent().getStringArrayExtra(KeyChain.EXTRA_KEY_TYPES);
        if (keyTypes == null) {
            keyTypes = new String[]{};
        }
        Log.i(TAG, String.format("Key types specified: %s", Arrays.toString(keyTypes)));

        ArrayList<byte[]> issuers = (ArrayList<byte[]>) getIntent().getSerializableExtra(
                KeyChain.EXTRA_ISSUERS);
        if (issuers == null) {
            issuers = new ArrayList<byte[]>();
        } else {
            Log.i(TAG, "Issuers specified, will be listed later.");
        }

        final AliasLoader loader = new AliasLoader(mKeyStore, this, keyInfoProvider,
                new CertificateParametersFilter(mKeyStore, keyTypes, issuers));
        loader.execute();

        final IKeyChainAliasCallback.Stub callback = new IKeyChainAliasCallback.Stub() {
            @Override public void alias(String alias) {
                Log.i(TAG, String.format("Alias provided by device policy client: %s", alias));
                // Use policy-suggested alias if provided or abort further actions if alias is
                // KeyChain.KEY_ALIAS_SELECTION_DENIED
                if (alias != null) {
                    finishWithAliasFromPolicy(alias);
                    return;
                }

                // No suggested alias - instead finish loading and show UI to pick one
                final CertificateAdapter certAdapter;
                try {
                    certAdapter = loader.get();
                } catch (InterruptedException | ExecutionException e) {
                    Log.e(TAG, "Loading certificate aliases interrupted", e);
                    finish(null);
                    return;
                }
                /*
                 * If there are no keys for the user to choose from, do not display
                 * the dialog. This is in line with what other operating systems do.
                 */
                if (!certAdapter.hasKeysToChoose()) {
                    Log.i(TAG, "No keys to choose from");
                    finish(null);
                    return;
                }
                runOnUiThread(() -> {
                    finishSnackBar();
                    displayCertChooserDialog(certAdapter);
                });
            }
        };

        // Show a snackbar to the user to indicate long-running task.
        if (mSnackbar == null) {
            handler.postDelayed(mShowSnackBar, SNACKBAR_DELAY_TIME);
        }
        Uri uri = getIntent().getParcelableExtra(KeyChain.EXTRA_URI);
        String alias = getIntent().getStringExtra(KeyChain.EXTRA_ALIAS);

        if (isManagedDevice()) {
            // Give a profile or device owner the chance to intercept the request, if a private key
            // access listener is registered with the DevicePolicyManagerService.
            IDevicePolicyManager devicePolicyManager = IDevicePolicyManager.Stub.asInterface(
                    ServiceManager.getService(Context.DEVICE_POLICY_SERVICE));
            try {
                devicePolicyManager.choosePrivateKeyAlias(mSenderUid, uri, alias, callback);
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to request alias from DevicePolicyManager", e);
                // Proceed without a suggested alias.
                try {
                    callback.alias(null);
                } catch (RemoteException shouldNeverHappen) {
                    finish(null);
                }
            }
        } else {
            // If the device is unmanaged, check whether the credential management app has provided
            // an alias for the given uri and calling package name.
            getAliasFromCredentialManagementApp(uri, callback);
        }
    }

    private boolean isManagedDevice() {
        DevicePolicyManager devicePolicyManager = getSystemService(DevicePolicyManager.class);
        return devicePolicyManager.getDeviceOwner() != null
                || devicePolicyManager.getProfileOwner() != null
                || hasManagedProfile();
    }

    private boolean hasManagedProfile() {
        UserManager userManager = getSystemService(UserManager.class);
        for (final UserInfo userInfo : userManager.getProfiles(getUserId())) {
            if (userInfo.isManagedProfile()) {
                return true;
            }
        }
        return false;
    }

    private void getAliasFromCredentialManagementApp(Uri uri,
            IKeyChainAliasCallback.Stub callback) {
        executor.execute(() -> {
            try (KeyChain.KeyChainConnection keyChainConnection = KeyChain.bind(this)) {
                String chosenAlias = null;
                if (keyChainConnection.getService().hasCredentialManagementApp()) {
                    Log.i(TAG, "There is a credential management app on the device. "
                            + "Looking for an alias in the policy.");
                    chosenAlias = keyChainConnection.getService()
                            .getPredefinedAliasForPackageAndUri(mSenderPackageName, uri);
                    if (chosenAlias != null) {
                        keyChainConnection.getService().setGrant(mSenderUid, chosenAlias, true);
                        Log.w(TAG, String.format("Selected alias %s from the "
                                + "credential management app's policy", chosenAlias));
                        DevicePolicyEventLogger
                                .createEvent(DevicePolicyEnums
                                        .CREDENTIAL_MANAGEMENT_APP_CREDENTIAL_FOUND_IN_POLICY)
                                .write();
                    } else {
                        Log.i(TAG, "No alias provided from the credential management app");
                    }
                }
                callback.alias(chosenAlias);
            } catch (InterruptedException | RemoteException | AssertionError e) {
                Log.e(TAG, "Unable to request find predefined alias from credential "
                        + "management app policy");
                // Proceed without a suggested alias.
                try {
                    callback.alias(null);
                } catch (RemoteException shouldNeverHappen) {
                    finish(null);
                } finally {
                    DevicePolicyEventLogger
                            .createEvent(DevicePolicyEnums
                                    .CREDENTIAL_MANAGEMENT_APP_POLICY_LOOKUP_FAILED)
                            .write();
                }
            }
        });
    }

    @VisibleForTesting
    public static class CertificateParametersFilter {
        private final KeyStore mKeyStore;
        private final List<String> mKeyTypes;
        private final List<X500Principal> mIssuers;

        public CertificateParametersFilter(KeyStore keyStore,
                @NonNull String[] keyTypes, @NonNull ArrayList<byte[]> issuers) {
            mKeyStore = keyStore;
            mKeyTypes = Arrays.asList(keyTypes);
            mIssuers = new ArrayList<X500Principal>();
            for (byte[] issuer : issuers) {
                try {
                    X500Principal issuerPrincipal = new X500Principal(issuer);
                    Log.i(TAG, "Added issuer: " + issuerPrincipal.getName());
                    mIssuers.add(new X500Principal(issuer));
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Skipping invalid issuer", e);
                }
            }
        }

        public boolean shouldPresentCertificate(String alias) {
            X509Certificate cert = loadCertificate(mKeyStore, alias);
            // If there's no certificate associated with the alias, skip.
            if (cert == null) {
                Log.i(TAG, String.format("No certificate associated with alias %s", alias));
                return false;
            }
            List<X509Certificate> certChain = new ArrayList(loadCertificateChain(mKeyStore, alias));
            Log.i(TAG, String.format("Inspecting certificate %s aliased with %s, chain length %d",
                        cert.getSubjectDN().getName(), alias, certChain.size()));

            // If the caller has provided a list of key types to restrict the certificates
            // offered for selection, skip this alias if the key algorithm is not in that
            // list.
            // Note that the end entity (leaf) certificate's public key has to be compatible
            // with the specified key algorithm, not any one of the chain (see RFC5246
            // section 7.4.6)
            String keyAlgorithm = cert.getPublicKey().getAlgorithm();
            Log.i(TAG, String.format("Certificate key algorithm: %s", keyAlgorithm));
            if (!mKeyTypes.isEmpty() && !mKeyTypes.contains(keyAlgorithm)) {
                return false;
            }

            // If the caller has provided a list of issuers to restrict the certificates
            // offered for selection, skip this alias if none of the issuers in the client
            // certificate chain is in that list.
            List<X500Principal> chainIssuers = new ArrayList();
            chainIssuers.add(cert.getIssuerX500Principal());
            for (X509Certificate intermediate : certChain) {
                X500Principal subject = intermediate.getSubjectX500Principal();
                Log.i(TAG, String.format("Subject of intermediate in client certificate chain: %s",
                            subject.getName()));
                // Collect the subjects of all the intermediates, as the RFC specifies that
                // "one of the certificates in the certificate chain SHOULD be issued by one of
                // the listed CAs."
                chainIssuers.add(subject);
            }

            if (!mIssuers.isEmpty()) {
                for (X500Principal issuer : chainIssuers) {
                    if (mIssuers.contains(issuer)) {
                        Log.i(TAG, String.format("Requested issuer found: %s", issuer));
                        return true;
                    }
                }
                return false;
            }

            return true;
        }
    }

    @VisibleForTesting
    static class AliasLoader extends AsyncTask<Void, Void, CertificateAdapter> {
        private final KeyStore mKeyStore;
        private final Context mContext;
        private final KeyInfoProvider mInfoProvider;
        private final CertificateParametersFilter mCertificateFilter;

        public AliasLoader(KeyStore keyStore, Context context,
                KeyInfoProvider infoProvider, CertificateParametersFilter certificateFilter) {
          mKeyStore = keyStore;
          mContext = context;
          mInfoProvider = infoProvider;
          mCertificateFilter = certificateFilter;
        }

        @Override protected CertificateAdapter doInBackground(Void... params) {
            final List<String> rawAliasList = new ArrayList<>();
            try {
                final Enumeration<String> aliases = mKeyStore.aliases();
                while (aliases.hasMoreElements()) {
                    final String alias = aliases.nextElement();
                    if (mKeyStore.isKeyEntry(alias)) {
                        rawAliasList.add(alias);
                    }
                }
            } catch (KeyStoreException e) {
                Log.e(TAG, "Error while loading entries from keystore. "
                        + "List may be empty or incomplete.");
            }

            return new CertificateAdapter(mKeyStore, mContext,
                    rawAliasList.stream().filter(mInfoProvider::isUserSelectable)
                    .filter(mCertificateFilter::shouldPresentCertificate)
                    .sorted().collect(Collectors.toList()));
        }
    }

    private void displayCertChooserDialog(final CertificateAdapter adapter) {
        if (adapter.mAliases.isEmpty()) {
            Log.w(TAG, "Should not be asked to display the cert chooser without aliases.");
            finish(null);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setNegativeButton(R.string.deny_button, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int id) {
                dialog.cancel(); // will cause OnDismissListener to be called
            }
        });

        int selectedItem = -1;
        Resources res = getResources();
        String alias = getIntent().getStringExtra(KeyChain.EXTRA_ALIAS);

        if (alias != null) {
            // if alias was requested, set it if found
            int adapterPosition = adapter.mAliases.indexOf(alias);
            if (adapterPosition != -1) {
                // increase by 1 to account for item 0 being the header.
                selectedItem = adapterPosition + 1;
            }
        } else if (adapter.mAliases.size() == 1) {
            // if only one choice, preselect it
            selectedItem = 1;
        }

        builder.setPositiveButton(R.string.allow_button, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int id) {
                if (dialog instanceof AlertDialog) {
                    ListView lv = ((AlertDialog) dialog).getListView();
                    int listViewPosition = lv.getCheckedItemPosition();
                    int adapterPosition = listViewPosition-1;
                    String alias = ((adapterPosition >= 0)
                                    ? adapter.getItem(adapterPosition)
                                    : null);
                    Log.i(TAG, String.format("User chose: %s", alias));
                    finish(alias);
                } else {
                    Log.wtf(TAG, "Expected AlertDialog, got " + dialog, new Exception());
                    finish(null);
                }
            }
        });

        builder.setTitle(res.getString(R.string.title_select_cert));
        builder.setSingleChoiceItems(adapter, selectedItem, null);
        final AlertDialog dialog = builder.create();

        // Show text above the list to explain what the certificate will be used for.
        TextView contextView = (TextView) View.inflate(
                this, R.layout.cert_chooser_header, null);

        final ListView lv = dialog.getListView();
        lv.addHeaderView(contextView, null, false);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    // Header. Just text; ignore clicks.
                    return;
                } else {
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                    lv.setItemChecked(position, true);
                    adapter.notifyDataSetChanged();
                }
            }
        });

        String contextMessage = String.format(res.getString(R.string.requesting_application),
                getApplicationLabel());
        Uri uri = getIntent().getParcelableExtra(KeyChain.EXTRA_URI);
        if (uri != null) {
            String hostMessage = String.format(res.getString(R.string.requesting_server),
                    Uri.encode(uri.getAuthority(), "$,;:@&=+"));
            if (contextMessage == null) {
                contextMessage = hostMessage;
            } else {
                contextMessage += " " + hostMessage;
            }
        }
        contextView.setText(contextMessage);

        if (selectedItem == -1) {
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialogInterface) {
                     dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                }
            });
        }
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override public void onCancel(DialogInterface dialog) {
                finish(null);
            }
        });
        dialog.create();
        // Prevents screen overlay attack.
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setFilterTouchesWhenObscured(true);
        dialog.show();
    }

    private String getApplicationLabel() {
        PackageManager pm = getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(mSenderPackageName, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return mSenderPackageName;
        }
    }

    @VisibleForTesting
    static class CertificateAdapter extends BaseAdapter {
        private final List<String> mAliases;
        private final List<String> mSubjects = new ArrayList<String>();
        private final KeyStore mKeyStore;
        private final Context mContext;

        private CertificateAdapter(KeyStore keyStore, Context context, List<String> aliases) {
            mAliases = aliases;
            mSubjects.addAll(Collections.nCopies(aliases.size(), (String) null));
            mKeyStore = keyStore;
            mContext = context;
        }
        @Override public int getCount() {
            return mAliases.size();
        }
        @Override public String getItem(int adapterPosition) {
            return mAliases.get(adapterPosition);
        }
        @Override public long getItemId(int adapterPosition) {
            return adapterPosition;
        }
        @Override public View getView(final int adapterPosition, View view, ViewGroup parent) {
            ViewHolder holder;
            if (view == null) {
                LayoutInflater inflater = LayoutInflater.from(mContext);
                view = inflater.inflate(R.layout.cert_item, parent, false);
                holder = new ViewHolder();
                holder.mAliasTextView = (TextView) view.findViewById(R.id.cert_item_alias);
                holder.mSubjectTextView = (TextView) view.findViewById(R.id.cert_item_subject);
                holder.mRadioButton = (RadioButton) view.findViewById(R.id.cert_item_selected);
                view.setTag(holder);
            } else {
                holder = (ViewHolder) view.getTag();
            }

            String alias = mAliases.get(adapterPosition);

            holder.mAliasTextView.setText(alias);

            String subject = mSubjects.get(adapterPosition);
            if (subject == null) {
                new CertLoader(adapterPosition, holder.mSubjectTextView).execute();
            } else {
                holder.mSubjectTextView.setText(subject);
            }

            ListView lv = (ListView)parent;
            int listViewCheckedItemPosition = lv.getCheckedItemPosition();
            int adapterCheckedItemPosition = listViewCheckedItemPosition-1;
            holder.mRadioButton.setChecked(adapterPosition == adapterCheckedItemPosition);
            return view;
        }

        /**
         * Returns true if there are keys to choose from.
         */
        public boolean hasKeysToChoose() {
            return !mAliases.isEmpty();
        }

        private class CertLoader extends AsyncTask<Void, Void, String> {
            private final int mAdapterPosition;
            private final TextView mSubjectView;
            private CertLoader(int adapterPosition, TextView subjectView) {
                mAdapterPosition = adapterPosition;
                mSubjectView = subjectView;
            }
            @Override protected String doInBackground(Void... params) {
                String alias = mAliases.get(mAdapterPosition);
                X509Certificate cert = loadCertificate(mKeyStore, alias);
                if (cert == null) {
                    return null;
                }
                // bouncycastle can handle the emailAddress OID of 1.2.840.113549.1.9.1
                X500Principal subjectPrincipal = cert.getSubjectX500Principal();
                X509Name subjectName = X509Name.getInstance(subjectPrincipal.getEncoded());
                return subjectName.toString(true, X509Name.DefaultSymbols);
            }
            @Override protected void onPostExecute(String subjectString) {
                mSubjects.set(mAdapterPosition, subjectString);
                mSubjectView.setText(subjectString);
            }
        }
    }

    private static class ViewHolder {
        TextView mAliasTextView;
        TextView mSubjectTextView;
        RadioButton mRadioButton;
    }

    private void finish(String alias) {
        finish(alias, false);
    }

    private void finishWithAliasFromPolicy(String alias) {
        finish(alias, true);
    }

    private void finish(String alias, boolean isAliasFromPolicy) {
        if (alias == null || alias.equals(KeyChain.KEY_ALIAS_SELECTION_DENIED)) {
            alias = null;
            setResult(RESULT_CANCELED);
        } else {
            Intent result = new Intent();
            result.putExtra(Intent.EXTRA_TEXT, alias);
            setResult(RESULT_OK, result);
        }
        IKeyChainAliasCallback keyChainAliasResponse
                = IKeyChainAliasCallback.Stub.asInterface(
                        getIntent().getIBinderExtra(KeyChain.EXTRA_RESPONSE));
        if (keyChainAliasResponse != null) {
            new ResponseSender(keyChainAliasResponse, alias, isAliasFromPolicy).execute();
            return;
        }
        finishActivity();
    }

    private class ResponseSender extends AsyncTask<Void, Void, Void> {
        private IKeyChainAliasCallback mKeyChainAliasResponse;
        private String mAlias;
        private boolean mFromPolicy;

        private ResponseSender(IKeyChainAliasCallback keyChainAliasResponse, String alias,
                boolean isFromPolicy) {
            mKeyChainAliasResponse = keyChainAliasResponse;
            mAlias = alias;
            mFromPolicy = isFromPolicy;
        }
        @Override protected Void doInBackground(Void... unused) {
            if (mAlias == null) {
                respondWithAlias(null);
                return null;
            }
            try (KeyChain.KeyChainConnection connection = KeyChain.bind(KeyChainActivity.this)) {
                // This is a safety check to make sure an alias was not somehow chosen by
                // the user but is not user-selectable.
                // However, if the alias was selected by the Device Owner / Profile Owner
                // (by implementing DeviceAdminReceiver), then there's no need to check
                // this.
                if (!mFromPolicy && (!connection.getService().isUserSelectable(mAlias))) {
                    Log.w(TAG, String.format("Alias %s not user-selectable.", mAlias));
                    respondWithAlias(null);
                    return null;
                }
                connection.getService().setGrant(mSenderUid, mAlias, true);
                respondWithAlias(mAlias);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                Log.d(TAG, "interrupted while granting access", ignored);
                respondWithAlias(null);
            } catch (IllegalArgumentException ignored) {
                Log.d(TAG, "attempt to set grant on a non-existent alias", ignored);
                respondWithAlias(null);
            } catch (Exception | AssertionError ignored) {
                // Catchall so we always call mKeyChainAliasResponse.
                // AssertionError is thrown in case of failure to connect to the service.
                Log.e(TAG, "error while granting access", ignored);
                respondWithAlias(null);
            }
            return null;
        }

        private void respondWithAlias(String alias) {
            try {
                mKeyChainAliasResponse.alias(alias);
            } catch (Exception e) {
                // don't just catch RemoteException, caller could
                // throw back a RuntimeException across processes
                // which we should protect against.
                Log.e(TAG, "Error while returning alias", e);
            }
        }

        @Override protected void onPostExecute(Void unused) {
            finishActivity();
        }
    }

    private void finishActivity() {
        long timeElapsedSinceFirstIntent =
                System.currentTimeMillis() - mFirstIntentReceivedTimeMillis;
        if (mFirstIntentReceivedTimeMillis == 0L
                || timeElapsedSinceFirstIntent > SNACKBAR_MIN_TIME) {
            finishSnackBar();
            finish();
        } else {
            long remainingTimeToShowSnackBar = SNACKBAR_MIN_TIME - timeElapsedSinceFirstIntent;
            handler.postDelayed(mFinishActivity, remainingTimeToShowSnackBar);
        }
    }

    @Override public void onBackPressed() {
        finish(null);
    }

    private static X509Certificate loadCertificate(KeyStore keyStore, String alias) {
        final Certificate cert;
        try {
            if (keyStore.isCertificateEntry(alias)) {
                return null;
            }
            cert = keyStore.getCertificate(alias);
        } catch (KeyStoreException e) {
            Log.e(TAG, String.format("Error trying to retrieve certificate for \"%s\".", alias), e);
            return null;
        }
        if (cert != null) {
            if (cert instanceof X509Certificate) {
                return (X509Certificate) cert;
            } else {
                Log.w(TAG, String.format("Certificate associated with alias \"%s\" is not X509.",
                        alias));
            }
        }
        return null;
    }

    private static List<X509Certificate> loadCertificateChain(KeyStore keyStore,
            String alias) {
        final Certificate[] certs;
        final boolean isCertificateEntry;
        try {
            isCertificateEntry = keyStore.isCertificateEntry(alias);
            certs = keyStore.getCertificateChain(alias);
        } catch (KeyStoreException e) {
            Log.e(TAG, String.format("Error trying to retrieve certificate chain for \"%s\".",
                    alias), e);
            return Collections.emptyList();
        }
        final List<X509Certificate> result = new ArrayList<>();
        // If this is a certificate entry we return the single certificate. Otherwise we trim the
        // leaf and return only the rest of the chain.
        for (int i = isCertificateEntry ? 0 : 1; i < certs.length; ++i) {
            if (certs[i] instanceof X509Certificate) {
                result.add((X509Certificate) certs[i]);
            } else {
                Log.w(TAG,"A certificate in the chain of alias \""
                        + alias + "\" is not X509.");
                return Collections.emptyList();
            }
        }
        return result;
    }
}
