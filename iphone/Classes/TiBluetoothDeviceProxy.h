/**
 * Akylas
 * Copyright (c) 2009-2010 by Akylas. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */


#ifdef USE_TI_BLUETOOTHDEVICE
#import "TiProxy.h"
#import <ExternalAccessory/ExternalAccessory.h>

@interface TiBluetoothDeviceProxy : TiProxy<NSStreamDelegate, EAAccessoryDelegate>

@end
#endif