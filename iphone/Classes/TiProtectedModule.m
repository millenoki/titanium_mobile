//
//  TiProtectedModule.m
//  Titanium
//
//  Created by Martin Guillon on 15/01/2016.
//
//

#import "TiProtectedModule.h"
#import "TiBase.h"
#import "TiHost.h"
#import "TiUtils.h"
#import "TiApp.h"
#import "NSString+AES.h"
#import "NSData+Additions.h"
#define COMMON_DIGEST_FOR_OPENSSL
#import "CommonCrypto/CommonDigest.h"
#import "TiExceptionHandler.h"

extern NSString * const TI_APPLICATION_ID;
@implementation TiProtectedModule


#pragma mark Lifecycle

-(NSString*) digest:(NSString*)input{
    NSData *data = [input dataUsingEncoding:NSASCIIStringEncoding allowLossyConversion:YES];
    uint8_t digest[CC_SHA1_DIGEST_LENGTH];
    CC_SHA1(data.bytes, (CC_LONG)data.length, digest);
    NSMutableString* output = [NSMutableString stringWithCapacity:CC_SHA1_DIGEST_LENGTH * 2];
    
    for(int i = 0; i < CC_SHA1_DIGEST_LENGTH; i++)
        [output appendFormat:@"%02x", digest[i]];
    
    return output;
}
-(NSString*)getPassword {
    @throw [NSException exceptionWithName:NSInternalInconsistencyException
                                   reason:[NSString stringWithFormat:@"You must override %@ in a subclass", NSStringFromSelector(_cmd)]
                                 userInfo:nil];
}

-(NSString*)getPasswordKey {
    @throw [NSException exceptionWithName:NSInternalInconsistencyException
                                   reason:[NSString stringWithFormat:@"You must override %@ in a subclass", NSStringFromSelector(_cmd)]
                                 userInfo:nil];
}

static NSMutableDictionary* sComputedKeys = nil;

-(void)startup
{
    NSString* appId = TI_APPLICATION_ID;
    NSDictionary* tiappProperties = [TiApp tiAppProperties];
    NSString* passwordKey = [self getPasswordKey];
    NSString* commonjsKey = [tiappProperties objectForKey:passwordKey];
    if (![appId isEqualToString:@"com.akylas.titanium.ks"]) {
        if (!commonjsKey) {
            [self throwException:[NSString stringWithFormat:@"You need to set the \"%@\"", passwordKey] subreason:nil location:CODELOCATION];
        }
        if (sComputedKeys == nil) {
            sComputedKeys = [[NSMutableDictionary dictionary] retain];
        }
        NSString * toCompute = [NSString stringWithFormat:@"%@%@", TI_APPLICATION_ID, [self getPassword]];
        NSString* result = [sComputedKeys objectForKey:toCompute];
        if (!result) {
            result = [self digest:toCompute];
            [sComputedKeys setObject:result forKey:toCompute];
        }
        if (![[commonjsKey lowercaseString] isEqualToString:result]) {
            [self throwException:[NSString stringWithFormat:@"wrong \"%@\" key!", passwordKey] subreason:nil location:CODELOCATION];
        }
    }
    
    // this method is called when the module is first loaded
    // you *must* call the superclass
    [super startup];
    
    
    
    DebugLog(@"[INFO] %@ loaded",self);
}

-(void)shutdown:(id)sender
{
    // this method is called when the module is being unloaded
    // typically this is during shutdown. make sure you don't do too
    // much processing here or the app will be quit forceably
    
    // you *must* call the superclass
    [super shutdown:sender];
}

#pragma mark Cleanup

-(void)dealloc
{
    // release any resources that have been retained by the module
    [super dealloc];
}

#pragma mark Internal Memory Management

-(void)didReceiveMemoryWarning:(NSNotification*)notification
{
    // optionally release any resources that can be dynamically
    // reloaded once memory is available - such as caches
    [super didReceiveMemoryWarning:notification];
}

#pragma mark Listener Notifications

@end