package org.andbootmgr.app.legacy.ui.installer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.andbootmgr.app.R;
import org.andbootmgr.app.legacy.ui.wizard.WizardViewModel;

public class ROMNameChooserWizardPageFragment extends Fragment {
    protected WizardViewModel model;
    protected DeviceInstallerViewModel imodel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(requireActivity()).get(WizardViewModel.class);
        imodel = new ViewModelProvider(requireActivity()).get(DeviceInstallerViewModel.class);
        model.setPositiveFragment(null);
        model.setNegativeFragment(DeviceTestWizardPageFragment.class);
        model.setPositiveAction(null);
        model.setNegativeAction(null);
        model.setPositiveText("");
        model.setNegativeText(getString(R.string.cancel));
        final View root = inflater.inflate(R.layout.wizard_installer_getname, container, false);
        final TextView val = root.findViewById(R.id.wizard_installer_getname_val);
        final Button btn = root.findViewById(R.id.wizard_installer_getname_btn);
        btn.setOnClickListener(v -> {
            imodel.setName(val.getText().toString());
            val.setEnabled(false);
            btn.setVisibility(View.INVISIBLE);
            model.setPositiveFragment(DeviceInstallerWizardPageFragment.class);
            model.setPositiveText(getString(R.string.next));
        });
        return root;
    }
}
