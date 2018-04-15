#
# Appcelerator Titanium Mobile
# Copyright (c) 2011-2016 by Appcelerator, Inc. All Rights Reserved.
# Licensed under the terms of the Apache Public License
# Please see the LICENSE included with this distribution for details.
#
# Entry point Makefile for ndk-build

APP_BUILD_SCRIPT = src/native/Android.mk
TARGET_PLATFORM = android-16

# APP_CPPFLAGS += -std=c++11 -fexceptions -fno-builtin-stpcpy -fno-rtti -O3 -fvisibility=hidden -ffunction-sections -fno-data-sections -Wl,--exclude-libs=ALL -Wl,--gc-sections
APP_CPPFLAGS += -std=c++11 -fexceptions -fno-builtin-stpcpy -fno-rtti -O3
APP_STL := c++_shared 

	# APP_CPPFLAGS += -fvisibility=hidden -ffunction-sections -fno-data-sections -Wl,--exclude-libs=ALL -Wl,--gc-sections

APP_CPPFLAGS += -ffunction-sections -fdata-sections
APP_CFLAGS += -ffunction-sections -fdata-sections
APP_LDFLAGS += -fuse-ld=bfd -Wl,--gc-sections
APP_LDLIBS += -fuse-ld=bfd -Wl,--gc-sections

ifeq ($(BUILD_X86), 1)
	APP_ABI := arm64-v8a armeabi-v7a x86
else
	APP_ABI := arm64-v8a armeabi-v7a
endif

# APP_ABI := armeabi-v7a

TARGET_DEVICE := device
APP_OPTIM := release
#TI_DEBUG := 1
