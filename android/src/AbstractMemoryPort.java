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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.InputStream;
import android.util.Log;

/**
 * fake a Port to a Device (used to connect a BLE Device to the XCSoar Bluetooth
 * Driver)
 */
abstract class AbstractMemoryPort implements AndroidPort, InputListener {
	private static final String TAG = "XCSoar";

	private final String name;

	// connected to the Driver as InputStream
	private PipedInputStream inputStream;
	// connected to the Driver as OutputStream
	private PipedOutputStream outputStream;
	// connected to BLEPort, so we can send data to the
	// input stream of the driver
	private PipedOutputStream sendStream;
	// data from the Driver, comes to this Input Stream
	private PipedInputStream receiveStream;

	private InputListener listener;
	private InputThread receiveThread;
	private OutputThread sendThread;
	private InputThread inputThread;
	private OutputThread outputThread;

	protected AbstractMemoryPort(String _name)  throws IOException {
		name = _name;

		sendStream = new PipedOutputStream();
		receiveStream = new PipedInputStream();
		inputStream = new PipedInputStream(sendStream, 2048);
		outputStream = new PipedOutputStream(receiveStream);

		receiveThread = new InputThread("mem" + _name, this, receiveStream);
		sendThread = new OutputThread("mem" + _name, sendStream);
		sendThread.setTimeout(5000);

		inputThread = new InputThread(name, listener, inputStream);
		outputThread = new OutputThread(name, outputStream);
		outputThread.setTimeout(5000);

	}

	  @Override public String toString() {
		    return name;
		  }
	  
	private synchronized InputThread stealInputThread() {
		InputThread i = inputThread;
		inputThread = null;
		return i;
	}

	private synchronized OutputThread stealOutputThread() {
		OutputThread o = outputThread;
		outputThread = null;
		return o;
	}

	private synchronized InputThread stealReceiveThread() {
		InputThread r = receiveThread;
		receiveThread = null;
		return r;
	}

	private synchronized OutputThread stealSendThread() {
		OutputThread o = sendThread;
		sendThread = null;
		return o;
	}

	protected void setWriteTimeout(int timeout_ms) {
		sendThread.setTimeout(timeout_ms);
	}

	@Override
	public void close() {
		OutputThread o = stealOutputThread();
		if (o != null)
			o.close();
		OutputThread s = stealSendThread();
		if (s != null)
			s.close();
		InputThread i = stealInputThread();
		if (i != null)
			i.close();
		InputThread r = stealReceiveThread();
		if (r != null)
			r.close();
	}

	@Override
	public void setListener(InputListener _listener) {
		listener = _listener;
		if (inputThread != null)
			inputThread.setListener(listener);
	}

	@Override
	public int getState() {
		InputThread i = inputThread;
		return i != null && i.isValid() ? STATE_READY : STATE_FAILED;
	}

	@Override
	public final boolean drain() {
		OutputThread o = outputThread;
		return o != null && o.drain();
	}

	@Override
	public int write(byte[] data, int length) {
		OutputThread o = outputThread;
		return o != null ? o.write(data, length) : -1;
	}

	public int send(byte[] data, int length) {
		OutputThread o = sendThread;
		return o != null ? o.write(data, length) : -1;
	}

	@Override
	public boolean setBaudRate(int baud) {
		return true;
	}

	@Override
	public int getBaudRate() {
		return 0;
	}

}
