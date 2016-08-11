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

#import <arpa/inet.h>

@implementation TiNetworkBonjourBrowserProxy
{
    bool _resolveOnDiscover;
    NSRegularExpression* _nameRegex;
    NSMutableArray* _resolvingServices;
}

@synthesize serviceType, domain;

#pragma mark Public

-(id)init
{
    if (self = [super init]) {
        _resolveOnDiscover = NO;
        _nameRegex = nil;
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
    RELEASE_TO_NIL(_resolvingServices)
    [_nameRegex release];
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


-(void)setResolveOnDiscover:(BOOL)value
{
    _resolveOnDiscover = value;
}

-(void)setNameRegex:(NSString*)reg_
{
    [_nameRegex release];
    NSError *err = NULL;
    _nameRegex = [[NSRegularExpression regularExpressionWithPattern:reg_
                                                                           options:NSRegularExpressionCaseInsensitive
                                                                             error:&err] retain];
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
    NSMutableArray* array = [NSMutableArray array];
    [services enumerateObjectsUsingBlock:^(id  _Nonnull obj, NSUInteger idx, BOOL * _Nonnull stop) {
        [array addObject:[[[TiNetworkBonjourServiceProxy alloc] initWithContext:[self pageContext] service:obj local:NO] autorelease]];
    }];
    [self fireEvent:@"updatedservices" withObject:@{
                                                    @"services":array} propagate:NO checkForListener:NO];
}


-(void)fireEvent:(NSString*)type forService:(NSNetService*)service {
    if ([self _hasListeners:type]) {
        TiNetworkBonjourServiceProxy* proxy = [[[TiNetworkBonjourServiceProxy alloc] initWithContext:[self pageContext] service:service local:NO] autorelease];

        NSMutableDictionary* dict = [NSMutableDictionary dictionary];
        if (service.port > 0) {
            [dict setValue:@(service.port) forKey:@"name"];
        }
        if (service.name) {
            [dict setValue:service.name forKey:@"name"];
        }
        if (service.type) {
            [dict setValue:service.type forKey:@"type"];
        }
        if (service.domain) {
            [dict setValue:service.domain forKey:@"domain"];
        }
        if (service.hostName) {
            [dict setValue:service.hostName forKey:@"host"];
        }
        if (service.addresses) {
            NSMutableArray* addresses = [NSMutableArray array];
            char addressBuffer[INET6_ADDRSTRLEN];
            
            for (NSData *data in service.addresses)
            {
                memset(addressBuffer, 0, INET6_ADDRSTRLEN);
                
                typedef union {
                    struct sockaddr sa;
                    struct sockaddr_in ipv4;
                    struct sockaddr_in6 ipv6;
                } ip_socket_address;
                
                ip_socket_address *socketAddress = (ip_socket_address *)[data bytes];
                
                if (socketAddress && (socketAddress->sa.sa_family == AF_INET || socketAddress->sa.sa_family == AF_INET6))
                {
                    const char *addressStr = inet_ntop(
                                                       socketAddress->sa.sa_family,
                                                       (socketAddress->sa.sa_family == AF_INET ? (void *)&(socketAddress->ipv4.sin_addr) : (void *)&(socketAddress->ipv6.sin6_addr)),
                                                       addressBuffer,
                                                       sizeof(addressBuffer));
                    
                    int port = ntohs(socketAddress->sa.sa_family == AF_INET ? socketAddress->ipv4.sin_port : socketAddress->ipv6.sin6_port);
                    
                    if (addressStr && port)
                    {
                        //                        NSLog(@"Found service at %s:%d", addressStr, port);
                        [addresses addObject:[NSString stringWithFormat:@"%s:%d", addressStr, port]];
                    }
                }
                NSOrderedSet *orderedSet = [NSOrderedSet orderedSetWithArray:addresses];
                [dict setValue:[orderedSet array] forKey:@"addresses"];
            }
        }
        [dict setValue:proxy forKey:@"service"];
        [self fireEvent:type withObject:dict propagate:NO checkForListener:NO];
    }
}

-(void)netServiceBrowser:(NSNetServiceBrowser*)browser_ didFindService:(NSNetService*)service moreComing:(BOOL)more
{
    if ([service.type containsString:serviceType] &&
        (!_nameRegex || [_nameRegex numberOfMatchesInString:service.name options:0 range:NSMakeRange(0, [service.name length])])) {
        if (_resolveOnDiscover) {
            if (!_resolvingServices) {
                _resolvingServices = [[NSMutableArray alloc] init];
            }
            [_resolvingServices addObject:service];
            service.delegate = self;
            [service resolveWithTimeout:120.0];
            
        } else {
            [self fireEvent:@"discover" forService:service];
        }
    }
    
    if (!more) {
        if ([self _hasListeners:@"updatedservices"]) {
            [services addObject:service];
            [self fireServiceUpdateEvent];
        }
        [self stopSearch:nil];
    }
    
    
}

-(void)netServiceBrowser:(NSNetServiceBrowser*)browser_ didRemoveService:(NSNetService*)service moreComing:(BOOL)more
{
    // Create a temp object to release; this is what -[TiBonjourServiceProxy isEqual:] is for
    [services removeObject:service];
    if ([self _hasListeners:@"updatedservices"]) {
        if (!more) {
            [self fireServiceUpdateEvent];
        }
    }
    [self fireEvent:@"lost" forService:service];

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

- (void)netServiceDidResolveAddress:(NSNetService *)service {
    [self fireEvent:@"resolve" forService:service];
    [_resolvingServices removeObject:service];
    if (_resolvingServices == 0) {
        RELEASE_TO_NIL(_resolvingServices)
    }
}

- (void)netService:(NSNetService *)sender didNotResolve:(NSDictionary<NSString *, NSNumber *> *)errorDict
{
    [_resolvingServices removeObject:sender];
    if (_resolvingServices == 0) {
        RELEASE_TO_NIL(_resolvingServices)
    }
}

- (void)netServiceDidStop:(NSNetService *)sender {
    [_resolvingServices removeObject:sender];
    if (_resolvingServices == 0) {
        RELEASE_TO_NIL(_resolvingServices)
    }
}

@end

#endif
