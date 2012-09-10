/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#import "TiModule.h"
#import <CoreMotion/CMMotionManager.h>

#ifdef USE_TI_MOTION

@interface MotionModule : TiModule {
	CMMotionManager *motionManager;
	CMDeviceMotionHandler motionHandler;
	NSOperationQueue* motionQueue;
	BOOL accelerometerRegistered;
	BOOL gyroscopeRegistered;
	BOOL magnetometerRegistered;
	BOOL orientationRegistered;
	BOOL motionRegistered;
    BOOL computeRotationMatrix;
    float updateInterval;
//    CMAttitude* referenceAttitude;
//    BOOL usingReference;
}
@property(nonatomic,readonly) NSNumber *STANDARD_GRAVITY;

@end

#endif