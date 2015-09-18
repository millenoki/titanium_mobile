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

@implementation TiUIScrollViewImpl

-(void)setTouchHandler:(TiUIView*)handler
{
    //Assign only. No retain
    touchHandler = handler;
}

- (BOOL)touchesShouldBegin:(NSSet *)touches withEvent:(UIEvent *)event inContentView:(UIView *)view
{
    //If the content view is of type TiUIView touch events will automatically propagate
    //If it is not of type TiUIView we will fire touch events with ourself as source
    if ([view isKindOfClass:[TiUIView class]] || [view respondsToSelector:@selector(touchDelegate)]) {
        touchedContentView= view;
    }
    else {
        touchedContentView = nil;
    }
    return [super touchesShouldBegin:touches withEvent:event inContentView:view];
}

- (void)touchesBegan:(NSSet *)touches withEvent:(UIEvent *)event 
{
    //When userInteractionEnabled is false we do nothing since touch events are automatically
    //propagated. If it is dragging,tracking or zooming do not do anything.
    if (!self.dragging && !self.zooming && !self.tracking 
        && self.userInteractionEnabled && (touchedContentView == nil) ) {
        [touchHandler processTouchesBegan:touches withEvent:event];
 	}		
	[super touchesBegan:touches withEvent:event];
}
- (void)touchesMoved:(NSSet *)touches withEvent:(UIEvent *)event 
{
    if (!self.dragging && !self.zooming && !self.tracking 
        && self.userInteractionEnabled && (touchedContentView == nil) ) {
        [touchHandler processTouchesMoved:touches withEvent:event];
    }		
	[super touchesMoved:touches withEvent:event];
}

- (void)touchesEnded:(NSSet *)touches withEvent:(UIEvent *)event 
{
    if (!self.dragging && !self.zooming && !self.tracking 
        && self.userInteractionEnabled && (touchedContentView == nil)) {
        [touchHandler processTouchesEnded:touches withEvent:event];
    }		
	[super touchesEnded:touches withEvent:event];
}

- (void)touchesCancelled:(NSSet *)touches withEvent:(UIEvent *)event 
{
    if (!self.dragging && !self.zooming && !self.tracking 
        && self.userInteractionEnabled && (touchedContentView == nil) ) {
        [touchHandler processTouchesCancelled:touches withEvent:event];
    }		
	[super touchesCancelled:touches withEvent:event];
}

//-(void)setZoomScale:(CGFloat)zoomScale
//{
//    [super setZoomScale:zoomScale];
//    if (self.zoomScale == self.minimumZoomScale) {
//        self.scrollEnabled = NO;
//    }else {
//        self.scrollEnabled = YES;
//    }
//    NSLog(@"zoom scale %f", self.zoomScale)
//}
//-(void)setZoomScale:(CGFloat)zoomScale animated:(BOOL)animated
//{
//    [super setZoomScale:zoomScale animated:animated];
//    
//    NSLog(@"zoom scale animated %f", self.zoomScale)
//}
//- (void)setContentSize:(CGSize)contentSize
//{
////    contentSize = CGSizeMake(ceilf(contentSize.width), ceilf(contentSize.height));
//    [super setContentSize:contentSize];
//    NSLog(@"setContentSize animated %@", NSStringFromCGSize(contentSize))
//}
//-(void)setContentOffset:(CGPoint)contentOffset animated:(BOOL)animated
//{
////    contentOffset = CGPointMake(ceilf(contentOffset.x), ceilf(contentOffset.y));
//    [super setContentOffset:contentOffset animated:animated];
//    NSLog(@"setContentOffset animated %@", NSStringFromCGPoint(contentOffset))
//}
//- (void)setContentOffset:(CGPoint)contentOffset
//{
//////    if (_centerContent) {
//////        const CGSize contentSize = self.contentSize;
//////        const CGSize scrollViewSize = self.bounds.size;
//////        
//////        if (contentSize.width < scrollViewSize.width)
//////        {
//////            contentOffset.x = -(scrollViewSize.width - contentSize.width) / 2.0;
//////        }
//////        
//////        if (contentSize.height < scrollViewSize.height)
//////        {
//////            contentOffset.y = -(scrollViewSize.height - contentSize.height) / 2.0;
//////        }
//////    }
////    contentOffset = CGPointMake(ceilf(contentOffset.x), ceilf(contentOffset.y));
//    NSLog(@"setContentOffset %@, %f, %f", NSStringFromCGPoint(contentOffset), self.zoomScale, self.minimumZoomScale)
//    [super setContentOffset:contentOffset];
//}


