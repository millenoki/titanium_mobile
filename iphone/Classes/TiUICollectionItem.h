/**
 * Akylas
 * Copyright (c) 2009-2010 by Akylas. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#ifdef USE_TI_UICOLLECTIONVIEW

#import "MGSwipeCollectionViewCell.h"
#import "TiUICollectionItemProxy.h"
#import "TiUICollectionView.h"
#import <UIKit/UIKit.h>

typedef enum {
  TiUICollectionItemTemplateStyleCustom = -1,
  TiUICollectionItemTemplateStyleListView = 0
} TiUICollectionItemTemplateStyle;

typedef enum {
  TiGroupedCollectionItemPositionTop,
  TiGroupedCollectionItemPositionMiddle,
  TiGroupedCollectionItemPositionBottom,
  TiGroupedCollectionItemPositionSingleLine
} TiGroupedCollectionItemPosition;

@interface TiUICollectionItem : MGSwipeCollectionViewCell <TiProxyDelegate> {
}

@property (nonatomic, readonly) NSInteger templateStyle;
@property (nonatomic, readonly) TiUICollectionItemProxy *proxy;
@property (nonatomic, readonly) TiUIView *viewHolder;
@property (nonatomic, readwrite, retain) NSDictionary *dataItem;
@property (nonatomic) BOOL touchPassThrough;

- (id)prepareWithStyle:(TiUICollectionItemTemplateStyle)style proxy:(TiUICollectionItemProxy *)proxy;

- (BOOL)canApplyDataItem:(NSDictionary *)otherItem;
- (void)configurationStart;
- (void)configurationSet;
- (BOOL)canSwipeLeft;
- (BOOL)canSwipeRight;
@end

#endif
