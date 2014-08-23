'use strict';

var gabblerServices =
  angular.module(
    'gabbler.services',
    [
      'ngResource'
    ]
  );

gabblerServices.factory(
  'Message',
  function($resource) {
    return $resource('/api/messages');
  });

gabblerServices.factory(
  'Cursor',
  function($resource) {
    return $resource('/api/cursors/:id');
  });
