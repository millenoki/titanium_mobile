#import "TiScrollingView.h"
#import "TiApp.h"
#import "TiViewProxy.h"

#import "UIScrollView+INSPullToRefresh.h"

@implementation TiScrollingView
{
    BOOL _hasPullView;
    BOOL _pullViewVisible;
}

- (void)didMoveToSuperview
{
	[super didMoveToSuperview];
    [self updateKeyboardInset];
}

-(UIScrollView*)scrollview {
    return nil;
}

- (void)dealloc
{
    UIScrollView* scrollView = [self scrollview];
    [scrollView ins_removeInfinityScroll];
    [scrollView ins_removePullToRefresh];
    [super dealloc];
}


-(void)frameSizeChanged:(CGRect)frame bounds:(CGRect)bounds
{
    [super frameSizeChanged:frame bounds:bounds];
    if (_hasPullView) {
        TiViewProxy* vp = [[self viewProxy] holdedProxyForKey:@"pullView"];
        if (vp) {
            [vp setSandboxBounds:bounds];
            [vp refreshView];
            UIScrollView* scrollView = [self scrollview];
            scrollView.ins_pullToRefreshBackgroundView.frame = [[vp view] bounds];
        }
    }
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
- (void)pullToRefreshBackgroundView:(INSPullToRefreshBackgroundView *)pullToRefreshBackgroundView didChangeState:(INSPullToRefreshBackgroundViewState)state
{
    if (_pullViewVisible) {
        return;
    }
    if (state == INSPullToRefreshBackgroundViewStateLoading) {
        [[self viewProxy] fireEvent:@"pullend" propagate:NO];

    } else {
        if ([[self viewProxy] _hasListeners:@"pullchanged" checkParent:NO]) {
            [self.proxy fireEvent:@"pullchanged" withObject:[NSDictionary dictionaryWithObjectsAndKeys:@(state != INSPullToRefreshBackgroundViewStateTriggered),@"active",nil] propagate:NO checkForListener:NO];
        }
    }
}

- (void)pullToRefreshBackgroundView:(INSPullToRefreshBackgroundView *)pullToRefreshBackgroundView didChangeTriggerStateProgress:(CGFloat)progress
{
    if ([[self viewProxy] _hasListeners:@"pull" checkParent:NO]) {
        [self.proxy fireEvent:@"pull" withObject:[NSDictionary dictionaryWithObjectsAndKeys:@(YES),@"active",@(progress),@"progress",nil] propagate:NO checkForListener:NO];
    }
}


-(void)setPullView_:(id)args
{
    UIScrollView* scrollView = [self scrollview];
    
    if (!scrollView) {
        return;
    }
    id vp = [[self viewProxy] addObjectToHold:args forKey:@"pullView"];
    if (IS_OF_CLASS(vp, TiViewProxy)) {
        TiViewProxy* viewproxy = (TiViewProxy*)vp;
        _hasPullView = YES;
        
        LayoutConstraint *viewLayout = [viewproxy layoutProperties];
        if (viewLayout->height.type != TiDimensionTypeDip) {
            viewLayout->height = TiDimensionAutoSize;
        }

        [scrollView ins_addPullToRefreshWithHeight:1.0 handler:^(UIScrollView *scrollView) {
//            [scrollView ins_endPullToRefresh];
        }];
        scrollView.ins_pullToRefreshBackgroundView.delegate = self;
        scrollView.ins_pullToRefreshBackgroundView.autoresizingMask = UIViewAutoresizingNone;
        [scrollView.ins_pullToRefreshBackgroundView addSubview:[viewproxy getAndPrepareViewForOpening:scrollView.bounds]];
    } else {
        _hasPullView = NO;
        [scrollView ins_removeInfinityScroll];
        [scrollView ins_removePullToRefresh];
    }
    
}

-(void)closePullView:(NSNumber*)anim
{
    if (!_hasPullView || !_pullViewVisible) return;
    UIScrollView* scrollView = [self scrollview];
    
    if (!scrollView) {
        return;
    }
    _pullViewVisible = NO;
    BOOL animated = YES;
    if (anim != nil) {
        animated = [anim boolValue];
    }
    [CATransaction begin];
    if (!animated) {
        [CATransaction setValue:(id)kCFBooleanTrue forKey:kCATransactionDisableActions];
    }
    [scrollView ins_beginPullToRefresh];
    [CATransaction commit];

    
}

-(void)showPullView:(NSNumber*)anim
{
    if (!_hasPullView || _pullViewVisible) {
        return;
    }
    UIScrollView* scrollView = [self scrollview];
    
    if (!scrollView) {
        return;
    }
    _pullViewVisible = YES;
    BOOL animated = YES;
    if (anim != nil) {
        animated = [anim boolValue];
    }
    [CATransaction begin];
    if (!animated) {
        [CATransaction setValue:(id)kCFBooleanTrue forKey:kCATransactionDisableActions];
    }
    [scrollView ins_beginPullToRefresh];
    [CATransaction commit];
}


@end
