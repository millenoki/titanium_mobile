#import "TiScrollingView.h"
#import "TiApp.h"

@implementation TiScrollingView

- (void)didMoveToSuperview
{
	[super didMoveToSuperview];
    [self updateKeyboardInset];
}

-(void)frameSizeChanged:(CGRect)frame bounds:(CGRect)bounds
{
    [super frameSizeChanged:frame bounds:bounds];
    [self updateKeyboardInset];
}

-(void)keyboardDidShowAtHeight:(CGFloat)keyboardTop
{
}

-(void)scrollToShowView:(UIView *)firstResponderView withKeyboardHeight:(CGFloat)keyboardTop
{
}

- (BOOL) topView:(UIView*)topView containsView:(UIView *) view  {
    for (UIView * theView in [topView subviews]){
        if (theView == view || [self topView:theView containsView:view]) {
            return YES;
        }
    }
    return NO;
}

- (void)updateKeyboardInset  {
    UIView* inputView = [[TiApp app] controller].keyboardActiveInput;
    CGFloat keyboardHeight = [[TiApp app] controller].keyboardHeight;
    
    
    [self keyboardDidShowAtHeight:keyboardHeight];
    
    if (inputView && [self topView:self containsView:inputView]) {
        double delayInSeconds = 0.3;
        dispatch_time_t popTime = dispatch_time(DISPATCH_TIME_NOW, (int64_t)(delayInSeconds * NSEC_PER_SEC));
        dispatch_after(popTime, dispatch_get_main_queue(), ^(void){
            [self scrollToShowView:inputView withKeyboardHeight:keyboardHeight];
        });
    }
}


@end
