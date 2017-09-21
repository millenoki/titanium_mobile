//
//  NSString+AES.h
//  AESEncryptionDemo

//

#import "NSData+AES.h"
#import <Foundation/Foundation.h>

@interface NSString (AES)
- (NSString *)AES128EncryptWithKey:(NSString *)key;
- (NSString *)AES128DecryptWithKey:(NSString *)key;
@end
