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
#define BUFSIZE 65536U

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
    _outbuf = [NSMutableData new];
    [super _initWithProperties:properties];
}

-(EAAccessory*)accessory
{
    if (!_accessory) {
        NSArray *accList = [[EAAccessoryManager sharedAccessoryManager] connectedAccessories];
        for(EAAccessory *acc in accList) {
            NSLog(@"acc found");
            if([[acc protocolStrings] containsObject:_protocolString]) {
                _accessory = [acc retain];
                break;
            }
        }
        if (_accessory) {
            _accessory.delegate = self;
        }
    }
    return _accessory;
}

-(void)connect:(id)args
{
    if (_session) {
        return;
    }

    if ([self accessory]) {
        _session = [[EASession alloc] initWithAccessory:_accessory forProtocol:_protocolString];
    } else {
        [self fireEvent:@"error" withObject:[TiUtils dictionaryWithCode:-1 message:[NSString stringWithFormat:@"Accessory not found for protocol %@",_protocolString]]];
        return NO;
    }
    
    if(!_session) {
        NSLog(@"Couldn't create session!");
        [self fireEvent:@"error" withObject:[TiUtils dictionaryWithCode:-1 message:[NSString stringWithFormat:@"Couldn't create session. Did you add the protocol \"%@\" to the Info.plist?",_protocolString]]];
        return NO;
    }
    
    // Set our BGdemoStreamDelegate instance to receive stream events
    [_session.outputStream setDelegate:self];
    [_session.inputStream setDelegate:self];
    
    // Schedule stream I/O in the current runloop
    [_session.inputStream scheduleInRunLoop:[NSRunLoop mainRunLoop] forMode:NSDefaultRunLoopMode];
    [_session.outputStream scheduleInRunLoop:[NSRunLoop mainRunLoop] forMode:NSDefaultRunLoopMode];
    
    // Open fire
    [_session.inputStream open];
    [_session.outputStream open];
    if (_session.inputStream.streamStatus == NSStreamStatusOpen) {
        [self fireEvent:@"connected"];
    }
}

-(void)disconnect:(id)args
{
    if (!_session) {
        return;
    }
    [self fireEvent:@"disconnected"];
    if (_session) {
        [[_session inputStream] close];
        [[_session inputStream] removeFromRunLoop:[NSRunLoop mainRunLoop] forMode:NSDefaultRunLoopMode];
        [[_session inputStream] setDelegate:nil];
        [[_session outputStream] close];
        [[_session outputStream] removeFromRunLoop:[NSRunLoop mainRunLoop] forMode:NSDefaultRunLoopMode];
        [[_session outputStream] setDelegate:nil];
        RELEASE_TO_NIL(_session);
    }
}
-(id)connected
{
    if (_session) {
        return NUMBOOL(YES);
    }
    return NUMBOOL(NO);
}

-(id)paired
{
//    if (_accessory && _session) {
        return NUMBOOL([[self accessory] isConnected]);
//    }
//    return NUMBOOL(NO);
}

-(id)connectionID
{
//    if (_accessory) {
        return NUMUINTEGER([[self accessory] connectionID]);
//    }
//    return NUMUINTEGER(-1);
}

-(id)manufacturer
{
//    if (_accessory) {
        return [[self accessory] manufacturer];
//    }
//    return nil;
}

-(id)modelNumber
{
//    if (_accessory) {
        return [[self accessory] modelNumber];
//    }
//    return nil;
}

-(id)serialNumber
{
//    if (_accessory) {
        return [[self accessory] serialNumber];
//    }
//    return nil;
}

-(id)firmwareRevision
{
//    if (_accessory) {
        return [[self accessory] firmwareRevision];
//    }
//    return nil;
}

-(id)hardwareRevision
{
//    if (_accessory) {
        return [[self accessory] hardwareRevision];
//    }
//    return nil;
}

