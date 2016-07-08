/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 *
 * Special thanks to Steve Tramer for implementing this.
 */
#ifdef USE_TI_NETWORK

#import "TiNetworkBonjourBrowserProxy.h"
#import "TiNetworkBonjourServiceProxy.h"

@implementation TiNetworkBonjourBrowserProxy

@synthesize serviceType, domain;

#pragma mark Public

-(id)init
{
    if (self = [super init]) {
        browser = [[NSNetServiceBrowser alloc] init];
        services = [[NSMutableArray alloc] init];
        
        [browser removeFromRunLoop:[NSRunLoop currentRunLoop] 
                           forMode:NSDefaultRunLoopMode];
        [browser scheduleInRunLoop:[NSRunLoop mainRunLoop] 
                           forMode:NSDefaultRunLoopMode];
        
        [browser setDelegate:self];
        searching = NO;
        error = nil;
        
        serviceType = nil;
        domain = [[NSString alloc] initWithString:@"local."];
    }
    
    return self;
}

-(void)dealloc
{
    [browser release];
    [serviceType release];
    [domain release];
    [services release];
    
    [super dealloc];
}

-(NSString*)apiName
{
    return @"Ti.Network.BonjourBrowser";
}

-(NSString*)description
{
    return [NSString stringWithFormat:@"BonjourServiceBrowser: %@ (%lu)", [services description], (unsigned long)[services retainCount]];
}

-(void)setServiceType:(NSString*)type_
{
    if (serviceType == type_) {
        return;
    }
    
    [serviceType release];
    serviceType = [type_ retain];
}

-(void)setDomain:(NSString*)domain_
{
    if (domain == domain_) {
        return;
    }
    
    [domain release];
    domain = [domain_ retain];
}

-(void)search:(id)unused
{
    if (serviceType == nil) {
        [self throwException:@"Service type not set"
                   subreason:nil
                    location:CODELOCATION];
    }
    
    RELEASE_TO_NIL(error);
    [browser searchForServicesOfType:serviceType 
                            inDomain:domain];
    
    if (!searching && !error) {
        [searchCondition lock];
        [searchCondition wait];
        [searchCondition unlock];
    }
    
    if (error) {
        [self throwException:[@"Failed to search: " stringByAppendingString:error]
                   subreason:nil
                    location:CODELOCATION];
    }
}

-(void)stopSearch:(id)unused
{
    [browser stop];
    
    if (searching) {
        [searchCondition lock];
        [searchCondition wait];
        [searchCondition unlock];
    }
    
    [services removeAllObjects];
}

-(NSNumber*)isSearching
{
    return [NSNumber numberWithBool:searching];
}

#pragma mark Private

-(void)setError:(NSString*)error_
{
    if (error != error_) {
        [error release];
        error = [error_ retain];
    }
}

#pragma mark Delegate methods

#pragma mark Service management

-(void)fireServiceUpdateEvent
{
	NSDictionary * eventObject = [NSDictionary dictionaryWithObject:[[services copy] autorelease] 
															 forKey:@"services"];
	[self fireEvent:@"updatedservices" withObject:eventObject propagate:NO checkForListener:NO];
}

-(void)netServiceBrowser:(NSNetServiceBrowser*)browser_ didFindService:(NSNetService*)service moreComing:(BOOL)more
{
    if ([self _hasListeners:@"updatedservices"]) {
        [services addObject:[[[TiNetworkBonjourServiceProxy alloc] initWithContext:[self pageContext]
                                                                           service:service
                                                                             local:NO] autorelease]];
        if (!more) {
            [self fireServiceUpdateEvent];
        }
    }
    if ([self _hasListeners:@"discover"]) {
        NSMutableDictionary* data = [NSMutableDictionary dictionaryWithDictionary:@{
                                                                                   @"port'": @(service.port)
                                                                                   }];
        if (service.name) {
            [data setValue:service.name forKey:@"name"];
        }
        if (service.type) {
            [data setValue:service.type forKey:@"type"];
        }
        if (service.domain) {
            [data setValue:service.domain forKey:@"domain"];
        }
        if (service.hostName) {
            [data setValue:service.hostName forKey:@"host"];
        }
        NSLog( @"%@", data );
        [self fireEvent:@"discover" withObject:data propagate:NO checkForListener:NO];
    }
    
}

-(void)netServiceBrowser:(NSNetServiceBrowser*)browser_ didRemoveService:(NSNetService*)service moreComing:(BOOL)more
{
    if ([self _hasListeners:@"updatedservices"]) {
        // Create a temp object to release; this is what -[TiBonjourServiceProxy isEqual:] is for
        [services removeObject:[[[TiNetworkBonjourServiceProxy alloc] initWithContext:[self pageContext]
                                                                       service:service
                                                                         local:NO] autorelease]];
        
        if (!more) {
            [self fireServiceUpdateEvent];
        }
    }
    if ([self _hasListeners:@"lost"]) {
        NSMutableDictionary* data = [NSMutableDictionary dictionaryWithDictionary:@{
                                                                                    @"port'": @(service.port)
                                                                                    }];
        if (service.name) {
            [data setValue:service.name forKey:@"name"];
        }
        if (service.type) {
            [data setValue:service.type forKey:@"type"];
        }
        if (service.domain) {
            [data setValue:service.domain forKey:@"domain"];
        }
        if (service.hostName) {
            [data setValue:service.hostName forKey:@"host"];
        }
        [self fireEvent:@"lost" withObject:data propagate:NO checkForListener:NO];
    }
}

#pragma mark Search management

-(void)netServiceBrowserWillSearch:(NSNetServiceBrowser*)browser_
{
    searching = YES;
    [searchCondition lock];
    [searchCondition signal];
    [searchCondition unlock];
}

-(void)netServiceBrowser:(NSNetServiceBrowser *)browser_ didNotSearch:(NSDictionary *)errorDict
{
    [self setError:[TiNetworkBonjourServiceProxy stringForErrorCode:[[errorDict objectForKey:NSNetServicesErrorCode] intValue]]];
    
    [searchCondition lock];
    [searchCondition signal];
    [searchCondition unlock];
}

-(void)netServiceBrowserDidStopSearch:(NSNetServiceBrowser*)browser_
{
    searching = NO;
    
    [searchCondition lock];
    [searchCondition signal];
    [searchCondition unlock];
}

@end

#endif