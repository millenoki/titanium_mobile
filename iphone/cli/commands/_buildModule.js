/**
* iOS module build command.
*
* @module cli/_buildModule
*
* @copyright
* Copyright (c) 2014 by Appcelerator, Inc. All Rights Reserved.
*
* @license
* Licensed under the terms of the Apache Public License
* Please see the LICENSE included with this distribution for details.
*/

var appc = require('node-appc'),
	AdmZip = require('adm-zip'),
	archiver = require('archiver'),
	async = require('async'),
	crypto = require('crypto'),
	Builder = require('titanium-sdk/lib/builder'),
	ioslib = require('ioslib'),
	iosPackageJson = appc.pkginfo.package(module),
	jsanalyze = require('titanium-sdk/lib/jsanalyze'),
	ejs = require('ejs'),
	fs = require('fs'),
	markdown = require('markdown').markdown,
	path = require('path'),
	spawn = require('child_process').spawn,
	temp = require('temp'),
	ti = require('titanium-sdk'),
	util = require('util'),
	babel = require('babel-core')
	ts = require('typescript')
	wrench = require('wrench'),
	__ = appc.i18n(__dirname).__,
	parallel = appc.async.parallel,
	series = appc.async.series,
	version = appc.version;

function iOSModuleBuilder() {
	Builder.apply(this, arguments);
}

util.inherits(iOSModuleBuilder, Builder);

iOSModuleBuilder.prototype.validate = function validate(logger, config, cli) {
	Builder.prototype.config.apply(this, arguments);
	Builder.prototype.validate.apply(this, arguments);

	this.ignoreDirs = new RegExp(config.get('cli.ignoreDirs'));
    this.ignoreFiles = new RegExp(config.get('cli.ignoreFiles'));

	// cli.manifest is set by the --project-dir option's callback in cli/commands/build.js
	this.manifest = cli.manifest;
	this.moduleId = cli.manifest.moduleid;
	this.moduleName = cli.manifest.name;
	this.moduleVersion = cli.manifest.version;
	this.moduleGuid = cli.manifest.guid;

	this.buildOnly = cli.argv['build-only'];
	this.xcodeEnv = null;

	return function(finished) {
		ioslib.detect({
			// env
			xcodeSelect: config.get('osx.executables.xcodeSelect'),
			security: config.get('osx.executables.security'),
			// provisioning
			profileDir: config.get('ios.profileDir'),
			// xcode
			searchPath: config.get('paths.xcode'),
			minIosVersion: iosPackageJson.minIosVersion,
			supportedVersions: iosPackageJson.vendorDependencies.xcode
		}, function(err, iosInfo) {
			this.iosInfo = iosInfo;
			this.xcodeEnv = this.iosInfo.selectedXcode;

			if (!this.xcodeEnv) {
				// this should never happen
				logger.error(__('Unable to find suitable Xcode install') + '\n');
				process.exit(1);
			}

			finished();
		}.bind(this));
	}.bind(this);
};

iOSModuleBuilder.prototype.run = function run(logger, config, cli, finished) {
	Builder.prototype.run.apply(this, arguments);
	var compileOnly = !!cli.argv.compilejs;

	series(this, [
		function(next) {
			cli.emit('build.module.pre.construct', this, next);
		},

		'doAnalytics',
		'initialize',
		'loginfo',

		function(next) {
			cli.emit('build.module.pre.compile', this, next);
		},
		function(next) {
			if (!compileOnly) {
				series(this, [
					'processLicense',
					'processTiXcconfig',
				], next);
			} else {
				next();
			}
		},
		
		'compileJS',
		function(next) {
			if (!compileOnly) {
				series(this, [
					'buildModule',
					'createUniBinary',
					'verifyBuildArch',
					'packageModule',
				], next);
			} else {
				next();
			}
		},

		function(next) {
			if (cli.argv.run) {
				series(this, [
					'runModule',
				], next);
			} else {
				next();
			}
		},

		function(next) {
			cli.emit('build.module.post.compile', this, next);
		}
	], function(err) {
		cli.emit('build.module.finalize', this, function() {
			finished(err);
		});
	});

};

iOSModuleBuilder.prototype.doAnalytics = function doAnalytics() {
	var cli = this.cli,
		manifest = this.manifest,
		eventName = 'ios.' + cli.argv.type;

	cli.addAnalyticsEvent(eventName, {
		dir: this.cli.argv['project-dir'],
		name: this.moduleName,
		publisher: this.manifest.author,
		appid: this.moduleId,
		description: this.manifest.description,
		type: this.cli.argv.type,
		guid: this.moduleGuid,
		version: this.moduleVersion,
		copyright: this.manifest.copyright,
		date: new Date().toDateString()
	});
};

