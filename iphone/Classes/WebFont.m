/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#import "WebFont.h"
#import "TiBase.h"
#import "TiUtils.h"

@implementation WebFont

@synthesize family, size, isBoldWeight, isNormalWeight, isThinWeight, isLightWeight, isUltraLightWeight, isItalicStyle, isNormalStyle, isSemiboldWeight, textStyle;

- (void)dealloc
{
  RELEASE_TO_NIL(textStyle);
  RELEASE_TO_NIL(_weight);
  RELEASE_TO_NIL(font);
  RELEASE_TO_NIL(family);
  [super dealloc];
}

- (CGFloat)size
{
  if (size < 4) {
    size = [UIFont labelFontSize];
  }
  return size;
}

- (BOOL)isSizeNotSet
{
  return size < 4;
}

- (BOOL)isValidTextStyle:(NSString *)theStyle
{
  if ([TiUtils isIOS9OrGreater]) {
    return (
        [theStyle isEqualToString:UIFontTextStyleBody] ||
        [theStyle isEqualToString:UIFontTextStyleCaption1] ||
        [theStyle isEqualToString:UIFontTextStyleCaption2] ||
        [theStyle isEqualToString:UIFontTextStyleHeadline] ||
        [theStyle isEqualToString:UIFontTextStyleSubheadline] ||
        [theStyle isEqualToString:UIFontTextStyleFootnote] ||
        [theStyle isEqualToString:UIFontTextStyleCallout] ||
        [theStyle isEqualToString:UIFontTextStyleTitle3] ||
        [theStyle isEqualToString:UIFontTextStyleTitle2] ||
        [theStyle isEqualToString:UIFontTextStyleTitle1]);
  } else {
    return (
        [theStyle isEqualToString:UIFontTextStyleBody] ||
        [theStyle isEqualToString:UIFontTextStyleCaption1] ||
        [theStyle isEqualToString:UIFontTextStyleCaption2] ||
        [theStyle isEqualToString:UIFontTextStyleHeadline] ||
        [theStyle isEqualToString:UIFontTextStyleSubheadline] ||
        [theStyle isEqualToString:UIFontTextStyleFootnote]);
  }
}

- (NSCharacterSet *)camelcaseDelimiters
{
  return [NSCharacterSet characterSetWithCharactersInString:@"-_"];
}

- (NSString *)classifyString:(NSString *)string
{
  if (!string)
    return nil;
  NSUInteger len = [string length];

  BOOL capitalizeNext = YES;
  NSMutableString *underscored = [NSMutableString new];
  NSCharacterSet *delimiters = [self camelcaseDelimiters];

  for (int i = 0; i < len; ++i) {
    unichar current = [string characterAtIndex:i];
    if ([delimiters characterIsMember:current]) {
      capitalizeNext = YES;
    } else {
      NSString *currChar = [NSString stringWithCharacters:&current length:1];
      if (capitalizeNext) {
        [underscored appendString:[currChar uppercaseString]];
        capitalizeNext = NO;
      } else {
        [underscored appendString:currChar];
      }
    }
  }

  return [underscored autorelease];
}

