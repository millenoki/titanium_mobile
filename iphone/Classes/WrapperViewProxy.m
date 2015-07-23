//
//  WrapperViewProxy.m
//  Titanium
//
//  Created by Martin Guillon on 22/11/2014.
//
//

#import "WrapperViewProxy.h"
#import "TiTableView.h"


@implementation WrapperViewProxy
{
    TiTableView* _tableView;
}

- (id)initWithVerticalLayout:(BOOL)vertical tableView:(TiTableView*)tableView;
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
    __block BOOL result;
    [_tableView processBlock:^void(UITableView * tableView) {
        result = [super relayout];
    } animated:NO];
    return result;
}

-(void)willChangePosition
{
    //we ignore position change as the tableview handles it
}

@end
