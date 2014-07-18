//
//  EsOyatsuAvcActivityViewControllerProxy.m
//  ActivityViewController
//
//  Created by Alberto Gonzalez on 9/18/13.
//
//

#import "TiUIiOSActivityViewProxy.h"
#import "ActivityProxy.h"
#import "TiUIiOSActivityProxy.h"

#import "TiApp.h"
#import "TiUtils.h"
#import "TiFile.h"

@implementation TiUIiOSActivityViewProxy
{
    UIActivityViewController* _controller;
    NSMutableArray* _excluded;
    NSMutableArray* _items;
    NSMutableArray* _activities;
    KrollCallback* _itemForActivityType;
}
@synthesize excluded = _excluded, items = _items, activities = _activities, itemForActivityType = _itemForActivityType;

-(void)cleanup
{
//    TiThreadPerformOnMainThread(^{
        [self detach];
//    }, YES);
    [self detachItems];
    RELEASE_TO_NIL(_excluded);
    RELEASE_TO_NIL(_activities);
    RELEASE_TO_NIL(_itemForActivityType);
}

-(void)detach {
    if (_controller) {
        [_controller removeFromParentViewController];
        RELEASE_TO_NIL(_controller);
    }
}

-(void)detachItems {
    if (_items) {
        for(UIActivity* activity in _items) {
            if ([activity isKindOfClass:[TiActivity class]]) {
                [self forgetProxy:[((TiActivity*)activity) proxy]];
            }
        }
        RELEASE_TO_NIL(_items)
    }
}


-(void)dealloc
{
    [self cleanup];
    [super dealloc];
}


-(UIActivityViewController*)controller
{
    if (_controller == nil) {
        if (_itemForActivityType) {
            NSMutableArray* realItems = [[NSMutableArray alloc] initWithObjects:self, nil];
            [realItems addObjectsFromArray:_items];
            _controller = [[UIActivityViewController alloc] initWithActivityItems:realItems applicationActivities:_activities];
        }
        else {
            _controller = [[UIActivityViewController alloc] initWithActivityItems:_items applicationActivities:_activities];
        }
        
        if (_excluded != nil) {
            [_controller setExcludedActivityTypes:_excluded];
        }
        _controller.completionHandler = ^(NSString *act, BOOL done) {
            NSMutableDictionary *event = [[NSMutableDictionary alloc] initWithObjectsAndKeys:NUMBOOL(done), @"success", nil];
            if (act != nil) {
                [event setValue:act forKey:@"activity"];
            }
            [self fireEvent:@"completed" withObject:event];
            [event release];
            _controller.completionHandler = nil;
            [self forgetSelf];
            [self detach];
        };
    }
    return _controller;
}

-(void)setItemForActivityType:(KrollCallback *)callback
{
    RELEASE_TO_NIL(_itemForActivityType);
    [self detach];
    _itemForActivityType = [callback retain];
}

-(void)setExcluded:(id)theExcluded
{
    ENSURE_TYPE_OR_NIL(theExcluded, NSArray)
    RELEASE_TO_NIL(_excluded);
    if (theExcluded) {
        _excluded = [[NSMutableArray alloc] initWithArray:theExcluded];
    }
    if (_controller) {
        [_controller setExcludedActivityTypes:_excluded];
    }
}

-(void)setActivities:(id)theAtivities
{
    ENSURE_TYPE_OR_NIL(theAtivities, NSArray)
    [self detach];
    RELEASE_TO_NIL(_activities);
    if (theAtivities) {
        _activities = [[NSMutableArray alloc] initWithCapacity:[theAtivities count]];
        id<TiEvaluator> context = [self executionContext];
        for (id theactivity in theAtivities) {
            TiUIiOSActivityProxy* actProxy = [TiUIiOSActivityProxy activityFromArg:theactivity context:context];
            [self rememberProxy:actProxy];
            [_activities addObject:[actProxy asActivity]];
        }
    }
}

-(void)setItems:(id)theItems
{
    ENSURE_TYPE_OR_NIL(theItems, NSArray)
    [self detach];
    [self detachItems];
    if (theItems) {
        _items = [[NSMutableArray alloc] initWithCapacity:[theItems count]];
        id<TiEvaluator> context = [self executionContext];
        for (id obj in theItems) {
            if (obj == nil) continue;
            UIImage* img = [TiUtils image:obj proxy:self];
            if (img != nil) {
                [_items addObject:img];
            } else if ([obj isKindOfClass:[NSString class]]) {
                NSURL *url = [NSURL URLWithString:obj];
                if (url != nil && ([url.scheme isEqualToString:@"http"] || [url.scheme isEqualToString:@"https"])) {
                    [_items addObject:url];
                } else {
                    [_items addObject:obj];
                }
            } else if ([obj isKindOfClass:[TiFile class]]) {
                TiFile* file = obj;
                NSURL *url = [NSURL fileURLWithPath:file.path];
                [_items addObject:url];
            } else {
                [_items addObject:obj];
            }
        }
    }
}

-(void) show:(id)args
{
    [self rememberSelf];
 	ENSURE_UI_THREAD_1_ARG(args)
	[[TiApp app] showModalController:[self controller] animated:YES];
}

#pragma mark - UIActivityItemSource Protocol

- (id)activityViewController:(UIActivityViewController *)activityViewController itemForActivityType:(NSString *)activityType {
    if (_itemForActivityType) {
        id result = [_itemForActivityType call:@[activityType, _items] thisObject:self];
        return result;
    }
    return _items;
}

- (id)activityViewControllerPlaceholderItem:(UIActivityViewController *)activityViewController {
    return [NSDictionary dictionary];
}

@end
