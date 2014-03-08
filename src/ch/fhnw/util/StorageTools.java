package ch.fhnw.util;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * some tools for storage devices
 *
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class StorageTools {

    private static final Logger LOGGER
            = Logger.getLogger(StorageTools.class.getName());
    private static final String DEBIAN_LIVE_SYSTEM_PATH
            = "/lib/live/mount/medium";
    private static final String SYSTEM_PARTITION_LABEL = "system";
    private static final long SYSTEM_SIZE;
    private static final long SYSTEM_SIZE_ENLARGED;
    // SIZE_FACTOR is >1 so that we leave some space for updates, etc...
    private static final float SIZE_FACTOR = 1.1f;

    private static StorageDevice systemStorageDevice;

    static {
        File system = new File(DEBIAN_LIVE_SYSTEM_PATH);
        SYSTEM_SIZE = system.getTotalSpace() - system.getFreeSpace();
        LOGGER.log(Level.FINEST, "systemSpace: {0}", SYSTEM_SIZE);
        SYSTEM_SIZE_ENLARGED = (long) (SYSTEM_SIZE * SIZE_FACTOR);
        LOGGER.log(Level.FINEST, "systemSize: {0}", SYSTEM_SIZE_ENLARGED);
    }

    /**
     * returns the system size
     *
     * @return the system size
     */
    public static long getSystemSize() {
        return SYSTEM_SIZE;
    }

    /**
     * returns the enlarged system size
     *
     * @return the enlarged system size
     */
    public static long getEnlargedSystemSize() {
        return SYSTEM_SIZE_ENLARGED;
    }

    /**
     * returns the storage device of the system partition
     *
     * @return the storage device of the system partition
     * @throws DBusException if getting the StorageDevice properties via d-bus
     * fails
     * @throws IOException if reading "/proc/mounts" fails
     */
    public static synchronized StorageDevice getSystemStorageDevice()
            throws DBusException, IOException {
        if (systemStorageDevice == null) {
            Partition systemPartition = Partition.getPartitionFromMountPoint(
                    DEBIAN_LIVE_SYSTEM_PATH, SYSTEM_PARTITION_LABEL,
                    SYSTEM_SIZE);
            LOGGER.log(Level.INFO, "system partition: {0}", systemPartition);

            if (systemPartition == null) {
                // not booted from a partition but from a device itself,
                // e.g. an isohybrid image on a usb flash drive
                systemStorageDevice
                        = StorageDevice.getStorageDeviceFromMountPoint(
                                DEBIAN_LIVE_SYSTEM_PATH, SYSTEM_PARTITION_LABEL,
                                SYSTEM_SIZE);
            } else {
                systemStorageDevice = systemPartition.getStorageDevice();
            }
        }
        return systemStorageDevice;
    }
}
