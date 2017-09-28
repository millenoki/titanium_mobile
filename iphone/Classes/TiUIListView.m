/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UILISTVIEW
#import "TiUIListView.h"
#import "ImageLoader.h"
#import "TiApp.h"
#import "TiProxyTemplate.h"
#import "TiRootViewController.h"
#import "TiUILabelProxy.h"
#import "TiUILabelProxy.h"
#import "TiUIListItem.h"
#import "TiUIListItemProxy.h"
#import "TiUISearchBarProxy.h"
#import "TiWindowProxy.h"
#import "WrapperViewProxy.h"
#ifdef USE_TI_UIREFRESHCONTROL
#import "TiUIRefreshControlProxy.h"
#endif
#import "TiApp.h"
#import "TiUIHelper.h"

#define GROUPED_MARGIN_WIDTH 18.0

@interface TiUIView (eventHandler)
;
- (void)handleListenerRemovedWithEvent:(NSString *)event;
- (void)handleListenerAddedWithEvent:(NSString *)event;
@end

@interface TiUIListView ()
@property (nonatomic, readonly) TiUIListViewProxy *listViewProxy;
@property (nonatomic, copy, readwrite) NSString *searchString;
@property (nonatomic, strong) NSMutableSet *shownIndexes;
@end

@interface TiUIListSectionProxy ()
- (TiViewProxy *)currentViewForLocation:(NSString *)location inListView:(TiUIListView *)listView;
@end

@implementation TiUIListView {
  TiTableView *_tableView;
  TiTableView *_searchTableView;
  id _defaultItemTemplate;
  BOOL hideOnSearch;
  BOOL searchViewAnimating;
  BOOL searchIgnoreExactMatch;

  TiDimension _rowHeight;
  TiDimension _minRowHeight;
  TiDimension _maxRowHeight;

  UISearchController *searchController;
  UITableViewController *resultViewController;

  NSMutableArray *sectionTitles;
  NSMutableArray *sectionIndices;
  NSMutableArray *filteredTitles;
  NSMutableArray *filteredIndices;

  BOOL pruneSections;

  BOOL allowsSelection;
  BOOL allowsMultipleSelectionDuringEditing;

  BOOL caseInsensitiveSearch;
  NSString *_searchString;
  BOOL searchActive;
  BOOL searchHidden;
  BOOL keepSectionsInSearch;
  NSMutableArray *_searchResults;
  UIEdgeInsets _defaultSeparatorInsets;
  UIEdgeInsets _rowSeparatorInsets;

  BOOL _scrollSuspendImageLoading;
  BOOL hasOnDisplayCell;
  BOOL _updateInsetWithKeyboard;

  NSInteger _currentSection;

  BOOL _canSwipeCells;
  MGSwipeTableCell *_currentSwipeCell;
  id _appearAnimation;
  BOOL _useAppearAnimation;

  BOOL _dimsBackgroundDuringPresentation;
}

static NSDictionary *replaceKeysForRow;
- (NSDictionary *)replaceKeysForRow
{
  if (replaceKeysForRow == nil) {
    replaceKeysForRow = [@{ @"rowHeight" : @"height" } retain];
  }
  return replaceKeysForRow;
}

- (NSString *)replacedKeyForKey:(NSString *)key
{
  NSString *result = [[self replaceKeysForRow] objectForKey:key];
  return result ? result : key;
}

- (WrapperViewProxy *)wrapperProxyWithVerticalLayout:(BOOL)vertical
{
  WrapperViewProxy *theProxy = [[WrapperViewProxy alloc] initWithVerticalLayout:vertical];
  [theProxy setParent:(TiParentingProxy *)self.proxy];
  return [theProxy autorelease];
}

#ifdef TI_USE_AUTOLAYOUT
- (void)initializeTiLayoutView
{
  [super initializeTiLayoutView];
  [self setDefaultHeight:TiDimensionAutoFill];
  [self setDefaultWidth:TiDimensionAutoFill];
}
#endif

- (id)init
{
  self = [super init];
  if (self) {
    _defaultItemTemplate = [[NSNumber numberWithUnsignedInteger:UITableViewCellStyleSubtitle] retain];
    allowsSelection = YES;
    allowsMultipleSelectionDuringEditing = NO;
    _defaultSeparatorInsets = UIEdgeInsetsZero;
    _scrollSuspendImageLoading = NO;
    hideOnSearch = NO;
    searchViewAnimating = NO;
    _updateInsetWithKeyboard = NO;
    _currentSection = -1;
    _canSwipeCells = YES;
    caseInsensitiveSearch = YES;
    _appearAnimation = nil;
    _useAppearAnimation = NO;
    searchIgnoreExactMatch = NO;
    _dimsBackgroundDuringPresentation = YES;
  }
  return self;
}

- (void)dealloc
{
  [[NSNotificationCenter defaultCenter] removeObserver:self];
  _tableView.delegate = nil;
  _tableView.dataSource = nil;

  _searchTableView.delegate = nil;
  _searchTableView.dataSource = nil;
  RELEASE_TO_NIL(_searchTableView)

#if IS_XCODE_8
  if ([TiUtils isIOS10OrGreater]) {
    _tableView.prefetchDataSource = nil;
  }
#endif
  RELEASE_TO_NIL(_tableView)
  RELEASE_TO_NIL(_defaultItemTemplate)
  RELEASE_TO_NIL(_searchString)
  RELEASE_TO_NIL(_searchResults)
  RELEASE_TO_NIL(searchController);
  RELEASE_TO_NIL(resultViewController);

  RELEASE_TO_NIL(_appearAnimation)
  RELEASE_TO_NIL(_shownIndexes)
  RELEASE_TO_NIL(sectionTitles)
  RELEASE_TO_NIL(sectionIndices)
  RELEASE_TO_NIL(filteredTitles)
  RELEASE_TO_NIL(filteredIndices)

  [super dealloc];
}
- (void)setHeaderFooter:(TiViewProxy *)theProxy isHeader:(BOOL)header
{
  UIView *headerView = [theProxy getAndPrepareViewForOpening:[TiUtils appFrame]];
  if (header) {
    [self.tableView setTableHeaderView:headerView];
  } else {
    [self.tableView setTableFooterView:headerView];
  }
  [theProxy setProxyObserver:self];
}

- (TiViewProxy *)getOrCreateHeaderHolder
{
  TiViewProxy *vp = [self holdedProxyForKey:@"headerWrapper"];
  if (!vp) {
    vp = (TiViewProxy *)[[self viewProxy] addObjectToHold:@{
      @"layout" : @"vertical",
      @"touchPassThrough" : @(YES),
      @"width" : @"FILL",
      @"height" : @"SIZE"
    }
                                                   forKey:@"headerWrapper"];
    vp.canBeResizedByFrame = YES;
  }
  [self setHeaderFooter:vp isHeader:YES];
  return vp;
}

- (TiTableView *)searchTableView
{
  if (_searchTableView == nil) {
    UITableViewStyle style = [TiUtils intValue:[self.proxy valueForKey:@"style"] def:UITableViewStylePlain];

    _searchTableView = [[TiTableView alloc] initWithFrame:CGRectZero style:style];
    _searchTableView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    _searchTableView.delegate = self;
    _searchTableView.dataSource = self;
    _tableView.touchDelegate = self;

#if IS_XCODE_8
    if ([TiUtils isIOS10OrGreater]) {
      _searchTableView.prefetchDataSource = self;
    }
#endif

    if (TiDimensionIsDip(_rowHeight)) {
      [_searchTableView setRowHeight:_rowHeight.value];
    }
    id backgroundColor = [self.proxy valueForKey:@"backgroundColor"];
    BOOL doSetBackground = YES;
    if (style == UITableViewStyleGrouped) {
      doSetBackground = (backgroundColor != nil);
    }
    if (doSetBackground) {
      [[self class] setBackgroundColor:[UIColor clearColor] onTable:_tableView];
    }
    UITapGestureRecognizer *tapGestureRecognizer = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(handleTap:)];
    tapGestureRecognizer.delegate = self;
    [_searchTableView addGestureRecognizer:tapGestureRecognizer];
    [tapGestureRecognizer release];

    _defaultSeparatorInsets = [_tableView separatorInset];
    [_searchTableView setLayoutMargins:UIEdgeInsetsZero];

    if ([TiUtils isIOS9OrGreater]) {
      _searchTableView.cellLayoutMarginsFollowReadableWidth = NO;
    }
  }
  return _searchTableView;
}

- (TiTableView *)tableView
{
  if (_tableView == nil) {
    UITableViewStyle style = [TiUtils intValue:[self.proxy valueForKey:@"style"] def:UITableViewStylePlain];

    _tableView = [[TiTableView alloc] initWithFrame:self.bounds style:style];
    _tableView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    _tableView.delegate = self;
    _tableView.dataSource = self;
    _tableView.touchDelegate = self;

#if IS_XCODE_8
    if ([TiUtils isIOS10OrGreater]) {
      _tableView.prefetchDataSource = self;
    }
#endif

    if (TiDimensionIsDip(_rowHeight)) {
      [_tableView setRowHeight:_rowHeight.value];
    }
    id backgroundColor = [self.proxy valueForKey:@"backgroundColor"];
    BOOL doSetBackground = YES;
    if (style == UITableViewStyleGrouped) {
      doSetBackground = (backgroundColor != nil);
    }
    if (doSetBackground) {
      [[self class] setBackgroundColor:[UIColor clearColor] onTable:_tableView];
    }
    UITapGestureRecognizer *tapGestureRecognizer = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(handleTap:)];
    tapGestureRecognizer.delegate = self;
    [_tableView addGestureRecognizer:tapGestureRecognizer];
    [tapGestureRecognizer release];
    _defaultSeparatorInsets = [_tableView separatorInset];

    [_tableView setLayoutMargins:UIEdgeInsetsZero];

    if ([TiUtils isIOS9OrGreater]) {
      _tableView.cellLayoutMarginsFollowReadableWidth = NO;
    }
  }
  if ([_tableView superview] != self) {
    [self addSubview:_tableView];
  }

  return _tableView;
}

- (UIScrollView *)scrollView
{
  return [self tableView];
}

- (void)reloadTableViewData
{
  //    _canSwipeCells = NO;
  [_tableView reloadData];
}

- (void)frameSizeChanged:(CGRect)frame bounds:(CGRect)bounds
{
  //        if (searchHidden)
  //        {
  //            if (searchViewProxy!=nil)
  //            {
  //                [self hideSearchScreen:nil];
  //            }
  //        }
  //
  [_tableView setFrame:bounds];
  if (!searchViewAnimating && ![[self searchController] isActive]) {

    TiUISearchBarProxy *searchViewProxy = (TiUISearchBarProxy *)[self holdedProxyForKey:@"searchView"];
    if (searchViewProxy) {
      [searchViewProxy ensureSearchBarHierarchy];
#ifndef TI_USE_AUTOLAYOUT
      CGFloat rowWidth = [self computeRowWidth:_tableView];
      if (rowWidth > 0) {
        CGFloat right = _tableView.bounds.size.width - rowWidth;
        [searchViewProxy layoutProperties]->right = TiDimensionDip(right);
      }
#endif
    }
  } else {
    [_tableView reloadData];
  }
  [super frameSizeChanged:frame bounds:bounds];

  TiViewProxy *vp = [self holdedProxyForKey:@"headerWrapper"];
  if (vp) {
    [vp parentSizeWillChange];
  }
  vp = [self holdedProxyForKey:@"footerView"];
  if (vp) {
    [vp parentSizeWillChange];
  }
  //    if (_pullViewWrapper != nil) {
  //        _pullViewWrapper.frame = CGRectMake(0.0f, 0.0f - bounds.size.height, bounds.size.width, bounds.size.height);
  //    }
}

- (id)accessibilityElement
{
  return self.tableView;
}

- (TiUIListViewProxy *)listViewProxy
{
  return (TiUIListViewProxy *)self.proxy;
}

- (void)selectItem:(NSIndexPath *)indexPath animated:(BOOL)animated
{
  //    [self tableView:_tableView willSelectRowAtIndexPath:indexPath];
  UITableViewScrollPosition pos = UITableViewScrollPositionMiddle;
  [_tableView scrollToRowAtIndexPath:indexPath atScrollPosition:pos animated:animated];
  [_tableView selectRowAtIndexPath:indexPath animated:animated scrollPosition:pos];
  [self tableView:_tableView didSelectRowAtIndexPath:indexPath];
}
- (void)deselectItem:(NSIndexPath *)indexPath animated:(BOOL)animated
{
  [_tableView deselectRowAtIndexPath:indexPath animated:animated];
}

- (id)selectedItems_
{
  NSMutableArray *array = [NSMutableArray array];
  [_tableView.indexPathsForSelectedRows enumerateObjectsUsingBlock:^(NSIndexPath *indexPath, NSUInteger idx, BOOL *stop) {
    [array addObject:@{
      @"index" : @(indexPath.row),
      @"sectionIndex" : @(indexPath.section),
    }];

  }];
  return array;
}

- (void)deselectAll:(BOOL)animated
{
  if (_tableView != nil) {
    [_tableView.indexPathsForSelectedRows enumerateObjectsUsingBlock:^(NSIndexPath *indexPath, NSUInteger idx, BOOL *stop) {
      [_tableView deselectRowAtIndexPath:indexPath animated:animated];
    }];
  }
}

- (void)updateIndicesForVisibleRows
{
  if (_tableView == nil || [self isSearchActive]) {
    return;
  }

  NSArray *visibleRows = [_tableView indexPathsForVisibleRows];
  [visibleRows enumerateObjectsUsingBlock:^(NSIndexPath *vIndexPath, NSUInteger idx, BOOL *stop) {
    UITableViewCell *theCell = [_tableView cellForRowAtIndexPath:vIndexPath];
    if ([theCell isKindOfClass:[TiUIListItem class]]) {
      ((TiUIListItem *)theCell).proxy.indexPath = vIndexPath;
      [((TiUIListItem *)theCell) ensureVisibleSelectorWithTableView:_tableView];
    }
  }];
}

