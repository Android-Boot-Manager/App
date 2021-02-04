package org.androidbootmanager.app.devices;

import androidx.fragment.app.Fragment;

import java.util.List;

public class DeviceModel {
    public String codename = "";
    public String viewname = "";
    public String bdev = "";
    public String pbdev = "";
    public List<Class<? extends Fragment>> flow;
    @SuppressWarnings("CanBeFinal")
    public boolean usesLegacyDir = false;
}