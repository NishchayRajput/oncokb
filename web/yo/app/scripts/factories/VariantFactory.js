var $ = window.$;

angular.module('oncokbApp').factory('TumorType', ['$http', function($http) {
    'use strict';

    function getFromServer() {
        return $http.get(OncoKB.config.apiLink + 'tumorType.json');
    }

    function getFromFile() {
        return $http.get('data/tumorType.json');
    }

    return {
        getFromServer: getFromServer,
        getFromFile: getFromFile
    };
}]);

angular.module('oncokbApp').factory('Gene', ['$http', function($http) {
    'use strict';

    function getFromServer() {
        return $http.get(OncoKB.config.curationLink + 'gene.json');
    }

    function getFromFile() {
        return $http.get('data/gene.json');
    }

    return {
        getFromServer: getFromServer,
        getFromFile: getFromFile
    };
}]);

angular.module('oncokbApp').factory('DataSummary', ['$http', function($http) {
    'use strict';

    function getFromServer() {
        return $http.get('data/summary.json');
    }

    function getFromFile() {
        return $http.get('data/summary.json');
    }

    return {
        getFromServer: getFromServer,
        getFromFile: getFromFile
    };
}]);

angular.module('oncokbApp').factory('GeneStatus', ['$http', function($http) {
    'use strict';

    function getFromServer(params) {
        console.log(params);
        return $http({
            url: OncoKB.config.apiLink + 'geneStatus.json',
            method: 'GET',
            params: {geneId: params.geneId || ''}
        });
    }

    function setToServer(params) {
        console.log(params);
        var transform = function(data) {
            return $.param(data);
        };
        return $http.post(
            OncoKB.config.apiLink + 'geneStatus.json',
            {geneId: params.geneId || '', status: params.status || 'Not ready'},
            {
                headers: {'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'},
                transformRequest: transform
            });
    }

    function getFromFile() {
        return $http.get('data/geneStatus.json');
    }

    function setToFile(params) {
        console.log(params);
        return $http.get('data/geneStatus.json');
    }

    return {
        getFromServer: getFromServer,
        getFromFile: getFromFile,
        setToServer: setToServer,
        setToFile: setToFile
    };
}]);

angular.module('oncokbApp').factory('Alteration', ['$http', function($http) {
    'use strict';

    function getFromServer() {
        return $http.get(OncoKB.config.apiLink + 'alteration.json');
    }

    function getFromFile() {
        return $http.get('data/alteration.json');
    }

    return {
        getFromServer: getFromServer,
        getFromFile: getFromFile
    };
}]);

angular.module('oncokbApp').factory('DriveOncokbInfo', ['$http', function($http) {
    'use strict';

    function getFromServer() {
        return $http.get(OncoKB.config.curationLink + 'oncokbInfo.json');
    }

    function getFromFile() {
        return $http.get('data/oncokbInfo.json');
    }

    return {
        getFromServer: getFromServer,
        getFromFile: getFromFile
    };
}]);

angular.module('oncokbApp')
    .config(function($httpProvider) {
        $httpProvider.interceptors.push('xmlHttpInterceptor');
    }).factory(('SearchVariant'), ['$http', function($http) {
    'use strict';
    function getAnnotation(params) {
        var _params = angular.copy(params),
            _url = OncoKB.config.apiLink + 'var_annotation?';

        for (var _key in _params) {
            if (typeof _params[_key] !== 'undefined' && _params[_key] && _params[_key] !== '') {
                _url += _key + '=' + _params[_key] + '&';
            }
        }
        _url = _url.substring(0, _url.length - 1);
        return $http.get(_url);
    }

    function postAnnotation(params) {
        return $http({
            url: OncoKB.config.apiLink + 'var_annotation',
            method: 'POST',
            params: params
        });
    }

    function annotationFromFile() {
        return $http.get('data/annotation.xml');
    }

    return {
        getAnnotation: getAnnotation,
        postAnnotation: postAnnotation,
        annotationFromFile: annotationFromFile
    };
}]);

angular.module('oncokbApp').factory('GenerateDoc', ['$http', function($http) {
    'use strict';
    var transform = function(data) {
        return $.param(data);
    };

    function getDoc(params) {
        return $http.post(
            OncoKB.config.apiLink + 'generateGoogleDoc',
            {'reportParams': JSON.stringify(params)},
            {
                headers: {'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'},
                transformRequest: transform
            });
    }

    function createFolder(params) {
        return $http.post(
            OncoKB.config.apiLink + 'createGoogleFolder',
            params,
            {
                headers: {'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'},
                transformRequest: transform
            });
    }

    return {
        getDoc: getDoc,
        createFolder: createFolder
    };
}]);

