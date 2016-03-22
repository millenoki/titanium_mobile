/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UITEXTAREA

#import "TiUITextAreaProxy.h"
#import "TiUITextArea.h"

@implementation TiUITextAreaProxy
{
    UIEdgeInsets _padding;
}


#pragma mark Defaults

DEFINE_DEF_PROP(value,@"");
DEFINE_DEF_PROP(scrollsToTop,@YES);
DEFINE_DEF_INT_PROP(maxLength,-1);

-(NSString*)apiName
{
    return @"Ti.UI.TextArea";
}

-(id)init
{
    if (self = [super init]) {
        _padding = UIEdgeInsetsZero;
    }
    return self;
}

-(void)setPadding:(id)value
{
    _padding = [TiUtils insetValue:value];
    if (view != nil)
        [(TiUITextArea*)view setPadding:_padding];
    [self contentsWillChange];
    [self replaceValue:value forKey:@"padding" notification:NO];
}

-(id)padding {
    return [self valueForUndefinedKey:@"padding"];
}

-(void)configurationSet
{
    [super configurationSet];
    [(TiUITextArea*)view setPadding:_padding];
}

-(CGSize)contentSizeForSize:(CGSize)size
{
    if (view != nil)
        return [(TiUITextArea*)view contentSizeForSize:size];
    else
    {
        return [TiUtils sizeForString:[TiUtils stringValue:[self valueForKey:@"value"]] forSize:size options:self padding:_padding];
    }
    return CGSizeZero;
}

-(void)didRotateFromInterfaceOrientation:(UIInterfaceOrientation)fromInterfaceOrientation
{
    [super didRotateFromInterfaceOrientation:fromInterfaceOrientation];
    [(TiUITextArea*)view updateCaretPosition];
}

@end

#endif