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

function connect() {
    var ws = new WebSocket('ws://172.16.4.26:7001/DriverCore/ws/client');

    ws.onmessage = function(e) {
        update([{name: 'json', value: e.data}]);
    };

    ws.onclose = function(e) {
        console.log('Socket is closed. Reconnect will be attempted in 1 second.', e.reason);
        setTimeout(function() {
            connect();
        }, 1000);
    };

    ws.onerror = function(err) {
        console.error('Socket encountered error: ', err.message, 'Closing socket');
        ws.close();
    };

    setInterval(function(){
        ws.send('ping message server')}, 25000)
}