iOSModuleBuilder.prototype.initialize = function initialize() {
	this.moduleIdAsIdentifier = this.moduleId.replace(/[\s-]/g, '_').replace(/_+/g, '_').split(/\./).map(function(s) {
		return s.substring(0, 1).toUpperCase() + s.substring(1);
	}).join('');
	this.metaData = [];
	this.metaDataFile = path.join(this.projectDir, 'metadata.json');
	this.manifestFile = path.join(this.projectDir, 'manifest');
	this.templatesDir = path.join(this.platformPath, 'templates');
	this.assetsTemplateFile = path.join(this.templatesDir, 'module', 'default', 'template', 'iphone', 'Classes',
		'{{ModuleIdAsIdentifier}}ModuleAssets.m.ejs');
	this.universalBinaryDir = path.join(this.projectDir, 'build');
	this.buildAssetsDir = path.join(this.universalBinaryDir, 'assets');
	this.documentationBuildDir = path.join(this.universalBinaryDir, 'doc');

	['assets', 'documentation', 'example', 'platform', 'Resources'].forEach(function(folder) {
		var dirName = folder.toLowerCase() + 'Dir';
		this[dirName] = path.join(this.projectDir, folder);
		if (!fs.existsSync(this[dirName])) {
			this[dirName] = path.join(this.projectDir, '..', folder);
		}
	}, this);

	this.licenseDefault = "TODO: place your license here and we'll include it in the module distribution";
	this.licenseFile = path.join(this.projectDir, 'license.json');
	if (!fs.existsSync(this.licenseFile)) {
		this.licenseFile = path.join(this.projectDir, '..', 'license.json');
	}

	this.tiXcconfig = {};
	this.tiXcconfigFile = path.join(this.projectDir, 'titanium.xcconfig');

	this.moduleXcconfigFile = path.join(this.projectDir, 'module.xcconfig');
};

iOSModuleBuilder.prototype.loginfo = function loginfo() {
	this.logger.debug(__('Titanium SDK iOS directory: %s', this.platformPath.cyan));
	this.logger.info(__('Project directory: %s', this.projectDir.cyan));
	this.logger.info(__('Module ID: %s', this.moduleId.cyan));
	this.logger.info(__('ignoreDirs: %s', this.ignoreDirs));
	this.logger.info(__('universalBinaryDir: %s', this.universalBinaryDir));
	this.logger.info(__('buildAssetsDir: %s', this.buildAssetsDir));
};

iOSModuleBuilder.prototype.dirWalker = function dirWalker(currentPath, callback) {

	var ignoreDirs = this.ignoreDirs;
	var ignoreFiles = this.ignoreFiles;
	fs.readdirSync(currentPath).forEach(function(name, i, arr) {
		var currentFile = path.join(currentPath, name);
		var isDir = fs.statSync(currentFile).isDirectory();
		if (isDir) {
			if (!ignoreDirs || !ignoreDirs.test(name)) {
				this.dirWalker(currentFile, callback);
			} else {
				this.logger.warn(__('ignoring dir %s', name.cyan));
			}
		} else {
			if (!ignoreFiles || !ignoreFiles.test(name)) {
			callback(currentFile, name, i, arr);
			} else {
				this.logger.warn(__('ignoring file %s', name.cyan));
			}
		}
	}, this);
};

iOSModuleBuilder.prototype.processLicense = function processLicense() {
	if (fs.existsSync(this.licenseFile) && fs.readFileSync(this.licenseFile).toString().indexOf(this.licenseDefault) !==
		-1) {
		this.logger.warn(__('Please update the LICENSE file with your license text before distributing.'));
	}
};

iOSModuleBuilder.prototype.processTiXcconfig = function processTiXcconfig(next) {
	var re = /^(\S+)\s*=\s*(.*)$/,
		bindingReg = /\$\(([^$]+)\)/g,
		match,
		bindingMatch;

	if (fs.existsSync(this.tiXcconfigFile)) {
		fs.readFileSync(this.tiXcconfigFile).toString().split('\n').forEach(function(line) {
			match = line.match(re);
			if (match) {
				var keyList = [],
					value = match[2].trim();

				bindingMatch = bindingReg.exec(value);
				if (bindingMatch !== null) {
					while (bindingMatch !== null) {
						keyList.push(bindingMatch[1]);
						bindingMatch = bindingReg.exec(value);
					}

					keyList.forEach(function(key) {
						if (this.tiXcconfig[key]) {
							value = value.replace('$(' + key + ')', this.tiXcconfig[key]);
						}
					}, this);
				}
				this.tiXcconfig[match[1].trim()] = value;
			}
		}, this);
	}

	next();
};

