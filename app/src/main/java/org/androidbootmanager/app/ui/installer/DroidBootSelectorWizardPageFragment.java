package org.androidbootmanager.app.ui.installer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.ui.wizard.WizardViewModel;

public class DroidBootSelectorWizardPageFragment extends Fragment {

    protected WizardViewModel model;
    protected DeviceInstallerViewModel imodel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(requireActivity()).get(WizardViewModel.class);
        imodel = new ViewModelProvider(requireActivity()).get(DeviceInstallerViewModel.class);
        model.setPositiveFragment(imodel.flow.get(++imodel.flowPos));
        model.setNegativeFragment(imodel.flow.get(imodel.flowPos-1));
        model.setPositiveAction(null);
        model.setNegativeAction(null);
        model.setPositiveText(getString(R.string.next));
        model.setNegativeText(getString(R.string.prev));
        final View root = inflater.inflate(R.layout.wizardpage_fragment, container, false);
        return root;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }
}