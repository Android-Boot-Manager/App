#!/system/bin/sh

installed() {
ls /data/abm-part.cfg /data/abm-part.2.cfg || /data/data/org.androidbootmanager.app/assets/Scripts/config/`cat /data/abm-codename.cfg`.sh
}

/data/data/org.androidbootmanager.app/assets/Scripts/is_installed.sh && installed || rm /data/abm-part.*
