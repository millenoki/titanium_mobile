/*
 * MGSwipeTableCell is licensed under MIT license. See LICENSE.md file for more information.
 * Copyright (c) 2014 Imanol Fernandez @MortimerGoro
 */

#import <UIKit/UIKit.h>

#import "MGSwipeTableCell.h"

/** helper forward declaration */
@class MGSwipeCollectionViewCell;

/** 
 * Optional delegate to configure swipe buttons or to receive triggered actions.
 * Buttons can be configured inline when the cell is created instead of using this delegate,
 * but using the delegate improves memory usage because buttons are only created in demand
 */
@protocol MGSwipeCollectionViewCellDelegate <NSObject>

@optional
/**
 * Delegate method to enable/disable swipe gestures
 * @return YES if swipe is allowed
 **/
-(BOOL) swipeTableCell:(MGSwipeCollectionViewCell*) cell canSwipe:(MGSwipeDirection) direction fromPoint:(CGPoint) point;

/**
 * Delegate method invoked when the current swipe state changes
 @param state the current Swipe State
 @param gestureIsActive YES if the user swipe gesture is active. No if the uses has already ended the gesture
 **/
-(void) swipeTableCell:(MGSwipeCollectionViewCell*) cell didChangeSwipeState:(MGSwipeState) state gestureIsActive:(BOOL) gestureIsActive;

/**
 * Called when the user clicks a swipe button or when a expandable button is automatically triggered
 * @return YES to autohide the current swipe buttons
 **/
-(BOOL) swipeTableCell:(MGSwipeCollectionViewCell*) cell tappedButtonAtIndex:(NSInteger) index direction:(MGSwipeDirection)direction fromExpansion:(BOOL) fromExpansion;
/**
 * Delegate method to setup the swipe buttons and swipe/expansion settings
 * Buttons can be any kind of UIView but it's recommended to use the convenience MGSwipeButton class
 * Setting up buttons with this delegate instead of using cell properties improves memory usage because buttons are only created in demand
 * @param swipeTableCell the UITableVieCel to configure. You can get the indexPath using [tableView indexPathForCell:cell]
 * @param direction The swipe direction (left to right or right to left)
 * @param swipeSettings instance to configure the swipe transition and setting (optional)
 * @param expansionSettings instance to configure button expansions (optional)
 * @return Buttons array
 **/
-(NSArray*) swipeTableCell:(MGSwipeCollectionViewCell*) cell swipeButtonsForDirection:(MGSwipeDirection)direction
             swipeSettings:(MGSwipeSettings*) swipeSettings expansionSettings:(MGSwipeExpansionSettings*) expansionSettings;

/**
 * Called when the user taps on a swiped cell
 * @return YES to autohide the current swipe buttons
 **/
-(BOOL) swipeTableCell:(MGSwipeCollectionViewCell *)cell shouldHideSwipeOnTap:(CGPoint) point;

/**
 * Called when the cell will begin swiping
 * Useful to make cell changes that only are shown after the cell is swiped open
 **/
-(void) swipeTableCellWillBeginSwiping:(MGSwipeCollectionViewCell *) cell;

/**
 * Called when the cell will end swiping
 **/
-(void) swipeTableCellWillEndSwiping:(MGSwipeCollectionViewCell *) cell;

@end


/**
 * Swipe Cell class
 * To implement swipe cells you have to override from this class
 * You can create the cells programmatically, using xibs or storyboards
 */
@interface MGSwipeCollectionViewCell : UICollectionViewCell<MGSwipeCell, UIGestureRecognizerDelegate>

/** optional delegate (not retained) */
@property (nonatomic, weak) id<MGSwipeCollectionViewCellDelegate> delegate;

/** optional to use contentView alternative. Use this property instead of contentView to support animated views while swiping */
@property (nonatomic, strong, readonly) UIView * swipeContentView;

/** 
 * Left and right swipe buttons and its settings.
 * Buttons can be any kind of UIView but it's recommended to use the convenience MGSwipeButton class
 */
@property (nonatomic, copy) NSArray * leftButtons;
@property (nonatomic, copy) NSArray * rightButtons;
@property (nonatomic, strong) MGSwipeSettings * leftSwipeSettings;
@property (nonatomic, strong) MGSwipeSettings * rightSwipeSettings;

/** Optional settings to allow expandable buttons */
@property (nonatomic, strong) MGSwipeExpansionSettings * leftExpansion;
@property (nonatomic, strong) MGSwipeExpansionSettings * rightExpansion;

/** Readonly property to fetch the current swipe state */
@property (nonatomic, readonly) MGSwipeState swipeState;
/** Readonly property to check if the user swipe gesture is currently active */
@property (nonatomic, readonly) BOOL isSwipeGestureActive;

// default is NO. Controls whether multiple cells can be swiped simultaneously
@property (nonatomic) BOOL allowsMultipleSwipe;
// default is NO. Controls whether buttons with different width are allowed. Buttons are resized to have the same size by default.
@property (nonatomic) BOOL allowsButtonsWithDifferentWidth;
//default is YES. Controls wheter swipe gesture is allowed when the touch starts into the swiped buttons
@property (nonatomic) BOOL allowsSwipeWhenTappingButtons;
// default is NO.  Controls whether the cell selection/highlight status is preserved when expansion occurs
@property (nonatomic) BOOL preservesSelectionStatus;

/** Optional background color for swipe overlay. If not set, its inferred automatically from the cell contentView */
@property (nonatomic, strong) UIColor * swipeBackgroundColor;
/** Property to read or change the current swipe offset programmatically */
@property (nonatomic, assign) CGFloat swipeOffset;

/** Utility methods to show or hide swipe buttons programmatically */
-(void) hideSwipeAnimated: (BOOL) animated;
-(void) hideSwipeAnimated: (BOOL) animated completion:(void(^)()) completion;
-(void) showSwipe: (MGSwipeDirection) direction animated: (BOOL) animated;
-(void) showSwipe: (MGSwipeDirection) direction animated: (BOOL) animated completion:(void(^)()) completion;
-(void) setSwipeOffset:(CGFloat)offset animated: (BOOL) animated completion:(void(^)()) completion;
-(void) setSwipeOffset:(CGFloat)offset animation: (MGSwipeAnimation *) animation completion:(void(^)()) completion;
-(void) expandSwipe: (MGSwipeDirection) direction animated: (BOOL) animated;

/** Refresh method to be used when you want to update the cell contents while the user is swiping */
-(void) refreshContentView;
/** Refresh method to be used when you want to dinamically change the left or right buttons (add or remove)
 * If you only want to change the title or the backgroundColor of a button you can change it's properties (get the button instance from leftButtons or rightButtons arrays)
 * @param usingDelegate if YES new buttons will be fetched using the MGSwipeTableCellDelegate. Otherwise new buttons will be fetched from leftButtons/rightButtons properties.
 */
-(void) refreshButtons: (BOOL) usingDelegate;

@end

