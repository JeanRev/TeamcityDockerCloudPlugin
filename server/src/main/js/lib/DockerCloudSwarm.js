
const globalInit = require("GlobalInit");
const Controller = require('Controller');
const SwarmSchema = require('SwarmSchema');

module.exports = {
    init: function (bs, oo, tabbedPane, params) {
        globalInit(params, function() {
            const schema = new SwarmSchema();
            new Controller(bs, oo, tabbedPane, params, schema);
        });
    },
};