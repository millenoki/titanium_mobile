//
//  WrapperViewProxy.m
//  Titanium
//
//  Created by Martin Guillon on 22/11/2014.
//
//

#import "WrapperViewProxy.h"


@implementation WrapperViewProxy
{
    UITableView* _tableView;
}

- (id)initWithVerticalLayout:(BOOL)vertical tableView:(UITableView*)tableView;
{
    self = [super init];
    if (self) {
        _tableView = [tableView retain];
        self.canBeResizedByFrame = YES;
        LayoutConstraint* viewLayout = [self layoutProperties];
        viewLayout->width = TiDimensionAutoFill;
        viewLayout->height = TiDimensionAutoSize;
        if (TiDimensionIsUndefined(viewLayout->top))
        {
            viewLayout->top = TiDimensionDip(0);
        }
        if (vertical) {
            viewLayout->layoutStyle = TiLayoutRuleVertical;
        }
    }
    return self;
}
- (void)dealloc
{
    RELEASE_TO_NIL(_tableView)
    [super dealloc];
}

-(BOOL)relayout
{
    [UIView setAnimationsEnabled:[self animating]];
    [_tableView beginUpdates];
    BOOL result = [super relayout];
    [_tableView endUpdates];
    [UIView setAnimationsEnabled:YES];
    return result;
}



@end
