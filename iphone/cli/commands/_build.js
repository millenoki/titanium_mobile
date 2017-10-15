/**
 * iOS build command.
 *
 * @module cli/_build
 *
 * @copyright
 * Copyright (c) 2009-2017 by Appcelerator, Inc. All Rights Reserved.
 *
 * @license
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

'use strict';

const appc = require('node-appc'),
	async = require('async'),
	bufferEqual = require('buffer-equal'),
	Builder = require('node-titanium-sdk/lib/builder'),
	CleanCSS = require('clean-css'),
	crypto = require('crypto'),
	cyan = require('colors').cyan,
	DOMParser = require('xmldom').DOMParser,
	ejs = require('ejs'),
	latenize = require('latenize'),
	fields = require('fields'),
	fs = require('fs'),
	humanize = require('humanize'),
	ioslib = require('ioslib'),
	minimatch = require('minimatch'),
	jsanalyze = require('node-titanium-sdk/lib/jsanalyze'),
	moment = require('moment'),
	net = require('net'),
	path = require('path'),
	PNG = require('pngjs').PNG,
	spawn = require('child_process').spawn, // eslint-disable-line security/detect-child-process
	ti = require('node-titanium-sdk'),
	util = require('util'),
	wrench = require('wrench'),
	xcode = require('xcode'),
	xcodeParser = require('xcode/lib/parser/pbxproj'),
	babel = require('babel-core'),
	ts = require('typescript'),
	i18n = appc.i18n(__dirname),
	__ = i18n.__,
	__n = i18n.__n,
	parallel = appc.async.parallel,
	series = appc.async.series,
	version = appc.version;
const platformsRegExp = new RegExp('^(' + ti.allPlatformNames.join('|') + '$)'); // eslint-disable-line security/detect-non-literal-regexp
const pemCertRegExp = /(^-----BEGIN CERTIFICATE-----)|(-----END CERTIFICATE-----.*$)|\n/g;

process.on('unhandledRejection', (reason, p) => {
	console.log('Unhandled Rejection at: Promise', p, 'reason:', reason);
	// application specific logging, throwing an error, or other logic here
});

function iOSBuilder() {
	Builder.apply(this, arguments);

	// the minimum supported iOS SDK required when building
	this.minSupportedIosSdk = parseInt(version.parseMin(this.packageJson.vendorDependencies['ios sdk']));

	// the maximum supported iOS SDK required when building
	this.maxSupportedIosSdk = parseInt(version.parseMax(this.packageJson.vendorDependencies['ios sdk']));

	// object mapping the build-targets to their deploy-types
	this.deployTypes = {
		'simulator': 'development',
		'device': 'test',
		'dist-appstore': 'production',
		'dist-adhoc': 'production'
	};

	// list of available build-targets
	this.targets = [ 'simulator', 'device', 'dist-appstore', 'dist-adhoc' ];

	// object of device families to map the --device-family parameter to the
	// native TARGETED_DEVICE_FAMILY build-setting
	this.deviceFamilies = {
		iphone: '1',
		ipad: '2',
		universal: '1,2',
		watch: '4'
	};

	// device-family set by the --device-family parameter
	this.deviceFamily = null;

	// blacklisted files and directories that throw an error when used and will
	// lead to a rejection when submitted
	this.blacklistDirectories = [
		'contents',
		'resources',
		'plugins',
		'watch',
		'_codesignature',
		'embedded.mobileprovision',
		'info.plist',
		'pkginfo'
	];

	// graylisted directories that throw a warning when used and may lead to a
	// rejection when submitted
	this.graylistDirectories = [
		'frameworks'
	];

	// templates-directory to render the ApplicationRouting.m into
	this.templatesDir = path.join(this.platformPath, 'templates', 'build');

	// object of all used Titanium symbols, used to determine preprocessor statements, e.g. USE_TI_UIWINDOW
	this.tiSymbols = {};

	// when true, uses the JavaScriptCore that ships with iOS instead of the original Titanium version
	this.useJSCore = true;

	// when false, JavaScript will run on its own thread - the Kroll Thread
	this.runOnMainThread = true;

	this.useBabel = false;

	this.useAutoLayout = false;

	// populated the first time getDeviceInfo() is called
	this.deviceInfoCache = null;

	// the selected provisioning profile info when doing a device, dist-appstore, or dist-adhoc build
	this.provisioningProfile = null;

	// cache of provisioning profiles
	this.provisioningProfileLookup = {};

	// list of all extensions (including watch apps)
	this.extensions = [];

	// simulator handles; only used when --target is simulator
	this.simHandle = null;
	this.watchSimHandle = null;

	// when true and building an app with a watch extension for the simulator and the --launch-watch-app
	// flag is passed in, then show the external display and launch the watch app
	this.hasWatchAppV2orNewer = false;

	// if this app has any watch apps, then we need to know the min watchOS version for one of them
	// so that we can select a watch simulator
	this.watchMinOSVersion = null;

	// the parsed build manifest from the previous build
	this.previousBuildManifest = {};

	// contains the current build's info
	this.currentBuildManifest = {
		files: {}
	};

	// when true, the entire build dir is nuked at the start of the build
	this.forceCleanBuild = false;

	// when true, calls xcodebuild
	this.forceRebuild = false;

	// a list of relative paths to js files that need to be encrypted
	// note: the filename will have all periods replaced with underscores
	this.jsFilesToEncrypt = [];

	// set to true if any js files changed so that we can trigger encryption to run
	this.jsFilesChanged = false;

	// an array of products (Xcode targets) being built
	this.products = [];

	// when true and Apple Transport Security is manually enabled via custom Info.plist or
	// tiapp.xml <ios><plist> section, then injects appcelerator.com whitelisted
	//
	// we default to true, but if "ios.whitelist.appcelerator.com" tiapp.xml property is
	// set to false, then we'll force appcelerator.com to NOT be whitelisted
	this.whitelistAppceleratorDotCom = true;

	// launch screen storyboard settings
	this.enableLaunchScreenStoryboard = true;
	this.defaultLaunchScreenStoryboard = true;
	this.defaultBackgroundColor = null;
}

util.inherits(iOSBuilder, Builder);

iOSBuilder.prototype.assertIssue = function assertIssue(issues, name) {
	for (let i = 0; i < issues.length; i++) {
		if ((typeof name === 'string' && issues[i].id === name) || (typeof name === 'object' && name.test(issues[i].id))) {
			this.logger.banner();
			appc.string.wrap(issues[i].message, this.config.get('cli.width', 100)).split('\n').forEach(function (line, i, arr) {
				this.logger.error(line.replace(/(__(.+?)__)/g, '$2'.bold));
				if (!i && arr.length > 1) {
					this.logger.log();
				}
			}, this);
			this.logger.log();
			process.exit(1);
		}
	}
};

/**
 * Retrieves the certificate information by name.
 *
 * @param {String} name - The cert name.
 * @param {String} [type] - The type of cert to scan (developer or distribution).
 * @returns {Object|null}
 * @access private
 */
iOSBuilder.prototype.findCertificate = function findCertificate(name, type) {
	/* eslint-disable max-depth */
	if (name && this.iosInfo) {
		for (const keychain of Object.keys(this.iosInfo.certs.keychains)) {
			const scopes = this.iosInfo.certs.keychains[keychain];
			const types = type ? [ type ] : Object.keys(scopes);
			for (const scope of types) {
				if (scopes[scope]) {
					for (const cert of scopes[scope]) {
						if (cert.name === name) {
							return cert;
						}
					}
				}
			}
		}
	}

	return null;
};

/**
 * Determines the valid list of devices or simulators. This is used for prompting
 * and validation.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.getDeviceInfo = function getDeviceInfo() {
	if (this.deviceInfoCache) {
		return this.deviceInfoCache;
	}

	const argv = this.cli.argv,
		deviceInfo = {
			devices: [],
			udids: {},
			maxName: 0,
			preferred: null
		};

	if (argv.target === 'device') {
		// build the list of devices
		this.iosInfo.devices.forEach(function (device) {
			device.name.length > deviceInfo.maxName && (deviceInfo.maxName = device.name.length);
			deviceInfo.devices.push({
				udid: device.udid,
				name: device.name,
				deviceClass: device.deviceClass,
				productVersion: device.productVersion
			});
			deviceInfo.udids[device.udid] = device;
		});

		if (this.config.get('ios.autoSelectDevice', true) && !argv['device-id']) {
			deviceInfo.preferred = deviceInfo.devices[0];
		}
	} else if (argv.target === 'simulator') {
		deviceInfo.devices = {};

		this.initTiappSettings();

		// build the list of simulators
		Object
			.keys(this.iosInfo.simulators.ios)
			.sort(function (v1, v2) {
				return appc.version.eq(v1, v2) ? 0 : appc.version.lt(v1, v2) ? -1 : 1;
			})
			.reverse()
			.forEach(function (ver) {
				deviceInfo.devices[ver] || (deviceInfo.devices[ver] = []);
				this.iosInfo.simulators.ios[ver].forEach(function (sim) {
					// get the Xcode id for the version that supports the --ios-version
					// var xcodeId = argv['ios-version'] && this.iosInfo.iosSDKtoXcode[argv['ios-version']];
					// if ((argv['launch-watch-app'] || argv['launch-watch-app-only']) && (!Object.keys(sim.watchCompanion).length || (xcodeId && !sim.watchCompanion[xcodeId]))) {
					if ((argv['launch-watch-app'] || argv['launch-watch-app-only']) && !Object.keys(sim.watchCompanion).length) {
						// this sim doesn't support watches, skip
						return;
					}

					const watchUDID = argv['watch-device-id'];
					if (watchUDID) {
						const isValid = Object
							.keys(this.iosInfo.simulators.watchos)
							.some(function (ver) {
								return this.iosInfo.simulators.watchos[ver].some(function (wsim) {
									return wsim.udid === watchUDID;
								});
							}, this);

						if (isValid) {
							if (!Object.keys(sim.watchCompanion).length) {
								return;
							}

							if (!Object.keys(sim.watchCompanion).some(function (xcodeId) {
								return sim.watchCompanion[xcodeId][watchUDID];
							})) {
								return;
							}
						}
					}

					sim.name.length > deviceInfo.maxName && (deviceInfo.maxName = sim.name.length);
					deviceInfo.devices[ver].push({
						udid: sim.udid,
						name: sim.name,
						deviceClass: sim.family,
						model: sim.model,
						productVersion: ver
					});
					deviceInfo.udids[sim.udid] = sim;

					// see if we should prefer this simulator
					if (this.config.get('ios.autoSelectDevice', true) && argv['ios-version'] && !argv['device-id']) {
						deviceInfo.preferred = deviceInfo.devices[argv['ios-version']] && deviceInfo.devices[argv['ios-version']][0];
					}
				}, this);
			}, this);
	}

	return this.deviceInfoCache = deviceInfo;
};

iOSBuilder.prototype.getDeviceFamily = function getDeviceFamily() {
	if (this.deviceFamily) {
		return this.deviceFamily;
	}

	let deviceFamily = this.cli.argv['device-family'];
	const deploymentTargets = this.tiapp && this.tiapp['deployment-targets'];

	if (!deviceFamily && deploymentTargets) {
		// device family was not an environment variable, construct via the tiapp.xml's deployment targets
		if (deploymentTargets.iphone && deploymentTargets.ipad) {
			deviceFamily = this.cli.argv.$originalPlatform === 'ipad' ? 'ipad' : 'universal';
		} else if (deploymentTargets.iphone) {
			deviceFamily = 'iphone';
		} else if (deploymentTargets.ipad) {
			deviceFamily = 'ipad';
		}
	}

	return this.deviceFamily = deviceFamily;
};

/**
 * Returns iOS build-specific configuration options.
 *
 * @param {Object} logger - The logger instance
 * @param {Object} config - The CLI config
 * @param {Object} cli - The CLI instance
 *
 * @returns {Function|undefined} A function that returns the config info or undefined
 */
iOSBuilder.prototype.config = function config(logger, config, cli) {
	Builder.prototype.config.apply(this, arguments);

	const _t = this;

	this.ignoreDirs = new RegExp(config.get('cli.ignoreDirs'));
	this.ignoreFiles = new RegExp(config.get('cli.ignoreFiles'));

	// we hook into the pre-validate event so that we can stop the build before
	// prompting if we know the build is going to fail.
	cli.on('cli:pre-validate', function (obj, callback) {
		if (cli.argv.platform && !/^(ios|iphone|ipad)$/i.test(cli.argv.platform)) {
			return callback();
		}

		// check that the iOS environment is found and sane
		this.assertIssue(this.iosInfo.issues, 'IOS_XCODE_NOT_INSTALLED');
		this.assertIssue(this.iosInfo.issues, 'IOS_NO_SUPPORTED_XCODE_FOUND');
		this.assertIssue(this.iosInfo.issues, 'IOS_NO_IOS_SDKS');
		this.assertIssue(this.iosInfo.issues, 'IOS_NO_IOS_SIMS');
		this.assertIssue(this.iosInfo.issues, 'IOS_XCODE_EULA_NOT_ACCEPTED');

		callback();
	}.bind(this));

	return function (done) {
		ioslib.detect({
			// env
			xcodeSelect:       config.get('osx.executables.xcodeSelect'),
			security:          config.get('osx.executables.security'),
			// provisioning
			profileDir:        config.get('ios.profileDir'),
			// xcode
			searchPath:        config.get('paths.xcode'),
			minIosVersion:     this.packageJson.minIosVersion,
			supportedVersions: this.packageJson.vendorDependencies.xcode
		}, function (err, iosInfo) {
			if (err) {
				// this is bad and probably because we don't have a compatible
				// node-ios-device binary for the current version of node
				//
				// ideally we'd failout, but we can't... the Titanium CLI doesn't
				// allow the config() call to return an error. my bad design. :(
				iosInfo = {
					certs: {
						keychains: {}
					},
					devices: [],
					issues: [],
					simulators: {
						ios: []
					},
					xcode: {}
				};
			}

			this.iosInfo = iosInfo;

			// add itunes sync
			iosInfo.devices.push({
				udid: 'itunes',
				name: 'iTunes Sync'
			});

			// we have more than 1 device plus itunes, so we should show 'all'
			if (iosInfo.devices.length > 2) {
				iosInfo.devices.push({
					udid: 'all',
					name: 'All Devices'
				});
			}

			// get the all installed iOS SDKs and Simulators across all Xcode versions
			const allSdkVersions = {},
				sdkVersions = {},
				simVersions = {};
			Object.keys(iosInfo.xcode).forEach(function (ver) {
				if (iosInfo.xcode[ver].supported) {
					iosInfo.xcode[ver].sdks.forEach(function (sdk) {
						allSdkVersions[sdk] = 1;
						if (version.gte(sdk, this.minSupportedIosSdk)) {
							sdkVersions[sdk] = 1;
						}
					}, this);
					iosInfo.xcode[ver].sims.forEach(function (sim) {
						simVersions[sim] = 1;
					});
				}
			}, this);
			this.iosAllSdkVersions = version.sort(Object.keys(allSdkVersions));
			this.iosSdkVersions = version.sort(Object.keys(sdkVersions));

			cli.createHook('build.ios.config', function (callback) {
				callback(null, {
					flags: {
						'force-copy': {
							desc: __('forces files to be copied instead of symlinked for %s builds only', 'simulator'.cyan)
						},
						'force-copy-all': {
							desc: __('identical to the %s flag, except this will also copy the %s libTiCore.a file', '--force-copy',
								humanize.filesize(fs.statSync(path.join(_t.platformPath, 'libTiCore.a')).size, 1024, 1).toUpperCase().cyan)
						},
						'launch-watch-app': {
							desc: __('for %s builds, after installing an app with a watch extention, launch the watch app and the main app', 'simulator'.cyan)
						},
						'launch-watch-app-only': {
							desc: __('for %s builds, after installing an app with a watch extention, launch the watch app instead of the main app', 'simulator'.cyan)
						},
						'sim-focus': {
							default: true,
							desc: __('focus the iOS Simulator')
						},
						'xcode': {
							// DEPRECATED
							// secret flag to perform Xcode pre-compile build step
							// callback: function(value) {
							// 	if (value) {
							// 		// we deprecated the --xcode flag which was passed in during the Xcode pre-compile phase
							// 		logger.error(__('The generated Titanium Xcode project is too old.'));
							// 		logger.error(__('Please clean and rebuild the project.'));
							// 		process.exit(1);
							// 	}
							// },
							hidden: true
						}
					},
					options: {
						'build-type': {
							hidden: true
						},
						'debug-host': {
							hidden: true
						},
						'deploy-type':                this.configOptionDeployType(100),
						'device-id':                  this.configOptionDeviceID(210),
						'developer-name':             this.configOptionDeveloperName(170),
						'distribution-name':          this.configOptionDistributionName(180),
						'device-family':              this.configOptionDeviceFamily(120),
						'hide-error-controller': {
							hidden: true
						},
						'ios-version':                this.configOptioniOSVersion(130),
						'keychain':                   this.configOptionKeychain(),
						'launch-bundle-id':           this.configOptionLaunchBundleId(),
						'launch-url': {
							// url for the application to launch in mobile Safari, as soon as the app boots up
							hidden: true
						},
						'output-dir':                 this.configOptionOutputDir(200),
						'pp-uuid':                    this.configOptionPPuuid(190),
						'profiler-host': {
							hidden: true
						},
						'target':                     this.configOptionTarget(110),
						'watch-app-name':             this.configOptionWatchAppName(212),
						'watch-device-id':            this.configOptionWatchDeviceId(215)
					}
				});
			}.bind(this))(function (err, result) {
				done(_t.conf = result);
			});
		}.bind(this)); // end of ioslib.detect()
	}.bind(this);
};

/**
 * Defines the --deploy-type option.
 *
 * @param {Integer} order - The order to apply to this option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionDeployType = function configOptionDeployType(order) {
	return {
		abbr: 'D',
		desc: __('the type of deployment; only used when target is %s or %s', 'simulator'.cyan, 'device'.cyan),
		hint: __('type'),
		order: order,
		values: [ 'test', 'development' ]
	};
};

/**
 * Defines the --device-id option.
 *
 * @param {Integer} order - The order to apply to this option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionDeviceID = function configOptionDeviceID(order) {
	return {
		abbr: 'C',
		desc: __('the udid of the iOS simulator or iOS device to install the application to; for %s builds %s',
			'device'.cyan, ('[' + 'itunes'.bold + ', <udid>, all]').grey),
		hint: __('udid'),
		order: order,
		helpNoPrompt: function (logger, msg) {
			// if prompting is disabled and there's a problem, then help will use this function to display details
			logger.error(msg);
			const info = this.getDeviceInfo();
			if (info.devices) {
				if (this.cli.argv.target === 'device') {
					logger.log('\n' + __('Available iOS Devices:'));
					info.devices.forEach(function (sim) {
						logger.log('  ' + (info.devices.length > 1 ? appc.string.rpad(sim.udid, 40) : sim.udid).cyan + '  ' + sim.name);
					});
					logger.log();
				} else {
					logger.log('\n' + __('Available iOS Simulators:'));
					Object.keys(info.devices).forEach(function (ver) {
						logger.log(String(ver).grey);
						info.devices[ver].forEach(function (sim) {
							logger.log('  ' + sim.udid.cyan + '  ' + sim.name);
						});
						logger.log();
					});
				}
			}
		}.bind(this),
		prompt: function (callback) {
			var info = this.getDeviceInfo();
			if (info.preferred) {
				this.cli.argv['device-id'] = info.preferred.udid;
				return callback();
			}

			let options = {},
				maxName = 0,
				maxDesc = 0;

			// build a filtered list of simulators based on any legacy options/flags
			if (Array.isArray(info.devices)) {
				options = info.devices;
				info.devices.forEach(function (d) {
					if (d.name.length > maxName) {
						maxName = d.name.length;
					}
					const s = d.deviceClass ? (d.deviceClass + ' (' + d.productVersion + ')') : '';
					if (s.length > maxDesc) {
						maxDesc = s.length;
					}
				});
			} else {
				Object.keys(info.devices).forEach(function (sdk) {
					info.devices[sdk].forEach(function (sim) {
						options[sdk] || (options[sdk] = []);
						options[sdk].push(sim);
						if (sim.name.length > maxName) {
							maxName = sim.name.length;
						}
					});
				});
			}

			const params = {
				formatters: {},
				default: '1', // just default to the first one, whatever that will be
				autoSelectOne: true,
				margin: '',
				optionLabel: 'name',
				optionValue: 'udid',
				numbered: true,
				relistOnError: true,
				complete: true,
				suggest: true,
				options: options
			};

			if (this.cli.argv.target === 'device') {
				// device specific settings
				params.title = __('Which device do you want to install your app on?');
				params.promptLabel = __('Select an device by number or name');
				params.formatters.option = function (opt, idx, num) {
					return '  ' + num + [
						appc.string.rpad(opt.name, info.maxName).cyan,
						appc.string.rpad(opt.deviceClass ? opt.deviceClass + ' (' + opt.productVersion + ')' : '', maxDesc),
						opt.udid.grey
					].join('  ');
				};
			} else if (this.cli.argv.target === 'simulator') {
				// simulator specific settings
				params.title = __('Which simulator do you want to launch your app in?');
				params.promptLabel = __('Select an simulator by number or name');
				params.formatters.option = function (opt, idx, num) {
					return '  ' + num + appc.string.rpad(opt.name, maxName).cyan + '  ' + opt.udid.grey;
				};
			}

			callback(fields.select(params));
		}.bind(this),
		required: true,
		validate: function (udid, callback) {
			// this function is called if they specify a --device-id and we need to check that it is valid
			if (typeof udid === 'boolean') {
				return callback(true);
			}

			if (this.cli.argv['build-only']) {
				return callback();
			}

			if ((this.cli.argv.target === 'device' || this.cli.argv.target === 'dist-adhoc') && udid === 'all') {
				// we let 'all' slide by
				return callback(null, udid);
			}

			const info = this.getDeviceInfo();
			if (info.udids[udid]) {
				callback(null, udid);
			} else {
				callback(new Error(this.cli.argv.target === 'device' ? __('Invalid iOS device "%s"', udid) : __('Invalid iOS simulator "%s"', udid)));
			}
		}.bind(this),
		verifyIfRequired: function (callback) {
			// this function is called by the CLI when the option is not specified and is required (i.e. missing).
			// the CLI will then double check that this option is still required by calling this function
			if (this.cli.argv['build-only']) {
				// not required if we're build only
				return callback();
			}

			if (this.cli.argv['device-id'] === undefined && this.config.get('ios.autoSelectDevice', true) && (this.cli.argv.target === 'simulator' || this.cli.argv.target === 'device')) {
				// --device-id not specified and we're not prompting, so pick a device later
				callback();
			} else {
				// yup, still required
				callback(true);
			}
		}.bind(this)
	};
};

/**
 * Defines the --developer-name option.
 *
 * @param {Integer} order - The order to apply to this option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionDeveloperName = function configOptionDeveloperName(order) {
	const cli = this.cli,
		iosInfo = this.iosInfo,
		developerCertLookup = {};

	Object.keys(iosInfo.certs.keychains).forEach(function (keychain) {
		(iosInfo.certs.keychains[keychain].developer || []).forEach(function (d) {
			if (!d.invalid) {
				developerCertLookup[d.name.toLowerCase()] = d.name;
			}
		});
	});

	return {
		abbr: 'V',
		default: (process.env.EXPANDED_CODE_SIGN_IDENTITY_NAME && /iPhone Developer/.test(process.env.EXPANDED_CODE_SIGN_IDENTITY_NAME)
			&& process.env.EXPANDED_CODE_SIGN_IDENTITY_NAME.replace(/^iPhone Developer(?:: )?/, '')) || this.config.get(
			'ios.developerName'),
		desc: __('the iOS Developer Certificate to use; required when target is %s', 'device'.cyan),
		hint: 'name',
		order: order,
		prompt: function (callback) {
			var developerCerts = {},
				maxDevCertLen = 0;

			Object.keys(iosInfo.certs.keychains).forEach(function (keychain) {
				(iosInfo.certs.keychains[keychain].developer || []).forEach(function (d) {
					if (!d.invalid) {
						Array.isArray(developerCerts[keychain]) || (developerCerts[keychain] = []);
						developerCerts[keychain].push(d);
						maxDevCertLen = Math.max(d.name.length, maxDevCertLen);
					}
				});
			});

			// sort the certs
			Object.keys(developerCerts).forEach(function (keychain) {
				developerCerts[keychain] = developerCerts[keychain].sort(function (a, b) {
					return a.name.toLowerCase().localeCompare(b.name.toLowerCase());
				});
			});

			callback(fields.select({
				title: __('Which developer certificate would you like to use?'),
				promptLabel: __('Select a certificate by number or name'),
				formatters: {
					option: function (opt, idx, num) {
						var expires = moment(opt.after),
							day = expires.format('D'),
							hour = expires.format('h');
						return '  ' + num + appc.string.rpad(opt.name, maxDevCertLen + 1).cyan
							+ (opt.after ? (' (' + __('expires %s', expires.format('MMM') + ' '
							+ (day.length === 1 ? ' ' : '') + day + ', ' + expires.format('YYYY') + ' '
							+ (hour.length === 1 ? ' ' : '') + hour + ':' + expires.format('mm:ss a'))
							+ ')').grey : '');
					}
				},
				margin: '',
				optionLabel: 'name',
				optionValue: 'name',
				numbered: true,
				relistOnError: true,
				complete: true,
				suggest: false,
				options: developerCerts
			}));
		},
		validate: function (value, callback) {
			if (typeof value === 'boolean') {
				return callback(true);
			}
			if (cli.argv.target !== 'device') {
				return callback(null, value);
			}
			if (value) {
				const v = developerCertLookup[value.toLowerCase()];
				if (v) {
					return callback(null, v);
				}
			}
			callback(new Error(__('Invalid developer certificate "%s"', value)));
		}
	};
};

/**
 * Defines the --distribution-name option.
 *
 * @param {Integer} order - The order to apply to this option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionDistributionName = function configOptionDistributionName(order) {
	const cli = this.cli,
		iosInfo = this.iosInfo,
		distributionCertLookup = {};

	Object.keys(iosInfo.certs.keychains).forEach(function (keychain) {
		(iosInfo.certs.keychains[keychain].distribution || []).forEach(function (d) {
			if (!d.invalid) {
				distributionCertLookup[d.name.toLowerCase()] = d.name;
			}
		});
	});

	return {
		abbr: 'R',
		default: process.env.EXPANDED_CODE_SIGN_IDENTITY_NAME && /iPhone Distribution/.test(process.env.EXPANDED_CODE_SIGN_IDENTITY_NAME)
			&& process.env.EXPANDED_CODE_SIGN_IDENTITY_NAME.replace(/^iPhone Distribution(?:: )?/, '') || this.config.get(
			'ios.distributionName'),
		desc: __('the iOS Distribution Certificate to use; required when target is %s or %s', 'dist-appstore'.cyan, 'dist-adhoc'.cyan),
		hint: 'name',
		order: order,
		prompt: function (callback) {
			const distributionCerts = {};
			let maxDistCertLen = 0;

			Object.keys(iosInfo.certs.keychains).forEach(function (keychain) {
				(iosInfo.certs.keychains[keychain].distribution || []).forEach(function (d) {
					if (!d.invalid) {
						Array.isArray(distributionCerts[keychain]) || (distributionCerts[keychain] = []);
						distributionCerts[keychain].push(d);
						maxDistCertLen = Math.max(d.name.length, maxDistCertLen);
					}
				});
			});

			// sort the certs
			Object.keys(distributionCerts).forEach(function (keychain) {
				distributionCerts[keychain] = distributionCerts[keychain].sort(function (a, b) {
					return a.name.toLowerCase().localeCompare(b.name.toLowerCase());
				});
			});

			callback(fields.select({
				title: __('Which distribution certificate would you like to use?'),
				promptLabel: __('Select a certificate by number or name'),
				formatters: {
					option: function (opt, idx, num) {
						var expires = moment(opt.after),
							day = expires.format('D'),
							hour = expires.format('h');
						return '  ' + num + appc.string.rpad(opt.name, maxDistCertLen + 1).cyan
							+ (opt.after ? (' (' + __('expires %s', expires.format('MMM') + ' '
							+ (day.length === 1 ? ' ' : '') + day + ', ' + expires.format('YYYY') + ' '
							+ (hour.length === 1 ? ' ' : '') + hour + ':' + expires.format('mm:ss a'))
							+ ')').grey : '');
					}
				},
				margin: '',
				optionLabel: 'name',
				optionValue: 'name',
				numbered: true,
				relistOnError: true,
				complete: true,
				suggest: false,
				options: distributionCerts
			}));
		},
		validate: function (value, callback) {
			if (typeof value === 'boolean') {
				return callback(true);
			}
			if (cli.argv.target !== 'dist-appstore' && cli.argv.target !== 'dist-adhoc') {
				return callback(null, value);
			}
			if (value) {
				const v = distributionCertLookup[value.toLowerCase()];
				if (v) {
					return callback(null, v);
				}
			}
			callback(new Error(__('Invalid distribution certificate "%s"', value)));
		}
	};
};

/**
 * Defines the --device-family option.
 *
 * @param {Integer} order - The order to apply to this option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionDeviceFamily = function configOptionDeviceFamily(order) {
	return {
		abbr: 'F',
		desc: __('the device family to build for'),
		order: order,
		values: Object.keys(this.deviceFamilies)
	};
};

/**
 * Defines the --ios-version option.
 *
 * @param {Integer} order - The order to apply to this option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptioniOSVersion = function configOptioniOSVersion(order) {
	const _t = this,
		logger = this.logger;

	return {
		abbr: 'I',
		callback: function (value) {
			try {
				if (value && _t.iosAllSdkVersions.indexOf(value) !== -1 && version.lt(value, _t.minSupportedIosSdk)) {
					logger.banner();
					logger.error(__('The specified iOS SDK version "%s" is not supported by Titanium %s', value, _t.titaniumSdkVersion) + '\n');
					if (_t.iosSdkVersions.length) {
						logger.log(__('Available supported iOS SDKs:'));
						_t.iosSdkVersions.forEach(function (ver) {
							logger.log('   ' + ver.cyan);
						});
						logger.log();
					}
					process.exit(1);
				}
			} catch (e) {
				// squelch and let the cli detect the bad version
			}
		},
		desc: __('iOS SDK version to build with'),
		order: order,
		prompt: function (callback) {
			callback(fields.select({
				title: __('Which iOS SDK version would you like to build with?'),
				promptLabel: __('Select an iOS SDK version by number or name'),
				margin: '',
				numbered: true,
				relistOnError: true,
				complete: true,
				suggest: false,
				options: _t.iosSdkVersions
			}));
		},
		values: _t.iosSdkVersions
	};
};

/**
 * Defines the --keychain option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionKeychain = function configOptionKeychain() {
	return {
		abbr: 'K',
		desc: __('path to the distribution keychain to use instead of the system default; only used when target is %s, %s, or %s', 'device'.cyan, 'dist-appstore'.cyan, 'dist-adhoc'.cyan),
		hideValues: true,
		validate: function (value, callback) {
			value && typeof value !== 'string' && (value = null);
			if (value && !fs.existsSync(value)) {
				callback(new Error(__('Unable to find keychain: %s', value)));
			} else {
				callback(null, value);
			}
		}
	};
};

/**
 * Defines the --launch-bundle-id option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionLaunchBundleId = function configOptionLaunchBundleId() {
	return {
		desc: __('after installing the app, launch an different app instead; only used when target is %s', 'simulator'.cyan),
		hint: __('id')
	};
};

/**
 * Defines the --output-dir option.
 *
 * @param {Integer} order - The order to apply to this option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionOutputDir = function configOptionOutputDir(order) {
	const _t = this,
		cli = this.cli;

	function validate(outputDir, callback) {
		callback(outputDir || !_t.conf.options['output-dir'].required ? null : new Error(__('Invalid output directory')), outputDir);
	}

	return {
		abbr: 'O',
		desc: __('the output directory when using %s or %s', 'dist-appstore'.cyan, 'dist-adhoc'.cyan),
		hint: 'dir',
		order: order,
		prompt: function (callback) {
			callback(fields.file({
				promptLabel: __('Where would you like the output IPA file saved?'),
				default: cli.argv['project-dir'] && appc.fs.resolvePath(cli.argv['project-dir'], 'dist'),
				complete: true,
				showHidden: true,
				ignoreDirs: _t.ignoreDirs,
				ignoreFiles: /.*/,
				validate: validate
			}));
		},
		validate: validate
	};
};

/**
 * Defines the --pp-uuid option.
 *
 * @param {Integer} order - The order to apply to this option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionPPuuid = function configOptionPPuuid(order) {
	const _t = this,
		cli = this.cli,
		iosInfo = this.iosInfo,
		logger = this.logger;

	return {
		abbr: 'P',
		desc: __('the provisioning profile uuid; required when target is %s, %s, or %s', 'device'.cyan, 'dist-appstore'.cyan, 'dist-adhoc'.cyan),
		hint: 'uuid',
		order: order,
		prompt: function (callback) {
			const provisioningProfiles = {};
			const appId = cli.tiapp.id;
			const target = cli.argv.target;
			let maxAppId = 0;
			let pp;

			function prep(a, cert) {
				return a.filter(function (p) {
					if (!p.expired && !p.managed && (!cert || p.certs.indexOf(cert) !== -1)) {
						const re = new RegExp(p.appId.replace(/\./g, '\\.').replace(/\*/g, '.*')); // eslint-disable-line security/detect-non-literal-regexp
						if (re.test(appId)) {
							let label = p.name;
							if (label.indexOf(p.appId) === -1) {
								label += ': ' + p.appId;
							}
							p.label = label;
							maxAppId = Math.max(p.label.length, maxAppId);
							return true;
						}
					}
					return false;
				}).sort(function (a, b) {
					return a.label.toLowerCase().localeCompare(b.label.toLowerCase());
				});
			}

			let cert;
			if (target === 'device') {
				cert = _t.findCertificate(cli.argv['developer-name'], 'developer');
			} else {
				cert = _t.findCertificate(cli.argv['distribution-name'], 'distribution');
			}

			if (target === 'device') {
				if (iosInfo.provisioning.development.length) {
					pp = prep(iosInfo.provisioning.development, cert.pem.replace(pemCertRegExp, ''));
					if (pp.length) {
						provisioningProfiles[__('Available Development UUIDs:')] = pp;
					} else {
						if (cert) {
							logger.error(__('Unable to find any non-expired development provisioning profiles that match the app id "%s" and the "%s" certificate.', appId, cert.name) + '\n');
						} else {
							logger.error(__('Unable to find any non-expired development provisioning profiles that match the app id "%s".', appId) + '\n');
						}
						logger.log(__('You will need to log in to %s with your Apple Developer account, then create, download, and install a profile.',
							'http://appcelerator.com/ios-dev-certs'.cyan) + '\n');
						process.exit(1);
					}
				} else {
					logger.error(__('Unable to find any development provisioning profiles') + '\n');
					logger.log(__('You will need to log in to %s with your Apple Developer account, then create, download, and install a profile.',
						'http://appcelerator.com/ios-dev-certs'.cyan) + '\n');
					process.exit(1);
				}

			} else if (target === 'dist-appstore') {
				if (iosInfo.provisioning.distribution.length) {
					pp = prep(iosInfo.provisioning.distribution, cert.pem.replace(pemCertRegExp, ''));
					if (pp.length) {
						provisioningProfiles[__('Available App Store Distribution UUIDs:')] = pp;
					} else {
						logger.error(__('Unable to find any non-expired App Store distribution provisioning profiles that match the app id "%s".', appId) + '\n');
						logger.log(__('You will need to log in to %s with your Apple Developer account, then create, download, and install a profile.',
							'http://appcelerator.com/ios-dist-certs'.cyan) + '\n');
						process.exit(1);
					}
				} else {
					logger.error(__('Unable to find any App Store distribution provisioning profiles'));
					logger.log(__('You will need to log in to %s with your Apple Developer account, then create, download, and install a profile.',
						'http://appcelerator.com/ios-dist-certs'.cyan) + '\n');
					process.exit(1);
				}

			} else if (target === 'dist-adhoc') {
				if (iosInfo.provisioning.adhoc.length || iosInfo.provisioning.enterprise.length) {
					pp = prep(iosInfo.provisioning.adhoc, cert.pem.replace(pemCertRegExp, ''));
					let valid = pp.length;
					if (pp.length) {
						provisioningProfiles[__('Available Ad Hoc UUIDs:')] = pp;
					}

					pp = prep(iosInfo.provisioning.enterprise);
					valid += pp.length;
					if (pp.length) {
						provisioningProfiles[__('Available Enterprise Ad Hoc UUIDs:')] = pp;
					}

					if (!valid) {
						logger.error(__('Unable to find any non-expired Ad Hoc or Enterprise Ad Hoc provisioning profiles that match the app id "%s".', appId) + '\n');
						logger.log(__('You will need to log in to %s with your Apple Developer account, then create, download, and install a profile.',
							'http://appcelerator.com/ios-dist-certs'.cyan) + '\n');
						process.exit(1);
					}
				} else {
					logger.error(__('Unable to find any Ad Hoc or Enterprise Ad Hoc provisioning profiles'));
					logger.log(__('You will need to log in to %s with your Apple Developer account, then create, download, and install a profile.',
						'http://appcelerator.com/ios-dist-certs'.cyan) + '\n');
					process.exit(1);
				}
			}

			callback(fields.select({
				title: __('Which provisioning profile would you like to use?'),
				promptLabel: __('Select a provisioning profile UUID by number or name'),
				formatters: {
					option: function (opt, idx, num) {
						const expires = opt.expirationDate && moment(opt.expirationDate),
							day = expires && expires.format('D'),
							hour = expires && expires.format('h');
						return '  ' + num + String(opt.uuid).cyan + ' '
							+ appc.string.rpad(opt.label, maxAppId + 1)
							+ (opt.expirationDate ? (' (' + __('expires %s', expires.format('MMM') + ' '
							+ (day.length === 1 ? ' ' : '') + day + ', ' + expires.format('YYYY') + ' '
							+ (hour.length === 1 ? ' ' : '') + hour + ':' + expires.format('mm:ss a'))
							+ ')').grey : '');
					}
				},
				margin: '',
				optionLabel: 'name',
				optionValue: 'uuid',
				numbered: true,
				relistOnError: true,
				complete: true,
				suggest: false,
				options: provisioningProfiles
			}));
		},
		validate: function (value, callback) {
			const target = cli.argv.target;

			if (target === 'simulator') {
				return callback(null, value);
			}

			if (value) {
				const p = _t.provisioningProfileLookup[value.toLowerCase()];
				if (!p) {
					return callback(new Error(__('Invalid provisioning profile UUID "%s"', value)));
				}
				if (p.managed) {
					return callback(new Error(__('Specified provisioning profile UUID "%s" is managed and not supported', value)));
				}
				if (p.expired) {
					return callback(new Error(__('Specified provisioning profile UUID "%s" is expired', value)));
				}

				let cert;
				if (target === 'device') {
					cert = _t.findCertificate(cli.argv['developer-name'], 'developer');
				} else {
					cert = _t.findCertificate(cli.argv['distribution-name'], 'distribution');
				}

				if (cert && p.certs.indexOf(cert.pem.replace(pemCertRegExp, '')) === -1) {
					return callback(new Error(__('Specified provisioning profile UUID "%s" does not include the "%s" certificate', value, cert.name)));
				}

				return callback(null, p.uuid);
			}

			callback(true);
		}
	};
};

