/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#import "TopTiModule.h"
#import "KrollBridge.h"
#import "TiApp.h"
#import "TiBase.h"
#import "TiBuffer.h"
#import "TiUtils.h"

#ifdef KROLL_COVERAGE
#include "KrollCoverage.h"
#endif

@implementation TopTiModule {
  NSString *_defaultUserAgent;
  KrollCallback *_prepareErrorHandler;
}

- (void)dealloc
{
  RELEASE_TO_NIL(_prepareErrorHandler)
  [super dealloc];
}

- (id)version
{
  return @"__VERSION__";
}

- (id)buildDate
{
  return @"__TIMESTAMP__";
}

- (id)buildHash
{
  return @"__GITHASH__";
}

+ (BOOL)shouldRegisterOnInit
{
  return NO;
}

- (id)userAgent
{
  return [[TiApp app] userAgent];
}

- (void)setUserAgent:(id)value
{
  ENSURE_TYPE(value, NSString);
  [[TiApp app] setUserAgent:[TiUtils stringValue:value]];
}

- (id)defaultUserAgent
{
  if (!_defaultUserAgent) {
    TiThreadPerformOnMainThread(^{
      UIWebView *webView = [[UIWebView alloc] initWithFrame:CGRectZero];
      _defaultUserAgent = [[webView stringByEvaluatingJavaScriptFromString:@"navigator.userAgent"] retain];
      [webView release];

    },
        YES);
  }
  return _defaultUserAgent;
}

- (id)tiSDKInfo
{
  return @{
    @"version" : [self version],
    @"buildDate" : [self buildDate],
    @"buildHash" : [self buildHash]
  };
}

- (NSString *)apiName
{
  return @"Ti";
}

- (void)include:(NSArray *)jsfiles
{
  id<TiEvaluator> context = [self executionContext];
  for (id file in jsfiles) {
    [context includeFile:file withBaseUrl:[self _baseURL]];
  }
}

- (id)resourcesRelativePath
{
  id<TiEvaluator> context = [self executionContext];
  NSURL *oldUrl = [context currentURL];
  NSURL *rootURL = (oldUrl != nil) ? oldUrl : [self _baseURL];
  NSString *result = [[rootURL absoluteString] stringByReplacingOccurrencesOfString:[[self _baseURL] absoluteString] withString:@""];
  return result;
}

#ifdef DEBUG
// an internal include that works with absolute URLs (debug mode only)
- (void)includeAbsolute:(NSArray *)jsfiles
{
  for (id file in jsfiles) {
    DebugLog(@"[DEBUG] Absolute url: %@", file);

    NSURL *url = nil;
    if (![file hasPrefix:@"file:"]) {
      url = [NSURL URLWithString:file];
    } else {
      url = [[NSURL fileURLWithPath:file] standardizedURL];
    }
    [[self executionContext] evalFile:[url absoluteString]];
  }
}
#endif


- (TiBlob *)createBlob:(id)arg
{
    ENSURE_SINGLE_ARG_OR_NIL(arg, NSDictionary);
    
    id data;
    NSString *mimetype;
    
    ENSURE_ARG_OR_NIL_FOR_KEY(data, arg, @"value", NSObject);
    ENSURE_ARG_OR_NIL_FOR_KEY(mimetype, arg, @"mimetype", NSString);
    if (mimetype == nil) {
        mimetype = @"application/octet-stream";
    }
    
    TiBlob *blob = [[[TiBlob alloc] _initWithPageContext:[self pageContext] andData:[TiUtils dataValue:data] mimetype:mimetype] autorelease];
    
    return blob;
}

