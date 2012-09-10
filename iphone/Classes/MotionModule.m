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
		orientationRegistered = FALSE;
		gyroscopeRegistered = FALSE;
		magnetometerRegistered = FALSE;
        updateInterval = 0.030; // time between 2 data in seconds
        computeRotationMatrix = TRUE;
        
//        usingReference = false;

		motionManager = [[CMMotionManager alloc] init];

        motionManager.deviceMotionUpdateInterval = updateInterval;
        motionManager.gyroUpdateInterval = updateInterval;
        motionManager.accelerometerUpdateInterval = updateInterval;
        motionManager.magnetometerUpdateInterval = updateInterval;
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
        else if ([type isEqualToString:@"orientation"])
		{
			needsStart = TRUE;
			orientationRegistered = TRUE;
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
//                if (([CMMotionManager availableAttitudeReferenceFrames] & CMAttitudeReferenceFrameXTrueNorthZVertical) != 0)
//                {
//                    [motionManager startDeviceMotionUpdatesUsingReferenceFrame:CMAttitudeReferenceFrameXTrueNorthZVertical toQueue:[NSOperationQueue currentQueue]
//                        withHandler:motionHandler];
//                }
//                else
//                {
                    [motionManager startDeviceMotionUpdatesToQueue:[NSOperationQueue currentQueue]
                                                                   withHandler:motionHandler];
//                }
                
            }, NO);
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
        else if ([type isEqualToString:@"orientation"])
		{
			orientationRegistered = FALSE;
		}
		else if ([type isEqualToString:@"magnetometer"])
		{
			magnetometerRegistered = FALSE;
		}
		if (!motionRegistered && !accelerometerRegistered && !orientationRegistered && !gyroscopeRegistered && !magnetometerRegistered)
			[motionManager stopDeviceMotionUpdates];
	}
}

//- (void)markZeroReference
//{
//    [referenceAttitude release];
//    CMDeviceMotion* deviceMotion = motionManager.deviceMotion;
//    referenceAttitude = [deviceMotion.attitude retain];
//}

