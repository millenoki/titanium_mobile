/**
 * Akylas
 * Copyright (c) 2009-2010 by Akylas. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#ifdef USE_TI_UICOLLECTIONVIEW

#define DEFAULT_TEMPLATE_STYLE @"TiUICollectionView__internal_template"
#define HEADER_VIEW_STYLE @"TiUICollectionView__internal_headerView"
#import "MGSwipeCollectionViewCell.h"
#import "TiScrollingView.h"
#import "TiUICollectionViewFlowLayout.h"
#import "TiUICollectionViewProxy.h"
@class TiCollectionView;

#if IS_XCODE_8
// Add support for iOS 10 table-view prefetching
@interface TiUICollectionView : TiScrollingView <MGSwipeCollectionViewCellDelegate, UICollectionViewDelegate, UICollectionViewDataSource, UICollectionViewDataSourcePrefetching, UIGestureRecognizerDelegate, UISearchBarDelegate, UISearchResultsUpdating, UISearchControllerDelegate, TiScrolling, TiProxyObserver, TiUICollectionViewFlowLayoutDelegate>
#else
@interface TiUICollectionView : TiScrollingView <MGSwipeCollectionViewCellDelegate, UICollectionViewDelegate, UICollectionViewDataSource, UIGestureRecognizerDelegate, UISearchBarDelegate, UISearchResultsUpdating, UISearchControllerDelegate, TiScrolling, TiProxyObserver, TiUICollectionViewFlowLayoutDelegate>
#endif
{
  BOOL allowsSelection;
}

#pragma mark - Private APIs

@property (nonatomic, readonly) TiCollectionView *tableView;
@property (nonatomic, readonly) BOOL isSearchActive;

- (void)updateSearchResults:(id)unused;
- (void)setContentInsets_:(id)value withObject:(id)props;
- (void)deselectAll:(BOOL)animated;
- (void)updateIndicesForVisibleRows;
- (void)viewResignFocus;
- (void)viewGetFocus;

+ (UITableViewRowAnimation)animationStyleForProperties:(NSDictionary *)properties;
- (BOOL)shouldHighlightCurrentCollectionItem;
- (NSIndexPath *)nextIndexPath:(NSIndexPath *)indexPath;
- (void)selectItem:(NSIndexPath *)indexPath animated:(BOOL)animated;
- (void)deselectItem:(NSIndexPath *)indexPath animated:(BOOL)animated;

@end

#endif
