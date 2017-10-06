
const ValidationHandler = require('ValidationHandler');

describe('Field validation', function() {

    let Validator = function(result) {
        let self = this;
        self.invoked = false;
        self.run = function() {
            self.invoked = true;
            return result;
        }
    };

    let emptyValidationResult = { warnings: [], error: null };

    it('should work with no validators defined', function() {
        const validationHandler = new ValidationHandler({});

        expect(validationHandler.validate($j('<input/>', null))).toEqual(emptyValidationResult);
        expect(validationHandler.validate($j('<input/>', []))).toEqual(emptyValidationResult);
    });

    it('should return empty result for successful validation', function() {
        let validator = new Validator();
        const validationHandler = new ValidationHandler({ testId: [ validator.run ] });
        expect(validationHandler.validate($j('<input/>'), 'testId')).toEqual(emptyValidationResult);
        expect(validator.invoked).toEqual(true);
    });

    it('should handle validation error', function() {
        let okValidator1 = new Validator();
        let errorValidator1 = new Validator({ msg: 'error1' });
        let errorValidator2 = new Validator({ msg: 'error2' });
        let okValidator2 = new Validator();

        const validationHandler = new ValidationHandler({ testId: [ okValidator1.run, errorValidator1.run, errorValidator2.run,
            okValidator2.run ] });

        expect(validationHandler.validate($j('<input/>'), 'testId')).toEqual({ warnings: [], error: 'error1' });
        expect(okValidator1.invoked).toEqual(true);
        expect(errorValidator1.invoked).toEqual(true);
        expect(errorValidator2.invoked).toEqual(true);
        expect(okValidator2.invoked).toEqual(true);
    });

    it('should handle validation warnings', function() {
        let errorValidator1 = new Validator({ msg: 'error1' });
        let warnValidator1 = new Validator({ msg: 'warn1', warning: true });
        let errorValidator2 = new Validator({ msg: 'error1' });
        let warnValidator2 = new Validator({ msg: 'warn2', warning: true });

        const validationHandler = new ValidationHandler({ testId: [ errorValidator1.run, warnValidator1.run, errorValidator2.run,
            warnValidator2.run ] });

        expect(validationHandler.validate($j('<input/>'), 'testId')).toEqual({ warnings: [ 'warn1', 'warn2' ], error: 'error1' });
        expect(errorValidator1.invoked).toEqual(true);
        expect(warnValidator1.invoked).toEqual(true);
        expect(errorValidator2.invoked).toEqual(true);
        expect(warnValidator2.invoked).toEqual(true);
    });
});