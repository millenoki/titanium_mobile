/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UISCROLLVIEW

#import "TiUIScrollView.h"
#import "TiUIScrollViewProxy.h"
#import "TiUtils.h"

@interface TiUIView ()
- (void)setClipChildren_:(id)arg;
@end

@implementation TiUIScrollView {
  BOOL _flexibleContentWidth;
  BOOL _flexibleContentHeight;
}
//@synthesize contentWidth;

- (void)dealloc
{
  RELEASE_WITH_DELEGATE(scrollView);
#ifndef TI_USE_AUTOLAYOUT
  RELEASE_TO_NIL(wrapperView);
#endif
#if IS_XCODE_8
#ifdef USE_TI_UIREFRESHCONTROL
  RELEASE_TO_NIL(refreshControl);
#endif
#endif
  [super dealloc];
}

- (id)init
{
  self = [super init];
  if (self != nil) {
    needsHandleContentSize = YES;
    _flexibleContentWidth = NO;
    _flexibleContentHeight = NO;
  }
  return self;
}

#ifndef TI_USE_AUTOLAYOUT
- (UIView *)wrapperView
{
  if (wrapperView == nil) {
    CGRect wrapperFrame;
    wrapperFrame.size = [[self scrollView] contentSize];
    wrapperFrame.origin = CGPointZero;
    wrapperView = [[UIView alloc] initWithFrame:wrapperFrame];
    [wrapperView setUserInteractionEnabled:YES];
    [scrollView addSubview:wrapperView];
  }
  return wrapperView;
}
#endif

- (TDUIScrollView *)scrollView
{
  if (scrollView == nil) {
#ifdef TI_USE_AUTOLAYOUT
    scrollView = [[TDUIScrollView alloc] init];
    contentView = [[TiLayoutView alloc] init];
    [contentView setTranslatesAutoresizingMaskIntoConstraints:NO];
    [scrollView setTranslatesAutoresizingMaskIntoConstraints:NO];
    [contentView setViewName:@"TiScrollView.ContentView"];

    [contentView setDefaultHeight:TiDimensionAutoSize];
    [contentView setDefaultWidth:TiDimensionAutoSize];

    [scrollView addSubview:contentView];
#else
    scrollView = [[TDUIScrollView alloc] initWithFrame:[self bounds]];
    [scrollView setAutoresizingMask:UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight];
#endif
    [scrollView setBackgroundColor:[UIColor clearColor]];
    [scrollView setShowsHorizontalScrollIndicator:NO];
    [scrollView setShowsVerticalScrollIndicator:NO];
    [scrollView setDelegate:self];
    [scrollView setTouchDelegate:self];
    [self addSubview:scrollView];
  }
  return scrollView;
}

- (UIView *)viewForHitTest
{
  return wrapperView;
}

- (UIView *)parentViewForChildren
{
  return [self wrapperView];
}

- (void)setClipChildren_:(id)arg
{
  [super setClipChildren_:arg];
  [self scrollView].clipsToBounds = self.clipsToBounds;
}

- (id)accessibilityElement
{
  return [self scrollView];
}

- (BOOL)flexibleContentWidth
{
  return _flexibleContentWidth;
}

- (BOOL)flexibleContentHeight
{
  return _flexibleContentHeight;
}

- (void)setNeedsHandleContentSizeIfAutosizing
{
#ifndef TI_USE_AUTOLAYOUT
  if ([self flexibleContentWidth] || [self flexibleContentHeight]) {
    [self setNeedsHandleContentSize];
  }
#endif
}

- (void)setNeedsHandleContentSize
{
#ifndef TI_USE_AUTOLAYOUT
  if (!needsHandleContentSize) {
    needsHandleContentSize = YES;
    if (configurationSet) {
      [self handleContentSize];
    }
  }
#endif
}

- (BOOL)handleContentSizeIfNeeded
{
#ifndef TI_USE_AUTOLAYOUT
  if (needsHandleContentSize) {
    [self handleContentSize];
    return YES;
  }
#endif
  return NO;
}

