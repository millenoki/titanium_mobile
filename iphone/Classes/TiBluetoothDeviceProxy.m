/**
 * Akylas
 * Copyright (c) 2009-2010 by Akylas. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */


#ifdef USE_TI_BLUETOOTHDEVICE
#import "TiBluetoothDeviceProxy.h"
#import "TiUtils.h"
#import "TiBlob.h"

#define DATA_CHUNK_SIZE     (1024)

@implementation TiBluetoothDeviceProxy
{
    EASession *_session;
    NSMutableData *_outbuf;
    NSString* _protocolString;
    EAAccessory* _accessory;
}

-(void)_initWithProperties:(NSDictionary *)properties
{
    id arg = [properties valueForKey:@"protocol"];
    
    if (IS_NULL_OR_NIL(arg)) {
        [self throwException:@"Invalid argument passed to protocol property" subreason:@"You must pass a protocol String" location:CODELOCATION];
    }
    _protocolString = [[TiUtils stringValue:arg] retain];
    [super _initWithProperties:properties];
}

-(void)connect:(id)args
{
    if (_session) {
        return;
    }
    
    if (!_accessory) {
        NSArray *accList = [[EAAccessoryManager sharedAccessoryManager] connectedAccessories];
        for(EAAccessory *acc in accList) {
            NSLog(@"acc found");
            if([[acc protocolStrings] containsObject:_protocolString]) {
                _accessory = [acc retain];
                break;
            }
        }
        if (!_accessory) {
            return;
        }
        _accessory.delegate = self;
    }
   
    _session = [[EASession alloc] initWithAccessory:_accessory forProtocol:_protocolString];
    
    if(!_session) {
        NSLog(@"Couldn't create session!");
        return NO;
    }
    
    // Set our BGdemoStreamDelegate instance to receive stream events
    [_session.outputStream setDelegate:self];
    [_session.inputStream setDelegate:self];
    
    // Schedule stream I/O in the current runloop
    [_session.inputStream scheduleInRunLoop:[NSRunLoop currentRunLoop] forMode:NSDefaultRunLoopMode];
    [_session.outputStream scheduleInRunLoop:[NSRunLoop currentRunLoop] forMode:NSDefaultRunLoopMode];
    
    // Open fire
    [_session.inputStream open];
    [_session.outputStream open];
}

-(void)disconnect:(id)args
{
    if (!_session) {
        return;
    }
    [self fireEvent:@"disconnected"];
    if (_session) {
        [[_session inputStream] close];
        [[_session inputStream] removeFromRunLoop:[NSRunLoop currentRunLoop] forMode:NSDefaultRunLoopMode];
        [[_session inputStream] setDelegate:nil];
        [[_session outputStream] close];
        [[_session outputStream] removeFromRunLoop:[NSRunLoop currentRunLoop] forMode:NSDefaultRunLoopMode];
        [[_session outputStream] setDelegate:nil];
        RELEASE_TO_NIL(_session);
    }
    RELEASE_TO_NIL(_accessory)
}
-(id)connected
{
    if (_accessory) {
        return NUMBOOL([_accessory isConnected]);
    }
    return NUMBOOL(NO);
}

-(id)connectionID
{
    if (_accessory) {
        return NUMUINTEGER([_accessory connectionID]);
    }
    return NUMUINTEGER(-1);
}

-(id)manufacturer
{
    if (_accessory) {
        return [_accessory manufacturer];
    }
    return nil;
}

-(id)modelNumber
{
    if (_accessory) {
        return [_accessory modelNumber];
    }
    return nil;
}

-(id)serialNumber
{
    if (_accessory) {
        return [_accessory serialNumber];
    }
    return nil;
}

-(id)firmwareRevision
{
    if (_accessory) {
        return [_accessory firmwareRevision];
    }
    return nil;
}

-(id)hardwareRevision
{
    if (_accessory) {
        return [_accessory hardwareRevision];
    }
    return nil;
}

// This is called when we get an incoming data event. Notify the appDelegate that we have data to print.
- (void)handleIncoming:(NSInputStream*)stream {
    while ([stream hasBytesAvailable]) {
        unsigned char buf[DATA_CHUNK_SIZE];
        NSUInteger len;
        len = [stream read:buf maxLength:DATA_CHUNK_SIZE];
        if(len>0) {
            [self fireEvent:@"read" withObject:@{
                                                 @"length":NUMUINTEGER(len),
                                                 @"data":[NSString stringWithCString:(const char*)buf encoding:NSUTF8StringEncoding]}];
        }
    }
}

- (void)handleSpace:(NSOutputStream*)stream {
    if ([_session.outputStream streamStatus] != NSStreamStatusOpen &&
        [_session.outputStream streamStatus] != NSStreamStatusWriting) {
        NSLog(@"handleSpace: streamStatus invalid!");
        return;
    }
    NSInteger done = [_session.outputStream write:[_outbuf bytes] maxLength:[_outbuf length]];
    if(done > 0) [_outbuf replaceBytesInRange:NSMakeRange(0, done) withBytes:nil length:0]; // Remove sent bytes from buffer
}

- (void)sendData:(uint8_t*)data length:(NSUInteger)len {
    [_outbuf appendBytes:data length:len];
    if([_session.outputStream hasSpaceAvailable]) {
        [self handleSpace:_session.outputStream];
    }
}

-(void)write:(id)args
{
//    TiStreamProxy<TiStreamInternal>* stream = nil;
//    ENSURE_ARG_AT_INDEX(stream, args, 0, TiStreamProxy);
//    
//    // TODO: Throw exception, or call callback?
//    if (![stream isWritable:nil]) {
//        [self throwException:@"StreamException"
//                   subreason:@"write() operation on stream that is not writable"
//                    location:CODELOCATION];
//    }
//    
//    [self invokeRWOperation:@selector(writeFromBuffer:offset:length:callback:) withArgs:args];
}

// This is where we receive notifications of incoming data and space to push outgoing data
- (void)stream:(NSStream *)stream handleEvent:(NSStreamEvent)event {
    if(event & NSStreamEventErrorOccurred) {
        // TODO: handle errors
        NSLog(@"stream error");
        return;
    }
    
    if(stream == _session.inputStream && event & NSStreamEventHasBytesAvailable)
        [self handleIncoming:(NSInputStream*) stream];
    
    if(stream == _session.outputStream && event & NSStreamEventHasSpaceAvailable)
        [self handleSpace:(NSOutputStream*) stream];
}

- (void)dealloc {
    [self disconnect:nil];
    RELEASE_TO_NIL(_protocolString)
    RELEASE_TO_NIL(_outbuf)
    [super dealloc];
}

- (void)accessoryDidDisconnect:(EAAccessory *)accessory {
    if (accessory == _accessory) {
        [self disconnect:nil];
    }
}


@end
#endif
