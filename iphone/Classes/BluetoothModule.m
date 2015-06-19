/**
 * Akylas
 * Copyright (c) 2009-2010 by Akylas. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */


#ifdef USE_TI_BLUETOOTH
#import "BluetoothModule.h"
#import <ExternalAccessory/ExternalAccessory.h>

@implementation BluetoothModule
{
    BOOL _registeredForNotifs;
    BOOL _pairing;
    EAAccessory * _pairingAccessory;
}

- (void)_configure
{
    [super _configure];
    _registeredForNotifs = NO;
    _pairing = NO;
}

-(NSString*)apiName
{
    return @"Ti.Bluetooth";
}

- (id)connectedDevices {
    // Get list of connected accessories
    NSArray *accList = [[EAAccessoryManager sharedAccessoryManager] connectedAccessories];
    NSMutableArray* accs = [NSMutableArray arrayWithCapacity:[accList count]];
    [accList enumerateObjectsUsingBlock:^(EAAccessory* obj, NSUInteger idx, BOOL *stop) {
        [accs addObject:[self dictFromAccessory:obj]];
    }];
    return accs;
}

-(NSDictionary*)dictFromAccessory:(EAAccessory*)accessory
{
    return @{
             @"connected" : NUMBOOL([accessory isConnected]),
             @"connectionID" : NUMUINTEGER(accessory.connectionID),
             @"manufacturer" : accessory.manufacturer,
             @"name" : accessory.name,
             @"modelNumber" : accessory.modelNumber,
             @"serialNumber" : accessory.serialNumber,
             @"firmwareRevision" : accessory.firmwareRevision,
             @"hardwareRevision" : accessory.hardwareRevision};
}

-(void)_listenerAdded:(NSString*)type count:(NSInteger)count
{
    if (count == 1 && ([type isEqual:@"connected"] || [type isEqual:@"disconnected"]))
    {
        if (!_registeredForNotifs) {
            _registeredForNotifs = YES;
            TiThreadPerformOnMainThread(^{
                [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(accessoryDidDisconnect:) name:EAAccessoryDidDisconnectNotification object:nil];
                [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(accessoryDidConnect:) name:EAAccessoryDidConnectNotification object:nil];
                [[ EAAccessoryManager sharedAccessoryManager] registerForLocalNotifications];
                
            }, YES);
        }
        
    }
}

-(void)_listenerRemoved:(NSString*)type count:(NSInteger)count
{
    if (count == 1 && (([type isEqual:@"connected"] && ![self _hasListeners:@"disconnected"])
                       || ([type isEqual:@"disconnected"] && ![self _hasListeners:@"connected"])))
    {
        if (_registeredForNotifs) {
            _registeredForNotifs = NO;
            TiThreadPerformOnMainThread(^{
                [[NSNotificationCenter defaultCenter] removeObserver:self];
                [[ EAAccessoryManager sharedAccessoryManager] unregisterForLocalNotifications];
            }, YES);
        }
    }
}

- (void)accessoryDidConnect:(NSNotification *)notification
{
    
    EAAccessory * connectedAccessory = [[notification userInfo] objectForKey:EAAccessoryKey];
    if(connectedAccessory == nil)
        return;
    if (_pairing) {
        _pairingAccessory = [connectedAccessory retain];
    }
    [self fireEvent:@"connected" withObject:[self dictFromAccessory:connectedAccessory]];
}


- (void)accessoryDidDisconnect:(NSNotification *)notification
{
    EAAccessory * disconnectedAccessory = [[notification userInfo] objectForKey:EAAccessoryKey];
    if(disconnectedAccessory == nil)
        return;
    [self fireEvent:@"disconnected" withObject:[self dictFromAccessory:disconnectedAccessory]];

}

-(void)pairDevice:(id)args
{
    if (_pairing) {
        return;
    }
    ENSURE_SINGLE_ARG_OR_NIL(args, NSDictionary)
    NSString* predicateString = [TiUtils stringValue:@"predicate" properties:args];
    if (args) {
        KrollCallback* successCallback = [args valueForKey:@"success"];
        ENSURE_TYPE_OR_NIL(successCallback, KrollCallback);
        KrollCallback* errorCallback = [args valueForKey:@"error"];
        ENSURE_TYPE_OR_NIL(errorCallback, KrollCallback);
    }
    NSPredicate* predicated = nil;
    if (predicateString) {
        predicated = [NSPredicate predicateWithFormat:predicateString];
    }
    TiThreadPerformOnMainThread(^{
        if (!_registeredForNotifs) {
            [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(accessoryDidConnect:) name:EAAccessoryDidConnectNotification object:nil];
            [[ EAAccessoryManager sharedAccessoryManager] registerForLocalNotifications];
        }
        _pairing = YES;
        [[ EAAccessoryManager sharedAccessoryManager] showBluetoothAccessoryPickerWithNameFilter:predicated completion:^(NSError *error) {
            if (!_registeredForNotifs) {
                [[NSNotificationCenter defaultCenter] removeObserver:self];
                [[ EAAccessoryManager sharedAccessoryManager] unregisterForLocalNotifications];
            }
            if (error != nil) {
                [self _fireEventToListener:@"error" withObject:[TiUtils dictionaryWithCode:[error code] message:[TiUtils messageFromError:error]] listener:[args valueForKey:@"error"] thisObject:nil];
            } else {
                [self _fireEventToListener:@"success" withObject:_pairingAccessory?@{@"device":[self dictFromAccessory:_pairingAccessory]}:@{} listener:[args valueForKey:@"success"] thisObject:nil];
            }
            RELEASE_TO_NIL(_pairingAccessory)
            _pairing = NO;
        }];
    }, YES);
}

-(void)enableBluetooth:(id)args
{
    //for android compat
}

-(void)disableBluetooth:(id)args
{
    //for android compat
}

-(id)supported
{
    //for android compat
    return NUMBOOL(YES);
}

-(void)discover:(id)args
{
    //for android compat
}

-(void)unpairDevice:(id)args
{
    //for android compat
}

@end

#endif
