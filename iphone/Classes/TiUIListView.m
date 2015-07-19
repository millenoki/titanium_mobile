/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UILISTVIEW

#import "TiUIListView.h"
#import "TiUIListSectionProxy.h"
#import "TiUIListItem.h"
#import "TiUIListItemProxy.h"
#import "TiUILabelProxy.h"
#import "WrapperViewProxy.h"
#import "TiUISearchBarProxy.h"
#import "ImageLoader.h"
#ifdef USE_TI_UIREFRESHCONTROL
#import "TiUIRefreshControlProxy.h"
#endif
#import "TiTableView.h"
#import "TiUIHelper.h"
#import "TiApp.h"

#define GROUPED_MARGIN_WIDTH 18.0

@interface TiUIView(eventHandler);
-(void)handleListenerRemovedWithEvent:(NSString *)event;
-(void)handleListenerAddedWithEvent:(NSString *)event;
@end


@interface TiUIListView ()
@property (nonatomic, readonly) TiUIListViewProxy *listViewProxy;
@property (nonatomic,copy,readwrite) NSString * searchString;
@property (nonatomic, strong) NSMutableSet *shownIndexes;
@end

@interface TiUIListSectionProxy()
-(TiViewProxy*)currentViewForLocation:(NSString*)location inListView:(TiUIListView*)listView;
@end


@implementation TiUIListView {
    TiTableView *_tableView;
    NSDictionary *_templates;
    id _defaultItemTemplate;
    BOOL hideOnSearch;
    BOOL searchViewAnimating;

    TiDimension _rowHeight;
    TiDimension _minRowHeight;
    TiDimension _maxRowHeight;

//    UITableViewController *tableController;

    NSMutableArray * sectionTitles;
    NSMutableArray * sectionIndices;
    NSMutableArray * filteredTitles;
    NSMutableArray * filteredIndices;

    UIView *_pullViewWrapper;
    CGFloat pullThreshhold;
    BOOL _pullViewVisible;
    BOOL _hasPullView;

    BOOL pullActive;
    BOOL editing;
    BOOL pruneSections;
    
    BOOL allowsSelection;

    BOOL caseInsensitiveSearch;
    NSString* _searchString;
    BOOL searchActive;
	BOOL searchHidden;
    BOOL keepSectionsInSearch;
    NSMutableArray* _searchResults;
    UIEdgeInsets _defaultSeparatorInsets;
    
    NSMutableDictionary* _measureProxies;
    BOOL _scrollSuspendImageLoading;
    BOOL _scrollHidesKeyboard;
    BOOL hasOnDisplayCell;
    BOOL _updateInsetWithKeyboard;
    
    NSInteger _currentSection;

	BOOL canFireScrollStart;
    BOOL canFireScrollEnd;
    BOOL isScrollingToTop;
    
    BOOL _canSwipeCells;
    MGSwipeTableCell * _currentSwipeCell;
    id _appearAnimation;
    BOOL _useAppearAnimation;
}

static NSDictionary* replaceKeysForRow;
-(NSDictionary *)replaceKeysForRow
{
	if (replaceKeysForRow == nil)
	{
		replaceKeysForRow = [@{@"rowHeight":@"height"} retain];
	}
	return replaceKeysForRow;
}

-(NSString*)replacedKeyForKey:(NSString*)key
{
    NSString* result = [[self replaceKeysForRow] objectForKey:key];
    return result?result:key;
}

-(WrapperViewProxy*)wrapperProxyWithVerticalLayout:(BOOL)vertical
{
    WrapperViewProxy* theProxy = [[WrapperViewProxy alloc] initWithVerticalLayout:vertical];
    [theProxy setParent:(TiParentingProxy*)self.proxy];
    return [theProxy autorelease];
}

- (id)init
{
    self = [super init];
    if (self) {
        _defaultItemTemplate = [[NSNumber numberWithUnsignedInteger:UITableViewCellStyleSubtitle] retain];
        allowsSelection = YES;
        _defaultSeparatorInsets = UIEdgeInsetsZero;
        _scrollSuspendImageLoading = NO;
        _scrollHidesKeyboard = NO;
        hideOnSearch = NO;
        searchViewAnimating = NO;
        _updateInsetWithKeyboard = NO;
        _currentSection = -1;
        _canSwipeCells = NO;
        _hasPullView = NO;
        caseInsensitiveSearch = YES;
		canFireScrollEnd = NO;
        canFireScrollStart = YES;
        _appearAnimation = nil;
        _useAppearAnimation = NO;
    }
    return self;
}

- (void)dealloc
{
    [[NSNotificationCenter defaultCenter] removeObserver:self];
    _tableView.delegate = nil;
    _tableView.dataSource = nil;
    RELEASE_TO_NIL(_tableView)
    RELEASE_TO_NIL(_templates)
    RELEASE_TO_NIL(_defaultItemTemplate)
    RELEASE_TO_NIL(_searchString)
    RELEASE_TO_NIL(_searchResults)
    RELEASE_TO_NIL(_pullViewWrapper)

    RELEASE_TO_NIL(_appearAnimation)
    RELEASE_TO_NIL(_shownIndexes)
    RELEASE_TO_NIL(sectionTitles)
    RELEASE_TO_NIL(sectionIndices)
    RELEASE_TO_NIL(filteredTitles)
    RELEASE_TO_NIL(filteredIndices)
    RELEASE_TO_NIL(_measureProxies)
    [super dealloc];
}
-(void)setHeaderFooter:(TiViewProxy*)theProxy isHeader:(BOOL)header
{
    [theProxy setProxyObserver:self];
    if (header) {
        [self.tableView setTableHeaderView:[theProxy getAndPrepareViewForOpening:[TiUtils appFrame]]];
    } else {
        [self.tableView setTableFooterView:[theProxy getAndPrepareViewForOpening:[TiUtils appFrame]]];
    }
}

-(TiViewProxy*)getOrCreateHeaderHolder
{
    TiViewProxy* vp = [self holdedProxyForKey:@"headerWrapper"];
    if (!vp) {
        vp = (TiViewProxy*)[[self viewProxy] addObjectToHold:@{
                                                               @"layout":@"vertical",
                                                               @"top":@(0),
                                                               @"width":@"FILL",
                                                               @"height":@"SIZE"
                                                               } forKey:@"headerWrapper"];
        vp.canBeResizedByFrame = YES;
        [self setHeaderFooter:vp isHeader:YES];
    }
    return vp;
}


- (TiTableView *)tableView
{
    if (_tableView == nil) {
        UITableViewStyle style = UITableViewStylePlain;
        style = [TiUtils intValue:[self.proxy valueForKey:@"style"] def:style];

        _tableView = [[TiTableView alloc] initWithFrame:self.bounds style:style];
        _tableView.autoresizingMask = UIViewAutoresizingFlexibleWidth|UIViewAutoresizingFlexibleHeight;
        _tableView.delegate = self;
        _tableView.dataSource = self;
        _tableView.touchDelegate = self;
        
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
        
        if ([TiUtils isIOS8OrGreater]) {
            [_tableView setLayoutMargins:UIEdgeInsetsZero];
        }
        
    }
    if ([_tableView superview] != self) {
        [self addSubview:_tableView];
    }
    return _tableView;
}

-(void)reloadTableViewData {
    _canSwipeCells = NO;
    [_tableView reloadData];
}

-(void)frameSizeChanged:(CGRect)frame bounds:(CGRect)bounds
{
    //        if (searchHidden)
    //        {
    //            if (searchViewProxy!=nil)
    //            {
    //                [self hideSearchScreen:nil];
    //            }
    //        }
    //
    if (!searchViewAnimating && ![[self searchController] isActive]) {
        
        TiUISearchBarProxy* searchViewProxy = (TiUISearchBarProxy*) [self holdedProxyForKey:@"searchView"];
        if (searchViewProxy) {
            [searchViewProxy ensureSearchBarHeirarchy];
            CGFloat rowWidth = [self computeRowWidth:_tableView];
            if (rowWidth > 0) {
                CGFloat right = _tableView.bounds.size.width - rowWidth;
                [searchViewProxy layoutProperties]->right = TiDimensionDip(right);
            }
        }
    } else {
        [_tableView reloadData];
    }
    [super frameSizeChanged:frame bounds:bounds];
    
    TiViewProxy* vp = [self holdedProxyForKey:@"headerWrapper"];
    if (vp) {
        [vp parentSizeWillChange];
    }
    vp = [self holdedProxyForKey:@"footerView"];
    if (vp) {
        [vp parentSizeWillChange];
    }
    if (_pullViewWrapper != nil) {
        _pullViewWrapper.frame = CGRectMake(0.0f, 0.0f - bounds.size.height, bounds.size.width, bounds.size.height);
    }
}

- (id)accessibilityElement
{
	return self.tableView;
}

- (TiUIListViewProxy *)listViewProxy
{
	return (TiUIListViewProxy *)self.proxy;
}

- (void)deselectAll:(BOOL)animated
{
	if (_tableView != nil) {
		[_tableView.indexPathsForSelectedRows enumerateObjectsUsingBlock:^(NSIndexPath *indexPath, NSUInteger idx, BOOL *stop) {
			[_tableView deselectRowAtIndexPath:indexPath animated:animated];
		}];
	}
}

- (void) updateIndicesForVisibleRows
{
    if (_tableView == nil || [self isSearchActive]) {
        return;
    }
    
    NSArray* visibleRows = [_tableView indexPathsForVisibleRows];
    [visibleRows enumerateObjectsUsingBlock:^(NSIndexPath *vIndexPath, NSUInteger idx, BOOL *stop) {
        UITableViewCell* theCell = [_tableView cellForRowAtIndexPath:vIndexPath];
        if ([theCell isKindOfClass:[TiUIListItem class]]) {
            ((TiUIListItem*)theCell).proxy.indexPath = vIndexPath;
            [((TiUIListItem*)theCell) ensureVisibleSelectorWithTableView:_tableView];
        }
    }];
}

