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

import java.util.UUID;
import java.util.Collection;
import java.util.Set;
import java.util.LinkedList;
import java.io.IOException;
import android.util.Log;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Handler;

/**
 * A utility class which wraps the Java API into an easier API for the C++ code.
 */
final class BLEServerPort extends MultiPort implements InputListener {

	private static final String TAG = "XCSoar";

	private BluetoothAdapter adapter;
	private Context context;
	private boolean mScanning;
	private long SCAN_PERIOD = 10000;
	
	  private static Handler handler;

	  /**
	   * Global initialization of the class.  Must be called from the main
	   * event thread, because the Handler object must be bound to that
	   * thread.
	   */
	  public static void Initialize() {
	    handler = new Handler();
	  }

	BLEServerPort(Context _context, BluetoothAdapter _adapter) throws IOException {
		this.adapter = _adapter;
		this.context = _context;
		// start scanning for BLE Devices
		scanLeDevice(true);
	}

	// Device scan callback.
	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
		@Override
		public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
		    try {
		    	// check if we already have this device in the MultiPort
		    	// sometimes we get multiple time the same device from LE Scan....
		    	// depends on the Smartphone
		    	if (!contains(device.getAddress())) {
			    	BLEPort port = new BLEPort(device.getAddress(), context, device);
		    		Log.d(TAG, "Add new BLE Port: " + port.toString());
		            /* make writes non-blocking and potentially lossy, to avoid
		            blocking when one of the peers doesn't receive quickly
		            enough */
		    		//port.setWriteTimeout(100);
			    	add(port);
		    	}
		    } catch (IOException e) {
		    	
		    }
		}
	};

	private void scanLeDevice(final boolean enable) {
		if (enable) {
			// Stops scanning after a pre-defined scan period.
			handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					mScanning = false;
					adapter.stopLeScan(mLeScanCallback);
				}
			}, SCAN_PERIOD);

			mScanning = true;
			adapter.startLeScan(mLeScanCallback);
		} else {
			mScanning = false;
			adapter.stopLeScan(mLeScanCallback);
		}
	}





	@Override
	public void close() {

		super.close();
	}

	@Override
	public int getState() {
		return STATE_READY;
//		return super.getState();
	}
}
