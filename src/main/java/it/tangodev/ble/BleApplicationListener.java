package it.tangodev.ble;

public interface BleApplicationListener {
	public void deviceConnected(String id);
	public void deviceDisconnected(String id);
}
