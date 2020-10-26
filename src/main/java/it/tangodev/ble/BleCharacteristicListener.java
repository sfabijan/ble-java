package it.tangodev.ble;

import java.util.List;

/**
 * Interface that describe the source of the data source of one Characteristic.
 * @author Tongo
 *
 */
public interface BleCharacteristicListener {
	public byte[] getValue();
	public void setValue(byte[] value);
}
