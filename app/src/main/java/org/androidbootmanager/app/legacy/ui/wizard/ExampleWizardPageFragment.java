package org.androidbootmanager.app.legacy.ui.wizard;

import androidx.lifecycle.ViewModelProvider;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.androidbootmanager.app.R;

public class ExampleWizardPageFragment extends Fragment {

    protected WizardViewModel model;

    @SuppressLint("SetTextI18n")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(requireActivity()).get(WizardViewModel.class);
        model.setPositiveFragment(ExampleWizardPage2Fragment.class);
        model.setNegativeFragment(ExampleWizardPage2Fragment.class);
        model.setPositiveAction(null);
        model.setNegativeAction(null);
        model.setPositiveText(getString(R.string.next));
        model.setNegativeText(getString(R.string.prev));
        final View root = inflater.inflate(R.layout.wizardpage_fragment, container, false);
        final TextView message = root.findViewById(R.id.wizardpage_message);
        message.setText("hi.");
        return root;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

}