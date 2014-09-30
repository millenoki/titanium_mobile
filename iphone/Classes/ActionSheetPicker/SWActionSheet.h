//
// Created by Petr Korolev on 11/08/14.
//

#import <Foundation/Foundation.h>
@class SWActionSheet;
@protocol SWActionSheetDelegate <NSObject>
@optional

// Called when a button is clicked. The view will be automatically dismissed after this call returns
- (void)actionSheet:(SWActionSheet *)actionSheet clickedButtonAtIndex:(NSInteger)buttonIndex;

// Called when we cancel a view (eg. the user clicks the Home button). This is not called when the user clicks the cancel button.
// If not defined in the delegate, we simulate a click in the cancel button
- (void)actionSheetCancel:(SWActionSheet *)actionSheet;

- (void)willPresentActionSheet:(SWActionSheet *)actionSheet;  // before animation and showing view
- (void)didPresentActionSheet:(SWActionSheet *)actionSheet;  // after animation

- (void)actionSheet:(SWActionSheet *)actionSheet willDismissWithButtonIndex:(NSInteger)buttonIndex; // before animation and hiding view
- (void)actionSheet:(SWActionSheet *)actionSheet didDismissWithButtonIndex:(NSInteger)buttonIndex;  // after animation

@end

@interface SWActionSheet : UIView
@property(nonatomic, strong) UIView *bgView;
@property(nonatomic, strong) id<SWActionSheetDelegate> delegate;

- (void)dismissWithClickedButtonIndex:(int)i animated:(BOOL)animated;

- (void)showFromBarButtonItem:(UIBarButtonItem *)item animated:(BOOL)animated;

- (id)initWithView:(UIView *)view;

- (void)showInContainerView;
@end