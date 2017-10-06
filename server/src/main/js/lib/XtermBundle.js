
// Meta-module to gather the needed XTerm.js resources.

const Terminal = require('xterm/xterm.js');
require('xterm/xterm.css');
require('xterm/addons/attach/attach.js');
require('xterm/addons/fit/fit.js');

// Export XTerm.js Terminal constructor.
module.exports = Terminal;
