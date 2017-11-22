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
{
  UIView* safeAreaView;
  BOOL shouldExtendSafeArea;
}
- (id)init
{
  self = [super init];
  if (self != nil) {
    shouldExtendSafeArea = YES;
  }
  return self;
}

- (void)dealloc
{
#if IS_XCODE_9
  safeAreaView = nil;
#endif
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

- (UIView *)parentViewForChildren
{
  if (safeAreaView) {
    return safeAreaView;
  }
  return self;
}

- (void)setShouldExtendSafeArea:(id)value
{
  shouldExtendSafeArea = [TiUtils boolValue:value];
  if (shouldExtendSafeArea && !safeAreaView) {
    safeAreaView = [[UIView alloc] initWithFrame:[self frame]];
    [safeAreaView setAutoresizingMask:UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight];
    [self addSubview:safeAreaView];
    [self processForSafeArea];
  }
}

- (void)processForSafeArea
{
#if IS_XCODE_9
  // TO DO : Refactor this method
  if (!safeAreaView || shouldExtendSafeArea) {
    return;
  }
  float left = 0.0;
  float right = 0.0;
  float top = 0.0;
  float bottom = 0.0;
  UIViewController<TiControllerContainment> *topContainerController = [[[TiApp app] controller] topContainerController];
  UIEdgeInsets safeAreaInset = [[topContainerController hostingView] safeAreaInsets];
  UIEdgeInsets actualInset = UIEdgeInsetsZero;
  TiWindowProxy *windowProxy = (TiWindowProxy*)self.proxy;
  if (windowProxy.tabGroup) {
    if ([windowProxy.tabGroup isKindOfClass:[TiWindowProxy class]]) {
      windowProxy = (TiWindowProxy *)windowProxy.tabGroup;
    }
    UIInterfaceOrientation orientation = [[UIApplication sharedApplication] statusBarOrientation];
    if (!UIInterfaceOrientationIsPortrait(orientation)) {
      if (windowProxy.isMasterWindow) {
        actualInset.left = safeAreaInset.left;
      } else if (windowProxy.isDetailWindow) {
        actualInset.right = safeAreaInset.right;
      } else {
        actualInset.left = safeAreaInset.left;
        actualInset.right = safeAreaInset.right;
      }
    }
  } else if (windowProxy.tab) {
    if ([windowProxy.tab isKindOfClass:[TiWindowProxy class]]) {
      windowProxy = (TiWindowProxy *)windowProxy.tab;
    }
    UIInterfaceOrientation orientation = [[UIApplication sharedApplication] statusBarOrientation];
    if (!UIInterfaceOrientationIsPortrait(orientation)) {
      if (windowProxy.isMasterWindow) {
        actualInset.left = safeAreaInset.left;
      } else if (windowProxy.isDetailWindow) {
        actualInset.right = safeAreaInset.right;
      } else {
        actualInset.left = safeAreaInset.left;
        actualInset.right = safeAreaInset.right;
      }
    }
    actualInset.bottom = safeAreaInset.bottom;
  } else {
    if (windowProxy.isMasterWindow) {
      actualInset.left = safeAreaInset.left;
    } else if (windowProxy.isDetailWindow) {
      actualInset.right = safeAreaInset.right;
    } else {
      actualInset.left = safeAreaInset.left;
      actualInset.right = safeAreaInset.right;
    }
    actualInset.bottom = safeAreaInset.bottom;
    actualInset.top = safeAreaInset.top;
  }
  safeAreaView.frame = UIEdgeInsetsInsetRect(self.bounds, actualInset);
#endif
}
@end
