#!/system/bin/sh

/data/data/org.androidbootmanager.app/assets/Scripts/is_installed.sh >/dev/null && echo /dev/block/bootdevice/by-name/oem > /data/abm-part.cfg || rm -f /data/abm-part.cfg
