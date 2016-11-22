/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2015 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#ifdef USE_TI_NETWORK

#import "TiNetworkHTTPClientProxy.h"
#import "NetworkModule.h"
#import "TiUtils.h"
#import "TiBase.h"
#import "TiApp.h"
#import "Mimetypes.h"

#define TI_HTTP_REQUEST_PROGRESS_INTERVAL 0.03f

extern NSString * const TI_APPLICATION_GUID;

@implementation TiNetworkHTTPClientProxy

- (void)dealloc
{
    RELEASE_TO_NIL(httpRequest);
    RELEASE_TO_NIL(apsConnectionManager);
    RELEASE_TO_NIL(apsConnectionDelegate);
    [super dealloc];
}

-(void)_initWithProperties:(NSDictionary *)properties
{
    _useTiUserAgent = true;
    id arg = [properties valueForKey:@"securityManager"];
    
    if (IS_NULL_OR_NIL(arg) || [arg conformsToProtocol:@protocol(SecurityManagerProtocol)]) {
        if (arg != [NSNull null]) {
            apsConnectionManager = [arg retain];
        }
    } else {
        [self throwException:@"Invalid argument passed to securityManager property" subreason:@"Does not conform to SecurityManagerProtocol" location:CODELOCATION];
    }
    [super _initWithProperties:properties];
}

-(void)ensureClient
{
    if(httpRequest == nil) {
        httpRequest = [[APSHTTPRequest alloc] init];
        [httpRequest setDelegate:self];
        if (_useTiUserAgent) {
            [httpRequest addRequestHeader:@"User-Agent" value:[[TiApp app] userAgent]];
        }
    }
}

-(APSHTTPResponse*)response
{
    [self ensureClient];
    return [httpRequest response];
}

#pragma mark - Public methods

-(id)open:(id)args
{
    ENSURE_ARRAY(args);
    
    if ([httpRequest response] != nil) {
        APSHTTPResponseState curState = [[httpRequest response] readyState];
        if ( (curState == APSHTTPResponseStateUnsent) || (curState == APSHTTPResponseStateDone) ) {
            //Clear out the client + delegate and continue
            RELEASE_TO_NIL(httpRequest);
            RELEASE_TO_NIL(apsConnectionDelegate);
        } else {
            NSLog(@"[ERROR] open can only be called if client is disconnected(0) or done(4). Current state is %d ",curState);
            return nil;
        }
    }
    
    NSString *method = [TiUtils stringValue:[args objectAtIndex:0]];
    NSURL *url = [TiUtils toURL:[args objectAtIndex:1] proxy:self];
    [self ensureClient];
    [httpRequest setMethod: method];
    [httpRequest setUrl:url];
    
    // twitter specifically disallows X-Requested-With so we only add this normal
    // XHR header if not going to twitter. however, other services generally expect
    // this header to indicate an XHR request (such as RoR)
    if ([[url absoluteString] rangeOfString:@"twitter.com"].location==NSNotFound)
    {
        [httpRequest addRequestHeader:@"X-Requested-With" value:@"XMLHttpRequest"];
    }
    if ( (apsConnectionManager != nil) && ([apsConnectionManager willHandleURL:url]) ){
        apsConnectionDelegate = [[apsConnectionManager connectionDelegateForUrl:url] retain];
    }
    
    [httpRequest setConnectionDelegate:apsConnectionDelegate];
    
    if([args count] >= 3) {
        [self replaceValue:[args objectAtIndex:2] forKey:@"async" notification: YES];
    }
    
    [self replaceValue:[url absoluteString] forKey:@"url" notification:NO];
    [self replaceValue:method forKey:@"method" notification:NO];
    return self;
}

