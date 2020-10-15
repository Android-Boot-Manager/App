package org.androidbootmanager.app.ui.activities;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.os.Bundle;
import android.view.View;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.ui.wizard.WizardPageFragment;
import org.androidbootmanager.app.ui.wizard.WizardViewModel;

public class WizardActivity extends AppCompatActivity {

    WizardViewModel model;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        model = new ViewModelProvider(this).get(WizardViewModel.class);
        setContentView(R.layout.wizard_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.wizard_container, new WizardPageFragment())
                    .commitNow();
        }
        findViewById(R.id.wizard_positiveButton).setOnClickListener(v -> {
            if (model.getPositiveFragment() != null) {
                try {
                    getSupportFragmentManager().beginTransaction()
                        .replace(R.id.wizard_container, model.getPositiveFragment().newInstance())
                        .commitNow();
                } catch (IllegalAccessException | InstantiationException e) {
                    e.printStackTrace();
                }
            }
        });
        findViewById(R.id.wizard_negativeButton).setOnClickListener(v -> {
            if (model.getNegativeFragment() != null) {
                try {
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.wizard_container, model.getNegativeFragment().newInstance())
                            .commitNow();
                } catch (IllegalAccessException | InstantiationException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}