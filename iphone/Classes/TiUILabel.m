/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2014 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UILABEL

#import "TiUILabel.h"
#import "TiUILabelProxy.h"
#import "TiUtils.h"
#import "UIImage+Resize.h"
#import <CoreText/CoreText.h>

#if defined (USE_TI_UIATTRIBUTEDSTRING)
#import "TiUIAttributedStringProxy.h"
#endif
#import "DTCoreText.h"
#import "TiTransitionHelper.h"
#import "TiTransition.h"

@interface TiUILabel()
{
    BOOL _reusing;
}
@property(nonatomic,retain) NSDictionary *transition;
@end


@implementation TiUILabel

#pragma mark Internal

-(id)init
{
    if (self = [super init]) {
        self.transition = nil;
    }
    return self;
}

-(void)dealloc
{
	RELEASE_TO_NIL(_transition);
    RELEASE_TO_NIL(label);
    [super dealloc];
}

-(UIView*)viewForHitTest
{
    return label;
}

- (CGSize)suggestedFrameSizeToFitEntireStringConstraintedToSize:(CGSize)size
{
    CGSize maxSize = CGSizeMake(size.width<=0 ? 10000 : size.width, 10000);
    CGSize result = [[self label] sizeThatFits:maxSize];
    if ([label shadowRadius] > 0) {
        CGSize shadowOffset = [label shadowOffset];
        if (result.width > 0) {
            result.width += fabs(shadowOffset.width);
        }
        if (result.height > 0) {
            result.height += fabs(shadowOffset.height);
        }
    }
    return result;
}

-(CGSize)contentSizeForSize:(CGSize)size
{
    return [self suggestedFrameSizeToFitEntireStringConstraintedToSize:size];
}

-(void)frameSizeChanged:(CGRect)frame bounds:(CGRect)bounds
{
    [label setFrame:bounds];
    [super frameSizeChanged:frame bounds:bounds];
}

- (void)configurationStart {
    [super configurationStart];
    needsSetText = NO;
}

- (void)configurationSet {
    
    [super configurationSet];
    if (needsSetText)
        [self setAttributedTextViewContent];
}

-(TTTAttributedLabel*)label
{
	if (label==nil)
	{
        label = [[TiLabel alloc] initWithFrame:CGRectZero];
        label.backgroundColor = [UIColor clearColor];
        label.numberOfLines = 0;//default wordWrap to True
        label.lineBreakMode = NSLineBreakByWordWrapping; //default ellipsis to none
        label.layer.shadowRadius = 0; //for backward compatibility
        label.layer.shadowOffset = CGSizeZero;
		label.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
        label.touchDelegate = self;
        label.strokeColorAttributeProperty = DTBackgroundStrokeColorAttribute;
        label.strokeWidthAttributeProperty = DTBackgroundStrokeWidthAttribute;
        label.cornerRadiusAttributeProperty = DTBackgroundCornerRadiusAttribute;
        label.paddingAttributeProperty = DTPaddingAttribute;
        label.linkAttributeProperty = DTLinkAttribute;
        label.strikeOutAttributeProperty = NSStrikethroughStyleAttributeName;
        label.backgroundColorAttributeProperty = NSBackgroundColorAttributeName;

        label.delegate = self;
        [self addSubview:label];
	}
	return label;
}

-(NSURL *)checkLinkAttributeForString:(NSAttributedString*)theString atPoint:(CGPoint)p
{
    if ([label.links count] == 0) return nil;
    NSTextCheckingResult* result = [label linkAtPoint:p];

    if (result) {
        return result.URL;
    }
    return nil;
}

- (NSInteger)characterIndexAtPoint:(CGPoint)p;
{
    return [label characterIndexAtPoint:p];
}

- (id)accessibilityElement
{
	return [self label];
}

