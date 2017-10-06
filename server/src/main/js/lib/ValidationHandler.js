

function ValidationHandler(validatorsRegistry) {

    this.validate = validate;

    function validate($elt, id, context) {
        let result;
        let error = null;
        let warnings = [];
        let validators = validatorsRegistry[id];

        if (validators) {
            $j.each(validators, function (i, validator) {
                result = validator($elt, context);
                if (result) {
                    if (result.warning) {
                        warnings.push(result.msg);
                    } else if (!error) {
                        error = result.msg;
                    }
                }
            });
        }

        return { warnings: warnings || [], error: error };
    }
}

module.exports = ValidationHandler;