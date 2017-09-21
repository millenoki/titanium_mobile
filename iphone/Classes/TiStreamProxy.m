/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#import "TiStreamProxy.h"
#import "TiUtils.h"
@implementation TiStreamProxy

#pragma mark Public API : Functions

- (NSString *)apiName
{
  return @"Ti.IOStream";
}

- (NSNumber *)read:(id)args
{
  if (![[self isReadable:nil] boolValue]) {
    // TODO: Codify exception name
    [self throwException:@"StreamException"
               subreason:@"Stream is not readable"
                location:CODELOCATION];
  }

  TiBuffer *buffer = nil;
  id offset = nil;
  id length = nil;
  id position = nil;
  KrollCallback *callback = nil;

  ENSURE_ARG_AT_INDEX(buffer, args, 0, TiBuffer);
  ENSURE_ARG_OR_NIL_AT_INDEX(offset, args, 1, NSObject);
  ENSURE_ARG_OR_NIL_AT_INDEX(length, args, 2, NSObject);
  ENSURE_ARG_OR_NIL_AT_INDEX(position, args, 3, NSObject);
  ENSURE_ARG_OR_NIL_AT_INDEX(callback, args, 4, KrollCallback);

  if (offset == nil && length == nil) {
    return NUMINTEGER([self readToBuffer:buffer offset:0 length:[[buffer data] length] position:position callback:callback]);
  } else {
    if (offset == nil || length == nil) {
      // TODO: Codify behavior
      [self throwException:@"StreamException"
                 subreason:@"Requires OFFSET or LENGTH value"
                  location:CODELOCATION];
    }

    NSInteger offsetValue = [TiUtils intValue:offset];
    BOOL valid = NO;
    NSUInteger lengthValue = [TiUtils intValue:length def:0 valid:&valid];
    if (!valid) {
      lengthValue = [[buffer data] length];
    }

    // TODO: Throw exception
    if (offsetValue != 0 && offsetValue >= lengthValue) {
      NSString *errorStr = [NSString stringWithFormat:@"[ERROR] Offset %ld is past buffer bounds (length %lu)", (long)offsetValue, (unsigned long)[[buffer data] length]];
      NSLog(errorStr);
      return NUMINT(-1);
    }

    return NUMINTEGER([self readToBuffer:buffer offset:offsetValue length:lengthValue position:position callback:callback]);
  }

  return NUMINT(-1);
}

- (NSNumber *)write:(id)args
{
  if (![[self isWritable:nil] boolValue]) {
    // TODO: Codify exception name
    [self throwException:@"StreamException"
               subreason:@"Stream is not writable"
                location:CODELOCATION];
  }

  NSObject *dataArg = nil;
  id offset = nil; // May need to perform type coercion from string->int
  id length = nil;

  ENSURE_ARG_AT_INDEX(dataArg, args, 0, NSObject);
  ENSURE_ARG_OR_NIL_AT_INDEX(offset, args, 1, NSObject);
  ENSURE_ARG_OR_NIL_AT_INDEX(length, args, 2, NSObject);

  NSData *data = [TiUtils dataValue:dataArg];

  if (offset == nil && length == nil) {
    return NUMINTEGER([self writeData:data offset:0 length:[data length] callback:nil]);
  } else {
    if (offset == nil || length == nil) {
      // TODO: Codify behavior
      [self throwException:@"StreamException"
                 subreason:@"Invalid OFFSET or LENGTH value"
                  location:CODELOCATION];
    }

    NSInteger offsetValue = [TiUtils intValue:offset];
    BOOL valid = NO;
    NSUInteger lengthValue = [TiUtils intValue:length def:0 valid:&valid];
    if (!valid) {
      lengthValue = [data length];
    }

    // TODO: Throw exception
    if (offsetValue != 0 && offsetValue >= lengthValue) {
      NSString *errorStr = [NSString stringWithFormat:@"[ERROR] Offset %ld is past buffer bounds (length %lu)", (long)offsetValue, (unsigned long)[data length]];
      NSLog(errorStr);
      return NUMINT(-1);
    }

    return NUMINTEGER([self writeData:data offset:offsetValue length:lengthValue callback:nil]);
  }

  return NUMINT(-1);
}

#pragma mark Protocol stubs

- (NSInteger)readToBuffer:(TiBuffer *)buffer offset:(NSInteger)offset length:(NSInteger)length position:(NSNumber *)position callback:(KrollCallback *)callback
{
  [self throwException:@"Incomplete stream implementation" subreason:[NSString stringWithFormat:@"Missing %@", NSStringFromSelector(_cmd)] location:CODELOCATION];
}

- (NSInteger)writeFromBuffer:(TiBuffer *)buffer offset:(NSInteger)offset length:(NSInteger)length callback:(KrollCallback *)callback
{
  [self writeData:[buffer data] offset:offset length:length callback:callback];
}
- (NSInteger)writeData:(NSData *)data offset:(NSInteger)offset length:(NSInteger)length callback:(KrollCallback *)callback
{
  [self throwException:@"Incomplete stream implementation" subreason:[NSString stringWithFormat:@"Missing %@", NSStringFromSelector(_cmd)] location:CODELOCATION];
}

- (NSInteger)writeToStream:(id<TiStreamInternal>)output chunkSize:(NSInteger)size callback:(KrollCallback *)callback
{
  [self throwException:@"Incomplete stream implementation" subreason:[NSString stringWithFormat:@"Missing %@", NSStringFromSelector(_cmd)] location:CODELOCATION];
}
- (void)pumpToCallback:(KrollCallback *)callback chunkSize:(NSInteger)size asynch:(BOOL)asynch
{
  [self throwException:@"Incomplete stream implementation" subreason:[NSString stringWithFormat:@"Missing %@", NSStringFromSelector(_cmd)] location:CODELOCATION];
}

- (NSNumber *)isReadable:(id)_void
{
  [self throwException:@"Incomplete stream implementation" subreason:[NSString stringWithFormat:@"Missing %@", NSStringFromSelector(_cmd)] location:CODELOCATION];
}

- (NSNumber *)isWritable:(id)_void
{
  [self throwException:@"Incomplete stream implementation" subreason:[NSString stringWithFormat:@"Missing %@", NSStringFromSelector(_cmd)] location:CODELOCATION];
}

- (void)close:(id)_void
{
  [self throwException:@"Incomplete stream implementation" subreason:[NSString stringWithFormat:@"Missing %@", NSStringFromSelector(_cmd)] location:CODELOCATION];
}

@end