angular.module('oncokbApp').factory('SendEmail', ['$http', function($http) {
    'use strict';
    var transform = function(data) {
        return $.param(data);
    };

    function init(params) {
        return $http.post(
            OncoKB.config.apiLink + 'sendEmail',
            params,
            {
                headers: {'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'},
                transformRequest: transform
            });
    }

    return {
        init: init
    };
}]);

angular.module('oncokbApp').factory('DriveAnnotation', ['$http', function($http) {
    'use strict';
    var transform = function(data) {
        return $.param(data);
    };

    function updateGene(data) {
        return $http.post(
            OncoKB.config.curationLink + 'driveAnnotation',
            data,
            {
                headers: {'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'},
                transformRequest: transform
            });
    }

    return {
        updateGene: updateGene
    };
}]);

angular.module('oncokbApp').factory('InternalAccess', ['$http', function($http) {
    'use strict';
    return $http.get(OncoKB.config.apiLink + 'access');
}]);

angular.module('oncokbApp').factory('ServerUtils', ['$http', function($http) {
    'use strict';

    function getFromServer(type) {
        if (type === 'hotspot') {
            return $http.get(OncoKB.config.apiLink + 'utils?cmd=hotspot');
        } else if (type === 'autoMutation') {
            return $http.get(OncoKB.config.apiLink + 'utils?cmd=autoMutation');
        }
        return null;
    }

    function getFromFile(type) {
        if (type === 'hotspot') {
            return $http.get('data/hotspot.json');
        } else if (type === 'autoMutation') {
            return $http.get('data/autoMutation.json');
        }
        return null;
    }

    return {
        hotspot: {
            getFromServer: function() {
                return getFromServer('hotspot');
            },
            getFromFile: function() {
                return getFromFile('hotspot');
            }
        },
        autoMutation: {
            getFromServer: function() {
                return getFromServer('autoMutation');
            },
            getFromFile: function() {
                return getFromFile('autoMutation');
            }
        }
    };
}]);

angular.module('oncokbApp').factory('Cache', ['$http', function($http) {
    'use strict';
    var transform = function(data) {
        return $.param(data);
    };

    function setStatus(status) {
        return $http.post(
            OncoKB.config.apiLink + 'cache',
            {cmd: status},
            {
                headers: {'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'},
                transformRequest: transform
            }
        );
    }

    function getStatus() {
        return $http({
            url: OncoKB.config.apiLink + 'cache',
            method: 'GET',
            params: {cmd: 'getStatus'}
        });
    }

    function updateGene(hugoSymbol) {
        var transform = function(data) {
            return $.param(data);
        };
        return $http.post(
            OncoKB.config.apiLink + 'cache', {
                cmd: 'updateGene',
                hugoSymbol: hugoSymbol
            }, {
                headers: {'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'},
                transformRequest: transform
            });
    }

    return {
        reset: function() {
            return setStatus('reset');
        },
        enable: function() {
            return setStatus('enable');
        },
        disable: function() {
            return setStatus('disable');
        },
        getStatus: getStatus,
        updateGene: updateGene
    };
}]);

angular.module('oncokbApp').factory('OncoTree', ['$http', function($http) {
    'use strict';

    function getMainType(status) {
        return $http.get(OncoKB.config.oncoTreeLink + 'mainTypes?version=oncokb');
    }

    function getTumorTypeByMainType(mainType) {
        return $http.get(OncoKB.config.oncoTreeLink +
            'tumorTypes/search/maintype/' + mainType + '?exactMatch=true&version=oncokb');
    }

    function getTumorType(type, query, exactMatch) {
        if (!type || !query) {
            return null;
        }
        exactMatch = !_.isBoolean(exactMatch) ? true : exactMatch;
        return $http.get(OncoKB.config.oncoTreeLink +
            'tumorTypes/search/' + type + '/' + query + '?exactMatch=' + exactMatch + '&version=oncokb');
    }

    function getTumorTypesByMainTypes(mainTypes) {
        var queries = _.map(mainTypes, function(mainType) {
            return {
                'type': 'maintype',
                'query': mainType,
                'exactMatch': true
            }
        });
        return $http.post(OncoKB.config.oncoTreeLink + 'tumorTypes/search',
            {
                'queries': queries,
                'version': 'oncokb'
            }, {
                headers: {'Content-Type': 'application/json'}
            });
    }

    return {
        getMainType: getMainType,
        getTumorTypeByMainType: getTumorTypeByMainType,
        getTumorType: getTumorType,
        getTumorTypesByMainTypes: getTumorTypesByMainTypes
    };
}]);

angular.module('oncokbApp').factory('ApiUtils', ['$http', function($http) {
    'use strict';

    function getIsoForms() {
        return $http.get('https://raw.githubusercontent.com/mskcc/vcf2maf/master/data/isoform_overrides_at_mskcc');
    }

    return {
        getIsoForms: getIsoForms
    };
}]);