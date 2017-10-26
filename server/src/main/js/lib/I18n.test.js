
const I18n = require('I18n');

describe('Text lookup', function () {

    it('should return matching string', function() {
        let i18n = new I18n({
            'foo': 'bar',
            'foo2': 'bar2'
        });

        expect(i18n.text('foo')).toEqual('bar');
        expect(i18n.text('foo2')).toEqual('bar2');
    });

    it('should return formatted key for missing resource', function() {
        let i18n = new I18n({
            'foo': 'bar'
        });

        expect(i18n.text('foo2')).toEqual('??foo2??');
    });

    it('should perform text substitution', function() {
        let i18n = new I18n({
            'foo': '{0} bar {2} {2} {1} {3}',
            'foo2': '{0}'
        });

        expect(i18n.text('foo', 0, 1, 2)).toEqual('0 bar 2 2 1 {3}');
    });
});