/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#import "ADTransitioningViewController.h"
#import "TiControllerProtocols.h"
#import <UIKit/UIKit.h>

@interface ControllerWrapperView : UIView
@property (nonatomic, assign) TiViewProxy *proxy;

@end

@interface TiViewController : ADTransitioningViewController {

  TiViewProxy *_proxy;
  TiOrientationFlags _supportedOrientations;
}

@property (nonatomic, retain) NSArray *previewActions;

- (id)initWithViewProxy:(TiViewProxy *)window;
- (void)detachProxy;
- (TiViewProxy *)proxy;

@end
