define(['text!./compile.html', 'core/pluginapi', 'core/widgets/log', 'core/model', 'css!./compile.css'], function(template, api, log, model){

	var ko = api.ko;
	var sbt = api.sbt;

	var compileConsole = api.PluginWidget({
		id: 'compile-widget',
		template: template,
		init: function(parameters){
			var self = this

			this.title = ko.observable("Compile");
			this.activeTask = ko.observable(""); // empty string or taskId
			this.haveActiveTask = ko.computed(function() {
				return self.activeTask() != "";
			}, this);
			this.startStopLabel = ko.computed(function() {
				if (self.haveActiveTask())
					return "Stop";
				else
					return "Start";
			}, this);
			this.needCompile = ko.observable(false);
			this.recompileOnChange = ko.observable(true);

			this.logModel = new log.Log();
			this.logScroll = this.logModel.findScrollState();

			this.status = ko.observable(api.STATUS_DEFAULT);

			api.events.subscribe(function(event) {
				return event.type == 'FilesChanged';
			},
			function(event) {
				if (self.recompileOnChange()) {
					console.log("files changed, doing a recompile");
					// doCompile just marks a compile pending if one is already
					// active.
					self.doCompile();
				} else {
					console.log("recompile on change unchecked, doing nothing");
				}
			});

			// we generally expect to get this event on startup, which
			// will then cause us to reload sources, which will then
			// trigger a FilesChanged which will trigger a compile.
			api.events.subscribe(function(event) {
				console.log("filtering event: ", event);
				return event.type == 'SourcesMayHaveChanged';
			},
			function(event) {
				console.log("Sources may have changed, reloading the list");
				self.reloadSources(null);
			});
		},
		update: function(parameters){
		},
		logEvent: function(event) {
			if (this.logModel.event(event)) {
				// logged already
			} else {
				this.logModel.leftoverEvent(event);
			}
		},
		reloadProjectInfoThenCompile: function() {
			var self = this;

			self.logModel.info("Loading details of this project...");

			self.status(api.STATUS_BUSY);
			var taskId = api.sbt.runTask({
				task: 'name',
				onmessage: function(event) {
					console.log("event from name task ", event);
					self.logEvent(event);
				},
				success: function(result) {
					console.log("name task results ", result);
					self.activeTask("");

					var app = model.snap.app;
					app.name(result.params.name);
					app.hasAkka(result.params.hasAkka !== false);
					app.hasPlay(result.params.hasPlay !== false);
					app.hasConsole(result.params.hasConsole !== false);

					self.logModel.debug("name=" + app.name() +
							" hasAkka=" + app.hasAkka() +
							" hasPlay=" + app.hasPlay() +
							" hasConsole=" + app.hasConsole());

					self.compileAfterReloadProjectInfo();
				},
				failure: function(status, message) {
					console.log("loading project info failed", message);
					self.activeTask("");
					self.logModel.warn("Failed to load project details: " + message);
					self.status(api.STATUS_ERROR);
				}
			});
			self.activeTask(taskId);
		},
		// after = optional
		reloadSources: function(after) {
			var self = this;

			self.logModel.info("Refreshing list of source files to watch for changes...");
			// Are we busy when watching sources? I think so...
			self.status(api.STATUS_BUSY);
			sbt.watchSources({
				onmessage: function(event) {
					console.log("event watching sources", event);
					self.logEvent(event);
				},
				success: function(data) {
					console.log("watching sources result", data);
					self.status(api.STATUS_DEFAULT);
					self.logModel.info("Will watch " + data.count + " source files.");
					if (typeof(after) === 'function')
						after();
				},
				failure: function(status, message) {
					console.log("watching sources failed", message);
					self.logModel.warn("Failed to reload source file list: " + message);
					// WE should modify our status here!
					self.status(api.STATUS_ERROR);
					if (typeof(after) === 'function')
						after();
				}
			});
		},
		afterCompile: function(succeeded) {
			var self = this;

			if (succeeded)
				self.status(api.STATUS_DEFAULT);
			else
				self.status(api.STATUS_ERROR);

			if (self.needCompile()) {
				console.log("need to recompile because something changed while we were compiling");
				self.needCompile(false);
				self.doCompile();
			} else if (succeeded) {
				// asynchronously reload the list of sources in case
				// they changed. we are trying to serialize sbt usage
				// here so we only send our event out when we finish
				// with the reload.
				self.reloadSources(function() {
					// notify others
					api.events.send({ 'type' : 'CompileSucceeded' });
				});
			}
		},
		compileAfterReloadProjectInfo: function() {
			var self = this;

			if (self.needCompile()) {
				// asked to restart while loading the project info; so bail out here
				// without starting the actual compile task.
				self.logModel.info('Recompile requested.');
				self.afterCompile(false);
				return;
			}

			self.status(api.STATUS_BUSY);
			self.logModel.info("Compiling...");
			var task = { task: 'compile' };
			var taskId = sbt.runTask({
				task: task,
				onmessage: function(event) {
					self.logEvent(event);
				},
				success: function(data) {
					console.log("compile result: ", data);
					self.activeTask("");
					if (data.type == 'GenericResponse') {
						self.logModel.info('Compile complete.');
					} else {
						self.logModel.error('Unexpected reply: ' + JSON.stringify(data));
					}
					self.afterCompile(true); // true=success
				},
				failure: function(status, message) {
					console.log("compile failed: ", status, message)
					self.activeTask("");
					self.logModel.error("Request failed: " + status + ": " + message);
					self.afterCompile(false); // false=failed
				}
			});
			self.activeTask(taskId);
		},
		// this does reload project info task, compile task, then watch sources task.
		doCompile: function() {
			var self = this;
			if (self.haveActiveTask()) {
				console.log("Attempt to compile with a compile already active, will recompile again when we finish");
				self.needCompile(true);
				return;
			}

			self.logModel.clear();
			self.reloadProjectInfoThenCompile();
		},
		startStopButtonClicked: function(self) {
			console.log("Start/stop compile was clicked");
			if (self.haveActiveTask()) {
				sbt.killTask({
					taskId: self.activeTask(),
					success: function(data) {
						console.log("kill success: ", data)
					},
					failure: function(status, message) {
						console.log("kill failed: ", status, message)
						self.logModel.error("Killing task failed: " + status + ": " + message)
					}
				});
			} else {
				self.doCompile();
			}
		},
		onPreDeactivate: function() {
			this.logScroll = this.logModel.findScrollState();
		},
		onPostActivate: function() {
			this.logModel.applyScrollState(this.logScroll);
		}
	});

	return api.Plugin({
		id: 'compile',
		name: "Compile",
		icon: "B",
		url: "#compile",
		routes: {
			'compile': function() { api.setActiveWidget(compileConsole); }
		},
		widgets: [compileConsole],
		status: compileConsole.status
	});
});
