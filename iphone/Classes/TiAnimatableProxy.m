#import "TiAnimatableProxy.h"

@interface TiAnimatableProxy()
{
    NSMutableArray* _pendingAnimations;
    NSMutableArray* _runningAnimations;
	pthread_rwlock_t runningLock;
	pthread_rwlock_t pendingLock;
    BOOL _animating;
    BOOL _goingThroughPending;
}

@end

@implementation TiAnimatableProxy
@synthesize animating = _animating;

-(id)init
{
    self = [super init];
    if (self != nil) {
		pthread_rwlock_init(&runningLock, NULL);
		pthread_rwlock_init(&pendingLock, NULL);
        _pendingAnimations = [[NSMutableArray alloc] init];
        _runningAnimations = [[NSMutableArray alloc] init];
        _animating = NO;
        _goingThroughPending = NO;
    }
    return self;
}

-(void)dealloc
{
	pthread_rwlock_rdlock(&pendingLock);
	RELEASE_TO_NIL(_pendingAnimations);
	pthread_rwlock_unlock(&pendingLock);
	pthread_rwlock_destroy(&pendingLock);
	pthread_rwlock_rdlock(&runningLock);
	RELEASE_TO_NIL(_runningAnimations);
	pthread_rwlock_unlock(&runningLock);
	pthread_rwlock_destroy(&runningLock);
	[super dealloc];
}

-(BOOL)animating
{
    return (_animating || [_runningAnimations count] > 0);
}

-(void)clearAnimations
{
    [self cancelAllAnimations:nil];
}

-(void)removePendingAnimation:(TiAnimation *)animation
{
	pthread_rwlock_rdlock(&pendingLock);
    if ([_pendingAnimations containsObject:animation])
    {
        [self forgetProxy:animation];
        [_pendingAnimations removeObject:animation];
    }
	pthread_rwlock_unlock(&pendingLock);
}

-(void)addRunningAnimation:(TiAnimation *)animation
{
	pthread_rwlock_rdlock(&runningLock);
    [_runningAnimations addObject:animation];
	pthread_rwlock_unlock(&runningLock);
}
-(void)removeRunningAnimation:(TiAnimation *)animation
{
	pthread_rwlock_rdlock(&runningLock);
    if ([_runningAnimations containsObject:animation])
    {
        [self forgetProxy:animation];
        [_runningAnimations removeObject:animation];
    }
	pthread_rwlock_unlock(&runningLock);
}

-(void)cancelPending {
    pthread_rwlock_rdlock(&pendingLock);
    if ([_pendingAnimations count] == 0) {
        pthread_rwlock_unlock(&pendingLock);
        return;
    }
    NSArray* pending = _pendingAnimations;
    _pendingAnimations = [[NSMutableArray alloc] init];
    pthread_rwlock_unlock(&pendingLock);
    for (TiAnimation* animation in pending) {
        [self removePendingAnimation:animation];
    }
    [pending release];
}

-(void)cancelRunning {
    pthread_rwlock_rdlock(&runningLock);
    if ([_runningAnimations count] == 0) {
        pthread_rwlock_unlock(&runningLock);
        return;
    }
    NSArray* running = _runningAnimations;
    _runningAnimations = [NSMutableArray new];
    pthread_rwlock_unlock(&runningLock);
    for (TiAnimation* animation in running) {
        [self removeRunningAnimation:animation];
        [animation cancelWithReset:animation.restartFromBeginning];
    }
    [running release];
}

-(void)cancelAllAnimations:(id)arg
{
    [self cancelPending];
    [self cancelRunning];
}

-(void)cancelAnimation:(TiAnimation *)animation shouldReset:(BOOL)reset
{
    [self animationDidComplete:animation];
    if (reset) [self resetProxyPropertiesForAnimation:animation];
}

-(HLSAnimation*)animationForAnimation:(TiAnimation*)animation
{
    return [HLSAnimation animationWithAnimationSteps:nil];
}


-(void)cancelAnimation:(TiAnimation *)animation
{
    [self cancelAnimation:animation shouldReset:YES];
}

-(void)animationWillStart:(TiAnimation *)animation
{
    
}

-(void)animationWillComplete:(TiAnimation *)animation
{
    
}

-(void)animationDidStart:(TiAnimation*)animation
{
}

-(void)animationDidComplete:(TiAnimation*)animation
{
    if (animation.animation)
    {
        TiThreadPerformBlockOnMainThread(^{
            [animation.animation cancel];
            animation.animation = nil;
        }, YES);
    }
    [self removeRunningAnimation:animation];
}

-(void)handlePendingAnimation
{
    if (_goingThroughPending || ![self readyToAnimate]) {
        return;
    }
    pthread_rwlock_rdlock(&pendingLock);
    if ([_pendingAnimations count] == 0) {
        pthread_rwlock_unlock(&pendingLock);
        return;
    }
    
    NSArray* pending = _pendingAnimations;
    _pendingAnimations = [[NSMutableArray alloc] init];
	pthread_rwlock_unlock(&pendingLock);
    for (TiAnimation* anim in pending) {
        [self handlePendingAnimation:anim];
    }
    [pending release];
}