-(void)send:(id)args
{
    if (httpRequest == nil) {
        NSLog(@"[ERROR] No request object found. Did you call open?");
        return;
    }
    if ([httpRequest response] != nil) {
        APSHTTPResponseState curState = [[httpRequest response] readyState];
        if (curState != APSHTTPResponseStateUnsent) {
            NSLog(@"[ERROR] send can only be called if client is disconnected(0). Current state is %d ",curState);
            return;
        }
    }
    
    [self rememberSelf];
    
    [httpRequest setShowActivity: [TiUtils boolValue:[self valueForUndefinedKey:@"showActivity"] def:YES]];

    if([self valueForUndefinedKey:@"timeout"]) {
        [httpRequest setTimeout: [TiUtils doubleValue:[self valueForUndefinedKey:@"timeout"] def:15000] / 1000 ];
    }
    if([self valueForUndefinedKey:@"autoRedirect"]) {
        [httpRequest setRedirects:
         [TiUtils boolValue: [self valueForUndefinedKey:@"autoRedirect"] def:YES] ];
    }
    if([self valueForUndefinedKey:@"headers"]) {
        NSDictionary* headers =  [self valueForUndefinedKey:@"headers"];
        if ([headers isKindOfClass:[NSDictionary class]]) {
            [headers enumerateKeysAndObjectsUsingBlock:^(NSString* key, id obj, BOOL *stop) {
                [httpRequest addRequestHeader:key value:[TiUtils stringValue:obj]];
            }];
        }
    }
    if([self valueForUndefinedKey:@"cache"]) {
        [httpRequest setCachePolicy:
         [TiUtils boolValue: [self valueForUndefinedKey:@"cache"] def:YES] ?
             NSURLRequestUseProtocolCachePolicy : NSURLRequestReloadIgnoringLocalAndRemoteCacheData
         ];
    }
    if([self valueForUndefinedKey:@"validatesSecureCertificate"]) {
        [httpRequest setValidatesSecureCertificate:
         [TiUtils boolValue: [self valueForUndefinedKey:@"validatesSecureCertificate"] def:YES] ];
    }
    if([self valueForUndefinedKey:@"username"]) {
        [httpRequest setRequestUsername:
         [TiUtils stringValue: [self valueForUndefinedKey:@"username"]]];
    }
    if([self valueForUndefinedKey:@"password"]) {
        [httpRequest setRequestPassword:
         [TiUtils stringValue: [self valueForUndefinedKey:@"password"]]];
    }
    if([self valueForUndefinedKey:@"domain"]) {
        // TODO: NTLM
    }
    id file = [self valueForUndefinedKey:@"file"];
    if(file) {
        NSString *filePath = nil;
        if([file isKindOfClass:[TiFile class]]) {
            filePath = [(TiFile*)file path];
        }
        if([file isKindOfClass:[NSString class]]) {
            filePath = [TiUtils stringValue:file];
        }
        if(filePath != nil) {
            [httpRequest setFilePath:filePath];
        }
    }
    
    
    APSHTTPPostForm *form = nil;
    if(args != nil) {
        BOOL json = [TiUtils boolValue: [self valueForUndefinedKey:@"json"] def:false];
        ENSURE_ARRAY(args);
        NSInteger dataIndex = 0;
        
        form = [[[APSHTTPPostForm alloc] init] autorelease];
//        NSString* contentType = [[self responseHeaders] objectForKey:@"Content-Type"];
        
        id arg = [args objectAtIndex:0];
        NSInteger timestamp = (NSInteger)[[NSDate date] timeIntervalSince1970];
        if ([arg isKindOfClass:[NSDictionary class]]) {
            NSDictionary *dict = (NSDictionary*)arg;
            if (json) {
                [form setJSONData:dict];
            }
            else {
                for(NSString *key in dict) {
                    id value = [dict objectForKey:key];
                    if ([value isKindOfClass:[TiBlob class]]|| [value isKindOfClass:[TiFile class]]) {
                        TiBlob *blob;
                        NSString *name = nil;
                        NSString *mime = nil;
                        if ([value isKindOfClass:[TiBlob class]]) {
                            blob = (TiBlob*)value;
                            if([blob path] != nil) {
                                name = [[blob path] lastPathComponent];
                            }
                        }else{
                            blob = [(TiFile*)value blob];
                            name = [[(TiFile*)value path] lastPathComponent];
                        }
                        mime = [blob mimeType];
                        NSString* extension = nil;
                        if (mime != nil) {
                            extension = [Mimetypes extensionForMimeType:mime];
                        }
                        if (name == nil) {
                            name = [NSString stringWithFormat:@"%li%li", (long)dataIndex++, (long)timestamp];
                            if (extension != nil) {
                                name = [NSString stringWithFormat:@"%@.%@", name, extension];
                            }
                        }
                        if (mime != nil) {
                            [form addFormData:[blob data] fileName:name fieldName:key contentType:mime];
                        } else {
                            [form addFormData:[blob data] fileName:name fieldName:key];
                        }
                    }
                    else if ([value isKindOfClass:[NSDictionary class]]) {
                        [form setJSONData:value];
                    }
                    else {
                        [form addFormKey:key
                                andValue:[TiUtils stringValue:value]];
                    }
                }
            }
        } else if ([arg isKindOfClass:[TiBlob class]] || [arg isKindOfClass:[TiFile class]]) {
            TiBlob *blob;
            if([arg isKindOfClass:[TiBlob class]]) {
                blob = (TiBlob*)arg;
            } else {
                blob = [(TiFile*)arg blob];
            }
            NSString *mime = [blob mimeType];
            if(mime == nil) {
                mime = @"application/octet-stream";
            }
            [form appendData:[blob data] withContentType:mime];
        } else if (arg  && !IS_OF_CLASS(arg, NSNull)) {
            if (json) {
                [form setJSONData:arg];
            } else {
                [form setStringData:[TiUtils stringValue:arg]];
            }
        }
    }

    if(form != nil) {
        [httpRequest setPostForm:form];
    }
    
    BOOL async = [TiUtils boolValue:[self valueForUndefinedKey:@"async"] def:YES];
    
    NSOperationQueue *operationQueue = [NetworkModule operationQueue];
    
    if(async) {
        [httpRequest setTheQueue:operationQueue];
        [httpRequest send];
    } else {
        [httpRequest setSynchronous:YES];
        [httpRequest send];
        [self forgetSelf];
    }
}

