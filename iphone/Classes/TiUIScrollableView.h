/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UISCROLLABLEVIEW

#import "TiScrollingView.h"

@interface TiUIScrollableView : TiScrollingView<TiScrolling> {
@private
	TDUIScrollView *scrollview;
	UIPageControl *pageControl;
	NSInteger currentPage; // Duplicate some info, just in case we're not showing the page control
	BOOL showPageControl;
	UIColor *pageControlBackgroundColor;
	CGFloat pageControlHeight;
    CGFloat pagingControlAlpha;
	BOOL handlingPageControlEvent;
    BOOL pagingControlOnTop;
    BOOL overlayEnabled;
    
    // Have to correct for an apple goof; rotation stops scrolling, AND doesn't move to the next page.
    BOOL rotatedWhileScrolling;

#ifndef TI_USE_AUTOLAYOUT
    BOOL needsToRefreshScrollView;

    // See the code for why we need this...
    BOOL enforceCacheRecalculation;
    NSInteger cacheSize;
    BOOL verticalLayout;
    
    BOOL pageChanged;
#endif
    
}
@property(nonatomic,readwrite,assign)CGFloat switchPageAnimationDuration;

#pragma mark - MapMe Internal Use Only
-(void)manageRotation;
-(void)refreshScrollView:(CGRect)visibleBounds readd:(BOOL)readd;
-(NSArray*)wrappers;
@end

#endif
