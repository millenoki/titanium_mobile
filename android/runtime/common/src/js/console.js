/**
 * Appcelerator Titanium Mobile Copyright (c) 2012-2013 by Appcelerator, Inc.
 * All Rights Reserved. Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

exports._times = new Map()

exports.time = function(label) {
	exports._times.set(label, Date.now());
};

exports.timeEnd = function(label) {
	var time = exports._times.get(label);
	if (!time) {
		throw new Error('No such label: ' + label);
	}
	var duration = Date.now() - time;
	exports.log(label + ':', duration + 'ms');
};

function join(args) {
	// Handle null / undefined args up front since we can't slice them
	if (typeof args === "undefined") {
		return "undefined";
	} else if (args === null) {
		return "null";
	}

	return [].concat(Array.prototype.slice.call(args)).map(
			function(arg) {
				if (typeof arg === "undefined") {
					return "undefined";
				}

				return (arg === null) ? "null"
						: ((typeof arg === "object") ? (arg
								.hasOwnProperty('toString') ? arg.toString()
								: JSON.stringify(arg)) : arg);
			}).join(' ');
}

exports.log = function() {
	Titanium.API.info(join(arguments));
}

exports.info = function() {
	Titanium.API.info(join(arguments));
}

exports.warn = function() {
	Titanium.API.warn(join(arguments));
}

exports.error = function() {
	Titanium.API.error(join(arguments));
}

exports.debug = function() {
	Titanium.API.debug(join(arguments));
}

exports.trace = function() {
	// TODO probably can to do this better with V8's debug object once that is
	// exposed.
	var err = new Error();
	err.name = 'Trace';
	err.message = util.format.apply(this, arguments);
	Error.captureStackTrace(err, trace);
	exports.error(err.stack);
};