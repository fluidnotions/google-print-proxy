self.addEventListener('message', function(e) {
  var data = e.data;
  switch (data.cmd) {
    case 'start':
      self.postMessage('polljobqueue worker started');
      polljobqueue(data);
      break;

    case 'stop':
      self.postMessage('polljobqueue worker stopped');
      self.close(); // Terminates the worker.
      break;
    default:
      self.postMessage('Unknown command: ' + data.cmd);
  };
}, false);

function polljobqueue(data) {
  setInterval(function() {
    var response = httpGet(data.url, data);
    self.postMessage(response);
  }, data.refreshRate);
};

function httpGet(url, data) {
  var xmlHttp = new XMLHttpRequest();
  xmlHttp.open("GET", url+"jobqueue?username="+data.username, false);
  xmlHttp.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
  xmlHttp.send(null);
  return xmlHttp.responseText;
}
