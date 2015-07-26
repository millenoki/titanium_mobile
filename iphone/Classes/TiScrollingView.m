#import "TiScrollingView.h"
#import "TiApp.h"
#import "TiViewProxy.h"

#import "UIScrollView+INSPullToRefresh.h"

@implementation TiScrollingView
{
    BOOL _hasPullView;
    BOOL _pullViewVisible;
    BOOL canFireScrollStart;
    BOOL canFireScrollEnd;
    BOOL isScrollingToTop;
    BOOL pullActive;
    CGFloat pullThreshhold;
    BOOL _scrollHidesKeyboard;
}

- (id)init
{
    self = [super init];
    if (self) {
        _hasPullView = NO;
        canFireScrollEnd = NO;
        canFireScrollStart = YES;
        _scrollHidesKeyboard = NO;
    }
    return self;
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

-(Class)scrollPanClass
{
    static Class scrollPanClass = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        scrollPanClass = [NSClassFromString(@"UIScrollViewPanGestureRecognizer") retain];;
    });
    return scrollPanClass;
}

//- (BOOL)gestureRecognizer:(UIGestureRecognizer *)gestureRecognizer shouldRecognizeSimultaneouslyWithGestureRecognizer:(UIGestureRecognizer *)otherGestureRecognizer
//{
//
//    if (([gestureRecognizer isKindOfClass:[self scrollPanClass]] &&
//         otherGestureRecognizer == panRecognizer) || ([otherGestureRecognizer isKindOfClass:[self scrollPanClass]] &&
//                                                 gestureRecognizer == panRecognizer)) {
//        return NO;
//    }
//    return YES;
//}
//- (BOOL)gestureRecognizer:(UIGestureRecognizer *)gestureRecognizer
//shouldBeRequiredToFailByGestureRecognizer:(UIGestureRecognizer *)otherGestureRecognizer {
//    if ([otherGestureRecognizer isKindOfClass:[self scrollPanClass]] &&
//         gestureRecognizer == panRecognizer) {
//        return YES;
//    }
//    return NO;
//}

//- (BOOL)gestureRecognizer:(UIGestureRecognizer *)gestureRecognizer
//shouldRequireFailureOfGestureRecognizer:(UIGestureRecognizer *)otherGestureRecognizer {
//    if ([otherGestureRecognizer isKindOfClass:[self scrollPanClass]] &&
//        gestureRecognizer == panRecognizer) {
//        return YES;
//    }
//    return NO;
//}

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
        [viewproxy setProxyObserver:self];
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


