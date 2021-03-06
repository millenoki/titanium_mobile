/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2018 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#import "TiBuffer.h"
#import "TiUtils.h"

NSArray *bufferKeySequence = nil;

@implementation TiBuffer
@synthesize data, byteOrder;

#pragma mark Internals

- (id)init
{
  if (self = [super init]) {
    byteOrder = [NUMLONG(CFByteOrderGetCurrent()) retain];
  }
  return self;
}

- (void)dealloc
{
  RELEASE_TO_NIL(data);
  RELEASE_TO_NIL(byteOrder);
  [super dealloc];
}

- (NSString *)apiName
{
  return @"Ti.Buffer";
}

- (NSArray *)keySequence
{
  if (bufferKeySequence == nil) {
    bufferKeySequence = [[NSArray arrayWithObjects:@"length", nil] retain];
  }
  return bufferKeySequence;
}

#pragma mark Public API : Functions

// NOTE: Translating to 32-bit values is OK here... NSData is a 32-bit length max on iOS!  It's unsigned, as well.
// However, SIGNED ints allow access to up to 1GB of data as far as addressing... but it might still be worthwhile to coerce.
// Although we can always just place this limit in documentation, because it makes life easier on us (one conversion to int vs.
// two conversions, to int and uint - one for typechecking, the other for indexing.)
- (NSNumber *)append:(id)args
{
  NSObject *source = nil;
  NSData *sourceData = nil;
  NSUInteger sourceOffset;
  BOOL hasSourceOffset;
  NSUInteger sourceLength;
  BOOL hasSourceLength;

  ENSURE_ARG_AT_INDEX(source, args, 0, NSObject);
  ENSURE_INT_OR_NIL_AT_INDEX(sourceOffset, args, 1, hasSourceOffset);
  ENSURE_INT_OR_NIL_AT_INDEX(sourceLength, args, 2, hasSourceLength);

  if (IS_OF_CLASS(source, TiBuffer)) {
    sourceData = [(TiBuffer *)source data];
  } else if (IS_OF_CLASS(source, TiBlob)) {
    sourceData = [(TiBlob *)source data];
  } else {
    [self throwException:[NSString stringWithFormat:@"cant copy from source"]
               subreason:nil
                location:CODELOCATION];
  }

  sourceOffset = (hasSourceOffset) ? sourceOffset : 0;
  sourceLength = (hasSourceLength) ? sourceLength : [sourceData length];

  if (hasSourceOffset && !hasSourceLength) {
    [self throwException:@"TiArgsException"
               subreason:@"Ti.Buffer.append(buf,offset,length) requires three arguments"
                location:CODELOCATION];
  }

  if (sourceOffset + sourceLength > [sourceData length]) {
    [self throwException:@"TiBoundsException"
               subreason:[NSString stringWithFormat:@"Source offset %lu plus sourceLength %lu extends past source bounds (length %lu)", (unsigned long)sourceOffset, (unsigned long)sourceLength, (unsigned long)[sourceData length]]
                location:CODELOCATION];
  }

  NSUInteger length = MIN(sourceLength, [sourceData length] - sourceOffset);
  const void *bytes = [sourceData bytes];
  if (data == nil) {
    data = [[NSMutableData alloc] initWithBytes:bytes + sourceOffset length:length];
  } else {
    [data appendBytes:(bytes + sourceOffset) length:length];
  }

  return NUMUINTEGER(length);
}

- (NSNumber *)insert:(id)args
{
  NSObject *source = nil;
  NSData *sourceData = nil;
  int offset;
  int sourceOffset;
  BOOL hasSourceOffset;
  NSUInteger sourceLength;
  BOOL hasSourceLength;

  ENSURE_ARG_AT_INDEX(source, args, 0, NSObject);
  ENSURE_INT_AT_INDEX(offset, args, 1);
  ENSURE_INT_OR_NIL_AT_INDEX(sourceOffset, args, 2, hasSourceOffset);
  ENSURE_INT_OR_NIL_AT_INDEX(sourceLength, args, 3, hasSourceLength);

  if (IS_OF_CLASS(source, TiBuffer)) {
    sourceData = [(TiBuffer *)source data];
  } else if (IS_OF_CLASS(source, TiBlob)) {
    sourceData = [(TiBlob *)source data];
  } else {
    [self throwException:[NSString stringWithFormat:@"cant copy from source"]
               subreason:nil
                location:CODELOCATION];
  }

  sourceOffset = (hasSourceOffset) ? sourceOffset : 0;
  sourceLength = (hasSourceLength) ? sourceLength : [sourceData length];

  if (hasSourceOffset && !hasSourceLength) {
    [self throwException:@"TiArgsException"
               subreason:@"Ti.Buffer.insert(buf,offset,sourceOffset,sourceLength) requires three arguments"
                location:CODELOCATION];
  }
  if (offset >= [data length]) {
    [self throwException:@"TiBoundsException"
               subreason:[NSString stringWithFormat:@"Offset %d is past buffer bounds (length %lu)", offset, (unsigned long)[data length]]
                location:CODELOCATION];
  }
  if (sourceOffset + sourceLength > [sourceData length]) {
    [self throwException:@"TiBoundsException"
               subreason:[NSString stringWithFormat:@"Source offset %d plus sourceLength %lu extends past source bounds (length %lu)", sourceOffset, (unsigned long)sourceLength, (unsigned long)[sourceData length]]
                location:CODELOCATION];
  }

  // There is no natural 'insert' operation on NSData, so we perform it as so:
  // 1. Extend the data the amount of 'length'
  // 2. "shift" the existing data from 'offset' over 'length'
  // 3. Copy the bytes into the data starting at 'offset'.

  // Here we have 2 possible lengths: sourceLength, or the data from sourceOffset to the end of the source buffer.
  // We're extending the buffer, so the end of our current data is IRRELEVANT.
  NSUInteger length = MIN(sourceLength, [sourceData length] - sourceOffset);

  // 1.
  [data increaseLengthBy:length];

  // 2.
  const void *currentBytes = [data bytes];
  [data replaceBytesInRange:NSMakeRange(offset + length, [data length] - (offset + length)) withBytes:(currentBytes + offset)];

  // 3.
  const void *newBytes = [sourceData bytes];
  [data replaceBytesInRange:NSMakeRange(offset, length) withBytes:(newBytes + sourceOffset)];

  return NUMUINTEGER(length);
}

