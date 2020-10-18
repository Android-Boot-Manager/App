package org.androidbootmanager.app.ui.wizard;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.ui.wizard.WizardViewModel;

public class WizardActivity extends AppCompatActivity {

    WizardViewModel model;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        model = new ViewModelProvider(this).get(WizardViewModel.class);
        setContentView(R.layout.wizard_activity);
        if (savedInstanceState == null) {
            try {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.wizard_container, ((Class<? extends Fragment>) getIntent().getSerializableExtra("StartFragment")).newInstance())
                        .commitNow();
            } catch (IllegalAccessException | InstantiationException e) {
                throw new RuntimeException(e);
            }
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