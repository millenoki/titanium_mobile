/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UISCROLLVIEW

#import "TiUIScrollViewProxy.h"
#import "TiUIScrollView.h"

#import "TiUtils.h"

@implementation TiUIScrollViewProxy

-(NSArray *)keySequence
{
    static NSArray *keySequence = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        keySequence = [[[super keySequence] arrayByAddingObjectsFromArray:@[@"minZoomScale",@"maxZoomScale",@"zoomScale"]] retain];;
    });
    return keySequence;
}


-(void)_initWithProperties:(NSDictionary *)properties
{
    [self initializeProperty:@"minZoomScale" defaultValue:NUMFLOAT(1.0)];
    [self initializeProperty:@"maxZoomScale" defaultValue:NUMFLOAT(1.0)];
    [self initializeProperty:@"zoomScale" defaultValue:NUMFLOAT(1.0)];
    [self initializeProperty:@"canCancelEvents" defaultValue:NUMBOOL(YES)];
    [self initializeProperty:@"scrollingEnabled" defaultValue:NUMBOOL(YES)];
    [super _initWithProperties:properties];
}

-(NSString*)apiName
{
    return @"Ti.UI.ScrollView";
}

-(TiPoint *) contentOffset{
    __block TiPoint * contentOffset;
    if([self viewAttached]){
        TiThreadPerformOnMainThread(^{
                   contentOffset = [[TiPoint alloc] initWithPoint:CGPointMake(
                                        [(TiUIScrollView *)[self view] scrollView].contentOffset.x,
                                        [(TiUIScrollView *)[self view] scrollView].contentOffset.y)] ; 
          }, YES);
    }
    else{
        contentOffset = [[TiPoint alloc] initWithPoint:CGPointMake(0,0)];
    }
    return [contentOffset autorelease];
}

-(void)windowWillOpen
{
    [super windowWillOpen];
    //Since layout children is overridden in scrollview need to make sure that 
    //a full layout occurs atleast once if view is attached
    if ([self viewAttached]) {
        [self contentsWillChange];
    }
}

-(void)contentsWillChange
{
	if ([self viewAttached] && parentVisible)
	{
		[(TiUIScrollView *)[self view] setNeedsHandleContentSize];
	}
	[super contentsWillChange];
}

-(void)willChangeSize
{
	if ([self viewAttached] && parentVisible)
	{
		[(TiUIScrollView *)[self view] setNeedsHandleContentSizeIfAutosizing];
	}
	[super willChangeSize];
}


-(void)layoutChildren:(BOOL)optimize
{
	if (![self viewAttached] || !parentVisible)
	{
		return;
	}

	[(TiUIScrollView *)[self view] handleContentSizeIfNeeded];
}

-(void)layoutChildrenAfterContentSize:(BOOL)optimize
{
	[super layoutChildren:optimize];	
}

-(CGSize)autoSizeForSize:(CGSize)size
{
    CGSize contentSize = CGSizeMake(size.width,size.height);
    if ([(TiUIScrollView *)[self view] flexibleContentWidth]) {
        contentSize.width = 0; //let the child be as wide as it wants.
    }
    if ([(TiUIScrollView *)[self view] flexibleContentHeight]) {
        contentSize.height = 0; //let the child be as high as it wants.
    }
    return [super autoSizeForSize:contentSize];
    }
    
//-(CGRect)computeChildSandbox:(TiViewProxy*)child withBounds:(CGRect)bounds
//{
//    CGRect contentSize = CGRectMake(bounds.origin.x, bounds.origin.y, bounds.size.width, bounds.size.height);
//    if ([(TiUIScrollView *)[self view] flexibleContentWidth]) {
//        contentSize.size.width = 0; //let the child be as wide as it wants.
//    }
//    if ([(TiUIScrollView *)[self view] flexibleContentHeight]) {
//        contentSize.size.height = 0; //let the child be as high as it wants.
//    }
//    
//    return [super computeChildSandbox:child withBounds:contentSize];
//}
-(void)childWillResize:(TiViewProxy *)child withinAnimation:(TiViewAnimationStep*)animation
{
	[super childWillResize:child withinAnimation:animation];
	[(TiUIScrollView *)[self view] setNeedsHandleContentSizeIfAutosizing];
}

