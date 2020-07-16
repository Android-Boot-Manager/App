#!/system/bin/sh

/data/data/org.androidbootmanager.app/assets/Scripts/is_installed.sh >/dev/null && {ls /data/abm-part.cfg || echo efatal_unknown > /data/abm-part.cfg} || rm -f /data/abm-part.cfg