- (void)proxyDidRelayout:(id)sender
{
  NSArray *keys = [[self viewProxy] allKeysForHoldedProxy:sender];
  if ([keys count] > 0) {
    NSString *key = [keys objectAtIndex:0];
    if ([key isEqualToString:@"headerWrapper"] || [key isEqualToString:@"headerView"]) {
      UIView *headerView = [[self tableView] tableHeaderView];
      [[self tableView] setTableHeaderView:headerView];
      [((TiUIListViewProxy *)[self proxy])contentsWillChange];
    } else if ([key isEqualToString:@"footerView"]) {
      UIView *footerView = [[self tableView] tableFooterView];
      [[self tableView] setTableFooterView:footerView];
      [((TiUIListViewProxy *)[self proxy])contentsWillChange];
    }
  }
  [super proxyDidRelayout:sender];
}

- (void)setContentOffset_:(id)value withObject:(id)args
{
  CGPoint offset = [TiUtils pointValue:value];
  BOOL animated = [TiUtils boolValue:[args valueForKey:@"animated"] def:NO];
  [_tableView setContentOffset:offset animated:animated];
}

- (void)setContentInsets_:(id)value withObject:(id)props
{
  UIEdgeInsets insets = [TiUtils contentInsets:value];
  BOOL animated = [TiUtils boolValue:@"animated" properties:props def:NO];
  void (^setInset)(void) = ^{
    [_tableView setContentInset:insets];
  };
  if (animated) {
    double duration = [TiUtils doubleValue:@"duration" properties:props def:300] / 1000;
    [UIView animateWithDuration:duration animations:setInset];
  } else {
    setInset();
  }
}
- (void)setTemplates_:(id)args
{
  [self reloadTableViewData];
}
//- (void)setTemplates_:(id)args
//{
//    ENSURE_TYPE_OR_NIL(args,NSDictionary);
//	NSMutableDictionary *templates = [[NSMutableDictionary alloc] initWithCapacity:[args count]];
//	NSMutableDictionary *measureProxies = [[NSMutableDictionary alloc] initWithCapacity:[args count]];
//	[(NSDictionary *)args enumerateKeysAndObjectsUsingBlock:^(NSString *key, id obj, BOOL *stop) {
//		TiProxyTemplate *template = [TiProxyTemplate templateFromViewTemplate:obj];
//		if (template != nil) {
//			[templates setObject:template forKey:key];
//
//            //create fake proxy for height computation
//            id<TiEvaluator> context = self.listViewProxy.executionContext;
//            if (context == nil) {
//                context = self.listViewProxy.pageContext;
//            }
//            TiUIListItemProxy *cellProxy = [[TiUIListItemProxy alloc] initWithListViewProxy:self.listViewProxy inContext:context];
//            [cellProxy unarchiveFromTemplate:template withEvents:NO];
//            [cellProxy bindings];
//            [measureProxies setObject:cellProxy forKey:key];
//            [cellProxy release];
//		}
//	}];
//
//	[_templates release];
//	_templates = [templates copy];
//	[templates release];
//
//    [_measureProxies release];
//	_measureProxies = [measureProxies copy];
//	[measureProxies release];
//
//    [self reloadTableViewData];
//}

- (TiViewProxy *)sectionViewProxy:(NSInteger)section forLocation:(NSString *)location
{
  TiUIListSectionProxy *sectionProxy = [self.listViewProxy sectionForIndex:section];
  return [sectionProxy sectionViewForLocation:location inListView:self];
}

- (TiViewProxy *)currentSectionViewProxy:(NSInteger)section forLocation:(NSString *)location
{
  TiUIListSectionProxy *sectionProxy = [self.listViewProxy sectionForIndex:section];
  return [sectionProxy currentViewForLocation:location inListView:self];
}

- (UIView *)sectionView:(NSInteger)section forLocation:(NSString *)location section:(TiUIListSectionProxy **)sectionResult
{
  TiViewProxy *viewproxy = [self sectionViewProxy:section forLocation:location];
  if (viewproxy != nil) {
    return [viewproxy getAndPrepareViewForOpening:self.tableView.bounds];
  }
  return nil;
}

- (CGSize)contentSizeForSize:(CGSize)size
{
  if (_tableView == nil) {
    return CGSizeZero;
  }

  CGSize refSize = CGSizeMake(size.width, 1000);

  CGFloat resultHeight = 0;
  TiViewProxy *vp;
  //Last Section rect
  NSInteger lastSectionIndex = [self numberOfSectionsInTableView:_tableView] - 1;
  if (lastSectionIndex >= 0) {
    CGRect refRect = [_tableView rectForSection:lastSectionIndex];
    resultHeight += refRect.size.height + refRect.origin.y;
  } else {
    //Header auto height when no sections
    vp = [self holdedProxyForKey:@"searchWrapper"];
    if (vp) {
      resultHeight += [vp contentSizeForSize:refSize].height;
    }
  }

  //Footer auto height
  vp = [self holdedProxyForKey:@"footerView"];
  if (vp) {
    resultHeight += [vp contentSizeForSize:refSize].height;
  }

  refSize.height = resultHeight;

  return refSize;
}

#pragma mark Searchbar-related IBActions

- (IBAction)showSearchScreen:(id)sender
{
  [_tableView setContentOffset:CGPointZero animated:YES];
}

- (void)hideSearchScreen:(id)sender animated:(BOOL)animated
{
  if (!searchHidden || ![(TiViewProxy *)self.proxy viewReady]) {
    return;
  }

  // check to make sure we're not in the middle of a layout, in which case we
  // want to try later or we'll get weird drawing animation issues
  if (_tableView.bounds.size.width == 0) {
    [self performSelector:@selector(hideSearchScreen:) withObject:sender afterDelay:0.1];
    return;
  }

  TiUISearchBarProxy *searchViewProxy = (TiUISearchBarProxy *)[self holdedProxyForKey:@"searchView"];
  if (searchViewProxy && [[searchViewProxy view] isFirstResponder]) {
    [searchViewProxy blur:nil];
  }

  // This logic here is contingent on search controller deactivation
  // (-[TiUITableView searchDisplayControllerDidEndSearch:]) triggering a hide;
  // doing this ensures that:
  //
  // * The hide when the search controller was active is animated
  // * The animation only occurs once

  if ([[self searchController] isActive]) {
    [[self searchController] setActive:NO];
    searchActive = NO;
    return;
  }

  searchActive = NO;
  if (![(TiViewProxy *)self.proxy viewReady]) {
    return;
  }
  NSArray *visibleRows = [_tableView indexPathsForVisibleRows];

  // We only want to scroll if the following conditions are met:
  // 1. The top row of the first section (and hence searchbar) are visible (or there are no rows)
  // 2. The current offset is smaller than the new offset (otherwise the search is already hidden)

  if (searchViewProxy && searchHidden) {
    CGPoint offset = CGPointMake(0, MAX(TI_NAVBAR_HEIGHT, searchViewProxy.view.frame.size.height));
    if (([visibleRows count] == 0) || ([_tableView contentOffset].y < offset.y && [visibleRows containsObject:[NSIndexPath indexPathForRow:0 inSection:0]])) {
      [_tableView setContentOffset:offset animated:animated];
    }
  }
}
- (void)hideSearchScreen:(id)sender
{
  [self hideSearchScreen:sender animated:YES];
}

- (BOOL)shouldHighlightCurrentListItem
{
  return [_tableView shouldHighlightCurrentListItem];
}

#pragma mark - Helper Methods

- (CGFloat)computeRowWidth:(UITableView *)tableView
{
  if (tableView == nil) {
    return 0;
  }
  CGFloat rowWidth = tableView.bounds.size.width;

  // Apple does not provide a good way to get information about the index sidebar size
  // in the event that it exists - it silently resizes row content which is "flexible width"
  // but this is not useful for us. This is a problem when we have Ti.UI.SIZE/FILL behavior
  // on row contents, which rely on the height of the row to be accurately precomputed.
  //
  // The following is unreliable since it uses a private API name, but one which has existed
  // since iOS 3.0. The alternative is to grab a specific subview of the tableview itself,
  // which is more fragile.
  if ((sectionTitles == nil) || ([searchController isActive])) {
    return rowWidth;
  }
  NSArray *subviews = [tableView subviews];
  if ([subviews count] > 0) {
    // Obfuscate private class name
    Class indexview = NSClassFromString([@"UITableView" stringByAppendingString:@"Index"]);
    for (UIView *view in subviews) {
      if ([view isKindOfClass:indexview]) {
        rowWidth -= [view frame].size.width;
      }
    }
  }

  return floorf(rowWidth);
}

- (id)valueWithKey:(NSString *)key atIndexPath:(NSIndexPath *)indexPath
{
  NSDictionary *item = [[self.listViewProxy sectionForIndex:indexPath.section] itemAtIndex:indexPath.row];
  id propertiesValue = [item objectForKey:@"properties"];
  NSDictionary *properties = ([propertiesValue isKindOfClass:[NSDictionary class]]) ? propertiesValue : item;
  NSString *replaceKey = [self replacedKeyForKey:key];
  id theValue = [properties objectForKey:replaceKey];
  if (theValue == nil) {
    id templateId = [item objectForKey:@"template"];
    if (templateId == nil) {
      templateId = _defaultItemTemplate;
    }
    if (![templateId isKindOfClass:[NSNumber class]]) {
      TiProxyTemplate *template = [self.listViewProxy.templates objectForKey:templateId];
      theValue = [template.properties objectForKey:replaceKey];
    }
    if (theValue == nil) {
      theValue = [self.proxy valueForKey:key];
    }
  }

  return theValue;
}

- (id)firstItemValueForKeys:(NSArray *)keys atIndexPath:(NSIndexPath *)indexPath
{
  NSDictionary *item = [[self.listViewProxy sectionForIndex:indexPath.section] itemAtIndex:indexPath.row];
  NSDictionary *properties = [item objectForKey:@"properties"];
  __block id theResult = nil;
  [keys enumerateObjectsUsingBlock:^(NSString *key, NSUInteger idx, BOOL *stop) {
    theResult = [item objectForKey:key];
    if (!theResult && properties) {
      theResult = [properties objectForKey:key];
    }
    if (theResult) {
      *stop = YES;
    }
  }];
  return theResult;
}

- (void)buildResultsForSearchText
{
  searchActive = ([self.searchString length] > 0);
  RELEASE_TO_NIL(filteredIndices);
  RELEASE_TO_NIL(filteredTitles);
  if (searchActive) {
    BOOL hasResults = NO;
    //Initialize
    if (_searchResults == nil) {
      _searchResults = [[NSMutableArray alloc] init];
    }
    //Clear Out
    [_searchResults removeAllObjects];

    //Search Options
    NSStringCompareOptions searchOpts = (caseInsensitiveSearch ? NSCaseInsensitiveSearch : 0);

    NSUInteger maxSection = [[self.listViewProxy sectionCount] unsignedIntegerValue];
    NSMutableArray *singleSection = keepSectionsInSearch ? nil : [[NSMutableArray alloc] init];
    for (int i = 0; i < maxSection; i++) {
      NSMutableArray *thisSection = keepSectionsInSearch ? [[NSMutableArray alloc] init] : nil;
      NSUInteger maxItems = [[self.listViewProxy sectionForIndex:i] itemCount];
      for (int j = 0; j < maxItems; j++) {
        NSIndexPath *thePath = [NSIndexPath indexPathForRow:j inSection:i];
        id theValue = [TiUtils stringValue:[self firstItemValueForKeys:@[ @"searchableText", @"title" ] atIndexPath:thePath]];
        if (theValue != nil && [theValue rangeOfString:self.searchString options:searchOpts].location != NSNotFound) {
          if (searchIgnoreExactMatch && [theValue isEqualToString:self.searchString]) {
            continue;
          }
          (thisSection != nil) ? [thisSection addObject:thePath] : [singleSection addObject:thePath];
          hasResults = YES;
        }
      }
      if (thisSection != nil) {
        [_searchResults addObject:thisSection];

        if (sectionTitles != nil && sectionIndices != nil) {
          NSNumber *theIndex = [NSNumber numberWithInt:i];
          if ([sectionIndices containsObject:theIndex]) {
            id theTitle = [sectionTitles objectAtIndex:[sectionIndices indexOfObject:theIndex]];
            if (filteredTitles == nil) {
              filteredTitles = [[NSMutableArray alloc] init];
            }
            if (filteredIndices == nil) {
              filteredIndices = [[NSMutableArray alloc] init];
            }
            [filteredTitles addObject:theTitle];
            [filteredIndices addObject:NUMUINTEGER([_searchResults count] - 1)];
          }
        }
        [thisSection release];
      }
    }
    if (singleSection != nil) {
      if ([singleSection count] > 0) {
        [_searchResults addObject:singleSection];
      }
      [singleSection release];
    }
    if (!hasResults) {
      if ([(TiViewProxy *)self.proxy _hasListeners:@"noresults" checkParent:NO]) {
        [self.proxy fireEvent:@"noresults" withObject:nil propagate:NO reportSuccess:NO errorCode:0 message:nil];
      }
    }
  } else {
    RELEASE_TO_NIL(_searchResults);
  }
}

- (BOOL)isSearchActive
{
  return searchActive || [[self searchController] isActive];
}

- (void)updateSearchResults:(id)unused
{
  if ([self isSearchActive]) {
    [self buildResultsForSearchText];
  }
  [self reloadTableViewData];
  if ([[self searchController] isActive]) {
    [resultViewController.tableView reloadData];
  }
}

- (NSIndexPath *)pathForSearchPath:(NSIndexPath *)indexPath
{
  if (_searchResults != nil && [_searchResults count] > indexPath.section) {
    NSArray *sectionResults = [_searchResults objectAtIndex:indexPath.section];
    if ([sectionResults count] > indexPath.row) {
      return [sectionResults objectAtIndex:indexPath.row];
    }
  }
  return indexPath;
}

- (NSInteger)sectionForSearchSection:(NSInteger)section
{
  if (_searchResults != nil) {
    NSArray *sectionResults = [_searchResults objectAtIndex:section];
    NSIndexPath *thePath = [sectionResults objectAtIndex:0];
    return thePath.section;
  }
  return section;
}

#pragma mark - Public API

- (void)setSeparatorInsets_:(id)arg
{
  DEPRECATED_REPLACED(@"UI.ListView.separatorInsets", @"5.2.0", @"UI.ListView.listSeparatorInsets");
  [self setListSeparatorInsets_:arg];
}

