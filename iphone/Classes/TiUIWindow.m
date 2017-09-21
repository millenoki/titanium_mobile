/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#import "TiUIWindow.h"
#import "TiApp.h"
#import "TiUIWindowProxy.h"

@implementation TiUIWindow

- (void)dealloc
{
  [super dealloc];
}

#ifdef TI_USE_AUTOLAYOUT
- (void)initializeTiLayoutView
{
  [super initializeTiLayoutView];
  [self setDefaultHeight:TiDimensionAutoFill];
  [self setDefaultWidth:TiDimensionAutoFill];
}
#endif

- (void)setStatusBarStyle_:(id)value withObject:(id)props
{
  NSInteger theStyle = [TiUtils intValue:value def:[[[TiApp app] controller] defaultStatusBarStyle]];
  BOOL animated = [TiUtils boolValue:@"animated" properties:props def:(props != nil)];
  UIStatusBarAnimation animationStyle = UIStatusBarAnimationNone;
  if (animated) {
    animationStyle = [TiUtils intValue:@"animationStyle" properties:props def:animationStyle];
  }
  ((TiWindowProxy *)[self viewProxy]).internalStatusBarStyle = theStyle;
  if ([[self viewProxy] viewInitialized] && [[self viewProxy] focussed]) {
    TiThreadPerformBlockOnMainThread(^{
      [(TiRootViewController *)[[TiApp app] controller] updateStatusBar:animated withStyle:animationStyle];
    },
        YES);
  }
}

- (void)setFullscreen_:(id)value withObject:(id)props
{
  BOOL newValue = [TiUtils boolValue:value def:[[[TiApp app] controller] statusBarInitiallyHidden]];
  BOOL animated = [TiUtils boolValue:@"animated" properties:props def:(props != nil)];
  UIStatusBarAnimation animationStyle = UIStatusBarAnimationNone;
  if (animated) {
    animationStyle = [TiUtils intValue:@"animationStyle" properties:props def:animationStyle];
  }
  ((TiWindowProxy *)[self viewProxy]).hidesStatusBar = newValue;
  if ([[self viewProxy] viewInitialized] && [[self viewProxy] focussed]) {
    TiThreadPerformBlockOnMainThread(^{
      [(TiRootViewController *)[[TiApp app] controller] updateStatusBar:animated withStyle:animationStyle];
    },
        YES);
  }
}

@end
