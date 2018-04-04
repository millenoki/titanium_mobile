//
//  TiTransition.m
//  Titanium
//
//  Created by Martin Guillon on 18/10/13.
//
//

#import "TiTransition.h"
#import "TiBase.h"
#import "ADDualTransition.h"
#import "TiViewController.h"

@implementation TiADTransition {
  TiAnimation * _inAnimation;
  TiAnimation * _outAnimation;
}

- (void)dealloc
{
  RELEASE_TO_NIL(_inAnimation);
  RELEASE_TO_NIL(_outAnimation);
  [super dealloc];
}
- (id)initWithInAnimation:(TiAnimation *)inAnimation andOutAnimation:(TiAnimation *)outAnimation orientation:(ADTransitionOrientation)orientation sourceRect:(CGRect)sourceRect reversed:(BOOL)reversed {
  if (self = [super initWithDuration:inAnimation.duration orientation:orientation sourceRect:sourceRect reversed:reversed]) {
    _inAnimation = [inAnimation retain];
    _outAnimation = [outAnimation retain];
  }
  return self;
}
- (id)initWithInAnimation:(TiAnimation *)inAnimation andOutAnimation:(TiAnimation *)outAnimation {
  if (self = [self initWithInAnimation:inAnimation andOutAnimation:outAnimation orientation:ADTransitionLeftToRight sourceRect:CGRectZero reversed:NO]) {
  }
  return self;
}
- (TiADTransition *)reverseTransitionForSourceRect:(CGRect)rect {
  
  return [[[self class] alloc] initWithInAnimation:_inAnimation andOutAnimation:_outAnimation orientation:self.orientation sourceRect:rect reversed:!self.isReversed];
}

-(void)prepareTransitionFromView:(UIView *)viewOut toView:(UIView *)viewIn inside:(UIView *)viewContainer {
  [super prepareTransitionFromView:viewOut toView:viewIn inside:viewContainer];
}

-(void)startTransitionFromView:(UIView *)viewOut toView:(UIView *)viewIn inside:(UIView *)viewContainer {
  [super startTransitionFromView:viewOut toView:viewIn inside:viewContainer];
  if (_inAnimation && IS_OF_CLASS(viewIn, ControllerWrapperView)) {
     [[(ControllerWrapperView*)viewIn proxy] handleAnimation:_inAnimation witDelegate:self];
  }
  if (_outAnimation && IS_OF_CLASS(viewOut, ControllerWrapperView)) {
    [[(ControllerWrapperView*)viewOut proxy] handleAnimation:_outAnimation witDelegate:self];
  }
//  [viewIn.layer addAnimation:self.inAnimation forKey:kAdKey];
////  [viewOut.layer addAnimation:self.outAnimation forKey:kAdKey];
//  if (_self.inAnimation != nil && fromProxy != nil) {
//    [fromProxy handleAnimation:_transitionFrom witDelegate:self];
//  }
//  if (_transitionTo != nil && toProxy != nil) {
//    [toProxy handleAnimation:_transitionTo witDelegate:self];
//  }
}
- (NSTimeInterval)duration {
  NSTimeInterval result = MAX(_inAnimation.duration, _outAnimation.duration);
  return result;
}

- (void)animationDidComplete:(TiAnimation *)animation;
{
  if (animation == _outAnimation) {
//    _endedFrom = YES;
    [animation.animatedProxy animationDidComplete:animation];
  }
  if (animation == _inAnimation) {
//    _endedTo = YES;
    [animation.animatedProxy animationDidComplete:animation];
  }
//  if (_endedTo && _endedFrom) {
//    [_transitionContext completeTransition:YES];
//  }
}

-(BOOL)onlyForPush {
  return super.onlyForPush || _outAnimation == nil;
}

-(BOOL)onlyForPop {
  return super.onlyForPop ||  _inAnimation == nil;
}
@end

@implementation TiTransition {
  TiAnimation *_inAnimation;
  TiAnimation *_outAnimation;
}
@synthesize adTransition = _adTransition;
@synthesize orientation;
@synthesize custom;

