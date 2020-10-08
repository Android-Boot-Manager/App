#!/system/bin/sh

installed() {
ls /data/abm-part.legacy_cfg /data/abm-part.2.legacy_cfg || /data/data/org.androidbootmanager.app/assets/Scripts/config/`cat /data/abm-codename.legacy_cfg`.sh
}

/data/data/org.androidbootmanager.app/assets/Scripts/is_installed.sh && installed || rm /data/abm-part.*
