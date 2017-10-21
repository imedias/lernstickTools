package ch.fhnw.util;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;
import org.freedesktop.dbus.exceptions.DBusException;
import org.xml.sax.SAXException;

/**
 * A storage device
 *
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class StorageDevice implements Comparable<StorageDevice> {

    /**
     * all known types of storage devices
     */
    public enum Type {

        /**
         * a CD or DVD
         */
        OpticalDisc,
        /**
         * a hard drive
         */
        HardDrive,
        /**
         * a usb flash drive
         */
        USBFlashDrive,
        /**
         * a secure digital memory card
         */
        SDMemoryCard
    }

    /**
     * all known upgrade variants
     */
    public enum UpgradeVariant {

        /**
         * The persistency partition is cleaned (excluding the users home
         * directory and the cups configuration) and the system partition is
         * replaced.
         */
        REGULAR,
        /**
         * The system partition must be enlarged before upgrading.
         */
        REPARTITION,
        /**
         * The personal data and configuration is backed up before executing a
         * completely new installation on the storage device. After installation
         * the backed up data is restored.
         */
        BACKUP,
        /**
         * Upgrading is done by a clean default installation.
         */
        INSTALLATION,
        /**
         * The storage device can not be upgraded.
         */
        IMPOSSIBLE
    }

    private static final Logger LOGGER
            = Logger.getLogger(StorageDevice.class.getName());
    private static final ResourceBundle STRINGS
            = ResourceBundle.getBundle("ch/fhnw/util/Strings");
    private final String device;
    private String vendor;
    private String model;
    private String revision;
    private String serial;
    private boolean raid;
    private String raidLevel;
    private int raidDeviceCount;
    private boolean removable;
    private long size;
    private final Type type;
    private List<Partition> partitions;
    private UpgradeVariant upgradeVariant;
    private String noUpgradeReason;
    private Partition exchangePartition;
    private Partition dataPartition;
    private Partition efiPartition;
    private Partition systemPartition;

    /**
     * creates a new StorageDevice
     *
     * @param device the unix device name, e.g. "sda"
     * @throws DBusException if getting the device properties via d-bus fails
     */
    public StorageDevice(String device) throws DBusException {
        this.device = device;
        boolean isOpticalDisc = false;
        String connectionInterface = null;

        /*
         * The Udisks2 interface "Drive" reports *all* USB drives as removable,
         * even those where /sys/block/[device]/removable is '0'.
         * Because the value in /sys/block/[device]/removable is the correct one
         * we use it instead of the more "modern" Udisks2 interface. :-P
         */
        try {
            removable = new FileReader(
                    "/sys/block/" + device + "/removable").read() == '1';
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "", ex);
        }

        boolean systemInternal = false;

        if (DbusTools.DBUS_VERSION == DbusTools.DbusVersion.V1) {
            vendor = DbusTools.getStringProperty(device, "DriveVendor");
            model = DbusTools.getStringProperty(device, "DriveModel");
            revision = DbusTools.getStringProperty(device, "DriveRevision");
            serial = DbusTools.getStringProperty(device, "DriveSerial");
            size = DbusTools.getLongProperty(device, "DeviceSize");
            systemInternal = DbusTools.getBooleanProperty(
                    device, "DeviceIsSystemInternal");
            connectionInterface = DbusTools.getStringProperty(
                    device, "DriveConnectionInterface");
            isOpticalDisc = DbusTools.getBooleanProperty(
                    device, "DeviceIsOpticalDisc");

        } else {

            try {
                List<String> interfaceNames
                        = DbusTools.getDeviceInterfaceNames(device);
                String blockInterfaceName = "org.freedesktop.UDisks2.Block";
                if (interfaceNames.contains(blockInterfaceName)) {
                    // query block device specific properties
                    systemInternal = DbusTools.getDeviceBooleanProperty(
                            device, blockInterfaceName, "HintSystem");

                    // query drive specific properties
                    String driveObjectPath = DbusTools.getDevicePathProperty(
                            device, blockInterfaceName, "Drive").toString();
                    if (driveObjectPath.equals("/")) {
                        // raid device
                        raid = true;
                        String raidObjectPath = DbusTools.getDevicePathProperty(
                                device, blockInterfaceName, "MDRaid").toString();
                        String raidInterface = "org.freedesktop.UDisks2.MDRaid";
                        raidLevel = DbusTools.getStringProperty(
                                raidObjectPath, raidInterface, "Level");
                        raidDeviceCount = DbusTools.getIntProperty(
                                raidObjectPath, raidInterface, "NumDevices");
                        size = DbusTools.getLongProperty(
                                raidObjectPath, raidInterface, "Size");
                    } else {
                        // non-raid device
                        String driveInterface = "org.freedesktop.UDisks2.Drive";
                        vendor = DbusTools.getStringProperty(
                                driveObjectPath, driveInterface, "Vendor");
                        model = DbusTools.getStringProperty(
                                driveObjectPath, driveInterface, "Model");
                        revision = DbusTools.getStringProperty(
                                driveObjectPath, driveInterface, "Revision");
                        serial = DbusTools.getStringProperty(
                                driveObjectPath, driveInterface, "Serial");
                        size = DbusTools.getLongProperty(
                                driveObjectPath, driveInterface, "Size");
                        connectionInterface = DbusTools.getStringProperty(
                                driveObjectPath, driveInterface, "ConnectionBus");
                        isOpticalDisc = DbusTools.getBooleanProperty(
                                driveObjectPath, driveInterface, "Optical");
                    }
                }
            } catch (SAXException | IOException
                    | ParserConfigurationException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            }
        }

        // little stupid heuristic to determine storage device type
        if (isOpticalDisc) {
            type = Type.OpticalDisc;
        } else if (device.startsWith("mmcblk")) {
            type = Type.SDMemoryCard;
        } else if ((systemInternal == false)
                && "usb".equals(connectionInterface)) {
            type = Type.USBFlashDrive;
        } else {
            type = Type.HardDrive;
        }
    }

    /**
     * creates a new StorageDevice
     *
     * @param mountPoint the mount point of the storage device
     * @param systemSize the on-disk-size of the operating system
     * @return a new StorageDevice
     * @throws DBusException if getting the device properties via d-bus fails
     * @throws IOException if reading "/proc/mounts" fails
     */
    public static StorageDevice getStorageDeviceFromMountPoint(
            String mountPoint, long systemSize)
            throws DBusException, IOException {
        LOGGER.log(Level.FINE, "mountPoint: \"{0}\"", mountPoint);
        List<String> mounts
                = LernstickFileTools.readFile(new File("/proc/mounts"));
        for (String mount : mounts) {
            String[] tokens = mount.split(" ");
            if (tokens[0].startsWith("/dev/") && tokens[1].equals(mountPoint)) {
                return new StorageDevice(tokens[0].substring(5));
            }
        }
        return null;
    }

    @Override
    public int compareTo(StorageDevice other) {
        return device.compareTo(other.getDevice());
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof StorageDevice) {
            StorageDevice otherDevice = (StorageDevice) other;
            return otherDevice.getDevice().equals(device)
                    && otherDevice.getSize() == size;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 41 * hash + (device != null ? device.hashCode() : 0);
        hash = 41 * hash + (int) (size ^ (size >>> 32));
        return hash;
    }

    @Override
    public String toString() {
        return device + ", " + type + ", "
                + LernstickFileTools.getDataVolumeString(size, 1);
    }

    /**
     * returns the type of this storage device
     *
     * @return the type of this storage device
     */
    public Type getType() {
        return type;
    }

    /**
     * returns the device node of the storage device (e.g. sda)
     *
     * @return the device node of the storage device (e.g. sda)
     */
    public String getDevice() {
        return device;
    }

    /**
     * returns the storage device vendor
     *
     * @return the storage device vendor
     */
    public String getVendor() {
        return vendor;
    }

    /**
     * returns the storage device model
     *
     * @return the storage device model
     */
    public String getModel() {
        return model;
    }

    /**
     * returns the revision of this storage device
     *
     * @return the revision of this storage device
     */
    public String getRevision() {
        return revision;
    }

    /**
     * returns the serial of the storage device
     *
     * @return the serial of the storage device
     */
    public String getSerial() {
        return serial;
    }

    /**
     * returns the size of the storage device (in Byte)
     *
     * @return the size of the storage device (in Byte)
     */
    public long getSize() {
        return size;
    }

    /**
     * returns <code>true</code>, if this device is removable,
     * <code>false</code> otherwise
     *
     * @return <code>true</code>, if this device is removable,
     * <code>false</code> otherwise
     */
    public boolean isRemovable() {
        return removable;
    }

    /**
     * returns <code>true</code>, if this device is a RAID, <code>false</code>
     * otherwise
     *
     * @return <code>true</code>, if this device is a RAID, <code>false</code>
     * otherwise
     */
    public boolean isRaid() {
        return raid;
    }

    /**
     * returns the RAID level or <code>null</code>, if this is no RAID
     *
     * @return the RAID level or <code>null</code>, if this is no RAID
     */
    public String getRaidLevel() {
        return raidLevel;
    }

    /**
     * returns the number of devices in this RAID
     *
     * @return the number of devices in this RAID
     */
    public int getRaidDeviceCount() {
        return raidDeviceCount;
    }

    /**
     * returns the list of partitions of this storage device
     *
     * @return the list of partitions of this storage device
     */
    public synchronized List<Partition> getPartitions() {
        if (partitions == null) {
            // create new list
            partitions = new ArrayList<>();

            List<String> partitionStings = DbusTools.getPartitions();
            Pattern pattern = Pattern.compile(device + "(\\d+)");
            for (String partitionString : partitionStings) {
                Matcher matcher = pattern.matcher(partitionString);
                if (matcher.matches()) {
                    partitions.add(parsePartition(matcher.group(1)));
                }
            }

            LOGGER.log(Level.INFO, "found {0} partitions on {1}",
                    new Object[]{partitions.size(), device});
        }
        return partitions;
    }

    /**
     * returns if and how the storage device can be upgraded
     *
     * @param enlargedSystemSize the enlarged system size
     * @return if and how the storage device can be upgraded
     * @throws DBusException if a dbus exception occurs
     * @throws java.io.IOException if determining the size of /home and
     * /etc/cups fails
     */
    public synchronized UpgradeVariant getUpgradeVariant(
            long enlargedSystemSize) throws DBusException, IOException {

        // lazy initialization of upgradeVariant
        if (upgradeVariant != null) {
            return upgradeVariant;
        }

        // !!! must be called before the next check, otherwise bootPartition
        // !!! could still be null even when there is one
        getPartitions();

        if (systemPartition == null) {
            noUpgradeReason = STRINGS.getString("No_System_Partition_Found");
            upgradeVariant = UpgradeVariant.IMPOSSIBLE;
            return upgradeVariant;
        }

        if (dataPartition == null) {
            noUpgradeReason = STRINGS.getString("No_Data_Partition_Found");
            upgradeVariant = UpgradeVariant.IMPOSSIBLE;
            return upgradeVariant;
        }

        // determine the size of the data to keep (/home and /etc/cups)
        MountInfo systemMountInfo = systemPartition.mount();
        List<String> readOnlyMountPoints = LernstickFileTools.mountAllSquashFS(
                systemMountInfo.getMountPath());
        MountInfo dataMountInfo = dataPartition.mount();
        String dataMountPoint = dataMountInfo.getMountPath();

        File cowDir;
        Path homePath = Paths.get(dataMountPoint, "home");
        if (Files.exists(homePath)) {
            // up to Debian 8
            cowDir = LernstickFileTools.mountAufs(
                    dataMountPoint, readOnlyMountPoints);
        } else {
            // starting with Debian 9
            cowDir = LernstickFileTools.mountOverlay(
                    dataMountPoint + "/rw", readOnlyMountPoints);
        }
        File homeDir = new File(cowDir, "home");
        long homeSize = LernstickFileTools.getSize(homeDir.toPath());
        LOGGER.log(Level.INFO, "homeSize : {0}",
                LernstickFileTools.getDataVolumeString(homeSize, 1));
        File cupsDir = new File(cowDir, "etc/cups");
        long cupsSize = LernstickFileTools.getSize(cupsDir.toPath());
        LOGGER.log(Level.INFO, "cupsSize : {0}",
                LernstickFileTools.getDataVolumeString(cupsSize, 1));
        long oldDataSize = homeSize + cupsSize;
        LOGGER.log(Level.INFO, "oldDataSize : {0}",
                LernstickFileTools.getDataVolumeString(oldDataSize, 1));
        // add a safety factor for file system overhead
        long oldDataSizeEnlarged = (long) (oldDataSize * 1.1);
        LOGGER.log(Level.INFO, "oldDataSizeEnlarged : {0}",
                LernstickFileTools.getDataVolumeString(
                        oldDataSizeEnlarged, 1));
        long dataPartitionSize = dataPartition.getSize();
        LOGGER.log(Level.INFO, "dataPartitionSize : {0}",
                LernstickFileTools.getDataVolumeString(dataPartitionSize, 1));
        // umount all temporary mountpoints
        LernstickFileTools.umount(cowDir.getPath());
        for (String readOnlyMountPoint : readOnlyMountPoints) {
            LernstickFileTools.umount(readOnlyMountPoint);
        }
        if (!dataMountInfo.alreadyMounted()) {
            dataPartition.umount();
        }

        if (oldDataSizeEnlarged > dataPartitionSize) {
            noUpgradeReason = STRINGS.getString("Data_Partition_Too_Small");
            upgradeVariant = UpgradeVariant.IMPOSSIBLE;
            return upgradeVariant;
        }

        // check partitioning schema
        if (efiPartition == null) {
            // old partitioning schema without any efi partition
            setDestructiveUpgradeVariant();
            return upgradeVariant;

        } else {
            switch (efiPartition.getNumber()) {
                case 1:
                    // fine, current partitioning schema
                    break;
                case 2:
                    if ((exchangePartition != null)
                            && (exchangePartition.getNumber() == 1)) {
                        // fine, we have the partitioning schema for
                        // older, *removable* USB flash drives:
                        //  1. partition: exchange
                        //  2. partition: boot
                        break;
                    } else {
                        // unknown partitioning schema
                        setDestructiveUpgradeVariant();
                        return upgradeVariant;
                    }
                default:
                    // unknown partitioning schema
                    setDestructiveUpgradeVariant();
                    return upgradeVariant;
            }
        }

        // Even though we already know the system partition we need to loop
        // here because we also need to know the previous partition to
        // decide if we can repartition the device.
        long remaining = -1;
        Partition previousPartition = null;
        for (Partition partition : partitions) {
            if (partition.isSystemPartition()) {
                long partitionSize = partition.getSize();
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.log(Level.INFO,
                            "enlargedSystemSize: {1}, size of {2}: {3}",
                            new Object[]{
                                enlargedSystemSize,
                                partition.getDeviceAndNumber(),
                                partitionSize
                            });
                }
                remaining = partitionSize - enlargedSystemSize;
                LOGGER.log(Level.FINE, "remaining = {0}", remaining);
                if (remaining >= 0) {
                    // the new system fits into the current system partition
                    upgradeVariant = UpgradeVariant.REGULAR;
                    return upgradeVariant;
                }

                // The new system is larger than the current system
                // partition. Check if repartitioning is possible.
                //
                // TODO: more sophisticated checks
                //  - device with partition gaps
                //  - expand in both directions
                //  - ...
                if ((previousPartition != null)
                        && (!previousPartition.isExtended())) {
                    // right now we can only resize ext partitions
                    if (previousPartition.hasExtendedFilesystem()) {
                        long previousUsedSpace;
                        if (previousPartition.isPersistencePartition()) {
                            previousUsedSpace
                                    = previousPartition.getUsedSpace(true);
                        } else {
                            previousUsedSpace
                                    = previousPartition.getUsedSpace(false);
                        }

                        long usableSpace
                                = previousPartition.getSize()
                                - previousUsedSpace;
                        if (usableSpace > Math.abs(remaining)) {
                            upgradeVariant = UpgradeVariant.REPARTITION;
                            return upgradeVariant;
                        }
                    }
                }

                // we already found the system partition
                // no need to search further
                break;
            }
            previousPartition = partition;
        }

        if (remaining < 0) {
            noUpgradeReason = STRINGS.getString("System_Partition_Too_Small");
            upgradeVariant = UpgradeVariant.IMPOSSIBLE;
        }

        return upgradeVariant;
    }

    /**
     * returns the reason why this storage device can not be upgraded
     *
     * @return the noUpgradeReason
     */
    public String getNoUpgradeReason() {
        return noUpgradeReason;
    }

    /**
     * returns the EFI partition of this storage device
     *
     * @return the EFI partition of this storage device
     */
    public synchronized Partition getEfiPartition() {
        getPartitions();
        return efiPartition;
    }

    /**
     * returns the system partition of this storage device
     *
     * @return the system partition of this storage device
     */
    public synchronized Partition getSystemPartition() {
        getPartitions();
        return systemPartition;
    }

    /**
     * returns the data partition of this storage device
     *
     * @return the data partition of this storage device
     */
    public Partition getDataPartition() {
        getPartitions();
        return dataPartition;
    }

    /**
     * returns the exchange partition of this storage device
     *
     * @return the exchange partition of this storage device
     */
    public Partition getExchangePartition() {
        getPartitions();
        return exchangePartition;
    }

    /**
     *
     * @param numberString the partition number as a string, e.g. "1" for sda1
     * @return the partition
     */
    private Partition parsePartition(String numberString) {
        Partition partition = null;
        try {
            partition = Partition.getPartitionFromDevice(device, numberString);
            if (partition.isPersistencePartition()) {
                dataPartition = partition;
            } else if (partition.isEfiPartition()) {
                // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                // ! put the EFI partition check before the exchange    !
                // ! partition check because it is the more specific one !
                // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                efiPartition = partition;
            } else if (partition.isExchangePartition()) {
                exchangePartition = partition;
            } else if (partition.isSystemPartition()) {
                // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                // ! put the system partition check at the end of the list   !
                // ! because it is the most expensive one                    !
                // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                systemPartition = partition;
            }
        } catch (NumberFormatException | DBusException numberFormatException) {
            LOGGER.log(Level.WARNING, "", numberFormatException);
        }
        return partition;
    }

    private void setDestructiveUpgradeVariant() {
        if ((dataPartition == null) && (exchangePartition == null)) {
            upgradeVariant = UpgradeVariant.INSTALLATION;
        } else {
            upgradeVariant = UpgradeVariant.BACKUP;
        }
    }
}
