#
# Appcelerator Titanium Mobile
# Copyright (c) 2011-2016 by Appcelerator, Inc. All Rights Reserved.
# Licensed under the terms of the Apache Public License
# Please see the LICENSE included with this distribution for details.
#
# Entry point Makefile for ndk-build

APP_BUILD_SCRIPT = src/native/Android.mk
TARGET_PLATFORM = android-16
APP_CPPFLAGS += -std=c++11
APP_STL := c++_shared
ifeq ($(BUILD_X86), 1)
	APP_ABI := armeabi-v7a x86
else
	APP_ABI := armeabi-v7a
endif

TARGET_DEVICE := device
APP_OPTIM := release
TI_DEBUG := 1
