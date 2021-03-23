var x = [];

var bin_max = 800.0, bin_min = 0.0, bin_size = 5.0;
var trace = {
    x: x, autobinx: false,
    marker: {
        color: "rgba(0, 0, 255, 0.7)",
        line: {
            color: "rgba(0, 0, 0, 1)",
            width: 1
        }
    },
    opacity: 1,
    type: "histogram",
    xbins: {
        end: bin_max,
        size: bin_size,
        start: bin_min,
    }
};
var data = [trace];

var layout = {
    title: "Energy in volume",
    xaxis: {
        title: "Energy (keV)", range: [bin_min, bin_max],
    },
    yaxis: {title: "Counts"},
}

Plotly.newPlot("histogram", data, layout);


const ws = new WebSocket("ws://localhost:8080/")
// Connection opened
ws.addEventListener('open', function (event) {
    ws.send('Hello Server!');
});

// Listen for messages
ws.addEventListener('message', function (event) {
    // console.log('Message from server ', event.data + "\n");
    //ws.send("Client received " + event.data)
});

ws.onmessage = function (evt) {
    let json = JSON.parse(evt.data);
    // json should contain a 'volume' and a 'energy' field
    let volume = json["volume"]
    let energy = json["volume"]
    console.log(json)
    console.log(volume)
    console.log(energy)
    //Plotly.extendTraces('histogram', {x: [energy]}, [0]);
};

