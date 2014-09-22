
#import "UIImage+UserInfo.h"
#import <objc/runtime.h>

NSString * const kInfoKey = @"kInfo";
@implementation UIImage (UserInfo)

- (void)setInfo:(NSDictionary *)info
{
    objc_setAssociatedObject(self, kInfoKey, info, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (NSDictionary*)info
{
    return objc_getAssociatedObject(self, kInfoKey);
}
@end
