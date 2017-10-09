#!/usr/bin/env node
'use strict';

const exec = require('child_process').exec,
	os = require('os'),
	path = require('path'),
	fs = require('fs-extra'),
	program = require('commander'),
	appc = require('node-appc'),
	version = require('../package.json').version + '.AKYLAS';

program
	.option('-v, --sdk-version [version]', 'Override the SDK version we report', process.env.PRODUCT_VERSION || version)
	.option('-t, --version-tag [tag]', 'Override the SDK version tag we report')
	.parse(process.argv);

const versionTag = program.versionTag || program.sdkVersion;

/**
 * @param  {String}   versionTag [description]
 * @param  {Function} next        [description]
 */
function install(versionTag, next) {
	let dest,
		osName = os.platform();

	if (osName === 'win32') {
		dest = path.join(process.env.ProgramData, 'Titanium');
	}

	if (osName === 'darwin') {
		osName = 'osx';
		dest = path.join(process.env.HOME, 'Library', 'Application Support', 'Titanium');
	}

	if (osName === 'linux') {
		osName = 'linux';
		dest = path.join(process.env.HOME, '.titanium');
	}

	const zipfile = path.join(__dirname, '..', 'dist', 'mobilesdk-' + versionTag + '-' + osName + '.zip');
	console.log('Installing %s...', zipfile);

	fs.removeSync(path.join(dest, 'mobilesdk', osName, versionTag));
	// TODO Combine with unzip method in packager.js?
	// TODO Support unzipping on windows
	exec('/usr/bin/unzip -q -o -d "' + dest + '" "' + zipfile + '"', function (err, stdout, stderr) {
		if (err) {
			return next(err);
		}
		return next();
	});
}

install(versionTag, function (err) {
	if (err) {
		console.error(err);
		process.exit(1);
	}
	process.exit(0);
});
