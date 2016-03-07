
#import "NSDictionary+Merge.h"

@implementation NSMutableDictionary (Merge)


- (void) mergeWithDictionary: (NSDictionary *) dict2 {
    [dict2 enumerateKeysAndObjectsUsingBlock: ^(id key, id obj, BOOL *stop) {
        NSMutableDictionary* current = [self objectForKey:key];
        if ([current isKindOfClass:[NSDictionary class]]) {
            [current mergeWithDictionary:obj];
        } else {
            if ([obj isKindOfClass:[NSDictionary class]]) {
                [self setObject: [NSMutableDictionary dictionaryWithDictionary:obj] forKey: key];
            } else if (obj == [NSNull null]) {
                [self removeObjectForKey:key];
            } else {
                [self setObject: obj forKey: key];
            }
        }
    }];
}
@end
    
@implementation NSDictionary (Merge)

+ (NSDictionary *) dictionaryByMerging: (NSDictionary *) dict1 with: (NSDictionary *) dict2 {
    return [[self class] dictionaryByMerging:dict1 with:dict2 force:NO];
}

+ (NSDictionary *) dictionaryByMerging: (NSDictionary *) dict1 with: (NSDictionary *) dict2 force:(BOOL)force {
    NSMutableDictionary * result = [NSMutableDictionary dictionaryWithDictionary:dict1];
    
    [dict2 enumerateKeysAndObjectsUsingBlock: ^(id key, id obj, BOOL *stop) {
        NSDictionary* current = [dict1 objectForKey:key];
        if (force || !current) {
            if ([obj isKindOfClass:[NSDictionary class]]) {
                NSDictionary * newVal = current?[current dictionaryByMergingWith:(NSDictionary *) obj force:force]:obj;
                [result setObject: newVal forKey: key];
            } else {
                [result setObject: obj forKey: key];
            }
        }
    }];
    
    return (NSDictionary *) [[result mutableCopy] autorelease];
}

- (NSDictionary *) dictionaryByMergingWith: (NSDictionary *) dict {
    return [self dictionaryByMergingWith:dict force:false];
}

- (NSDictionary *) dictionaryByMergingWith: (NSDictionary *) dict force:(BOOL)force {
    return [[self class] dictionaryByMerging: self with: dict force:force];
}

@end
