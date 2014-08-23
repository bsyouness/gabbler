'use strict';

var gabblerControllers =
  angular.module(
    'gabbler.controllers',
    [
      'gabbler.services'
    ]
  );

gabblerControllers.controller('HomeCtrl', ['$scope', 'Message', 'Cursor', function($scope, Message, Cursor) {

  $scope.getMessages = function() {
    var params = {id: $scope.cursor, since: 0};
    if ($scope.lastId > 0) {
      params.since = $scope.lastId;
    }
    Cursor.get(params, function(messages) {
      $scope.messages = messages.list.concat($scope.messages);
      $scope.lastId = messages.lastId;
      $scope.firstId = messages.firstId;
      $scope.state = 'connected';
      $scope.getMessages();
    }, function() {
      $scope.state = 'disconnected';
      setTimeout($scope.getMessages, 5000);
    });
  };

  $scope.messages = [];
  
  if (sessionStorage.cursor) {
    $scope.cursor = sessionStorage.cursor;
  }
  
  if (!$scope.hasOwnProperty('cursor')) {
    Cursor.save({}, {}, function(value, responseHeaders) {
	  var loc = responseHeaders('location');
	  $scope.cursor = /[0-9a-z]+$/.exec(loc).pop();
	  sessionStorage.cursor = $scope.cursor;
	  $scope.getMessages();
    });
  } else {
    $scope.getMessages();
  };

  document.onkeydown = function(evt) {
    if (!evt.ctrlKey && !evt.metaKey) return;
    if (evt.keyIdentifier == 'Enter') {
      $scope.sendMessage();
    }
  };

  $scope.message = new Message({ 'username': '' });
  
  $scope.sending = false;
  $scope.resending = false;

  $scope.sendMessage = function() {
    if ($scope.message.text == '' || $scope.sending && !$scope.resending) return;
    $scope.sending = true;
    $scope.message.$save({}, function() { 
      $scope.sending = false;
      $scope.resending = false;
      $scope.message = new Message({ 'username': '' });
      document.getElementById('text-input').focus();
    }, function() {
      $scope.resending = true;
      setTimeout($scope.sendMessage, 1000);
    });
  };
}]);
