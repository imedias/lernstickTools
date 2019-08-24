package org.freedesktop.dbus;

import java.util.List;
import java.util.Map;
import org.freedesktop.dbus.exceptions.DBusException;

@DBusInterfaceName("org.freedesktop.DBus.ObjectManager")
public interface ObjectManager extends DBusInterface {

    public static class InterfacesAdded extends DBusSignal {

        public final DBusInterface object_path;
        public final Map<String, Map<String, Variant>> interfaces_and_properties;

        public InterfacesAdded(String path, DBusInterface object_path,
                Map<String, Map<String, Variant>> interfaces_and_properties)
                throws DBusException {
            super(path, object_path, interfaces_and_properties);
            this.object_path = object_path;
            this.interfaces_and_properties = interfaces_and_properties;
        }
    }

    public static class InterfacesRemoved extends DBusSignal {

        public final DBusInterface object_path;
        public final List<String> interfaces;

        public InterfacesRemoved(String path, DBusInterface object_path,
                List<String> interfaces) throws DBusException {
            super(path, object_path, interfaces);
            this.object_path = object_path;
            this.interfaces = interfaces;
        }
    }

    public Map<DBusInterface, Map<String, Map<String, Variant>>> GetManagedObjects();

}
