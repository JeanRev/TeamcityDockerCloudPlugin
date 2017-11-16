

function migrateSettings(imageData) {
    var editor;
    switch(imageData.Administration.Version) {
        case 1:
            // V1: 'Binds' must be exported from the container configuration into the editor configuration,
            // where they will not be stored using the Docker syntax ([host_path]:[container_path]:[mode])
            // but splitted into JSON fields. This allow us to avoid to handle specially with colons in
            // filename for unix (see docker issue #8604, still open today) and windows drive letters
            // (solved in Docker using complexes regexes).
            var container = imageData.Container || {};
            var hostConfig = container.HostConfig || {};
            editor = imageData.Editor || {};
            imageData.Editor = editor;

            editor.Binds = [];

            if (hostConfig.Binds) {
                hostConfig.Binds.forEach(function(bind) {
                    var tokens = bind.split(':');
                    if (tokens.length > 3) {
                        // We are in difficulty as soon as we have more than three tokens: we will then not
                        // evaluate the whole binding definition. This is less crucial for unix file paths,
                        // because the Docker daemon will consider such definition invalid and reject them
                        // anyway.
                        // For Windows file paths, we apply a simple heuristic that should be "good enough":
                        // if a definition token looks like a drive letter then we merge it with the following
                        // token.
                        var copy = tokens.slice();
                        var newTokens = [];
                        var mode = copy.pop();
                        while(copy.length) {
                            var token = copy.shift();
                            if (token.match('^[a-zA-Z0-9]$') && copy.length) {
                                token += ':' + copy.shift();
                            }
                            newTokens.push(token);
                        }
                        if (newTokens.length >= 2 && (mode === 'ro' || mode === 'rw')) {
                            tokens = [newTokens[0], newTokens[1], mode];
                        }
                    }
                    editor.Binds.push({ PathOnHost: tokens[0], PathInContainer: tokens[1],  ReadOnly: tokens[2] });
                });
            }
        case 2:
            imageData.Administration.PullOnCreate = true;
        case 3:

            editor = imageData.Editor || {};
            var migrationInfo = [];
            imageData.Editor = editor;
            [
                {value: 'Memory', unit: 'MemoryUnit'},
                {value: 'MemorySwap', unit: 'MemorySwapUnit'}].forEach(function(mem) {
                var value;
                if (imageData.Container && imageData.Container.HostConfig) {
                    value = imageData.Container.HostConfig[mem.value];
                }
                var unit = editor[mem.unit];
                if (unit === 'MiB' || unit === 'GiB') {
                    migrationInfo.push(editor);
                    if (value && value !== -1) {
                        imageData.Container.HostConfig[mem.value] = value * 8;
                    }
                }
            });
        case 4:
            if (imageData.Container) {
                imageData.AgentHolderSpec = imageData.Container;
                delete imageData.Container;
            }
    }
}

module.exports = migrateSettings;