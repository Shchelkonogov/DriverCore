jQuery(window).resize(function() {
    var different = document.getElementById("tableForm:table").offsetWidth - document.getElementById("tableForm:table_data").offsetWidth;

    if (different > 0) {
        document.getElementById("tableForm:table").getElementsByClassName("ui-datatable-scrollable-header-box")[0].style.marginRight = different + "px";
    } else {
        document.getElementById("tableForm:table").getElementsByClassName("ui-datatable-scrollable-header-box")[0].style.marginRight = "0";
    }
});

jQuery(window).on('load', function () {
    connect();
});

var interval;

function connect() {
    var ws = new WebSocket('ws://172.16.4.26:7001/DriverCore/ws/client');
    // var ws = new WebSocket('ws://localhost:7001/DriverCore/ws/client');

    ws.onmessage = function(e) {
        var split = e.data.toString().split(':');
        if (split.length >= 2) {
            var commandName = split[0];
            var data = e.data.toString().replace(commandName + ':', '');
            switch (commandName) {
                case 'id':
                    setID([{name: 'json', value: data}]);
                    break;
                case 'update':
                    update([{name: 'json', value: data}]);
                    break;
                case 'info':
                    updateInfo([{name: 'json', value: data}]);
                    break;
                case 'allStatistic':
                    updateAll([{name: 'json', value: data}]);
                    break;
                case 'logData':
                    setLogData([{name: 'json', value: data}]);
                    break;
                case 'configNames':
                    updateConfigNames([{name: 'json', value: data}]);
                    break;
            }
        }
    };

    ws.onclose = function(e) {
        console.log('Socket is closed. Reconnect will be attempted in 1 second.', e.reason);
        // Попытка единоразового переподключения через 1 секунду
        setTimeout(function() {
            connect();
        }, 1000);
    };

    ws.onerror = function(err) {
        console.error('Socket encountered error: ', err.message, 'Closing socket');
        ws.close();
    };

    clearInterval(interval);

    interval = setInterval(function() {ws.send('ping message server');}, 25000);
}