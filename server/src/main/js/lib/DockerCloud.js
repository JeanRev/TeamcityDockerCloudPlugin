
const Controller = require('Controller');
const Schema = require('Schema');

module.exports = {
    init: function (bs, oo, tabbedPane, params) {
        const schema = new Schema();
        new Controller(bs, oo, tabbedPane, params, schema);
    },
};