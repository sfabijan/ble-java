package it.tangodev.ble;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bluez.GattApplication1;
import org.bluez.GattManager1;
import org.bluez.LEAdvertisingManager1;
import org.dbus.ObjectManager;
import org.dbus.InterfacesAddedSignal.InterfacesAdded;
import org.dbus.InterfacesRomovedSignal.InterfacesRemoved;
import org.freedesktop.DBus;
import org.freedesktop.DBus.Properties;
import org.freedesktop.dbus.*;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * BleApplication class is the starting point of the entire Peripheral service's structure.
 * It is responsible of the service's publishment and the advertisement.
 *
 * @author Tongo
 */
public class BleApplication implements GattApplication1 {

    public static final String DBUS_BUSNAME = "org.freedesktop.DBus";
    public static final String BLUEZ_DBUS_BUSNAME = "org.bluez";
    public static final String BLUEZ_DEVICE_INTERFACE = "org.bluez.Device1";
    public static final String BLUEZ_ADAPTER_INTERFACE = "org.bluez.Adapter1";
    public static final String BLUEZ_GATT_INTERFACE = "org.bluez.GattManager1";
    public static final String BLUEZ_LE_ADV_INTERFACE = "org.bluez.LEAdvertisingManager1";

    private List<BleService> servicesList = new ArrayList<BleService>();
    private String path;
    private String adapterPath;
    private BleAdvertisement adv;
    private String adapterAlias;

    private AtomicBoolean hasDeviceConnected = new AtomicBoolean(false);

    private DBusSigHandler<InterfacesAdded> interfacesAddedSignalHandler;
    private DBusSigHandler<InterfacesRemoved> interfacesRemovedSignalHandler;
    private BleApplicationListener listener;
    private DBusConnection dbusConnection;

    /**
     * In order to create a BleApplication you need to pass a path.
     * The bluezero standard structure is:
     * APPLICATION
     * SERVICE
     * CHARACTERISTIC-1
     * CHARACTERISTIC-2
     * <p>
     * Since bluez 5.43, the advertisement is able to run only ONE service.
     *
     * @param path
     */
    public BleApplication(String path, BleApplicationListener listener) {
        this.path = path;
        this.listener = listener;

        String advPath = path + "/advertisement";
        this.adv = new BleAdvertisement(BleAdvertisement.ADVERTISEMENT_TYPE_PERIPHERAL, advPath);
    }

    /**
     * First of all the method power-on the adapter.
     * Then publish the service with their characteristic and start the advertisement (only primary service can advertise).
     *
     * @throws DBusException
     */
    public void start() throws DBusException, DBusReferenceLostException {
        this.dbusConnection = DBusConnection.getConnection(DBusConnection.SYSTEM);
        adapterPath = findAdapterPath();

        if (adapterPath == null) {
            throw new RuntimeException("No BLE adapter found");
        }
        Properties adapterProperties = (Properties) dbusConnection.getRemoteObject(BLUEZ_DBUS_BUSNAME, adapterPath, Properties.class);
        adapterProperties.Set(BLUEZ_ADAPTER_INTERFACE, "Powered", new Variant<Boolean>(true));

        if (adapterAlias != null) {
            adapterProperties.Set(BLUEZ_ADAPTER_INTERFACE, "Alias", new Variant<String>(adapterAlias));
        }

        GattManager1 gattManager = (GattManager1) dbusConnection.getRemoteObject(BLUEZ_DBUS_BUSNAME, adapterPath, GattManager1.class);
        LEAdvertisingManager1 advManager = (LEAdvertisingManager1) dbusConnection.getRemoteObject(BLUEZ_DBUS_BUSNAME, adapterPath, LEAdvertisingManager1.class);
        if (!adv.hasServices()) {
            updateAdvertisement();
        }
        export();

        boolean throwDBusEx = false;
        try {
            Map<String, Variant> advOptions = new HashMap<String, Variant>();
            advManager.RegisterAdvertisement(adv, advOptions);
        } catch (Throwable throwable) {
            throwDBusEx = true;
        }
        try {
			Map<String, Variant> appOptions = new HashMap<String, Variant>();
			gattManager.RegisterApplication(this, appOptions);
		} catch (Throwable throwable) {
        	throwDBusEx = true;
		}

        initInterfacesHandler();

        if (throwDBusEx) {
            throw new DBusReferenceLostException("Reference to DBUS invalid. Cannot register " +
                    "advertisement or application. " +
                    "Nevertheless, BLE application was started successfully.");
        }
    }

    /**
     * Stop the advertisement and unpublish the service.
     *
     * @throws DBusException
     */
    public void stop() throws DBusException, DBusReferenceLostException {
        if (adapterPath == null) {
            return;
        }
        GattManager1 gattManager = (GattManager1) dbusConnection.getRemoteObject(BLUEZ_DBUS_BUSNAME, adapterPath, GattManager1.class);
        LEAdvertisingManager1 advManager = (LEAdvertisingManager1) dbusConnection.getRemoteObject(BLUEZ_DBUS_BUSNAME, adapterPath, LEAdvertisingManager1.class);

        boolean throwDBusEx = false;
        try {
            if (adv != null && advManager != null) {
                advManager.UnregisterAdvertisement(adv);
            }
        } catch (Throwable throwable) {
            throwDBusEx = true;
        }

        try {
            if (gattManager != null) {
                gattManager.UnregisterApplication(this);
            }
        } catch (Throwable throwable) {
            throwDBusEx = true;
        }

        unexport();
        dbusConnection.removeSigHandler(InterfacesAdded.class, interfacesAddedSignalHandler);
        dbusConnection.removeSigHandler(InterfacesRemoved.class, interfacesRemovedSignalHandler);
        dbusConnection.disconnect();
        dbusConnection = null;

        if (throwDBusEx) {
            throw new DBusReferenceLostException("Reference to DBUS invalid. Cannot unregister " +
                    "advertisement or application. " +
                    "Nevertheless, BLE application was stopped successfully.");
        }
    }

