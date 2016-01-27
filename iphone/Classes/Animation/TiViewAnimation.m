#import "TiViewAnimation+Friend.h"
#import "TiViewAnimationStep.h"

#import "TiViewProxy.h"
#import "TiPoint.h"
#import "TiColor.h"

/**
 * Please read the remarks at the top of HLSLayerAnimation.m
 */
@interface TiViewAnimation ()

@property (nonatomic, retain) TiViewProxy* tiViewProxy;

@property(nonatomic,retain,readwrite) NSNumber	*zIndex;
@property(nonatomic,retain,readwrite) TiPoint	*center;
@property(nonatomic,retain,readwrite) TiColor	*color;
@property(nonatomic,retain,readwrite) TiColor	*backgroundColor;
@property(nonatomic,retain,readwrite) NSNumber	*opacity;
@property(nonatomic,retain,readwrite) NSNumber	*opaque;
@property(nonatomic,retain,readwrite) NSNumber	*visible;
@property(nonatomic,retain,readwrite) TiProxy	*transform;


- (TiViewProxy*)viewProxy;

@end

@implementation TiViewAnimation

#pragma mark Object creation and destruction

- (id)init
{
    if ((self = [super init])) {
    }
    return self;
}

-(void)dealloc
{
	RELEASE_TO_NIL(m_tiViewProxy);
    [super dealloc];
}

#pragma mark Private API


-(void)applyOnView:(UIView*)_view forStep:(TiViewAnimationStep*) step
{
    //that could be the future but for now it doesnt work because
    //applyProperties will set the actual object props which we dont want
    
    [m_tiViewProxy setRunningAnimationRecursive:step];
    [m_animationProxy applyToOptionsForAnimation:self];
    [m_tiViewProxy setRunningAnimationRecursive:nil];

}

#pragma mark Accessors and mutators

@synthesize tiViewProxy = m_tiViewProxy;

- (TiViewProxy*)viewProxy
{
    return m_tiViewProxy;
}

#pragma mark Reverse animation

- (id)reverseObjectAnimation
{
    // See remarks at the beginning
    TiViewAnimation *reverseViewAnimation = [super reverseObjectAnimation];
    reverseViewAnimation.tiViewProxy = self.tiViewProxy;
    return reverseViewAnimation;
}

#pragma mark NSCopying protocol implementation

- (id)copyWithZone:(NSZone *)zone
{
    TiViewAnimation *viewAnimationCopy = [super copyWithZone:zone];
    viewAnimationCopy.tiViewProxy = self.tiViewProxy;
    return viewAnimationCopy;
}

@end
