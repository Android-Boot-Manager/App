package org.androidbootmanager.app.ui.addrom;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.ui.activities.SplashActivity;
import org.androidbootmanager.app.ui.wizard.WizardViewModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DeviceROMInstallerWizardPageFragment extends Fragment {

    protected WizardViewModel model;
    protected AddROMViewModel imodel;
    protected TextView txt;
    protected Button ok;
    protected String key;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        final View root;
        model = new ViewModelProvider(requireActivity()).get(WizardViewModel.class);
        imodel = new ViewModelProvider(requireActivity()).get(AddROMViewModel.class);
        model.setNegativeFragment(null);
        model.setPositiveFragment(null);
        model.setPositiveAction(null);
        model.setNegativeAction(() -> requireActivity().finish());
        model.setPositiveText("");
        model.setNegativeText(getString(R.string.cancel));
        if (imodel.getROM().getValue().requiredFiles.size() > 0) {
            root = inflater.inflate(R.layout.wizard_installer_droidboot, container, false);
            txt = root.findViewById(R.id.wizard_installer_droidboot_text);
            ok = root.findViewById(R.id.wizard_installer_droidboot_btn);
            ok.setOnClickListener((a) -> {
                Intent intent = new Intent();
                intent.setType("*/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent, 5210);
            });
            key = (String) imodel.getROM().getValue().requiredFiles.keySet().toArray()[0];
            String val = imodel.getROM().getValue().requiredFiles.get(key);
            txt.setText(val);
            imodel.getROM().getValue().requiredFiles.remove(key);
        } else if (imodel.getROM().getValue().parts.size() > 0) {
            // TODO
            return null;
        } else {
            root = inflater.inflate(R.layout.wizard_installer_deviceinstaller, container, false);
            ((TextView) root.findViewById(R.id.wizard_deviceinstaller)).setText(getString(R.string.wizard_devicerominstaller_text));
            model.setPositiveFragment(null); // TODO
        }
        return root;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == 5210) {
            if (resultCode == Activity.RESULT_OK) {
                Uri selectedUri = data.getData();
                try {
                    InputStream initialStream;
                    if (selectedUri != null) {
                        initialStream = requireActivity().getContentResolver().openInputStream(selectedUri);
                    } else {
                        Toast.makeText(requireContext(), R.string.failure, Toast.LENGTH_LONG).show();
                        new IllegalStateException("null selected").printStackTrace();
                        return;
                    }
                    @SuppressLint("SdCardPath") File targetFile = new File("/data/data/org.androidbootmanager.app/files/" + key);
                    OutputStream outStream = new FileOutputStream(targetFile);
                    assert initialStream != null;
                    SplashActivity.copyFile(initialStream, outStream);
                    initialStream.close();
                    outStream.close();
                    model.setPositiveText(getString(R.string.next));
                    model.setPositiveFragment(DeviceROMInstallerWizardPageFragment.class);
                    txt.setText(getString(R.string.selected));
                    ok.setVisibility(View.INVISIBLE);
                } catch (IOException e) {
                    Toast.makeText(requireContext(), R.string.failure, Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            } else {
                Toast.makeText(requireContext(), R.string.failure, Toast.LENGTH_LONG).show();
                new IllegalStateException("Result not OK but " + resultCode).printStackTrace();
            }
        }
        else super.onActivityResult(requestCode, resultCode, data);
    }

}
