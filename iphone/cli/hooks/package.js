/*
 * package.js: Titanium iOS CLI package hook
 *
 * Copyright (c) 2012-2013, Appcelerator, Inc.  All Rights Reserved.
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

    cli.addHook('build.post.compile', {
        priority: 8000,
        post: function (build, finished) {
            if (!/dist-(appstore|adhoc)|device/.test(cli.argv.target)) return finished();

            switch (cli.argv.target) {
                case 'dist-appstore':
                    // logger.info('Packaging for App Store distribution');

                    // var name = build.tiapp.name,
                    //     now = new Date(),
                    //     month = now.getMonth() + 1,
                    //     day = now.getDate(),
                    //     hours = now.getHours(),
                    //     minutes = now.getMinutes(),
                    //     seconds = now.getSeconds(), 
                    //     // productsDir = path.join(build.buildDir, 'build', 'Products'),
                    //     dateStr = now.getFullYear() + '-' + (month >= 10 ? month : '0' + month) + '-' + (day >= 10 ? day : '0' + day),
                    //     archiveBundlePath = path.join(afs.resolvePath('~/Library/Developer/Xcode/Archives'),
                    //     now.getFullYear() + '-' + (month >= 10 ? month : '0' + month) + '-' + (day >= 10 ? day : '0' + day)); 
                    //     archiveBundle = path.join(archiveBundlePath, name + ' ' + (hours >= 10 ? hours : '0' + hours) + '-' + (minutes >= 10 ? minutes : '0' + minutes) + '-' +
                    //         (seconds >= 10 ? seconds : '0' + seconds) + '.xcarchive');

                    // var xcodebuildHook = cli.createHook('build.ios.xcodebuild', this, function (exe, args, opts, done) {
                    //         logger.debug(__('Invoking: %s', (exe + ' ' + args.map(function (a) { return a.indexOf(' ') !== -1 ? '"' + a + '"' : a; }).join(' ')).cyan));
                    //         exec(exe, args, opts, function (err, stdout, stderr) {
                    //             if (err) {
                    //                 logger.error(__('xcodebuild failed to run'));
                    //                 logger.error(__(err)  + '\n');
                    //                 process.exit(1);
                    //             }
                    //             done(code);
                    //         }.bind(this));
                    //     });
                    // var args = [
                    //     '-exportArchive',
                    //     '-archivePath', archiveBundle,
                    //     '-exportPath', archiveBundlePath
                    // ];

                    // if (fs.existsSync(path.join(build.projectDir, 'exportOptions.plist'))) {
                    //     args.push('-exportOptionsPlist', path.join(build.projectDir, 'exportOptions.plist'));
                    // }
                    // async.series([
                    //     function (next) {
                    //     xcodebuildHook(
                    //         build.xcodeEnv.executables.xcodebuild,
                    //         [
                    //             'archive',
                    //             '-scheme', build.tiapp.name.replace(/[-\W]/g, '_'),
                    //             '-archivePath', archiveBundle
                    //         ],
                    //         {
                    //             cwd: build.buildDir,
                    //             // env: {
                    //             //     DEVELOPER_DIR: build.xcodeEnv.path,
                    //             //     TMPDIR: process.env.TMPDIR,
                    //             //     HOME: process.env.HOME,
                    //             //     PATH: process.env.PATH,
                    //                 // TITANIUM_CLI_XCODEBUILD: 'Enjoy hacking? http://jobs.appcelerator.com/'
                    //             // }
                    //         },
                    //         next
                    //     );
                    //     }, 
                    //     function (next) {
                    //         xcodebuildHook(
                    //             build.xcodeEnv.executables.xcrun,
                    //             [
                    //                 'xcodebuild',
                    //                 '-exportArchive',
                    //                 '-archivePath', archiveBundle,
                    //                 '-exportPath', archiveBundlePath
                    //             ].concat(fs.existsSync(path.join(build.projectDir, 'exportOptions.plist'))?[
                    //                 '-exportOptionsPlist', path.join(build.projectDir, 'exportOptions.plist')
                    //                 ]:[]),
                    //             {
                    //                 cwd: build.buildDir,
                    //                 // env: {
                    //                     // DEVELOPER_DIR: build.xcodeEnv.path,
                    //                     // TMPDIR: process.env.TMPDIR,
                    //                     // HOME: process.env.HOME,
                    //                     // PATH: process.env.PATH,
                    //                     // TITANIUM_CLI_XCODEBUILD: 'Enjoy hacking? http://jobs.appcelerator.com/'
                    //                 // }
                    //             },
                    //             next
                    //         );
                    //     }
                    // ], function () {
                        // workaround for dumb Xcode4 bug that doesn't update the organizer unless files are touched in a very specific manner
                        // var temp = afs.resolvePath('~/Library/Developer/Xcode/Archives/temp');
                        // fs.renameSync(archiveBundle, temp);
                        // fs.renameSync(temp, archiveBundle);

                        // open xcode + organizer after packaging
                        logger.info(__('Launching Xcode: %s', build.xcodeEnv.xcodeapp.cyan));
                        exec('open -a "' + build.xcodeEnv.xcodeapp + '"', function (err, stdout, stderr) {
                            process.env.TI_ENV_NAME = process.env.STUDIO_NAME || 'Terminal.app';
                            exec('osascript "' + path.join(build.platformPath, 'xcode_organizer.scpt') + '"', { env: process.env }, function (err, stdout, stderr) {
                                logger.info(__('Packaging complete'));
                                finished();
                            });
                        });
                    // });
                    break;
                    
                //  if (!cli.argv['build-only']) {
                //      return finished();
                //  }

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