-(void)abort:(id)args
{
    [httpRequest abort];
}

-(void)clearCookies:(id)args
{
    ENSURE_ARG_COUNT(args, 1);
    
    NSString *host = [TiUtils stringValue:[args objectAtIndex:0]];
    
    NSHTTPCookie *cookie;
    NSHTTPCookieStorage *storage = [NSHTTPCookieStorage sharedHTTPCookieStorage];
    NSArray* targetCookies = [storage cookiesForURL:[NSURL URLWithString:host]];
    if ([targetCookies count] > 0) {
        for (cookie in targetCookies) {
            [storage deleteCookie:cookie];
        }
    }
}

-(NSString*)getResponseHeader:(id)args
{
    ENSURE_SINGLE_ARG(args, NSString)
    return [[[self response] headers] valueForKey:args];
}

# pragma mark - Callback functions

-(void)request:(APSHTTPRequest *)request onDataStream:(APSHTTPResponse *)response
{
    if(hasOndatastream) {
        NSTimeInterval currentTime = [[NSDate date] timeIntervalSince1970];
        NSTimeInterval diff = currentTime - _downloadTime;
        if(_downloadTime == 0 || diff > TI_HTTP_REQUEST_PROGRESS_INTERVAL || [response readyState] == APSHTTPResponseStateDone) {
            _downloadTime = 0;
            NSDictionary *eventDict = [NSMutableDictionary dictionary];
            float downloadProgress = [response downloadProgress];
            // return progress as -1 if it is outside the valid range
            if (downloadProgress > 1 || downloadProgress < 0) {
                downloadProgress = -1.0f;
            }
            [eventDict setValue:[NSNumber numberWithFloat: downloadProgress] forKey:@"progress"];
            [self fireCallback:@"ondatastream" withArg:eventDict withSource:self];
        }
        if(_downloadTime == 0) {
            _downloadTime = currentTime;
        }
    }
}

-(void)request:(APSHTTPRequest *)request onSendStream:(APSHTTPResponse *)response
{
    if(hasOnsendstream) {
        NSTimeInterval currentTime = [[NSDate date] timeIntervalSince1970];
        NSTimeInterval diff = currentTime - _uploadTime;
        if(_uploadTime == 0 || diff > TI_HTTP_REQUEST_PROGRESS_INTERVAL || [response readyState] == APSHTTPResponseStateDone) {
            _uploadTime = 0;
            NSDictionary *eventDict = [NSMutableDictionary dictionary];
            [eventDict setValue:[NSNumber numberWithFloat: [response uploadProgress]] forKey:@"progress"];
            [self fireCallback:@"onsendstream" withArg:eventDict withSource:self];
        }
        if(_uploadTime == 0) {
            _uploadTime = currentTime;
        }
    }
}

