package org.androidbootmanager.app.ui.generalcfg;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.topjohnwu.superuser.io.SuFile;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.devices.DeviceList;
import org.androidbootmanager.app.ui.activities.MainActivity;
import org.androidbootmanager.app.ui.debug.DebugActivity;
import org.androidbootmanager.app.ui.home.InstalledViewModel;
import org.androidbootmanager.app.ui.updatelk.BlUpdateWizardPageFragment;
import org.androidbootmanager.app.ui.wizard.WizardActivity;
import org.androidbootmanager.app.util.ActionAbortedCleanlyError;
import org.androidbootmanager.app.util.ConfigFile;
import org.androidbootmanager.app.util.ConfigTextWatcher;

import java.util.Objects;

public class GeneralCfgFragment extends Fragment {

    private InstalledViewModel model;
    private ConfigFile generalConfig;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        model = new ViewModelProvider(requireActivity()).get(InstalledViewModel.class);
        View root = inflater.inflate(R.layout.fragment_generalcfg, container, false);
        root.findViewById(R.id.generalcfg_umount).setOnClickListener((view) -> {
            MainActivity a = (MainActivity) requireActivity();
            if(a.umount(DeviceList.getModel(Objects.requireNonNull(model.getCodename().getValue()))))
                a.finish();
        });
        root.findViewById(R.id.generalcfg_update_bl).setOnClickListener((view) -> startActivity(new Intent(requireActivity(), WizardActivity.class).putExtra("codename",model.getCodename().getValue()).putExtra("StartFragment", BlUpdateWizardPageFragment.class)));
        Button debug = root.findViewById(R.id.debug);
        if (debug != null)
            debug.setOnClickListener((view) -> startActivity(new Intent(requireActivity(), DebugActivity.class)));

        final String fileName = SuFile.open("/data/abm/bootset/lk2nd/lk2nd.conf").exists() ? "/data/abm/bootset/lk2nd/lk2nd.conf" : "/data/abm/bootset/lk2nd/db.conf";
        try {
            generalConfig = ConfigFile.importFromFile(fileName);
        } catch (ActionAbortedCleanlyError actionAbortedCleanlyError) {
            actionAbortedCleanlyError.printStackTrace();
            Toast.makeText(requireContext(), "Loading configuration file: Error. Action aborted cleanly. Creating new.", Toast.LENGTH_LONG).show();
            generalConfig = new ConfigFile();
        }
        generalConfig.exportToPrivFile("lk2nd.conf", fileName);
        ((EditText) root.findViewById(R.id.generalcfg_timeout)).setText(generalConfig.get("timeout"));
        ((EditText) root.findViewById(R.id.generalcfg_default_entry)).setText(generalConfig.get("default"));
        ConfigTextWatcher.attachTo(R.id.generalcfg_timeout, root, generalConfig, "timeout");
        ConfigTextWatcher.attachTo(R.id.generalcfg_default_entry, root, generalConfig, "default");

        root.findViewById(R.id.generalcfg_save).setOnClickListener(p1 -> generalConfig.exportToPrivFile("lk2nd.conf", fileName));
        return root;
    }
}