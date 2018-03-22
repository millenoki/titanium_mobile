/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2015 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#if defined(USE_TI_UITEXTWIDGET) || defined(USE_TI_UITEXTAREA) || defined(USE_TI_UITEXTFIELD)

#import "TiUITextWidgetProxy.h"
#import "TiUITextWidget.h"

#import "TiUtils.h"

@implementation TiUITextWidgetProxy {
  TiViewProxy *keyboardAccessoryProxy;
}
DEFINE_DEF_BOOL_PROP(suppressReturn, YES);
@synthesize suppressFocusEvents = _suppressFocusEvents;

- (void)windowWillClose
{
    [self blur:nil];

  if (keyboardAccessoryProxy) {
    [keyboardAccessoryProxy windowWillClose];
  }
  [super windowWillClose];
}

- (void)windowDidClose
{
  if (keyboardAccessoryProxy) {
    [keyboardAccessoryProxy windowDidClose];
  }
  [super windowDidClose];
}

- (void)dealloc
{
  if (keyboardAccessoryProxy) {
    [self forgetProxy:keyboardAccessoryProxy];
    RELEASE_TO_NIL(keyboardAccessoryProxy);
  }
  [super dealloc];
}

- (NSString *)apiName
{
  return @"Ti.UI.TextWidget";
}

- (NSNumber *)hasText:(id)unused
{
  if ([self viewAttached]) {
    __block BOOL viewHasText = NO;
    TiThreadPerformOnMainThread(^{
      viewHasText = [(TiUITextWidget *)[self view] hasText];
    },
        YES);
    return [NSNumber numberWithBool:viewHasText];
  } else {
    BOOL viewHasText = !NULL_OR_EMPTY([self valueForKey:@"value"]);
    return [NSNumber numberWithBool:viewHasText];
  }
}

- (void)setValue:(id)value
{
  [self noteValueChange:value];
  [self replaceValue:value forKey:@"value" notification:YES];
  }

- (void)noteValueChange:(NSString *)newValue
{
  BOOL needsChange = NO;
  ARE_DIFFERENT_NULL_OR_EMPTY([self valueForUndefinedKey:@"value"], newValue, needsChange)
  if (!needsChange)
    return;
    [self replaceValue:newValue forKey:@"value" notification:NO];
  if ([self isConfigurationSet]) {
    [self contentsWillChange];
    if ([self _hasListeners:@"change" checkParent:NO]) {
      [self fireEvent:@"change"
                withObject:@{
                  @"value" : newValue ? newValue : @""
  }
                 propagate:NO
          checkForListener:NO];
}
    //        TiThreadPerformOnMainThread(^{
    //            //Make sure the text widget is in view when editing.
    //            [(TiUITextWidget*)[self view] updateKeyboardStatus];
    //        }, NO);
  }
}

#pragma mark Toolbar

- (CGFloat)keyboardAccessoryHeight
{
  CGFloat result = 0;
#ifndef TI_USE_AUTOLAYOUT
  if (keyboardAccessoryProxy) {
    UIView *theView;
    if (keyboardAccessoryProxy.view) {
      [keyboardAccessoryProxy refreshView];
      theView = keyboardAccessoryProxy.view;
    } else {
      theView = [keyboardAccessoryProxy getAndPrepareViewForOpening:[TiUtils appFrame]];
  }
    result = theView.bounds.size.height;
  }
#endif
  return result;
}

- (void)setKeyboardToolbar:(id)value
{

  TiViewProxy *vp = (TiViewProxy *)[(TiUITextWidgetProxy *)self createChildFromObject:value];
  if (keyboardAccessoryProxy) {
    [keyboardAccessoryProxy windowDidClose];
    [keyboardAccessoryProxy setParentForBubbling:nil];
    [self forgetProxy:keyboardAccessoryProxy];
    RELEASE_TO_NIL(keyboardAccessoryProxy)
  }
  if (vp) {
    [vp setParentForBubbling:(TiParentingProxy *)self];
    vp.canBeResizedByFrame = YES;
    LayoutConstraint *constraint = [vp layoutProperties];
    if (TiDimensionIsUndefined(constraint->width)) {
      constraint->width = TiDimensionAutoFill;
}
    keyboardAccessoryProxy = [vp retain];
    }
  [self replaceValue:value forKey:@"keyboardToolbar" notification:YES];
  }

- (TiViewProxy *)keyboardAccessoryProxy;
{
  return keyboardAccessoryProxy;
    }