- (void)setTableSeparatorInsets_:(id)arg
{
  DEPRECATED_REPLACED(@"UI.ListView.tableSeparatorInsets", @"5.4.0", @"UI.ListView.listSeparatorInsets");
  [self setListSeparatorInsets_:arg];
}

- (void)setDimBackgroundForSearch_:(id)arg
{
  ENSURE_SINGLE_ARG_OR_NIL(arg, NSNumber);

  if (searchController) {
    searchController.dimsBackgroundDuringPresentation = [TiUtils boolValue:arg def:YES];
  } else {
    _dimsBackgroundDuringPresentation = [TiUtils boolValue:arg def:YES];
  }
}

- (void)setListSeparatorInsets_:(id)arg
{
  [self tableView];
  if ([arg isKindOfClass:[NSDictionary class]]) {
    CGFloat left = [TiUtils floatValue:@"left" properties:arg def:_defaultSeparatorInsets.left];
    CGFloat right = [TiUtils floatValue:@"right" properties:arg def:_defaultSeparatorInsets.right];
    [_tableView setSeparatorInset:UIEdgeInsetsMake(0, left, 0, right)];
  } else {
    [_tableView setSeparatorInset:_defaultSeparatorInsets];
  }
  if (![self isSearchActive]) {
    [_tableView setNeedsDisplay];
  }
}

- (void)setRowSeparatorInsets_:(id)arg
{
  if ([arg isKindOfClass:[NSDictionary class]]) {
    CGFloat left = [TiUtils floatValue:@"left" properties:arg def:_defaultSeparatorInsets.left];
    CGFloat right = [TiUtils floatValue:@"right" properties:arg def:_defaultSeparatorInsets.right];
    _rowSeparatorInsets = UIEdgeInsetsMake(0, left, 0, right);
  }
  if (![searchController isActive]) {
    [[self tableView] setNeedsDisplay];
  }
}

- (void)setPruneSectionsOnEdit_:(id)args
{
  pruneSections = [TiUtils boolValue:args def:NO];
}

- (void)setSeparatorStyle_:(id)arg
{
  [[self tableView] setSeparatorStyle:[TiUtils intValue:arg]];
}

- (void)setSeparatorColor_:(id)arg
{
  TiColor *color = [TiUtils colorValue:arg];
  [[self tableView] setSeparatorColor:[color _color]];
}

- (void)setDefaultItemTemplate_:(id)args
{
  if (![args isKindOfClass:[NSString class]] && ![args isKindOfClass:[NSNumber class]]) {
    ENSURE_TYPE_OR_NIL(args, NSString);
  }
  [_defaultItemTemplate release];
  _defaultItemTemplate = [args copy];
  [self reloadTableViewData];
}

- (void)setRowHeight_:(id)height
{
  _rowHeight = [TiUtils dimensionValue:height];
  if (TiDimensionIsDip(_rowHeight)) {
    [_tableView setRowHeight:_rowHeight.value];
  }
}

- (void)setMinRowHeight_:(id)height
{
  _minRowHeight = [TiUtils dimensionValue:height];
}

- (void)setMaxRowHeight_:(id)height
{
  _maxRowHeight = [TiUtils dimensionValue:height];
}

- (void)onCreateCustomBackground
{
  if (_tableView != nil) {
    [[self class] setBackgroundColor:[UIColor clearColor] onTable:_tableView];
  }
}

- (void)setHeaderTitle_:(id)args
{
  NSString *text = [TiUtils stringValue:args];
  if (text) {
    TiViewProxy *vp = (TiViewProxy *)[[self viewProxy] addObjectToHold:@{
      @"type" : @"Ti.UI.Label",
      @"font" : @{ @"size" : @(15) },
      @"padding" : @{ @"left" : @(15), @"right" : @(15) },
      @"width" : @"FILL",
      @"height" : @(50),
      @"autocapitalization" : @(YES),
      @"text" : [TiUtils stringValue:args],
      @"color" : @"#333333",
    }
                                                                forKey:@"headerView"];
    [vp setProxyObserver:self];
    [[self getOrCreateHeaderHolder] addProxy:vp atIndex:1 shouldRelayout:YES];
  } else {
    [[self viewProxy] removeHoldedProxyForKey:@"headerView"];
  }
}

- (void)setFooterTitle_:(id)args
{
  NSString *text = [TiUtils stringValue:args];
  if (text) {
    TiViewProxy *vp = (TiViewProxy *)[[self viewProxy] addObjectToHold:@{
      @"type" : @"Ti.UI.Label",
      @"font" : @{ @"size" : @(14) },
      @"padding" : @{ @"left" : @(15), @"right" : @(15), @"top" : @(10), @"bottom" : @(10) },
      @"width" : @"FILL",
      @"height" : @"SIZE",
      @"text" : [TiUtils stringValue:args],
      @"color" : @"#6E6E6E",
    }
                                                                forKey:@"footerView"];
    [vp setProxyObserver:self];
    vp.canBeResizedByFrame = YES;
    [self setHeaderFooter:vp isHeader:NO];
  } else {
    [self setHeaderFooter:nil isHeader:NO];
  }
}

- (TiViewProxy *)holdedProxyForKey:(NSString *)key
{
  return (TiViewProxy *)[[self viewProxy] holdedProxyForKey:key];
}

- (void)setHeaderView_:(id)args
{
  id vp = [[self viewProxy] addObjectToHold:args forKey:@"headerView"];
  if (IS_OF_CLASS(vp, TiViewProxy)) {
    [(TiViewProxy *)vp setProxyObserver:self];
    [[self getOrCreateHeaderHolder] addProxy:vp atIndex:1 shouldRelayout:YES];
  }
}

- (void)setFooterView_:(id)args
{
  id vp = [[self viewProxy] addObjectToHold:args forKey:@"footerView"];
  if (IS_OF_CLASS(vp, TiViewProxy)) {
    [(TiViewProxy *)vp setProxyObserver:self];
    ((TiViewProxy *)vp).canBeResizedByFrame = YES;
    [self setHeaderFooter:(TiViewProxy *)vp isHeader:NO];
  } else {
    [self setHeaderFooter:nil isHeader:NO];
  }
}

- (void)setRefreshControl_:(id)args
{
#ifdef USE_TI_UIREFRESHCONTROL
  id vp = [[self viewProxy] addObjectToHold:args forKey:@"refreshControl"];
  if (IS_OF_CLASS(vp, TiUIRefreshControlProxy)) {
    [[self tableView] addSubview:[(TiUIRefreshControlProxy *)vp control]];
  }
#endif
}

- (void)setKeepSectionsInSearch_:(id)args
{
  keepSectionsInSearch = [TiUtils boolValue:args def:NO];
  TiUISearchBarProxy *vp = (TiUISearchBarProxy *)[self holdedProxyForKey:@"searchView"];
  if (vp && searchActive) {
    [self buildResultsForSearchText];
    [[resultViewController tableView] reloadData];
    //    [self reloadTableViewData];
  }
}

- (void)setAllowsSelection_:(id)value
{
  allowsSelection = [TiUtils boolValue:value];
  //    [[self tableView] setAllowsSelection:allowsSelection];
  //    [tableController setClearsSelectionOnViewWillAppear:!allowsSelection];
}

- (void)setAllowsSelectionDuringEditing_:(id)value
{
  [[self tableView] setAllowsSelectionDuringEditing:[TiUtils boolValue:value]];
}

- (void)setAllowsMultipleSelection_:(id)value
{
  [[self tableView] setAllowsMultipleSelection:[TiUtils boolValue:value]];
}

- (void)setAllowsMultipleSelectionDuringEditing_:(id)value
{
  allowsMultipleSelectionDuringEditing = [TiUtils boolValue:value def:NO];
  if (_editing) {
    [_tableView setAllowsMultipleSelectionDuringEditing:allowsMultipleSelectionDuringEditing];
  }
}

- (void)setEditing:(BOOL)editing
{
  if (editing != _editing) {
    _editing = editing;
    [self.viewProxy setFakeAnimationOfDuration:0.3 andCurve:[CAMediaTimingFunction functionWithName:kCAMediaTimingFunctionEaseInEaseOut]];
    if ([searchController isActive] && searchActive) {
      [[resultViewController tableView] beginUpdates];
      [[resultViewController tableView] setEditing:_editing animated:YES];
      [[resultViewController tableView] setAllowsMultipleSelectionDuringEditing:_editing ? allowsMultipleSelectionDuringEditing : NO];
      [[resultViewController tableView] endUpdates];
    } else {
      [[self tableView] beginUpdates];
      [_tableView setEditing:_editing animated:YES];
      [_tableView setAllowsMultipleSelectionDuringEditing:_editing ? allowsMultipleSelectionDuringEditing : NO];
      [_tableView endUpdates];
    }

    [self.viewProxy removeFakeAnimation];
  }
}

- (void)setKeyboardDismissMode_:(id)value
{
  ENSURE_TYPE(value, NSNumber);
  [[self tableView] setKeyboardDismissMode:[TiUtils intValue:value def:UIScrollViewKeyboardDismissModeNone]];
  [[self proxy] replaceValue:value forKey:@"keyboardDismissMode" notification:NO];
}

- (void)setEditing_:(id)args
{
  self.editing = [TiUtils boolValue:args def:NO];
}
- (id)editing_
{
  return @(_editing);
}

- (void)setScrollSuspendsImageLoading_:(id)value
{
  _scrollSuspendImageLoading = [TiUtils boolValue:value def:_scrollSuspendImageLoading];
}

- (void)setLazyLoadingEnabled_:(id)value
{
  [self setScrollSuspendsImageLoading_:value];
}

- (void)setOnDisplayCell_:(id)callback
{
  hasOnDisplayCell = [callback isKindOfClass:[KrollCallback class]] || [callback isKindOfClass:[KrollWrapper class]];
}

- (void)setAppearAnimation_:(id)value
{
  RELEASE_TO_NIL(_appearAnimation)
  if (IS_OF_CLASS(TiAnimation, value)) {
    _appearAnimation = [value retain];
  } else {
    ENSURE_SINGLE_ARG(value, NSDictionary);
    _appearAnimation = [value retain];
  }
  _useAppearAnimation = _useAppearAnimation || _appearAnimation != nil;
}

- (void)setUseAppearAnimation_:(id)value
{
  _useAppearAnimation = [TiUtils boolValue:value def:NO];
}

- (void)setDisableBounce_:(id)value
{
  [[self tableView] setBounces:![TiUtils boolValue:value def:NO]];
}

#pragma mark - Search Support

- (UISearchController *)searchController
{
  //  TiUISearchBarProxy *vp = (TiUISearchBarProxy *)[self holdedProxyForKey:@"searchView"];
  //  if (vp && vp.canHaveSearchDisplayController) {
  //    return [(TiUISearchBarProxy *)vp searchController];
  //  }
  return searchController;
}

- (void)setCaseInsensitiveSearch_:(id)args
{
  caseInsensitiveSearch = [TiUtils boolValue:args def:YES];
  if (searchActive) {
    [self buildResultsForSearchText];
    if ([[self searchController] isActive]) {
      [[resultViewController tableView] reloadData];
    } else {
      [self reloadTableViewData];
    }
  }
}

- (void)setSearchText_:(id)args
{
  id searchView = [self holdedProxyForKey:@"searchView"];
  if (searchView) {
    DebugLog(@"Can not use searchText with searchView. Ignoring call.");
    return;
  }
  self.searchString = [TiUtils stringValue:args];
  [self buildResultsForSearchText];
  [[resultViewController tableView] reloadData];
  if ([[self viewProxy] isConfigurationSet]) {
    [[self viewProxy] contentsWillChange];
  }
}

- (void)setSearchIgnoreExactMatch_:(id)args
{
  searchIgnoreExactMatch = [TiUtils boolValue:args];
  [self updateSearchResults:nil];
}

- (void)setSearchViewExternal_:(id)args
{
  ENSURE_SINGLE_ARG_OR_NIL(args, TiUISearchBarProxy)

  id vp = [[self viewProxy] addProxyToHold:args setParent:NO forKey:@"searchView" shouldRelayout:NO];

  if (IS_OF_CLASS(vp, TiUISearchBarProxy)) {
    //        [self tableView];
    [(TiUISearchBarProxy *)vp setReadyToCreateView:YES];
    [(TiUISearchBarProxy *)vp setDelegate:self];
    ((TiUISearchBarProxy *)vp).canHaveSearchDisplayController = YES;
    if (searchHidden) {
      [self hideSearchScreen:nil animated:NO];
    }
  } else {
    [[self viewProxy] removeHoldedProxyForKey:@"searchView"];
  }
}

- (void)setSearchView_:(id)args
{
  id vp = [[self viewProxy] addObjectToHold:args forKey:@"searchView"];
  if (IS_OF_CLASS(vp, TiUISearchBarProxy)) {
    [(TiUISearchBarProxy *)vp setReadyToCreateView:YES];
    [(TiUISearchBarProxy *)vp setDelegate:self];
    ((TiUISearchBarProxy *)vp).canHaveSearchDisplayController = [TiUtils boolValue:@"dontUseResultView" def:NO];
    [[self getOrCreateHeaderHolder] addProxy:vp atIndex:0 shouldRelayout:YES];
    self.searchString = [[vp searchBar] text];
    [self initSearchController:self];
    if (self.searchString) {
      [self buildResultsForSearchText];
      [[resultViewController tableView] reloadData];
    }
    if (searchHidden) {
      [self hideSearchScreen:nil animated:NO];
    }
  } else {
    [[self viewProxy] removeHoldedProxyForKey:@"searchView"];
  }
}

- (void)setSearchHidden_:(id)hide
{
  searchHidden = [TiUtils boolValue:hide def:NO];
  TiUISearchBarProxy *vp = (TiUISearchBarProxy *)[self holdedProxyForKey:@"searchView"];
  if (vp) {
    if (searchHidden) {
      [self hideSearchScreen:nil];
    } else {
      [self showSearchScreen:nil];
    }
  }
}

- (void)setHideSearchOnSelection_:(id)yn
{
  hideOnSearch = [TiUtils boolValue:yn def:NO];
}

- (void)cleanup:(id)unused
{
  if ([[self searchController] isActive]) {
    [[self searchController] setActive:NO];
  }
}

