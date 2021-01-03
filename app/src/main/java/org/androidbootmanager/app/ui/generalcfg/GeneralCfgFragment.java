package org.androidbootmanager.app.ui.generalcfg;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.ui.home.InstalledViewModel;
import org.androidbootmanager.app.ui.updatelk.BlUpdateWizardPageFragment;
import org.androidbootmanager.app.ui.wizard.WizardActivity;

public class GeneralCfgFragment extends Fragment {

    private InstalledViewModel model;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        model = new ViewModelProvider(requireActivity()).get(InstalledViewModel.class);
        View root = inflater.inflate(R.layout.fragment_generalcfg, container, false);
        root.findViewById(R.id.generalcfg_update_bl).setOnClickListener((view) -> startActivity(new Intent(requireActivity(), WizardActivity.class).putExtra("codename",model.getCodename().getValue()).putExtra("StartFragment", BlUpdateWizardPageFragment.class)));
        return root;
    }
}