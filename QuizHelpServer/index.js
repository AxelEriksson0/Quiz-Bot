var express = require('express');
var fs = require('fs');
var http = require('http');
var https = require('https');
var socket = require('socket.io');
var app = express();

app.use(express.json());

var latest_answer = [
    {"answer_1":"Option 1", "probability": 0.3333},
    {"answer_2":"Option 2", "probability": 0.3333},
    {"answer_3":"Option 3", "probability": 0.3333}
  ]

var photo_response = [
  {"description": "Water", "score": 0.58},
  {"description": "Wind", "score": 0.20},
  {"description": "Fire", "score": 0.24}
]

var app_version = "v0.6"

app.get("/api/app_version", function(req, res) {
  res.send(app_version)
})

app.get('/api/get_latest_app', function(req, res){
  var file = __dirname + '/quiz.help.apk';
  res.download(file);
});

app.get('/api/primetime', function (req, res) {
  res.send(latest_answer)
});

app.post('/api/primetime', function (req, res) {
  latest_answer = req.body;
  res.send(`Posted a new answer! ${req.body}`)
});

var port = process.env.PORT || 7001;
var hostname = '0.0.0.0';

https.createServer({
  key: fs.readFileSync('server.key'),
  cert: fs.readFileSync('server.cert')
}, app)
.listen(port, hostname, function () {
  console.log(`HTTPS listening on port ${port}...`)
})

var server = http.createServer(app)

// WebSocket
var io = socket.listen(server);

io.on('connection', (socket) => {

  socket.on('answers', (data) => {
    console.log(data)
    latest_answer = data;
    io.sockets.emit('answers', latest_answer);
  });

  socket.on('photo_response', (photo_data) => {
    photo_response = photo_data;
    io.sockets.emit('photo_response', photo_response);
  });

  socket.on('disconnect', function() {
    console.log("Someone left!")
  })

});

port_http = process.env.PORT || 7000;

server.listen(port_http, hostname, function () {
  console.log(`HTTP listening on port ${port_http}...`)
})
