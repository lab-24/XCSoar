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
 * An #AndroidPort implementation that initiates a Bluetooth RFCOMM
 * connection.
 */
class BLEPort extends AbstractMemoryPort implements Runnable {
  private static final String TAG = "XCSoar";
  
  private static final int ACTION_SEND_INFO = 0;
  private static final int DATA_RECEIVED = 1;
  
  private static final String BUNDLE_DATA = "BUNDLE_DATA";
  
  static final int BUFFER_SIZE = 256;
  
  private static final UUID GATT_MANUFACTURER_NAME = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb");
  private static final UUID GATT_MODEL_NUMBER = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb");
  private static final UUID GATT_SERIAL_NUMBER = UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb");
  private static final UUID GATT_HARDWARE_REVISION = UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb");
  private static final UUID GATT_FIRMWARE_REVISION = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");
  private static final UUID GATT_SOFTWARE_REVISION = UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb");
  private static final UUID GATT_SYSTEM_ID = UUID.fromString("00002a23-0000-1000-8000-00805f9b34fb");
  private static final UUID GATT_PNP_ID = UUID.fromString("00002a50-0000-1000-8000-00805f9b34fb");
  
  private List<BluetoothGattCharacteristic> characteristics;

  private volatile BluetoothDevice device;
  private volatile BluetoothGatt gatt;
  private volatile boolean isConnected = false;

  private Context context;
  
  
  // device information
  private volatile String manufacturername = "";
  private volatile String modelnumber = "";
  private volatile String serialnumber = "";
  private volatile String hardwarerevision = "";
  private volatile String firmwarerevision = "";
  private volatile String softwarerevision = "";
  private volatile long systemid = 0;
  private volatile long pnpid = 0;
  
  private Thread thread;
  private Handler handle;

