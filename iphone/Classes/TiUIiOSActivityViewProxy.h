//
//  EsOyatsuAvcActivityViewControllerProxy.h
//  ActivityViewController
//
//  Created by Alberto Gonzalez on 9/18/13.
//
//

#import "TiProxy.h"

#import "RDActivityViewControllerDelegate.h"
@interface TiUIiOSActivityViewProxy : TiProxy<RDActivityViewControllerDelegate>

@property(retain, nonatomic) NSArray* excluded;
@property(retain, nonatomic) NSArray* items;
@property(retain, nonatomic) NSArray* activities;
@property(retain, nonatomic) KrollCallback* itemForActivityType;

@end
