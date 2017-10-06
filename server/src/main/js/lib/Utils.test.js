
const Utils = require('Utils');



describe('Version number comparator', function () {

    it('should order valid version numbers', function () {
        // An intentionally failing test. No code within expect() will never equal 4.
        expect(Utils.compareVersionNumbers('1.0', '2.0')).toBeLessThan(0);
        expect(Utils.compareVersionNumbers('2.0', '2.0')).toEqual(0);
        expect(Utils.compareVersionNumbers('2.0', '1.0')).toBeGreaterThan(0);
        expect(Utils.compareVersionNumbers('2.0', '2.0.1')).toBeLessThan(0);
    });

    it('should treat unparseable token digit as 0', function () {
        // An intentionally failing test. No code within expect() will never equal 4.
        expect(Utils.compareVersionNumbers('0', 'hello world')).toEqual(0);
        expect(Utils.compareVersionNumbers('1.0', '1.a')).toEqual(0);
        expect(Utils.compareVersionNumbers('1.0', '1.a')).toEqual(0);
        expect(Utils.compareVersionNumbers('2.0', '2.0')).toEqual(0);
        expect(Utils.compareVersionNumbers('2.0.0', '2.0.a')).toEqual(0);
        expect(Utils.compareVersionNumbers('2.0.0', '2.0.-1')).toEqual(0);
        expect(Utils.compareVersionNumbers('2.0.0.0', '2.0.404a.0')).toEqual(0);
    });
});

describe('Sanitizing Daemon URI', function() {

    it('should preserve the given scheme if any', function () {
        expect(Utils.sanitizeURI('tpc://hostname', false)).toEqual('tpc://hostname');
        expect(Utils.sanitizeURI('unix:///some_socket', true)).toEqual('unix:///some_socket');
        expect(Utils.sanitizeURI('blah://this is completely wrong', true)).toEqual('blah://this is completely wrong');

    });

    it('should auto-detect IPv4 address', function() {
        expect(Utils.sanitizeURI('127.0.0.1', false)).toEqual('tcp://127.0.0.1');
        expect(Utils.sanitizeURI('127.0.0.1', true)).toEqual('tcp://127.0.0.1');
    });

    it('should auto-detect IPv6 address', function() {
        expect(Utils.sanitizeURI('0000:0000:0000:0000:0000:0000:0000:0001', false)).toEqual('tcp://0000:0000:0000:0000:0000:0000:0000:0001');
        expect(Utils.sanitizeURI('::1', true)).toEqual('tcp://::1');
    });

    it('should auto detect unix scheme when not on windows host', function() {
        expect(Utils.sanitizeURI('/some/path', false)).toEqual('unix:///some/path');
    });

    it('should auto correct the count of leading slashes in the scheme specific part for unix sockets', function() {
        expect(Utils.sanitizeURI('unix:/some/path', false)).toEqual('unix:///some/path');
        expect(Utils.sanitizeURI('unix://some/path', false)).toEqual('unix:///some/path');
        expect(Utils.sanitizeURI('unix://////some/path', false)).toEqual('unix:///some/path');

        expect(Utils.sanitizeURI('//some/path', false)).toEqual('unix:///some/path');
        expect(Utils.sanitizeURI('//////some/path', false)).toEqual('unix:///some/path');
    });

    it('should auto detect npipe scheme when on windows host', function() {
        expect(Utils.sanitizeURI('//server/pipe/pipename', true)).toEqual('npipe:////server/pipe/pipename');
    });

    it('should auto correct the count of leading slashes in the scheme specific part for named pipes', function() {
        expect(Utils.sanitizeURI('/server/pipe/pipename', true)).toEqual('npipe:////server/pipe/pipename');
        expect(Utils.sanitizeURI('//////server/pipe/pipename', true)).toEqual('npipe:////server/pipe/pipename');
        expect(Utils.sanitizeURI('npipe:/server/pipe/pipename', false)).toEqual('npipe:////server/pipe/pipename');
        expect(Utils.sanitizeURI('npipe://////server/pipe/pipename', false)).toEqual('npipe:////server/pipe/pipename');
    });

    it('should replace all backlashes with slashes for named pipes', function() {
        expect(Utils.sanitizeURI('\\server\\pipe\\pipename', true)).toEqual('npipe:////server/pipe/pipename');
        expect(Utils.sanitizeURI('npipe:\\\\server\\pipe\\pipename', true)).toEqual('npipe:////server/pipe/pipename');
        expect(Utils.sanitizeURI('npipe:\\/server\\pipe/pipename', true)).toEqual('npipe:////server/pipe/pipename');
        expect(Utils.sanitizeURI('/some\\path', false)).toEqual('unix:///some\\path');
    });

    it('should trim paths', function() {
        expect(Utils.sanitizeURI(null, true)).toEqual(null);
        expect(Utils.sanitizeURI('', false)).toEqual('');
        expect(Utils.sanitizeURI('  ', false)).toEqual('');
        expect(Utils.sanitizeURI('  tcp://127.0.0.1', false)).toEqual('tcp://127.0.0.1');
        expect(Utils.sanitizeURI('tcp://127.0.0.1  ', false)).toEqual('tcp://127.0.0.1');
        expect(Utils.sanitizeURI('  tcp://127.0.0.1  ', false)).toEqual('tcp://127.0.0.1');
    });

    it('should default to tcp', function() {
        expect(Utils.sanitizeURI('somehostname:2375', false)).toEqual('tcp://somehostname:2375');
    });
});

describe('shortenString', function() {

    it('should shorten string to the desired length', function() {
        expect(Utils.shortenString('hello world', 10)).toEqual('hello worl…')
    });

    it('should not affect strings shorter or of maximal length', function() {
        expect(Utils.shortenString('', 100)).toEqual('');
        expect(Utils.shortenString('hello world', 100)).toEqual('hello world');
        expect(Utils.shortenString('hello world', 11)).toEqual('hello world')
    });

    it('should trim whitespaces when shortening strings', function() {
        expect(Utils.shortenString('hello world ', 100)).toEqual('hello world ');
        expect(Utils.shortenString('hello world ', 6)).toEqual('hello…');
        expect(Utils.shortenString('hello world ', 5)).toEqual('hello…');
    });

    it('should be resilient to null or undefined values', function() {
        expect(Utils.shortenString(null, 100)).toEqual('');
        expect(Utils.shortenString(undefined, 100)).toEqual('');
    });
});