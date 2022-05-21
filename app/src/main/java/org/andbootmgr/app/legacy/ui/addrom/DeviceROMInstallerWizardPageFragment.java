package org.andbootmgr.app.legacy.ui.addrom;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
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

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.io.SuFile;
import com.topjohnwu.superuser.io.SuFileOutputStream;

import org.andbootmgr.app.R;
import org.andbootmgr.app.legacy.devices.DeviceList;
import org.andbootmgr.app.legacy.ui.activities.SplashActivity;
import org.andbootmgr.app.legacy.ui.wizard.WizardViewModel;
import org.andbootmgr.app.legacy.util.SDUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class DeviceROMInstallerWizardPageFragment extends Fragment {

    protected WizardViewModel model;
    protected AddROMViewModel imodel;
    protected TextView txt;
    protected Button ok;
    protected String key, pdump;

    @SuppressLint("SdCardPath")
    @SuppressWarnings("deprecation")
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
            key = (String) Objects.requireNonNull(imodel.getROM().getValue()).requiredFiles.keySet().toArray()[0];
            pdump = "/data/data/org.andbootmgr.app/cache/" + key;
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
            Map<Integer, String> a = new HashMap<>();
            for (SDUtils.Partition partition : meta.p) {
                if (partition.code.equals("8305"))
                    a.put(partition.id, getString(R.string.partidt, SDUtils.codes.get(partition.code), partition.id, partition.name));
            }
            txt.setText(Objects.requireNonNull(imodel.getROM().getValue().flashes.get(key))[1]);
            dd.setAdapter(new ArrayAdapter<>(requireActivity(), android.R.layout.simple_spinner_dropdown_item, a.values().toArray()));
            ok.setOnClickListener((v) -> {
                dd.setEnabled(false);
                Integer selectedPart = (Integer) a.keySet().toArray()[dd.getSelectedItemPosition()];
                pdump = meta.ppath + selectedPart;

                File imageFile = new File("/data/data/org.andbootmgr.app/cache/" + key);
                if (imageFile.exists()) {
                    txt.setText(R.string.copy_file);
                    ok.setVisibility(View.INVISIBLE);
                    flashPartition(key);
                } else {
                    txt.setText(Objects.requireNonNull(imodel.getROM().getValue().flashes.get(key))[0]);
                    ok.setOnClickListener((b) -> {
                        Intent intent = new Intent();
                        intent.setType("*/*");
                        intent.setAction(Intent.ACTION_GET_CONTENT);
                        startActivityForResult(intent, 5299);
                    });
                }
                imodel.getROM().getValue().flashes.remove(key);
                imodel.addPart(selectedPart);
            });
        } else if (imodel.getROM().getValue().parts.size() > 0) {
            root = inflater.inflate(R.layout.wizard_addrom_getpart, container, false);
            ok = root.findViewById(R.id.wizard_addrom_getpart_btn);
            txt = root.findViewById(R.id.wizard_addrom_getpart_txt);
            Spinner dd = root.findViewById(R.id.wizard_addrom_getpart_dd);
            final SDUtils.SDPartitionMeta meta = SDUtils.generateMeta(DeviceList.getModel(model));
            Map<Integer, String> a = new HashMap<>();
            for (SDUtils.Partition partition : meta.p) {
                if (partition.code.equals("8302"))
                    a.put(partition.id, getString(R.string.partidt, SDUtils.codes.get(partition.code), partition.id, partition.name));
            }
            txt.setText(imodel.getROM().getValue().parts.get(0));
            dd.setAdapter(new ArrayAdapter<>(requireActivity(), android.R.layout.simple_spinner_dropdown_item, a.values().toArray()));
            ok.setOnClickListener((v) -> {
                dd.setEnabled(false);
                ok.setVisibility(View.INVISIBLE);
                imodel.getROM().getValue().parts.remove(0);
                imodel.addPart((Integer) a.keySet().toArray()[dd.getSelectedItemPosition()]);
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

    private void flashPartition(String image) {
        new Thread(() -> {
            try {
                File targetFile = new File("/data/data/org.andbootmgr.app/cache/" + image);
                InputStream targetIS = new FileInputStream(targetFile);
                byte[] buffer = new byte[4];
                //noinspection ResultOfMethodCallIgnored
                targetIS.read(buffer, 0, 4);
                if (ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).getInt() == 0xed26ff3a) {
                    Shell.su("/data/data/org.andbootmgr.app/assets/Toolkit/simg2img /data/data/org.andbootmgr.app/cache/" + image + " " + pdump).exec();
                } else {
                    Shell.su("dd bs=4096 if=/data/data/org.andbootmgr.app/cache/" + image + " of=" + pdump).exec();
                }

                requireActivity().runOnUiThread(() -> {
                    model.setPositiveText(getString(R.string.next));
                    model.setPositiveFragment(DeviceROMInstallerWizardPageFragment.class);
                    txt.setText(getString(R.string.selected));
                });
                Log.i("ABM", "Result of delete: " + SuFile.open(targetFile.getAbsolutePath()).delete());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    @SuppressLint("SdCardPath")
    @SuppressWarnings("deprecation")
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
                            File targetFile = new File(pdump);
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
        } else if (requestCode == 5299) {
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
                            File targetFile = new File("/data/data/org.andbootmgr.app/cache/unsparse.img");
                            assert initialStream != null;
                            OutputStream outStream = SuFileOutputStream.open(targetFile);
                            SplashActivity.copyFile(initialStream, outStream);
                            initialStream.close();
                            outStream.close();
                            Log.i("ABM","copy done");
                            flashPartition("unsparse.img");
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
