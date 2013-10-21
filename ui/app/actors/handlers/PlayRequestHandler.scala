/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package actors.handlers

import play.api.libs.json.{ JsString, JsObject, JsValue }
import akka.actor.ActorRef
import scala.concurrent.{ ExecutionContext, Future }
import actors._

class PlayRequestHandler extends ConsoleHandlerActor {
  import ExecutionContext.Implicits.global

  def call(receiver: ActorRef, mi: ModuleInformation): Future[(ActorRef, JsValue)] = {
    val timeFilter = mi.time.queryParams
    val scopeFilter = "&" + mi.scope.queryParams
    val playRequestPromise = call(ConsoleHandlerActor.playRequestURL2 + mi.traceId.get, timeFilter + scopeFilter)
    for {
      playRequest ← playRequestPromise
    } yield {
      val result = validateResponse(playRequest) match {
        case ValidResponse ⇒
          val data = playRequest.json
          JsObject(Seq(
            "type" -> JsString("playrequest"),
            "data" -> data))
        case InvalidLicense(jsonLicense) ⇒ jsonLicense
        case ErrorResponse(jsonErrorCodes) ⇒ jsonErrorCodes
      }

      (receiver, result)
    }
  }
}
