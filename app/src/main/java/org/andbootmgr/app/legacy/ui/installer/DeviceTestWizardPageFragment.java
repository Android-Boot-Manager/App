package org.andbootmgr.app.legacy.ui.installer;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.andbootmgr.app.R;
import org.andbootmgr.app.legacy.devices.DeviceList;
import org.andbootmgr.app.legacy.ui.wizard.WizardViewModel;

public class DeviceTestWizardPageFragment extends Fragment {


    protected WizardViewModel model;
    protected DeviceInstallerViewModel imodel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(requireActivity()).get(WizardViewModel.class);
        imodel = new ViewModelProvider(requireActivity()).get(DeviceInstallerViewModel.class);
        model.setPositiveFragment(ROMNameChooserWizardPageFragment.class);
        model.setNegativeFragment(InstallerWelcomeWizardPageFragment.class);
        model.setPositiveAction(null);
        model.setNegativeAction(null);
        model.setPositiveText(getString(R.string.next));
        model.setNegativeText(getString(R.string.prev));
        final View root = inflater.inflate(R.layout.wizard_installer_devicetest, container, false);
        final ImageView img = root.findViewById(R.id.wizard_installer_devicetest_img);
        final TextView text = root.findViewById(R.id.wizard_installer_devicetest_text);
        if (DeviceList.deviceList.contains(Build.DEVICE)) {
            img.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_ok));
            text.setText(getString(R.string.device_msg, Build.DEVICE));
            model.setNegativeFragment(FindDeviceWizardPageFragment.class);
            model.setNegativeText(getString(R.string.device_btn));
            imodel.setCodename(Build.DEVICE);
        } else if (DeviceList.bspList.containsKey(Build.DEVICE)) {
            img.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_no));
            text.setText(getString(R.string.bsp_msg, Build.DEVICE));
            model.setPositiveFragment(FindDeviceWizardPageFragment.class);
        } else {
            img.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_no));
            text.setText(getString(R.string.wrong_device_msg, Build.DEVICE));
            model.setPositiveText(getString(R.string.device_btn));
            model.setPositiveFragment(FindDeviceWizardPageFragment.class);
        }
        return root;
    }

}