- (NSNumber *)copy:(id)args
{
  NSObject *source = nil;
  NSData *sourceData = nil;
  int offset;
  int sourceOffset;
  BOOL hasSourceOffset;
  NSUInteger sourceLength;
  BOOL hasSourceLength;

  ENSURE_ARG_AT_INDEX(source, args, 0, NSObject);
  ENSURE_INT_AT_INDEX(offset, args, 1);
  ENSURE_INT_OR_NIL_AT_INDEX(sourceOffset, args, 2, hasSourceOffset);
  ENSURE_INT_OR_NIL_AT_INDEX(sourceLength, args, 3, hasSourceLength);

  if (IS_OF_CLASS(source, TiBuffer)) {
    sourceData = [(TiBuffer *)source data];
  } else if (IS_OF_CLASS(source, TiBlob)) {
    sourceData = [(TiBlob *)source data];
  } else {
    [self throwException:[NSString stringWithFormat:@"cant copy from source"]
               subreason:nil
                location:CODELOCATION];
  }

  sourceOffset = (hasSourceOffset) ? sourceOffset : 0;
  sourceLength = (hasSourceLength) ? sourceLength : [sourceData length];

  if (offset + sourceLength > [data length]) {
    [self throwException:@"TiBoundsException"
               subreason:[NSString stringWithFormat:@"Offset %d plus sourceLength %lu extends past bounds (length %lu)", offset, (unsigned long)sourceLength, (unsigned long)[data length]]
                location:CODELOCATION];
  }
  if (sourceOffset + sourceLength > [sourceData length]) {
    [self throwException:@"TiBoundsException"
               subreason:[NSString stringWithFormat:@"Source offset %d plus sourceLength %lu extends past source bounds (length %lu)", sourceOffset, (unsigned long)sourceLength, (unsigned long)[sourceData length]]
                location:CODELOCATION];
  }

  const void *sourceBytes = [sourceData bytes];
  NSRange replacement = NSMakeRange(offset, sourceLength);
  [data replaceBytesInRange:replacement withBytes:(sourceBytes + sourceOffset)];
//ignore leak, Xcode getting confused over the function name
#ifndef __clang_analyzer__
  return NUMUINTEGER(replacement.length);
#endif
}

- (TiBuffer *)clone:(id)args
{
  id offset = nil;
  id length = nil;

  ENSURE_ARG_OR_NIL_AT_INDEX(offset, args, 0, NSObject);
  ENSURE_ARG_OR_NIL_AT_INDEX(length, args, 1, NSObject);

  if (offset != nil && length == nil) {
    [self throwException:@"TiArgsException"
               subreason:@"Bad args to clone(): Expected (int offset, int length), found (int length)"
                location:CODELOCATION];
  }

  NSInteger offsetVal = [TiUtils intValue:offset];
  BOOL valid = NO;
  NSUInteger lengthVal = [TiUtils intValue:length def:0 valid:&valid];
  if (!valid) {
    lengthVal = [data length];
  }

  if (offsetVal + lengthVal > [data length]) {
    [self throwException:@"TiBoundsException"
               subreason:[NSString stringWithFormat:@"Offset %d plus length %lu extends past bounds (length %lu)", offsetVal, (unsigned long)lengthVal, (unsigned long)[data length]]
                location:CODELOCATION];
  }

  NSMutableData *cloneData = [[[NSMutableData alloc] initWithData:[data subdataWithRange:NSMakeRange(offsetVal, lengthVal)]] autorelease];

  TiBuffer *newBuffer = [[[TiBuffer alloc] _initWithPageContext:[self executionContext]] autorelease];
  [newBuffer setData:cloneData];
  [newBuffer setByteOrder:byteOrder];
  return newBuffer;
}

