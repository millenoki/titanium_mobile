/**
 * Akylas
 * Copyright (c) 2009-2010 by Akylas. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#import "TiUICollectionViewFlowLayout.h"


@implementation TiUICollectionViewFlowLayout

- (BOOL) shouldInvalidateLayoutForBoundsChange:(CGRect)newBounds {
    return YES;
}

- (NSArray *) layoutAttributesForElementsInRect:(CGRect)rect {
    NSMutableArray *attributes = [[super layoutAttributesForElementsInRect:rect] mutableCopy];
    NSMutableArray *visibleSectionsWithoutHeader = [NSMutableArray array];
    for (UICollectionViewLayoutAttributes *itemAttributes in attributes) {
        if (![visibleSectionsWithoutHeader containsObject:[NSNumber numberWithInteger:itemAttributes.indexPath.section]]) {
            [visibleSectionsWithoutHeader addObject:[NSNumber numberWithInteger:itemAttributes.indexPath.section]];
        }
        if (itemAttributes.representedElementKind==UICollectionElementKindSectionHeader) {
            NSUInteger indexOfSectionObject=[visibleSectionsWithoutHeader indexOfObject:[NSNumber numberWithInteger:itemAttributes.indexPath.section]];
            if (indexOfSectionObject!=NSNotFound) {
                [visibleSectionsWithoutHeader removeObjectAtIndex:indexOfSectionObject];
            }
        }
    }
    for (NSNumber *sectionNumber in visibleSectionsWithoutHeader) {
        if ([self shouldStickHeaderToTopInSection:[sectionNumber integerValue]]) {
            UICollectionViewLayoutAttributes *headerAttributes=[self layoutAttributesForSupplementaryViewOfKind:UICollectionElementKindSectionHeader atIndexPath:[NSIndexPath indexPathForItem:0 inSection:[sectionNumber integerValue]]];
            if (headerAttributes.frame.size.width>0 && headerAttributes.frame.size.height>0) {
                [attributes addObject:headerAttributes];
            }
        }
    }
    for (UICollectionViewLayoutAttributes *itemAttributes in attributes) {
        if (itemAttributes.representedElementKind==UICollectionElementKindSectionHeader) {
            UICollectionViewLayoutAttributes *headerAttributes = itemAttributes;
            if ([self shouldStickHeaderToTopInSection:headerAttributes.indexPath.section]) {
                CGPoint contentOffset = self.collectionView.contentOffset;
                CGPoint originInCollectionView=CGPointMake(headerAttributes.frame.origin.x-contentOffset.x, headerAttributes.frame.origin.y-contentOffset.y);
                originInCollectionView.y-=self.collectionView.contentInset.top;
                CGRect frame = headerAttributes.frame;
                if (originInCollectionView.y<0) {
                    frame.origin.y+=(originInCollectionView.y*-1);
                }
                NSInteger numberOfSections = 1;
                if ([self.collectionView.dataSource respondsToSelector:@selector(numberOfSectionsInCollectionView:)]) {
                    numberOfSections = [self.collectionView.dataSource numberOfSectionsInCollectionView:self.collectionView];
                }
                if (numberOfSections>headerAttributes.indexPath.section+1) {
                    UICollectionViewLayoutAttributes *nextHeaderAttributes=[self layoutAttributesForSupplementaryViewOfKind:UICollectionElementKindSectionHeader atIndexPath:[NSIndexPath indexPathForItem:0 inSection:headerAttributes.indexPath.section+1]];
                    CGFloat maxY=nextHeaderAttributes.frame.origin.y;
                    if (CGRectGetMaxY(frame)>=maxY) {
                        frame.origin.y=maxY-frame.size.height;
                    }
                }
                headerAttributes.frame = frame;
            }
            headerAttributes.zIndex = 1024;
        }
    }
    return [attributes autorelease];
}
- (BOOL)shouldStickHeaderToTopInSection:(NSUInteger)section{
    BOOL shouldStickToTop=YES;
    if ([self.collectionView.delegate conformsToProtocol:@protocol(TiUICollectionViewFlowLayoutDelegate)]) {
        id<TiUICollectionViewFlowLayoutDelegate> stickyHeadersDelegate=(id<TiUICollectionViewFlowLayoutDelegate>)self.collectionView.delegate;
        if ([stickyHeadersDelegate respondsToSelector:@selector(shouldStickHeaderToTopInSection:)]) {
            shouldStickToTop=[stickyHeadersDelegate shouldStickHeaderToTopInSection:section];
        }
    }
    return shouldStickToTop;
}


//code to left align
//- (UICollectionViewLayoutAttributes *)layoutAttributesForItemAtIndexPath:(NSIndexPath *)indexPath {
//    UICollectionViewLayoutAttributes* atts =
//    [super layoutAttributesForItemAtIndexPath:indexPath];
//    
//    if (indexPath.item == 0) // degenerate case 1, first item of section
//        return atts;
//    
//    NSIndexPath* ipPrev =
//    [NSIndexPath indexPathForItem:indexPath.item-1 inSection:indexPath.section];
//    
//    CGRect fPrev = [self layoutAttributesForItemAtIndexPath:ipPrev].frame;
//    CGFloat rightPrev = fPrev.origin.x + fPrev.size.width + 10;
//    if (atts.frame.origin.x <= rightPrev) // degenerate case 2, first item of line
//        return atts;
//    
//    CGRect f = atts.frame;
//    f.origin.x = rightPrev;
//    atts.frame = f;
//    return atts;
//}
@end