- (void)handleContentSize
{
  ENSURE_UI_THREAD_0_ARGS
#ifndef TI_USE_AUTOLAYOUT
  if (!needsHandleContentSize) {
    return;
  }
  CGSize newContentSize = [self bounds].size;
  CGFloat scale = [scrollView zoomScale];

  CGSize autoSize = CGSizeZero;

  if ([self flexibleContentWidth] || [self flexibleContentHeight]) {
    autoSize = [(TiViewProxy *)[self proxy] autoSizeForSize:newContentSize ignoreMinMax:YES];
  }

  switch (contentWidth.type) {
  case TiDimensionTypeDip:
  case TiDimensionTypePercent: {
    newContentSize.width = TiDimensionCalculateValue(contentWidth, newContentSize.width);
    break;
  }
  case TiDimensionTypeAutoSize:
  case TiDimensionTypeAuto: // TODO: This may break the layout spec for content "auto"
  {
    newContentSize.width = MAX(newContentSize.width, autoSize.width);
    break;
  }
  case TiDimensionTypeUndefined:
  case TiDimensionTypeAutoFill: // Assume that "fill" means "fill scrollview bounds"; not in spec
  default: {
    break;
  }
  }

  switch (contentHeight.type) {
  case TiDimensionTypeDip:
  case TiDimensionTypePercent: {
    minimumContentHeight = TiDimensionCalculateValue(contentHeight, newContentSize.height);
    break;
  }
  case TiDimensionTypeAutoSize:
  case TiDimensionTypeAuto: // TODO: This may break the layout spec for content "auto"
  {
    minimumContentHeight = MAX(newContentSize.height, autoSize.height);
    break;
  }
  case TiDimensionTypeUndefined:
  case TiDimensionTypeAutoFill: // Assume that "fill" means "fill scrollview bounds"; not in spec
  default:
    minimumContentHeight = newContentSize.height;
    break;
  }
  newContentSize.width *= scale;
  newContentSize.height = scale * minimumContentHeight;
  CGSize oldContentSize = scrollView.contentSize;
  if (oldContentSize.width != newContentSize.width || oldContentSize.height != newContentSize.height) {
    CGRect wrapperBounds;
    wrapperBounds.origin = CGPointZero;
    wrapperBounds.size = newContentSize;
    [wrapperView setFrame:wrapperBounds];
    [scrollView setContentSize:newContentSize];
    [self scrollViewDidZoom:scrollView];
  }

  [(TiUIScrollViewProxy *)[self proxy] layoutChildrenAfterContentSize:NO];
  needsHandleContentSize = NO;
#endif
}

- (void)frameSizeChanged:(CGRect)frame bounds:(CGRect)bounds
{
  if ([self flexibleContentWidth] || [self flexibleContentHeight]) {
    needsHandleContentSize = YES;
  } else {
    [TiUtils setView:[self wrapperView] positionRect:bounds];
  }
  [super frameSizeChanged:frame bounds:bounds];
}

#ifndef TI_USE_AUTOLAYOUT
- (void)setContentWidth_:(id)value
{
  contentWidth = [TiUtils dimensionValue:value];
  _flexibleContentWidth = TiDimensionIsAuto(contentWidth) || TiDimensionIsAutoSize(contentWidth);
  [self setNeedsHandleContentSize];
}

- (void)setContentHeight_:(id)value
{
  contentHeight = [TiUtils dimensionValue:value];
  _flexibleContentHeight = TiDimensionIsAuto(contentHeight) || TiDimensionIsAutoSize(contentHeight);
  [self setNeedsHandleContentSize];
}
#endif

- (void)setRefreshControl_:(id)args
{
#if IS_XCODE_8
#ifdef USE_TI_UIREFRESHCONTROL
  if (![TiUtils isIOS10OrGreater]) {
    NSLog(@"[WARN] Ti.UI.RefreshControl inside Ti.UI.ScrollView is only available in iOS 10 and later.");
    return;
  }
  ENSURE_SINGLE_ARG_OR_NIL(args, TiUIRefreshControlProxy);
  [[refreshControl control] removeFromSuperview];
  RELEASE_TO_NIL(refreshControl);
  [[self proxy] replaceValue:args forKey:@"refreshControl" notification:NO];
  if (args != nil) {
    refreshControl = [args retain];
    [[self scrollView] setRefreshControl:[refreshControl control]];
  }
#endif
#else
  NSLog(@"[WARN] Ti.UI.RefreshControl inside Ti.UI.ScrollView is only available in iOS 10 and later.");
#endif
}