- (void)layoutSubviews {
    [super layoutSubviews];
    self.contentSize = CGSizeMake(floorf(self.contentSize.width), floorf(self.contentSize.height));
}
@end

@implementation TiUIScrollView
{
    BOOL _flexibleContentWidth;
    BOOL _flexibleContentHeight;
}
//@synthesize contentWidth;

- (void) dealloc
{
	RELEASE_WITH_DELEGATE(scrollview);
	RELEASE_TO_NIL(wrapperView);
	[super dealloc];
}

- (id) init
{
    self = [super init];
    if (self != nil)
    {
        _flexibleContentWidth = NO;
        _flexibleContentHeight = NO;
    }
    return self;
}


-(UIView *)wrapperView
{
	if (wrapperView == nil)
	{
		CGRect wrapperFrame;
		wrapperFrame.size = [[self scrollview] contentSize];
		wrapperFrame.origin = CGPointZero;
		wrapperView = [[UIView alloc] initWithFrame:wrapperFrame];
		[wrapperView setUserInteractionEnabled:YES];
		[scrollview addSubview:wrapperView];
	}
	return wrapperView;
}

-(TiUIScrollViewImpl *)scrollview
{
	if(scrollview == nil)
	{
		scrollview = [[TiUIScrollViewImpl alloc] initWithFrame:[self bounds]];
		[scrollview setAutoresizingMask:UIViewAutoresizingFlexibleWidth|UIViewAutoresizingFlexibleHeight];
		[scrollview setBackgroundColor:[UIColor clearColor]];
		[scrollview setShowsHorizontalScrollIndicator:NO];
		[scrollview setShowsVerticalScrollIndicator:NO];
		[scrollview setDelegate:self];
        [scrollview setTouchHandler:self];
		[self addSubview:scrollview];
	}
	return scrollview;
}

-(UIView*)viewForHitTest
{
    return wrapperView;
}

-(UIView*)parentViewForChildren
{
    return [self wrapperView];
}

-(void)setClipChildren_:(id)arg
{
    [super setClipChildren_:arg];
    [self scrollview].clipsToBounds = self.clipsToBounds;
}

- (id)accessibilityElement
{
	return [self scrollview];
}

-(BOOL)flexibleContentWidth
{
    return _flexibleContentWidth;
}

-(BOOL)flexibleContentHeight
{
    return _flexibleContentHeight;
}

-(void)setNeedsHandleContentSizeIfAutosizing
{
	if ([self flexibleContentWidth] || [self flexibleContentHeight])
	{
		[self setNeedsHandleContentSize];
	}
}

-(void)setNeedsHandleContentSize
{
	if (!needsHandleContentSize)
	{
		needsHandleContentSize = YES;
		TiThreadPerformOnMainThread(^{[self handleContentSize];}, NO);
	}
}


-(BOOL)handleContentSizeIfNeeded
{
	if (needsHandleContentSize)
	{
		[self handleContentSize];
		return YES;
	}
	return NO;
}