-(void)proxyDidRelayout:(id)sender
{
    NSArray* keys = [[self viewProxy] allKeysForHoldedProxy:sender];
    if ([keys count] > 0) {
        NSString* key = [keys objectAtIndex:0];
        if ([key isEqualToString:@"pullView"]) {
            pullThreshhold = -[self tableView].contentInset.top + ([(TiViewProxy*)sender view].frame.origin.y - _pullViewWrapper.bounds.size.height);
        } else if ([key isEqualToString:@"headerWrapper"]) {
            UIView* headerView = [[self tableView] tableHeaderView];
            [headerView setFrame:[headerView bounds]];
            [[self tableView] setTableHeaderView:headerView];
            [((TiUIListViewProxy*)[self proxy]) contentsWillChange];
        } else if ([key isEqualToString:@"footerView"]) {
            UIView* footerView = [[self tableView] tableFooterView];
            [footerView setFrame:[footerView bounds]];
            [[self tableView] setTableFooterView:footerView];
            [((TiUIListViewProxy*)[self proxy]) contentsWillChange];
        }
    }
}

-(void)setContentInsets_:(id)value withObject:(id)props
{
    UIEdgeInsets insets = [TiUtils contentInsets:value];
    BOOL animated = [TiUtils boolValue:@"animated" properties:props def:NO];
    void (^setInset)(void) = ^{
        [_tableView setContentInset:insets];
    };
    if (animated) {
        double duration = [TiUtils doubleValue:@"duration" properties:props def:300]/1000;
        [UIView animateWithDuration:duration animations:setInset];
    }
    else {
        setInset();
    }
}

- (void)setTemplates_:(id)args
{
    ENSURE_TYPE_OR_NIL(args,NSDictionary);
	NSMutableDictionary *templates = [[NSMutableDictionary alloc] initWithCapacity:[args count]];
	NSMutableDictionary *measureProxies = [[NSMutableDictionary alloc] initWithCapacity:[args count]];
	[(NSDictionary *)args enumerateKeysAndObjectsUsingBlock:^(NSString *key, id obj, BOOL *stop) {
		TiProxyTemplate *template = [TiProxyTemplate templateFromViewTemplate:obj];
		if (template != nil) {
			[templates setObject:template forKey:key];
            
            //create fake proxy for height computation
            id<TiEvaluator> context = self.listViewProxy.executionContext;
            if (context == nil) {
                context = self.listViewProxy.pageContext;
            }
            TiUIListItemProxy *cellProxy = [[TiUIListItemProxy alloc] initWithListViewProxy:self.listViewProxy inContext:context];
            [cellProxy unarchiveFromTemplate:template withEvents:NO];
            [cellProxy bindings];
            [measureProxies setObject:cellProxy forKey:key];
            [cellProxy release];
		}
	}];
    
	[_templates release];
	_templates = [templates copy];
	[templates release];
    
    [_measureProxies release];
	_measureProxies = [measureProxies copy];
	[measureProxies release];
    
    [self reloadTableViewData];
}

-(TiViewProxy*)sectionViewProxy:(NSInteger)section forLocation:(NSString*)location
{
    TiUIListSectionProxy *proxy = [self.listViewProxy sectionForIndex:section];
    return [proxy sectionViewForLocation:location inListView:self];
}

-(TiViewProxy*)currentSectionViewProxy:(NSInteger)section forLocation:(NSString*)location
{
    TiUIListSectionProxy *proxy = [self.listViewProxy sectionForIndex:section];
    return [proxy currentViewForLocation:location inListView:self];
}

-(UIView*)sectionView:(NSInteger)section forLocation:(NSString*)location section:(TiUIListSectionProxy**)sectionResult
{
    TiViewProxy* viewproxy = [self sectionViewProxy:section forLocation:location];
    if (viewproxy!=nil) {
        return [viewproxy getAndPrepareViewForOpening:self.tableView.bounds];
    }
    return nil;
}

-(CGSize)contentSizeForSize:(CGSize)size
{
    if (_tableView == nil) {
        return CGSizeZero;
    }
    
    CGSize refSize = CGSizeMake(size.width, 1000);
    
    CGFloat resultHeight = 0;
    TiViewProxy* vp;
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


- (IBAction) showSearchScreen: (id) sender
{
	[_tableView setContentOffset:CGPointZero animated:YES];
}

-(void)hideSearchScreen:(id)sender animated:(BOOL)animated
{
    if (!searchHidden || ![(TiViewProxy*)self.proxy viewReady]) {
        return;
    }
    
	// check to make sure we're not in the middle of a layout, in which case we
	// want to try later or we'll get weird drawing animation issues
	if (_tableView.bounds.size.width==0)
	{
		[self performSelector:@selector(hideSearchScreen:) withObject:sender afterDelay:0.1];
		return;
	}
    
    TiUISearchBarProxy* searchViewProxy = (TiUISearchBarProxy*) [self holdedProxyForKey:@"searchView"];
    if (searchViewProxy && [[searchViewProxy view] isFirstResponder]) {
        [[searchViewProxy view] resignFirstResponder];
        [self makeRootViewFirstResponder];
    }
    
    // This logic here is contingent on search controller deactivation
    // (-[TiUITableView searchDisplayControllerDidEndSearch:]) triggering a hide;
    // doing this ensures that:
    //
    // * The hide when the search controller was active is animated
    // * The animation only occurs once
    
    if ([[self searchController] isActive]) {
        [[self searchController] setActive:NO animated:YES];
        searchActive = NO;
        return;
    }
    
    searchActive = NO;
    if (![(TiViewProxy*)self.proxy viewReady]) {
        return;
    }
    NSArray* visibleRows = [_tableView indexPathsForVisibleRows];
    
    // We only want to scroll if the following conditions are met:
    // 1. The top row of the first section (and hence searchbar) are visible (or there are no rows)
    // 2. The current offset is smaller than the new offset (otherwise the search is already hidden)
    
    if (searchViewProxy && searchHidden) {
        CGPoint offset = CGPointMake(0,MAX(TI_NAVBAR_HEIGHT, searchViewProxy.view.frame.size.height));
        if (([visibleRows count] == 0) ||
            ([_tableView contentOffset].y < offset.y && [visibleRows containsObject:[NSIndexPath indexPathForRow:0 inSection:0]]))
        {
            [_tableView setContentOffset:offset animated:animated];
        }
    }
}
-(void)hideSearchScreen:(id)sender {
    [self hideSearchScreen:sender animated:YES];
}

-(void)scrollToTop:(NSInteger)top animated:(BOOL)animated
{
	[_tableView setContentOffset:CGPointMake(0,top - _tableView.contentInset.top) animated:animated];
}


-(void)scrollToBottom:(NSInteger)bottom animated:(BOOL)animated
{
    if (_tableView.contentSize.height > _tableView.frame.size.height)
    {
        CGPoint offset = CGPointMake(0, _tableView.contentSize.height - _tableView.frame.size.height - bottom);
        [_tableView setContentOffset:offset animated:animated];
    }
}

-(void)closePullView:(NSNumber*)anim
{
    if (!_hasPullView || !_pullViewVisible) return;
    _pullViewVisible = NO;
    BOOL animated = YES;
	if (anim != nil)
		animated = [anim boolValue];
    
    if (IOS_7) {
        //we have to delay it on ios7 :s
        double delayInSeconds = 0.01;
        dispatch_time_t popTime = dispatch_time(DISPATCH_TIME_NOW, (int64_t)(delayInSeconds * NSEC_PER_SEC));
        dispatch_after(popTime, dispatch_get_main_queue(), ^(void){
            [_tableView setContentOffset:CGPointMake(0,-_tableView.contentInset.top) animated:animated];
        });
    }
    else {
        [_tableView setContentOffset:CGPointMake(0,-_tableView.contentInset.top) animated:animated];
    }
    
}

-(void)showPullView:(NSNumber*)anim
{
    if (!_hasPullView || _pullViewVisible) {
        return;
    }
    _pullViewVisible = YES;
    BOOL animated = YES;
	if (anim != nil)
		animated = [anim boolValue];
	[_tableView setContentOffset:CGPointMake(0,pullThreshhold) animated:animated];
}

-(BOOL)shouldHighlightCurrentListItem {
    return [_tableView shouldHighlightCurrentListItem];
}

#pragma mark - Helper Methods

-(CGFloat)computeRowWidth:(UITableView*)tableView
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
    if ((sectionTitles == nil) || (tableView != _tableView) ) {
        return rowWidth;
    }
    NSArray* subviews = [tableView subviews];
    if ([subviews count] > 0) {
        // Obfuscate private class name
        Class indexview = NSClassFromString([@"UITableView" stringByAppendingString:@"Index"]);
        for (UIView* view in subviews) {
            if ([view isKindOfClass:indexview]) {
                rowWidth -= [view frame].size.width;
            }
        }
    }
    
    return floorf(rowWidth);
}

-(id)valueWithKey:(NSString*)key atIndexPath:(NSIndexPath*)indexPath
{
    NSDictionary *item = [[self.listViewProxy sectionForIndex:indexPath.section] itemAtIndex:indexPath.row];
    id propertiesValue = [item objectForKey:@"properties"];
    NSDictionary *properties = ([propertiesValue isKindOfClass:[NSDictionary class]]) ? propertiesValue : nil;
    NSString* replaceKey = [self replacedKeyForKey:key];
    id theValue = [properties objectForKey:replaceKey];
    if (theValue == nil) {
        id templateId = [item objectForKey:@"template"];
        if (templateId == nil) {
            templateId = _defaultItemTemplate;
        }
        if (![templateId isKindOfClass:[NSNumber class]]) {
            TiProxyTemplate *template = [_templates objectForKey:templateId];
            theValue = [template.properties objectForKey:replaceKey];
        }
        if (theValue == nil) {
            theValue = [self.proxy valueForKey:key];
        }
    }
    
    return theValue;
}

