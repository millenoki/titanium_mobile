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

-(void)scrollToShowView:(TiUIView *)firstResponderView withKeyboardHeight:(CGFloat)keyboardTop
{
}

- (void)updateKeyboardInset  {
    UIView* inputView = [[TiApp app] controller].keyboardActiveInput;
    CGFloat keyboardHeight = [[TiApp app] controller].keyboardHeight;
    
    
    [self keyboardDidShowAtHeight:keyboardHeight];
    
    if ([inputView isKindOfClass:[TiUIView class]]) {
        [self scrollToShowView:(TiUIView*)inputView withKeyboardHeight:keyboardHeight];
    }
}


@end