- (void)setAttributedTextViewContent {
    if (!configurationSet) {
        needsSetText = YES;
        return; // lazy init
    }
    if (![NSThread isMainThread])
    {
        TiThreadPerformOnMainThread(^{
            [self setAttributedTextViewContent];
        }, YES);
        return;
    }
    id content = [(TiUILabelProxy*)[self proxy] getLabelContent];
    if ([content isKindOfClass:[NSAttributedString class]]){
        
    }
    [self transitionToText:content];
}


- (TiLabel*) cloneView:(TiLabel*)source {
    NSData *archivedViewData = [NSKeyedArchiver archivedDataWithRootObject: source];
    TiLabel* clone = [NSKeyedUnarchiver unarchiveObjectWithData:archivedViewData];
    
    //seems to be duplicated < ios7
    clone.font = source.font;
    clone.textColor = source.textColor;
    clone.highlightedTextColor = source.highlightedTextColor;
    //
    
    clone.touchDelegate = source.touchDelegate;
    clone.delegate = source.delegate;
    [clone setLinks:source.links];
    return clone;
}

-(void) transitionToText:(id)text
{
    TiTransition* transition = [TiTransitionHelper transitionFromArg:self.transition containerView:self];
    if (transition != nil) {
        TiLabel *oldView = [self label];
        TiLabel *newView = [self cloneView:oldView];
        newView.text = text;
        [TiTransitionHelper transitionfromView:oldView toView:newView insideView:self withTransition:transition prepareBlock:^{
        } completionBlock:^{
            [oldView release];
        }];
        label = [newView retain];
	}
    else {
        [[self label] setText:text];
    }
}

-(void)setHighlighted:(BOOL)newValue animated:(BOOL)animated
{
    [super setHighlighted:newValue animated:animated];
    [[self label] setHighlighted:newValue];
}

- (void)didMoveToSuperview
{
	[self setHighlighted:NO];
	[super didMoveToSuperview];
}

- (void)didMoveToWindow
{
    /*
     * See above
     */
    [self setHighlighted:NO];
    [super didMoveToWindow];
}

-(BOOL)isHighlighted
{
    return [[self label] isHighlighted];
}

-(void)setExclusiveTouch:(BOOL)value
{
    [super setExclusiveTouch:value];
	[[self label] setExclusiveTouch:value];
}

#pragma mark Public APIs

-(void)setCustomUserInteractionEnabled:(BOOL)value
{
    [super setCustomUserInteractionEnabled:value];
	[[self label] setEnabled:[self interactionEnabled]];
}

-(void)setVerticalAlign_:(id)value
{
    UIControlContentVerticalAlignment verticalAlign = [TiUtils contentVerticalAlignmentValue:value];
    [[self label] setVerticalAlignment:(TTTAttributedLabelVerticalAlignment)verticalAlign];
}
-(void)setAutoLink_:(id)value
{
    [[self label] setDataDetectorTypes:[TiUtils intValue:value]];
    //we need to update the text
    [self setAttributedTextViewContent];
}

-(void)setDisableLinkStyle_:(id)value {
    BOOL currentlyDisabled = [self label].linkAttributes == nil;
    BOOL disable = [TiUtils boolValue:value def:NO];
    if (disable == currentlyDisabled) return;
    if (disable) {
        [self label].linkAttributes = nil;
        [self label].activeLinkAttributes = nil;
        [self label].inactiveLinkAttributes = nil;
    }
    else {
        [[self label] initLinksStyle];
    }
}

-(void)setColor_:(id)color
{
	UIColor * newColor = [[TiUtils colorValue:color] _color];
    if (newColor == nil)
        newColor = [UIColor darkTextColor];
	[[self label] setTextColor:newColor];
}

-(void)setText_:(id)value
{
    needsSetText = YES;
}

-(void)setHtml_:(id)value
{
    needsSetText = YES;
}

-(void)setHighlightedColor_:(id)color
{
	UIColor * newColor = [[TiUtils colorValue:color] _color];
	[[self label] setHighlightedTextColor:(newColor != nil)?newColor:[UIColor lightTextColor]];
}

-(void)setSelectedColor_:(id)color
{
	UIColor * newColor = [[TiUtils colorValue:color] _color];
	[[self label] setHighlightedTextColor:(newColor != nil)?newColor:[UIColor lightTextColor]];
}

