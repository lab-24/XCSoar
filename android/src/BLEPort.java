/*
Copyright_License {

  XCSoar Glide Computer - http://www.xcsoar.org/
  Copyright (C) 2000-2014 The XCSoar Project
  A detailed list of copyright holders can be found in the file "AUTHORS".

  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU General Public License
  as published by the Free Software Foundation; either version 2
  of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software
  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
}
 */

package org.xcsoar;

import java.io.IOException;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import android.util.Log;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Looper;

/**
 * An #AndroidPort implementation that initiates a Bluetooth RFCOMM connection.
 */
class BLEPort extends AbstractMemoryPort implements Runnable {
	private static final String TAG = "XCSoar";

	private static final int ACTION_SEND_INFO = 0;
	private static final int ACTION_SEND_DISCOVERED_CHARACTERISTIC = 1;

	private static final String BUNDLE_DATA = "BUNDLE_DATA";

	private List<BluetoothGattCharacteristic> characteristics;

	private volatile BluetoothDevice device;
	private volatile BluetoothGatt gatt;
	private volatile boolean isConnected = false;

	private Context context;


	private Thread thread;
	private Handler handle;

	BLEPort(String _name, Context context, BluetoothDevice _device)
			throws IOException {
		super(_name);
		device = _device;

		characteristics = Collections
				.synchronizedList(new ArrayList<BluetoothGattCharacteristic>());

		handle = new Handler(Looper.getMainLooper()) {
			@Override
			public void handleMessage(Message msg) {
				String buffer;
				switch (msg.what) {
				case ACTION_SEND_INFO:
					buffer = StringToNmeaLine(
							String.format("BLECONNECT,%s", device.getAddress()));
					send(buffer.getBytes(), buffer.length());
					break;
				case ACTION_SEND_DISCOVERED_CHARACTERISTIC:
//					StringBuilder sb = new StringBuilder();
//					sb.append(String.format("BLEDISCOVER,%s",device.getAddress()));
					for (BluetoothGattCharacteristic gattCharacteristic : characteristics) {
//						sb.append(String.format(",%s", gattCharacteristic.getUuid().toString()));						
						buffer = StringToNmeaLine(
								String.format("BLEDISCOVER,%s,%s",device.getAddress(), gattCharacteristic.getUuid().toString()));
						send(buffer.getBytes(), buffer.length());
					}
//					buffer = StringToNmeaLine(sb.toString());
//					Log.d(TAG, buffer);
//					send(buffer.getBytes(), buffer.length());
					break;
				default:
					break;
				}
			}
		};
		thread = new Thread(this, toString());
		thread.start();
	}

	
	@Override
	public void dataReceived(byte[] data, int length) {
		Log.d(TAG, ByteArrayToString(data));
	}

	@Override
	public void close() {
		Thread thread = this.thread;
		if (thread != null) {
			try {
				thread.join();
			} catch (InterruptedException e) {
			}
		}

		BluetoothGatt gatt = this.gatt;
		if (gatt != null) {
			gatt.close();
			gatt = null;
		}

		// close handlers to the memory port
		super.close();

	}

	@Override
	public int getState() {
		return !isConnected ? STATE_LIMBO : super.getState();
	}


	@Override
	public void run() {
		int bootUpCounter = 0;
		try {
			// do this in the Thread, so we do not block the other things....
			if (device != null) {
				gatt = device.connectGatt(context, true, mGattCallback);
			}
		} catch (Exception e) {
			Log.e(TAG, "Failed to connect to BLE Device", e);
		} finally {
			thread = null;
		}
	}

	private boolean readCharacteristic(UUID characteristic) {
		if (gatt == null) {
			return false;
		}
		for (BluetoothGattCharacteristic gattCharacteristic : characteristics) {
			if (gattCharacteristic.getUuid().equals(characteristic)) {
				gatt.readCharacteristic(gattCharacteristic);
				return true;
			}
		}
		return false;
	}

	// Various callback methods defined by the BLE API.
	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status,
				int newState) {
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				Log.d(TAG, "BLE Device connected!");
				
				Message msg = handle.obtainMessage(ACTION_SEND_INFO);
				handle.sendMessage(msg);

				isConnected = true;
				
				gatt.discoverServices();
			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				Log.d(TAG, "BLE Device disconnected!");
				isConnected = false;
			} else {
				Log.d(TAG, "unknown State: " + String.valueOf(newState));
			}
		}

		@Override
		// New services discovered
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				characteristics.clear();
				for (BluetoothGattService gattService : gatt.getServices()) {
					for (BluetoothGattCharacteristic gattCharacteristic : gattService
							.getCharacteristics()) {
						characteristics.add(gattCharacteristic);
					}
				}
				Message msg = handle.obtainMessage(ACTION_SEND_DISCOVERED_CHARACTERISTIC);
				handle.sendMessage(msg);					
				// read the DeviceInfo:
			} else {
				Log.e(TAG, "Failure in Discovering BLE Services");
			}
		}

		@Override
		// Result of a characteristic read operation
		public void onCharacteristicRead(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
			}
		}
	};

	private static String ByteArrayToString(byte[] arr) {
		int i;
		for (i = 0; i < arr.length && arr[i] != 0; i++) {
		}
		return new String(arr, 0, i);
	}

	private static long ByteArrayToLong(byte[] arr) {
		if (arr.length > 8)
			return 0;
		long value = 0;
		for (int i = 0; i < arr.length; i++) {
			value += ((long) arr[i] & 0xffL) << (8 * i);
		}
		return value;
	}

	private static String StringToNmeaLine(String line) {
		byte res = 0;

		int i = 0;
		byte[] bytes = line.getBytes();

		for (; i < line.length(); ++i) {
			byte b = bytes[i];
			res = (byte) (res ^ b);
		}

		return String.format("$%s*%02X\r\n", line, res);
	}
}