- (TiBuffer *)createBuffer:(id)arg
{
  ENSURE_SINGLE_ARG_OR_NIL(arg, NSDictionary);

  int length;
  BOOL hasLength;
  id data;
  NSString *type;
  CFByteOrder byteOrder;
  BOOL hasByteOrder;

  ENSURE_INT_OR_NIL_FOR_KEY(length, arg, @"length", hasLength);
  ENSURE_ARG_OR_NIL_FOR_KEY(data, arg, @"value", NSObject);
  ENSURE_ARG_OR_NIL_FOR_KEY(type, arg, @"type", NSString);
  ENSURE_INT_OR_NIL_FOR_KEY(byteOrder, arg, @"byteOrder", hasByteOrder);

  TiBuffer *buffer = [[[TiBuffer alloc] _initWithPageContext:[self executionContext]] autorelease];
  if (hasLength) {
    [buffer setLength:[NSNumber numberWithInt:length]];
  }

  // NOTE: We use the length of the buffer as a hint when encoding strings.  In this case, if [string length] > length,
  // we only encode up to 'length' of the string.
  if ([data isKindOfClass:[NSString class]]) {
    NSUInteger encodeLength = (hasLength) ? length : [data length];

    NSString *charset = (type != nil) ? type : kTiUTF8Encoding;

    // Just put the string data directly into the buffer, if we can.
    if (!hasLength) {
      NSStringEncoding encoding = [TiUtils charsetToEncoding:charset];
      [buffer setData:[NSMutableData dataWithData:[data dataUsingEncoding:encoding]]];
    } else {
      switch ([TiUtils encodeString:data toBuffer:buffer charset:charset offset:0 sourceOffset:0 length:encodeLength]) {
      case BAD_DEST_OFFSET: // Data length == 0 : return our empty buffer
      case BAD_SRC_OFFSET: { // String length == 0 : return our empty buffer (no string encoded into it)
        return buffer;
        break;
      }
      case BAD_ENCODING: {
        [self throwException:[NSString stringWithFormat:@"Invalid string encoding type '%@'", charset]
                   subreason:nil
                    location:CODELOCATION];
        break;
      }
      }
    }
  } else if ([data isKindOfClass:[NSNumber class]]) {
    if (type == nil) {
      [self throwException:[NSString stringWithFormat:@"Missing required type information for buffer created with number %@", data]
                 subreason:nil
                  location:CODELOCATION];
    }

    if (!hasLength) {
      length = [TiUtils dataSize:[TiUtils constantToType:type]];
      [buffer setLength:NUMINT(length)];
    }

    byteOrder = (hasByteOrder) ? byteOrder : CFByteOrderGetCurrent();
    [buffer setByteOrder:NUMLONG(byteOrder)];
    switch ([TiUtils encodeNumber:data toBuffer:buffer offset:0 type:type endianness:byteOrder]) {
    case BAD_ENDIAN: {
      [self throwException:[NSString stringWithFormat:@"Invalid endianness: %ld", byteOrder]
                 subreason:nil
                  location:CODELOCATION];
      break;
    }
    case BAD_DEST_OFFSET: { // Buffer size == 0; throw exception for numbers (is this right?!?)
      NSString *errorStr = [NSString stringWithFormat:@"Offset %d is past buffer bounds (length %d)", 0, length];
      [self throwException:errorStr
                 subreason:nil
                  location:CODELOCATION];
      break;
    }
    case BAD_TYPE: {
      [self throwException:[NSString stringWithFormat:@"Invalid type identifier '%@'", type]
                 subreason:nil
                  location:CODELOCATION];
      break;
    }
    case TOO_SMALL: { // This makes sense, at least.
      [self throwException:[NSString stringWithFormat:@"Buffer of length %d too small to hold type %@", length, type]
                 subreason:nil
                  location:CODELOCATION];
      break;
    }
    }
  } else if (data) {
    [buffer setData:[NSMutableData dataWithData:[TiUtils dataValue:data]]];
  }

  return buffer;
}

- (NSDictionary *)dumpCoverage:(id)unused_
{
#ifdef KROLL_COVERAGE
  NSDictionary *coverage = [KrollCoverageObject dumpCoverage];
  return coverage;
#else
  return [NSDictionary dictionary];
#endif
}

- (void)setPrepareError:(KrollCallback *)callback
{
  RELEASE_TO_NIL(_prepareErrorHandler);
  _prepareErrorHandler = [callback retain];
}

- (NSDictionary *)prepareErrorArgs:(NSDictionary *)args
{
  //    id test = [self valueForUndefinedKey:@"prepareError"];
  if (_prepareErrorHandler) {
    return [_prepareErrorHandler call:@[ args ] thisObject:self];
  }
  return args;
}

@end
