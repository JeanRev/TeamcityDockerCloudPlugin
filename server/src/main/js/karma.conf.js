
const webpackConfig = require('./webpack.config.js');

webpackConfig.devtool = 'inline-source-map';

module.exports = function(config) {
    config.set({
        browsers: ['PhantomJS'],
        frameworks: ['jasmine'],
        reporters: ['progress', 'junit', 'teamcity'],
        files: [
            'node_modules/jquery/dist/jquery.js',
            'karma-global.js',
            'lib/*.test.js',
            'static/image-settings.html',
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
