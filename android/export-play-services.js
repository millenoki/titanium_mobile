const path = require('path');
const fs = require('fs');
const AdmZip = require('adm-zip');

var argv = require('yargs').argv;

if (!argv.output) {
    throw "missing output dir"
}

if (!argv.input) {
    throw "missing input dir"
}

if (!argv.version) {
    throw "missing version"
}

if (!fs.existsSync(argv.output)) {
    fs.mkdirSync(argv.output);
}

fs.readdir(argv.input, function(err, files) {
    if (err) {
        throw err;
    }
    files.forEach(function(element) {
        var zipPath = path.join(argv.input, element, argv.version, element + '-' + argv.version +
            '.aar');
        if (fs.existsSync(zipPath)) {
            var zip = new AdmZip(zipPath);
            zip.getEntries().forEach(function(el){console.log(el.name)})

            try {
                zip.extractEntryTo('classes.jar', argv.output, false, true)
                fs.renameSync(path.join(argv.output, 'classes.jar'), path.join(argv.output, element + '.jar'));
            } catch(e) {
                console.log(element + ' has no classes.jar: ' + e.toString());
            }
        }
    })
})