package org.andbootmgr.app.legacy.roms;

import org.andbootmgr.app.legacy.ui.addrom.AddROMViewModel;

public interface CmdlineGenerator {
    void gen(AddROMViewModel imodel, String menuName, String folderName);
}
