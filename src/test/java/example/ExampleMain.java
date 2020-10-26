package example;

import it.tangodev.ble.*;
import it.tangodev.ble.BleCharacteristic.CharacteristicFlag;

import java.util.ArrayList;
import java.util.List;

import org.freedesktop.dbus.exceptions.DBusException;

public class ExampleMain implements Runnable {
	
	protected String valueString = "Ciao ciao";
	BleApplication app;
	BleService service;
	BleCharacteristic characteristic;

	public void notifyBle(String value) {
		this.valueString = value;
		characteristic.sendNotification();
	}
	
	public ExampleMain() throws DBusException, InterruptedException, DBusReferenceLostException {
		BleApplicationListener appListener = new BleApplicationListener() {
			@Override
			public void deviceDisconnected(String id) {
				System.out.println("Device disconnected");
			}
			
			@Override
			public void deviceConnected(String id) {
				System.out.println("Device connected");
			}
		};
		app = new BleApplication("/tango", appListener);
		service = new BleService("/tango/s", "13333333-3333-3333-3333-333333333001", true);
		List<CharacteristicFlag> flags = new ArrayList<CharacteristicFlag>();
		flags.add(CharacteristicFlag.READ);
		flags.add(CharacteristicFlag.WRITE);
		flags.add(CharacteristicFlag.NOTIFY);
		
		characteristic = new BleCharacteristic("/tango/s/c", service, flags, "13333333-3333-3333-3333-333333333002", new BleCharacteristicListener() {
			@Override
			public void setValue(byte[] value) {
				try {
					valueString = new String(value, "UTF8");
				} catch(Exception e) {
					System.out.println("");
				}
			}
			
			@Override
			public byte[] getValue() {
				try {
					return valueString.getBytes("UTF8");
				} catch(Exception e) {
					throw new RuntimeException(e);
				}
			}
		});
		service.addCharacteristic(characteristic);
		app.addService(service);
		
		ExampleCharacteristic exampleCharacteristic = new ExampleCharacteristic(service);
		service.addCharacteristic(exampleCharacteristic);
		app.start();
	}

	@Override
	public void run() {
		try {
			this.wait();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public BleApplication getApp() {
		return app;
	}

	public static void main(String[] args) throws DBusException, InterruptedException, DBusReferenceLostException {
		ExampleMain example = new ExampleMain();
		example.notifyBle("woooooo");
		example.getApp().stop();
	}
	
}
