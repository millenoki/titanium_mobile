#import "TiUIView.h"

#import "INSPullToRefreshBackgroundView.h"

@interface TiScrollingView : TiUIView<INSPullToRefreshBackgroundViewDelegate>
{
    BOOL scrollingEnabled;
}
-(void)scrollToBottom;
- (void)zoomToPoint:(CGPoint)zoomPoint withScale: (CGFloat)scale animated: (BOOL)animated;

- (NSMutableDictionary *) eventObjectForScrollView: (UIScrollView *) scrollView;
// For now, this is fired on `scrollstart` and `scrollend`
- (void)fireScrollEvent:(NSString*)eventName forScrollView:(UIScrollView*)scrollView withAdditionalArgs:(NSDictionary*)args;
- (void)fireScrollEvent:(UIScrollView *)scrollView;
- (void)scrollViewDidZoom:(UIScrollView *)scrollView_;
- (void)scrollViewDidEndZooming:(UIScrollView *)scrollView_ withView:(UIView *)view atScale:(CGFloat)scale;
- (void)scrollViewDidScroll:(UIScrollView *)scrollView;
- (void)scrollViewWillBeginDragging:(UIScrollView *)scrollView;
- (void)scrollViewDidEndDragging:(UIScrollView *)scrollView willDecelerate:(BOOL)decelerate;
- (void)scrollViewDidEndDecelerating:(UIScrollView *)scrollView;
- (BOOL)scrollViewShouldScrollToTop:(UIScrollView *)scrollView;
- (void)scrollViewDidScrollToTop:(UIScrollView *)scrollView;
-(void)setContentOffset_:(id)value withObject:(id)property;
-(void)setZoomScale_:(id)value withObject:(id)property;

@end