-(void)proxyDidRelayout:(id)sender
{
    NSArray* keys = [[self viewProxy] allKeysForHoldedProxy:sender];
    if ([keys count] > 0) {
        NSString* key = [keys objectAtIndex:0];
        if ([key isEqualToString:@"pullView"]) {
            CGRect frame = [(TiViewProxy*)sender view].frame;
            pullThreshhold = -[self scrollview].contentInset.top + ([(TiViewProxy*)sender view].frame.origin.y - frame.size.height);
        }
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


-(void)setDisableBounce_:(id)value
{
    [[self scrollview] setBounces:![TiUtils boolValue:value]];
}

-(void)setBouncesZoom_:(id)value
{
    [[self scrollview] setBouncesZoom:[TiUtils boolValue:value]];
}

-(void)setCanCancelEvents_:(id)args
{
    [[self scrollview] setCanCancelContentTouches:[TiUtils boolValue:args def:YES]];
}

-(void)setDelaysContentTouches_:(id)value
{
    [[self scrollview] setDelaysContentTouches:[TiUtils boolValue:value def:YES]];
}

-(void)setScrollingEnabled_:(id)enabled
{
    scrollingEnabled = [TiUtils boolValue:enabled];
    [[self scrollview] setScrollEnabled:scrollingEnabled];
}
- (void)setWillScrollOnStatusTap_:(id)value
{
    [[self scrollview] setScrollsToTop:[TiUtils boolValue:value def:YES]];
}


- (void)zoomToPoint:(CGPoint)touchPoint withScale: (CGFloat)scale animated: (BOOL)animated
{
    UIScrollView* scrollView = [self scrollview];
    CGFloat touchX = touchPoint.x;
    CGFloat touchY = touchPoint.y;
    touchX *= 1/scrollView.zoomScale;
    touchY *= 1/scrollView.zoomScale;
    touchX += scrollView.contentOffset.x;
    touchY += scrollView.contentOffset.y;
    
    CGFloat xsize = scrollView.bounds.size.width / scale;
    CGFloat ysize = self.bounds.size.height / scale;
    [scrollView zoomToRect:CGRectMake(touchX - xsize/2, touchY - ysize/2, xsize, ysize) animated:animated];
}

-(void)scrollToBottom
{
    /*
     * Calculate the bottom height & width and, sets the offset from the
     * content view’s origin that corresponds to the receiver’s origin.
     */
    UIScrollView *currScrollView = [self scrollview];
    
    CGSize svContentSize = currScrollView.contentSize;
    CGSize svBoundSize = currScrollView.bounds.size;
    CGFloat svBottomInsets = currScrollView.contentInset.bottom;
    
    CGFloat bottomHeight = svContentSize.height - svBoundSize.height + svBottomInsets;
    CGFloat bottomWidth = svContentSize.width - svBoundSize.width;
    
    CGPoint newOffset = CGPointMake(bottomWidth,bottomHeight);
    
    [currScrollView setContentOffset:newOffset animated:YES];
    
}



-(void)setMaxZoomScale_:(id)args
{
    UIScrollView* scrollView = [self scrollview];
    CGFloat val = [TiUtils floatValue:args def:1.0];
    [[self scrollview] setMaximumZoomScale:val];
    if ([scrollView zoomScale] > val) {
        [self setZoomScale_:args withObject:nil];
    }
    else if ([scrollView zoomScale] < [scrollView minimumZoomScale]){
        [self setZoomScale_:[NSNumber numberWithFloat:[scrollView minimumZoomScale]] withObject:nil];
    }
}

-(void)setMinZoomScale_:(id)args
{
    UIScrollView* scrollView = [self scrollview];
    CGFloat val = [TiUtils floatValue:args def:1.0];
    [scrollView setMinimumZoomScale:val];
    if ([scrollView zoomScale] < val) {
        [self setZoomScale_:args withObject:nil];
    }
}

-(void)setZoomScale_:(id)value withObject:(id)property
{
    UIScrollView* scrollView = [self scrollview];
    CGFloat scale = [TiUtils floatValue:value def:1.0];
    BOOL animated = [TiUtils boolValue:@"animated" properties:property def:NO];
    if ([property valueForKey:@"point"]) {
        [self zoomToPoint:[TiUtils pointValue:@"point" properties:property] withScale:scale animated:animated];
    } else {
        [scrollView setZoomScale:scale animated:animated];
        
    }
    
    scale = [scrollView zoomScale]; //Why are we doing this? Because of minZoomScale or maxZoomScale.
    if (!animated) {
        [self scrollViewDidEndZooming:scrollView withView:scrollView atScale:scale];
    }

}

-(void)setContentOffset_:(id)value withObject:(id)property
{
    UIScrollView* scrollView = [self scrollview];
    CGPoint newOffset = [TiUtils pointValue:value];
    BOOL animated = [TiUtils boolValue:@"animated" properties:property def:(scrollView !=nil)];
    [scrollView setContentOffset:newOffset animated:animated];
}


-(void)setScrollsToTop_:(id)value
{
    [[self scrollview] setScrollsToTop:[TiUtils boolValue:value def:YES]];
}

-(void)setHorizontalBounce_:(id)value
{
    [[self scrollview] setAlwaysBounceHorizontal:[TiUtils boolValue:value]];
}

-(void)setVerticalBounce_:(id)value
{
    [[self scrollview] setAlwaysBounceVertical:[TiUtils boolValue:value]];
}


-(void)setDecelerationRate_:(id)value
{
    [[self scrollview] setDecelerationRate:[TiUtils floatValue:value def:UIScrollViewDecelerationRateNormal]];
}
-(void)setShowHorizontalScrollIndicator_:(id)value
{
    [[self scrollview] setShowsHorizontalScrollIndicator:[TiUtils boolValue:value]];
}

-(void)setShowVerticalScrollIndicator_:(id)value
{
    [[self scrollview] setShowsVerticalScrollIndicator:[TiUtils boolValue:value]];
}

-(void)setScrollIndicatorStyle_:(id)value
{
    [[self scrollview] setIndicatorStyle:[TiUtils intValue:value def:UIScrollViewIndicatorStyleDefault]];
}
-(void)setDirectionalLockEnabled_:(id)value
{
    [[self scrollview] setDirectionalLockEnabled:[TiUtils boolValue:value]];
}


-(void)setScrollHidesKeyboard_:(id)value
{
    _scrollHidesKeyboard = [TiUtils boolValue:value def:_scrollHidesKeyboard];
}


- (NSMutableDictionary *) eventObjectForScrollView: (UIScrollView *) scrollView
{
    return [NSMutableDictionary dictionaryWithObjectsAndKeys:
            [TiUtils pointToDictionary:scrollView.contentOffset],@"contentOffset",
            @(scrollView.zoomScale),@"zoomScale",
            @([scrollView isZooming]),@"zooming",
            @([scrollView isDecelerating]),@"decelerating",
            @([scrollView isDragging]),@"dragging",
            [TiUtils sizeToDictionary:scrollView.contentSize], @"contentSize",
            [TiUtils sizeToDictionary:scrollView.bounds.size], @"size",
            nil];
}


// For now, this is fired on `scrollstart` and `scrollend`
- (void)fireScrollEvent:(NSString*)eventName forScrollView:(UIScrollView*)scrollView withAdditionalArgs:(NSDictionary*)args
{
    if([[self viewProxy] _hasListeners:eventName checkParent:NO])
    {
        NSMutableDictionary* eventArgs = [self eventObjectForScrollView:scrollView];
        
        [eventArgs setValuesForKeysWithDictionary:args];
        
        [[self proxy] fireEvent:eventName withObject:eventArgs propagate:NO checkForListener:NO];
    }
}


- (void)fireScrollEvent:(UIScrollView *)scrollView {
    [self fireScrollEvent:@"scroll" forScrollView:scrollView withAdditionalArgs:nil];
}

- (void)scrollViewDidZoom:(UIScrollView *)scrollView_
{
}



- (void)scrollViewDidEndZooming:(UIScrollView *)scrollView_ withView:(UIView *)view atScale:(CGFloat)scale
{
    if (scrollView_.zoomScale == scrollView_.minimumZoomScale) {
        scrollView_.scrollEnabled = NO;
    }else {
        scrollView_.scrollEnabled = scrollingEnabled;
    }
    [self.proxy replaceValue:NUMFLOAT(scale) forKey:@"zoomScale" notification:NO];
    
    [self fireScrollEvent:@"scale" forScrollView:scrollView_ withAdditionalArgs:nil];
}

- (void)scrollViewDidScroll:(UIScrollView *)scrollView
{
    if (scrollView.isDragging || scrollView.isDecelerating)
    {
        [self fireScrollEvent:scrollView];
    }
    if ( _hasPullView && ([scrollView isTracking]) ) {
        BOOL pullChanged = NO;
        if ( (scrollView.contentOffset.y < pullThreshhold) && (pullActive == NO) ) {
            pullActive = YES;
            pullChanged = YES;
        } else if ( (scrollView.contentOffset.y > pullThreshhold) && (pullActive == YES) ) {
            pullActive = NO;
            pullChanged = YES;
        }
        if (pullChanged && [[self viewProxy] _hasListeners:@"pullchanged" checkParent:NO]) {
            [self fireScrollEvent:@"pullchanged" forScrollView:scrollView withAdditionalArgs:@{@"active": @(pullActive)}];
        }
        if (scrollView.contentOffset.y <= 0 && [[self viewProxy] _hasListeners:@"pull" checkParent:NO]) {
            [self fireScrollEvent:@"pull" forScrollView:scrollView withAdditionalArgs:@{@"active": @(pullActive)}];
        }
    }
}

- (void)fireScrollEnd:(UIScrollView*)scrollView
{
//    if(canFireScrollEnd) {
//        canFireScrollEnd = NO;
//        canFireScrollStart = YES;
        [self fireScrollEvent:@"scrollend" forScrollView:scrollView withAdditionalArgs:nil];
//    }
}
- (void)fireScrollStart:(UIScrollView *)scrollView
{
//    if(canFireScrollStart) {
//        canFireScrollStart = NO;
//        canFireScrollEnd = YES;
        [self fireScrollEvent:@"scrollstart" forScrollView:scrollView withAdditionalArgs:nil];
//    }
}



- (void)scrollViewWillBeginDragging:(UIScrollView *)scrollView
{
    if (_scrollHidesKeyboard) {
        [scrollView endEditing:YES];
    }
    [self fireScrollStart:scrollView];
    [self fireScrollEvent:@"dragstart" forScrollView:scrollView withAdditionalArgs:nil];
}

- (void)scrollViewDidEndDragging:(UIScrollView *)scrollView willDecelerate:(BOOL)decelerate
{
    if(!decelerate) {
        [self fireScrollEnd:(UITableView *)scrollView];
    }
    if ([(TiViewProxy*)self.proxy _hasListeners:@"dragend" checkParent:NO])
    {
        [self fireScrollEvent:@"dragend" forScrollView:scrollView withAdditionalArgs:nil];
    }
    
    
    if ( _hasPullView && (pullActive == YES) ) {
        pullActive = NO;
        [self fireScrollEvent:@"pullend" forScrollView:scrollView withAdditionalArgs:nil];
    }
}



- (void)scrollViewDidEndDecelerating:(UIScrollView *)scrollView
{
    // resume image loader when we're done scrolling
    [self fireScrollEvent:scrollView];
    if(isScrollingToTop) {
        isScrollingToTop = NO;
    } else {
        [self fireScrollEnd:scrollView];
    }
}

- (BOOL)scrollViewShouldScrollToTop:(UIScrollView *)scrollView
{
    isScrollingToTop = YES;
    [self fireScrollStart:scrollView];
    return YES;
}

- (void)scrollViewDidScrollToTop:(UIScrollView *)scrollView
{
    [self fireScrollEnd:scrollView];
}


@end