-(BOOL)optimizeSubviewInsertion
{
    return YES;
}

-(void)scrollTo:(id)args
{
	ENSURE_ARG_COUNT(args,2);
	TiPoint * offset = [[TiPoint alloc] initWithPoint:CGPointMake(
			[TiUtils floatValue:[args objectAtIndex:0]],
			[TiUtils floatValue:[args objectAtIndex:1]])];

	[self setContentOffset:offset withObject:Nil];
	[offset release];
}

-(void)scrollToBottom:(id)args
{
    TiThreadPerformOnMainThread(^{
        [(TiUIScrollView *)[self view] scrollToBottom];
    }, YES);
}

-(void) setContentOffset:(id)value withObject:(id)animated
{
    TiThreadPerformOnMainThread(^{
        [(TiUIScrollView *)[self view] setContentOffset_:value withObject:animated];
    }, YES);
}

-(void) setZoomScale:(id)value withObject:(id)animated
{
    TiThreadPerformOnMainThread(^{
        [(TiUIScrollView *)[self view] setZoomScale_:value withObject:animated];
    }, YES);
}


-(NSDictionary*)dictForEventInScrollView:(UIScrollView *)scrollView_
{
    CGPoint offset = [scrollView_ contentOffset];
    return [NSDictionary dictionaryWithObjectsAndKeys:
            NUMFLOAT(offset.x),@"x",
            NUMFLOAT(offset.y),@"y",
            NUMFLOAT(scrollView_.zoomScale),@"curZoomScale",
            NUMBOOL([scrollView_ isZooming]),@"zooming",
            NUMBOOL([scrollView_ isDecelerating]),@"decelerating",
            NUMBOOL([scrollView_ isDragging]),@"dragging",
            nil];
}
- (void)scrollViewDidEndDecelerating:(UIScrollView *)scrollView_               // scrolling has ended
{
	if ([self _hasListeners:@"scrollend" checkParent:NO])
	{
        [self fireEvent:@"scrollend" withObject:[self dictForEventInScrollView:scrollView_] propagate:NO checkForListener:NO];
	}
}

-(void)scrollViewDidScroll:(UIScrollView *)scrollView_
{
	if ([self _hasListeners:@"scroll" checkParent:NO])
	{
        [self fireEvent:@"scroll" withObject:[self dictForEventInScrollView:scrollView_] propagate:NO checkForListener:NO];
    }
}

- (void)scrollViewDidEndZooming:(UIScrollView *)scrollView_ withView:(UIView *)view atScale:(CGFloat)scale
{
	[self replaceValue:NUMFLOAT(scale) forKey:@"zoomScale" notification:NO];
	
	if ([self _hasListeners:@"scale" checkParent:NO])
	{
        NSMutableDictionary* dict = [NSMutableDictionary dictionaryWithDictionary:[self dictForEventInScrollView:scrollView_]];
        [dict setObject:@(scale) forKey:@"scale"];
		[self fireEvent:@"scale" withObject:dict propagate:NO checkForListener:NO];
	}
}

-(void)scrollViewWillBeginDragging:(UIScrollView *)scrollView_
{
	if([self _hasListeners:@"dragstart" checkParent:NO])
	{
        [self fireEvent:@"dragstart" withObject:[self dictForEventInScrollView:scrollView_] propagate:NO checkForListener:NO];
	}
}

//listerner which tells when dragging ended in the scroll view.

-(void)scrollViewDidEndDragging:(UIScrollView *)scrollView_ willDecelerate:(BOOL)decelerate
{
	if([self _hasListeners:@"dragend" checkParent:NO])
	{
        NSMutableDictionary* dict = [NSMutableDictionary dictionaryWithDictionary:[self dictForEventInScrollView:scrollView_]];
        [dict setObject:@(decelerate) forKey:@"decelerate"];
        [self fireEvent:@"dragend" withObject:dict propagate:NO checkForListener:NO];
	}
}

DEFINE_DEF_PROP(scrollsToTop,@YES);

@end

#endif
