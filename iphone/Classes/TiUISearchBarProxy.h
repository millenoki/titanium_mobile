/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UISEARCHBAR

#import "TiUISearchBar.h"
#import "TiUITextWidgetProxy.h"

@interface TiUISearchBarProxy : TiUITextWidgetProxy {
  BOOL showsCancelButton;
}
@property BOOL canHaveSearchDisplayController;

- (void)setDelegate:(id<UISearchBarDelegate>)delegate;
- (UISearchBar *)searchBar;

#pragma mark - Titanium Internal Use
- (TiSearchDisplayController *)searchController;
- (void)setSearchBar:(UISearchBar *)searchBar;
- (void)ensureSearchBarHierarchy;
@end

#endif