-(void)handlePendingAnimation:(TiAnimation*)pendingAnimation
{
    if (pendingAnimation.cancelRunningAnimations) {
        [self cancelAllAnimations:nil];
    }
    else {
        pthread_rwlock_rdlock(&runningLock);
        BOOL needsCancelling = [_runningAnimations containsObject:pendingAnimation];
        if (needsCancelling) {
            [_runningAnimations removeObject:pendingAnimation];
            [pendingAnimation cancelMyselfBeforeStarting];
        }
        pthread_rwlock_unlock(&runningLock);
    }
    
    [self addRunningAnimation:pendingAnimation];
    
    [self handleAnimation:pendingAnimation];
}

-(void)playAnimation:(HLSAnimation*)animation withRepeatCount:(NSUInteger)repeatCount afterDelay:(double)delay
{
    [animation playWithRepeatCount:repeatCount afterDelay:delay];
}

-(BOOL)handlesAutoReverse
{
    return FALSE;
}

-(void)handleAnimation:(TiAnimation*)animation
{
    [self handleAnimation:animation witDelegate:self];
}


-(void)applyPendingFromProps
{
    pthread_rwlock_rdlock(&pendingLock);
    if ([_pendingAnimations count] == 0) {
        pthread_rwlock_unlock(&pendingLock);
        return;
    }
    _goingThroughPending = YES;
    for (TiAnimation* anim in _pendingAnimations) {
        [anim applyFromOptions:self];
    }
    _goingThroughPending = NO;
    pthread_rwlock_unlock(&pendingLock);
}


-(Class)animationClassType
{
    return nil;
}

-(void)handleAnimation:(TiAnimation*)animation witDelegate:(id)delegate
{
    animation.animatedProxy = self;
    animation.delegate = delegate;
    
    HLSAnimation* hlsAnimation  = [self animationForAnimation:animation];
    if (animation.autoreverse && ![self handlesAutoReverse]) {
        hlsAnimation = [hlsAnimation loopAnimation];
    }
    hlsAnimation.delegate = animation;
    hlsAnimation.lockingUI = NO;
    animation.animation = hlsAnimation;
    
    [animation applyFromOptions:self];
    Class animationClassType = [self animationClassType];
    if (animationClassType) {
        NSDictionary* toProps = [animation propertiesForAnimation:self destination:YES reverse:NO];
        [toProps enumerateKeysAndObjectsUsingBlock:^(id  _Nonnull key, id  _Nonnull obj, BOOL * _Nonnull stop) {
            TiProxy* bindedProxy = [self bindingForKey:key];
            if (IS_OF_CLASS(obj, NSDictionary) && IS_OF_CLASS(bindedProxy, TiAnimatableProxy) && ![[(TiAnimatableProxy*)bindedProxy animationClassType] isSubclassOfClass:animationClassType] ) {
                TiAnimation* childAnim = [TiAnimation animationFromArg:obj context:[bindedProxy executionContext] create:NO];
                if (childAnim.duration == 0) {
                    childAnim.duration = animation.duration;
                }
                childAnim.autoreverse = animation.autoreverse;
                childAnim.dontApplyOnFinish = animation.dontApplyOnFinish;
                [(TiAnimatableProxy*)bindedProxy handleAnimation:childAnim];
            }
        }];
    }
    
    [self playAnimation:hlsAnimation withRepeatCount:[animation repeatCount] afterDelay:[animation delay]];
}

-(BOOL)canAnimateWithoutParent
{
    return NO;
}

-(BOOL)readyToAnimate
{
    return NO;
}

-(void)animate:(id)arg
{
    TiAnimation * newAnimation = [TiAnimation animationFromArg:arg context:[self executionContext] create:NO];
    if (newAnimation == nil) return;
    if (!parent && ![self canAnimateWithoutParent]) {
        [newAnimation simulateFinish:self];
        return;
    }
    [self rememberProxy:newAnimation];
    pthread_rwlock_rdlock(&pendingLock);
    [_pendingAnimations addObject:newAnimation];
    pthread_rwlock_unlock(&pendingLock);
    [self handlePendingAnimation];
}

-(void)resetProxyPropertiesForAnimation:(TiAnimation*)animation
{
    NSMutableSet* props = [NSMutableSet setWithArray:(NSArray *)[animation allKeys]];
    id<NSFastEnumeration> keySeq = props;

    for (NSString* key in keySeq) {
        id value = [self valueForKey:key];
        [self setValue:value?value:[NSNull null] forKey:key];
    }
}


-(void)handleStateDiffPropertyForKey:(NSString*)key value:(id)obj currentValues:(NSMutableDictionary*)currentValues newValues:(NSMutableDictionary*)newValues
{
    if ([key isEqualToString:@"duration"]) {
        [newValues setValue:obj forKey:key];
        [currentValues removeObjectForKey:key];
    } else {
        [super handleStateDiffPropertyForKey:key value:obj currentValues:currentValues newValues:newValues];
    }
}

-(void)applyStateProperties:(NSDictionary*)props
{
    if ([props objectForKey:@"duration"]) {
        TiAnimation * newAnimation = [TiAnimation animationFromArg:props context:[self executionContext] create:NO];
        newAnimation.dontApplyOnFinish = YES;
        [self handlePendingAnimation:newAnimation];
        
    } else {
        [super applyStateProperties:props];
    }
}

@end
