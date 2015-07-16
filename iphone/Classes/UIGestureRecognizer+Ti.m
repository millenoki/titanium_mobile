//
//  UIGestureRecognizer+Ti.m
//  MapMe
//
//  Created by Martin Guillon on 16/07/2015.
//
//

#import "UIGestureRecognizer+Ti.h"
#import "TiUIView.h"
#import <objc/runtime.h>
#import <UIKit/UIGestureRecognizerSubclass.h>
#import "JRSwizzle.h"

static NSString * const kTiGestureStartTouchedView = @"kTiGestureStartTouchedView";

@implementation UIGestureRecognizer (StartTouchedView)

+ (void) swizzle
{
    [UIGestureRecognizer jr_swizzleMethod:@selector(setState:) withMethod:@selector(setStateCustom:) error:nil];
}

- (void)setStartTouchedView:(TiUIView *)view
{
    objc_setAssociatedObject(self, kTiGestureStartTouchedView, view, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (TiUIView*)startTouchedView
{
    return objc_getAssociatedObject(self, kTiGestureStartTouchedView);
}
-(void)setStateCustom:(UIGestureRecognizerState)state
{
    //WARNING: this is the swizzle trick, will actually call [UIGestureRecognizer setState:]
    [self setStateCustom:state];
    UIView* theView = [self startTouchedView];
    if (state == UIGestureRecognizerStateBegan || !theView) {
        UIView* view = self.view;
        CGPoint loc = [self locationInView:view];
        theView = [view hitTest:loc withEvent:nil];
        while (theView && !IS_OF_CLASS(theView, TiUIView)) {
            theView = [theView superview];
        }
        if (state == UIGestureRecognizerStateBegan) {
            [self setStartTouchedView:(TiUIView*)theView];
        }
    } else if (state == UIGestureRecognizerStateRecognized) {
        TiUIView* theView = [self startTouchedView];
        [theView processTouchesCancelled:nil withEvent:nil];
    } else if (state == UIGestureRecognizerStateEnded ||
               state == UIGestureRecognizerStateCancelled) {
        [self setStartTouchedView:nil];
    }
    
}
@end