-(void)setDisabledColor_:(id)color
{
	UIColor * newColor = [[TiUtils colorValue:color] _color];
	[[self label] setDisabledColor:newColor];
}

-(void)setFont_:(id)fontValue
{
    UIFont * font;
    if (fontValue!=nil)
    {
        font = [[TiUtils fontValue:fontValue] font];
    }
    else
    {
        font = [UIFont systemFontOfSize:[UIFont labelFontSize]];
    }
	[[self label] setFont:font];
}

-(void)setMinimumFontSize_:(id)size
{
    CGFloat newSize = [TiUtils floatValue:size];
    if (newSize < 4) { // Beholden to 'most minimum' font size
        [[self label] setAdjustsFontSizeToFitWidth:NO];
        [[self label] setMinimumScaleFactor:0.0];
    }
    else {
        [[self label] setAdjustsFontSizeToFitWidth:YES];
        
        [[self label] setMinimumScaleFactor:(newSize / [self label].font.pointSize)];
    }
    [self updateNumberLines];   
}

#if defined (USE_TI_UIIOSATTRIBUTEDSTRING)
-(void)setAttributedString_:(id)arg
{
    ENSURE_SINGLE_ARG(arg, TiUIAttributedStringProxy);
    [[self label] setAttributedText:[arg attributedString]];
    [(TiViewProxy *)[self proxy] contentsWillChange];
}
#endif



-(void)setTextAlign_:(id)alignment
{
	[[self label] setTextAlignment:[TiUtils textAlignmentValue:alignment]];
}

-(void)setShadowColor_:(id)color
{
	if (color==nil)
	{
		[[[self label] layer]setShadowColor:nil];
	}
	else
	{
		color = [TiUtils colorValue:color];
		[[self label] setShadowColor:[color _color]];
	}
}

-(void)setShadowRadius_:(id)arg
{
    [[self label] setShadowRadius:[TiUtils floatValue:arg]];
}
-(void)setShadowOffset_:(id)value
{
	CGPoint p = [TiUtils pointValue:value];
	CGSize size = {p.x,p.y};
	[[self label] setShadowOffset:size];
}

-(void)setPadding:(UIEdgeInsets)inset
{
    [self label].textInsets = inset;
}

-(void) updateNumberLines
{
    if ([[self label] minimumScaleFactor] != 0)
    {
        [[self label] setNumberOfLines:1];
    }
    else if ([[self proxy] valueForKey:@"maxLines"])
        [[self label] setNumberOfLines:([[[self proxy] valueForKey:@"maxLines"] integerValue])];
    else
    {
        BOOL shouldWordWrap = [TiUtils boolValue:[[self proxy] valueForKey:@"wordWrap"] def:YES];
        if (shouldWordWrap)
        {
            [[self label] setNumberOfLines:0];
        }
        else
        {
            [[self label] setNumberOfLines:1];
        }
    }
}

-(void)setWordWrap_:(id)value
{
    [self updateNumberLines];
}

-(void)setMaxLines_:(id)value
{
	[self updateNumberLines];
}

-(void)setEllipsize_:(id)value
{
    [[self label] setLineBreakMode:[TiUtils intValue:value]];
}


-(void)setMultiLineEllipsize_:(id)value
{
    NSInteger multilineBreakMode = [TiUtils intValue:value];
    if (multilineBreakMode != NSLineBreakByWordWrapping)
    {
        [[self label] setLineBreakMode:NSLineBreakByWordWrapping];
    }
}

-(void)setTransition_:(id)arg
{
    ENSURE_SINGLE_ARG_OR_NIL(arg, NSDictionary)
    self.transition = arg;
}

#pragma mark -
#pragma mark DTAttributedTextContentViewDelegate

