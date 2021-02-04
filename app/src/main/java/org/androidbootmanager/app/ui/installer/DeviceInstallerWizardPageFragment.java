package org.androidbootmanager.app.ui.installer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.devices.DeviceList;
import org.androidbootmanager.app.ui.wizard.WizardViewModel;

import java.util.Objects;

public class DeviceInstallerWizardPageFragment extends Fragment {

        protected WizardViewModel model;
        protected DeviceInstallerViewModel imodel;

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            model = new ViewModelProvider(requireActivity()).get(WizardViewModel.class);
            imodel = new ViewModelProvider(requireActivity()).get(DeviceInstallerViewModel.class);
            model.setPositiveFragment(DeviceList.getModel(Objects.requireNonNull(imodel.getCodename().getValue())).flow.get(1));
            model.setNegativeFragment(null);
            model.setPositiveAction(null);
            model.setNegativeAction(() -> requireActivity().finish());
            model.setPositiveText(getString(R.string.next));
            model.setNegativeText(getString(R.string.cancel));
            imodel.flow.addAll(DeviceList.getModel(Objects.requireNonNull(imodel.getCodename().getValue())).flow);
            return inflater.inflate(R.layout.wizard_installer_deviceinstaller, container, false);
        }

        @Override
        public void onActivityCreated(@Nullable Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
        }

    }