-(void)request:(APSHTTPRequest *)request onLoad:(APSHTTPResponse *)response
{
    if(![request cancelled]) {
        NSInteger responseCode = [response status];
        /**
         *    Per customer request, successful communications that resulted in an
         *    4xx or 5xx response is treated as an error instead of an onload.
         *    For backwards compatibility, if no error handler is provided, even
         *    an 4xx or 5xx response will fall back onto an onload.
         */
        if (hasOnerror && (responseCode >= 400) && (responseCode <= 599)) {
            NSMutableDictionary * event = [TiUtils dictionaryWithCode:responseCode message:@"HTTP error"];
            [event setObject:@"error" forKey:@"type"];
            [self fireCallback:@"onerror" withArg:event withSource:self withHandler:^(id result){
                [self forgetSelf];
            }];
        } else if(hasOnload) {
            NSMutableDictionary * event = [TiUtils dictionaryWithCode:responseCode message:nil];
            [event setObject:@"load" forKey:@"type"];
            [self fireCallback:@"onload" withArg:event withSource:self withHandler:^(id result){
                [self forgetSelf];
            }];
        } else {
            [self forgetSelf];
        }
    }
}

-(void)request:(APSHTTPRequest *)request onError:(APSHTTPResponse *)response
{
//    if(![request cancelled]) {
        if(hasOnerror) {
            NSError *error = [response error];
            NSMutableDictionary * event = [TiUtils dictionaryWithCode:[error code] message:[TiUtils messageFromError:error]];
            [event setObject:@"error" forKey:@"type"];
            [self fireCallback:@"onerror" withArg:event withSource:self withHandler:^(id result) {
                [self forgetSelf];
            }];
        } else {
            [self forgetSelf];
        }
//    }
}


-(void)request:(APSHTTPRequest *)request onReadyStateChange:(APSHTTPResponse *)response
{
    if(hasOnreadystatechange) {
        [self fireCallback:@"onreadystatechange" withArg:[NSDictionary dictionaryWithObjectsAndKeys:NUMINT(response.readyState),@"readyState", nil] withSource:self];
    }
}

-(void)request:(APSHTTPRequest *)request onRedirect:(APSHTTPResponse *)response
{
    if(hasOnredirect) {
        [self fireCallback:@"onredirect" withArg:nil withSource:self];
    }
}

#pragma mark - Public setters

-(void)setOnload:(id)callback
{
    ENSURE_SINGLE_ARG_OR_NIL(callback, KrollCallback)
    [self replaceValue:callback forKey:@"onload" notification:NO];
    hasOnload = (callback == nil) ? NO : YES;
}
-(void)setOnerror:(id)callback
{
    ENSURE_SINGLE_ARG_OR_NIL(callback, KrollCallback)
    [self replaceValue:callback forKey:@"onerror" notification:NO];
    hasOnerror = (callback == nil) ? NO : YES;;
}
-(void)setOnreadystatechange:(id)callback
{
    ENSURE_SINGLE_ARG_OR_NIL(callback, KrollCallback)
    [self replaceValue:callback forKey:@"onreadystatechange" notification:NO];
    hasOnreadystatechange = (callback == nil) ? NO : YES;;
}
-(void)setOndatastream:(id)callback
{
    ENSURE_SINGLE_ARG_OR_NIL(callback, KrollCallback)
    [self replaceValue:callback forKey:@"ondatastream" notification:NO];
    hasOndatastream = (callback == nil) ? NO : YES;;
}
-(void)setOnsendstream:(id)callback
{
    ENSURE_SINGLE_ARG_OR_NIL(callback, KrollCallback)
    [self replaceValue:callback forKey:@"onsendstream" notification:NO];
    hasOnsendstream = (callback == nil) ? NO : YES;;
}
-(void)setOnredirect:(id)callback
{
    ENSURE_SINGLE_ARG_OR_NIL(callback, KrollCallback)
    [self replaceValue:callback forKey:@"onredirect" notification:NO];
    hasOnredirect = (callback == nil) ? NO : YES;;
}