- (void)windowDidClose:(id)unused
{
  UIView *view = [[self tableView] tableHeaderView];
  if (IS_OF_CLASS(view, TiUIView)) {
    [[(TiUIView *)view viewProxy] detachView:YES];
  }
  view = [[self tableView] tableFooterView];
  if (IS_OF_CLASS(view, TiUIView)) {
    [[(TiUIView *)view viewProxy] detachView:YES];
  }
}

#pragma mark - SectionIndexTitle Support

- (void)setSectionIndexTitles_:(id)args
{
  ENSURE_TYPE_OR_NIL(args, NSArray);

  RELEASE_TO_NIL(sectionTitles);
  RELEASE_TO_NIL(sectionIndices);
  RELEASE_TO_NIL(filteredTitles);
  RELEASE_TO_NIL(filteredIndices);

  NSArray *theIndex = args;
  if ([theIndex count] > 0) {
    sectionTitles = [[NSMutableArray alloc] initWithCapacity:[theIndex count]];
    sectionIndices = [[NSMutableArray alloc] initWithCapacity:[theIndex count]];

    for (NSDictionary *entry in theIndex) {
      ENSURE_DICT(entry);
      NSString *title = [entry objectForKey:@"title"];
      id index = [entry objectForKey:@"index"];
      [sectionTitles addObject:title];
      [sectionIndices addObject:NUMINTEGER([TiUtils intValue:index])];
    }
  }
  TiUISearchBarProxy *vp = (TiUISearchBarProxy *)[self holdedProxyForKey:@"searchView"];
  if (vp) {
    if (searchActive) {
      [self buildResultsForSearchText];
    }
  }
  [_tableView reloadSectionIndexTitles];
}

#pragma mark - SectionIndexTitle Support Datasource methods.

- (NSArray *)sectionIndexTitlesForTableView:(UITableView *)tableView
{
  if (tableView != _tableView) {
    return nil;
  }

  if (_editing) {
    return nil;
  }

  if (searchActive) {
    if (keepSectionsInSearch && ([_searchResults count] > 0)) {
      return filteredTitles;
    } else {
      return nil;
    }
  }
  return sectionTitles;
}

- (NSInteger)tableView:(UITableView *)tableView sectionForSectionIndexTitle:(NSString *)title atIndex:(NSInteger)theIndex
{
  if (tableView != _tableView) {
    return 0;
  }

  if (_editing) {
    return 0;
  }

  if (searchActive) {
    if (keepSectionsInSearch && ([_searchResults count] > 0) && (filteredTitles != nil) && (filteredIndices != nil)) {
      // get the index for the title
      NSUInteger index = [filteredTitles indexOfObject:title];
      int sectionIndex = [[filteredIndices objectAtIndex:index] intValue];

      if ([(TiViewProxy *)[self proxy] _hasListeners:@"indexclick" checkParent:NO]) {
        [[self proxy] fireEvent:@"indexclick"
                     withObject:@{
                       @"title" : title,
                       @"index" : @(index),
                       @"sectionIndex" : @(sectionIndex),
                     }
                      propagate:NO
               checkForListener:NO];
      }

      if (index > 0 && (index < [filteredIndices count])) {
        return [[filteredIndices objectAtIndex:index] intValue];
      }
      return 0;
    } else {
      return 0;
    }
  }

  if ((sectionTitles != nil) && (sectionIndices != nil)) {
    // get the index for the title
    NSUInteger index = [sectionTitles indexOfObject:title];
    int sectionIndex = [[sectionIndices objectAtIndex:index] intValue];

    if ([(TiViewProxy *)[self proxy] _hasListeners:@"indexclick" checkParent:NO]) {

      [[self proxy] fireEvent:@"indexclick"
                   withObject:@{
                     @"title" : title,
                     @"index" : @(index),
                     @"sectionIndex" : @(sectionIndex),
                   }
                    propagate:NO
             checkForListener:NO];
    }

    if (index > 0 && (index < [sectionIndices count])) {
      return sectionIndex;
    }
    return 0;
  }
  return 0;
}

#pragma mark - Editing Support

- (BOOL)canEditRowAtIndexPath:(NSIndexPath *)indexPath
{
  id editValue = [self valueWithKey:@"canEdit" atIndexPath:indexPath];
  //canEdit if undefined is false
  return [TiUtils boolValue:editValue def:NO];
}

- (BOOL)canInsertRowAtIndexPath:(NSIndexPath *)indexPath
{
  id insertValue = [self valueWithKey:@"canInsert" atIndexPath:indexPath];
  //canInsert if undefined is false
  return [TiUtils boolValue:insertValue def:NO];
}

- (BOOL)canMoveRowAtIndexPath:(NSIndexPath *)indexPath
{
  id moveValue = [self valueWithKey:@"canMove" atIndexPath:indexPath];
  //canMove if undefined is false
  return [TiUtils boolValue:moveValue def:NO];
}

- (NSArray *)editActionsFromValue:(id)value
{
  ENSURE_ARRAY(value);
  NSArray *propArray = (NSArray *)value;
  NSMutableArray *returnArray = nil;

  for (id prop in propArray) {
    ENSURE_DICT(prop);
    NSString *title = [TiUtils stringValue:@"title" properties:prop];
    NSString *identifier = [TiUtils stringValue:@"identifier" properties:prop];
    int actionStyle = [TiUtils intValue:@"style" properties:prop def:UITableViewRowActionStyleDefault];
    TiColor *color = [TiUtils colorValue:@"color" properties:prop];

    UITableViewRowAction *theAction = [UITableViewRowAction rowActionWithStyle:actionStyle
                                                                         title:title
                                                                       handler:^(UITableViewRowAction *action, NSIndexPath *indexPath) {
                                                                         NSString *eventName = @"editaction";

                                                                         NSIndexPath *realIndexPath = [self pathForSearchPath:indexPath];

                                                                         if ([self.listViewProxy _hasListeners:eventName checkParent:NO]) {
                                                                           TiUIListSectionProxy *theSection = [[self.listViewProxy sectionForIndex:realIndexPath.section] retain];
                                                                           NSDictionary *theItem = [[theSection itemAtIndex:realIndexPath.row] retain];
                                                                           NSMutableDictionary *eventObject = [[NSMutableDictionary alloc] initWithObjectsAndKeys:
                                                                                                                                               theSection, @"section",
                                                                                                                                           NUMINTEGER(realIndexPath.section), @"sectionIndex",
                                                                                                                                           NUMINTEGER(realIndexPath.row), @"itemIndex",
                                                                                                                                           action.title, @"action",
                                                                                                                                           nil];
                                                                           id propertiesValue = [theItem objectForKey:@"properties"];
                                                                           NSDictionary *properties = ([propertiesValue isKindOfClass:[NSDictionary class]]) ? propertiesValue : nil;
                                                                           id itemId = [properties objectForKey:@"itemId"];
                                                                           if (itemId) {
                                                                             [eventObject setObject:itemId forKey:@"itemId"];
                                                                           }
                                                                           if (identifier) {
                                                                             [eventObject setObject:identifier forKey:@"identifier"];
                                                                           }
                                                                           [self.proxy fireEvent:eventName withObject:eventObject withSource:self.proxy propagate:NO reportSuccess:NO errorCode:0 message:nil];
                                                                           [eventObject release];
                                                                           [theItem release];
                                                                           [theSection release];
                                                                         }

                                                                         // Hide editActions after selection
                                                                         [[self tableView] setEditing:NO];
                                                                         if ([searchController isActive]) {
                                                                           [[resultViewController tableView] setEditing:NO];
                                                                         }

                                                                       }];
    if (color) {
      theAction.backgroundColor = [color color];
    }
    if (!returnArray) {
      returnArray = [NSMutableArray arrayWithObject:theAction];
    } else {
      [returnArray addObject:theAction];
    }
  }

  return returnArray;
}

#pragma mark - Editing Support Datasource methods.

- (BOOL)tableView:(UITableView *)tableView canEditRowAtIndexPath:(NSIndexPath *)indexPath
{
  NSIndexPath *realIndexPath = [self pathForSearchPath:indexPath];
  if ([self canEditRowAtIndexPath:realIndexPath] || [self canInsertRowAtIndexPath:realIndexPath]) {
    return YES;
  }

  if (_editing) {
    return [self canMoveRowAtIndexPath:indexPath];
  }
  return NO;
}

- (void)tableView:(UITableView *)tableView commitEditingStyle:(UITableViewCellEditingStyle)editingStyle forRowAtIndexPath:(NSIndexPath *)indexPath
{
  NSIndexPath *realIndexPath = [self pathForSearchPath:indexPath];
  TiUIListSectionProxy *theSection = [[self.listViewProxy sectionForIndex:realIndexPath.section] retain];
  if (editingStyle == UITableViewCellEditingStyleDelete) {

    NSDictionary *theItem = [[theSection itemAtIndex:realIndexPath.row] retain];

    //Delete Data
    [theSection deleteItemAtIndex:realIndexPath.row];

    [self fireEditEventWithName:@"delete" andSection:theSection atIndexPath:realIndexPath item:theItem];
    [theItem release];

    BOOL emptyTable = NO;
    NSUInteger sectionCount = [[self.listViewProxy sectionCount] unsignedIntValue];

    if (sectionCount == 0) {
      emptyTable = YES;
    }

    BOOL emptySection = NO;

    if ([theSection itemCount] == 0) {
      emptySection = YES;
      if (pruneSections) {
        [self.listViewProxy deleteSectionAtIndex:realIndexPath.section];
      }
    }

    if (searchActive) {
      [self buildResultsForSearchText];
    }

    if ([self isSearchActive] && _searchResults && ([_searchResults count] == 0) && !keepSectionsInSearch) {
      [_searchResults insertObject:[NSArray array] atIndex:indexPath.section];
    }

    //Reload the data now.
    [tableView beginUpdates];
    if (emptyTable) {
      //Table is empty. Just reload fake section with FADE animation to clear out header and footers
      NSIndexSet *theSet = [NSIndexSet indexSetWithIndex:0];
      [tableView reloadSections:theSet withRowAnimation:UITableViewRowAnimationFade];
    } else if (emptySection) {
      //Section is empty.
      if (pruneSections) {
        if (!keepSectionsInSearch && searchActive) {
          [tableView deleteRowsAtIndexPaths:[NSArray arrayWithObject:indexPath] withRowAnimation:UITableViewRowAnimationFade];
        } else {
          //Delete the section
          BOOL needsReload = (indexPath.section < sectionCount);
          //If this is not the last section we need to set indices for all the sections coming in after this that are visible.
          //Otherwise the events will not work properly since the indexPath stored in the cell will be incorrect.

          if (needsReload) {
            NSArray *visibleRows = [tableView indexPathsForVisibleRows];
            [visibleRows enumerateObjectsUsingBlock:^(NSIndexPath *vIndexPath, NSUInteger idx, BOOL *stop) {
              if (vIndexPath.section > indexPath.section) {
                //This belongs to the next section. So set the right indexPath otherwise events wont work properly.
                NSIndexPath *newIndex = [NSIndexPath indexPathForRow:vIndexPath.row inSection:(vIndexPath.section - 1)];
                UITableViewCell *theCell = [tableView cellForRowAtIndexPath:vIndexPath];
                if ([theCell isKindOfClass:[TiUIListItem class]]) {
                  ((TiUIListItem *)theCell).proxy.indexPath = newIndex;
                }
              }
            }];
          }
          NSIndexSet *deleteSet = [NSIndexSet indexSetWithIndex:indexPath.section];
          [tableView deleteSections:deleteSet withRowAnimation:UITableViewRowAnimationFade];
        }
      } else {
        //Just delete the row. Section stays
        [tableView deleteRowsAtIndexPaths:[NSArray arrayWithObject:indexPath] withRowAnimation:UITableViewRowAnimationFade];
      }
    } else {
      //Just delete the row.
      BOOL needsReload = (indexPath.row < [theSection itemCount]);
      //If this is not the last row need to set indices for all rows in the section following this row.
      //Otherwise the events will not work properly since the indexPath stored in the cell will be incorrect.

      if (needsReload) {
        NSArray *visibleRows = [tableView indexPathsForVisibleRows];
        [visibleRows enumerateObjectsUsingBlock:^(NSIndexPath *vIndexPath, NSUInteger idx, BOOL *stop) {
          if ((vIndexPath.section == indexPath.section) && (vIndexPath.row > indexPath.row)) {
            //This belongs to the same section. So set the right indexPath otherwise events wont work properly.
            NSIndexPath *newIndex = [NSIndexPath indexPathForRow:(vIndexPath.row - 1) inSection:(vIndexPath.section)];
            UITableViewCell *theCell = [tableView cellForRowAtIndexPath:vIndexPath];
            if ([theCell isKindOfClass:[TiUIListItem class]]) {
              ((TiUIListItem *)theCell).proxy.indexPath = newIndex;
            }
          }
        }];
      }
      [tableView deleteRowsAtIndexPaths:[NSArray arrayWithObject:indexPath]
                       withRowAnimation:UITableViewRowAnimationFade];
    }
    [tableView endUpdates];
  } else if (editingStyle == UITableViewCellEditingStyleInsert) {
    NSDictionary *theItem = [[theSection itemAtIndex:realIndexPath.row] retain];
    [self fireEditEventWithName:@"insert" andSection:theSection atIndexPath:realIndexPath item:theItem];
    [theItem release];
  }
  [theSection release];
}

#pragma mark - Editing Support Delegate Methods.

- (UITableViewCellEditingStyle)tableView:(UITableView *)tableView editingStyleForRowAtIndexPath:(NSIndexPath *)indexPath
{
  //No support for insert style yet
  if (_editing && [self canEditRowAtIndexPath:indexPath]) {
    return UITableViewCellEditingStyleDelete;
  } else if (_editing && [self canInsertRowAtIndexPath:indexPath] == YES) {
    return UITableViewCellEditingStyleInsert;
  }

  return UITableViewCellEditingStyleNone;
}

- (NSArray *)tableView:(UITableView *)tableView editActionsForRowAtIndexPath:(NSIndexPath *)indexPath
{
  NSIndexPath *realIndexPath = [self pathForSearchPath:indexPath];

  if (![self canEditRowAtIndexPath:realIndexPath]) {
    return nil;
  }

  id editValue = [self valueWithKey:@"editActions" atIndexPath:realIndexPath];

  if (IS_NULL_OR_NIL(editValue)) {
    return nil;
  }

  return [self editActionsFromValue:editValue];
}