-(NSData *)dataWithContentsOfStream:(NSInputStream *)input initialCapacity:(NSUInteger)capacity error:(NSError **)error {
    size_t bufsize = MIN(BUFSIZE, capacity);
    uint8_t buf[bufsize];
    NSMutableData* result = capacity == NSUIntegerMax ? [NSMutableData data] : [NSMutableData dataWithCapacity:capacity];
    @try {
        while ([input hasBytesAvailable]) {
            NSInteger n = [input read:buf maxLength:bufsize];
            if (n < 0) {
                result = nil;
                if (error) {
                    *error = [NSError errorWithDomain:NSPOSIXErrorDomain code:errno userInfo:nil];
                }
                break;
            }
            else if (n == 0) {
                break;
            }
            else {
                [result appendBytes:(const void *)buf length:n];
            }
        }
    }
    @catch (NSException * exn) {
        NSLog(@"Caught exception writing to file: %@", exn);
        result = nil;
        if (error) {
            *error = [NSError errorWithDomain:NSPOSIXErrorDomain code:EIO userInfo:nil];
        }
    }
    
    free(buf);
    return result;
}

// This is called when we get an incoming data event. Notify the appDelegate that we have data to print.
- (void)handleIncoming:(NSInputStream*)stream {
    double timestamp = [[NSDate date] timeIntervalSince1970]*1000;
    NSError* error = nil;
    NSData* result = [self dataWithContentsOfStream:stream initialCapacity:DATA_CHUNK_SIZE error:&error];
    if (error) {
        [self fireEvent:@"error" withObject:[TiUtils dictionaryWithCode:[error code] message:[TiUtils messageFromError:error]]];
    } else if (result.length > 0) {
        [self fireEvent:@"read" withObject:@{
                                             @"timestamp":NUMDOUBLE(timestamp),
                                             @"length":NUMUINTEGER(result.length),
                                             @"data":[[[TiBlob alloc] initWithData:result mimetype:@"application/octet-stream"] autorelease]}];
    }
    //        unsigned char buf[DATA_CHUNK_SIZE];
    //        NSUInteger len;
    //        len = [stream read:buf maxLength:DATA_CHUNK_SIZE];
    //        if(len>0) {
    //
    //        }
}

- (void)handleSpace:(NSOutputStream*)stream {
    if ([_session.outputStream streamStatus] != NSStreamStatusOpen &&
        [_session.outputStream streamStatus] != NSStreamStatusWriting) {
        NSLog(@"handleSpace: streamStatus invalid!");
        return;
    }
    
    @try {
        NSInteger done = [_session.outputStream write:[_outbuf bytes] maxLength:[_outbuf length]];
        if(done > 0) [_outbuf replaceBytesInRange:NSMakeRange(0, done) withBytes:nil length:0]; // Remove sent bytes from buffer
    }
    @catch (NSException * exn) {
        NSLog(@"Caught exception writing to stream: %@", exn);
    }
    
}

- (void)sendData:(uint8_t*)data length:(NSUInteger)len {
    [_outbuf appendBytes:data length:len];
    if([_session.outputStream hasSpaceAvailable]) {
        [self handleSpace:_session.outputStream];
    }
}

- (void)sendData:(NSData*)data {
    [_outbuf appendData:data];
    if([_session.outputStream hasSpaceAvailable]) {
        [self handleSpace:_session.outputStream];
    }

}

-(void)send:(id)args
{
    
    ENSURE_SINGLE_ARG(args, NSObject)
    if (IS_OF_CLASS(args, TiBlob)) {
        TiBlob *blob = (TiBlob*)args;
        [self sendData:blob.data];
    }
    else if (IS_OF_CLASS(args, NSString)) {
        // called within this class
        [self sendData: [args dataUsingEncoding:NSUTF8StringEncoding]];
    }else if (IS_OF_CLASS(args, NSArray)) {
        //supposed to be a byte array
        [self sendData: [NSKeyedArchiver archivedDataWithRootObject:args]];
    }
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
    
    if(stream == _session.inputStream && event & NSStreamEventHasBytesAvailable) {
        [self handleIncoming:(NSInputStream*) stream];
    }
    
    if(stream == _session.outputStream && event & NSStreamEventHasSpaceAvailable) {
        [self handleSpace:(NSOutputStream*) stream];
    }
}

- (void)dealloc {
    [self disconnect:nil];
    RELEASE_TO_NIL(_accessory)
    RELEASE_TO_NIL(_protocolString)
    RELEASE_TO_NIL(_outbuf)
    [super dealloc];
}

- (void)accessoryDidDisconnect:(EAAccessory *)accessory {
    if (accessory == _accessory) {
        [self disconnect:nil];
        RELEASE_TO_NIL(_accessory)
    }
}


@end
#endif
