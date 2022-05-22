package org.andbootmgr.app.legacy.util;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.topjohnwu.superuser.Shell;

import org.andbootmgr.app.R;
import org.andbootmgr.app.legacy.devices.DeviceModel;
import org.andbootmgr.app.util.SOUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class SDUtils {

    public static final HashMap<String, String> codes = new HashMap<>();

    public static void setupCodes(Context c) {
        codes.put("0700", c.getString(R.string.portable_part));
        codes.put("8302", c.getString(R.string.data_part));
        codes.put("8301", c.getString(R.string.meta_part));
        codes.put("8305", c.getString(R.string.system_part));
        codes.put("8300", c.getString(R.string.unknown_part));
    }

    public enum PartitionType {
        RESERVED, ADOPTED, PORTABLE, UNKNOWN, FREE, SYSTEM, DATA
    }

    public static class Partition {
        public PartitionType type;
        public int id;
        public long startSector;
        public long endSector;
        public long size;
        public String sizeFancy;
        public String code;
        public String name;
        public int minor;

        @NonNull
        @Override
        public String toString() {
            return "Partition{" +
                    "type=" + type +
                    ", id=" + id +
                    ", startSector=" + startSector +
                    ", endSector=" + endSector +
                    ", size=" + size +
                    ", sizeFancy='" + sizeFancy + '\'' +
                    ", code='" + code + '\'' +
                    ", name='" + name + '\'' +
                    ", minor=" + minor +
                    '}';
        }

        public static class FreeSpace extends Partition {

            public FreeSpace(long start, long end, int bytes) {
                startSector = ((start / 2048) + 1) * 2048 ; endSector = end; size = end - start; type = PartitionType.FREE; sizeFancy = SOUtils.humanReadableByteCountBin(size * bytes);
            }

            @NonNull
            @Override
            public String toString() {
                return "FreeSpace{" +
                        "startSector=" + startSector +
                        ", endSector=" + endSector +
                        ", size=" + size +
                        '}';
            }
        }
    }

    public static class SDPartitionMeta {
        @SuppressWarnings("CanBeFinal")
        public List<Partition> p = new ArrayList<>();
        @SuppressWarnings("CanBeFinal")
        public List<Partition> u = new ArrayList<>();
        public List<Partition> s = new ArrayList<>();
        public String friendlySize;
        public String guid;
        public long sectors;
        public int logicalSectorSizeBytes;
        public int maxEntries;
        public long firstUsableSector;
        public long lastUsableSector;
        public long alignSector;
        public long totalFreeSectors;
        public String totalFreeFancy;
        public long usableSectors;
        public int nid;
        public int major;
        public int minor;
        public String path;
        public String ppath;

        @NonNull
        @Override
        public String toString() {
            return "SDPartitionMeta{" +
                    "p=" + p +
                    ", s=" + s +
                    ", friendlySize='" + friendlySize + '\'' +
                    ", guid='" + guid + '\'' +
                    ", sectors=" + sectors +
                    ", logicalSectorSizeBytes=" + logicalSectorSizeBytes +
                    ", maxEntries=" + maxEntries +
                    ", firstUsableSector=" + firstUsableSector +
                    ", lastUsableSector=" + lastUsableSector +
                    ", alignSector=" + alignSector +
                    ", totalFreeSectors=" + totalFreeSectors +
                    ", totalFreeFancy='" + totalFreeFancy + '\'' +
                    ", usableSectors=" + usableSectors +
                    '}';
        }

        public int countPartition() {
            return p.size();
        }

        public Partition dumpPartition(int id) {
            return p.get(id);
        }

        public int count() { return u.size(); }

        public Partition dump(int id) {
            return u.get(id);
        }

        public Partition dumpS(int id) { return s.get(id); }
    }

    public static String umsd(PartitionType t, int major, int minor) {
        switch (t) {
            case FREE:
                return "true";
            case PORTABLE:
                return "sm unmount public:" + major + "," + minor;
            case ADOPTED:
                return "sm unmount private:" + major + "," + minor;
            case RESERVED:
            case UNKNOWN:
            default:
                return "echo 'Warning: Unsure on how to unmount this partition.'";
        }
    }

    public static String umsd(SDPartitionMeta meta) {
        StringBuilder s = new StringBuilder();
        for (Partition p : meta.p)
            s.append(umsd(p.type, meta.major, p.minor)).append(" && ");
        String e = s.toString();
        if (e.isEmpty()) return "true";
        return e.substring(0, e.length()-4);
    }

    @Nullable
    public static SDPartitionMeta generateMeta(String bdev, String pbdev) {
        SDPartitionMeta meta;
        Shell.Result r = Shell.su("printf \"mm:%d:%d\\n\" `stat -c '0x%t 0x%T' " + bdev + "` && sgdisk " + bdev + " --print").exec();
        if (r.isSuccess())
            meta = new SDPartitionMeta();
        else
            return null;
        meta.path = bdev;
        meta.ppath = pbdev;
        meta.nid = 1;
        long temp = -1;
        for (String o : r.getOut()) {
            if (o.startsWith("Disk ") && !o.contains("GUID")) {
                String[] t = o.split(": ")[1].split(", ");
                meta.sectors = Long.parseLong(t[0].replace(" sectors",""));
                meta.friendlySize = t[1];
            } else if(o.startsWith("Logical sector size: ") && o.endsWith(" bytes")) {
                meta.logicalSectorSizeBytes = Integer.parseInt(o.replace("Logical sector size: ","").replace(" bytes",""));
            } else if(o.startsWith("Sector size (logical/physical): ") && o.endsWith(" bytes")) {
                meta.logicalSectorSizeBytes = Integer.parseInt(o.replace("Sector size (logical/physical): ","").replace(" bytes","").split("/")[0]);
            } else if(o.startsWith("Disk identifier (GUID): ")) {
                meta.guid = o.replace("Disk identifier (GUID): ", "");
            } else if (o.startsWith("Partition table holds up to ")) {
                meta.maxEntries = Integer.parseInt(o.replace("Partition table holds up to ","").replace(" entries",""));
            } else if (o.startsWith("First usable sector is ")) {
                meta.firstUsableSector = Long.parseLong(o.replace("First usable sector is ","").replaceFirst(", last usable sector is .*$",""));
                meta.lastUsableSector = Long.parseLong(o.replace("First usable sector is " + meta.firstUsableSector + ", last usable sector is ",""));
                meta.usableSectors = meta.lastUsableSector - meta.firstUsableSector;
                temp = meta.firstUsableSector;
            } else if (o.startsWith("Partitions will be aligned on ") && o.endsWith("-sector boundaries")) {
                meta.alignSector = Long.parseLong(o.replace("Partitions will be aligned on ","").replace("-sector boundaries",""));
            } else if (o.startsWith("Total free space is ") && o.contains("sectors")) {
                String[] t = o.replace(")", "").split("\\(");
                meta.totalFreeSectors = Long.parseLong(t[0].replace("Total free space is ", "").replace(" sectors ", ""));
                meta.totalFreeFancy = t[1];
            } else if(o.startsWith("mm:")) {
                String[] t = o.trim().split(":");
                meta.major = Integer.parseInt(t[1]);
                meta.minor = Integer.parseInt(t[2]);
            } else if(o.equals("") || o.startsWith("Number  Start (sector)    End (sector)  Size       Code  Name") || o.startsWith("Main partition table begins at")) {
                assert true; //avoid empty statement warning but do nothing
            } else if (o.startsWith("  ") && o.contains("iB")) {
                while (o.contains("  "))
                    o = o.trim().replace("  "," ");
                String[] ocut = o.split(" ");
                Partition p = new Partition();
                p.id = Integer.parseInt(ocut[0]);
                if (meta.nid == p.id) meta.nid++;
                p.minor = meta.minor + p.id;
                p.startSector = Long.parseLong(ocut[1]);
                p.endSector = Long.parseLong(ocut[2]);
                p.sizeFancy = ocut[3] + " " + ocut[4];
                p.size = p.endSector - p.startSector;
                p.code = ocut[5];
                p.name = String.join(" ", Arrays.copyOfRange(ocut, 6, ocut.length));
                switch (p.code) {
                    case "8301":
                        p.type = p.id == 1 ? PartitionType.RESERVED : PartitionType.UNKNOWN;
                        break;
                    case "0700":
                        p.type = PartitionType.PORTABLE;
                        break;
                    case "FFFF":
                        p.type = p.id == 1 ? PartitionType.RESERVED : PartitionType.ADOPTED;
                        break;
                    case "8302":
                        p.type = PartitionType.DATA;
                        break;
                    case "8305":
                        p.type = PartitionType.SYSTEM;
                        break;
                    case "8300":
                    default:
                        p.type = PartitionType.UNKNOWN;
                        break;
                }
                meta.p.add(p);
                meta.u.add(p);
            } else {
                Log.e("ABM SDUtils","can't handle " + o);
                return null;
            }
        }
        @SuppressWarnings({"FuseStreamOperations", "SimplifyStreamApiCallChains"}) List<Partition> l = meta.u.stream().collect(Collectors.toList()); // this actually copies, therefore, we need this.
        l.sort((o1, o2) -> {
            // this is like this because startSector is long and I don't want overflows due to casting
            if(o1.startSector - o2.startSector < -1)
                return -1;
            else if (o1.startSector == o2.startSector)
                return 0;
            else return 1;
        });
        for (Partition p : l) {
            if (p.startSector > temp + meta.alignSector)
                meta.u.add(new Partition.FreeSpace(temp, p.startSector-1, meta.logicalSectorSizeBytes));
            temp = p.endSector;
        }
        if (meta.lastUsableSector > temp + meta.alignSector)
            meta.u.add(new Partition.FreeSpace(temp, meta.lastUsableSector-1, meta.logicalSectorSizeBytes));
        meta.s = meta.u.subList(0, meta.u.size());
        meta.s.sort((o1, o2) -> {
            // this is like this because startSector is long and I don't want overflows due to casting
            if(o1.startSector - o2.startSector < -1)
                return -1;
            else if (o1.startSector == o2.startSector)
                return 0;
            else return 1;
        });
        Log.i("ABM",meta.toString());
        return meta;
    }

    public static SDPartitionMeta generateMeta(DeviceModel m) {
        return generateMeta(m.bdev, m.pbdev);
    }

}
