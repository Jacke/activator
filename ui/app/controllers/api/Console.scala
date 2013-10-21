/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package controllers.api

import play.api.mvc.{ WebSocket, Controller }
import play.api.libs.json.JsValue
import actors.ClientController

object Console extends Controller {
  def handle(id: String) = WebSocket.async[JsValue] { req ⇒
    ClientController.join(id)
  }
}
