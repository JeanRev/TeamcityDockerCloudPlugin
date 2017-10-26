
const webpackConfig = require('./webpack.config.js');
process.env.CHROME_BIN = require('puppeteer').executablePath();

webpackConfig.devtool = 'inline-source-map';

module.exports = function(config) {
    config.set({
        browsers: ['ChromeHeadlessNoSandbox'],
        customLaunchers: {
            ChromeHeadlessNoSandbox: {
                base: 'ChromeHeadless',
                flags: ['--no-sandbox']
            }
        },
        frameworks: ['jasmine'],
        reporters: ['progress', 'junit', 'teamcity'],
        files: [
            'node_modules/jquery/dist/jquery.js',
            'karma-global.js',
            'lib/*.test.js'
        ],

        preprocessors: {
            'lib/*.js': [ 'webpack', 'sourcemap' ]
        },

        webpack: webpackConfig,

        junitReporter: {
            outputDir: 'test-results/karma'
        }
    });
};
