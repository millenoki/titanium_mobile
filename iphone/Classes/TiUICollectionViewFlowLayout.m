/**
 * Akylas
 * Copyright (c) 2009-2010 by Akylas. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#import "TiUICollectionViewFlowLayout.h"

/// The default resistance factor that determines the bounce of the collection. Default is 900.0f.
#define kScrollResistanceFactorDefault 900.0f;

@interface TiUICollectionViewFlowLayout ()

/// The dynamic animator used to animate the collection's bounce
@property (nonatomic, strong, readwrite) UIDynamicAnimator *dynamicAnimator;

// Needed for tiling
@property (nonatomic, strong) NSMutableSet *visibleIndexPathsSet;
@property (nonatomic, strong) NSMutableSet *visibleHeaderAndFooterSet;
@property (nonatomic, assign) CGFloat latestDelta;
@property (nonatomic, assign) UIInterfaceOrientation interfaceOrientation;

/// The scrolling resistance factor determines how much bounce / resistance the collection has. A higher number is less bouncy, a lower number is more bouncy. The default is 900.0f.
@property (nonatomic, assign) CGFloat scrollResistanceFactor;

@end

@implementation TiUICollectionViewFlowLayout
@synthesize stickyHeaders;

- (instancetype)init {
    self = [super init];
    if (self){
        [self setup];
    }
    return self;
}

- (instancetype)initWithCoder:(NSCoder *)aDecoder {
    self = [super initWithCoder:aDecoder];
    if (self){
        [self setup];
    }
    return self;
}

- (void)setup {
    stickyHeaders = YES;
    _dynamicAnimator = [[UIDynamicAnimator alloc] initWithCollectionViewLayout:self];
    self.visibleIndexPathsSet = [NSMutableSet set];
    self.visibleHeaderAndFooterSet = [NSMutableSet set];
}

-(void)dealloc {
    self.dynamicAnimator = nil;
    self.visibleIndexPathsSet = nil;
    [super dealloc];
}

- (void)prepareLayout {
    [super prepareLayout];
    
    if ([[UIApplication sharedApplication] statusBarOrientation] != self.interfaceOrientation) {
        [self.dynamicAnimator removeAllBehaviors];
        self.visibleIndexPathsSet = [NSMutableSet set];
    }
    
    self.interfaceOrientation = [[UIApplication sharedApplication] statusBarOrientation];
    
    // Need to overflow our actual visible rect slightly to avoid flickering.
    CGRect visibleRect = CGRectInset((CGRect){.origin = self.collectionView.bounds.origin, .size = self.collectionView.frame.size}, -100, -100);
    
    NSArray *itemsInVisibleRectArray = [super layoutAttributesForElementsInRect:visibleRect];
    
    NSSet *itemsIndexPathsInVisibleRectSet = [NSSet setWithArray:[itemsInVisibleRectArray valueForKey:@"indexPath"]];
    
    // Step 1: Remove any behaviours that are no longer visible.
    NSArray *noLongerVisibleBehaviours = [self.dynamicAnimator.behaviors filteredArrayUsingPredicate:[NSPredicate predicateWithBlock:^BOOL(UIAttachmentBehavior *behaviour, NSDictionary *bindings) {
        return [itemsIndexPathsInVisibleRectSet containsObject:[[[behaviour items] firstObject] indexPath]] == NO;
    }]];
    
    [noLongerVisibleBehaviours enumerateObjectsUsingBlock:^(id obj, NSUInteger index, BOOL *stop) {
        [self.dynamicAnimator removeBehavior:obj];
        [self.visibleIndexPathsSet removeObject:[[[obj items] firstObject] indexPath]];
        [self.visibleHeaderAndFooterSet removeObject:[[[obj items] firstObject] indexPath]];
    }];
    
    // Step 2: Add any newly visible behaviours.
    // A "newly visible" item is one that is in the itemsInVisibleRect(Set|Array) but not in the visibleIndexPathsSet
    NSArray *newlyVisibleItems = [itemsInVisibleRectArray filteredArrayUsingPredicate:[NSPredicate predicateWithBlock:^BOOL(UICollectionViewLayoutAttributes *item, NSDictionary *bindings) {
        return (item.representedElementCategory == UICollectionElementCategoryCell ?
                [self.visibleIndexPathsSet containsObject:item.indexPath] : [self.visibleHeaderAndFooterSet containsObject:item.indexPath]) == NO;
    }]];
    
    CGPoint touchLocation = [self.collectionView.panGestureRecognizer locationInView:self.collectionView];
    
    [newlyVisibleItems enumerateObjectsUsingBlock:^(UICollectionViewLayoutAttributes *item, NSUInteger idx, BOOL *stop) {
        CGPoint center = item.center;
        UIAttachmentBehavior *springBehaviour = [[UIAttachmentBehavior alloc] initWithItem:item attachedToAnchor:center];
        
        springBehaviour.length = 1.0f;
        springBehaviour.damping = 0.8f;
        springBehaviour.frequency = 1.0f;
        
        // If our touchLocation is not (0,0), we'll need to adjust our item's center "in flight"
        if (!CGPointEqualToPoint(CGPointZero, touchLocation)) {
            if (self.scrollDirection == UICollectionViewScrollDirectionVertical) {
                CGFloat distanceFromTouch = fabsf(touchLocation.y - springBehaviour.anchorPoint.y);
                
                CGFloat scrollResistance;
                if (self.scrollResistanceFactor) scrollResistance = distanceFromTouch / self.scrollResistanceFactor;
                else scrollResistance = distanceFromTouch / kScrollResistanceFactorDefault;
                
                if (self.latestDelta < 0) center.y += MAX(self.latestDelta, self.latestDelta*scrollResistance);
                else center.y += MIN(self.latestDelta, self.latestDelta*scrollResistance);
                
                item.center = center;
                
            } else {
                CGFloat distanceFromTouch = fabsf(touchLocation.x - springBehaviour.anchorPoint.x);
                
                CGFloat scrollResistance;
                if (self.scrollResistanceFactor) scrollResistance = distanceFromTouch / self.scrollResistanceFactor;
                else scrollResistance = distanceFromTouch / kScrollResistanceFactorDefault;
                
                if (self.latestDelta < 0) center.x += MAX(self.latestDelta, self.latestDelta*scrollResistance);
                else center.x += MIN(self.latestDelta, self.latestDelta*scrollResistance);
                
                item.center = center;
            }
        }
        
        [self.dynamicAnimator addBehavior:springBehaviour];
        if(item.representedElementCategory == UICollectionElementCategoryCell)
        {
            [self.visibleIndexPathsSet addObject:item.indexPath];
        }
        else
        {
            [self.visibleHeaderAndFooterSet addObject:item.indexPath];
        }
        [springBehaviour release];
    }];
}

- (NSArray *) layoutAttributesForElementsInRect:(CGRect)rect {
    NSMutableArray *answer = [[self.dynamicAnimator itemsInRect:rect] mutableCopy];

    if (!stickyHeaders) return [answer autorelease];
    UICollectionView * const cv = self.collectionView;
    CGPoint const contentOffset = cv.contentOffset;
    
    NSMutableIndexSet *missingSections = [NSMutableIndexSet indexSet];
    for (UICollectionViewLayoutAttributes *layoutAttributes in answer) {
        if (layoutAttributes.representedElementCategory == UICollectionElementCategoryCell) {
            [missingSections addIndex:layoutAttributes.indexPath.section];
        }
    }
    for (UICollectionViewLayoutAttributes *layoutAttributes in answer) {
        if ([layoutAttributes.representedElementKind isEqualToString:UICollectionElementKindSectionHeader]) {
            [missingSections removeIndex:layoutAttributes.indexPath.section];
        }
    }
    
    [missingSections enumerateIndexesUsingBlock:^(NSUInteger idx, BOOL *stop) {
        NSIndexPath *indexPath = [NSIndexPath indexPathForItem:0 inSection:idx];
        UICollectionViewLayoutAttributes *layoutAttributes = [self layoutAttributesForSupplementaryViewOfKind:UICollectionElementKindSectionHeader atIndexPath:indexPath];
        [answer addObject:layoutAttributes];
    }];
    
    for (UICollectionViewLayoutAttributes *layoutAttributes in answer) {
        if ([layoutAttributes.representedElementKind isEqualToString:UICollectionElementKindSectionHeader]) {
            NSInteger section = layoutAttributes.indexPath.section;
            NSInteger numberOfItemsInSection = [cv numberOfItemsInSection:section];
            
            NSIndexPath *firstCellIndexPath = [NSIndexPath indexPathForItem:0 inSection:section];
            NSIndexPath *lastCellIndexPath = [NSIndexPath indexPathForItem:MAX(0, (numberOfItemsInSection - 1)) inSection:section];
            
            UICollectionViewLayoutAttributes *firstCellAttrs = [self layoutAttributesForItemAtIndexPath:firstCellIndexPath];
            UICollectionViewLayoutAttributes *lastCellAttrs = [self layoutAttributesForItemAtIndexPath:lastCellIndexPath];
            
            if (self.scrollDirection == UICollectionViewScrollDirectionVertical) {
                CGFloat headerHeight = CGRectGetHeight(layoutAttributes.frame);
                CGPoint origin = layoutAttributes.frame.origin;
                origin.y = MIN(
                               MAX(contentOffset.y, (CGRectGetMinY(firstCellAttrs.frame) - headerHeight)),
                               (CGRectGetMaxY(lastCellAttrs.frame) - headerHeight)
                               );
                
                layoutAttributes.zIndex = 1024;
                layoutAttributes.frame = (CGRect){
                    .origin = origin,
                    .size = layoutAttributes.frame.size
                };
            } else {
                CGFloat headerWidth = CGRectGetWidth(layoutAttributes.frame);
                CGPoint origin = layoutAttributes.frame.origin;
                origin.x = MIN(
                               MAX(contentOffset.x, (CGRectGetMinX(firstCellAttrs.frame) - headerWidth)),
                               (CGRectGetMaxX(lastCellAttrs.frame) - headerWidth)
                               );
                
                layoutAttributes.zIndex = 1024;
                layoutAttributes.frame = (CGRect){
                    .origin = origin,
                    .size = layoutAttributes.frame.size
                };
            }
        }
    }
    return [answer autorelease];
}

- (BOOL)shouldInvalidateLayoutForBoundsChange:(CGRect)newBounds {
    if (stickyHeaders) return YES;
    UIScrollView *scrollView = self.collectionView;
    
    CGFloat delta;
    if (self.scrollDirection == UICollectionViewScrollDirectionVertical) delta = newBounds.origin.y - scrollView.bounds.origin.y;
    else delta = newBounds.origin.x - scrollView.bounds.origin.x;
    
    self.latestDelta = delta;
    
    CGPoint touchLocation = [self.collectionView.panGestureRecognizer locationInView:self.collectionView];
    
    [self.dynamicAnimator.behaviors enumerateObjectsUsingBlock:^(UIAttachmentBehavior *springBehaviour, NSUInteger idx, BOOL *stop) {
        if (self.scrollDirection == UICollectionViewScrollDirectionVertical) {
            CGFloat distanceFromTouch = fabsf(touchLocation.y - springBehaviour.anchorPoint.y);
            
            CGFloat scrollResistance;
            if (self.scrollResistanceFactor) scrollResistance = distanceFromTouch / self.scrollResistanceFactor;
            else scrollResistance = distanceFromTouch / kScrollResistanceFactorDefault;
            
            UICollectionViewLayoutAttributes *item = [springBehaviour.items firstObject];
            CGPoint center = item.center;
            if (delta < 0) center.y += MAX(delta, delta*scrollResistance);
            else center.y += MIN(delta, delta*scrollResistance);
            
            item.center = center;
            
            [self.dynamicAnimator updateItemUsingCurrentState:item];
        } else {
            CGFloat distanceFromTouch = fabsf(touchLocation.x - springBehaviour.anchorPoint.x);
            
            CGFloat scrollResistance;
            if (self.scrollResistanceFactor) scrollResistance = distanceFromTouch / self.scrollResistanceFactor;
            else scrollResistance = distanceFromTouch / kScrollResistanceFactorDefault;
            
            UICollectionViewLayoutAttributes *item = [springBehaviour.items firstObject];
            CGPoint center = item.center;
            if (delta < 0) center.x += MAX(delta, delta*scrollResistance);
            else center.x += MIN(delta, delta*scrollResistance);
            
            item.center = center;
            
            [self.dynamicAnimator updateItemUsingCurrentState:item];
        }
    }];
    
    return NO;
}

- (void)prepareForCollectionViewUpdates:(NSArray *)updateItems {
    [super prepareForCollectionViewUpdates:updateItems];
    
    [updateItems enumerateObjectsUsingBlock:^(UICollectionViewUpdateItem *updateItem, NSUInteger idx, BOOL *stop) {
        if (updateItem.updateAction == UICollectionUpdateActionInsert) {
            if([self.dynamicAnimator layoutAttributesForCellAtIndexPath:updateItem.indexPathAfterUpdate])
            {
                return;
            }
            
            UICollectionViewLayoutAttributes *attributes = [UICollectionViewLayoutAttributes layoutAttributesForCellWithIndexPath:updateItem.indexPathAfterUpdate];
            
            //attributes.frame = CGRectMake(10, updateItem.indexPathAfterUpdate.item * 310, 300, 44); // or some other initial frame
            
            UIAttachmentBehavior *springBehaviour = [[UIAttachmentBehavior alloc] initWithItem:attributes attachedToAnchor:attributes.center];
            
            springBehaviour.length = 1.0f;
            springBehaviour.damping = 0.8f;
            springBehaviour.frequency = 1.0f;
            [self.dynamicAnimator addBehavior:springBehaviour];
            [springBehaviour release];
        }
    }];
}


@end