-(void)updateWrapperViewFrame {
  // Center the image as it becomes smaller than the size of the screen
  CGSize boundsSize = self.bounds.size;
  CGRect frameToCenter = wrapperView.frame;
  
  // Horizontally
  if (frameToCenter.size.width < boundsSize.width) {
    frameToCenter.origin.x = floorf((boundsSize.width - frameToCenter.size.width) / 2.0);
  } else {
    frameToCenter.origin.x = 0;
  }
  
  // Vertically
  if (frameToCenter.size.height < boundsSize.height) {
    frameToCenter.origin.y = floorf((boundsSize.height - frameToCenter.size.height) / 2.0);
  } else {
    frameToCenter.origin.y = 0;
  }
  
  // Center
  if (!CGRectEqualToRect(wrapperView.frame, frameToCenter)) {
    wrapperView.frame = frameToCenter;
  }
}

- (void)layoutSubviews
{
  [super layoutSubviews];
  [self updateWrapperViewFrame];
}

- (void)zoomToPoint:(CGPoint)touchPoint withScale:(CGFloat)scale animated:(BOOL)animated
{
  touchPoint.x -= wrapperView.frame.origin.x;
  touchPoint.y -= wrapperView.frame.origin.y;
  [super zoomToPoint:touchPoint withScale:scale animated:animated];
}
#pragma mark scrollView delegate stuff

- (UIView *)viewForZoomingInScrollView:(UIScrollView *)scrollView
{
  return [self wrapperView];
}

- (void)scrollViewDidZoom:(UIScrollView *)scrollView_
{
  [self setNeedsLayout];
  [self layoutIfNeeded];
  [super scrollViewDidZoom:scrollView_];
}

- (void)setKeyboardDismissMode_:(id)value
{
  ENSURE_TYPE(value, NSNumber);
  [[self scrollView] setKeyboardDismissMode:[TiUtils intValue:value def:UIScrollViewKeyboardDismissModeNone]];
  [[self proxy] replaceValue:value forKey:@"keyboardDismissMode" notification:NO];
}

- (void)scrollViewDidEndZooming:(UIScrollView *)scrollView_ withView:(UIView *)view atScale:(CGFloat)scale
{
  [super scrollViewDidEndZooming:scrollView_ withView:view atScale:scale];
}

- (id)zoomScale_
{
  return @(scrollView.zoomScale);
}

- (void)scrollViewDidScroll:(UIScrollView *)theScrollView
{
  [super scrollViewDidScroll:theScrollView];
}

- (void)scrollViewWillBeginDragging:(UIScrollView *)theScrollView
{
  [super scrollViewWillBeginDragging:theScrollView];
}

- (void)scrollViewDidEndDragging:(UIScrollView *)theScrollView willDecelerate:(BOOL)decelerate
{
  [super scrollViewDidEndDragging:theScrollView willDecelerate:decelerate];
}

- (void)scrollViewDidEndDecelerating:(UIScrollView *)theScrollView
{
  [super scrollViewDidEndDecelerating:theScrollView];
}

- (BOOL)scrollViewShouldScrollToTop:(UIScrollView *)theScrollView
{
  return [super scrollViewShouldScrollToTop:theScrollView];
}

- (void)scrollViewDidScrollToTop:(UIScrollView *)theScrollView
{
  return [super scrollViewDidScrollToTop:theScrollView];
}

#pragma mark Keyboard delegate stuff

- (void)keyboardDidShowAtHeight:(CGFloat)keyboardTop
{
  InsetScrollViewForKeyboard(scrollView, keyboardTop, minimumContentHeight);
}

- (void)scrollToShowView:(UIView *)firstResponderView withKeyboardHeight:(CGFloat)keyboardTop
{
#ifndef TI_USE_AUTOLAYOUT
  if ([scrollView isScrollEnabled]) {
    CGRect responderRect = [wrapperView convertRect:[firstResponderView bounds] fromView:firstResponderView];
    OffsetScrollViewForRect(scrollView, keyboardTop, minimumContentHeight, responderRect);
  }
#endif
}

@end

#endif
