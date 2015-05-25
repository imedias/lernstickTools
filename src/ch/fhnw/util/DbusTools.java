package ch.fhnw.util;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.UInt64;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.udisks.Device;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

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
                DBus.Properties properties
                        = dbusSystemConnection.getRemoteObject(
                                "org.freedesktop.UDisks",
                                "/org/freedesktop/UDisks",
                                DBus.Properties.class);
                String udisksVersion = properties.Get(
                        "org.freedesktop.UDisks", "DaemonVersion");
                v1found = true;
                LOGGER.log(Level.INFO,
                        "detected UDisks version 1 ({0})", udisksVersion);

            } catch (DBusException | DBusExecutionException ex) {
                LOGGER.log(Level.INFO, "calling a UDisks 1 method failed with "
                        + "the following exception:", ex);

                // this only works with udisks v2
                DBus.Properties properties
                        = dbusSystemConnection.getRemoteObject(
                                "org.freedesktop.UDisks2",
                                "/org/freedesktop/UDisks2/Manager",
                                DBus.Properties.class);
                String udisksVersion = properties.Get(
                        "org.freedesktop.UDisks2.Manager", "Version");
                v2found = true;
                LOGGER.log(Level.INFO,
                        "detected UDisks version 2 ({0})", udisksVersion);
            }
        } catch (DBusException | DBusExecutionException ex) {
            LOGGER.log(Level.SEVERE, "unknown/unsupported UDisks version", ex);
        }

        if (v1found) {
            DBUS_VERSION = DbusVersion.V1;
            busName = "org.freedesktop.UDisks";
            deviceObjectPath = "/org/freedesktop/UDisks/devices/";
        } else if (v2found) {
            DBUS_VERSION = DbusVersion.V2;
            busName = "org.freedesktop.UDisks2";
            deviceObjectPath = "/org/freedesktop/UDisks2/block_devices/";
        } else {
            DBUS_VERSION = null;
            busName = null;
            deviceObjectPath = null;
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

    public static List<String> getPartitions() {
        // unfortunately, this sucks with dbus, we better parse /proc/partitions
        //
        // the format of the /proc/partitions file looks like this:
        // major minor  #blocks  name
        // 11        0    4097694 sr0        
        List<String> lines = null;
        try {
            lines = LernstickFileTools.readFile(new File("/proc/partitions"));
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
        Pattern pattern = Pattern.compile("\\p{Space}*\\p{Digit}+\\p{Space}+"
                + "\\p{Digit}+\\p{Space}+\\p{Digit}+\\p{Space}+(\\p{Alnum}+)");
        List<String> partitions = new ArrayList<>();
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                partitions.add(matcher.group(1));
            }
        }
        Collections.sort(partitions);
        return partitions;
    }

    public static byte[] removeNullByte(byte[] input) {
        byte[] output = new byte[input.length - 1];
        System.arraycopy(input, 0, output, 0, output.length);
        return output;
    }

    public static boolean isPartition(String device) throws DBusException {
        String dbusPath = deviceObjectPath + device;
        LOGGER.log(Level.INFO, "dbusPath = \"{0}\"", dbusPath);
        try {
            List<String> interfaceNames = getInterfaceNames(dbusPath);
            boolean isPartition = interfaceNames.contains(
                    "org.freedesktop.UDisks2.Partition");
            String logMessage
                    = "{0} is " + (isPartition ? "a" : "no") + " partition";
            LOGGER.log(Level.FINE, logMessage, dbusPath);
            return isPartition;
        } catch (SAXException | IOException | ParserConfigurationException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
            return false;
        }
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
        return deviceProperties.Get(busName, property);
    }

    /**
     * returns a property of a partition device as a string
     *
     * @param device the device to query
     * @param interfaceName the interface to query
     * @param property the property to query
     * @return a property of a partition device as a string
     * @throws DBusException if a d-bus exception occurs
     */
    public static String getDeviceStringProperty(String device,
            String interfaceName, String property) throws DBusException {
        String objectPath = deviceObjectPath + device;
        return getStringProperty(objectPath, interfaceName, property);
    }

    /**
     * returns a property of an object as a string
     *
     * @param objectPath the object to query
     * @param interfaceName the interface to query
     * @param property the property to query
     * @return a property of a partition device as a string
     * @throws DBusException if a d-bus exception occurs
     */
    public static String getStringProperty(String objectPath,
            String interfaceName, String property) throws DBusException {
        LOGGER.log(Level.INFO, "objectPath = \"{0}\", interfaceName = \"{1}\", "
                + "property = \"{2}\"",
                new Object[]{objectPath, interfaceName, property});
        DBus.Properties stringProperty = dbusSystemConnection.getRemoteObject(
                busName, objectPath, DBus.Properties.class);
        return stringProperty.Get(interfaceName, property);
    }

    /**
     * returns a property of an object as a byte array
     *
     * @param objectPath the object to query
     * @param interfaceName the interface to query
     * @param property the property to query
     * @return a property of a partition device as a string
     * @throws DBusException if a d-bus exception occurs
     */
    public static byte[] getByteArrayProperty(String objectPath,
            String interfaceName, String property) throws DBusException {
        LOGGER.log(Level.INFO, "objectPath = \"{0}\", interfaceName = \"{1}\", "
                + "property = \"{2}\"",
                new Object[]{objectPath, interfaceName, property});
        DBus.Properties stringProperty = dbusSystemConnection.getRemoteObject(
                busName, objectPath, DBus.Properties.class);
        return stringProperty.Get(interfaceName, property);
    }

    /**
     * returns a property of an object as a list of lists
     *
     * @param objectPath the object to query
     * @param interfaceName the interface to query
     * @param property the property to query
     * @return a property of a partition device as a string
     * @throws DBusException if a d-bus exception occurs
     */
    public static List<List> getListListProperty(String objectPath,
            String interfaceName, String property) throws DBusException {
        LOGGER.log(Level.INFO, "objectPath = \"{0}\", interfaceName = \"{1}\", "
                + "property = \"{2}\"",
                new Object[]{objectPath, interfaceName, property});
        DBus.Properties stringProperty = null;
        try {
            stringProperty = dbusSystemConnection.getRemoteObject(
                    busName, objectPath, DBus.Properties.class);
        } catch (DBusException dBusException) {
            LOGGER.log(Level.WARNING, "", dBusException);
        }
        return stringProperty.Get(interfaceName, property);
    }

    /**
     * returns a property of a device as a Path
     *
     * @param device the device to query
     * @param interfaceName the interface to query
     * @param property the property to query
     * @return a property of a partition device as a string
     * @throws DBusException if a d-bus exception occurs
     */
    public static Path getDevicePathProperty(String device,
            String interfaceName, String property) throws DBusException {
        String objectPath = deviceObjectPath + device;
        return getPathProperty(objectPath, interfaceName, property);
    }

    /**
     * returns a property of an object as a Path
     *
     * @param objectPath the object to query
     * @param interfaceName the interface to query
     * @param property the property to query
     * @return a property of a partition device as a string
     * @throws DBusException if a d-bus exception occurs
     */
    public static Path getPathProperty(String objectPath,
            String interfaceName, String property) throws DBusException {
        LOGGER.log(Level.INFO, "objectPath = \"{0}\", interfaceName = \"{1}\", "
                + "property = \"{2}\"",
                new Object[]{objectPath, interfaceName, property});
        DBus.Properties stringProperty = dbusSystemConnection.getRemoteObject(
                busName, objectPath, DBus.Properties.class);
        return stringProperty.Get(interfaceName, property);
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
                busName, dbusPath, DBus.Properties.class);
        return deviceProperties.Get(busName, property);
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
                busName, dbusPath, DBus.Properties.class);
        UInt64 value = deviceProperties.Get(busName, property);
        return value.longValue();
    }

    /**
     * returns a property of a partition device as a long value
     *
     * @param device the device to query
     * @param interfaceName the interface to query
     * @param property the property to query
     * @return a property of a partition device as a long value
     * @throws DBusException if a d-bus exception occurs
     */
    public static long getDeviceLongProperty(String device,
            String interfaceName, String property) throws DBusException {
        String objectPath = deviceObjectPath + device;
        return getLongProperty(objectPath, interfaceName, property);
    }

    /**
     * returns a property of a partition device as a long value
     *
     * @param objectPath the object to query
     * @param interfaceName the interface to query
     * @param property the property to query
     * @return a property of a partition device as a long value
     * @throws DBusException if a d-bus exception occurs
     */
    public static long getLongProperty(String objectPath,
            String interfaceName, String property) throws DBusException {
        LOGGER.log(Level.INFO, "objectPath = \"{0}\", interfaceName = \"{1}\", "
                + "property = \"{2}\"",
                new Object[]{objectPath, interfaceName, property});
        DBus.Properties properties = dbusSystemConnection.getRemoteObject(
                busName, objectPath, DBus.Properties.class);
        UInt64 value = properties.Get(interfaceName, property);
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
                busName, dbusPath, DBus.Properties.class);
        return deviceProperties.Get(busName, property);
    }

    /**
     * returns a property of a partition device as a boolean value
     *
     * @param device the device to query
     * @param interfaceName the interface to query
     * @param property the property to query
     * @return a property of a partition device as a boolean value
     * @throws DBusException if a d-bus exception occurs
     */
    public static Boolean getDeviceBooleanProperty(String device,
            String interfaceName, String property) throws DBusException {
        String objectPath = deviceObjectPath + device;
        return getBooleanProperty(objectPath, interfaceName, property);
    }

    /**
     * returns a property of a partition device as a boolean value
     *
     * @param objectPath the object path to query
     * @param interfaceName the interface to query
     * @param property the property to query
     * @return a property of a partition device as a boolean value
     * @throws DBusException if a d-bus exception occurs
     */
    public static Boolean getBooleanProperty(String objectPath,
            String interfaceName, String property) throws DBusException {
        LOGGER.log(Level.INFO, "objectPath = \"{0}\", interfaceName = \"{1}\", "
                + "property = \"{2}\"",
                new Object[]{objectPath, interfaceName, property});
        DBus.Properties stringProperty = dbusSystemConnection.getRemoteObject(
                busName, objectPath, DBus.Properties.class);
        return stringProperty.Get(interfaceName, property);
    }

    public static List<String> getDeviceInterfaceNames(String device)
            throws DBusException, SAXException, IOException,
            ParserConfigurationException {
        return getInterfaceNames(deviceObjectPath + device);
    }

    public static List<String> getInterfaceNames(String objectPath)
            throws DBusException, SAXException, IOException,
            ParserConfigurationException {

        // introspect object path via dbus
        DBus.Introspectable introspectable
                = (DBus.Introspectable) dbusSystemConnection.getRemoteObject(
                        busName, objectPath, DBus.Introspectable.class);
        String xml = introspectable.Introspect();

        // parse xml
        DocumentBuilderFactory documentBuilderFactory
                = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder
                = documentBuilderFactory.newDocumentBuilder();
        InputSource inputSource = new InputSource(new StringReader(xml));
        Document document = documentBuilder.parse(inputSource);

        // get list of interfaces
        Element rootElement = document.getDocumentElement();
        NodeList interfaceNodeList
                = rootElement.getElementsByTagName("interface");
        int length = interfaceNodeList.getLength();
        List<String> interfaceNames = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            NamedNodeMap attributes = interfaceNodeList.item(i).getAttributes();
            interfaceNames.add(attributes.getNamedItem("name").getNodeValue());
        }
        
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(objectPath);
        stringBuilder.append(" has the follwing interfaces:\n");
        for (int i = 0, size = interfaceNames.size(); i < size; i++) {
            stringBuilder.append('\t');
            stringBuilder.append(interfaceNames.get(i));
            if (i != size - 1) {
                stringBuilder.append(System.lineSeparator());
            }
        }
        String logMessage = stringBuilder.toString();
        LOGGER.info(logMessage);
        
        return interfaceNames;
    }
}
