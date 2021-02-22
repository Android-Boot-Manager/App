package org.androidbootmanager.app.ui.addrom;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.topjohnwu.superuser.io.SuFileOutputStream;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.devices.DeviceList;
import org.androidbootmanager.app.ui.activities.SplashActivity;
import org.androidbootmanager.app.ui.wizard.WizardViewModel;
import org.androidbootmanager.app.util.SDUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Objects;

public class DeviceROMInstallerWizardPageFragment extends Fragment {

    protected WizardViewModel model;
    protected AddROMViewModel imodel;
    protected TextView txt;
    protected Button ok;
    protected String key, pdump;

    @SuppressLint("SdCardPath")
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
        SDUtils.setupCodes(requireContext());
        if (Objects.requireNonNull(imodel.getROM().getValue()).requiredFiles.size() > 0) {
            root = inflater.inflate(R.layout.wizard_installer_droidboot, container, false);
            txt = root.findViewById(R.id.wizard_installer_droidboot_text);
            ok = root.findViewById(R.id.wizard_installer_droidboot_btn);
            ok.setOnClickListener((a) -> {
                Intent intent = new Intent();
                intent.setType("*/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent, 5210);
            });
            pdump = "/data/data/org.androidbootmanager.app/cache/" + key;
            key = (String) Objects.requireNonNull(imodel.getROM().getValue()).requiredFiles.keySet().toArray()[0];
            String val = imodel.getROM().getValue().requiredFiles.get(key);
            txt.setText(val);
            imodel.getROM().getValue().requiredFiles.remove(key);
        } else if (imodel.getROM().getValue().flashes.size() > 0) {
            root = inflater.inflate(R.layout.wizard_addrom_getpart, container, false);
            ok = root.findViewById(R.id.wizard_addrom_getpart_btn);
            txt = root.findViewById(R.id.wizard_addrom_getpart_txt);
            Spinner dd = root.findViewById(R.id.wizard_addrom_getpart_dd);
            key = (String) imodel.getROM().getValue().flashes.keySet().toArray()[0];
            final SDUtils.SDPartitionMeta meta = SDUtils.generateMeta(DeviceList.getModel(model));
            ArrayList<String> a = new ArrayList<>();
            for (SDUtils.Partition partition : meta.p) {
                a.add(getString(R.string.partidt, SDUtils.codes.get(partition.code), partition.id, partition.name));
            }
            txt.setText(imodel.getROM().getValue().flashes.get(key));
            dd.setAdapter(new ArrayAdapter<>(requireActivity(), android.R.layout.simple_spinner_dropdown_item, a));
            ok.setOnClickListener((v) -> {
                dd.setEnabled(false);
                pdump = meta.ppath + meta.dumpPartition(dd.getSelectedItemPosition()).id;
                txt.setText(key);
                ok.setOnClickListener((b) -> {
                    Intent intent = new Intent();
                    intent.setType("*/*");
                    intent.setAction(Intent.ACTION_GET_CONTENT);
                    startActivityForResult(intent, 5210);
                });
                imodel.getROM().getValue().flashes.remove(key);
                imodel.addPart(meta.dumpPartition(dd.getSelectedItemPosition()).id);
            });
        } else if (imodel.getROM().getValue().parts.size() > 0) {
            root = inflater.inflate(R.layout.wizard_addrom_getpart, container, false);
            ok = root.findViewById(R.id.wizard_addrom_getpart_btn);
            txt = root.findViewById(R.id.wizard_addrom_getpart_txt);
            Spinner dd = root.findViewById(R.id.wizard_addrom_getpart_dd);
            final SDUtils.SDPartitionMeta meta = SDUtils.generateMeta(DeviceList.getModel(model));
            ArrayList<String> a = new ArrayList<>();
            for (SDUtils.Partition partition : meta.p) {
                a.add(getString(R.string.partidt, SDUtils.codes.get(partition.code), partition.id, partition.name));
            }
            txt.setText(imodel.getROM().getValue().parts.get(0));
            dd.setAdapter(new ArrayAdapter<>(requireActivity(), android.R.layout.simple_spinner_dropdown_item, a));
            ok.setOnClickListener((v) -> {
                dd.setEnabled(false);
                ok.setVisibility(View.INVISIBLE);
                imodel.getROM().getValue().parts.remove(0);
                imodel.addPart(meta.dumpPartition(dd.getSelectedItemPosition()).id);
                model.setPositiveText(getString(R.string.next));
                model.setPositiveFragment(DeviceROMInstallerWizardPageFragment.class);
            });
        } else if (imodel.getROM().getValue().strings.size() > 0) {
            root = inflater.inflate(R.layout.wizard_installer_getname, container, false);
            final EditText val = root.findViewById(R.id.wizard_installer_getname_val);
            key = (String) imodel.getROM().getValue().strings.keySet().toArray()[0];
            ok = root.findViewById(R.id.wizard_installer_getname_btn);
            txt = root.findViewById(R.id.wizard_installer_getname_txt);
            txt.setText(key);
            val.setText(imodel.getROM().getValue().strings.get(key));
            ok.setOnClickListener(v -> {
                imodel.getName().add(val.getText().toString());
                val.setEnabled(false);
                ok.setVisibility(View.INVISIBLE);
                imodel.getROM().getValue().strings.remove(key);
                model.setPositiveFragment(DeviceROMInstallerWizardPageFragment.class);
                model.setPositiveText(getString(R.string.next));
            });
        } else {
            root = inflater.inflate(R.layout.wizard_installer_deviceinstaller, container, false);
            ((TextView) root.findViewById(R.id.wizard_deviceinstaller)).setText(getString(R.string.wizard_devicerominstaller_text));
            Objects.requireNonNull(imodel.getROM().getValue()).gen.gen(imodel, imodel.getName().get(0), imodel.getName().get(1));
            model.setPositiveFragment(DoAddROMWizardPageFragment.class);
            model.setPositiveText(getString(R.string.next));
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
                    txt.setText(R.string.copy_file);
                    ok.setVisibility(View.INVISIBLE);
                    new Thread(() -> {
                        try {
                            @SuppressLint("SdCardPath") File targetFile = new File(pdump);
                            assert initialStream != null;
                            OutputStream outStream = SuFileOutputStream.open(targetFile);
                            SplashActivity.copyFile(initialStream, outStream);
                            initialStream.close();
                            outStream.close();
                            requireActivity().runOnUiThread(() -> {
                                model.setPositiveText(getString(R.string.next));
                                model.setPositiveFragment(DeviceROMInstallerWizardPageFragment.class);
                                txt.setText(getString(R.string.selected));
                            });
                        } catch (IOException e) {
                            requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), R.string.failure, Toast.LENGTH_LONG).show());
                            e.printStackTrace();
                        }
                    }).start();
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