- (UIFont *)font
{
  if (font == nil) {
    if (textStyle != nil && [self isValidTextStyle:textStyle]) {
      font = [[UIFont preferredFontForTextStyle:textStyle] retain];
    } else {
      if (family != nil) {
        NSString *fontName = family;

        if (self.weight) {
          fontName = [NSString stringWithFormat:@"%@-%@", fontName, self.weight];
        }
        NSSortDescriptor *sortOrder = [NSSortDescriptor sortDescriptorWithKey:@"self" ascending:NO];
        NSArray *fontNames = [[UIFont fontNamesForFamilyName:fontName] sortedArrayUsingDescriptors:[NSArray arrayWithObject:sortOrder]];
        if ([fontNames count] == 0 && ![fontName isEqualToString:family]) {
          //try ignoring the weight
          fontNames = [[UIFont fontNamesForFamilyName:family] sortedArrayUsingDescriptors:[NSArray arrayWithObject:sortOrder]];
        }
        if ([fontNames count] == 1) {
          font = [[UIFont fontWithName:[fontNames objectAtIndex:0] size:self.size] retain];
        } else if ([fontNames count] > 0) {
          NSString *foundFontName = nil;
          if (isBoldWeight || isSemiboldWeight || isItalicStyle) {
            NSMutableArray *primaryMatches = [[NSMutableArray alloc] init];
            NSString *primaryStyle = nil;
            NSString *secondaryStyle = nil;
            BOOL hasSecondaryStyle = NO;
            if (isBoldWeight || isSemiboldWeight) {
              primaryStyle = (isBoldWeight) ? @"Bold" : @"emiBold" /*Matches both SemiBold and DemiBold*/;
              secondaryStyle = @"Italic";
              if (isItalicStyle) {
                hasSecondaryStyle = YES;
              }
            } else {
              primaryStyle = @"Italic";
              secondaryStyle = @"Bold";
            }
            for (NSString *name in fontNames) {
              if (isItalicStyle && !hasSecondaryStyle) {
                if (([name rangeOfString:primaryStyle].location != NSNotFound) || ([name rangeOfString:@"Oblique"].location != NSNotFound)
                    || ([name rangeOfString:@"It" options:NSBackwardsSearch].location == ([name length] - 2))) {
                  if (([name rangeOfString:secondaryStyle].location == NSNotFound) && ([name rangeOfString:@"Heavy"].location == NSNotFound)) {
                    foundFontName = name;
                  } else {
                    [primaryMatches addObject:name];
                  }
                }
              } else {
                if (([name rangeOfString:primaryStyle].location != NSNotFound) || ([name rangeOfString:@"Heavy"].location != NSNotFound)) {
                  BOOL matchesItalic = ([name rangeOfString:secondaryStyle].location != NSNotFound) || ([name rangeOfString:@"Oblique"].location != NSNotFound)
                      || ([name rangeOfString:@"It" options:NSBackwardsSearch].location == ([name length] - 2));
                  if (matchesItalic == hasSecondaryStyle) {
                    foundFontName = name;
                  } else {
                    [primaryMatches addObject:name];
                  }
                }
              }
              if (foundFontName != nil) {
                break;
              }
            }

            if (foundFontName == nil) {
              if ([primaryMatches count] > 0) {
                foundFontName = [primaryMatches objectAtIndex:0];
              } else if ([fontNames count] > 0) {
                foundFontName = [fontNames objectAtIndex:0];
              }
            }

            [primaryMatches removeAllObjects];
            RELEASE_TO_NIL(primaryMatches);

            if (foundFontName != nil) {
              font = [[UIFont fontWithName:foundFontName size:self.size] retain];
            }

          } else {
            for (NSString *name in fontNames) {
              if (([name rangeOfString:@"Bold"].location == NSNotFound) && ([name rangeOfString:@"Italic"].location == NSNotFound)
                  && ([name rangeOfString:@"Oblique"].location == NSNotFound) && ([name rangeOfString:@"Heavy"].location == NSNotFound)) {
                foundFontName = name;
              }
              if (foundFontName != nil) {
                break;
              }
            }
            if (foundFontName == nil && [fontNames count] > 0) {
              foundFontName = [fontNames objectAtIndex:0];
            }
            if (foundFontName != nil) {
              font = [[UIFont fontWithName:foundFontName size:self.size] retain];
            }
          }
        } else {
          //family points to a fully qualified font name (so we hope)
          font = [[UIFont fontWithName:family size:self.size] retain];
        }
      }
    }
    if (font == nil) {
      //NO valid family specified. Just check for characteristics. Semi bold is ignored here.
      if (self.isBoldWeight) {
        UIFont *theFont = ([TiUtils isIOSVersionOrGreater:@"8.2"]) ? [UIFont systemFontOfSize:self.size weight:UIFontWeightBold] : [UIFont boldSystemFontOfSize:self.size];
        if (self.isItalicStyle) {
          NSString *fontFamily = [theFont familyName];
          if ([fontFamily isEqualToString:@".Helvetica Neue Interface"]) {
            fontFamily = @"Helvetica Neue";
          }
          NSArray *fontNames = [UIFont fontNamesForFamilyName:fontFamily];
          NSString *foundFontName = nil;
          for (NSString *name in fontNames) {
            if ([name rangeOfString:@"Bold"].location != NSNotFound) {
              if (([name rangeOfString:@"Italic"].location != NSNotFound) || ([name rangeOfString:@"Oblique"].location != NSNotFound)) {
                foundFontName = name;
              } else if ([name rangeOfString:@"It" options:NSBackwardsSearch].location == ([name length] - 2)) {
                foundFontName = name;
              }
            }
            if (foundFontName != nil) {
              break;
            }
          }
          if (foundFontName != nil) {
            font = [[UIFont fontWithName:foundFontName size:self.size] retain];
          } else {
            font = [theFont retain];
          }
        } else {
          font = [theFont retain];
        }
      } else if (self.isItalicStyle) {
        font = [[UIFont italicSystemFontOfSize:self.size] retain];
      } else if ([TiUtils isIOSVersionOrGreater:@"8.2"]) {
        if (self.isSemiboldWeight) {
          font = [[UIFont systemFontOfSize:self.size weight:UIFontWeightSemibold] retain];
        } else if (self.isThinWeight) {
          font = [[UIFont systemFontOfSize:self.size weight:UIFontWeightThin] retain];
        } else if (self.isLightWeight) {
          font = [[UIFont systemFontOfSize:self.size weight:UIFontWeightLight] retain];
        } else if (self.isUltraLightWeight) {
          font = [[UIFont systemFontOfSize:self.size weight:UIFontWeightUltraLight] retain];
        } else {
          font = [[UIFont systemFontOfSize:self.size] retain];
        }
      }
    }
  }
  // WORST-CASE-SCENARIO
  if (font == nil) {
    font = [[UIFont systemFontOfSize:self.size] retain];
  }
  return font;
}

