const path = require('path');

module.exports = {
    entry: 'DockerCloud',
    output: {
        path: __dirname + "/dist",
        filename: 'docker-cloud.js',
        library: 'DockerCloud',
    },
    devtool: 'source-map',
    module: {
        rules: [
            {
                test: /^.*\.css$/,
                use: [
                    'style-loader', 'css-loader'
                ]
            },
            {
                test: /\.js$/,
                exclude: /node_modules/,
                use: {
                    loader: 'babel-loader',
                    options: {
                        plugins: ['babel-plugin-syntax-dynamic-import'],
                        presets: [['env', {
                            "targets": {
                                "browsers": ["ie >= 9"]
                            }
                        }]],
                        env: {
                            "production": {
                                "presets": ["minify"]
                            }
                        }
                    }
                }
            }
        ]
    },
    resolve: {
        alias: {
            xterm: path.resolve(__dirname, 'node_modules/xterm/dist'),
            clipboard: path.resolve(__dirname, 'node_modules/clipboard/dist/clipboard.min.js'),
            'ua-parser': path.resolve(__dirname, 'node_modules/ua-parser-js/dist/ua-parser.min.js'),
            'base64-js': path.resolve(__dirname, 'node_modules/base64-js/base64js.min.js')
        },
        modules: [ path.resolve(__dirname, "lib"), "node_modules" ]

    }
};
