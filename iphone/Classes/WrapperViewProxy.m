//
//  WrapperViewProxy.m
//  Titanium
//
//  Created by Martin Guillon on 22/11/2014.
//
//

#import "WrapperViewProxy.h"

@implementation WrapperViewProxy

- (id)initWithVerticalLayout:(BOOL)vertical
{
    self = [super init];
    if (self) {
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
    [super dealloc];
}

@end
