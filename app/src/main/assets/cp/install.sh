#!/system/bin/sh

# Create working dir
mkdir -p /sdcard/abm

# Backup
dd if=/dev/block/bootdevice/by-name/boot of=/sdcard/abm/stockboot.img

# Flash lk2nd to boot partition
dd if="$1" of=/dev/block/bootdevice/by-name/boot

# Mount bootset
mkdir -p /data/bootset
mount -t ext4 /dev/block/bootdevice/by-name/oem /data/bootset

# Create folder for current OS
mkdir -p /data/bootset/lk2nd/entries
mkdir -p "/data/bootset/$2"

# Copy device tree
cp /sys/firmware/fdt "/data/bootset/$2/dtb.dtb"
cp /sys/firmware/fdt /data/bootset/msm8937-motorola-cedric.dtb

# Copy kernel
mkdir -p /sdcard/abm/temp/boot
./unpackbootimg -i /sdcard/abm/stockboot.img -o /sdcard/abm/temp/boot 
cp /sdcard/abm/temp/boot/stockboot.img-zImage "/data/bootset/$2/zImage"

# Copy rd
cp /sdcard/abm/temp/boot/stockboot.img-ramdisk.gz "/data/bootset/$2/initrd.cpio.gz"

# Create entry
cmdline=$(cat /proc/cmdline)
cat << EOF >> /data/bootset/lk2nd/lk2nd.conf
   default    Entry 01
   timeout    5
EOF
cat << EOF >> /data/bootset/lk2nd/entries/entry01.conf
  title      $3
  linux      $2/zImage
  initrd     $2/initrd.cpio.gz
  dtb        $2/dtb.dtb
  options    $cmdline
EOF

# Unmount bootset, and sync cache
umount /data/bootset
sync

# Clean up
rm -r /sdcard/abm/temp
