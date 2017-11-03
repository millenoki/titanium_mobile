/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#import "TiConsole.h"

@implementation TiConsole
{
    NSMutableDictionary* _times;
}


- (void)dealloc
{
    RELEASE_TO_NIL(_times);
    [super dealloc];
}


- (void)log:(NSArray *)args withSeverity:(NSString *)severity
{
  __block NSMutableString *message = [NSMutableString string];
  [args enumerateObjectsUsingBlock:^(id _Nonnull obj, NSUInteger idx, BOOL *_Nonnull stop) {
    [message appendString:@" "];
    [message appendString:[TiUtils stringifyObject:obj]];
  }];
  [self logMessage:@[ message ] severity:severity];
}

- (void)log:(NSArray *)args
{
  [self log:args withSeverity:@"info"];
}

- (void)error:(NSArray *)args
{
  [self log:args withSeverity:@"error"];
}

- (void)warn:(NSArray *)args
{
  [self log:args withSeverity:@"warn"];
}

- (void)info:(NSArray *)args
{
  [self log:args withSeverity:@"info"];
}

- (void)debug:(NSArray *)args
{
  [self log:args withSeverity:@"debug"];
}


- (void)time:(id)args
{
    ENSURE_SINGLE_ARG(args, NSString)
    if (!_times) {
        _times = [[NSMutableDictionary alloc] init];
    }
    [_times setObject:@([[NSDate date] timeIntervalSince1970]*1000) forKey:args];
}

- (void)timeEnd:(id)args
{
    ENSURE_SINGLE_ARG(args, NSString)
    NSNumber* time = [_times objectForKey:args];
    if (!time) {
        [self throwException:[NSString stringWithFormat:@"No such label: %@", args] subreason:nil location:CODELOCATION];
    }
    long duration = [[NSDate date] timeIntervalSince1970]*1000 - time.integerValue;
    [self log:@[[NSString stringWithFormat:@"%@: %lims", args, (long)duration]] withSeverity:@"info"];
}
@end
