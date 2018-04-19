#
# Appcelerator Titanium Mobile
# Copyright (c) 2011-2016 by Appcelerator, Inc. All Rights Reserved.
# Licensed under the terms of the Apache Public License
# Please see the LICENSE included with this distribution for details.
#
# Entry point Makefile for ndk-build

APP_BUILD_SCRIPT = src/native/Android.mk
TARGET_PLATFORM = android-16

APP_CPPFLAGS += -std=c++11 -fno-builtin-stpcpy -fno-rtti -Os -g -DNDEBUG -fomit-frame-pointer -fstrict-aliasing -funswitch-loops -finline-limit=64 -ffunction-sections -fdata-sections
APP_CFLAGS += -std=c++11 -fno-builtin-stpcpy -fno-rtti -Os -g -DNDEBUG -fomit-frame-pointer -fstrict-aliasing -funswitch-loops -finline-limit=64 -ffunction-sections -fdata-sections
APP_STL := c++_shared 

APP_LDFLAGS +=  -Wl,--gc-sections,--strip-all,--icf=safe #,--exclude-libs=ALL
APP_LDLIBS +=   -Wl,--gc-sections,--strip-all,--icf=safe #,--exclude-libs=ALL

ifeq ($(BUILD_X86), 1)
	APP_ABI := arm64-v8a armeabi-v7a x86
else
	APP_ABI := arm64-v8a armeabi-v7a
endif

# APP_ABI := armeabi-v7a

TARGET_DEVICE := device
APP_OPTIM := release
#TI_DEBUG := 1
