/**
 * Akylas
 * Copyright (c) 2009-2010 by Akylas. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#ifdef USE_TI_UICOLLECTIONVIEW

#import "TiUICollectionItem.h"
#import "TiBase.h"
#import "TiUtils.h"
#import "TiViewProxy.h"
#import "Webcolor.h"
#import "TiSelectableBackgroundLayer.h"
#import "TiCellBackgroundView.h"
#import "ImageLoader.h"


#define GROUP_ROUND_RADIUS 6

@implementation TiUICollectionItem {
	TiUICollectionItemProxy *_proxy;
	NSInteger _templateStyle;
	NSDictionary *_dataItem;
    TiUIView* _viewHolder;
    TiCellBackgroundView* _bgSelectedView;
    TiCellBackgroundView* _bgView;
    TiCap imageCap;
    BOOL _needsLayout;
    BOOL configurationSet;
    BOOL _unHighlightOnSelect;
}

@synthesize templateStyle = _templateStyle;
@synthesize proxy = _proxy;
@synthesize dataItem = _dataItem;
@synthesize viewHolder = _viewHolder;

DEFINE_EXCEPTIONS

- (id)initWithProxy:(TiUICollectionItemProxy *)proxy
{
		_templateStyle = TiUICollectionItemTemplateStyleCustom;
		_proxy = [proxy retain];
        _viewHolder = [[TiUIView alloc] initWithFrame:self.contentView.bounds];
        _viewHolder.proxy = _proxy;
        _viewHolder.shouldHandleSelection = NO;
    [_viewHolder setBackgroundColor_:[UIColor redColor]];
        [_viewHolder setAutoresizingMask:UIViewAutoresizingFlexibleWidth|UIViewAutoresizingFlexibleHeight];
        [_viewHolder setClipsToBounds: YES];
        [_viewHolder.layer setMasksToBounds: YES];
        //    [_viewHolder selectableLayer].animateTransition = YES;
        [self.contentView addSubview:_viewHolder];
        [self initialize];
}

-(void) initialize
{
//    self.contentView.backgroundColor = [UIColor clearColor];
//    if ([TiUtils isIOS7OrGreater]) {
//        self.backgroundColor = [UIColor clearColor];
//    }
//    self.contentView.opaque = NO;
    _unHighlightOnSelect = YES;
    
    _proxy.listItem = self;
    _proxy.modelDelegate = [self autorelease]; //without the autorelease we got a memory leak
    configurationSet = NO;
    [_proxy dirtyItAll];
}

-(void)configurationStart
{
    configurationSet = NO;
    [_viewHolder configurationStart];
    if (_bgSelectedView) {
        [_bgSelectedView selectableLayer].readyToCreateDrawables = configurationSet;
    }
    if (_bgView) {
        [_bgView selectableLayer].readyToCreateDrawables = configurationSet;
    }
}

-(void)configurationSet
{
	// can be used to trigger things after all properties are set
    configurationSet = YES;
    [_viewHolder configurationSet];
    if (_bgSelectedView) {
        [_bgSelectedView selectableLayer].readyToCreateDrawables = configurationSet;
    }
    if (_bgView) {
        [_bgView selectableLayer].readyToCreateDrawables = configurationSet;
    }
}

//TIMOB-17373. Workaround for separators disappearing on iOS7 and above
//- (void) ensureVisibleSelectorWithTableView:(UICollectionView*)tableView
//{
//    if (![TiUtils isIOS7OrGreater] || [self selectedOrHighlighted]) {
//        return;
//    }
//    UICollectionView* attachedTableView = tableView;
//    UIView* superView = [self superview];
//    while (attachedTableView == nil && superView != nil) {
//        if ([superView isKindOfClass:[UICollectionView class]]) {
//            attachedTableView = (UICollectionView*)superView;
//        }
//        superView = [superView superview];
//    }
//    
//    if (attachedTableView != nil && attachedTableView.separatorStyle != UITableViewCellSeparatorStyleNone) {
//        for (UIView *subview in self.contentView.superview.subviews) {
//            if ([NSStringFromClass(subview.class) hasSuffix:@"SeparatorView"]) {
//                subview.hidden = NO;
//            }
//        }
//    }
//}

-(TiCellBackgroundView*)getOrCreateSelectedBackgroundView
{
    if (_bgSelectedView != nil) {
        return _bgSelectedView;
    }
    
    self.selectedBackgroundView = [[[TiCellBackgroundView alloc] initWithFrame:CGRectZero] autorelease];
    _bgSelectedView = (TiCellBackgroundView*)self.selectedBackgroundView;
    [_bgSelectedView selectableLayer].animateTransition = YES;
    _bgSelectedView.alpha = self.contentView.alpha;

    [_bgSelectedView selectableLayer].readyToCreateDrawables = configurationSet;
    return _bgSelectedView;
}

-(void)setBackgroundView:(UIView*)view
{
    
    [super setBackgroundView:view];
    if (view == nil) {
        RELEASE_TO_NIL(_bgView);
    }
    if (_bgView && ![view isKindOfClass:[TiCellBackgroundView class]]){
        [_bgView setFrame:view.bounds];
        [view addSubview:_bgView];
    }
}

-(TiCellBackgroundView*)getOrCreateBackgroundView
{
    if (_bgView == nil) {
        _bgView = [[TiCellBackgroundView alloc] initWithFrame:CGRectZero];
        if ([TiUtils isIOS7OrGreater]) {
            self.backgroundView = _bgView;
        }
        else if(self.backgroundView !=nil){
            [_bgView setFrame:self.backgroundView.bounds];
            [self.backgroundView addSubview:_bgView];
        }
        _bgView.alpha = self.contentView.alpha;
    }

    return _bgView;
}

-(void) setBackgroundGradient_:(id)newGradientDict
{
    TiGradient * newGradient = [TiGradient gradientFromObject:newGradientDict proxy:self.proxy];
    [[self getOrCreateBackgroundView].selectableLayer setGradient:newGradient forState:UIControlStateNormal];
}

-(void) setBackgroundSelectedGradient_:(id)newGradientDict
{
    TiGradient * newGradient = [TiGradient gradientFromObject:newGradientDict proxy:self.proxy];
    [[self getOrCreateSelectedBackgroundView].selectableLayer setGradient:newGradient forState:UIControlStateNormal];
}

-(void) setBackgroundColor_:(id)color
{
    UIColor* uicolor;
	if ([color isKindOfClass:[UIColor class]])
	{
        uicolor = (UIColor*)color;
	}
	else
	{
		uicolor = [[TiUtils colorValue:color] _color];
	}
    [[self getOrCreateBackgroundView].selectableLayer setColor:uicolor forState:UIControlStateNormal];
    
}

-(void) setBackgroundSelectedColor_:(id)color
{
    UIColor* uiColor = [TiUtils colorValue:color].color;
    [[self getOrCreateSelectedBackgroundView].selectableLayer setColor:uiColor forState:UIControlStateNormal];
}


-(void)setImageCap_:(id)arg
{
    imageCap = [TiUtils capValue:arg def:TiCapUndefined];
}

-(UIImage*)loadImage:(id)arg
{
    if (arg==nil) return nil;
    UIImage *image = nil;
	if (TiCapIsUndefined(imageCap)) {
        image =  [TiUtils loadBackgroundImage:arg forProxy:_proxy];
    }
    else {
        image =  [TiUtils loadBackgroundImage:arg forProxy:_proxy withCap:imageCap];
    }
	return image;
}

-(void) setBackgroundImage_:(id)image
{
    UIImage* bgImage = [self loadImage:image];
    [[self getOrCreateBackgroundView].selectableLayer setImage:bgImage forState:UIControlStateNormal];
}

-(void) setBackgroundSelectedImage_:(id)image
{
    UIImage* bgImage = [self loadImage:image];
    [[self getOrCreateSelectedBackgroundView].selectableLayer setImage:bgImage forState:UIControlStateNormal];
}

-(void)setBackgroundOpacity_:(id)opacity
{
    [self getOrCreateBackgroundView].selectableLayer.opacity = [TiUtils floatValue:opacity def:1.0f];
}

-(void)setOpacity_:(id)opacity
{
 	ENSURE_UI_THREAD_1_ARG(opacity);
    
	self.contentView.alpha = [TiUtils floatValue:opacity];
    if (_bgView)
    {
        _bgView.alpha = self.contentView.alpha;
    }
    if (_bgSelectedView)
    {
        _bgSelectedView.alpha = self.contentView.alpha;
    }
}


- (void)dealloc
{
	[_proxy detachView];
	[_proxy cleanup];
	[_proxy deregisterProxy:[_proxy pageContext]];
	_proxy.listItem = nil;
	_proxy.modelDelegate = nil;
    _viewHolder.proxy = nil;
    
    RELEASE_TO_NIL(_viewHolder)
    RELEASE_TO_NIL(_dataItem)
    RELEASE_TO_NIL(_bgView)
    RELEASE_TO_NIL(_proxy)
	[super dealloc];
}

- (void)prepareForReuse
{
//	RELEASE_TO_NIL(_dataItem);
    [_proxy prepareForReuse];
	[super prepareForReuse];
}

static NSArray* handledKeys;
-(NSArray *)handledKeys
{
    if (handledKeys == nil)
    {
        handledKeys = [@[@"selectionStyle", @"title", @"accessoryType", @"subtitle", @"color", @"image", @"font"
                         , @"unHighlightOnSelect"] retain];
    }
    return handledKeys;
}

-(void)propertyChanged:(NSString*)key oldValue:(id)oldValue newValue:(id)newValue proxy:(TiProxy*)proxy_
{
    if (_templateStyle == TiUICollectionItemTemplateStyleCustom && [[self handledKeys] indexOfObject:key] == NSNotFound)
    {
        DoProxyDelegateChangedValuesWithProxy(_viewHolder, key, oldValue, newValue, proxy_);
    } else {
        DoProxyDelegateChangedValuesWithProxy(self, key, oldValue, newValue, proxy_);
    }
}


#pragma mark - Background Support
-(BOOL) selectedOrHighlighted
{
	return [self isSelected] || [self isHighlighted];
}

-(void)unHighlight:(NSArray*)views
{
    for (UIView *subview in views) {
		if ([(id)subview respondsToSelector:@selector(setHighlighted:)])
        {
            [(id)subview setHighlighted:NO];
        }
        else {
            NSArray *subviews = [subview subviews];
            if ([subviews count] > 0)
                [self unHighlight:subviews];
        }
        // Get the subviews of the view
    
    }
}

-(void)unHighlight
{
    if (_viewHolder)
    {
        [self unHighlight:[_viewHolder subviews]];
    }
    else {
        [self unHighlight:[self subviews]];
    }
}

-(void)setSelected:(BOOL)yn
{
    [super setSelected:yn];
    if ([self.proxy shouldHighlight]) {
        [_viewHolder setSelected:yn animated:NO];
    }
    if (_unHighlightOnSelect && yn)[self unHighlight];
}

-(void)setHighlighted:(BOOL)yn
{
    [super setHighlighted:yn];
    if ([self.proxy shouldHighlight]) {
        [_viewHolder setHighlighted:yn animated:NO];
    }
    if (_unHighlightOnSelect && yn)[self unHighlight];
}

- (BOOL)canApplyDataItem:(NSDictionary *)otherItem;
{
	id template = [_dataItem objectForKey:@"template"];
	id otherTemplate = [otherItem objectForKey:@"template"];
	BOOL same = (template == otherTemplate) || [template isEqual:otherTemplate];
	return same;
}

- (void)setDataItem:(NSDictionary *)dataItem
{
    if (dataItem == (_dataItem)) return;
    if (_dataItem) {
        RELEASE_TO_NIL(_dataItem)
        [(TiViewProxy*)self.proxy dirtyItAll];
    }
	_dataItem = [dataItem retain];
    [_proxy setDataItem:_dataItem];
}

-(void)setUnHighlightOnSelect_:(id)newValue
{
    _unHighlightOnSelect = [TiUtils boolValue:newValue def:YES];
}

- (BOOL) hasSwipeButtons {
    return [self.proxy valueForKey:@"leftSwipeButtons"] || [self.proxy valueForKey:@"rightSwipeButtons"];
}
-(void)setFrame:(CGRect)frame
{
	// this happens when a controller resizes its view
    
    if (!CGRectIsEmpty(frame))
	{
        CGRect currentbounds = [_viewHolder bounds];
        CGRect newBounds = CGRectMake(0, 0, frame.size.width, frame.size.height);
        if (!CGRectEqualToRect(newBounds, currentbounds))
        {
            [(TiViewProxy*)self.proxy dirtyItAll];
        }
	}
    [super setFrame:frame];
	
}

- (void)layoutSubviews
{
    [super layoutSubviews];
    if (_templateStyle == TiUICollectionItemTemplateStyleCustom) {
        TiViewAnimationStep* anim = [_proxy runningAnimation];
        if (anim)
        {
            [_proxy setRunningAnimationRecursive:anim];
            [_proxy refreshViewIfNeeded:YES];
            [_proxy setRunningAnimationRecursive:nil];
        }
        else {
            [_proxy refreshViewIfNeeded:YES];
        }
    }
}


//override to get the correct backgroundColor
//-(UIColor *) backgroundColorForSwipe
//{
//    if (self.swipeBackgroundColor) {
//        return self.swipeBackgroundColor; //user defined color
//    }
//    return [[_bgView selectableLayer] getColorForState:UIControlStateNormal];
//}

@end

#endif
