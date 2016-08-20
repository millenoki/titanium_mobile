/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2015 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#ifdef USE_TI_UIIOSBLURVIEW
#import "TiUIiOSBlurView.h"
#import "TiUIiOSBlurViewProxy.h"

@implementation TiUIiOSBlurView
{
    UIVisualEffectView *blurView;
    UIVisualEffectView *vibrancyView;
}

-(UIVisualEffectView*)blurView
{
    if (blurView == nil) {
        
        blurView = [[UIVisualEffectView alloc] initWithFrame:[self frame]];
        
        [blurView setAutoresizingMask:UIViewAutoresizingFlexibleWidth|UIViewAutoresizingFlexibleHeight];
//        [blurView setContentMode:[self contentModeForBlurView]];
        
        [self addSubview:blurView];
    }
    
    return blurView;
}

-(UIVisualEffectView*)vibrancyView
{
    if (vibrancyView == nil) {
        
        vibrancyView = [[UIVisualEffectView alloc] initWithFrame:[self frame]];
        
        [vibrancyView setAutoresizingMask:UIViewAutoresizingFlexibleWidth|UIViewAutoresizingFlexibleHeight];
        //        [blurView setContentMode:[self contentModeForBlurView]];
        
        [[[[self blurView] contentView] subviews] enumerateObjectsUsingBlock:^(__kindof UIView * _Nonnull view, NSUInteger idx, BOOL * _Nonnull stop) {
            [view removeFromSuperview];
            [[vibrancyView contentView] addSubview:view];
        }];
        [[[self blurView] contentView] addSubview:vibrancyView];
    }
    
    return vibrancyView;
}

-(UIView *)parentViewForChildren
{
    if (vibrancyView) {
        return [vibrancyView contentView];
    }
    return [[self blurView] contentView];
}

#pragma mark Cleanup

-(void)dealloc
{
    RELEASE_TO_NIL(blurView);
    [super dealloc];
}

#pragma mark Public APIs

-(void)setEffect_:(id)value
{
    ENSURE_TYPE(value, NSNumber);
    NSInteger style = [TiUtils intValue:value def:UIBlurEffectStyleLight];
    UIBlurEffect* effect = [UIBlurEffect effectWithStyle:style];
    [[self blurView] setEffect:effect];
    if (vibrancyView) {
        [vibrancyView setEffect:[UIVibrancyEffect effectForBlurEffect:effect]];
    }
}

-(void)setVibrancyEnabled_:(id)value
{
    BOOL enabled = [TiUtils boolValue:value];
    if (enabled) {
        if (!vibrancyView) {
            [[self vibrancyView] setEffect:[UIVibrancyEffect effectForBlurEffect:(UIBlurEffect*)[[self blurView] effect]]];
        }
    } else {
        if (vibrancyView) {
            [[[vibrancyView contentView] subviews] enumerateObjectsUsingBlock:^(__kindof UIView * _Nonnull view, NSUInteger idx, BOOL * _Nonnull stop) {
                [view removeFromSuperview];
                [[blurView contentView] addSubview:view];
            }];
        }
    }
}

//-(void)setWidth_:(id)width_
//{
//    width = TiDimensionFromObject(width_);
//    [self updateContentMode];
//}
//
//-(void)setHeight_:(id)height_
//{
//    height = TiDimensionFromObject(height_);
//    [self updateContentMode];
//}

#pragma mark Layout helper

//-(void)updateContentMode
//{
//    if ([self blurView] != nil) {
//        [[self blurView] setContentMode:[self contentModeForBlurView]];
//    }
//}

//-(UIViewContentMode)contentModeForBlurView
//{
////    if (TiDimensionIsAuto(width) || TiDimensionIsAutoSize(width) || TiDimensionIsUndefined(width) ||
////        TiDimensionIsAuto(height) || TiDimensionIsAutoSize(height) || TiDimensionIsUndefined(height)) {
//        return UIViewContentModeScaleAspectFit;
////    } else {
//        return UIViewContentModeScaleToFill;
////    }
//}

//-(void)frameSizeChanged:(CGRect)frame bounds:(CGRect)bounds
//{
//    for (UIView *child in [self subviews]) {
//        [TiUtils setView:child positionRect:bounds];
//    }
//    
//    [super frameSizeChanged:frame bounds:bounds];
//}


//-(CGFloat)contentWidthForWidth:(CGFloat)suggestedWidth
//{
//    if (autoWidth > 0) {
//        //If height is DIP returned a scaled autowidth to maintain aspect ratio
//        if (TiDimensionIsDip(height) && autoHeight > 0) {
//            return roundf(autoWidth*height.value/autoHeight);
//        }
//        return autoWidth;
//    }
//    
//    CGFloat calculatedWidth = TiDimensionCalculateValue(width, autoWidth);
//    if (calculatedWidth > 0) {
//        return calculatedWidth;
//    }
//    
//    return 0;
//}
//
//-(CGFloat)contentHeightForWidth:(CGFloat)width_
//{
//    if (width_ != autoWidth && autoWidth>0 && autoHeight > 0) {
//        return (width_*autoHeight/autoWidth);
//    }
//    
//    if (autoHeight > 0) {
//        return autoHeight;
//    }
//    
//    CGFloat calculatedHeight = TiDimensionCalculateValue(height, autoHeight);
//    if (calculatedHeight > 0) {
//        return calculatedHeight;
//    }
//    
//    return 0;
//}
//
//-(UIViewContentMode)contentMode
//{
//    if (TiDimensionIsAuto(width) || TiDimensionIsAutoSize(width) || TiDimensionIsUndefined(width) ||
//        TiDimensionIsAuto(height) || TiDimensionIsAutoSize(height) || TiDimensionIsUndefined(height)) {
//        return UIViewContentModeScaleAspectFit;
//    } else {
//        return UIViewContentModeScaleToFill;
//    }
//}

@end
#endif