- (TiTransition *)initCustomTransitionWithDict:(NSDictionary *)options
{
  if (self = [super init]) {
    custom = YES;
    id defaultDuration = [options objectForKey:@"duration"] ? [options objectForKey:@"duration"] : @(0);
    //        id defaultCurve = [options objectForKey:@"curve"]?[options objectForKey:@"curve"]:[NSNull null];
    //        BOOL reversed =  [TiUtils boolValue:@"reverse" properties:options def:NO];
    NSDictionary *from = [options objectForKey:@"from"];
    NSDictionary *to = [options objectForKey:@"to"];
    if (from) {
      _inAnimation = [[TiAnimation alloc] initWithDictionary:@{
                                                               @"from" : from,
                                                               @"duration" : [from objectForKey:@"duration"] ? [from objectForKey:@"duration"] : defaultDuration,
                                                               @"curve" : [from objectForKey:@"curve"] ? [from objectForKey:@"curve"] : defaultDuration,
                                                               }
                                                     context:[self executionContext]
                                                    callback:nil];
    }
    if (to) {
      _outAnimation = [[TiAnimation alloc] initWithDictionary:@{
                                                                @"to" : to,
                                                                @"duration" : [to objectForKey:@"duration"] ? [to objectForKey:@"duration"] : defaultDuration,
                                                                @"curve" : [from objectForKey:@"curve"] ? [to objectForKey:@"curve"] : defaultDuration,
                                                                }
                                                      context:[self executionContext]
                                                     callback:nil];
    }
    
                       
    _adTransition = [[TiADTransition alloc] initWithInAnimation:_inAnimation andOutAnimation:_outAnimation];
  }
  return self;
}
- (id)init
{
  if (self = [super init]) {
    custom = NO;
  }
  return self;
}

- (Class)adTransitionClass
{
  return [ADTransition class];
}

- (id)initWithDuration:(CFTimeInterval)duration orientation:(ADTransitionOrientation)_orientation sourceRect:(CGRect)sourceRect reversed:(BOOL)reversed
{
  if (self = [super init]) {
    _adTransition = [[[self adTransitionClass] alloc] initWithDuration:duration orientation:_orientation sourceRect:sourceRect reversed:reversed];
    self.duration = duration;
  }
  return self;
}

- (void)dealloc
{
  RELEASE_TO_NIL(_inAnimation);
  RELEASE_TO_NIL(_outAnimation);
  RELEASE_TO_NIL(_adTransition);
  [super dealloc];
}
- (id)initWithADTransition:(ADTransition *)transition
{
  if (self = [super init]) {
    _adTransition = [transition retain];
  }
  return self;
}

- (void)transformView:(UIView *)view withPosition:(CGFloat)position size:(CGSize)size
{
  view.layer.hidden = YES;
  view.layer.transform = CATransform3DIdentity;
}
- (void)transformView:(UIView *)view withPosition:(CGFloat)position
{
  [self transformView:view withPosition:position size:view.bounds.size];
}
- (BOOL)needsReverseDrawOrder
{
  return NO;
}
- (void)prepareViewHolder:(UIView *)holder {}

- (BOOL)isTransitionPush
{
  return _adTransition && [TiTransitionHelper isTransitionPush:_adTransition];
}

- (BOOL)isTransitionVertical
{
  return _adTransition && [TiTransitionHelper isTransitionVertical:_adTransition];
}

- (ADTransitionOrientation)getOrientation
{
  return _adTransition && _adTransition.orientation;
}

- (void)reverseADTransitionForSourceRect:(CGRect)rect
{
  if (_adTransition) {
    ADTransition *reverse = [_adTransition reverseTransitionForSourceRect:rect];
    _adTransition = [reverse retain];
  }
}

- (void)finishedTransitionFromView:(UIView *)viewOut toView:(UIView *)viewIn inside:(UIView *)viewContainer
{
}

@end
