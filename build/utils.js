var path = require('path'),
	async = require('async'),
	fs = require('fs-extra'),
	glob = require('glob'),
	Utils = {};

Utils.copyFile = function (srcFolder, destFolder, filename, options, next) {
	fs.copy(path.join(srcFolder, filename), path.join(destFolder, filename), options, next);
}

Utils.copyFiles = function (srcFolder, destFolder, files, options, next) {
	var realNext = options;
	var hasOptions = false;
	if (next) {
		realNext = next;
		hasOptions = true;
	}
	async.each(files, function (file, cb) {
		Utils.copyFile(srcFolder, destFolder, file, hasOptions?options:undefined, cb);
	}, realNext);
}

Utils.globCopy = function (pattern, srcFolder, destFolder, options, next) {
	glob(pattern, {cwd: srcFolder}, function (err, files) {
		if (err) {
			return next(err);
		}
		Utils.copyFiles(srcFolder, destFolder, files, options, next);
	});
}

Utils.copyAndModifyFile = function (srcFolder, destFolder, filename, substitutions, next) {
	// FIXME If this is a directory, we need to recurse into directory!

	// read in src file, modify contents, write to dest folder
	fs.readFile(path.join(srcFolder, filename), function (err, data) {
		var str;
		if (err) {
			return next(err);
		}
		// Go through each substitution and replace!
		str = data.toString();
		for (var key in substitutions) {
			if (substitutions.hasOwnProperty(key) ) {
				str = str.split(key).join(substitutions[key]);
			}
		}
		fs.writeFile(path.join(destFolder, filename), str, next);
	});
}

Utils.copyAndModifyFiles = function (srcFolder, destFolder, files, substitutions, next) {
	async.each(files, function (file, cb) {
		Utils.copyAndModifyFile(srcFolder, destFolder, file, substitutions, cb);
	}, next);
}

module.exports = Utils;