-(void)setRequestHeader:(id)args
{
    ENSURE_ARG_COUNT(args,2);
    if (httpRequest == nil) {
        NSLog(@"[ERROR] No request object found. Did you call open?");
        return;
    }
    NSString *key = [TiUtils stringValue:[args objectAtIndex:0]];
    NSString *value = [TiUtils stringValue:[args objectAtIndex:1]];
    
    if ([key isEqualToString:@"User-Agent"] && ![[[TiApp app] userAgent] isEqualToString:[[TiApp app] systemUserAgent]]) {
        NSLog(@"[WARN] You already specified a custom 'User-Agent' using Ti.userAgent. The user-agents will be concatenated.");
    }
    
    [httpRequest addRequestHeader:key value:value];
}

#pragma mark - Public getter properties

-(NSString*)allResponseHeaders
{
    NSDictionary* headers = [[self response] headers];
    NSMutableArray *array = [NSMutableArray array];
    for (NSString *key in headers) {
        [array addObject:[NSString stringWithFormat:@"%@:%@", key, [headers objectForKey:key]]];
    }
    return [array componentsJoinedByString: @"\n"];
}

-(NSString*)apiName
{
    return @"Ti.Network.HTTPClient";
}

-(NSNumber*)connected
{
    if([self response] == nil) {
        return NUMBOOL(NO);
    }
    APSHTTPResponseState state = [[self response] readyState];
    return NUMBOOL(
                   state == APSHTTPResponseStateHeaders ||
                   state == APSHTTPResponseStateLoading ||
                   state == APSHTTPResponseStateOpened
                   );
}

-(NSNumber*)status
{
    return NUMINTEGER([[self response] status]);
}

-(NSString*)statusText
{
    if (([self response] != nil) && ([[self response] readyState] >= APSHTTPResponseStateHeaders) ) {
        NSInteger status = [[self response] status];
        return [NSHTTPURLResponse localizedStringForStatusCode:status];
    }
    return nil;
}


-(NSString*)location
{
    if([self response] == nil) {
        return [self valueForUndefinedKey:@"url"];
    }
    return [[self response] location];
}
-(NSString*)connectionType
{
    if([self response] == nil) {
        return [self valueForUndefinedKey:@"method"];
    }
    return [[self response] connectionType];
}
-(NSString*)responseText
{
    return [[self response] responseString];
}

-(id)responseJSON
{
    return [TiUtils jsonParse:[self responseText]];
}

-(TiBlob*)responseData
{
    TiBlob *blob;
    if([[self response] saveToFile]) {
        blob = [[TiBlob alloc] _initWithPageContext:[self executionContext] andFile:[[self response] filePath]];
    } else {
        NSString *contentType = [TiUtils stringValue: [[self responseHeaders] valueForKey:@"Content-Type"]];
        blob = [[TiBlob alloc] _initWithPageContext:[self executionContext] andData:[[self response] responseData] mimetype:contentType];
    }
    return [blob autorelease];
}
-(TiDOMDocumentProxy*)responseXML
{
    if ([self responseText] != nil && (![[self responseText] isEqual:(id)[NSNull null]])) {
        TiDOMDocumentProxy *responseXML = [[[TiDOMDocumentProxy alloc] _initWithPageContext:[self executionContext]] autorelease];
        [responseXML parseString:[self responseText]];
        return responseXML;
    }
    return nil;
}
-(NSDictionary*)responseDictionary
{
    return [[self response] responseDictionary];
}
-(NSArray*)responseArray
{
    return [[self response] responseArray];
}
-(NSNumber*)readyState
{
    return NUMINT([[self response] readyState]);
}
-(NSDictionary*)responseHeaders
{
    return [[self response] headers];
}

MAKE_SYSTEM_NUMBER(UNSENT, NUMINT(APSHTTPResponseStateUnsent))
MAKE_SYSTEM_NUMBER(OPENED, NUMINT(APSHTTPResponseStateOpened))
MAKE_SYSTEM_NUMBER(HEADERS_RECEIVED, NUMINT(APSHTTPResponseStateHeaders))
MAKE_SYSTEM_NUMBER(LOADING, NUMINT(APSHTTPResponseStateLoading))
MAKE_SYSTEM_NUMBER(DONE, NUMINT(APSHTTPResponseStateDone))


@end

#endif