- (BOOL)updateWithDict:(NSDictionary *)fontDict inherits:(WebFont *)inheritedFont;
{
  BOOL didChange = NO;

  NSString *theStyle = [TiUtils stringValue:[fontDict objectForKey:@"textStyle"]];
  if (theStyle == nil && inheritedFont != NULL) {
    theStyle = inheritedFont.textStyle;
  }
  if (![theStyle isEqualToString:textStyle]) {
    textStyle = [theStyle retain];
    didChange = YES;
  }

  float multiplier = 1.0; //Default is px.

  id sizeObject = [fontDict objectForKey:@"fontSize"];
  if ([sizeObject isKindOfClass:[NSString class]]) {
    sizeObject = [sizeObject lowercaseString];
    if ([sizeObject hasSuffix:@"px"]) {
      sizeObject = [sizeObject substringToIndex:[sizeObject length] - 2];
    }
    //TODO: Mod multipler with different suffixes (in, cm, etc)
  }

  if ([sizeObject respondsToSelector:@selector(floatValue)]) {
    float fontSize = multiplier * [sizeObject floatValue];
    if (fontSize != self.size) {
      didChange = YES;
      self.size = fontSize;
    }
  } else if ((inheritedFont != NULL) && (sizeObject == nil)) {
    float fontSize = inheritedFont.size;
    if (self.size != fontSize) {
      didChange = YES;
      self.size = fontSize;
    }

    float multiplier = 1.0; //Default is px.

    id sizeObject = [fontDict objectForKey:@"size"];
    if ([sizeObject isKindOfClass:[NSString class]]) {
      sizeObject = [sizeObject lowercaseString];
      if ([sizeObject hasSuffix:@"px"]) {
        sizeObject = [sizeObject substringToIndex:[sizeObject length] - 2];
      }
      //TODO: Mod multipler with different suffixes (in, cm, etc)
    }

    if ([sizeObject respondsToSelector:@selector(floatValue)]) {
      float fontSize = multiplier * [sizeObject floatValue];
      if (fontSize != self.size) {
        didChange = YES;
        self.size = fontSize;
      }
    } else if ((inheritedFont != NULL) && (sizeObject == nil)) {
      float fontSize = inheritedFont.size;
      if (self.size != fontSize) {
        didChange = YES;
        self.size = fontSize;
      }
    }

    id familyObject = [fontDict objectForKey:@"family"];
    if ([familyObject isKindOfClass:[NSString class]]) {
      // Expedient fix for compatibility with Android.  Apparently this is OK.
      if ([familyObject isEqual:@"monospace"] || [familyObject isEqual:@"monospaced"]) {
        self.family = @"Courier";
      } else {
        self.family = familyObject;
      }
      didChange = YES;
    } else if (inheritedFont != nil && inheritedFont.family != nil) {
      self.family = inheritedFont.family;
      didChange = YES;
    }

    NSString *fontWeightObject = [fontDict objectForKey:@"weight"];
    if ([fontWeightObject isKindOfClass:[NSString class]]) {
      fontWeightObject = [fontWeightObject lowercaseString];
      if ([fontWeightObject isEqualToString:@"semibold"]) {
        didChange |= !(self.isSemiboldWeight) || (self.isBoldWeight) || (self.isNormalWeight) || (self.isThinWeight) || (self.isLightWeight) || (self.isUltraLightWeight);
        self.isSemiboldWeight = YES;
        self.isBoldWeight = NO;
        self.isNormalWeight = NO;
        self.isThinWeight = NO;
        self.isLightWeight = NO;
        self.isUltraLightWeight = NO;
      } else if ([fontWeightObject isEqualToString:@"bold"]) {
        didChange |= !(self.isBoldWeight) || (self.isSemiboldWeight) || (self.isNormalWeight) || (self.isThinWeight) || (self.isLightWeight) || (self.isUltraLightWeight);
        self.isBoldWeight = YES;
        self.isSemiboldWeight = NO;
        self.isNormalWeight = NO;
        self.isThinWeight = NO;
        self.isLightWeight = NO;
        self.isUltraLightWeight = NO;
      } else if ([fontWeightObject isEqualToString:@"thin"]) {
        didChange |= !(self.isThinWeight) || (self.isBoldWeight) || (self.isSemiboldWeight) || (self.isNormalWeight) || (self.isLightWeight) || (self.isUltraLightWeight);
        self.isThinWeight = YES;
        self.isBoldWeight = NO;
        self.isSemiboldWeight = NO;
        self.isNormalWeight = NO;
        self.isLightWeight = NO;
        self.isUltraLightWeight = NO;
      } else if ([fontWeightObject isEqualToString:@"light"]) {
        didChange |= !(self.isLightWeight) || (self.isBoldWeight) || (self.isSemiboldWeight) || (self.isNormalWeight) || (self.isThinWeight) || (self.isUltraLightWeight);
        self.isLightWeight = YES;
        self.isBoldWeight = NO;
        self.isSemiboldWeight = NO;
        self.isNormalWeight = NO;
        self.isThinWeight = NO;
        self.isUltraLightWeight = NO;
      } else if ([fontWeightObject isEqualToString:@"ultralight"]) {
        didChange |= !(self.isUltraLightWeight) || (self.isBoldWeight) || (self.isSemiboldWeight) || (self.isNormalWeight) || (self.isLightWeight) || (self.isThinWeight);
        self.isUltraLightWeight = YES;
        self.isBoldWeight = NO;
        self.isSemiboldWeight = NO;
        self.isNormalWeight = NO;
        self.isLightWeight = NO;
        self.isThinWeight = NO;
      } else if ([fontWeightObject isEqualToString:@"normal"]) {
        didChange |= (self.isBoldWeight) || (self.isSemiboldWeight) || !(self.isNormalWeight) || (self.isThinWeight) || (self.isLightWeight) || (self.isUltraLightWeight);
        self.isBoldWeight = NO;
        self.isSemiboldWeight = NO;
        self.isNormalWeight = YES;
        self.isThinWeight = NO;
        self.isLightWeight = NO;
        self.isUltraLightWeight = NO;
      } else if ((inheritedFont != NULL) && (fontWeightObject == nil)) {
        BOOL isBoldBool = inheritedFont.isBoldWeight;
        if (self.isBoldWeight != isBoldBool) {
          didChange = YES;
          self.isBoldWeight = isBoldBool;
        }
        BOOL isNormalBool = inheritedFont.isNormalWeight;
        if (self.isNormalWeight != isNormalBool) {
          didChange = YES;
          self.isNormalWeight = isNormalBool;
        }
        BOOL isSemiboldBool = inheritedFont.isSemiboldWeight;
        if (self.isSemiboldWeight != isSemiboldBool) {
          didChange = YES;
          self.isSemiboldWeight = isSemiboldBool;
        }
        BOOL thinWeight = inheritedFont.isThinWeight;
        if (self.isThinWeight != thinWeight) {
          didChange = YES;
          self.isThinWeight = thinWeight;
        }
        BOOL lightWeight = inheritedFont.isLightWeight;
        if (self.isLightWeight != lightWeight) {
          didChange = YES;
          self.isLightWeight = lightWeight;
        }
        BOOL ultraLightWeight = inheritedFont.isUltraLightWeight;
        if (self.isUltraLightWeight != ultraLightWeight) {
          didChange = YES;
          self.isUltraLightWeight = ultraLightWeight;
        }
      }

      NSString *fontStyleObject = [fontDict objectForKey:@"style"];
      if ([fontStyleObject isKindOfClass:[NSString class]]) {
        fontStyleObject = [fontStyleObject lowercaseString];
        if ([fontStyleObject isEqualToString:@"italic"]) {
          didChange |= !(self.isItalicStyle) || (self.isNormalStyle);
          self.isItalicStyle = YES;
          self.isNormalStyle = NO;
        } else if ([fontStyleObject isEqualToString:@"normal"]) {
          didChange |= (self.isItalicStyle) || !(self.isNormalStyle);
          self.isItalicStyle = NO;
          self.isNormalStyle = YES;
        }
      }
    } else if ((inheritedFont != NULL) && (fontWeightObject == nil)) {
      BOOL isBoldBool = inheritedFont.isBoldWeight;
      if (self.isBoldWeight != isBoldBool) {
        didChange = YES;
        self.isBoldWeight = isBoldBool;
      }
      BOOL isNormalBool = inheritedFont.isNormalWeight;
      if (self.isNormalWeight != isNormalBool) {
        didChange = YES;
        self.isNormalWeight = isNormalBool;
      }
      BOOL isSemiboldBool = inheritedFont.isSemiboldWeight;
      if (self.isSemiboldWeight != isSemiboldBool) {
        didChange = YES;
        self.isSemiboldWeight = isSemiboldBool;
      }
    }

    NSString *fontStyleObject = [fontDict objectForKey:@"fontStyle"];
    if ([fontStyleObject isKindOfClass:[NSString class]]) {
      fontStyleObject = [fontStyleObject lowercaseString];
      if ([fontStyleObject isEqualToString:@"italic"]) {
        didChange |= !(self.isItalicStyle) || (self.isNormalStyle);
        self.isItalicStyle = YES;
        self.isNormalStyle = NO;
      } else if ([fontStyleObject isEqualToString:@"normal"]) {
        didChange |= (self.isItalicStyle) || !(self.isNormalStyle);
        self.isItalicStyle = NO;
        self.isNormalStyle = YES;
      }
    } else if ((inheritedFont != NULL) && (fontStyleObject == nil)) {
      BOOL isItalic = inheritedFont.isItalicStyle;
      if (self.isItalicStyle != isItalic) {
        didChange = YES;
        self.isItalicStyle = isItalic;
      }
      BOOL isNormal = inheritedFont.isNormalStyle;
      if (self.isNormalStyle != isNormal) {
        didChange = YES;
        self.isNormalStyle = isNormal;
      }
    }
  }

  return didChange;
}

+ (WebFont *)defaultBoldFont
{
  WebFont *result = [[self alloc] init];
  result.size = [UIFont labelFontSize];
  result.isBoldWeight = YES;
  return [result autorelease];
}

+ (WebFont *)defaultItalicFont
{
  WebFont *result = [[self alloc] init];
  result.size = [UIFont labelFontSize];
  result.isItalicStyle = YES;
  return [result autorelease];
}

+ (WebFont *)defaultFont
{
  WebFont *result = [[self alloc] init];
  result.size = [UIFont labelFontSize];
  return [result autorelease];
}

+ (WebFont *)fontWithName:(NSString *)name
{
  WebFont *result = [[[self alloc] init] autorelease];
  result.family = [[name copy] autorelease];
  result.size = [UIFont labelFontSize];
  return result;
}

@end