  BLEPort(String _name, Context context, BluetoothDevice _device) throws IOException {
	super(_name);
    device = _device;

    characteristics = Collections.synchronizedList(new ArrayList<BluetoothGattCharacteristic>());
      
    handle = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ACTION_SEND_INFO:
                	sendInfo();
                    break;
                	Bundle recBundle = msg.getData();
                    if (recBundle != null) {
                        byte[] data = recBundle.getByteArray(BUNDLE_DATA);
                        Log.d(TAG, ByteArrayToString(data));
                    }
                    break;

                default:
                    break;
            }
        }
    };
    thread = new Thread(this, toString());
    thread.start();

  }

  @Override public void dataReceived(byte[] data, int length) {
	  Log.d(TAG, ByteArrayToString(data));
  }
	  
  @Override public void close() {
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

  @Override public int getState() {
    return !isConnected
      ? STATE_LIMBO
      : super.getState();
  } 


  private void sendInfo() {
	  byte[] buffer = StringToNmeaLine(String.format("BLEINFO,%s,%s,%s,%s,%s,%s,%d,%d", manufacturername
	    		, modelnumber, serialnumber, hardwarerevision, firmwarerevision, softwarerevision,
	    		systemid, pnpid)).getBytes();
	  Log.d(TAG, ByteArrayToString(buffer));
	  send(buffer, buffer.length);
  }
  
  @Override public void run() {
	  int bootUpCounter = 0;
	  byte[] buffer = new byte[BUFFER_SIZE];
	    try {
	    	// do this in the Thread, so we do not block the other things....
	        if (device != null) 	        	
	        	gatt = device.connectGatt(context, true, mGattCallback);
	        
	        if (gatt != null) {
		        // read out DeviceInfo Service
		    	while (bootUpCounter <= 7) {
		    		// wait for new data on InputStream (commands from Driver)
		    		if (isConnected) {
		    			switch (bootUpCounter) {
		    			case 0:
		    				readCharacteristic(GATT_MANUFACTURER_NAME);
		    			case 1:
		    				readCharacteristic(GATT_MODEL_NUMBER);
		    			case 2:
		    				readCharacteristic(GATT_SERIAL_NUMBER);
		    			case 3:
		    				readCharacteristic(GATT_HARDWARE_REVISION);
		    			case 4:
		    				readCharacteristic(GATT_FIRMWARE_REVISION);
		    			case 5:
		    				readCharacteristic(GATT_SOFTWARE_REVISION);
		    			case 6:
		    				readCharacteristic(GATT_SYSTEM_ID);
		    			case 7:
		    				readCharacteristic(GATT_PNP_ID);		    				
		    			}
		    			bootUpCounter ++;
		    		}
		    	    Thread.sleep(200);
		    	}
		        Message msg = handle.obtainMessage(ACTION_SEND_INFO);
		        handle.sendMessage(msg);
		        
//		        while (recvStream != null) {
//		          int n;
//		          try {
//		            n = recvStream.read(buffer, 0, BUFFER_SIZE);
//			        msg = handle.obtainMessage(DATA_RECEIVED);
//	                if (msg != null) {
//	                	Bundle bundle = new Bundle();
//                        bundle.putByteArray(BUNDLE_DATA, buffer);
//	                    msg.setData(bundle);
//	                }
//			        handle.sendMessage(msg);
//		          } catch (IOException e) {
//		            if (recvStream != null)
//		              Log.e(TAG, "Failed to read:", e);
//		            //closeInternal();
//		            break;
//		          }
//
//		        }


		        
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
  private final BluetoothGattCallback mGattCallback =
          new BluetoothGattCallback() {
      @Override
      public void onConnectionStateChange(BluetoothGatt gatt, int status,
              int newState) {
          if (newState == BluetoothProfile.STATE_CONNECTED) {
        	  Log.d(TAG, "BLE Device connected!");
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
        		  for (BluetoothGattCharacteristic gattCharacteristic : gattService.getCharacteristics())
        		 	characteristics.add(gattCharacteristic);
        	  }
        	  isConnected = true;
        	  Log.d(TAG, "Services received ");

        	  // read the DeviceInfo:        	  
          } else {
        	  Log.e(TAG, "Failure in Discovering BLE Services"); 
          }
      }

      @Override
      // Result of a characteristic read operation
      public void onCharacteristicRead(BluetoothGatt gatt,
              BluetoothGattCharacteristic characteristic,
              int status) {
          if (status == BluetoothGatt.GATT_SUCCESS) {
        	  if (characteristic.getUuid().equals(GATT_MANUFACTURER_NAME))
        		manufacturername =  ByteArrayToString(characteristic.getValue());
        	  else if (characteristic.getUuid().equals(GATT_MODEL_NUMBER))
          		modelnumber =  ByteArrayToString(characteristic.getValue());
        	  else if (characteristic.getUuid().equals(GATT_SERIAL_NUMBER))
            		serialnumber =  ByteArrayToString(characteristic.getValue());
        	  else if (characteristic.getUuid().equals(GATT_HARDWARE_REVISION))
            		hardwarerevision =  ByteArrayToString(characteristic.getValue());
        	  else if (characteristic.getUuid().equals(GATT_FIRMWARE_REVISION))
            		firmwarerevision =  ByteArrayToString(characteristic.getValue());
        	  else if (characteristic.getUuid().equals(GATT_SOFTWARE_REVISION))
            		softwarerevision =  ByteArrayToString(characteristic.getValue());
        	  else if (characteristic.getUuid().equals(GATT_SYSTEM_ID))
            		systemid =  ByteArrayToLong(characteristic.getValue());
        	  else if (characteristic.getUuid().equals(GATT_PNP_ID))
            		pnpid =  ByteArrayToLong(characteristic.getValue());
          }
      }
  };
  
  private static String ByteArrayToString(byte[] arr) {
	  int i;
	  for (i = 0; i < arr.length && arr[i] != 0; i++) { }
	  return new String(arr, 0, i);
  }
  private static long ByteArrayToLong(byte[] arr) {
	  if (arr.length > 8)
		  return 0;
	  long value = 0;
	  for (int i = 0; i < arr.length; i++)
	  {
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
		  res = (byte)(res ^ b);
	  }
	
	  return String.format("$%s*%02X\r\n", line, res);
  }
}
