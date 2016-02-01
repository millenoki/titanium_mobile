
Akylas Fork Titanium Mobile
============================

Welcome to the Akylas for of the Titanium open source project.  Titanium provides
a platform for web developers to build cross-platform, native mobile applications
using JavaScript.

Currently, Titanium supports mobile smartphone operating systems such as Apple iPhone, Google's Android, and Mobile Web. Other platforms, such as Windows Phone, are currently in development.

Titanium is licensed under the OSI approved Apache Public License (version 2). Please
see the LICENSE file for specific details.


## Differences

This fork has a few big changes from the main repo.

* most analytics unwanted logs have been turned off (not the one you want using the module).
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

## Download

You can get a prebuild version of [6.0.0.AKYLAS] (https://drive.google.com/open?id=0B6sfR-aIHi1oRUVZOFFzNFI2OEE)

## Legal Stuff

Appcelerator is a registered trademark of Appcelerator, Inc. Titanium is 
a registered trademark of Appcelerator, Inc.  Please see the LEGAL information about using our trademarks,
privacy policy, terms of usage and other legal information at [http://www.appcelerator.com/legal](http://www.appcelerator.com/legal).



