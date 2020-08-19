#!/system/bin/sh

noconfig() {
/data/data/org.androidbootmanager.app/assets/Scripts/config/`cat /data/abm-codename.cfg`.sh
}

installed() {
ls /data/abm-part.cfg /data/abm-part.2.cfg || noconfig
}

/data/data/org.androidbootmanager.app/assets/Scripts/is_installed.sh && installed || rm /data/abm-part.*