-(void)handleContentSize
{
    if (!needsHandleContentSize) {
        return;
    }
    CGSize newContentSize = [self bounds].size;
    CGFloat scale = [scrollview zoomScale];
    
    CGSize autoSize;
    
    if ([self flexibleContentWidth] || [self flexibleContentHeight])
    {
        autoSize = [(TiViewProxy *)[self proxy] autoSizeForSize:newContentSize ignoreMinMax:YES];
    }
    
    switch (contentWidth.type)
    {
        case TiDimensionTypeDip:
        case TiDimensionTypePercent:
        {
            newContentSize.width = TiDimensionCalculateValue(contentWidth, newContentSize.width);
            break;
        }
        case TiDimensionTypeAutoSize:
        case TiDimensionTypeAuto: // TODO: This may break the layout spec for content "auto"
        {
            newContentSize.width = MAX(newContentSize.width,autoSize.width);
            break;
        }
        case TiDimensionTypeUndefined:
        case TiDimensionTypeAutoFill: // Assume that "fill" means "fill scrollview bounds"; not in spec
        default: {
            break;
        }
    }
    
    switch (contentHeight.type)
    {
        case TiDimensionTypeDip:
        case TiDimensionTypePercent:
        {
            minimumContentHeight = TiDimensionCalculateValue(contentHeight, newContentSize.height);
            break;
        }
        case TiDimensionTypeAutoSize:
        case TiDimensionTypeAuto: // TODO: This may break the layout spec for content "auto"
        {
            minimumContentHeight = MAX(newContentSize.height,autoSize.height);
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
    
    [scrollview setContentSize:newContentSize];
    CGRect wrapperBounds;
    wrapperBounds.origin = CGPointZero;
    wrapperBounds.size = newContentSize;
    [wrapperView setFrame:wrapperBounds];
    [self scrollViewDidZoom:scrollview];
    needsHandleContentSize = NO;
    [(TiUIScrollViewProxy *)[self proxy] layoutChildrenAfterContentSize:NO];
}

-(void)frameSizeChanged:(CGRect)frame bounds:(CGRect)visibleBounds
{
    if ([self flexibleContentWidth] || [self flexibleContentHeight])
	{
		needsHandleContentSize = YES;
	}
    [super frameSizeChanged:frame bounds:visibleBounds];
}



-(void)setContentWidth_:(id)value
{
	contentWidth = [TiUtils dimensionValue:value];
    _flexibleContentWidth = TiDimensionIsAuto(contentWidth) || TiDimensionIsAutoSize(contentWidth);
	[self performSelector:@selector(setNeedsHandleContentSize) withObject:nil afterDelay:.1];
}

-(void)setContentHeight_:(id)value
{
	contentHeight = [TiUtils dimensionValue:value];
    _flexibleContentHeight = TiDimensionIsAuto(contentHeight) || TiDimensionIsAutoSize(contentHeight);
	[self performSelector:@selector(setNeedsHandleContentSize) withObject:nil afterDelay:.1];
}

- (void)layoutSubviews {
    [super layoutSubviews];
    
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

- (void)zoomToPoint:(CGPoint)touchPoint withScale: (CGFloat)scale animated: (BOOL)animated
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



- (void)scrollViewDidEndZooming:(UIScrollView *)scrollView_ withView:(UIView *)view atScale:(CGFloat)scale
{
    [super scrollViewDidEndZooming:scrollView_ withView:view atScale:scale];
}

-(id)zoomScale_ {
    return @(scrollview.zoomScale);
}

- (void)scrollViewDidScroll:(UIScrollView *)scrollView
{
    [super scrollViewDidScroll:scrollView];
}

- (void)scrollViewWillBeginDragging:(UIScrollView *)scrollView
{
    [super scrollViewWillBeginDragging:scrollView];
}

- (void)scrollViewDidEndDragging:(UIScrollView *)scrollView willDecelerate:(BOOL)decelerate
{
    [super scrollViewDidEndDragging:scrollView willDecelerate:decelerate];
}

- (void)scrollViewDidEndDecelerating:(UIScrollView *)scrollView
{
    [super scrollViewDidEndDecelerating:scrollView];
}

- (BOOL)scrollViewShouldScrollToTop:(UIScrollView *)scrollView
{
    return [super scrollViewShouldScrollToTop:scrollView];
}

- (void)scrollViewDidScrollToTop:(UIScrollView *)scrollView
{
    return [super scrollViewDidScrollToTop:scrollView];
}

#pragma mark Keyboard delegate stuff

-(void)keyboardDidShowAtHeight:(CGFloat)keyboardTop
{
	InsetScrollViewForKeyboard(scrollview,keyboardTop,minimumContentHeight);
}

-(void)scrollToShowView:(UIView *)firstResponderView withKeyboardHeight:(CGFloat)keyboardTop
{
    if ([scrollview isScrollEnabled]) {
        CGRect responderRect = [wrapperView convertRect:[firstResponderView bounds] fromView:firstResponderView];
        OffsetScrollViewForRect(scrollview,keyboardTop,minimumContentHeight,responderRect);
    }
}

@end

#endif
