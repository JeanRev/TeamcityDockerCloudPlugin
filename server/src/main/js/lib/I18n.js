
const Logger = require('Logger');

function I18n(translations) {

    this.text = text;

    function text(key, ...args) {

        let translation = translations[key];
        if (!translation) {
            Logger.logError('Missing i18n key: ' + key);
            return '??' + key + '??';
        }

        if (args && args.length) {
            let index = 0;
            for (let arg of args) {
                translation = translation.replace(new RegExp('\\{' + index++ + '\\}', 'g'), arg);
            }
        }

        return translation;
    }
}

module.exports = I18n;