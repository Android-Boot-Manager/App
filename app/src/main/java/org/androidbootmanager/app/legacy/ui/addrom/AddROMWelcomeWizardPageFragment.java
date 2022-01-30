package org.androidbootmanager.app.legacy.ui.addrom;

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
import org.androidbootmanager.app.legacy.ui.wizard.WizardViewModel;

public class AddROMWelcomeWizardPageFragment extends Fragment {
    protected WizardViewModel model;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(requireActivity()).get(WizardViewModel.class);
        model.setPositiveFragment(AddROMChooseWizardPageFragment.class);
        model.setNegativeFragment(null);
        model.setPositiveAction(null);
        model.setNegativeAction(() -> requireActivity().finish());
        model.setPositiveText(getString(R.string.next));
        model.setNegativeText(getString(R.string.cancel));
        final View root = inflater.inflate(R.layout.wizard_installer_welcome, container, false);
        final TextView message = root.findViewById(R.id.wizard_installer_welcome_txt);
        message.setText(R.string.wizard_addrom_intro);
        return root;
    }

}
