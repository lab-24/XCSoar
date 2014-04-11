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
import java.util.Set;
import android.util.Log;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;

/**
 * A library that constructs Bluetooth ports.  It is called by C++
 * code.
 */
final class BLEHelper {
  private static final String TAG = "XCSoar";
  private static Context context;
  private static final BluetoothAdapter adapter;

  static {
    BluetoothAdapter _adapter;
    try {
      _adapter = BluetoothAdapter.getDefaultAdapter();
    } catch (Exception e) {
      Log.e(TAG, "BluetoothAdapter.getDefaultAdapter() failed", e);
      _adapter = null;
    }

    adapter = _adapter;
  }

  public static void Initialize(Context _context) {
	  context = _context;
  }

  public static boolean isEnabled() {
    return adapter != null && adapter.isEnabled();
  }

  /**
   * Turns the #BluetoothDevice into a human-readable string.
   */
  public static String getDisplayString(BluetoothDevice device) {
    String name = device.getName();
    String address = device.getAddress();

    if (name == null)
      return address;

    return name + " [" + address + "]";
  }

  public static String getNameFromAddress(String address) {
    if (adapter == null)
      return null;

    try {
      return adapter.getRemoteDevice(address).getName();
    } catch (Exception e) {
      Log.e(TAG, "Failed to look up name of " + address, e);
      return null;
    }
  }



  public static AndroidPort createServer() {
    if (adapter == null)
      return null;

    try {
      return new BLEServerPort(context, adapter);
    } catch (Exception e) {
      Log.e(TAG, "Failed to create Bluetooth server", e);
      return null;
    }
  }
}
