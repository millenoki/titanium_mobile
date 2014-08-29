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

@end
