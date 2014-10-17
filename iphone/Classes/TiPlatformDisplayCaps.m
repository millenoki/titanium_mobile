/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_PLATFORM

#import "TiPlatformDisplayCaps.h"
#import "TiUtils.h"

@implementation TiPlatformDisplayCaps



// NOTE: device capabilities currently are hardcoded for iPad, while high/low
// display density is now detected for iPhone / iPod Touch under iOS 4.

- (id)density
{
    if ([TiUtils isRetinaHDDisplay]) {
        return @"xhigh";
    }
	if ([TiUtils isRetinaDisplay]) {
		return @"high";
	}
	return @"medium";
}

-(NSString*)apiName
{
    return @"Ti.Platform.DisplayCaps";
}

- (id)dpi
{
	return NUMINT([TiUtils dpi]);
}

- (BOOL)isDevicePortrait
{
	UIDeviceOrientation orientation = [UIDevice currentDevice].orientation;
	return  (orientation == UIDeviceOrientationPortrait || 
			 orientation == UIDeviceOrientationPortraitUpsideDown || 
			 orientation == UIDeviceOrientationUnknown);
}

-(BOOL)isUIPortrait
{
	UIInterfaceOrientation orientation = [[UIApplication sharedApplication] statusBarOrientation];
	return  UIInterfaceOrientationIsPortrait(orientation);
}


- (NSNumber*) platformWidth
{
    static int platformWidth;
    static dispatch_once_t predicate;
    dispatch_once(&predicate, ^{
        CGFloat scale = [[UIScreen mainScreen] scale];
        if ([TiUtils isIOS8OrGreater] || [self isUIPortrait])
        {
            platformWidth = [[UIScreen mainScreen] bounds].size.width * scale;
        }
        else
        {
            platformWidth = [[UIScreen mainScreen] bounds].size.height * scale;
        }
    });
    
    return NUMFLOAT(platformWidth);
}

- (NSNumber*) platformHeight
{
    static int platformHeight;
    static dispatch_once_t predicate;
    dispatch_once(&predicate, ^{
        CGFloat scale = [[UIScreen mainScreen] scale];
        if ([TiUtils isIOS8OrGreater] || [self isUIPortrait])
        {
            platformHeight = [[UIScreen mainScreen] bounds].size.height * scale;
        }
        else
        {
            platformHeight = [[UIScreen mainScreen] bounds].size.width * scale;
        }
    });
    
    return NUMFLOAT(platformHeight);
}

- (NSNumber*) logicalDensityFactor
{
	return [NSNumber numberWithFloat:[[UIScreen mainScreen] scale]];
}
@end

#endif