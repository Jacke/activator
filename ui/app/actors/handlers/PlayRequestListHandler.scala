/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package actors.handlers

import actors._
import akka.actor.ActorRef
import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.json.{ JsString, JsObject, JsValue }
import actors.ModuleInformation
import actors.InvalidLicense

/**
 * This actor can be used to fetch data in two ways:
 *   - setting offset enables paging (default limit will be used if not set)
 *   - not setting offset will retrieve the "limit" latest events, i.e. a "live view" of the latest data
 */
class PlayRequestListHandler extends ConsoleHandlerActor {
  import ExecutionContext.Implicits.global

  def call(receiver: ActorRef, mi: ModuleInformation): Future[(ActorRef, JsValue)] = {
    val timeFilter = mi.time.queryParams
    val scopeFilter = "&" + mi.scope.queryParams
    val offset = for { pi ← mi.pagingInformation } yield pi.offset
    val offsetFilter =
      if (offset.isDefined) "&offset=" + offset.get
      else ""
    val limit = for { pi ← mi.pagingInformation } yield pi.limit
    val limitFilter = "&limit=" + limit.getOrElse("")
    val playRequestListPromise = call(ConsoleHandlerActor.playRequestListURL, timeFilter + scopeFilter + offsetFilter + limitFilter)
    for {
      playRequestList ← playRequestListPromise
    } yield {
      val result = validateResponse(playRequestList) match {
        case ValidResponse ⇒
          val data = JsObject(Seq("playRequestSummaries" -> playRequestList.json))
          JsObject(Seq(
            "type" -> JsString("playrequests"),
            "data" -> data))
        case InvalidLicense(jsonLicense) ⇒ jsonLicense
        case ErrorResponse(jsonErrorCodes) ⇒ jsonErrorCodes
      }

      (receiver, result)
    }
  }
}