/**
 * Defines the --target option.
 *
 * @param {Integer} order - The order to apply to this option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionTarget = function configOptionTarget(order) {
	const _t = this,
		iosInfo = this.iosInfo;

	return {
		abbr: 'T',
		callback: function (value) {
			const cli = _t.cli;
			// if we're building from Xcode, no need to check certs and provisioning profiles
			if (cli.argv.xcode) {
				_t.conf.options['developer-name'].required = false;
				_t.conf.options['device-id'].required = false;
				_t.conf.options['distribution-name'].required = false;
				_t.conf.options['pp-uuid'].required = false;
				// return;
			}
			if (value !== 'simulator') {
				_t.assertIssue(iosInfo.issues, 'IOS_NO_KEYCHAINS_FOUND');
				_t.assertIssue(iosInfo.issues, 'IOS_NO_WWDR_CERT_FOUND');
			}

			// as soon as we know the target, toggle required options for validation
			switch (value) {
				case 'device':
					_t.assertIssue(iosInfo.issues, 'IOS_NO_VALID_DEV_CERTS_FOUND');
					_t.assertIssue(iosInfo.issues, 'IOS_NO_VALID_DEVELOPMENT_PROVISIONING_PROFILES');
					iosInfo.provisioning.development.forEach(function (p) {
						_t.provisioningProfileLookup[p.uuid.toLowerCase()] = p;
					});
					_t.conf.options['developer-name'].required = true;
					_t.conf.options['pp-uuid'].required = !cli.argv.xcode;
					break;

				case 'dist-adhoc':
					_t.assertIssue(iosInfo.issues, 'IOS_NO_VALID_DIST_CERTS_FOUND');
					// TODO: assert there is at least one distribution or adhoc provisioning profile

					if (!cli.argv.xcode) {
						_t.conf.options['output-dir'].required = true;
						_t.conf.options['deploy-type'].values = [ 'production' ];
						_t.conf.options['device-id'].required = false;
						_t.conf.options['distribution-name'].required = true;
						_t.conf.options['pp-uuid'].required = true;
					}

					iosInfo.provisioning.adhoc.forEach(function (p) {
						_t.provisioningProfileLookup[p.uuid.toLowerCase()] = p;
					});
					iosInfo.provisioning.enterprise.forEach(function (p) {
						_t.provisioningProfileLookup[p.uuid.toLowerCase()] = p;
					});

					break;

				case 'dist-appstore':
					_t.assertIssue(iosInfo.issues, 'IOS_NO_VALID_DIST_CERTS_FOUND');

					if (value === 'dist-appstore') {
						_t.conf.options['deploy-type'].values = [ 'production', 'test' ];
					}
					_t.conf.options['device-id'].required = false;
					_t.conf.options['distribution-name'].required = true;
					_t.conf.options['pp-uuid'].required = true;

					// build lookup maps
					iosInfo.provisioning.distribution.forEach(function (p) {
						_t.provisioningProfileLookup[p.uuid.toLowerCase()] = p;
					});
			}
		},
		default: 'simulator',
		desc: __('the target to build for'),
		order: order,
		required: true,
		values: this.targets
	};
};

/**
 * Defines the --watch-app-name option.
 *
 * @param {Integer} order - The order to apply to this option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionWatchAppName = function configOptionWatchAppName(order) {
	return {
		desc: __('when building an app with multiple watch app, the name of the watch app to launch; only used when target is %s', 'simulator'.cyan),
		hint: __('name'),
		order: order
	};
};

/**
 * Defines the --watch-device-id option.
 *
 * @param {Integer} order - The order to apply to this option.
 *
 * @returns {Object}
 */
iOSBuilder.prototype.configOptionWatchDeviceId = function configOptionWatchDeviceId(order) {
	const cli = this.cli,
		iosSims = this.iosInfo.simulators.ios,
		watchSims = this.iosInfo.simulators.watchos,
		xcodes = this.iosInfo.xcode;

	return {
		abbr: 'W',
		desc: __('the watch simulator UDID to launch when building an app with a watch app; only used when target is %s', 'simulator'.cyan),
		hint: __('udid'),
		order: order,
		prompt: function (callback) {
			if (cli.argv.target !== 'simulator') {
				return callback();
			}

			const options = {},
				iosSdkVersion = cli.argv['ios-version'];
			let iphoneSim = null,
				maxName = 0;

			if (cli.argv['device-id']) {
				Object.keys(iosSims).some(function (ver) {
					return iosSims[ver].some(function (sim) {
						if (sim.udid === cli.argv['device-id']) {
							iphoneSim = sim;
							return true;
						}
						return false;
					});
				});
			}

			Object.keys(watchSims).forEach(function (sdk) {
				watchSims[sdk].forEach(function (sim) {
					// check iOS SDK compatibility
					if ((!iosSdkVersion
							|| Object.keys(sim.supportsXcode).some(function (xcodeId) {
								if (sim.supportsXcode[xcodeId] && xcodes[xcodeId].sdks.indexOf(iosSdkVersion) !== -1) {
									return true;
								}
								return false;
							})
					)
						&& (!iphoneSim || iphoneSim.watchCompanion[sim.udid])
					) {
						options[sdk] || (options[sdk] = []);
						options[sdk].push(sim);
						if (sim.name.length > maxName) {
							maxName = sim.name.length;
						}
					}
				});
			});

			const params = {
				formatters: {},
				default: '1', // just default to the first one, whatever that will be
				autoSelectOne: true,
				margin: '',
				optionLabel: 'name',
				optionValue: 'udid',
				numbered: true,
				relistOnError: true,
				complete: true,
				suggest: true,
				options: options
			};

			// simulator specific settings
			params.title = __('Which simulator do you want to launch your app in?');
			params.promptLabel = __('Select an simulator by number or name');
			params.formatters.option = function (opt, idx, num) {
				return '  ' + num + appc.string.rpad(opt.name, maxName).cyan + '  ' + opt.udid.grey;
			};

			callback(fields.select(params));
		},
		validate: function (value, callback) {
			if (!cli.argv['build-only'] && cli.argv.target === 'simulator') {
				if (!value || value === true) {
					return callback(true);
				} else if (!Object.keys(watchSims).some(function (ver) { return watchSims[ver].some(function (sim) { return sim.udid === value; }); })) { // eslint-disable-line max-statements-per-line
					return callback(new Error(__('Invalid Watch Simulator UDID "%s"', value)));
				}
			}
			callback(null, value);
		}
	};
};

/**
 * Validates and initializes settings from the tiapp.xml. This function can be
 * invoked when trying to build the list of iOS Simulators for --device-id
 * prompting, otherwise it'll be called from the iOS build's validate().
 *
 * It's critical that this function ONLY logs output in the event of a fatal
 * error.
 */
iOSBuilder.prototype.initTiappSettings = function initTiappSettings() {
	if (this._tiappSettingsInitialized) {
		return;
	}

	const cli = this.cli;
	const logger = this.logger;

	// redundant, but we need it earlier than validate()
	this.projectDir = cli.argv['project-dir'];

	const tiapp = cli.tiapp;
	tiapp.ios || (tiapp.ios = {});
	tiapp.ios.capabilities || (tiapp.ios.capabilities = {});
	Array.isArray(tiapp.ios.extensions) || (tiapp.ios.extensions = []);

	// the existance of an app id was already checked in cli/commands/build.js,
	// but now we need to check for underscores
	if (!this.config.get('app.skipAppIdValidation') && !tiapp.properties['ti.skipAppIdValidation']) {
		if (!/^([a-zA-Z_]{1}[a-zA-Z0-9_-]*(\.[a-zA-Z0-9_-]*)*)$/.test(tiapp.id)) {
			logger.error(__('tiapp.xml contains an invalid app id "%s"', tiapp.id));
			logger.error(__('The app id must consist only of letters, numbers, dashes, and underscores.'));
			logger.error(__('Note: iOS does not allow underscores.'));
			logger.error(__('The first character must be a letter or underscore.'));
			logger.error(__('Usually the app id is your company\'s reversed Internet domain name. (i.e. com.example.myapp)') + '\n');
			process.exit(1);
		}

		if (tiapp.id.indexOf('_') !== -1) {
			logger.error(__('tiapp.xml contains an invalid app id "%s"', tiapp.id));
			logger.error(__('The app id must consist of letters, numbers, and dashes.'));
			logger.error(__('The first character must be a letter.'));
			logger.error(__('Usually the app id is your company\'s reversed Internet domain name. (i.e. com.example.myapp)') + '\n');
			process.exit(1);
		}
	}

	// make sure the app doesn't have any blacklisted directories or files in the Resources directory and warn about graylisted names
	if (this.blacklistDirectories.indexOf(tiapp.name.toLowerCase()) !== -1 || tiapp.name.toLowerCase() === 'frameworks') {
		logger.error(__('The app name conflicts with a reserved file.'));
		logger.error(__('You must change the name of the app in the tiapp.xml.') + '\n');
		process.exit(1);
	}

	// make sure we have an app icon
	if (!tiapp.icon || ![ 'Resources', 'Resources/iphone', 'Resources/ios' ].some(function (p) { return fs.existsSync(this.projectDir, p, tiapp.icon); }, this)) { // eslint-disable-line max-statements-per-line
		tiapp.icon = 'appicon.png';
	}

	if (!/\.png$/.test(tiapp.icon)) {
		logger.error(__('Application icon must be a PNG formatted image.') + '\n');
		process.exit(1);
	}

	// validate the log server port
	const logServerPort = tiapp.ios['log-server-port'];
	if (!/^dist-(appstore|adhoc)$/.test(this.target) && logServerPort && (typeof logServerPort !== 'number' || logServerPort < 1024 || logServerPort > 65535)) {
		logger.error(__('Invalid <log-server-port> found in the tiapp.xml'));
		logger.error(__('Port must be a positive integer between 1024 and 65535') + '\n');
		process.exit(1);
	}

	// process min ios version
	this.minIosVersion = tiapp.ios['min-ios-ver'] && appc.version.gt(tiapp.ios['min-ios-ver'], this.packageJson.minIosVersion) ? tiapp.ios['min-ios-ver'] : this.packageJson.minIosVersion;
	if (this.hasWatchAppV2orNewer && appc.version.lt(this.minIosVersion, '9.0')) {
		this.minIosVersion = '9.0';
	} else if (tiapp.ios['enable-launch-screen-storyboard'] && appc.version.lt(this.minIosVersion, '8.0')) {
		this.minIosVersion = '8.0';
	}

	// process device family
	const deploymentTargets = tiapp['deployment-targets'];
	this.deviceFamily = cli.argv['device-family'];
	if (!this.deviceFamily && deploymentTargets) {
		if (deploymentTargets.ipad && (!deploymentTargets.iphone || cli.argv.$originalPlatform === 'ipad')) {
			this.deviceFamily = 'ipad';
		} else if (deploymentTargets.iphone && !deploymentTargets.ipad) {
			this.deviceFamily = 'iphone';
		} else {
			this.deviceFamily = 'universal';
		}
	}

	if (cli.argv.$originalPlatform === 'ipad') {
		logger.warn(__('--platform ipad has been deprecated and will be removed in Titanium SDK 7.0.0'));
		logger.warn(__('See %s for more details', 'https://jira.appcelerator.org/browse/TIMOB-24228'));
	}

	// init the extensions
	tiapp.ios.extensions.forEach(function (ext) {
		if (!ext.projectPath) {
			logger.error(__('iOS extensions must have a "projectPath" attribute that points to a folder containing an Xcode project.') + '\n');
			process.exit(1);
		}

		// projectPath could be either the path to a project directory or the actual .xcodeproj
		ext.origProjectPath = ext.projectPath;
		ext.projectPath = ext.projectPath[0] === '/' ? appc.fs.resolvePath(ext.projectPath) : appc.fs.resolvePath(this.projectDir, ext.projectPath);

		const xcodeprojRegExp = /\.xcodeproj$/;
		if (!xcodeprojRegExp.test(ext.projectPath)) {
			// maybe we're the parent dir?
			ext.projectPath = path.join(ext.projectPath, path.basename(ext.projectPath) + '.xcodeproj');
		}

		const projectName = path.basename(ext.projectPath.replace(xcodeprojRegExp, ''));

		if (!fs.existsSync(ext.projectPath)) {
			logger.error(__('iOS extension "%s" Xcode project not found: %s', projectName, ext.projectPath) + '\n');
			process.exit(1);
		}

		const projFile = path.join(ext.projectPath, 'project.pbxproj');
		if (!fs.existsSync(projFile)) {
			logger.error(__('iOS extension "%s" project missing Xcode project file: %s', projectName, projFile) + '\n');
			process.exit(1);
		}

		if (!Array.isArray(ext.targets) || !ext.targets.length) {
			// logger.warn(__('iOS extension "%s" has no targets, skipping.', projectName));
			return;
		}

		const proj = xcode.project(path.join(ext.projectPath, 'project.pbxproj')).parseSync();

		// flag each target we care about
		let tiappTargets = {};
		ext.targets.forEach(function (target) {
			tiappTargets[target.name] = target;
		});

		// augment the ext entry with some extra details that we'll use later when constructing the Xcode project
		ext.objs        = proj.hash.project.objects;
		ext.project     = ext.objs.PBXProject[proj.hash.project.rootObject];
		ext.projectName = path.basename(ext.projectPath).replace(/\.xcodeproj$/, '');
		ext.basePath    = path.dirname(ext.projectPath);
		ext.relPath     = 'extensions/' + path.basename(path.dirname(ext.projectPath));
		ext.targetInfo  = {};

		const globalCfg = ext.objs.XCConfigurationList[ext.project.buildConfigurationList];
		const globalCfgId = globalCfg.buildConfigurations
			.filter(function (c) { return c.comment.toLowerCase() === (globalCfg.defaultConfigurationName ? globalCfg.defaultConfigurationName.toLowerCase() : 'release'); })
			.map(function (c) { return c.value; })
			.shift();
		const globalBuildSettings = ext.objs.XCBuildConfiguration[globalCfgId].buildSettings;

		// check that the PP UUID is correct
		let pps = [];
		if (cli.argv.target === 'device') {
			pps = this.iosInfo.provisioning.development;
		} else if (cli.argv.target === 'dist-appstore') {
			pps = this.iosInfo.provisioning.distribution;
		} else if (cli.argv.target === 'dist-adhoc') {
			pps = []
				.concat(this.iosInfo.provisioning.adhoc, this.iosInfo.provisioning.enterprise)
				.filter(function (p) {
					return p;
				});
		}

		function getPPbyUUID(ppuuid) {
			return pps
				.filter(function (p) {
					if (!p.expired && !p.managed && p.uuid === ppuuid) {
						return true;
					}
					return false;
				})
				.shift();
		}

		// check that the PP UUID is correct
		if (cli.argv.target === 'device') {
			pps = this.iosInfo.provisioning.development;
		} else if (cli.argv.target === 'dist-appstore') {
			pps = this.iosInfo.provisioning.distribution;
		} else if (cli.argv.target === 'dist-adhoc') {
			pps = [].concat(this.iosInfo.provisioning.adhoc, this.iosInfo.provisioning.enterprise).filter(function (p) {
				return p;
			});
		}

		// find our targets
		ext.project.targets.forEach(function (t) {
			const targetName = t.comment;

			if (!tiappTargets[targetName]) {
				// not a target we care about
				return;
			}

			// we have found our target!

			const nativeTarget = ext.objs.PBXNativeTarget[t.value];
			const cfg = ext.objs.XCConfigurationList[nativeTarget.buildConfigurationList];
			const cfgid = cfg.buildConfigurations
				.filter(function (c) { return c.comment.toLowerCase() === (cfg.defaultConfigurationName ? cfg.defaultConfigurationName.toLowerCase() : 'release'); })
				.map(function (c) { return c.value; })
				.shift();
			const buildSettings = ext.objs.XCBuildConfiguration[cfgid].buildSettings;
			const productType = nativeTarget.productType.replace(/^"/, '').replace(/"$/, '');
			const containsExtension = productType.indexOf('extension') !== -1;
			const containsWatchApp = productType.indexOf('watchapp') !== -1;
			const containsWatchKit = productType.indexOf('watchkit') !== -1;

			const targetInfo = ext.targetInfo[targetName] = {
				productType:           productType,
				isWatchAppV1Extension: productType === 'com.apple.product-type.watchkit-extension',
				isExtension:           containsExtension && (!containsWatchKit || productType === 'com.apple.product-type.watchkit-extension'),
				isWatchAppV1:          productType === 'com.apple.product-type.application.watchapp',
				isWatchAppV2orNewer:   containsWatchApp && productType !== 'com.apple.product-type.application.watchapp',
				sdkRoot:               productType === 'com.apple.product-type.application.watchapp' ? 'watchos' : (buildSettings.SDKROOT || globalBuildSettings.SDKROOT || null),
				watchOS:               productType === 'com.apple.product-type.application.watchapp' ? '1.0' : (buildSettings.WATCHOS_DEPLOYMENT_TARGET || globalBuildSettings.WATCHOS_DEPLOYMENT_TARGET || null),
				infoPlist:             null
			};

			if (targetInfo.isWatchAppV1Extension || targetInfo.isWatchAppV1) {
				logger.error(__('WatchOS1 app detected.'));
				logger.error(__('Titanium %s does not support WatchOS1 apps.', this.titaniumSdkVersion) + '\n');
				process.exit(1);
			}

			// we need to get a min watch os version so that we can intelligently pick an appropriate watch simulator
			if (targetInfo.isWatchAppV2orNewer
					&& (!cli.argv['watch-app-name'] || targetName === cli.argv['watch-app-name'])
					&& (!this.watchMinOSVersion || appc.version.lt(targetInfo.watchOS, this.watchMinOSVersion))) {
				this.watchMinOSVersion = targetInfo.watchOS;
			}

			if (targetInfo.isWatchAppV2orNewer) {
				this.hasWatchAppV2orNewer = true;
			}

			// find this target's Info.plist
			ext.objs.PBXGroup[ext.project.mainGroup].children.some(function (child) {
				if (child.comment !== targetName) {
					return false;
				}

				(function walkGroup(uuid, basePath) {
					if (ext.objs.PBXGroup[uuid].path) {
						basePath = path.join(basePath, ext.objs.PBXGroup[uuid].path.replace(/^"/, '').replace(/"$/, ''));
					}

					ext.objs.PBXGroup[uuid].children.some(function (child) {
						if (ext.objs.PBXGroup[child.value]) {
							return walkGroup(child.value, basePath);
						} else if (ext.objs.PBXFileReference[child.value] && child.comment === 'Info.plist') {
							const infoPlistFile = path.join(basePath, 'Info.plist');
							if (!fs.existsSync(infoPlistFile)) {
								logger.error(__('Unable to find "%s" iOS extension\'s "%s" target\'s Info.plist: %s', ext.projectName, targetName, infoPlistFile) + '\n');
								process.exit(1);
							}

							const plist = ext.targetInfo[targetName].infoPlist = ioslib.utilities.readPlist(infoPlistFile);
							if (!plist) {
								logger.error(__('Failed to parse "%s" iOS extension\'s "%s" target\'s Info.plist: %s', ext.projectName, targetName, infoPlistFile) + '\n');
								process.exit(1);
							}

							if (plist.WKWatchKitApp) {
								const CFBundleIdentifier = plist.CFBundleIdentifier.replace('$(PRODUCT_BUNDLE_IDENTIFIER)', buildSettings.PRODUCT_BUNDLE_IDENTIFIER);
								if (CFBundleIdentifier.indexOf(tiapp.id) !== 0) {
									logger.error(__('iOS extension "%s" WatchKit App bundle identifier is "%s", but must be prefixed with "%s".', ext.projectName, plist.CFBundleIdentifier, tiapp.id) + '\n');
									process.exit(1);
								}

								if (CFBundleIdentifier.toLowerCase() === tiapp.id.toLowerCase()) {
									logger.error(__('iOS extension "%s" WatchKit App bundle identifier must be different from the Titanium app\'s id "%s".', ext.projectName, tiapp.id) + '\n');
									process.exit(1);
								}
							} else if (targetInfo.isWatchAppV1 || targetInfo.isWatchAppV2orNewer) {
								logger.error(__('The "%s" iOS extension "%s" target\'s Info.plist is missing the WKWatchKitApp property, yet the product type is of a watch: %s', ext.projectName, targetName, productType) + '\n');
								process.exit(1);
							}

							ext.targetInfo.id = plist.CFBundleIdentifier.replace(/^\$\((.*)\)$/, function (s, m) {
								return buildSettings[m] || s;
							});

							return true;
						}
						return false;
					});
				}(child.value, ext.basePath));

				return true;
			});

			if (cli.argv.target !== 'simulator') {
				// check that all target provisioning profile uuids are valid
				if (!tiappTargets[targetName].ppUUIDs || !tiappTargets[targetName].ppUUIDs[cli.argv.target]) {
					if (cli.argv['pp-uuid']) {
						if (!tiappTargets[targetName].ppUUIDs) {
							tiappTargets[targetName].ppUUIDs = {};
						}
						tiappTargets[targetName].ppUUIDs[cli.argv.target] = cli.argv['pp-uuid'];
						// logger.warn(__('iOS extension "%s" target "%s" is missing the %s provisioning profile UUID in tiapp.xml.', projectName, '<' + cli.argv.target + '>', targetName));
						// logger.warn(__('Using the iOS app provisioning profile UUID "%s"', cli.argv['pp-uuid']));
					} else {
						logger.error(__('iOS extension "%s" target "%s" is missing the %s provisioning profile UUID in tiapp.xml.', projectName, '<' + cli.argv.target + '>', targetName));
						logger.log();
						logger.log('<ti:app xmlns:ti="http://ti.appcelerator.org">'.grey);
						logger.log('    <ios>'.grey);
						logger.log('        <extensions>'.grey);
						logger.log(('            <extension projectPath="' + ext.origProjectPath + '">').grey);
						logger.log(('                <target name="' + targetName + '">').grey);
						logger.log('                    <provisioning-profiles>'.grey);
						logger.log(('                        <' + cli.argv.target + '>PROVISIONING PROFILE UUID</' + cli.argv.target + '>').magenta);
						logger.log('                    </provisioning-profiles>'.grey);
						logger.log('                </target>'.grey);
						logger.log('            </extension>'.grey);
						logger.log('        </extensions>'.grey);
						logger.log('    </ios>'.grey);
						logger.log('</ti:app>'.grey);
						logger.log();
						process.exit(1);
					}
				}

				// find the selected provisioning profile
				const ppuuid = tiappTargets[targetName].ppUUIDs[cli.argv.target];
				const pp = getPPbyUUID(ppuuid);

				if (!pp) {
					logger.error(__('iOS extension "%s" target "%s" has invalid provisioning profile UUID in tiapp.xml.', projectName, targetName));
					logger.error(__('Unable to find a valid provisioning profile matching the UUID "%s".', ppuuid) + '\n');
					process.exit(1);
				}

				if (ext.targetInfo.id && !(new RegExp('^' + pp.appId.replace(/\*/g, '.*') + '$')).test(ext.targetInfo.id)) { // eslint-disable-line security/detect-non-literal-regexp
					logger.error(__('iOS extension "%s" target "%s" has invalid provisioning profile UUID in tiapp.xml.', projectName, targetName));
					logger.error(__('The provisioning profile "%s" is tied to the application identifier "%s", however the extension\'s identifier is "%s".', ppuuid, pp.appId, ext.targetInfo.id));
					logger.log();

					const matches = pps.filter(function (p) {
						return !p.expired && !p.managed && (new RegExp('^' + p.appId.replace(/\./g, '\\.').replace(/\*/g, '.*') + '$')).test(ext.targetInfo.id); // eslint-disable-line security/detect-non-literal-regexp
					});
					if (matches.length) {
						logger.log(__('Did you mean?'));
						let max = 0;
						matches.forEach(function (m) {
							if (m.appId.length > max) {
								max = m.appId.length;
							}
						});
						matches.forEach(function (m) {
							const expires = m.expirationDate && moment(m.expirationDate),
								day = expires && expires.format('D'),
								hour = expires && expires.format('h');
							logger.log('  ' + String(m.uuid).cyan + ' '
								+ appc.string.rpad(m.appId, max + 1)
								+ (m.expirationDate ? (' (' + __('expires %s', expires.format('MMM') + ' '
								+ (day.length === 1 ? ' ' : '') + day + ', ' + expires.format('YYYY') + ' '
								+ (hour.length === 1 ? ' ' : '') + hour + ':' + expires.format('mm:ss a'))
								+ ')').grey : ''));
						});
						logger.log();
					}

					process.exit(1);
				}
			}

			// we don't need the tiapp target lookup anymore
			delete tiappTargets[targetName];
		}, this);

		// check if we're missing any targets
		tiappTargets = Object.keys(tiappTargets);
		if (tiappTargets.length) {
			logger.error(__n('iOS extension "%%s" does not contain a target named "%%s".', 'iOS extension "%%s" does not contain the following targets: "%%s".', tiappTargets.length, projectName, tiappTargets.join(', ')) + '\n');
			process.exit(1);
		}

		this.extensions.push(ext);
	}, this);

	this._tiappSettingsInitialized = true;
};

/**
 * Validates the iOS build-specific arguments, tiapp.xml settings, and environment.
 *
 * @param {Object} logger - The logger instance.
 * @param {Object} config - The Titanium CLI config instance.
 * @param {Object} cli - The Titanium CLI instance.
 *
 * @returns {Function} A function to be called async which returns the actual configuration.
 */