iOSModuleBuilder.prototype.compileJSFiles = function compileJSFiles(jsFiles, next) {
    var minifyJS = false; //for now this breaks the source map ...
	async.eachSeries(jsFiles, function(info, next) {
		setImmediate(function() {
			var file = info.src;
			var src = file;
			if (file.indexOf('/') === 0) {
				file = path.basename(file);
			}
			var srcFile = file;
			file = file.replace(/\./g, '_');
			var relPath = path.dirname(info.path);
			var destDir = path.join(this.buildAssetsDir, relPath)
			var dest = path.join(destDir, file);
			
			this.cli.createHook('build.ios.compileJsFile', this, function(from, to,
				cb) {
				var _this = this;
				var inSourceMap = null;
                if (fs.existsSync(from + '.map')) {
                    inSourceMap =  JSON.parse(fs.readFileSync(from + '.map'));
                }
                var moduleId = this.manifest.moduleid;
				babel.transformFile(from, {
					sourceMaps: true,
					sourceMapTarget: moduleId + info.path,					
					sourceFileName: moduleId + info.path,
                    inputSourceMap:inSourceMap
				}, function(err, transformed) {
					if (err) {
						_this.logger.error('Babel error: ' + err + '\n');
						process.exit(1);
					}

					try {
						// parse the AST
						var r = jsanalyze.analyzeJs(transformed.code, {
							minify: minifyJS
						});
					} catch (ex) {
						ex.message.split('\n').forEach(_this.logger.error);
						_this.logger.log();
						process.exit(1);
					}

					// we want to sort by the "to" filename so that we correctly handle file overwriting
					if (_this.jsFilesToEncrypt.indexOf(file) === -1) {
						_this.jsFilesToEncrypt.push(path.join(relPath, file));
						_this.metaData.push.apply(_this.metaData, r.symbols);
					}

					var dir = path.dirname(to);
					fs.existsSync(dir) || wrench.mkdirSyncRecursive(dir);

					_this.unmarkBuildDirFile(to);
					var exists = fs.existsSync(to);
					if (!exists || r.contents !== fs.readFileSync(to).toString()) {
						_this.logger.debug(__(minifyJS?'Copying and minifying %s => %s' : 'Copying %s => %s', from.cyan,
							to.cyan));
						exists && fs.unlinkSync(to);
						fs.writeFileSync(to, r.contents);
						
					} else {
						_this.logger.trace(__('No change, skipping transformed file %s', to.cyan));
					}
					if (transformed.map) {
						//we remove sourcesContent as it is big and not really usefull
						delete transformed.map.sourcesContent;

						// fix file 
						transformed.map.file = info.path
						if (transformed.map.file[0] !== '/') {
							transformed.map.file = '/' + transformed.map.file;
						}
						transformed.map.file = moduleId + transformed.map.file;
						// handle wrong ts map sources path
						if (transformed.map.sources) {
							var relToBuild = path.relative(path.dirname(src), _this.assetsDir);
							transformed.map.sources = transformed.map.sources.map(function(value) {
								if (value.indexOf(relToBuild) != -1) {
									return moduleId + value.replace(relToBuild, '');
								}
								return value;
							});
						}
                        fs.writeFileSync(path.join(destDir, srcFile + '.map'), JSON.stringify(transformed.map));
                    }
					cb();
				});

			})(src, dest, next);
		}.bind(this));
	}.bind(this), next);
}

iOSModuleBuilder.prototype.dirWalker = function dirWalker(currentPath, callback) {
    var ignoreDirs = this.ignoreDirs;
    var ignoreFiles = this.ignoreFiles;
    fs.readdirSync(currentPath).forEach(function(name, i, arr) {
        var currentFile = path.join(currentPath, name);
        var isDir = fs.statSync(currentFile).isDirectory();
        if (isDir) {
            if (!ignoreDirs || !ignoreDirs.test(name)) {
                this.dirWalker(currentFile, callback);
            } else {
                this.logger.warn(__('ignoring dir %s', name.cyan));
            }
        } else {
            if (!ignoreFiles || !ignoreFiles.test(name)) {
            callback(currentFile, name, i, arr);
            } else {
                this.logger.warn(__('ignoring file %s', name.cyan));
            }
        }
    }, this);
};

iOSModuleBuilder.prototype.getTsConfig = function getTsConfig(next) {
    var options = {
        noEmitOnError: false,
        sourceMap: true,
        inlineSourceMap: false,
        outDir: this.buildAssetsDir,
        rootDir:this.assetsDir,
        allowJS: true,
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
    }

    var tsconfigPath = path.join(this.projectDir, 'tsconfig.json');
    if (fs.existsSync(tsconfigPath)) {
        var parsedConfig, errors;
        var rawConfig = ts.parseConfigFileTextToJson(tsconfigPath, fs.readFileSync(tsconfigPath, 'utf8'));
        var dirname = tsconfigPath && path.dirname(tsconfigPath);
        var basename = tsconfigPath && path.basename(tsconfigPath);
        var tsconfigJSON = rawConfig.config;
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
        //we should always overwrite those keys
        delete parsedConfig.noEmit;
        delete parsedConfig.outDir;
        Object.keys(parsedConfig).forEach(function(prop) {
            options[prop] = parsedConfig[prop];
        }, this);
    }
    return options;
}

