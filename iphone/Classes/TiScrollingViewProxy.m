#import "TiScrollingViewProxy.h"

@implementation TiScrollingViewProxy


- (void)viewDidInitialize
{
    [super viewDidInitialize];
    if (parentVisible) {
         [[NSNotificationCenter defaultCenter] addObserver:self.view selector:@selector(updateKeyboardInset) name:kTiKeyboardHeightChangedNotification object:nil];
    }
}

- (void)willShow
{
    [[NSNotificationCenter defaultCenter] addObserver:self.view selector:@selector(updateKeyboardInset) name:kTiKeyboardHeightChangedNotification object:nil];
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
@end
