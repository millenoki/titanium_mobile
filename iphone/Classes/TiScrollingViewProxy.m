#import "TiScrollingViewProxy.h"
#import "TiScrollingView.h"

@implementation TiScrollingViewProxy

-(NSArray *)keySequence
{
    static NSArray *keySequence = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        keySequence = [[[super keySequence] arrayByAddingObjectsFromArray:@[@"minZoomScale",@"maxZoomScale",@"zoomScale"]] retain];;
    });
    return keySequence;
}

-(TiScrollingView*)scrollingView {
    return (TiScrollingView*)[self view];
}

- (void)viewDidInitialize
{
    [super viewDidInitialize];
    if (parentVisible) {
         [[NSNotificationCenter defaultCenter] addObserver:self.view selector:@selector(updateKeyboardInset) name:kTiKeyboardHeightChangedNotification object:nil];
    }
}

- (void)willShow
{
    if (view) {
        [[NSNotificationCenter defaultCenter] addObserver:self.view selector:@selector(updateKeyboardInset) name:kTiKeyboardHeightChangedNotification object:nil];
    }
	[super willShow];
}

- (void)willHide
{
    [[NSNotificationCenter defaultCenter] removeObserver:self.view name:kTiKeyboardHeightChangedNotification object:nil];
	[super willHide];
}

-(void)viewWillDetach
{
    [[NSNotificationCenter defaultCenter] removeObserver:self.view name:kTiKeyboardHeightChangedNotification object:nil];
	[super viewWillDetach];
}


-(void) setContentOffset:(id)value withObject:(id)animated
{
    TiThreadPerformOnMainThread(^{
        [[self scrollingView] setContentOffset_:value withObject:animated];
    }, YES);
}

-(void) setZoomScale:(id)value withObject:(id)animated
{
    TiThreadPerformOnMainThread(^{
        [[self scrollingView] setZoomScale_:value withObject:animated];
    }, YES);
}


-(void)showPullView:(id)args
{
    ENSURE_SINGLE_ARG_OR_NIL(args,NSNumber);
    [self makeViewPerformSelector:@selector(showPullView:) withObject:args createIfNeeded:NO waitUntilDone:NO];
}

-(void)closePullView:(id)args
{
    ENSURE_SINGLE_ARG_OR_NIL(args,NSNumber);
    [self makeViewPerformSelector:@selector(closePullView:) withObject:args createIfNeeded:NO waitUntilDone:NO];
}


-(void)scrollToTop:(id)args
{
    NSInteger top = [TiUtils intValue:[args objectAtIndex:0] def:0];
    NSDictionary *options = [args count] > 1 ? [args objectAtIndex:1] : nil;
    BOOL animated = [TiUtils boolValue:@"animated" properties:options def:YES];
    TiThreadPerformBlockOnMainThread(^{
        [[self scrollingView] setContentOffsetToTop:top animated:animated];
    }, NO);
}

-(void)scrollToBottom:(id)args
{
    NSInteger top = [TiUtils intValue:[args objectAtIndex:0] def:0];
    NSDictionary *options = [args count] > 1 ? [args objectAtIndex:1] : nil;
    BOOL animated = [TiUtils boolValue:@"animated" properties:options def:YES];
    TiThreadPerformBlockOnMainThread(^{
        [[self scrollingView] setContentOffsetToBottom:top animated:animated];
    }, NO);
}

-(TiPoint *) contentOffset{
    __block TiPoint * contentOffset;
    if([self viewAttached]){
        TiThreadPerformOnMainThread(^{
            CGPoint offset = [[self scrollingView] scrollView].contentOffset;
            contentOffset = [[TiPoint alloc] initWithPoint:CGPointMake(
                                                                       offset.x,
                                                                       offset.y)] ;
        }, YES);
    }
    else{
        contentOffset = [[TiPoint alloc] initWithPoint:CGPointMake(0,0)];
    }
    return [contentOffset autorelease];
}

@end
