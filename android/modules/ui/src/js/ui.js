/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

var bootstrap = require("bootstrap");

// Keeps an object alive until dispose() is called.
// This is currently used to keep "top level" objects
// (ex: windows, tab groups) alive until their lifecycle ends.
function PersistentHandle(object) {
	this.cell = PersistentHandle.lastId++;
	PersistentHandle.objects[this.cell] = object;
}

// Objects retained by persistent handles.
// Each element in this array acts as a storage "cell"
// keeping the object reachable and alive until it is removed.
PersistentHandle.objects = {};

PersistentHandle.lastId = 0;

PersistentHandle.prototype.dispose = function() {
	if (this.cell == -1) {
		// This handle has already been disposed.
		return;
	}

	delete PersistentHandle.objects[this.cell];
	this.cell = -1;
}

exports.PersistentHandle = PersistentHandle;

exports.bootstrap = function(Titanium) {
	require("window").bootstrap(Titanium);
	require("tabgroup").bootstrap(Titanium);
	require("tab").bootstrap(Titanium);
	require("webview").bootstrap(Titanium);
	require("navigationwindow").bootstrap(Titanium);
	require("view").bootstrap(Titanium);

	Titanium.invocationAPIs.push({namespace: "UI", api: "createWindow"});
	Titanium.invocationAPIs.push({namespace: "UI", api: "createTabGroup"});
	Titanium.invocationAPIs.push({namespace: "UI", api: "createTab"});
	Titanium.invocationAPIs.push({namespace: "UI", api: "createNavigationWindow"});

	function iPhoneConstant(name) {
		Titanium.API.error("!!!");
		Titanium.API.error("!!! WARNING : Use of unsupported constant Ti.UI.iPhone." + name + " !!!");
		Titanium.API.error("!!!");
		return 0;
	}

	// TODO: Remove me. Only for temporary compatibility
	Titanium.UI.iPhone = {
		ActivityIndicatorStyle: {
			get BIG() { return iPhoneConstant("ActivityIndicatorStyle.BIG"); },
			get DARK() { return  iPhoneConstant("ActivityIndicatorStyle.DARK"); }
		},
		AnimationStyle: {
			get FLIP_FROM_LEFT() { return iPhoneConstant("AnimationStyle.FLIP_FROM_LEFT"); }
		},
		ProgressBarStyle: {
			get SIMPLE() { return iPhoneConstant("ProgressBarStyle.SIMPLE"); }
		},
		SystemButton: {
			get FLEXIBLE_SPACE() { return iPhoneConstant("SystemButton.FLEXIBLE_SPACE"); },
			get DISCLOSURE() { return iPhoneConstant("SystemButton.DISCLOSURE"); }
		},
		SystemButtonStyle: {
			get BAR() { return iPhoneConstant("SystemButtonStyle.BAR"); }
		},
		TableViewCellSelectionStyle: {
			get NONE() { return iPhoneConstant("TableViewCellSelectionStyle.NONE"); }
		},
		TableViewSeparatorStyle: {
			get NONE() { return iPhoneConstant("TableViewSeparatorStyle.NONE"); }
		},
		RowAnimationStyle: {
			get NONE() { return iPhoneConstant("RowAnimationStyle.NONE"); }
		},
		TableViewScrollPosition: {
			get MIDDLE() { return iPhoneConstant("TableViewScrollPosition.MIDDLE"); }
		},
		TableViewStyle: {
			get GROUPED() { return iPhoneConstant("TableViewStyle.GROUPED"); }
		}
	};

	// Define constants for ActivityIndicator here for now.
	Titanium.UI.ActivityIndicator.STATUS_BAR = 0;
	Titanium.UI.ActivityIndicator.DIALOG = 1;
	Titanium.UI.ActivityIndicator.INDETERMINANT = 0;
	Titanium.UI.ActivityIndicator.DETERMINANT = 1;
}

