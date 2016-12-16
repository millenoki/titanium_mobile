/*
 * package.js: Titanium iOS CLI package hook
 *
 * Copyright (c) 2012-2016, Appcelerator, Inc.  All Rights Reserved.
 * See the LICENSE file for more information.
 */

var appc = require('node-appc'),
    __ = appc.i18n(__dirname).__,
    afs = appc.fs,
    fs = require('fs'),
    path = require('path'),
    async = require('async'),
    wrench = require('wrench'),
    exec = require('child_process').exec;

var deleteFolderRecursive = function(path) {
  if( fs.existsSync(path) ) {
      fs.readdirSync(path).forEach(function(file,index) {
        var curPath = path + "/" + file;
          if(fs.statSync(curPath).isDirectory()) { // recurse
              deleteFolderRecursive(curPath);
          } else { // delete file
              fs.unlinkSync(curPath);
          }
      });
      fs.rmdirSync(path);
    }
};

exports.cliVersion = '>=3.2';

exports.init = function (logger, config, cli) {
	cli.on('build.ios.xcodebuild', {
		pre: function (data, finished) {
			if (this.target !== 'dist-appstore') {
				return finished();
			}

			var stagingArchiveDir = path.join(this.buildDir, 'staging.xcarchive');
			fs.existsSync(stagingArchiveDir) && wrench.rmdirSyncRecursive(stagingArchiveDir);

			// inject the temporary archive path into the xcodebuild args
			var args = data.args[1];
			var p = args.indexOf('-archivePath');
			if (p === -1) {
				args.push('-archivePath', stagingArchiveDir);
			} else {
				args[p + 1] = stagingArchiveDir;
			}

			finished();
		}
	});

	cli.on('build.post.compile', {
        priority: 8000,
        post: function (build, finished) {
            if (!/dist-(appstore|adhoc)|device/.test(cli.argv.target)) return finished();

            switch (cli.argv.target) {
                case 'dist-appstore':
					logger.info(__('Preparing xcarchive'));

					var stagingArchiveDir = path.join(builder.buildDir, 'staging.xcarchive');
					if (!fs.existsSync(stagingArchiveDir)) {
						return finished(new Error(__('Staging archive directory does not exist')));
					}

					var productsDir = path.join(builder.buildDir, 'build', 'Products');
					if (!fs.existsSync(productsDir)) {
						return finished(new Error(__('Products directory does not exist')));
					}

					// copy symbols
					var archiveDsymDir = path.join(stagingArchiveDir, 'dSYMs');
					fs.existsSync(archiveDsymDir) || wrench.mkdirSyncRecursive(archiveDsymDir);
					var bcSymbolMapsDir = path.join(stagingArchiveDir, 'BCSymbolMaps');
					fs.existsSync(bcSymbolMapsDir) || wrench.mkdirSyncRecursive(bcSymbolMapsDir);
					var dsymRegExp = /\.dSYM$/;
					var bcSymbolMapsRegExp = /\.bcsymbolmap$/;
					fs.readdirSync(productsDir).forEach(function (name) {
						var subdir = path.join(productsDir, name);
						if (fs.existsSync(subdir) && fs.statSync(subdir).isDirectory()) {
							fs.readdirSync(subdir).forEach(function (name) {
								var file = path.join(subdir, name);
								if (dsymRegExp.test(name) && fs.existsSync(file) && fs.statSync(file).isDirectory()) {
									logger.info(__('Archiving debug symbols: %s', file.cyan));
									wrench.copyDirSyncRecursive(file, path.join(archiveDsymDir, name), { forceDelete: false });
								} else if (bcSymbolMapsRegExp.test(name) && fs.existsSync(file) && fs.statSync(file).isFile()) {
									var dest = path.join(bcSymbolMapsDir, name);
									logger.info(__('Archiving Bitcode Symbol Map: %s', file.cyan));
									fs.writeFileSync(dest, fs.readFileSync(file));
								}
							});
						}
					});

					var name = builder.tiapp.name;
					var now = new Date;
					var month = now.getMonth() + 1;
					var day = now.getDate();
					var hours = now.getHours();
					var minutes = now.getMinutes();
					var seconds = now.getSeconds();
					var date = now.getFullYear() + '-' + (month >= 10 ? month : '0' + month) + '-' + (day >= 10 ? day : '0' + day);
					var time = (hours >= 10 ? hours : '0' + hours) + '-' + (minutes >= 10 ? minutes : '0' + minutes) + '-' + (seconds >= 10 ? seconds : '0' + seconds);

					var archivesDir = afs.resolvePath('~/Library/Developer/Xcode/Archives', date);
					var dest = path.join(archivesDir, name + ' ' + date + ' ' + time + '.xcarchive');

					// move the finished archive directory into the correct location
					fs.existsSync(archivesDir) || wrench.mkdirSyncRecursive(archivesDir);
					fs.renameSync(stagingArchiveDir, dest);

					// open xcode + organizer after packaging
					logger.info(__('Launching Xcode: %s', builder.xcodeEnv.xcodeapp.cyan));
					exec('open -a "' + builder.xcodeEnv.xcodeapp + '"', function (err, stdout, stderr) {
						process.env.TI_ENV_NAME = process.env.STUDIO_NAME || 'Terminal.app';
						exec('osascript "' + path.join(builder.platformPath, 'xcode_organizer.scpt') + '"', { env: process.env }, function (err, stdout, stderr) {
							logger.info(__('Packaging complete'));
							finished();
						});
					});
					return;

                case 'device':
                case 'dist-adhoc':
                    logger.info('Packaging for Ad Hoc distribution');
                    var pkgapp = path.join(build.xcodeEnv.path, 'Platforms', 'iPhoneOS.platform', 'Developer', 'usr', 'bin', 'PackageApplication');
                    exec('"' + pkgapp + '" "' + build.xcodeAppDir + '"', function (err, stdout, stderr) {
                        if (err) {
                            logger.error(__('Failed to package application'));
                            stderr.split('\n').forEach(logger.error);
                            return finished();
                        }

                        var appName = build.tiapp.name;
                        if (cli.argv.target == 'device')
                            appName += '_dev';
                        
                        var ipa = path.join(path.dirname(build.xcodeAppDir), build.tiapp.name + '.ipa'),
                            dest = ipa,
                            dsymfilename=build.tiapp.name + '.app.dSYM'
                            dsym = path.join(path.dirname(build.xcodeAppDir), dsymfilename);
                        
                        if (cli.argv['output-dir']) {
                            dest = path.join(cli.argv['output-dir'], appName + '.ipa');
                            afs.exists(dest) && fs.unlinkSync(dest);
                            afs.copyFileSync(ipa, dest, { logger: logger.debug });

                            dest = path.join(cli.argv['output-dir'], appName + '.app.dSYM');
                            afs.exists(dest) && deleteFolderRecursive(dest);
                            afs.copyDirSyncRecursive(dsym, dest, { logger: logger.debug });

                            dest = path.join(cli.argv['output-dir'], appName + '.app.dSYM.zip');
                            afs.exists(dest) && fs.unlinkSync(dest);
                            exec('cd "' + path.dirname(dsym) + '"; /usr/bin/zip -r  "' + dest +  '" "' + dsymfilename +  '"', function (err, stdout, stderr) {
                                logger.info(__('Packaging complete'));
                                logger.info(__('Package location: %s', dest.cyan));
                                finished();
                            });
                        }
                        else finished();
                    });
                    break;
            }
        }
    });

};
