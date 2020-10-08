package org.androidbootmanager.app.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.topjohnwu.superuser.Shell;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.util.Constants;

public class HomeFragment extends Fragment {

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        TextView statusText = root.findViewById(R.id.home_installedWorking_text);
        ImageView statusImg = root.findViewById(R.id.home_installedWorking_image);
        Shell.su(Constants.scriptDir + "is_installed.sh").submit((result) -> {
            switch (result.getCode()) {
                case 0:
                    if (result.getOut().contains("ABM.bootloader=1")) {
                        statusImg.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.ic_ok));
                        statusText.setText(R.string.home_installedWorking_ok);
                        break;
                    }
                case 1:
                    statusImg.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.ic_no));
                    statusText.setText(R.string.home_installedWorking_no);
            }
        });
        return root;
    }
}