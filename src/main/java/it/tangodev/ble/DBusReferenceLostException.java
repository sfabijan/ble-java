package it.tangodev.ble;

/**
 * Exception class created for exceptions that occur because
 * a reference to BLE adapter is lost.
 */
public class DBusReferenceLostException  extends Exception{
    public DBusReferenceLostException(String msg) {
            super(msg);
        }
}
