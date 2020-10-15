package org.androidbootmanager.app.ui.wizard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import org.androidbootmanager.app.R;

public class WizardPage2Fragment extends WizardPageFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(requireActivity()).get(WizardViewModel.class);
        model.setPositiveFragment(WizardPageFragment.class);
        model.setNegativeFragment(WizardPageFragment.class);
        final View root = inflater.inflate(R.layout.wizardpage_fragment, container, false);
        final TextView message = (TextView) root.findViewById(R.id.wizardpage_message);
        message.setText("ho.");
        return root;
    }
}
