//
//  TiUICollectionViewDefaultTemplate.m
//  Titanium
//
//  Created by Martin Guillon on 03/03/2016.
//
//

#import "TiUICollectionViewDefaultTemplate.h"
#import "TiUtils.h"

@implementation TiUICollectionViewDefaultTemplate

- (id)init
{
    self = [super initWithViewTemplate:[TiUtils jsonParse:@"{\"properties\":{\"layout\":\"horizontal\", \"height\":44, \"selector\":true,\"borderColor\":\"gray\", \"borderPadding\":{\"left\":-1, \"top\":-1, \"right\":-1}},\"childTemplates\":[{\"type\":\"Ti.UI.ImageView\",\"bindId\":\"imageView\",\"touchEnabled\":false,\"left\":15,\"width\":35,\"height\":35},{\"properties\":{\"layout\":\"vertical\",\"touchEnabled\":false,\"left\":15,\"right\":15},\"childTemplates\":[{\"type\":\"Ti.UI.Label\",\"bindId\":\"titleView\",\"properties\":{\"color\":\"black\",\"font\":{\"weight\":\"normal\",\"size\":18},\"ellipsize\":true,\"maxLines\":1,\"height\":\"FILL\",\"width\":\"FILL\"}},{\"type\":\"Ti.UI.Label\",\"bindId\":\"subtitleView\",\"properties\":{\"color\":\"black\",\"font\":{\"size\":15},\"ellipsize\":true,\"maxLines\":1,\"height\":\"FILL\",\"width\":\"FILL\", \"verticalAlign\":\"top\"}}]}]}" error:nil]];
    return self;
}


-(NSDictionary*)prepareDataItem:(NSDictionary*)dataItem {
    NSMutableDictionary* newDataItem = [NSMutableDictionary dictionaryWithDictionary:dataItem];
    NSMutableDictionary* properties = [newDataItem objectForKey:@"properties"];
    if (!properties) {
        properties = newDataItem;
    }
    BOOL hasSubtitle = [properties objectForKey:@"subtitle"];
    if ([properties objectForKey:@"title"] || [properties objectForKey:@"font"] || [properties objectForKey:@"color"])
    {
        NSMutableDictionary* labelDict = [properties objectForKey:@"titleView"];
        if (!labelDict)
        {
            labelDict = [NSMutableDictionary dictionary];
            [newDataItem setObject:labelDict forKey:@"titleView"];
        }
        [labelDict setObject:hasSubtitle?@"bottom":@"center" forKey:@"verticalAlign"];
        if ([properties objectForKey:@"title"]) {
            [labelDict setObject:[properties objectForKey:@"title"] forKey:@"text"];
        }
        if ([properties objectForKey:@"font"]) {
            [labelDict setObject:[properties objectForKey:@"font"] forKey:@"font"];
        }
        if ([properties objectForKey:@"color"]) {
            [labelDict setObject:[properties objectForKey:@"color"] forKey:@"color"];
        }
    }
    NSMutableDictionary* subDict = [properties objectForKey:@"subtitleView"];
    if (!subDict)
    {
        subDict = [NSMutableDictionary dictionary];
        [newDataItem setObject:subDict forKey:@"subtitleView"];
    }
    if ([properties objectForKey:@"subtitle"]) {
        [subDict setObject:[properties objectForKey:@"subtitle"] forKey:@"text"];
        [subDict setObject:@(YES) forKey:@"visible"];
    } else {
        [subDict setObject:@(NO) forKey:@"visible"];
    }
    return newDataItem;
}
@end
