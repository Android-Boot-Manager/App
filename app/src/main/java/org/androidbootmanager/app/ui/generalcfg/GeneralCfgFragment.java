package org.androidbootmanager.app.ui.generalcfg;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.ui.installer.InstallerWelcomeWizardPageFragment;
import org.androidbootmanager.app.ui.updatelk.BlUpdateWizardPageFragment;
import org.androidbootmanager.app.ui.wizard.WizardActivity;

public class GeneralCfgFragment extends Fragment {

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_generalcfg, container, false);
        root.findViewById(R.id.generalcfg_update_bl).setOnClickListener((view) -> startActivity(new Intent(requireActivity(), WizardActivity.class).putExtra("StartFragment", BlUpdateWizardPageFragment.class)));
        return root;
    }
}