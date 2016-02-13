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
import org.freedesktop.dbus.UInt32;
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

    /**
     * the different known and supported D-Bus versions
     */
    public enum DbusVersion {

        /**
         * version 1
         */
        V1,
        /**
         * version 2
         */
        V2
    };

    /**
     * the detected D-Bus version
     */
    public static final DbusVersion DBUS_VERSION;
    private static final Logger LOGGER
            = Logger.getLogger(DbusTools.class.getName());
    private static DBusConnection dbusSystemConnection;
    private static final String BUS_NAME;
    private static final String DEVICE_OBJECT_PATH;

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
            BUS_NAME = "org.freedesktop.UDisks";
            DEVICE_OBJECT_PATH = "/org/freedesktop/UDisks/devices/";
        } else if (v2found) {
            DBUS_VERSION = DbusVersion.V2;
            BUS_NAME = "org.freedesktop.UDisks2";
            DEVICE_OBJECT_PATH = "/org/freedesktop/UDisks2/block_devices/";
        } else {
            DBUS_VERSION = null;
            BUS_NAME = null;
            DEVICE_OBJECT_PATH = null;
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
        return dbusSystemConnection.getRemoteObject(BUS_NAME,
                DEVICE_OBJECT_PATH + device, Device.class);
    }

    /**
     * Returns a list of all partitions in the system. Partitions are named e.g.
     * "sda", "sr0", ...
     *
     * @return a list of partitions in the system
     */
    public static List<String> getPartitions() {
        // unfortunately, this sucks with dbus, we better parse /proc/partitions
        //
        // the format of the /proc/partitions file looks like this:
        // major minor  #blocks  name
        // 11        0    4097694 sr0        
        try {
            List<String> lines = LernstickFileTools.readFile(
                    new File("/proc/partitions"));
            Pattern pattern = Pattern.compile("\\p{Space}*\\p{Digit}+"
                    + "\\p{Space}+\\p{Digit}+\\p{Space}+\\p{Digit}+\\p{Space}+"
                    + "(\\p{Alnum}+)");
            List<String> partitions = new ArrayList<>();
            for (String line : lines) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    partitions.add(matcher.group(1));
                }
            }
            Collections.sort(partitions);
            return partitions;
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
        return null;
    }

    /**
     * removes the last byte of an array
     *
     * @param input an array
     * @return the shortened array
     */
    public static byte[] removeNullByte(byte[] input) {
        byte[] output = new byte[input.length - 1];
        System.arraycopy(input, 0, output, 0, output.length);
        return output;
    }

    /**
     * returns <code>true</code>, if the given device is a partition,
     * <code>false</code> otherwise
     *
     * @param device the given device
     * @return <code>true</code>, if the given device is a partition,
     * <code>false</code> otherwise
     * @throws DBusException
     */
    public static boolean isPartition(String device) throws DBusException {
        String dbusPath = DEVICE_OBJECT_PATH + device;
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
        String dbusPath = DEVICE_OBJECT_PATH + device;
        LOGGER.log(Level.INFO, "dbusPath = \"{0}\", property = \"{1}\"",
                new Object[]{dbusPath, property});
        DBus.Properties deviceProperties = dbusSystemConnection.getRemoteObject(
                BUS_NAME, dbusPath, DBus.Properties.class);
        return deviceProperties.Get(BUS_NAME, property);
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
        String objectPath = DEVICE_OBJECT_PATH + device;
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
                BUS_NAME, objectPath, DBus.Properties.class);
        String returnValue = stringProperty.Get(interfaceName, property);
        LOGGER.log(Level.INFO, "stringProperty = \"{0}\"", returnValue);
        return returnValue;
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
                BUS_NAME, objectPath, DBus.Properties.class);
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
        DBus.Properties stringProperty = dbusSystemConnection.getRemoteObject(
                BUS_NAME, objectPath, DBus.Properties.class);
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
        String objectPath = DEVICE_OBJECT_PATH + device;
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
                BUS_NAME, objectPath, DBus.Properties.class);
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
        String dbusPath = DEVICE_OBJECT_PATH + device;
        LOGGER.log(Level.INFO, "dbusPath = \"{0}\", property = \"{1}\"",
                new Object[]{dbusPath, property});
        DBus.Properties deviceProperties = dbusSystemConnection.getRemoteObject(
                BUS_NAME, dbusPath, DBus.Properties.class);
        return deviceProperties.Get(BUS_NAME, property);
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
        String dbusPath = DEVICE_OBJECT_PATH + device;
        LOGGER.log(Level.INFO, "dbusPath = \"{0}\", property = \"{1}\"",
                new Object[]{dbusPath, property});
        DBus.Properties deviceProperties = dbusSystemConnection.getRemoteObject(
                BUS_NAME, dbusPath, DBus.Properties.class);
        UInt64 value = deviceProperties.Get(BUS_NAME, property);
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
        String objectPath = DEVICE_OBJECT_PATH + device;
        return getLongProperty(objectPath, interfaceName, property);
    }

    /**
     * returns a property as an int value
     *
     * @param objectPath the object to query
     * @param interfaceName the interface to query
     * @param property the property to query
     * @return a property of a partition device as a long value
     * @throws DBusException if a d-bus exception occurs
     */
    public static int getIntProperty(String objectPath,
            String interfaceName, String property) throws DBusException {
        LOGGER.log(Level.INFO, "objectPath = \"{0}\", interfaceName = \"{1}\", "
                + "property = \"{2}\"",
                new Object[]{objectPath, interfaceName, property});
        DBus.Properties properties = dbusSystemConnection.getRemoteObject(
                BUS_NAME, objectPath, DBus.Properties.class);
        UInt32 value = properties.Get(interfaceName, property);
        int returnValue = value.intValue();
        LOGGER.log(Level.INFO, "intProperty = \"{0}\"", returnValue);
        return returnValue;
    }

    /**
     * returns a property as a long value
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
                BUS_NAME, objectPath, DBus.Properties.class);
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
        String dbusPath = DEVICE_OBJECT_PATH + device;
        LOGGER.log(Level.INFO, "dbusPath = \"{0}\", property = \"{1}\"",
                new Object[]{dbusPath, property});
        DBus.Properties deviceProperties = dbusSystemConnection.getRemoteObject(
                BUS_NAME, dbusPath, DBus.Properties.class);
        return deviceProperties.Get(BUS_NAME, property);
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
        String objectPath = DEVICE_OBJECT_PATH + device;
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
                BUS_NAME, objectPath, DBus.Properties.class);
        return stringProperty.Get(interfaceName, property);
    }

    /**
     * returns the interface names of a given device
     *
     * @param device the given device
     * @return the interface names of a given device
     * @throws DBusException
     * @throws SAXException
     * @throws IOException
     * @throws ParserConfigurationException
     */
    public static List<String> getDeviceInterfaceNames(String device)
            throws DBusException, SAXException, IOException,
            ParserConfigurationException {
        return getInterfaceNames(DEVICE_OBJECT_PATH + device);
    }

    /**
     * returns the interface names of a given object path
     *
     * @param objectPath the given object path
     * @return the interface names of a given object path
     * @throws DBusException
     * @throws SAXException
     * @throws IOException
     * @throws ParserConfigurationException
     */
    public static List<String> getInterfaceNames(String objectPath)
            throws DBusException, SAXException, IOException,
            ParserConfigurationException {

        // introspect object path via dbus
        DBus.Introspectable introspectable
                = (DBus.Introspectable) dbusSystemConnection.getRemoteObject(
                        BUS_NAME, objectPath, DBus.Introspectable.class);
        String xml = introspectable.Introspect();

        // parse xml
        DocumentBuilderFactory documentBuilderFactory
                = DocumentBuilderFactory.newInstance();

        // The standard document builder tries to validate the DTD.
        // If the system is offline (as it is mostly the case in the exam
        // environment), documentBuilder.parse(inputSource) would just fail
        // with the following exceptin:
        // java.net.UnknownHostException: www.freedesktop.org
        // Therefore we try to disable DTD validation here.
        documentBuilderFactory.setValidating(false);
        documentBuilderFactory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false);

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
