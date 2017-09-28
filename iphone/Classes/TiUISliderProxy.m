/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UISLIDER

#import "TiUISliderProxy.h"

@implementation TiUISliderProxy

- (NSArray *)keySequence
{
  static NSArray *keySequence = nil;
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    keySequence = [[[super keySequence] arrayByAddingObjectsFromArray:@[ @"min", @"max", @"value", @"leftTrackLeftCap", @"leftTrackTopCap", @"rightTrackLeftCap", @"rightTrackTopCap",
      @"leftTrackImage", @"selectedLeftTrackImage", @"highlightedLeftTrackImage", @"disabledLeftTrackImage",
      @"rightTrackImage", @"selectedRightTrackImage", @"highlightedRightTrackImage", @"disabledRightTrackImage" ]] retain];
    ;
  });
  return keySequence;
}

- (NSString *)apiName
{
  return @"Ti.UI.Slider";
}

- (void)_initWithProperties:(NSDictionary *)properties
{
  [super _initWithProperties:properties];
}

- (UIViewAutoresizing)verifyAutoresizing:(UIViewAutoresizing)suggestedResizing
{
  return suggestedResizing & ~UIViewAutoresizingFlexibleHeight;
}

- (TiDimension)defaultAutoHeightBehavior:(id)unused
{
  return TiDimensionAutoSize;
}

USE_VIEW_FOR_VERIFY_HEIGHT

@end

#endif
