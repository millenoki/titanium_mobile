/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2017-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#if defined(USE_TI_UIIOSTOOLBAR) || defined(USE_TI_UITOOLBAR)

#import "TiUIToolbarProxy.h"
#import "TiUIToolbar.h"

@implementation TiUIToolbarProxy

- (NSArray *)keySequence
{
    static NSArray *keySequence = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        keySequence = [[[super keySequence] arrayByAddingObjectsFromArray:@[ @"barColor" ]] retain];
        ;
    });
    return keySequence;
}

//USE_VIEW_FOR_VERIFY_HEIGHT
//
//- (UIViewAutoresizing)verifyAutoresizing:(UIViewAutoresizing)suggestedResizing
//{
//  return suggestedResizing & ~UIViewAutoresizingFlexibleHeight;
//}

- (id)_initWithPageContext:(id<TiEvaluator>)context_ args:(NSArray *)args apiName:(NSString *)apiName
{
  if (self = [super _initWithPageContext:context_ args:args]) {
    _apiName = [apiName retain];
  }

  return self;
}

- (TiUIView *)newView
{
    return [[TiUIToolbar alloc] init];
}

- (void)dealloc
{
  RELEASE_TO_NIL(_apiName);
  [super dealloc];
}

- (NSString *)apiName
{
  return _apiName;
}

- (UIToolbar *)toolbar
{
  TiUIToolbar *theview = (TiUIToolbar *)[self view];
  return [theview toolBar];
}

- (TiDimension)defaultAutoHeightBehavior:(id)unused
{
  return TiDimensionAutoSize;
}

USE_VIEW_FOR_CONTENT_SIZE


@end

#endif
