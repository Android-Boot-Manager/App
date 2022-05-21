package org.andbootmgr.app.legacy.ui.wizard;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import org.andbootmgr.app.R;

public class ExampleWizardPage2Fragment extends ExampleWizardPageFragment {

    @SuppressLint("SetTextI18n")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(requireActivity()).get(WizardViewModel.class);
        model.setPositiveFragment(ExampleWizardPageFragment.class);
        model.setNegativeFragment(ExampleWizardPageFragment.class);
        final View root = inflater.inflate(R.layout.wizardpage_fragment, container, false);
        final TextView message = root.findViewById(R.id.wizardpage_message);
        message.setText("ho.");
        return root;
    }
}
