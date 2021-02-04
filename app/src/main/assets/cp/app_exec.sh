#!/system/bin/sh

cd /data/data/org.androidbootmanager.app/assets/Toolkit || exit 37
exec "$@" >/sdcard/abm/action.log 2>&1