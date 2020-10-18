package org.androidbootmanager.app.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.topjohnwu.superuser.Shell;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.ui.installer.InstallerWelcomeWizardPageFragment;
import org.androidbootmanager.app.ui.wizard.ExampleWizardPage2Fragment;
import org.androidbootmanager.app.ui.wizard.WizardActivity;
import org.androidbootmanager.app.util.Constants;

import java.util.concurrent.atomic.AtomicBoolean;

public class HomeFragment extends Fragment {

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        TextView statusText0 = root.findViewById(R.id.home_installedWorking_superUser);
        TextView statusText1 = root.findViewById(R.id.home_installedWorking_install1);
        TextView statusText2 = root.findViewById(R.id.home_installedWorking_install2);
        TextView statusText3 = root.findViewById(R.id.home_installedWorking_install3);
        MaterialButton installButton = root.findViewById(R.id.home_installButton);
        AtomicBoolean check0 = new AtomicBoolean(false);
        AtomicBoolean check1 = new AtomicBoolean(false);
        AtomicBoolean check2 = new AtomicBoolean(false);
        AtomicBoolean check3 = new AtomicBoolean(false);
        ImageView statusImg = root.findViewById(R.id.home_installedWorking_image);
        Shell.su(Constants.scriptDir + "is_installed.sh").submit((result) -> {
            check0.set(Shell.rootAccess());
            statusText0.setText(check0.get() ? R.string.ok : R.string.failure);
            check1.set(result.getCode() == 0);
            statusText1.setText(check1.get() ? R.string.ok : R.string.failure);
            check2.set(String.join("",result.getOut()).contains("ABM.bootloader=1"));
            statusText2.setText(check2.get() ? R.string.ok : R.string.failure);
            check3.set(check1.get() && check2.get());
            statusText3.setText(check3.get() ? R.string.ok : R.string.failure);
            statusImg.setImageDrawable(ContextCompat.getDrawable(requireActivity(),check3.get() ? R.drawable.ic_ok : R.drawable.ic_no));
            installButton.setVisibility(!check3.get() ? View.VISIBLE : View.INVISIBLE);
            installButton.setOnClickListener((v) -> startActivity(new Intent(requireActivity(), WizardActivity.class).putExtra("StartFragment", InstallerWelcomeWizardPageFragment.class)));
        });
        return root;
    }
}