- (BOOL)tableView:(UITableView *)tableView shouldIndentWhileEditingRowAtIndexPath:(NSIndexPath *)indexPath
{
  NSIndexPath *realIndexPath = [self pathForSearchPath:indexPath];

  return [self canEditRowAtIndexPath:realIndexPath];
}

- (void)tableView:(UITableView *)tableView willBeginEditingRowAtIndexPath:(NSIndexPath *)indexPath
{
  _editing = YES;
}

- (void)tableView:(UITableView *)tableView didEndEditingRowAtIndexPath:(NSIndexPath *)indexPath
{
  _editing = [_tableView isEditing];
  if (!_editing) {
    [self performSelector:@selector(reloadTableViewData) withObject:nil afterDelay:0.1];
  }
}

#pragma mark - UITableViewDataSource

- (BOOL)tableView:(UITableView *)tableView canMoveRowAtIndexPath:(NSIndexPath *)indexPath
{
  NSIndexPath *realIndexPath = [self pathForSearchPath:indexPath];
  return [self canMoveRowAtIndexPath:realIndexPath];
}

- (void)tableView:(UITableView *)tableView moveRowAtIndexPath:(NSIndexPath *)fromIndexPath toIndexPath:(NSIndexPath *)toIndexPath
{
  NSIndexPath *realToIndexPath = [self pathForSearchPath:toIndexPath];
  NSIndexPath *realFromIndexPath = [self pathForSearchPath:fromIndexPath];

  NSInteger fromSectionIndex = [realFromIndexPath section];
  NSInteger fromRowIndex = [realFromIndexPath row];
  NSInteger toSectionIndex = [realToIndexPath section];
  NSInteger toRowIndex = [realToIndexPath row];

  if (fromSectionIndex == toSectionIndex) {
    if (fromRowIndex == toRowIndex) {
      return;
    }
    //Moving a row in the same index. Just move and reload section
    TiUIListSectionProxy *theSection = [[self.listViewProxy sectionForIndex:fromSectionIndex] retain];
    NSDictionary *theItem = [[theSection itemAtIndex:fromRowIndex] retain];

    //Delete Data
    [theSection deleteItemAtIndex:fromRowIndex];

    //Insert the data
    [theSection addItem:theItem atIndex:toRowIndex];

    //Fire the move Event if required
    NSString *eventName = @"move";
    if ([self.proxy _hasListeners:eventName]) {

      NSMutableDictionary *eventObject = [[NSMutableDictionary alloc] initWithObjectsAndKeys:
                                                                          self.proxy, @"listView",
                                                                      theItem, @"item",
                                                                      theSection, @"section",
                                                                      NUMINTEGER(fromSectionIndex), @"sectionIndex",
                                                                      NUMINTEGER(fromRowIndex), @"itemIndex",
                                                                      theSection, @"targetSection",
                                                                      NUMINTEGER(toSectionIndex), @"targetSectionIndex",
                                                                      NUMINTEGER(toRowIndex), @"targetItemIndex",
                                                                      nil];
      id propertiesValue = [theItem objectForKey:@"properties"];
      NSDictionary *properties = ([propertiesValue isKindOfClass:[NSDictionary class]]) ? propertiesValue : nil;
      id itemId = [properties objectForKey:@"itemId"];
      if (itemId != nil) {
        [eventObject setObject:itemId forKey:@"itemId"];
      }
      [self.proxy fireEvent:eventName withObject:eventObject propagate:NO checkForListener:NO];
      [eventObject release];
    }

    if (searchActive) {
      [self buildResultsForSearchText];
    }

    [tableView reloadData];

    [theSection release];
    [theItem release];

  } else {
    TiUIListSectionProxy *fromSection = [[self.listViewProxy sectionForIndex:fromSectionIndex] retain];
    NSDictionary *theItem = [[fromSection itemAtIndex:fromRowIndex] retain];
    TiUIListSectionProxy *toSection = [[self.listViewProxy sectionForIndex:toSectionIndex] retain];

    //Delete Data
    [fromSection deleteItemAtIndex:fromRowIndex];

    //Insert the data
    [toSection addItem:theItem atIndex:toRowIndex];

    //Fire the move Event if required
    NSString *eventName = @"move";
    if ([self.proxy _hasListeners:eventName]) {

      NSMutableDictionary *eventObject = [[NSMutableDictionary alloc] initWithObjectsAndKeys:
                                                                          fromSection, @"section",
                                                                      theItem, @"item",
                                                                      self.proxy, @"listView",
                                                                      NUMINTEGER(fromSectionIndex), @"sectionIndex",
                                                                      NUMINTEGER(fromRowIndex), @"itemIndex",
                                                                      toSection, @"targetSection",
                                                                      NUMINTEGER(toSectionIndex), @"targetSectionIndex",
                                                                      NUMINTEGER(toRowIndex), @"targetItemIndex",
                                                                      nil];
      id propertiesValue = [theItem objectForKey:@"properties"];
      NSDictionary *properties = ([propertiesValue isKindOfClass:[NSDictionary class]]) ? propertiesValue : nil;
      id itemId = [properties objectForKey:@"itemId"];
      if (itemId != nil) {
        [eventObject setObject:itemId forKey:@"itemId"];
      }
      [self.proxy fireEvent:eventName withObject:eventObject checkForListener:NO];
      [eventObject release];
    }

    if ([fromSection itemCount] == 0) {
      if (pruneSections) {
        [self.listViewProxy deleteSectionAtIndex:fromSectionIndex];
      }
    }

    if (searchActive) {
      [self buildResultsForSearchText];
    }
    [tableView reloadData];

    [fromSection release];
    [toSection release];
    [theItem release];
  }
}

- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView
{
  NSUInteger sectionCount = 0;
  //sometimes while setting props the cells gets loaded and it makes app slow
  //like settings the separatorstyle
  if (!configurationSet) {
    return sectionCount;
  }
  // TIMOB-15526
  if ([searchController isActive] && tableView.backgroundColor == [UIColor clearColor]) {
    tableView.backgroundColor = [UIColor whiteColor];
  }

  if (_searchResults != nil) {
    sectionCount = [_searchResults count];
  } else {
    sectionCount = [self.listViewProxy.sectionCount unsignedIntegerValue];
  }
  return MAX(0, sectionCount);
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section
{
  if ((_searchResults != nil) || (tableView == _searchTableView)) {
    if ([_searchResults count] <= section) {
      return 0;
    }
    NSArray *theSection = [_searchResults objectAtIndex:section];
    return [theSection count];
  } else {
    TiUIListSectionProxy *theSection = [self.listViewProxy sectionForIndex:section];
    if (theSection != nil) {
      return theSection.itemCount;
    }
    return 0;
  }
}

- (UITableViewCell *)forceCellForRowAtIndexPath:(NSIndexPath *)indexPath
{
  return [_tableView cellForRowAtIndexPath:indexPath];
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath
{
  NSIndexPath *realIndexPath = [self pathForSearchPath:indexPath];
  TiUIListSectionProxy *theSection = [self.listViewProxy sectionForIndex:realIndexPath.section];
  NSInteger maxItem = 0;

  if (_searchResults != nil && [_searchResults count] > indexPath.section) {
    NSArray *sectionResults = [_searchResults objectAtIndex:indexPath.section];
    maxItem = [sectionResults count];
  } else {
    maxItem = theSection.itemCount;
  }

  NSDictionary *item = [theSection itemAtIndex:realIndexPath.row];
  id templateId = [item objectForKey:@"template"];
  if (templateId == nil) {
    templateId = _defaultItemTemplate;
  }
  NSString *cellIdentifier = [templateId isKindOfClass:[NSNumber class]] ? [NSString stringWithFormat:@"TiUIListView__internal%@", templateId] : [templateId description];
  TiUIListItem *cell = [tableView dequeueReusableCellWithIdentifier:cellIdentifier];

  TiGroupedListItemPosition position = TiGroupedListItemPositionMiddle;
  BOOL grouped = NO;
  if (tableView.style == UITableViewStyleGrouped) {
    grouped = YES;
    if (indexPath.row == 0) {
      if (maxItem == 1) {
        position = TiGroupedListItemPositionSingleLine;
      } else {
        position = TiGroupedListItemPositionTop;
      }
    } else if (indexPath.row == (maxItem - 1)) {
      position = TiGroupedListItemPositionBottom;
    } else {
      position = TiGroupedListItemPositionMiddle;
    }
  }

  if (cell == nil) {
    id<TiEvaluator> context = self.listViewProxy.executionContext;
    if (context == nil) {
      context = self.listViewProxy.pageContext;
    }
    TiUIListItemProxy *cellProxy = [[TiUIListItemProxy alloc] initWithListViewProxy:self.listViewProxy inContext:context];
    if ([templateId isKindOfClass:[NSNumber class]]) {
      UITableViewCellStyle cellStyle = [templateId unsignedIntegerValue];
      cell = [[TiUIListItem alloc] initWithStyle:cellStyle position:position grouped:grouped reuseIdentifier:cellIdentifier proxy:cellProxy];
    } else {
      [cell configurationStart];
      cell = [[TiUIListItem alloc] initWithProxy:cellProxy position:position grouped:grouped reuseIdentifier:cellIdentifier];
      id template = [self.listViewProxy.templates objectForKey:templateId];
      if (template != nil) {
        [cellProxy unarchiveFromTemplate:template withEvents:YES];
        [cellProxy windowWillOpen];
        [cellProxy windowDidOpen];
        [cellProxy setParentVisible:YES];
      }
      [cell configurationSet];
    }
    cell.delaysContentTouches = tableView.delaysContentTouches;
    cell.delegate = self;

    //we actually need this for longpress hanlding in listview
    UILongPressGestureRecognizer *longPress = [[UILongPressGestureRecognizer alloc] initWithTarget:self action:@selector(recognizedLongPress:)];
    [cell addGestureRecognizer:longPress];
    [longPress release];

    if ([TiUtils isIOS8OrGreater] && (tableView == _tableView)) {
      [cell setLayoutMargins:UIEdgeInsetsZero];
    }

    [cellProxy release];
    [cell autorelease];
  } else {
    [cell setPosition:position isGrouped:grouped];
  }

  if (_rowSeparatorInsets.left != 0 || _rowSeparatorInsets.right != 0) {
    [cell setSeparatorInset:_rowSeparatorInsets];
  }
  cell.dataItem = item;
  cell.proxy.indexPath = realIndexPath;
  //    _canSwipeCells |= [cell canSwipeLeft] || [cell canSwipeRight]];
  return cell;
}

- (NSString *)tableView:(UITableView *)tableView titleForHeaderInSection:(NSInteger)section
{
  if (searchActive) {
    if (keepSectionsInSearch && ([_searchResults count] > 0)) {
      return [[self.listViewProxy sectionForIndex:section] headerTitle];
    } else {
      return nil;
    }
  }

  return [[self.listViewProxy sectionForIndex:section] headerTitle];
}

- (NSString *)tableView:(UITableView *)tableView titleForFooterInSection:(NSInteger)section
{
  if (searchActive) {
    if (keepSectionsInSearch && ([_searchResults count] > 0)) {
      return [[self.listViewProxy sectionForIndex:section] footerTitle];
    } else {
      return nil;
    }
  }
  return [[self.listViewProxy sectionForIndex:section] footerTitle];
}

#pragma mark - MGSwipeTableCell Delegate
- (BOOL)swipeTableCell:(MGSwipeTableCell *)cell canSwipe:(MGSwipeDirection)direction fromPoint:(CGPoint)point
{
  if (!_canSwipeCells || (direction == MGSwipeDirectionLeftToRight && point.x < 30)) {
    return NO;
  }
  if (IS_OF_CLASS(cell, TiUIListItem)) {
    TiUIListItem *listItem = (TiUIListItem *)cell;
    NSIndexPath *indexPath = listItem.proxy.indexPath;
    BOOL isRight = (direction == MGSwipeDirectionRightToLeft);

    id theValue = [self valueWithKey:(isRight ? @"canSwipeRight" : @"canSwipeLeft") atIndexPath:indexPath];
    if (theValue) {
      return [theValue boolValue];
    } else {
      return isRight ? [listItem canSwipeRight] : [listItem canSwipeLeft];
    }
  }
  return NO;
}
- (NSArray *)swipeTableCell:(MGSwipeTableCell *)cell swipeButtonsForDirection:(MGSwipeDirection)direction
               swipeSettings:(MGSwipeSettings *)swipeSettings
           expansionSettings:(MGSwipeExpansionSettings *)expansionSettings
{
  if (!_canSwipeCells) {
    return nil;
  }
  if (IS_OF_CLASS(cell, TiUIListItem)) {
    TiUIListItem *listItem = (TiUIListItem *)cell;
    //        NSIndexPath* indexPath = listItem.proxy.indexPath;
    BOOL isRight = (direction == MGSwipeDirectionRightToLeft);
    id theValue = [listItem.proxy valueForKey:(isRight ? @"rightSwipeButtons" : @"leftSwipeButtons")];
    if (IS_OF_CLASS(theValue, NSArray)) {
      NSMutableArray *buttonViews = [NSMutableArray arrayWithCapacity:[theValue count]];
      [theValue enumerateObjectsUsingBlock:^(id obj, NSUInteger idx, BOOL *stop) {
        if (IS_OF_CLASS(obj, TiViewProxy)) {
          [(TiViewProxy *)obj setCanBeResizedByFrame:YES];
          [(TiViewProxy *)obj setCanRepositionItself:NO];
          //                        if ([(TiViewProxy*)obj viewAttached]) {
          //                            [(TiViewProxy*)obj refreshView];
          //                            [buttonViews addObject:[(TiViewProxy*)obj view]];
          //                        }
          //                        else {
          [buttonViews addObject:[(TiViewProxy *)obj getAndPrepareViewForOpening:listItem.bounds]];
          //                        }
        }
      }];
      theValue = [NSArray arrayWithArray:buttonViews];
    }
    return theValue;
  }
  return nil;
}

- (void)swipeTableCell:(MGSwipeTableCell *)cell didChangeSwipeState:(MGSwipeState)state gestureIsActive:(BOOL)gestureIsActive
{
  if (state != MGSwipeStateNone) {
    _currentSwipeCell = cell;
  } else {
    _currentSwipeCell = nil;
  }
}

- (void)closeSwipeMenu:(id)args
{
  if (!_currentSwipeCell)
    return;
  BOOL animated = YES;
  id value = nil;
  NSNumber *anim = nil;
  KrollCallback *callback = nil;
  ENSURE_ARG_OR_NIL_AT_INDEX(anim, args, 0, NSNumber);
  ENSURE_ARG_AT_INDEX(callback, args, 1, KrollCallback);
  if (_currentSwipeCell) {
    [_currentSwipeCell hideSwipeAnimated:animated
                              completion:^{
                                if (callback) {
                                  [self.proxy _fireEventToListener:@"menuClosed"
                                                        withObject:nil
                                                          listener:callback
                                                        thisObject:nil];
                                }
                              }];
  }
}

#pragma mark - UITableViewDataSourcePrefetching

#if IS_XCODE_8
- (void)tableView:(UITableView *)tableView prefetchRowsAtIndexPaths:(NSArray<NSIndexPath *> *)indexPaths
{
  NSString *eventName = @"prefetch";
  if (![self.proxy _hasListeners:eventName]) {
    return;
  }

  NSMutableArray *cells = [[NSMutableArray arrayWithCapacity:[indexPaths count]] retain];

  for (NSIndexPath *indexPath in indexPaths) {
    [cells addObject:[self listItemFromIndexPath:indexPath]];
  }

  [self.proxy fireEvent:eventName withObject:@{ @"prefetchedItems" : cells }];
  RELEASE_TO_NIL(cells);
}

- (void)tableView:(UITableView *)tableView cancelPrefetchingForRowsAtIndexPaths:(NSArray<NSIndexPath *> *)indexPaths
{
  NSString *eventName = @"cancelprefetch";
  if (![self.proxy _hasListeners:eventName]) {
    return;
  }

  NSMutableArray *cells = [[NSMutableArray arrayWithCapacity:[indexPaths count]] retain];

  for (NSIndexPath *indexPath in indexPaths) {
    [cells addObject:[self listItemFromIndexPath:indexPath]];
  }

  [self.proxy fireEvent:eventName withObject:@{ @"prefetchedItems" : cells }];
  RELEASE_TO_NIL(cells);
}
#endif

- (NSDictionary *)listItemFromIndexPath:(NSIndexPath *)indexPath
{
  NSIndexPath *realIndexPath = [self pathForSearchPath:indexPath];

  TiUIListSectionProxy *section = [self.listViewProxy sectionForIndex:realIndexPath.section];
  NSDictionary *item = [section itemAtIndex:realIndexPath.row];
  NSMutableDictionary *eventObject = [[NSMutableDictionary alloc] initWithObjectsAndKeys:
                                                                      section, @"section",
                                                                  NUMINTEGER(realIndexPath.section), @"sectionIndex",
                                                                  NUMINTEGER(realIndexPath.row), @"itemIndex",
                                                                  nil];
  id propertiesValue = [item objectForKey:@"properties"];
  NSDictionary *properties = ([propertiesValue isKindOfClass:[NSDictionary class]]) ? propertiesValue : nil;
  id itemId = [properties objectForKey:@"itemId"];
  if (itemId != nil) {
    [eventObject setObject:itemId forKey:@"itemId"];
  }

  return [eventObject autorelease];
}

#pragma mark - UITableViewDelegate

- (void)tableView:(UITableView *)tableView willDisplayCell:(UITableViewCell *)cell forRowAtIndexPath:(NSIndexPath *)indexPath
{
  if (searchActive || (tableView != _tableView)) {
    return;
  }
  TiUIListItem *item = (TiUIListItem *)cell;
  NSDictionary *data = item.dataItem;

  BOOL firesOnDisplay = [data objectForKey:@"fireOnDisplay"] && [TiUtils boolValue:[data objectForKey:@"fireOnDisplay"] def:NO];

  if (hasOnDisplayCell || firesOnDisplay) {
    TiUIListSectionProxy *section = [self.listViewProxy sectionForIndex:indexPath.section];
    NSDictionary *propertiesDict = @{
      @"view" : ((TiUIListItem *)cell).proxy,
      @"listView" : self.proxy,
      @"section" : section,
      @"item" : data,
      @"searchResult" : NUMBOOL([self isSearchActive]),
      @"sectionIndex" : NUMINTEGER(indexPath.section),
      @"itemIndex" : NUMINTEGER(indexPath.row)
    };
    if (hasOnDisplayCell) {
      [self.proxy fireCallback:@"onDisplayCell" withArg:propertiesDict withSource:self.proxy];
    }
    if (firesOnDisplay) {
      [self.proxy fireEvent:@"onDisplayItem" withObject:propertiesDict propagate:NO checkForListener:NO];
    }
  }
  if (_useAppearAnimation) {
    if (!_shownIndexes) {
      _shownIndexes = [[NSMutableSet set] retain];
    }
    if (![_shownIndexes containsObject:indexPath]) {
      [_shownIndexes addObject:indexPath];
      id appearAnimation = [data objectForKey:@"appearAnimation"];
      if (!appearAnimation) {
        appearAnimation = _appearAnimation;
      }
      TiViewProxy *itemProxy = item.proxy;
      TiAnimation *newAnimation = [TiAnimation animationFromArg:appearAnimation context:[itemProxy executionContext] create:NO];
      //            newAnimation.dontApplyOnFinish = YES;
      [itemProxy handleAnimation:newAnimation];
    }
  }

  //Tell the proxy about the cell to be displayed
  [self.listViewProxy willDisplayCell:indexPath];
}

- (UIView *)tableView:(UITableView *)tableView viewForHeaderInSection:(NSInteger)section
{
  if (tableView != _tableView) {
    return nil;
  }

  NSInteger realSection = section;
  if (searchActive) {
    if (keepSectionsInSearch && ([_searchResults count] > 0)) {
      realSection = [self sectionForSearchSection:section];
    } else {
      return nil;
    }
  }

  TiUIListSectionProxy *sectionProxy = [self.listViewProxy sectionForIndex:realSection];
  if ([sectionProxy isHidden] || [sectionProxy.headerTitle length] > 0) {
    return nil;
  }

  return [self sectionView:realSection forLocation:@"headerView" section:nil];
}

- (UIView *)tableView:(UITableView *)tableView viewForFooterInSection:(NSInteger)section
{
  if (tableView != _tableView) {
    return nil;
  }

  NSInteger realSection = section;
  if (searchActive) {
    if (keepSectionsInSearch && ([_searchResults count] > 0)) {
      realSection = [self sectionForSearchSection:section];
    } else {
      return nil;
    }
  }
  TiUIListSectionProxy *sectionProxy = [self.listViewProxy sectionForIndex:realSection];
  if ([sectionProxy isHidden] || [sectionProxy.footerTitle length] > 0) {
    return nil;
  }

  return [self sectionView:realSection forLocation:@"footerView" section:nil];
}

#define DEFAULT_SECTION_HEADERFOOTER_HEIGHT 29.0

- (CGFloat)tableView:(UITableView *)tableView heightForHeaderInSection:(NSInteger)section
{
  NSInteger realSection = section;

  if (searchActive) {
    if (keepSectionsInSearch && ([_searchResults count] > 0)) {
      realSection = section;
    } else {
      return 0.0;
    }
  }

  TiUIListSectionProxy *sectionProxy = [self.listViewProxy sectionForIndex:realSection];
  if ([sectionProxy isHidden]) {
    return 0.0;
  }
  if ([sectionProxy.headerTitle length] > 0) {
    return [tableView sectionHeaderHeight];
  }
  TiViewProxy *viewProxy = [sectionProxy sectionViewForLocation:@"headerView" inListView:self];

  CGFloat size = 0.0;
  if (viewProxy != nil) {
    size += [viewProxy minimumParentSizeForSize:[self.tableView bounds].size].height;
    //        [viewProxy getAndPrepareViewForOpening:[self.tableView bounds]]; //to make sure it is setup
    //        size += [[viewProxy view] bounds].size.height;
    //        LayoutConstraint *viewLayout = [viewProxy layoutProperties];
    //        TiDimension constraint =  TiDimensionIsUndefined(viewLayout->height)?[viewProxy defaultAutoHeightBehavior:nil]:viewLayout->height;
    //        switch (constraint.type)
    //        {
    //            case TiDimensionTypeDip:
    //                size += constraint.value;
    //                break;
    //            case TiDimensionTypeAuto:
    //            case TiDimensionTypeAutoSize:
    //                size += [viewProxy minimumParentSizeForSizeNoPadding:[self.tableView bounds].size].height;
    //                break;
    //            default:
    //                size+=DEFAULT_SECTION_HEADERFOOTER_HEIGHT;
    //                break;
    //        }
  }
  return size;
}

- (CGFloat)tableView:(UITableView *)tableView heightForFooterInSection:(NSInteger)section
{
  NSInteger realSection = section;

  if (searchActive) {
    if (keepSectionsInSearch && ([_searchResults count] > 0)) {
      realSection = section;
    } else {
      return 0.0;
    }
  }

  TiUIListSectionProxy *sectionProxy = [self.listViewProxy sectionForIndex:realSection];
  if ([sectionProxy isHidden]) {
    return 0.0;
  }
  if ([sectionProxy.footerTitle length] > 0) {
    return [tableView sectionFooterHeight];
  }
  TiViewProxy *viewProxy = [sectionProxy sectionViewForLocation:@"footerView" inListView:self];

  CGFloat size = 0.0;
  if (viewProxy != nil) {
    size += [viewProxy minimumParentSizeForSize:[self.tableView bounds].size].height;
    //        [viewProxy getAndPrepareViewForOpening:[self.tableView bounds]]; //to make sure it is setup
    //        size += [[viewProxy view] bounds].size.height;
    //        [viewProxy getAndPrepareViewForOpening:[self.tableView bounds]]; //to make sure it is setup
    //        LayoutConstraint *viewLayout = [viewProxy layoutProperties];
    //        TiDimension constraint =  TiDimensionIsUndefined(viewLayout->height)?[viewProxy defaultAutoHeightBehavior:nil]:viewLayout->height;
    //        switch (constraint.type)
    //        {
    //            case TiDimensionTypeDip:
    //                size += constraint.value;
    //                break;
    //            case TiDimensionTypeAuto:
    //            case TiDimensionTypeAutoSize:
    //                size += [viewProxy minimumParentSizeForSize:[self.tableView bounds].size].height;
    //                break;
    //            default:
    //                size+=DEFAULT_SECTION_HEADERFOOTER_HEIGHT;
    //                break;
    //        }
  }
  return size;
}

- (CGFloat)computeRowWidth
{
  CGFloat rowWidth = _tableView.bounds.size.width;
  if (rowWidth == 0) {
    return rowWidth;
  }

  // Apple does not provide a good way to get information about the index sidebar size
  // in the event that it exists - it silently resizes row content which is "flexible width"
  // but this is not useful for us. This is a problem when we have Ti.UI.SIZE/FILL behavior
  // on row contents, which rely on the height of the row to be accurately precomputed.
  //
  // The following is unreliable since it uses a private API name, but one which has existed
  // since iOS 3.0. The alternative is to grab a specific subview of the tableview itself,
  // which is more fragile.

  NSArray *subviews = [_tableView subviews];
  if ([subviews count] > 0) {
    // Obfuscate private class name
    Class indexview = NSClassFromString([@"UITableView" stringByAppendingString:@"Index"]);
    for (UIView *view in subviews) {
      if ([view isKindOfClass:indexview]) {
        rowWidth -= [view frame].size.width;
      }
    }
  }

  return rowWidth;
}

- (CGFloat)tableView:(UITableView *)tableView rowHeight:(CGFloat)height
{
  if (TiDimensionIsDip(_minRowHeight)) {
    height = MAX(_minRowHeight.value, height);
  }
  if (TiDimensionIsDip(_maxRowHeight)) {
    height = MIN(_maxRowHeight.value, height);
  }
  return height < 1 ? tableView.rowHeight : height;
}

- (NSMutableArray *)visibleCellsProxies
{
  NSArray *visibleCells = _tableView.visibleCells;
  if (!visibleCells)
    return nil;
  NSMutableArray *result = [NSMutableArray arrayWithCapacity:[visibleCells count]];
  [visibleCells enumerateObjectsUsingBlock:^(TiUIListItem *obj, NSUInteger idx, BOOL *stop) {
    if ([obj isKindOfClass:[TiUIListItem class]]) {
      [result addObject:((TiUIListItem *)obj).proxy];
    }

  }];
  return result;
}

- (TiUIListItem *)visibleCellAtIndexPath:(NSIndexPath *)indexPath
{
  NSArray *visibleCells = _tableView.visibleCells;
  if (!visibleCells)
    return nil;
  __block TiUIListItem *result = nil;
  [visibleCells enumerateObjectsUsingBlock:^(TiUIListItem *obj, NSUInteger idx, BOOL *stop) {
    if ([obj isKindOfClass:[TiUIListItem class]]) {
      if ([obj.proxy.indexPath compare:indexPath] == NSOrderedSame) {
        result = obj;
        *stop = YES;
      }
    }

  }];
  return result;
}

- (CGFloat)tableView:(UITableView *)tableView heightForRowAtIndexPath:(NSIndexPath *)indexPath
{
  if (_tableView.bounds.size.width == 0) {
    return 0;
  }
  NSIndexPath *realIndexPath = [self pathForSearchPath:indexPath];

  id visibleProp = [self valueWithKey:@"visible" atIndexPath:realIndexPath];
  BOOL visible = realIndexPath ? [visibleProp boolValue] : true;
  if (!visible)
    return 0.0f;

  id heightValue = [self valueWithKey:@"rowHeight" atIndexPath:realIndexPath];
  TiDimension height = _rowHeight;
  if (heightValue != nil) {
    height = [TiUtils dimensionValue:heightValue];
  }
  if (TiDimensionIsDip(height)) {
    return [self tableView:tableView rowHeight:height.value];
  }

  else if (TiDimensionIsAuto(height) || TiDimensionIsAutoSize(height)) {
    TiUIListSectionProxy *theSection = [self.listViewProxy sectionForIndex:realIndexPath.section];
    NSDictionary *item = [theSection itemAtIndex:realIndexPath.row];
    id templateId = [item objectForKey:@"template"];
    if (templateId == nil) {
      templateId = _defaultItemTemplate;
    }
    TiUIListItemProxy *cellProxy = nil;
    //        TiUIListItem* visibleCell = [self visibleCellAtIndexPath:realIndexPath];
    //        if (visibleCell) {
    //            cellProxy = [((TiUIListItem*)visibleCell) proxy];
    //        }
    if (!cellProxy) {
      cellProxy = [self.listViewProxy.measureProxies objectForKey:templateId];
    }
    if (cellProxy != nil) {
      CGFloat width = [cellProxy sizeWidthForDecorations:[self computeRowWidth] forceResizing:YES];
      if (width > 0) {
        [cellProxy setDataItem:item];
        return [self tableView:tableView rowHeight:[cellProxy minimumParentSizeForSize:CGSizeMake(width, 0)].height];
      }
    }
  } else if (TiDimensionIsPercent(height) || TiDimensionIsAutoFill(height)) {
    return [self tableView:tableView rowHeight:TiDimensionCalculateValue(height, tableView.bounds.size.height)];
  }
  return 44;
}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath
{
  if (!_editing && allowsSelection == NO) {
    [tableView deselectRowAtIndexPath:indexPath animated:YES];
  }
  [self fireClickForItemAtIndexPath:[self pathForSearchPath:indexPath] tableView:tableView accessoryButtonTapped:NO];
}

- (void)tableView:(UITableView *)tableView didDeselectRowAtIndexPath:(NSIndexPath *)indexPath
{
  if (_editing) {
    [self fireClickForItemAtIndexPath:[self pathForSearchPath:indexPath] tableView:tableView accessoryButtonTapped:NO];
  }
}

- (void)tableView:(UITableView *)tableView accessoryButtonTappedForRowWithIndexPath:(NSIndexPath *)indexPath
{
  if (!_editing && allowsSelection == NO) {
    [tableView deselectRowAtIndexPath:indexPath animated:YES];
  }
  [self fireClickForItemAtIndexPath:[self pathForSearchPath:indexPath] tableView:tableView accessoryButtonTapped:YES];
}

#pragma mark - ScrollView Delegate

- (void)detectSectionChange
{
  NSArray *visibles = [_tableView indexPathsForVisibleRows];
  NSIndexPath *indexPath = [visibles firstObject];
  NSInteger section = [indexPath section];
  if (_currentSection != section) {
    _currentSection = section;
    if ([[self viewProxy] _hasListeners:@"headerchange" checkParent:NO]) {
      NSMutableDictionary *event = [self EventObjectForItemAtIndexPath:indexPath tableView:_tableView];
      [event setObject:NUMINTEGER(indexPath.row) forKey:@"firstVisibleItem"];
      [event setObject:NUMINTEGER([visibles count]) forKey:@"visibleItemCount"];
      TiViewProxy *headerView = [self currentSectionViewProxy:_currentSection forLocation:@"headerView"];
      if (headerView) {
        [event setObject:headerView forKey:@"headerView"];
      }
      [self.proxy fireEvent:@"headerchange" withObject:event checkForListener:NO];
    }
  }
}

- (NSMutableDictionary *)eventObjectForScrollView:(UIScrollView *)scrollView
{
  NSMutableDictionary *eventArgs = [super eventObjectForScrollView:scrollView];
  NSArray *indexPaths = [_tableView indexPathsForVisibleRows];
  NSUInteger visibleItemCount = [indexPaths count];
  TiUIListSectionProxy *section;
  if ([indexPaths count] > 0) {
    NSIndexPath *indexPath = [self pathForSearchPath:[indexPaths objectAtIndex:0]];
    section = [[self listViewProxy] sectionForIndex:[indexPath section]];

    [eventArgs setValue:NUMINTEGER([indexPath row]) forKey:@"firstVisibleItemIndex"];
    [eventArgs setValue:NUMUINTEGER(visibleItemCount) forKey:@"visibleItemCount"];
    [eventArgs setValue:NUMINTEGER([indexPath section]) forKey:@"firstVisibleSectionIndex"];
    [eventArgs setValue:section forKey:@"firstVisibleSection"];
    [eventArgs setValue:[section itemAtIndex:[indexPath row]] forKey:@"firstVisibleItem"];
  } else {
    section = [[self listViewProxy] sectionForIndex:0];

    [eventArgs setValue:NUMINTEGER(-1) forKey:@"firstVisibleItemIndex"];
    [eventArgs setValue:NUMUINTEGER(0) forKey:@"visibleItemCount"];
    [eventArgs setValue:NUMINTEGER(0) forKey:@"firstVisibleSectionIndex"];
    [eventArgs setValue:section forKey:@"firstVisibleSection"];
    [eventArgs setValue:NUMINTEGER(-1) forKey:@"firstVisibleItem"];
  }
  return eventArgs;
}

- (void)scrollViewDidScroll:(UIScrollView *)scrollView
{
  [super scrollViewDidScroll:scrollView];

  [self detectSectionChange];
}

- (void)scrollViewWillBeginDragging:(UIScrollView *)scrollView
{
  if ([self isLazyLoadingEnabled]) {
    [[ImageLoader sharedLoader] suspend];
  }
  [super scrollViewWillBeginDragging:scrollView];
}

- (void)scrollViewWillEndDragging:(UIScrollView *)scrollView withVelocity:(CGPoint)velocity targetContentOffset:(inout CGPoint *)targetContentOffset
{
  if ([[self proxy] _hasListeners:@"scrolling"]) {
    NSString *direction = nil;

    if (velocity.y > 0) {
      direction = @"up";
    }

    if (velocity.y < 0) {
      direction = @"down";
    }

    NSMutableDictionary *event = [NSMutableDictionary dictionaryWithDictionary:@{
      @"targetContentOffset" : NUMFLOAT(targetContentOffset->y),
      @"velocity" : NUMFLOAT(velocity.y)
    }];
    if (direction != nil) {
      [event setValue:direction forKey:@"direction"];
    }

    [[self proxy] fireEvent:@"scrolling" withObject:event];
    RELEASE_TO_NIL(direction);
  }
}

- (void)scrollViewDidEndDragging:(UIScrollView *)scrollView willDecelerate:(BOOL)decelerate
{
  if (!decelerate) {
    [self detectSectionChange];
    if ([self isLazyLoadingEnabled]) {
      [[ImageLoader sharedLoader] resume];
    }
  }
  [self detectSectionChange];
  [super scrollViewDidEndDragging:scrollView willDecelerate:decelerate];
}

- (void)scrollViewDidEndDecelerating:(UIScrollView *)scrollView
{
  if ([self isLazyLoadingEnabled]) {
    [[ImageLoader sharedLoader] resume];
  }
  [self detectSectionChange];
  [super scrollViewDidEndDecelerating:scrollView];
}

- (BOOL)scrollViewShouldScrollToTop:(UIScrollView *)scrollView
{
  if ([self isLazyLoadingEnabled]) {
    [[ImageLoader sharedLoader] suspend];
  }
  return [super scrollViewShouldScrollToTop:scrollView];
}

- (void)scrollViewDidScrollToTop:(UIScrollView *)scrollView
{
  if ([self isLazyLoadingEnabled]) {
    [[ImageLoader sharedLoader] resume];
  }
  return [super scrollViewDidScrollToTop:scrollView];
  //Events none (maybe scroll later)
}

#pragma mark Overloaded view handling
- (UIView *)viewForHitTest
{
  return _tableView;
}

//- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event
//{
//	UIView * result = [super hitTest:point withEvent:event];
//	if(result == self)
//	{	//There is no valid reason why the TiUITableView will get an
//		//touch event; it should ALWAYS be a child view.
//		return nil;
//	}
//	return result;
//}

- (void)touchesBegan:(NSSet *)touches withEvent:(UIEvent *)event
{
  // iOS idiom seems to indicate that you should never be able to interact with a table
  // while the 'delete' button is showing for a row, but touchesBegan:withEvent: is still triggered.
  // Turn it into a no-op while we're editing
  if (!_editing) {
    [super touchesBegan:touches withEvent:event];
  }
}

- (void)recognizedSwipe:(UISwipeGestureRecognizer *)recognizer
{
  BOOL viaSearch = [self isSearchActive];
  UITableView *theTableView = viaSearch ? [resultViewController tableView] : [self tableView];
  CGPoint point = [recognizer locationInView:theTableView];
  NSIndexPath *indexPath = [theTableView indexPathForRowAtPoint:point];
  [super recognizedSwipe:recognizer];

  if (allowsSelection == NO) {
    [theTableView deselectRowAtIndexPath:indexPath animated:YES];
  }
}

- (NSMutableDictionary *)dictionaryFromGesture:(UIGestureRecognizer *)recognizer
{
  NSMutableDictionary *event = [super dictionaryFromGesture:recognizer];

  BOOL viaSearch = [self isSearchActive];
  UITableView *theTableView = viaSearch ? [resultViewController tableView] : [self tableView];
  CGPoint point = [recognizer locationInView:theTableView];
  NSIndexPath *indexPath = [theTableView indexPathForRowAtPoint:point];
  indexPath = [self pathForSearchPath:indexPath];
  if (indexPath != nil) {
    [event setValuesForKeysWithDictionary:[self EventObjectForItemAtIndexPath:indexPath tableView:theTableView atPoint:point]];
  };
  return event;
}

- (void)recognizedLongPress:(UILongPressGestureRecognizer *)recognizer
{
  [super recognizedLongPress:recognizer];
  if ([recognizer state] == UIGestureRecognizerStateBegan) {
    BOOL viaSearch = [self isSearchActive];
    UITableView *theTableView = viaSearch ? [resultViewController tableView] : [self tableView];
    CGPoint point = [recognizer locationInView:theTableView];
    NSIndexPath *indexPath = [theTableView indexPathForRowAtPoint:point];
    if (allowsSelection == NO) {
      [theTableView deselectRowAtIndexPath:indexPath animated:YES];
    }
    TiUISearchBarProxy *searchViewProxy = (TiUISearchBarProxy *)[self holdedProxyForKey:@"searchView"];
    if (viaSearch && searchViewProxy) {
      if (hideOnSearch) {
        [self hideSearchScreen:nil];
      } else {
        /*
                 TIMOB-7397. Observed that `searchBarTextDidBeginEditing` delegate
                 method was being called on screen transition which was causing a
                 visual glitch. Checking for isFirstResponder at this point always
                 returns false. Calling blur here so that the UISearchBar resigns
                 as first responder on main thread
                 */
        [searchViewProxy performSelector:@selector(blur:) withObject:nil];
      }
    }
  }
}

#pragma mark - UISearchBarDelegate Methods
- (BOOL)searchBarShouldBeginEditing:(UISearchBar *)searchBar
{
  TiViewProxy *vp = [self holdedProxyForKey:@"searchView"];
  if (vp) {
#ifndef TI_USE_AUTOLAYOUT
    [vp layoutProperties]->right = TiDimensionDip(0);
#endif
    [vp refreshViewIfNeeded];
    [self initSearchController:self];
  }
}

- (void)searchBarTextDidBeginEditing:(UISearchBar *)searchBar
{
  if ([searchBar.text isEqualToString:self.searchString] && [searchController isActive]) {
    return;
  }
  self.searchString = (searchBar.text == nil) ? @"" : searchBar.text;
  [self buildResultsForSearchText];
  if (searchController.isActive) {
    [[resultViewController tableView] reloadData];
  } else {
    [_tableView reloadData];
  }
}

- (void)searchBarTextDidEndEditing:(UISearchBar *)searchBar
{
  if ([searchBar.text length] == 0) {
    self.searchString = @"";
    [self buildResultsForSearchText];
    if ([[self searchController] isActive]) {
      [[self searchController] setActive:NO];
    }
  }
}

- (void)searchBar:(UISearchBar *)searchBar textDidChange:(NSString *)searchText
{
  self.searchString = (searchText == nil) ? @"" : searchText;
  [self buildResultsForSearchText];
  if (!searchActive || !resultViewController) {
    // Reload since some cells could be reused as part of previous search.
    [self reloadTableViewData];
  }
}

- (void)searchBarSearchButtonClicked:(UISearchBar *)searchBar
{
  [searchBar resignFirstResponder];
  [self makeRootViewFirstResponder];
}

- (void)searchBarCancelButtonClicked:(UISearchBar *)searchBar
{
  self.searchString = @"";
  [searchBar setText:self.searchString];
  [self buildResultsForSearchText];
  if (!searchActive || !resultViewController) {
    // Reload since some cells could be reused as part of previous search.
    [self reloadTableViewData];
  }
}
#pragma mark - UISearchControllerDelegate
- (void)willDismissSearchController:(UISearchController *)searchController
{
  [[resultViewController tableView] setEditing:NO];
  [_tableView setEditing:NO];
}

- (void)didDismissSearchController:(UISearchController *)searchController
{
  searchViewAnimating = NO;
  self.searchString = @"";
  [self buildResultsForSearchText];

  //IOS7 DP3. TableView seems to be adding the searchView to
  //tableView. Bug on IOS7?
  TiUISearchBarProxy *searchViewProxy = (TiUISearchBarProxy *)[self holdedProxyForKey:@"searchView"];
  if (searchViewProxy) {
    CGFloat rowWidth = floorf([self computeRowWidth:_tableView]);
    if (rowWidth > 0) {
      CGFloat right = _tableView.bounds.size.width - rowWidth;
#ifndef TI_USE_AUTOLAYOUT
      [searchViewProxy layoutProperties]->right = TiDimensionDip(right);
#endif
      [searchViewProxy refreshViewIfNeeded];
    }
    [searchViewProxy ensureSearchBarHierarchy];
  }
  //  [self clearSearchController:self];
  [self reloadTableViewData];
  [self hideSearchScreen:nil];

  id searchButtonTitle = [searchViewProxy valueForKey:@"cancelButtonTitle"];
  ENSURE_TYPE_OR_NIL(searchButtonTitle, NSString);

  if (!searchButtonTitle) {
    return;
  }

  // TODO: Use [UIBarButtonItem appearanceWhenContainedInInstancesOfClasses:@[[UISearchBar class]]];
  // as soon as we remove iOS < 9 support
  id searchButton = searchButton = [UIBarButtonItem appearanceWhenContainedIn:[UISearchBar class], nil];
  [searchButton setTitle:[TiUtils stringValue:searchButtonTitle]];
  //    [self performSelector:@selector(hideSearchScreen:) withObject:nil afterDelay:0.2];
}

- (void)presentSearchController:(UISearchController *)controller
{
  TiColor *resultsBackgroundColor = [TiUtils colorValue:[[self proxy] valueForKey:@"resultsBackgroundColor"]];
  TiColor *resultsSeparatorColor = [TiUtils colorValue:[[self proxy] valueForKey:@"resultsSeparatorColor"]];
  id resultsSeparatorInsets = [[self proxy] valueForKey:@"resultsSeparatorInsets"];
  id resultsSeparatorStyle = [[self proxy] valueForKey:@"resultsSeparatorStyle"];

  ENSURE_TYPE_OR_NIL(resultsSeparatorInsets, NSDictionary);
  ENSURE_TYPE_OR_NIL(resultsSeparatorStyle, NSNumber);

  if (resultsBackgroundColor) {
    // TIMOB-23281: Hack to support transparent backgrounds (not officially supported)
    UIColor *color = [resultsBackgroundColor _color] == [UIColor clearColor] ? [UIColor colorWithWhite:1.0 alpha:0.0001] : [resultsBackgroundColor _color];
    [resultViewController.tableView setBackgroundColor:color];
  }

  if (resultsSeparatorColor) {
    [resultViewController.tableView setSeparatorColor:[resultsSeparatorColor _color]];
  }

  if (resultsSeparatorInsets) {
    [resultViewController.tableView setSeparatorInset:[TiUtils contentInsets:resultsSeparatorInsets]];
  }

  if (resultsSeparatorStyle) {
    [resultViewController.tableView setSeparatorStyle:[TiUtils intValue:resultsSeparatorStyle def:UITableViewCellSeparatorStyleSingleLine]];
  }

  // Presenting search controller on window holding controller
  id theProxy = [(TiViewProxy *)self.proxy parent];
  while ([theProxy isKindOfClass:[TiViewProxy class]] && ![proxy isKindOfClass:[TiWindowProxy class]]) {
    theProxy = [theProxy parent];
  }
  UIViewController *viewController = nil;
  if ([theProxy isKindOfClass:[TiWindowProxy class]]) {
    viewController = [theProxy windowHoldingController];
  } else {
    viewController = [[TiApp app] controller];
  }
  viewController.definesPresentationContext = YES;

  [viewController presentViewController:controller
                               animated:NO
                             completion:^{
                               UIView *view = controller.searchBar.superview;
                               view.frame = CGRectMake(view.frame.origin.x, self.frame.origin.y, view.frame.size.width, view.frame.size.height);
                               controller.searchBar.frame = CGRectMake(0, 0, view.frame.size.width, view.frame.size.height);
                               resultViewController.tableView.frame = CGRectMake(self.frame.origin.x, self.frame.origin.y + view.frame.size.height, self.frame.size.width, self.frame.size.height);
                             }];

  TiUISearchBarProxy *searchViewProxy = (TiUISearchBarProxy *)[self holdedProxyForKey:@"searchView"];
  id searchButtonTitle = [searchViewProxy valueForKey:@"cancelButtonTitle"];
  ENSURE_TYPE_OR_NIL(searchButtonTitle, NSString);

  if (!searchButtonTitle) {
    return;
  }

  // TODO: Use [UIBarButtonItem appearanceWhenContainedInInstancesOfClasses:@[[UISearchBar class]]];
  // as soon as we remove iOS < 9 support
  id searchButton = searchButton = [UIBarButtonItem appearanceWhenContainedIn:[UISearchBar class], nil];
  [searchButton setTitle:[TiUtils stringValue:searchButtonTitle]];
  [[resultViewController tableView] setEditing:NO];
  [_tableView setEditing:NO];
}

#pragma mark - UISearchResultsUpdating

- (void)updateSearchResultsForSearchController:(UISearchController *)controller
{
  self.searchString = [controller.searchBar text];
  [self buildResultsForSearchText];
  if (controller.isActive) {
    [resultViewController.tableView reloadData];
  } else {
    [_tableView reloadData];
  }
}
#pragma mark - TiScrolling

- (void)keyboardDidShowAtHeight:(CGFloat)keyboardTop
{
  CGRect minimumContentRect = [_tableView bounds];
  InsetScrollViewForKeyboard(_tableView, keyboardTop, minimumContentRect.size.height + minimumContentRect.origin.y);
}

- (void)scrollToShowView:(UIView *)firstResponderView withKeyboardHeight:(CGFloat)keyboardTop
{
  if ([_tableView isScrollEnabled]) {
    CGRect minimumContentRect = [_tableView bounds];
    CGRect responderRect = [self convertRect:[firstResponderView bounds] fromView:firstResponderView];
    CGPoint offsetPoint = [_tableView contentOffset];
    responderRect.origin.x += offsetPoint.x;
    responderRect.origin.y += offsetPoint.y;

    OffsetScrollViewForRect(_tableView, keyboardTop, minimumContentRect.size.height + minimumContentRect.origin.y, responderRect);
  }
}

#pragma mark - Internal Methods

- (BOOL)isLazyLoadingEnabled
{
  return _scrollSuspendImageLoading;
  //    return [TiUtils boolValue: [[self proxy] valueForKey:@"lazyLoadingEnabled"] def:YES];
}

- (NSMutableDictionary *)EventObjectForItemAtIndexPath:(NSIndexPath *)indexPath tableView:(UITableView *)tableView atPoint:(CGPoint)point accessoryButtonTapped:(BOOL)accessoryButtonTapped
{
  TiUIListSectionProxy *section = [self.listViewProxy sectionForIndex:indexPath.section];
  NSDictionary *item = [section itemAtIndex:indexPath.row];
  NSMutableDictionary *eventObject = [[NSMutableDictionary alloc] initWithObjectsAndKeys:
                                                                      section, @"section",
                                                                  self.proxy, @"listView",
                                                                  @(_editing), @"editing",
                                                                  @([self isSearchActive]), @"searchResult",
                                                                  @(indexPath.section), @"sectionIndex",
                                                                  item, @"item",
                                                                  @(indexPath.row), @"itemIndex",
                                                                  @(accessoryButtonTapped), @"accessoryClicked",
                                                                  nil];
  id propertiesValue = [item objectForKey:@"properties"];
  NSDictionary *properties = ([propertiesValue isKindOfClass:[NSDictionary class]]) ? propertiesValue : nil;
  id itemId = [properties objectForKey:@"itemId"];
  if (itemId != nil) {
    [eventObject setObject:itemId forKey:@"itemId"];
  }
  TiUIListItem *cell = (TiUIListItem *)[tableView cellForRowAtIndexPath:indexPath];

  if (cell.templateStyle == TiUIListItemTemplateStyleCustom) {
    UIView *contentView = cell.contentView;
    CGPoint convertedPoint = [tableView convertPoint:point toView:contentView];
    // if searchController is active, tableView.contentOffset.y = -44
    // else tableView.contentOffset.y = 0
    if ([searchController isActive]) {
      convertedPoint.y = convertedPoint.y + tableView.contentOffset.y;
    }
    TiViewProxy *tapViewProxy = [TiUIHelper findViewProxyWithBindIdUnder:contentView containingPoint:convertedPoint];
    if (tapViewProxy != nil && tapViewProxy.bindId) {
      [eventObject setObject:tapViewProxy.bindId forKey:@"bindId"];
    }
  }
  return [eventObject autorelease];
}

- (NSMutableDictionary *)EventObjectForItemAtIndexPath:(NSIndexPath *)indexPath tableView:(UITableView *)tableView
{
  return [self EventObjectForItemAtIndexPath:indexPath tableView:tableView atPoint:[_tableView touchPoint]];
}

- (NSMutableDictionary *)EventObjectForItemAtIndexPath:(NSIndexPath *)indexPath tableView:(UITableView *)tableView atPoint:(CGPoint)point
{
  NSMutableDictionary *event = [self EventObjectForItemAtIndexPath:indexPath tableView:tableView atPoint:point accessoryButtonTapped:NO];
  [event setObject:@(point.x) forKey:@"x"];
  [event setObject:@(point.y) forKey:@"y"];
  return event;
}

- (void)fireClickForItemAtIndexPath:(NSIndexPath *)indexPath tableView:(UITableView *)tableView accessoryButtonTapped:(BOOL)accessoryButtonTapped
{
  NSString *eventName = @"itemclick";
  if (![self.proxy _hasListeners:eventName]) {
    return;
  }

  [self.proxy fireEvent:eventName withObject:[self EventObjectForItemAtIndexPath:indexPath tableView:tableView] checkForListener:NO];
}

- (NSIndexPath *)nextIndexPath:(NSIndexPath *)indexPath
{
  NSInteger numOfSections = [self numberOfSectionsInTableView:self.tableView];
  if (numOfSections == 0)
    return nil;
  NSInteger nextSection = ((indexPath.section + 1) % numOfSections);

  if (indexPath.row + 1 == [self tableView:self.tableView numberOfRowsInSection:indexPath.section]) {
    return [NSIndexPath indexPathForRow:0 inSection:nextSection];
  } else {
    return [NSIndexPath indexPathForRow:(indexPath.row + 1) inSection:indexPath.section];
  }
}

//- (void)clearSearchController:(id)sender
//{
//  if (sender == self) {
//    TiUISearchBarProxy *searchViewProxy = (TiUISearchBarProxy *)[self holdedProxyForKey:@"searchView"];
//    if (!searchViewProxy.canHaveSearchDisplayController) {
//      //      RELEASE_TO_NIL(tableController);
//      RELEASE_TO_NIL(resultViewController);
//      RELEASE_TO_NIL(searchController);
//      [searchViewProxy ensureSearchBarHierarchy];
//    }
//  }
//}

- (void)initSearchController:(id)sender
{
  if (sender == self && searchController == nil) {
    TiUISearchBarProxy *searchViewProxy = (TiUISearchBarProxy *)[self holdedProxyForKey:@"searchView"];
    if (!searchViewProxy.canHaveSearchDisplayController) {
      resultViewController = [[UITableViewController alloc] init];
      resultViewController.tableView = [self searchTableView];
      searchController = [[[UISearchController alloc] initWithSearchResultsController:resultViewController] retain];
      searchController.delegate = self;
      searchController.searchResultsUpdater = self;
      searchController.hidesNavigationBarDuringPresentation = NO;
      searchController.dimsBackgroundDuringPresentation = _dimsBackgroundDuringPresentation;

      searchController.searchBar.frame = CGRectMake(searchController.searchBar.frame.origin.x, searchController.searchBar.frame.origin.y, 0, 44.0);
      searchController.searchBar.autoresizingMask = UIViewAutoresizingFlexibleWidth;
      searchController.searchBar.placeholder = [[searchViewProxy searchBar] placeholder];
      searchController.searchBar.text = [[searchViewProxy searchBar] text];
      [searchViewProxy setSearchBar:searchController.searchBar];

      [TiUtils configureController:resultViewController withObject:self.proxy];
      [TiUtils configureController:searchController withObject:self.proxy];
    }
  }
}

- (void)fireEditEventWithName:(NSString *)name andSection:(TiUIListSectionProxy *)section atIndexPath:(NSIndexPath *)indexPath item:(NSDictionary *)item
{
  //Fire the delete Event if required
  if ([self.proxy _hasListeners:name]) {

    NSMutableDictionary *eventObject = [[NSMutableDictionary alloc] initWithObjectsAndKeys:
                                                                        section, @"section",
                                                                    NUMINTEGER(indexPath.section), @"sectionIndex",
                                                                    NUMINTEGER(indexPath.row), @"itemIndex",
                                                                    nil];
    id propertiesValue = [item objectForKey:@"properties"];
    NSDictionary *properties = ([propertiesValue isKindOfClass:[NSDictionary class]]) ? propertiesValue : nil;
    id itemId = [properties objectForKey:@"itemId"];
    if (itemId != nil) {
      [eventObject setObject:itemId forKey:@"itemId"];
    }
    [self.proxy fireEvent:name withObject:eventObject withSource:self.proxy propagate:NO reportSuccess:NO errorCode:0 message:nil];
    [eventObject release];
  }
}

#pragma mark - UITapGestureRecognizer

- (BOOL)gestureRecognizerShouldBegin:(UIGestureRecognizer *)gestureRecognizer
{
  //	tapPoint = [gestureRecognizer locationInView:gestureRecognizer.view];
  return NO;
}

- (void)handleTap:(UITapGestureRecognizer *)tapGestureRecognizer
{
  // Never called
}

#pragma mark - Static Methods

+ (void)setBackgroundColor:(UIColor *)bgColor onTable:(UITableView *)table
{
  UIColor *defaultColor = [table style] == UITableViewStylePlain ? [UIColor whiteColor] : [UIColor groupTableViewBackgroundColor];

  // WORKAROUND FOR APPLE BUG: 4.2 and lower don't like setting background color for grouped table views on iPad.
  // So, we check the table style and device, and if they match up wrong, we replace the background view with our own.
  if ([table style] == UITableViewStyleGrouped) {
    UIView *bgView = [[[UIView alloc] initWithFrame:[table frame]] autorelease];
    [table setBackgroundView:bgView];
  }

  [table setBackgroundColor:(bgColor != nil ? bgColor : defaultColor)];
  [[table backgroundView] setBackgroundColor:[table backgroundColor]];

  [table setOpaque:![[table backgroundColor] isEqual:[UIColor clearColor]]];
}

+ (UITableViewRowAnimation)animationStyleForProperties:(NSDictionary *)properties
{
  BOOL found;
  UITableViewRowAnimation animationStyle = [TiUtils intValue:@"animationStyle" properties:properties def:UITableViewRowAnimationNone exists:&found];
  if (found) {
    return animationStyle;
  }
  BOOL animate = [TiUtils boolValue:@"animated" properties:properties def:NO];
  return animate ? UITableViewRowAnimationFade : UITableViewRowAnimationNone;
}

@end

#endif
