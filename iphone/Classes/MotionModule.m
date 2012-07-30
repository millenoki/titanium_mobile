/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_MOTION

#import "CoreLocation/CoreLocation.h"
#import "MotionModule.h"
#import "Ti3DMatrix.h"


@implementation MotionModule

 -(id)init
{
	if (self = [super init])
	{
		accelerometerRegistered = FALSE;
		gyroscopeRegistered = FALSE;
		magnetometerRegistered = FALSE;
        refreshRate = 30; // time between 2 data in ms
        computeRotationMatrix = TRUE;

		motionManager = [[CMMotionManager alloc] init];

        motionManager.deviceMotionUpdateInterval = 1.0/refreshRate;
        motionManager.accelerometerUpdateInterval = 1.0/refreshRate;
        motionManager.magnetometerUpdateInterval = 1.0/refreshRate;
		motionHandler = Block_copy(^(CMDeviceMotion *motion, NSError *error){
			[self processMotionData:motion withError:error];});
	}
	return self;
}

-(void)dealloc
{
	RELEASE_TO_NIL(motionManager);
	RELEASE_TO_NIL(motionHandler);
	RELEASE_TO_NIL(motionQueue);
	[super dealloc];
}

-(void)_listenerAdded:(NSString *)type count:(int)count
{
	if (count == 1)
	{
		BOOL needsStart = FALSE;
		if ([type isEqualToString:@"motion"])
		{
			needsStart = TRUE;
			motionRegistered = TRUE;
		}
		else if ([type isEqualToString:@"accelerometer"])
		{
			needsStart = TRUE;
			accelerometerRegistered = TRUE;
		}
		else if ([type isEqualToString:@"gyroscope"])
		{
			needsStart = TRUE;
			gyroscopeRegistered = TRUE;
		}
		else if ([type isEqualToString:@"magnetometer"])
		{
			needsStart = TRUE;
			magnetometerRegistered = TRUE;
		}
		if (needsStart && motionManager.deviceMotionAvailable
			&& !motionManager.deviceMotionActive) 
		{
            TiThreadPerformOnMainThread(^{
                if (([CMMotionManager availableAttitudeReferenceFrames] & CMAttitudeReferenceFrameXTrueNorthZVertical) != 0)
                {
                    [motionManager startDeviceMotionUpdatesUsingReferenceFrame:CMAttitudeReferenceFrameXTrueNorthZVertical toQueue:[NSOperationQueue currentQueue]
                        withHandler:motionHandler];
                }
                else
                {
                    [motionManager startDeviceMotionUpdatesToQueue:[NSOperationQueue currentQueue]
                                                                   withHandler:motionHandler];
                }
                
            }, YES);
		}
	}
}

-(void)_listenerRemoved:(NSString *)type count:(int)count
{
	if (count == 0)
	{
		if ([type isEqualToString:@"motion"])
		{
			motionRegistered = FALSE;
		}
		else if ([type isEqualToString:@"accelerometer"])
		{
			accelerometerRegistered = FALSE;
		}
		else if ([type isEqualToString:@"gyroscope"])
		{
			gyroscopeRegistered = FALSE;
		}
		else if ([type isEqualToString:@"magnetometer"])
		{
			magnetometerRegistered = FALSE;
		}
		if (!motionRegistered && !accelerometerRegistered && !gyroscopeRegistered && !magnetometerRegistered)
			[motionManager stopDeviceMotionUpdates];
	}
}

