/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define(['text!./console.html', 'core/pluginapi', 'core/model', 'css!./console.css'], function(template, api, model){
  var ko = api.ko;
  var sbt = api.sbt;

  var consoleConsole = api.PluginWidget({
    id: 'console-widget',
    template: template,
    init: function(parameters){
      var self = this
    }
  });

  return api.Plugin({
    id: 'console',
    name: "Console",
    icon: "B",
    url: "#console",
    routes: {
      'console': function() { api.setActiveWidget(consoleConsole); }
    },
    widgets: [consoleConsole],
    status: consoleConsole.status
  });
});
