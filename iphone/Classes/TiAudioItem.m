/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_AUDIO
#import "TiAudioItem.h"
#import "AudioModule.h"

@implementation TiAudioItem

#pragma mark Internal

- (id)_initWithPageContext:(id<TiEvaluator>)context item:(MPMediaItem *)item_
{
  if (self = [super _initWithPageContext:context]) {
    item = [item_ retain];
  }
  return self;
}

- (void)dealloc
{
  RELEASE_TO_NIL(item);
  [super dealloc];
}

- (NSString *)apiName
{
  return @"Ti.Audio.Item";
}

- (MPMediaItem *)item
{
  return item;
}

#pragma mark Properties

- (TiBlob *)artwork
{
  MPMediaItemArtwork *artwork = [item artwork];
  if (artwork != nil) {
    return [[[TiBlob alloc] _initWithPageContext:[self executionContext] andImage:[artwork imageWithSize:[artwork imageCropRect].size]] autorelease];
  }
  return nil;
}

- (NSString *)persistentID
{
  return [NSString stringWithFormat:@"%lld", [item persistentID]];
}

// Handle all properties automatically
- (id)valueForUndefinedKey:(NSString *)key
{
  id propertyName = [[AudioModule itemProperties] objectForKey:key];
  if (propertyName == nil) {
    propertyName = [[AudioModule filterableItemProperties] objectForKey:key];
    if (propertyName == nil) {
      return [super valueForUndefinedKey:key];
    }
  }
  return [item valueForProperty:propertyName];
}

@end

#endif
