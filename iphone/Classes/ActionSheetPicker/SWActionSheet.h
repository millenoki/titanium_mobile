//
// Created by Petr Korolev on 11/08/14.
//


#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

@interface SWActionSheet : UIView
@property(nonatomic, strong) UIView *bgView;

- (void)dismissWithClickedButtonIndex:(NSInteger)i animated:(BOOL)animated;

- (void)showFromBarButtonItem:(UIBarButtonItem *)item animated:(BOOL)animated;

- (id)initWithView:(UIView *)view;

- (void)showInContainerView;
@end