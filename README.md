
Akylas Fork Titanium Mobile
============================
| Travis CI  | Jenkins CI |
|------------|------------|
| [![Build Status](https://travis-ci.org/appcelerator/titanium_mobile.svg?branch=master)](https://travis-ci.org/appcelerator/titanium_mobile) | [![Build Status](https://jenkins.appcelerator.org/buildStatus/icon?job=titanium_mobile_master_SG)](https://jenkins.appcelerator.org/job/titanium_mobile_master_SG/) |

Welcome to the Akylas for of the Titanium open source project.  Titanium provides
a platform for web developers to build cross-platform, native mobile applications
using JavaScript.

Currently, Titanium supports mobile smartphone operating systems such as Apple iPhone, Google's Android, and Mobile Web. Other platforms, such as Windows Phone, are currently in development.

Titanium is licensed under the OSI approved Apache Public License (version 2). Please
see the LICENSE file for specific details.


## Differences

First know that fork is centered around iOS and Android. No work have been done on other platforms.

This fork has a few big changes from the main repo.

* most analytics unwanted logs have been turned off (not the one you want using the module).
* possible to debug your app with you modules code directly from XCode and Eclipse
* some properties were changed for easier use and better handling
	* ``padding``: ``paddingLeft``, ``paddingRight``,``paddingBottom``,``paddingTop`` were removed in favor of the ``padding`` property which you can use in many ways
		1. ``padding:2``
		2. ``padding:[0,0,2,0]`` (top, left, bottom, right)
		3. ``padding:{left:2}``
	* ``font``: the ``font`` has changed a bit in the sense that inner props lose there ``font`` prefix
		1. use ``size`` instead of ``fontSize``
		2. use ``family`` instead of ``fontFamily``
		3. use ``weight`` instead of ``fontWeight``
* a lot of  method definitions were changed. The reason was to make a more powerful framework. For example on android most method definitions using ``KrollDict`` were changed to use ``HashMap``. This prevented a lot of unecessary object allocation. Consequently most modules will need to be recompiled for that fork

## Documentation

Updating documentation takes a lot of time, time that i don't really have. I am a one man project...
Any contribution is welcome

## Building

To build and work with that fork you need to know a few things

* The XCode project references some of my modules projects. This is not a problem when building the framework as those references will be removed. However if you want do work on the framework using XCode (the kitchensink project) you will need to remove those references
* i use a custom version of the Ti CLI. You can get it [here](https://github.com/Akylas/titanium)
* i use a custom version of the Ti appc node module. The reason was to remove any statistic sent to Appcelerator. You can find it [here](https://github.com/Akylas/node-appc). This fork uses it automatically.
* the android modules projects reference some external projects (SubImageView, ListViewAnimations ...). This is only for when developing the framework itself. You need to clone those projects and add them to your eclipse workspace:
	* [ListViewAnimations](https://github.com/Akylas/ListViewAnimations)
	* [StickyListHeaders](https://github.com/Akylas/StickyListHeaders)
	* [gpuimage](https://github.com/Akylas/android-gpuimage)
* everything is made to be built using nodejs now. Which means that python build is deprecated, even for modules. Your modules structure might need to be updated. Especially the XCode projects and the eclipse projects. You should create a test module to see the difference.

## Sample App

The sample app have been changed. The first thing is the project changed on disk to allow iOS and Android dev apps to use the same code.
Also the ``app.js`` has been totally rewritten. It's not pretty but it's there to allow me to easily test anything!
The sample app is also used to develop Akylas owns modules. So you need to disable them in the sample app:
	* put ``__AKYLAS_DEV__`` to false in the first lines of the ``app.js``

## Download

You can get a prebuild version of [6.0.0.AKYLAS] (https://drive.google.com/open?id=0B6sfR-aIHi1oajFRcG0zNExGVFk)

## Legal Stuff

Appcelerator is a registered trademark of Appcelerator, Inc. Titanium is 
a registered trademark of Appcelerator, Inc.  Please see the LEGAL information about using our trademarks,
privacy policy, terms of usage and other legal information at [http://www.appcelerator.com/legal](http://www.appcelerator.com/legal).
