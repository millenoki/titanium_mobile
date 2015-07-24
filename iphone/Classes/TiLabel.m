//
//  TiLabel.m
//  Titanium
//
//  Created by Martin Guillon on 19/12/13.
//
//

#import "TiLabel.h"

@interface TiLabel (PrivateMethods)
- (void)redrawLabel;
- (void)resetGradient;
@end


@implementation TiLabel
{
    BOOL _hasStroke;
    UIColor* _strokeColor;
    CGFloat _strokeWidth;
}
@synthesize hasStroke = _hasStroke;
@synthesize strokeColor = _strokeColor;
@synthesize strokeWidth = _strokeWidth;
- (id)initWithFrame:(CGRect)frame
{
    self = [super initWithFrame:frame];
    if (self) {
        //initialize common properties
        _hasStroke = NO;
        _strokeWidth = 1.0f;
//        self.backgroundColor = [UIColor clearColor];
        
        [self redrawLabel];
    }
    return self;
}

-(void)dealloc
{
    [_strokeColor release];
    [super dealloc];
}

- (void)redrawLabel {
    //draw gradient
    [self resetGradient];
}

- (void)resetGradient {
    if (!CGRectEqualToRect(self.frame, CGRectZero)) {
    }
}

#pragma mark Overriden/custom setters/getters

- (void)setHasStroke:(BOOL)stroke {
    _hasStroke = stroke;
    [self setNeedsDisplay];
}

- (void)setStrokeColor:(UIColor *)color {
    [_strokeColor release];
    _strokeColor = [color retain];
    //enable/disable stroke
    self.hasStroke = (_strokeColor != nil);
}

- (void)setStrokeWidth:(CGFloat)width {
    _strokeWidth = width;
}




- (void)setText:(NSString *)text {
    [super setText:text];
    [self redrawLabel];
}

- (void)setFont:(UIFont *)font {
    [super setFont:font];
    [self redrawLabel];
}

- (void)setFrame:(CGRect)frame {
    [super setFrame:frame];
    //draw gradient
    [self resetGradient];
}

#pragma mark -


- (void)drawTextInRect:(CGRect)rect {
    //draw stroke
    if (_hasStroke) {
        CGSize shadowOffset = self.shadowOffset;
        UIColor *textColor = self.textColor;
        
        CGContextRef c = UIGraphicsGetCurrentContext();
        CGContextSetLineWidth(c, _strokeWidth);
        CGContextSetLineJoin(c, kCGLineJoinRound);
        
        CGContextSetTextDrawingMode(c, kCGTextStroke);
        self.textColor = _strokeColor;
        [super drawTextInRect:rect];
        
        CGContextSetTextDrawingMode(c, kCGTextFill);
        self.textColor = textColor;
        self.shadowOffset = CGSizeMake(0, 0);
        [super drawTextInRect:rect];
        self.shadowOffset = shadowOffset;
    } else {
        [super drawTextInRect:rect];
    }
    
}

@end
