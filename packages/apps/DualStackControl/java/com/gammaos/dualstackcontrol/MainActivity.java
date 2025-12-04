package com.gammaos.dualstackcontrol;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String PROP_DUALSTACK_ENABLED = "persist.gammaos.dualstack.enabled";
    private static final String PROP_DUALSTACK_PKGS = "persist.gammaos.dualstack.pkgs";

    private SwitchCompat switchDualstack;
    private RecyclerView recyclerApps;

    private final List<AppEntry> appEntries = new ArrayList<>();
    private final Set<String> selectedPackages = new HashSet<>();

    private AppsAdapter adapter;

    private boolean updatingSwitchProgrammatically = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        switchDualstack = findViewById(R.id.switchDualstack);
        recyclerApps = findViewById(R.id.recyclerApps);

        // Load current state for global enable
        boolean enabled = getSystemPropertyBool(PROP_DUALSTACK_ENABLED, false);
        updatingSwitchProgrammatically = true;
        switchDualstack.setChecked(enabled);
        updatingSwitchProgrammatically = false;

        switchDualstack.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (updatingSwitchProgrammatically) return;
            setSystemProperty(PROP_DUALSTACK_ENABLED, isChecked ? "1" : "0");
        });

        // Load current package list from property
        loadSelectedPackagesFromProperty();

        // Set up app list
        recyclerApps.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppsAdapter(appEntries);
        recyclerApps.setAdapter(adapter);

        loadInstalledUserApps();
    }

    // ---------------------------------------------------------------------
    // System property helpers
    // ---------------------------------------------------------------------

    private String getSystemProperty(String key, String def) {
        try {
            Class<?> spClass = Class.forName("android.os.SystemProperties");
            Method getMethod = spClass.getMethod("get", String.class, String.class);
            return (String) getMethod.invoke(null, key, def);
        } catch (Exception e) {
            return def;
        }
    }

    private void setSystemProperty(String key, String value) {
        try {
            Class<?> spClass = Class.forName("android.os.SystemProperties");
            Method setMethod = spClass.getMethod("set", String.class, String.class);
            setMethod.invoke(null, key, value);
        } catch (Exception ignored) {
        }
    }

    private boolean getSystemPropertyBool(String key, boolean def) {
        String v = getSystemProperty(key, def ? "1" : "0");
        return "1".equals(v) || "true".equalsIgnoreCase(v);
    }

    // ---------------------------------------------------------------------
    // DualStack package list handling
    // ---------------------------------------------------------------------

    private void loadSelectedPackagesFromProperty() {
        selectedPackages.clear();
        String raw = getSystemProperty(PROP_DUALSTACK_PKGS, "");
        if (TextUtils.isEmpty(raw)) {
            return;
        }
        String[] parts = raw.split(",");
        for (String p : parts) {
            if (p == null) continue;
            String pkg = p.trim();
            if (!pkg.isEmpty()) {
                selectedPackages.add(pkg);
            }
        }
    }

    private void writeSelectedPackagesToProperty() {
        if (selectedPackages.isEmpty()) {
            setSystemProperty(PROP_DUALSTACK_PKGS, "");
            return;
        }
        List<String> list = new ArrayList<>(selectedPackages);
        Collections.sort(list, String::compareToIgnoreCase);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(list.get(i));
        }
        setSystemProperty(PROP_DUALSTACK_PKGS, sb.toString());
    }

    private void onAppCheckedChanged(AppEntry entry, boolean isChecked) {
        if (isChecked) {
            selectedPackages.add(entry.packageName);
        } else {
            selectedPackages.remove(entry.packageName);
        }
        writeSelectedPackagesToProperty();
    }

    // ---------------------------------------------------------------------
    // Package listing
    // ---------------------------------------------------------------------

    private void loadInstalledUserApps() {
        appEntries.clear();

        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        if (apps == null) {
            adapter.notifyDataSetChanged();
            return;
        }

        for (ApplicationInfo ai : apps) {
            if (isSystemApp(ai)) {
                continue;
            }

            String pkg = ai.packageName;
            CharSequence labelCs = ai.loadLabel(pm);
            String label = labelCs != null ? labelCs.toString() : pkg;

            boolean checked = selectedPackages.contains(pkg);
            appEntries.add(new AppEntry(label, pkg, checked));
        }

        Collections.sort(appEntries, (a, b) -> a.label.toLowerCase(Locale.ROOT)
                .compareTo(b.label.toLowerCase(Locale.ROOT)));

        adapter.notifyDataSetChanged();
    }

    private boolean isSystemApp(ApplicationInfo ai) {
        final int flags = ai.flags;
        return (flags & ApplicationInfo.FLAG_SYSTEM) != 0
                || (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    // ---------------------------------------------------------------------
    // Data model & adapter
    // ---------------------------------------------------------------------

    private static class AppEntry {
        final String label;
        final String packageName;
        boolean checked;

        AppEntry(String label, String packageName, boolean checked) {
            this.label = label;
            this.packageName = packageName;
            this.checked = checked;
        }
    }

    private class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.ViewHolder> {

        private final List<AppEntry> data;

        AppsAdapter(List<AppEntry> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app_entry, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppEntry entry = data.get(position);
            holder.textAppLabel.setText(entry.label);
            holder.textPackageName.setText(entry.packageName);

            holder.checkEnabled.setOnCheckedChangeListener(null);
            holder.checkEnabled.setChecked(entry.checked);
            holder.checkEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                entry.checked = isChecked;
                onAppCheckedChanged(entry, isChecked);
            });

            holder.itemView.setOnClickListener(v -> {
                boolean newChecked = !holder.checkEnabled.isChecked();
                holder.checkEnabled.setChecked(newChecked);
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView textAppLabel;
            final TextView textPackageName;
            final CheckBox checkEnabled;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                textAppLabel = itemView.findViewById(R.id.textAppLabel);
                textPackageName = itemView.findViewById(R.id.textPackageName);
                checkEnabled = itemView.findViewById(R.id.checkEnabled);
            }
        }
    }
}