iOSModuleBuilder.prototype.compileTsFiles = function compileTsFiles(tsFiles) {
	if (!tsFiles || tsFiles.length == 0) {
        return;
    }
    var tiTsDef = path.join(this.platformPath, '..', 'titanium.d.ts');
    tsFiles.unshift(tiTsDef);
    this.logger.debug(__('Compiling TS files: %s', tsFiles));

    //we need to make sure that babel is used in that case 
    this.useBabel = true;
	if (fs.existsSync(path.join(this.projectDir, 'typings'))) {
        this.dirWalker(path.join(this.projectDir, 'typings'), function(file) {
            if (/\.d\.ts$/.test(file)) {
                tsFiles.push(file);
            }
        }.bind(this));
    }
	var options = this.getTsConfig();
	this.logger.debug(__('Compyling TS files: %s, %s', tsFiles, JSON.stringify(options)));
	var host = ts.createCompilerHost(options);
    var program = ts.createProgram(tsFiles,options, host);
    var emitResult = program.emit();

    var allDiagnostics = ts.getPreEmitDiagnostics(program).concat(emitResult.diagnostics);

    allDiagnostics.forEach(function (diagnostic) {
        if (diagnostic.file) {
            var data = diagnostic.file.getLineAndCharacterOfPosition(diagnostic.start);
            var message = ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n');
            this.logger.debug(__('TsCompile:%s (%s, %s): %s', diagnostic.file.fileName,data.line +1,data.character +1, message ));
        } else{
            this.logger.debug(__('TsCompile:%s', diagnostic.messageText));
        }
    }.bind(this));
    this.logger.debug(__('TsCompile done!'));
}

