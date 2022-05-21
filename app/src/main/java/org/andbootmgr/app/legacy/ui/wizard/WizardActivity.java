package org.andbootmgr.app.legacy.ui.wizard;

import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.andbootmgr.app.R;

import java.util.Objects;

public class WizardActivity extends AppCompatActivity {

    WizardViewModel model;

    @Override
    @SuppressWarnings("unchecked")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        model = new ViewModelProvider(this).get(WizardViewModel.class);
        model.setCodename(getIntent().getStringExtra("codename"));
        setContentView(R.layout.wizard_activity);
        if (savedInstanceState == null) {
            try {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.wizard_container, ((Class<? extends Fragment>) Objects.requireNonNull(getIntent().getSerializableExtra("StartFragment"))).newInstance())
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
            } else {
                if (model.getPositiveAction().getValue() != null) model.getPositiveAction().getValue().run();
            }
        });
        model.getPositiveText().observe(this,((Button) findViewById(R.id.wizard_positiveButton))::setText);
        findViewById(R.id.wizard_negativeButton).setOnClickListener(v -> {
            if (model.getNegativeFragment() != null) {
                try {
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.wizard_container, model.getNegativeFragment().newInstance())
                            .commitNow();
                } catch (IllegalAccessException | InstantiationException e) {
                    e.printStackTrace();
                }
            } else {
                if (model.getNegativeAction().getValue() != null) model.getNegativeAction().getValue().run();
            }
        });
        model.getNegativeText().observe(this,((Button) findViewById(R.id.wizard_negativeButton))::setText);
    }

    @Override
    public void onBackPressed() {
        // Do nothing
    }
}