- (void)setKeyboardToolbar:(id)value
{
  // TODO: The entire codebase needs to be evaluated for the following:
  //
  // - Any property setter which potentially takes an array of proxies MUST ALWAYS have its
  // content evaluated to protect them. This is INCREDIBLY CRITICAL and almost certainly a major
  // source of memory bugs in Titanium iOS!!!
  //
  // - Any property setter which is active on the main thread only MAY NOT protect their object
  // correctly or in time (see the comment in -[KrollObject noteKeylessKrollObject:]).
  //
  // This may have to be done as part of TIMOB-6990 (convert KrollContext to serialized GCD)

  if ([value isKindOfClass:[NSArray class]]) {
    for (id item in value) {
      ENSURE_TYPE(item, TiProxy);
      [self rememberProxy:item];
    }
  }

  //Because views aren't lock-protected, ANY and all references, even checking if non-nil, should be done in the main thread.

  // TODO: ENSURE_UI_THREAD needs to be deprecated in favor of more effective and concicse mechanisms
  // which use the main thread only when necessary to reduce latency.
  ENSURE_UI_THREAD_1_ARG(value);
  [self replaceValue:value forKey:@"keyboardToolbar" notification:YES];

  if (value == nil) {
    for (TiProxy *proxy in keyboardToolbarItems) {
      [self forgetProxy:proxy];
    }
    RELEASE_TO_NIL(keyboardTiView);
    RELEASE_TO_NIL(keyboardToolbarItems);
    [keyboardUIToolbar setItems:nil];
    [(UITextField *)[(TiUITextWidget *)[self view] textWidgetView] setInputAccessoryView:nil];
    [[(TiUITextWidget *)[self view] textWidgetView] reloadInputViews];
    return;
  }

  if ([value isKindOfClass:[NSArray class]]) {
    RELEASE_TO_NIL(keyboardTiView);

    // TODO: Check for proxies
    [keyboardToolbarItems autorelease];
    for (TiProxy *proxy in keyboardToolbarItems) {
      [self forgetProxy:proxy];
    }

    keyboardToolbarItems = [value copy];
    if (keyboardUIToolbar != nil) {
      [self updateUIToolbar];
    }
    [[self keyboardAccessoryView] setBounds:CGRectMake(0, 0, 0, [self keyboardAccessoryHeight])];
    [(UITextField *)[(TiUITextWidget *)[self view] textWidgetView] setInputAccessoryView:[self keyboardAccessoryView]];
    [[(TiUITextWidget *)[self view] textWidgetView] reloadInputViews];
    return;
  }

  if ([value isKindOfClass:[TiViewProxy class]]) {
    TiUIView *valueView = [(TiViewProxy *)value view];
    if (valueView == keyboardTiView) { //Nothing to do here.
      return;
    }
    RELEASE_TO_NIL(keyboardTiView);
    for (TiProxy *proxy in keyboardToolbarItems) {
      [self forgetProxy:proxy];
    }
    RELEASE_TO_NIL(keyboardToolbarItems);
    [keyboardUIToolbar setItems:nil];

    keyboardTiView = [valueView retain];
    [keyboardTiView setBounds:CGRectMake(0, 0, 0, [self keyboardAccessoryHeight])];
    [(UITextField *)[(TiUITextWidget *)[self view] textWidgetView] setInputAccessoryView:keyboardTiView];
    [[(TiUITextWidget *)[self view] textWidgetView] reloadInputViews];
  }
}

- (UIView *)keyboardAccessoryView;
{
  if (keyboardAccessoryProxy) {
    return [keyboardAccessoryProxy getAndPrepareViewForOpening:[TiUtils appFrame]];
  }
  return nil;
}

#ifndef TI_USE_AUTOLAYOUT
- (TiDimension)defaultAutoWidthBehavior:(id)unused
{
  return TiDimensionAutoSize;
}
- (TiDimension)defaultAutoHeightBehavior:(id)unused
{
  return TiDimensionAutoSize;
}
#endif

- (TiParentingProxy *)parentForNextWidget
{
  if (IS_OF_CLASS(self.eventOverrideDelegate, TiParentingProxy)) {
    return (TiParentingProxy *)self.eventOverrideDelegate;
  }
  return [self parent];
}

- (BOOL)selectNextTextWidget
{
  TiParentingProxy *theParent = [self parentForNextWidget];
  TiProxy *firstChild = self;
  TiUITextWidgetProxy *nextTF = [theParent getNextChildrenOfClass:[TiUITextWidgetProxy class] afterChild:firstChild];
  while (theParent && !nextTF) {
    firstChild = theParent;
    theParent = [theParent parent];
    nextTF = [theParent getNextChildrenOfClass:[TiUITextWidgetProxy class] afterChild:firstChild];
  }
  if (nextTF) {
    return [[nextTF view] becomeFirstResponder];
  }
  return false;
}

- (NSDictionary *)selection
{
  if ([self viewAttached]) {
    __block NSDictionary *result = nil;
    TiThreadPerformOnMainThread(^{
      result = [[(TiUITextWidget *)[self view] selectedRange] retain];
    },
        YES);
    return [result autorelease];
  }
  return nil;
}

- (void)setSelection:(id)arg withObject:(id)property
{
  NSInteger start = [TiUtils intValue:arg def:-1];
  NSInteger end = [TiUtils intValue:property def:-1];
  NSString *curValue = [TiUtils stringValue:[self valueForKey:@"value"]];
  NSInteger textLength = [curValue length];
  if ((start < 0) || (start > textLength) || (end < 0) || (end > textLength)) {
    DebugLog(@"Invalid range for text selection. Ignoring.");
    return;
  }
  TiThreadPerformOnMainThread(^{
    [(TiUITextWidget *)[self view] setSelectionFrom:arg to:property];
  },
      NO);
}
#ifndef TI_USE_AUTOLAYOUT
USE_VIEW_FOR_CONTENT_SIZE
#endif

@end

#endif