-(id)firstItemValueForKeys:(NSArray*)keys atIndexPath:(NSIndexPath*)indexPath
{
    NSDictionary *item = [[self.listViewProxy sectionForIndex:indexPath.section] itemAtIndex:indexPath.row];
    NSDictionary* properties = [item objectForKey:@"properties"]?[item objectForKey:@"properties"]:item;
    __block id theResult = nil;
    [keys enumerateObjectsUsingBlock:^(NSString* key, NSUInteger idx, BOOL *stop) {
        theResult = [properties objectForKey:key];
        if (theResult) {
            *stop = YES;
        }
    }];
    return theResult;
}


-(void)buildResultsForSearchText
{
    searchActive = ([self.searchString length] > 0);
    RELEASE_TO_NIL(filteredIndices);
    RELEASE_TO_NIL(filteredTitles);
    if (searchActive) {
        BOOL hasResults = NO;
        //Initialize
        if(_searchResults == nil) {
            _searchResults = [[NSMutableArray alloc] init];
        }
        //Clear Out
        [_searchResults removeAllObjects];
        
        //Search Options
        NSStringCompareOptions searchOpts = (caseInsensitiveSearch ? NSCaseInsensitiveSearch : 0);
        
        NSUInteger maxSection = [[self.listViewProxy sectionCount] unsignedIntegerValue];
        NSMutableArray* singleSection = keepSectionsInSearch ? nil : [[NSMutableArray alloc] init];
        for (int i = 0; i < maxSection; i++) {
            NSMutableArray* thisSection = keepSectionsInSearch ? [[NSMutableArray alloc] init] : nil;
            NSUInteger maxItems = [[self.listViewProxy sectionForIndex:i] itemCount];
            for (int j = 0; j < maxItems; j++) {
                NSIndexPath* thePath = [NSIndexPath indexPathForRow:j inSection:i];
                id theValue = [self firstItemValueForKeys:@[@"searchableText", @"title"] atIndexPath:thePath];
                if (theValue!=nil && [[TiUtils stringValue:theValue] rangeOfString:self.searchString options:searchOpts].location != NSNotFound) {
                    (thisSection != nil) ? [thisSection addObject:thePath] : [singleSection addObject:thePath];
                    hasResults = YES;
                }
            }
            if (thisSection != nil) {
                if ([thisSection count] > 0) {
                    [_searchResults addObject:thisSection];
                    
                    if (sectionTitles != nil && sectionIndices != nil) {
                        NSNumber* theIndex = [NSNumber numberWithInt:i];
                        if ([sectionIndices containsObject:theIndex]) {
                            id theTitle = [sectionTitles objectAtIndex:[sectionIndices indexOfObject:theIndex]];
                            if (filteredTitles == nil) {
                                filteredTitles = [[NSMutableArray alloc] init];
                            }
                            if (filteredIndices == nil) {
                                filteredIndices = [[NSMutableArray alloc] init];
                            }
                            [filteredTitles addObject:theTitle];
                            [filteredIndices addObject:NUMUINTEGER([_searchResults count] - 1 )];
                        }
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
            if ([(TiViewProxy*)self.proxy _hasListeners:@"noresults" checkParent:NO]) {
                [self.proxy fireEvent:@"noresults" withObject:nil propagate:NO reportSuccess:NO errorCode:0 message:nil];
            }
        }
        
    } else {
        RELEASE_TO_NIL(_searchResults);
    }
}

-(BOOL) isSearchActive
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
        [[[self searchController] searchResultsTableView] reloadData];
    } 
}

-(NSIndexPath*)pathForSearchPath:(NSIndexPath*)indexPath
{
    if (_searchResults != nil && [_searchResults count] > indexPath.section) {
        NSArray* sectionResults = [_searchResults objectAtIndex:indexPath.section];
        if([sectionResults count] > indexPath.row) {
            return [sectionResults objectAtIndex:indexPath.row];
        }
    }
    return indexPath;
}

-(NSInteger)sectionForSearchSection:(NSInteger)section
{
    if (_searchResults != nil) {
        NSArray* sectionResults = [_searchResults objectAtIndex:section];
        NSIndexPath* thePath = [sectionResults objectAtIndex:0];
        return thePath.section;
    }
    return section;
}

#pragma mark - Public API

-(void)setSeparatorInsets_:(id)arg
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

-(void)setPruneSectionsOnEdit_:(id)args
{
    pruneSections = [TiUtils boolValue:args def:NO];
}

-(void)setScrollingEnabled_:(id)args
{
    UITableView *table = [self tableView];
    [table setScrollEnabled:[TiUtils boolValue:args def:YES]];
}

-(void)setSeparatorStyle_:(id)arg
{
    [[self tableView] setSeparatorStyle:[TiUtils intValue:arg]];
}

-(void)setSeparatorColor_:(id)arg
{
    TiColor *color = [TiUtils colorValue:arg];
    [[self tableView] setSeparatorColor:[color _color]];
}

- (void)setDefaultItemTemplate_:(id)args
{
	if (![args isKindOfClass:[NSString class]] && ![args isKindOfClass:[NSNumber class]]) {
		ENSURE_TYPE_OR_NIL(args,NSString);
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

-(void)onCreateCustomBackground
{
    if (_tableView != nil) {
		[[self class] setBackgroundColor:[UIColor clearColor] onTable:_tableView];
	}
}


- (void)setHeaderTitle_:(id)args
{
    NSString* text = [TiUtils stringValue:args];
    if (text) {
        TiViewProxy* vp = (TiViewProxy*)[[self viewProxy] addObjectToHold:@{
                                                                @"type":@"Ti.UI.Label",
                                                                @"font":@{@"size":@(15)},
                                                                @"padding":@{@"left":@(15), @"right":@(15)},
                                                                @"width":@"FILL",
                                                                @"height":@(50),
                                                                @"autocapitalization":@(YES),
                                                                @"text":[TiUtils stringValue:args],
                                                                @"color":@"#333333",
                                                               } forKey:@"headerView"];
        [vp setProxyObserver:self];
        [[self getOrCreateHeaderHolder] addProxy:vp atIndex:1 shouldRelayout:YES];
    } else {
        [[self viewProxy] removeHoldedProxyForKey:@"headerView"];
    }
}

- (void)setFooterTitle_:(id)args
{
    NSString* text = [TiUtils stringValue:args];
    if (text) {
        TiViewProxy* vp = (TiViewProxy*)[[self viewProxy] addObjectToHold:@{
                                                                            @"type":@"Ti.UI.Label",
                                                                            @"font":@{@"size":@(14)},
                                                                            @"padding":@{@"left":@(15), @"right":@(15), @"top":@(10), @"bottom":@(10)},
                                                                            @"width":@"FILL",
                                                                            @"height":@"SIZE",
                                                                            @"text":[TiUtils stringValue:args],
                                                                            @"color":@"#6E6E6E",
                                                                            } forKey:@"footerView"];
        [vp setProxyObserver:self];
        vp.canBeResizedByFrame = YES;
        [self setHeaderFooter:vp isHeader:NO];
    } else {
        [self setHeaderFooter:nil isHeader:NO];

    }
}

-(TiViewProxy*)holdedProxyForKey:(NSString*)key
{
    return (TiViewProxy*)[[self viewProxy] holdedProxyForKey:key];
}

-(void)setHeaderView_:(id)args
{
    id vp = [[self viewProxy] addObjectToHold:args forKey:@"headerView"];
    if (IS_OF_CLASS(vp, TiViewProxy)) {
        [(TiViewProxy*)vp setProxyObserver:self];
        [[self getOrCreateHeaderHolder] addProxy:vp atIndex:1 shouldRelayout:YES];
    }
}

-(void)setFooterView_:(id)args
{
    id vp = [[self viewProxy] addObjectToHold:args forKey:@"footerView"];
    if (IS_OF_CLASS(vp, TiViewProxy)) {
        [(TiViewProxy*)vp setProxyObserver:self];
        ((TiViewProxy*)vp).canBeResizedByFrame = YES;
        [self setHeaderFooter:(TiViewProxy*)vp isHeader:NO];
    }
    else {
        [self setHeaderFooter:nil isHeader:NO];
    }
}

-(void)setRefreshControl_:(id)args
{
#ifdef USE_TI_UIREFRESHCONTROL
    id vp = [[self viewProxy] addObjectToHold:args forKey:@"refreshControl"];
    if (IS_OF_CLASS(vp, TiUIRefreshControlProxy)) {
        [[self tableView] addSubview:[(TiUIRefreshControlProxy*)vp control]];
    }
#endif
}

-(void)setPullView_:(id)args
{
    if ([self tableView].bounds.size.width==0)
    {
        [self performSelector:@selector(setPullView_:) withObject:args afterDelay:0.1];
        return;
    }
    id vp = [[self viewProxy] addObjectToHold:args forKey:@"pullView"];
    if (IS_OF_CLASS(vp, TiViewProxy)) {
        TiViewProxy* viewproxy = (TiViewProxy*)vp;
        _hasPullView = YES;
        if (_pullViewWrapper == nil) {
            _pullViewWrapper = [[UIView alloc] init];
            _pullViewWrapper.backgroundColor = [UIColor clearColor];
            [_tableView addSubview:_pullViewWrapper];
        }
        CGSize refSize = _tableView.bounds.size;
        [_pullViewWrapper setFrame:CGRectMake(0.0, 0.0 - refSize.height, refSize.width, refSize.height)];
        LayoutConstraint *viewLayout = [viewproxy layoutProperties];
        //If height is not dip, explicitly set it to SIZE
        if (viewLayout->height.type != TiDimensionTypeDip) {
            viewLayout->height = TiDimensionAutoSize;
        }
        //If bottom is not dip set it to 0
        if (viewLayout->bottom.type != TiDimensionTypeDip) {
            viewLayout->bottom = TiDimensionZero;
        }
        //Remove other vertical positioning constraints
        viewLayout->top = TiDimensionUndefined;
        viewLayout->centerY = TiDimensionUndefined;
        
        [viewproxy setCanBeResizedByFrame:YES];
        [viewproxy setProxyObserver:self];
        [_pullViewWrapper addSubview:[viewproxy getAndPrepareViewForOpening:_pullViewWrapper.bounds]];
        if (_pullViewVisible) {
            [self showPullView:@(NO)];
        }
    } else {
        _hasPullView = NO;
        [_pullViewWrapper removeFromSuperview];
        RELEASE_TO_NIL(_pullViewWrapper);
    }
    
}

-(void)setKeepSectionsInSearch_:(id)args
{
    keepSectionsInSearch = [TiUtils boolValue:args def:NO];
    TiUISearchBarProxy* vp = (TiUISearchBarProxy*) [self holdedProxyForKey:@"searchView"];
    if (vp && searchActive) {
        [self buildResultsForSearchText];
        [self reloadTableViewData];
    }
}

- (void)setScrollIndicatorStyle_:(id)value
{
	[self.tableView setIndicatorStyle:[TiUtils intValue:value def:UIScrollViewIndicatorStyleDefault]];
}

- (void)setWillScrollOnStatusTap_:(id)value
{
	[self.tableView setScrollsToTop:[TiUtils boolValue:value def:YES]];
}

- (void)setShowVerticalScrollIndicator_:(id)value
{
	[self.tableView setShowsVerticalScrollIndicator:[TiUtils boolValue:value]];
}

-(void)setAllowsSelection_:(id)value
{
    allowsSelection = [TiUtils boolValue:value];
//    [tableController setClearsSelectionOnViewWillAppear:!allowsSelection];
}

-(void)setAllowsSelectionDuringEditing_:(id)arg
{
	[[self tableView] setAllowsSelectionDuringEditing:[TiUtils boolValue:arg def:NO]];
}

-(void)setEditing_:(id)args
{
    if ([TiUtils boolValue:args def:NO] != editing) {
        editing = !editing;
        [[self tableView] beginUpdates];
        [_tableView setEditing:editing animated:YES];
        [_tableView endUpdates];
    }
}

-(void)setDelaysContentTouches_:(id)value
{
    [[self tableView] setDelaysContentTouches:[TiUtils boolValue:value def:YES]];
}

-(void)setCanCancelEvents_:(id)args
{
    [[self tableView] setCanCancelContentTouches:[TiUtils boolValue:args def:YES]];
}

-(void)setScrollSuspendsImageLoading_:(id)value
{
    _scrollSuspendImageLoading = [TiUtils boolValue:value def:_scrollSuspendImageLoading];
}

-(void)setDisableBounce_:(id)value
{
	[[self tableView] setBounces:![TiUtils boolValue:value]];
}

-(void)setHorizontalBounce_:(id)value
{
    [[self tableView] setAlwaysBounceHorizontal:[TiUtils boolValue:value]];
}

-(void)setVerticalBounce_:(id)value
{
    [[self tableView] setAlwaysBounceVertical:[TiUtils boolValue:value]];
}

-(void)setScrollHidesKeyboard_:(id)value
{
    _scrollHidesKeyboard = [TiUtils boolValue:value def:_scrollHidesKeyboard];
}

-(void)setOnDisplayCell_:(id)callback
{
    hasOnDisplayCell = [callback isKindOfClass:[KrollCallback class]] || [callback isKindOfClass:[KrollWrapper class]];
}


-(void)setAppearAnimation_:(id)value
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

-(void)setUseAppearAnimation_:(id)value
{
    _useAppearAnimation = [TiUtils boolValue:value def:NO];
}

#pragma mark - Search Support

-(TiSearchDisplayController*) searchController
{
    id obj = [self holdedProxyForKey:@"searchView"];
    if (obj) {
        return [(TiUISearchBarProxy*) obj searchController];
    }
    return nil;
}

-(void)setCaseInsensitiveSearch_:(id)args
{
    caseInsensitiveSearch = [TiUtils boolValue:args def:YES];
    if (searchActive) {
        [self buildResultsForSearchText];
        if ([[self searchController] isActive]) {
            [[[self searchController] searchResultsTableView] reloadData];
        } else {
            [self reloadTableViewData];
        }
    }
}

-(void)setSearchText_:(id)args
{
    id searchView = [self holdedProxyForKey:@"searchView"];
    if (searchView) {
        DebugLog(@"Can not use searchText with searchView. Ignoring call.");
        return;
    }
    self.searchString = [TiUtils stringValue:args];
    [self buildResultsForSearchText];
    [self reloadTableViewData];
}

//-(UITableViewController*)tableViewController
//{
//    if (!tableController) {
//        tableController = [[UITableViewController alloc] init];
//        [TiUtils configureController:tableController withObject:nil];
//        tableController.tableView = [self tableView];
//        [tableController setClearsSelectionOnViewWillAppear:!allowsSelection];
//    }
//    return tableController;
//}

-(void)setSearchViewExternal_:(id)args {
    ENSURE_SINGLE_ARG_OR_NIL(args, TiUISearchBarProxy);
//    RELEASE_TO_NIL(tableController);
    id vp = [[self viewProxy] addProxyToHold:args setParent:NO forKey:@"searchView" shouldRelayout:NO];
    if (IS_OF_CLASS(vp, TiUISearchBarProxy)) {
//        [self tableView];
        [(TiUISearchBarProxy*)vp setReadyToCreateView:YES];
        [(TiUISearchBarProxy*)vp setDelegate:self];
        ((TiUISearchBarProxy*)vp).canHaveSearchDisplayController = YES;
        if (searchHidden)
        {
            [self hideSearchScreen:nil animated:NO];
        }
    }
    
}

-(void)setSearchView_:(id)args
{
//    RELEASE_TO_NIL(tableController);
    id vp = [[self viewProxy] addObjectToHold:args forKey:@"searchView"];
    if (IS_OF_CLASS(vp, TiUISearchBarProxy)) {
        [(TiUISearchBarProxy*)vp setReadyToCreateView:YES];
        [(TiUISearchBarProxy*)vp setDelegate:self];
        ((TiUISearchBarProxy*)vp).canHaveSearchDisplayController = YES;
        [[self getOrCreateHeaderHolder] addProxy:vp atIndex:0 shouldRelayout:YES];
        if (searchHidden)
		{
			[self hideSearchScreen:nil animated:NO];
		}
    }
}

-(void)setSearchHidden_:(id)hide
{
    searchHidden = [TiUtils boolValue:hide def:NO];
    TiUISearchBarProxy* vp = (TiUISearchBarProxy*) [self holdedProxyForKey:@"searchView"];
    if (vp) {
        if (searchHidden)
        {
            [self hideSearchScreen:nil];
        }
        else
        {
            [self showSearchScreen:nil];
        }
    }
}

-(void)setHideSearchOnSelection_:(id)yn
{
    hideOnSearch = [TiUtils boolValue:yn def:NO];
}

-(void)cleanup:(id)unused
{
    if ([[self searchController] isActive]) {
        [[self searchController] setActive:NO animated:NO];
    }
}

#pragma mark - SectionIndexTitle Support

-(void)setSectionIndexTitles_:(id)args
{
    ENSURE_TYPE_OR_NIL(args, NSArray);
    
    RELEASE_TO_NIL(sectionTitles);
    RELEASE_TO_NIL(sectionIndices);
    RELEASE_TO_NIL(filteredTitles);
    RELEASE_TO_NIL(filteredIndices);
    
    NSArray* theIndex = args;
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
    TiUISearchBarProxy* vp = (TiUISearchBarProxy*) [self holdedProxyForKey:@"searchView"];
    if (vp) {
        if (searchActive) {
            [self buildResultsForSearchText];
        }
        [_tableView reloadSectionIndexTitles];
    }
}

#pragma mark - SectionIndexTitle Support Datasource methods.

-(NSArray *)sectionIndexTitlesForTableView:(UITableView *)tableView
{
    if (tableView != _tableView) {
        return nil;
    }
    
    if (editing) {
        return nil;
    }
    
    if (searchActive) {
        if (keepSectionsInSearch && ([_searchResults count] > 0) ) {
            return filteredTitles;
        } else {
            return nil;
        }
    }
    
    return sectionTitles;
}

-(NSInteger)tableView:(UITableView *)tableView sectionForSectionIndexTitle:(NSString *)title atIndex:(NSInteger)theIndex
{
    if (tableView != _tableView) {
        return 0;
    }
    
    if (editing) {
        return 0;
    }
    
    if (searchActive) {
        if (keepSectionsInSearch && ([_searchResults count] > 0) && (filteredTitles != nil) && (filteredIndices != nil) ) {
            // get the index for the title
            NSUInteger index = [filteredTitles indexOfObject:title];
            if (index > 0 && (index < [filteredIndices count]) ) {
                return [[filteredIndices objectAtIndex:index] intValue];
            }
            return 0;
        } else {
            return 0;
        }
    }
    
    if ( (sectionTitles != nil) && (sectionIndices != nil) ) {
        // get the index for the title
        NSUInteger index = [sectionTitles indexOfObject:title];
        if (index > 0 && (index < [sectionIndices count]) ) {
            return [[sectionIndices objectAtIndex:index] intValue];
        }
        return 0;
    }
    return 0;
}

#pragma mark - Editing Support

-(BOOL)canEditRowAtIndexPath:(NSIndexPath *)indexPath
{
    id editValue = [self valueWithKey:@"canEdit" atIndexPath:indexPath];
    //canEdit if undefined is false
    return [TiUtils boolValue:editValue def:NO];
}


-(BOOL)canMoveRowAtIndexPath:(NSIndexPath *)indexPath
{
    id moveValue = [self valueWithKey:@"canMove" atIndexPath:indexPath];
    //canMove if undefined is false
    return [TiUtils boolValue:moveValue def:NO];
}

-(NSArray*)editActionsFromValue:(id)value
{
    ENSURE_ARRAY(value);
    NSArray* propArray = (NSArray*)value;
    NSMutableArray* returnArray = nil;
    for (id prop in propArray) {
        ENSURE_DICT(prop);
        NSString* title = [TiUtils stringValue:@"title" properties:prop];
        int actionStyle = [TiUtils intValue:@"style" properties:prop];
        TiColor* theColor = [TiUtils colorValue:@"color" properties:prop];
        UITableViewRowAction* theAction = [UITableViewRowAction rowActionWithStyle:actionStyle title:title handler:^(UITableViewRowAction *action, NSIndexPath *indexPath){
            NSString* eventName = @"rowAction";
            if ([self.listViewProxy _hasListeners:eventName checkParent:NO]) {
                TiUIListSectionProxy* theSection = [[self.listViewProxy sectionForIndex:indexPath.section] retain];
                NSDictionary *theItem = [[theSection itemAtIndex:indexPath.row] retain];
                NSMutableDictionary *eventObject = [[NSMutableDictionary alloc] initWithObjectsAndKeys:
                                                    theSection, @"section",
                                                    NUMINTEGER(indexPath.section), @"sectionIndex",
                                                    NUMINTEGER(indexPath.row), @"itemIndex",
                                                    action.title,@"action",
                                                    nil];
                id propertiesValue = [theItem objectForKey:@"properties"];
                NSDictionary *properties = ([propertiesValue isKindOfClass:[NSDictionary class]]) ? propertiesValue : nil;
                id itemId = [properties objectForKey:@"itemId"];
                if (itemId != nil) {
                    [eventObject setObject:itemId forKey:@"itemId"];
                }
                [self.proxy fireEvent:eventName withObject:eventObject withSource:self.proxy propagate:NO reportSuccess:NO errorCode:0 message:nil];
                [eventObject release];
                [theItem release];
                [theSection release];
            }

        }];
        if (theColor != nil) {
            theAction.backgroundColor = [theColor color];
        }
        if (returnArray == nil) {
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
    if (tableView != _tableView) {
        return NO;
    }
    
    if (searchActive) {
        return NO;
    }
    
    if ([self canEditRowAtIndexPath:indexPath]) {
        return YES;
    }
    if (editing) {
        return [self canMoveRowAtIndexPath:indexPath];
    }
    return NO;
}

- (void)tableView:(UITableView *)tableView commitEditingStyle:(UITableViewCellEditingStyle)editingStyle forRowAtIndexPath:(NSIndexPath *)indexPath
{
    if (editingStyle == UITableViewCellEditingStyleDelete) {
        TiUIListSectionProxy* theSection = [[self.listViewProxy sectionForIndex:indexPath.section] retain];
        NSDictionary *theItem = [[theSection itemAtIndex:indexPath.row] retain];

        
        //Fire the delete Event if required
        NSString *eventName = @"delete";
        if ([self.proxy _hasListeners:eventName]) {
        
            NSMutableDictionary *eventObject = [[NSMutableDictionary alloc] initWithObjectsAndKeys:
                                                theSection, @"section",
                                                self.proxy, @"listView",
                                                NUMINTEGER(indexPath.section), @"sectionIndex",
                                                theItem, @"item",
                                                NUMINTEGER(indexPath.row), @"itemIndex",
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
        [theItem release];
        
        BOOL asyncDelete = [TiUtils boolValue:[self.proxy valueForKey:@"asyncDelete"] def:NO];
        if (asyncDelete) {
            [theSection release];
            return;
        }
        [tableView beginUpdates];
//        [theSection willRemoveItemAt:indexPath];
//        [tableView deleteRowsAtIndexPaths:@[indexPath] withRowAnimation:UITableViewRowAnimationFade];
//        [tableView endUpdates];
        [theSection deleteItemsAt:@[@(indexPath.row), @(1), @{@"animated":@(YES)}]];
    }
}

#pragma mark - Editing Support Delegate Methods.

- (UITableViewCellEditingStyle)tableView:(UITableView *)tableView editingStyleForRowAtIndexPath:(NSIndexPath *)indexPath
{
    //No support for insert style yet
    if ([self canEditRowAtIndexPath:indexPath]) {
        return UITableViewCellEditingStyleDelete;
    } else {
        return UITableViewCellEditingStyleNone;
    }
}

- (NSArray *)tableView:(UITableView *)tableView editActionsForRowAtIndexPath:(NSIndexPath *)indexPath
{
    if (tableView != _tableView) {
        return nil;
    }
    
    if (searchActive) {
        return nil;
    }
    
    if (![self canEditRowAtIndexPath:indexPath]) {
        return nil;
    }
    
    id editValue = [self valueWithKey:@"editActions" atIndexPath:indexPath];
    
    if (IS_NULL_OR_NIL(editValue)) {
        return nil;
    }
    
    return [self editActionsFromValue:editValue];

}

- (BOOL)tableView:(UITableView *)tableView shouldIndentWhileEditingRowAtIndexPath:(NSIndexPath *)indexPath
{
    return [self canEditRowAtIndexPath:indexPath];
}

- (void)tableView:(UITableView *)tableView willBeginEditingRowAtIndexPath:(NSIndexPath *)indexPath
{
    editing = YES;
    [self.proxy replaceValue:NUMBOOL(editing) forKey:@"editing" notification:NO];
}

- (void)tableView:(UITableView *)tableView didEndEditingRowAtIndexPath:(NSIndexPath *)indexPath
{
    editing = [_tableView isEditing];
    [self.proxy replaceValue:NUMBOOL(editing) forKey:@"editing" notification:NO];
    if (!editing) {
        [self performSelector:@selector(reloadTableViewData) withObject:nil afterDelay:0.1];
    }
}

#pragma mark - UITableViewDataSource

- (BOOL)tableView:(UITableView *)tableView canMoveRowAtIndexPath:(NSIndexPath *)indexPath
{
    if (tableView != _tableView) {
        return NO;
    }
    
    if (searchActive) {
        return NO;
    }
    
	return [self canMoveRowAtIndexPath:indexPath];
}

- (void)tableView:(UITableView *)tableView moveRowAtIndexPath:(NSIndexPath *)fromIndexPath toIndexPath:(NSIndexPath *)toIndexPath
{
    NSInteger fromSectionIndex = [fromIndexPath section];
    NSInteger fromRowIndex = [fromIndexPath row];
    NSInteger toSectionIndex = [toIndexPath section];
    NSInteger toRowIndex = [toIndexPath row];
    
    
    
    if (fromSectionIndex == toSectionIndex) {
        if (fromRowIndex == toRowIndex) {
            return;
        }
        //Moving a row in the same index. Just move and reload section
        TiUIListSectionProxy* theSection = [[self.listViewProxy sectionForIndex:fromSectionIndex] retain];
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
                                                theSection,@"targetSection",
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
        
        [tableView reloadData];
        
        [theSection release];
        [theItem release];
        
        
    } else {
        TiUIListSectionProxy* fromSection = [[self.listViewProxy sectionForIndex:fromSectionIndex] retain];
        NSDictionary *theItem = [[fromSection itemAtIndex:fromRowIndex] retain];
        TiUIListSectionProxy* toSection = [[self.listViewProxy sectionForIndex:toSectionIndex] retain];
        
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
                                                toSection,@"targetSection",
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
        
        [tableView reloadData];
        
        [fromSection release];
        [toSection release];
        [theItem release];
    }
}

- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView
{
    NSUInteger sectionCount = 0;
    
    //TIMOB-15526
    if (tableView != _tableView && tableView.backgroundColor == [UIColor clearColor]) {
        tableView.backgroundColor = [UIColor whiteColor];
    }

    if (_searchResults != nil) {
        sectionCount = [_searchResults count];
    } else {
        sectionCount = [self.listViewProxy.sectionCount unsignedIntegerValue];
    }
    return MAX(0,sectionCount);
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section
{
    if (_searchResults != nil) {
        if ([_searchResults count] <= section) {
            return 0;
        }
        NSArray* theSection = [_searchResults objectAtIndex:section];
        return [theSection count];
        
    } else {
        TiUIListSectionProxy* theSection = [self.listViewProxy sectionForIndex:section];
        if (theSection != nil) {
            return theSection.itemCount;
        }
        return 0;
    }
}

-(UITableViewCell *) forceCellForRowAtIndexPath:(NSIndexPath *)indexPath {
    return [_tableView cellForRowAtIndexPath:indexPath];
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath
{
    NSIndexPath* realIndexPath = [self pathForSearchPath:indexPath];
    TiUIListSectionProxy* theSection = [self.listViewProxy sectionForIndex:realIndexPath.section];
    NSInteger maxItem = 0;
    
    if (_searchResults != nil && [_searchResults count] > indexPath.section) {
        NSArray* sectionResults = [_searchResults objectAtIndex:indexPath.section];
        maxItem = [sectionResults count];
    } else {
        maxItem = theSection.itemCount;
    }
    
    NSDictionary *item = [theSection itemAtIndex:realIndexPath.row];
    id templateId = [item objectForKey:@"template"];
    if (templateId == nil) {
        templateId = _defaultItemTemplate;
    }
    NSString *cellIdentifier = [templateId isKindOfClass:[NSNumber class]] ? [NSString stringWithFormat:@"TiUIListView__internal%@", templateId]: [templateId description];
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
        } else if (indexPath.row == (maxItem - 1) ) {
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
            id template = [_templates objectForKey:templateId];
            if (template != nil) {
                [cellProxy unarchiveFromTemplate:template withEvents:YES];
                [cellProxy windowWillOpen];
                [cellProxy windowDidOpen];
            }
            [cell configurationSet];
        }
        cell.delaysContentTouches = tableView.delaysContentTouches;
        cell.delegate = self;
        
        if ([TiUtils isIOS8OrGreater] && (tableView == _tableView)) {
            [cell setLayoutMargins:UIEdgeInsetsZero];
        }
        
        [cellProxy release];
        [cell autorelease];
    }
    else {
        [cell setPosition:position isGrouped:grouped];
    }
    
    cell.dataItem = item;
    cell.proxy.indexPath = realIndexPath;
    _canSwipeCells |= [cell hasSwipeButtons];
    return cell;
}

- (NSString *)tableView:(UITableView *)tableView titleForHeaderInSection:(NSInteger)section
{
    if (tableView != _tableView) {
        return nil;
    }
    
    if (searchActive) {
        if (keepSectionsInSearch && ([_searchResults count] > 0) ) {
            NSInteger realSection = [self sectionForSearchSection:section];
            return [[self.listViewProxy sectionForIndex:realSection] headerTitle];
        } else {
            return nil;
        }
    }
    
    return [[self.listViewProxy sectionForIndex:section] headerTitle];
}

- (NSString *)tableView:(UITableView *)tableView titleForFooterInSection:(NSInteger)section
{
    if (tableView != _tableView) {
        return nil;
    }
    
    if (searchActive) {
        if (keepSectionsInSearch && ([_searchResults count] > 0) ) {
            NSInteger realSection = [self sectionForSearchSection:section];
            return [[self.listViewProxy sectionForIndex:realSection] footerTitle];
        } else {
            return nil;
        }
    }
    return [[self.listViewProxy sectionForIndex:section] footerTitle];
}

#pragma mark - MGSwipeTableCell Delegate
-(BOOL) swipeTableCell:(MGSwipeTableCell*) cell canSwipe:(MGSwipeDirection) direction {
    if (!_canSwipeCells) {
        return NO;
    }
    if (IS_OF_CLASS(cell, TiUIListItem)) {
        TiUIListItem* listItem = (TiUIListItem*)cell;
        NSIndexPath* indexPath = listItem.proxy.indexPath;
        BOOL isRight = (direction == MGSwipeDirectionRightToLeft);
        
        id theValue = [self valueWithKey:(isRight?@"canSwipeRight":@"canSwipeLeft") atIndexPath:indexPath];
        if (theValue) {
            return [theValue boolValue];
        }
        else {
            return [listItem hasSwipeButtons];
        }
    }
    return NO;
}
-(NSArray*) swipeTableCell:(MGSwipeTableCell*) cell swipeButtonsForDirection:(MGSwipeDirection)direction
             swipeSettings:(MGSwipeSettings*) swipeSettings expansionSettings:(MGSwipeExpansionSettings*) expansionSettings
{
    if (!_canSwipeCells) {
        return nil;
    }
    if (IS_OF_CLASS(cell, TiUIListItem)) {
        TiUIListItem* listItem = (TiUIListItem*)cell;
//        NSIndexPath* indexPath = listItem.proxy.indexPath;
        BOOL isRight = (direction == MGSwipeDirectionRightToLeft);
        id theValue = [listItem.proxy valueForKey:(isRight?@"rightSwipeButtons":@"leftSwipeButtons")];
            if (IS_OF_CLASS(theValue, NSArray)) {
                NSMutableArray* buttonViews = [NSMutableArray arrayWithCapacity:[theValue count]];
                [theValue enumerateObjectsUsingBlock:^(id obj, NSUInteger idx, BOOL *stop) {
                    if (IS_OF_CLASS(obj, TiViewProxy)) {
                        [(TiViewProxy*)obj setCanBeResizedByFrame:YES];
//                        if ([(TiViewProxy*)obj viewAttached]) {
//                            [(TiViewProxy*)obj refreshView];
//                            [buttonViews addObject:[(TiViewProxy*)obj view]];
//                        }
//                        else {
                            [buttonViews addObject:[(TiViewProxy*)obj getAndPrepareViewForOpening]];
//                        }
                    }
                }];
                theValue = [NSArray arrayWithArray:buttonViews];                
            }
        return theValue;
    }
    return nil;
}

-(void) swipeTableCell:(MGSwipeTableCell*) cell didChangeSwipeState:(MGSwipeState) state gestureIsActive:(BOOL) gestureIsActive {
    if (state != MGSwipeStateNone) {
        _currentSwipeCell = cell;
    } else {
        _currentSwipeCell = nil;
    }
}

-(void)closeSwipeMenu:(NSNumber*)anim {
    if (!_currentSwipeCell) return;
    BOOL animated = YES;
    if (anim != nil)
        animated = [anim boolValue];
    if (_currentSwipeCell) {
        [_currentSwipeCell hideSwipeAnimated:animated];
    }
}

#pragma mark - UITableViewDelegate

- (void)tableView:(UITableView *)tableView willDisplayCell:(UITableViewCell *)cell forRowAtIndexPath:(NSIndexPath *)indexPath
{
    if (searchActive || (tableView != _tableView)) {
        return;
    }
    TiUIListItem* item = (TiUIListItem*)cell;
    NSDictionary *data = item.dataItem;
    
    if (hasOnDisplayCell) {
        TiUIListSectionProxy *section = [self.listViewProxy sectionForIndex:indexPath.section];
        NSDictionary * propertiesDict = @{
                                          @"view":((TiUIListItem*)cell).proxy,
                                          @"listView": self.proxy,
                                          @"section":section,
                                          @"item":data,
                                          @"searchResult":NUMBOOL([self isSearchActive]),
                                          @"sectionIndex":NUMINTEGER(indexPath.section),
                                          @"itemIndex":NUMINTEGER(indexPath.row)
        };
        [self.proxy fireCallback:@"onDisplayCell" withArg:propertiesDict withSource:self.proxy];
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
            TiViewProxy* proxy = item.proxy;
            TiAnimation * newAnimation = [TiAnimation animationFromArg:appearAnimation context:[proxy executionContext] create:NO];
//            newAnimation.dontApplyOnFinish = YES;
            [proxy handleAnimation:newAnimation];
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
        if (keepSectionsInSearch && ([_searchResults count] > 0) ) {
            realSection = [self sectionForSearchSection:section];
        } else {
            return nil;
        }
    }
    
    TiUIListSectionProxy *sectionProxy = [self.listViewProxy sectionForIndex:realSection];
    if([sectionProxy isHidden] || [sectionProxy.headerTitle length] > 0)
    {
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
        if (keepSectionsInSearch && ([_searchResults count] > 0) ) {
            realSection = [self sectionForSearchSection:section];
        } else {
            return nil;
        }
    }
    TiUIListSectionProxy *sectionProxy = [self.listViewProxy sectionForIndex:realSection];
    if([sectionProxy isHidden] || [sectionProxy.footerTitle length] > 0)
    {
        return nil;
    }
    
    return [self sectionView:realSection forLocation:@"footerView" section:nil];
}

#define DEFAULT_SECTION_HEADERFOOTER_HEIGHT 20.0

- (CGFloat)tableView:(UITableView *)tableView heightForHeaderInSection:(NSInteger)section
{
    if (tableView != _tableView) {
        return 0.0;
    }
    
    NSInteger realSection = section;
    
    if (searchActive) {
        if (keepSectionsInSearch && ([_searchResults count] > 0) ) {
            realSection = [self sectionForSearchSection:section];
        } else {
            return 0.0;
        }
    }
    
    TiUIListSectionProxy *sectionProxy = [self.listViewProxy sectionForIndex:realSection];
    if([sectionProxy isHidden])
    {
        return 0.0;
    }
    if([sectionProxy.headerTitle length] > 0)
    {
        return [tableView sectionHeaderHeight];
    }
    TiViewProxy* viewProxy = [sectionProxy sectionViewForLocation:@"headerView" inListView:self];
	
    CGFloat size = 0.0;
    if (viewProxy!=nil) {
        [viewProxy getAndPrepareViewForOpening:[self.tableView bounds]]; //to make sure it is setup
        size += [[viewProxy view] bounds].size.height;
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
    if (tableView != _tableView) {
        return 0.0;
    }
    
    NSInteger realSection = section;
    
    if (searchActive) {
        if (keepSectionsInSearch && ([_searchResults count] > 0) ) {
            realSection = [self sectionForSearchSection:section];
        } else {
            return 0.0;
        }
    }
    
    TiUIListSectionProxy *sectionProxy = [self.listViewProxy sectionForIndex:realSection];
    if([sectionProxy isHidden])
    {
        return 0.0;
    }
    if([sectionProxy.footerTitle length] > 0)
    {
        return [tableView sectionFooterHeight];
    }
    TiViewProxy* viewProxy = [sectionProxy sectionViewForLocation:@"footerView" inListView:self];
	
    CGFloat size = 0.0;
    if (viewProxy!=nil) {
        [viewProxy getAndPrepareViewForOpening:[self.tableView bounds]]; //to make sure it is setup
        size += [[viewProxy view] bounds].size.height;
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

-(CGFloat)computeRowWidth
{
    CGFloat rowWidth = _tableView.bounds.size.width;
    
    // Apple does not provide a good way to get information about the index sidebar size
    // in the event that it exists - it silently resizes row content which is "flexible width"
    // but this is not useful for us. This is a problem when we have Ti.UI.SIZE/FILL behavior
    // on row contents, which rely on the height of the row to be accurately precomputed.
    //
    // The following is unreliable since it uses a private API name, but one which has existed
    // since iOS 3.0. The alternative is to grab a specific subview of the tableview itself,
    // which is more fragile.
    
    NSArray* subviews = [_tableView subviews];
    if ([subviews count] > 0) {
        // Obfuscate private class name
        Class indexview = NSClassFromString([@"UITableView" stringByAppendingString:@"Index"]);
        for (UIView* view in subviews) {
            if ([view isKindOfClass:indexview]) {
                rowWidth -= [view frame].size.width;
            }
        }
    }
    
    return rowWidth;
}

-(CGFloat)tableView:(UITableView *)tableView rowHeight:(CGFloat)height
{
	if (TiDimensionIsDip(_minRowHeight))
	{
		height = MAX(_minRowHeight.value,height);
	}
	if (TiDimensionIsDip(_maxRowHeight))
	{
		height = MIN(_maxRowHeight.value,height);
	}
	return height < 1 ? tableView.rowHeight : height;
}

-(NSMutableArray*)visibleCellsProxies {
    NSArray* visibleCells = _tableView.visibleCells;
    if (!visibleCells) return nil;
    NSMutableArray* result = [NSMutableArray arrayWithCapacity:[visibleCells count]];
    [visibleCells enumerateObjectsUsingBlock:^(TiUIListItem* obj, NSUInteger idx, BOOL *stop) {
        if ([obj isKindOfClass:[TiUIListItem class]]) {
            [result addObject:((TiUIListItem*)obj).proxy];
        }
        
    }];
    return result;
}

-(TiUIListItem*)visibleCellAtIndexPath:(NSIndexPath *)indexPath {
    NSArray* visibleCells = _tableView.visibleCells;
    if (!visibleCells) return nil;
    __block TiUIListItem* result = nil;
    [visibleCells enumerateObjectsUsingBlock:^(TiUIListItem* obj, NSUInteger idx, BOOL *stop) {
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
    NSIndexPath* realIndexPath = [self pathForSearchPath:indexPath];
    
    id visibleProp = [self valueWithKey:@"visible" atIndexPath:realIndexPath];
    BOOL visible = realIndexPath?[visibleProp boolValue]:true;
    if (!visible) return 0.0f;
    
    id heightValue = [self valueWithKey:@"rowHeight" atIndexPath:realIndexPath];
    TiDimension height = _rowHeight;
    if (heightValue != nil) {
        height = [TiUtils dimensionValue:heightValue];
    }
    if (TiDimensionIsDip(height)) {
        return [self tableView:tableView rowHeight:height.value];
    }
    
    else if (TiDimensionIsAuto(height) || TiDimensionIsAutoSize(height))
    {
        TiUIListSectionProxy* theSection = [self.listViewProxy sectionForIndex:realIndexPath.section];
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
            cellProxy = [_measureProxies objectForKey:templateId];
        }
        if (cellProxy != nil) {
            CGFloat width = [cellProxy sizeWidthForDecorations:[self computeRowWidth] forceResizing:YES];
            if (width > 0) {
                [cellProxy setDataItem:item];
                return [self tableView:tableView rowHeight:[cellProxy minimumParentSizeForSize:CGSizeMake(width, 0)].height];
            }
        }
    }
    else if (TiDimensionIsPercent(height) || TiDimensionIsAutoFill(height)) {
        return [self tableView:tableView rowHeight:TiDimensionCalculateValue(height, tableView.bounds.size.height)];
    }
    return 44;
}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath
{
    if (allowsSelection==NO)
	{
		[tableView deselectRowAtIndexPath:indexPath animated:YES];
	}
    [self fireClickForItemAtIndexPath:[self pathForSearchPath:indexPath] tableView:tableView accessoryButtonTapped:NO];
}

- (void)tableView:(UITableView *)tableView accessoryButtonTappedForRowWithIndexPath:(NSIndexPath *)indexPath
{
    if (allowsSelection==NO)
	{
		[tableView deselectRowAtIndexPath:indexPath animated:YES];
	}
    [self fireClickForItemAtIndexPath:[self pathForSearchPath:indexPath] tableView:tableView accessoryButtonTapped:YES];
}

#pragma mark - ScrollView Delegate

- (NSMutableDictionary *) eventObjectForScrollView: (UIScrollView *) scrollView
{
	return [NSMutableDictionary dictionaryWithObjectsAndKeys:
			[TiUtils pointToDictionary:scrollView.contentOffset],@"contentOffset",
			[TiUtils sizeToDictionary:scrollView.contentSize], @"contentSize",
			[TiUtils sizeToDictionary:_tableView.bounds.size], @"size",
			nil];
}

- (void)fireScrollEvent:(UITableView *)scrollView {
    [self fireScrollEvent:@"scroll" forTableView:scrollView withAdditionalArgs:nil];
}

-(void)detectSectionChange {
    NSArray* visibles = [_tableView indexPathsForVisibleRows];
    NSIndexPath* indexPath = [visibles firstObject];
    NSInteger section = [indexPath section];
    if (_currentSection != section) {
        _currentSection = section;
        if ([[self viewProxy] _hasListeners:@"headerchange" checkParent:NO])
        {
            [self fireScrollEvent:@"headerchange" forTableView:_tableView withAdditionalArgs:@{
                                                                                               @"headerView":[self currentSectionViewProxy:_currentSection forLocation:@"headerView"]
                                                                                               }];
        }
    }
}

- (void)scrollViewDidScroll:(UIScrollView *)scrollView
{
    if (scrollView.isDragging || scrollView.isDecelerating)
	{
        [self fireScrollEvent:_tableView];
    }
    if ( _hasPullView && ([scrollView isTracking]) ) {
        BOOL pullChanged = NO;
        if ( (scrollView.contentOffset.y < pullThreshhold) && (pullActive == NO) ) {
            pullActive = YES;
            pullChanged = YES;
        } else if ( (scrollView.contentOffset.y > pullThreshhold) && (pullActive == YES) ) {
            pullActive = NO;
            pullChanged = YES;
        }
        if (pullChanged && [[self viewProxy] _hasListeners:@"pullchanged" checkParent:NO]) {
            [self fireScrollEvent:@"pullchanged" forTableView:_tableView withAdditionalArgs:@{@"active": @(pullActive)}];
        }
        if (scrollView.contentOffset.y <= 0 && [[self viewProxy] _hasListeners:@"pull" checkParent:NO]) {
            [self fireScrollEvent:@"pull" forTableView:_tableView withAdditionalArgs:@{@"active": @(pullActive)}];
        }
    }
    [self detectSectionChange];
}


// For now, this is fired on `scrollstart` and `scrollend`
- (void)fireScrollEvent:(NSString*)eventName forTableView:(UITableView*)tableView withAdditionalArgs:(NSDictionary*)args
{
    if([[self viewProxy] _hasListeners:eventName checkParent:NO])
    {
        NSArray* indexPaths = [tableView indexPathsForVisibleRows];
        NSIndexPath *indexPath = [self pathForSearchPath:[indexPaths objectAtIndex:0]];

        NSUInteger visibleItemCount = [indexPaths count];
        
        TiUIListSectionProxy* section = [[self listViewProxy] sectionForIndex: [indexPath section]];
        
        NSMutableDictionary* eventArgs = [self eventObjectForScrollView:tableView];
        [eventArgs setValuesForKeysWithDictionary:args];
        [eventArgs setValue:NUMINTEGER([indexPath row]) forKey:@"firstVisibleItemIndex"];
        [eventArgs setValue:NUMUINTEGER(visibleItemCount) forKey:@"visibleItemCount"];
        [eventArgs setValue:NUMINTEGER([indexPath section]) forKey:@"firstVisibleSectionIndex"];
        [eventArgs setValue:section forKey:@"firstVisibleSection"];
        [eventArgs setValue:[section itemAtIndex:[indexPath row]] forKey:@"firstVisibleItem"];
        [[self proxy] fireEvent:eventName withObject:eventArgs propagate:NO];
    }
}

- (void)fireScrollEnd:(UITableView*)tableView
{
    if(canFireScrollEnd) {
        canFireScrollEnd = NO;
        canFireScrollStart = YES;
        [self fireScrollEvent:@"scrollend" forTableView:tableView withAdditionalArgs:nil];
    }
}
- (void)fireScrollStart:(UITableView *)tableView
{
    if(canFireScrollStart) {
        canFireScrollStart = NO;
        canFireScrollEnd = YES;
        [self fireScrollEvent:@"scrollstart" forTableView:tableView withAdditionalArgs:nil];
    }
}

- (void)scrollViewWillBeginDragging:(UIScrollView *)scrollView
{
    if (_scrollHidesKeyboard) {
        [scrollView endEditing:YES];
    }
	// suspend image loader while we're scrolling to improve performance
	if (_scrollSuspendImageLoading) [[ImageLoader sharedLoader] suspend];
	[self fireScrollStart: (UITableView*)scrollView];
    [self fireScrollEvent:@"dragstart" forTableView:(UITableView *)scrollView withAdditionalArgs:nil];
}

- (void)scrollViewDidEndDragging:(UIScrollView *)scrollView willDecelerate:(BOOL)decelerate
{
	if(!decelerate) {
        [self detectSectionChange];
		if (_scrollSuspendImageLoading) {
            // resume image loader when we're done scrolling
            [[ImageLoader sharedLoader] resume];
        }
        [self fireScrollEnd:(UITableView *)scrollView];
    }    
	if ([(TiViewProxy*)self.proxy _hasListeners:@"dragend" checkParent:NO])
	{
        [self fireScrollEvent:@"dragend" forTableView:(UITableView *)scrollView withAdditionalArgs:nil];
	}
    
    [self detectSectionChange];
    
    if ( _hasPullView && (pullActive == YES) ) {
        pullActive = NO;
        [self fireScrollEvent:@"pullend" forTableView:(UITableView *)scrollView withAdditionalArgs:nil];
    }
}

- (void)scrollViewDidEndDecelerating:(UIScrollView *)scrollView
{
	// resume image loader when we're done scrolling
	if (_scrollSuspendImageLoading) [[ImageLoader sharedLoader] resume];
    [self fireScrollEvent:_tableView];
	if(isScrollingToTop) {
        isScrollingToTop = NO;
    } else {
        [self fireScrollEnd:(UITableView *)scrollView];
    }
    [self detectSectionChange];
}

- (BOOL)scrollViewShouldScrollToTop:(UIScrollView *)scrollView
{
	// suspend image loader while we're scrolling to improve performance
	if (_scrollSuspendImageLoading) [[ImageLoader sharedLoader] suspend];
	isScrollingToTop = YES;
    [self fireScrollStart:(UITableView*) scrollView];
	return YES;
}

- (void)scrollViewDidScrollToTop:(UIScrollView *)scrollView
{
    if (_scrollSuspendImageLoading) [[ImageLoader sharedLoader] resume];
    [self fireScrollEnd:(UITableView *)scrollView];
    //Events none (maybe scroll later)
}

#pragma mark Overloaded view handling
- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event
{
	UIView * result = [super hitTest:point withEvent:event];
	if(result == self)
	{	//There is no valid reason why the TiUITableView will get an
		//touch event; it should ALWAYS be a child view.
		return nil;
	}
	return result;
}

- (void)touchesBegan:(NSSet *)touches withEvent:(UIEvent *)event
{
	// iOS idiom seems to indicate that you should never be able to interact with a table
	// while the 'delete' button is showing for a row, but touchesBegan:withEvent: is still triggered.
	// Turn it into a no-op while we're editing
	if (!editing) {
		[super touchesBegan:touches withEvent:event];
	}
}

-(void)recognizedSwipe:(UISwipeGestureRecognizer *)recognizer
{
    BOOL viaSearch = [self isSearchActive];
    UITableView* theTableView = viaSearch ? [[self searchController] searchResultsTableView] : [self tableView];
    CGPoint point = [recognizer locationInView:theTableView];
    NSIndexPath* indexPath = [theTableView indexPathForRowAtPoint:point];
    indexPath = [self pathForSearchPath:indexPath];
    if (indexPath != nil) {
        if ([[self proxy] _hasListeners:@"swipe"]) {
            NSMutableDictionary *event = [self EventObjectForItemAtIndexPath:indexPath tableView:theTableView];
            [event setValuesForKeysWithDictionary:[TiUtils dictionaryFromGesture:recognizer inView:theTableView]];
            [[self proxy] fireEvent:@"swipe" withObject:event propagate:NO checkForListener:NO];
        }
        
    }
    else {
        [super recognizedSwipe:recognizer];
    }
    
    if (allowsSelection == NO)
    {
        [theTableView deselectRowAtIndexPath:indexPath animated:YES];
    }
}

-(void)recognizedLongPress:(UILongPressGestureRecognizer*)recognizer
{
    if ([recognizer state] == UIGestureRecognizerStateBegan) {
        BOOL viaSearch = [self isSearchActive];
        UITableView* theTableView = viaSearch ? [[self searchController] searchResultsTableView] : [self tableView];
        CGPoint point = [recognizer locationInView:theTableView];
        NSIndexPath* indexPath = [theTableView indexPathForRowAtPoint:point];
        indexPath = [self pathForSearchPath:indexPath];
        
        NSMutableDictionary *event;
        if (indexPath != nil) {
            if ([[self proxy] _hasListeners:@"longpress"]) {
                NSMutableDictionary *event = [self EventObjectForItemAtIndexPath:indexPath tableView:theTableView atPoint:point];
                [event setValuesForKeysWithDictionary:[TiUtils dictionaryFromGesture:recognizer inView:theTableView]];
                [[self proxy] fireEvent:@"longpress" withObject:event propagate:NO checkForListener:NO];
            }
        }
        else {
            [super recognizedLongPress:recognizer];
        }
        
        if (allowsSelection == NO)
        {
            [theTableView deselectRowAtIndexPath:indexPath animated:YES];
        }
        TiUISearchBarProxy* searchViewProxy = (TiUISearchBarProxy*) [self holdedProxyForKey:@"searchView"];
        if (viaSearch && searchViewProxy) {
            if (hideOnSearch) {
                [self hideSearchScreen:nil];
            }
            else {
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
    TiViewProxy* vp = [self holdedProxyForKey:@"searchView"];
    if (vp) {
        [vp layoutProperties]->right = TiDimensionDip(0);
        [vp refreshViewIfNeeded];
    }
}

- (void)searchBarTextDidBeginEditing:(UISearchBar *)searchBar
{
    self.searchString = (searchBar.text == nil) ? @"" : searchBar.text;
}

- (void)searchBarTextDidEndEditing:(UISearchBar *)searchBar
{
    if ([searchBar.text length] == 0) {
        self.searchString = @"";
        [self buildResultsForSearchText];
        if ([[self searchController] isActive]) {
            [[self searchController] setActive:NO animated:YES];
        }
    }
}

- (void)searchBar:(UISearchBar *)searchBar textDidChange:(NSString *)searchText
{
    self.searchString = (searchText == nil) ? @"" : searchText;
    [self buildResultsForSearchText];
}

- (void)searchBarSearchButtonClicked:(UISearchBar *)searchBar
{
    [searchBar resignFirstResponder];
    [self makeRootViewFirstResponder];
}

- (void)searchBarCancelButtonClicked:(UISearchBar *) searchBar
{
    self.searchString = @"";
    [searchBar setText:self.searchString];
    [self buildResultsForSearchText];
}

#pragma mark - UISearchDisplayDelegate Methods

- (void)searchDisplayControllerWillBeginSearch:(UISearchDisplayController *)controller
{
    searchViewAnimating = YES;
	[_tableView setContentOffset:CGPointZero animated:NO];
    
}

- (void) searchDisplayControllerWillEndSearch:(UISearchDisplayController *)controller {
    searchViewAnimating = YES;
}

- (void) searchDisplayControllerDidBeginSearch:(UISearchDisplayController *)controller {
    searchViewAnimating = NO;
    [controller.searchBar setText:self.searchString];
    [self buildResultsForSearchText];
    [[controller searchResultsTableView] reloadData];
}

- (void) searchDisplayControllerDidEndSearch:(UISearchDisplayController *)controller
{
    searchViewAnimating = NO;
    self.searchString = @"";
    [self buildResultsForSearchText];
    
    //IOS7 DP3. TableView seems to be adding the searchView to
    //tableView. Bug on IOS7?
    TiUISearchBarProxy* searchViewProxy = (TiUISearchBarProxy*) [self holdedProxyForKey:@"searchView"];
    if (searchViewProxy) {
        CGFloat rowWidth = floorf([self computeRowWidth:_tableView]);
        if (rowWidth > 0) {
            CGFloat right = _tableView.bounds.size.width - rowWidth;
            [searchViewProxy layoutProperties]->right = TiDimensionDip(right);
            [searchViewProxy refreshViewIfNeeded];
        }
        [searchViewProxy ensureSearchBarHeirarchy];
    }
    [self reloadTableViewData];
    [self hideSearchScreen:nil];
//    [self performSelector:@selector(hideSearchScreen:) withObject:nil afterDelay:0.2];
}

#pragma mark - TiScrolling

-(void)keyboardDidShowAtHeight:(CGFloat)keyboardTop
{
    CGRect minimumContentRect = [_tableView bounds];
    InsetScrollViewForKeyboard(_tableView,keyboardTop,minimumContentRect.size.height + minimumContentRect.origin.y);
}

-(void)scrollToShowView:(UIView *)firstResponderView withKeyboardHeight:(CGFloat)keyboardTop
{
    if ([_tableView isScrollEnabled]) {
        CGRect minimumContentRect = [_tableView bounds];
        CGRect responderRect = [self convertRect:[firstResponderView bounds] fromView:firstResponderView];
        CGPoint offsetPoint = [_tableView contentOffset];
        responderRect.origin.x += offsetPoint.x;
        responderRect.origin.y += offsetPoint.y;
        
        OffsetScrollViewForRect(_tableView,keyboardTop,minimumContentRect.size.height + minimumContentRect.origin.y,responderRect);
    }
}


#pragma mark - Internal Methods

- (NSMutableDictionary*)EventObjectForItemAtIndexPath:(NSIndexPath *)indexPath tableView:(UITableView *)tableView  atPoint:(CGPoint)point accessoryButtonTapped:(BOOL)accessoryButtonTapped
{
    TiUIListSectionProxy *section = [self.listViewProxy sectionForIndex:indexPath.section];
	NSDictionary *item = [section itemAtIndex:indexPath.row];
    NSMutableDictionary *eventObject = [[NSMutableDictionary alloc] initWithObjectsAndKeys:
										section, @"section",
                                        self.proxy, @"listView",
										NUMBOOL([self isSearchActive]), @"searchResult",
										NUMINTEGER(indexPath.section), @"sectionIndex",
                                        item, @"item",
                                        NUMINTEGER(indexPath.row), @"itemIndex",
										NUMBOOL(accessoryButtonTapped), @"accessoryClicked",
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
        TiViewProxy *tapViewProxy =[TiUIHelper findViewProxyWithBindIdUnder:contentView containingPoint:[tableView convertPoint:point toView:contentView]];
		if (tapViewProxy != nil) {
			[eventObject setObject:[tapViewProxy valueForKey:@"bindId"] forKey:@"bindId"];
		}
	}
    return [eventObject autorelease];
}

- (NSMutableDictionary*)EventObjectForItemAtIndexPath:(NSIndexPath *)indexPath tableView:(UITableView *)tableView
{
    return [self EventObjectForItemAtIndexPath:indexPath tableView:tableView atPoint:[_tableView touchPoint]];
}

- (NSMutableDictionary*)EventObjectForItemAtIndexPath:(NSIndexPath *)indexPath tableView:(UITableView *)tableView atPoint:(CGPoint)point
{
    NSMutableDictionary *event = [self EventObjectForItemAtIndexPath:indexPath tableView:tableView atPoint:point accessoryButtonTapped:NO];
    [event setObject:NUMFLOAT(point.x) forKey:@"x"];
    [event setObject:NUMFLOAT(point.y) forKey:@"y"];
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


- (NSIndexPath *) nextIndexPath:(NSIndexPath *) indexPath {
    NSInteger numOfSections = [self numberOfSectionsInTableView:self.tableView];
    if (numOfSections == 0) return nil;
    NSInteger nextSection = ((indexPath.section + 1) % numOfSections);
    
    if (indexPath.row + 1 == [self tableView:self.tableView numberOfRowsInSection:indexPath.section]) {
        return [NSIndexPath indexPathForRow:0 inSection:nextSection];
    } else {
        return [NSIndexPath indexPathForRow:(indexPath.row + 1) inSection:indexPath.section];
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

+ (void)setBackgroundColor:(UIColor*)bgColor onTable:(UITableView*)table
{
	UIColor* defaultColor = [table style] == UITableViewStylePlain ? [UIColor whiteColor] : [UIColor groupTableViewBackgroundColor];
	
	// WORKAROUND FOR APPLE BUG: 4.2 and lower don't like setting background color for grouped table views on iPad.
	// So, we check the table style and device, and if they match up wrong, we replace the background view with our own.
	if ([table style] == UITableViewStyleGrouped) {
		UIView* bgView = [[[UIView alloc] initWithFrame:[table frame]] autorelease];
		[table setBackgroundView:bgView];
	}
	
	[table setBackgroundColor:(bgColor != nil ? bgColor : defaultColor)];
	[[table backgroundView] setBackgroundColor:[table backgroundColor]];
	
	[table setOpaque:![[table backgroundColor] isEqual:[UIColor clearColor]]];
}

+ (UITableViewRowAnimation)animationStyleForProperties:(NSDictionary*)properties
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
