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

#include "Device.hpp"
#include "Device/Parser.hpp"
#include "Device/Port/Port.hpp"
#include "NMEA/Info.hpp"
#include "NMEA/InputLine.hpp"
#include "NMEA/Checksum.hpp"
#include "Units/System.hpp"
#include "Atmosphere/Temperature.hpp"
#include "LogFile.hpp"

bool
FlytecBLEDevice::DataReceived(const void *_data, size_t length,
                                    struct NMEAInfo &info)
{
  assert(_data != NULL);
  assert(length > 0);

  LogFormat("recv:");

  bool result = false;

  //const char *data = (const char *)_data, *end = data + length;
  return result;
}


bool
FlytecBLEDevice::ParseNMEA(const char *_line, NMEAInfo &info)
{
  LogFormat("nmea:%s", _line);

  if (!VerifyNMEAChecksum(_line))
    return false;


  NMEAInputLine line(_line);
  char type[16];
  line.Read(type, 16);
  // check if we are in the correct Driver for this BLE Device
  if (StringIsEqual(type, "$BLEINFO")) {
//    if (FlytecParseInfo(line)) {
      // send Start of Notify
      char buffer[256];
      strcat(buffer, "$BLESTRNOTI,aba27100-143b4b81-a444edcd-0000f022");
      AppendNMEAChecksum(buffer);
      strcat(buffer, "\r\n");
      port.Write(buffer);
//    }
    return false;
  }
  else
    return false;
}