- (void)fill:(id)args
{
  id fillByte = nil;
  id offset = nil;
  id length = nil;

  ENSURE_ARG_AT_INDEX(fillByte, args, 0, NSObject);
  ENSURE_ARG_OR_NIL_AT_INDEX(offset, args, 1, NSObject);
  ENSURE_ARG_OR_NIL_AT_INDEX(length, args, 2, NSObject);

  if (offset != nil && length == nil) {
    [self throwException:@"TiArgsException"
               subreason:@"Bad args to fill(): Expected (int offset, int length), found (int length)"
                location:CODELOCATION];
  }

  char byte = [TiUtils intValue:fillByte];
  NSInteger offsetVal = [TiUtils intValue:offset];
  BOOL valid = NO;
  NSUInteger lengthVal = [TiUtils intValue:length def:0 valid:&valid];
  if (!valid) {
    lengthVal = [data length];
  }

  if (offsetVal + lengthVal > [data length]) {
    [self throwException:@"TiBoundsException"
               subreason:[NSString stringWithFormat:@"Offset %d plus length %lu extends past bounds (length %lu)", offsetVal, (unsigned long)lengthVal, (unsigned long)[data length]]
                location:CODELOCATION];
  }

  void *bytes = [data mutableBytes];
  for (NSUInteger i = offsetVal; i < offsetVal + lengthVal; i++) {
    *(char *)(bytes + i) = byte;
  }
}

- (void)clear:(id)_void
{
  [data resetBytesInRange:NSMakeRange(0, [data length])];
}

- (void)release:(id)_void
{
  RELEASE_TO_NIL(data);
}

- (TiBlob *)toBlob:(id)_void
{
  //TODO: Static analysis finds we're leaking the [data copy]. We should have an autorelease here, but for later.
  return [[[TiBlob alloc] _initWithPageContext:[self executionContext] andData:[[data copy] autorelease] mimetype:@"application/octet-stream"] autorelease];
}

- (NSString *)text
{
  return [[[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding] autorelease];
}

- (NSString *)hexString
{
  /* Returns hexadecimal string of NSData. Empty string if data is empty.   */

  const unsigned char *dataBuffer = (const unsigned char *)[data bytes];

  if (!dataBuffer) {
    return [NSString string];
  }

  NSUInteger dataLength = [data length];
  NSMutableString *hexString = [NSMutableString stringWithCapacity:(dataLength * 2)];

  for (int i = 0; i < dataLength; ++i) {
    [hexString appendFormat:@"%02x", (unsigned int)dataBuffer[i]];
  }

  return [NSString stringWithString:hexString];
}

- (NSArray *)bytes
{
  return [TiUtils byteArray:[self data]];
}

- (NSArray *)byteArray
{
  return [self bytes];
}

- (NSString *)toString:(id)_void
{
  return [self text];
}

#pragma mark Public API : Properties

- (void)setLength:(NSNumber *)length
{
  NSInteger len = [TiUtils intValue:length];
  if (len == 0) {
    RELEASE_TO_NIL(data);
    return;
  }

  if (data == nil) {
    data = [[NSMutableData alloc] initWithLength:len];
  } else {
    [data setLength:len];
  }
}

- (NSNumber *)length
{
  return NUMUINTEGER([data length]);
}

#pragma mark "operator[] overload" (Array behavior)

- (void)setValue:(id)value forUndefinedKey:(NSString *)key
{
  int index = [key intValue];
  // -[NSString intValue] returns 0 for unparsables; so check to for isEqual:@"0" on that value
  if (index != 0 || [key isEqualToString:@"0"]) {
    if (index < 0 || index >= [data length]) {
      [self throwException:@"TiBoundsException"
                 subreason:[NSString stringWithFormat:@"Index %d out of bounds on buffer (length %lu)", index, (unsigned long)[data length]]
                  location:CODELOCATION];
    }
    if (![value respondsToSelector:@selector(charValue)]) {
      [self throwException:@"TiTypeException"
                 subreason:[NSString stringWithFormat:@"Object %@ cannot be converted to a byte", value]
                  location:CODELOCATION];
      return;
    }

    void *bytes = [data mutableBytes];
    *(unsigned char *)(bytes + index) = (unsigned char)[value charValue];
  } else {
    [super setValue:value forUndefinedKey:key];
  }
}

- (id)valueForUndefinedKey:(NSString *)key
{
  int index = [key intValue];
  if (index != 0 || [key isEqualToString:@"0"]) {
    if (index < 0 || index >= [data length]) {
      [self throwException:@"TiBoundsException"
                 subreason:[NSString stringWithFormat:@"Index %d out of bounds on buffer (length %lu)", index, (unsigned long)[data length]]
                  location:CODELOCATION];
    }

    const void *bytes = [data bytes];
    // NOTE: We have to do this internal type conversion because in the id->TiValue process, a byte
    // is autotranslated to a boolean.  So we get the value as a char, then coerce to int.
    return [NSNumber numberWithInt:*(unsigned char *)(bytes + index)];
  } else {
    return [super valueForUndefinedKey:key];
  }
}

@end
