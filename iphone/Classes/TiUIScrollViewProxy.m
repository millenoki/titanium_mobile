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


//-(void)windowWillOpen
//{
//    [super windowWillOpen];
//    //Since layout children is overridden in scrollview need to make sure that 
//    //a full layout occurs atleast once if view is attached
//    if ([self viewAttached]) {
//        [self contentsWillChange];
//    }
//}

-(void)contentsWillChange
{
	[super contentsWillChange];
    if ([self viewAttached] && parentVisible)
    {
        [(TiUIScrollView *)[self view] setNeedsHandleContentSize];
    }
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
    if (CGSizeEqualToSize([[self view] bounds].size, CGSizeZero)) return;

    if (![(TiUIScrollView *)[self view] handleContentSizeIfNeeded]) {
        [self layoutChildrenAfterContentSize:optimize];
    }
}

-(void)layoutChildrenAfterContentSize:(BOOL)optimize
{
	[super layoutChildren:optimize];	
}

-(CGSize)autoSizeForSize:(CGSize)size ignoreMinMax:(BOOL)ignoreMinMaxComputation
{
#ifndef TI_USE_AUTOLAYOUT
    CGSize contentSize = CGSizeMake(size.width,size.height);
    if ([(TiUIScrollView *)[self view] flexibleContentWidth]) {
        contentSize.width = 0; //let the child be as wide as it wants.
    }
    if ([(TiUIScrollView *)[self view] flexibleContentHeight]) {
        contentSize.height = 0; //let the child be as high as it wants.
    }
        return [super autoSizeForSize:contentSize ignoreMinMax:ignoreMinMaxComputation];
#else
    return 0.0;
#endif
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

@end

#endif
