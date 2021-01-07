package org.androidbootmanager.app.legacy;

import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.topjohnwu.superuser.io.SuFile;

import org.androidbootmanager.app.util.ActionAbortedCleanlyError;
import org.androidbootmanager.app.util.ConfigFile;
import org.androidbootmanager.app.util.ConfigTextWatcher;
import org.androidbootmanager.app.R;

public class GeneralTabFragment extends ConfiguratorActivity.BaseFragment {

    ConfigFile generalConfig;
    String fileName;

    @Override
    protected void onPreInit() {
        layout = R.layout.legacy_tab_general;
    }

    @Override
    protected void onInit() {
        fileName = SuFile.open("/data/bootset/lk2nd/lk2nd.conf").exists() ? "/data/bootset/lk2nd/lk2nd.conf" : "/data/bootset/lk2nd/db.conf";
        try {
            generalConfig = ConfigFile.importFromFile(fileName);
        } catch (ActionAbortedCleanlyError actionAbortedCleanlyError) {
            actionAbortedCleanlyError.printStackTrace();
            Toast.makeText(xcontext, "Loading configuration file: Error. Action aborted cleanly. Creating new.", Toast.LENGTH_LONG).show();
            generalConfig = new ConfigFile();
        }
        generalConfig.exportToPrivFile("lk2nd.conf", fileName);
        ((EditText) requireView().findViewById(R.id.generalcfg_timeout)).setText(generalConfig.get("timeout"));
        ((EditText) requireView().findViewById(R.id.generalcfg_default_entry)).setText(generalConfig.get("default"));
        ConfigTextWatcher.attachTo(R.id.generalcfg_timeout, getView(), generalConfig, "timeout");
        ConfigTextWatcher.attachTo(R.id.generalcfg_default_entry, getView(), generalConfig, "default");

        ((Button) requireView().findViewById(R.id.tabgeneralSave)).setOnClickListener((OnClickListener) p1 -> generalConfig.exportToPrivFile("lk2nd.conf", fileName));
    }
}
