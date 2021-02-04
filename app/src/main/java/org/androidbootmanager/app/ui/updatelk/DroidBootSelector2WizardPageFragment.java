package org.androidbootmanager.app.ui.updatelk;

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

import com.topjohnwu.superuser.Shell;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.ui.activities.SplashActivity;
import org.androidbootmanager.app.ui.wizard.WizardViewModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DroidBootSelector2WizardPageFragment extends Fragment {
    protected WizardViewModel model;
    private TextView txt;
    private Button ok;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(requireActivity()).get(WizardViewModel.class);
        model.setPositiveFragment(null);
        model.setNegativeFragment(null);
        model.setPositiveAction(() -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Android-Boot-Manager/App/wiki/DroidBoot-FAQ"))));
        model.setNegativeAction(() -> requireActivity().finish());
        model.setPositiveText(getString(R.string.help));
        model.setNegativeText(getString(R.string.cancel));
        final View root = inflater.inflate(R.layout.wizard_installer_droidboot, container, false);
        txt = root.findViewById(R.id.wizard_installer_droidboot_text);
        ok = root.findViewById(R.id.wizard_installer_droidboot_btn);
        ok.setOnClickListener((a) -> {
            Intent intent = new Intent();
            intent.setType("*/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(intent, 5207);
        });
        return root;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == 5207) {
            if (resultCode == Activity.RESULT_OK) {
                assert data != null;
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
                    @SuppressLint("SdCardPath") File targetFile = new File("/data/data/org.androidbootmanager.app/files/lk2nd.img");
                    OutputStream outStream = new FileOutputStream(targetFile);
                    assert initialStream != null;
                    SplashActivity.copyFile(initialStream, outStream);
                    initialStream.close();
                    outStream.close();
                    Shell.sh("file /data/data/org.androidbootmanager.app/files/lk2nd.img").submit((out) -> {
                        if (String.join("\n",out.getOut()).contains(": data")) {
                            model.setPositiveFragment(DoUpdateWizardPageFragment.class);
                            model.setPositiveText(getString(R.string.next));
                            txt.setText(getString(R.string.select_droidboot_ok));
                            ok.setVisibility(View.INVISIBLE);
                        } else if (String.join("\n",out.getOut()).contains("Zip")) {
                            Toast.makeText(requireContext(), R.string.select_droidboot_unzip, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(requireContext(), R.string.select_droidboot_invalid, Toast.LENGTH_LONG).show();
                        }
                    });
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