-(void) processMotionData: (CMDeviceMotion *) motion withError:(NSError *) error
{
    CMAttitude* currentAttitude = motion.attitude;
//    if (!usingReference)
//    {
////        [lastAttitude release];
////        lastAttitude = [currentAttitude retain];
//    }
//    else if (referenceAttitude)
//    {
//        [currentAttitude multiplyByInverseOfAttitude: referenceAttitude];
//        NSLog(@"obtaining: %f, %f, %f",currentAttitude.yaw,currentAttitude.pitch,currentAttitude.roll);
//    }
    if (motionRegistered)
	{
//        NSLog(@"yaw: %f, %f",currentAttitude.yaw,currentAttitude.yaw*currentAttitude.rotationMatrix.m11);
        //        NSLog(@"obtaining: %f, %f, %f",currentAttitude.yaw,currentAttitude.pitch,currentAttitude.roll);
        NSMutableDictionary *event = [NSMutableDictionary dictionaryWithObjectsAndKeys:
                                      [NSDictionary dictionaryWithObjectsAndKeys:
                                       [NSDictionary dictionaryWithObjectsAndKeys:
                                       NUMFLOAT(motion.gravity.x), @"x",
                                       NUMFLOAT(motion.gravity.y), @"y",
                                       NUMFLOAT(motion.gravity.z), @"z", nil], @"gravity",
                                       [NSDictionary dictionaryWithObjectsAndKeys:
                                       NUMFLOAT(motion.userAcceleration.x), @"x",
                                       NUMFLOAT(motion.userAcceleration.y), @"y",
                                       NUMFLOAT(motion.userAcceleration.z), @"z", nil], @"user",
                                       NUMFLOAT(motion.gravity.x + motion.userAcceleration.x), @"x",
                                       NUMFLOAT(motion.gravity.y + motion.userAcceleration.y), @"y",
                                       NUMFLOAT(motion.gravity.z + motion.userAcceleration.z), @"z", nil], @"accelerometer",
                                      [NSDictionary dictionaryWithObjectsAndKeys:
                                       NUMFLOAT(currentAttitude.yaw), @"yaw",
                                       NUMFLOAT(currentAttitude.pitch), @"pitch",
                                       NUMFLOAT(currentAttitude.roll), @"roll", nil], @"orientation",
                                      [NSDictionary dictionaryWithObjectsAndKeys:
                                       NUMDOUBLE(motion.magneticField.accuracy), @"accuracy",
                                       NUMDOUBLE(motion.magneticField.field.x), @"x",
                                       NUMDOUBLE(motion.magneticField.field.y), @"y",
                                       NUMDOUBLE(motion.magneticField.field.z), @"z", nil], @"magnetometer",
                                      [NSDictionary dictionaryWithObjectsAndKeys:
                                       NUMDOUBLE(motion.rotationRate.x), @"x",
                                       NUMDOUBLE(motion.rotationRate.y), @"y",
                                       NUMDOUBLE(motion.rotationRate.z), @"z", nil], @"gyroscope",
                                      NUMLONGLONG(motion.timestamp * 1000000), @"timestamp",
                                      nil];
        if (computeRotationMatrix)
        {
            CMRotationMatrix rotation = currentAttitude.rotationMatrix;
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
    else{
        if (accelerometerRegistered)
        {
            NSDictionary *event = [NSDictionary dictionaryWithObjectsAndKeys:
                                   [NSDictionary dictionaryWithObjectsAndKeys:
                                       NUMFLOAT(motion.gravity.x), @"x",
                                       NUMFLOAT(motion.gravity.y), @"y",
                                       NUMFLOAT(motion.gravity.z), @"z", nil], @"gravity",
                                       [NSDictionary dictionaryWithObjectsAndKeys:
                                       NUMFLOAT(motion.userAcceleration.x), @"x",
                                       NUMFLOAT(motion.userAcceleration.y), @"y",
                                       NUMFLOAT(motion.userAcceleration.z), @"z", nil], @"user",
                                       NUMFLOAT(motion.gravity.x + motion.userAcceleration.x), @"x",
                                       NUMFLOAT(motion.gravity.y + motion.userAcceleration.y), @"y",
                                       NUMFLOAT(motion.gravity.z + motion.userAcceleration.z), @"z",
                                   NUMLONGLONG(motion.timestamp * 1000), @"timestamp",
                               nil];
            [self fireEvent:@"accelerometer" withObject:event];
        }
        if (orientationRegistered)
        {
            NSDictionary *event = [NSDictionary dictionaryWithObjectsAndKeys:
                                   NUMFLOAT(currentAttitude.yaw), @"yaw",
                                   NUMFLOAT(currentAttitude.pitch), @"pitch",
                                   NUMFLOAT(currentAttitude.roll), @"roll",
                                   NUMLONGLONG(motion.timestamp * 1000), @"timestamp",
                               nil];
            [self fireEvent:@"orientation" withObject:event];
        }
        if (gyroscopeRegistered)
        {
            NSDictionary *event = [NSDictionary dictionaryWithObjectsAndKeys:
                                   NUMFLOAT(motion.rotationRate.x), @"x",
                                   NUMFLOAT(motion.rotationRate.y), @"y",
                                   NUMFLOAT(motion.rotationRate.z), @"z",
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
                                   NUMFLOAT(motion.magneticField.accuracy), @"accuracy",
                                   NUMLONGLONG(motion.timestamp * 1000), @"timestamp",
                                   nil];
            [self fireEvent:@"magnetometer" withObject:event];
        }
    }
}

MAKE_SYSTEM_PROP_DBL(ACCURACY_HIGH,CMMagneticFieldCalibrationAccuracyHigh);
MAKE_SYSTEM_PROP_DBL(ACCURACY_MEDIUM,CMMagneticFieldCalibrationAccuracyMedium);
MAKE_SYSTEM_PROP_DBL(ACCURACY_LOW,CMMagneticFieldCalibrationAccuracyLow);
MAKE_SYSTEM_PROP_DBL(ACCURACY_UNCALIBRATED,CMMagneticFieldCalibrationAccuracyUncalibrated);
MAKE_SYSTEM_PROP(STANDARD_GRAVITY,9.80665);

-(NSNumber*)updateInterval
{
	return NUMINT(updateInterval*1000);
}

-(void)setUpdateInterval:(NSNumber *)interval //in ms
{
    updateInterval = [interval intValue] / 1000.0; // in seconds
    motionManager.deviceMotionUpdateInterval = updateInterval;
    motionManager.accelerometerUpdateInterval = updateInterval;
    motionManager.gyroUpdateInterval = updateInterval;
    motionManager.magnetometerUpdateInterval = updateInterval;
}

//-(NSNumber*)useReference
//{
//	return NUMBOOL(usingReference);
//}
//
//
////the previous event will be used as a reference
//-(void)setUseReference:(NSNumber *)value
//{
//    NSLog(@"setUseReference: %d", [value boolValue]);
//    [self markZeroReference];
//    usingReference = [value boolValue];
//}

-(NSNumber*)computeRotationMatrix
{
	return NUMBOOL(computeRotationMatrix);
}


-(void)setComputeRotationMatrix:(NSNumber *)value
{
    computeRotationMatrix = [value boolValue];
}

-(NSNumber*)hasAccelerometer
{
	return NUMBOOL(motionManager.accelerometerAvailable);
}

-(NSNumber*)hasGyroscope
{
	return NUMBOOL(motionManager.gyroAvailable);
}

-(NSNumber*)hasMagnetometer
{
	return NUMBOOL(motionManager.magnetometerAvailable);
}

-(NSNumber*)hasOrientation
{
	return NUMBOOL(motionManager.deviceMotionAvailable);
}

@end

#endif