iOSBuilder.prototype.validate = function (logger, config, cli) {
	Builder.prototype.validate.apply(this, arguments);

	// add the ios specific default icon to the list of icons
	this.defaultIcons.unshift(path.join(this.projectDir, 'DefaultIcon-ios.png'));

	return function (callback) {
		this.target = cli.argv.target;
		this.projectName = latenize(this.tiapp.name);
		this.deployType = !/^dist-/.test(this.target) && cli.argv['deploy-type'] ? cli.argv['deploy-type'] : this.deployTypes[this.target];
		this.buildType = cli.argv['build-type'] || '';
		this.provisioningProfileUUID = cli.argv['pp-uuid'];
		if (this.provisioningProfileUUID) {
			this.provisioningProfile = this.findProvisioningProfile(this.target, this.provisioningProfileUUID);
		}

		// check if this is the Xcode pre-compile phase
		if (cli.argv.xcode) {
			if (process.env.TITANIUM_CLI_XCODEBUILD) {
				// readBuildManifest();
				// // we are being built from the CLI, so we must have a valid buildManifest.json
				//  // first mix everything in
				// appc.util.mix(cli.argv, this.buildManifest);
				// // next translate specific keys that may differ
				// cli.argv.target = process.env.CURRENT_ARCH === 'i386' ? 'simulator' : (this.buildManifest.target != 'simulator' ? this.buildManifest.target : 'device');
				// cli.argv['deploy-type'] = this.buildManifest.deployType;
				// cli.argv['output-dir'] = this.buildManifest.outputDir;
				// cli.argv['developer-name'] = this.buildManifest.developerName;
				// cli.argv['distribution-name'] = this.buildManifest.distributionName;
				// cli.argv['skip-js-minify'] = this.buildManifest.skipJSMinification;
				// cli.argv['force-copy'] = this.buildManifest.forceCopy;
				// cli.argv['force-copy-all'] = this.buildManifest.forceCopyAll;
			} else {
				// we are being build from Xcode
				cli.argv.target = process.env.CURRENT_ARCH === 'i386' ? 'simulator' : 'device';
				cli.argv['deploy-type'] = process.env.CURRENT_ARCH === 'i386' ? 'development' : 'test';
				cli.argv['developer-name'] = process.env.CODE_SIGN_IDENTITY.replace(/^iPhone Developer: /, '');
				cli.argv['distribution-name'] = process.env.CODE_SIGN_IDENTITY.replace(/^iPhone Distribution: /, '');

			}
			cli.argv['skip-js-minify'] = true; // never minify Xcode builds
			cli.argv['force-copy'] = true; // if building from xcode, we'll force files to be copied instead of symlinked
			cli.argv['force-copy-all'] = false; // we don't want to copy the big libTiCore.a file around by default
		}
		if (cli.argv.xcode) {
			this.deployType = cli.argv['deploy-type'] || this.deployTypes[this.target];
		} else {
			this.deployType = cli.argv['deploy-type'] ? cli.argv['deploy-type'] : this.deployTypes[
				this.target];
		}

		// manually inject the build profile settings into the tiapp.xml
		switch (this.deployType) {
			case 'production':
				this.showErrorController = false;
				this.minifyJS = true;
				this.encryptJS = true;
				this.minifyCSS = true;
				this.allowDebugging = false;
				this.allowProfiling = false;
				this.includeAllTiModules = false;
				break;

			case 'test':
				this.showErrorController = true;
				this.minifyJS = true;
				this.encryptJS = true;
				this.minifyCSS = true;
				this.allowDebugging = true;
				this.allowProfiling = true;
				this.includeAllTiModules = false;
				break;

			case 'development':
			default:
				this.showErrorController = true;
				this.minifyJS = false;
				this.encryptJS = false;
				this.minifyCSS = false;
				this.allowDebugging = true;
				this.allowProfiling = true;
				this.includeAllTiModules = true;
		}

		if (cli.argv['skip-js-minify']) {
			this.minifyJS = false;
		}
		if (cli.argv.hasOwnProperty('hide-error-controller')) {
			this.showErrorController = false;
		}

		// this may have already been called in an option validate() callback
		this.initTiappSettings();

		// make sure the app doesn't have any blacklisted directories or files in the Resources directory and warn about graylisted names
		// const platformsRegExp = /^(android|ios|iphone|ipad|mobileweb|blackberry|windows|tizen)$/;
		this.blacklistDirectories.push(this.tiapp.name);
		[	path.join(this.projectDir, 'Resources'),
			path.join(this.projectDir, 'Resources', 'iphone'),
			path.join(this.projectDir, 'Resources', 'ios')
		].forEach(function (dir) {
			fs.existsSync(dir) && fs.readdirSync(dir).forEach(function (filename) {
				const lcaseFilename = filename.toLowerCase(),
					isDir = fs.statSync(path.join(dir, filename)).isDirectory();

				// if we have a platform resource dir, then this will not be copied and we should be ok
				if (ti.allPlatformNames.indexOf(lcaseFilename) !== -1) {
					return;
				}

				if (this.blacklistDirectories.indexOf(lcaseFilename) !== -1) {
					if (isDir) {
						logger.error(__('Found blacklisted directory in the Resources directory'));
						logger.error(__('The directory "%s" is a reserved directory.', filename));
						logger.error(__('You must rename this directory to something else.') + '\n');
					} else {
						logger.error(__('Found blacklisted file in the Resources directory'));
						logger.error(__('The file "%s" is a reserved file.', filename));
						logger.error(__('You must rename this file to something else.') + '\n');
					}
					process.exit(1);
				} else if (this.graylistDirectories.indexOf(lcaseFilename) !== -1) {
					if (isDir) {
						logger.warn(__('Found graylisted directory in the Resources directory'));
						logger.warn(__('The directory "%s" is potentially a reserved directory.', filename));
						logger.warn(__('There is a good chance your app will be rejected by Apple.'));
						logger.warn(__('It is highly recommended you rename this directory to something else.'));
					} else {
						logger.warn(__('Found graylisted file in the Resources directory'));
						logger.warn(__('The file "%s" is potentially a reserved file.', filename));
						logger.warn(__('There is a good chance your app will be rejected by Apple.'));
						logger.warn(__('It is highly recommended you rename this file to something else.'));
					}
				}
			}, this);
		}, this);

		if (this.deployType !== 'production' && cli.argv.target !== 'simulator') {
			// determine if we're going to be minifying javascript
			const compileJSProp = cli.tiapp.properties['ti.compilejs'];
			if (cli.argv['skip-js-minify']) {
				if (this.compileJS) {
					logger.debug(__('JavaScript files were going to be minified, but %s is forcing them to not be minified',
						'--skip-js-minify'.cyan));
				}
				this.compileJS = this.encryptJS = this.minifyJS = false;
			} else if (compileJSProp) {
				if (this.compileJS && !compileJSProp.value) {
					logger.debug(__('JavaScript files were going to be minified, but %s is forcing them to not be minified',
						'ti.compilejs'.cyan));
				}
				this.encryptJS = this.minifyJS = !!compileJSProp.value;
			}
			this.currentBuildManifest.encryptJS = !!this.encryptJS;
		}
		// check if we are running from Xcode
		if (cli.argv.xcode) {
			cli.argv['skip-js-minify'] = true; // never minify Xcode builds
			cli.argv['force-copy'] = true; // if building from xcode, we'll force files to be copied instead of symlinked
			cli.argv['force-copy-all'] = false; // we don't want to copy the big libTiCore.a file around by default
		}

		// if in the prepare phase and doing a device/dist build...
		if (cli.argv.target !== 'simulator') {
			// make sure they have Apple's WWDR cert installed
			if (!this.iosInfo.certs.wwdr) {
				logger.error(__('WWDR Intermediate Certificate not found') + '\n');
				logger.log(__('Download and install the certificate from %s', 'http://appcelerator.com/ios-wwdr'.cyan) + '\n');
				process.exit(1);
			}

			// validate keychain
			const keychain = cli.argv.keychain ? appc.fs.resolvePath(cli.argv.keychain) : null;
			if (keychain && !fs.existsSync(keychain)) {
				logger.error(__('Unable to find keychain "%s"', keychain) + '\n');
				logger.log(__('Available keychains:'));
				Object.keys(this.iosInfo.certs.keychains).forEach(function (kc) {
					logger.log('    ' + kc.cyan);
				});
				logger.log();
				appc.string.suggest(keychain, Object.keys(this.iosInfo.certs.keychains), logger.log);
				process.exit(1);
			}
		}

		let deviceFamily = this.getDeviceFamily();
		if (!deviceFamily) {
			logger.info(__('No device family specified, defaulting to %s', 'universal'));
			deviceFamily = this.deviceFamily = 'universal';
		}

		if (!this.deviceFamilies[deviceFamily]) {
			logger.error(__('Invalid device family "%s"', deviceFamily) + '\n');
			appc.string.suggest(deviceFamily, Object.keys(this.deviceFamilies), logger.log, 3);
			process.exit(1);
		}

		// device family may have been modified, so set it back in the args
		cli.argv['device-family'] = deviceFamily;

		if (cli.argv.target !== 'dist-appstore') {
			const tool = [];
			this.allowDebugging && tool.push('debug');
			this.allowProfiling && tool.push('profiler');
			tool.forEach(function (type) {
				if (cli.argv[type + '-host']) {
					if (typeof cli.argv[type + '-host'] === 'number') {
						logger.error(__('Invalid %s host "%s"', type, cli.argv[type + '-host']) + '\n');
						logger.log(__('The %s host must be in the format "host:port".', type) + '\n');
						process.exit(1);
					}

					const parts = cli.argv[type + '-host'].split(':');

					if ((cli.argv.target === 'simulator' && parts.length < 2) || (cli.argv.target !== 'simulator' && parts.length < 4)) {
						logger.error(__('Invalid ' + type + ' host "%s"', cli.argv[type + '-host']) + '\n');
						if (cli.argv.target === 'simulator') {
							logger.log(__('The %s host must be in the format "host:port".', type) + '\n');
						} else {
							logger.log(__('The %s host must be in the format "host:port:airkey:hosts".', type) + '\n');
						}
						process.exit(1);
					}

					if (parts.length > 1 && parts[1]) {
						const port = parseInt(parts[1]);
						if (isNaN(port) || port < 1 || port > 65535) {
							logger.error(__('Invalid ' + type + ' host "%s"', cli.argv[type + '-host']) + '\n');
							logger.log(__('The port must be a valid integer between 1 and 65535.') + '\n');
							process.exit(1);
						}
					}
				}
			});
		}

		// make sure we have an app icon
		if (!this.tiapp.icon || ![ 'Resources', 'Resources/iphone', 'Resources/ios' ].some(function (p) {
			return fs.existsSync(this.projectDir, p, this.tiapp.icon);
		}, this)) {
			logger.info(this.tiapp.icon ? __('Unable to find an app icon in the Resources directory, using default') : __('No app icon set in tiapp.xml, using default'));
			this.tiapp.icon = 'appicon.png';
		}

		if (!/\.png$/.test(this.tiapp.icon)) {
			logger.error(__('Application icon must be a PNG formatted image.') + '\n');
			process.exit(1);
		}

		this.tiapp.ios || (this.tiapp.ios = {});
		this.tiapp.ios.capabilities || (this.tiapp.ios.capabilities = {});
		this.tiapp.ios.extensions || (this.tiapp.ios.extensions = []);
		const appId = this.tiapp.id;

		// validate the log server port
		const logServerPort = this.tiapp.ios['log-server-port'];
		if (!/^dist-(appstore|adhoc)$/.test(this.target) && logServerPort && (typeof logServerPort !== 'number' || logServerPort < 1024 || logServerPort > 65535)) {
			logger.error(__('Invalid <log-server-port> found in the tiapp.xml'));
			logger.error(__('Port must be a positive integer between 1024 and 65535') + '\n');
			process.exit(1);
		}

		series(this, [
			function validateExtensions(next) {
				// if there's no extensions, then skip this step
				if (!this.tiapp.ios.extensions.length) {
					return next();
				}

				// if there are any extensions, validate them
				async.eachSeries(this.tiapp.ios.extensions, function (ext, next) {
					if (!ext.projectPath) {
						logger.error(__('iOS extensions must have a "projectPath" attribute that points to a folder containing an Xcode project.') + '\n');
						process.exit(1);
					}

					// projectPath could be either the path to a project directory or the actual .xcodeproj
					ext.origProjectPath = ext.projectPath;
					ext.projectPath = ext.projectPath[0] === '/' ? appc.fs.resolvePath(ext.projectPath) : appc.fs.resolvePath(this.projectDir, ext.projectPath);

					const xcodeprojRegExp = /\.xcodeproj$/;
					if (!xcodeprojRegExp.test(ext.projectPath)) {
						// maybe we're the parent dir?
						ext.projectPath = path.join(ext.projectPath, path.basename(ext.projectPath) + '.xcodeproj');
					}

					const projectName = path.basename(ext.projectPath.replace(xcodeprojRegExp, ''));

					if (!fs.existsSync(ext.projectPath)) {
						logger.error(__('iOS extension "%s" Xcode project not found: %s', projectName, ext.projectPath) + '\n');
						process.exit(1);
					}

					const projFile = path.join(ext.projectPath, 'project.pbxproj');
					if (!fs.existsSync(projFile)) {
						logger.error(__('iOS extension "%s" project missing Xcode project file: %s', projectName, projFile) + '\n');
						process.exit(1);
					}

					if (!Array.isArray(ext.targets) || !ext.targets.length) {
						logger.warn(__('iOS extension "%s" has no targets, skipping.', projectName));
						return next();
					}

					let tiappTargets = {},
						// swiftRegExp = /\.swift$/,
						proj = xcode.project(path.join(ext.projectPath, 'project.pbxproj')).parseSync();

					// flag each target we care about
					ext.targets.forEach(function (target) {
						tiappTargets[target.name] = target;
					});

					// augment the ext entry with some extra details that we'll use later when constructing the Xcode project
					ext.objs        = proj.hash.project.objects;
					ext.project     = ext.objs.PBXProject[proj.hash.project.rootObject];
					ext.projectName = path.basename(ext.projectPath).replace(/\.xcodeproj$/, '');
					ext.basePath    = path.dirname(ext.projectPath);
					ext.relPath     = 'extensions/' + path.basename(path.dirname(ext.projectPath));
					ext.targetInfo  = {};

					const globalCfg = ext.objs.XCConfigurationList[ext.project.buildConfigurationList],
						globalCfgId = globalCfg.buildConfigurations
							.filter(function (c) { return c.comment.toLowerCase() === (globalCfg.defaultConfigurationName ? globalCfg.defaultConfigurationName.toLowerCase() : 'release'); })
							.map(function (c) { return c.value; })
							.shift(),
						globalBuildSettings = ext.objs.XCBuildConfiguration[globalCfgId].buildSettings;

					// find our targets
					ext.project.targets.forEach(function (t) {
						var targetName = t.comment;

						if (!tiappTargets[targetName]) {
							// not a target we care about
							return;
						}

						// we have found our target!

						const nativeTarget = ext.objs.PBXNativeTarget[t.value],

							cfg = ext.objs.XCConfigurationList[nativeTarget.buildConfigurationList],
							cfgid = cfg.buildConfigurations
								.filter(function (c) { return c.comment.toLowerCase() === (cfg.defaultConfigurationName ? cfg.defaultConfigurationName.toLowerCase() : 'release'); })
								.map(function (c) { return c.value; })
								.shift(),

							buildSettings = ext.objs.XCBuildConfiguration[cfgid].buildSettings,
							// sourcesBuildPhase = nativeTarget.buildPhases.filter(function (p) { return /^Sources$/i.test(p.comment); }),

							productType = nativeTarget.productType.replace(/^"/, '').replace(/"$/, ''),
							containsExtension = productType.indexOf('extension') !== -1,
							containsWatchApp = productType.indexOf('watchapp') !== -1,
							containsWatchKit = productType.indexOf('watchkit') !== -1,

							targetInfo = ext.targetInfo[targetName] = {
								productType:           productType,
								isWatchAppV1Extension: productType === 'com.apple.product-type.watchkit-extension',
								isExtension:           containsExtension && (!containsWatchKit || productType === 'com.apple.product-type.watchkit-extension'),
								isWatchAppV1:          productType === 'com.apple.product-type.application.watchapp',
								isWatchAppV2orNewer:   containsWatchApp && productType !== 'com.apple.product-type.application.watchapp',
								sdkRoot:               productType === 'com.apple.product-type.application.watchapp' ? 'watchos' : (buildSettings.SDKROOT || globalBuildSettings.SDKROOT || null),
								watchOS:               productType === 'com.apple.product-type.application.watchapp' ? '1.0' : (buildSettings.WATCHOS_DEPLOYMENT_TARGET || globalBuildSettings.WATCHOS_DEPLOYMENT_TARGET || null),
								infoPlist:             null
							};

						if (targetInfo.isWatchAppV1Extension || targetInfo.isWatchAppV1) {
							logger.error(__('WatchOS1 app detected.'));
							logger.error(__('Titanium %s does not support WatchOS1 apps.', this.titaniumSdkVersion) + '\n');
							process.exit(1);
						}

						// we need to get a min watch os version so that we can intelligently pick an appropriate watch simulator
						if (targetInfo.isWatchAppV2orNewer
								&& (!cli.argv['watch-app-name'] || targetName === cli.argv['watch-app-name'])
								&& (!this.watchMinOSVersion || appc.version.lt(targetInfo.watchOS, this.watchMinOSVersion))) {
							this.watchMinOSVersion = targetInfo.watchOS;
						}

						if (targetInfo.isWatchAppV2orNewer) {
							this.hasWatchAppV2orNewer = true;
						}

						// find this target's Info.plist
						ext.objs.PBXGroup[ext.project.mainGroup].children.some(function (child) {
							if (child.comment !== targetName) {
								return false;
							}

							(function walkGroup(uuid, basePath) {
								if (ext.objs.PBXGroup[uuid].path) {
									basePath = path.join(basePath, ext.objs.PBXGroup[uuid].path.replace(/^"/, '').replace(/"$/, ''));
								}

								ext.objs.PBXGroup[uuid].children.some(function (child) {
									if (ext.objs.PBXGroup[child.value]) {
										return walkGroup(child.value, basePath);
									} else if (ext.objs.PBXFileReference[child.value] && child.comment === 'Info.plist') {
										const infoPlistFile = path.join(basePath, 'Info.plist');
										if (!fs.existsSync(infoPlistFile)) {
											logger.error(__('Unable to find "%s" iOS extension\'s "%s" target\'s Info.plist: %s', ext.projectName, targetName, infoPlistFile) + '\n');
											process.exit(1);
										}

										const plist = ext.targetInfo[targetName].infoPlist = ioslib.utilities.readPlist(infoPlistFile);
										if (!plist) {
											logger.error(__('Failed to parse "%s" iOS extension\'s "%s" target\'s Info.plist: %s', ext.projectName, targetName, infoPlistFile) + '\n');
											process.exit(1);
										}

										if (plist.WKWatchKitApp) {
											const CFBundleIdentifier = plist.CFBundleIdentifier.replace('$(PRODUCT_BUNDLE_IDENTIFIER)', buildSettings.PRODUCT_BUNDLE_IDENTIFIER);
											if (CFBundleIdentifier.indexOf(appId) !== 0) {
												logger.error(__('iOS extension "%s" WatchKit App bundle identifier is "%s", but must be prefixed with "%s".', ext.projectName, plist.CFBundleIdentifier, appId) + '\n');
												process.exit(1);
											}

											if (CFBundleIdentifier.toLowerCase() === appId.toLowerCase()) {
												logger.error(__('iOS extension "%s" WatchKit App bundle identifier must be different from the Titanium app\'s id "%s".', ext.projectName, appId) + '\n');
												process.exit(1);
											}
										} else if (targetInfo.isWatchAppV1 || targetInfo.isWatchAppV2orNewer) {
											logger.error(__('The "%s" iOS extension "%s" target\'s Info.plist is missing the WKWatchKitApp property, yet the product type is of a watch: %s', ext.projectName, targetName, productType) + '\n');
											process.exit(1);
										}

										ext.targetInfo.id = plist.CFBundleIdentifier.replace(/^\$\((.*)\)$/, function (s, m) {
											return buildSettings[m] || s;
										});

										return true;
									} else {
										return false;
									}
								});
							}(child.value, ext.basePath));

							return true;
						});

						if (cli.argv.target !== 'simulator') {
							// check that all target provisioning profile uuids are valid
							if (!tiappTargets[targetName].ppUUIDs || !tiappTargets[targetName].ppUUIDs[cli.argv.target]) {
								if (cli.argv['pp-uuid']) {
									if (!tiappTargets[targetName].ppUUIDs) {
										tiappTargets[targetName].ppUUIDs = {};
									}
									tiappTargets[targetName].ppUUIDs[cli.argv.target] = cli.argv['pp-uuid'];
									logger.warn(__('iOS extension "%s" target "%s" is missing the %s provisioning profile UUID in tiapp.xml.', projectName, '<' + cli.argv.target + '>', targetName));
									logger.warn(__('Using the iOS app provisioning profile UUID "%s"', cli.argv['pp-uuid']));
								} else {
									logger.error(__('iOS extension "%s" target "%s" is missing the %s provisioning profile UUID in tiapp.xml.', projectName, '<' + cli.argv.target + '>', targetName));
									logger.log();
									logger.log('<ti:app xmlns:ti="http://ti.appcelerator.org">'.grey);
									logger.log('    <ios>'.grey);
									logger.log('        <extensions>'.grey);
									logger.log(('            <extension projectPath="' + ext.origProjectPath + '">').grey);
									logger.log(('                <target name="' + targetName + '">').grey);
									logger.log('                    <provisioning-profiles>'.grey);
									logger.log(('                        <' + cli.argv.target + '>PROVISIONING PROFILE UUID</' + cli.argv.target + '>').magenta);
									logger.log('                    </provisioning-profiles>'.grey);
									logger.log('                </target>'.grey);
									logger.log('            </extension>'.grey);
									logger.log('        </extensions>'.grey);
									logger.log('    </ios>'.grey);
									logger.log('</ti:app>'.grey);
									logger.log();
									process.exit(1);
								}
							}

							// check that the PP UUID is correct
							let ppuuid = tiappTargets[targetName].ppUUIDs[cli.argv.target],
								pps = [],
								pp;

							function getPPbyUUID() {
								return pps
									.filter(function (p) {
										if (!p.expired && !p.managed && p.uuid === ppuuid) {
											return true;
										}
										return false;
									})
									.shift();
							}

							if (cli.argv.target === 'device') {
								pps = this.iosInfo.provisioning.development;
								pp = getPPbyUUID();
							} else if (cli.argv.target === 'dist-appstore' || cli.argv.target === 'dist-adhoc') {
								pps = [].concat(this.iosInfo.provisioning.distribution, this.iosInfo.provisioning.adhoc);
								pp = getPPbyUUID();
							}

							if (!pp) {
								logger.error(__('iOS extension "%s" target "%s" has invalid provisioning profile UUID in tiapp.xml.', projectName, targetName));
								logger.error(__('Unable to find a valid provisioning profile matching the UUID "%s".', ppuuid) + '\n');
								process.exit(1);
							}

							if (ext.targetInfo.id && !(new RegExp('^' + pp.appId.replace(/\*/g, '.*') + '$')).test(ext.targetInfo.id)) {
								logger.error(__('iOS extension "%s" target "%s" has invalid provisioning profile UUID in tiapp.xml.', projectName, targetName));
								logger.error(__('The provisioning profile "%s" is tied to the application identifier "%s", however the extension\'s identifier is "%s".', ppuuid, pp.appId, ext.targetInfo.id));
								logger.log();

								const matches = pps.filter(function (p) {
									return !p.expired && !p.managed && (new RegExp('^' + p.appId.replace(/\./g, '\\.').replace(/\*/g, '.*') + '$')).test(ext.targetInfo.id);
								});
								if (matches.length) {
									logger.log(__('Did you mean?'));
									let max = 0;
									matches.forEach(function (m) {
										if (m.appId.length > max) {
											max = m.appId.length;
										}
									});
									matches.forEach(function (m) {
										var expires = m.expirationDate && moment(m.expirationDate),
											day = expires && expires.format('D'),
											hour = expires && expires.format('h');
										logger.log('  ' + String(m.uuid).cyan + ' '
											+ appc.string.rpad(m.appId, max + 1)
											+ (m.expirationDate ? (' (' + __('expires %s', expires.format('MMM') + ' '
											+ (day.length === 1 ? ' ' : '') + day + ', ' + expires.format('YYYY') + ' '
											+ (hour.length === 1 ? ' ' : '') + hour + ':' + expires.format('mm:ss a'))
											+ ')').grey : ''));
									});
									logger.log();
								}

								process.exit(1);
							}
						}

						// we don't need the tiapp target lookup anymore
						delete tiappTargets[targetName];
					}, this);

					// check if we're missing any targets
					tiappTargets = Object.keys(tiappTargets);
					if (tiappTargets.length) {
						logger.error(__n('iOS extension "%%s" does not contain a target named "%%s".', 'iOS extension "%%s" does not contain the following targets: "%%s".', tiappTargets.length, projectName, tiappTargets.join(', ')) + '\n');
						process.exit(1);
					}

					this.extensions.push(ext);

					next();
				}.bind(this), next);
			},

			function selectIosVersion() {
				this.iosSdkVersion = cli.argv['ios-version'] || null;
				this.xcodeEnv = null;

				const xcodeInfo = this.iosInfo.xcode;

				function sortXcodeIds(a, b) {
					return xcodeInfo[a].selected || appc.version.gt(xcodeInfo[a].version, xcodeInfo[b].version) ? -1 : appc.version.lt(xcodeInfo[a].version, xcodeInfo[b].version) ? 1 : 0;
				}

				if (this.iosSdkVersion) {
					// find the Xcode for this version
					Object.keys(this.iosInfo.xcode).sort(sortXcodeIds).reverse().some(function (ver) {
						if (this.iosInfo.xcode[ver].sdks.indexOf(this.iosSdkVersion) !== -1) {
							this.xcodeEnv = this.iosInfo.xcode[ver];
							return true;
						}
						return false;
					}, this);

					if (!this.xcodeEnv) {
						// this should not be possible, but you never know
						logger.error(__('Unable to find any Xcode installations that support iOS SDK %s.', this.iosSdkVersion) + '\n');
						process.exit(1);
					}
				} else if (cli.argv.target === 'simulator' && !cli.argv['build-only']) {
					// we'll let ioslib suggest an iOS version
				} else { // device, dist-appstore, dist-adhoc
					let minVer = this.tiapp.ios['min-ios-ver'] && appc.version.gt(this.tiapp.ios['min-ios-ver'], this.minSupportedIosSdk) ? this.tiapp.ios['min-ios-ver'] : this.minSupportedIosSdk;
					if (this.hasWatchAppV2orNewer && appc.version.lt(minVer, '9.0')) {
						minVer = '9.0';
					} else if (this.tiapp.ios['enable-launch-screen-storyboard'] && appc.version.lt(minVer, '8.0')) {
						minVer = '8.0';
					}

					Object.keys(xcodeInfo)
						.filter(function (id) { return xcodeInfo[id].supported; })
						.sort(sortXcodeIds)
						.some(function (id) {
							return xcodeInfo[id].sdks.sort().reverse().some(function (ver) {
								if (appc.version.gte(ver, minVer)) {
									this.iosSdkVersion = ver;
									this.xcodeEnv = xcodeInfo[id];
									return true;
								}
								return false;
							}, this);
						}, this);

					if (!this.iosSdkVersion) {
						logger.error(__('Unable to find any Xcode installations with a supported iOS SDK.'));
						logger.error(__('Please install the latest Xcode and point xcode-select to it.') + '\n');
						process.exit(1);
					}
				}
			},

			function selectDevice(next) {
				if (cli.argv.target === 'dist-appstore' || cli.argv.target === 'dist-adhoc') {
					return next();
				}

				// no --device-id or doing a build-only sim build, so pick a device

				if (cli.argv.target === 'device') {
					if (!cli.argv['build-only'] && !cli.argv['device-id']) {
						cli.argv['device-id'] = this.iosInfo.devices.length ? this.iosInfo.devices[0].udid : 'itunes';
					}
					return next();
				}

				// if we found a watch app and --watch-device-id was set, but --launch-watch-app was not, then set it
				if (this.hasWatchAppV2orNewer && cli.argv['watch-device-id'] && !cli.argv['launch-watch-app-only']) {
					cli.argv['launch-watch-app'] = true;
				}

				// make sure we have a watch app
				if ((cli.argv['launch-watch-app'] || cli.argv['launch-watch-app-only']) && !this.hasWatchAppV2orNewer) {
					logger.warn(__('%s flag was set, however there are no iOS extensions containing a watch app.', cli.argv['launch-watch-app'] ? '--launch-watch-app' : '--launch-watch-app-only'));
					logger.warn(__('Disabling launch watch app flag'));
					cli.argv['launch-watch-app'] = cli.argv['launch-watch-app-only'] = false;
				}

				// target is simulator
				ioslib.simulator.findSimulators({
					// env
					xcodeSelect:            config.get('osx.executables.xcodeSelect'),
					security:               config.get('osx.executables.security'),
					// provisioning
					profileDir:             config.get('ios.profileDir'),
					// xcode
					searchPath:             config.get('paths.xcode'),
					minIosVersion:          this.tiapp.ios['min-ios-ver'] || this.packageJson.minIosVersion,
					supportedVersions:      this.packageJson.vendorDependencies.xcode,
					// find params
					appBeingInstalled:      true,
					simHandleOrUDID:        cli.argv['device-id'],
					iosVersion:             this.iosSdkVersion,
					simType:                deviceFamily === 'ipad' ? 'ipad' : 'iphone',
					simVersion:             this.iosSdkVersion,
					watchAppBeingInstalled: this.hasWatchAppV2orNewer && (cli.argv['launch-watch-app'] || cli.argv['launch-watch-app-only']),
					watchHandleOrUDID:      cli.argv['watch-device-id'],
					watchMinOSVersion:      this.watchMinOSVersion,
					logger: function (msg) {
						logger.trace(('[ioslib] ' + msg).grey);
					}
				}, function (err, simHandle, watchSimHandle, selectedXcode) {
					if (err) {
						return next(err);
					}

					this.simHandle = simHandle;
					this.watchSimHandle = watchSimHandle;
					this.xcodeEnv = selectedXcode;

					if (!this.iosSdkVersion) {
						const sdks = selectedXcode.sdks.sort();
						this.iosSdkVersion = sdks[sdks.length - 1];
					}

					next();
				}.bind(this));
			},

			function validateTeamId() {
				this.teamId = this.tiapp.ios['team-id'];
				if (!this.teamId && this.provisioningProfile) {
					if (this.provisioningProfile.team.length === 1) {
						// only one team, so choose this over the appPrefix
						this.teamId = this.provisioningProfile.team[0];
					} else {
						// we have multiple teams and we don't know which one to pick, so prefer the appPrefix
						this.teamId = this.provisioningProfile.appPrefix;

						// if the appPrefix is not in the list of teams, then we need to fail and force the user
						// to manually specify their team id
						if (this.provisioningProfile.team.length && this.provisioningProfile.team.indexOf(this.teamId) === -1) {
							logger.log(__('Available teams:'));
							this.provisioningProfile.team.forEach(function (id) {
								logger.log('  ' + id.cyan);
							});
							logger.log();
							logger.log('<ti:app xmlns:ti="http://ti.appcelerator.org">'.grey);
							logger.log('    <ios>'.grey);
							logger.log('        <team-id>TEAM ID</team-id>'.magenta);
							logger.log('    </ios>'.grey);
							logger.log('</ti:app>'.grey);
							logger.log();
							process.exit(1);
						}
					}
				}
			},

			function toSymlinkOrNotToSymlink() {
				this.symlinkLibrariesOnCopy = config.get('ios.symlinkResources', true) && !cli.argv['force-copy'] && !cli.argv['force-copy-all'];
				this.symlinkFilesOnCopy = false;

				/* eslint-disable max-len */
				/*
				// since things are looking good, determine if files should be symlinked on copy
				// note that iOS 9 simulator does not support symlinked files :(
				this.symlinkFilesOnCopy = cli.argv.target === 'simulator' &&  config.get('ios.symlinkResources', true) && !cli.argv['force-copy'] && !cli.argv['force-copy-all'];

				// iOS 9 Simulator does not like symlinks :(
				if (cli.argv.target === 'simulator' && this.symlinkFilesOnCopy) {
					if (cli.argv['build-only'] && this.symlinkFilesOnCopy) {
						logger.warn(__('Files are being symlinked which is known to not work when running in an iOS 9 Simulators'));
						logger.warn(__('You may want to specify the --force-copy flag'));
					} else if (this.simHandle && appc.version.gte(this.simHandle.version, '9.0')) {
						logger.info(__('Symlinked files not supported with iOS %s simulator, forcing files to be copied', this.simHandle.version));
						this.symlinkFilesOnCopy = false;
					}
				} else if (this.symlinkFilesOnCopy && cli.argv.target === 'device' && (cli.argv['debug-host'] || cli.argv['profiler-host']) && version.gte(this.iosSdkVersion, '9.0')) {
					logger.info(__('Symlinked files are not supported with iOS %s device %s builds, forcing files to be copied', version.format(this.iosSdkVersion, 2, 2), cli.argv['debug-host'] ? 'debug' : 'profiler'));
					this.symlinkFilesOnCopy = false;
				}
				*/
				/* eslint-enable max-len */
			},

			function determineMinIosVer() {
				// figure out the min-ios-ver that this app is going to support
				let defaultMinIosSdk = this.packageJson.minIosVersion;

				if (version.gte(this.iosSdkVersion, '10.0') && version.lt(defaultMinIosSdk, '8.0')) {
					defaultMinIosSdk = '8.0';
				}

				this.minIosVer = this.tiapp.ios['min-ios-ver'] || defaultMinIosSdk;
				if (version.gte(this.iosSdkVersion, '6.0') && version.lt(this.minIosVer, defaultMinIosSdk)) {
					logger.info(__('Building for iOS %s; using %s as minimum iOS version', version.format(this.iosSdkVersion, 2).cyan, defaultMinIosSdk.cyan));
					this.minIosVer = defaultMinIosSdk;
				} else if (version.lt(this.minIosVer, defaultMinIosSdk)) {
					logger.info(__('The %s of the iOS section in the tiapp.xml is lower than minimum supported version: Using %s as minimum', 'min-ios-ver'.cyan, version.format(defaultMinIosSdk, 2).cyan));
					this.minIosVer = defaultMinIosSdk;
				} else if (version.gt(this.minIosVer, this.iosSdkVersion)) {
					logger.error(__('The <min-ios-ver> of the iOS section in the tiapp.xml is set to %s and is greater than the specified iOS version %s', version.format(this.minIosVer, 2), version.format(this.iosSdkVersion, 2)));
					logger.error(__('Either rerun with --ios-version %s or set the <min-ios-ver> to %s.', version.format(this.minIosVer, 2), version.format(this.iosSdkVersion, 2)) + '\n');
					process.exit(1);
				}
			},

			function validateDevice() {
				// check the min-ios-ver for the device we're installing to
				if (this.target === 'device') {
					this.getDeviceInfo().devices.forEach(function (device) {
						if (device.udid !== 'all' && device.udid !== 'itunes' && (cli.argv['device-id'] === 'all' || cli.argv['device-id'] === device.udid) && version.lt(device.productVersion, this.minIosVer)) {
							logger.error(__('This app does not support the device "%s"', device.name) + '\n');
							logger.log(__('The device is running iOS %s, however the app\'s the minimum iOS version is set to %s', device.productVersion.cyan, version.format(this.minIosVer, 2, 3).cyan));
							logger.log(__('In order to install this app on this device, lower the %s to %s in the tiapp.xml:', '<min-ios-ver>'.cyan, version.format(device.productVersion, 2, 2).cyan));
							logger.log();
							logger.log('<ti:app xmlns:ti="http://ti.appcelerator.org">'.grey);
							logger.log('    <ios>'.grey);
							logger.log(('        <min-ios-ver>' + version.format(device.productVersion, 2, 2) + '</min-ios-ver>').magenta);
							logger.log('    </ios>'.grey);
							logger.log('</ti:app>'.grey);
							logger.log();
							process.exit(0);
						}
					}, this);
				}
			},

			function validateModules(next) {
				this.validateTiModules([ 'ios', 'iphone' ], this.deployType, function (err, modules) {
					this.modules = modules.found;

					this.commonJsModules = [];
					this.nativeLibModules = [];

					const nativeHashes = [];

					modules.found.forEach(function (module) {
						if (module.platform.indexOf('commonjs') !== -1) {
							module.native = false;

							// look for legacy module.id.js first
							let libFile = path.join(module.modulePath, module.id + '.js');
							module.libFile = fs.existsSync(libFile) ? libFile : null;
							// If no legacy file, let require.resolve get the main script
							if (!module.libFile) {
								libFile = require.resolve(module.modulePath);
								if (fs.existsSync(libFile)) {
									module.libFile = libFile;
								}

								if (!module.libFile) {
									this.logger.error(__('Module "%s" v%s is missing main file: %s, package.json with "main" entry, index.js, or index.json', module.id, module.manifest.version || 'latest', module.id + '.js') + '\n');
									process.exit(1);
								}
							}

							this.commonJsModules.push(module);
						} else {
							module.native = true;

							module.libName = 'lib' + module.id.toLowerCase() + '.a';
							module.libFile = path.join(module.modulePath, module.libName);
							const platformPath = path.join(module.modulePath, 'platform');

							if (fs.existsSync(path.join(platformPath))) {
								let thePath = path.join(platformPath, 'frameworks');
								if (fs.existsSync(thePath)) {
									module.frameworks = fs.readdirSync(thePath).filter(function (file) {
										return /\.framework$/.test(file);
									}).map(function (file) { return path.join(thePath, file); });
								} else {
									thePath = path.join(module.modulePath, 'platform');
									module.frameworks = fs.readdirSync(thePath).filter(function (file) {
										return /\.framework$/.test(file);
									}).map(function (file) { return path.join(thePath, file); });
								}
								thePath = path.join(platformPath, 'embeddedFrameworks');
								if (fs.existsSync(thePath)) {
									module.embeddedFrameworks = fs.readdirSync(thePath).filter(function (file) {
										return /\.framework$/.test(file);
									}).map(function (file) { return path.join(thePath, file); });
								} else {
									module.embeddedFrameworks = [];
								}
							} else {
								module.frameworks = [];
								module.embeddedFrameworks = [];
							}

							if (!fs.existsSync(module.libFile)) {
								this.logger.error(__('Module %s version %s is missing library file: %s', module.id.cyan, (module.manifest.version || 'latest').cyan, module.libFile.cyan) + '\n');
								process.exit(1);
							}

							nativeHashes.push(module.hash = this.hash(fs.readFileSync(module.libFile)));
							this.nativeLibModules.push(module);
						}

						// scan the module for any CLI hooks
						cli.scanHooks(path.join(module.modulePath, 'hooks'));
					}, this);

					this.currentBuildManifest.modulesManifestHash = this.modulesNativeHash = this.hash(nativeHashes.length ? nativeHashes.sort().join(',') : '');

					next();
				}.bind(this));
			}
		], function (err) {
			if (err) {
				logger.error((err.message || err.toString()) + '\n');
				process.exit(1);
			}
			callback();
		});
	}.bind(this); // end of function returned by validate()
};

/**
 * Performs the build operations.
 *
 * @param {Object} logger - The logger instance.
 * @param {Object} config - The Titanium CLI config instance.
 * @param {Object} cli - The Titanium CLI instance.
 * @param {Function} finished - A function to call when the build has finished or errored.
 */
iOSBuilder.prototype.run = function (logger, config, cli, finished) {
	Builder.prototype.run.apply(this, arguments);

	// force the platform to "ios" just in case it was "iphone" so that plugins can reference it
	cli.argv.platform = 'ios';

	series(this, [
		function (next) {
			cli.emit('build.pre.construct', this, next);
		},

		// initialization
		'doAnalytics',
		'readBuildManifest',
		'initialize',
		'determineLogServerPort',
		'loginfo',
		'checkIfNeedToRecompile',
		'initBuildDir',

		function (next) {
			cli.emit('build.pre.compile', this, next);
		},

		// function () {
		//	// Make sure we have an app.js. This used to be validated in validate(), but since plugins like
		//	// Alloy generate an app.js, it may not have existed during validate(), but should exist now
		//	// that build.pre.compile was fired.
		//	ti.validateAppJsExists(this.projectDir, this.logger, ['iphone', 'ios']);
		// },
		function (next) {
			// if (!cli.argv.xcode) {
			series(this, [
				// xcode related tasks
				'createXcodeProject',
				'writeEntitlementsPlist',
				'writeInfoPlist',
				'writeMain',
				'writeXcodeConfigFiles',
				'copyTitaniumLibraries',
				'copyTitaniumiOSFiles',
				'copyExtensionFiles',
				'cleanXcodeDerivedData',
			], next);
			// } else {
			// 	next();
			// }
		},
		function (next) {
			if (!this.symlinkFilesOnCopy || this.forceRebuild) {
				series(this, [
					// titanium related tasks
					'writeDebugProfilePlists',
					// 'copyItunesArtwork',
					'copyResources',
					'encryptJSFiles',
					'writeI18NFiles',
					'processTiSymbols',
				], next);
			} else {
				next();
			}
		},

		function (next) {
			if (!cli.argv.xcode && this.forceRebuild) {
				series(this, [

					// cleanup and optimization
					'removeFiles',
					'optimizeFiles',

					// build baby, build
					'invokeXcodeBuild',
					// provide a hook event after xcodebuild
					function (next) {
						cli.emit('build.post.build', this, next);
					}
				], next);
			} else {
				next();
			}
		},
		// finalize
		'writeBuildManifest',

		function (next) {
			cli.emit('build.post.compile', this, next);
		},

		function (next) {
			if (!this.buildOnly && (this.target === 'dist-appstore' || this.target === 'dist-adhoc')) {
				const delta = appc.time.prettyDiff(this.cli.startTime, Date.now());
				this.logger.info(__('Finished building the application in %s', delta.cyan));
			}

			cli.emit('build.finalize', this, next);
		}
	], finished);
};

iOSBuilder.prototype.doAnalytics = function doAnalytics() {
	/* const cli = this.cli,
		eventName = cli.argv['device-family'] + '.' + cli.argv.target;

	if (cli.argv.target === 'dist-appstore' || cli.argv.target === 'dist-adhoc') {
		eventName = cli.argv['device-family'] + '.distribute.' + cli.argv.target.replace('dist-', '');
	} else if (this.allowDebugging && cli.argv['debug-host']) {
		eventName += '.debug';
	} else if (this.allowProfiling && cli.argv['profiler-host']) {
		eventName += '.profile';
	} else {
		eventName += '.run';
	}

	cli.addAnalyticsEvent(eventName, {
		dir:         cli.argv['project-dir'],
		name:        this.tiapp.name,
		publisher:   this.tiapp.publisher,
		url:         this.tiapp.url,
		image:       this.tiapp.icon,
		appid:       this.tiapp.id,
		description: this.tiapp.description,
		type:        cli.argv.type,
		guid:        this.tiapp.guid,
		version:     this.tiapp.version,
		copyright:   this.tiapp.copyright,
		date:        (new Date).toDateString()
	});*/
};

