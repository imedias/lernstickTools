package ch.fhnw.util;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.UInt64;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.udisks.Device;

/**
 * A collection of useful functions regarding dbus-java
 *
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class DbusTools {

    public enum DbusVersion {

        V1, V2
    };
    public static final DbusVersion DBUS_VERSION;
    private static final Logger LOGGER
            = Logger.getLogger(DbusTools.class.getName());
    private static DBusConnection dbusSystemConnection;
    private static final String busName;
    private static final String deviceObjectPath;

    static {
        boolean v1found = false;
        boolean v2found = false;
        try {
            // get system connection
            dbusSystemConnection = DBusConnection.getConnection(
                    DBusConnection.SYSTEM);

            // determine udisks version
            try {
                // this only works with udisks v1
                DBus.Properties version = dbusSystemConnection.getRemoteObject(
                        "org.freedesktop.UDisks", "DaemonVersion",
                        DBus.Properties.class);
                v1found = true;

            } catch (DBusException dBusException) {
                // this only works with udisks v2
                DBus.Properties version = dbusSystemConnection.getRemoteObject(
                        "org.freedesktop.UDisks2.Manager", "Version",
                        DBus.Properties.class);
                v2found = true;
            }
        } catch (DBusException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }

        if (v1found) {
            DBUS_VERSION = DbusVersion.V1;
        } else if (v2found) {
            DBUS_VERSION = DbusVersion.V2;
        } else {
            DBUS_VERSION = null;
        }

        if (DBUS_VERSION == DbusVersion.V1) {
            busName = "org.freedesktop.UDisks";
            deviceObjectPath = "/org/freedesktop/UDisks/devices/";
        } else {
            busName = "org.freedesktop.UDisks2";
            deviceObjectPath = "/org/freedesktop/UDisks2/block_devices/";
        }
    }

    /**
     * returns a dbus Device object for a given device
     *
     * @param device the given device (e.g. "sda1")
     * @return a dbus Device object for a given device
     * @throws DBusException if a d-bus exception occurs
     */
    public static Device getDevice(String device) throws DBusException {
        return dbusSystemConnection.getRemoteObject(busName,
                deviceObjectPath + device, Device.class);
    }

    public static boolean isPartition(String device) throws DBusException {
        String dbusPath = deviceObjectPath + device;
        LOGGER.log(Level.INFO, "dbusPath = \"{0}\"", dbusPath);
        DBus.Properties deviceProperties = dbusSystemConnection.getRemoteObject(
                busName, dbusPath, DBus.Properties.class);
        try {
            deviceProperties.Get(
                    "org.freedesktop.UDisks2.Partition", "Size");
            return true;
        } catch (Exception e) {
        }
        return false;
    }

    /**
     * returns a property of a partition device as a string
     *
     * @param device the device to query
     * @param property the property to query
     * @return a property of a partition device as a string
     * @throws DBusException if a d-bus exception occurs
     */
    public static String getStringProperty(String device, String property)
            throws DBusException {
        String dbusPath = deviceObjectPath + device;
        LOGGER.log(Level.INFO, "dbusPath = \"{0}\", property = \"{1}\"",
                new Object[]{dbusPath, property});
        DBus.Properties deviceProperties = dbusSystemConnection.getRemoteObject(
                busName, dbusPath, DBus.Properties.class);
        return deviceProperties.Get("org.freedesktop.UDisks", property);
    }

    /**
     * returns a property of a partition device as a list of strings
     *
     * @param device the device to query
     * @param property the property to query
     * @return a property of a partition device as a list of strings
     * @throws DBusException if a d-bus exception occurs
     */
    public static List<String> getStringListProperty(String device,
            String property) throws DBusException {
        String dbusPath = deviceObjectPath + device;
        LOGGER.log(Level.INFO, "dbusPath = \"{0}\", property = \"{1}\"",
                new Object[]{dbusPath, property});
        DBus.Properties deviceProperties = dbusSystemConnection.getRemoteObject(
                "org.freedesktop.UDisks", dbusPath, DBus.Properties.class);
        return deviceProperties.Get("org.freedesktop.UDisks", property);
    }

    /**
     * returns a property of a partition device as a long value
     *
     * @param device the device to query
     * @param property the property to query
     * @return a property of a partition device as a long value
     * @throws DBusException if a d-bus exception occurs
     */
    public static long getLongProperty(String device, String property)
            throws DBusException {
        String dbusPath = deviceObjectPath + device;
        LOGGER.log(Level.INFO, "dbusPath = \"{0}\", property = \"{1}\"",
                new Object[]{dbusPath, property});
        DBus.Properties deviceProperties = dbusSystemConnection.getRemoteObject(
                "org.freedesktop.UDisks", dbusPath, DBus.Properties.class);
        UInt64 value = deviceProperties.Get("org.freedesktop.UDisks", property);
        return value.longValue();
    }

    /**
     * returns a property of a partition device as a boolean value
     *
     * @param device the device to query
     * @param property the property to query
     * @return a property of a partition device as a boolean value
     * @throws DBusException if a d-bus exception occurs
     */
    public static Boolean getBooleanProperty(String device, String property)
            throws DBusException {
        String dbusPath = deviceObjectPath + device;
        LOGGER.log(Level.INFO, "dbusPath = \"{0}\", property = \"{1}\"",
                new Object[]{dbusPath, property});
        DBus.Properties deviceProperties = dbusSystemConnection.getRemoteObject(
                "org.freedesktop.UDisks", dbusPath, DBus.Properties.class);
        return deviceProperties.Get("org.freedesktop.UDisks", property);
    }
}