-(void) processMotionData: (CMDeviceMotion *) motion withError:(NSError *) error
{
    if (motionRegistered)
	{
        NSMutableDictionary *event = [NSMutableDictionary dictionaryWithObjectsAndKeys:
                                      [NSDictionary dictionaryWithObjectsAndKeys:
                                       NUMFLOAT(motion.gravity.x), @"gx",
                                       NUMFLOAT(motion.gravity.y), @"gy",
                                       NUMFLOAT(motion.gravity.z), @"gz",
                                       NUMFLOAT(motion.userAcceleration.x), @"ux",
                                       NUMFLOAT(motion.userAcceleration.y), @"uy",
                                       NUMFLOAT(motion.userAcceleration.z), @"uz",
                                       NUMFLOAT(motion.gravity.x + motion.userAcceleration.x), @"x",
                                       NUMFLOAT(motion.gravity.y + motion.userAcceleration.y), @"y",
                                       NUMFLOAT(motion.gravity.z + motion.userAcceleration.z), @"z", nil], @"accelerometer",
                                      [NSDictionary dictionaryWithObjectsAndKeys:
                                       NUMFLOAT(motion.attitude.yaw), @"yaw",
                                       NUMFLOAT(motion.attitude.pitch), @"pitch",
                                       NUMFLOAT(motion.attitude.roll), @"roll", nil], @"gyroscope",
                                      [NSDictionary dictionaryWithObjectsAndKeys:
                                       NUMDOUBLE(motion.magneticField.accuracy), @"accuracy",
                                       NUMDOUBLE(motion.magneticField.field.x), @"x",
                                       NUMDOUBLE(motion.magneticField.field.y), @"y",
                                       NUMDOUBLE(motion.magneticField.field.z), @"z", nil], @"magnetometer",
                                      NUMLONGLONG(motion.timestamp * 1000), @"timestamp",
                                      nil];
        if (computeRotationMatrix)
        {
            CMRotationMatrix rotation = motion.attitude.rotationMatrix;
            Ti3DMatrix *timatrix = [[Ti3DMatrix alloc] init];
            timatrix.m11 = [NSNumber numberWithFloat:rotation.m11];
            timatrix.m12 = [NSNumber numberWithFloat:rotation.m21];
            timatrix.m13 = [NSNumber numberWithFloat:rotation.m31];
            
            timatrix.m21 = [NSNumber numberWithFloat:rotation.m12];
            timatrix.m22 = [NSNumber numberWithFloat:rotation.m22];
            timatrix.m23 = [NSNumber numberWithFloat:rotation.m32];
            
            timatrix.m31 = [NSNumber numberWithFloat:rotation.m13];
            timatrix.m32 = [NSNumber numberWithFloat:rotation.m23];
            timatrix.m33 = [NSNumber numberWithFloat:rotation.m33];
            [event setObject:[timatrix autorelease] forKey:@"rotationMatrix"];
        }
 		[self fireEvent:@"motion" withObject:event];
	}
	if (accelerometerRegistered)
	{
		NSDictionary *event = [NSDictionary dictionaryWithObjectsAndKeys:
                               NUMFLOAT(motion.gravity.x), @"gx",
                               NUMFLOAT(motion.gravity.y), @"gy",
                               NUMFLOAT(motion.gravity.z), @"gz",
                               NUMFLOAT(motion.userAcceleration.x), @"ux",
                               NUMFLOAT(motion.userAcceleration.y), @"uy",
                               NUMFLOAT(motion.userAcceleration.z), @"uz",
                               NUMFLOAT(motion.gravity.x + motion.userAcceleration.x), @"x",
                               NUMFLOAT(motion.gravity.y + motion.userAcceleration.y), @"y",
                               NUMFLOAT(motion.gravity.z + motion.userAcceleration.z), @"z",
                               NUMLONGLONG(motion.timestamp * 1000), @"timestamp",
						   nil];
		[self fireEvent:@"accelerometer" withObject:event];
	}
	if (gyroscopeRegistered)
	{
		NSDictionary *event = [NSDictionary dictionaryWithObjectsAndKeys:
						   NUMFLOAT(motion.attitude.yaw), @"yaw",
						   NUMFLOAT(motion.attitude.pitch), @"pitch",
						   NUMFLOAT(motion.attitude.roll), @"roll",
						   NUMLONGLONG(motion.timestamp * 1000), @"timestamp",
						   nil];
		[self fireEvent:@"gyroscope" withObject:event];
	}
	if (magnetometerRegistered)
	{
        NSDictionary *event = [NSDictionary dictionaryWithObjectsAndKeys:
						   NUMFLOAT(motion.magneticField.field.x), @"x",
						   NUMFLOAT(motion.magneticField.field.y), @"y",
						   NUMFLOAT(motion.magneticField.field.z), @"z",
						   NUMLONGLONG(motion.timestamp * 1000), @"timestamp",
						   nil];
		[self fireEvent:@"magnetometer" withObject:event];
	}
}

MAKE_SYSTEM_PROP(STANDARD_GRAVITY,9.80665);

-(NSNumber*)refrehRate
{
	return NUMINT(refreshRate);
}

-(void)setRefreshRate:(NSNumber *)rate
{
    refreshRate = [rate intValue];
    motionManager.deviceMotionUpdateInterval = 1.0/refreshRate;
    motionManager.accelerometerUpdateInterval = 1.0/refreshRate;
    motionManager.magnetometerUpdateInterval = 1.0/refreshRate;
}

-(NSNumber*)computeRotationMatrix
{
	return NUMBOOL(computeRotationMatrix);
}

-(void)setComputeRotationMatrix:(NSNumber *)value
{
    computeRotationMatrix = [value boolValue];
}

@end

#endif