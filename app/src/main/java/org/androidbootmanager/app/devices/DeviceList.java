package org.androidbootmanager.app.devices;

import org.androidbootmanager.app.ui.installer.DeviceInstallerWizardPageFragment;
import org.androidbootmanager.app.ui.installer.DoInstallWizardPageFragment;
import org.androidbootmanager.app.ui.installer.DroidBootSelectorWizardPageFragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class DeviceList {
    public static final ArrayList<String> deviceList = new ArrayList<>();
    public static final HashMap<String, List<String>> bspList = new HashMap<>();
    static {
        //deviceList.add("cedric");
        deviceList.add("yggdrasil");
        bspList.put("k63v2_64_bsp", Collections.singletonList("yggdrasil"));
    }

    public static DeviceModel getModel(String codename) {
        DeviceModel d;
        //noinspection SwitchStatementWithTooFewBranches
        switch(codename) {
            case "yggdrasil":
                d = new DeviceModel();
                d.codename = "yggdrasil";
                d.viewname = "Volla Phone";
                d.bdev = "/dev/block/mmcblk1";
                d.pbdev = "/dev/block/mmcblk1p";
                d.flow = Arrays.asList(DeviceInstallerWizardPageFragment.class, DroidBootSelectorWizardPageFragment.class, DoInstallWizardPageFragment.class);
                break;
            /*case "cedric":
                d = new DeviceModel();
                d.codename = "cedric";
                d.viewname = "Moto G5";
                d.bdev = "/dev/block/mmcblk1";
                d.pbdev = "/dev/block/mmcblk1p";
                d.usesLegacyDir = true;
                d.flow = Arrays.asList(DeviceInstallerWizardPageFragment.class, DroidBootSelectorWizardPageFragment.class, ExampleWizardPageFragment.class);
                break;*/
            default:
                throw new RuntimeException(new IllegalStateException("DeviceModel not found: unknown device '" + codename + "'"));
        }
        return d;
    }

    public static List<DeviceModel> getModels() {
        return Collections.singletonList(/*DeviceList.getModel("cedric"),*/DeviceList.getModel("yggdrasil"));
    }
}
