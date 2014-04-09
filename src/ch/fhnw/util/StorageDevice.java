package ch.fhnw.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.freedesktop.dbus.exceptions.DBusException;

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
    private static final Logger LOGGER
            = Logger.getLogger(StorageDevice.class.getName());
    private static final ResourceBundle STRINGS
            = ResourceBundle.getBundle("ch/fhnw/util/Strings");
    private final String device;
    private final String vendor;
    private final String model;
    private final String revision;
    private final String serial;
    private final long size;
    private final long systemSize;
    private final boolean systemInternal;
    private final String connectionInterface;
    private final Type type;
    private List<Partition> partitions;
    private Boolean canBeUpgraded;
    private boolean needsRepartitioning;
    private String noUpgradeReason;
    private Partition exchangePartition;
    private Partition dataPartition;
    private Partition bootPartition;
    private Partition systemPartition;

    /**
     * creates a new StorageDevice
     *
     * @param device the unix device name, e.g. "sda"
     * @param systemSize the size of the currently running Debian Live system
     * @throws DBusException if getting the device properties via d-bus fails
     */
    public StorageDevice(String device, long systemSize) throws DBusException {
        this.device = device;
        this.systemSize = systemSize;
        vendor = DbusTools.getStringProperty(device, "DriveVendor");
        model = DbusTools.getStringProperty(device, "DriveModel");
        revision = DbusTools.getStringProperty(device, "DriveRevision");
        serial = DbusTools.getStringProperty(device, "DriveSerial");
        size = DbusTools.getLongProperty(device, "DeviceSize");
        systemInternal = DbusTools.getBooleanProperty(
                device, "DeviceIsSystemInternal");
        connectionInterface = DbusTools.getStringProperty(
                device, "DriveConnectionInterface");
        boolean isOpticalDisc = DbusTools.getBooleanProperty(
                device, "DeviceIsOpticalDisc");

        // little stupid heuristic to determine storage device type
        if (isOpticalDisc) {
            type = Type.OpticalDisc;
        } else {
            if (device.startsWith("mmcblk")) {
                type = Type.SDMemoryCard;
            } else {
                if (connectionInterface.equals("usb")
                        && (systemInternal == false)) {
                    type = Type.USBFlashDrive;
                } else {
                    type = Type.HardDrive;
                }
            }
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
                return new StorageDevice(tokens[0].substring(5), systemSize);
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
     * returns <code>true</code>, if this device is system internal,
     * <code>false</code> otherwise
     *
     * @return <code>true</code>, if this device is system internal,
     * <code>false</code> otherwise
     */
    public boolean isSystemInternal() {
        return systemInternal;
    }

    /**
     * returns the list of partitions of this storage device
     *
     * @return the list of partitions of this storage device
     */
    public synchronized List<Partition> getPartitions() {
        if (partitions == null) {
            // create new list
            partitions = new ArrayList<Partition>();

            // call udisks to get partition info
            ProcessExecutor processExecutor = new ProcessExecutor();
            LOGGER.log(Level.INFO,
                    "calling \"udisks --enumerate\" to get the partitions of {0}",
                    device);
            processExecutor.executeProcess(true, true, "udisks", "--enumerate");
            List<String> lines = processExecutor.getStdOutList();
            Collections.sort(lines);

            // parse udisks output, example:
            //      /org/freedesktop/UDisks/devices/sdb
            //      /org/freedesktop/UDisks/devices/sda1
            //      /org/freedesktop/UDisks/devices/sda2
            //      /org/freedesktop/UDisks/devices/sr0
            //      /org/freedesktop/UDisks/devices/sdb2
            //      /org/freedesktop/UDisks/devices/loop0
            //      /org/freedesktop/UDisks/devices/loop1
            //      /org/freedesktop/UDisks/devices/sdb1
            //      /org/freedesktop/UDisks/devices/sda
            // we only want to catch the partition numbers...
            Pattern pattern = Pattern.compile(
                    "/org/freedesktop/UDisks/devices/" + device + "(\\d+)");
            for (String line : lines) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    partitions.add(parsePartition(matcher));
                }
            }

            LOGGER.log(Level.INFO, "found {0} partitions on {1}",
                    new Object[]{partitions.size(), device});
        }
        return partitions;
    }

    /**
     * returns <code>true</code>, if this storage device can be upgraded,
     * <code>false</code> otherwise
     *
     * @return <code>true</code>, if this storage device can be upgraded,
     * <code>false</code> otherwise
     * @throws DBusException if a dbus exception occurs
     */
    public synchronized boolean canBeUpgraded() throws DBusException {
        // lazy initialization of canBeUpgraded
        if (canBeUpgraded == null) {
            canBeUpgraded = false;

            // !!! must be called before the next chech, otherwise bootPartition
            // !!! could still be null even when there is one
            getPartitions();

            // check if we have a current partitioning schema
            if ((bootPartition == null) || (bootPartition.getNumber() != 1)) {
                noUpgradeReason = STRINGS.getString("Deprecated_Partitioning");
                return false;
            }

            long remaining = -1;
            Partition previousPartition = null;
            for (Partition partition : partitions) {
                if (partition.isSystemPartition()) {
                    long partitionSize = partition.getSize();
                    // wild guess: give file system maximum 1% overhead...
                    long saveSystemSize = (long) (systemSize * 1.01);
                    if (LOGGER.isLoggable(Level.INFO)) {
                        LOGGER.log(Level.INFO,
                                "systemSize: {0}, saveSystemSize: {1}, size of {2}: {3}",
                                new Object[]{
                                    systemSize, saveSystemSize,
                                    partition.getDeviceAndNumber(),
                                    partitionSize
                                });
                    }
                    remaining = partitionSize - saveSystemSize;
                    LOGGER.log(Level.FINE, "remaining = {0}", remaining);
                    if (remaining >= 0) {
                        canBeUpgraded = true;
                        break; // for
                    }

                    // TODO: more sophisticated checks
                    //  - device with partition gaps
                    //  - expand in both directions
                    //  - ...
                    // check if repartitioning is possible
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
                                canBeUpgraded = true;
                                needsRepartitioning = true;
                                break; // for
                            }
                        }
                    }
                }
                previousPartition = partition;
            }
            if (remaining < 0) {
                if (systemPartition == null) {
                    noUpgradeReason
                            = STRINGS.getString("No_System_Partition_Found");
                } else {
                    noUpgradeReason
                            = STRINGS.getString("System_Partition_Too_Small");
                }
            }
        }
        return canBeUpgraded;
    }

    /**
     * checks if the storage device must be repartitioned when upgraded
     *
     * @return <code>true</code>, if the system must be repartitioned when
     * upgraded, <code>false</code> otherwise
     */
    public boolean needsRepartitioning() {
        return needsRepartitioning;
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
     * returns the system partition of this storage device
     *
     * @return the system partition of this storage device
     */
    public synchronized Partition getBootPartition() {
        getPartitions();
        return bootPartition;
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

    private Partition parsePartition(Matcher matcher) {
        try {
            String numberString = matcher.group(1);
            Partition partition = Partition.getPartitionFromDevice(
                    device, numberString, systemSize);
            if (partition.isPersistencePartition()) {
                dataPartition = partition;
            } else if (partition.isBootPartition()) {
                // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                // ! put the boot partition check before the exchange    !
                // ! partition check because it is the more specific one !
                // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                bootPartition = partition;
            } else if (partition.isExchangePartition()) {
                exchangePartition = partition;
            } else if (partition.isSystemPartition()) {
                // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                // ! put the system partition check at the end of the list   !
                // ! because it is the most expensive one                    !
                // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                systemPartition = partition;
            }
            return partition;
        } catch (NumberFormatException numberFormatException) {
            LOGGER.log(Level.WARNING, "", numberFormatException);
        } catch (DBusException ex) {
            LOGGER.log(Level.WARNING, "", ex);
        }
        return null;
    }
}