iOSBuilder.prototype.initialize = function initialize() {
	const argv = this.cli.argv;

	// populate the build manifest object
	this.currentBuildManifest.target            = this.target;
	this.currentBuildManifest.deployType        = this.deployType;
	this.currentBuildManifest.sdkVersion        = this.tiapp['sdk-version'];
	this.currentBuildManifest.iosSdkVersion     = this.iosSdkVersion;
	this.currentBuildManifest.deviceFamily      = this.deviceFamily;
	this.currentBuildManifest.iosSdkPath        = this.platformPath;
	this.currentBuildManifest.tiCoreHash        = this.libTiCoreHash            = this.hash(fs.readFileSync(path.join(this.platformPath, 'libTiCore.a')));
	this.currentBuildManifest.developerName     = this.certDeveloperName        = argv['developer-name'];
	this.currentBuildManifest.distributionName  = this.certDistributionName     = argv['distribution-name'];
	this.currentBuildManifest.modulesHash       = this.modulesHash              = this.hash(!Array.isArray(this.tiapp.modules) ? '' : this.tiapp.modules.filter(function (m) {
		return !m.platform || /^iphone|ipad|ios|commonjs$/.test(m.platform);
	}).map(function (m) {
		return m.id + ',' + m.platform + ',' + m.version;
	}).join('|'));
	this.currentBuildManifest.modulesNativeHash  = this.modulesNativeHash;
	this.currentBuildManifest.gitHash            = ti.manifest.githash;
	this.currentBuildManifest.ppUuid             = this.provisioningProfileUUID;
	this.currentBuildManifest.outputDir          = this.cli.argv['output-dir'];
	this.currentBuildManifest.forceCopy          = this.forceCopy               = !!argv['force-copy'];
	this.currentBuildManifest.forceCopyAll       = this.forceCopyAll            = !!argv['force-copy-all'];
	this.currentBuildManifest.name               = this.tiapp.name;
	this.currentBuildManifest.id                 = this.tiapp.id;
	this.currentBuildManifest.analytics          = this.tiapp.analytics;
	this.currentBuildManifest.publisher          = this.tiapp.publisher;
	this.currentBuildManifest.url                = this.tiapp.url;
	this.currentBuildManifest.version            = this.tiapp.version;
	this.currentBuildManifest.description        = this.tiapp.description;
	this.currentBuildManifest.copyright          = this.tiapp.copyright;
	this.currentBuildManifest.guid               = this.tiapp.guid;
	this.currentBuildManifest.useAppThinning     = this.useAppThinning = this.tiapp.ios['use-app-thinning'] === true;
	this.currentBuildManifest.skipJSMinification = !!this.cli.argv['skip-js-minify'];
	this.currentBuildManifest.encryptJS          = !!this.encryptJS;
	this.currentBuildManifest.showErrorController          = this.showErrorController;

	// Use native JSCore by default (TIMOB-23136)
	this.currentBuildManifest.useJSCore = this.useJSCore = !this.debugHost && !this.profilerHost && this.tiapp.ios['use-jscore-framework'] !== false;

	// Remove this check on 7.0.0
	if (this.tiapp.ios && (this.tiapp.ios.hasOwnProperty('run-on-main-thread'))) {
		this.logger.warn(__('run-on-main-thread no longer set in the <ios> section of the tiapp.xml. Use <property name="run-on-main-thread" type="bool">true</property> instead'));
		this.currentBuildManifest.runOnMainThread = this.runOnMainThread = (this.tiapp.ios['run-on-main-thread'] === true);
	} else {
		this.currentBuildManifest.runOnMainThread = this.runOnMainThread = (this.tiapp.properties && this.tiapp.properties.hasOwnProperty('run-on-main-thread') && this.tiapp.properties['run-on-main-thread'].value || false);
	}

	this.currentBuildManifest.useBabel = this.useBabel = !!this.tiapp.properties['use-babel'];
	// we test the package.json hash in case babel settings changed
	this.currentBuildManifest.packageJSONHash            = this.packageJSONHash = fs.exists('package.json') ? this.hash(fs.readFileSync('package.json')) : '';

	this.currentBuildManifest.useAutoLayout = this.useAutoLayout = this.tiapp.ios && (this.tiapp.ios['use-autolayout'] === true);

	// Deprecate TiJSCore and leave a warning if used anyway
	if (!this.useJSCore) {
		this.logger.warn(__('Titanium 7.0.0 deprecates the legacy JavaScriptCore library in favor of the built-in JavaScriptCore.'));
		this.logger.warn(__('The legacy JavaScriptCore library will be removed in Titanium SDK 8.0.0.'));
	}

	// Deprecate KrollThread and leave a warning if used anyway
	if (!this.runOnMainThread) {
		this.logger.warn(__('Titanium 7.0.0 deprecates the legacy UI-execution on Kroll-Thread in favor of the Main-Thread.'));
		this.logger.warn(__('The legacy execution will be removed in Titanium SDK 8.0.0.'));
	}

	this.moduleSearchPaths = [ this.projectDir, appc.fs.resolvePath(this.platformPath, '..', '..', '..', '..') ];
	if (this.config.paths && Array.isArray(this.config.paths.modules)) {
		this.moduleSearchPaths = this.moduleSearchPaths.concat(this.config.paths.modules);
	}

	this.debugHost     = this.allowDebugging && argv['debug-host'];
	this.profilerHost  = this.allowProfiling && argv['profiler-host'];
	this.buildOnly     = argv['build-only'];
	this.launchUrl     = argv['launch-url'];
	this.keychain      = argv['keychain'];
	this.deviceId      = argv['device-id'];
	this.deviceInfo    = this.deviceId ? this.getDeviceInfo().udids[this.deviceId] : null;
	this.xcodeTarget   = process.env.CONFIGURATION || (/^device|simulator$/.test(this.target) || this.deployType
		=== 'development' ? 'Debug' : 'Release');
	this.xcodeTargetOS = this.target === 'simulator' ? 'iphonesimulator' : 'iphoneos';

	this.iosBuildDir            = path.join(this.buildDir, 'build', 'Products', this.xcodeTarget + '-' + this.xcodeTargetOS);

	if (argv.xcode && process.env.TARGET_BUILD_DIR && process.env.CONTENTS_FOLDER_PATH) {
		this.xcodeAppDir = path.join(process
			.env.TARGET_BUILD_DIR, process.env.CONTENTS_FOLDER_PATH);
	} else if (this.target === 'dist-appstore' || this.target === 'dist-adhoc') {
		this.xcodeAppDir        = path.join(this.buildDir, 'ArchiveStaging');
	} else {
		this.xcodeAppDir        = path.join(this.iosBuildDir, this.tiapp.name + '.app');
	}

	this.xcodeProjectConfigFile = path.join(this.buildDir, 'project.xcconfig');
	this.buildAssetsDir         = path.join(this.buildDir, 'assets');
	this.buildTsDir         	= path.join(this.buildDir, 'ts');
	this.buildTsOutputDir       = path.join(this.buildDir, 'tsoutput');

	if ((this.tiapp.properties && this.tiapp.properties.hasOwnProperty('ios.whitelist.appcelerator.com') && this.tiapp.properties['ios.whitelist.appcelerator.com'].value === false) || !this.tiapp.analytics) {
		// force appcelerator.com to not be whitelisted in the Info.plist ATS section
		this.whitelistAppceleratorDotCom = false;
	}

	if (!this.tiapp.ios['enable-launch-screen-storyboard'] || appc.version.lt(this.xcodeEnv.version, '7.0.0')) {
		this.enableLaunchScreenStoryboard = false;
		this.defaultLaunchScreenStoryboard = false;
	}

	if (this.enableLaunchScreenStoryboard && (fs.existsSync(path.join(this.projectDir, 'platform', 'ios', 'LaunchScreen.storyboard')) || fs.existsSync(path.join(this.projectDir, 'platform', 'iphone', 'LaunchScreen.storyboard')))) {
		this.defaultLaunchScreenStoryboard = false;
	}

	const defaultColor = this.defaultLaunchScreenStoryboard ? 'ffffff' : null,
		color = this.tiapp.ios['default-background-color'] || defaultColor;
	if (color) {
		const m = color.match(/^#?([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/);
		let c = m && m[1];
		if (c && (c.length === 3 || c.length === 6)) {
			if (c.length === 3) {
				c = c
					.split('')
					.map(function (b) {
						return String(b) + String(b);
					}).join('');
			}
			this.defaultBackgroundColor = {
				red: parseInt(c.substr(0, 2), 16) / 255,
				green: parseInt(c.substr(2, 2), 16) / 255,
				blue: parseInt(c.substr(4, 2), 16) / 255
			};
		} else {
			this.logger.warn(__('Invalid default background color "%s" in the <ios> section of the tiapp.xml', color));
			if (defaultColor) {
				this.logger.warn(__('Using default background color "%s"', '#' + defaultColor));
			}
		}
	}
};

iOSBuilder.prototype.findProvisioningProfile = function findProvisioningProfile(target, uuid) {
	const provisioning = this.iosInfo.provisioning;

	function getPP(type, uuid) {
		const list = provisioning[type];
		for (let i = 0; i < list.length; i++) {
			if (list[i].uuid === uuid) {
				list[i].getTaskAllow = !!list[i].getTaskAllow;
				list[i].type = type;
				return list[i];
			}
		}
	}

	switch (target) {
		case 'device':
			return getPP('development', uuid);
		case 'dist-appstore':
			return getPP('distribution', uuid);
		case 'dist-adhoc':
			return getPP('adhoc', uuid) || getPP('enterprise', uuid);
	}
};

iOSBuilder.prototype.determineLogServerPort = function determineLogServerPort(next) {
	this.tiLogServerPort = 0;

	if (/^dist-(appstore|adhoc)$/.test(this.target)) {
		// we don't allow the log server in production
		return next();
	}

	// if there's not an explicit <log-server-port> in the <ios> section of
	// the tiapp.xml, then we pick a port between 10000 and 60000 based on
	// the app's id. this is VERY prone to collisions, but we will show an
	// error if two different apps have been assigned the same port.
	this.tiLogServerPort = this.tiapp.ios['log-server-port'] || (parseInt(sha1(this.tiapp.id), 16) % 50000 + 10000);

	if (this.target === 'device') {
		return next();
	}

	const _t = this;

	this.logger.debug(__('Checking if log server port %d is available', this.tiLogServerPort));

	// for simulator builds, the port is shared with the local machine, so we
	// just need to detect if the port is available with the help of Node
	const server = net.createServer();

	server.on('error', function () {
		// we weren't able to bidn to the port :(
		server.close(function () {
			_t.logger.debug(__('Log server port %s is in use, testing if it\'s the app we\'re building', _t.tiLogServerPort));

			let client = null;

			function die() {
				client && client.destroy();
				_t.logger.error(__('Another process is currently bound to port %d', _t.tiLogServerPort));
				_t.logger.error(__('Set a unique <log-server-port> between 1024 and 65535 in the <ios> section of the tiapp.xml') + '\n');
				process.exit(1);
			}

			// connect to the port and see if it's a Titanium app...
			//  - if the port is bound by a Titanium app with the same appid, then assume
			//    that when we install new build, the old process will be terminated
			//  - if the port is bound by another process, such as MySQL on port 3306,
			//    then we will fail out
			//  - if the port is bound by another process that expects data before the
			//    response is returned, then we will just timeout and fail out
			client = net.connect({
				host: 'localhost',
				port: _t.tiLogServerPort,
				timeout: parseInt(_t.config.get('ios.logServerTestTimeout', 1000)) || null
			})
				.on('data', function (data) {
					client.destroy();
					try {
						const headers = JSON.parse(data.toString().split('\n').shift());
						if (headers.appId !== _t.tiapp.id) {
							_t.logger.error(__('Another Titanium app "%s" is currently running and using the log server port %d', headers.appId, _t.tiLogServerPort));
							_t.logger.error(__('Stop the running Titanium app, then rebuild this app'));
							_t.logger.error(__('-or-'));
							_t.logger.error(__('Set a unique <log-server-port> between 1024 and 65535 in the <ios> section of the tiapp.xml') + '\n');
							process.exit(1);
						}
					} catch (e) {
						die();
					}
					_t.logger.debug(__('The log server port is being used by the app being built, continuing'));
					next();
				})
				.on('error', die)
				.on('timeout', die);
		});
	});

	server.listen({
		host: 'localhost',
		port: _t.tiLogServerPort
	}, function () {
		server.close(function () {
			_t.logger.debug(__('Log server port %s is available', _t.tiLogServerPort));
			next();
		});
	});
};

iOSBuilder.prototype.loginfo = function loginfo() {
	this.logger.debug(__('Titanium SDK iOS directory: %s', cyan(this.platformPath)));
	this.logger.info(__('Deploy type: %s', cyan(this.deployType)));
	this.logger.info(__('Building for target: %s', cyan(this.target)));
	this.logger.info(__('Building using iOS SDK: %s', cyan(version.format(this.iosSdkVersion, 2))));

	if (this.buildOnly) {
		this.logger.info(__('Performing build only'));
	} else if (this.target === 'simulator') {
		this.logger.info(__('Building for iOS Simulator: %s', cyan(this.simHandle.name)));
		this.logger.debug(__('UDID: %s', cyan(this.simHandle.udid)));
		this.logger.debug(__('Simulator type: %s', cyan(this.simHandle.family)));
		this.logger.debug(__('Simulator version: %s', cyan(this.simHandle.version)));
	} else if (this.target === 'device') {
		this.logger.info(__('Building for iOS device: %s', cyan(this.deviceId)));
	}

	this.logger.info(__('Building for device family: %s', cyan(this.deviceFamily)));
	this.logger.debug(__('Setting Xcode target to %s', cyan(this.xcodeTarget)));
	this.logger.debug(__('Setting Xcode build OS to %s', cyan(this.xcodeTargetOS)));
	this.logger.debug(__('Xcode installation: %s', cyan(this.xcodeEnv.path)));
	this.logger.debug(__('iOS WWDR certificate: %s', cyan(this.iosInfo.certs.wwdr ? __('installed') : __('not found'))));

	if (this.target === 'device') {
		this.logger.info(__('iOS Development Certificate: %s', cyan(this.certDeveloperName)));
	} else if (/^dist-appstore|dist-adhoc$/.test(this.target)) {
		this.logger.info(__('iOS Distribution Certificate: %s', cyan(this.certDistributionName)));
	}
	this.logger.info(__('Team ID: %s', this.teamId ? cyan(this.teamId) : 'n/a'.grey));

	// validate the min-ios-ver from the tiapp.xml
	this.logger.info(__('Minimum iOS version: %s', cyan(version.format(this.minIosVer, 2, 3))));

	if (/^device|dist-appstore|dist-adhoc$/.test(this.target)) {
		if (this.keychain) {
			this.logger.info(__('Using keychain: %s', cyan(this.keychain)));
		} else {
			this.logger.info(__('Using default keychain'));
		}
	}

	if (this.tiLogServerPort) {
		this.logger.info(__('Logging enabled on port %s', cyan(String(this.tiLogServerPort))));
	} else {
		this.logger.info(__('Logging disabled'));
	}

	if (this.debugHost) {
		this.logger.info(__('Debugging enabled via debug host: %s', cyan(this.debugHost)));
	} else {
		this.logger.info(__('Debugging disabled'));
	}

	if (this.profilerHost) {
		this.logger.info(__('Profiler enabled via profiler host: %s', cyan(this.profilerHost)));
	} else {
		this.logger.info(__('Profiler disabled'));
	}

	if (this.symlinkFilesOnCopy) {
		this.logger.info(__('Set to symlink files instead of copying'));
	} else {
		this.logger.info(__('Set to copy files instead of symlinking'));
	}

	if (this.useAppThinning) {
		this.logger.info(__('App Thining is enabled'));
	} else {
		this.logger.info(__('App Thining is disabled'));
	}

	if (this.runOnMainThread) {
		this.logger.info(__('App runs on main thread'));
	}

	if (this.useBabel) {
		this.logger.info(__('JS files will be transformed with Babel'));
	}
};

iOSBuilder.prototype.readBuildManifest = function readBuildManifest() {
	this.buildManifestFile      = path.join(this.buildDir, 'build-manifest.json');
	// read the build manifest from the last build, if exists, so we
	// can determine if we need to do a full rebuild
	if (fs.existsSync(this.buildManifestFile)) {
		try {
			this.previousBuildManifest = JSON.parse(fs.readFileSync(this.buildManifestFile)) || {};
			if (this.cli.argv.xcode) {
				if (!process.env.TITANIUM_CLI_XCODEBUILD) {
					const manifest = this.previousBuildManifest;

					this.platformPath = manifest.iosSdkPath;
					this.target = manifest.target;
					this.deployType = manifest.deployType;
					this.sdkVersion = manifest.sdkVersion;
					this.target = manifest.target;
					this.target = manifest.target;
					this.forceCopy = manifest.forceCopy;
					this.skipJSMinification = manifest.skipJSMinification;
					this.encryptJS = manifest.encryptJS;
					this.forceCopyAll = manifest.forceCopyAll;
					this.target = manifest.target;
					this.iosSdkVersion = manifest.iosSdkVersion;
					this.deviceFamily = manifest.deviceFamily;
					this.developerName = manifest.developerName;
					this.distributionName = manifest.distributionName;
					this.provisioningProfileUUID = manifest.ppUuid;
					this.useJSCore = manifest.useJSCore;
					this.runOnMainThread = manifest.runOnMainThread;
					this.cli.argv['output-dir'] = manifest.outputDir;
					this.useBabel = manifest.useBabel;
					this.useAutoLayout = manifest.useAutoLayout;
					this.useAppThinning = manifest.useAppThinning;
					return; // so that we don't remove the build manifest on error
				}
			}
		} catch (ex) {
			this.logger.error(__('readBuildManifest error'));
			ex.message.split('\n').forEach(this.logger.error);
			this.logger.log();
		}
	}

	this.unmarkBuildDirFile(this.buildManifestFile);

	// now that we've read the build manifest, delete it so if this build
	// becomes incomplete, the next build will be a full rebuild
	fs.existsSync(this.buildManifestFile) && fs.unlinkSync(this.buildManifestFile);

};

iOSBuilder.prototype.checkIfNeedToRecompile = function checkIfNeedToRecompile() {
	if (this.cli.argv.xcode) {
		return false;
	}
	const manifest = this.previousBuildManifest;

	// check if we need to clean the build directory
	this.forceCleanBuild = function () {
		// check if the --force flag was passed in
		if (this.cli.argv.force) {
			this.logger.info(__('Forcing clean build: %s flag was set', cyan('--force')));
			return true;
		}

		// check if the build manifest file was read
		if (!Object.keys(this.previousBuildManifest).length) {
			this.logger.info(__('Forcing clean build: %s does not exist', cyan(this.buildManifestFile)));
			return true;
		}

		// check the <sdk-version> from the tiapp.xml
		if (!appc.version.eq(this.tiapp['sdk-version'], manifest.sdkVersion)) {
			this.logger.info(__('Forcing rebuild: tiapp.xml Titanium SDK version changed since last build'));
			this.logger.info('  ' + __('Was: %s', cyan(manifest.sdkVersion)));
			this.logger.info('  ' + __('Now: %s', cyan(this.tiapp['sdk-version'])));
			return true;
		}

		// check if the titanium sdk version changed
		if (fs.existsSync(this.xcodeProjectConfigFile)) {
			// we have a previous build, see if the Titanium SDK changed
			const conf = fs.readFileSync(this.xcodeProjectConfigFile).toString(),
				versionMatch = conf.match(/TI_VERSION=([^\n]*)/);

			if (versionMatch && !appc.version.eq(versionMatch[1], this.titaniumSdkVersion)) {
				this.logger.info(__('Forcing rebuild: Titanium SDK version in the project.xcconfig changed since last build'));
				this.logger.info('  ' + __('Was: %s', cyan(versionMatch[1])));
				this.logger.info('  ' + __('Now: %s', cyan(this.titaniumSdkVersion)));
				return true;
			}
		}

		return false;
	}.call(this);

	if (this.cli.argv.xcode) {
		this.forceCleanBuild = false;
		this.forceRebuild = false;
		return false;
	}
	if (!this.forceCleanBuild && this.deployType !== 'development') {
		this.logger.info(__('Forcing rebuild: deploy type is %s, so need to recompile ApplicationRouting.m', this.deployType));
		this.forceCleanBuild = true;
	}

	// if true, this will cause xcodebuild to be called
	// if false, it's possible that other steps after this will force xcodebuild to be called
	this.forceRebuild = this.forceCleanBuild || function () {
		// check if the xcode app directory exists
		if (!fs.existsSync(this.xcodeAppDir)) {
			this.logger.info(__('Forcing rebuild: %s does not exist', cyan(this.xcodeAppDir)));
			return true;
		}

		// check if the --force-copy or --force-copy-all flags were set
		if (this.forceCopy !== manifest.forceCopy) {
			this.logger.info(__('Forcing rebuild: force copy flag changed since last build'));
			this.logger.info('  ' + __('Was: %s', cyan(manifest.forceCopy)));
			this.logger.info('  ' + __('Now: %s', cyan(this.forceCopy)));
			return true;
		}

		if (this.forceCopyAll !== manifest.forceCopyAll) {
			this.logger.info(__('Forcing rebuild: force copy all flag changed since last build'));
			this.logger.info('  ' + __('Was: %s', cyan(manifest.forceCopyAll)));
			this.logger.info('  ' + __('Now: %s', cyan(this.forceCopyAll)));
			return true;
		}

		// check if the target changed
		if (this.target !== manifest.target) {
			this.logger.info(__('Forcing rebuild: target changed since last build'));
			this.logger.info('  ' + __('Was: %s', cyan(manifest.target)));
			this.logger.info('  ' + __('Now: %s', cyan(this.target)));
			return true;
		}

		if (this.target === 'dist-adhoc' || this.target === 'dist-appstore') {
			this.logger.info(__('Forcing rebuild: distribution builds require \'xcodebuild\' to be run so that resources are copied into the archive'));
			return true;
		}

		if (fs.existsSync(this.xcodeProjectConfigFile)) {
			// we have a previous build, see if the app id changed
			const conf = fs.readFileSync(this.xcodeProjectConfigFile).toString(),
				idMatch = conf.match(/TI_APPID=([^\n]*)/);

			if (idMatch && idMatch[1] !== this.tiapp.id) {
				this.logger.info(__('Forcing rebuild: app id changed since last build'));
				this.logger.info('  ' + __('Was: %s', cyan(idMatch[1])));
				this.logger.info('  ' + __('Now: %s', cyan(this.tiapp.id)));
				return true;
			}
		}

		// check that we have a libTiCore hash
		if (!manifest.tiCoreHash) {
			this.logger.info(__('Forcing rebuild: incomplete version file %s', cyan(this.buildVersionFile)));
			return true;
		}

		// determine the libTiCore hash and check if the libTiCore hashes are different
		if (this.libTiCoreHash !== manifest.tiCoreHash) {
			this.logger.info(__('Forcing rebuild: libTiCore hash changed since last build'));
			this.logger.info('  ' + __('Was: %s', cyan(manifest.tiCoreHash)));
			this.logger.info('  ' + __('Now: %s', cyan(this.libTiCoreHash)));
			return true;
		}

		// check if the titanium sdk paths are different
		if (manifest.iosSdkPath !== this.platformPath) {
			this.logger.info(__('Forcing rebuild: Titanium SDK path changed since last build'));
			this.logger.info('  ' + __('Was: %s', cyan(manifest.iosSdkPath)));
			this.logger.info('  ' + __('Now: %s', cyan(this.platformPath)));
			return true;
		}

		// check if the iOS SDK has changed
		if (manifest.iosSdkVersion !== this.iosSdkVersion) {
			this.logger.info(__('Forcing rebuild: iOS SDK version changed since last build'));
			this.logger.info('  ' + __('Was: %s', cyan(manifest.iosSdkVersion)));
			this.logger.info('  ' + __('Now: %s', cyan(this.iosSdkVersion)));
			return true;
		}

		// check if the device family has changed (i.e. was universal, now iphone)
		if (manifest.deviceFamily !== this.deviceFamily) {
			this.logger.info(__('Forcing rebuild: device family changed since last build'));
			this.logger.info('  ' + __('Was: %s', cyan(manifest.deviceFamily)));
			this.logger.info('  ' + __('Now: %s', cyan(this.deviceFamily)));
			return true;
		}

		// check the git hashes are different
		if (!manifest.gitHash || manifest.gitHash !== ti.manifest.githash) {
			this.logger.info(__('Forcing rebuild: githash changed since last build'));
			this.logger.info('  ' + __('Was: %s', cyan(manifest.gitHash)));
			this.logger.info('  ' + __('Now: %s', cyan(ti.manifest.githash)));
			return true;
		}

		// determine the modules hash and check if the modules hashes has changed
		if (this.modulesHash !== manifest.modulesHash) {
			this.logger.info(__('Forcing rebuild: modules hash changed since last build'));
			this.logger.info('  ' + __('Was: %s', cyan(manifest.modulesHash)));
			this.logger.info('  ' + __('Now: %s', cyan(this.modulesHash)));
			return true;
		}

		// check if the native modules hashes has changed
		if (this.modulesNativeHash !== manifest.modulesNativeHash) {
			this.logger.info(__('Forcing rebuild: native modules hash changed since last build'));
			this.logger.info('  ' + __('Was: %s', manifest.modulesNativeHash));
			this.logger.info('  ' + __('Now: %s', this.modulesNativeHash));
			return true;
		}

		// check if the provisioning profile has changed
		if (this.provisioningProfileUUID !== manifest.ppUuid) {
			this.logger.info(__('Forcing rebuild: provisioning profile changed since last build'));
			this.logger.info('  ' + __('Was: %s', manifest.ppUuid));
			this.logger.info('  ' + __('Now: %s', this.provisioningProfileUUID));
			return true;
		}

		// check if the use JavaScriptCore flag has changed
		if (this.useJSCore !== manifest.useJSCore) {
			this.logger.info(__('Forcing rebuild: use JSCore flag changed since last build'));
			this.logger.info('  ' + __('Was: %s', manifest.useJSCore));
			this.logger.info('  ' + __('Now: %s', this.useJSCore));
			return true;
		}

		// check if the use RunOnMainThread flag has changed
		if (this.runOnMainThread !== manifest.runOnMainThread) {
			this.logger.info(__('Forcing rebuild: use RunOnMainThread flag changed since last build'));
			this.logger.info('  ' + __('Was: %s', manifest.runOnMainThread));
			this.logger.info('  ' + __('Now: %s', this.runOnMainThread));
			return true;
		}

		// check if the use useBabel flag has changed
		if (this.useBabel !== manifest.useBabel) {
			this.logger.info(__('Forcing rebuild: use useBabel flag changed since last build'));
			this.logger.info('  ' + __('Was: %s', manifest.useBabel));
			this.logger.info('  ' + __('Now: %s', this.useBabel));
			return true;
		}

		// check if the use package.json has changed
		if (this.packageJSONHash !== manifest.packageJSONHash) {
			this.logger.info(__('Forcing rebuild: package.json changed since last build'));
			this.logger.info('  ' + __('Was: %s', manifest.packageJSONHash));
			this.logger.info('  ' + __('Now: %s', this.packageJSONHash));
			return true;
		}

		// check if the use UserAutoLayout flag has changed
		if (this.useAutoLayout !== manifest.useAutoLayout) {
			this.logger.info(__('Forcing rebuild: use UserAutoLayout flag changed since last build'));
			this.logger.info('  ' + __('Was: %s', manifest.useAutoLayout));
			this.logger.info('  ' + __('Now: %s', this.useAutoLayout));
			return true;
		}

		// check if the use use-app-thinning flag has changed
		if (this.useAppThinning !== manifest.useAppThinning) {
			this.logger.info(__('Forcing rebuild: use use-app-thinning flag changed since last build'));
			this.logger.info('  ' + __('Was: %s', manifest.useAppThinning));
			this.logger.info('  ' + __('Now: %s', this.useAppThinning));
			return true;
		}

		// check if the showErrorController flag has changed
		if (this.showErrorController !== manifest.showErrorController) {
			this.logger.info(__('Forcing rebuild: showErrorController flag changed since last build'));
			this.logger.info('  ' + __('Was: %s', manifest.showErrorController));
			this.logger.info('  ' + __('Now: %s', this.showErrorController));
			return true;
		}

		// next we check if any tiapp.xml values changed so we know if we need to reconstruct the main.m
		// note: as soon as these tiapp.xml settings are written to an encrypted file instead of the binary, we can remove this whole section
		const tiappSettings = {
			'name':        'project name',
			'id':          'app id',
			'analytics':   'analytics flag',
			'publisher':   'publisher',
			'url':         'url',
			'version':     'version',
			'description': 'description',
			'copyright':   'copyright',
			'guid':        'guid'
		};
		let changed = null;

		Object.keys(tiappSettings).some(function (key) {
			if (this.tiapp[key] !== manifest[key]) {
				changed = key;
				return true;
			}
			return false;
		}, this);

		if (changed) {
			this.logger.info(__('Forcing rebuild: tiapp.xml %s changed since last build', tiappSettings[changed]));
			this.logger.info('  ' + __('Was: %s', cyan(manifest[changed])));
			this.logger.info('  ' + __('Now: %s', cyan(this.tiapp[changed])));
			return true;
		}

		return false;
	}.call(this);
};

iOSBuilder.prototype.initBuildDir = function initBuildDir() {
	this.logger.info(__('Initializing the build directory'));

	const buildDirExists = fs.existsSync(this.buildDir);

	if (this.forceCleanBuild && buildDirExists) {
		this.logger.debug(__('Recreating %s', cyan(this.buildDir)));
		wrench.rmdirSyncRecursive(this.buildDir);
		wrench.mkdirSyncRecursive(this.buildDir);
	} else if (!buildDirExists) {
		this.logger.debug(__('Creating %s', cyan(this.buildDir)));
		wrench.mkdirSyncRecursive(this.buildDir);
		this.forceCleanBuild = true;
	}

	fs.existsSync(this.xcodeAppDir) || wrench.mkdirSyncRecursive(this.xcodeAppDir);
};

iOSBuilder.prototype.generateXcodeUuid = function generateXcodeUuid(xcodeProject) {
	// normally we would want truly unique ids, but we want predictability so that we
	// can detect when the project has changed and if we need to rebuild the app
	if (!this.xcodeUuidIndex) {
		this.xcodeUuidIndex = 1;
	}
	const id = appc.string.lpad(this.xcodeUuidIndex++, 24, '0');
	if (xcodeProject && xcodeProject.allUuids().indexOf(id) >= 0) {
		return this.generateXcodeUuid(xcodeProject);
	} else {
		return id;
	}
};

iOSBuilder.prototype.createXcodeProject = function createXcodeProject(next) {
	if (!this.forceRebuild || this.cli.argv.xcode) {
		delete this.buildDirFiles[path.join(this.buildDir, this.tiapp.name + '.xcodeproj', 'project.pbxproj')];
		return next();
	}
	this.logger.info(__('Creating Xcode project'));

	const appName = this.tiapp.name,
		scrubbedAppName = appName.replace(/[-\W]/g, '_'),
		srcFile = path.join(this.platformPath, 'iphone', 'Titanium.xcodeproj', 'project.pbxproj'),
		contents = fs.readFileSync(srcFile).toString(),
		xcodeProject = xcode.project(path.join(this.buildDir, this.tiapp.name + '.xcodeproj', 'project.pbxproj')),
		relPathRegExp = /\.\.\/(Classes|Resources|headers|lib|external)/;

	xcodeProject.hash = xcodeParser.parse(contents);
	const xobjs = xcodeProject.hash.project.objects;

	if (appc.version.lt(this.xcodeEnv.version, '7.0.0')) {
		this.logger.info(__('LaunchScreen.storyboard is not supported with Xcode %s, removing from Xcode project', this.xcodeEnv.version));
	}

	function removeFromBuildPhase(phase, id) {
		if (phase) {
			Object.keys(phase).some(function (phaseId) {
				const files = phase[phaseId].files;
				if (Array.isArray(files)) {
					for (let i = 0; i < files.length; i++) {
						if (files[i].value === id) {
							files.splice(i, 1);
							return true;
						}
					}
				}
				return false;
			});
		}
	}

	// we need to replace all instances of "Titanium" with the app name
	Object.keys(xobjs.PBXFileReference).forEach(function (id) {
		var obj = xobjs.PBXFileReference[id];
		if (obj && typeof obj === 'object') {
			if (obj.path === 'Titanium_Prefix.pch') {
				obj.path = xobjs.PBXFileReference[id + '_comment'] = scrubbedAppName + '_Prefix.pch';
			} else if (obj.path === 'Titanium.plist') {
				obj.path = xobjs.PBXFileReference[id + '_comment'] = 'Info.plist';
			} else if (obj.path === 'Titanium.entitlements') {
				obj.path = xobjs.PBXFileReference[id + '_comment'] = '"' + appName + '.entitlements"';
			} else if (obj.path === 'Titanium.app') {
				obj.path = xobjs.PBXFileReference[id + '_comment'] = '"' + appName + '.app"';
			} else if (relPathRegExp.test(obj.path)) {
				obj.path = obj.path.replace(relPathRegExp, '$1');
			} else if (obj.path === 'LaunchScreen.storyboard' && appc.version.lt(this.xcodeEnv.version, '7.0.0')) {
				delete xobjs.PBXFileReference[id];

				// remove the LaunchScreen.storyboard BuildFile and BuildPhase records
				Object.keys(xobjs.PBXBuildFile).some(function (bfid) {
					if (typeof xobjs.PBXBuildFile[bfid] === 'object' && xobjs.PBXBuildFile[bfid].fileRef === id) {
						delete xobjs.PBXBuildFile[bfid];
						delete xobjs.PBXBuildFile[bfid + '_comment'];

						removeFromBuildPhase(xobjs.PBXResourcesBuildPhase, bfid);
						removeFromBuildPhase(xobjs.PBXFrameworksBuildPhase, bfid);

						return true;
					}
					return false;
				});
			}
		}
	}, this);

	Object.keys(xobjs.PBXGroup).forEach(function (id) {
		const obj = xobjs.PBXGroup[id];
		if (obj && typeof obj === 'object') {
			if (obj.children) {
				for (let i = 0; i < obj.children.length; i++) {
					const child = obj.children[i];
					if (child.comment === 'Titanium_Prefix.pch') {
						child.comment = scrubbedAppName + '_Prefix.pch';
					} else if (child.comment === 'Titanium.plist') {
						child.comment = 'Info.plist';
					} else if (child.comment === 'Titanium.app') {
						child.comment = '"' + appName + '.app"';
					} else if (child.comment === 'Titanium.entitlements') {
						child.comment = '"' + appName + '.entitlements"';
					} else if (child.comment === 'LaunchScreen.storyboard' && appc.version.lt(this.xcodeEnv.version, '7.0.0')) {
						obj.children.splice(i--, 1);
					}
				}
			}
			if (obj.path && relPathRegExp.test(obj.path)) {
				obj.path = obj.path.replace(relPathRegExp, '$1');
			}
		}
	}, this);

	Object.keys(xobjs.PBXNativeTarget).forEach(function (id) {
		const obj = xobjs.PBXNativeTarget[id];
		if (obj && typeof obj === 'object') {
			Object.keys(obj).forEach(function (key) {
				if (obj[key] && typeof obj[key] === 'string' && obj[key].indexOf('Titanium') !== -1) {
					obj[key] = xobjs.PBXNativeTarget[id + '_comment'] = '"' + obj[key].replace(/Titanium/g, appName).replace(/^"/, '').replace(/"$/, '') + '"';
				}
			});
		}
	});

	Object.keys(xobjs.PBXProject).forEach(function (id) {
		const obj = xobjs.PBXProject[id];
		if (obj && typeof obj === 'object') {
			obj.buildConfigurationList_comment = '"' + obj.buildConfigurationList_comment.replace(/Titanium/g, appName).replace(/^"/, '').replace(/"$/, '') + '"';
			obj.targets.forEach(function (item) {
				item.comment = '"' + item.comment.replace(/Titanium/g, appName).replace(/^"/, '').replace(/"$/, '') + '"';
			});
		}
	});

	Object.keys(xobjs.XCBuildConfiguration).forEach(function (id) {
		const obj = xobjs.XCBuildConfiguration[id];
		if (obj && typeof obj === 'object' && obj.buildSettings) {
			if (obj.buildSettings.GCC_PREFIX_HEADER === 'Titanium_Prefix.pch') {
				obj.buildSettings.GCC_PREFIX_HEADER = scrubbedAppName + '_Prefix.pch';
			}
			if (obj.buildSettings.INFOPLIST_FILE === 'Titanium.plist') {
				obj.buildSettings.INFOPLIST_FILE = 'Info.plist';
			}
			if (obj.buildSettings.PRODUCT_NAME === 'Titanium') {
				obj.buildSettings.PRODUCT_NAME = '"' + appName + '"';
			}
			if (Array.isArray(obj.buildSettings.FRAMEWORK_SEARCH_PATHS)) {
				obj.buildSettings.FRAMEWORK_SEARCH_PATHS.forEach(function (item, i, arr) {
					arr[i] = item.replace(relPathRegExp, '$1');
				});
			}
			if (Array.isArray(obj.buildSettings.LIBRARY_SEARCH_PATHS)) {
				obj.buildSettings.LIBRARY_SEARCH_PATHS.forEach(function (item, i, arr) {
					arr[i] = item.replace(relPathRegExp, '$1');
				});
			}
			if (Array.isArray(obj.buildSettings.HEADER_SEARCH_PATHS)) {
				obj.buildSettings.HEADER_SEARCH_PATHS.forEach(function (item, i, arr) {
					arr[i] = item.replace(relPathRegExp, '$1');
				});
			}
		}
	});

	Object.keys(xobjs.XCConfigurationList).forEach(function (id) {
		if (xobjs.XCConfigurationList[id] && typeof xobjs.XCConfigurationList[id] === 'string') {
			xobjs.XCConfigurationList[id] = xobjs.XCConfigurationList[id].replace(/Titanium/g, appName);
		}
	});

	// delete the pre-compile build phases since we don't need it
	this.logger.trace(__('Removing pre-compile phase'));
	const that = this;
	Object.keys(xobjs.PBXShellScriptBuildPhase).forEach(function (buildPhaseUuid) {
		if (xobjs.PBXShellScriptBuildPhase[buildPhaseUuid] && typeof xobjs.PBXShellScriptBuildPhase[buildPhaseUuid]
			=== 'object' && /^"?Pre-Compile"?$/i.test(xobjs.PBXShellScriptBuildPhase[buildPhaseUuid].name)) {

			// Object.keys(xobjs.PBXNativeTarget).forEach(function(key) {
			// 	if (xobjs.PBXNativeTarget[key] && typeof xobjs.PBXNativeTarget[key] === 'object') {
			// 		xobjs.PBXNativeTarget[key].buildPhases = xobjs.PBXNativeTarget[key].buildPhases.filter(function(phase) {
			// 			return phase.value !== buildPhaseUuid;
			// 		});
			// 	}
			// });

			xobjs.PBXShellScriptBuildPhase[buildPhaseUuid].shellScript = '"export TITANIUM_PREFIX=\\"_Prefix-*\\"\\n'
				+ 'echo \\"Xcode Pre-Compile Phase: Removing $SHARED_PRECOMPS_DIR/$PROJECT$TITANIUM_PREFIX\\"\\n'
				+ 'find \\"$SHARED_PRECOMPS_DIR\\" -name \\"$PROJECT$TITANIUM_PREFIX\\" -print0 | xargs -0 rm -rf\\n'
				+ 'if [ \\"x$TITANIUM_CLI_XCODEBUILD\\" == \\"x\\" ]; then\\n'
				+ '    ' + (process.execPath || 'node') + ' \\"' + that.cli.argv.$0.replace(/^(.+\/)*node /, '') + '\\" build --platform ' + that.platformName + ' --sdk \\"' + that.titaniumSdkName + '\\" --no-prompt --no-progress-bars --no-banner --no-colors --build-only --xcode\\n'
				+ '    exit $?\\n'
				+ 'else\\n'
				+ '    echo \\"skipping pre-compile phase\\"\\n'
				+ 'fi"';

			// delete xobjs.PBXShellScriptBuildPhase[buildPhaseUuid];
			// delete xobjs.PBXShellScriptBuildPhase[buildPhaseUuid + '_comment'];
		}
	});

	const projectUuid = xcodeProject.hash.project.rootObject,
		pbxProject = xobjs.PBXProject[projectUuid],
		mainTargetUuid = pbxProject.targets.filter(function (t) { return t.comment.replace(/^"/, '').replace(/"$/, '') === appName; })[0].value,
		mainGroupChildren = xobjs.PBXGroup[pbxProject.mainGroup].children,
		extensionsGroup = xobjs.PBXGroup[mainGroupChildren.filter(function (child) { return child.comment === 'Extensions'; })[0].value],
		frameworksGroup = xobjs.PBXGroup[mainGroupChildren.filter(function (child) { return child.comment === 'Frameworks'; })[0].value],
		resourcesGroup = xobjs.PBXGroup[mainGroupChildren.filter(function (child) { return child.comment === 'Resources'; })[0].value],
		productsGroup = xobjs.PBXGroup[mainGroupChildren.filter(function (child) { return child.comment === 'Products'; })[0].value],
		frameworksBuildPhase = xobjs.PBXFrameworksBuildPhase[
			xobjs.PBXNativeTarget[mainTargetUuid].buildPhases
				.filter(function (phase) {
					return xobjs.PBXFrameworksBuildPhase[phase.value];
				})[0].value
		],
		resourcesBuildPhase = xobjs.PBXResourcesBuildPhase[xobjs.PBXNativeTarget[mainTargetUuid].buildPhases.filter(function (phase) { return xobjs.PBXResourcesBuildPhase[phase.value]; })[0].value],
		caps = this.tiapp.ios.capabilities,
		gccDefs = [ 'DEPLOYTYPE=' + this.deployType ],
		buildSettings = {
			IPHONEOS_DEPLOYMENT_TARGET: appc.version.format(this.minIosVer, 2),
			TARGETED_DEVICE_FAMILY: '"' + this.deviceFamilies[this.deviceFamily] + '"',
			// ONLY_ACTIVE_ARCH: 'NO',
			DEAD_CODE_STRIPPING: 'YES',
			SDKROOT: 'iphoneos',
			CODE_SIGN_ENTITLEMENTS: '"' + appName + '.entitlements"'
		},
		legacySwift = version.lt(this.xcodeEnv.version, '8.0.0');

	// set additional build settings
	if (this.target === 'simulator') {
		gccDefs.push('DEBUG=1');
		gccDefs.push('TI_VERSION=' + this.titaniumSdkVersion);
	} else if (this.deployType === 'development') {
		gccDefs.push('DEBUG=1');
	} else {
		buildSettings.ONLY_ACTIVE_ARCH = 'NO';
	}

	if (/simulator|device|dist-adhoc/.test(this.target) && this.tiapp.ios.enablecoverage) {
		gccDefs.push('KROLL_COVERAGE=1');
	}

	if (this.enableLaunchScreenStoryboard) {
		gccDefs.push('LAUNCHSCREEN_STORYBOARD=1');
	}

	if (this.defaultBackgroundColor) {
		gccDefs.push(
			'DEFAULT_BGCOLOR_RED=' + this.defaultBackgroundColor.red,
			'DEFAULT_BGCOLOR_GREEN=' + this.defaultBackgroundColor.green,
			'DEFAULT_BGCOLOR_BLUE=' + this.defaultBackgroundColor.blue
		);
	}

	if (this.tiLogServerPort === 0) {
		gccDefs.push('DISABLE_TI_LOG_SERVER=1');
	} else {
		gccDefs.push('TI_LOG_SERVER_PORT=' + this.tiLogServerPort);
	}

	buildSettings.GCC_PREPROCESSOR_DEFINITIONS = '"' + gccDefs.join(' ') + '"';

	if (/device|dist-appstore|dist-adhoc/.test(this.target)) {
		buildSettings.DEPLOYMENT_POSTPROCESSING = 'YES';
		if (this.keychain) {
			buildSettings.OTHER_CODE_SIGN_FLAGS = '"--keychain ' + this.keychain + '"';
		}
	}

	// add the post-compile build phase for dist-appstore builds
	if (this.target === 'dist-appstore' || this.target === 'dist-adhoc') {
		buildSettings.CODE_SIGN_IDENTITY = '"iPhone Distribution"';
		buildSettings.CODE_SIGN_STYLE = 'Manual';

		xobjs.PBXShellScriptBuildPhase || (xobjs.PBXShellScriptBuildPhase = {});
		const buildPhaseUuid = this.generateXcodeUuid(xcodeProject);
		const name = 'Copy Resources to Archive';

		xobjs.PBXNativeTarget[mainTargetUuid].buildPhases.push({
			value: buildPhaseUuid,
			comment: '"' + name + '"'
		});

		xobjs.PBXShellScriptBuildPhase[buildPhaseUuid] = {
			isa: 'PBXShellScriptBuildPhase',
			buildActionMask: 2147483647,
			files: [],
			inputPaths: [],
			name: '"' + name + '"',
			outputPaths: [],
			runOnlyForDeploymentPostprocessing: 0,
			shellPath: '/bin/sh',
			shellScript: '"cp -rf \\"$PROJECT_DIR/ArchiveStaging\\"/ \\"$TARGET_BUILD_DIR/$PRODUCT_NAME.app/\\""',
			showEnvVarsInLog: 0
		};
		xobjs.PBXShellScriptBuildPhase[buildPhaseUuid + '_comment'] = '"' + name + '"';
	} else if (this.target === 'device') {
		buildSettings.CODE_SIGN_IDENTITY = '"iPhone Developer: ' + this.certDeveloperName + '"';
		buildSettings.CODE_SIGN_STYLE = 'Manual';
	}

	// inject the team id and app groups
	if (this.provisioningProfile) {
		const attr = pbxProject.attributes || (pbxProject.attributes = {});
		const targetAttr = attr.TargetAttributes || (attr.TargetAttributes = {});
		const mainTargetAttr = targetAttr[mainTargetUuid] || (targetAttr[mainTargetUuid] = {});

		mainTargetAttr.DevelopmentTeam = this.teamId;

		// turn on any capabilities
		Object.keys(caps).forEach(function (cap) {
			if (cap === 'app-groups') {
				const syscaps = mainTargetAttr.SystemCapabilities || (mainTargetAttr.SystemCapabilities = {});
				syscaps['com.apple.ApplicationGroups.iOS'] || (syscaps['com.apple.ApplicationGroups.iOS'] = {});
				syscaps['com.apple.ApplicationGroups.iOS'].enabled = 1;
			}
		});
	}

	// set the min ios version for the whole project
	xobjs.XCConfigurationList[pbxProject.buildConfigurationList].buildConfigurations.forEach(function (buildConf) {
		const buildSettings = xobjs.XCBuildConfiguration[buildConf.value].buildSettings;
		buildSettings.IPHONEOS_DEPLOYMENT_TARGET = appc.version.format(this.minIosVer, 2);
		delete buildSettings['"CODE_SIGN_IDENTITY[sdk=iphoneos*]"'];
	}, this);

	// set the target-specific build settings
	xobjs.XCConfigurationList[xobjs.PBXNativeTarget[mainTargetUuid].buildConfigurationList].buildConfigurations.forEach(function (buildConf) {
		const bs = appc.util.mix(xobjs.XCBuildConfiguration[buildConf.value].buildSettings, buildSettings);
		delete bs['"CODE_SIGN_IDENTITY[sdk=iphoneos*]"'];

		bs.PRODUCT_BUNDLE_IDENTIFIER = '"' + this.tiapp.id + '"';

		if (this.provisioningProfile) {
			bs.DEVELOPMENT_TEAM = this.teamId;
			bs.PROVISIONING_PROFILE = '"' + this.provisioningProfile.uuid + '"';
			bs.PROVISIONING_PROFILE_SPECIFIER = '"' + this.provisioningProfile.name + '"';
		}
	}, this);

	// if the storyboard launch screen is disabled, remove it from the resources build phase
	if (!this.enableLaunchScreenStoryboard) {
		for (let i = 0; i < resourcesBuildPhase.files.length; i++) {
			if (xobjs.PBXBuildFile[resourcesBuildPhase.files[i].value].fileRef_comment === 'LaunchScreen.storyboard') {
				resourcesBuildPhase.files.splice(i, 1);
				break;
			}
		}
	}

	// if we have a Settings.bundle, add it to the project
	[ 'ios', 'iphone' ].some(function (name) {
		const settingsBundleDir = path.join(this.projectDir, 'platform', name, 'Settings.bundle');
		if (!fs.existsSync(settingsBundleDir) || !fs.statSync(settingsBundleDir).isDirectory()) {
			return false;
		}

		const fileRefUuid = this.generateXcodeUuid(xcodeProject),
			buildFileUuid = this.generateXcodeUuid(xcodeProject);

		// add the file reference
		xobjs.PBXFileReference[fileRefUuid] = {
			isa: 'PBXFileReference',
			lastKnownFileType: 'wrapper.plug-in',
			path: 'Settings.bundle',
			sourceTree: '"<group>"'
		};
		xobjs.PBXFileReference[fileRefUuid + '_comment'] = 'Settings.bundle';

		// add the build file
		xobjs.PBXBuildFile[buildFileUuid] = {
			isa: 'PBXBuildFile',
			fileRef: fileRefUuid,
			fileRef_comment: 'Settings.bundle'
		};
		xobjs.PBXBuildFile[buildFileUuid + '_comment'] = 'Settings.bundle in Resources';

		// add the resources build phase
		resourcesBuildPhase.files.push({
			value: buildFileUuid,
			comment: 'Settings.bundle in Resources'
		});

		// add to resouces group
		resourcesGroup.children.push({
			value: fileRefUuid,
			comment: 'Settings.bundle'
		});

		return true;
	}, this);

	let deploymentTarget = this.minIosVer;
	let hasSwift = false;
	// add the native libraries to the project
	if (this.nativeLibModules.length) {
		this.logger.trace(__n('Adding %%d native module library', 'Adding %%d native module libraries', this.nativeLibModules.length === 1 ? 1 : 2, this.nativeLibModules.length));
		this.nativeLibModules.forEach(function (lib) {
			const fileRefUuid = this.generateXcodeUuid(xcodeProject),
				buildFileUuid = this.generateXcodeUuid(xcodeProject);

			// add the file reference
			xobjs.PBXFileReference[fileRefUuid] = {
				isa: 'PBXFileReference',
				lastKnownFileType: 'archive.ar',
				name: lib.libName,
				path: '"' + lib.libFile + '"',
				sourceTree: '"<absolute>"'
			};
			xobjs.PBXFileReference[fileRefUuid + '_comment'] = lib.libName;

			// add the library to the Frameworks group
			frameworksGroup.children.push({
				value: fileRefUuid,
				comment: lib.libName
			});

			// add the build file
			xobjs.PBXBuildFile[buildFileUuid] = {
				isa: 'PBXBuildFile',
				fileRef: fileRefUuid,
				fileRef_comment: lib.libName
			};
			xobjs.PBXBuildFile[buildFileUuid + '_comment'] = lib.libName + ' in Frameworks';

			// add the library to the frameworks build phase
			frameworksBuildPhase.files.push({
				value: buildFileUuid,
				comment: lib.libName + ' in Frameworks'
			});

			// add the library to the search paths
			xobjs.XCConfigurationList[xobjs.PBXNativeTarget[mainTargetUuid].buildConfigurationList].buildConfigurations.forEach(function (buildConf) {
				var buildSettings = xobjs.XCBuildConfiguration[buildConf.value].buildSettings;
				buildSettings.LIBRARY_SEARCH_PATHS || (buildSettings.LIBRARY_SEARCH_PATHS = [ '"$(inherited)"' ]);
				buildSettings.LIBRARY_SEARCH_PATHS.push('"\\"' + path.dirname(lib.libFile) + '\\""');
			});

			let embedBuildPhase;
			function getEmbedFrameworksBuildPhase() {
				if (embedBuildPhase) {
					return embedBuildPhase;
				}
				const name = 'Embed Frameworks';
				const embedExtPhase = xobjs.PBXNativeTarget[mainTargetUuid].buildPhases.filter(function (phase) {
					return phase.comment === name;
				}).shift();
				let embedUuid = embedExtPhase && embedExtPhase.value;
				this.logger.trace(__('getEmbedFrameworksBuildPhase %s', embedUuid));
				if (!embedUuid) {
					embedUuid = this.generateXcodeUuid(xcodeProject);
					xobjs.PBXNativeTarget[mainTargetUuid].buildPhases.push({
						value: embedUuid,
						comment: name
					});
					xobjs.PBXCopyFilesBuildPhase || (xobjs.PBXCopyFilesBuildPhase = {});
					embedBuildPhase = xobjs.PBXCopyFilesBuildPhase[embedUuid] = {
						isa: 'PBXCopyFilesBuildPhase',
						buildActionMask: 2147483647,
						dstPath: '""',
						dstSubfolderSpec: 10,
						files: [],
						name: '"' + name + '"',
						runOnlyForDeploymentPostprocessing: 0
					};
					xobjs.PBXCopyFilesBuildPhase[embedUuid + '_comment'] = name;
				} else {
					embedBuildPhase = xobjs.PBXCopyFilesBuildPhase[embedUuid];
				}
				return embedBuildPhase;
			}
			// looking for swift frameworks
			const frameworksToCheck = lib.embeddedFrameworks;
			frameworksToCheck.forEach(function (fullPath) {
				var framework = path.basename(fullPath);
				var frameworkName = framework.replace(/\.framework$/, '');
				// var fullPath = path.join(lib.modulePath, 'platform', framework);
				hasSwift  = true;
				this.logger.trace(__('handling embedded framework %s, %s, %s', framework, frameworkName, fullPath));
				if (appc.version.lt(deploymentTarget, '8.0')) {
					deploymentTarget = '8.0';
				}
				const embedBuildPhase = getEmbedFrameworksBuildPhase.call(this);
				if (!embedBuildPhase) {
					return;
				}
				const fileRefUuid = this.generateXcodeUuid(xcodeProject);
				let buildFileUuid = this.generateXcodeUuid(xcodeProject);

				// add the file reference
				xobjs.PBXFileReference[fileRefUuid] = {
					isa: 'PBXFileReference',
					lastKnownFileType: 'wrapper.framework',
					name: framework,
					path: '"' + fullPath + '"',
					sourceTree: '"<absolute>"'
				};
				xobjs.PBXFileReference[fileRefUuid + '_comment'] = framework;

				// add the library to the Frameworks group
				frameworksGroup.children.push({
					value: fileRefUuid,
					comment: framework + ' in Frameworks'
				});

				// add the framework build file
				xobjs.PBXBuildFile[buildFileUuid] = {
					isa: 'PBXBuildFile',
					fileRef: fileRefUuid,
					fileRef_comment: framework,
				};
				xobjs.PBXBuildFile[buildFileUuid + '_comment'] = framework + ' in Frameworks';

				// add the library to the frameworks build phase
				frameworksBuildPhase.files.push({
					value: buildFileUuid,
					comment: framework + ' in Frameworks'
				});

				buildFileUuid = this.generateXcodeUuid(xcodeProject);
				// add the embed framework build file
				xobjs.PBXBuildFile[buildFileUuid] = {
					isa: 'PBXBuildFile',
					fileRef: fileRefUuid,
					fileRef_comment: framework,
					settings: { ATTRIBUTES: [ 'CodeSignOnCopy', 'RemoveHeadersOnCopy' ] }
				};
				xobjs.PBXBuildFile[buildFileUuid + '_comment'] = framework + ' in Embed Frameworks';

				embedBuildPhase.files.push({
					value: buildFileUuid,
					comment: framework + ' in Embed Frameworks'
				});
			}, this);
		}, this);
	} else {
		this.logger.trace(__('No native module libraries to add'));
	}

	// set the min ios version for the whole project
	buildSettings.IPHONEOS_DEPLOYMENT_TARGET = appc.version.format(deploymentTarget, 2);
	xobjs.XCConfigurationList[pbxProject.buildConfigurationList].buildConfigurations.forEach(function (buildConf) {
		var buildSettings = xobjs.XCBuildConfiguration[buildConf.value].buildSettings;
		buildSettings.IPHONEOS_DEPLOYMENT_TARGET = appc.version.format(deploymentTarget, 2);
	}, this);
	xobjs.XCConfigurationList[xobjs.PBXNativeTarget[mainTargetUuid].buildConfigurationList].buildConfigurations.forEach(function (buildConf) {
		var buildSettings = xobjs.XCBuildConfiguration[buildConf.value].buildSettings;
		buildSettings.IPHONEOS_DEPLOYMENT_TARGET = appc.version.format(deploymentTarget, 2);
	});

	// add extensions and their targets to the project
	if (this.extensions.length) {
		this.logger.trace(__n('Adding %%d iOS extension', 'Adding %%d iOS extensions', this.extensions.length === 1 ? 1 : 2, this.extensions.length));

		// const swiftRegExp = /\.swift$/;

		this.extensions.forEach(function (ext) {
			const extObjs = ext.objs,
				extPBXProject = ext.project;

			// create a group in the Extensions group for all the extension's groups
			const groupUuid = this.generateXcodeUuid(xcodeProject);
			extensionsGroup.children.push({
				value: groupUuid,
				comment: ext.projectName
			});
			xobjs.PBXGroup[groupUuid] = {
				isa: 'PBXGroup',
				children: [],
				name: '"' + ext.projectName + '"',
				path: '"' + ext.relPath + '"',
				sourceTree: '"<group>"'
			};
			xobjs.PBXGroup[groupUuid + '_comment'] = ext.projectName;

			// loop through all of the extension's targets
			extPBXProject.targets.forEach(function (extTarget) {
				const
					targetUuid = extTarget.value,
					targetName = extTarget.comment,
					targetInfo = ext.targetInfo[targetName];
				let target = null,
					targetGroup = null,
					extFrameworksGroup = null,
					extFrameworkReference = null,
					extResourcesGroup = null,
					extResourceReference = null;

				// do we care about this target?
				ext.targets.some(function (t) {
					if (t.name === targetName) {
						target = t;
						return true;
					}
					return false;
				});
				if (!target) {
					return;
				}

				pbxProject.targets.push(extTarget);

				// add target attributes
				if (extPBXProject.attributes && extPBXProject.attributes.TargetAttributes && extPBXProject.attributes.TargetAttributes[targetUuid]) {
					pbxProject.attributes || (pbxProject.attributes = {});
					pbxProject.attributes.TargetAttributes || (pbxProject.attributes.TargetAttributes = {});
					pbxProject.attributes.TargetAttributes[targetUuid] = extPBXProject.attributes.TargetAttributes[targetUuid];
				}

				if (this.provisioningProfile) {
					const ta = pbxProject.attributes.TargetAttributes[targetUuid] || (pbxProject.attributes.TargetAttributes[targetUuid] = {});
					ta.DevelopmentTeam = this.teamId;

					Object.keys(caps).forEach(function (cap) {
						ta.SystemCapabilities || (ta.SystemCapabilities = {});
						if (cap === 'app-groups') {
							ta.SystemCapabilities['com.apple.ApplicationGroups.iOS'] || (ta.SystemCapabilities['com.apple.ApplicationGroups.iOS'] = {});
							ta.SystemCapabilities['com.apple.ApplicationGroups.iOS'].enabled = true;
						}
					});
				}

				// add the native target
				xobjs.PBXNativeTarget[targetUuid] = extObjs.PBXNativeTarget[targetUuid];
				xobjs.PBXNativeTarget[targetUuid + '_comment'] = extObjs.PBXNativeTarget[targetUuid + '_comment'];

				// add the target product to the products group
				productsGroup.children.push({
					value: xobjs.PBXNativeTarget[targetUuid].productReference,
					comment: xobjs.PBXNativeTarget[targetUuid].productReference_comment
				});

				// find the extension frameworks and resources group
				Object.keys(extObjs.PBXGroup).forEach(function (key) {
					if (extObjs.PBXGroup[key] === 'Frameworks') {
						extFrameworksGroup = key.split('_')[0];
					}
					if (extObjs.PBXGroup[key] === 'Resources') {
						extResourcesGroup = key.split('_')[0];
					}
				});

				// add the extension frameworks to the frameworks group
				if (extFrameworksGroup) {
					extObjs.PBXGroup[extFrameworksGroup].children.forEach(function (child) {
						frameworksGroup.children.push(child);
						// find the extension framework file reference
						Object.keys(extObjs.PBXFileReference).forEach(function (key) {
							if (extObjs.PBXFileReference[key] === child.comment) {
								// add the file reference
								extFrameworkReference = key.split('_')[0];
								xobjs.PBXFileReference[extFrameworkReference] = extObjs.PBXFileReference[extFrameworkReference];
								xobjs.PBXFileReference[extFrameworkReference + '_comment'] = child.comment;
							}
						});
					});
				}

				// add the extension resources to the resources group
				if (extResourcesGroup) {
					extObjs.PBXGroup[extResourcesGroup].children.forEach(function (child) {
						resourcesGroup.children.push(child);
						// find the extension framework file reference
						Object.keys(extObjs.PBXFileReference).forEach(function (key) {
							if (extObjs.PBXFileReference[key] === child.comment) {
								// add the file reference
								extResourceReference = key.split('_')[0];
								xobjs.PBXFileReference[extResourceReference] = extObjs.PBXFileReference[extResourceReference];
								xobjs.PBXFileReference[extResourceReference + '_comment'] = child.comment;
							}
						});
					});
				}

				// add the build phases
				xobjs.PBXNativeTarget[targetUuid].buildPhases.forEach(function (phase) {
					let type;
					if (extObjs.PBXSourcesBuildPhase[phase.value]) {
						type = 'PBXSourcesBuildPhase';
					} else if (extObjs.PBXFrameworksBuildPhase[phase.value]) {
						type = 'PBXFrameworksBuildPhase';
					} else if (extObjs.PBXResourcesBuildPhase[phase.value]) {
						type = 'PBXResourcesBuildPhase';
					} else if (extObjs.PBXCopyFilesBuildPhase[phase.value]) {
						type = 'PBXCopyFilesBuildPhase';
					} else {
						return;
					}

					xobjs[type] || (xobjs[type] = {});
					xobjs[type][phase.value] = extObjs[type][phase.value];
					xobjs[type][phase.value + '_comment'] = extObjs[type][phase.value + '_comment'];

					// add files
					xobjs[type][phase.value].files.forEach(function (file) {
						xobjs.PBXBuildFile[file.value] = extObjs.PBXBuildFile[file.value];
						xobjs.PBXBuildFile[file.value + '_comment'] = extObjs.PBXBuildFile[file.value + '_comment'];
					});
				});

				// add dependencies
				xobjs.PBXNativeTarget[targetUuid].dependencies.forEach(function (dep) {
					xobjs.PBXTargetDependency || (xobjs.PBXTargetDependency = {});
					xobjs.PBXTargetDependency[dep.value] = extObjs.PBXTargetDependency[dep.value];
					xobjs.PBXTargetDependency[dep.value + '_comment'] = extObjs.PBXTargetDependency[dep.value + '_comment'];

					// add the target proxy
					const proxyUuid = xobjs.PBXTargetDependency[dep.value].targetProxy;
					xobjs.PBXContainerItemProxy || (xobjs.PBXContainerItemProxy = {});
					xobjs.PBXContainerItemProxy[proxyUuid] = extObjs.PBXContainerItemProxy[proxyUuid];
					xobjs.PBXContainerItemProxy[proxyUuid].containerPortal = projectUuid;
					xobjs.PBXContainerItemProxy[proxyUuid + '_comment'] = extObjs.PBXContainerItemProxy[proxyUuid + '_comment'];
				});

				// add the product reference
				const productUuid = xobjs.PBXNativeTarget[targetUuid].productReference;
				xobjs.PBXFileReference[productUuid] = extObjs.PBXFileReference[productUuid];
				xobjs.PBXFileReference[productUuid + '_comment'] = extObjs.PBXFileReference[productUuid + '_comment'];

				// add the groups and files
				let hasSwiftFiles = false;
				extObjs.PBXGroup[extPBXProject.mainGroup].children.some(function (child) {
					if (child.comment !== target.name) {
						return false;
					}

					xobjs.PBXGroup[groupUuid].children.push(child);

					(function addGroup(uuid, basePath) {
						if (extObjs.PBXGroup[uuid].path) {
							basePath = path.join(basePath, extObjs.PBXGroup[uuid].path.replace(/^"/, '').replace(/"$/, ''));
						}

						xobjs.PBXGroup[uuid] = extObjs.PBXGroup[uuid];
						xobjs.PBXGroup[uuid + '_comment'] = extObjs.PBXGroup[uuid + '_comment'];

						extObjs.PBXGroup[uuid].children.forEach(function (child) {
							if (extObjs.PBXGroup[child.value]) {
								return addGroup(child.value, basePath);
							}

							if (extObjs.PBXFileReference[child.value]) {
								xobjs.PBXFileReference[child.value] = extObjs.PBXFileReference[child.value];
								xobjs.PBXFileReference[child.value + '_comment'] = extObjs.PBXFileReference[child.value + '_comment'];
							}

							if (extObjs.PBXVariantGroup && extObjs.PBXVariantGroup[child.value]) {
								xobjs.PBXVariantGroup || (xobjs.PBXVariantGroup = {});
								const varGroup = xobjs.PBXVariantGroup[child.value] = extObjs.PBXVariantGroup[child.value];
								varGroup.children && varGroup.children.forEach(function (child) {
									xobjs.PBXFileReference[child.value] = extObjs.PBXFileReference[child.value];
									xobjs.PBXFileReference[child.value + '_comment'] = extObjs.PBXFileReference[child.value + '_comment'];
								});
							}
						});
					}(child.value, ext.basePath));

					// save the target group so that we can add an entitlements.plist to it if it doesn't already exist
					targetGroup = xobjs.PBXGroup[child.value];

					return true;
				});

				// add the build configuration
				const buildConfigurationListUuid = xobjs.PBXNativeTarget[targetUuid].buildConfigurationList;
				xobjs.XCConfigurationList[buildConfigurationListUuid] = extObjs.XCConfigurationList[buildConfigurationListUuid];
				xobjs.XCConfigurationList[buildConfigurationListUuid + '_comment'] = extObjs.XCConfigurationList[buildConfigurationListUuid + '_comment'];

				let haveEntitlements = this.provisioningProfile
					&& Object.keys(caps).some(function (cap) {
						return /^(app-groups)$/.test(cap);
					});

				xobjs.XCConfigurationList[buildConfigurationListUuid].buildConfigurations.forEach(function (conf) {
					xobjs.XCBuildConfiguration[conf.value] = extObjs.XCBuildConfiguration[conf.value];
					xobjs.XCBuildConfiguration[conf.value + '_comment'] = extObjs.XCBuildConfiguration[conf.value + '_comment'];

					// update info.plist path
					const extBuildSettings = xobjs.XCBuildConfiguration[conf.value].buildSettings;

					if (extBuildSettings.INFOPLIST_FILE) {
						extBuildSettings.INFOPLIST_FILE = '"' + ext.relPath + '/' + extBuildSettings.INFOPLIST_FILE.replace(/^"/, '').replace(/"$/, '') + '"';
					}

					if (!extBuildSettings.CLANG_ENABLE_OBJC_ARC) {
						// inherits from project
						const confList = extObjs.XCConfigurationList[extPBXProject.buildConfigurationList],
							confUuid = confList.buildConfigurations.filter(function (c) { return c.comment === confList.defaultConfigurationName || 'Release'; })[0].value;
						if (extObjs.XCBuildConfiguration[confUuid].buildSettings.CLANG_ENABLE_OBJC_ARC === 'YES') {
							extBuildSettings.CLANG_ENABLE_OBJC_ARC = 'YES';
						}
					}

					if (/device|dist-appstore|dist-adhoc/.test(this.target)) {
						const pp = this.findProvisioningProfile(this.target, target.ppUUIDs[this.target]);
						extBuildSettings.PROVISIONING_PROFILE = '"' + pp.uuid + '"';

						// NOTE: if there isn't an explicit <team-id> in the tiapp.xml and there is no
						// teams or more than 1 team in the provisioning profile, then we use the appPrefix
						// which should be the team id, but can differ and since we don't check it, this
						// next line of code could be problematic
						extBuildSettings.DEVELOPMENT_TEAM = this.teamId || (pp.team.length === 1 ? pp.team[0] : pp.appPrefix);

						extBuildSettings.PROVISIONING_PROFILE_SPECIFIER = '"' + pp.name + '"';
						extBuildSettings.DEPLOYMENT_POSTPROCESSING = 'YES';
						if (this.keychain) {
							extBuildSettings.OTHER_CODE_SIGN_FLAGS = '"--keychain ' + this.keychain + '"';
						}
					}

					if (extBuildSettings['"CODE_SIGN_IDENTITY[sdk=iphoneos*]"']) {
						extBuildSettings.CODE_SIGN_IDENTITY = extBuildSettings['"CODE_SIGN_IDENTITY[sdk=iphoneos*]"'];
						delete extBuildSettings['"CODE_SIGN_IDENTITY[sdk=iphoneos*]"'];
					}

					if (buildSettings.CODE_SIGN_IDENTITY) {
						extBuildSettings.CODE_SIGN_IDENTITY = buildSettings.CODE_SIGN_IDENTITY;
					}

					if (extBuildSettings.CODE_SIGN_ENTITLEMENTS) {
						const entFile = extBuildSettings.CODE_SIGN_ENTITLEMENTS.replace(/^"/, '').replace(/"$/, '');
						extBuildSettings.CODE_SIGN_ENTITLEMENTS = '"' + path.join(ext.relPath, targetName, entFile) + '"';
						targetInfo.entitlementsFile = path.join(this.buildDir, ext.relPath, targetName, entFile);
					} else if (haveEntitlements) {
						haveEntitlements = false;

						const entFile = targetName + '.entitlements';
						extBuildSettings.CODE_SIGN_ENTITLEMENTS = '"' + path.join(ext.relPath, targetName, entFile) + '"';
						targetInfo.entitlementsFile = path.join(this.buildDir, ext.relPath, targetName, entFile);

						// create the file reference
						const entFileRefUuid = this.generateXcodeUuid(xcodeProject);
						xobjs.PBXFileReference[entFileRefUuid] = {
							isa: 'PBXFileReference',
							lastKnownFileType: 'text.xml',
							path: '"' + entFile + '"',
							sourceTree: '"<group>"'
						};
						xobjs.PBXFileReference[entFileRefUuid + '_comment'] = entFile;

						// add the file to the target's pbx group
						targetGroup && targetGroup.children.push({
							value: entFileRefUuid,
							comment: entFile
						});
					}

					if (hasSwiftFiles) {
						if (!extBuildSettings.SWIFT_VERSION) {
							extBuildSettings.SWIFT_VERSION = '3.1';
						}

						if (legacySwift) {
							extBuildSettings.EMBEDDED_CONTENT_CONTAINS_SWIFT = 'YES';
						} else {
							extBuildSettings.ALWAYS_EMBED_SWIFT_STANDARD_LIBRARIES = 'YES';
						}
					}
				}, this);

				if (targetInfo.isWatchAppV1Extension) {
					this.unmarkBuildDirFiles(path.join(this.xcodeAppDir, 'PlugIns', xobjs.PBXFileReference[productUuid].path.replace(/^"/, '').replace(/"$/, '')));
				} else if (targetInfo.isWatchAppV2orNewer) {
					this.unmarkBuildDirFiles(path.join(this.xcodeAppDir, 'Watch', xobjs.PBXFileReference[productUuid].path.replace(/^"/, '').replace(/"$/, '')));
				}

				if (targetInfo.isExtension || targetInfo.isWatchAppV2orNewer) {
					// add this target as a dependency of the titanium app's project
					const proxyUuid = this.generateXcodeUuid(xcodeProject);
					xobjs.PBXContainerItemProxy || (xobjs.PBXContainerItemProxy = {});
					xobjs.PBXContainerItemProxy[proxyUuid] = {
						isa: 'PBXContainerItemProxy',
						containerPortal: projectUuid,
						containerPortal_comment: 'Project object',
						proxyType: 1,
						remoteGlobalIDString: targetUuid,
						remoteInfo: '"' + targetName + '"'
					};
					xobjs.PBXContainerItemProxy[proxyUuid + '_comment'] = 'PBXContainerItemProxy';

					const depUuid = this.generateXcodeUuid(xcodeProject);
					xobjs.PBXTargetDependency || (xobjs.PBXTargetDependency = {});
					xobjs.PBXTargetDependency[depUuid] = {
						isa: 'PBXTargetDependency',
						target: targetUuid,
						target_comment: targetName,
						targetProxy: proxyUuid,
						targetProxy_comment: 'PBXContainerItemProxy'
					};
					xobjs.PBXTargetDependency[depUuid + '_comment'] = 'PBXTargetDependency';

					xobjs.PBXNativeTarget[mainTargetUuid].dependencies.push({
						value: depUuid,
						comment: 'PBXTargetDependency'
					});

					function addEmbedBuildPhase(name, dstPath, dstSubfolderSpec) {
						const embedExtPhase = xobjs.PBXNativeTarget[mainTargetUuid]
							.buildPhases
							.filter(function (phase) {
								return phase.comment === name;
							}).shift();
						let embedUuid = embedExtPhase && embedExtPhase.value;

						if (!embedUuid) {
							embedUuid = this.generateXcodeUuid(xcodeProject);
							xobjs.PBXNativeTarget[mainTargetUuid].buildPhases.push({
								value: embedUuid,
								comment: name
							});
							xobjs.PBXCopyFilesBuildPhase || (xobjs.PBXCopyFilesBuildPhase = {});
							xobjs.PBXCopyFilesBuildPhase[embedUuid] = {
								isa: 'PBXCopyFilesBuildPhase',
								buildActionMask: 2147483647,
								dstPath: '"' + (dstPath || '') + '"',
								dstSubfolderSpec: dstSubfolderSpec,
								files: [],
								name: '"' + name + '"',
								runOnlyForDeploymentPostprocessing: 0
							};
							xobjs.PBXCopyFilesBuildPhase[embedUuid + '_comment'] = name;
						}

						const productName = xobjs.PBXNativeTarget[targetUuid].productReference_comment;

						// add the copy files build phase
						const copyFilesUuid = this.generateXcodeUuid(xcodeProject);

						xobjs.PBXCopyFilesBuildPhase[embedUuid].files.push({
							value: copyFilesUuid,
							comment: productName + ' in ' + name
						});

						xobjs.PBXBuildFile[copyFilesUuid] = {
							isa: 'PBXBuildFile',
							fileRef: productUuid,
							fileRef_comment: productName,
							settings: { ATTRIBUTES: [ 'RemoveHeadersOnCopy' ] }
						};
						xobjs.PBXBuildFile[copyFilesUuid + '_comment'] = productName + ' in ' + name;
					}

					if (targetInfo.isWatchAppV2orNewer) {
						addEmbedBuildPhase.call(this, 'Embed Watch Content', '$(CONTENTS_FOLDER_PATH)/Watch', 16 /* type "watch app" */);
					} else {
						addEmbedBuildPhase.call(this, 'Embed App Extensions', null, 13 /* type "plugin" */);
					}
				}
			}, this);
		}, this);
	} else {
		this.logger.trace(__('No extensions to add'));
	}

	// if any extensions contain a watch app, we must force the min iOS deployment target to 8.2
	if (this.hasWatchAppV2orNewer) {
		// TODO: Make sure the version of Xcode can support this version of watch app

		let once = 0;
		const iosDeploymentTarget = this.hasWatchAppV2orNewer ? '9.0' : '8.2';

		xobjs.XCConfigurationList[pbxProject.buildConfigurationList].buildConfigurations.forEach(function (buildConf) {
			const buildSettings = xobjs.XCBuildConfiguration[buildConf.value].buildSettings;
			if (buildSettings.IPHONEOS_DEPLOYMENT_TARGET && appc.version.lt(buildSettings.IPHONEOS_DEPLOYMENT_TARGET, iosDeploymentTarget)) {
				once++ === 0 && this.logger.warn(__('WatchKit App detected, changing minimum iOS deployment target from %s to %s', buildSettings.IPHONEOS_DEPLOYMENT_TARGET, iosDeploymentTarget));
				buildSettings.IPHONEOS_DEPLOYMENT_TARGET = iosDeploymentTarget;
			}
		}, this);

		this.hasWatchApp = true;
	}

	Object.keys(xobjs.XCBuildConfiguration).forEach(function (key) {
		const conf = xobjs.XCBuildConfiguration[key];
		if (!conf || typeof conf !== 'object' || !conf.buildSettings) {
			return;
		}

		if (hasSwift) {
			if (!conf.buildSettings.SWIFT_VERSION) {
				conf.buildSettings.SWIFT_VERSION = '3.1';
			}

			if (legacySwift) {
				conf.buildSettings.EMBEDDED_CONTENT_CONTAINS_SWIFT = 'YES';
			} else {
				conf.buildSettings.ALWAYS_EMBED_SWIFT_STANDARD_LIBRARIES = 'YES';
			}
		} else {
			delete conf.buildSettings.EMBEDDED_CONTENT_CONTAINS_SWIFT;
			delete conf.buildSettings.ALWAYS_EMBED_SWIFT_STANDARD_LIBRARIES;
		}
	});

	// get the product names
	this.products = productsGroup.children.map(function (product) {
		return product.comment;
	});

	const hook = this.cli.createHook('build.ios.xcodeproject', this, function (xcodeProject, done) {
		const contents = xcodeProject.writeSync(),
			dest = xcodeProject.filepath,
			parent = path.dirname(dest);

		if (!fs.existsSync(dest) || contents !== fs.readFileSync(dest).toString()) {
			if (!this.forceRebuild) {
				this.logger.info(__('Forcing rebuild: Xcode project has changed since last build'));
				this.forceRebuild = true;
			}
			this.logger.debug(__('Writing %s', dest.cyan));
			fs.existsSync(parent) || wrench.mkdirSyncRecursive(parent);
			fs.writeFileSync(dest, contents);
		} else {
			this.logger.trace(__('No change, skipping %s', dest.cyan));
		}

		this.unmarkBuildDirFile(dest);

		done();
	});

	hook(xcodeProject, next);
};

iOSBuilder.prototype.mergePlist = function mergePlist(src, dest) {
	return (function merge(src, dest) {
		Object.keys(src).forEach(function (prop) {
			if (!/^\+/.test(prop)) {
				if (Object.prototype.toString.call(src[prop]) === '[object Object]') {
					dest.hasOwnProperty(prop) || (dest[prop] = {});
					merge(src[prop], dest[prop]);
				} else {
					dest[prop] = src[prop];
				}
			}
		});
	}(src, dest));
};

iOSBuilder.prototype._embedCapabilitiesAndWriteEntitlementsPlist = function _embedCapabilitiesAndWriteEntitlementsPlist(plist, dest, isExtension, next) {
	const caps = this.tiapp.ios.capabilities,
		parent = path.dirname(dest);

	// add any capabilities entitlements
	Object.keys(caps).forEach(function (cap) {
		if (cap === 'app-groups') {
			Array.isArray(plist['com.apple.security.application-groups']) || (plist['com.apple.security.application-groups'] = []);
			caps[cap].forEach(function (group) {
				if (plist['com.apple.security.application-groups'].indexOf(group) === -1) {
					plist['com.apple.security.application-groups'].push(group);
				}
			});
		}
	});

	this.unmarkBuildDirFile(dest);

	const rel = path.relative(this.buildDir, dest);
	if ([ 'ios', 'iphone' ].some(function (dir) {
		if (fs.existsSync(path.join(this.projectDir, 'platform', dir, rel))) {
			return true;
		}
		return false;
	}, this)) {
		return next();
	}

	const name = 'build.ios.write' + (isExtension ? 'Extension' : '') + 'Entitlements';
	const hook = this.cli.createHook(name, this, function (plist, dest, done) {
		// write the entitlements.plist
		const contents = plist.toString('xml');

		if (!fs.existsSync(dest) || contents !== fs.readFileSync(dest).toString()) {
			if (!this.forceRebuild) {
				this.logger.info(__('Forcing rebuild: %s has changed since last build', dest.replace(this.projectDir + '/', '')));
				this.forceRebuild = true;
			}
			this.logger.debug(__('Writing %s', dest.cyan));
			fs.existsSync(parent) || wrench.mkdirSyncRecursive(parent);
			fs.writeFileSync(dest, contents);
		} else {
			this.logger.trace(__('No change, skipping %s', dest.cyan));
		}

		done();
	});

	hook(plist, dest, next);
};

iOSBuilder.prototype.writeEntitlementsPlist = function writeEntitlementsPlist(next) {
	if (!this.forceRebuild || this.cli.argv.xcode) {
		delete this.buildDirFiles[path.join(this.buildDir, this.tiapp.name + '.entitlements')];
		return next();
	}
	this.logger.info(__('Creating Entitlements.plist'));

	// allow the project to have its own custom entitlements
	const entitlementsFile = path.join(this.projectDir, 'Entitlements.plist');
	let plist = new appc.plist();

	// check if we have a custom entitlements plist file
	if (fs.existsSync(entitlementsFile)) {
		this.logger.info(__('Found custom entitlements: %s', entitlementsFile.cyan));
		plist = new appc.plist(entitlementsFile);
	}

	// tiapp.xml entitlements
	if (this.tiapp.ios.entitlements) {
		this.mergePlist(this.tiapp.ios.entitlements, plist);
	}

	// if we have a provisioning profile, make sure some entitlement settings are correct set
	const pp = this.provisioningProfile;
	if (pp) {
		// attempt to customize it by reading provisioning profile
		if (!plist.hasOwnProperty('application-identifier')) {
			plist['application-identifier'] = pp.appPrefix + '.' + this.tiapp.id;
		}
		if (pp.apsEnvironment) {
			plist['aps-environment'] = this.target === 'dist-appstore' || this.target === 'dist-adhoc' ? 'production' : 'development';
		}
		if (/dist/.test(this.target)  && !plist.hasOwnProperty('beta-reports-active')) {
			plist['beta-reports-active'] = true;
		}
		if (!plist.hasOwnProperty('get-task-allow')) {
			plist['get-task-allow'] = pp.getTaskAllow;
		}
		Array.isArray(plist['keychain-access-groups']) || (plist['keychain-access-groups'] = []);
		if (!plist['keychain-access-groups'].some(function (id) { return id === plist['application-identifier']; })) { // eslint-disable-line max-statements-per-line
			plist['keychain-access-groups'].push(plist['application-identifier']);
		}
	}

	this._embedCapabilitiesAndWriteEntitlementsPlist(plist, path.join(this.buildDir, this.tiapp.name + '.entitlements'), false, next);
};

iOSBuilder.prototype.writeInfoPlist = function writeInfoPlist() {
	// if (this.cli.arg.xcode) {
	// 	var filePath 
	// 	this.infoPlist = new appc.plist()
	// 	delete this.buildDirFiles[path.join(this.buildDir, this.tiapp.name + '.entitlements')];
	// 	return next();
	// }
	this.logger.info(__('Creating Info.plist'));

	const defaultInfoPlistFile = path.join(this.platformPath, 'Info.plist'),
		customInfoPlistFile = this.projectDir + '/Info.plist',
		plist = this.infoPlist = new appc.plist(),
		ios = this.tiapp.ios,
		fbAppId = this.tiapp.properties && this.tiapp.properties['ti.facebook.appid'] && this.tiapp.properties['ti.facebook.appid'].value,
		iconName = this.tiapp.icon.replace(/(.+)(\..*)$/, '$1'), // note: this is basically stripping the file extension
		consts = {
			'__APPICON__': iconName,
			'__PROJECT_NAME__': this.tiapp.name,
			'__PROJECT_ID__': this.tiapp.id,
			'__URL__': this.tiapp.id,
			'__URLSCHEME__': latenize(this.tiapp.name.replace(/\./g, '_').replace(/ /g, '').toLowerCase()),
			'__ADDITIONAL_URL_SCHEMES__': fbAppId ? '<string>fb' + fbAppId + '</string>' : ''
		};
		// resourceDir = path.join(this.projectDir, 'Resources'),
		// iphoneDir = path.join(resourceDir, 'iphone'),
		// iosDir = path.join(resourceDir, 'ios');

	// load the default Info.plist
	plist.parse(fs.readFileSync(defaultInfoPlistFile).toString().replace(/(__.+__)/g, function (match, key) {
		return consts.hasOwnProperty(key) ? consts[key] : '<!-- ' + key + ' -->'; // if they key is not a match, just comment out the key
	}));

	// override the default versions with the tiapp.xml version
	plist.CFBundleVersion = String(this.tiapp.version);
	try {
		plist.CFBundleShortVersionString = appc.version.format(this.tiapp.version, 0, 3);
	} catch (ex) {
		plist.CFBundleShortVersionString = this.tiapp.version;
	}

	// if they have not explicitly set the UIRequiresFullScreen setting, then force it to true
	if (plist.UIRequiresFullScreen === undefined) {
		plist.UIRequiresFullScreen = true;
	}

	// this should not exist, but nuke it so we can create it below
	delete plist.UIAppFonts;

	// delete the app icon and launch image keys (which there should be any in the default Info.plist)
	// so that we can detect below if the custom Info.plist uses these keys.
	delete plist.CFBundleIconFile;
	delete plist.CFBundleIconFiles;
	delete plist.UILaunchImages;
	delete plist['UILaunchImages~ipad'];
	delete plist.UILaunchImageFile;

	const i18nLaunchScreens = {};
	ti.i18n.findLaunchScreens(this.projectDir, this.logger, { ignoreDirs: this.ignoreDirs }).forEach(function (p) {
		i18nLaunchScreens[path.basename(p)] = 1;
	});

	[ {
		'orientation': 'Portrait',
		'minimum-system-version': '11.0',
		'name': 'Default',
		'subtype': '2436h',
		'scale': [ '3x' ],
		'size': '{375, 812}'
	},
	{
		'orientation': 'Landscape',
		'minimum-system-version': '11.0',
		'name': 'Default-Landscape',
		'subtype': '2436h',
		'scale': [ '3x' ],
		'size': '{812, 375}'
	},
	{
		'orientation': 'Portrait',
		'minimum-system-version': '8.0',
		'name': 'Default-Portrait',
		'subtype': '736h',
		'scale': [ '3x' ],
		'size': '{414, 736}'
	},
	{
		'orientation': 'Landscape',
		'minimum-system-version': '8.0',
		'name': 'Default-Landscape',
		'subtype': '736h',
		'scale': [ '3x' ],
		'size': '{414, 736}'
	},
	{
		'orientation': 'Portrait',
		'minimum-system-version': '8.0',
		'name': 'Default',
		'subtype': '667h',
		'scale': [ '2x' ],
		'size': '{375, 667}'
	},
	{
		'orientation': 'Portrait',
		'minimum-system-version': '7.0',
		'name': 'Default',
		'scale': [ '2x', '1x' ],
		'size': '{320, 480}'
	},
	{
		'orientation': 'Portrait',
		'minimum-system-version': '7.0',
		'name': 'Default',
		'subtype': '568h',
		'scale': [ '2x' ],
		'size': '{320, 568}'
	},
	{
		'orientation': 'Portrait',
		'idiom': 'ipad',
		'minimum-system-version': '7.0',
		'name': 'Default-Portrait',
		'scale': [ '2x', '1x' ],
		'size': '{768, 1024}'
	},
	{
		'orientation': 'Landscape',
		'idiom': 'ipad',
		'minimum-system-version': '7.0',
		'name': 'Default-Landscape',
		'scale': [ '2x', '1x' ],
		'size': '{768, 1024}'
	} ].forEach(function (asset) {
		asset.scale.some(function (scale) {
			let key;
			const basefilename = asset.name + (asset.subtype ? '-' + asset.subtype : ''),
				filename = basefilename + (scale !== '1x' ? '@' + scale : '') + '.png';

			// if we have a launch image and if we're doing iPhone only, don't include iPad launch images
			if (i18nLaunchScreens[filename] && (this.deviceFamily !== 'iphone' || asset.idiom === 'iphone')) {
				key = 'UILaunchImages' + (asset.idiom === 'ipad' ? '~ipad' : '');
				Array.isArray(plist[key]) || (plist[key] = []);
				plist[key].push({
					UILaunchImageName: basefilename,
					UILaunchImageOrientation: asset.orientation,
					UILaunchImageSize: asset.size,
					UILaunchImageMinimumOSVersion: asset['minimum-system-version']
				});
				return true;
			}
			return false;
		}, this);
	}, this);

	if (this.enableLaunchScreenStoryboard) {
		plist.UILaunchStoryboardName = 'LaunchScreen';
	} else {
		delete plist.UILaunchStoryboardName;
	}

	// if the user has a Info.plist in their project directory, consider that a custom override
	if (fs.existsSync(customInfoPlistFile)) {
		this.logger.info(__('Copying custom Info.plist from project directory'));
		const custom = new appc.plist().parse(fs.readFileSync(customInfoPlistFile).toString());
		this.mergePlist(custom, plist);
	}

	// tiapp.xml settings override the default and custom Info.plist
	plist.UIRequiresPersistentWiFi = this.tiapp.hasOwnProperty('persistent-wifi')  ? !!this.tiapp['persistent-wifi']  : false;
	plist.UIPrerenderedIcon        = this.tiapp.hasOwnProperty('prerendered-icon') ? !!this.tiapp['prerendered-icon'] : false;
	plist.UIStatusBarHidden        = this.tiapp.hasOwnProperty('statusbar-hidden') ? !!this.tiapp['statusbar-hidden'] : false;

	plist.UIStatusBarStyle = 'UIStatusBarStyleDefault';
	if (/opaque_black|opaque|black/.test(this.tiapp['statusbar-style'])) {
		plist.UIStatusBarStyle = 'UIStatusBarStyleBlackOpaque';
	} else if (/translucent_black|transparent|translucent/.test(this.tiapp['statusbar-style'])) {
		plist.UIStatusBarStyle = 'UIStatusBarStyleBlackTranslucent';
	}

	if (this.tiapp.iphone) {
		this.logger.error(__('The <iphone> section of the tiapp.xml has been removed in Titanium SDK 7.0.0 and later.'));
		this.logger.log(__('Please use the <ios> section of the tiapp.xml to specify iOS-specific values instead:'));
		this.logger.log();
		this.logger.log('<ti:app xmlns:ti="http://ti.appcelerator.org">'.grey);
		this.logger.log('    <ios>'.grey);
		this.logger.log('        <plist>'.grey);
		this.logger.log('            <dict>'.grey);
		this.logger.log('                <!-- Enter your Info.plist keys here -->'.magenta);
		this.logger.log('            </dict>'.grey);
		this.logger.log('        </plist>'.grey);
		this.logger.log('    </ios>'.grey);
		this.logger.log('</ti:app>'.grey);
		this.logger.log();

		process.exit(1);
	}

	// custom Info.plist from the tiapp.xml overrides everything
	ios && ios.plist && this.mergePlist(ios.plist, plist);

	// override the CFBundleIdentifier to the app id
	plist.CFBundleIdentifier = this.tiapp.id;

	// inject Apple Transport Security settings
	if (!plist.NSAppTransportSecurity || typeof plist.NSAppTransportSecurity !== 'object') {
		this.logger.info(__('Disabling ATS'));
		// disable ATS
		plist.NSAppTransportSecurity = {
			NSAllowsArbitraryLoads: true
		};
	} else if (plist.NSAppTransportSecurity.NSAllowsArbitraryLoads) {
		this.logger.info(__('ATS explicitly disabled'));
	} else if (this.whitelistAppceleratorDotCom) {
		// we have a whitelist, make sure appcelerator.com is in the list
		plist.NSAppTransportSecurity || (plist.NSAppTransportSecurity = {});
		plist.NSAppTransportSecurity.NSAllowsArbitraryLoads = false;

		this.logger.info(__('ATS enabled, injecting appcelerator.com into ATS whitelist'));
		plist.NSAppTransportSecurity.NSExceptionDomains || (plist.NSAppTransportSecurity.NSExceptionDomains = {});
		if (!plist.NSAppTransportSecurity.NSExceptionDomains['appcelerator.com']) {
			plist.NSAppTransportSecurity.NSExceptionDomains['appcelerator.com'] = {
				NSExceptionMinimumTLSVersion: 'TLSv1.2',
				NSExceptionRequiresForwardSecrecy: true,
				NSExceptionAllowsInsecureHTTPLoads: false,
				NSRequiresCertificateTransparency: false,
				NSIncludesSubdomains: true,
				NSThirdPartyExceptionMinimumTLSVersion: 'TLSv1.2',
				NSThirdPartyExceptionRequiresForwardSecrecy: true,
				NSThirdPartyExceptionAllowsInsecureHTTPLoads: true
			};
		}
	} else {
		this.logger.warn(__('ATS enabled, however *.appcelerator.com are not whitelisted'));
		this.logger.warn(__('Consider setting the "ios.whitelist.appcelerator.com" property in the tiapp.xml to "true"'));
	}

	if (!plist.CFBundleVersion || this.target === 'dist-appstore') {
		// device builds require an additional token to ensure uniquiness so that iTunes will detect an updated app to sync
		if (this.config.get('app.skipVersionValidation') || this.tiapp.properties['ti.skipVersionValidation']) {
			plist.CFBundleVersion = this.tiapp.version;
		} else if (this.target === 'device' && this.deviceId === 'itunes') {
		// device builds require an additional token to ensure uniqueness so that iTunes will detect an updated app to sync.
		// we drop the milliseconds from the current time so that we still have a unique identifier, but is less than 10
		// characters so iTunes 11.2 doesn't get upset.
			plist.CFBundleVersion = String(+new Date());
			this.logger.debug(__(
				'Building for iTunes sync which requires us to set the CFBundleVersion to a unique number to trigger iTunes to update your app'
			));
			this.logger.debug(__('Setting Info.plist CFBundleVersion to current epoch time %s', plist.CFBundleVersion.cyan));
		}
	}

	// scan for ttf and otf font files
	const fontMap = {};
	function scanFonts(dir, isRoot, replaceString) {
		fs.existsSync(dir) && fs.readdirSync(dir).forEach(function (file) {
			const p = path.join(dir, file);
			if (fs.statSync(p).isDirectory() && (!isRoot || file === 'iphone' || file === 'ios' || ti.availablePlatformsNames.indexOf(file) === -1)) {
				scanFonts(p, false, replaceString);
			} else if (/\.(otf|ttf)$/i.test(file)) {
				fontMap['/' + p.replace(replaceString, '').replace(/^\//, '')] = 1;
			}
		});
	}

	const resourcesPaths = [ path.join(this.projectDir, 'Resources'), path.join(this.projectDir, 'Resources', 'iphone'),
		path.join(this.projectDir, 'Resources', 'ios')
	];
	this.cli.createHook('build.ios.resourcesPaths', this, function (resourcesPaths) {
		resourcesPaths.forEach(function (dir) {
			scanFonts.call(this, dir, true, dir);
		}, this);
	})(resourcesPaths, function () {});

	if (Array.isArray(plist.UIAppFonts)) {
		plist.UIAppFonts.forEach(function (f) {
			if (!fontMap[f]) {
				this.logger.warn(__('Info.plist references non-existent font: %s', cyan(f)));
				fontMap[f] = 1;
			}
		}, this);
	}

	const fonts = Object.keys(fontMap);
	fonts.length && (plist.UIAppFonts = fonts);

	// if CFBundleIconFile, CFBundleIconFiles, & UILaunchImages exists, delete it since we're going to use an asset catalog
	if (plist.CFBundleIconFile) {
		this.logger.warn(__('Removing custom Info.plist "CFBundleIconFile" since we now use an asset catalog for app icons.'));
		delete plist.CFBundleIconFile;
	}
	if (plist.CFBundleIconFiles) {
		this.logger.warn(__('Removing custom Info.plist "CFBundleIconFiles" since we now use an asset catalog for app icons.'));
		delete plist.CFBundleIconFiles;
	}
	if (!Object.keys(i18nLaunchScreens).length) {
		// no i18n launch images, so nuke the launch image related keys
		if (plist.UILaunchImages) {
			this.logger.warn(__('Removing custom Info.plist "UILaunchImages" since we now use an asset catalog for launch images.'));
			delete plist.UILaunchImages;
		}
		if (plist['UILaunchImages~ipad']) {
			this.logger.warn(__('Removing custom Info.plist "UILaunchImages~ipad" since we now use an asset catalog for launch images.'));
			delete plist['UILaunchImages~ipad'];
		}
	}
	if (plist.UILaunchImageFile) {
		this.logger.warn(__('Removing custom Info.plist "UILaunchImageFile" since we now use an asset catalog for launch images.'));
		delete plist.UILaunchImageFile;
	}

	// write the Info.plist
	const prev = this.previousBuildManifest.files && this.previousBuildManifest.files['Info.plist'],
		contents = plist.toString('xml'),
		hash = this.hash(contents),
		dest = path.join(this.buildDir, 'Info.plist');

	this.currentBuildManifest.files['Info.plist'] = {
		hash:  hash,
		mtime: 0,
		size:  contents.length
	};

	if (!fs.existsSync(dest) || !prev || prev.size !== contents.length || prev.hash !== hash) {
		if (!this.forceRebuild) {
			this.logger.info(__('Forcing rebuild: %s changed since last build', 'Info.plist'));
			this.forceRebuild = true;
		}
		this.logger.debug(__('Writing %s', dest.cyan));
		fs.writeFileSync(dest, contents);
	} else {
		this.logger.trace(__('No change, skipping %s', dest.cyan));
	}

	this.unmarkBuildDirFile(dest);
};

iOSBuilder.prototype.writeMain = function writeMain() {
	this.logger.info(__('Creating main.m'));

	const consts = {
			'__PROJECT_NAME__':     this.tiapp.name,
			'__PROJECT_ID__':       this.tiapp.id,
			'__DEPLOYTYPE__':       this.deployType,
			'__SHOW_ERROR_CONTROLLER__':       this.showErrorController,
			'__APP_ID__':           this.tiapp.id,
			'__APP_ANALYTICS__':    String(this.tiapp.hasOwnProperty('analytics') ? !!this.tiapp.analytics : true),
			'__APP_PUBLISHER__':    this.tiapp.publisher,
			'__APP_URL__':          this.tiapp.url,
			'__APP_NAME__':         this.tiapp.name,
			'__APP_VERSION__':      this.tiapp.version,
			'__APP_DESCRIPTION__':  this.tiapp.description,
			'__APP_COPYRIGHT__':    this.tiapp.copyright,
			'__APP_GUID__':         this.tiapp.guid,
			'__APP_RESOURCE_DIR__': '',
			'__APP_DEPLOY_TYPE__': this.buildType,
			'__APP_BUILD_DATE__': Date.now()
		},
		contents = fs.readFileSync(path.join(this.platformPath, 'main.m')).toString().replace(/(__.+?__)/g, function (match, key) {
			const s = consts.hasOwnProperty(key) ? consts[key] : key;
			return typeof s === 'string' ? s.replace(/"/g, '\\"').replace(/\n/g, '\\n') : s;
		}),
		dest = path.join(this.buildDir, 'main.m');

	// if (!fs.existsSync(dest) || contents !== fs.readFileSync(dest).toString()) {
	if (!fs.existsSync(dest)) {
		if (!this.forceRebuild) {
			this.logger.info(__('Forcing rebuild: %s has changed since last build', 'main.m'));
			this.forceRebuild = true;
		}
		this.logger.debug(__('Writing %s', dest.cyan));
		fs.writeFileSync(dest, contents);
	} else {
		this.logger.trace(__('No change, skipping %s', dest.cyan));
	}

	this.unmarkBuildDirFile(dest);
};

iOSBuilder.prototype.writeXcodeConfigFiles = function writeXcodeConfigFiles() {
	if (!this.forceRebuild || this.cli.argv.xcode) {
		return;
	}
	this.logger.info(__('Creating Xcode config files'));

	// write the project.xcconfig
	let dest = this.xcodeProjectConfigFile,
		contents = [
			'TI_VERSION=' + this.titaniumSdkVersion,
			'TI_SDK_DIR=' + this.platformPath.replace(this.titaniumSdkVersion, '$(TI_VERSION)'),
			'TI_APPID=' + this.tiapp.id,
			'JSCORE_LD_FLAGS=-weak_framework JavaScriptCore',
			'TICORE_LD_FLAGS=-weak-lti_ios_profiler -weak-lti_ios_debugger -weak-lTiCore',
			'OTHER_LDFLAGS[sdk=iphoneos*]=$(inherited) ' + (this.useJSCore ? '$(JSCORE_LD_FLAGS)' : '$(TICORE_LD_FLAGS)'),
			'OTHER_LDFLAGS[sdk=iphonesimulator*]=$(inherited) ' + (this.useJSCore ? '$(JSCORE_LD_FLAGS)' : '$(TICORE_LD_FLAGS)'),
			'OTHER_LDFLAGS[sdk=iphoneos9.*]=$(inherited) -weak_framework Contacts -weak_framework ContactsUI -weak_framework WatchConnectivity -weak_framework CoreSpotlight',
			'OTHER_LDFLAGS[sdk=iphonesimulator9.*]=$(inherited) -weak_framework Contacts -weak_framework ContactsUI -weak_framework WatchConnectivity -weak_framework CoreSpotlight',
			'#include "module"'
		].join('\n') + '\n';

	if (!fs.existsSync(dest) || contents !== fs.readFileSync(dest).toString()) {
		if (!this.forceRebuild) {
			this.logger.info(__('Forcing rebuild: %s has changed since last build', path.basename(this.xcodeProjectConfigFile)));
			this.forceRebuild = true;
		}
		this.logger.debug(__('Writing %s', this.xcodeProjectConfigFile.cyan));
		fs.writeFileSync(dest, contents);
	} else {
		this.logger.trace(__('No change, skipping %s', this.xcodeProjectConfigFile.cyan));
	}
	this.unmarkBuildDirFile(dest);

	// write the module.xcconfig
	const variables = {};
	dest = path.join(this.buildDir, 'module.xcconfig');
	contents = [
		'// this is a generated file - DO NOT EDIT',
		''
	];

	this.modules.forEach(function (m) {
		const modulePath = m.modulePath,
			moduleName = m.manifest.name.toLowerCase(),
			prefix = m.manifest.moduleid.toUpperCase().replace(/\./g, '_');

		[ path.join(modulePath, 'module.xcconfig'),
			path.join(this.projectDir, 'modules', 'iphone', moduleName + '.xcconfig')
		].forEach(function (file) {
			if (fs.existsSync(file)) {
				const xc = new appc.xcconfig(file);
				Object.keys(xc).forEach(function (key) {
					var name = (prefix + '_' + key).replace(/[^\w]/g, '_');
					Array.isArray(variables[key]) || (variables[key] = []);
					variables[key].push(name);
					contents.push((name + '=' + xc[key]).replace(new RegExp('\\$\\(' + key + '\\)', 'g'), '$(' + name + ')').replace('$(SRCROOT)', modulePath)); // eslint-disable-line security/detect-non-literal-regexp
				});
			}
		});
	}, this);

	Object.keys(variables).forEach(function (v) {
		contents.push(v + '=$(inherited) '
			+ variables[v].map(function (x) { return '$(' + x + ') '; }).join(''));
	});
	contents = contents.join('\n');

	if (!fs.existsSync(dest) || contents !== fs.readFileSync(dest).toString()) {
		if (!this.forceRebuild) {
			this.logger.info(__('Forcing rebuild: %s has changed since last build', 'module.xcconfig'));
			this.forceRebuild = true;
		}
		this.logger.debug(__('Writing %s', dest.cyan));
		fs.writeFileSync(dest, contents);
	} else {
		this.logger.trace(__('No change, skipping %s', dest.cyan));
	}
	this.unmarkBuildDirFile(dest);
};

iOSBuilder.prototype.copyTitaniumLibraries = function copyTitaniumLibraries() {
	if (!this.forceRebuild || this.cli.argv.xcode) {
		return;
	}
	this.logger.info(__('Copying Titanium libraries'));

	let libDir = path.join(this.buildDir, 'lib');
	fs.existsSync(libDir) || wrench.mkdirSyncRecursive(libDir);

	[ 'libTiCore.a', 'libtiverify.a', 'libti_ios_debugger.a', 'libti_ios_profiler.a' ].forEach(function (filename) {
		const src = path.join(this.platformPath, filename),
			srcStat = fs.statSync(src),
			srcMtime = JSON.parse(JSON.stringify(srcStat.mtime)),
			dest = path.join(libDir, filename),
			destExists = fs.existsSync(dest),
			rel = src.replace(path.dirname(this.titaniumSdkPath) + '/', ''),
			prev = this.previousBuildManifest.files && this.previousBuildManifest.files[rel],
			fileChanged = !destExists || !prev || prev.size !== srcStat.size || prev.mtime !== srcMtime;

		// note: we're skipping the hash check so that we don't have to read in 36MB of data
		// this isn't going to be bulletproof, but hopefully the size and mtime will be enough to catch any changes
		if (!fileChanged || !this.copyFileSync(src, dest, { forceSymlink: filename === 'libTiCore.a' ? !this.forceCopyAll : this.symlinkLibrariesOnCopy, forceCopy: filename === 'libTiCore.a' && this.forceCopyAll })) {
			this.logger.trace(__('No change, skipping %s', dest.cyan));
		}
		this.currentBuildManifest.files[rel] = {
			hash:  null,
			mtime: srcMtime,
			size:  srcStat.size
		};

		this.unmarkBuildDirFile(dest);
	}, this);
	libDir = path.join(this.buildDir, 'externalLibs');
	this.copyDirSync(path.join(this.platformPath, 'externalLibs'), libDir, {
		forceSymlink:this.symlinkLibrariesOnCopy
	});
	this.unmarkBuildDirFiles(libDir);
	libDir = path.join(this.buildDir, 'externalEmbeddedFrameworks');
	this.copyDirSync(path.join(this.platformPath, 'externalEmbeddedFrameworks'), libDir, {
		forceCopy:true
	});
	this.unmarkBuildDirFiles(libDir);
};

iOSBuilder.prototype._scrubiOSSourceFile = function _scrubiOSSourceFile(contents) {
	const name = this.tiapp.name.replace(/[-\W]/g, '_'),
		namespace = /^[0-9]/.test(name) ? 'k' + name : name,
		regexes = [
			// note: order of regexps matters
			[ /TitaniumViewController/g, namespace + '$ViewController' ],
			[ /TitaniumModule/g, namespace + '$Module' ],
			[ /Titanium|Appcelerator/g, namespace ],
			[ /titanium/g, '_' + namespace.toLowerCase() ],
			[ /(org|com)\.appcelerator/g, '$1.' + namespace.toLowerCase() ],
			[ new RegExp('\\* ' + namespace + ' ' + namespace + ' Mobile', 'g'), '* Appcelerator Titanium Mobile' ], // eslint-disable-line security/detect-non-literal-regexp
			[ new RegExp('\\* Copyright \\(c\\) \\d{4}(-\\d{4})? by ' + namespace + ', Inc\\.', 'g'), '* Copyright (c) 2009-' + (new Date()).getFullYear() + ' by Appcelerator, Inc.' ], // eslint-disable-line security/detect-non-literal-regexp
			[ /(\* Please see the LICENSE included with this distribution for details.\n)(?! \*\s*\* WARNING)/g, '$1 * \n * WARNING: This is generated code. Modify at your own risk and without support.\n' ]
		];

	for (let i = 0; i < regexes.length; i++) {
		contents = contents.replace(regexes[i][0], regexes[i][1]);
	}

	return contents;
};

iOSBuilder.prototype.copyTitaniumiOSFiles = function copyTitaniumiOSFiles() {
	if (!this.forceRebuild || this.cli.argv.xcode) {
		return;
	}
	this.logger.info(__('Copying Titanium iOS files'));

	const nameChanged = !this.previousBuildManifest || this.tiapp.name !== this.previousBuildManifest.name,
		name = this.tiapp.name.replace(/[-\W]/g, '_'),
		extRegExp = /\.(c|cpp|h|m|mm)$/,

		// files to watch for while copying
		appFiles = {};

	appFiles['ApplicationDefaults.m'] = {
		props:      this.tiapp.properties || {},
		deployType: this.deployType,
		launchUrl:  this.launchUrl
	};

	appFiles['ApplicationMods.m'] = {
		modules: this.modules
	};

	[ 'Classes', 'headers' ].forEach(function (dir) {
		this.copyDirSync(path.join(this.platformPath, dir), path.join(this.buildDir, dir), {
			ignoreDirs: this.ignoreDirs,
			ignoreFiles: /^(defines\.h|bridge\.txt|libTitanium\.a|\.gitignore|\.npmignore|\.cvsignore|\.DS_Store|\._.*|[Tt]humbs.db|\.vspscc|\.vssscc|\.sublime-project|\.sublime-workspace|\.project|\.tmproj)$/, // eslint-disable-line max-len
			beforeCopy: function (srcFile, destFile, srcStat) {
				var filename = path.basename(srcFile);

				// we skip the ApplicationRouting.m file here because we'll copy it in the encryptJSFiles task below
				if (dir === 'Classes' && (filename === 'ApplicationRouting.m' || filename === 'defines.h')) {
					this.logger.trace(__('Skipping %s, it\'ll be processed later', (dir + '/' + filename).cyan));
					return null;
				}

				let contents = fs.readFileSync(srcFile),
					changed = false;
				const rel = srcFile.replace(path.dirname(this.titaniumSdkPath) + '/', ''),
					destExists = fs.existsSync(destFile),
					existingContent = destExists && fs.readFileSync(destFile),
					srcHash = this.hash(contents),
					srcMtime = JSON.parse(JSON.stringify(srcStat.mtime));

				this.unmarkBuildDirFile(destFile);

				this.currentBuildManifest.files[rel] = {
					hash: srcHash,
					mtime: srcMtime,
					size: srcStat.size
				};

				if (appFiles[filename]) {
					contents = ejs.render(contents.toString(), appFiles[filename]);
					if (!destExists || contents !== existingContent.toString()) {
						if (!this.forceRebuild) {
							this.logger.info(__('Forcing rebuild: %s has changed since last build', rel));
							this.forceRebuild = true;
						}
						this.logger.debug(__('Writing %s', destFile.cyan));
						fs.writeFileSync(destFile, contents);
					}
					return null;
				}

				if (extRegExp.test(srcFile) && srcFile.indexOf('TiCore') === -1) {
					// look up the file to see if the original source changed
					const prev = this.previousBuildManifest.files && this.previousBuildManifest.files[rel];
					if (destExists && !nameChanged && prev && prev.size === srcStat.size && prev.mtime === srcMtime && prev.hash === srcHash) {
						// the original hasn't changed, so let's assume that there's nothing to do
						return null;
					}

					contents = this._scrubiOSSourceFile(contents.toString());
					changed = contents !== existingContent.toString();
				} else {
					changed = !destExists || !bufferEqual(contents, existingContent);
					if (!changed) {
						return null;
					}
				}

				if (!destExists || changed) {
					if (!this.forceRebuild) {
						this.logger.info(__('Forcing rebuild: %s has changed since last build', rel));
						this.forceRebuild = true;
					}
					this.logger.debug(__('Writing %s', destFile.cyan));
					fs.writeFileSync(destFile, contents);

					return null; // tell copyDirSync not to copy the file because we wrote it ourselves
				}
			}.bind(this),
			afterCopy: function (srcFile, destFile, srcStat, result) {
				if (!result) {
					this.logger.trace(__('No change, skipping %s', destFile.cyan));
				}
			}.bind(this)
		});
	}, this);

	function copyAndReplaceFile(src, dest, processContent) {
		const srcStat = fs.statSync(src),
			srcMtime = JSON.parse(JSON.stringify(srcStat.mtime)),
			rel = src.replace(path.dirname(this.titaniumSdkPath) + '/', ''),
			prev = this.previousBuildManifest.files && this.previousBuildManifest.files[rel],
			destDir = path.dirname(dest),
			destExists = fs.existsSync(dest),
			contents = (typeof processContent === 'function' ? processContent(fs.readFileSync(src).toString()) : fs.readFileSync(src).toString()).replace(/Titanium/g, this.tiapp.name),
			hash = this.hash(contents),
			fileChanged = !destExists || !prev || prev.size !== srcStat.size || prev.mtime !== srcMtime || prev.hash !== hash;

		if (fileChanged) {
			this.logger.debug(__('Writing %s', dest.cyan));
			fs.existsSync(destDir) || wrench.mkdirSyncRecursive(destDir);
			fs.writeFileSync(dest, contents);
		} else {
			this.logger.trace(__('No change, skipping %s', dest.cyan));
		}

		this.currentBuildManifest.files[rel] = {
			hash:  contents === null && prev ? prev.hash  : hash || this.hash(contents || ''),
			mtime: contents === null && prev ? prev.mtime : srcMtime,
			size:  contents === null && prev ? prev.size  : srcStat.size
		};

		this.unmarkBuildDirFile(dest);
	}

	copyAndReplaceFile.call(
		this,
		path.join(this.platformPath, 'iphone', 'Titanium_Prefix.pch'),
		path.join(this.buildDir, name + '_Prefix.pch')
	);
	copyAndReplaceFile.call(
		this,
		path.join(this.platformPath, 'iphone', 'Titanium.xcodeproj', 'xcshareddata', 'xcschemes', 'Titanium.xcscheme'),
		path.join(this.buildDir, this.tiapp.name + '.xcodeproj', 'xcshareddata', 'xcschemes', name + '.xcscheme')
	);

	if (this.enableLaunchScreenStoryboard && this.defaultLaunchScreenStoryboard) {
		this.logger.info(__('Installing default %s', 'LaunchScreen.storyboard'.cyan));
		copyAndReplaceFile.call(
			this,
			path.join(this.platformPath, 'iphone', 'LaunchScreen.storyboard'),
			path.join(this.buildDir, 'LaunchScreen.storyboard'),
			function (contents) {
				const bgColor = this.defaultBackgroundColor;
				if (!bgColor) {
					return contents;
				}

				function findNode(node, tags) {
					let child = node.firstChild;
					while (child) {
						if (child.nodeType === 1 && child.tagName === tags[0]) {
							return tags.length === 1 ? child : findNode(child, tags.slice(1));
						}
						child = child.nextSibling;
					}
					return null;
				}

				const dom = new DOMParser({ errorHandler: function () {} }).parseFromString(contents, 'text/xml'),
					colorNode = findNode(dom.documentElement, [ 'scenes', 'scene', 'objects', 'viewController', 'view', 'color' ]);

				if (colorNode) {
					colorNode.setAttribute('red', bgColor.red);
					colorNode.setAttribute('green', bgColor.green);
					colorNode.setAttribute('blue', bgColor.blue);
					colorNode.setAttribute('alpha', 1);
				}

				return '<?xml version="1.0" encoding="UTF-8"?>\n' + dom.documentElement.toString();
			}.bind(this)
		);
	}
};

iOSBuilder.prototype.copyExtensionFiles = function copyExtensionFiles(next) {
	if (!this.forceRebuild || this.cli.argv.xcode) {
		return next();
	}
	if (!this.extensions.length) {
		return next();
	}

	this.logger.info(__('Copying iOS extensions'));

	async.eachSeries(this.extensions, function (extension, next) {
		const extName = path.basename(extension.projectPath).replace(/\.xcodeproj$/, ''),
			src = path.dirname(extension.projectPath),
			dest = path.join(this.buildDir, 'extensions', path.basename(src));

		this.logger.debug(__('Copying %s', extName.cyan));

		this.copyDirSync(src, dest, {
			rootIgnoreDirs: new RegExp('^(build|' + path.basename(extension.projectPath) + ')$', 'i'), // eslint-disable-line security/detect-non-literal-regexp
			ignoreDirs: this.ignoreDirs,
			ignoreFiles: this.ignoreFiles,
			beforeCopy: function (srcFile, destFile, srcStat) {
				this.unmarkBuildDirFile(destFile);

				if (path.basename(srcFile) === 'Info.plist') {
					// validate the info.plist
					const infoPlist = new appc.plist(srcFile);
					if (infoPlist.WKWatchKitApp) {
						infoPlist.WKCompanionAppBundleIdentifier = this.tiapp.id;

						// note: we track whether the versions changed here to not confuse the output with warnings
						// if doing an subsequent build and the extension's Info.plist hasn't changed.
						const origCFBundleShortVersionString = infoPlist.CFBundleShortVersionString,
							changedCFBundleShortVersionString = origCFBundleShortVersionString !== this.infoPlist.CFBundleShortVersionString,
							origCFBundleVersion = infoPlist.CFBundleVersion,
							changedCFBundleVersion = origCFBundleVersion !== this.infoPlist.CFBundleVersion;

						if (changedCFBundleShortVersionString) {
							infoPlist.CFBundleShortVersionString = this.infoPlist.CFBundleShortVersionString;
						}

						if (changedCFBundleVersion) {
							infoPlist.CFBundleVersion = this.infoPlist.CFBundleVersion;
						}

						const contents = infoPlist.toString('xml');
						if (!fs.existsSync(destFile) || contents !== fs.readFileSync(destFile).toString()) {
							if (!this.forceRebuild) {
								this.logger.info(__('Forcing rebuild: iOS Extension "%s" has changed since last build', extName));
								this.forceRebuild = true;
							}
							if (changedCFBundleShortVersionString) {
								this.logger.warn(__('WatchKit App\'s CFBundleShortVersionString "%s" does not match the app\'s CFBundleShortVersionString "%s".', origCFBundleShortVersionString, this.infoPlist.CFBundleShortVersionString));
								this.logger.warn(__('Setting the WatchKit App\'s CFBundleShortVersionString to "%s"', this.infoPlist.CFBundleShortVersionString));
							}
							if (changedCFBundleVersion) {
								this.logger.warn(__('WatchKit App\'s CFBundleVersion "%s" does not match the app\'s CFBundleVersion "%s".', origCFBundleVersion, this.infoPlist.CFBundleVersion));
								this.logger.warn(__('Setting the WatchKit App\'s CFBundleVersion to "%s"', this.infoPlist.CFBundleVersion));
							}
							this.logger.debug(__('Writing %s', destFile.cyan));
							fs.writeFileSync(destFile, contents);
						} else {
							this.logger.trace(__('No change, skipping %s', destFile.cyan));
						}

						return null;
					}
				}

				const prev = this.previousBuildManifest.files && this.previousBuildManifest.files[srcFile],
					srcMtime = JSON.parse(JSON.stringify(srcStat.mtime)),
					srcHash = this.hash(fs.readFileSync(srcFile));

				if (!this.forceRebuild && prev && (prev.size !== srcStat.size || prev.mtime !== srcMtime || prev.hash !== srcHash)) {
					this.logger.info(__('Forcing rebuild: iOS Extension "%s" has changed since last build', extName));
					this.forceRebuild = true;
				}

				this.currentBuildManifest.files[srcFile] = {
					hash: srcHash,
					mtime: srcMtime,
					size: srcStat.size
				};
			}.bind(this),
			afterCopy: function (srcFile, destFile, srcStat, result) {
				if (!result) {
					this.logger.trace(__('No change, skipping %s', destFile.cyan));
				}
			}.bind(this)
		});

		extension.projectPath = path.join(dest, path.basename(extension.projectPath));

		// check if we need to write an entitlements file
		async.eachSeries(Object.keys(extension.targetInfo), function (target, next) {
			if (!extension.targetInfo[target].entitlementsFile) {
				return next();
			}

			const plist = new appc.plist(fs.existsSync(extension.targetInfo[target].entitlementsFile) ? extension.targetInfo[target].entitlementsFile : null);
			this._embedCapabilitiesAndWriteEntitlementsPlist(plist, extension.targetInfo[target].entitlementsFile, true, next);
		}.bind(this), next);
	}.bind(this), next);
};

iOSBuilder.prototype.cleanXcodeDerivedData = function cleanXcodeDerivedData(next) {
	if (!this.forceCleanBuild || this.cli.argv.xcode) {
		return next();
	}

	const exe = this.xcodeEnv.executables.xcodebuild,
		args = [ 'clean' ];
	let tries = 0,
		lastErr = null,
		done = false;

	this.logger.info(__('Cleaning Xcode derived data'));
	this.logger.debug(__('Invoking: %s', ('DEVELOPER_DIR=' + this.xcodeEnv.path + ' ' + exe + ' ' + args.join(' ')).cyan));

	// If going back and forth between compiling with different iOS SDKs that
	// are tied to different Xcode versions, then it's possible for the
	// CoreSimulator service to panic and cause a "INTERNAL ERROR: Uncaught
	// exception". To fix it, we simply retry up to 3 times. If we detect a
	// failure, we wait 500ms between tries. Super hacky, but seems to work.
	async.whilst(
		function () {
			return tries++ < 3 && !done;
		},

		function (cb) {
			const child = spawn(exe, args, {
				cwd: this.buildDir,
				env: {
					DEVELOPER_DIR: this.xcodeEnv.path,
					TMPDIR: process.env.TMPDIR,
					HOME: process.env.HOME,
					PATH: process.env.PATH
				}
			});

			let out = '';
			child.stdout.on('data', function (data) {
				out += data.toString();
			});
			child.stderr.on('data', function (data) {
				out += data.toString();
			});

			child.on('close', function (code) {
				if (code === 65) {
					lastErr = out;
					done = true;
					cb();
				} else if (code) {
					this.logger.debug(__('Retrying to clean project (code %s)', code));
					lastErr = out;
					setTimeout(cb, 500);
				} else if (out.indexOf('INTERNAL ERROR') !== -1) {
					this.logger.debug(__('Retrying to clean project'));
					lastErr = out;
					setTimeout(cb, 500);
				} else {
					done = true;
					lastErr = null;
					fs.existsSync(this.xcodeAppDir) || wrench.mkdirSyncRecursive(this.xcodeAppDir);
					out.split('\n').forEach(function (line) {
						line = line.trim();
						line && this.logger.trace(line);
					}, this);
					cb();
				}
			}.bind(this));
		}.bind(this),

		function () {
			if (lastErr) {
				lastErr.split('\n').forEach(function (line) {
					line = line.trim();
					line && this.logger.error(line);
				}, this);
				process.exit(1);
			}
			next();
		}.bind(this)
	);
};

iOSBuilder.prototype.writeDebugProfilePlists = function writeDebugProfilePlists() {
	this.logger.info(__('Creating debugger and profiler plists'));

	function processPlist(filename, host) {
		const src = path.join(this.templatesDir, filename),
			dest = path.join(this.xcodeAppDir, filename),
			exists = fs.existsSync(dest);

		if (host) {
			const prev = this.previousBuildManifest.files && this.previousBuildManifest.files[filename],
				parts = host.split(':'),
				contents = ejs.render(fs.readFileSync(src).toString(), {
					host: parts.length > 0 ? parts[0] : '',
					port: parts.length > 1 ? parts[1] : '',
					airkey: parts.length > 2 ? parts[2] : '',
					hosts: parts.length > 3 ? parts[3] : ''
				}),
				hash = this.hash(contents);

			this.currentBuildManifest.files[filename] = {
				hash:  hash,
				mtime: 0,
				size:  contents.length
			};

			if (!exists || !prev || prev.size !== contents.length || prev.hash !== hash) {
				if (!this.forceRebuild && /device|dist-appstore|dist-adhoc/.test(this.target)) {
					this.logger.info(__('Forcing rebuild: %s changed since last build', filename));
					this.forceRebuild = true;
				}
				this.logger.debug(__('Writing %s', dest.cyan));
				fs.writeFileSync(dest, contents);
			} else {
				this.logger.trace(__('No change, skipping %s', dest.cyan));
			}
		} else if (exists) {
			this.logger.debug(__('Removing %s', dest.cyan));
			fs.unlinkSync(dest);
		} else {
			this.logger.debug(__('Skipping %s', dest.cyan));
		}

		this.unmarkBuildDirFile(dest);
	}

	processPlist.call(this, 'debugger.plist', this.debugHost);
	processPlist.call(this, 'profiler.plist', this.profilerHost);
};

iOSBuilder.prototype.dirWalker = function dirWalker(currentPath, callback) {
	var ignoreDirs = this.ignoreDirs;
	var ignoreFiles = this.ignoreFiles;
	fs.readdirSync(currentPath).forEach(function (name, i, arr) {
		var currentFile = path.join(currentPath, name);
		var isDir = fs.statSync(currentFile).isDirectory();
		if (isDir) {
			if (!ignoreDirs || !ignoreDirs.test(name)) {
				this.dirWalker(currentFile, callback);
			} else {
				this.logger.warn(__('ignoring dir %s', name.cyan));
			}
		} else if (!ignoreFiles || !ignoreFiles.test(name)) {
			callback(currentFile, name, i, arr);
		} else {
			this.logger.warn(__('ignoring file %s', name.cyan));
		}
	}, this);
};

iOSBuilder.prototype.analyseJS = function analyseJS(to, data, opts, next) {
	var r;
	opts = opts || {};
	opts.filename = to;
	this.cli.createHook('build.android.analyseJS', this, function (to, data, opts, r, cb) {
		try {
			// parse the AST
			r = jsanalyze.analyzeJs(data, opts);
		} catch (ex) {
			this.logger.error(__('analyseJS error at %s', to.cyan));
			ex.message.split('\n').forEach(this.logger.error);
			this.logger.log();
			process.exit(1);
		}
		this.tiSymbols[to] = r.symbols;
		cb(r);
	}.bind(this))(to, data, opts, r, next);
};

iOSBuilder.prototype.getTsConfig = function getTsConfig() {
	var options = {
		noEmitOnError: false,
		sourceMap: this.deployType !== 'production',
		mapRoot:'',
		inlineSourceMap: false,
		outDir: this.buildTsOutputDir,
		rootDir:this.buildTsDir,
		target: ts.ScriptTarget.ES2016,
		module: ts.ModuleKind.CommonJS,
		moduleResolution: ts.ModuleResolutionKind.Classic,
		preserveConstEnums: true,
		declaration: true,
		noImplicitAny: false,
		experimentalDecorators: true,
		noImplicitUseStrict: true,
		removeComments: true,
		noLib: false,
		emitDecoratorMetadata: true
	};

	var tsconfigPath = path.join(this.projectDir, 'tsconfig.json');
	if (fs.existsSync(tsconfigPath)) {
		let parsedConfig, errors;
		const rawConfig = ts.parseConfigFileTextToJson(tsconfigPath, fs.readFileSync(tsconfigPath, 'utf8'));
		const dirname = tsconfigPath && path.dirname(tsconfigPath);
		const basename = tsconfigPath && path.basename(tsconfigPath);
		const tsconfigJSON = rawConfig.config;
		if (ts.convertCompilerOptionsFromJson.length === 5) {
			// >= 1.9?
			errors = [];
			parsedConfig = ts.convertCompilerOptionsFromJson([], tsconfigJSON.compilerOptions, dirname, errors,
				basename || 'tsconfig.json');
		} else {
			// 1.8
			parsedConfig = ts.convertCompilerOptionsFromJson(tsconfigJSON.compilerOptions, dirname).options;
			errors = parsedConfig.errors;
		}
		// we should always overwrite those keys
		delete parsedConfig.noEmit;
		delete parsedConfig.outDir;
		Object.keys(parsedConfig).forEach(function (prop) {
			options[prop] = parsedConfig[prop];
		}, this);
	}
	return options;
};
iOSBuilder.prototype.copyResources = function copyResources(next) {
	const filenameRegExp = /^(.*)\.(\w+)$/,
		useAppThinning = this.useAppThinning,

		jsonPackageTitanium = fs.existsSync(path.join(this.projectDir, 'package.json')) &&  JSON.parse(fs.readFileSync(path.join(this.projectDir, 'package.json'))).titanium,
		appIcon = this.tiapp.icon.match(filenameRegExp),

		ignoreDirs = this.ignoreDirs,
		ignoreFiles = this.ignoreFiles,

		unsymlinkableFileRegExp = /^Default.*\.png|.+\.(otf|ttf)$/,
		appIconRegExp = appIcon && new RegExp('^' + appIcon[1].replace(/\./g, '\\.') + '(.*)\\.png$'), // eslint-disable-line security/detect-non-literal-regexp
		launchImageRegExp = /^(Default(-(Landscape|Portrait))?(-[0-9]+h)?(@[2-9]x)?)\.png$/,
		launchLogoRegExp = /^LaunchLogo(?:@([23])x)?(?:~(iphone|ipad))?\.(?:png|jpg)$/,
		bundleFileRegExp = /.+\.bundle\/.+/,

		isProduction = this.deployType === 'production',

		resourcesToCopy = {},
		jsFiles = {},
		tsFiles = [],
		cssFiles = {},
		htmlJsFiles = {},
		appIcons = {},
		launchImages = {},
		launchLogos = {},
		imageAssets = {};

	var that = this;
	var toIgnore;
	if (jsonPackageTitanium && jsonPackageTitanium.ignores) {
		toIgnore = [];
		jsonPackageTitanium.ignores.forEach(function(r) {
			toIgnore.push(r);
		})
	}

	function walk(src, dest, ignore, origSrc, prefix) {
		origSrc = origSrc || src;
		fs.existsSync(src) && fs.readdirSync(src).forEach(function (name) {
			const from = path.join(src, name),
				relPath = from.replace(origSrc + '/', prefix ? prefix + '/' : ''),
				srcStat = fs.statSync(from),
				isDir = srcStat.isDirectory();

			let ignored = false;
			if (toIgnore) {
				for (let i = 0; i < toIgnore.length; i++) {
					if (minimatch(relPath, toIgnore[i], { dot:true })) {
						ignored = true;
						that.logger.debug(__('Ignoring %s', from.cyan));
						break;
					}
				}
			}

			if (!ignored && (!ignore || !ignore.test(name)) && (!ignoreDirs || !isDir || !ignoreDirs.test(name)) && (!ignoreFiles || isDir || !ignoreFiles.test(name)) && fs.existsSync(from)) {
				const to = path.join(dest, name);

				if (srcStat.isDirectory()) {
					return walk(from, to, ignore, origSrc, prefix);
				}

				const parts = name.match(filenameRegExp),
					info = {
						ignored:false,
						name: parts ? parts[1] : name,
						ext: parts ? parts[2] : null,
						src: from,
						origSrc: origSrc,
						relPath: relPath,
						dest: to,
						srcStat: srcStat
					};

				that.cli.createHook('build.ios.walkResource', this, function (info) {
					if (info.ignored) {
						return;
					}
					// check if we have an app icon

					if (!info.origSrc) {
						if (appIconRegExp) {
							const m = name.match(appIconRegExp);
							if (m) {
								info.tag = m[1];
								appIcons[info.relPath] = info;
								return;
							}
						}

						if (launchImageRegExp.test(name)) {
							launchImages[info.relPath] = info;
							return;
						}
					}
					switch (parts && parts[2]) {
						case 'js':
							jsFiles[info.relPath] = info;
							break;
						case 'ts':
						{
							const relativeFilePath = path.relative(info.origSrc, info.src),
								tsRealPath = path.join(that.buildTsDir, relativeFilePath),
								fromStat = fs.statSync(from),
								fromMtime = JSON.parse(JSON.stringify(fromStat.mtime)),
								prev = that.previousBuildManifest.files && that.previousBuildManifest.files[relativeFilePath];
							let contents = null,
								hash = null,
								fileChanged = !prev || prev.size !== fromStat.size || prev.mtime !== fromMtime || prev.hash !== (hash = that.hash(contents = fs.readFileSync(from)));
							if (fileChanged) {
								that.currentBuildManifest.files[relativeFilePath] = {
									hash:  contents === null && prev ? prev.hash  : hash || that.hash(contents || ''),
									mtime: contents === null && prev ? prev.mtime : fromMtime,
									size:  contents === null && prev ? prev.size  : fromStat.size
								};
								that.copyFileSync.call(that, info.src, tsRealPath);
								tsFiles.push(tsRealPath);
							} else {
								that.logger.trace(__('No change, skipping %s', from.cyan));
							}
							break;
						}
						case 'css':
							cssFiles[info.relPath] = info;
							break;

						case 'png':
						case 'jpg':
						// if the image is the LaunchLogo.png, then let that pass so we can use it
						// in the LaunchScreen.storyboard
							const m = info.name.match(launchLogoRegExp);
							if (m) {
								info.scale = m[1];
								info.device = m[2];
								launchLogos[info.relPath] = info;

								// if we are using app thinning, then don't copy the image, instead mark the
							// image to be injected into the asset catalog
							} else if (useAppThinning && !info.relPath.match(bundleFileRegExp)) {
								imageAssets[info.relPath] = info;

							} else {
								resourcesToCopy[info.relPath] = info;
							}
							break;

						case 'html':
							console.log('html', info);
							jsanalyze.analyzeHtmlFile(info.src, info.relPath.split('/').slice(0, -1).join('/')).forEach(function (file) {
								htmlJsFiles[file] = 1;
							});
							break;
						case 'json':
						case 'map':
						{
							if (that.encryptJS) {
								info.relPath = info.relPath.replace(/\./g, '_');
								info.dest = path.join(that.buildAssetsDir, info.relPath);
								that.jsFilesToEncrypt.push(info.relPath);
								// break;
							}
						// fall through to default case
						}
						default:
							resourcesToCopy[info.relPath] = info;
							break;
					}
				})(info, function () {});
			}
		});
	}

	this.logger.info(__('Analyzing Resources directory'));

	const resourcesPaths = [
		path.join(this.projectDir, 'Resources'),
		path.join(this.projectDir, 'Resources', 'iphone'),
		path.join(this.projectDir, 'Resources', 'ios')
	];

	const platformPaths = [
		path.join(this.projectDir, this.cli.argv['platform-dir'] || 'platform', 'iphone'),
		path.join(this.projectDir, this.cli.argv['platform-dir'] || 'platform', 'ios')
	];

	this.logger.info(__('Analyzing module files'));
	this.modules.forEach(function (module) {
		// ignore source maps in production
		walk(path.join(module.modulePath, 'assets'), path.join(this.xcodeAppDir, module.id.toLowerCase()), (isProduction && /\.js\.map$/));
		platformPaths.push(
			path.join(module.modulePath, 'platform', 'iphone'),
			path.join(module.modulePath, 'platform', 'ios')
		);
		resourcesPaths.push(
			path.join(module.modulePath, 'Resources')
		);
	}, this);

	this.logger.info(__('Analyzing resource files'));
	this.cli.createHook('build.ios.resourcesPaths', this, function (resourcesPaths) {
		resourcesPaths.forEach(function (dir) {
			walk(dir, this.xcodeAppDir, platformsRegExp);
		}, this);
	})(resourcesPaths, function () {});

	// don't process JS files referenced from HTML files
	Object.keys(htmlJsFiles).forEach(function (file) {
		if (jsFiles[file]) {
			resourcesToCopy[file] = jsFiles[file];
			delete jsFiles[file];
		}
	});

	this.logger.info(__('Analyzing platform files'));
	this.cli.createHook('build.ios.platformsPaths', this, function (platformPaths) {
		platformPaths.forEach(function (dir) {
			if (fs.existsSync(dir)) {
				walk(dir, this.xcodeAppDir);
			}
		}, this);
	})(platformPaths, function () {});

	this.logger.info(__('Analyzing localized launch images'));
	ti.i18n.findLaunchScreens(this.projectDir, this.logger, { ignoreDirs: this.ignoreDirs }).forEach(function (launchImage) {
		const parts = launchImage.split('/'),
			file = parts.pop(),
			lang = parts.pop(),
			relPath = path.join(lang + '.lproj', file);

		launchImages[relPath] = {
			i18n: lang,
			src: launchImage,
			dest: path.join(this.xcodeAppDir, relPath),
			srcStat: fs.statSync(launchImage)
		};
	}, this);

	// detect ambiguous modules
	this.modules.forEach(function (module) {
		const filename = module.id + '.js';
		if (jsFiles[filename]) {
			this.logger.error(__('There is a project resource "%s" that conflicts with a native iOS module', filename));
			this.logger.error(__('Please rename the file, then rebuild') + '\n');
			process.exit(1);
		}
	}, this);

	this.logger.info(__('Analyzing CommonJS modules'));
	this.commonJsModules.forEach(function (module) {
		this.logger.info(__('Analyzing CommonJS module: %s', module.id));
		const dest = path.join(this.xcodeAppDir, path.basename(module.id));
		// Pass in the relative path prefix we should give because we aren't copying direct to the root here.
		// Otherwise index.js in one module "overwrites" index.js in another (because they're at same relative path inside module)
		walk(module.modulePath, dest, /^(apidoc|docs|documentation|example)$/, null, module.id); // TODO Consult some .moduleignore file in the module or something? .npmignore?
	}, this);

	function writeAssetContentsFile(dest, json) {
		const parent = path.dirname(dest),
			contents = JSON.stringify(json, null, '  ');

		this.unmarkBuildDirFile(dest);

		if (!fs.existsSync(dest) || contents !== fs.readFileSync(dest).toString()) {
			if (!this.forceRebuild) {
				this.logger.info(__('Forcing rebuild: %s has changed since last build', dest.replace(this.projectDir + '/', '')));
				this.forceRebuild = true;
			}
			this.logger.debug(__('Writing %s', dest.cyan));
			fs.existsSync(parent) || wrench.mkdirSyncRecursive(parent);
			fs.writeFileSync(dest, contents);
		} else {
			this.logger.trace(__('No change, skipping %s', dest.cyan));
		}
	}

	series(this, [
		function initAssetCatalog() {
			this.logger.info(__('Creating asset catalog'));
			writeAssetContentsFile.call(this, path.join(this.buildDir, 'Assets.xcassets', 'Contents.json'), {
				info: {
					version: 1,
					author: 'xcode'
				}
			});
		},

		function createAppIconSetAndiTunesArtwork(next) {
			this.logger.info(__('Creating app icon set'));

			const appIconSetDir = path.join(this.buildDir, 'Assets.xcassets', 'AppIcon.appiconset'),
				appIconSet = {
					images: [],
					info: {
						version: 1,
						author: 'xcode'
					}
				},
				lookup = {
					'-Small':       { height: 29,   width: 29,   scale: 1, idioms: [ 'ipad' ] },
					'-Small@2x':    { height: 29,   width: 29,   scale: 2, idioms: [ 'iphone', 'ipad' ] },
					'-Small@3x':    { height: 29,   width: 29,   scale: 3, idioms: [ 'iphone' ] },
					'-Small-40':    { height: 40,   width: 40,   scale: 1, idioms: [ 'ipad' ] },
					'-Small-40@2x': { height: 40,   width: 40,   scale: 2, idioms: [ 'iphone', 'ipad' ] },
					'-Small-40@3x': { height: 40,   width: 40,   scale: 3, idioms: [ 'iphone' ] },
					'-60@2x':       { height: 60,   width: 60,   scale: 2, idioms: [ 'iphone' ], required: true },
					'-60@3x':       { height: 60,   width: 60,   scale: 3, idioms: [ 'iphone' ], required: true },
					'-76':          { height: 76,   width: 76,   scale: 1, idioms: [ 'ipad' ], required: true },
					'-76@2x':       { height: 76,   width: 76,   scale: 2, idioms: [ 'ipad' ], required: true },
					'-83.5@2x':     { height: 83.5, width: 83.5, scale: 2, idioms: [ 'ipad' ], minXcodeVer: '7.2' },
					'-Marketing':   { height: 1024, width: 1024, scale: 1, idioms: [ 'ios-marketing' ], required: true, minXcodeVer: '9.0' }
				},
				deviceFamily = this.deviceFamily,
				flattenIcons = [],
				flattenedDefaultIconDest = path.join(this.buildDir, 'DefaultIcon.png'),
				missingIcons = [];
			let defaultIcon,
				defaultIconChanged = false,
				defaultIconHasAlpha = false;

			this.defaultIcons.some(function (icon) {
				if (fs.existsSync(icon)) {
					defaultIcon = icon;
					return true;
				}
				return false;
			});

			if (defaultIcon) {
				const defaultIconPrev = this.previousBuildManifest.files && this.previousBuildManifest.files['DefaultIcon.png'],
					defaultIconContents = fs.readFileSync(defaultIcon),
					defaultIconInfo = appc.image.pngInfo(defaultIconContents),
					defaultIconExists = !defaultIconInfo.alpha || fs.existsSync(flattenedDefaultIconDest),
					defaultIconStat = defaultIconExists && fs.statSync(defaultIconInfo.alpha ? flattenedDefaultIconDest : defaultIcon),
					defaultIconMtime = defaultIconExists && JSON.parse(JSON.stringify(defaultIconStat.mtime)),
					defaultIconHash = this.hash(defaultIconContents);

				if (!defaultIconExists || !defaultIconPrev || defaultIconPrev.size !== defaultIconStat.size || defaultIconPrev.mtime !== defaultIconMtime || defaultIconPrev.hash !== defaultIconHash) {
					defaultIconChanged = true;
				}

				defaultIconHasAlpha = defaultIconInfo.alpha;

				this.currentBuildManifest.files['DefaultIcon.png'] = {
					hash: defaultIconHash,
					mtime: defaultIconMtime,
					size: defaultIconStat.size
				};
			}

			// remove all unnecessary icons from the lookup
			Object.keys(lookup).forEach(function (key) {
				if (deviceFamily === 'iphone' && lookup[key].idioms.indexOf('iphone') === -1 && lookup[key].idioms.indexOf('ios-marketing') === -1) {
					// remove ipad only
					delete lookup[key];
				} else if (deviceFamily === 'ipad' && lookup[key].idioms.indexOf('ipad') === -1 && lookup[key].idioms.indexOf('ios-marketing') === -1) {
					// remove iphone only
					delete lookup[key];
				} else if (lookup[key].minXcodeVer && appc.version.lt(this.xcodeEnv.version, lookup[key].minXcodeVer)) {
					// remove unsupported
					delete lookup[key];
				}
			}, this);

			fs.existsSync(appIconSetDir) || wrench.mkdirSyncRecursive(appIconSetDir);

			Object.keys(appIcons).forEach(function (filename) {
				const info = appIcons[filename];

				if (!info.tag) {
					// probably appicon.png, we don't care so skip it
					return;
				}

				if (!lookup[info.tag]) {
					// we don't care about this image
					this.logger.debug(__('Unsupported app icon %s, skipping', info.src.replace(this.projectDir + '/', '').cyan));
					return;
				}

				const meta = lookup[info.tag],
					contents = fs.readFileSync(info.src),
					pngInfo = appc.image.pngInfo(contents),
					w = meta.width * meta.scale,
					h = meta.height * meta.scale;
				let flatten = false;

				// check that the app icon is square
				if (pngInfo.width !== pngInfo.height) {
					this.logger.warn(__('Skipping app icon %s because dimensions (%sx%s) are not equal', info.src.replace(this.projectDir + '/', ''), pngInfo.width, pngInfo.height));
					return;
				}

				// validate the app icon meets the requirements
				if (pngInfo.width !== w) {
					this.logger.warn(__('Expected app icon %s to be %sx%s, but was %sx%s, skipping', info.src.replace(this.projectDir + '/', ''), w, h, pngInfo.width, pngInfo.height));
					return;
				}

				if (pngInfo.alpha) {
					if (defaultIcon && !defaultIconHasAlpha) {
						this.logger.warn(__('Skipping %s because it has an alpha channel and generating one from %s', info.src.replace(this.projectDir + '/', ''), defaultIcon.replace(this.projectDir + '/', '')));
						return;
					}

					this.logger.warn(__('%s contains an alpha channel and will be flattened against a white background', info.src.replace(this.projectDir + '/', '')));
					flatten = true;
					flattenIcons.push(info);
				}

				// inject images into the app icon set
				meta.idioms.forEach(function (idiom) {
					appIconSet.images.push({
						size:     meta.width + 'x' + meta.height,
						idiom:    idiom,
						filename: filename,
						scale:    meta.scale + 'x'
					});
				});

				delete lookup[info.tag];

				info.dest = path.join(appIconSetDir, filename);

				if (!flatten) {
					this.logger.debug(__('Found valid app icon %s (%sx%s)', info.src.replace(this.projectDir + '/', '').cyan, pngInfo.width, pngInfo.height));
					info.contents = contents;
					resourcesToCopy[filename] = info;
				}
			}, this);

			if (this.target === 'dist-adhoc') {
				this.logger.info(__('Copying iTunes artwork'));

				const artworkFiles = [
					{ filename: 'iTunesArtwork', size: 512 },
					{ filename: 'iTunesArtwork@2x', size: 1024 }
				];

				artworkFiles.forEach(function (artwork) {
					const src = path.join(this.projectDir, artwork.filename),
						dest = path.join(this.xcodeAppDir, artwork.filename);

					this.unmarkBuildDirFile(dest);

					try {
						if (!fs.existsSync(src)) {
							throw new Error();
						}

						const contents = fs.readFileSync(src),
							pngInfo = appc.image.pngInfo(contents);

						if (pngInfo.width !== artwork.size || pngInfo.height !== artwork.size) {
							this.logger.warn(__('Skipping %s because dimensions (%sx%s) are wrong; should be %sx%s', artwork.filename, pngInfo.width, pngInfo.height, artwork.size, artwork.size));
							throw new Error();
						}

						if (pngInfo.alpha) {
							this.logger.warn(__('Skipping %s because iTunesArtwork must not have an alpha channel', artwork.filename));
							throw new Error();
						}

						if (!this.copyFileSync(src, dest, { contents: contents })) {
							this.logger.trace(__('No change, skipping %s', dest.cyan));
						}
					} catch (ex) {
						missingIcons.push({
							description: __('%s - Used for Ad Hoc dist', artwork.filename),
							file: dest,
							width: artwork.size,
							height: artwork.size,
							required: false
						});
					}
				}, this);
			}

			series(this, [
				function (next) {
					if (!Object.keys(lookup).length) {
						// wow, we had all of the icons! amazing!
						if (this.target === 'dist-adhoc') {
							this.logger.debug(__('All app icons and iTunes artwork are present and are correct'));
						} else {
							this.logger.debug(__('All app icons are present and are correct'));
						}
						writeAssetContentsFile.call(this, path.join(appIconSetDir, 'Contents.json'), appIconSet);
						return next();
					}

					Object.keys(lookup).forEach(function (key) {
						const meta = lookup[key],
							width = meta.width * meta.scale,
							height = meta.height * meta.scale,
							filename = this.tiapp.icon.replace(/\.png$/, '') + key + '.png',
							dest = path.join(appIconSetDir, filename);

						this.unmarkBuildDirFile(dest);

						// inject images into the app icon set
						meta.idioms.forEach(function (idiom) {
							appIconSet.images.push({
								size:     meta.width + 'x' + meta.height,
								idiom:    idiom,
								filename: filename,
								scale:    meta.scale + 'x'
							});
						});

						// check if the icon was previously resized
						if (!defaultIconChanged && fs.existsSync(dest)) {
							const contents = fs.readFileSync(dest),
								pngInfo = appc.image.pngInfo(contents);

							if (pngInfo.width === width && pngInfo.height === height) {
								this.logger.trace(__('Found generated %sx%s app icon: %s', width, height, dest.cyan));
								// icon looks good, no need to generate it!
								return;
							}
						}

						missingIcons.push({
							description: __('%s - Used for %s',
								filename,
								meta.idioms.map(function (i) { return i === 'ipad' ? 'iPad' : 'iPhone'; }).join(', ')
							),
							file: dest,
							width: width,
							height: height,
							required: !!meta.required
						});
					}, this);

					writeAssetContentsFile.call(this, path.join(appIconSetDir, 'Contents.json'), appIconSet);

					next();
				},

				function processLaunchLogos(next) {
					if (!this.enableLaunchScreenStoryboard || !this.defaultLaunchScreenStoryboard) {
						return next();
					}

					this.logger.info(__('Creating launch logo image set'));

					const assetCatalogDir = path.join(this.buildDir, 'Assets.xcassets', 'LaunchLogo.imageset'),
						images = [],
						lookup = {
							'LaunchLogo~iphone':    { idiom: 'iphone', scale: 1, size: 320 },
							'LaunchLogo@2x~iphone': { idiom: 'iphone', scale: 2, size: 374 },
							'LaunchLogo@3x~iphone': { idiom: 'iphone', scale: 3, size: 621 },
							'LaunchLogo~ipad':      { idiom: 'ipad', scale: 1, size: 384 },
							'LaunchLogo@2x~ipad':   { idiom: 'ipad', scale: 2, size: 1024 }
						};
					let launchLogo = null;

					fs.existsSync(assetCatalogDir) || wrench.mkdirSyncRecursive(assetCatalogDir);

					// loop over each of the launch logos that we found, then for each remove it from the lookup
					// anything left in the lookup will be considered missing
					if (Object.keys(launchLogos).length) {
						Object.keys(launchLogos).forEach(function (file) {
							const img = launchLogos[file];

							if (img.name === 'LaunchLogo') {
								launchLogo = img;
								return;
							}

							if (!lookup[img.name]) {
								return;
							}
							delete lookup[img.name];

							images.push({
								// size?
								idiom: img.device || 'universal',
								filename: img.name + '.' + img.ext,
								scale: (img.scale || 1) + 'x'
							});

							const dest = path.join(assetCatalogDir, img.name + '.' + img.ext);
							img.dest = dest;
							resourcesToCopy[file] = img;
						}, this);
					}

					const missingCount = Object.keys(lookup).length,
						missingLaunchLogos = [];

					// if there's anything left in the `lookup`, then they are missing
					if (missingCount) {
						if (!launchLogo && !defaultIcon) {
							this.logger.warn(__('No DefaultIcon.png found, copying default Titanium LaunchLogo images'));

							// copy the default launch logos
							const defaultLaunchLogosDir = path.join(this.platformPath, 'iphone', 'Assets.xcassets', 'LaunchLogo.imageset'),
								defaultFilesRegExp = /\.(json|png)$/;
							fs.readdirSync(defaultLaunchLogosDir).forEach(function (filename) {
								const file = path.join(defaultLaunchLogosDir, filename);
								if (fs.statSync(file).isFile() && defaultFilesRegExp.test(filename)) {
									resourcesToCopy[filename] = {
										src: path.join(defaultLaunchLogosDir, filename),
										dest: path.join(assetCatalogDir, filename)
									};
								}
							});
							return next();
						}

						let changed = false;
						const prev = this.previousBuildManifest.files && this.previousBuildManifest.files['LaunchLogo.png'];

						if (launchLogo) {
							// sanity check that LaunchLogo is usable
							const stat = fs.statSync(launchLogo.src),
								mtime = JSON.parse(JSON.stringify(stat.mtime)),
								launchLogoContents = fs.readFileSync(launchLogo.src),
								hash = this.hash(launchLogoContents);

							changed = !prev || prev.size !== stat.size || prev.mtime !== mtime || prev.hash !== hash;

							this.currentBuildManifest.files['LaunchLogo.png'] = {
								hash: hash,
								mtime: mtime,
								size: stat.size
							};

							if (changed) {
								const launchLogoInfo = appc.image.pngInfo(launchLogoContents);
								if (launchLogoInfo.width !== 1024 || launchLogoInfo.height !== 1024) {
									this.logger.warn(__('Found LaunchLogo.png that is %sx%s, however the size must be 1024x1024', launchLogoInfo.width, launchLogoInfo.height));
									launchLogo = null;
								}
							}
						} else {
							// using the DefaultIcon.png
							const cur = this.currentBuildManifest.files['LaunchLogo.png'] = this.currentBuildManifest.files['DefaultIcon.png'];
							if (defaultIconChanged || !prev || prev.size !== cur.size || prev.mtime !== cur.mtime || prev.hash !== cur.hash) {
								changed = true;
							}
						}

						let logged = false;

						// build the list of images to be generated
						Object.keys(lookup).forEach(function (name) {
							const spec = lookup[name],
								filename = name + '.png',
								dest = path.join(assetCatalogDir, filename),
								desc = __('%s - Used for %s - size: %sx%s',
									name,
									spec.idiom,
									spec.size,
									spec.size
								);

							images.push({
								idiom: spec.idiom,
								filename: filename,
								scale: spec.scale + 'x'
							});

							this.unmarkBuildDirFile(dest);

							// if the source image hasn't changed, then don't need to regenerate the missing launch logos
							if (!changed && fs.existsSync(dest)) {
								this.logger.trace(__('Found generated %sx%s launch logo: %s', spec.size, spec.size, dest.cyan));
								return;
							}

							missingLaunchLogos.push({
								description: desc,
								file: dest,
								width: spec.size,
								height: spec.size,
								required: false
							});

							if (!logged) {
								logged = true;
								this.logger.info(__n(
									'Missing %s launch logo, generating missing launch logo from %%s',
									'Missing %s launch logos, generating missing launch logos from %%s',
									missingCount,
									launchLogo ? 'LaunchLogo.png' : 'DefaultIcon.png'
								));
							}

							if (launchLogo) {
								this.logger.info('  ' + desc);
							}
						}, this);
					}

					writeAssetContentsFile.call(this, path.join(assetCatalogDir, 'Contents.json'), {
						images: images,
						info: {
							version: 1,
							author: 'xcode'
						}
					});

					if (!missingLaunchLogos.length) {
						return next();
					}

					if (!this.forceRebuild) {
						this.logger.info(__('Forcing rebuild: launch logos changed since last build'));
						this.forceRebuild = true;
					}

					if (!this.buildOnly && (this.target === 'device' || this.target === 'simulator')) {
						this.logger.warn(__('If this app has been previously installed on this %s, you may need restart it to see the latest launch logo', this.target));
						this.logger.warn(__('iOS renders and caches the launch screen to a PNG image that seems to only be invalidated by restarting iOS'));
					}

					if (!launchLogo) {
						// just use the DefaultIcon.png to generate the missing LaunchLogos
						Array.prototype.push.apply(missingIcons, missingLaunchLogos);
						return next();
					}

					appc.image.resize(launchLogo.src, missingLaunchLogos, function (error) {
						if (error) {
							this.logger.error(error);
							this.logger.log();
							process.exit(1);
						}
						next();
					}.bind(this), this.logger);
				}
			], function () {
				if (missingIcons.length && defaultIcon && defaultIconChanged && defaultIconHasAlpha) {
					this.defaultIcons = [ flattenedDefaultIconDest ];
					flattenIcons.push({
						name: path.basename(defaultIcon),
						src: defaultIcon,
						dest: flattenedDefaultIconDest
					});
					this.logger.warn(__('The default icon "%s" contains an alpha channel and will be flattened against a white background', defaultIcon.replace(this.projectDir + '/', '')));
					this.logger.warn(__('You may create an image named "DefaultIcon-ios.png" that does not have an alpha channel in the root of your project'));
					this.logger.warn(__('It is highly recommended that the DefaultIcon.png be 1024x1024'));
				}

				async.eachLimit(flattenIcons, 5, function (icon, next) {
					this.logger.debug(__('Stripping alpha channel: %s => %s', icon.src.cyan, icon.dest.cyan));
					const _t = this;
					fs.createReadStream(icon.src)
						.pipe(new PNG({
							colorType: 2,
							bgColor: {
								red: 255,
								green: 255,
								blue: 255
							}
						}))
						.on('parsed', function () {
							if (icon.dest === flattenedDefaultIconDest) {
								// if the icon we just flattened is the DefaultIcon, then we need to
								// update the currentBuildManifest which means we can't just pipe the
								// the flattened icon to disk, we need to compute the hash and stat it
								const buf = [];
								this.pack()
									.on('data', function (bytes) {
										buf.push(new Buffer(bytes)); // eslint-disable-line security/detect-new-buffer
									})
									.on('end', function (err) {
										if (err) {
											return next(err);
										}

										const contents = Buffer.concat(buf);
										fs.writeFileSync(icon.dest, contents);

										const stat = fs.statSync(icon.dest);
										_t.currentBuildManifest.files['DefaultIcon.png'] = {
											hash: _t.hash(contents),
											mtime: JSON.parse(JSON.stringify(stat.mtime)),
											size: stat.size
										};

										next();
									});
								return;
							}

							this.pack()
								.on('end', next)
								.pipe(fs.createWriteStream(icon.dest));
						});
				}.bind(this), function () {
					if (!missingIcons.length) {
						return next();
					}

					if (!defaultIcon) {
						// we're going to fail, but we let generateAppIcons() do the dirty work
						this.generateAppIcons(missingIcons, next);
						return;
					}

					if (!defaultIconChanged) {
						// we have missing icons, but the default icon hasn't changed
						// call generateAppIcons() and have it deal with determining if the icons need
						// to be generated or if it needs to error out
						this.generateAppIcons(missingIcons, next);
						return;
					}

					if (!this.forceRebuild) {
						this.logger.info(__('Forcing rebuild: %s changed since last build', defaultIcon.replace(this.projectDir + '/', '')));
						this.forceRebuild = true;
					}

					this.generateAppIcons(missingIcons, next);
				}.bind(this));
			});
		},

		function createLaunchImageSet() {
			this.logger.info(__('Creating launch image set'));

			const launchImageDir = path.join(this.buildDir, 'Assets.xcassets', 'LaunchImage.launchimage'),
				launchImageSet = {
					images: [],
					info: {
						version: 1,
						author: 'xcode'
					}
				},
				lookup = {
					// iPhone Portrait - iOS 7-9 - 2x (640x960)
					'Default@2x.png': {
						idiom: 'iphone',
						extent: 'full-screen',
						minSysVer: '7.0',
						orientation: 'portrait',
						width: 640,
						height: 960,
						scale: 2
					},
					// iPhone Portrait - iOS 7-9 - Retina 4 (640x1136)
					'Default-568h@2x.png': {
						idiom: 'iphone',
						extent: 'full-screen',
						minSysVer: '7.0',
						orientation: 'portrait',
						width: 640,
						height: 1136,
						scale: 2,
						subtype: 'retina4'
					},
					// iPhone Portrait - iOS 8,9 - Retina HD 4.7 (750x1334) iPhone 6
					'Default-667h@2x.png': {
						idiom: 'iphone',
						extent: 'full-screen',
						minSysVer: '8.0',
						orientation: 'portrait',
						width: 750,
						height: 1334,
						scale: 2,
						subtype: '667h'
					},

					// iPad Landscape - iOS 7-9 - 1x (1024x768)
					'Default-Landscape.png': {
						idiom: 'ipad',
						extent: 'full-screen',
						minSysVer: '7.0',
						orientation: 'landscape',
						width: 1024,
						height: 768,
						scale: 1
					},
					// iPad Landscape - iOS 7-9 - 2x (2048x1536)
					'Default-Landscape@2x.png': {
						idiom: 'ipad',
						extent: 'full-screen',
						minSysVer: '7.0',
						orientation: 'landscape',
						width: 2048,
						height: 1536,
						scale: 2
					},
					// iPhone Landscape - iOS 8,9 - Retina HD 5.5 (2208x1242)
					'Default-Landscape-736h@3x.png': { idiom: 'iphone', extent: 'full-screen', minSysVer: '8.0', orientation: 'landscape', width: 2208, height: 1242, scale: 3, subtype: '736h' },
					// iPhone Landscape - iOS 11 - Retina HD iPhone X (2436x1125)
					'Default-Landscape-2436h@3x.png': { idiom: 'iphone', extent: 'full-screen', minSysVer: '8.0', orientation: 'landscape', width: 2436, height: 1125, scale: 3, subtype: '2436h' },

					// iPad Portrait - iOS 7-9 - 1x (????)
					'Default-Portrait.png': {
						idiom: 'ipad',
						extent: 'full-screen',
						minSysVer: '7.0',
						orientation: 'portrait',
						width: 768,
						height: 1024,
						scale: 1
					},
					// iPad Portrait - iOS 7-9 - 2x (????)
					'Default-Portrait@2x.png': {
						idiom: 'ipad',
						extent: 'full-screen',
						minSysVer: '7.0',
						orientation: 'portrait',
						width: 1536,
						height: 2048,
						scale: 2
					},
					// iPhone Portrait - iOS 8,9 - Retina HD 5.5 (1242x2208)
					'Default-Portrait-736h@3x.png':  { idiom: 'iphone', extent: 'full-screen', minSysVer: '8.0', orientation: 'portrait', width: 1242, height: 2208, scale: 3, subtype: '736h' },
					// iPhone Portrait - iOS 11 - Retina HD iPhone X (1125x2436)
					'Default-Portrait-2436h@3x.png':  { idiom: 'iphone', extent: 'full-screen', minSysVer: '11.0', orientation: 'portrait', width: 1125, height: 2436, scale: 3, subtype: '2436h' }
				},
				found = {};

			fs.existsSync(launchImageDir) || wrench.mkdirSyncRecursive(launchImageDir);

			Object.keys(launchImages).forEach(function (filename) {
				const info = launchImages[filename];
				let meta = lookup[filename];

				if (info.i18n) {
					meta = lookup[path.basename(filename)];
				}

				if (!meta) {
					// we don't care about this image
					this.logger.debug(__('Unsupported launch image %s, skipping', path.relative(this.projectDir, info.src).cyan));
					return;
				}

				// skip device specific launch images
				if (this.deviceFamily === 'iphone' && meta.idiom !== 'iphone') {
					this.logger.debug(__('Skipping iPad launch image: %s', path.relative(this.projectDir, info.src).cyan));
					return;
				}

				if (this.deviceFamily === 'ipad' && meta.idiom !== 'ipad') {
					this.logger.debug(__('Skipping iPhone launch image: %s', path.relative(this.projectDir, info.src).cyan));
					return;
				}

				if (!info.i18n) {
					const img = {
						'extent': meta.extent,
						'idiom': meta.idiom,
						'filename': filename,
						'minimum-system-version': meta.minSysVer,
						'orientation': meta.orientation,
						'scale': meta.scale + 'x'
					};
					meta.subtype && (img.subtype = meta.subtype);
					launchImageSet.images.push(img);

					// only override the dest if this is NOT an i18n image
					info.dest = path.join(launchImageDir, filename);
				}

				found[info.i18n || '_'] || (found[info.i18n || '_'] = {});
				found[info.i18n || '_'][path.basename(filename)] = 1;

				resourcesToCopy[filename] = info;
			}, this);

			// determine if we're missing any launch images
			const missing = {};
			let totalMissing = 0;
			Object.keys(found).forEach(function (lang) {
				Object.keys(lookup).forEach(function (filename) {
					if (!found[lang][filename] && (this.deviceFamily !== 'ipad' || lookup[filename].idiom === 'ipad') && (this.deviceFamily !== 'iphone' || lookup[filename].idiom === 'iphone')) {
						missing[lang] || (missing[lang] = {});
						missing[lang][filename] = 1;
						totalMissing++;
					}
				}, this);
			}, this);

			if (totalMissing) {
				// we have missing launch images :(
				this.logger.warn(__n('Missing a launch image:', 'Missing %s launch images:', totalMissing));
				Object.keys(missing).forEach(function (lang) {
					this.logger.warn('  ' + (lang === '_' ? __('Default') : 'i18n/' + lang));
					Object.keys(missing[lang]).forEach(function (filename) {
						const meta = lookup[filename];
						this.logger.warn('    '
							+ __('%s - Used for %s - dimensions: %sx%s, orientation: %s',
								filename,
								meta.idiom === 'ipad' ? 'iPad' : 'iPhone',
								meta.width,
								meta.height,
								meta.orientation
							)
						);
					}, this);
				}, this);
			}

			writeAssetContentsFile.call(this, path.join(launchImageDir, 'Contents.json'), launchImageSet);
		},

		function createAssetImageSets() {
			if (!this.useAppThinning) {
				this.logger.info(__('App thinning disabled, skipping asset image sets'));
				return;
			}

			this.logger.info(__('Creating assets image set'));
			const assetCatalog = path.join(this.buildDir, 'Assets.xcassets'),
				imageSets = {},
				imageNameRegExp = /^(.*?)(@[2-9]x)?(~iphone|~ipad)?\.(png|jpg)$/;

			Object.keys(imageAssets).forEach(function (file) {
				const imageName = imageAssets[file].name,
					match = file.match(imageNameRegExp);

				if (match) {
					const imageExt = imageAssets[file].ext;
					const imageSetName = match[1];
					const imageSetNameSHA = sha1(imageSetName + '.' + imageExt);
					const imageSetRelPath = imageSetNameSHA + '.imageset';

					// update image file's destination
					const dest = path.join(assetCatalog, imageSetRelPath, imageName + '.' + imageExt);
					imageAssets[file].dest = dest;

					this.unmarkBuildDirFile(dest);

					if (!imageSets[imageSetRelPath]) {
						imageSets[imageSetRelPath] = {
							images: [],
							name: imageSetName
						};
					}

					imageSets[imageSetRelPath].images.push({
						idiom: !match[3] ? 'universal' : match[3].replace('~', ''),
						filename: imageName + '.' + imageExt,
						scale: !match[2] ? '1x' : match[2].replace('@', '')
					});
				}

				resourcesToCopy[file] = imageAssets[file];
				resourcesToCopy[file].isImage = true;
			}, this);

			// finally create all the Content.json files
			Object.keys(imageSets).forEach(function (set) {
				writeAssetContentsFile.call(this, path.join(assetCatalog, set, 'Contents.json'), {
					images: imageSets[set].images,
					info: {
						version: 1,
						author: 'xcode'
					}
				});
			}, this);
		},

		function copyResources(next) {
			async.eachSeries(Object.keys(resourcesToCopy), function (file, next) {
				const info = resourcesToCopy[file],
					srcStat = fs.statSync(info.src),
					srcMtime = JSON.parse(JSON.stringify(srcStat.mtime)),
					prev = this.previousBuildManifest.files && this.previousBuildManifest.files[file],
					destExists = fs.existsSync(info.dest),
					// destStat = destExists && fs.statSync(info.dest),
					unsymlinkable = unsymlinkableFileRegExp.test(path.basename(file));
				let contents = info.contents || null,
					hash = null;

				let fileChanged = !destExists || !prev || prev.size !== srcStat.size || prev.mtime !== srcMtime
					|| ((!this.symlinkFilesOnCopy  || unsymlinkable) &&  prev.hash !== (hash = this.hash(contents = contents || fs.readFileSync(info.src))));

				this.currentBuildManifest.files[file] = {
					hash: contents === null && prev ? prev.hash : hash || this.hash(contents || ''),
					mtime: contents === null && prev ? prev.mtime : srcMtime,
					size: contents === null && prev ? prev.size : srcStat.size
				};

				delete this.buildDirFiles[info.dest];

				this.cli.createHook('build.ios.copyResource', this, function (from, to, cb) {
					if (!fileChanged) {
						this.logger.trace(__('No change, skipping %s', info.dest.cyan));
					} else if (this.copyFileSync(info.src, info.dest, {
						// contents: contents || (contents = fs.readFileSync(info.src)), 
						forceCopy: unsymlinkable })) {
						if (this.useAppThinning && info.isImage && !this.forceRebuild) {
							this.logger.info(__('Forcing rebuild: image was updated, recompiling asset catalog'));
							this.forceRebuild = true;
						}
					} else {
						this.logger.trace(__('No change, skipping %s', info.dest.cyan));
					}
					this.unmarkBuildDirFile(info.dest);
					cb();
				})(info.src, info.dest, next);

			}.bind(this), next);
		},

		function copyCSSFiles() {
			this.logger.debug(__('Copying CSS files'));
			Object.keys(cssFiles).forEach(function (file) {
				const info = cssFiles[file];
				if (this.minifyCSS) {
					this.logger.debug(__('Copying and minifying %s => %s', info.src.cyan, info.dest.cyan));
					const dir = path.dirname(info.dest);
					fs.existsSync(dir) || wrench.mkdirSyncRecursive(dir);
					fs.writeFileSync(info.dest, new CleanCSS({ processImport: false }).minify(fs.readFileSync(info.src).toString()).styles);
				} else if (!this.copyFileSync(info.src, info.dest, { forceCopy: unsymlinkableFileRegExp.test(path.basename(file)) })) {
					this.logger.trace(__('No change, skipping %s', info.dest.cyan));
				}
				this.unmarkBuildDirFile(info.dest);
			}, this);
		},
		function  compileTsFiles() {
			if (!tsFiles || tsFiles.length === 0) {
				return;
			}
			const tiTsDef = path.join(this.platformPath, '..', 'titanium.d.ts');
			tsFiles.unshift(tiTsDef);
			this.logger.debug(__('Compiling TS files: %s', tsFiles));

			// we need to make sure that babel is used in that case (if not forced to no) 
			if (this.tiapp.properties['use-babel'] !== false) {
				this.currentBuildManifest.useBabel = this.useBabel = true;				
			}

			fs.existsSync(path.join(this.projectDir, 'typings')) && this.dirWalker(path.join(this.projectDir, 'typings'), function (file) {
				if (/\.d\.ts$/.test(file)) {
					tsFiles.push(file);
				}
			});

			const options = this.getTsConfig();
			const host = ts.createCompilerHost(options);

			const program = ts.createProgram(tsFiles, options, host);
			const emitResult = program.emit();

			const allDiagnostics = ts.getPreEmitDiagnostics(program).concat(emitResult.diagnostics);

			allDiagnostics.forEach(function (diagnostic) {
				if (diagnostic.file) {
					const data = diagnostic.file.getLineAndCharacterOfPosition(diagnostic.start);
					const message = ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n');
					this.logger.error(__('TsCompile:%s (%s, %s): %s', diagnostic.file.fileName, data.line + 1, data.character + 1, message));
				} else {
					this.logger.error(__('TsCompile:%s', diagnostic.messageText));
				}
			}.bind(this));
			this.logger.debug(__('TsCompile done!'));
		},
		function onTsDone(next) {
			walk(this.buildTsOutputDir, this.xcodeAppDir);
			next();
		},
		function processJSFiles(next) {
			this.logger.info(__('Processing JavaScript files with babel:%s ', this.useBabel));

			const minifyJS = this.minifyJS;
			const useBabel = this.useBabel;
			async.eachSeries(Object.keys(jsFiles), function (file, next) {
				setImmediate(function () {
					const info = jsFiles[file];
					info.destSourceMap = info.dest + '.map';
					if (this.encryptJS) {
						if (file.indexOf('/') === 0) {
							file = path.basename(file);
						}
						file = file.replace(/\./g, '_');
						info.dest = path.join(this.buildAssetsDir, file);
						info.destSourceMap = info.dest + '_map';
						this.jsFilesToEncrypt.push(file);
					}
					const from = info.src,
						to = info.dest,
						fromStat = fs.statSync(from),
						fromMtime = JSON.parse(JSON.stringify(fromStat.mtime)),
						prev = this.previousBuildManifest.files && this.previousBuildManifest.files[file],
						toExists = fs.existsSync(to);
						// toStat = toExists && fs.statSync(to),
					let contents = null,
						hash = null,
						fileChanged = !toExists || !prev || prev.size !== fromStat.size || prev.mtime !== fromMtime || prev.hash !== (hash = this.hash(contents = fs.readFileSync(from)));

					this.currentBuildManifest.files[file] = {
						hash:  contents === null && prev ? prev.hash  : hash || this.hash(contents || ''),
						mtime: contents === null && prev ? prev.mtime : fromMtime,
						size:  contents === null && prev ? prev.size  : fromStat.size
					};
					if (!fileChanged) {
						this.logger.trace(__('No change, skipping %s', from.cyan));
						const data = fs.readFileSync(to).toString();
						this.analyseJS(to, data, { minify:minifyJS }, function () {
							// make sure not to return the result of analyzeJS in next
							// as the builder might see it as an error
							next();
						});
						return;
					}

					this.cli.createHook('build.ios.copyResource', this, function (from, to, cb) {
						if (useBabel) {
							this.cli.createHook('build.ios.compileJsFile', this, function (from, to, cb2) {
								let inSourceMap = null;
								if (fs.existsSync(from + '.map')) {
									inSourceMap =  JSON.parse(fs.readFileSync(from + '.map'));
								}
								babel.transformFile(from, {
									sourceMaps:this.deployType !== 'production',
									sourceFileName:file,
									sourceMapTarget:info.destSourceMap,
									inputSourceMap:inSourceMap
								}, function (err, transformed) {
									if (err) {
										this.logger.error(__('Babel error: %s', (err.message || err.toString())));
										this.logger.error(err);
										process.exit(1);
									}
									this.analyseJS(to, transformed.code, { minify:minifyJS, sourcemap:{ file:to, orig:transformed.map } }, function (r) {
										const dir = path.dirname(to);
										fs.existsSync(dir) || wrench.mkdirSyncRecursive(dir);

										this.unmarkBuildDirFile(to);
										const exists = fs.existsSync(to);
										if (!exists || r.contents !== fs.readFileSync(to).toString()) {
											this.logger.debug(__(minifyJS ? 'Copying and minifying %s => %s' : 'Copying %s => %s', from.cyan, to.cyan));
											exists && fs.unlinkSync(to);
											fs.writeFileSync(to, r.contents);
											this.jsFilesChanged = true;
											const rMap  = r.map || transformed.map;
											if (rMap) {

												// we remove sourcesContent as it is big and not really usefull
												delete rMap.sourcesContent;

												// fix file 
												rMap.file = file;
												if (rMap.sources) {
													const relToBuild = path.relative(path.dirname(from), path.join(this.projectDir, 'Resources')) + '/';
													rMap.sources = rMap.sources.map(function (value) {
														if (value.indexOf(relToBuild) !== -1) {
															return value.replace(relToBuild, '');
														}
														return value;
													});
												}
												fs.writeFileSync(info.destSourceMap, JSON.stringify(rMap));
												if (this.encryptJS) {
													this.jsFilesToEncrypt.push(path.relative(info.destSourceMap, this.buildAssetsDir));
												}
											}
										} else {
											this.logger.trace(__('No change, skipping transformed file %s', to.cyan));
										}
										cb2();
									}.bind(this));
								}.bind(this));
							}.bind(this))(from, to, cb);

						} else {
							const data = fs.readFileSync(from).toString();
							this.analyseJS(to, data, { sourcemap:{ file:to } }, function (r) {
								const dir = path.dirname(to);
								fs.existsSync(dir) || wrench.mkdirSyncRecursive(dir);

								this.unmarkBuildDirFile(to);

								if (this.minifyJS) {
									this.cli.createHook('build.ios.compileJsFile', this, function (from, to, cb2) {
										const exists = fs.existsSync(to);
										if (!exists || r.contents !== fs.readFileSync(to).toString()) {
											this.logger.debug(__('Copying and minifying %s => %s', from.cyan, to.cyan));
											exists && fs.unlinkSync(to);
											fs.writeFileSync(to, r.contents);
											this.jsFilesChanged = true;
										} else {
											this.logger.trace(__('No change, skipping %s', to.cyan));
										}
										cb2();
									})(from, to, cb);
								} else {
									if (this.copyFileSync(from, to)) {
										this.jsFilesChanged = true;
									} else {
										this.logger.trace(__('No change, skipping %s', to.cyan));
									}
									cb();
								}
							}.bind(this));
						}

					})(from, to, next);
				}.bind(this));
			}.bind(this), next);
		},

		function writeAppProps() {
			this.logger.info(__('Writing app properties'));

			const appPropsFile = this.encryptJS ? path.join(this.buildAssetsDir, '_app_props__json') : path.join(this.xcodeAppDir, '_app_props_.json'),
				props = {};

			this.encryptJS && this.jsFilesToEncrypt.push('_app_props__json');

			this.tiapp.properties && Object.keys(this.tiapp.properties).forEach(function (prop) {
				props[prop] = this.tiapp.properties[prop].value;
			}, this);

			const contents = JSON.stringify(props);
			if (!fs.existsSync(appPropsFile) || contents !== fs.readFileSync(appPropsFile).toString()) {
				this.logger.debug(__('Writing %s', appPropsFile.cyan));
				fs.writeFileSync(appPropsFile, contents);
			} else {
				this.logger.trace(__('No change, skipping %s', appPropsFile.cyan));
			}

			this.unmarkBuildDirFile(appPropsFile);
		},

		function writeJSONFiles() {
			this.logger.info(__('Writing json files'));
			// write the license file
			const licenseFile = this.encryptJS ? path.join(this.buildAssetsDir, '_license__json') : path.join(this.xcodeAppDir, '_license_.json'),
				license = JSON.parse(fs.readFileSync(path.join(this.platformPath, '..', 'license.json'))),
				iosLicenses = license['ios'];
			for (let key in iosLicenses) {
				if (iosLicenses.hasOwnProperty(key)) {
					license[key] = iosLicenses[key];
				}
			}
			delete license['ios'];
			delete license['android'];
			this.modules.forEach(function (module) {
				const moduleLicenseFile = path.join(module.modulePath, 'license.json');
				if (fs.existsSync(moduleLicenseFile)) {
					const moduleLicense = JSON.parse(fs.readFileSync(moduleLicenseFile));
					if (moduleLicense) {
						for (let key in moduleLicense) {
							if (moduleLicense.hasOwnProperty(key)) {
								license[key] = moduleLicense[key];
							}
						}
					}
				}
			});
			fs.writeFileSync(
				licenseFile,
				JSON.stringify(license)
			);
			this.encryptJS && this.jsFilesToEncrypt.push('_license__json');

			this.unmarkBuildDirFile(licenseFile);

			if (this.deployType !== 'production') {
				this.logger.info(__('Copying Error Template'));
				const dirBase = this.encryptJS ? path.join(this.buildAssetsDir, '_error_template_json') : path.join(this.xcodeAppDir, '_error_template.json');
				let src;
				if (fs.existsSync(src = path.join(this.projectDir, '_error_template.json'))
					|| fs.existsSync(src = path.join(this.templatesDir, '_error_template.json'))) {
					appc.fs.copyFileSync(src, dirBase, {
						logger: this.logger.debug
					});
					this.encryptJS && this.jsFilesToEncrypt.push('_error_template_json');
				}
				this.unmarkBuildDirFile(dirBase);
			}
		}
	], next);
};

iOSBuilder.prototype.encryptJSFiles = function encryptJSFiles(next) {
	this.logger.info(__('Encrypting js files, %s', this.jsFilesToEncrypt));

	const rel = 'Classes/ApplicationRouting.m',
		dest = path.join(this.buildDir, 'Classes', 'ApplicationRouting.m'),
		destExists = fs.existsSync(dest),
		destStat = destExists && fs.statSync(dest),
		existingContent = destExists && fs.readFileSync(dest).toString(),
		prev = this.previousBuildManifest.files && this.previousBuildManifest.files[rel];

	this.unmarkBuildDirFile(dest);

	if (!this.encryptJS || !this.jsFilesToEncrypt.length) {
		const srcFile = path.join(this.platformPath, 'Classes', 'ApplicationRouting.m'),
			srcStat = fs.statSync(srcFile),
			srcMtime = JSON.parse(JSON.stringify(srcStat.mtime)),
			contents = this._scrubiOSSourceFile(fs.readFileSync(srcFile).toString()),
			srcHash = this.hash(contents);

		this.logger.debug(__('Using default application routing'));

		if (!destExists || contents !== existingContent) {
			if (!this.forceRebuild) {
				this.logger.info(__('Forcing rebuild: %s has changed since last build', rel));
				this.forceRebuild = true;
			}
			this.logger.debug(__('Writing %s', dest.cyan));
			fs.writeFileSync(dest, contents);
		} else {
			this.logger.trace(__('No change, skipping %s', dest.cyan));
		}

		this.currentBuildManifest.files[rel] = {
			hash: srcHash,
			mtime: srcMtime,
			size: srcStat.size
		};

		return next();
	}

	this.logger.info(__('Encrypting JavaScript files'));

	if (!this.jsFilesChanged && destExists && prev && prev.size === destStat.size && prev.mtime === JSON.parse(JSON.stringify(destStat.mtime)) && prev.hash === this.hash(existingContent)) {
		this.logger.info(__('No JavaScript file changes, skipping titanium_prep'));
		this.currentBuildManifest.files[rel] = prev;
		return next();
	}

	const titaniumPrepHook = this.cli.createHook('build.ios.titaniumprep', this, function (exe, args, opts, done) {
		let tries = 0,
			completed = false;

		this.jsFilesToEncrypt.forEach(function (file) {
			this.logger.debug(__('Preparing %s', file.cyan));
		}, this);

		async.whilst(
			function () {
				if (!completed && tries > 3) {
					// we failed 3 times, so just give up
					this.logger.error(__('titanium_prep failed to complete successfully'));
					this.logger.error(__('Try cleaning this project and build again') + '\n');
					process.exit(1);
				}
				return !completed;
			},
			function (cb) {
				this.logger.debug(__('Running %s', (exe + ' "' + args.slice(0, -1).join('" "') + '"').cyan));

				const child = spawn(exe, args, opts);
				let out = '',
					err = '';

				child.stdin.write(this.jsFilesToEncrypt.join('\n'));
				child.stdin.end();

				child.stdout.on('data', function (data) {
					out += data.toString();
				});

				child.stderr.on('data', function (data) {
					err += data.toString();
				});

				child.on('close', function (code) {
					if (code) {
						this.logger.error(__('titanium_prep failed to run (%s)', code));
						this.logger.error(__(err)  + '\n');
						process.exit(1);
					}

					if (out.indexOf('initWithObjectsAndKeys') !== -1) {
						out = out.replace(/static NSDictionary \*map = nil;\s*if \(map == nil\) \{\s*map = /, '+(NSDictionary*) map;{ static NSDictionary *map = nil;if (map == nil) {map = ');
						out = out.replace(/nil\];\s*\}/, 'nil]; }return map;}');
						out = out.replace(/static NSRange ranges\[\] = \{/, 'const NSRange ranges[] = {');
						out = out.replace(/static UInt8 data\[\] = \{/, 'UInt8 data[] = {');
						// success!

						// success!
						const contents = ejs.render(fs.readFileSync(path.join(this.templatesDir, 'ApplicationRouting.m')).toString(), { bytes: out });

						if (!destExists || contents !== existingContent) {
							if (!this.forceRebuild) {
								// since we just modified the ApplicationRouting.m, we need to force xcodebuild
								this.forceRebuild = true;
								this.logger.info(__('Forcing rebuild: %s changed since last build', dest.replace(this.buildDir + '/', '').cyan));
							}

							this.logger.debug(__('Writing application routing source file: %s', dest.cyan));
							fs.writeFileSync(dest, contents);

							const stat = fs.statSync(dest);
							this.currentBuildManifest.files['Classes/ApplicationRouting.m'] = {
								hash: this.hash(contents),
								mtime: stat.mtime,
								size: stat.size
							};
						} else {
							this.logger.trace(__('No change, skipping %s', dest.cyan));
						}

						this.unmarkBuildDirFile(dest);
						completed = true;
					} else {
						// failure, maybe it was a fluke, try again
						this.logger.warn(__('titanium_prep failed to complete successfully, trying again'));
						tries++;
					}
					cb();
				}.bind(this));
			}.bind(this),
			done
		);
	});

	titaniumPrepHook(
		path.join(this.platformPath, 'titanium_prep'),
		[ this.tiapp.id, this.buildAssetsDir, this.tiapp.guid ],
		{},
		next
	);
};

iOSBuilder.prototype.writeI18NFiles = function writeI18NFiles() {
	this.logger.info(__('Writing i18n files'));

	const data = ti.i18n.load(this.projectDir, this.logger),
		header = '/**\n'
			+ ' * Appcelerator Titanium\n'
			+ ' * this is a generated file - DO NOT EDIT\n'
			+ ' */\n\n';

	function add(obj, dest, map) {
		if (obj) {
			const rel = dest.replace(this.xcodeAppDir + '/', ''),
				contents = header + Object.keys(obj).map(function (name) {
					return '"' + (map && map[name] || name).replace(/\\"/g, '"').replace(/"/g, '\\"')
						+ '" = "' + ('' + obj[name]).replace(/%s/g, '%@').replace(/\\"/g, '"').replace(/"/g, '\\"') + '";';
				}).join('\n');

			this.currentBuildManifest.files[rel] = {
				hash: this.hash(contents),
				mtime: 0,
				size: contents.length
			};

			if (!fs.existsSync(dest) || contents !== fs.readFileSync(dest).toString()) {
				if (!this.forceRebuild && /device|dist-appstore|dist-adhoc/.test(this.target)) {
					this.logger.info(__('Forcing rebuild: %s changed since last build', rel));
					this.forceRebuild = true;
				}
				this.logger.debug(__('Writing %s', dest.cyan));
				fs.writeFileSync(dest, contents);
			} else {
				this.logger.trace(__('No change, skipping %s', dest.cyan));
			}

			this.unmarkBuildDirFile(dest);
		}
	}

	const keys = Object.keys(data);
	if (keys.length) {
		keys.forEach(function (lang) {
			const dir = path.join(this.xcodeAppDir, lang + '.lproj');
			fs.existsSync(dir) || wrench.mkdirSyncRecursive(dir);

			add.call(this, data[lang].app, path.join(dir, 'InfoPlist.strings'), { appname: 'CFBundleDisplayName' });
			add.call(this, data[lang].strings, path.join(dir, 'Localizable.strings'));
		}, this);
	} else {
		this.logger.debug(__('No i18n files to process'));
	}
};

iOSBuilder.prototype.processTiSymbols = function processTiSymbols() {
	this.logger.info(__('Processing Titanium symbols'));
	const depMap = JSON.parse(fs.readFileSync(path.join(this.platformPath, 'dependency.json'))),
		namespaces = {
			'api': 1,
			'network': 1,
			'platform': 1,
			'ui': 1
		},
		symbols = {};
	if (!this.tiapp.hasOwnProperty('analytics') || !!this.tiapp.analytics) {
		namespaces['analytics'] = 1;
	}

	// generate the default symbols
	Object.keys(namespaces).forEach(function (ns) {
		symbols[ns.toUpperCase()] = 1;
	});

	function addSymbol(symbol) {
		const parts = symbol.replace(/^(Ti|Titanium)./, '').split('.');
		if (parts.length) {
			if (parts.indexOf('Android') !== -1) {
				return;
			}
			namespaces[parts[0].toLowerCase()] = 1;
			while (parts.length) {
				symbols[parts.join('.').replace(/\.create/gi, '').replace(/\./g, '').replace(/-/g, '_').toUpperCase()] = 1;
				parts.pop();
			}
		}
	}
	// add the symbols we found
	Object.keys(this.tiSymbols).forEach(function (file) {
		this.tiSymbols[file].forEach(addSymbol);
	}, this);

	// for each module, if it has a metadata.json file, add its symbols
	this.modules.forEach(function (m) {
		const file = path.join(m.modulePath, 'metadata.json');
		if (fs.existsSync(file)) {
			try {
				const metadata = JSON.parse(fs.readFileSync(file));
				if (metadata && typeof metadata === 'object' && Array.isArray(metadata.exports)) {
					metadata.exports.forEach(addSymbol);
				}
			} catch (e) {
				// ignore
			}
		}
	});

	const defineDependencies = depMap.defineDependencies;
	for (let key in defineDependencies) {
		if (defineDependencies.hasOwnProperty(key)) {
			const depend = key.replace(/^(Ti|Titanium)./, '').split('.').join('.').replace(/\.create/gi, '').replace(/\./g, '').replace(
				/-/g, '_').toUpperCase();
			if (symbols[depend] === 1) {
				defineDependencies[key].forEach(addSymbol);
			}
		}
	}

	this.logger.info(__('symbols %s', JSON.stringify(symbols)));
	// for each Titanium namespace, copy any resources
	this.logger.debug(__('Processing Titanium namespace resources'));
	Object.keys(namespaces).forEach(function (ns) {
		const dir = path.join(this.platformPath, 'modules', ns, 'images');
		fs.existsSync(dir) && fs.readdirSync(dir).forEach(function (name) {
			const src = path.join(dir, name),
				srcStat = fs.statSync(src),
				srcMtime = JSON.parse(JSON.stringify(srcStat.mtime)),
				relPath = path.join('modules', ns, 'images', name),
				prev = this.previousBuildManifest.files && this.previousBuildManifest.files[relPath],
				dest = path.join(this.xcodeAppDir, relPath),
				destExists = fs.existsSync(dest);
			let contents = null,
				hash = null;
			const fileChanged = !destExists || !prev || prev.size !== srcStat.size || prev.mtime !== srcMtime || prev.hash !== (hash = this.hash(contents = fs.readFileSync(src)));

			if (!fileChanged || !this.copyFileSync(src, dest, { contents: contents || (contents = fs.readFileSync(src)) })) {
				this.logger.trace(__('No change, skipping %s', dest.cyan));
			}

			this.currentBuildManifest.files[relPath] = {
				hash:  contents === null && prev ? prev.hash  : hash || this.hash(contents || ''),
				mtime: contents === null && prev ? prev.mtime : srcMtime,
				size:  contents === null && prev ? prev.size  : srcStat.size
			};

			this.unmarkBuildDirFile(dest);
		}, this);
	}, this);

	const dest = path.join(this.buildDir, 'Classes', 'defines.h'),
		destExists = fs.existsSync(dest),
		infoPlist = this.infoPlist;
	let hasRemoteNotification = false,
		hasFetch = false,
		contents;

	this.unmarkBuildDirFile(dest);

	if (Array.isArray(infoPlist.UIBackgroundModes) && infoPlist.UIBackgroundModes.indexOf('remote-notification') !== -1) {
		hasRemoteNotification = true;
	}
	if (Array.isArray(infoPlist.UIBackgroundModes) && infoPlist.UIBackgroundModes.indexOf('fetch') !== -1) {
		hasFetch = true;
	}
	// if we're doing a simulator build or we're including all titanium modules,
	// return now since we don't care about writing the defines.h
	if (this.target === 'simulator' || this.includeAllTiModules) {
		const definesFile = path.join(this.platformPath, 'Classes', 'defines.h');

		contents = fs.readFileSync(definesFile).toString();
		if (this.runOnMainThread && !this.useJSCore && !this.useAutoLayout && !hasRemoteNotification && !hasFetch) {
			if ((destExists && contents === fs.readFileSync(dest).toString()) || !this.copyFileSync(definesFile, dest, { contents: contents })) {
				this.logger.trace(__('No change, skipping %s', dest.cyan));
			}
			return;
		}

		if (!this.runOnMainThread) {
			contents += '\n#define TI_USE_KROLL_THREAD';
		}
		if (this.useAutoLayout) {
			contents += '\n#define TI_USE_AUTOLAYOUT';
		}
		if (this.useJSCore) {
			contents += '\n#define USE_JSCORE_FRAMEWORK';
		}
	} else {
		// build the defines.h file
		contents = [
			'// Warning: this is generated file. Do not modify!',
			'',
			'#define TI_VERSION ' + this.titaniumSdkVersion,
			'#define TI_DEPLOY_TYPE_' + this.deployType.toUpperCase()
		].concat(Object.keys(symbols).sort().map(function (s) {
			return '#define USE_TI_' + s;
		}));

		if (this.deployType !== 'production') {
			contents.push('#define USE_TI_UISCROLLVIEW');
		}

		contents.push(
			'#ifdef USE_TI_UILISTVIEW',
			'#define USE_TI_UILABEL',
			'#define USE_TI_UIBUTTON',
			'#define USE_TI_UIBUTTONBAR',
			'#define USE_TI_UIIMAGEVIEW',
			'#define USE_TI_UIMASKEDIMAGE',
			'#define USE_TI_UIPROGRESSBAR',
			'#define USE_TI_UIACTIVITYINDICATOR',
			'#define USE_TI_UISWITCH',
			'#define USE_TI_UISLIDER',
			'#define USE_TI_UITEXTFIELD',
			'#define USE_TI_UITEXTAREA',
			'#define USE_TI_UISCROLLABLEVIEW',
			'#define USE_TI_UIIOSSTEPPER',
			'#define USE_TI_UIIOSBLURVIEW',
			'#define USE_TI_UIIOSLIVEPHOTOVIEW',
			'#define USE_TI_UIIOSTABBEDBAR',
			'#define USE_TI_UIPICKER',
			'#endif'
		);

		if (this.useJSCore) {
			contents.push('#define USE_JSCORE_FRAMEWORK');
		}
		if (!this.runOnMainThread) {
			contents.push('#define TI_USE_KROLL_THREAD');
		}
		if (this.useAutoLayout) {
			contents.push('#define TI_USE_AUTOLAYOUT');
		}

		contents = contents.join('\n');
	}

	if (hasRemoteNotification) {
		contents += '\n#define USE_TI_SILENTPUSH';
	}
	if (hasFetch) {
		contents += '\n#define USE_TI_FETCH';
	}

	if (!destExists || contents !== fs.readFileSync(dest).toString()) {
		if (!this.forceRebuild) {
			this.logger.info(__('Forcing rebuild: %s has changed since last build', 'Classes/defines.h'));
			this.forceRebuild = true;
		}
		this.logger.debug(__('Writing %s', dest.cyan));
		fs.writeFileSync(dest, contents);
	} else {
		this.logger.trace(__('No change, skipping %s', dest.cyan));
	}
};

iOSBuilder.prototype.removeFiles = function removeFiles(next) {
	this.unmarkBuildDirFiles(path.join(this.buildDir, 'DerivedData'));
	this.unmarkBuildDirFiles(path.join(this.buildDir, 'build', 'Intermediates'));
	this.unmarkBuildDirFiles(path.join(this.buildDir, 'build', this.tiapp.name + '.build'));
	this.products.forEach(function (product) {
		product = product.replace(/^"/, '').replace(/"$/, '');
		this.unmarkBuildDirFiles(path.join(this.iosBuildDir, product));
		this.unmarkBuildDirFiles(path.join(this.iosBuildDir, product + '.dSYM'));
	}, this);
	this.unmarkBuildDirFiles(path.join(this.xcodeAppDir, '_CodeSignature'));
	this.unmarkBuildDirFiles(path.join(this.xcodeAppDir, 'AppIcon*'));
	this.unmarkBuildDirFiles(path.join(this.xcodeAppDir, 'LaunchImage-*'));

	// mark a few files that would be generated by xcodebuild
	this.unmarkBuildDirFile(path.join(this.xcodeAppDir, this.tiapp.name));
	this.unmarkBuildDirFile(path.join(this.xcodeAppDir, 'Info.plist'));
	this.unmarkBuildDirFile(path.join(this.xcodeAppDir, 'PkgInfo'));
	this.unmarkBuildDirFile(path.join(this.xcodeAppDir, 'embedded.mobileprovision'));

	this.unmarkBuildDirFiles(path.join(this.buildDir, 'export_options.plist'));
	this.unmarkBuildDirFiles(path.join(this.buildDir, this.tiapp.name + '.xcarchive'));

	try {
		const releaseDir = path.join(this.buildDir, 'build', 'Products', 'Release-iphoneos');
		if (fs.lstatSync(path.join(releaseDir, this.tiapp.name + '.app')).isSymbolicLink()) {
			this.unmarkBuildDirFiles(releaseDir);
		}
	} catch (e) {
		// ignore
	}

	this.logger.info(__('Removing files'));

	const hook = this.cli.createHook('build.ios.removeFiles', this, function (done) {
		Object.keys(this.buildDirFiles).forEach(function (file) {
			try {
				this.logger.debug(__('Removing %s', file.cyan));
				fs.unlinkSync(file);
			} catch (ex) {
				// ignore
			}
		}, this);
		done();
	});

	hook(function () {
		this.logger.debug(__('Removing empty directories'));
		appc.subprocess.run('find', [ '.', '-type', 'd', '-empty', '-delete' ], { cwd: this.xcodeAppDir }, next);
	}.bind(this));
};

iOSBuilder.prototype.optimizeFiles = function optimizeFiles(next) {
	// if we're doing a simulator build, return now since we don't care about optimizing images
	if (this.target === 'simulator') {
		return next();
	}

	this.logger.info(__('Optimizing .plist and .png files'));

	const plistRegExp = /\.plist$/,
		pngRegExp = /\.png$/,
		plists = [],
		pngs = [],
		xcodeAppDir = this.xcodeAppDir + '/',
		previousBuildFiles = this.previousBuildManifest.files || {},
		currentBuildFiles = this.currentBuildManifest.files,
		logger = this.logger;

	function add(arr, name, file) {
		const rel = file.replace(xcodeAppDir, ''),
			prev = previousBuildFiles[rel],
			curr = currentBuildFiles[rel];

		if (!prev || prev.hash !== curr.hash) {
			arr.push(file);
		} else {
			logger.trace(__('No change, skipping %s', file.cyan));
		}
	}

	// find all plist and png files
	this.dirWalker(this.xcodeAppDir, function (file, name) {
		if (!/^(PlugIns|Watch)$/i.test(name)) {
			if (name === 'InfoPlist.strings' || name === 'Localizable.strings' || plistRegExp.test(name)) {
				add(plists, name, file);
			} else if (pngRegExp.test(name)) {
				add(pngs, name, file);
			}
		}
	});

	parallel(this, [
		function (next) {
			async.each(plists, function (file, cb) {
				this.logger.debug(__('Optimizing %s', file.cyan));
				appc.subprocess.run('plutil', [ '-convert', 'binary1', file ], cb);
			}.bind(this), next);
		},

		function (next) {
			if (!fs.existsSync(this.xcodeEnv.executables.pngcrush)) {
				this.logger.warn(__('Unable to find pngcrush in Xcode directory, skipping image optimization'));
				return next();
			}

			async.eachLimit(pngs, 5, function (file, cb) {
				const output = file + '.tmp';
				this.logger.debug(__('Optimizing %s', file.cyan));
				appc.subprocess.run(this.xcodeEnv.executables.pngcrush, [ '-q', '-iphone', '-f', 0, file, output ], function (code) {
					if (code) {
						this.logger.error(__('Failed to optimize %s (code %s)', file, code));
					} else if (fs.existsSync(output)) {
						fs.existsSync(file) && fs.unlinkSync(file);
						fs.renameSync(output, file);
					} else {
						this.logger.warn(__('Unable to optimize %s; invalid png?', file));
					}
					cb();
				}.bind(this));
			}.bind(this), next);
		}
	], next);
};

iOSBuilder.prototype.invokeXcodeBuild = function invokeXcodeBuild(next) {
	if (!this.forceRebuild) {
		this.logger.info(__('Skipping xcodebuild'));
		return next();
	}

	this.logger.info(__('Invoking xcodebuild'));

	const xcodebuildHook = this.cli.createHook('build.ios.xcodebuild', this, function (exe, args, opts, done) {
		const DEVELOPER_DIR = this.xcodeEnv.path
			+ ' ' + exe + ' '
			+ args.map(function (a) { return a.indexOf(' ') !== -1 ? '"' + a + '"' : a; })
				.join(' ');
		this.logger.debug(__('Invoking: %s', ('DEVELOPER_DIR=' + DEVELOPER_DIR).cyan));

		const p = spawn(exe, args, opts),
			out = [],
			err = [],
			clangCompileMFileRegExp = / -c ((?:.+)\.m) /,
			// here's a list of tasks that Xcode can perform... we use this so we can inject some whitespace and make the xcodebuild output pretty
			/* eslint-disable security/detect-non-literal-regexp */
			taskRegExp = new RegExp('^(' + [
				'CodeSign',
				'CompileAssetCatalog',
				'CompileC',
				'CompileStoryboard',
				'CopySwiftLibs',
				'CpHeader',
				'CreateUniversalBinary',
				'Ditto',
				'GenerateDSYMFile',
				'Ld',
				'Libtool',
				'LinkStoryboards',
				'PBXCp',
				'PhaseScriptExecution',
				'ProcessInfoPlistFile',
				'ProcessPCH',
				'ProcessPCH\\+\\+',
				'ProcessProductPackaging',
				'Strip',
				'Stripping',
				'Touch',
				'Validate',
				'ValidateEmbeddedBinary'
			].join('|') + ') ');
		/* eslint-enable security/detect-non-literal-regexp */
		let buffer = '',
			stopOutputting = false;

		function printLine(line) {
			if (line.length) {
				out.push(line);
				if (line.indexOf('Failed to minify') !== -1) {
					stopOutputting = true;
				}
				if (!stopOutputting) {
					if (taskRegExp.test(line)) {
						// add a blank line between tasks to make things easier to read
						this.logger.trace();
						this.logger.trace(line.cyan);
					} else if (line.indexOf('=== BUILD TARGET ') !== -1) {
						// build target
						this.logger.trace();
						this.logger.trace(line.magenta);
					} else if (/^\s+export /.test(line)) {
						// environment variable
						this.logger.trace(line.grey);
					} else if (line.indexOf('/usr/bin/clang') !== -1) {
						// highlight the source file being compiled
						this.logger.trace(line.replace(clangCompileMFileRegExp, ' -c ' + '$1'.green + ' '));
					} else if (line === '** BUILD SUCCEEDED **') {
						this.logger.trace();
						this.logger.trace(line.green);
					} else {
						this.logger.trace(line);
					}
				}
			}
		}

		p.stdout.on('data', function (data) {
			buffer += data.toString();
			const lines = buffer.split('\n');
			buffer = lines.pop();
			lines.forEach(printLine.bind(this));
		}.bind(this));

		p.stderr.on('data', function (data) {
			data.toString().split('\n').forEach(function (line) {
				if (line.length) {
					err.push(line);
				}
			}, this);
		}.bind(this));

		p.on('close', function (code) {
			if (buffer.length) {
				buffer.split('\n').forEach(printLine.bind(this));
			}

			if (code) {
				// first see if we errored due to a dependency issue
				if (err.join('\n').indexOf('Check dependencies') !== -1) {
					let len = out.length;
					for (let i = len - 1; i >= 0; i--) {
						if (out[i].indexOf('Check dependencies') !== -1) {
							if (out[out.length - 1].indexOf('Command /bin/sh failed with exit code') !== -1) {
								len--;
							}
							for (let j = i + 1; j < len; j++) {
								this.logger.error(__('Error details: %s', out[j]));
							}
							this.logger.log();
							process.exit(1);
						}
					}
				}

				// next see if it was a minification issue
				let len = out.length;
				for (let i = len - 1, k = 0; i >= 0 && k < 10; i--, k++) {
					if (out[i].indexOf('Failed to minify') !== -1) {
						if (out[out.length - 1].indexOf('Command /bin/sh failed with exit code') !== -1) {
							len--;
						}
						while (i < len) {
							this.logger.log(out[i++]);
						}
						this.logger.log();
						process.exit(1);
					}
				}

				// just print the entire error buffer
				err.forEach(function (line) {
					this.logger.error(line);
				}, this);
				this.logger.log();
				process.exit(1);
			}

			// end of the line
			done(code);
		}.bind(this));
	});

	const args = [
		this.target === 'dist-appstore' || this.target === 'dist-adhoc' ? 'archive' : 'build',
		'-target', this.tiapp.name,
		'-configuration', this.xcodeTarget,
		'-scheme', this.tiapp.name.replace(/[-\W]/g, '_'),
		'-derivedDataPath', path.join(this.buildDir, 'DerivedData'),
		'OBJROOT=' + path.join(this.buildDir, 'build', 'Intermediates'),
		'GCC_PREPROCESSOR_DEFINITIONS=$GCC_PREPROCESSOR_DEFINITIONS TITANIUM_CLI_XCODEBUILD=1',
		'SHARED_PRECOMPS_DIR=' + path.join(this.buildDir, 'build', 'Intermediates', 'PrecompiledHeaders'),
		'SYMROOT=' + path.join(this.buildDir, 'build', 'Products')
	];

	if (this.simHandle) {
		// when building for the simulator, we need to specify a destination and a scheme (above)
		// so that it can compile all targets (phone and watch targets) for the simulator
		args.push('-destination', 'platform=iOS Simulator,id=' + this.simHandle.udid + ',OS=' + appc.version.format(this.simHandle.version, 2, 2));
		args.push('ONLY_ACTIVE_ARCH=1');
	}

	xcodebuildHook(
		this.xcodeEnv.executables.xcodebuild,
		args,
		{
			cwd: this.buildDir,
			env: {
				DEVELOPER_DIR: this.xcodeEnv.path,
				TMPDIR: process.env.TMPDIR,
				HOME: process.env.HOME,
				PATH: process.env.PATH,
				TITANIUM_CLI_XCODEBUILD: 'Enjoy hacking? http://jobs.appcelerator.com/'
			}
		},
		next
	);
};

iOSBuilder.prototype.writeBuildManifest = function writeBuildManifest(next) {
	this.cli.createHook('build.ios.writeBuildManifest', this, function (manifest, cb) {
		fs.existsSync(this.buildDir) || wrench.mkdirSyncRecursive(this.buildDir);
		fs.existsSync(this.buildManifestFile) && fs.unlinkSync(this.buildManifestFile);
		fs.writeFile(this.buildManifestFile, JSON.stringify(this.buildManifest = manifest, null, '\t'), cb);
	})(this.currentBuildManifest, next);
};

function sha1(value) {
	return crypto.createHash('sha1').update(value).digest('hex');
}

// create the builder instance and expose the public api
(function (iosBuilder) {
	exports.config   = iosBuilder.config.bind(iosBuilder);
	exports.validate = iosBuilder.validate.bind(iosBuilder);
	exports.run      = iosBuilder.run.bind(iosBuilder);
}(new iOSBuilder(module)));