iOSModuleBuilder.prototype.movesTsDefinitionFiles = function movesTsDefinitionFiles() {
	fs.existsSync(this.documentationBuildDir) || wrench.mkdirSyncRecursive(this.documentationBuildDir);
	this.dirWalker(this.buildAssetsDir, function(file) {
		if (/\.d\.ts$/.test(file)) {
			var relPath = file.replace(this.buildAssetsDir, '').replace(/\\/g, '/').replace(/^\//, '');
			var dest = path.join(this.documentationBuildDir, relPath);
			var dir = path.dirname(dest);
			
            this.logger.debug(__('moving doc %s => %s', file.cyan, dest.cyan));
			fs.existsSync(dir) || wrench.mkdirSyncRecursive(dir);

			//fix reference paths
			var data = fs.readFileSync(file).toString();
			data = data.replace(/\.\.\/\.\.\/assets\//, '')
			fs.writeFileSync(dest, data);
			fs.unlinkSync(file);
		}
	}.bind(this));
	//also copy existing definition files
	this.dirWalker(this.assetsDir, function(file) {
		if (/\.d\.ts$/.test(file)) {
			var relPath = file.replace(this.assetsDir, '').replace(/\\/g, '/').replace(/^\//, '');
			var dest = path.join(this.documentationBuildDir, relPath);
			var dir = path.dirname(dest);
            this.logger.debug(__('copying doc %s => %s', file.cyan, dest.cyan));
			fs.existsSync(dir) || wrench.mkdirSyncRecursive(dir);
			fs.createReadStream(file).pipe(fs.createWriteStream(dest));
		}
	}.bind(this));
}

iOSModuleBuilder.prototype.compileJS = function compileJS(next) {
	this.jsFilesToEncrypt = [];

	var moduleJS = this.moduleId + '.js',
		moduleTS = this.moduleId + '.ts',
		renderData = {
			'moduleIdAsIdentifier': this.moduleIdAsIdentifier,
			'mainEncryptedAssetReturn': 'return filterDataInRange([NSData dataWithBytesNoCopy:data length:sizeof(data) freeWhenDone:NO], ranges[0]);',
			'allEncryptedAssetsReturn': 'NSNumber *index = [map objectForKey:path];' +
				'\n\t\tif (index == nil) {\n\t\t\treturn nil;\n\t\t}' +
				'\n\t\treturn filterDataInRange([NSData dataWithBytesNoCopy:data length:sizeof(data) freeWhenDone:NO], ranges[index.integerValue]);'
		},
		titaniumPrepHook = this.cli.createHook('build.ios.titaniumprep', this, function(exe, args, opts, done) {
			var tries = 0,
				completed = false,
				jsFilesToEncrypt = opts.jsFiles,
				placeHolderName = opts.placeHolder;

			this.logger.info('Encrypting JavaScript files: %s', (exe + ' "' + args.join('" "') + '"').cyan);
			jsFilesToEncrypt.forEach(function(file) {
				this.logger.debug(__('Preparing %s', file.cyan));
			}, this);

			async.whilst(
				function() {
					if (tries > 3) {
						// we failed 3 times, so just give up
						this.logger.error(__('titanium_prep failed to complete successfully'));
						this.logger.error(__('Try cleaning this project and build again') + '\n');
						process.exit(1);
					}
					return !completed;
				},
				function(cb) {
					var child = spawn(exe, args, opts),
						out = '';

					child.stdin.write(jsFilesToEncrypt.map(function(name) {
						return name.replace(/\./g, '_');
					}).join('\n'));
					child.stdin.end();

					child.stdout.on('data', function(data) {
						out += data.toString();
					});

					child.on('close', function(code) {
						if (code) {
							this.logger.error(__('titanium_prep failed to run (%s)', code) + '\n');
							process.exit(1);
						}

						if (out.indexOf('initWithObjectsAndKeys') !== -1) {
							// success!
							renderData[placeHolderName] = out;

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

	var tasks = [
		// 1. compile module ts
		function(cb) {
			if (fs.existsSync(this.buildAssetsDir)) {
				wrench.rmdirSyncRecursive(this.buildAssetsDir)
			}
			wrench.mkdirSyncRecursive(this.buildAssetsDir);

			var tsFiles = [path.join(this.assetsDir, moduleTS)];
			if (!fs.existsSync(tsFiles[0])) {
				renderData.mainEncryptedAsset = '';
				renderData.mainEncryptedAssetReturn = 'return nil;';
				return cb();
			}
			var defFile = path.join(this.assetsDir, this.moduleId + '.d.ts');
			if (fs.existsSync(defFile)) {
				tsFiles.push(defFile);
			}

			this.compileTsFiles(tsFiles);

			this.compileJSFiles([{
				path:moduleTS.replace(/\.ts$/, '.js'),
				src:path.join(this.buildAssetsDir, moduleTS.replace(/\.ts$/, '.js'))
			}], function() {
				titaniumPrepHook(
					path.join(this.platformPath, 'titanium_prep'), [this.moduleId, this.buildAssetsDir, this.moduleGuid], {
						cwd:this.universalBinaryDir,
						'jsFiles': this.jsFilesToEncrypt,
						'placeHolder': 'mainEncryptedAsset'
					},
					cb
				);
			}.bind(this))

		},
		// 1. compile module js
		function(cb) {
			//if a jsFilesToEncrypt file exists then it means we already got the module mainEncryptedAsset
			if (this.jsFilesToEncrypt.length !== 0) {
				return cb();
			}
			if (!fs.existsSync(path.join(this.assetsDir, moduleJS))) {
				renderData.mainEncryptedAsset = '';
				renderData.mainEncryptedAssetReturn = 'return nil;';
				return cb();
			}

			this.compileJSFiles([{
				path:moduleJS,
				src:path.join(this.assetsDir, moduleJS)
			}], function() {
				titaniumPrepHook(
					path.join(this.platformPath, 'titanium_prep'), [this.moduleId, this.buildAssetsDir, this.moduleGuid], {
						cwd:this.universalBinaryDir,
						'jsFiles': this.jsFilesToEncrypt,
						'placeHolder': 'mainEncryptedAsset'
					},
					cb
				);
			}.bind(this))

		},

		// 2. compile all other js files in assets dir
		function(cb) {
				if (!fs.existsSync(this.assetsDir)) {
                    renderData.allEncryptedAssets = '';
                    renderData.allEncryptedAssetsReturn = 'return nil;';
                    return cb();
				}

				var jsFilesCount = this.jsFilesToEncrypt.length;

				if (jsFilesCount === 0) {
                    renderData.allEncryptedAssets = '';
                    renderData.allEncryptedAssetsReturn = 'return nil;';
                    return cb();
				}

				fs.existsSync(this.buildAssetsDir) || wrench.mkdirSyncRecursive(this.buildAssetsDir);

				var jsFiles = [];
				var tsFiles = [];
				this.dirWalker(this.assetsDir, function(file) {
					if (path.extname(file) === '.js') {
						jsFiles.push({
							src:file,
							path:path.relative(this.assetsDir, file) 
						});
					} else if (/\.d\.ts$/.test(file)) {
						tsFiles.push(file);
					} else if (path.extname(file) === '.ts') {
						var relPath = file.replace(this.assetsDir, '').replace(/\\/g, '/').replace(/^\//, '');
						tsFiles.push(file);
						jsFiles.push({
							src:path.join(this.buildAssetsDir, relPath.replace(/\.ts$/, '.js')),
							path:relPath.replace(/\.ts$/, '.js')
						});
					}
				}.bind(this));


				this.compileTsFiles(tsFiles);
				this.movesTsDefinitionFiles();

				this.compileJSFiles(jsFiles, function() {
					titaniumPrepHook(
						path.join(this.platformPath, 'titanium_prep'), [this.moduleId, this.buildAssetsDir, this.moduleGuid], {
						cwd:this.universalBinaryDir,
							'jsFiles': this.jsFilesToEncrypt,
							'placeHolder': 'allEncryptedAssets'
						},
						cb
					);
				}.bind(this))

				// this.logger.warn(this.jsFilesToEncrypt);

				// titaniumPrepHook(
				// 	path.join(this.platformPath, 'titanium_prep'), [this.moduleId, this.assetsDir, this.moduleGuid], {
				// 		'jsFiles': this.jsFilesToEncrypt,
				// 		'placeHolder': 'allEncryptedAssets'
				// 	},
				// 	cb
				// );
			// } catch (e) {
			// 	renderData.allEncryptedAssets = renderData.mainEncryptedAsset;
			// 	renderData.allEncryptedAssetsReturn = 'return nil;';
			// 	cb();
			// }
		},

		// 3. write encrypted data to template
		function(cb) {
			var data = ejs.render(fs.readFileSync(this.assetsTemplateFile).toString(), renderData),
				moduleAssetsFile = path.join(this.projectDir, 'Classes', this.moduleIdAsIdentifier + 'ModuleAssets.m'),
				moduleAssetsHeaderFile = path.join(this.projectDir, 'Classes', this.moduleIdAsIdentifier + 'ModuleAssets.h');

			this.logger.debug(__('Writing module assets file: %s', moduleAssetsFile.cyan));
			fs.writeFileSync(moduleAssetsFile, data);
			if (!fs.existsSync(moduleAssetsHeaderFile)) {
				fs.writeFileSync(moduleAssetsHeaderFile, ejs.render(fs.readFileSync(path.join(this.templatesDir, 'module',
					'default', 'template', 'iphone', 'Classes',
					'{{ModuleIdAsIdentifier}}ModuleAssets.h.ejs')).toString(), renderData));
			}
			cb();
		},

		// 4. genereate exports
		function(cb) {
			// this.tiSymbols.forEach(function(symbols) {
			// 	// var r = jsanalyze.analyzeJsFile(path.join(this.assetsDir, file), {
			// 	// 	minify: true
			// 	// });
			// 	// this.tiSymbols[file] = r.symbols;
			// 	this.metaData.push.apply(this.metaData, symbols);
			// }.bind(this));

			fs.existsSync(this.metaDataFile) && fs.unlinkSync(this.metaDataFile);
			fs.writeFileSync(this.metaDataFile, JSON.stringify({
				"exports": this.metaData
			}));

			cb();
		}
	];

	appc.async.series(this, tasks, next);
};

iOSModuleBuilder.prototype.buildModule = function buildModule(next) {
	var opts = {
		cwd: this.projectDir
	};
	var xcodebuildHook = this.cli.createHook('build.module.ios.xcodebuild', this, function(exe, args, opts, type, done) {
		this.logger.debug(exe + ' ' + args.join(' '));

		var p = spawn(exe, args, opts),
			out = [],
			err = [],
			stopOutputting = false;

		p.stdout.on('data', function(data) {
			data.toString().split('\n').forEach(function(line) {
				if (line.length) {
					out.push(line);
					if (line.indexOf('Failed to minify') != -1) {
						stopOutputting = true;
					}
					if (!stopOutputting) {
						this.logger.trace('[' + type + '] ' + line);
					}
				}
			}, this);
		}.bind(this));

		p.stderr.on('data', function(data) {
			data.toString().split('\n').forEach(function(line) {
				if (line.length) {
					err.push(line);
				}
			}, this);
		}.bind(this));

		p.on('close', function(code, signal) {
			if (code) {
				// just print the entire error buffer
				err.forEach(function(line) {
					this.logger.error('[' + type + '] ' + line);
				}, this);
				this.logger.log();
				process.exit(1);
			}

			// end of the line
			done(code);
		}.bind(this));
	});

	process.env.DEVELOPER_DIR = this.xcodeEnv.path;

	var count = 0;

	function done() {
		if (++count === 2) {
			next();
		}
	}

	// Create a build for the device
	xcodebuildHook(this.xcodeEnv.executables.xcodebuild, [
		'-configuration', 'Release',
		'-sdk', 'iphoneos',
		'clean', 'build',
		'CONFIGURATION_BUILD_DIR=' + path.join(this.universalBinaryDir, 'TiRelease', 'iphoneos'),
		'TITANIUM_CLI_XCODEBUILD=1'
	], opts, 'xcode-dist', function() {
		// Create a build for the simulator
		xcodebuildHook(this.xcodeEnv.executables.xcodebuild, [
			'-configuration', 'Release',
			'-sdk', 'iphonesimulator',
			'clean', 'build',
			'CONFIGURATION_BUILD_DIR=' + path.join(this.universalBinaryDir, 'TiRelease', 'iphonesimulator'),
			'TITANIUM_CLI_XCODEBUILD=1'
		], opts, 'xcode-sim', next);
	}.bind(this));

};

iOSModuleBuilder.prototype.createUniBinary = function createUniBinary(next) {
	// Create a universal build by merging the all builds to a single binary
	var binaryFiles = [],
		outputFile = path.join(this.projectDir, 'build', 'lib' + this.moduleId + '.a'),
		lipoArgs = [
			'-create',
			'-output',
			outputFile
		];

	this.dirWalker(path.join(this.universalBinaryDir, 'TiRelease'), function(file) {
		if (path.extname(file) === '.a' && file.indexOf(this.moduleId + '.build') === -1 && file.indexOf(this.moduleId) > -
			1
		) {
			binaryFiles.push(file);
		}
	}.bind(this));
    
    this.logger.debug(this.xcodeEnv.executables.lipo + ' ' + binaryFiles.concat(lipoArgs).join(' '));

	appc.subprocess.run(this.xcodeEnv.executables.lipo, binaryFiles.concat(lipoArgs), function(code, out, err) {
		next();
	});
};

iOSModuleBuilder.prototype.verifyBuildArch = function verifyBuildArch(next) {
	var args = ['-info', path.join(this.projectDir, 'build', 'lib' + this.moduleId + '.a')];

	appc.subprocess.run(this.xcodeEnv.executables.lipo, args, function(code, out, err) {
		if (code) {
			this.logger.error(__('Unable to determine the compiled module\'s architecture (code %s):', code));
			this.logger.error(err.trim() + '\n');
			process.exit(1);
		}

		var manifestArchs = this.manifest.architectures && this.manifest.architectures.split(' '),
			buildArchs = out.substr(out.lastIndexOf(':') + 1).trim().split(' '),
			buildDiff = manifestArchs && manifestArchs.filter(function(i) {
				return buildArchs.indexOf(i) < 0;
			});

		if (manifestArchs && (buildArchs.length !== manifestArchs.length || buildDiff.length > 0)) {
			this.logger.error(__(
				'There is discrepancy between the architectures specified in module manifest and compiled binary.'));
			this.logger.error(__('Architectures in manifest: %s', manifestArchs.join(', ')));
			this.logger.error(__('Compiled binary architectures: %s', buildArchs.join(', ')));
			this.logger.error(__('Please update manifest to match module binary architectures.') + '\n');
			process.exit(1);
		}

		if (buildArchs.indexOf('arm64') === -1) {
			this.logger.warn(__('The module is missing 64-bit support.'));
		}

		next();
	}.bind(this));
};

iOSModuleBuilder.prototype.packageModule = function packageModule() {
	var dest = archiver('zip', {
			forceUTC: true
		}),
		zipStream,
		origConsoleError = console.error,
		name = this.moduleName,
		moduleId = this.moduleId,
		version = this.moduleVersion,
		moduleZipName = [moduleId, '-iphone-', version, '.zip'].join(''),
		moduleZipFullPath = path.join(this.cli.argv['output-dir'] ? this.cli.argv['output-dir'] : path.join(this.projectDir, 'dist'),
			moduleZipName),
		moduleFolder = path.join('modules', 'iphone', moduleId, version),
		binarylibName = 'lib' + moduleId + '.a',
		binarylibFile = path.join(this.projectDir, 'build', binarylibName);

	this.moduleZipPath = moduleZipFullPath;

	// since the archiver library didn't set max listeners, we squelch all error output
	console.error = function() {};

	try {
		// if the zip file is there, remove it
        var distDir = path.dirname(moduleZipFullPath);
        if (!fs.existsSync(distDir)) {
            fs.mkdirSync(distDir);
        }
		fs.existsSync(moduleZipFullPath) && fs.unlinkSync(moduleZipFullPath);
		zipStream = fs.createWriteStream(moduleZipFullPath);
		zipStream.on('close', function() {
			console.error = origConsoleError;
		});
		dest.catchEarlyExitAttached = true; // silence exceptions
		dest.pipe(zipStream);

		this.logger.info(__('Creating module zip'));

		// 1. documentation folder
		var mdRegExp = /\.md$/;
		(function walk(dir, parent) {
			if (!fs.existsSync(dir)) return;

			fs.readdirSync(dir).forEach(function(name) {
				var file = path.join(dir, name);
				if (!fs.existsSync(file)) return;
				if (fs.statSync(file).isDirectory()) {
					return walk(file, path.join(parent, name));
				}

				var contents = fs.readFileSync(file).toString();

				if (mdRegExp.test(name)) {
					contents = markdown.toHTML(contents);
					name = name.replace(/\.md$/, '.html');
				}

				dest.append(contents, {
					name: path.join(parent, name)
				});
			});
		}(this.documentationDir, path.join(moduleFolder, 'documentation')));

		// built doc
        if (fs.existsSync(this.documentationBuildDir)) {
            dest.directory(this.documentationBuildDir, path.join(moduleFolder, 'documentation'));
        }

		// 2. example folder
        if (fs.existsSync(this.exampleDir)) {
            dest.directory(this.exampleDir, path.join(moduleFolder, 'example'));
        }

		// 3. platform folder
		if (fs.existsSync(this.platformDir)) {
            dest.directory(this.platformDir, path.join(moduleFolder, 'platform'));
		}

		// 4. Resources folder
		if (fs.existsSync(this.resourcesDir)) {
			this.dirWalker(this.resourcesDir, function(file, name) {
				if (name !== 'README.md') {
                    dest.file(file,  {name:path.join(moduleFolder, 'Resources', path.relative(this.resourcesDir, file))});
				}
			}.bind(this));
		}

		// 5. assets folder, not including js files
		if (fs.existsSync(this.assetsDir)) {
			this.dirWalker(this.assetsDir, function(file) {
				if (path.extname(file) != '.js' && path.extname(file) != '.ts') {
                    dest.file(file,  {name:path.join(moduleFolder, 'assets', path.relative(this.assetsDir, file))});
				}
			}.bind(this));
		}

		if (fs.existsSync(this.buildAssetsDir)) {
			this.dirWalker(this.buildAssetsDir, function(file) {
				if (/\.js\.map$/.test(file)) {
                    dest.file(file,  {name:path.join(moduleFolder, 'assets', path.relative(this.buildAssetsDir, file))});
				}
			}.bind(this));
		}

		// 6. the merge *.a file
		// 7. license.json file
		// 8. manifest
		// 9. module.xcconfig
		// 10. metadata.json
        dest.file(binarylibFile, {name:path.join(moduleFolder, binarylibName)});
        if (fs.existsSync(this.licenseFile)) {
            dest.file(this.licenseFile, {name:path.join(moduleFolder,'license.json')});
        }
        dest.file(this.manifestFile, {name:path.join(moduleFolder, 'manifest')});
        if (fs.existsSync(this.moduleXcconfigFile)) {
            dest.file(this.moduleXcconfigFile, {name:path.join(moduleFolder, 'module.xcconfig')});
        }
        if (fs.existsSync(this.metaDataFile)) {
            dest.file(this.metaDataFile, {name:path.join(moduleFolder,'metadata.json')});
        }

		this.logger.info(__('Writing module zip: %s', moduleZipFullPath));
		dest.finalize();
	} catch (ex) {
		console.error = origConsoleError;
		throw ex;
	}
};

iOSModuleBuilder.prototype.runModule = function runModule(next) {
	if (this.buildOnly) {
		return next();
	}

	var tmpName,
		tmpDir = temp.path('ti-ios-module-build-'),
		tmpProjectDir;

	function checkLine(line, logger) {
		var re = new RegExp(
			'(?:\u001b\\[\\d+m)?\\[?(' +
			logger.getLevels().join('|') +
			')\\]?\s*(?:\u001b\\[\\d+m)?(.*)', 'i'
		);

		if (line) {
			var m = line.match(re);
			if (m) {
				logger[m[1].toLowerCase()](m[2].trim());
			} else {
				logger.debug(line);
			}
		}
	}

	function runTiCommand(cmd, args, logger, callback) {
		// when calling a Windows batch file, we need to escape ampersands in the command
		if (process.platform == 'win32' && /\.bat$/.test(cmd)) {
			args.unshift('/S', '/C', cmd.replace(/\&/g, '^&'));
			cmd = 'cmd.exe';
		}

		var child = spawn(cmd, args);

		child.stdout.on('data', function(data) {
			data.toString().split('\n').forEach(function(line) {
				checkLine(line, logger);
			});

		});

		child.stderr.on('data', function(data) {
			data.toString().split('\n').forEach(function(line) {
				checkLine(line, logger);
			});
		});

		child.on('close', function(code) {
			if (code) {
				logger.error(__('Failed to run ti %s', args[0]));
				logger.error();
				err.trim().split('\n').forEach(this.logger.error);
				logger.log();
				process.exit(1);
			}

			callback();
		});
	}

	series(this, [
		function(cb) {
			// 1. create temp dir
			wrench.mkdirSyncRecursive(tmpDir);

			// 2. create temp proj
			this.logger.debug(__('Staging module project at %s', tmpDir.cyan));
			runTiCommand(
				'ti', [
					'create',
					'--id', this.moduleId,
					'-n', this.moduleName,
					'-t', 'app',
					'-u', 'localhost',
					'-d', tmpDir,
					'-p', 'ios',
					'--force'
				],
				this.logger,
				cb
			);
		},

		function(cb) {
			tmpProjectDir = path.join(tmpDir, this.moduleName);
			this.logger.debug(__('Created temp project %s', tmpProjectDir.cyan));

			// 3. patch tiapp.xml with module id
			var data = fs.readFileSync(path.join(tmpProjectDir, 'tiapp.xml')).toString();
			var result = data.replace(/<modules>/g, '<modules>\n\t\t<module platform="iphone">' + this.moduleId + '</module>');
			fs.writeFileSync(path.join(tmpProjectDir, 'tiapp.xml'), result);

			// 4. copy files in example to Resource
			appc.fs.copyDirSyncRecursive(
				this.exampleDir,
				path.join(tmpProjectDir, 'Resources'), {
					preserve: true,
					logger: this.logger.debug
				}
			);

			// 5. unzip module to the tmp dir
			var zip = new AdmZip(this.moduleZipPath);
			zip.extractAllTo(tmpProjectDir, true);

			cb();
		},

		function(cb) {
			// 6. run the app
			this.logger.debug(__('Running example project...', tmpDir.cyan));
			runTiCommand(
				'ti', [
					'build',
					'-p', 'ios',
					'-d', tmpProjectDir
				],
				this.logger,
				cb
			);
		}
	], next);
};

// create the builder instance and expose the public api
(function(iOSModuleBuilder) {
	exports.validate = iOSModuleBuilder.validate.bind(iOSModuleBuilder);
	exports.run = iOSModuleBuilder.run.bind(iOSModuleBuilder);
}(new iOSModuleBuilder(module)));