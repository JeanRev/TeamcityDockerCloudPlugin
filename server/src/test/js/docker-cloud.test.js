

var dockerCloud = BS.Clouds.Docker;
var $j = jQuery.noConflict();
describe('Version number comparator', function() {

    it('should order valid version numbers', function() {
        // An intentionally failing test. No code within expect() will never equal 4.
        expect(dockerCloud.compareVersionNumbers('1.0', '2.0')).toBeLessThan(0);
        expect(dockerCloud.compareVersionNumbers('2.0', '2.0')).toEqual(0);
        expect(dockerCloud.compareVersionNumbers('2.0', '1.0')).toBeGreaterThan(0);
        expect(dockerCloud.compareVersionNumbers('2.0', '2.0.1')).toBeLessThan(0);
    });

    it('should treat unparseable token digit as 0', function() {
        // An intentionally failing test. No code within expect() will never equal 4.
        expect(dockerCloud.compareVersionNumbers('0', 'hello world')).toEqual(0);
        expect(dockerCloud.compareVersionNumbers('1.0', '1.a')).toEqual(0);
        expect(dockerCloud.compareVersionNumbers('1.0', '1.a')).toEqual(0);
        expect(dockerCloud.compareVersionNumbers('2.0', '2.0')).toEqual(0);
        expect(dockerCloud.compareVersionNumbers('2.0.0', '2.0.a')).toEqual(0);
        expect(dockerCloud.compareVersionNumbers('2.0.0', '2.0.-1')).toEqual(0);
        expect(dockerCloud.compareVersionNumbers('2.0.0.0', '2.0.404a.0')).toEqual(0);
    });
});



