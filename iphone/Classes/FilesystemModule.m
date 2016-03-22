/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2015 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_FILESYSTEM

#import "FilesystemModule.h"
#import "TiFilesystemFileProxy.h"
#import "TiFilesystemBlobProxy.h"
#import "TiFilesystemFileStreamProxy.h"
#import "TiHost.h"
#import <CommonCrypto/CommonDigest.h>
#import "TiFileSystemHelper.h"

#if TARGET_IPHONE_SIMULATOR 
extern NSString * TI_APPLICATION_RESOURCE_DIR;
#endif

@implementation FilesystemModule

-(void)dealloc
{
	[super dealloc];
}

// internal
-(id)resolveFile:(id)arg
{
	if ([arg isKindOfClass:[TiFilesystemFileProxy class]])
	{
		return [(TiFilesystemFileProxy*)arg path];
	}
	return [TiUtils stringValue:arg];
}

-(NSString*)apiName
{
    return @"Ti.Filesystem";
}

-(id)createTempFile:(id)args
{
	return [TiFilesystemFileProxy makeTemp:NO];
}

-(id)createTempDirectory:(id)args
{
	return [TiFilesystemFileProxy makeTemp:YES];
}

-(id)openStream:(id) args {
	NSNumber *fileMode = nil;
	
	ENSURE_ARG_AT_INDEX(fileMode, args, 0, NSNumber);
	ENSURE_VALUE_RANGE([fileMode intValue], TI_READ, TI_APPEND);

	if([args count] < 2) {
		[self throwException:TiExceptionNotEnoughArguments
				   subreason:nil
					location:CODELOCATION];
	}
	
	//allow variadic file components to be passed
	NSArray *pathComponents = [args subarrayWithRange:NSMakeRange(1, [args count] - 1 )];
	NSString *path = [TiFileSystemHelper pathFromComponents:pathComponents];
    
	NSArray *payload = [NSArray arrayWithObjects:path, fileMode, nil];

	return [[[TiFilesystemFileStreamProxy alloc] _initWithPageContext:[self executionContext] args:payload] autorelease];
}

-(id)MODE_APPEND
{
	return NUMINT(TI_APPEND);
}

-(id)MODE_READ
{
	return NUMINT(TI_READ);
}

-(id)MODE_WRITE
{
	return NUMINT(TI_WRITE);
}

-(id)isExternalStoragePresent:(id)unused
{
	//IOS treats the camera connection kit as just that, and does not allow
	//R/W access to it, which is just as well as it'd mess up cameras.
	return NUMBOOL(NO);
}

-(NSString*)resourcesDirectory
{
	return [TiFileSystemHelper resourcesDirectory];
}

-(NSString*)applicationDirectory
{
	return [TiFileSystemHelper applicationDirectory];
}

-(NSString*)applicationSupportDirectory
{
	return [TiFileSystemHelper applicationSupportDirectory];
}

-(NSString*)applicationDataDirectory
{
	return [TiFileSystemHelper applicationDataDirectory];
}

-(NSString*)applicationCacheDirectory
{
	return [TiFileSystemHelper applicationCacheDirectory];
}

-(NSString*)tempDirectory
{
	return [TiFileSystemHelper tempDirectory];
}

-(id)directoryForSuite:(id)args
{
    ENSURE_SINGLE_ARG(args, NSString);
    NSURL *groupURL = [[NSFileManager defaultManager] containerURLForSecurityApplicationGroupIdentifier:args];
    if (!groupURL) {
        NSLog(@"[ERROR] Directory not found for suite: %@ check the com.apple.security.application-groups entitlement.", args);
        return [NSNull null];
    }
    return [TiFileSystemHelper directoryForSuite:[groupURL path]];
}

-(NSString*)separator
{
	return [TiFileSystemHelper separator];
}

-(NSString*)lineEnding
{
	return [TiFileSystemHelper lineEnding];
}

-(id)getFile:(id)args
{
	NSString* newpath = [TiFileSystemHelper pathFromComponents:args];
    
	if ([newpath hasSuffix:@".html"]||
		 [newpath hasSuffix:@".js"]||
		 [newpath hasSuffix:@".css"])
	{
		NSURL *url = [NSURL fileURLWithPath:newpath];
		NSData *data = [TiUtils loadAppResource:url];
		if (data!=nil)
		{
			return [[[TiFilesystemBlobProxy alloc] initWithURL:url data:data] autorelease];
		}
	}
	
	return [[[TiFilesystemFileProxy alloc] initWithFile:newpath] autorelease];
}

-(id)getAsset:(id)args
{
    NSString* newpath = [self pathFromComponents:args];
    
    if ([newpath hasPrefix:[self resourcesDirectory]] &&
        ([newpath hasSuffix:@".jpg"]||
         [newpath hasSuffix:@".png"]))
    {
        UIImage *image = nil;
        NSRange range = [newpath rangeOfString:@".app"];
        NSString *imageArg = nil;
        if (range.location != NSNotFound) {
            imageArg = [newpath substringFromIndex:range.location+5];
        }
        //remove suffixes.
        imageArg = [imageArg stringByReplacingOccurrencesOfString:@"@3x" withString:@""];
        imageArg = [imageArg stringByReplacingOccurrencesOfString:@"@2x" withString:@""];
        imageArg = [imageArg stringByReplacingOccurrencesOfString:@"~iphone" withString:@""];
        imageArg = [imageArg stringByReplacingOccurrencesOfString:@"~ipad" withString:@""];
        
        if (imageArg != nil) {
            unsigned char digest[CC_SHA1_DIGEST_LENGTH];
            NSData *stringBytes = [imageArg dataUsingEncoding: NSUTF8StringEncoding];
            if (CC_SHA1([stringBytes bytes], (CC_LONG)[stringBytes length], digest)) {
                // SHA-1 hash has been calculated and stored in 'digest'.
                NSMutableString *sha = [[NSMutableString alloc] init];
                for (int i = 0; i < CC_SHA1_DIGEST_LENGTH; i++) {
                    [sha appendFormat:@"%02x", digest[i]];
                }
                [sha appendString:[newpath substringFromIndex:[newpath length] - 4]];
                image = [UIImage imageNamed:sha];
                RELEASE_TO_NIL(sha)
            }
        }
        return [[TiBlob alloc] _initWithPageContext:[self executionContext] andImage:image];
    }
    return [NSNull null];
}

-(NSString*)IOS_FILE_PROTECTION_NONE
{
	return NSFileProtectionNone;
}

-(NSString*)IOS_FILE_PROTECTION_COMPLETE
{
	return NSFileProtectionComplete;
}

-(NSString*)IOS_FILE_PROTECTION_COMPLETE_UNLESS_OPEN
{
	return NSFileProtectionCompleteUnlessOpen;
}

-(NSString*)IOS_FILE_PROTECTION_COMPLETE_UNTIL_FIRST_USER_AUTHENTICATION
{
	return NSFileProtectionCompleteUntilFirstUserAuthentication;
}

@end

#endif