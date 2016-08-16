/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#import <UIKit/UIKit.h>
#import "TiControllerProtocols.h"

@interface TiRootViewController : UIViewController<TiRootControllerProtocol, TiControllerContainment, TiOrientationController> {
    //Default background properties
    UIColor* bgColor;
    UIImage* bgImage;
    UIView* hostView;
    NSInteger curTransformAngle;
    BOOL forceLayout;
    UIImageView* defaultImageView;

#ifdef LAUNCHSCREEN_STORYBOARD
    UIView *storyboardView;
#endif

    //Orientation Stuff
    UIInterfaceOrientation orientationHistory[4];
    BOOL forcingStatusBarOrientation;
    BOOL isCurrentlyVisible;
    TiOrientationFlags defaultOrientations;
    NSMutableArray* containedWindows;
    NSMutableArray* modalWindows;
    BOOL forcingRotation;
//    BOOL forcedOrientation;
    BOOL statusBarInitiallyHidden;
    BOOL viewControllerControlsStatusBar;
    UIStatusBarStyle defaultStatusBarStyle;
    BOOL useDefaultImageView;
    
    UIInterfaceOrientation deviceOrientation;
    
    BOOL statusBarIsHidden;
    BOOL statusBarVisibilityChanged;
    NSInteger activeAlertControllerCount;
}

//Titanium Support
-(CGRect)resizeView;
-(void)repositionSubviews;
-(TiViewProxy *)topWindow;
-(UIView *)topWindowProxyView;
-(NSUInteger)supportedOrientationsForAppDelegate;
-(void)incrementActiveAlertControllerCount;
-(void)decrementActiveAlertControllerCount;
-(UIViewController*)topPresentedController;
-(UIInterfaceOrientation) lastValidOrientation:(TiOrientationFlags)orientationFlags;
-(void)updateStatusBar:(BOOL)animated;
-(void) updateStatusBar:(BOOL)animated withStyle:(UIStatusBarAnimation)style;
@property (nonatomic, readonly) BOOL statusBarInitiallyHidden;
@property (nonatomic, readonly) UIStatusBarStyle defaultStatusBarStyle;
@property (nonatomic, readonly) BOOL statusBarVisibilityChanged;
@property (nonatomic, readonly) UIView* keyboardActiveInput;
@property (nonatomic, readonly) CGFloat keyboardHeight;
-(CGRect)getAbsRect:(CGRect)rect fromView:(UIView*)view;
-(CGRect)getKeyboardFrameInView:(UIView*)view;
-(UIView *)viewForKeyboardAccessory;

@property(nonatomic,readonly) TiViewProxy<TiKeyboardFocusableView> * keyboardFocusedProxy;
#if defined(DEBUG) || defined(DEVELOPER)
-(void)shutdownUi:(id)arg;
#endif

+ (UIImage*)splashImageForOrientation:(UIDeviceOrientation)orientation;

-(void) handleNewNewKeyboardStatus;

@end
