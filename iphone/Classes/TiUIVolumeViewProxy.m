/**
 * Akylas
 * Copyright (c) 2009-2010 by Akylas. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#ifdef USE_TI_UIVOLUMEVIEW

#import "TiUIVolumeViewProxy.h"

NSArray* keySequence;

@implementation TiUIVolumeViewProxy

-(NSArray *)keySequence
{
    if (keySequence == nil)
    {
        keySequence = [[[super keySequence] arrayByAddingObjectsFromArray:@[@"leftTrackLeftCap",@"leftTrackTopCap",@"rightTrackLeftCap",@"rightTrackTopCap",
                                                                                  @"leftTrackImage",@"selectedLeftTrackImage", @"highlightedLeftTrackImage", @"disabledLeftTrackImage",
                                                                                  @"rightTrackImage",@"selectedRightTrackImage", @"highlightedRightTrackImage", @"disabledRightTrackImage"]] retain];
    }
    return keySequence;
}

-(NSString*)apiName
{
    return @"Ti.UI.VolumeView";
}

-(void)_initWithProperties:(NSDictionary *)properties
{
    [super _initWithProperties:properties];
}


-(UIViewAutoresizing)verifyAutoresizing:(UIViewAutoresizing)suggestedResizing
{
    return suggestedResizing & ~UIViewAutoresizingFlexibleHeight;
}

-(TiDimension)defaultAutoHeightBehavior:(id)unused
{
    return TiDimensionAutoSize;
}


USE_VIEW_FOR_VERIFY_HEIGHT



@end

#endif