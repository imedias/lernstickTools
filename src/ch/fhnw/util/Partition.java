package ch.fhnw.util;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.udisks.Device;

/**
 * A storage device partition
 *
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class Partition {

    /**
     * the label used for boot partitions
     */
    public final static String BOOT_LABEL = "boot";
    /**
     * the label used for persistence partitions
     */
    public final static String PERSISTENCE_LABEL = "persistence";
    private final static String[] LEGACY_PERSISTENCE_LABELs = new String[]{
        "live-rw"
    };
    private final static Logger LOGGER
            = Logger.getLogger(Partition.class.getName());
    private final static Pattern deviceAndNumberPattern
            = Pattern.compile("(.*)(\\d+)");
    private final String device;
    private final int number;
    private final String deviceAndNumber;
    private final long offset;
    private final long size;
    private final String type;
    private final String idLabel;
    private final String idType;
    private final long systemSize;
    private final boolean isDrive;
    private Boolean isBootPartition;
    private Boolean isSystemPartition;
    private Long usedSpace;
    private StorageDevice storageDevice;

    /**
     * creates a new Partition
     *
     * @param deviceAndNumber the device of the partition including the number
     * (e.g. "sda1")
     * @param systemSize the on-disk-size of the operating system
     * @return a new Partition
     * @throws DBusException if getting the partition properties via dbus fails
     */
    public static Partition getPartitionFromDeviceAndNumber(
            String deviceAndNumber, long systemSize) throws DBusException {
        LOGGER.log(Level.FINE, "deviceAndNumber: \"{0}\"", deviceAndNumber);
        Matcher matcher = deviceAndNumberPattern.matcher(deviceAndNumber);
        if (matcher.matches()) {
            return getPartitionFromDevice(
                    matcher.group(1), matcher.group(2), systemSize);
        }
        return null;
    }

    /**
     * creates a new Partition
     *
     * @param mountPoint the mount point of the partition
     * @param systemSize the on-disk-size of the operating system
     * @return a new Partition
     * @throws DBusException if getting the partition properties via dbus fails
     * @throws IOException if reading "/proc/mounts" fails
     */
    public static Partition getPartitionFromMountPoint(
            String mountPoint, long systemSize)
            throws DBusException, IOException {
        LOGGER.log(Level.FINE, "mountPoint: \"{0}\"", mountPoint);
        List<String> mounts
                = LernstickFileTools.readFile(new File("/proc/mounts"));
        for (String mount : mounts) {
            String[] tokens = mount.split(" ");
            if (tokens[0].startsWith("/dev/") && tokens[1].equals(mountPoint)) {
                return getPartitionFromDeviceAndNumber(
                        tokens[0].substring(5), systemSize);
            }
        }
        return null;
    }

    /**
     * creates a new Partition
     *
     * @param device the device of the partition (e.g. "sda")
     * @param numberString the number of the partition
     * @param systemSize the on-disk-size of the operating system
     * @return a String representation of the partition number
     * @throws DBusException if getting the partition properties via dbus fails
     */
    public static Partition getPartitionFromDevice(
            String device, String numberString, long systemSize)
            throws DBusException {
        LOGGER.log(Level.FINE, "device: \"{0}\", numberString: \"{1}\"",
                new Object[]{device, numberString});
        if (DbusTools.DBUS_VERSION == DbusTools.DbusVersion.V1) {
            return new Partition(
                    device, Integer.parseInt(numberString), systemSize);
        } else {
            // check, if this device is a partiton at all
            if (DbusTools.isPartition(device + numberString)) {
                return new Partition(
                        device, Integer.parseInt(numberString), systemSize);
            } else {
                return null;
            }
        }
    }

    private Partition(String device, int number, long systemSize)
            throws DBusException {
        LOGGER.log(Level.FINE, "device: \"{0}\", number = {1}",
                new Object[]{device, number});
        this.device = device;
        this.number = number;
        this.systemSize = systemSize;
        deviceAndNumber = device + number;
        offset = DbusTools.getLongProperty(deviceAndNumber, "PartitionOffset");
        size = DbusTools.getLongProperty(deviceAndNumber, "PartitionSize");
        idLabel = DbusTools.getStringProperty(deviceAndNumber, "IdLabel");
        idType = DbusTools.getStringProperty(deviceAndNumber, "IdType");
        type = DbusTools.getStringProperty(deviceAndNumber, "PartitionType");
        isDrive = DbusTools.getBooleanProperty(
                deviceAndNumber, "DeviceIsDrive");
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("/dev/");
        stringBuilder.append(deviceAndNumber);
        stringBuilder.append(", offset: ");
        stringBuilder.append(offset);
        stringBuilder.append(", size: ");
        stringBuilder.append(size);
        if ((idLabel != null) && !idLabel.isEmpty()) {
            stringBuilder.append(", idLabel: \"");
            stringBuilder.append(idLabel);
            stringBuilder.append('\"');
        }
        if ((idType != null) && !idType.isEmpty()) {
            stringBuilder.append(", idType: \"");
            stringBuilder.append(idType);
            stringBuilder.append('\"');
        }
        if ((type != null) && !type.isEmpty()) {
            stringBuilder.append(", type: \"");
            stringBuilder.append(type);
            stringBuilder.append('\"');
        }
        return stringBuilder.toString();
    }

    /**
     * returns the StorageDevice of this Partition
     *
     * @return the StorageDevice of this Partition
     * @throws DBusException if getting the StorageDevice properties via d-bus
     * fails
     */
    public synchronized StorageDevice getStorageDevice() throws DBusException {
        if (storageDevice == null) {
            storageDevice = new StorageDevice(
                    isDrive ? deviceAndNumber : device, systemSize);
        }
        return storageDevice;
    }

    /**
     * returns the partition number
     *
     * @return the partition number
     */
    public int getNumber() {
        return number;
    }

    /**
     * returns the device and number of this partition, e.g. "sda1"
     *
     * @return the device and number of this partition, e.g. "sda1"
     */
    public String getDeviceAndNumber() {
        return deviceAndNumber;
    }

    /**
     * returns the offset (start) of the partition
     *
     * @return the offset (start) of the partition
     */
    public long getOffset() {
        return offset;
    }

    /**
     * returns the size of this partition
     *
     * @return the size of this partition
     */
    public long getSize() {
        return size;
    }

    /**
     * returns the label of the partition
     *
     * @return the label of the partition
     */
    public String getIdLabel() {
        return idLabel;
    }

    /**
     * returns the ID type of the partition, e.g. "vfat"
     *
     * @return the ID type of the partition, e.g. "vfat"
     */
    public String getIdType() {
        return idType;
    }

    /**
     * returns the partition type, e.g. "0x83" for Linux partitions
     *
     * @return the partition type, e.g. "0x83" for Linux partitions
     */
    public String getType() {
        return type;
    }

    /**
     * returns a list of mount paths of this partition
     *
     * @return a list of mount paths of this partition
     * @throws DBusException if a dbus exception occurs
     */
    public List<String> getMountPaths() throws DBusException {
        return DbusTools.getStringListProperty(
                deviceAndNumber, "DeviceMountPaths");
    }

    /**
     * returns the first mount path of this partition
     *
     * @return the first mount path of this partition
     * @throws DBusException if a dbus exception occurs
     */
    public String getMountPath() throws DBusException {
        List<String> mountPaths = getMountPaths();
        if (mountPaths.isEmpty()) {
            return null;
        }
        return mountPaths.get(0);
    }

    /**
     * checks if the partition is an extended partition
     *
     * @return <code>true</code>, if the partition is an extended partition,
     * <code>false</code> otherwise
     */
    public boolean isExtended() {
        return type.equals("0x05") || type.equals("0x0f");
    }

    /**
     * checks if the file system on the partition is ext[2|3|4]
     *
     * @return <code>true</code>, if the file system on the partition is
     * ext[2|3|4], <code>false</code> otherwise
     */
    public boolean hasExtendedFilesystem() {
        return idType.equals("ext2")
                || idType.equals("ext3")
                || idType.equals("ext4");
    }

    /**
     * returns the used space on this partition
     *
     * @param onlyHomeAndCups calculate space only for "/home/user/" and
     * "/etc/cups/"
     * @return the free/usable space on this partition or "-1" if the usable
     * space is unknown
     */
    public long getUsedSpace(boolean onlyHomeAndCups) {
        try {
            if (usedSpace == null) {

                // mount partition
                MountInfo mountInfo = mount();
                String mountPath = mountInfo.getMountPath();

                if (onlyHomeAndCups) {
                    // in case of an upgrade we would only keep /home/user and
                    // /etc/cups
                    long userSize = 0;
                    long cupsSize = 0;
                    ProcessExecutor processExecutor = new ProcessExecutor();
                    Pattern pattern = Pattern.compile("^(\\d+).*");
                    processExecutor.executeProcess(true, true,
                            "du", "-sb", mountPath + "/home/user");
                    String stdOut = processExecutor.getStdOut();
                    LOGGER.log(Level.INFO, "stdOut = \"{0}\"", stdOut);
                    Matcher matcher = pattern.matcher(stdOut);
                    if (matcher.find()) {
                        String userSizeString = matcher.group(1);
                        userSize = Long.parseLong(userSizeString);
                    }
                    LOGGER.log(Level.INFO, "{0}: userSize = {1}",
                            new Object[]{toString(), userSize});

                    processExecutor.executeProcess(true, true,
                            "du", "-sb", mountPath + "/etc/cups");
                    matcher = pattern.matcher(processExecutor.getStdOut());
                    if (matcher.find()) {
                        String userSizeString = matcher.group(1);
                        cupsSize = Long.parseLong(userSizeString);
                    }
                    LOGGER.log(Level.INFO, "{0}: cupsSize = {1}",
                            new Object[]{toString(), cupsSize});
                    usedSpace = userSize + cupsSize;

                } else {
                    usedSpace = size - (new File(mountPath)).getUsableSpace();
                }

                LOGGER.log(Level.INFO, "{0}: usedSpace = {1}",
                        new Object[]{toString(), usedSpace});

                // cleanup
                if (!mountInfo.alreadyMounted()) {
                    umount();
                }
            }
        } catch (DBusExecutionException ex) {
            LOGGER.log(Level.WARNING, "", ex);
            usedSpace = -1l;
        } catch (DBusException ex) {
            LOGGER.log(Level.WARNING, "", ex);
            usedSpace = -1l;
        } catch (NumberFormatException ex) {
            LOGGER.log(Level.WARNING, "", ex);
            usedSpace = -1l;
        }

        return usedSpace;
    }

    /**
     * mounts this partition via dbus/udisks
     *
     * @param options the mount options
     * @return the mount information
     * @throws DBusException if a dbus exception occurs
     */
    public MountInfo mount(String... options) throws DBusException {
        try {
            String mountPath;
            boolean wasMounted = false;
            List<String> mountPaths = getMountPaths();
            if (mountPaths.isEmpty()) {
                Device udisksDevice = DbusTools.getDevice(deviceAndNumber);
                mountPath = udisksDevice.FilesystemMount(
                        "auto", Arrays.asList(options));
            } else {
                mountPath = mountPaths.get(0);
                wasMounted = true;
                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.log(Level.FINEST, "{0} already mounted at {1}",
                            new Object[]{this, mountPath}
                    );
                }
            }
            return new MountInfo(mountPath, wasMounted);
        } catch (DBusExecutionException ex) {
            throw new DBusException(ex.getMessage());
        }
    }

    /**
     * umounts this partition via dbus/udisks
     *
     * @return <code>true</code>, if the umount operation succeeded,
     * <code>false</code> otherwise
     * @throws DBusException if a dbus exception occurs
     */
    public boolean umount() throws DBusException {
        /**
         * TODO: umount timeout problem: when there have been previous copy
         * operations, this call very often fails with the following exception:
         * org.freedesktop.DBus$Error$NoReply: No reply within specified time at
         * org.freedesktop.dbus.RemoteInvocationHandler.executeRemoteMethod(RemoteInvocationHandler.java:133)
         * at
         * org.freedesktop.dbus.RemoteInvocationHandler.invoke(RemoteInvocationHandler.java:188)
         * at $Proxy2.FilesystemUnmount(Unknown Source)
         */
        boolean success = false;
        for (int i = 0; !success && (i < 60); i++) {
            // it already happend that during the timeout
            // in handleUmountException() the umount call succeeded!
            // therefore we need to test for the mount status in every round
            // and act accordingly...
            List<String> mountPaths = getMountPaths();
            if ((mountPaths != null) && (!mountPaths.isEmpty())) {
                LOGGER.log(Level.INFO,
                        "/dev/{0} is mounted on {1}, calling umount...",
                        new Object[]{deviceAndNumber, mountPaths.get(0)});
                try {
                    Device dbusDevice = DbusTools.getDevice(deviceAndNumber);
                    dbusDevice.FilesystemUnmount(new ArrayList<String>());
                    success = true;
                } catch (DBusException ex) {
                    handleUmountException(ex);
                } catch (DBusExecutionException ex) {
                    handleUmountException(ex);
                }
            } else {
                LOGGER.log(Level.INFO,
                        "/dev/{0} was NOT mounted", deviceAndNumber);
                success = true;
            }
        }
        if (!success) {
            LOGGER.log(Level.SEVERE,
                    "Could not umount /dev/{0}", deviceAndNumber);
        }
        return success;
    }

    /**
     * returns <code>true</code>, if this partition is a Lernstick boot
     * partition, <code>false</code> otherwise
     *
     * @return <code>true</code>, if this partition is a Lernstick boot
     * partition, <code>false</code> otherwise
     * @throws DBusException if a dbus exception occurs
     */
    public boolean isBootPartition() throws DBusException {
        if (isBootPartition == null) {
            isBootPartition = false;
            LOGGER.log(Level.FINEST, "checking partition {0}", deviceAndNumber);
            LOGGER.log(Level.FINEST, "partition label: \"{0}\"", idLabel);
            if (BOOT_LABEL.equals(idLabel)) {
                isBootPartition = true;
            } else {
                LOGGER.finest("does not match system partition label");
            }
        }
        return isBootPartition;
    }

    /**
     * returns <code>true</code>, if this partition is a Debian Live system
     * partition, <code>false</code> otherwise
     *
     * @return <code>true</code>, if this partition is a Debian Live system
     * partition, <code>false</code> otherwise
     * @throws DBusException if a dbus exception occurs
     */
    public boolean isSystemPartition() throws DBusException {
        if (isSystemPartition == null) {
            isSystemPartition = false;
            LOGGER.log(Level.FINEST, "checking partition {0}", deviceAndNumber);
            LOGGER.log(Level.FINEST, "partition label: \"{0}\"", idLabel);

            try {
                // mount partition (if not already mounted)
                MountInfo mountInfo = mount();
                String mountPath = mountInfo.getMountPath();

                // check partition file structure
                LOGGER.log(Level.FINEST,
                        "checking file structure on partition {0}",
                        deviceAndNumber);
                File liveDir = new File(mountPath, "live");
                if (liveDir.exists()) {
                    FilenameFilter squashFsFilter = new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                            return name.endsWith(".squashfs");
                        }
                    };
                    String[] squashFileSystems = liveDir.list(squashFsFilter);
                    isSystemPartition = (squashFileSystems.length > 0);
                }

                // cleanup
                if (!mountInfo.alreadyMounted()) {
                    umount();
                }
            } catch (DBusExecutionException ex) {
                throw new DBusException(ex.getMessage());
            }
        }
        return isSystemPartition;
    }

    /**
     * returns <code>true</code>, if this partition is a Debian Live persistence
     * partition, <code>false</code> otherwise
     *
     * @return <code>true</code>, if this partition is a Debian Live persistence
     * partition, <code>false</code> otherwise
     */
    public boolean isPersistencePartition() {
        if (idLabel.equals(PERSISTENCE_LABEL)) {
            return true;
        }
        for (String legacyLabel : LEGACY_PERSISTENCE_LABELs) {
            if (idLabel.equals(legacyLabel)) {
                return true;
            }
        }
        return false;
    }

    /**
     * returns <code>true</code> if this partition is the exchange partition,
     * <code>false</code> otherwise
     *
     * @return <code>true</code> if this partition is the exchange partition,
     * <code>false</code> otherwise
     */
    public boolean isExchangePartition() {
        // First check the partition number:
        // The exchange partition is either the first partition (legacy version
        // where the boot partition was not the first partition) or the second
        // partition (current version where the boot partition MUST be the first
        // partition).
        if ((number != 1) && (number != 2)) {
            return false;
        }

        // Check partition and file system types:
        // The exchange partition can either be of type 0x07 (when using exFAT
        // or NTFS) or of type 0x0c (when using FAT32).
        // Older Lernstick versions even used 0x0e for the exchange partition.
        if (type.equals("0x07")) {
            // exFAT or NTFS?
            return (idType.equals("exfat") || idType.equals("ntfs"));
        } else if (type.equals("0x0c") || type.equals("0x0e")) {
            // FAT32?
            return (idType.equals("vfat"));
        }

        return false;
    }

    /**
     * returns <code>true</code>, if this partition is an active persistence
     * partition, <code>false</code> otherwise
     *
     * @return <code>true</code>, if this partition is an active persistence
     * partition, <code>false</code> otherwise
     * @throws DBusException if a dbus exception occurs
     */
    public boolean isActivePersistencePartition() throws DBusException {
        if (isPersistencePartition()) {
            List<String> mountPaths = getMountPaths();
            for (String mountPath : mountPaths) {
                if (mountPath.startsWith(
                        "/lib/live/mount/persistence/")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * returns <code>true</code>, if the partition is mounted,
     * <code>false</code> otherwise
     *
     * @return <code>true</code>, if the partition is mounted,
     * <code>false</code> otherwise
     * @throws DBusException if an dbus exception occurs
     */
    public boolean isMounted() throws DBusException {
        List<String> mountPaths = getMountPaths();
        return (mountPaths != null) && (!mountPaths.isEmpty());
    }

    private void handleUmountException(Exception ex) {
        LOGGER.log(Level.WARNING, "", ex);
        try {
            ProcessExecutor processExecutor = new ProcessExecutor();
            int returnValue = processExecutor.executeProcess(true, true,
                    "fuser", "-v", "-m", "/dev/" + deviceAndNumber);
            if (returnValue == 0) {
                //while (returnValue == 0) {
                LOGGER.log(Level.INFO, "/dev/{0} is still being used by the "
                        + "following processes:\n{1}",
                        new Object[]{
                            deviceAndNumber,
                            processExecutor.getStdOut()
                        });
                Thread.sleep(1000);
//                returnValue = processExecutor.executeProcess(true, true,
//                        "fuser", "-v", "-m", "/dev/" + deviceAndNumber);
            }
        } catch (InterruptedException ex2) {
            LOGGER.log(Level.SEVERE, "", ex2);
        }
    }
}
