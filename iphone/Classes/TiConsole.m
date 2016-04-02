/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#import "TiConsole.h"


@implementation TiConsole

-(void)log:(NSArray*)args withSeverity:(NSString*)severity
{
//    __block NSMutableString* message = [NSMutableString string];
//    [args enumerateObjectsUsingBlock:^(id  _Nonnull obj, NSUInteger idx, BOOL * _Nonnull stop) {
//        [message appendString:@" "];
//        if (IS_OF_CLASS(obj, NSDictionary)) {
//            [message appendString:[TiUtils jsonStringify:obj]];
//        } else {
//            [message appendString:[obj description]];
//        }
//    }];
    [self logMessage:args severity:severity];
}

-(void)log:(NSArray*)args
{
    [self log:args withSeverity:@"info"];
}

-(void)error:(NSArray*)args
{
    [self log:args withSeverity:@"error"];
}

-(void)warn:(NSArray*)args
{
    [self log:args withSeverity:@"warn"];
}

-(void)info:(NSArray*)args
{
    [self log:args withSeverity:@"info"];
}

-(void)debug:(NSArray*)args
{
    [self log:args withSeverity:@"debug"];
}
@end