- (void)attributedLabel:(TTTAttributedLabel *)label
   didSelectLinkWithURL:(NSURL *)url
{
    if ([(TiViewProxy*)[self proxy] _hasListeners:@"link" checkParent:NO]) {
        NSDictionary *eventDict = [NSDictionary dictionaryWithObjectsAndKeys:
                                   url, @"url",
                                   nil];
        [[self proxy] fireEvent:@"link" withObject:eventDict propagate:NO checkForListener:NO];
    }
    else [[UIApplication sharedApplication] openURL:url];
}

- (void)attributedLabel:(TTTAttributedLabel *)label
didSelectLinkWithAddress:(NSDictionary *)addressComponents
{
    NSMutableString* address = [NSMutableString string];
    NSString* temp = nil;
    if((temp = [addressComponents objectForKey:NSTextCheckingStreetKey]))
        [address appendString:temp];
    if((temp = [addressComponents objectForKey:NSTextCheckingCityKey]))
        [address appendString:[NSString stringWithFormat:@"%@%@", ([address length] > 0) ? @", " : @"", temp]];
    if((temp = [addressComponents objectForKey:NSTextCheckingStateKey]))
        [address appendString:[NSString stringWithFormat:@"%@%@", ([address length] > 0) ? @", " : @"", temp]];
    if((temp = [addressComponents objectForKey:NSTextCheckingZIPKey]))
        [address appendString:[NSString stringWithFormat:@" %@", temp]];
    if((temp = [addressComponents objectForKey:NSTextCheckingCountryKey]))
        [address appendString:[NSString stringWithFormat:@"%@%@", ([address length] > 0) ? @", " : @"", temp]];
    NSString* urlString = [NSString stringWithFormat:@"http://maps.google.com/maps?q=%@", [address stringByAddingPercentEscapesUsingEncoding:NSUTF8StringEncoding]];
    NSDictionary *eventDict = [NSDictionary dictionaryWithObjectsAndKeys:
                               urlString, @"url",
                               nil];
    if ([(TiViewProxy*)[self proxy] _hasListeners:@"link" checkParent:NO]) {

        [[self proxy] fireEvent:@"link" withObject:eventDict propagate:NO checkForListener:NO];
    }
    else [[UIApplication sharedApplication] openURL:[NSURL URLWithString:urlString]];
}

- (void)attributedLabel:(TTTAttributedLabel *)label
didSelectLinkWithPhoneNumber:(NSString *)phoneNumber
{
    NSURL* url = [NSURL URLWithString:[NSString stringWithFormat:@"tel://%@", phoneNumber]];
    if ([(TiViewProxy*)[self proxy] _hasListeners:@"link" checkParent:NO]) {
        NSDictionary *eventDict = [NSDictionary dictionaryWithObjectsAndKeys:
                                   url, @"url",
                                   nil];
        [[self proxy] fireEvent:@"link" withObject:eventDict propagate:NO checkForListener:NO];
    }
    else [[UIApplication sharedApplication] openURL:url];
}

//-(void)setReusing:(BOOL)value
//{
//    _reusing = value;
//}

-(NSDictionary*)dictionaryFromTouch:(UITouch*)touch
{
    NSDictionary* event = [super dictionaryFromTouch:touch];
    NSAttributedString* attString = label.attributedText;
    if (attString != nil) {
        CGPoint localPoint = [touch locationInView:label];
        NSURL* url = [self checkLinkAttributeForString:attString atPoint:localPoint];
        if (url){
            event = [NSMutableDictionary dictionaryWithDictionary:event];
            [(NSMutableDictionary*)event setObject:url forKey:@"link"];
        }
    }
    return event;
}

-(NSMutableDictionary*)dictionaryFromGesture:(UIGestureRecognizer*)gesture
{
    NSMutableDictionary* event = [super dictionaryFromGesture:gesture];
    
    NSAttributedString* attString = label.attributedText;
    if (attString != nil) {
        CGPoint localPoint = [gesture locationInView:label];
        NSURL* url = [self checkLinkAttributeForString:attString atPoint:localPoint];
        if (url){
            [event setObject:url forKey:@"link"];
        }
    }
    return event;
}
@end

#endif
