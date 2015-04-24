/*global process, require */

(function() {

	"use strict";

	var args = process.argv, fs = require("fs"), mkdirp = require("mkdirp"), path = require("path");

	var SOURCE_FILE_MAPPINGS_ARG = 2;
	var TARGET_ARG = 3;
	var OPTIONS_ARG = 4;

	var sourceFileMappings = JSON.parse(args[SOURCE_FILE_MAPPINGS_ARG]);
	var target = args[TARGET_ARG];
	var options = JSON.parse(args[OPTIONS_ARG]);

	var sourcesToProcess = sourceFileMappings.length;
	var results = [];
	var problems = [];

	function compileDone() {
		if (--sourcesToProcess === 0) {
			console.log("\u0010" + JSON.stringify({
				results : results,
				problems : problems
			}));
		}
	}

	function throwIfErr(e) {
		if (e)
			throw e;
	}

	function endsWith(str, suffix) {
		return str.indexOf(suffix, str.length - suffix.length) !== -1;
	}

	sourceFileMappings.forEach(function(sourceFileMapping) {
		var input = sourceFileMapping[0];
		var outputFile = sourceFileMapping[1];
		var output = path.join(target, outputFile);
		console.log("DEFINE: " + input + " - " + outputFile);
		fs.readFile(input, "utf8", function(e, contents) {
			throwIfErr(e);
			mkdirp(path.dirname(output), function (e) {
                throwIfErr(e);
                //var content = "define(\"" + defineName + "\"," + input.trim() + ");"
				fs.writeFile(output, input.toString(), "utf8", function(e) {
					throwIfErr(e);
					results.push({
						source : input,
						result : {
							filesRead : [ input ],
							filesWritten: [output]
						}
					});
					compileDone();
				});
			});
		});
	});
})();