/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#if defined(USE_TI_AUDIOQUERYMUSICLIBRARY) || defined(USE_TI_AUDIOOPENMUSICLIBRARY) || defined(USE_TI_AUDIOMUSICPLAYER)
#import "TiAudioItem.h"
#import "AudioModule.h"


@implementation TiAudioItem

#pragma mark Internal

-(id)_initWithPageContext:(id<TiEvaluator>)context item:(MPMediaItem*)item_
{
	if (self = [super _initWithPageContext:context]) {
		item = [item_ retain];
	}
	return self;
}

-(void)dealloc
{
	RELEASE_TO_NIL(item);
	[super dealloc];
}

-(NSString*)apiName
{
    return @"Ti.Audio.Item";
}

-(MPMediaItem*)item
{
	return item;
}

#pragma mark Properties

-(TiBlob*)artwork
{
	MPMediaItemArtwork* artwork = [item valueForProperty:MPMediaItemPropertyArtwork];
	if (artwork != nil) {
		return [[[TiBlob alloc] _initWithPageContext:[self executionContext] andImage:[artwork imageWithSize:[artwork imageCropRect].size]] autorelease];
	}
	return nil;
}

// This is a sleazy way of getting properties so that I don't have to write 15 functions.
-(id)valueForUndefinedKey:(NSString *)key
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
