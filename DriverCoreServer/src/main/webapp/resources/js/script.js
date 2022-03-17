$(document).ready(function() {
    connect();

    try {
        siteFunctions.patchContextMenuShow();

        $(document.getElementById("tableForm:table")).bind("contextmenu", function (event) {
            event.preventDefault();
        });
    } catch (e) {
        console.error(e);
    }
});

jQuery(window).resize(function() {
    var different = document.getElementById("tableForm:table").offsetWidth - document.getElementById("tableForm:table_data").offsetWidth;

    if (different > 0) {
        document.getElementById("tableForm:table").getElementsByClassName("ui-datatable-scrollable-header-box")[0].style.marginRight = different + "px";
    } else {
        document.getElementById("tableForm:table").getElementsByClassName("ui-datatable-scrollable-header-box")[0].style.marginRight = "0";
    }
});

var interval;

function connect() {
    var ws = new WebSocket('ws://172.16.4.26:7003/DriverCore/ws/client');
    // var ws = new WebSocket('ws://localhost:7001/DriverCore/ws/client');
    // var ws = new WebSocket('ws://10.230.1.102:7001/DriverCore/ws/client');

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

//patch to fix a problem that the context menu disappears after update
//delay the show to occure after the update
var siteFunctions = {
    patchContextMenuShow: function() {
        var protShow = PrimeFaces.widget.ContextMenu.prototype.show;
        siteFunctions.patchContextMenuShow.lastEvent = null;
        PrimeFaces.widget.ContextMenu.prototype.show = function(e) {
            var ret;
            if (e) {
                //saving last event
                siteFunctions.patchContextMenuShow.lastEvent = e;
                siteFunctions.patchContextMenuShow.lastEventArg = arguments;
                siteFunctions.patchContextMenuShow.lastEventContext = this;
            } else if (siteFunctions.patchContextMenuShow.lastEvent) {
                //executing last event
                ret = protShow.apply(siteFunctions.patchContextMenuShow.lastEventContext, siteFunctions.patchContextMenuShow.lastEventArg);
                //clearing last event
                siteFunctions.patchContextMenuShow.lastEvent = null;
            }
            return ret;
        };
    }
};