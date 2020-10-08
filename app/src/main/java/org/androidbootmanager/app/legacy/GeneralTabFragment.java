package org.androidbootmanager.app.legacy;

import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.topjohnwu.superuser.io.SuFile;

import org.androidbootmanager.app.ActionAbortedCleanlyError;
import org.androidbootmanager.app.ConfigFile;
import org.androidbootmanager.app.ConfigTextWatcher;
import org.androidbootmanager.app.R;

import java.util.Objects;

public class GeneralTabFragment extends ConfiguratorActivity.BaseFragment {

    ConfigFile generalConfig;
    String fileName;

    @Override
    protected void onPreInit() {
        layout = R.layout.tab_general;
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
        Objects.requireNonNull(getView());
        ((EditText) getView().findViewById(R.id.tabgeneralSettingTimeout)).setText(generalConfig.get("timeout"));
        ((EditText) getView().findViewById(R.id.tabgeneralSettingDefaultEntry)).setText(generalConfig.get("default"));
        ConfigTextWatcher.attachTo(R.id.tabgeneralSettingTimeout, getView(), generalConfig, "timeout");
        ConfigTextWatcher.attachTo(R.id.tabgeneralSettingDefaultEntry, getView(), generalConfig, "default");

        ((Button) getView().findViewById(R.id.tabgeneralSave)).setOnClickListener((OnClickListener) p1 -> generalConfig.exportToPrivFile("lk2nd.conf", fileName));
    }
}
