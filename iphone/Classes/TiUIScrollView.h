/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UISCROLLVIEW

#import "TiScrollingView.h"



@interface TiUIScrollView : TiScrollingView<TiScrolling> {

@private
	TDUIScrollView * scrollview;
	UIView * wrapperView;
	TiDimension contentWidth;
	TiDimension contentHeight;
	
	CGFloat minimumContentHeight;
	
	BOOL needsHandleContentSize;
	
}

@property(nonatomic,retain,readonly) TDUIScrollView * scrollview;

//@property(nonatomic,readonly) TiDimension contentWidth;

-(void)setNeedsHandleContentSize;
-(void)setNeedsHandleContentSizeIfAutosizing;
-(BOOL)handleContentSizeIfNeeded;
-(void)handleContentSize;
-(UIView *)wrapperView;
-(BOOL)flexibleContentWidth;
-(BOOL)flexibleContentHeight;
@end

#endif