    protected void initInterfacesHandler() throws DBusException {
        DBus dbus = dbusConnection.getRemoteObject(DBUS_BUSNAME, "/or/freedesktop/DBus", DBus.class);
        String bluezDbusBusName = dbus.GetNameOwner(BLUEZ_DBUS_BUSNAME);
        ObjectManager bluezObjectManager = (ObjectManager) dbusConnection.getRemoteObject(BLUEZ_DBUS_BUSNAME, "/", ObjectManager.class);

        interfacesAddedSignalHandler = new DBusSigHandler<InterfacesAdded>() {
            @Override
            public void handle(InterfacesAdded signal) {
                Map<String, Variant> iamap = signal.getInterfacesAdded().get(BLUEZ_DEVICE_INTERFACE);
                if (iamap != null) {
                    Variant<String> address = iamap.get("Address");
                    hasDeviceConnected.set(true);
                    if (listener != null) {
                        listener.deviceConnected(address.getValue());
                    }
                }
            }
        };

        interfacesRemovedSignalHandler = new DBusSigHandler<InterfacesRemoved>() {
            @Override
            public void handle(InterfacesRemoved signal) {
                List<String> irlist = signal.getInterfacesRemoved();
                for (String ir : irlist) {
                    if (BLUEZ_DEVICE_INTERFACE.equals(ir)) {
                        hasDeviceConnected.set(false);
                        if (listener != null) {
                            listener.deviceDisconnected(ir);
                        }
                    }
                }
            }
        };

        dbusConnection.addSigHandler(InterfacesAdded.class, bluezDbusBusName, bluezObjectManager, interfacesAddedSignalHandler);
        dbusConnection.addSigHandler(InterfacesRemoved.class, bluezDbusBusName, bluezObjectManager, interfacesRemovedSignalHandler);
    }

    /**
     * Set the alias name of the peripheral. This name is visible by the central that discover s peripheral.
     * This must set before start to take effect.
     *
     * @param alias
     */
    public void setAdapterAlias(String alias) {
        adapterAlias = alias;
    }

    public void addService(BleService service) {
        this.servicesList.add(service);
    }

    public void removeService(BleService service) {
        this.servicesList.remove(service);
    }

    public List<BleService> getServicesList() {
        return servicesList;
    }

    public boolean hasDeviceConnected() {
        return hasDeviceConnected.get();
    }

    public BleAdvertisement getAdvertisement() {
        return adv;
    }

    public boolean isDeviceConnected(String id) {
        return false; // TODO IMPLEMENT
    }

    /**
     * Search for a Adapter that has GattManager1 and LEAdvertisement1 interfaces, otherwise return null.
     *
     * @return
     * @throws DBusException
     */
    private String findAdapterPath() throws DBusException {
        ObjectManager bluezObjectManager = (ObjectManager) dbusConnection.getRemoteObject(BLUEZ_DBUS_BUSNAME, "/", ObjectManager.class);
        if (bluezObjectManager == null) {
            return null;
        }

        Map<Path, Map<String, Map<String, Variant>>> bluezManagedObject = bluezObjectManager.GetManagedObjects();
        if (bluezManagedObject == null) {
            return null;
        }

        for (Path path : bluezManagedObject.keySet()) {
            Map<String, Map<String, Variant>> value = bluezManagedObject.get(path);
            boolean hasGattManager = false;
            boolean hasAdvManager = false;

            for (String key : value.keySet()) {
                if (key.equals(BLUEZ_GATT_INTERFACE)) {
                    hasGattManager = true;
                }
                if (key.equals(BLUEZ_LE_ADV_INTERFACE)) {
                    hasAdvManager = true;
                }

                if (hasGattManager && hasAdvManager) {
                    return path.toString();
                }
            }
        }

        return null;
    }

    /**
     * Export the application in Dbus system.
     *
     * @throws DBusException
     */
    private void export() throws DBusException {
        if (adv != null) {
            adv.export(dbusConnection);
        }
        for (BleService service : servicesList) {
            service.export(dbusConnection);
        }
        dbusConnection.exportObject(path, this);
    }

    /**
     * Unexport the application in Dbus system.
     *
     * @throws DBusException
     */
    private void unexport() throws DBusException {
        if (adv != null) {
            adv.unexport(dbusConnection);
        }
        for (BleService service : servicesList) {
            service.unexport(dbusConnection);
        }
        dbusConnection.unExportObject(path);
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public Map<Path, Map<String, Map<String, Variant>>> GetManagedObjects() {
        Map<Path, Map<String, Map<String, Variant>>> response = new HashMap<Path, Map<String, Map<String, Variant>>>();
        for (BleService service : servicesList) {
            response.put(service.getPath(), service.getProperties());
            for (BleCharacteristic characteristic : service.getCharacteristics()) {
                response.put(characteristic.getPath(), characteristic.getProperties());
                // TODO foreach Description in Characteristic
            }
        }
        return response;
    }

    // add primary service uuids to advertisement
    private void updateAdvertisement() {
        for (BleService service : servicesList) {
            if (service.isPrimary()) {
                adv.addService(service);
                break;
            }
        }
    }
}
